package com.sosea1.fastsuite112.core;

import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;
import zone.rong.mixinbooter.service.ModDiscoverer;

import java.io.File;

/**
 * MixinBooter 11+ connector.
 */
public final class FastSuiteMixinConnector implements IMixinConnector {
    @Override
    public void connect() {
        // Registry observer is always queued to keep index lifecycle synced with Forge registries
        Mixins.addConfiguration("mixins.fastsuite112.registry.json");

        // Independent client-side Forge 1.12 packed-ingredient race workaround
        Mixins.addConfiguration("mixins.fastsuite112.fix.ingredientcache.json");

        // When Universal Tweaks has Crafting Cache enabled, it overwrites CraftingManager methods.
        // We accelerate UT's fallback via UniversalTweaksCraftingCacheMixin instead of hooking CraftingManager.
        // If UT is absent or its Crafting Cache is explicitly disabled, we load the vanilla CraftingManager hook.
        boolean utCacheActive = isUniversalTweaksCraftingCacheActive();
        if (utCacheActive) {
            Mixins.addConfiguration("mixins.fastsuite112.compat.universaltweaks.json");
        } else {
            Mixins.addConfiguration("mixins.fastsuite112.json");
        }

        // Development JARs add opt-in profiling mixins. The normal release has no dev classes, so
        // these configurations are never queued and the crafting hot path contains no profiler hooks.
        if (classPresent("com.sosea1.fastsuite112.recipe.RecipePerformanceTelemetry")) {
            Mixins.addConfiguration("mixins.fastsuite112.dev.json");
            if (ModDiscoverer.isModPresent("universaltweaks")) {
                Mixins.addConfiguration("mixins.fastsuite112.dev.universaltweaks.json");
            }
        }
    }

    private static boolean isUniversalTweaksCraftingCacheActive() {
        if (!ModDiscoverer.isModPresent("universaltweaks")) {
            return false;
        }

        File[] candidateFiles = new File[] {
            new File("config/Universal Tweaks - Tweaks.cfg"),
            new File("config/universaltweaks.cfg"),
            new File("config/UniversalTweaks.cfg")
        };

        for (File cfg : candidateFiles) {
            if (cfg.isFile() && cfg.canRead()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(cfg))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("B:\"Crafting Cache\"=")) {
                            String val = line.substring("B:\"Crafting Cache\"=".length()).trim();
                            return Boolean.parseBoolean(val);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Default in Universal Tweaks is enabled
        return true;
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name, false, FastSuiteMixinConnector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
