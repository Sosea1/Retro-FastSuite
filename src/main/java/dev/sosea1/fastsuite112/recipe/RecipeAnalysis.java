package dev.sosea1.fastsuite112.recipe;

import dev.sosea1.fastsuite112.recipe.RecipeIndex.FallbackReason;
import dev.sosea1.fastsuite112.recipe.RecipeSafetyClassifier.Safety;
import net.minecraft.item.Item;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable rebuild-time facts produced by {@link RecipeAnalyzer}. */
final class RecipeAnalysis {
    final List<Item[]> pivotCandidates;
    final List<CandidateRouting> candidateRoutings;
    final int[] candidateIngredientIndices;
    final boolean[] advancedCandidateSafe;
    @Nullable final Safety recipeSafety;
    @Nullable final FallbackReason fallbackReason;
    final boolean indexingFailure;
    @Nullable final Class<?> diagnosticClass;
    @Nullable final Class<?> failureClass;
    final int exactOccupiedSlotCount;
    final int shapeWidth;
    final int shapeHeight;
    final long shapeOccupancyMask;
    final long mirroredShapeOccupancyMask;
    final MandatoryStackConstraint mandatoryStackConstraint;

    private RecipeAnalysis(List<Item[]> pivotCandidates,
                           List<CandidateRouting> candidateRoutings,
                           int[] candidateIngredientIndices,
                           boolean[] advancedCandidateSafe,
                           @Nullable Safety recipeSafety,
                           @Nullable FallbackReason fallbackReason,
                           boolean indexingFailure,
                           @Nullable Class<?> diagnosticClass,
                           @Nullable Class<?> failureClass,
                           int exactOccupiedSlotCount,
                           int shapeWidth,
                           int shapeHeight,
                           long shapeOccupancyMask,
                           long mirroredShapeOccupancyMask,
                           MandatoryStackConstraint mandatoryStackConstraint) {
        this.pivotCandidates = freezeCandidates(pivotCandidates);
        this.candidateRoutings = Collections.unmodifiableList(
            new ArrayList<CandidateRouting>(candidateRoutings));
        this.candidateIngredientIndices = candidateIngredientIndices.clone();
        this.advancedCandidateSafe = advancedCandidateSafe.clone();
        this.recipeSafety = recipeSafety;
        this.fallbackReason = fallbackReason;
        this.indexingFailure = indexingFailure;
        this.diagnosticClass = diagnosticClass;
        this.failureClass = failureClass;
        this.exactOccupiedSlotCount = exactOccupiedSlotCount;
        this.shapeWidth = shapeWidth;
        this.shapeHeight = shapeHeight;
        this.shapeOccupancyMask = shapeOccupancyMask;
        this.mirroredShapeOccupancyMask = mirroredShapeOccupancyMask;
        this.mandatoryStackConstraint = mandatoryStackConstraint;
    }

    boolean isIndexable() {
        return !indexingFailure && fallbackReason == null && !pivotCandidates.isEmpty();
    }

    static RecipeAnalysis indexable(List<Item[]> pivotCandidates,
                                    List<CandidateRouting> candidateRoutings,
                                    List<Integer> candidateIngredientIndices,
                                    List<Boolean> advancedCandidateSafe,
                                    Safety safety,
                                    int exactOccupiedSlotCount,
                                    int shapeWidth,
                                    int shapeHeight,
                                    long shapeOccupancyMask,
                                    long mirroredShapeOccupancyMask) {
        boolean[] advancedSafe = new boolean[advancedCandidateSafe.size()];
        int[] ingredientIndices = new int[candidateIngredientIndices.size()];
        for (int i = 0; i < advancedSafe.length; i++) {
            advancedSafe[i] = advancedCandidateSafe.get(i);
            ingredientIndices[i] = candidateIngredientIndices.get(i);
        }
        return new RecipeAnalysis(
            pivotCandidates, candidateRoutings, ingredientIndices, advancedSafe, safety, null, false, null, null,
            exactOccupiedSlotCount, shapeWidth, shapeHeight, shapeOccupancyMask, mirroredShapeOccupancyMask,
            MandatoryStackConstraint.UNCONSTRAINED);
    }

    static RecipeAnalysis fallback(FallbackReason reason) {
        return fallback(reason, null);
    }

    static RecipeAnalysis fallback(FallbackReason reason, @Nullable Class<?> diagnosticClass) {
        return new RecipeAnalysis(
            Collections.<Item[]>emptyList(), Collections.<CandidateRouting>emptyList(), new int[0], new boolean[0],
            null, reason, false, diagnosticClass, null, -1, 0, 0, 0L, 0L,
            MandatoryStackConstraint.UNCONSTRAINED);
    }

    static RecipeAnalysis constrainedFallback(MandatoryStackConstraint constraint) {
        return new RecipeAnalysis(
            Collections.<Item[]>emptyList(), Collections.<CandidateRouting>emptyList(), new int[0], new boolean[0],
            null, FallbackReason.PROVIDER_FILTER_ONLY, false, null, null, -1, 0, 0, 0L, 0L, constraint);
    }

    static RecipeAnalysis providerFailure(@Nullable Class<?> failureClass) {
        return new RecipeAnalysis(
            Collections.<Item[]>emptyList(), Collections.<CandidateRouting>emptyList(), new int[0], new boolean[0],
            null, FallbackReason.PROVIDER_FAILURE, false, null, failureClass, -1, 0, 0, 0L, 0L,
            MandatoryStackConstraint.UNCONSTRAINED);
    }

    static RecipeAnalysis failure(@Nullable Class<?> failureClass) {
        return new RecipeAnalysis(
            Collections.<Item[]>emptyList(), Collections.<CandidateRouting>emptyList(), new int[0], new boolean[0],
            null, FallbackReason.INDEXING_ERROR, true, null, failureClass, -1, 0, 0, 0L, 0L,
            MandatoryStackConstraint.UNCONSTRAINED);
    }

    private static List<Item[]> freezeCandidates(List<Item[]> candidates) {
        List<Item[]> frozen = new ArrayList<Item[]>(candidates.size());
        for (Item[] candidate : candidates) {
            frozen.add(candidate.clone());
        }
        return Collections.unmodifiableList(frozen);
    }
}
