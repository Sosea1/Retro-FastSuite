package com.sosea1.fastsuite112.recipe;

import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

/**
 * Allocation-light ordered union for already-sorted primitive recipe-id buckets.
 *
 * <p>Small source sets use a specialized linear k-way merge whose eight cursor positions live as
 * primitive fields on the iterator itself. This avoids the per-lookup {@code int[]} cursor array
 * without introducing shared/thread-local scratch that could be corrupted by recursive recipe
 * lookups or by callers that short-circuit and retain an iterator. Metadata-aware queries with more
 * sources use a primitive min-heap; those uncommon large unions still own private arrays so every
 * iterator remains completely independent.</p>
 */
final class SortedIntUnion {
    private static final int LINEAR_SOURCE_LIMIT = 8;
    private static final int[] EMPTY = new int[0];

    private SortedIntUnion() {}

    static PrimitiveIterator.OfInt iterator(int[][] sources, int sourceCount, int[] extraSortedSource) {
        if (sources == null) throw new NullPointerException("sources");
        if (sourceCount < 0 || sourceCount > sources.length) {
            throw new IllegalArgumentException("sourceCount out of bounds: " + sourceCount);
        }
        final int[] extra = extraSortedSource == null ? EMPTY : extraSortedSource;
        int totalSources = sourceCount + (extra.length == 0 ? 0 : 1);
        return totalSources <= LINEAR_SOURCE_LIMIT
            ? new LinearIterator(sources, sourceCount, extra)
            : heapIterator(sources, sourceCount, extra);
    }


    /**
     * Array-free source descriptor for the common multi-bucket crafting path. RecipeIndex keeps the
     * first four distinct immutable bucket arrays in local/direct fields and only materializes an
     * {@code int[][]} when a query actually needs a fifth source. Keeping this overload here also
     * means the returned iterator owns every cursor and is safe across recursive/short-circuited
     * crafting lookups.
     */
    static PrimitiveIterator.OfInt iterator(int[] source0,
                                            int[] source1,
                                            int[] source2,
                                            int[] source3,
                                            int sourceCount,
                                            int[] extraSortedSource) {
        if (sourceCount < 0 || sourceCount > 4) {
            throw new IllegalArgumentException("direct sourceCount out of bounds: " + sourceCount);
        }
        final int[] extra = extraSortedSource == null ? EMPTY : extraSortedSource;
        return new DirectLinearIterator(source0, source1, source2, source3, sourceCount, extra);
    }

    private static final class DirectLinearIterator implements PrimitiveIterator.OfInt {
        private final int[] source0;
        private final int[] source1;
        private final int[] source2;
        private final int[] source3;
        private final int sourceCount;
        private final int[] extra;
        private int p0, p1, p2, p3;
        private int extraPosition;
        private int nextValue;
        private boolean prepared;
        private boolean exhausted;

        private DirectLinearIterator(int[] source0, int[] source1, int[] source2, int[] source3,
                                     int sourceCount, int[] extra) {
            this.source0 = source0;
            this.source1 = source1;
            this.source2 = source2;
            this.source3 = source3;
            this.sourceCount = sourceCount;
            this.extra = extra;
        }

        @Override
        public boolean hasNext() {
            prepare();
            return !exhausted;
        }

        @Override
        public int nextInt() {
            prepare();
            if (exhausted) throw new NoSuchElementException();
            int result = nextValue;
            prepared = false;
            return result;
        }

        private void prepare() {
            if (prepared || exhausted) return;

            int minimum = Integer.MAX_VALUE;
            boolean found = false;
            if (extraPosition < extra.length) {
                minimum = extra[extraPosition];
                found = true;
            }

            for (int i = 0; i < sourceCount; i++) {
                int[] source = source(i);
                int position = position(i);
                if (source == null || position >= source.length) continue;
                int value = source[position];
                if (!found || value < minimum) {
                    minimum = value;
                    found = true;
                }
            }

            if (!found) {
                exhausted = true;
                return;
            }

            for (int i = 0; i < sourceCount; i++) {
                int[] source = source(i);
                if (source == null) continue;
                int position = position(i);
                while (position < source.length && source[position] == minimum) position++;
                setPosition(i, position);
            }
            while (extraPosition < extra.length && extra[extraPosition] == minimum) extraPosition++;

            nextValue = minimum;
            prepared = true;
        }

