package dev.sosea1.fastsuite112.test;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class FuzzSeedSequenceTest {
    @Test
    void replayConsumesRecordedCaseSeedDirectly() {
        long masterSeed = 0x5A17C0FFEE12L;
        long recordedCaseSeed = new FuzzSeedSequence(masterSeed).nextCaseSeed();

        assertEquals(recordedCaseSeed, FuzzSeedSequence.replayCaseSeed(recordedCaseSeed));
        assertNotEquals(new Random(recordedCaseSeed).nextLong(), FuzzSeedSequence.replayCaseSeed(recordedCaseSeed));
    }

    @Test
    void masterSequenceIsStable() {
        FuzzSeedSequence a = new FuzzSeedSequence(123456789L);
        FuzzSeedSequence b = new FuzzSeedSequence(123456789L);
        for (int i = 0; i < 32; i++) {
            assertEquals(a.nextCaseSeed(), b.nextCaseSeed());
        }
    }
}
