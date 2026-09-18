package com.sosea1.fastsuite112.api;

import net.minecraft.item.crafting.IRecipe;

/**
 * Allocation-friendly callback for ordered recipe-conflict scans.
 * Return {@code true} to continue visiting later matches or {@code false} to stop early.
 */
@FunctionalInterface
public interface RecipeMatchVisitor {
    boolean visit(IRecipe recipe);
}