        private int[] source(int index) {
            switch (index) {
                case 0: return source0;
                case 1: return source1;
                case 2: return source2;
                case 3: return source3;
                default: throw new IllegalArgumentException("direct source out of range: " + index);
            }
        }

        private int position(int source) {
            switch (source) {
                case 0: return p0;
                case 1: return p1;
                case 2: return p2;
                case 3: return p3;
                default: throw new IllegalArgumentException("direct source out of range: " + source);
            }
        }

        private void setPosition(int source, int value) {
            switch (source) {
                case 0: p0 = value; return;
                case 1: p1 = value; return;
                case 2: p2 = value; return;
                case 3: p3 = value; return;
                default: throw new IllegalArgumentException("direct source out of range: " + source);
            }
        }

        @Override public Integer next() { return nextInt(); }
        @Override public void remove() { throw new UnsupportedOperationException("immutable sorted int union"); }
    }

    /**
     * Cursor-array-free common path. The source count is bounded by {@link #LINEAR_SOURCE_LIMIT}, so
     * eight primitive fields are cheaper and safer than pooling mutable scratch between iterators.
     */
    private static final class LinearIterator implements PrimitiveIterator.OfInt {
        private final int[][] sources;
        private final int sourceCount;
        private final int[] extra;
        private int p0, p1, p2, p3, p4, p5, p6, p7;
        private int extraPosition;
        private int nextValue;
        private boolean prepared;
        private boolean exhausted;

        private LinearIterator(int[][] sources, int sourceCount, int[] extra) {
            this.sources = sources;
            this.sourceCount = sourceCount;
            this.extra = extra;
        }

        @Override
        public boolean hasNext() {
            prepare();
            return !exhausted;
        }

        @Override
        public int nextInt() {
            prepare();
            if (exhausted) throw new NoSuchElementException();
            int result = nextValue;
            prepared = false;
            return result;
        }

        private void prepare() {
            if (prepared || exhausted) return;

            int minimum = Integer.MAX_VALUE;
            boolean found = false;
            if (extraPosition < extra.length) {
                minimum = extra[extraPosition];
                found = true;
            }

            for (int i = 0; i < sourceCount; i++) {
                int[] source = sources[i];
                int position = position(i);
                if (source == null || position >= source.length) continue;
                int value = source[position];
                if (!found || value < minimum) {
                    minimum = value;
                    found = true;
                }
            }

            if (!found) {
                exhausted = true;
                return;
            }

            for (int i = 0; i < sourceCount; i++) {
                int[] source = sources[i];
                if (source == null) continue;
                int position = position(i);
                while (position < source.length && source[position] == minimum) position++;
                setPosition(i, position);
            }
            while (extraPosition < extra.length && extra[extraPosition] == minimum) extraPosition++;

            nextValue = minimum;
            prepared = true;
        }

        private int position(int source) {
            switch (source) {
                case 0: return p0;
                case 1: return p1;
                case 2: return p2;
                case 3: return p3;
                case 4: return p4;
                case 5: return p5;
                case 6: return p6;
                case 7: return p7;
                default: throw new IllegalArgumentException("linear source out of range: " + source);
            }
        }

        private void setPosition(int source, int value) {
            switch (source) {
                case 0: p0 = value; return;
                case 1: p1 = value; return;
                case 2: p2 = value; return;
                case 3: p3 = value; return;
                case 4: p4 = value; return;
                case 5: p5 = value; return;
                case 6: p6 = value; return;
                case 7: p7 = value; return;
                default: throw new IllegalArgumentException("linear source out of range: " + source);
            }
        }

