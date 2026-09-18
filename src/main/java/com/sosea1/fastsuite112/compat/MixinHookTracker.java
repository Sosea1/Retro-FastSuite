package com.sosea1.fastsuite112.compat;

/**
 * Tracks the live application and runtime execution status of FastSuite's mixin hooks.
 * This prevents "silent fallback" where the index is reported as enabled but no hook is
 * actually intercepting crafting operations.
 */
public final class MixinHookTracker {
    private MixinHookTracker() {}

    public static volatile boolean vanillaCraftingHookApplied = false;
    public static volatile boolean vanillaCraftingHookActive = false;

    public static volatile boolean universalTweaksHookApplied = false;
    public static volatile boolean universalTweaksHookActive = false;

    public static volatile boolean forgeRegistryHookApplied = false;
    public static volatile boolean forgeRegistryHookActive = false;

    public static volatile boolean ingredientCacheFixApplied = false;
    public static volatile boolean ingredientCacheFixActive = false;

    /**
     * Returns true if at least one crafting lookup hook (Vanilla CraftingManager or Universal Tweaks)
     * was transformed and applied to target classes.
     */
    public static boolean hasAnyLookupHookApplied() {
        return vanillaCraftingHookApplied || universalTweaksHookApplied;
    }

    /**
     * Returns true if at least one crafting lookup hook has actually intercepted a crafting call.
     */
    public static boolean hasAnyLookupHookActive() {
        return vanillaCraftingHookActive || universalTweaksHookActive;
    }

    public static String formatHookState(boolean applied, boolean active) {
        if (!applied) return "not applied";
        if (active) return "ACTIVE";
        return "applied (idle)";
    }
}
