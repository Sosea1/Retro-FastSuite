package dev.sosea1.fastsuite112.recipe;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Rebuild-time compiled positional reject probes for trusted shaped recipes on 2x2/3x3 grids.
 *
 * <p>Each placement stores the exact occupancy mask plus up to three compact fingerprints for
 * selected non-empty ingredient positions. Runtime evaluation only rejects a placement when one of
 * those fingerprints is definitely absent at the required slot. Fingerprint collisions therefore
 * produce false positives (an extra IRecipe.matches call), never false negatives.</p>
 */
final class CompiledPositionalProbes {
    static final CompiledPositionalProbes EMPTY = new CompiledPositionalProbes(
        0, null, null, null, null,
        PlacementTable.EMPTY, PlacementTable.EMPTY);

    private final int size;
    @Nullable private final int[] start2;
    @Nullable private final byte[] count2;
    @Nullable private final int[] start3;
    @Nullable private final byte[] count3;
    private final PlacementTable table2;
    private final PlacementTable table3;

    private CompiledPositionalProbes(int size,
                                     @Nullable int[] start2,
                                     @Nullable byte[] count2,
                                     @Nullable int[] start3,
                                     @Nullable byte[] count3,
                                     PlacementTable table2,
                                     PlacementTable table3) {
        this.size = size;
        this.start2 = start2;
        this.count2 = count2;
        this.start3 = start3;
        this.count3 = count3;
        this.table2 = table2;
        this.table3 = table3;
    }

    static CompiledPositionalProbes compile(@Nullable byte[] shapeWidth,
                                            @Nullable byte[] shapeHeight,
                                            @Nullable long[] shapeMask,
                                            @Nullable long[] mirroredShapeMask,
                                            @Nullable byte[] probeCount,
                                            @Nullable byte[] probeIndex1,
                                            @Nullable byte[] probeIndex2,
                                            @Nullable byte[] probeIndex3,
                                            @Nullable int[] probeSignature1,
                                            @Nullable int[] probeSignature2,
                                            @Nullable int[] probeSignature3) {
        if (shapeWidth == null || shapeHeight == null || shapeMask == null || mirroredShapeMask == null
            || probeCount == null || probeIndex1 == null || probeIndex2 == null || probeIndex3 == null
            || probeSignature1 == null || probeSignature2 == null || probeSignature3 == null) {
            return EMPTY;
        }
        int size = shapeWidth.length;
        if (shapeHeight.length != size || shapeMask.length != size || mirroredShapeMask.length != size
            || probeCount.length != size || probeIndex1.length != size || probeIndex2.length != size
            || probeIndex3.length != size || probeSignature1.length != size || probeSignature2.length != size
            || probeSignature3.length != size) {
            throw new IllegalArgumentException("Mismatched positional-probe columns");
        }

        boolean any = false;
        for (byte count : probeCount) {
            if ((count & 0xff) != 0) {
                any = true;
                break;
            }
        }
        if (!any) return EMPTY;

        int[] start2 = new int[size];
        byte[] count2 = new byte[size];
        int[] start3 = new int[size];
        byte[] count3 = new byte[size];
        PlacementBuilder placements2 = new PlacementBuilder();
        PlacementBuilder placements3 = new PlacementBuilder();

        for (int id = 0; id < size; id++) {
            int probes = probeCount[id] & 0xff;
            int width = shapeWidth[id] & 0xff;
            int height = shapeHeight[id] & 0xff;
            if (probes == 0 || width == 0 || height == 0) continue;

            start2[id] = placements2.size();
            count2[id] = (byte) appendPlacements(
                placements2, 2, 2, width, height, shapeMask[id], mirroredShapeMask[id], probes,
                probeIndex1[id] & 0xff, probeIndex2[id] & 0xff, probeIndex3[id] & 0xff,
                probeSignature1[id], probeSignature2[id], probeSignature3[id]);

            start3[id] = placements3.size();
            count3[id] = (byte) appendPlacements(
                placements3, 3, 3, width, height, shapeMask[id], mirroredShapeMask[id], probes,
                probeIndex1[id] & 0xff, probeIndex2[id] & 0xff, probeIndex3[id] & 0xff,
                probeSignature1[id], probeSignature2[id], probeSignature3[id]);
        }

        return new CompiledPositionalProbes(
            size, start2, count2, start3, count3, placements2.freeze(), placements3.freeze());
    }

    boolean hasAny() {
        return size != 0;
    }

    boolean supports(int gridWidth, int gridHeight) {
        if (size == 0) return false;
        return (gridWidth == 2 && gridHeight == 2) || (gridWidth == 3 && gridHeight == 3);
    }

