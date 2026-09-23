package com.sosea1.fastsuite112.recipe;

import javax.annotation.Nullable;

/**
 * Struct-of-arrays runtime representation for second-stage pruning. Primitive columns are allocated
 * only when at least one recipe actually uses the corresponding constraint family. This keeps the
 * hot iterator dense and predictable without paying registry-sized memory for unused features.
 */
final class ConstraintTable {
    private static final int FLAG_OCCUPIED = 1;
    private static final int FLAG_ITEM_SIGNATURE = 1 << 1;
    private static final int FLAG_VARIANT_SIGNATURE = 1 << 2;
    private static final int FLAG_PRESENCE_1 = 1 << 3;
    private static final int FLAG_PRESENCE_2 = 1 << 4;
    private static final int FLAG_REPEATED = 1 << 5;
    private static final int FLAG_SHAPE = 1 << 6;
    private static final int FLAG_POSITIONAL = 1 << 7;

    private static final int QUERY_ITEM_FINGERPRINTS = 1;
    private static final int QUERY_VARIANT_FINGERPRINTS = 1 << 1;
    private static final int QUERY_COUNT_SKETCH = 1 << 2;
    private static final int QUERY_SHAPE_OCCUPANCY = 1 << 3;

    static final ConstraintTable EMPTY = new ConstraintTable(0, 0,
        null, null,
        null, null, null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        CompiledShapePlacements.EMPTY, CompiledPositionalProbes.EMPTY,
        null, null, null, null, null, null);

    private final int size;
    private final int queryFeatures;
    @Nullable private final byte[] flags;
    @Nullable private final byte[] occupiedSlots;
    @Nullable private final long[] presenceMaskA1;
    @Nullable private final long[] presenceMaskB1;
    @Nullable private final long[] presenceMaskA2;
    @Nullable private final long[] presenceMaskB2;
    @Nullable private final long[] presenceMaskA3;
    @Nullable private final long[] presenceMaskB3;
    @Nullable private final long[] requiredItemMaskA;
    @Nullable private final long[] requiredItemMaskB;
    @Nullable private final long[] requiredVariantMaskA;
    @Nullable private final long[] requiredVariantMaskB;
    @Nullable private final byte[] shapeWidth;
    @Nullable private final byte[] shapeHeight;
    @Nullable private final long[] shapeMask;
    @Nullable private final long[] mirroredShapeMask;
    private final CompiledShapePlacements compiledShapes;
    private final CompiledPositionalProbes compiledPositional;
    @Nullable private final byte[] repeatedIndexA1;
    @Nullable private final byte[] repeatedIndexB1;
    @Nullable private final byte[] repeatedCount1;
    @Nullable private final byte[] repeatedIndexA2;
    @Nullable private final byte[] repeatedIndexB2;
    @Nullable private final byte[] repeatedCount2;

