package dev.sosea1.fastsuite112.recipe;

import net.minecraft.item.Item;

final class CandidateRouting {
    static final CandidateRouting EMPTY = new CandidateRouting(new Item[0], new ItemRoute[0], 0L, 0L);

    final Item[] items;
    final ItemRoute[] routes;
    final long matchMaskA;
    final long matchMaskB;

    CandidateRouting(Item[] items, ItemRoute[] routes, long matchMaskA, long matchMaskB) {
        this.items = items;
        this.routes = routes;
        this.matchMaskA = matchMaskA;
        this.matchMaskB = matchMaskB;
    }
}