        @Override public Integer next() { return nextInt(); }
        @Override public void remove() { throw new UnsupportedOperationException("immutable sorted int union"); }
    }

    private static PrimitiveIterator.OfInt heapIterator(final int[][] sources,
                                                        final int sourceCount,
                                                        final int[] extra) {
        return new PrimitiveIterator.OfInt() {
            // Large unions are rare. Keep their scratch private: an IRecipe.matches() call may
            // recursively start another lookup before the outer iterator is exhausted.
            private final int sourceSlots = sourceCount + 1;
            // One private work array instead of separate cursor + heap arrays. Cursors occupy
            // [0, sourceSlots), heap source ids occupy [sourceSlots, sourceSlots * 2).
            private final int[] work = new int[sourceSlots << 1];
            private int heapSize;
            private int nextValue;
            private boolean prepared;
            private boolean initialized;
            private boolean exhausted;

            @Override
            public boolean hasNext() {
                prepare();
                return !exhausted;
            }

            @Override
            public int nextInt() {
                prepare();
                if (exhausted) throw new NoSuchElementException();
                int result = nextValue;
                prepared = false;
                return result;
            }

            private void prepare() {
                if (prepared || exhausted) return;
                if (!initialized) initialize();
                if (heapSize == 0) {
                    exhausted = true;
                    return;
                }

                int minimum = headValue(heapAt(0));
                do {
                    int sourceId = heapAt(0);
                    advancePast(sourceId, minimum);
                    if (hasValue(sourceId)) {
                        siftDown(0);
                    } else {
                        setHeap(0, heapAt(--heapSize));
                        if (heapSize != 0) siftDown(0);
                    }
                } while (heapSize != 0 && headValue(heapAt(0)) == minimum);

                nextValue = minimum;
                prepared = true;
            }

            private void initialize() {
                initialized = true;
                for (int sourceId = 0; sourceId < sourceCount; sourceId++) {
                    int[] source = sources[sourceId];
                    if (source != null && source.length != 0) setHeap(heapSize++, sourceId);
                }
                if (extra.length != 0) setHeap(heapSize++, sourceCount);
                for (int i = (heapSize >>> 1) - 1; i >= 0; i--) siftDown(i);
            }

            private boolean hasValue(int sourceId) {
                int[] source = source(sourceId);
                return position(sourceId) < source.length;
            }

            private int headValue(int sourceId) {
                int[] source = source(sourceId);
                return source[position(sourceId)];
            }

            private void advancePast(int sourceId, int value) {
                int[] source = source(sourceId);
                int position = position(sourceId);
                while (position < source.length && source[position] == value) position++;
                work[sourceId] = position;
            }

            private int[] source(int sourceId) {
                return sourceId == sourceCount ? extra : sources[sourceId];
            }

            private int position(int sourceId) {
                return work[sourceId];
            }

            private int heapAt(int index) {
                return work[sourceSlots + index];
            }

            private void setHeap(int index, int sourceId) {
                work[sourceSlots + index] = sourceId;
            }

            private void siftDown(int index) {
                int sourceId = heapAt(index);
                int value = headValue(sourceId);
                int half = heapSize >>> 1;
                while (index < half) {
                    int left = (index << 1) + 1;
                    int right = left + 1;
                    int child = left;
                    int childSource = heapAt(left);
                    int childValue = headValue(childSource);
                    if (right < heapSize) {
                        int rightSource = heapAt(right);
                        int rightValue = headValue(rightSource);
                        if (rightValue < childValue) {
                            child = right;
                            childSource = rightSource;
                            childValue = rightValue;
                        }
                    }
                    if (value <= childValue) break;
                    setHeap(index, childSource);
                    index = child;
                }
                setHeap(index, sourceId);
            }

            @Override public Integer next() { return nextInt(); }
            @Override public void remove() { throw new UnsupportedOperationException("immutable sorted int union"); }
        };
    }
}