    private ConstraintTable(int size, int queryFeatures,
                            @Nullable byte[] flags, @Nullable byte[] occupiedSlots,
                            @Nullable long[] presenceMaskA1, @Nullable long[] presenceMaskB1,
                            @Nullable long[] presenceMaskA2, @Nullable long[] presenceMaskB2,
                            @Nullable long[] presenceMaskA3, @Nullable long[] presenceMaskB3,
                            @Nullable long[] requiredItemMaskA, @Nullable long[] requiredItemMaskB,
                            @Nullable long[] requiredVariantMaskA, @Nullable long[] requiredVariantMaskB,
                            @Nullable byte[] shapeWidth, @Nullable byte[] shapeHeight,
                            @Nullable long[] shapeMask, @Nullable long[] mirroredShapeMask,
                            CompiledShapePlacements compiledShapes, CompiledPositionalProbes compiledPositional,
                            @Nullable byte[] repeatedIndexA1, @Nullable byte[] repeatedIndexB1,
                            @Nullable byte[] repeatedCount1, @Nullable byte[] repeatedIndexA2,
                            @Nullable byte[] repeatedIndexB2, @Nullable byte[] repeatedCount2) {
        this.size = size;
        this.queryFeatures = queryFeatures;
        this.flags = flags;
        this.occupiedSlots = occupiedSlots;
        this.presenceMaskA1 = presenceMaskA1;
        this.presenceMaskB1 = presenceMaskB1;
        this.presenceMaskA2 = presenceMaskA2;
        this.presenceMaskB2 = presenceMaskB2;
        this.presenceMaskA3 = presenceMaskA3;
        this.presenceMaskB3 = presenceMaskB3;
        this.requiredItemMaskA = requiredItemMaskA;
        this.requiredItemMaskB = requiredItemMaskB;
        this.requiredVariantMaskA = requiredVariantMaskA;
        this.requiredVariantMaskB = requiredVariantMaskB;
        this.shapeWidth = shapeWidth;
        this.shapeHeight = shapeHeight;
        this.shapeMask = shapeMask;
        this.mirroredShapeMask = mirroredShapeMask;
        this.compiledShapes = compiledShapes;
        this.compiledPositional = compiledPositional;
        this.repeatedIndexA1 = repeatedIndexA1;
        this.repeatedIndexB1 = repeatedIndexB1;
        this.repeatedCount1 = repeatedCount1;
        this.repeatedIndexA2 = repeatedIndexA2;
        this.repeatedIndexB2 = repeatedIndexB2;
        this.repeatedCount2 = repeatedCount2;
    }

