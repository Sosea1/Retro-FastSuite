package com.sosea1.fastsuite112.mixin.compat;

import com.sosea1.fastsuite112.compat.MixinHookTracker;
import com.sosea1.fastsuite112.recipe.RecipeIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional CraftTweaker 1.12 lifecycle bridge.
 *
 * <p>CraftTweaker applies recipe actions during post-init and refreshes its recipe view after each
 * mutation batch. Observing that refresh gives FastSuite a late, explicit invalidation checkpoint
 * even when an integration mutates an already-registered recipe object rather than replacing the
 * Forge registry entry. CraftTweaker is not a compile/runtime dependency of FastSuite.</p>
 */
@Pseudo
@Mixin(targets = "crafttweaker.mc1120.recipes.MCRecipeManager", remap = false)
public abstract class CraftTweakerRecipeRefreshMixin {
    static {
        MixinHookTracker.craftTweakerRefreshHookApplied = true;
    }

    @Inject(method = "refreshRecipes", at = @At("RETURN"), require = 0, remap = false)
    private static void fastsuite112$afterRecipeRefresh(CallbackInfo ci) {
        MixinHookTracker.craftTweakerRefreshHookActive = true;
        RecipeIndex.INSTANCE.invalidate("CraftTweaker MCRecipeManager.refreshRecipes checkpoint");
    }
}
