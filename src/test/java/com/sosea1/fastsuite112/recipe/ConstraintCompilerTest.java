package com.sosea1.fastsuite112.recipe;

import net.minecraft.item.Item;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConstraintCompilerTest {

    @Test
    void compilesRequiredMasksForNonPivotMandatoryIngredientsOnly() {
        Item first = new Item();
        Item second = itemWithDistinctFingerprints(first, 7);
        RecipeAnalysis analysis = RecipeAnalysis.indexable(
            Arrays.asList(
                routing(first, 1L, 2L),
                exactRouting(second, 4L, 8L, 7)),
            Arrays.asList(0, 1),
            Arrays.asList(true, true),
            RecipeSafetyClassifier.Safety.TRUSTED_BASE,
            2, 2, 1, 3L, 3L);

        RecipeConstraint constraint = ConstraintCompiler.compile(analysis, 0, frequencies(first, second));

        assertEquals(2, constraint.occupiedSlots);
        assertEquals(MandatoryStackConstraint.presenceBitA(second), constraint.requiredItemMaskA);
        assertEquals(MandatoryStackConstraint.presenceBitB(second), constraint.requiredItemMaskB);
        assertEquals(MandatoryStackConstraint.exactVariantBitA(second, 7), constraint.requiredVariantMaskA);
        assertEquals(MandatoryStackConstraint.exactVariantBitB(second, 7), constraint.requiredVariantMaskB);
        assertEquals(2, constraint.shapeWidth);
        assertEquals(1, constraint.shapeHeight);
    }

    @Test
    void compilesARepeatedSingleItemRequirementWithCountTwo() {
        Item repeated = new Item();
        RecipeAnalysis analysis = RecipeAnalysis.indexable(
            Arrays.asList(
                routing(repeated, 1L, 2L),
                routing(repeated, 1L, 2L)),
            Arrays.asList(0, 1),
            Arrays.asList(true, true),
            RecipeSafetyClassifier.Safety.TRUSTED_BASE,
            2, 2, 1, 3L, 3L);

        RecipeConstraint constraint = ConstraintCompiler.compile(analysis, 0, frequencies(repeated));

        assertTrue(constraint.repeatedCount1 == 2 || constraint.repeatedCount2 == 2);
    }

    private static CandidateRouting routing(Item item, long maskA, long maskB) {
        return new CandidateRouting(new Item[] {item}, new ItemRoute[] {
            new ItemRoute(item, true, new int[0])
        }, maskA, maskB);
    }

    private static CandidateRouting exactRouting(Item item, long maskA, long maskB, int metadata) {
        return new CandidateRouting(new Item[] {item}, new ItemRoute[] {
            new ItemRoute(item, false, new int[] {metadata})
        }, maskA, maskB);
    }

    private static Item itemWithDistinctFingerprints(Item other, int metadata) {
        long otherItemA = MandatoryStackConstraint.presenceBitA(other);
        long otherItemB = MandatoryStackConstraint.presenceBitB(other);
        long otherVariantA = MandatoryStackConstraint.exactVariantBitA(other, metadata);
        long otherVariantB = MandatoryStackConstraint.exactVariantBitB(other, metadata);
        for (int attempts = 0; attempts < 1024; attempts++) {
            Item candidate = new Item();
            if (MandatoryStackConstraint.presenceBitA(candidate) != otherItemA
                && MandatoryStackConstraint.presenceBitB(candidate) != otherItemB
                && MandatoryStackConstraint.exactVariantBitA(candidate, metadata) != otherVariantA
                && MandatoryStackConstraint.exactVariantBitB(candidate, metadata) != otherVariantB) {
                return candidate;
            }
        }
        throw new AssertionError("Could not create distinct fingerprint test Item");
    }

    private static Map<Item, Integer> frequencies(Item... items) {
        Map<Item, Integer> frequencies = new IdentityHashMap<Item, Integer>();
        for (Item item : items) frequencies.put(item, 1);
        return frequencies;
    }
}
