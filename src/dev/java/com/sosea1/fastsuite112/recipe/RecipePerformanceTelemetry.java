package com.sosea1.fastsuite112.recipe;

import com.sosea1.fastsuite112.config.FastSuiteConfig;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Opt-in sampled end-to-end timing for real crafting lookup calls.
 *
 * <p>This development-only sampler measures the surrounding lookup method, including
 * IRecipe.matches() work. It exists only in the development JAR, so the normal release has no injected timing calls at all.
 * When enabled, only one call in every 64 outermost lookups uses System.nanoTime().</p>
 */
public final class RecipePerformanceTelemetry {
    public enum Scope {
        CRAFTING_MANAGER_FIRST_MATCH,
        UNIVERSAL_TWEAKS_DEFAULT_MISS
    }

    public enum Backend {
        INDEXED,
        VANILLA
    }

    public static final RecipePerformanceTelemetry INSTANCE = new RecipePerformanceTelemetry();

    private static final int SAMPLE_MASK = 63; // 1 / 64

    private volatile boolean enabled;
    private final ThreadLocal<Probe> probes = new ThreadLocal<Probe>() {
        @Override
        protected Probe initialValue() {
            return new Probe();
        }
    };

    private final LongAdder[][] samples = counters();
    private final LongAdder[][] totalNanos = counters();
    private final AtomicLong[][] maxNanos = maxima();
    private final LongAdder abandonedFrameRecoveries = new LongAdder();

