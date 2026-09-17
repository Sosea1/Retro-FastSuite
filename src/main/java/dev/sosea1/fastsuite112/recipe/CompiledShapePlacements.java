package dev.sosea1.fastsuite112.recipe;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Rebuild-time compiled occupancy placements for the overwhelmingly common 2x2 and 3x3 crafting
 * grids. Runtime matching becomes a short scan of pre-shifted masks instead of rebuilding each
 * offset/mirror placement for every candidate recipe.
 *
 * <p>This is only a necessary-condition filter. Ingredient semantics remain in IRecipe.matches().</p>
 */
final class CompiledShapePlacements {
    static final CompiledShapePlacements EMPTY = new CompiledShapePlacements(
        0, null, null, null, null, new long[0], new long[0]);

    private final int size;
    @Nullable private final int[] start2;
    @Nullable private final byte[] count2;
    @Nullable private final int[] start3;
    @Nullable private final byte[] count3;
    private final long[] masks2;
    private final long[] masks3;

    private CompiledShapePlacements(int size,
                                    @Nullable int[] start2,
                                    @Nullable byte[] count2,
                                    @Nullable int[] start3,
                                    @Nullable byte[] count3,
                                    long[] masks2,
                                    long[] masks3) {
        this.size = size;
        this.start2 = start2;
        this.count2 = count2;
        this.start3 = start3;
        this.count3 = count3;
        this.masks2 = masks2;
        this.masks3 = masks3;
    }

    static CompiledShapePlacements compile(@Nullable byte[] shapeWidth,
                                           @Nullable byte[] shapeHeight,
                                           @Nullable long[] shapeMask,
                                           @Nullable long[] mirroredShapeMask,
                                           @Nullable byte[] positionalProbeCount) {
        if (shapeWidth == null || shapeHeight == null || shapeMask == null || mirroredShapeMask == null
            || positionalProbeCount == null) {
            return EMPTY;
        }
        int size = shapeWidth.length;
        if (shapeHeight.length != size || shapeMask.length != size || mirroredShapeMask.length != size
            || positionalProbeCount.length != size) {
            throw new IllegalArgumentException("Mismatched shape constraint columns");
        }
        boolean anyShape = false;
        for (int id = 0; id < size; id++) {
            if ((shapeWidth[id] & 0xff) != 0 && (positionalProbeCount[id] & 0xff) == 0) {
                anyShape = true;
                break;
            }
        }
        if (!anyShape) return EMPTY;

        int[] start2 = new int[size];
        byte[] count2 = new byte[size];
        int[] start3 = new int[size];
        byte[] count3 = new byte[size];
        LongBuilder masks2 = new LongBuilder();
        LongBuilder masks3 = new LongBuilder();

        for (int id = 0; id < size; id++) {
            if ((positionalProbeCount[id] & 0xff) != 0) continue;
            int width = shapeWidth[id] & 0xff;
            int height = shapeHeight[id] & 0xff;
            if (width == 0 || height == 0) continue;

            start2[id] = masks2.size();
            count2[id] = (byte) appendPlacements(
                masks2, 2, 2, width, height, shapeMask[id], mirroredShapeMask[id]);

            start3[id] = masks3.size();
            count3[id] = (byte) appendPlacements(
                masks3, 3, 3, width, height, shapeMask[id], mirroredShapeMask[id]);
        }

        return new CompiledShapePlacements(
            size, start2, count2, start3, count3, masks2.toArray(), masks3.toArray());
    }

    boolean supports(int gridWidth, int gridHeight) {
        if (size == 0) return false;
        return (gridWidth == 2 && gridHeight == 2) || (gridWidth == 3 && gridHeight == 3);
    }

    boolean matches(int id, int gridWidth, int gridHeight, long queryMask) {
        if (id < 0 || id >= size) return false;
        if (gridWidth == 2 && gridHeight == 2) {
            return contains(masks2, start2[id], count2[id] & 0xff, queryMask);
        }
        if (gridWidth == 3 && gridHeight == 3) {
            return contains(masks3, start3[id], count3[id] & 0xff, queryMask);
        }
        throw new IllegalArgumentException("Unsupported compiled grid: " + gridWidth + "x" + gridHeight);
    }

    private static boolean contains(long[] masks, int start, int count, long queryMask) {
        for (int i = start, end = start + count; i < end; i++) {
            if (masks[i] == queryMask) return true;
        }
        return false;
    }

    private static int appendPlacements(LongBuilder output,
                                        int gridWidth,
                                        int gridHeight,
                                        int recipeWidth,
                                        int recipeHeight,
                                        long recipeMask,
                                        long mirroredRecipeMask) {
        if (recipeWidth <= 0 || recipeHeight <= 0 || recipeWidth > gridWidth || recipeHeight > gridHeight) {
            return 0;
        }

        // 3x3 crafting bounds this to at most 18 raw placements. Keep a tiny local array so rebuild
        // can de-duplicate symmetric mirrors without allocating collections per recipe.
        long[] unique = new long[18];
        int uniqueCount = 0;
        for (int yOffset = 0; yOffset <= gridHeight - recipeHeight; yOffset++) {
            for (int xOffset = 0; xOffset <= gridWidth - recipeWidth; xOffset++) {
                long normal = ShapeOccupancy.place(
                    recipeMask, recipeWidth, recipeHeight, gridWidth, xOffset, yOffset);
                uniqueCount = appendUnique(unique, uniqueCount, normal);
                if (mirroredRecipeMask != recipeMask) {
                    long mirrored = ShapeOccupancy.place(
                        mirroredRecipeMask, recipeWidth, recipeHeight, gridWidth, xOffset, yOffset);
                    uniqueCount = appendUnique(unique, uniqueCount, mirrored);
                }
            }
        }

        for (int i = 0; i < uniqueCount; i++) output.add(unique[i]);
        return uniqueCount;
    }

    private static int appendUnique(long[] values, int size, long value) {
        for (int i = 0; i < size; i++) if (values[i] == value) return size;
        values[size] = value;
        return size + 1;
    }

    private static final class LongBuilder {
        private long[] values = new long[32];
        private int size;

        int size() { return size; }

        void add(long value) {
            if (size == values.length) values = Arrays.copyOf(values, values.length << 1);
            values[size++] = value;
        }

        long[] toArray() {
            return size == 0 ? new long[0] : Arrays.copyOf(values, size);
        }
    }
}
