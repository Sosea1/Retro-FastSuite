package com.sosea1.fastsuite112.recipe;

import com.sosea1.fastsuite112.FastSuite112;
import com.sosea1.fastsuite112.api.FastSuiteAPI;
import com.sosea1.fastsuite112.api.RecipeConstraintSpec;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Optional exact-class adapters for recipe implementations whose exposed ingredients are mandatory. */
public final class BuiltInConstraintProviders {
    private static final String[] KNOWN_RECIPE_CLASSES = {
        "crazypants.enderio.base.config.recipes.ShapedRecipe",
        "crazypants.enderio.base.config.recipes.ShapedRecipe$Upgrade",
        "crazypants.enderio.base.config.recipes.ShapelessRecipe",
        "ic2.core.recipe.AdvRecipe",
        "ic2.core.recipe.AdvShapelessRecipe",
        "forestry.core.recipes.ShapedRecipeCustom"
    };

    private BuiltInConstraintProviders() {}

    /** Register adapters only for optional recipe classes present in the current modpack. */
    public static int registerPresent() {
        int registered = 0;
        ClassLoader loader = BuiltInConstraintProviders.class.getClassLoader();
        for (String className : KNOWN_RECIPE_CLASSES) {
            Class<? extends IRecipe> recipeClass = findRecipeClass(className, loader);
            if (recipeClass == null) continue;
            register(recipeClass);
            registered++;
        }
        if (registered != 0) {
            FastSuite112.LOGGER.info("Registered {} built-in conservative recipe constraint providers", registered);
        }
        return registered;
    }

    private static <T extends IRecipe> void register(Class<T> recipeClass) {
        FastSuiteAPI.registerRecipeConstraintProvider(
            recipeClass, BuiltInConstraintProviders::describeMandatoryIngredients);
    }

    /** Build false-positive-only presence filters from mandatory ingredient alternatives. */
    @Nullable
    static RecipeConstraintSpec describeMandatoryIngredients(IRecipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) return null;

        List<ItemStack[]> groups = new ArrayList<ItemStack[]>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient == Ingredient.EMPTY) continue;
            ItemStack[] alternatives = IngredientRoutingCompiler.copyConservativeAlternatives(ingredient);
            if (!isUsableGroup(alternatives)) continue;
            groups.add(alternatives);
        }
        if (groups.isEmpty()) return null;

        groups.sort(Comparator.comparingInt(group -> group.length));
        int groupCount = Math.min(3, groups.size());
        ItemStack[][] selected = new ItemStack[groupCount][];
        for (int i = 0; i < groupCount; i++) selected[i] = groups.get(i);
        return RecipeConstraintSpec.filterOnly(selected);
    }

    private static boolean isUsableGroup(ItemStack[] alternatives) {
        if (alternatives == null || alternatives.length == 0) return false;
        for (ItemStack alternative : alternatives) {
            if (alternative == null || alternative.isEmpty() || alternative.getItem() == null) return false;
        }
        return true;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static Class<? extends IRecipe> findRecipeClass(String className, ClassLoader loader) {
        try {
            Class<?> candidate = Class.forName(className, false, loader);
            return IRecipe.class.isAssignableFrom(candidate)
                ? (Class<? extends IRecipe>) candidate
                : null;
        } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
            return null;
        }
    }
}
