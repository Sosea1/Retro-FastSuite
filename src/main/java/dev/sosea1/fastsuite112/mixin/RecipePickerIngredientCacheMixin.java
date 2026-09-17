package dev.sosea1.fastsuite112.mixin;

import dev.sosea1.fastsuite112.compat.MixinHookTracker;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Serializes lazy packed-stack cache construction per ingredient.
 *
 * <p>Forge 1.12 publishes the mutable IntArrayList used by Ingredient and OreIngredient before it
 * has finished filling it. Integrated client and server crafting lookups can consequently append
 * to the same list concurrently and corrupt IntArrayList's size/backing-array invariants. Every
 * vanilla use of this cache is inside RecipeItemHelper.RecipePicker, so locking these three call
 * sites fixes the race without serializing recipe scans or changing ingredient implementations.</p>
 */
@Mixin(targets = "net.minecraft.client.util.RecipeItemHelper$RecipePicker")
public abstract class RecipePickerIngredientCacheMixin {
    static {
        MixinHookTracker.ingredientCacheFixApplied = true;
    }

    @Redirect(
        method = {"<init>", "getUniqueAvailIngredientItems", "getMinIngredientCount"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/crafting/Ingredient;getValidItemStacksPacked()Lit/unimi/dsi/fastutil/ints/IntList;"
        ),
        require = 0,
        expect = 3
    )
    private IntList fastsuite112$threadSafePackedStacks(Ingredient ingredient) {
        MixinHookTracker.ingredientCacheFixActive = true;
        synchronized (ingredient) {
            return ingredient.getValidItemStacksPacked();
        }
    }
}
