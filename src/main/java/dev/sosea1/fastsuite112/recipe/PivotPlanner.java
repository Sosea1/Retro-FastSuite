package dev.sosea1.fastsuite112.recipe;

import dev.sosea1.fastsuite112.config.FastSuiteConfig;
import net.minecraft.item.Item;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Rebuild-only pivot selection and optional bucket-load balancing. */
final class PivotPlanner {
    private PivotPlanner() {}

    static Result plan(List<RecipeAnalysis> analyses,
                       Map<Item, Integer> potentialRecipeFrequency,
                       boolean loadBalanced) {
        int[] assignments = new int[analyses.size()];
        Arrays.fill(assignments, -1);
        DiagnosticsAccumulator diagnostics = new DiagnosticsAccumulator();

        for (int recipeId = 0; recipeId < analyses.size(); recipeId++) {
            RecipeAnalysis analysis = analyses.get(recipeId);
            if (!analysis.isIndexable()) continue;
            assignments[recipeId] = choosePivot(analysis, potentialRecipeFrequency, diagnostics);
        }

        if (!loadBalanced) return new Result(assignments, diagnostics.freeze());

        PivotLoadTable loads = new PivotLoadTable();
        for (int recipeId = 0; recipeId < analyses.size(); recipeId++) {
            int selected = assignments[recipeId];
            if (selected < 0) continue;
            loads.add(analyses.get(recipeId).candidateRoutings.get(selected), 1);
        }

        final int maxPasses = 3;
        int totalChanges = 0;
        int passesRun = 0;
        for (int pass = 0; pass < maxPasses; pass++) {
            int passChanges = 0;
            for (int recipeId = 0; recipeId < analyses.size(); recipeId++) {
                RecipeAnalysis analysis = analyses.get(recipeId);
                int currentIndex = assignments[recipeId];
                if (currentIndex < 0 || analysis.candidateRoutings.size() <= 1) continue;

                CandidateRouting current = analysis.candidateRoutings.get(currentIndex);
                loads.add(current, -1);

                int bestIndex = currentIndex;
                PivotLoadCost bestCost = loads.costWithCandidate(current);
                for (int candidateIndex = 0; candidateIndex < analysis.candidateRoutings.size(); candidateIndex++) {
                    if (candidateIndex == currentIndex) continue;
                    CandidateRouting candidate = analysis.candidateRoutings.get(candidateIndex);
                    PivotLoadCost candidateCost = loads.costWithCandidate(candidate);
                    if (candidateCost.isBetterThan(bestCost)) {
                        bestIndex = candidateIndex;
                        bestCost = candidateCost;
                    }
                }

                assignments[recipeId] = bestIndex;
                loads.add(analysis.candidateRoutings.get(bestIndex), 1);
                if (bestIndex != currentIndex) passChanges++;
            }
            passesRun++;
            totalChanges += passChanges;
            if (passChanges == 0) break;
        }

        diagnostics.passes = passesRun;
        diagnostics.changes = totalChanges;
        diagnostics.maximumQueryLoad = loads.maxObservedQueryLoad();
        return new Result(assignments, diagnostics.freeze());
    }