    static ConstraintTable freeze(RecipeConstraint[] source) {
        if (source == null || source.length == 0) return EMPTY;

        ColumnNeeds needs = ColumnNeeds.scan(source);
        if (!needs.any()) {
            return new ConstraintTable(source.length, 0,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null,
                null, null, null, null,
                CompiledShapePlacements.EMPTY, CompiledPositionalProbes.EMPTY,
                null, null, null, null, null, null);
        }

        int size = source.length;
        byte[] flags = new byte[size];
        byte[] occupiedSlots = needs.occupied ? new byte[size] : null;
        long[] presenceMaskA1 = needs.presence1 ? new long[size] : null;
        long[] presenceMaskB1 = needs.presence1 ? new long[size] : null;
        long[] presenceMaskA2 = needs.presence2 ? new long[size] : null;
        long[] presenceMaskB2 = needs.presence2 ? new long[size] : null;
        long[] presenceMaskA3 = needs.presence3 ? new long[size] : null;
        long[] presenceMaskB3 = needs.presence3 ? new long[size] : null;
        long[] requiredItemMaskA = needs.itemSignature ? new long[size] : null;
        long[] requiredItemMaskB = needs.itemSignature ? new long[size] : null;
        long[] requiredVariantMaskA = needs.variantSignature ? new long[size] : null;
        long[] requiredVariantMaskB = needs.variantSignature ? new long[size] : null;
        byte[] shapeWidth = needs.shape ? new byte[size] : null;
        byte[] shapeHeight = needs.shape ? new byte[size] : null;
        long[] shapeMask = needs.shape ? new long[size] : null;
        long[] mirroredShapeMask = needs.shape ? new long[size] : null;
        byte[] positionalProbeCount = needs.positional ? new byte[size] : null;
        byte[] positionalIndex1 = needs.positional ? new byte[size] : null;
        byte[] positionalIndex2 = needs.positional ? new byte[size] : null;
        byte[] positionalIndex3 = needs.positional ? new byte[size] : null;
        int[] positionalSignature1 = needs.positional ? new int[size] : null;
        int[] positionalSignature2 = needs.positional ? new int[size] : null;
        int[] positionalSignature3 = needs.positional ? new int[size] : null;
        byte[] repeatedIndexA1 = needs.repeated ? new byte[size] : null;
        byte[] repeatedIndexB1 = needs.repeated ? new byte[size] : null;
        byte[] repeatedCount1 = needs.repeated ? new byte[size] : null;
        byte[] repeatedIndexA2 = needs.repeated ? new byte[size] : null;
        byte[] repeatedIndexB2 = needs.repeated ? new byte[size] : null;
        byte[] repeatedCount2 = needs.repeated ? new byte[size] : null;
        int queryFeatures = 0;

        for (int id = 0; id < size; id++) {
            RecipeConstraint constraint = source[id];
            if (constraint == null) continue;
            int recipeFlags = 0;
            if (occupiedSlots != null && constraint.occupiedSlots > 0 && constraint.occupiedSlots <= 255) {
                occupiedSlots[id] = (byte) constraint.occupiedSlots;
                recipeFlags |= FLAG_OCCUPIED;
            }
            if (presenceMaskA1 != null) {
                presenceMaskA1[id] = constraint.presenceMaskA1;
                presenceMaskB1[id] = constraint.presenceMaskB1;
                if (constraint.presenceMaskA1 != 0L) recipeFlags |= FLAG_PRESENCE_1;
            }
            if (presenceMaskA2 != null) {
                presenceMaskA2[id] = constraint.presenceMaskA2;
                presenceMaskB2[id] = constraint.presenceMaskB2;
                if (constraint.presenceMaskA2 != 0L) recipeFlags |= FLAG_PRESENCE_2;
            }
            if (presenceMaskA3 != null) {
                presenceMaskA3[id] = constraint.presenceMaskA3;
                presenceMaskB3[id] = constraint.presenceMaskB3;
            }
            if (constraint.presenceMaskA1 != 0L || constraint.presenceMaskA2 != 0L
                || constraint.presenceMaskA3 != 0L) {
                queryFeatures |= QUERY_ITEM_FINGERPRINTS | QUERY_VARIANT_FINGERPRINTS;
            }
            if (requiredItemMaskA != null) {
                requiredItemMaskA[id] = constraint.requiredItemMaskA;
                requiredItemMaskB[id] = constraint.requiredItemMaskB;
                if (constraint.requiredItemMaskA != 0L) {
                    recipeFlags |= FLAG_ITEM_SIGNATURE;
                    queryFeatures |= QUERY_ITEM_FINGERPRINTS;
                }
            }
            if (requiredVariantMaskA != null) {
                requiredVariantMaskA[id] = constraint.requiredVariantMaskA;
                requiredVariantMaskB[id] = constraint.requiredVariantMaskB;
                if (constraint.requiredVariantMaskA != 0L) {
                    recipeFlags |= FLAG_VARIANT_SIGNATURE;
                    queryFeatures |= QUERY_VARIANT_FINGERPRINTS;
                }
            }
            if (shapeWidth != null && constraint.shapeWidth > 0 && constraint.shapeWidth <= 255
                && constraint.shapeHeight > 0 && constraint.shapeHeight <= 255) {
                shapeWidth[id] = (byte) constraint.shapeWidth;
                shapeHeight[id] = (byte) constraint.shapeHeight;
                shapeMask[id] = constraint.shapeMask;
                mirroredShapeMask[id] = constraint.mirroredShapeMask;
                recipeFlags |= FLAG_SHAPE;
                queryFeatures |= QUERY_SHAPE_OCCUPANCY;
                int probes = Math.min(3, Math.max(0, constraint.positionalProbeCount));
                if (probes > 0 && positionalProbeCount != null) {
                    positionalProbeCount[id] = (byte) probes;
                    positionalIndex1[id] = (byte) Math.max(0, constraint.positionalIndex1);
                    positionalIndex2[id] = (byte) Math.max(0, constraint.positionalIndex2);
                    positionalIndex3[id] = (byte) Math.max(0, constraint.positionalIndex3);
                    positionalSignature1[id] = constraint.positionalSignature1;
                    positionalSignature2[id] = constraint.positionalSignature2;
                    positionalSignature3[id] = constraint.positionalSignature3;
                    recipeFlags |= FLAG_POSITIONAL;
                    queryFeatures |= QUERY_ITEM_FINGERPRINTS | QUERY_VARIANT_FINGERPRINTS;
                }
            }
            if (repeatedCount1 != null) {
                repeatedIndexA1[id] = (byte) Math.max(0, constraint.repeatedIndexA1);
                repeatedIndexB1[id] = (byte) Math.max(0, constraint.repeatedIndexB1);
                repeatedCount1[id] = (byte) SaturatingCountSketch.capRequirement(constraint.repeatedCount1);
                repeatedIndexA2[id] = (byte) Math.max(0, constraint.repeatedIndexA2);
                repeatedIndexB2[id] = (byte) Math.max(0, constraint.repeatedIndexB2);
                repeatedCount2[id] = (byte) SaturatingCountSketch.capRequirement(constraint.repeatedCount2);
                if ((repeatedCount1[id] & 0xff) > 1 || (repeatedCount2[id] & 0xff) > 1) {
                    recipeFlags |= FLAG_REPEATED;
                    queryFeatures |= QUERY_COUNT_SKETCH;
                }
            }
            flags[id] = (byte) recipeFlags;
        }

        CompiledShapePlacements compiledShapes = CompiledShapePlacements.compile(
            shapeWidth, shapeHeight, shapeMask, mirroredShapeMask, positionalProbeCount);
        CompiledPositionalProbes compiledPositional = CompiledPositionalProbes.compile(
            shapeWidth, shapeHeight, shapeMask, mirroredShapeMask, positionalProbeCount,
            positionalIndex1, positionalIndex2, positionalIndex3, positionalSignature1,
            positionalSignature2, positionalSignature3);
        return new ConstraintTable(size, queryFeatures, flags, occupiedSlots, presenceMaskA1, presenceMaskB1, presenceMaskA2,
            presenceMaskB2, presenceMaskA3, presenceMaskB3, requiredItemMaskA, requiredItemMaskB,
            requiredVariantMaskA, requiredVariantMaskB, shapeWidth, shapeHeight, shapeMask, mirroredShapeMask,
            compiledShapes, compiledPositional, repeatedIndexA1, repeatedIndexB1, repeatedCount1,
            repeatedIndexA2, repeatedIndexB2, repeatedCount2);
    }

