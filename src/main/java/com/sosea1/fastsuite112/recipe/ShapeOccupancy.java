package com.sosea1.fastsuite112.recipe;

/**
 * Tiny allocation-free shaped-recipe occupancy matcher.
 *
 * <p>Only geometry is considered here: an occupied crafting slot is one bit. Ingredient semantics
 * remain in IRecipe.matches(). A recipe reaches this helper only when RecipeIndex has already proven
 * that every non-empty ingredient is structurally safe to treat as requiring an occupied slot.</p>
 */
final class ShapeOccupancy {
    private ShapeOccupancy() {}

    static long mirror(long mask, int width, int height) {
        long mirrored = 0L;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sourceBit = y * width + x;
                if ((mask & (1L << sourceBit)) == 0L) continue;
                int targetBit = y * width + (width - 1 - x);
                mirrored |= 1L << targetBit;
            }
        }
        return mirrored;
    }

    static boolean matches(long queryMask,
                           int gridWidth,
                           int gridHeight,
                           int recipeWidth,
                           int recipeHeight,
                           long recipeMask,
                           long mirroredRecipeMask) {
        if (recipeWidth <= 0 || recipeHeight <= 0) return true;
        if (gridWidth <= 0 || gridHeight <= 0) return true;
        if (recipeWidth > gridWidth || recipeHeight > gridHeight) return false;

        if (recipeWidth == gridWidth && recipeHeight == gridHeight) {
            return queryMask == recipeMask || queryMask == mirroredRecipeMask;
        }

        for (int yOffset = 0; yOffset <= gridHeight - recipeHeight; yOffset++) {
            for (int xOffset = 0; xOffset <= gridWidth - recipeWidth; xOffset++) {
                long placed = place(recipeMask, recipeWidth, recipeHeight, gridWidth, xOffset, yOffset);
                if (queryMask == placed) return true;
                if (mirroredRecipeMask != recipeMask) {
                    long mirrored = place(
                        mirroredRecipeMask, recipeWidth, recipeHeight, gridWidth, xOffset, yOffset);
                    if (queryMask == mirrored) return true;
                }
            }
        }
        return false;
    }

    static long place(long localMask,
                      int recipeWidth,
                      int recipeHeight,
                      int gridWidth,
                      int xOffset,
                      int yOffset) {
        long placed = 0L;
        for (int y = 0; y < recipeHeight; y++) {
            for (int x = 0; x < recipeWidth; x++) {
                int localBit = y * recipeWidth + x;
                if ((localMask & (1L << localBit)) == 0L) continue;
                int gridBit = (y + yOffset) * gridWidth + x + xOffset;
                placed |= 1L << gridBit;
            }
        }
        return placed;
    }
}
