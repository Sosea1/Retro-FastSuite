package com.sosea1.fastsuite112.recipe;

import com.sosea1.fastsuite112.api.RecipeMatchVisitor;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Canonical indexed lookup helpers shared by public compatibility consumers.
 *
 * <p>These methods intentionally do not catch exceptions from evaluated candidate recipes. FastSuite
 * expects {@link IRecipe#matches} to be a pure, side-effect-free predicate. Because the index filters
 * out recipes proven impossible for the supplied inventory, recipes outside the candidate set will not
 * have their {@code matches()} method invoked. For candidates that are evaluated, exceptions and
 * observable state changes behave identically to vanilla CraftingManager execution.
 * Candidate iteration preserves exact registry priority/order.</p>
 */
public final class RecipeLookup {
    private RecipeLookup() {}

    /** Return the first recipe that matches, preserving vanilla first-match ordering. */
    @Nullable
    public static IRecipe findFirstMatchingRecipe(InventoryCrafting inventory, World world) {
        if (inventory == null) return null;
        for (IRecipe recipe : RecipeIndex.INSTANCE.candidatesFor(inventory)) {
            if (recipe.matches(inventory, world)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Return every matching recipe in exact registry order.
     *
     * <p>This is intended for conflict-resolution consumers that genuinely need a materialized list.
     * Consumers that only need to detect a conflict or stream results should use
     * {@link #hasMultipleMatchingRecipes} or {@link #visitMatchingRecipes} to avoid the result-list
     * allocation entirely.</p>
     */
    public static List<IRecipe> findAllMatchingRecipes(InventoryCrafting inventory, World world) {
        if (inventory == null) return Collections.emptyList();

        List<IRecipe> matches = new ArrayList<IRecipe>();
        for (IRecipe recipe : RecipeIndex.INSTANCE.candidatesFor(inventory)) {
            if (recipe.matches(inventory, world)) {
                matches.add(recipe);
            }
        }
        return matches.isEmpty()
            ? Collections.<IRecipe>emptyList()
            : Collections.unmodifiableList(matches);
    }

    /**
     * Fast conflict probe for Polymorph-style consumers. Stops immediately after the second match
     * and never allocates a result list.
     */
    public static boolean hasMultipleMatchingRecipes(InventoryCrafting inventory, World world) {
        if (inventory == null) return false;
        int matches = 0;
        for (IRecipe recipe : RecipeIndex.INSTANCE.candidatesFor(inventory)) {
            if (recipe.matches(inventory, world) && ++matches >= 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Visit matching recipes in exact registry order without allocating a result List.
     *
     * <p>The return value is the number of matches delivered to the visitor. Returning false from
     * the visitor stops the scan immediately. The visitor is invoked synchronously in the caller's
     * logical crafting context and recipe exceptions remain observable.</p>
     */
    public static int visitMatchingRecipes(InventoryCrafting inventory, World world, RecipeMatchVisitor visitor) {
        return visitMatchingRecipes(inventory, world, Integer.MAX_VALUE, visitor);
    }

    /**
     * Bounded variant for consumers that only need the first few conflicts. {@code maxMatches == 0}
     * performs no recipe matching and returns zero.
     */
    public static int visitMatchingRecipes(InventoryCrafting inventory,
                                           World world,
                                           int maxMatches,
                                           RecipeMatchVisitor visitor) {
        if (visitor == null) throw new IllegalArgumentException("visitor");
        if (maxMatches < 0) throw new IllegalArgumentException("maxMatches");
        if (inventory == null || maxMatches == 0) return 0;

        int visited = 0;
        for (IRecipe recipe : RecipeIndex.INSTANCE.candidatesFor(inventory)) {
            if (recipe.matches(inventory, world)) {
                visited++;
                if (!visitor.visit(recipe) || visited >= maxMatches) {
                    break;
                }
            }
        }
        return visited;
    }
}
