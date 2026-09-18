package com.sosea1.fastsuite112.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PivotScoringTest {
    @Test
    void twoRareAlternativesBeatOneVeryCommonItem() {
        assertTrue(PivotScoring.isBetter(
            8, 14L, 2,
            1200, 1200L, 1
        ));
    }

    @Test
    void worstAlternativeDominatesAverageLookingCandidate() {
        assertFalse(PivotScoring.isBetter(
            900, 905L, 2,
            100, 100L, 1
        ));
    }

    @Test
    void totalSupportAndAlternativeCountAreStableTieBreakers() {
        assertTrue(PivotScoring.isBetter(20, 30L, 3, 20, 40L, 2));
        assertTrue(PivotScoring.isBetter(20, 30L, 2, 20, 30L, 3));
        assertFalse(PivotScoring.isBetter(20, 30L, 3, 20, 30L, 3));
    }
}
