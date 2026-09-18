package com.sosea1.fastsuite112.recipe;

/**
 * Necessary-condition-only filter evaluated before IRecipe.matches(). It is intentionally
 * incapable of accepting a recipe by itself; it can only reject candidates that are impossible.
 */
final class RecipeConstraint {
    final int occupiedSlots;
    final long presenceMaskA1;
    final long presenceMaskB1;
    final long presenceMaskA2;
    final long presenceMaskB2;
    final long presenceMaskA3;
    final long presenceMaskB3;
    final long requiredItemMaskA;
    final long requiredItemMaskB;
    final long requiredVariantMaskA;
    final long requiredVariantMaskB;
    final int shapeWidth;
    final int shapeHeight;
    final long shapeMask;
    final long mirroredShapeMask;
    final int positionalProbeCount;
    final int positionalIndex1;
    final int positionalIndex2;
    final int positionalIndex3;
    final int positionalSignature1;
    final int positionalSignature2;
    final int positionalSignature3;
    final int repeatedIndexA1;
    final int repeatedIndexB1;
    final int repeatedCount1;
    final int repeatedIndexA2;
    final int repeatedIndexB2;
    final int repeatedCount2;

    private RecipeConstraint(Builder builder) {
        occupiedSlots = builder.occupiedSlots;
        presenceMaskA1 = builder.presenceMaskA1;
        presenceMaskB1 = builder.presenceMaskB1;
        presenceMaskA2 = builder.presenceMaskA2;
        presenceMaskB2 = builder.presenceMaskB2;
        presenceMaskA3 = builder.presenceMaskA3;
        presenceMaskB3 = builder.presenceMaskB3;
        requiredItemMaskA = builder.requiredItemMaskA;
        requiredItemMaskB = builder.requiredItemMaskB;
        requiredVariantMaskA = builder.requiredVariantMaskA;
        requiredVariantMaskB = builder.requiredVariantMaskB;
        shapeWidth = builder.shapeWidth;
        shapeHeight = builder.shapeHeight;
        shapeMask = builder.shapeMask;
        mirroredShapeMask = builder.mirroredShapeMask;
        positionalProbeCount = builder.positionalProbeCount;
        positionalIndex1 = builder.positionalIndex1;
        positionalIndex2 = builder.positionalIndex2;
        positionalIndex3 = builder.positionalIndex3;
        positionalSignature1 = builder.positionalSignature1;
        positionalSignature2 = builder.positionalSignature2;
        positionalSignature3 = builder.positionalSignature3;
        repeatedIndexA1 = builder.repeatedIndexA1;
        repeatedIndexB1 = builder.repeatedIndexB1;
        repeatedCount1 = builder.repeatedCount1;
        repeatedIndexA2 = builder.repeatedIndexA2;
        repeatedIndexB2 = builder.repeatedIndexB2;
        repeatedCount2 = builder.repeatedCount2;
    }

    static Builder builder() {
        return new Builder();
    }

    static RecipeConstraint fromMandatory(MandatoryStackConstraint mandatory) {
        return builder()
            .occupiedSlots(-1)
            .presenceGroup(0, mandatory.groupCount() > 0 ? mandatory.maskA(0) : 0L,
                mandatory.groupCount() > 0 ? mandatory.maskB(0) : 0L)
            .presenceGroup(1, mandatory.groupCount() > 1 ? mandatory.maskA(1) : 0L,
                mandatory.groupCount() > 1 ? mandatory.maskB(1) : 0L)
            .presenceGroup(2, mandatory.groupCount() > 2 ? mandatory.maskA(2) : 0L,
                mandatory.groupCount() > 2 ? mandatory.maskB(2) : 0L)
            .build();
    }

    static final class Builder {
        private int occupiedSlots;
        private long presenceMaskA1;
        private long presenceMaskB1;
        private long presenceMaskA2;
        private long presenceMaskB2;
        private long presenceMaskA3;
        private long presenceMaskB3;
        private long requiredItemMaskA;
        private long requiredItemMaskB;
        private long requiredVariantMaskA;
        private long requiredVariantMaskB;
        private int shapeWidth;
        private int shapeHeight;
        private long shapeMask;
        private long mirroredShapeMask;
        private int positionalProbeCount;
        private int positionalIndex1 = -1;
        private int positionalIndex2 = -1;
        private int positionalIndex3 = -1;
        private int positionalSignature1;
        private int positionalSignature2;
        private int positionalSignature3;
        private int repeatedIndexA1 = -1;
        private int repeatedIndexB1 = -1;
        private int repeatedCount1;
        private int repeatedIndexA2 = -1;
        private int repeatedIndexB2 = -1;
        private int repeatedCount2;

        Builder occupiedSlots(int value) {
            occupiedSlots = value;
            return this;
        }

        Builder presenceGroup(int group, long maskA, long maskB) {
            if (group < 0 || group > 2) throw new IllegalArgumentException("group: " + group);
            if (group == 0) {
                presenceMaskA1 = maskA;
                presenceMaskB1 = maskB;
            } else if (group == 1) {
                presenceMaskA2 = maskA;
                presenceMaskB2 = maskB;
            } else {
                presenceMaskA3 = maskA;
                presenceMaskB3 = maskB;
            }
            return this;
        }

        Builder requiredItems(long maskA, long maskB) {
            requiredItemMaskA = maskA;
            requiredItemMaskB = maskB;
            return this;
        }

        Builder requiredVariants(long maskA, long maskB) {
            requiredVariantMaskA = maskA;
            requiredVariantMaskB = maskB;
            return this;
        }

        Builder shape(int width, int height, long mask, long mirroredMask) {
            shapeWidth = width;
            shapeHeight = height;
            shapeMask = mask;
            mirroredShapeMask = mirroredMask;
            return this;
        }

        Builder positionalProbe(int slot, int index, int signature) {
            if (slot < 0 || slot > 2) throw new IllegalArgumentException("slot: " + slot);
            if (slot == 0) {
                positionalIndex1 = index;
                positionalSignature1 = signature;
            } else if (slot == 1) {
                positionalIndex2 = index;
                positionalSignature2 = signature;
            } else {
                positionalIndex3 = index;
                positionalSignature3 = signature;
            }
            positionalProbeCount = Math.max(positionalProbeCount, slot + 1);
            return this;
        }

        Builder repeatedRequirement(int slot, int indexA, int indexB, int count) {
            if (slot < 0 || slot > 1) throw new IllegalArgumentException("slot: " + slot);
            if (slot == 0) {
                repeatedIndexA1 = indexA;
                repeatedIndexB1 = indexB;
                repeatedCount1 = count;
            } else {
                repeatedIndexA2 = indexA;
                repeatedIndexB2 = indexB;
                repeatedCount2 = count;
            }
            return this;
        }

        RecipeConstraint build() {
            return new RecipeConstraint(this);
        }
    }
}
