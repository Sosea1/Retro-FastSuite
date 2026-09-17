package dev.sosea1.fastsuite112.recipe;

import net.minecraft.item.Item;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConstraintCompilerTest {

    @Test
    void compilesRequiredMasksAndShapeFromTwoMandatoryIngredients() {
        Item first = new Item();
        Item second = new Item();
        RecipeAnalysis analysis = RecipeAnalysis.indexable(
            Arrays.asList(new Item[] {first}, new Item[] {second}),
            Arrays.asList(
                routing(first, 1L, 2L),
                routing(second, 4L, 8L)),
            Arrays.asList(0, 1),
            Arrays.asList(true, true),
            RecipeSafetyClassifier.Safety.TRUSTED_BASE,
            2, 2, 1, 3L, 3L);

        RecipeConstraint constraint = ConstraintCompiler.compile(analysis, 0, frequencies(first, second));

        assertEquals(2, constraint.occupiedSlots);
        assertTrue(constraint.requiredItemMaskA != 0L);
        assertTrue(constraint.requiredItemMaskB != 0L);
        assertEquals(2, constraint.shapeWidth);
        assertEquals(1, constraint.shapeHeight);
    }

    @Test
    void compilesARepeatedSingleItemRequirementWithCountTwo() {
        Item repeated = new Item();
        RecipeAnalysis analysis = RecipeAnalysis.indexable(
            Arrays.asList(new Item[] {repeated}, new Item[] {repeated}),
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

    private static Map<Item, Integer> frequencies(Item... items) {
        Map<Item, Integer> frequencies = new IdentityHashMap<Item, Integer>();
        for (Item item : items) frequencies.put(item, 1);
        return frequencies;
    }
}