    private static int choosePivot(RecipeAnalysis analysis,
                                   Map<Item, Integer> potentialRecipeFrequency,
                                   DiagnosticsAccumulator diagnostics) {
        int bestIndex = -1;
        int naiveIndex = -1;
        int naiveItemCount = Integer.MAX_VALUE;
        int bestWorstSupport = Integer.MAX_VALUE;
        long bestTotalSupport = Long.MAX_VALUE;
        int bestAlternativeCount = Integer.MAX_VALUE;
        int naiveWorstSupport = Integer.MAX_VALUE;

        for (int candidateIndex = 0; candidateIndex < analysis.candidateRoutings.size(); candidateIndex++) {
            Item[] items = analysis.candidateRoutings.get(candidateIndex).items;
            PivotCost cost = pivotCost(items, potentialRecipeFrequency);

            if (items.length < naiveItemCount) {
                naiveIndex = candidateIndex;
                naiveItemCount = items.length;
                naiveWorstSupport = cost.worstSupport;
            }

            if (bestIndex < 0 || PivotScoring.isBetter(
                cost.worstSupport,
                cost.totalSupport,
                items.length,
                bestWorstSupport,
                bestTotalSupport,
                bestAlternativeCount
            )) {
                bestIndex = candidateIndex;
                bestWorstSupport = cost.worstSupport;
                bestTotalSupport = cost.totalSupport;
                bestAlternativeCount = items.length;
            }
        }

        if (bestIndex < 0 || naiveIndex < 0) {
            throw new IllegalStateException("Indexable recipe analysis has no pivot candidates");
        }

        boolean frequencyAware = FastSuiteConfig.frequencyAwarePivotSelection;
        int selectedIndex = frequencyAware ? bestIndex : naiveIndex;
        diagnostics.recordPivotChoice(
            bestIndex != naiveIndex, naiveWorstSupport, bestWorstSupport,
            analysis.candidateRoutings.get(selectedIndex).items.length);
        return selectedIndex;
    }

    private static PivotCost pivotCost(Item[] items, Map<Item, Integer> potentialRecipeFrequency) {
        int worstSupport = 0;
        long totalSupport = 0L;
        for (Item item : items) {
            Integer supportBoxed = potentialRecipeFrequency.get(item);
            int support = supportBoxed == null ? 1 : Math.max(1, supportBoxed);
            worstSupport = Math.max(worstSupport, support);
            totalSupport += support;
        }
        return new PivotCost(worstSupport, totalSupport);
    }

    static final class Result {
        final int[] assignments;
        final Diagnostics diagnostics;

        private Result(int[] assignments, Diagnostics diagnostics) {
            this.assignments = assignments;
            this.diagnostics = diagnostics;
        }
    }

    static final class Diagnostics {
        final long pivotChoices;
        final long frequencyAwareChoiceChanges;
        final long naiveWorstSupportTotal;
        final long frequencyAwareWorstSupportTotal;
        final int selectedPivotMaxAlternatives;
        final int passes;
        final int changes;
        final int maximumQueryLoad;

        private Diagnostics(long pivotChoices,
                            long frequencyAwareChoiceChanges,
                            long naiveWorstSupportTotal,
                            long frequencyAwareWorstSupportTotal,
                            int selectedPivotMaxAlternatives,
                            int passes,
                            int changes,
                            int maximumQueryLoad) {
            this.pivotChoices = pivotChoices;
            this.frequencyAwareChoiceChanges = frequencyAwareChoiceChanges;
            this.naiveWorstSupportTotal = naiveWorstSupportTotal;
            this.frequencyAwareWorstSupportTotal = frequencyAwareWorstSupportTotal;
            this.selectedPivotMaxAlternatives = selectedPivotMaxAlternatives;
            this.passes = passes;
            this.changes = changes;
            this.maximumQueryLoad = maximumQueryLoad;
        }
    }

    private static final class DiagnosticsAccumulator {
        private long pivotChoices;
        private long frequencyAwareChoiceChanges;
        private long naiveWorstSupportTotal;
        private long frequencyAwareWorstSupportTotal;
        private int selectedPivotMaxAlternatives;
        private int passes;
        private int changes;
        private int maximumQueryLoad;

        private void recordPivotChoice(boolean changedFromNaive,
                                       int naiveWorstSupport,
                                       int frequencyAwareWorstSupport,
                                       int selectedAlternativeCount) {
            pivotChoices++;
            if (changedFromNaive) frequencyAwareChoiceChanges++;
            naiveWorstSupportTotal += Math.max(0, naiveWorstSupport);
            frequencyAwareWorstSupportTotal += Math.max(0, frequencyAwareWorstSupport);
            selectedPivotMaxAlternatives = Math.max(selectedPivotMaxAlternatives, selectedAlternativeCount);
        }

