package dev.sosea1.fastsuite112.recipe;

import dev.sosea1.fastsuite112.api.RecipeConstraintProvider;
import dev.sosea1.fastsuite112.api.RecipeConstraintSpec;
import net.minecraft.item.crafting.IRecipe;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

/** Exact-class provider registry. Provider code is never invoked while the registry lock is held. */
final class RecipeConstraintProviderRegistry {
    private final Object lock = new Object();
    private final IdentityHashMap<Class<?>, RecipeConstraintProvider<?>> providers =
        new IdentityHashMap<Class<?>, RecipeConstraintProvider<?>>();

    <T extends IRecipe> void register(Class<T> recipeClass,
                                      RecipeConstraintProvider<? super T> provider) {
        if (recipeClass == null) throw new IllegalArgumentException("recipeClass");
        if (provider == null) throw new IllegalArgumentException("provider");
        synchronized (lock) {
            providers.put(recipeClass, provider);
        }
    }

    Result describe(IRecipe recipe) {
        if (recipe == null) return Result.missing();

        RecipeConstraintProvider<?> provider;
        synchronized (lock) {
            provider = providers.get(recipe.getClass());
        }
        if (provider == null) return Result.missing();

        try {
            return Result.described(invoke(provider, recipe));
        } catch (RuntimeException | LinkageError failure) {
            return Result.failed(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static RecipeConstraintSpec invoke(RecipeConstraintProvider<?> provider, IRecipe recipe) {
        return ((RecipeConstraintProvider<IRecipe>) provider).describe(recipe);
    }

    static final class Result {
        final boolean hasProvider;
        @Nullable final RecipeConstraintSpec spec;
        @Nullable final Throwable failure;

        private Result(boolean hasProvider,
                       @Nullable RecipeConstraintSpec spec,
                       @Nullable Throwable failure) {
            this.hasProvider = hasProvider;
            this.spec = spec;
            this.failure = failure;
        }

        private static Result missing() {
            return new Result(false, null, null);
        }

        private static Result described(@Nullable RecipeConstraintSpec spec) {
            return new Result(true, spec, null);
        }

        private static Result failed(Throwable failure) {
            return new Result(true, null, failure);
        }
    }
}
