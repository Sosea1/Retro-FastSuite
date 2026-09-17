package dev.sosea1.fastsuite112.recipe;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Arrays;
import java.util.IdentityHashMap;

final class IngredientRoutingCompiler {
    private IngredientRoutingCompiler() {}

    static CandidateRouting compile(Ingredient ingredient, boolean metadataSafe) {
        ItemStack[] matches = ingredient.getMatchingStacks();
        if (matches == null || matches.length == 0) return CandidateRouting.EMPTY;

        IdentityHashMap<Item, MutableItemRoute> byItem = collectRoutes(matches);
        if (byItem.isEmpty()) return CandidateRouting.EMPTY;

        ItemRoute[] routes = new ItemRoute[byItem.size()];
        Item[] items = new Item[byItem.size()];
        long matchMaskA = 0L;
        long matchMaskB = 0L;
        int index = 0;
        for (MutableItemRoute mutable : byItem.values()) {
            ItemRoute route = conservativeRoute(ingredient, mutable, metadataSafe);
            routes[index] = route;
            items[index] = route.item;
            if (route.itemWide) {
                matchMaskA |= MandatoryStackConstraint.presenceBitA(route.item);
                matchMaskB |= MandatoryStackConstraint.presenceBitB(route.item);
            } else {
                for (int metadata : route.exactMetas) {
                    matchMaskA |= MandatoryStackConstraint.exactVariantBitA(route.item, metadata);
                    matchMaskB |= MandatoryStackConstraint.exactVariantBitB(route.item, metadata);
                }
            }
            index++;
        }
        return new CandidateRouting(items, routes, matchMaskA, matchMaskB);
    }

    static ItemStack[] copyConservativeAlternatives(Ingredient ingredient) {
        ItemStack[] matches = ingredient.getMatchingStacks();
        if (matches == null || matches.length == 0) return new ItemStack[0];

        IdentityHashMap<Item, MutableItemRoute> byItem = collectRoutes(matches);
        IdentityHashMap<Item, ItemRoute> routes = new IdentityHashMap<Item, ItemRoute>();
        for (MutableItemRoute mutable : byItem.values()) {
            routes.put(mutable.item, conservativeRoute(ingredient, mutable, true));
        }

        ItemStack[] copied = new ItemStack[matches.length];
        for (int i = 0; i < matches.length; i++) {
            ItemStack stack = matches[i];
            if (stack == null || stack.isEmpty()) {
                copied[i] = ItemStack.EMPTY;
                continue;
            }
            ItemStack copy = stack.copy();
            ItemRoute route = routes.get(copy.getItem());
            if (route != null && route.itemWide) copy.setItemDamage(OreDictionary.WILDCARD_VALUE);
            copied[i] = copy;
        }
        return copied;
    }

    private static ItemRoute conservativeRoute(Ingredient ingredient, MutableItemRoute mutable,
                                                boolean metadataSafe) {
        if (!mutable.item.getHasSubtypes()) {
            // RecipeItemHelper, used by vanilla and Forge shapeless recipes, normalizes the
            // metadata of non-subtype Items to zero. Route them by Item so the index remains
            // a conservative superset of those recipe matchers.
            mutable.itemWide = true;
        } else if (metadataSafe && !mutable.itemWide
            && ingredient.apply(new ItemStack(mutable.item, 1, OreDictionary.WILDCARD_VALUE))) {
            // Forge's patched 1.12.2 Ingredient expands wildcard stacks through getSubItems()
            // before exposing getMatchingStacks(). Some mod items omit valid metadata values
            // from that creative list even though Ingredient.apply() still accepts every meta.
            // Probe the trusted predicate itself so those recipes remain in the Item-wide route.
            mutable.itemWide = true;
        }
        ItemRoute route = mutable.freeze();
        if (!metadataSafe && !route.itemWide) {
            // Public API overrides promise only Item-level completeness, not metadata semantics.
            return new ItemRoute(route.item, true, new int[0]);
        }
        return route;
    }

    private static IdentityHashMap<Item, MutableItemRoute> collectRoutes(ItemStack[] matches) {
        IdentityHashMap<Item, MutableItemRoute> byItem = new IdentityHashMap<Item, MutableItemRoute>();
        for (ItemStack stack : matches) {
            if (stack == null || stack.isEmpty()) continue;
            Item item = stack.getItem();
            MutableItemRoute route = byItem.get(item);
            if (route == null) {
                route = new MutableItemRoute(item);
                byItem.put(item, route);
            }
            int metadata = stack.getMetadata();
            if (metadata == OreDictionary.WILDCARD_VALUE) route.itemWide = true;
            else route.addExact(metadata);
        }
        return byItem;
    }

    private static final class MutableItemRoute {
        private final Item item;
        private boolean itemWide;
        private int[] exactMetas = new int[4];
        private int exactMetaCount;

        private MutableItemRoute(Item item) {
            this.item = item;
        }

        private void addExact(int metadata) {
            if (itemWide) return;
            for (int i = 0; i < exactMetaCount; i++) {
                if (exactMetas[i] == metadata) return;
            }
            if (exactMetaCount == exactMetas.length) {
                exactMetas = Arrays.copyOf(exactMetas, exactMetas.length + Math.max(4, exactMetas.length / 2));
            }
            exactMetas[exactMetaCount++] = metadata;
        }

        private ItemRoute freeze() {
            if (itemWide) return new ItemRoute(item, true, new int[0]);
            int[] frozen = exactMetaCount == 0 ? new int[0] : Arrays.copyOf(exactMetas, exactMetaCount);
            Arrays.sort(frozen);
            return new ItemRoute(item, false, frozen);
        }
    }
}
