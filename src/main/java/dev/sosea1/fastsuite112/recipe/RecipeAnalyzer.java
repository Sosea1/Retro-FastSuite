package dev.sosea1.fastsuite112.recipe;

import dev.sosea1.fastsuite112.recipe.RecipeIndex.FallbackReason;
import dev.sosea1.fastsuite112.recipe.RecipeSafetyClassifier.Safety;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraftforge.oredict.ShapedOreRecipe;

import java.util.ArrayList;
import java.util.List;

/** Classifies recipes and collects conservative rebuild-time indexing facts. */
final class RecipeAnalyzer {
    private final RecipeSafetyClassifier classifier;
    private final RecipeConstraintProviderRegistry constraintProviders;

    RecipeAnalyzer(RecipeSafetyClassifier classifier,
                   RecipeConstraintProviderRegistry constraintProviders) {
        this.classifier = classifier;
        this.constraintProviders = constraintProviders;
    }

    RecipeAnalysis analyze(IRecipe recipe) {
        if (recipe == null) return RecipeAnalysis.fallback(FallbackReason.NULL_RECIPE);

        Class<?> recipeClass = recipe.getClass();
        Safety recipeSafety;

        ProviderConstraintDecision providerDecision =
            ProviderConstraintDecision.evaluate(constraintProviders, recipe);
        if (providerDecision.kind == ProviderConstraintDecision.Kind.FAILURE) {
            return RecipeAnalysis.providerFailure(providerDecision.failureClass);
        }
        if (providerDecision.kind == ProviderConstraintDecision.Kind.FILTER_ONLY) {
            return RecipeAnalysis.constrainedFallback(providerDecision.constraint);
        }

        // Explicit compatibility API is the only intentional escape hatch for dynamic recipes.
        // Integration authors accepting this path take responsibility for proving that the exposed
        // ingredients remain a necessary condition of matches().
        if (providerDecision.kind == ProviderConstraintDecision.Kind.FULL_INDEX
            || classifier.isForcedSafeRecipeClass(recipeClass)) {
            recipeSafety = Safety.API_OVERRIDE;
        } else {
            if (recipe.isDynamic()) {
                return RecipeAnalysis.fallback(FallbackReason.DYNAMIC_RECIPE);
            }

            recipeSafety = classifier.classifyRecipe(recipeClass);
            if (!recipeSafety.isSafe()) {
                return RecipeAnalysis.fallback(FallbackReason.UNKNOWN_RECIPE_CLASS, recipeClass);
            }
        }

        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return RecipeAnalysis.fallback(FallbackReason.NO_INGREDIENTS);
        }

        // Shape pruning is deliberately narrower than ordinary indexing. Vanilla ShapedRecipes has
        // public final dimensions used directly by its inherited matcher. ShapedOreRecipe dimensions
        // are trusted only for the exact Forge class; subclasses can override dimension accessors
        // without overriding matches(), so we fail closed there.
        int shapeWidth = 0;
        int shapeHeight = 0;
        if (recipeSafety != Safety.API_OVERRIDE && recipe instanceof ShapedRecipes) {
            ShapedRecipes shaped = (ShapedRecipes) recipe;
            shapeWidth = shaped.recipeWidth;
            shapeHeight = shaped.recipeHeight;
        } else if (recipeSafety != Safety.API_OVERRIDE && recipeClass == ShapedOreRecipe.class) {
            ShapedOreRecipe shaped = (ShapedOreRecipe) recipe;
            shapeWidth = shaped.getRecipeWidth();
            shapeHeight = shaped.getRecipeHeight();
        }

        boolean shapeOccupancySafe = shapeWidth > 0
            && shapeHeight > 0
            && (long) shapeWidth * (long) shapeHeight == ingredients.size()
            && ingredients.size() <= 63;
        long shapeOccupancyMask = 0L;

        List<CandidateRouting> candidateRoutings = new ArrayList<CandidateRouting>();
        List<Integer> candidateIngredientIndices = new ArrayList<Integer>();
        List<Boolean> advancedCandidateSafe = new ArrayList<Boolean>();
        boolean sawNonEmptyIngredient = false;
        boolean sawUnknownIngredient = false;
        Class<?> firstUnknownIngredientClass = null;
        Class<?> firstNonEnumerableIngredientClass = null;
        int nonEmptyIngredientCount = 0;
        boolean exactOccupiedCountSafe = true;

        for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++) {
            Ingredient ingredient = ingredients.get(ingredientIndex);
            if (ingredient == null) {
                exactOccupiedCountSafe = false;
                shapeOccupancySafe = false;
                continue;
            }
            if (ingredient == Ingredient.EMPTY) continue;

            sawNonEmptyIngredient = true;
            nonEmptyIngredientCount++;
            if (shapeOccupancySafe) shapeOccupancyMask |= 1L << ingredientIndex;

            Safety ingredientSafety = classifier.classifyIngredient(ingredient.getClass());
            if (!ingredientSafety.isSafe()) {
                sawUnknownIngredient = true;
                if (firstUnknownIngredientClass == null) firstUnknownIngredientClass = ingredient.getClass();
                exactOccupiedCountSafe = false;
                shapeOccupancySafe = false;
                continue;
            }
            if (ingredientSafety == Safety.API_OVERRIDE) {
                exactOccupiedCountSafe = false;
                shapeOccupancySafe = false;
            }

            CandidateRouting routing = IngredientRoutingCompiler.compile(
                ingredient, ingredientSafety != Safety.API_OVERRIDE);
            if (routing.items.length != 0) {
                candidateRoutings.add(routing);
                candidateIngredientIndices.add(ingredientIndex);
                advancedCandidateSafe.add(ingredientSafety != Safety.API_OVERRIDE);
            } else if (firstNonEnumerableIngredientClass == null) {
                firstNonEnumerableIngredientClass = ingredient.getClass();
            }
        }

        if (candidateRoutings.isEmpty()) {
            if (!sawNonEmptyIngredient) return RecipeAnalysis.fallback(FallbackReason.NO_INGREDIENTS);
            if (sawUnknownIngredient) return RecipeAnalysis.fallback(
                FallbackReason.NO_SAFE_PIVOT, firstUnknownIngredientClass);
            return RecipeAnalysis.fallback(FallbackReason.NO_ENUMERABLE_PIVOT, firstNonEnumerableIngredientClass);
        }

        long mirroredShapeOccupancyMask = shapeOccupancySafe
            ? ShapeOccupancy.mirror(shapeOccupancyMask, shapeWidth, shapeHeight)
            : 0L;
        return RecipeAnalysis.indexable(
            candidateRoutings,
            candidateIngredientIndices,
            advancedCandidateSafe,
            recipeSafety,
            exactOccupiedCountSafe ? nonEmptyIngredientCount : -1,
            shapeOccupancySafe ? shapeWidth : 0,
            shapeOccupancySafe ? shapeHeight : 0,
            shapeOccupancySafe ? shapeOccupancyMask : 0L,
            mirroredShapeOccupancyMask
        );
    }
}
