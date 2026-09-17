package dev.sosea1.fastsuite112.mixin.dev;

import dev.sosea1.fastsuite112.recipe.RecipePerformanceTelemetry;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mod.acgaming.universaltweaks.tweaks.performance.craftingcache.UTCraftingCache", remap = false)
public abstract class UniversalTweaksProfilerMixin {
    @Inject(method = "findMatchingRecipeDefault", at = @At("HEAD"), remap = false, require = 0)
    private static void fastsuite112$devProfileEnter(InventoryCrafting matrix, World world, CallbackInfoReturnable<IRecipe> cir) {
        RecipePerformanceTelemetry.INSTANCE.enter(RecipePerformanceTelemetry.Scope.UNIVERSAL_TWEAKS_DEFAULT_MISS);
    }

    @Inject(method = "findMatchingRecipeDefault", at = @At("RETURN"), remap = false, require = 0)
    private static void fastsuite112$devProfileExit(InventoryCrafting matrix, World world, CallbackInfoReturnable<IRecipe> cir) {
        RecipePerformanceTelemetry.INSTANCE.exit();
    }
}
