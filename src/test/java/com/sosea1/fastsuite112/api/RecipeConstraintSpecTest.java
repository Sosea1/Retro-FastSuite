package com.sosea1.fastsuite112.api;

import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RecipeConstraintSpecTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
    }

    @Test
    void filterOnlyDefensivelyCopiesGroupsAndStacks() {
        ItemStack original = new ItemStack(new Item(), 1, 3);
        ItemStack[] group = { original };

        RecipeConstraintSpec spec = RecipeConstraintSpec.filterOnly(group);
        group[0] = ItemStack.EMPTY;
        original.setItemDamage(7);

        ItemStack[][] firstCopy = spec.getMandatoryAlternatives();
        assertEquals(3, firstCopy[0][0].getMetadata());
        firstCopy[0][0].setItemDamage(9);
        assertEquals(3, spec.getMandatoryAlternatives()[0][0].getMetadata());
    }

    @Test
    void filterOnlyRejectsMoreThanThreeGroups() {
        ItemStack[] group = { new ItemStack(new Item()) };
        assertThrows(IllegalArgumentException.class,
            () -> RecipeConstraintSpec.filterOnly(group, group, group, group));
    }

    @Test
    void filterOnlyRejectsEmptyOrInvalidGroups() {
        assertThrows(IllegalArgumentException.class, RecipeConstraintSpec::filterOnly);
        assertThrows(IllegalArgumentException.class,
            () -> RecipeConstraintSpec.filterOnly(new ItemStack[0]));
        assertThrows(IllegalArgumentException.class,
            () -> RecipeConstraintSpec.filterOnly(new ItemStack[] { ItemStack.EMPTY }));
    }

    @Test
    void fullIndexContainsNoMandatoryFallbackGroups() {
        RecipeConstraintSpec spec = RecipeConstraintSpec.fullIndex();
        assertEquals(RecipeConstraintSpec.Mode.FULL_INDEX, spec.getMode());
        assertEquals(0, spec.getMandatoryAlternatives().length);
    }
}
