package com.sosea1.fastsuite112.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RecipeConstraintTest {

    @Test
    void builderNamesEveryCompiledConstraintField() {
        RecipeConstraint value = RecipeConstraint.builder()
            .occupiedSlots(2)
            .presenceGroup(0, 1L, 2L)
            .presenceGroup(2, 4L, 8L)
            .requiredItems(16L, 32L)
            .requiredVariants(64L, 128L)
            .shape(2, 1, 3L, 3L)
            .positionalProbe(0, 1, 0x11)
            .repeatedRequirement(0, 3, 5, 2)
            .build();

        assertEquals(2, value.occupiedSlots);
        assertEquals(4L, value.presenceMaskA3);
        assertEquals(128L, value.requiredVariantMaskB);
        assertEquals(0x11, value.positionalSignature1);
        assertEquals(2, value.repeatedCount1);
    }
}
