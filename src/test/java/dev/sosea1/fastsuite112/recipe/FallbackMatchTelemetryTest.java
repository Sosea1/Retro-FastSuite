package dev.sosea1.fastsuite112.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FallbackMatchTelemetryTest {

    @Test
    void recordsCallsTimeMaximumAndFailuresByClass() {
        FallbackMatchTelemetry telemetry = new FallbackMatchTelemetry(1);
        telemetry.setEnabled(true);

        telemetry.record("example.Recipe", 400L, false);
        telemetry.record("example.Recipe", 600L, true);

        FallbackMatchTelemetry.Snapshot snapshot = telemetry.snapshot();
        assertEquals(1, snapshot.groups.size());
        FallbackMatchTelemetry.Group group = snapshot.groups.get(0);
        assertEquals(2L, group.calls);
        assertEquals(1000L, group.totalNanos);
        assertEquals(600L, group.maxNanos);
        assertEquals(1L, group.failures);
    }

    @Test
    void sortsByTotalTimeThenCallsThenClassName() {
        FallbackMatchTelemetry telemetry = new FallbackMatchTelemetry(1);
        telemetry.setEnabled(true);
        telemetry.record("z.Slow", 1000L, false);
        telemetry.record("a.Many", 500L, false);
        telemetry.record("a.Many", 500L, false);
        telemetry.record("b.Many", 1000L, false);

        FallbackMatchTelemetry.Snapshot snapshot = telemetry.snapshot();
        assertEquals("a.Many", snapshot.groups.get(0).recipeClass);
        assertEquals("b.Many", snapshot.groups.get(1).recipeClass);
        assertEquals("z.Slow", snapshot.groups.get(2).recipeClass);
    }

    @Test
    void disabledTelemetryNeitherSamplesNorRecords() {
        FallbackMatchTelemetry telemetry = new FallbackMatchTelemetry(64);
        assertFalse(telemetry.shouldSample());
        telemetry.record("example.Recipe", 100L, false);
        assertTrue(telemetry.snapshot().groups.isEmpty());
    }

    @Test
    void resetClearsGroupsAndSamplingSequence() {
        FallbackMatchTelemetry telemetry = new FallbackMatchTelemetry(2);
        telemetry.setEnabled(true);
        assertFalse(telemetry.shouldSample());
        assertTrue(telemetry.shouldSample());
        telemetry.record("example.Recipe", 100L, false);

        telemetry.reset();

        assertTrue(telemetry.snapshot().groups.isEmpty());
        assertFalse(telemetry.shouldSample());
    }
}