        private Diagnostics freeze() {
            return new Diagnostics(
                pivotChoices, frequencyAwareChoiceChanges, naiveWorstSupportTotal,
                frequencyAwareWorstSupportTotal, selectedPivotMaxAlternatives,
                passes, changes, maximumQueryLoad);
        }
    }

    private static final class PivotCost {
        private final int worstSupport;
        private final long totalSupport;

        private PivotCost(int worstSupport, long totalSupport) {
            this.worstSupport = worstSupport;
            this.totalSupport = totalSupport;
        }
    }

    /** Build-only cost of querying the exact bucket routes produced by one pivot candidate. */
    private static final class PivotLoadCost {
        private final int worstQueryLoad;
        private final long totalQueryLoad;
        private final int alternativeCount;

        private PivotLoadCost(int worstQueryLoad, long totalQueryLoad, int alternativeCount) {
            this.worstQueryLoad = worstQueryLoad;
            this.totalQueryLoad = totalQueryLoad;
            this.alternativeCount = alternativeCount;
        }

        private boolean isBetterThan(PivotLoadCost other) {
            if (worstQueryLoad != other.worstQueryLoad) return worstQueryLoad < other.worstQueryLoad;
            if (totalQueryLoad != other.totalQueryLoad) return totalQueryLoad < other.totalQueryLoad;
            return alternativeCount < other.alternativeCount;
        }
    }

    /** Mutable rebuild-only model of the immutable wildcard/exact-meta index. */
    private static final class PivotLoadTable {
        private final IdentityHashMap<Item, ItemPivotLoad> items = new IdentityHashMap<Item, ItemPivotLoad>();

        private void add(CandidateRouting routing, int delta) {
            for (ItemRoute route : routing.routes) {
                ItemPivotLoad load = items.get(route.item);
                if (load == null) {
                    load = new ItemPivotLoad();
                    items.put(route.item, load);
                }
                if (route.itemWide) {
                    load.wildcard += delta;
                    if (load.wildcard < 0) throw new IllegalStateException("Negative wildcard pivot load");
                } else {
                    for (int metadata : route.exactMetas) load.addExact(metadata, delta);
                }
            }
        }

        private PivotLoadCost costWithCandidate(CandidateRouting routing) {
            int worst = 0;
            long total = 0L;
            int alternatives = 0;

            for (ItemRoute route : routing.routes) {
                ItemPivotLoad load = items.get(route.item);
                if (route.itemWide) {
                    int wildcardAfter = (load == null ? 0 : load.wildcard) + 1;
                    int worstExact = load == null ? 0 : load.maxExact();
                    int queryLoad = wildcardAfter + worstExact;
                    worst = Math.max(worst, queryLoad);
                    total += queryLoad;
                    alternatives++;
                } else {
                    int wildcard = load == null ? 0 : load.wildcard;
                    for (int metadata : route.exactMetas) {
                        int exact = load == null ? 0 : load.exact(metadata);
                        int queryLoad = wildcard + exact + 1;
                        worst = Math.max(worst, queryLoad);
                        total += queryLoad;
                        alternatives++;
                    }
                }
            }
            return new PivotLoadCost(worst, total, alternatives);
        }

        private int maxObservedQueryLoad() {
            int maximum = 0;
            for (ItemPivotLoad load : items.values()) {
                maximum = Math.max(maximum, load.wildcard + load.maxExact());
            }
            return maximum;
        }
    }

    private static final class ItemPivotLoad {
        private int wildcard;
        private final Map<Integer, Integer> exact = new HashMap<Integer, Integer>();

        private void addExact(int metadata, int delta) {
            Integer key = metadata;
            Integer current = exact.get(key);
            int updated = (current == null ? 0 : current) + delta;
            if (updated < 0) throw new IllegalStateException("Negative exact-meta pivot load");
            if (updated == 0) exact.remove(key);
            else exact.put(key, updated);
        }

        private int exact(int metadata) {
            Integer value = exact.get(metadata);
            return value == null ? 0 : value;
        }

        private int maxExact() {
            int maximum = 0;
            for (Integer value : exact.values()) maximum = Math.max(maximum, value);
            return maximum;
        }
    }
}