    private RecipePerformanceTelemetry() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            // Do not let an abandoned in-flight frame survive a profiling session boundary.
            probes.get().resetFrame();
        }
    }

    /** Called from a Mixin HEAD injection. Nested lookup helpers are intentionally not double-counted. */
    public void enter(Scope scope) {
        if (!enabled) return;
        Probe probe = probes.get();

        Scope normalizedScope = scope == null ? Scope.CRAFTING_MANAGER_FIRST_MATCH : scope;

        // RETURN injections do not execute when arbitrary third-party recipe code escapes by
        // exception. Without recovery, the abandoned depth would suppress all future samples on
        // this thread. A legitimate nested FastSuite/UT lookup uses a different scope; seeing the
        // same root scope again while a frame is still open therefore means either an abandoned
        // prior call or rare same-scope recipe recursion. Resetting is safe for crafting semantics
        // and preferable to permanently poisoning diagnostics. Crucially this is only comparisons
        // and does not add stack walking / nanoTime work to nested UT misses.
        if (probe.depth > 0 && probe.scope == normalizedScope) {
            probe.resetFrame();
            abandonedFrameRecoveries.increment();
        }

        if (probe.depth++ != 0) return;

        probe.sequence++;
        probe.sampled = (probe.sequence & SAMPLE_MASK) == 0L;
        probe.scope = normalizedScope;
        probe.backend = FastSuiteConfig.indexedCrafting ? Backend.INDEXED : Backend.VANILLA;
        if (!probe.sampled) return;

        probe.startedNanos = System.nanoTime();
    }

    /** Called from a matching Mixin RETURN injection. */
    public void exit() {
        if (!enabled) return;
        Probe probe = probes.get();
        if (probe.depth <= 0) {
            // Defensive recovery if a previous third-party lookup escaped abnormally without RETURN.
            probe.resetFrame();
            return;
        }
        if (--probe.depth != 0) return;
        if (!probe.sampled) {
            probe.resetFrame();
            return;
        }

        long elapsed = Math.max(0L, System.nanoTime() - probe.startedNanos);
        int b = probe.backend.ordinal();
        int s = probe.scope.ordinal();
        samples[b][s].increment();
        totalNanos[b][s].add(elapsed);
        updateMax(maxNanos[b][s], elapsed);
        probe.resetFrame();
    }

    public void reset() {
        for (int b = 0; b < Backend.values().length; b++) {
            for (int s = 0; s < Scope.values().length; s++) {
                samples[b][s].reset();
                totalNanos[b][s].reset();
                maxNanos[b][s].set(0L);
            }
        }
        abandonedFrameRecoveries.reset();
        Probe probe = probes.get();
        probe.sequence = 0L;
        probe.resetFrame();
    }

    public Snapshot snapshot() {
        long[][] sampleCopy = new long[Backend.values().length][Scope.values().length];
        long[][] nanosCopy = new long[Backend.values().length][Scope.values().length];
        long[][] maxCopy = new long[Backend.values().length][Scope.values().length];
        for (int b = 0; b < Backend.values().length; b++) {
            for (int s = 0; s < Scope.values().length; s++) {
                sampleCopy[b][s] = samples[b][s].sum();
                nanosCopy[b][s] = totalNanos[b][s].sum();
                maxCopy[b][s] = maxNanos[b][s].get();
            }
        }
        return new Snapshot(enabled, 64, sampleCopy, nanosCopy, maxCopy, abandonedFrameRecoveries.sum());
    }

    private static LongAdder[][] counters() {
        LongAdder[][] values = new LongAdder[Backend.values().length][Scope.values().length];
        for (int b = 0; b < values.length; b++) {
            for (int s = 0; s < values[b].length; s++) values[b][s] = new LongAdder();
        }
        return values;
    }

    private static AtomicLong[][] maxima() {
        AtomicLong[][] values = new AtomicLong[Backend.values().length][Scope.values().length];
        for (int b = 0; b < values.length; b++) {
            for (int s = 0; s < values[b].length; s++) values[b][s] = new AtomicLong();
        }
        return values;
    }

    private static void updateMax(AtomicLong target, long candidate) {
        long current = target.get();
        while (candidate > current && !target.compareAndSet(current, candidate)) current = target.get();
    }

    private static final class Probe {
        private long sequence;
        private int depth;
        private boolean sampled;
        private long startedNanos;
        private Scope scope;
        private Backend backend;

        private void resetFrame() {
            depth = 0;
            sampled = false;
            startedNanos = 0L;
            scope = null;
            backend = null;
        }
    }

    public static final class Snapshot {
        public final boolean enabled;
        public final int sampleEvery;
        public final long abandonedFrameRecoveries;
        private final long[][] samples;
        private final long[][] totalNanos;
        private final long[][] maxNanos;

        private Snapshot(boolean enabled,
                         int sampleEvery,
                         long[][] samples,
                         long[][] totalNanos,
                         long[][] maxNanos,
                         long abandonedFrameRecoveries) {
            this.enabled = enabled;
            this.sampleEvery = sampleEvery;
            this.samples = samples;
            this.totalNanos = totalNanos;
            this.maxNanos = maxNanos;
            this.abandonedFrameRecoveries = abandonedFrameRecoveries;
        }

        public long samples(Backend backend, Scope scope) {
            return samples[backend.ordinal()][scope.ordinal()];
        }

        public long totalSamples(Backend backend) {
            long sum = 0L;
            for (Scope scope : Scope.values()) sum += samples(backend, scope);
            return sum;
        }

        public long totalNanos(Backend backend) {
            long sum = 0L;
            for (Scope scope : Scope.values()) sum += totalNanos[backend.ordinal()][scope.ordinal()];
            return sum;
        }

        public long maxNanos(Backend backend) {
            long max = 0L;
            for (Scope scope : Scope.values()) max = Math.max(max, maxNanos[backend.ordinal()][scope.ordinal()]);
            return max;
        }

        public double averageMicros(Backend backend) {
            long count = totalSamples(backend);
            return count == 0L ? 0.0D : totalNanos(backend) / 1000.0D / (double) count;
        }

        public double maxMicros(Backend backend) {
            return maxNanos(backend) / 1000.0D;
        }

        public double averageMicros(Backend backend, Scope scope) {
            long count = samples(backend, scope);
            return count == 0L ? 0.0D : totalNanos[backend.ordinal()][scope.ordinal()] / 1000.0D / (double) count;
        }
    }
}
