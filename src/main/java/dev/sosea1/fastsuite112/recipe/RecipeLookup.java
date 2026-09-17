package dev.sosea1.fastsuite112.recipe;

import dev.sosea1.fastsuite112.api.RecipeMatchVisitor;
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
     * <p>This is intended for conflict-resolution consumers such as Polymorph-style selectors.
     * It is more expensive than first-match lookup by definition, but avoids scanning recipes that
     * the conservative index can prove impossible for the supplied grid.</p>
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
     * Visit matching recipes in exact registry order without allocating a result List.
     *
     * <p>The return value is the number of matches delivered to the visitor. Returning false from
     * the visitor stops the scan immediately. The visitor is invoked synchronously in the caller's
     * logical crafting context and recipe exceptions remain observable.</p>
     */
    public static int visitMatchingRecipes(InventoryCrafting inventory, World world, RecipeMatchVisitor visitor) {
        if (inventory == null) return 0;
        if (visitor == null) throw new IllegalArgumentException("visitor");

        int visited = 0;
        for (IRecipe recipe : RecipeIndex.INSTANCE.candidatesFor(inventory)) {
            if (recipe.matches(inventory, world)) {
                visited++;
                if (!visitor.visit(recipe)) {
                    break;
                }
            }
        }
        return visited;
    }

}
