package dev.sosea1.fastsuite112.recipe;

/**
 * Two-way 16-bucket saturating count sketch used as a necessary-condition accelerator.
 *
 * <p>Each 64-bit lane stores sixteen unsigned 4-bit counters. Collisions can only increase an
 * estimate, so using the minimum of two independent lanes can create false positives but never a
 * false negative for requirements capped at 15. That property makes the sketch safe for pruning:
 * it may leave an impossible recipe for IRecipe.matches(), but it cannot hide a valid recipe.</p>
 */
final class SaturatingCountSketch {
    static final int MAX_COUNT = 15;

    private SaturatingCountSketch() {}

    static long increment(long sketch, int bucket) {
        int shift = (bucket & 15) << 2;
        long value = (sketch >>> shift) & 0xFL;
        if (value == MAX_COUNT) return sketch;
        return sketch + (1L << shift);
    }

    static int estimate(long sketchA, int bucketA, long sketchB, int bucketB) {
        int countA = (int) ((sketchA >>> ((bucketA & 15) << 2)) & 0xFL);
        int countB = (int) ((sketchB >>> ((bucketB & 15) << 2)) & 0xFL);
        return Math.min(countA, countB);
    }

    static int capRequirement(int required) {
        if (required <= 0) return 0;
        return Math.min(MAX_COUNT, required);
    }
}
