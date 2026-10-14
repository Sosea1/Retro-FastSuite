package com.sosea1.fastsuite112.recipe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PrimitiveIterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class SortedIntUnionTest {
    @Test
    void mergesSortedBucketsAndDeduplicatesAcrossSources() {
        int[][] buckets = {
            {1, 4, 7, 11},
            {2, 4, 8, 11},
            {0, 7, 9}
        };
        int[] fallback = {3, 6, 10};

        assertEquals(
            Arrays.asList(0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11),
            collect(SortedIntUnion.iterator(buckets, buckets.length, fallback))
        );
    }

    @Test
    void handlesRepeatedBucketReferenceWithoutDuplicateOutput() {
        int[] shared = {1, 5, 8};
        int[][] buckets = {shared, shared, {2, 5, 9}};

        assertEquals(
            Arrays.asList(1, 2, 5, 8, 9),
            collect(SortedIntUnion.iterator(buckets, buckets.length, new int[0]))
        );
    }

    @Test
    void independentIteratorsCanBeInterleavedForRecursiveLookupSafety() {
        int[][] buckets = {{1, 3, 5}, {2, 3, 6}};
        int[] fallback = {0, 4};

        PrimitiveIterator.OfInt outer = SortedIntUnion.iterator(buckets, buckets.length, fallback);
        assertEquals(0, outer.nextInt());

        // Simulate a nested crafting lookup while the outer recipe iteration is still active.
        assertEquals(
            Arrays.asList(0, 1, 2, 3, 4, 5, 6),
            collect(SortedIntUnion.iterator(buckets, buckets.length, fallback))
        );

        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), collect(outer));
    }

    @Test
    void manySimultaneouslyLiveIteratorsStayIndependent() {
        int[][] buckets = {{1, 3, 5}, {2, 4, 6}};
        int[] fallback = {0, 7};
        List<PrimitiveIterator.OfInt> live = new ArrayList<PrimitiveIterator.OfInt>();

        // Keep several iterators alive at once to model nested/reentrant lookup consumers.
        for (int i = 0; i < 7; i++) {
            PrimitiveIterator.OfInt iterator = SortedIntUnion.iterator(buckets, buckets.length, fallback);
            assertEquals(0, iterator.nextInt());
            live.add(iterator);
        }

        for (PrimitiveIterator.OfInt iterator : live) {
            assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), collect(iterator));
        }

        // A fresh iterator after those lookups must still start from an independent cursor state.
        assertEquals(
            Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7),
            collect(SortedIntUnion.iterator(buckets, buckets.length, fallback))
        );
    }

    @Test
    void repeatedHasNextDoesNotAdvancePastPrefetchedValue() {
        int[][] buckets = {{1, 3}, {2, 4}};
        PrimitiveIterator.OfInt iterator = SortedIntUnion.iterator(buckets, buckets.length, new int[] {0, 5});

        // The alpha.19 iterator prefetches during hasNext(); repeated probes must remain idempotent.
        for (int expected = 0; expected <= 5; expected++) {
            org.junit.jupiter.api.Assertions.assertTrue(iterator.hasNext());
            org.junit.jupiter.api.Assertions.assertTrue(iterator.hasNext());
            assertEquals(expected, iterator.nextInt());
        }
        assertFalse(iterator.hasNext());
    }


    @Test
    void directFourSourcePathMergesWithoutSourceArrayDescriptor() {
        PrimitiveIterator.OfInt iterator = SortedIntUnion.iterator(
            new int[] {1, 5, 9},
            new int[] {2, 5, 10},
            new int[] {3, 8},
            new int[] {4, 9, 11},
            4,
            new int[] {0, 6, 7, 12});

        assertEquals(
            Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            collect(iterator)
        );
    }

    @Test
    void directPathIsIndependentAcrossRecursiveLookups() {
        PrimitiveIterator.OfInt outer = SortedIntUnion.iterator(
            new int[] {1, 4}, new int[] {2, 5}, new int[] {3, 6}, null, 3, new int[] {0, 7});
        assertEquals(0, outer.nextInt());

        assertEquals(
            Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7),
            collect(SortedIntUnion.iterator(
                new int[] {1, 4}, new int[] {2, 5}, new int[] {3, 6}, null, 3, new int[] {0, 7}))
        );
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), collect(outer));
    }

    @Test
    void heapPathMergesManyMetadataStyleSources() {
        int[][] buckets = {
            {0, 18, 36}, {1, 18, 37}, {2, 19, 38}, {3, 20, 39},
            {4, 21, 40}, {5, 22, 41}, {6, 23, 42}, {7, 24, 43},
            {8, 25, 44}, {9, 26, 45}, {10, 27, 46}, {11, 28, 47}
        };
        int[] fallback = {12, 18, 29, 48};

        assertEquals(
            Arrays.asList(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
                18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
                36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48),
            collect(SortedIntUnion.iterator(buckets, buckets.length, fallback))
        );
    }

    @Test
    void heapScratchIsAlsoIndependentUnderRecursion() {
        int[][] buckets = {
            {0, 30}, {1, 31}, {2, 32}, {3, 33}, {4, 34},
            {5, 35}, {6, 36}, {7, 37}, {8, 38}, {9, 39}
        };
        int[] fallback = {10, 40};

        PrimitiveIterator.OfInt outer = SortedIntUnion.iterator(buckets, buckets.length, fallback);
        assertEquals(0, outer.nextInt());
        PrimitiveIterator.OfInt nested = SortedIntUnion.iterator(buckets, buckets.length, fallback);
        assertEquals(0, nested.nextInt());
        assertEquals(1, outer.nextInt());
        assertEquals(1, nested.nextInt());
        collect(nested);
        collect(outer);
    }

    @Test
    void handlesFallbackOnlyAndEmptySources() {
        int[][] buckets = new int[2][];
        PrimitiveIterator.OfInt iterator = SortedIntUnion.iterator(buckets, 2, new int[] {2, 4});
        assertEquals(Arrays.asList(2, 4), collect(iterator));
        assertFalse(iterator.hasNext());
    }

    private static List<Integer> collect(PrimitiveIterator.OfInt iterator) {
        List<Integer> values = new ArrayList<Integer>();
        while (iterator.hasNext()) values.add(iterator.nextInt());
        return values;
    }
}
