package dev.sosea1.fastsuite112.recipe;

import dev.sosea1.fastsuite112.api.RecipeConstraintSpec;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConstrainedFallbackTest {
    private static Item required;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
        required = new Item();
    }

    @Test
    void filterOnlyProviderCompilesMandatoryGroupsWithoutPromotingRecipe() {
        RecipeConstraintProviderRegistry registry = new RecipeConstraintProviderRegistry();
        registry.register(FilterRecipe.class, recipe -> RecipeConstraintSpec.filterOnly(
            new ItemStack[] { new ItemStack(required, 1, 2) }));

        ProviderConstraintDecision decision = ProviderConstraintDecision.evaluate(
            registry, new FilterRecipe());

        assertEquals(ProviderConstraintDecision.Kind.FILTER_ONLY, decision.kind);
        assertFalse(decision.constraint.isUnconstrained());
        assertFalse(decision.constraint.accepts(new ItemStack[0]));
        assertTrue(decision.constraint.accepts(new ItemStack[] { new ItemStack(required, 1, 2) }));
    }

    @Test
    void fullIndexProviderCarriesNoFallbackConstraint() {
        RecipeConstraintProviderRegistry registry = new RecipeConstraintProviderRegistry();
        registry.register(FilterRecipe.class, recipe -> RecipeConstraintSpec.fullIndex());

        ProviderConstraintDecision decision = ProviderConstraintDecision.evaluate(
            registry, new FilterRecipe());

        assertEquals(ProviderConstraintDecision.Kind.FULL_INDEX, decision.kind);
        assertTrue(decision.constraint.isUnconstrained());
    }

    @Test
    void providerExceptionBecomesOpaqueFailure() {
        RecipeConstraintProviderRegistry registry = new RecipeConstraintProviderRegistry();
        registry.register(FilterRecipe.class, recipe -> { throw new IllegalStateException("broken"); });

        ProviderConstraintDecision decision = ProviderConstraintDecision.evaluate(
            registry, new FilterRecipe());

        assertEquals(ProviderConstraintDecision.Kind.FAILURE, decision.kind);
        assertEquals(IllegalStateException.class, decision.failureClass);
        assertTrue(decision.constraint.isUnconstrained());
    }

    @Test
    void absentOrNullProviderDescriptionLeavesStructuralClassifierInCharge() {
        RecipeConstraintProviderRegistry registry = new RecipeConstraintProviderRegistry();
        assertEquals(ProviderConstraintDecision.Kind.NONE,
            ProviderConstraintDecision.evaluate(registry, new FilterRecipe()).kind);

        registry.register(FilterRecipe.class, recipe -> null);
        ProviderConstraintDecision decision = ProviderConstraintDecision.evaluate(
            registry, new FilterRecipe());
        assertEquals(ProviderConstraintDecision.Kind.NONE, decision.kind);
        assertNull(decision.failureClass);
    }

    private static final class FilterRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
        @Override public boolean matches(InventoryCrafting inv, World worldIn) { return false; }
        @Override public ItemStack getCraftingResult(InventoryCrafting inv) { return null; }
        @Override public boolean canFit(int width, int height) { return false; }
        @Override public ItemStack getRecipeOutput() { return null; }
    }
}
