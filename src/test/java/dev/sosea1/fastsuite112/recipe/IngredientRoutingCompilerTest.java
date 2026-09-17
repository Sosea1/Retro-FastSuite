package dev.sosea1.fastsuite112.recipe;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraftforge.oredict.OreDictionary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IngredientRoutingCompilerTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
    }

    @Test
    void wildcardIngredientUsesItemWideRouteWhenCreativeVariantsAreIncomplete() {
        Item item = new IncompletelyEnumeratedSubtypeItem();
        Ingredient ingredient = Ingredient.fromStacks(
            new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE));

        CandidateRouting routing = IngredientRoutingCompiler.compile(ingredient, true);

        assertEquals(1, routing.routes.length);
        assertSame(item, routing.routes[0].item);
        assertTrue(routing.routes[0].itemWide);
        assertArrayEquals(new int[0], routing.routes[0].exactMetas);
        assertEquals(1, ingredient.getMatchingStacks().length);
        assertEquals(0, ingredient.getMatchingStacks()[0].getMetadata());
        assertTrue(ingredient.apply(new ItemStack(item, 1, 7)));
    }

    @Test
    void conservativeAlternativesCopyAndWidenAcceptedMetadata() {
        Item item = new IncompletelyEnumeratedSubtypeItem();
        Ingredient ingredient = Ingredient.fromStacks(
            new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE));

        ItemStack[] copied = IngredientRoutingCompiler.copyConservativeAlternatives(ingredient);

        assertEquals(1, copied.length);
        assertEquals(OreDictionary.WILDCARD_VALUE, copied[0].getMetadata());
        assertNotSame(ingredient.getMatchingStacks()[0], copied[0]);
    }

    @Test
    void nonSubtypeItemUsesItemWideRouteForForgeShapelessMetadataNormalization() {
        Item item = new Item();

        CandidateRouting routing = IngredientRoutingCompiler.compile(
            Ingredient.fromStacks(new ItemStack(item, 1, 0)), true);

        assertTrue(routing.routes[0].itemWide);
    }

    @Test
    void exactSubtypeMetadataStaysExact() {
        Item item = new Item().setHasSubtypes(true);
        CandidateRouting routing = IngredientRoutingCompiler.compile(
            Ingredient.fromStacks(new ItemStack(item, 1, 4)), true);

        assertFalse(routing.routes[0].itemWide);
        assertArrayEquals(new int[] { 4 }, routing.routes[0].exactMetas);
    }

    @Test
    void apiOverrideUsesItemWideRoute() {
        Item item = new Item().setHasSubtypes(true);
        CandidateRouting routing = IngredientRoutingCompiler.compile(
            Ingredient.fromStacks(new ItemStack(item, 1, 4)), false);

        assertTrue(routing.routes[0].itemWide);
    }

    private static final class IncompletelyEnumeratedSubtypeItem extends Item {
        private IncompletelyEnumeratedSubtypeItem() {
            setHasSubtypes(true);
        }

        @Override
        public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
            items.add(new ItemStack(this, 1, 0));
        }
    }
}
