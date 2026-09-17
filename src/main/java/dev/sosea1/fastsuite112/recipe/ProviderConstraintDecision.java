package dev.sosea1.fastsuite112.recipe;

import dev.sosea1.fastsuite112.api.RecipeConstraintSpec;
import net.minecraft.item.crafting.IRecipe;

import javax.annotation.Nullable;

/** Fail-open normalized result of consulting an exact-class constraint provider. */
final class ProviderConstraintDecision {
    enum Kind {
        NONE,
        FULL_INDEX,
        FILTER_ONLY,
        FAILURE
    }

    final Kind kind;
    final MandatoryStackConstraint constraint;
    @Nullable final Class<?> failureClass;

    private ProviderConstraintDecision(Kind kind,
                                       MandatoryStackConstraint constraint,
                                       @Nullable Class<?> failureClass) {
        this.kind = kind;
        this.constraint = constraint;
        this.failureClass = failureClass;
    }

    static ProviderConstraintDecision evaluate(RecipeConstraintProviderRegistry registry, IRecipe recipe) {
        RecipeConstraintProviderRegistry.Result result = registry.describe(recipe);
        if (result.failure != null) {
            return new ProviderConstraintDecision(Kind.FAILURE,
                MandatoryStackConstraint.UNCONSTRAINED, result.failure.getClass());
        }
        RecipeConstraintSpec spec = result.spec;
        if (spec == null) {
            return new ProviderConstraintDecision(Kind.NONE,
                MandatoryStackConstraint.UNCONSTRAINED, null);
        }
        if (spec.getMode() == RecipeConstraintSpec.Mode.FULL_INDEX) {
            return new ProviderConstraintDecision(Kind.FULL_INDEX,
                MandatoryStackConstraint.UNCONSTRAINED, null);
        }
        MandatoryStackConstraint constraint = MandatoryStackConstraint.compile(
            spec.getMandatoryAlternatives());
        if (constraint.isUnconstrained()) {
            return new ProviderConstraintDecision(Kind.NONE,
                MandatoryStackConstraint.UNCONSTRAINED, null);
        }
        return new ProviderConstraintDecision(Kind.FILTER_ONLY, constraint, null);
    }
}
