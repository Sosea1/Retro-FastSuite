package dev.sosea1.fastsuite112.recipe;

import dev.sosea1.fastsuite112.config.FastSuiteConfig;
import dev.sosea1.fastsuite112.recipe.RecipeSafetyClassifier.Safety;
import net.minecraft.item.Item;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/** Compiles rebuild-time recipe facts into conservative query constraints. */
final class ConstraintCompiler {
    private ConstraintCompiler() {}

    /**
     * Build conservative second-stage constraints for structurally proven vanilla/Forge recipes.
     * API overrides intentionally remain on the primary-pivot-only contract: integrations may have
     * custom semantics for extra slots even when their exposed ingredients contain a necessary pivot.
     */
    @Nullable
    static RecipeConstraint compile(RecipeAnalysis analysis,
                                    int selectedPivotIndex,
                                    Map<Item, Integer> potentialRecipeFrequency) {
        if (!FastSuiteConfig.advancedCandidatePruning || analysis.recipeSafety == Safety.API_OVERRIDE) {
            return null;
        }

        int firstPresenceIndex = chooseAdditionalPresenceCandidate(
            analysis, selectedPivotIndex, -1, potentialRecipeFrequency);
        int secondPresenceIndex = chooseAdditionalPresenceCandidate(
            analysis, selectedPivotIndex, firstPresenceIndex, potentialRecipeFrequency);

        Item repeatedItem1 = null;
        Item repeatedItem2 = null;
        int repeatedCount1 = 0;
        int repeatedCount2 = 0;
        IdentityHashMap<Item, Integer> singletonCounts = new IdentityHashMap<Item, Integer>();
        for (int candidateIndex = 0; candidateIndex < analysis.pivotCandidates.size(); candidateIndex++) {
            if (!analysis.advancedCandidateSafe[candidateIndex]) continue;
            Item[] candidate = analysis.pivotCandidates.get(candidateIndex);
            if (candidate.length != 1) continue;
            Item item = candidate[0];
            Integer old = singletonCounts.get(item);
            singletonCounts.put(item, old == null ? 1 : old + 1);
        }
        for (Map.Entry<Item, Integer> entry : singletonCounts.entrySet()) {
            int count = entry.getValue();
            if (count < 2) continue;
            Item item = entry.getKey();
            if (count > repeatedCount1) {
                repeatedItem2 = repeatedItem1;
                repeatedCount2 = repeatedCount1;
                repeatedItem1 = item;
                repeatedCount1 = count;
            } else if (count > repeatedCount2) {
                repeatedItem2 = item;
                repeatedCount2 = count;
            }
        }

        CandidateRouting firstPresence = firstPresenceIndex < 0 ? null : analysis.candidateRoutings.get(firstPresenceIndex);
        CandidateRouting secondPresence = secondPresenceIndex < 0 ? null : analysis.candidateRoutings.get(secondPresenceIndex);

        // Compile every structurally-safe single-Item ingredient into aggregate query signatures.
        // Requiring all hashed bits is safe: hash collisions can only make an impossible query look
        // plausible, never make a valid query lose a bit that its required Item/variant contributes.
        long requiredItemMaskA = 0L;
        long requiredItemMaskB = 0L;
        long requiredVariantMaskA = 0L;
        long requiredVariantMaskB = 0L;
        for (int candidateIndex = 0; candidateIndex < analysis.candidateRoutings.size(); candidateIndex++) {
            if (!analysis.advancedCandidateSafe[candidateIndex]) continue;
            CandidateRouting routing = analysis.candidateRoutings.get(candidateIndex);
            if (routing.routes.length != 1 || routing.items.length != 1) continue;

            ItemRoute route = routing.routes[0];
            requiredItemMaskA |= MandatoryStackConstraint.presenceBitA(route.item);
            requiredItemMaskB |= MandatoryStackConstraint.presenceBitB(route.item);
            if (!route.itemWide && route.exactMetas.length == 1) {
                int metadata = route.exactMetas[0];
                requiredVariantMaskA |= MandatoryStackConstraint.exactVariantBitA(route.item, metadata);
                requiredVariantMaskB |= MandatoryStackConstraint.exactVariantBitB(route.item, metadata);
            }
        }

        int[] positionalProbes = choosePositionalProbeCandidates(analysis, potentialRecipeFrequency, 3);
        int positionalProbeCount = positionalProbes.length;
        int positionalIndex1 = positionalProbeCount > 0
            ? analysis.candidateIngredientIndices[positionalProbes[0]] : -1;
        int positionalIndex2 = positionalProbeCount > 1
            ? analysis.candidateIngredientIndices[positionalProbes[1]] : -1;
        int positionalIndex3 = positionalProbeCount > 2
            ? analysis.candidateIngredientIndices[positionalProbes[2]] : -1;
        int positionalSignature1 = positionalProbeCount > 0
            ? compactRoutingSignature(analysis.candidateRoutings.get(positionalProbes[0])) : 0;
        int positionalSignature2 = positionalProbeCount > 1
            ? compactRoutingSignature(analysis.candidateRoutings.get(positionalProbes[1])) : 0;
        int positionalSignature3 = positionalProbeCount > 2
            ? compactRoutingSignature(analysis.candidateRoutings.get(positionalProbes[2])) : 0;

        RecipeConstraint.Builder builder = RecipeConstraint.builder()
            .occupiedSlots(analysis.exactOccupiedSlotCount)
            .presenceGroup(0, firstPresence == null ? 0L : firstPresence.matchMaskA,
                firstPresence == null ? 0L : firstPresence.matchMaskB)
            .presenceGroup(1, secondPresence == null ? 0L : secondPresence.matchMaskA,
                secondPresence == null ? 0L : secondPresence.matchMaskB)
            .presenceGroup(2, 0L, 0L)
            .requiredItems(requiredItemMaskA, requiredItemMaskB)
            .requiredVariants(requiredVariantMaskA, requiredVariantMaskB)
            .shape(analysis.shapeWidth, analysis.shapeHeight, analysis.shapeOccupancyMask,
                analysis.mirroredShapeOccupancyMask)
            .repeatedRequirement(0, repeatedItem1 == null ? -1 : countSketchIndexA(repeatedItem1),
                repeatedItem1 == null ? -1 : countSketchIndexB(repeatedItem1),
                SaturatingCountSketch.capRequirement(repeatedCount1))
            .repeatedRequirement(1, repeatedItem2 == null ? -1 : countSketchIndexA(repeatedItem2),
                repeatedItem2 == null ? -1 : countSketchIndexB(repeatedItem2),
                SaturatingCountSketch.capRequirement(repeatedCount2));
        if (positionalProbeCount > 0) builder.positionalProbe(0, positionalIndex1, positionalSignature1);
        if (positionalProbeCount > 1) builder.positionalProbe(1, positionalIndex2, positionalSignature2);
        if (positionalProbeCount > 2) builder.positionalProbe(2, positionalIndex3, positionalSignature3);
        return builder.build();
    }