    boolean matches(int id,
                    int gridWidth,
                    int gridHeight,
                    long queryOccupancyMask,
                    long querySlotTokens0,
                    long querySlotTokens1,
                    int querySlotToken8) {
        if (id < 0 || id >= size) return false;
        if (gridWidth == 2 && gridHeight == 2) {
            return table2.matches(start2[id], count2[id] & 0xff, queryOccupancyMask,
                querySlotTokens0, querySlotTokens1, querySlotToken8);
        }
        if (gridWidth == 3 && gridHeight == 3) {
            return table3.matches(start3[id], count3[id] & 0xff, queryOccupancyMask,
                querySlotTokens0, querySlotTokens1, querySlotToken8);
        }
        throw new IllegalArgumentException("Unsupported positional grid: " + gridWidth + "x" + gridHeight);
    }

    private static int appendPlacements(PlacementBuilder output,
                                        int gridWidth,
                                        int gridHeight,
                                        int recipeWidth,
                                        int recipeHeight,
                                        long recipeMask,
                                        long mirroredRecipeMask,
                                        int probeCount,
                                        int probeIndex1,
                                        int probeIndex2,
                                        int probeIndex3,
                                        int probeSignature1,
                                        int probeSignature2,
                                        int probeSignature3) {
        if (recipeWidth <= 0 || recipeHeight <= 0 || recipeWidth > gridWidth || recipeHeight > gridHeight) {
            return 0;
        }

        int start = output.size();
        for (int yOffset = 0; yOffset <= gridHeight - recipeHeight; yOffset++) {
            for (int xOffset = 0; xOffset <= gridWidth - recipeWidth; xOffset++) {
                long normalMask = ShapeOccupancy.place(
                    recipeMask, recipeWidth, recipeHeight, gridWidth, xOffset, yOffset);
                output.addUnique(
                    start,
                    normalMask,
                    packProbeSlots(gridWidth, recipeWidth, xOffset, yOffset, false,
                        probeCount, probeIndex1, probeIndex2, probeIndex3),
                    probeSignature1, probeSignature2, probeSignature3);

                // The occupancy may be symmetric even when the ingredient arrangement is not, so a
                // mirrored positional placement must still be emitted. addUnique removes it only when
                // both occupancy and all selected probe slots/signatures are identical.
                long mirroredMask = ShapeOccupancy.place(
                    mirroredRecipeMask, recipeWidth, recipeHeight, gridWidth, xOffset, yOffset);
                output.addUnique(
                    start,
                    mirroredMask,
                    packProbeSlots(gridWidth, recipeWidth, xOffset, yOffset, true,
                        probeCount, probeIndex1, probeIndex2, probeIndex3),
                    probeSignature1, probeSignature2, probeSignature3);
            }
        }
        return output.size() - start;
    }

    private static short packProbeSlots(int gridWidth,
                                        int recipeWidth,
                                        int xOffset,
                                        int yOffset,
                                        boolean mirror,
                                        int probeCount,
                                        int probeIndex1,
                                        int probeIndex2,
                                        int probeIndex3) {
        int packed = 0x0fff;
        if (probeCount > 0) packed = setPackedSlot(packed, 0,
            gridSlot(gridWidth, recipeWidth, xOffset, yOffset, mirror, probeIndex1));
        if (probeCount > 1) packed = setPackedSlot(packed, 1,
            gridSlot(gridWidth, recipeWidth, xOffset, yOffset, mirror, probeIndex2));
        if (probeCount > 2) packed = setPackedSlot(packed, 2,
            gridSlot(gridWidth, recipeWidth, xOffset, yOffset, mirror, probeIndex3));
        return (short) packed;
    }

    private static int setPackedSlot(int packed, int probe, int slot) {
        int shift = probe << 2;
        return (packed & ~(0xf << shift)) | ((slot & 0xf) << shift);
    }

    private static int gridSlot(int gridWidth,
                                int recipeWidth,
                                int xOffset,
                                int yOffset,
                                boolean mirror,
                                int recipeIndex) {
        int x = recipeIndex % recipeWidth;
        int y = recipeIndex / recipeWidth;
        if (mirror) x = recipeWidth - 1 - x;
        return (yOffset + y) * gridWidth + xOffset + x;
    }

