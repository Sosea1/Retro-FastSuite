package com.sosea1.fastsuite112.recipe;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Compact false-positive-only presence constraints compiled from concrete stack alternatives. */
final class MandatoryStackConstraint {
    static final MandatoryStackConstraint UNCONSTRAINED =
        new MandatoryStackConstraint(0, new long[0], new long[0]);

    private final int groupCount;
    private final long[] masksA;
    private final long[] masksB;

    private MandatoryStackConstraint(int groupCount, long[] masksA, long[] masksB) {
        this.groupCount = groupCount;
        this.masksA = masksA;
        this.masksB = masksB;
    }

    static MandatoryStackConstraint compile(ItemStack[][] groups) {
        if (groups == null || groups.length == 0 || groups.length > 3) return UNCONSTRAINED;
        long[] masksA = new long[groups.length];
        long[] masksB = new long[groups.length];
        for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
            ItemStack[] group = groups[groupIndex];
            if (group == null || group.length == 0) return UNCONSTRAINED;
            for (ItemStack stack : group) {
                if (stack == null || stack.isEmpty() || stack.getItem() == null) return UNCONSTRAINED;
                Item item = stack.getItem();
                int metadata = stack.getMetadata();
                if (metadata == OreDictionary.WILDCARD_VALUE) {
                    masksA[groupIndex] |= presenceBitA(item);
                    masksB[groupIndex] |= presenceBitB(item);
                } else {
                    masksA[groupIndex] |= exactVariantBitA(item, metadata);
                    masksB[groupIndex] |= exactVariantBitB(item, metadata);
                }
            }
            if (masksA[groupIndex] == 0L || masksB[groupIndex] == 0L) return UNCONSTRAINED;
        }
        return new MandatoryStackConstraint(groups.length, masksA, masksB);
    }

    boolean isUnconstrained() {
        return groupCount == 0;
    }

    int groupCount() {
        return groupCount;
    }

    long maskA(int group) {
        return masksA[group];
    }

    long maskB(int group) {
        return masksB[group];
    }

    boolean accepts(ItemStack[] query) {
        if (isUnconstrained()) return true;
        long queryMaskA = 0L;
        long queryMaskB = 0L;
        if (query != null) {
            for (ItemStack stack : query) {
                if (stack == null || stack.isEmpty() || stack.getItem() == null) continue;
                Item item = stack.getItem();
                queryMaskA |= presenceBitA(item) | exactVariantBitA(item, stack.getMetadata());
                queryMaskB |= presenceBitB(item) | exactVariantBitB(item, stack.getMetadata());
            }
        }
        return acceptsMasks(queryMaskA, queryMaskB);
    }

    boolean acceptsMasks(long queryMaskA, long queryMaskB) {
        for (int group = 0; group < groupCount; group++) {
            if ((queryMaskA & masksA[group]) == 0L || (queryMaskB & masksB[group]) == 0L) return false;
        }
        return true;
    }

    static long presenceBitA(Item item) {
        int hash = System.identityHashCode(item);
        hash ^= hash >>> 16;
        return 1L << (hash & 63);
    }

    static long presenceBitB(Item item) {
        int hash = System.identityHashCode(item);
        hash *= 0x9E3779B9;
        hash = Integer.rotateLeft(hash, 13) ^ (hash >>> 7) ^ 0x85EBCA6B;
        return 1L << (hash & 63);
    }

    static long exactVariantBitA(Item item, int metadata) {
        int hash = System.identityHashCode(item);
        hash ^= metadata * 0x9E3779B9;
        hash ^= hash >>> 16;
        hash *= 0x85EBCA6B;
        return 1L << (hash & 63);
    }

    static long exactVariantBitB(Item item, int metadata) {
        int hash = System.identityHashCode(item) * 0xC2B2AE35;
        hash ^= Integer.rotateLeft(metadata * 0x27D4EB2D, 11);
        hash ^= hash >>> 15;
        hash *= 0x165667B1;
        return 1L << (hash & 63);
    }
}
