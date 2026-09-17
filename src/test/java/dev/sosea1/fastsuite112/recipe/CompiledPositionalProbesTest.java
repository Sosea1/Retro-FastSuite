package dev.sosea1.fastsuite112.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledPositionalProbesTest {
    @Test
    public void acceptsNormalAndMirroredPlacements() {
        byte[] width = {(byte) 2};
        byte[] height = {(byte) 1};
        long[] mask = {0b11L};
        long[] mirrored = {0b11L};
        byte[] probeCount = {(byte) 2};
        byte[] probe1 = {(byte) 0};
        byte[] probe2 = {(byte) 1};
        byte[] probe3 = {0};

        // Probe signatures use the Item A/B channels only. Query tokens with the same low-byte
        // bits are guaranteed matches; the variant channels remain unused here.
        int sigA = 0x0011;
        int sigB = 0x0022;
        CompiledPositionalProbes compiled = CompiledPositionalProbes.compile(
            width, height, mask, mirrored,
            probeCount, probe1, probe2, probe3,
            new int[]{sigA}, new int[]{sigB}, new int[]{0});

        long normal = packTokens(sigA, sigB, 0, 0);
        assertTrue(compiled.matches(0, 2, 2, 0b0011L, normal, 0L, 0));

        long mirroredQuery = packTokens(sigB, sigA, 0, 0);
        assertTrue(compiled.matches(0, 2, 2, 0b0011L, mirroredQuery, 0L, 0));
    }

    @Test
    public void rejectsWrongPositionBeforeRecipeMatches() {
        byte[] width = {(byte) 2};
        byte[] height = {(byte) 1};
        long[] mask = {0b11L};
        byte[] probeCount = {(byte) 2};
        byte[] probe1 = {(byte) 0};
        byte[] probe2 = {(byte) 1};
        byte[] probe3 = {0};
        int sigA = 0x0011;
        int sigB = 0x0022;
        CompiledPositionalProbes compiled = CompiledPositionalProbes.compile(
            width, height, mask, mask,
            probeCount, probe1, probe2, probe3,
            new int[]{sigA}, new int[]{sigB}, new int[]{0});

        // Neither normal A,B nor mirrored B,A: the second slot has an unrelated two-hash token.
        long wrong = packTokens(sigA, 0x0044, 0, 0);
        assertFalse(compiled.matches(0, 2, 2, 0b0011L, wrong, 0L, 0));
    }

    @Test
    public void preservesRecipeLocalPlacementOwnership() {
        byte[] width = {(byte) 1, (byte) 1};
        byte[] height = {(byte) 1, (byte) 1};
        long[] mask = {1L, 1L};
        byte[] probeCount = {(byte) 1, (byte) 1};
        byte[] probe1 = {0, 0};
        byte[] unused = {0, 0};
        int sig = 0x0011;
        CompiledPositionalProbes compiled = CompiledPositionalProbes.compile(
            width, height, mask, mask,
            probeCount, probe1, unused, unused,
            new int[]{sig, sig}, new int[]{0, 0}, new int[]{0, 0});

        long query = packTokens(0, 0, 0, sig);
        assertTrue(compiled.matches(0, 2, 2, 0b1000L, query, 0L, 0));
        assertTrue(compiled.matches(1, 2, 2, 0b1000L, query, 0L, 0));
    }

    private static long packTokens(int s0, int s1, int s2, int s3) {
        return ((long) s0 & 0xffffL)
            | (((long) s1 & 0xffffL) << 16)
            | (((long) s2 & 0xffffL) << 32)
            | (((long) s3 & 0xffffL) << 48);
    }
}
