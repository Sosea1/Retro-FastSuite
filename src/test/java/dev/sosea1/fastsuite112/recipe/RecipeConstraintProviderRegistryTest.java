package dev.sosea1.fastsuite112.recipe;

import dev.sosea1.fastsuite112.api.RecipeConstraintSpec;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RecipeConstraintProviderRegistryTest {

    @Test
    void providerAppliesOnlyToItsExactRegisteredClass() {
        RecipeConstraintProviderRegistry registry = new RecipeConstraintProviderRegistry();
        registry.register(BaseRecipe.class, recipe -> RecipeConstraintSpec.fullIndex());

        RecipeConstraintProviderRegistry.Result base = registry.describe(new BaseRecipe());
        assertEquals(RecipeConstraintSpec.Mode.FULL_INDEX, base.spec.getMode());
        assertNull(registry.describe(new ChildRecipe()).spec);
    }

    @Test
    void laterRegistrationReplacesExactClassProvider() {
        RecipeConstraintProviderRegistry registry = new RecipeConstraintProviderRegistry();
        registry.register(BaseRecipe.class, recipe -> RecipeConstraintSpec.fullIndex());
        registry.register(BaseRecipe.class, recipe -> null);

        assertNull(registry.describe(new BaseRecipe()).spec);
    }

    @Test
    void providerFailureIsReturnedForFailOpenHandling() {
        RecipeConstraintProviderRegistry registry = new RecipeConstraintProviderRegistry();
        registry.register(BaseRecipe.class, recipe -> { throw new IllegalStateException("broken provider"); });

        RecipeConstraintProviderRegistry.Result result = registry.describe(new BaseRecipe());
        assertNull(result.spec);
        assertTrue(result.hasProvider);
        assertTrue(result.failure instanceof IllegalStateException);
    }

    @Test
    void missingProviderIsDistinguishedFromNullDescription() {
        RecipeConstraintProviderRegistry registry = new RecipeConstraintProviderRegistry();
        RecipeConstraintProviderRegistry.Result result = registry.describe(new BaseRecipe());

        assertFalse(result.hasProvider);
        assertNull(result.spec);
        assertNull(result.failure);
    }

    private static class BaseRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
        @Override public boolean matches(InventoryCrafting inv, World worldIn) { return false; }
        @Override public ItemStack getCraftingResult(InventoryCrafting inv) { return null; }
        @Override public boolean canFit(int width, int height) { return false; }
        @Override public ItemStack getRecipeOutput() { return null; }
    }

    private static final class ChildRecipe extends BaseRecipe {}
}
