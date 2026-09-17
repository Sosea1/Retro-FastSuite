package dev.sosea1.fastsuite112.api;

import net.minecraft.item.crafting.IRecipe;

import javax.annotation.Nullable;

/**
 * Describes conservative indexing information for one exact custom recipe class.
 * Implementations are called synchronously while the recipe index is rebuilt.
 */
public interface RecipeConstraintProvider<T extends IRecipe> {
    /** Return a sound description, or null to leave this recipe on opaque fallback. */
    @Nullable
    RecipeConstraintSpec describe(T recipe);
}
