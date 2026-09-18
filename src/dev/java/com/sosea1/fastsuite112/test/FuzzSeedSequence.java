package com.sosea1.fastsuite112.test;

import java.util.Random;

/** Pure deterministic seed plumbing shared by fuzz generation and replay. */
public final class FuzzSeedSequence {
    private final Random master;

    public FuzzSeedSequence(long masterSeed) {
        this.master = new Random(masterSeed);
    }

    public long nextCaseSeed() {
        return master.nextLong();
    }

    /** A recorded case seed is already final and must never be randomized again. */
    public static long replayCaseSeed(long recordedCaseSeed) {
        return recordedCaseSeed;
    }
}