    private static boolean probeMatches(int allowedSignature, int queryToken) {
        int allowedItemA = allowedSignature & 0xf;
        int allowedItemB = (allowedSignature >>> 4) & 0xf;
        if (allowedItemA != 0
            && (queryToken & allowedItemA) != 0
            && (((queryToken >>> 4) & allowedItemB) != 0)) {
            return true;
        }

        int allowedVariantA = (allowedSignature >>> 8) & 0xf;
        int allowedVariantB = (allowedSignature >>> 12) & 0xf;
        return allowedVariantA != 0
            && (((queryToken >>> 8) & allowedVariantA) != 0)
            && (((queryToken >>> 12) & allowedVariantB) != 0);
    }

    private static int tokenAt(long tokens0, long tokens1, int token8, int slot) {
        if (slot < 4) return (int) ((tokens0 >>> (slot << 4)) & 0xffffL);
        if (slot < 8) return (int) ((tokens1 >>> ((slot - 4) << 4)) & 0xffffL);
        return slot == 8 ? token8 & 0xffff : 0;
    }

    private static final class PlacementTable {
        private static final PlacementTable EMPTY = new PlacementTable(
            new long[0], new short[0], new int[0], new int[0], new int[0]);

        private final long[] occupancyMasks;
        private final short[] packedSlots;
        private final int[] signature1;
        private final int[] signature2;
        private final int[] signature3;

        private PlacementTable(long[] occupancyMasks,
                               short[] packedSlots,
                               int[] signature1,
                               int[] signature2,
                               int[] signature3) {
            this.occupancyMasks = occupancyMasks;
            this.packedSlots = packedSlots;
            this.signature1 = signature1;
            this.signature2 = signature2;
            this.signature3 = signature3;
        }

        private boolean matches(int start,
                                int count,
                                long queryOccupancyMask,
                                long querySlotTokens0,
                                long querySlotTokens1,
                                int querySlotToken8) {
            for (int i = start, end = start + count; i < end; i++) {
                if (occupancyMasks[i] != queryOccupancyMask) continue;
                int slots = packedSlots[i] & 0xffff;

                int sig1 = signature1[i];
                if (sig1 != 0 && !probeMatches(sig1,
                    tokenAt(querySlotTokens0, querySlotTokens1, querySlotToken8, slots & 0xf))) continue;

                int sig2 = signature2[i];
                if (sig2 != 0 && !probeMatches(sig2,
                    tokenAt(querySlotTokens0, querySlotTokens1, querySlotToken8, (slots >>> 4) & 0xf))) continue;

                int sig3 = signature3[i];
                if (sig3 != 0 && !probeMatches(sig3,
                    tokenAt(querySlotTokens0, querySlotTokens1, querySlotToken8, (slots >>> 8) & 0xf))) continue;

                return true;
            }
            return false;
        }
    }

    private static final class PlacementBuilder {
        private long[] occupancyMasks = new long[64];
        private short[] packedSlots = new short[64];
        private int[] signature1 = new int[64];
        private int[] signature2 = new int[64];
        private int[] signature3 = new int[64];
        private int size;

        int size() {
            return size;
        }

        void addUnique(int recipeStart, long occupancyMask, short slots, int sig1, int sig2, int sig3) {
            // De-duplicate only within the current recipe. Looking into the previous recipe would
            // corrupt start/count ownership when two recipes happen to share an identical placement.
            for (int i = size - 1; i >= recipeStart; i--) {
                if (occupancyMasks[i] == occupancyMask && packedSlots[i] == slots
                    && signature1[i] == sig1 && signature2[i] == sig2 && signature3[i] == sig3) {
                    return;
                }
            }
            ensureCapacity(size + 1);
            occupancyMasks[size] = occupancyMask;
            packedSlots[size] = slots;
            signature1[size] = sig1;
            signature2[size] = sig2;
            signature3[size] = sig3;
            size++;
        }

        PlacementTable freeze() {
            if (size == 0) return PlacementTable.EMPTY;
            return new PlacementTable(
                Arrays.copyOf(occupancyMasks, size),
                Arrays.copyOf(packedSlots, size),
                Arrays.copyOf(signature1, size),
                Arrays.copyOf(signature2, size),
                Arrays.copyOf(signature3, size));
        }

        private void ensureCapacity(int needed) {
            if (needed <= occupancyMasks.length) return;
            int next = Math.max(needed, occupancyMasks.length + (occupancyMasks.length >> 1));
            occupancyMasks = Arrays.copyOf(occupancyMasks, next);
            packedSlots = Arrays.copyOf(packedSlots, next);
            signature1 = Arrays.copyOf(signature1, next);
            signature2 = Arrays.copyOf(signature2, next);
            signature3 = Arrays.copyOf(signature3, next);
        }
    }
}
