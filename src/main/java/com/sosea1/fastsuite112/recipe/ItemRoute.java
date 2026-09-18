package com.sosea1.fastsuite112.recipe;

import net.minecraft.item.Item;

final class ItemRoute {
    final Item item;
    final boolean itemWide;
    final int[] exactMetas;

    ItemRoute(Item item, boolean itemWide, int[] exactMetas) {
        this.item = item;
        this.itemWide = itemWide;
        this.exactMetas = exactMetas;
    }
}
