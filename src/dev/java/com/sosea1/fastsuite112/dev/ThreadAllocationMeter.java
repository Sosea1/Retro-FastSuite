package com.sosea1.fastsuite112.dev;

import java.lang.management.ManagementFactory;

/**
 * Development-only current-thread allocation counter used by the recipe benchmark.
 *
 * <p>HotSpot/OpenJDK exposes this through {@code com.sun.management.ThreadMXBean}. The helper is
 * deliberately optional: unsupported JVMs simply report {@code -1} and the benchmark continues
 * with timing/candidate metrics. No reflection is performed inside measured lookups.</p>
 */
final class ThreadAllocationMeter {
    private final com.sun.management.ThreadMXBean bean;
    private final long threadId;
    private final String unavailableReason;

    private ThreadAllocationMeter(com.sun.management.ThreadMXBean bean,
                                  long threadId,
                                  String unavailableReason) {
        this.bean = bean;
        this.threadId = threadId;
        this.unavailableReason = unavailableReason;
    }

    static ThreadAllocationMeter createForCurrentThread() {
        java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
        if (!(base instanceof com.sun.management.ThreadMXBean)) {
            return unavailable("JVM ThreadMXBean does not expose allocated-byte counters");
        }

        com.sun.management.ThreadMXBean extended = (com.sun.management.ThreadMXBean) base;
        try {
            if (!extended.isThreadAllocatedMemorySupported()) {
                return unavailable("thread allocated memory is not supported");
            }
            if (!extended.isThreadAllocatedMemoryEnabled()) {
                extended.setThreadAllocatedMemoryEnabled(true);
            }
            if (!extended.isThreadAllocatedMemoryEnabled()) {
                return unavailable("thread allocated memory could not be enabled");
            }
            return new ThreadAllocationMeter(extended, Thread.currentThread().getId(), null);
        } catch (SecurityException | UnsupportedOperationException failure) {
            return unavailable(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static ThreadAllocationMeter unavailable(String reason) {
        return new ThreadAllocationMeter(null, -1L, reason == null ? "unknown" : reason);
    }

    boolean isAvailable() {
        return bean != null;
    }

    String getUnavailableReason() {
        return unavailableReason;
    }

    long readAllocatedBytes() {
        if (bean == null || Thread.currentThread().getId() != threadId) {
            return -1L;
        }
        long value = bean.getThreadAllocatedBytes(threadId);
        return value < 0L ? -1L : value;
    }
}
