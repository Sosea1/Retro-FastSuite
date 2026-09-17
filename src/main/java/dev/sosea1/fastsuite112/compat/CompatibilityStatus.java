package dev.sosea1.fastsuite112.compat;

import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;

/** Best-effort, dependency-free compatibility diagnostics. Never used from the crafting hot path. */
public final class CompatibilityStatus {
    private CompatibilityStatus() {}

    public static Snapshot snapshot() {
        boolean craftTweaker = Loader.isModLoaded("crafttweaker");
        boolean universalTweaks = Loader.isModLoaded("universaltweaks");
        boolean fastWorkbench = Loader.isModLoaded("fastbench");
        ToggleState utCraftingCache = universalTweaks
            ? detectUniversalTweaksCraftingCache()
            : ToggleState.NOT_INSTALLED;

        return new Snapshot(craftTweaker, universalTweaks, utCraftingCache, fastWorkbench);
    }

    private static ToggleState detectUniversalTweaksCraftingCache() {
        // Universal Tweaks deliberately keeps its Crafting Cache independently toggleable. Resolve
        // the current value reflectively so FastSuite does not acquire a hard compile/runtime dep.
        try {
            Class<?> config = Class.forName(
                "mod.acgaming.universaltweaks.config.UTConfigTweaks",
                false,
                CompatibilityStatus.class.getClassLoader()
            );
            Field performanceField = config.getField("PERFORMANCE");
            Object performance = performanceField.get(null);
            if (performance == null) return ToggleState.UNKNOWN;

            Field cacheToggle = performance.getClass().getField("utCraftingCacheToggle");
            return cacheToggle.getBoolean(performance) ? ToggleState.ENABLED : ToggleState.DISABLED;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return ToggleState.UNKNOWN;
        }
    }

    public enum ToggleState {
        ENABLED,
        DISABLED,
        UNKNOWN,
        NOT_INSTALLED
    }

    public static final class Snapshot {
        public final boolean craftTweakerInstalled;
        public final boolean universalTweaksInstalled;
        public final ToggleState universalTweaksCraftingCache;
        public final boolean fastWorkbenchInstalled;

        private Snapshot(boolean craftTweakerInstalled,
                         boolean universalTweaksInstalled,
                         ToggleState universalTweaksCraftingCache,
                         boolean fastWorkbenchInstalled) {
            this.craftTweakerInstalled = craftTweakerInstalled;
            this.universalTweaksInstalled = universalTweaksInstalled;
            this.universalTweaksCraftingCache = universalTweaksCraftingCache;
            this.fastWorkbenchInstalled = fastWorkbenchInstalled;
        }
    }
}
