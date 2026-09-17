package dev.sosea1.fastsuite112.recipe;

import dev.sosea1.fastsuite112.config.FastSuiteConfig;
import dev.sosea1.fastsuite112.util.IdentityCollections;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Conservative structural safety classifier.
 *
 * <p>Proves safety only from exact trusted bases, exact inherited implementations, or explicit API registration.</p>
 */
public final class RecipeSafetyClassifier {
    public static final RecipeSafetyClassifier INSTANCE = new RecipeSafetyClassifier();

    private final Object lock = new Object();

    private final Set<Class<?>> trustedRecipeBases = IdentityCollections.newIdentitySet();
    private final Set<Class<?>> trustedIngredientBases = IdentityCollections.newIdentitySet();
    private final Set<Class<?>> forcedSafeRecipeClasses = IdentityCollections.newIdentitySet();
    private final Set<Class<?>> forcedSafeIngredientClasses = IdentityCollections.newIdentitySet();

    private final IdentityHashMap<Class<?>, Safety> recipeCache = new IdentityHashMap<Class<?>, Safety>();
    private final IdentityHashMap<Class<?>, Safety> ingredientCache = new IdentityHashMap<Class<?>, Safety>();

    private RecipeSafetyClassifier() {
        trustedRecipeBases.add(ShapedRecipes.class);
        trustedRecipeBases.add(ShapelessRecipes.class);
        trustedRecipeBases.add(ShapedOreRecipe.class);
        trustedRecipeBases.add(ShapelessOreRecipe.class);

        trustedIngredientBases.add(Ingredient.class);

        // Optional Forge ingredient implementations are trusted bases when present.
        registerOptionalTrustedIngredientBase("net.minecraftforge.oredict.OreIngredient");
        registerOptionalTrustedIngredientBase("net.minecraftforge.common.crafting.CompoundIngredient");
        registerOptionalTrustedIngredientBase("net.minecraftforge.common.crafting.IngredientNBT");
    }

    public Safety classifyRecipe(Class<?> recipeClass) {
        if (recipeClass == null || !IRecipe.class.isAssignableFrom(recipeClass)) {
            return Safety.UNKNOWN;
        }

        synchronized (lock) {
            Safety cached = recipeCache.get(recipeClass);
            if (cached != null) return cached;

            Safety result = classifyRecipeUncached(recipeClass);
            recipeCache.put(recipeClass, result);
            return result;
        }
    }

    public Safety classifyIngredient(Class<?> ingredientClass) {
        if (ingredientClass == null || !Ingredient.class.isAssignableFrom(ingredientClass)) {
            return Safety.UNKNOWN;
        }

        synchronized (lock) {
            Safety cached = ingredientCache.get(ingredientClass);
            if (cached != null) return cached;

            Safety result = classifyIngredientUncached(ingredientClass);
            ingredientCache.put(ingredientClass, result);
            return result;
        }
    }

    public void registerSafeRecipeClass(Class<? extends IRecipe> recipeClass) {
        if (recipeClass == null) return;
        synchronized (lock) {
            forcedSafeRecipeClasses.add(recipeClass);
            recipeCache.remove(recipeClass);
        }
    }

    public void registerSafeIngredientClass(Class<? extends Ingredient> ingredientClass) {
        if (ingredientClass == null) return;
        synchronized (lock) {
            forcedSafeIngredientClasses.add(ingredientClass);
            ingredientCache.remove(ingredientClass);
        }
    }

    /**
     * Return true only for an exact recipe class explicitly trusted through FastSuiteAPI.
     *
     * <p>This query exists so RecipeIndex can apply the intentional precedence
     * API override -> dynamic fallback -> structural proof without invoking the broader
     * structural/bytecode classifier first.</p>
     */
    public boolean isForcedSafeRecipeClass(Class<?> recipeClass) {
        if (recipeClass == null) return false;
        synchronized (lock) {
            return forcedSafeRecipeClasses.contains(recipeClass);
        }
    }

    public void clearCaches() {
        synchronized (lock) {
            recipeCache.clear();
            ingredientCache.clear();
        }
    }

