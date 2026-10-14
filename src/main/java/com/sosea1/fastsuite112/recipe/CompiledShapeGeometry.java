package com.sosea1.fastsuite112.recipe;

/**
 * Geometry and up to three ingredient probes for the vanilla crafting grids.
 * The occupied bounding box determines at most one offset per orientation; the full recipe
 * dimensions still constrain that offset, including empty rows and columns around its contents.
 */
final class CompiledShapeGeometry {
    static final CompiledShapeGeometry EMPTY = new CompiledShapeGeometry(new long[0], null, null, null, false);
    private static final int[] BOUNDS_2 = buildBounds(2);
    private static final int[] BOUNDS_3 = buildBounds(3);
    private static final long NONEMPTY = 1L;
    private static final long EMPTY_SHAPE = 2L;
    private static final long MIRRORED = 4L;

    private final long[] headers;
    private final int[] signature1;
    private final int[] signature2;
    private final int[] signature3;
    private final boolean positional;

    private CompiledShapeGeometry(long[] headers, int[] signature1, int[] signature2,
                                  int[] signature3, boolean positional) {
        this.headers = headers;
        this.signature1 = signature1;
        this.signature2 = signature2;
        this.signature3 = signature3;
        this.positional = positional;
    }

    static CompiledShapeGeometry compile(byte[] widths, byte[] heights, long[] masks, long[] mirrors,
                                         byte[] counts, byte[] index1, byte[] index2, byte[] index3,
                                         int[] sig1, int[] sig2, int[] sig3) {
        if (widths == null || heights == null || masks == null || mirrors == null) return EMPTY;
        int size = widths.length;
        if (heights.length != size || masks.length != size || mirrors.length != size
            || (counts != null && (counts.length != size || index1 == null || index1.length != size
            || index2 == null || index2.length != size || index3 == null || index3.length != size
            || sig1 == null || sig1.length != size || sig2 == null || sig2.length != size
            || sig3 == null || sig3.length != size))) {
            throw new IllegalArgumentException("Mismatched geometry constraint columns");
        }
        long[] headers = new long[size];
        boolean positional = false;
        for (int id = 0; id < size; id++) {
            int probes = counts == null ? 0 : counts[id] & 0xff;
            if (probes > 3) throw new IllegalArgumentException("Too many positional probes: " + probes);
            positional |= probes != 0;
            int w = widths[id] & 0xff;
            int h = heights[id] & 0xff;
            if (w == 0 || h == 0 || w > 3 || h > 3) continue;
            long dimensions = ((long) w << 3) | ((long) h << 5);
            if (masks[id] == 0L) {
                // Zero signatures are skipped by the old matcher, even with a nonzero probe count.
                if ((probes < 1 || sig1[id] == 0) && (probes < 2 || sig2[id] == 0)
                    && (probes < 3 || sig3[id] == 0)) headers[id] = EMPTY_SHAPE | dimensions;
                continue;
            }
            int bounds = describe(masks[id], w, h);
            int minX = (bounds >>> 1) & 3;
            int minY = (bounds >>> 3) & 3;
            int spanW = (bounds >>> 5) & 3;
            int spanH = (bounds >>> 7) & 3;
            int maxX = minX + spanW - 1;
            int mirrorMinX = w - 1 - maxX;
            int normal = bounds >>> 9;
            int mirrored = describe(mirrors[id], w, h) >>> 9;
            int normalProbes = 0;
            int mirroredProbes = 0;
            for (int p = 0; p < probes; p++) {
                int index = (p == 0 ? index1[id] : p == 1 ? index2[id] : index3[id]) & 0xff;
                int signature = p == 0 ? sig1[id] : p == 1 ? sig2[id] : sig3[id];
                if (signature == 0) continue;
                int x = index % w;
                int y = index / w;
                normalProbes |= ((y - minY) << 2 | (x - minX)) << (p * 4);
                mirroredProbes |= ((y - minY) << 2 | (maxX - x)) << (p * 4);
            }
            // Bits 0..2: flags; 3..16: dimensions and margins; 17..34: occupancy masks;
            // 35..36: probe count; 37..60: three coordinates for each orientation.
            long header = NONEMPTY | dimensions | ((long) spanW << 7) | ((long) spanH << 9)
                | ((long) minX << 11) | ((long) minY << 13) | ((long) mirrorMinX << 15)
                | ((long) normal << 17) | ((long) mirrored << 26) | ((long) probes << 35)
                | ((long) normalProbes << 37) | ((long) mirroredProbes << 49);
            if (normal != mirrored || minX != mirrorMinX || normalProbes != mirroredProbes) header |= MIRRORED;
            headers[id] = header;
        }
        return new CompiledShapeGeometry(headers, positional ? sig1 : null,
            positional ? sig2 : null, positional ? sig3 : null, positional);
    }

