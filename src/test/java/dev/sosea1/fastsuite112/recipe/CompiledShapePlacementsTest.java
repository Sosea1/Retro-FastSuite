package dev.sosea1.fastsuite112.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledShapePlacementsTest {
    @Test
    public void compilesOffsetsAndMirrorsForThreeByThree() {
        byte[] width = {(byte) 2};
        byte[] height = {(byte) 1};
        long[] mask = {0b01L};
        long[] mirrored = {0b10L};
        CompiledShapePlacements compiled = CompiledShapePlacements.compile(
            width, height, mask, mirrored, new byte[1]);

        assertTrue(compiled.matches(0, 3, 3, 1L << 0));
        assertTrue(compiled.matches(0, 3, 3, 1L << 1));
        assertTrue(compiled.matches(0, 3, 3, 1L << 8));
        assertFalse(compiled.matches(0, 3, 3, (1L << 0) | (1L << 1)));
    }

    @Test
    public void rejectsRecipeLargerThanGrid() {
        byte[] width = {(byte) 3};
        byte[] height = {(byte) 3};
        long[] mask = {0x1FFL};
        CompiledShapePlacements compiled = CompiledShapePlacements.compile(
            width, height, mask, mask, new byte[1]);
        assertFalse(compiled.matches(0, 2, 2, 0xFL));
    }

    @Test
    void skipsRecipesHandledByPositionalPlacements() {
        byte[] width = {(byte) 1, (byte) 1};
        byte[] height = {(byte) 1, (byte) 1};
        long[] mask = {1L, 1L};
        byte[] positionalProbeCount = {(byte) 1, (byte) 0};

        CompiledShapePlacements compiled = CompiledShapePlacements.compile(
            width, height, mask, mask, positionalProbeCount);

        assertFalse(compiled.matches(0, 2, 2, 1L));
        assertTrue(compiled.matches(1, 2, 2, 1L));
    }

    @Test
    void returnsEmptyWhenEveryShapeHasPositionalPlacements() {
        CompiledShapePlacements compiled = CompiledShapePlacements.compile(
            new byte[] {(byte) 1},
            new byte[] {(byte) 1},
            new long[] {1L},
            new long[] {1L},
            new byte[] {(byte) 1});

        assertFalse(compiled.supports(2, 2));
    }
}