    private Safety classifyRecipeUncached(Class<?> recipeClass) {
        if (forcedSafeRecipeClasses.contains(recipeClass)) {
            return Safety.API_OVERRIDE;
        }

        for (Class<?> base : trustedRecipeBases) {
            if (recipeClass == base) {
                return Safety.TRUSTED_BASE;
            }
        }

        if (!FastSuiteConfig.automaticStructuralClassification) {
            return Safety.UNKNOWN;
        }

        for (Class<?> base : trustedRecipeBases) {
            if (!base.isAssignableFrom(recipeClass)) continue;

            boolean sameMatch = usesSameImplementationBySignature(
                recipeClass, base, boolean.class, InventoryCrafting.class, World.class);
            boolean sameIngredients = usesSameImplementationBySignature(
                recipeClass, base, NonNullList.class);

            if (sameMatch && sameIngredients) {
                return Safety.INHERITED_TRUSTED_IMPLEMENTATION;
            }
        }
        return Safety.UNKNOWN;
    }

    private Safety classifyIngredientUncached(Class<?> ingredientClass) {
        if (forcedSafeIngredientClasses.contains(ingredientClass)) {
            return Safety.API_OVERRIDE;
        }

        for (Class<?> base : trustedIngredientBases) {
            if (ingredientClass == base) {
                return Safety.TRUSTED_BASE;
            }
        }

        if (!FastSuiteConfig.automaticStructuralClassification) {
            return Safety.UNKNOWN;
        }

        for (Class<?> base : trustedIngredientBases) {
            if (!base.isAssignableFrom(ingredientClass)) continue;

            if (usesSameImplementationBySignature(
                    ingredientClass, base, boolean.class, ItemStack.class)
                && usesSameImplementationBySignature(
                    ingredientClass, base, ItemStack[].class)) {
                return Safety.INHERITED_TRUSTED_IMPLEMENTATION;
            }
        }
        return Safety.UNKNOWN;
    }

    private static boolean usesSameImplementationBySignature(Class<?> actualClass,
                                                              Class<?> trustedBase,
                                                              Class<?> returnType,
                                                              Class<?>... parameterTypes) {
        Method trusted = findPublicMethodBySignature(trustedBase, returnType, parameterTypes);
        if (trusted == null) return false;

        Method actual = findPublicMethodByNameAndParameters(
            actualClass, trusted.getName(), parameterTypes);
        return actual != null
            && actual.getDeclaringClass() == trusted.getDeclaringClass();
    }

    static Method findPublicMethodBySignature(Class<?> owner,
                                              Class<?> returnType,
                                              Class<?>... parameterTypes) {
        try {
            Method found = null;
            for (Method method : owner.getMethods()) {
                if (!returnType.isAssignableFrom(method.getReturnType())) continue;

                Class<?>[] actualParameters = method.getParameterTypes();
                if (actualParameters.length != parameterTypes.length) continue;

                boolean same = true;
                for (int i = 0; i < actualParameters.length; i++) {
                    if (actualParameters[i] != parameterTypes[i]) {
                        same = false;
                        break;
                    }
                }
                if (!same) continue;

                if (found != null) return null;
                found = method;
            }
            return found;
        } catch (LinkageError | SecurityException ignored) {
            return null;
        }
    }

    private static Method findPublicMethodByNameAndParameters(Class<?> owner,
                                                               String methodName,
                                                               Class<?>... parameterTypes) {
        try {
            return owner.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException | LinkageError | SecurityException ignored) {
            return null;
        }
    }

    private void registerOptionalTrustedIngredientBase(String className) {
        try {
            Class<?> clazz = Class.forName(className, false, RecipeSafetyClassifier.class.getClassLoader());
            if (Ingredient.class.isAssignableFrom(clazz)) {
                trustedIngredientBases.add(clazz);
            }
        } catch (ClassNotFoundException ignored) {
            // Optional Forge helper
        }
    }

    public enum Safety {
        TRUSTED_BASE(true),
        INHERITED_TRUSTED_IMPLEMENTATION(true),
        API_OVERRIDE(true),
        UNKNOWN(false);

        private final boolean safe;

        Safety(boolean safe) {
            this.safe = safe;
        }

        public boolean isSafe() {
            return safe;
        }
    }

}