    boolean hasAny() {
        return flags != null;
    }

    boolean hasPositionalProbes() {
        return compiledPositional.hasAny();
    }

    boolean needsItemFingerprints() {
        return (queryFeatures & QUERY_ITEM_FINGERPRINTS) != 0;
    }

    boolean needsVariantFingerprints() {
        return (queryFeatures & QUERY_VARIANT_FINGERPRINTS) != 0;
    }

    boolean needsCountSketch() {
        return (queryFeatures & QUERY_COUNT_SKETCH) != 0;
    }

    boolean needsShapeOccupancy() {
        return (queryFeatures & QUERY_SHAPE_OCCUPANCY) != 0;
    }

    boolean accepts(int id, int queryOccupiedSlots, long queryMatchMaskA, long queryMatchMaskB,
                    long queryItemMaskA, long queryItemMaskB, long queryVariantMaskA, long queryVariantMaskB,
                    long queryCountSketchA, long queryCountSketchB, int queryGridWidth, int queryGridHeight,
                    long queryOccupancyMask, long querySlotTokens0, long querySlotTokens1, int querySlotToken8) {
        if (id < 0 || id >= size) throw new IllegalStateException("Recipe constraint id out of range: " + id + " / " + size);
        if (flags == null) return true;
        int recipeFlags = flags[id] & 0xff;
        if (recipeFlags == 0 && (presenceMaskA3 == null || presenceMaskA3[id] == 0L)) return true;
        if ((recipeFlags & FLAG_OCCUPIED) != 0 && queryOccupiedSlots != (occupiedSlots[id] & 0xff)) return false;
        if ((recipeFlags & FLAG_ITEM_SIGNATURE) != 0) {
            long itemMaskA = requiredItemMaskA[id];
            if ((queryItemMaskA & itemMaskA) != itemMaskA || (queryItemMaskB & requiredItemMaskB[id]) != requiredItemMaskB[id]) return false;
        }
        if ((recipeFlags & FLAG_VARIANT_SIGNATURE) != 0) {
            long variantMaskA = requiredVariantMaskA[id];
            if ((queryVariantMaskA & variantMaskA) != variantMaskA || (queryVariantMaskB & requiredVariantMaskB[id]) != requiredVariantMaskB[id]) return false;
        }
        if ((recipeFlags & FLAG_PRESENCE_1) != 0) {
            long maskA1 = presenceMaskA1[id];
            if ((queryMatchMaskA & maskA1) == 0L || (queryMatchMaskB & presenceMaskB1[id]) == 0L) return false;
        }
        if ((recipeFlags & FLAG_PRESENCE_2) != 0) {
            long maskA2 = presenceMaskA2[id];
            if ((queryMatchMaskA & maskA2) == 0L || (queryMatchMaskB & presenceMaskB2[id]) == 0L) return false;
        }
        if (presenceMaskA3 != null && presenceMaskA3[id] != 0L
            && ((queryMatchMaskA & presenceMaskA3[id]) == 0L || (queryMatchMaskB & presenceMaskB3[id]) == 0L)) return false;
        if ((recipeFlags & FLAG_REPEATED) != 0) {
            int count1 = repeatedCount1[id] & 0xff;
            if (count1 > 1 && SaturatingCountSketch.estimate(queryCountSketchA, repeatedIndexA1[id] & 0xff, queryCountSketchB, repeatedIndexB1[id] & 0xff) < count1) return false;
            int count2 = repeatedCount2[id] & 0xff;
            if (count2 > 1 && SaturatingCountSketch.estimate(queryCountSketchA, repeatedIndexA2[id] & 0xff, queryCountSketchB, repeatedIndexB2[id] & 0xff) < count2) return false;
        }
        if ((recipeFlags & FLAG_SHAPE) != 0 && queryGridWidth != 0) {
            if ((recipeFlags & FLAG_POSITIONAL) != 0 && compiledPositional.supports(queryGridWidth, queryGridHeight)) {
                // Positional probes include the same exact occupancy check, avoiding an equivalent placement scan.
                if (!compiledPositional.matches(id, queryGridWidth, queryGridHeight, queryOccupancyMask, querySlotTokens0, querySlotTokens1, querySlotToken8)) return false;
            } else if (compiledShapes.supports(queryGridWidth, queryGridHeight)) {
                if (!compiledShapes.matches(id, queryGridWidth, queryGridHeight, queryOccupancyMask)) return false;
            } else if (!ShapeOccupancy.matches(queryOccupancyMask, queryGridWidth, queryGridHeight,
                shapeWidth[id] & 0xff, shapeHeight[id] & 0xff, shapeMask[id], mirroredShapeMask[id])) return false;
        }
        return true;
    }

