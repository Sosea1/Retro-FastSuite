package dev.sosea1.fastsuite112.mixin.dev;

import dev.sosea1.fastsuite112.recipe.FallbackMatchTelemetry;
import dev.sosea1.fastsuite112.recipe.RecipeIndex;
import dev.sosea1.fastsuite112.recipe.RecipeLookup;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Development-only sampled attribution around the actual fallback recipe predicate. */
@Mixin(value = RecipeLookup.class, remap = false)
public abstract class RecipeLookupFallbackProfilerMixin {
    @Redirect(
        method = {"findFirstMatchingRecipe", "findAllMatchingRecipes", "visitMatchingRecipes"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/crafting/IRecipe;matches(Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/world/World;)Z",
            remap = false
        ),
        require = 0
    )
    private static boolean fastsuite112$profileFallbackMatchMcp(
        IRecipe recipe, InventoryCrafting inventory, World world) {
        return fastsuite112$profileFallbackMatch(recipe, inventory, world);
    }

    @Redirect(
        method = {"findFirstMatchingRecipe", "findAllMatchingRecipes", "visitMatchingRecipes"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/crafting/IRecipe;func_77569_a(Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/world/World;)Z",
            remap = false
        ),
        require = 0
    )
    private static boolean fastsuite112$profileFallbackMatchSrg(
        IRecipe recipe, InventoryCrafting inventory, World world) {
        return fastsuite112$profileFallbackMatch(recipe, inventory, world);
    }

    private static boolean fastsuite112$profileFallbackMatch(
        IRecipe recipe, InventoryCrafting inventory, World world) {
        FallbackMatchTelemetry telemetry = FallbackMatchTelemetry.INSTANCE;
        if (!telemetry.shouldSample() || !RecipeIndex.INSTANCE.isFallbackRecipe(recipe)) {
            return recipe.matches(inventory, world);
        }

        long started = System.nanoTime();
        try {
            boolean result = recipe.matches(inventory, world);
            telemetry.record(recipe.getClass().getName(), System.nanoTime() - started, false);
            return result;
        } catch (RuntimeException | Error failure) {
            telemetry.record(recipe.getClass().getName(), System.nanoTime() - started, true);
            throw failure;
        }
    }
}
