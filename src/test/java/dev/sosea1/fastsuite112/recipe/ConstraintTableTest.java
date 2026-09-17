package dev.sosea1.fastsuite112.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConstraintTableTest {

    @Test
    void frozenTableCombinesOccupiedPresenceAndRepeatedRequirements() {
        RecipeConstraint constraint = RecipeConstraint.builder()
            .occupiedSlots(2)
            .presenceGroup(0, 1L, 2L)
            .repeatedRequirement(0, 3, 5, 2)
            .build();
        ConstraintTable table = ConstraintTable.freeze(new RecipeConstraint[] {constraint});

        long countsA = SaturatingCountSketch.increment(
            SaturatingCountSketch.increment(0L, 3), 3);
        long countsB = SaturatingCountSketch.increment(
            SaturatingCountSketch.increment(0L, 5), 5);
        long insufficientCountsA = SaturatingCountSketch.increment(0L, 3);
        long insufficientCountsB = SaturatingCountSketch.increment(0L, 5);

        assertTrue(table.accepts(0, 2, 1L, 2L, 0L, 0L, 0L, 0L,
            countsA, countsB, 0, 0, 0L, 0L, 0L, 0));
        assertFalse(table.accepts(0, 1, 1L, 2L, 0L, 0L, 0L, 0L,
            countsA, countsB, 0, 0, 0L, 0L, 0L, 0));
        assertFalse(table.accepts(0, 2, 1L, 0L, 0L, 0L, 0L, 0L,
            countsA, countsB, 0, 0, 0L, 0L, 0L, 0));
        assertFalse(table.accepts(0, 2, 1L, 2L, 0L, 0L, 0L, 0L,
            insufficientCountsA, insufficientCountsB, 0, 0, 0L, 0L, 0L, 0));
    }

    @Test
    void frozenTableRejectsMissingRequiredItemMask() {
        ConstraintTable table = ConstraintTable.freeze(new RecipeConstraint[] {RecipeConstraint.builder()
            .requiredItems(1L, 2L)
            .build()});

        assertTrue(table.accepts(0, 0, 0L, 0L, 1L, 2L, 0L, 0L,
            0L, 0L, 0, 0, 0L, 0L, 0L, 0));
        assertFalse(table.accepts(0, 0, 0L, 0L, 1L, 0L, 0L, 0L,
            0L, 0L, 0, 0, 0L, 0L, 0L, 0));
    }

    @Test
    void frozenTableRejectsMissingRequiredVariantMask() {
        ConstraintTable table = ConstraintTable.freeze(new RecipeConstraint[] {RecipeConstraint.builder()
            .requiredVariants(4L, 8L)
            .build()});

        assertTrue(table.accepts(0, 0, 0L, 0L, 0L, 0L, 4L, 8L,
            0L, 0L, 0, 0, 0L, 0L, 0L, 0));
        assertFalse(table.accepts(0, 0, 0L, 0L, 0L, 0L, 4L, 0L,
            0L, 0L, 0, 0, 0L, 0L, 0L, 0));
    }

    @Test
    void frozenTableRejectsMismatchedShapeOccupancy() {
        ConstraintTable table = ConstraintTable.freeze(new RecipeConstraint[] {RecipeConstraint.builder()
            .shape(2, 1, 3L, 3L)
            .build()});

        assertTrue(table.accepts(0, 0, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 2, 2, 3L, 0L, 0L, 0));
        assertFalse(table.accepts(0, 0, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 2, 2, 1L, 0L, 0L, 0));
    }

    @Test
    void frozenTableRejectsMismatchedPositionalProbe() {
        ConstraintTable table = ConstraintTable.freeze(new RecipeConstraint[] {RecipeConstraint.builder()
            .shape(1, 1, 1L, 1L)
            .positionalProbe(0, 0, 0x0011)
            .build()});

        assertTrue(table.accepts(0, 0, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 2, 2, 1L, 0x0011L, 0L, 0));
        assertFalse(table.accepts(0, 0, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 2, 2, 1L, 0x0044L, 0L, 0));
    }
}