    /** Rebuild-only census used to avoid allocating dead registry-sized columns. */
    private static final class ColumnNeeds {
        private boolean occupied;
        private boolean presence1;
        private boolean presence2;
        private boolean presence3;
        private boolean itemSignature;
        private boolean variantSignature;
        private boolean shape;
        private boolean positional;
        private boolean repeated;

        private boolean any() {
            return occupied || presence1 || presence2 || presence3 || itemSignature || variantSignature
                || shape || positional || repeated;
        }

        private static ColumnNeeds scan(RecipeConstraint[] source) {
            ColumnNeeds result = new ColumnNeeds();
            for (RecipeConstraint constraint : source) {
                if (constraint == null) continue;
                if (constraint.occupiedSlots > 0 && constraint.occupiedSlots <= 255) result.occupied = true;
                if (constraint.presenceMaskA1 != 0L) result.presence1 = true;
                if (constraint.presenceMaskA2 != 0L) result.presence2 = true;
                if (constraint.presenceMaskA3 != 0L) result.presence3 = true;
                if (constraint.requiredItemMaskA != 0L) result.itemSignature = true;
                if (constraint.requiredVariantMaskA != 0L) result.variantSignature = true;
                if (constraint.shapeWidth > 0 && constraint.shapeWidth <= 255
                    && constraint.shapeHeight > 0 && constraint.shapeHeight <= 255) {
                    result.shape = true;
                    if (constraint.positionalProbeCount > 0) result.positional = true;
                }
                if (SaturatingCountSketch.capRequirement(constraint.repeatedCount1) > 1
                    || SaturatingCountSketch.capRequirement(constraint.repeatedCount2) > 1) {
                    result.repeated = true;
                }
            }
            return result;
        }
    }
}