    boolean hasPositionalProbes() {
        return positional;
    }

    static boolean supports(int width, int height) {
        return width == height && (width == 2 || width == 3);
    }

    /** Packed bounds for 2x2/3x3; other grids retain their raw occupancy for ShapeOccupancy. */
    static long prepareQuery(int width, int height, long occupancy) {
        if (!supports(width, height)) return occupancy;
        int[] lookup = width == 2 ? BOUNDS_2 : BOUNDS_3;
        return occupancy >= 0 && occupancy < lookup.length ? lookup[(int) occupancy] : -1L;
    }

    boolean matches(int id, int gridWidth, int gridHeight, long queryBounds,
                    long tokens0, long tokens1, int token8) {
        if (id < 0 || id >= headers.length || queryBounds < 0) return false;
        long header = headers[id];
        int width = (int) (header >>> 3) & 3;
        int height = (int) (header >>> 5) & 3;
        if (width > gridWidth || height > gridHeight) return false;
        if ((header & EMPTY_SHAPE) != 0) return queryBounds == 0;
        if ((header & NONEMPTY) == 0 || queryBounds == 0) return false;
        int query = (int) queryBounds;
        if (((query >>> 5) & 3) != ((header >>> 7) & 3)
            || ((query >>> 7) & 3) != ((header >>> 9) & 3)) return false;
        int minX = (query >>> 1) & 3;
        int minY = (query >>> 3) & 3;
        int yOffset = minY - ((int) (header >>> 13) & 3);
        if (yOffset < 0 || yOffset + height > gridHeight) return false;
        int normalX = minX - ((int) (header >>> 11) & 3);
        int mask = query >>> 9;
        if (normalX >= 0 && normalX + width <= gridWidth && mask == ((header >>> 17) & 0x1ff)
            && probesMatch(id, header, 37, gridWidth, minX, minY, tokens0, tokens1, token8)) return true;
        int mirrorX = minX - ((int) (header >>> 15) & 3);
        return (header & MIRRORED) != 0 && mirrorX >= 0 && mirrorX + width <= gridWidth
            && mask == ((header >>> 26) & 0x1ff)
            && probesMatch(id, header, 49, gridWidth, minX, minY, tokens0, tokens1, token8);
    }

    private boolean probesMatch(int id, long header, int shift, int gridWidth, int minX, int minY,
                                long tokens0, long tokens1, int token8) {
        int count = (int) (header >>> 35) & 3;
        int coordinates = (int) (header >>> shift);
        for (int p = 0; p < count; p++) {
            int signature = p == 0 ? signature1[id] : p == 1 ? signature2[id] : signature3[id];
            if (signature == 0) continue;
            int coordinate = (coordinates >>> (p * 4)) & 15;
            int slot = (minY + (coordinate >>> 2)) * gridWidth + minX + (coordinate & 3);
            int token = slot < 4 ? (int) (tokens0 >>> (slot * 16)) & 0xffff
                : slot < 8 ? (int) (tokens1 >>> ((slot - 4) * 16)) & 0xffff
                : slot == 8 ? token8 & 0xffff : 0;
            if (!probeMatches(signature, token)) return false;
        }
        return true;
    }

    private static boolean probeMatches(int signature, int token) {
        int itemA = signature & 15;
        int itemB = (signature >>> 4) & 15;
        if (itemA != 0 && (token & itemA) != 0 && (((token >>> 4) & itemB) != 0)) return true;
        int variantA = (signature >>> 8) & 15;
        int variantB = (signature >>> 12) & 15;
        return variantA != 0 && (((token >>> 8) & variantA) != 0) && (((token >>> 12) & variantB) != 0);
    }

    private static int[] buildBounds(int width) {
        int[] lookup = new int[1 << (width * width)];
        for (int mask = 1; mask < lookup.length; mask++) lookup[mask] = describe(mask, width, width);
        return lookup;
    }

    private static int describe(long mask, int width, int height) {
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((mask & (1L << (y * width + x))) == 0) continue;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) return 0;
        int normalized = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if ((mask & (1L << (y * width + x))) != 0) normalized |= 1 << ((y - minY) * 3 + x - minX);
            }
        }
        return 1 | (minX << 1) | (minY << 3) | ((maxX - minX + 1) << 5)
            | ((maxY - minY + 1) << 7) | (normalized << 9);
    }
}
