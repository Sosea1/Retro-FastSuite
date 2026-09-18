package com.sosea1.fastsuite112.api;

import net.minecraft.item.ItemStack;

/** Immutable conservative description returned by a recipe constraint provider. */
public final class RecipeConstraintSpec {
    public enum Mode {
        FULL_INDEX,
        FILTER_ONLY
    }

    private static final RecipeConstraintSpec FULL_INDEX =
        new RecipeConstraintSpec(Mode.FULL_INDEX, new ItemStack[0][]);

    private final Mode mode;
    private final ItemStack[][] mandatoryAlternatives;

    private RecipeConstraintSpec(Mode mode, ItemStack[][] mandatoryAlternatives) {
        this.mode = mode;
        this.mandatoryAlternatives = mandatoryAlternatives;
    }

    /** Certify that the recipe's exposed ingredients contain a necessary pivot. */
    public static RecipeConstraintSpec fullIndex() {
        return FULL_INDEX;
    }

    /**
     * Keep the recipe in ordered fallback, but require at least one stack from every supplied group.
     * Between one and three groups may be supplied.
     */
    public static RecipeConstraintSpec filterOnly(ItemStack[]... groups) {
        if (groups == null || groups.length == 0 || groups.length > 3) {
            throw new IllegalArgumentException("filterOnly requires one to three mandatory groups");
        }

        ItemStack[][] copy = new ItemStack[groups.length][];
        for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
            ItemStack[] group = groups[groupIndex];
            if (group == null || group.length == 0) {
                throw new IllegalArgumentException("mandatory group " + groupIndex + " is empty");
            }
            copy[groupIndex] = new ItemStack[group.length];
            for (int stackIndex = 0; stackIndex < group.length; stackIndex++) {
                ItemStack stack = group[stackIndex];
                if (stack == null || stack.isEmpty()) {
                    throw new IllegalArgumentException(
                        "mandatory group " + groupIndex + " contains an empty stack");
                }
                copy[groupIndex][stackIndex] = stack.copy();
            }
        }
        return new RecipeConstraintSpec(Mode.FILTER_ONLY, copy);
    }

    public Mode getMode() {
        return mode;
    }

    /** Return a deep defensive copy of the declared alternative groups. */
    public ItemStack[][] getMandatoryAlternatives() {
        ItemStack[][] copy = new ItemStack[mandatoryAlternatives.length][];
        for (int groupIndex = 0; groupIndex < mandatoryAlternatives.length; groupIndex++) {
            ItemStack[] group = mandatoryAlternatives[groupIndex];
            copy[groupIndex] = new ItemStack[group.length];
            for (int stackIndex = 0; stackIndex < group.length; stackIndex++) {
                copy[groupIndex][stackIndex] = group[stackIndex].copy();
            }
        }
        return copy;
    }
}
