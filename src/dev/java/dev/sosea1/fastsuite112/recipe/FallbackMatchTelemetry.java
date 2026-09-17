package dev.sosea1.fastsuite112.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Development-only sampled runtime cost attribution for ordered fallback recipes. */
public final class FallbackMatchTelemetry {
    public static final FallbackMatchTelemetry INSTANCE = new FallbackMatchTelemetry(64);

    private final int sampleEvery;
    private final ConcurrentHashMap<String, MutableGroup> groups =
        new ConcurrentHashMap<String, MutableGroup>();
    private final ThreadLocal<Long> sequence = new ThreadLocal<Long>() {
        @Override protected Long initialValue() { return 0L; }
    };
    private volatile boolean enabled;

    FallbackMatchTelemetry(int sampleEvery) {
        if (sampleEvery < 1) throw new IllegalArgumentException("sampleEvery");
        this.sampleEvery = sampleEvery;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean shouldSample() {
        if (!enabled) return false;
        long next = sequence.get() + 1L;
        sequence.set(next);
        return next % sampleEvery == 0L;
    }

    public void record(String recipeClass, long elapsedNanos, boolean failed) {
        if (!enabled || recipeClass == null) return;
        MutableGroup group = groups.get(recipeClass);
        if (group == null) {
            MutableGroup fresh = new MutableGroup();
            MutableGroup raced = groups.putIfAbsent(recipeClass, fresh);
            group = raced == null ? fresh : raced;
        }
        long elapsed = Math.max(0L, elapsedNanos);
        group.calls.increment();
        group.totalNanos.add(elapsed);
        updateMax(group.maxNanos, elapsed);
        if (failed) group.failures.increment();
    }

    public void reset() {
        groups.clear();
        sequence.set(0L);
    }

    public Snapshot snapshot() {
        List<Group> copy = new ArrayList<Group>(groups.size());
        for (java.util.Map.Entry<String, MutableGroup> entry : groups.entrySet()) {
            MutableGroup value = entry.getValue();
            copy.add(new Group(entry.getKey(), value.calls.sum(), value.totalNanos.sum(),
                value.maxNanos.get(), value.failures.sum()));
        }
        Collections.sort(copy, GROUP_ORDER);
        return new Snapshot(enabled, sampleEvery, Collections.unmodifiableList(copy));
    }

    private static void updateMax(AtomicLong target, long candidate) {
        long current = target.get();
        while (candidate > current && !target.compareAndSet(current, candidate)) current = target.get();
    }

    private static final Comparator<Group> GROUP_ORDER = new Comparator<Group>() {
        @Override public int compare(Group left, Group right) {
            int time = Long.compare(right.totalNanos, left.totalNanos);
            if (time != 0) return time;
            int calls = Long.compare(right.calls, left.calls);
            if (calls != 0) return calls;
            return left.recipeClass.compareTo(right.recipeClass);
        }
    };

    private static final class MutableGroup {
        private final LongAdder calls = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();
        private final LongAdder failures = new LongAdder();
    }

    public static final class Snapshot {
        public final boolean enabled;
        public final int sampleEvery;
        public final List<Group> groups;

        private Snapshot(boolean enabled, int sampleEvery, List<Group> groups) {
            this.enabled = enabled;
            this.sampleEvery = sampleEvery;
            this.groups = groups;
        }
    }

    public static final class Group {
        public final String recipeClass;
        public final long calls;
        public final long totalNanos;
        public final long maxNanos;
        public final long failures;

        private Group(String recipeClass, long calls, long totalNanos, long maxNanos, long failures) {
            this.recipeClass = recipeClass;
            this.calls = calls;
            this.totalNanos = totalNanos;
            this.maxNanos = maxNanos;
            this.failures = failures;
        }

        public double averageMicros() {
            return calls == 0L ? 0.0D : totalNanos / 1000.0D / (double) calls;
        }

        public double totalMillis() {
            return totalNanos / 1_000_000.0D;
        }

        public double maxMicros() {
            return maxNanos / 1000.0D;
        }
    }
}
