package com.sosea1.fastsuite112;

import com.sosea1.fastsuite112.command.CommandFastSuite;
import com.sosea1.fastsuite112.compat.MixinHookTracker;
import com.sosea1.fastsuite112.config.FastSuiteConfig;
import com.sosea1.fastsuite112.recipe.BuiltInConstraintProviders;
import com.sosea1.fastsuite112.recipe.RecipeIndex;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

@Mod(
    modid = FastSuite112.MOD_ID,
    name = FastSuite112.NAME,
    version = FastSuite112.VERSION,
    acceptableRemoteVersions = "*"
)
public final class FastSuite112 {
    public static final String MOD_ID = "fastsuite";
    public static final String NAME = "Retro FastSuite";
    public static final String VERSION = "1.0.2";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    private static final Class<?> DEV_TOOLS = findDevTools();

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        BuiltInConstraintProviders.registerPresent();
        // Build lazily after registration/CraftTweaker mutation has settled.
        RecipeIndex.INSTANCE.invalidate("post-init recipe registry checkpoint");
        invokeDevHook("postInit", new Class<?>[0], new Object[0]);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Final cheap lifecycle boundary before normal gameplay. This deliberately invalidates even
        // when Forge registry identity did not change, covering late in-place recipe mutations.
        RecipeIndex.INSTANCE.invalidate("server-start recipe lifecycle checkpoint");
        event.registerServerCommand(new CommandFastSuite());
        logCompatibilityWarnings();
        invokeDevHook("serverStarting", new Class<?>[] { FMLServerStartingEvent.class }, new Object[] { event });
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        invokeDevHook("serverStopping", new Class<?>[] { FMLServerStoppingEvent.class }, new Object[] { event });
        RecipeIndex.INSTANCE.clear();
    }

    private static void logCompatibilityWarnings() {
        if (FastSuiteConfig.indexedCrafting && !MixinHookTracker.hasAnyLookupHookApplied()) {
            LOGGER.warn("Indexed crafting is enabled, but neither the vanilla nor Universal Tweaks lookup hook was applied. " +
                "FastSuite will not accelerate crafting lookups; run /fastsuite stats for diagnostics.");
        }

        if (Loader.isModLoaded("crafttweaker") && !MixinHookTracker.craftTweakerRefreshHookApplied) {
            LOGGER.warn("CraftTweaker is installed but the optional recipe-refresh checkpoint hook was not applied. " +
                "Forge registry mutations are still observed, but in-place CraftTweaker recipe changes may require /fastsuite rebuild.");
        }
    }

    private static Class<?> findDevTools() {
        try {
            return Class.forName("com.sosea1.fastsuite112.dev.FastSuiteDevTools", false, FastSuite112.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static void invokeDevHook(String name, Class<?>[] parameterTypes, Object[] arguments) {
        if (DEV_TOOLS == null) return;
        try {
            Method method = DEV_TOOLS.getMethod(name, parameterTypes);
            method.invoke(null, arguments);
        } catch (ReflectiveOperationException | LinkageError failure) {
            LOGGER.warn("Development tools hook {} failed: {}", name, failure.toString());
        }
    }
}
