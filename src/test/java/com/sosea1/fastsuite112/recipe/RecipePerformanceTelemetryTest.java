package com.sosea1.fastsuite112.recipe;

import com.sosea1.fastsuite112.config.FastSuiteConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RecipePerformanceTelemetryTest {
    private boolean originalIndexed;

    @BeforeEach
    void setUp() {
        originalIndexed = FastSuiteConfig.indexedCrafting;
        FastSuiteConfig.indexedCrafting = true;
        RecipePerformanceTelemetry.INSTANCE.setEnabled(true);
        RecipePerformanceTelemetry.INSTANCE.reset();
    }

    @AfterEach
    void tearDown() {
        RecipePerformanceTelemetry.INSTANCE.setEnabled(false);
        RecipePerformanceTelemetry.INSTANCE.reset();
        FastSuiteConfig.indexedCrafting = originalIndexed;
    }

    @Test
    void samplesOneInSixtyFourAndSeparatesBackends() {
        for (int i = 0; i < 64; i++) {
            RecipePerformanceTelemetry.INSTANCE.enter(RecipePerformanceTelemetry.Scope.CRAFTING_MANAGER_FIRST_MATCH);
            RecipePerformanceTelemetry.INSTANCE.exit();
        }
        FastSuiteConfig.indexedCrafting = false;
        for (int i = 0; i < 64; i++) {
            RecipePerformanceTelemetry.INSTANCE.enter(RecipePerformanceTelemetry.Scope.UNIVERSAL_TWEAKS_DEFAULT_MISS);
            RecipePerformanceTelemetry.INSTANCE.exit();
        }

        RecipePerformanceTelemetry.Snapshot snapshot = RecipePerformanceTelemetry.INSTANCE.snapshot();
        assertEquals(1L, snapshot.totalSamples(RecipePerformanceTelemetry.Backend.INDEXED));
        assertEquals(1L, snapshot.totalSamples(RecipePerformanceTelemetry.Backend.VANILLA));
        assertEquals(1L, snapshot.samples(
            RecipePerformanceTelemetry.Backend.INDEXED,
            RecipePerformanceTelemetry.Scope.CRAFTING_MANAGER_FIRST_MATCH));
        assertEquals(1L, snapshot.samples(
            RecipePerformanceTelemetry.Backend.VANILLA,
            RecipePerformanceTelemetry.Scope.UNIVERSAL_TWEAKS_DEFAULT_MISS));
    }

    @Test
    void nestedScopeDoesNotDoubleCount() {
        for (int i = 0; i < 64; i++) {
            RecipePerformanceTelemetry.INSTANCE.enter(RecipePerformanceTelemetry.Scope.CRAFTING_MANAGER_FIRST_MATCH);
            RecipePerformanceTelemetry.INSTANCE.enter(RecipePerformanceTelemetry.Scope.UNIVERSAL_TWEAKS_DEFAULT_MISS);
            RecipePerformanceTelemetry.INSTANCE.exit();
            RecipePerformanceTelemetry.INSTANCE.exit();
        }
        RecipePerformanceTelemetry.Snapshot snapshot = RecipePerformanceTelemetry.INSTANCE.snapshot();
        assertEquals(1L, snapshot.totalSamples(RecipePerformanceTelemetry.Backend.INDEXED));
        assertEquals(1L, snapshot.samples(
            RecipePerformanceTelemetry.Backend.INDEXED,
            RecipePerformanceTelemetry.Scope.CRAFTING_MANAGER_FIRST_MATCH));
        assertEquals(0L, snapshot.samples(
            RecipePerformanceTelemetry.Backend.INDEXED,
            RecipePerformanceTelemetry.Scope.UNIVERSAL_TWEAKS_DEFAULT_MISS));
    }


    @Test
    void repeatedRootScopeRecoversAbandonedFrame() {
        RecipePerformanceTelemetry.INSTANCE.enter(RecipePerformanceTelemetry.Scope.CRAFTING_MANAGER_FIRST_MATCH);
        // Simulate an exception escaping before the RETURN injection by deliberately omitting exit().
        RecipePerformanceTelemetry.INSTANCE.enter(RecipePerformanceTelemetry.Scope.CRAFTING_MANAGER_FIRST_MATCH);
        RecipePerformanceTelemetry.INSTANCE.exit();

        RecipePerformanceTelemetry.Snapshot snapshot = RecipePerformanceTelemetry.INSTANCE.snapshot();
        assertEquals(1L, snapshot.abandonedFrameRecoveries);
    }

    @Test
    void disablingProfilerDropsAnyOpenFrame() {
        RecipePerformanceTelemetry.INSTANCE.enter(RecipePerformanceTelemetry.Scope.CRAFTING_MANAGER_FIRST_MATCH);
        RecipePerformanceTelemetry.INSTANCE.setEnabled(false);
        RecipePerformanceTelemetry.INSTANCE.setEnabled(true);
        RecipePerformanceTelemetry.INSTANCE.enter(RecipePerformanceTelemetry.Scope.CRAFTING_MANAGER_FIRST_MATCH);
        RecipePerformanceTelemetry.INSTANCE.exit();
        assertEquals(0L, RecipePerformanceTelemetry.INSTANCE.snapshot().abandonedFrameRecoveries);
    }

    @Test
    void runtimeControlReusesExistingConfigSwitch() {
        FastSuiteConfig.indexedCrafting = true;
        RecipeRuntimeControl.setMode(RecipeRuntimeControl.Mode.VANILLA);
        assertFalse(FastSuiteConfig.indexedCrafting);
        RecipeRuntimeControl.setMode(RecipeRuntimeControl.Mode.INDEXED);
        assertTrue(FastSuiteConfig.indexedCrafting);
        RecipeRuntimeControl.setMode(RecipeRuntimeControl.Mode.CONFIG);
        assertTrue(FastSuiteConfig.indexedCrafting);
    }
}
