package com.sosea1.fastsuite112.api;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FastSuiteApiContractTest {

    @Test
    void apiV2KeepsOrderedAllMatchAndLegacyCandidateSurfaces() throws Exception {
        assertEquals(3, FastSuiteAPI.API_VERSION);
        assertEquals(3, FastSuiteAPI.getApiVersion());

        Method visitor = FastSuiteAPI.class.getMethod(
                "visitMatchingRecipes",
                InventoryCrafting.class,
                World.class,
                RecipeMatchVisitor.class);
        Method allMatches = FastSuiteAPI.class.getMethod(
                "findAllMatchingRecipes",
                InventoryCrafting.class,
                World.class);
        Method candidates = FastSuiteAPI.class.getMethod(
                "getCandidateRecipes",
                InventoryCrafting.class);
        Method legacy = FastSuiteAPI.class.getMethod(
                "getCraftingCandidates",
                InventoryCrafting.class);

        assertNotNull(visitor);
        assertNotNull(allMatches);
        assertNotNull(candidates);
        assertTrue(legacy.isAnnotationPresent(Deprecated.class));
    }

    @Test
    void exposesConstraintProviderContract() throws Exception {
        assertTrue(IRecipe.class.isAssignableFrom(
            RecipeConstraintProvider.class.getMethod("describe", IRecipe.class).getParameterTypes()[0]));
    }

    @Test
    void exposesExactClassConstraintProviderRegistration() throws Exception {
        Method registration = FastSuiteAPI.class.getMethod(
            "registerRecipeConstraintProvider",
            Class.class,
            RecipeConstraintProvider.class);
        assertNotNull(registration);
    }
}
