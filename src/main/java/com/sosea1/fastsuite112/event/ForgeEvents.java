package com.sosea1.fastsuite112.event;

import com.sosea1.fastsuite112.FastSuite112;
import com.sosea1.fastsuite112.recipe.RecipeIndex;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreDictionary;

/** Runtime invalidation hooks for mutable 1.12.2 data sources used by indexed ingredients. */
@Mod.EventBusSubscriber(modid = FastSuite112.MOD_ID)
public final class ForgeEvents {
    private ForgeEvents() {}

    @SubscribeEvent
    public static void onOreRegistered(OreDictionary.OreRegisterEvent event) {
        RecipeIndex.INSTANCE.invalidate("OreDictionary registration: " + event.getName());
    }

    @SubscribeEvent
    public static void onRecipeRegistered(RegistryEvent.Register<IRecipe> event) {
        // Registry mutation itself is also observed by ForgeRecipeRegistryMutationMixin. This event
        // remains a cheap registration-phase checkpoint in case another transformer bypasses the
        // normal ForgeRegistry entry point.
        RecipeIndex.INSTANCE.invalidate("RegistryEvent.Register<IRecipe> checkpoint");
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!FastSuite112.MOD_ID.equals(event.getModID())) return;
        RecipeIndex.INSTANCE.invalidateClassification("FastSuite config changed");
    }
}
