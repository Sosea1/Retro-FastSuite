package dev.sosea1.fastsuite112.api;

import dev.sosea1.fastsuite112.recipe.RecipeIndex;
import dev.sosea1.fastsuite112.recipe.RecipeLookup;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Public compatibility surface for recipe mods and recipe-conflict consumers.
 *
 * <p>The API deliberately stays dependency-free and conservative. Integrations should prefer these
 * entry points over reflecting into RecipeIndex internals.</p>
 *
 * <p>Lifecycle/thread contract: recipe lookup and invalidation calls are expected from the same
 * logical crafting/server context in which vanilla recipe code normally runs. FastSuite does not
 * move third-party {@link IRecipe} / {@link Ingredient} logic onto worker threads and does not
 * swallow exceptions thrown by that logic.</p>
 */
public final class FastSuiteAPI {
    /**
     * Public compatibility contract version. Version 3 adds conservative exact-class constraint
     * providers while retaining the version 2 ordered all-match and legacy candidate surfaces.
     */
    public static final int API_VERSION = 3;

    private FastSuiteAPI() {}

    /**
     * Force an exact custom recipe class to be considered index-safe.
     * The caller is responsible for ensuring getIngredients() exposes a necessary condition of matches().
     */
    public static void registerSafeRecipeClass(Class<? extends IRecipe> recipeClass) {
        RecipeIndex.INSTANCE.registerSafeRecipeClass(recipeClass);
    }

    /**
     * Force an exact custom ingredient class to be usable as an item-level pivot.
     * The caller must guarantee that getMatchingStacks() contains every Item that apply() may accept.
     */
    public static void registerSafeIngredientClass(Class<? extends Ingredient> ingredientClass) {
        RecipeIndex.INSTANCE.registerSafeIngredientClass(ingredientClass);
    }

    /**
     * Register a conservative description provider for one exact custom recipe class.
     * Registering again replaces the provider and invalidates the current index snapshot.
     */
    public static <T extends IRecipe> void registerRecipeConstraintProvider(
        Class<T> recipeClass, RecipeConstraintProvider<? super T> provider) {
        RecipeIndex.INSTANCE.registerRecipeConstraintProvider(recipeClass, provider);
    }

    /**
     * Notify FastSuite that recipe semantics changed without going through the Forge recipe registry
     * or OreDictionary mutation paths. This is required for mods that mutate fields/ingredients of
     * an already-registered IRecipe instance in place. Call from the normal logical recipe/server
     * lifecycle rather than an arbitrary worker thread.
     */
    public static void invalidateRecipeIndex() {
        invalidateRecipeIndex("API request / in-place recipe mutation");
    }

    /** Same as {@link #invalidateRecipeIndex()}, but preserves an integration-specific diagnostic reason. */
    public static void invalidateRecipeIndex(@Nullable String reason) {
        RecipeIndex.INSTANCE.invalidate(reason == null || reason.isEmpty()
            ? "API request / in-place recipe mutation"
            : "API request: " + reason);
    }

    /**
     * Return FastSuite's conservative candidate iterable in exact registry order.
     *
     * <p>This is the lowest-level lookup API. Consumers must still call IRecipe.matches(). The
     * returned iterable is read-only by contract and must not be mutated. When indexed crafting is
     * disabled it may be the live Forge recipe-registry iterable. The first call after an invalidation
     * may rebuild the index and therefore may invoke third-party ingredient accessors;
     * call it from the same logical context as normal crafting.</p>
     */
    public static Iterable<IRecipe> getCandidateRecipes(InventoryCrafting inventory) {
        return RecipeIndex.INSTANCE.candidatesFor(inventory);
    }

    /**
     * Legacy alpha compatibility alias for integrations compiled/reflected against the original
     * FastSuite112 candidate API name. New integrations should use {@link #getCandidateRecipes}.
     *
     * <p>This method deliberately preserves the exact low-level candidate semantics; it does not
     * return only matching recipes.</p>
     */
    @Deprecated
    public static Iterable<IRecipe> getCraftingCandidates(InventoryCrafting inventory) {
        return getCandidateRecipes(inventory);
    }

    /**
     * Return the first match using the same ordering contract as vanilla CraftingManager. Recipe
     * exceptions and side effects are intentionally observable exactly as they are in a normal scan.
     */
    @Nullable
    public static IRecipe findFirstMatchingRecipe(InventoryCrafting inventory, World world) {
        return RecipeLookup.findFirstMatchingRecipe(inventory, world);
    }

    /**
     * Return every matching recipe in exact registry order for conflict-resolution UIs. This calls
     * matching recipe code sequentially on the supplied grid; third-party exceptions are not hidden.
     */
    public static List<IRecipe> findAllMatchingRecipes(InventoryCrafting inventory, World world) {
        return RecipeLookup.findAllMatchingRecipes(inventory, world);
    }

    /**
     * Visit every matching recipe in exact registry order without allocating an intermediate
     * {@link java.util.List}. Return false from the visitor to stop after the current match.
     * The returned int is the number of matches delivered to the visitor.
     */
    public static int visitMatchingRecipes(InventoryCrafting inventory, World world, RecipeMatchVisitor visitor) {
        return RecipeLookup.visitMatchingRecipes(inventory, world, visitor);
    }

    /**
     * Current mutation generation. External caches may store this value and invalidate themselves
     * when it changes instead of guessing about CraftTweaker/OreDictionary lifecycle boundaries.
     */
    public static long getRecipeIndexGeneration() {
        return RecipeIndex.INSTANCE.getEpoch();
    }


    /** Public compatibility contract version for reflection-only optional integrations. */
    public static int getApiVersion() {
        return API_VERSION;
    }
}