    private static int[] choosePositionalProbeCandidates(RecipeAnalysis analysis,
                                                         Map<Item, Integer> potentialRecipeFrequency,
                                                         int limit) {
        if (analysis.shapeWidth <= 0 || analysis.shapeHeight <= 0 || limit <= 0) return new int[0];

        int[] selected = new int[Math.min(limit, analysis.candidateRoutings.size())];
        int selectedCount = 0;
        boolean[] used = new boolean[analysis.candidateRoutings.size()];
        while (selectedCount < selected.length) {
            int bestIndex = -1;
            int bestWorstSupport = Integer.MAX_VALUE;
            long bestTotalSupport = Long.MAX_VALUE;
            int bestAlternativeCount = Integer.MAX_VALUE;

            for (int i = 0; i < analysis.candidateRoutings.size(); i++) {
                if (used[i] || !analysis.advancedCandidateSafe[i]) continue;
                CandidateRouting routing = analysis.candidateRoutings.get(i);
                int signature = compactRoutingSignature(routing);
                if (signature == 0) continue;

                ConstraintCost cost = constraintCost(routing.items, potentialRecipeFrequency);
                int alternatives = routingAlternativeCount(routing);
                if (bestIndex < 0 || PivotScoring.isBetter(
                    cost.worstSupport,
                    cost.totalSupport,
                    alternatives,
                    bestWorstSupport,
                    bestTotalSupport,
                    bestAlternativeCount
                )) {
                    bestIndex = i;
                    bestWorstSupport = cost.worstSupport;
                    bestTotalSupport = cost.totalSupport;
                    bestAlternativeCount = alternatives;
                }
            }

            if (bestIndex < 0) break;
            selected[selectedCount++] = bestIndex;
            used[bestIndex] = true;
        }
        return selectedCount == selected.length ? selected : Arrays.copyOf(selected, selectedCount);
    }

    private static int routingAlternativeCount(CandidateRouting routing) {
        int alternatives = 0;
        for (ItemRoute route : routing.routes) {
            alternatives += route.itemWide ? 1 : Math.max(1, route.exactMetas.length);
        }
        return alternatives;
    }

