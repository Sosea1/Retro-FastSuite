package com.sosea1.fastsuite112.recipe;

import com.sosea1.fastsuite112.config.FastSuiteConfig;

/**
 * Runtime-only troubleshooting control for the indexedCrafting switch.
 *
 * <p>This deliberately reuses {@link FastSuiteConfig#indexedCrafting} instead of adding another
 * branch to RecipeIndex.candidatesFor(). The hot path reads the existing config flag; this helper is never consulted by recipe iteration. Changes made here are not written to disk.</p>
 */
public final class RecipeRuntimeControl {
    public enum Mode {
        CONFIG,
        INDEXED,
        VANILLA
    }

    private static volatile Mode mode = Mode.CONFIG;
    private static volatile boolean baselineCaptured;
    private static volatile boolean baselineIndexed;

    private RecipeRuntimeControl() {}

    public static synchronized Mode setMode(Mode requested) {
        if (requested == null) throw new IllegalArgumentException("requested");
        captureBaselineIfNeeded();
        mode = requested;
        switch (requested) {
            case CONFIG:
                FastSuiteConfig.indexedCrafting = baselineIndexed;
                break;
            case INDEXED:
                FastSuiteConfig.indexedCrafting = true;
                break;
            case VANILLA:
                FastSuiteConfig.indexedCrafting = false;
                break;
            default:
                throw new IllegalStateException("Unknown FastSuite runtime mode: " + requested);
        }
        return mode;
    }

    public static Mode getMode() {
        return mode;
    }

    public static boolean isIndexActuallyEnabled() {
        return FastSuiteConfig.indexedCrafting;
    }

    private static void captureBaselineIfNeeded() {
        if (!baselineCaptured) {
            baselineIndexed = FastSuiteConfig.indexedCrafting;
            baselineCaptured = true;
        }
    }
}
