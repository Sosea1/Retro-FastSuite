package com.sosea1.fastsuite112.recipe;

/**
 * Lexicographic selectivity comparison used while rebuilding the recipe index.
 *
 * <p>The worst potential item support is the primary cost because a pivot accepting one very
 * common alternative can still expose a huge bucket. Total potential support and alternative count
 * are deterministic tie-breakers. This helper is Minecraft-independent so the policy can be unit
 * tested without Forge classes.</p>
 */
final class PivotScoring {
    private PivotScoring() {}

    static boolean isBetter(int candidateWorstSupport,
                            long candidateTotalSupport,
                            int candidateAlternativeCount,
                            int bestWorstSupport,
                            long bestTotalSupport,
                            int bestAlternativeCount) {
        if (candidateWorstSupport != bestWorstSupport) {
            return candidateWorstSupport < bestWorstSupport;
        }
        if (candidateTotalSupport != bestTotalSupport) {
            return candidateTotalSupport < bestTotalSupport;
        }
        return candidateAlternativeCount < bestAlternativeCount;
    }
}
