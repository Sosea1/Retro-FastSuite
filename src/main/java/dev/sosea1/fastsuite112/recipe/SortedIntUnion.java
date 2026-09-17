package dev.sosea1.fastsuite112.recipe;

import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

/**
 * Allocation-light ordered union for already-sorted primitive recipe-id buckets.
 *
 * <p>Small source sets use a linear k-way merge because a heap costs more than a handful of direct
 * array reads. Metadata-aware indexing can create more sources (wildcard + exact-meta per Item), so
 * larger unions switch to a primitive min-heap. Both paths advance every source exposing the chosen
 * id and therefore deduplicate without HashSet/boxing/materializing the union.</p>
 */
final class SortedIntUnion {
    private static final int LINEAR_SOURCE_LIMIT = 8;

    private SortedIntUnion() {}

    static PrimitiveIterator.OfInt iterator(int[][] sources, int sourceCount, int[] extraSortedSource) {
        if (sources == null) throw new NullPointerException("sources");
        if (sourceCount < 0 || sourceCount > sources.length) {
            throw new IllegalArgumentException("sourceCount out of bounds: " + sourceCount);
        }
        final int[] extra = extraSortedSource == null ? new int[0] : extraSortedSource;
        int totalSources = sourceCount + (extra.length == 0 ? 0 : 1);
        return totalSources <= LINEAR_SOURCE_LIMIT
            ? linearIterator(sources, sourceCount, extra)
            : heapIterator(sources, sourceCount, extra);
    }

    private static PrimitiveIterator.OfInt linearIterator(final int[][] sources,
                                                          final int sourceCount,
                                                          final int[] extra) {
        return new PrimitiveIterator.OfInt() {
            private final int[] positions = new int[sourceCount];
            private int extraPosition;
            private int nextValue;
            private boolean prepared;
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

                int minimum = Integer.MAX_VALUE;
                boolean found = false;
                if (extraPosition < extra.length) {
                    minimum = extra[extraPosition];
                    found = true;
                }

                for (int i = 0; i < sourceCount; i++) {
                    int[] source = sources[i];
                    if (source == null || positions[i] >= source.length) continue;
                    int value = source[positions[i]];
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
                    while (positions[i] < source.length && source[positions[i]] == minimum) positions[i]++;
                }
                while (extraPosition < extra.length && extra[extraPosition] == minimum) extraPosition++;

                nextValue = minimum;
                prepared = true;
            }

            @Override public Integer next() { return nextInt(); }
            @Override public void remove() { throw new UnsupportedOperationException("immutable sorted int union"); }
        };
    }

    private static PrimitiveIterator.OfInt heapIterator(final int[][] sources,
                                                        final int sourceCount,
                                                        final int[] extra) {
        return new PrimitiveIterator.OfInt() {
            // source id [0, sourceCount) addresses indexed buckets; sourceCount addresses fallback.
            private final int[] positions = new int[sourceCount + 1];
            private final int[] heap = new int[sourceCount + 1];
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

                int minimum = headValue(heap[0]);
                do {
                    int sourceId = heap[0];
                    advancePast(sourceId, minimum);
                    if (hasValue(sourceId)) {
                        siftDown(0);
                    } else {
                        heap[0] = heap[--heapSize];
                        if (heapSize != 0) siftDown(0);
                    }
                } while (heapSize != 0 && headValue(heap[0]) == minimum);

                nextValue = minimum;
                prepared = true;
            }

            private void initialize() {
                initialized = true;
                for (int sourceId = 0; sourceId < sourceCount; sourceId++) {
                    int[] source = sources[sourceId];
                    if (source != null && source.length != 0) heap[heapSize++] = sourceId;
                }
                if (extra.length != 0) heap[heapSize++] = sourceCount;
                for (int i = (heapSize >>> 1) - 1; i >= 0; i--) siftDown(i);
            }

            private boolean hasValue(int sourceId) {
                int[] source = source(sourceId);
                return positions[sourceId] < source.length;
            }

            private int headValue(int sourceId) {
                int[] source = source(sourceId);
                return source[positions[sourceId]];
            }

            private void advancePast(int sourceId, int value) {
                int[] source = source(sourceId);
                int position = positions[sourceId];
                while (position < source.length && source[position] == value) position++;
                positions[sourceId] = position;
            }

            private int[] source(int sourceId) {
                return sourceId == sourceCount ? extra : sources[sourceId];
            }

            private void siftDown(int index) {
                int sourceId = heap[index];
                int value = headValue(sourceId);
                int half = heapSize >>> 1;
                while (index < half) {
                    int left = (index << 1) + 1;
                    int right = left + 1;
                    int child = left;
                    int childSource = heap[left];
                    int childValue = headValue(childSource);
                    if (right < heapSize) {
                        int rightSource = heap[right];
                        int rightValue = headValue(rightSource);
                        if (rightValue < childValue) {
                            child = right;
                            childSource = rightSource;
                            childValue = rightValue;
                        }
                    }
                    if (value <= childValue) break;
                    heap[index] = childSource;
                    index = child;
                }
                heap[index] = sourceId;
            }

            @Override public Integer next() { return nextInt(); }
            @Override public void remove() { throw new UnsupportedOperationException("immutable sorted int union"); }
        };
    }
}
