package dev.sosea1.fastsuite112.recipe;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class RecipeSafetyClassifierTest {

    @Test
    void inheritedTrustedRecipeImplementationIsAccepted() {
        assertEquals(RecipeSafetyClassifier.Safety.INHERITED_TRUSTED_IMPLEMENTATION,
            RecipeSafetyClassifier.INSTANCE.classifyRecipe(InheritedShapedRecipe.class));
    }

    @Test
    void overridingTrustedRecipeMethodIsRejected() {
        assertEquals(RecipeSafetyClassifier.Safety.UNKNOWN,
            RecipeSafetyClassifier.INSTANCE.classifyRecipe(OverridingShapedRecipe.class));
    }

    @Test
    void unrelatedSameSignatureMethodDoesNotHideInheritedTrustedRecipeMethod() {
        assertEquals(RecipeSafetyClassifier.Safety.INHERITED_TRUSTED_IMPLEMENTATION,
            RecipeSafetyClassifier.INSTANCE.classifyRecipe(UnrelatedSameSignatureShapedRecipe.class));
    }

    @Test
    void ambiguousTrustedBaseSignatureFailsClosed() {
        assertNull(RecipeSafetyClassifier.findPublicMethodBySignature(
            AmbiguousTrustedBase.class, boolean.class, String.class));
    }

    private static class InheritedShapedRecipe extends ShapedRecipes {
        private InheritedShapedRecipe() {
            super("", 0, 0, NonNullList.<Ingredient>create(), ItemStack.EMPTY);
        }
    }

    private static final class OverridingShapedRecipe extends InheritedShapedRecipe {
        @Override
        public boolean matches(InventoryCrafting inventory, World world) {
            return false;
        }
    }

    private static final class UnrelatedSameSignatureShapedRecipe extends InheritedShapedRecipe {
        public boolean inspect(InventoryCrafting inventory, World world) {
            return false;
        }
    }

    private static final class AmbiguousTrustedBase {
        public boolean first(String value) {
            return false;
        }

        public boolean second(String value) {
            return false;
        }
    }
}
