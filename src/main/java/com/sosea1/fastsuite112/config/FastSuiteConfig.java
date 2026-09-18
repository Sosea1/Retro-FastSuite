package com.sosea1.fastsuite112.config;

import com.sosea1.fastsuite112.FastSuite112;
import net.minecraftforge.common.config.Config;

@Config(modid = FastSuite112.MOD_ID, name = FastSuite112.MOD_ID)
public final class FastSuiteConfig {
    @Config.Comment({
        "Enable the indexed recipe lookup backend.",
        "Disable only for compatibility troubleshooting; /fastsuite mode can also switch it at runtime."
    })
    public static boolean indexedCrafting = true;

    @Config.Comment({
        "Choose pivots using a registry-wide selectivity estimate instead of only the number",
        "of Item alternatives. This changes only which proven-necessary ingredient indexes a recipe."
    })
    public static boolean frequencyAwarePivotSelection = true;

    @Config.Comment({
        "Rebalance pivot assignments during index rebuild against the wildcard/exact-meta bucket",
        "loads they actually create. Costs rebuild time only; crafting lookups pay no extra branch."
    })
    public static boolean loadBalancedPivotSelection = true;

    @Config.Comment({
        "Enable second-stage candidate pruning before IRecipe.matches().",
        "Uses only necessary conditions proven from trusted recipe/ingredient implementations:",
        "occupied-slot count, compiled shaped occupancy, metadata-aware query signatures,",
        "multi-alternative fingerprints, and a collision-safe repeated-Item count sketch."
    })
    public static boolean advancedCandidatePruning = true;

    @Config.Comment({
        "Trust subclasses of known recipe/ingredient bases only when they inherit the exact",
        "matching/enumeration implementations used by the trusted base."
    })
    public static boolean automaticStructuralClassification = true;

    @Config.Comment({
        "Enable extra index rebuild/classification logging for troubleshooting.",
        "Disabled by default. This never enables per-lookup telemetry, profiling, fuzzing, or benchmarks."
    })
    public static boolean debugLogging = false;

    private FastSuiteConfig() {}
}
