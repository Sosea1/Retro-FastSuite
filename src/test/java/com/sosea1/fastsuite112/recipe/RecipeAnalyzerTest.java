package com.sosea1.fastsuite112.recipe;

import com.sosea1.fastsuite112.api.RecipeConstraintSpec;
import net.minecraft.init.Bootstrap;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

final class RecipeAnalyzerTest {
    private static Item requiredItem;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
        requiredItem = new Item();
    }

    @Test
    void unknownRecipeFailsOpenWithItsClassRecorded() {
        RecipeAnalyzer analyzer = new RecipeAnalyzer(
            RecipeSafetyClassifier.INSTANCE, new RecipeConstraintProviderRegistry());

        RecipeAnalysis analysis = analyzer.analyze(new UnknownRecipe());

        assertFalse(analysis.isIndexable());
        assertEquals(RecipeIndex.FallbackReason.UNKNOWN_RECIPE_CLASS, analysis.fallbackReason);
        assertSame(UnknownRecipe.class, analysis.diagnosticClass);
    }

    @Test
    void filterOnlyProviderProducesConstrainedFallback() {
        RecipeConstraintProviderRegistry providers = new RecipeConstraintProviderRegistry();
        providers.register(UnknownRecipe.class, recipe -> RecipeConstraintSpec.filterOnly(
            new ItemStack[] { new ItemStack(requiredItem) }));

        RecipeAnalysis analysis = new RecipeAnalyzer(
            RecipeSafetyClassifier.INSTANCE, providers).analyze(new UnknownRecipe());

        assertEquals(RecipeIndex.FallbackReason.PROVIDER_FILTER_ONLY, analysis.fallbackReason);
        assertFalse(analysis.mandatoryStackConstraint.isUnconstrained());
    }

    private static final class UnknownRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
        @Override public boolean matches(InventoryCrafting inv, World worldIn) { return false; }
        @Override public ItemStack getCraftingResult(InventoryCrafting inv) { return ItemStack.EMPTY; }
        @Override public boolean canFit(int width, int height) { return false; }
        @Override public ItemStack getRecipeOutput() { return ItemStack.EMPTY; }
    }
}