    /** Compact two-hash, Item/meta-aware fingerprint used only as a fail-open positional reject. */
    private static int compactRoutingSignature(CandidateRouting routing) {
        int itemA = 0;
        int itemB = 0;
        int variantA = 0;
        int variantB = 0;
        for (ItemRoute route : routing.routes) {
            if (route.itemWide) {
                itemA |= compactLane(MandatoryStackConstraint.presenceBitA(route.item));
                itemB |= compactLane(MandatoryStackConstraint.presenceBitB(route.item));
            } else {
                for (int metadata : route.exactMetas) {
                    variantA |= compactLane(MandatoryStackConstraint.exactVariantBitA(route.item, metadata));
                    variantB |= compactLane(MandatoryStackConstraint.exactVariantBitB(route.item, metadata));
                }
            }
        }
        return (itemA & 0xf)
            | ((itemB & 0xf) << 4)
            | ((variantA & 0xf) << 8)
            | ((variantB & 0xf) << 12);
    }

    private static int chooseAdditionalPresenceCandidate(RecipeAnalysis analysis,
                                                         int primaryIndex,
                                                         int firstSelectedIndex,
                                                         Map<Item, Integer> potentialRecipeFrequency) {
        int bestIndex = -1;
        int bestWorstSupport = Integer.MAX_VALUE;
        long bestTotalSupport = Long.MAX_VALUE;
        int bestAlternativeCount = Integer.MAX_VALUE;
        CandidateRouting primaryRouting = analysis.candidateRoutings.get(primaryIndex);
        CandidateRouting firstSelectedRouting = firstSelectedIndex < 0 ? null : analysis.candidateRoutings.get(firstSelectedIndex);

        for (int i = 0; i < analysis.pivotCandidates.size(); i++) {
            if (i == primaryIndex || i == firstSelectedIndex || !analysis.advancedCandidateSafe[i]) continue;
            Item[] items = analysis.pivotCandidates.get(i);
            CandidateRouting routing = analysis.candidateRoutings.get(i);

            // Same Item but different exact metadata is still a useful independent condition.
            // Skip only genuinely equivalent accepted Item/meta route sets.
            if (sameCandidateRouting(routing, primaryRouting)) continue;
            if (firstSelectedRouting != null && sameCandidateRouting(routing, firstSelectedRouting)) continue;

            ConstraintCost cost = constraintCost(items, potentialRecipeFrequency);
            if (bestIndex < 0 || PivotScoring.isBetter(
                cost.worstSupport,
                cost.totalSupport,
                items.length,
                bestWorstSupport,
                bestTotalSupport,
                bestAlternativeCount
            )) {
                bestIndex = i;
                bestWorstSupport = cost.worstSupport;
                bestTotalSupport = cost.totalSupport;
                bestAlternativeCount = items.length;
            }
        }
        return bestIndex;
    }

    private static ConstraintCost constraintCost(Item[] items, Map<Item, Integer> potentialRecipeFrequency) {
        int worstSupport = 0;
        long totalSupport = 0L;
        for (Item item : items) {
            Integer supportBoxed = potentialRecipeFrequency.get(item);
            int support = supportBoxed == null ? 1 : Math.max(1, supportBoxed);
            worstSupport = Math.max(worstSupport, support);
            totalSupport += support;
        }
        return new ConstraintCost(worstSupport, totalSupport);
    }

    private static boolean sameCandidateRouting(CandidateRouting left, CandidateRouting right) {
        if (left == right) return true;
        if (left == null || right == null || left.routes.length != right.routes.length) return false;
        for (ItemRoute leftRoute : left.routes) {
            ItemRoute rightRoute = findRoute(right.routes, leftRoute.item);
            if (rightRoute == null || leftRoute.itemWide != rightRoute.itemWide) return false;
            if (!leftRoute.itemWide && !Arrays.equals(leftRoute.exactMetas, rightRoute.exactMetas)) return false;
        }
        return true;
    }

    @Nullable
    private static ItemRoute findRoute(ItemRoute[] routes, Item item) {
        for (ItemRoute route : routes) if (route.item == item) return route;
        return null;
    }

    private static int compactLane(long oneHotBit) {
        return 1 << (Long.numberOfTrailingZeros(oneHotBit) & 3);
    }

    static int compactQueryToken(long itemBitA,
                                 long itemBitB,
                                 long variantBitA,
                                 long variantBitB) {
        return compactLane(itemBitA)
            | (compactLane(itemBitB) << 4)
            | (compactLane(variantBitA) << 8)
            | (compactLane(variantBitB) << 12);
    }

    static int countSketchIndexA(Item item) {
        int hash = System.identityHashCode(item);
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        return hash & 15;
    }

    static int countSketchIndexB(Item item) {
        int hash = System.identityHashCode(item) ^ 0x9E3779B9;
        hash *= 0x846CA68B;
        hash ^= hash >>> 16;
        hash *= 0x27D4EB2D;
        hash ^= hash >>> 15;
        return hash & 15;
    }

    private static final class ConstraintCost {
        private final int worstSupport;
        private final long totalSupport;

        private ConstraintCost(int worstSupport, long totalSupport) {
            this.worstSupport = worstSupport;
            this.totalSupport = totalSupport;
        }
    }
}
