package dev.sosea1.fastsuite112.mixin.dev;

import dev.sosea1.fastsuite112.recipe.RecipePerformanceTelemetry;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingManager.class)
public abstract class CraftingManagerProfilerMixin {
    @Inject(
        method = {"func_192413_b", "findMatchingRecipe"},
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private static void fastsuite112$devProfileEnter(InventoryCrafting matrix, World world, CallbackInfoReturnable<IRecipe> cir) {
        RecipePerformanceTelemetry.INSTANCE.enter(RecipePerformanceTelemetry.Scope.CRAFTING_MANAGER_FIRST_MATCH);
    }

    @Inject(
        method = {"func_192413_b", "findMatchingRecipe"},
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
    private static void fastsuite112$devProfileExit(InventoryCrafting matrix, World world, CallbackInfoReturnable<IRecipe> cir) {
        RecipePerformanceTelemetry.INSTANCE.exit();
    }
}
