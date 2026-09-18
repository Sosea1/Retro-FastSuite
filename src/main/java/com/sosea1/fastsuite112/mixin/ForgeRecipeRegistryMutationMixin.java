package com.sosea1.fastsuite112.mixin;

import com.sosea1.fastsuite112.compat.MixinHookTracker;
import com.sosea1.fastsuite112.recipe.RecipeIndex;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Observes actual Forge recipe-registry mutations instead of probing the entire registry from every
 * crafting lookup. This catches normal registration, same-key replacement, removal, clear, and registry synchronization.
 *
 * <p>It intentionally does not pretend to detect in-place mutation of fields inside an existing
 * IRecipe object. Mods that do that must call FastSuiteAPI.invalidateRecipeIndex().</p>
 */
@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRecipeRegistryMutationMixin {
    static {
        MixinHookTracker.forgeRegistryHookApplied = true;
    }

    @Shadow
    public abstract Class<?> getRegistrySuperType();

    @Inject(
        method = "add(ILnet/minecraftforge/registries/IForgeRegistryEntry;Ljava/lang/String;)I",
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
    private void fastsuite112$afterAdd(int id,
                                       IForgeRegistryEntry<?> value,
                                       String owner,
                                       CallbackInfoReturnable<Integer> cir) {
        fastsuite112$invalidateIfRecipeRegistry("ForgeRegistry.add: " +
            (value == null ? "<null>" : value.getRegistryName()));
    }

    @Inject(method = "remove", at = @At("RETURN"), require = 0, remap = false)
    private void fastsuite112$afterRemove(ResourceLocation key, CallbackInfoReturnable<?> cir) {
        // A failed/no-op remove is harmless to invalidate as this path is rare and never a lookup hot path.
        fastsuite112$invalidateIfRecipeRegistry("ForgeRegistry.remove: " + key);
    }

    @Inject(method = "clear", at = @At("RETURN"), require = 0, remap = false)
    private void fastsuite112$afterClear(CallbackInfo ci) {
        fastsuite112$invalidateIfRecipeRegistry("ForgeRegistry.clear");
    }

    @Inject(
        method = "sync(Lnet/minecraft/util/ResourceLocation;Lnet/minecraftforge/registries/ForgeRegistry;)V",
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
    private void fastsuite112$afterSync(ResourceLocation registryName,
                                        ForgeRegistry<?> from,
                                        CallbackInfo ci) {
        // sync() clears backing maps directly and then normally repopulates through add(). Hooking
        // RETURN as well covers the empty-registry case and provides one final generation boundary.
        fastsuite112$invalidateIfRecipeRegistry("ForgeRegistry.sync: " + registryName);
    }

    private void fastsuite112$invalidateIfRecipeRegistry(String reason) {
        if (getRegistrySuperType() == IRecipe.class) {
            MixinHookTracker.forgeRegistryHookActive = true;
            RecipeIndex.INSTANCE.invalidate(reason);
        }
    }
}
