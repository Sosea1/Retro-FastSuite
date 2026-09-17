package dev.sosea1.fastsuite112.mixin.compat;

import dev.sosea1.fastsuite112.compat.MixinHookTracker;
import dev.sosea1.fastsuite112.recipe.RecipeIndex;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;

/**
 * Universal Tweaks Crafting Cache integration.
 *
 * UT remains the L1 cache (last recipe/hash cache). Only its expensive default linear fallback is
 * redirected to our index. This avoids two competing CraftingManager overwrites and preserves UT's
 * cache semantics.
 *
 */
@Pseudo
@Mixin(
    targets = "mod.acgaming.universaltweaks.tweaks.performance.craftingcache.UTCraftingCache",
    remap = false
)
public abstract class UniversalTweaksCraftingCacheMixin {
    static {
        MixinHookTracker.universalTweaksHookApplied = true;
    }

    @Redirect(
        method = "findMatchingRecipeDefault",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/registry/RegistryNamespaced;iterator()Ljava/util/Iterator;",
            remap = true
        ),
        remap = false,
        require = 0
    )
    private static Iterator<IRecipe> fastsuite112$replaceLinearFallback(
        RegistryNamespaced<ResourceLocation, IRecipe> registry,
        InventoryCrafting matrix,
        World world
    ) {
        MixinHookTracker.universalTweaksHookActive = true;
        return RecipeIndex.INSTANCE.candidatesFor(matrix).iterator();
    }
}
