package dev.sosea1.fastsuite112.recipe;

import dev.sosea1.fastsuite112.api.RecipeConstraintSpec;
import net.minecraft.init.Bootstrap;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuiltInConstraintProvidersTest {
    private static Item one;
    private static Item two;
    private static Item three;
    private static Item four;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
        one = new Item();
        two = new Item();
        three = new Item();
        four = new Item();
    }

    @Test
    void ingredientProviderKeepsThreeSmallestMandatoryAlternativeGroups() {
        TestRecipe recipe = new TestRecipe(
            Ingredient.fromStacks(new ItemStack(one), new ItemStack(two), new ItemStack(three)),
            Ingredient.fromStacks(new ItemStack(two)),
            Ingredient.EMPTY,
            Ingredient.fromStacks(new ItemStack(three), new ItemStack(four)),
            Ingredient.fromStacks(new ItemStack(four), new ItemStack(one), new ItemStack(two), new ItemStack(three))
        );

        RecipeConstraintSpec spec = BuiltInConstraintProviders.describeMandatoryIngredients(recipe);

        assertEquals(RecipeConstraintSpec.Mode.FILTER_ONLY, spec.getMode());
        ItemStack[][] groups = spec.getMandatoryAlternatives();
        assertEquals(3, groups.length);
        assertEquals(1, groups[0].length);
        assertEquals(2, groups[1].length);
        assertEquals(3, groups[2].length);
        assertSame(two, groups[0][0].getItem());
        assertSame(three, groups[1][0].getItem());
        assertSame(four, groups[1][1].getItem());
    }

    @Test
    void ingredientProviderLeavesRecipeOpaqueWhenNothingCanBeEnumerated() {
        assertNull(BuiltInConstraintProviders.describeMandatoryIngredients(
            new TestRecipe(Ingredient.EMPTY, Ingredient.EMPTY)));
    }

    @Test
    void filterDoesNotRejectAlternateMetadataForNonSubtypeItem() {
        Item furnaceLike = new Item();
        RecipeConstraintSpec spec = BuiltInConstraintProviders.describeMandatoryIngredients(
            new TestRecipe(Ingredient.fromStacks(new ItemStack(furnaceLike, 1, 0))));

        MandatoryStackConstraint constraint = MandatoryStackConstraint.compile(
            spec.getMandatoryAlternatives());

        assertTrue(constraint.accepts(new ItemStack[] { new ItemStack(furnaceLike, 1, 1) }));
    }

    @Test
    void filterDoesNotRejectMetadataAcceptedBeyondCreativeEnumeration() {
        Item incompleteSubtype = new IncompletelyEnumeratedSubtypeItem();
        RecipeConstraintSpec spec = BuiltInConstraintProviders.describeMandatoryIngredients(
            new TestRecipe(Ingredient.fromStacks(
                new ItemStack(incompleteSubtype, 1, OreDictionary.WILDCARD_VALUE))));

        MandatoryStackConstraint constraint = MandatoryStackConstraint.compile(
            spec.getMandatoryAlternatives());

        assertTrue(constraint.accepts(new ItemStack[] { new ItemStack(incompleteSubtype, 1, 7) }));
    }

    @Test
    void optionalClassResolverRejectsMissingAndNonRecipeClasses() {
        ClassLoader loader = BuiltInConstraintProvidersTest.class.getClassLoader();

        assertNull(BuiltInConstraintProviders.findRecipeClass("missing.recipe.Type", loader));
        assertNull(BuiltInConstraintProviders.findRecipeClass(String.class.getName(), loader));
        assertSame(TestRecipe.class,
            BuiltInConstraintProviders.findRecipeClass(TestRecipe.class.getName(), loader));
    }

    private static final class TestRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
        private final NonNullList<Ingredient> ingredients;

        private TestRecipe(Ingredient... ingredients) {
            this.ingredients = NonNullList.from(Ingredient.EMPTY, ingredients);
        }

        @Override public boolean matches(InventoryCrafting inv, World worldIn) { return false; }
        @Override public ItemStack getCraftingResult(InventoryCrafting inv) { return ItemStack.EMPTY; }
        @Override public boolean canFit(int width, int height) { return false; }
        @Override public ItemStack getRecipeOutput() { return ItemStack.EMPTY; }
        @Override public NonNullList<Ingredient> getIngredients() { return ingredients; }
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
