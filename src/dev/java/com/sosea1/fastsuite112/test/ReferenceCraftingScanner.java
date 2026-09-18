package com.sosea1.fastsuite112.test;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure linear reference scanner used by the differential harness.
 *
 * <p>Unlike the old implementation, exceptions are observable outcomes. A third-party recipe that
 * throws while vanilla is scanning is not silently skipped, because vanilla itself would not skip it.</p>
 */
public final class ReferenceCraftingScanner {
    private ReferenceCraftingScanner() {}

    public enum OutcomeKind {
        MATCH,
        NO_MATCH,
        THREW
    }

    public static final class FirstMatchOutcome {
        public final OutcomeKind kind;
        public final IRecipe recipe;
        public final IRecipe throwingRecipe;
        public final Class<? extends Throwable> exceptionClass;
        public final String exceptionMessage;

        private FirstMatchOutcome(OutcomeKind kind,
                                  IRecipe recipe,
                                  IRecipe throwingRecipe,
                                  Class<? extends Throwable> exceptionClass,
                                  String exceptionMessage) {
            this.kind = kind;
            this.recipe = recipe;
            this.throwingRecipe = throwingRecipe;
            this.exceptionClass = exceptionClass;
            this.exceptionMessage = exceptionMessage;
        }

        public static FirstMatchOutcome match(IRecipe recipe) {
            return new FirstMatchOutcome(OutcomeKind.MATCH, recipe, null, null, null);
        }

        public static FirstMatchOutcome noMatch() {
            return new FirstMatchOutcome(OutcomeKind.NO_MATCH, null, null, null, null);
        }

        public static FirstMatchOutcome threw(IRecipe recipe, Throwable throwable) {
            return new FirstMatchOutcome(
                OutcomeKind.THREW,
                null,
                recipe,
                throwable.getClass(),
                throwable.getMessage()
            );
        }
    }

    public static final class AllMatchesOutcome {
        public final List<IRecipe> matches;
        public final IRecipe throwingRecipe;
        public final Class<? extends Throwable> exceptionClass;
        public final String exceptionMessage;

        private AllMatchesOutcome(List<IRecipe> matches,
                                  IRecipe throwingRecipe,
                                  Class<? extends Throwable> exceptionClass,
                                  String exceptionMessage) {
            this.matches = Collections.unmodifiableList(matches);
            this.throwingRecipe = throwingRecipe;
            this.exceptionClass = exceptionClass;
            this.exceptionMessage = exceptionMessage;
        }

        public boolean threw() {
            return exceptionClass != null;
        }

        public static AllMatchesOutcome completed(List<IRecipe> matches) {
            return new AllMatchesOutcome(new ArrayList<IRecipe>(matches), null, null, null);
        }

        public static AllMatchesOutcome threw(List<IRecipe> matches, IRecipe recipe, Throwable throwable) {
            return new AllMatchesOutcome(
                new ArrayList<IRecipe>(matches),
                recipe,
                throwable.getClass(),
                throwable.getMessage()
            );
        }
    }

    public static FirstMatchOutcome findFirstOutcome(Iterable<IRecipe> registry, InventoryCrafting matrix, World world) {
        if (registry == null || matrix == null) return FirstMatchOutcome.noMatch();
        for (IRecipe recipe : registry) {
            try {
                if (recipe.matches(matrix, world)) {
                    return FirstMatchOutcome.match(recipe);
                }
            } catch (Throwable throwable) {
                rethrowFatal(throwable);
                return FirstMatchOutcome.threw(recipe, throwable);
            }
        }
        return FirstMatchOutcome.noMatch();
    }

    public static AllMatchesOutcome findAllOutcome(Iterable<IRecipe> registry, InventoryCrafting matrix, World world) {
        List<IRecipe> matches = new ArrayList<IRecipe>();
        if (registry == null || matrix == null) return AllMatchesOutcome.completed(matches);

        for (IRecipe recipe : registry) {
            try {
                if (recipe.matches(matrix, world)) {
                    matches.add(recipe);
                }
            } catch (Throwable throwable) {
                rethrowFatal(throwable);
                return AllMatchesOutcome.threw(matches, recipe, throwable);
            }
        }
        return AllMatchesOutcome.completed(matches);
    }

    public static void rethrowFatal(Throwable throwable) {
        if (throwable instanceof ThreadDeath) {
            throw (ThreadDeath) throwable;
        }
        if (throwable instanceof VirtualMachineError) {
            throw (VirtualMachineError) throwable;
        }
    }
}
