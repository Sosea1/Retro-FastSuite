package com.sosea1.fastsuite112.recipe;

import com.sosea1.fastsuite112.config.FastSuiteConfig;
import com.sosea1.fastsuite112.recipe.RecipeSafetyClassifier.Safety;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PivotPlannerTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
    }

    @Test
    void disabledBalancingKeepsFrequencyAwareInitialChoice() {
        Item common = new Item();
        Item rare = new Item();
        RecipeAnalysis analysis = analysis(
            routing(common), routing(rare));
        Map<Item, Integer> frequencies = frequencies(common, 50, rare, 1);

        boolean previous = FastSuiteConfig.frequencyAwarePivotSelection;
        FastSuiteConfig.frequencyAwarePivotSelection = true;
        try {
            PivotPlanner.Result result = PivotPlanner.plan(
                Arrays.asList(analysis), frequencies, false);

            assertArrayEquals(new int[] { 1 }, result.assignments);
            assertEquals(0, result.diagnostics.passes);
            assertEquals(0, result.diagnostics.changes);
        } finally {
            FastSuiteConfig.frequencyAwarePivotSelection = previous;
        }
    }

    @Test
    void enabledBalancingMovesLaterRecipeToLowerLoadRoute() {
        Item first = new Item();
        Item second = new Item();
        Item expensiveAlternative = new Item();
        List<RecipeAnalysis> analyses = Arrays.asList(
            analysis(
                routing(first), routing(first, expensiveAlternative)),
            analysis(
                routing(first), routing(second)));
        Map<Item, Integer> frequencies = frequencies(
            first, 1, second, 1, expensiveAlternative, 100);

        boolean previous = FastSuiteConfig.frequencyAwarePivotSelection;
        FastSuiteConfig.frequencyAwarePivotSelection = true;
        try {
            PivotPlanner.Result result = PivotPlanner.plan(analyses, frequencies, true);

            assertArrayEquals(new int[] { 0, 1 }, result.assignments);
            assertTrue(result.diagnostics.passes >= 1);
            assertTrue(result.diagnostics.changes >= 1);
        } finally {
            FastSuiteConfig.frequencyAwarePivotSelection = previous;
        }
    }

    @Test
    void equalCostsKeepEarlierCandidateIndex() {
        Item first = new Item();
        Item second = new Item();
        RecipeAnalysis analysis = analysis(
            routing(first), routing(second));

        boolean previous = FastSuiteConfig.frequencyAwarePivotSelection;
        FastSuiteConfig.frequencyAwarePivotSelection = true;
        try {
            PivotPlanner.Result result = PivotPlanner.plan(
                Arrays.asList(analysis), frequencies(first, 1, second, 1), true);

            assertArrayEquals(new int[] { 0 }, result.assignments);
        } finally {
            FastSuiteConfig.frequencyAwarePivotSelection = previous;
        }
    }

    private static RecipeAnalysis analysis(CandidateRouting firstRouting,
                                           CandidateRouting secondRouting) {
        return RecipeAnalysis.indexable(
            Arrays.asList(firstRouting, secondRouting),
            Arrays.asList(0, 1), Arrays.asList(true, true), Safety.TRUSTED_BASE,
            0, 0, 0, 0L, 0L);
    }

    private static CandidateRouting routing(Item... items) {
        ItemRoute[] routes = new ItemRoute[items.length];
        for (int index = 0; index < items.length; index++) {
            routes[index] = new ItemRoute(items[index], true, new int[0]);
        }
        return new CandidateRouting(items, routes, 0L, 0L);
    }

    private static Map<Item, Integer> frequencies(Object... pairs) {
        Map<Item, Integer> frequencies = new IdentityHashMap<Item, Integer>();
        for (int index = 0; index < pairs.length; index += 2) {
            frequencies.put((Item) pairs[index], (Integer) pairs[index + 1]);
        }
        return frequencies;
    }
}
