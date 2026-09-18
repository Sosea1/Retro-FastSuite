package com.sosea1.fastsuite112.recipe;

import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MandatoryStackConstraintTest {
    private static Item first;
    private static Item second;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
        first = new Item();
        second = new Item();
    }

    @Test
    void exactMetadataRejectsSameItemWithWrongMetadata() {
        MandatoryStackConstraint constraint = MandatoryStackConstraint.compile(new ItemStack[][] {
            { new ItemStack(first, 1, 3) }
        });

        assertTrue(constraint.accepts(new ItemStack[] { new ItemStack(first, 1, 3) }));
        assertFalse(constraint.accepts(new ItemStack[] { new ItemStack(first, 1, 4) }));
    }

    @Test
    void wildcardMetadataAcceptsEveryMetadataForTheItem() {
        MandatoryStackConstraint constraint = MandatoryStackConstraint.compile(new ItemStack[][] {
            { new ItemStack(first, 1, OreDictionary.WILDCARD_VALUE) }
        });

        assertTrue(constraint.accepts(new ItemStack[] { new ItemStack(first, 1, 0) }));
        assertTrue(constraint.accepts(new ItemStack[] { new ItemStack(first, 1, 12) }));
    }

    @Test
    void alternativesAcceptWhenAnyDeclaredStackIsPresent() {
        MandatoryStackConstraint constraint = MandatoryStackConstraint.compile(new ItemStack[][] {
            { new ItemStack(first, 1, 2), new ItemStack(second, 1, 5) }
        });

        assertTrue(constraint.accepts(new ItemStack[] { new ItemStack(second, 1, 5) }));
    }

    @Test
    void everyMandatoryGroupMustBePresent() {
        MandatoryStackConstraint constraint = MandatoryStackConstraint.compile(new ItemStack[][] {
            { new ItemStack(first, 1, OreDictionary.WILDCARD_VALUE) },
            { new ItemStack(second, 1, 5) }
        });

        assertFalse(constraint.accepts(new ItemStack[] { new ItemStack(first, 1, 0) }));
        assertTrue(constraint.accepts(new ItemStack[] {
            new ItemStack(first, 1, 0), new ItemStack(second, 1, 5)
        }));
    }

    @Test
    void invalidInternalInputCompilesToUnconstrained() {
        MandatoryStackConstraint constraint = MandatoryStackConstraint.compile(new ItemStack[][] {
            { ItemStack.EMPTY }
        });

        assertTrue(constraint.isUnconstrained());
        assertTrue(constraint.accepts(new ItemStack[0]));
    }
}
