package dev.sosea1.fastsuite112.mixin;

import dev.sosea1.fastsuite112.compat.MixinHookTracker;
import dev.sosea1.fastsuite112.recipe.RecipeIndex;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;

/**
 * Minimal vanilla hook: only replace the iterator that CraftingManager would linearly scan.
 * The surrounding vanilla matching/result/remaining-item logic remains untouched.
 *
 * priority=500 ensures this hook applies before higher-priority third-party overwrites (e.g. Universal Tweaks at 1000).
 * require=0 is intentional. Universal Tweaks may overwrite these method bodies; in that case its
 * dedicated compatibility mixin becomes the active iterator integration point instead of causing a crash.
 */
@Mixin(value = CraftingManager.class, priority = 500)
public abstract class CraftingManagerMixin {
    static {
        MixinHookTracker.vanillaCraftingHookApplied = true;
    }

    @Redirect(
        method = "findMatchingRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/registry/RegistryNamespaced;iterator()Ljava/util/Iterator;"
        ),
        require = 0
    )
    private static Iterator<IRecipe> fastsuite112$indexedFindRecipeIterator(
        RegistryNamespaced<ResourceLocation, IRecipe> registry,
        InventoryCrafting matrix,
        World world
    ) {
        MixinHookTracker.vanillaCraftingHookActive = true;
        return RecipeIndex.INSTANCE.candidatesFor(matrix).iterator();
    }

    @Redirect(
        method = "findMatchingResult",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/registry/RegistryNamespaced;iterator()Ljava/util/Iterator;"
        ),
        require = 0
    )
    private static Iterator<IRecipe> fastsuite112$indexedFindResultIterator(
        RegistryNamespaced<ResourceLocation, IRecipe> registry,
        InventoryCrafting matrix,
        World world
    ) {
        return RecipeIndex.INSTANCE.candidatesFor(matrix).iterator();
    }

    @Redirect(
        method = "getRemainingItems",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/registry/RegistryNamespaced;iterator()Ljava/util/Iterator;"
        ),
        require = 0
    )
    private static Iterator<IRecipe> fastsuite112$indexedRemainderIterator(
        RegistryNamespaced<ResourceLocation, IRecipe> registry,
        InventoryCrafting matrix,
        World world
    ) {
        return RecipeIndex.INSTANCE.candidatesFor(matrix).iterator();
    }
}
