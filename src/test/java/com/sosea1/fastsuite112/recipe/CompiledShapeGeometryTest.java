package com.sosea1.fastsuite112.recipe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompiledShapeGeometryTest {
    @Test
    void compilesShapeOnlyWhenPositionalColumnsAreAbsent() {
        CompiledShapeGeometry geometry = CompiledShapeGeometry.compile(
            new byte[] {2}, new byte[] {1}, new long[] {3L}, new long[] {3L},
            null, null, null, null, null, null, null);
        assertFalse(geometry.hasPositionalProbes());
        assertTrue(matches(geometry, 0, 2, 3L, new int[4]));
        assertTrue(matches(geometry, 0, 2, 12L, new int[4]));
        assertFalse(matches(geometry, 0, 2, 5L, new int[4]));
    }

    @Test
    void preservesPaddedTemplatesAndAsymmetricProbes() {
        Fixture padded = new Fixture(3, 1, 2L, new int[0], new int[0]);
        CompiledShapeGeometry geometry = compile(padded);
        assertFalse(matches(geometry, 0, 3, 1L, new int[9]));
        assertTrue(matches(geometry, 0, 3, 2L, new int[9]));
        assertFalse(matches(geometry, 0, 3, 4L, new int[9]));
        assertFalse(matches(geometry, 0, 2, 2L, new int[4]));

        Fixture asymmetric = new Fixture(2, 1, 3L, new int[] {0, 1}, new int[] {0x11, 0x22});
        geometry = compile(asymmetric);
        assertTrue(matches(geometry, 0, 2, 3L, new int[] {0x11, 0x22, 0, 0}));
        assertTrue(matches(geometry, 0, 2, 3L, new int[] {0x22, 0x11, 0, 0}));
        assertFalse(matches(geometry, 0, 2, 3L, new int[] {0x11, 0x44, 0, 0}));
    }

    @Test
    void handlesEmptyShapesZeroSignaturesAndOversizedRecipes() {
        CompiledShapeGeometry geometry = compile(
            new Fixture(3, 1, 0L, new int[] {0, 0}, new int[] {0, 0}),
            new Fixture(3, 1, 0L, new int[] {0}, new int[] {0x11}),
            new Fixture(4, 1, 0L, new int[0], new int[0]));
        assertTrue(matches(geometry, 0, 3, 0L, new int[9]));
        assertFalse(matches(geometry, 0, 2, 0L, new int[4]));
        assertFalse(matches(geometry, 1, 3, 0L, new int[9]));
        assertFalse(matches(geometry, 2, 3, 0L, new int[9]));
    }

    @Test
    void lookupBoundsReconstructEverySmallGridMask() {
        for (int width = 2; width <= 3; width++) {
            assertEquals(0L, CompiledShapeGeometry.prepareQuery(width, width, 0L));
            for (int mask = 1; mask < (1 << (width * width)); mask++) {
                int bounds = (int) CompiledShapeGeometry.prepareQuery(width, width, mask);
                int minX = (bounds >>> 1) & 3, minY = (bounds >>> 3) & 3;
                int spanW = (bounds >>> 5) & 3, spanH = (bounds >>> 7) & 3;
                int normalized = bounds >>> 9, restored = 0;
                assertTrue(spanW > 0 && spanH > 0 && minX + spanW <= width && minY + spanH <= width);
                assertNotEquals(0, normalized & 7);
                assertNotEquals(0, normalized & 0b001001001);
                assertNotEquals(0, normalized & (7 << ((spanH - 1) * 3)));
                assertNotEquals(0, normalized & (0b001001001 << (spanW - 1)));
                for (int y = 0; y < spanH; y++) {
                    for (int x = 0; x < spanW; x++) {
                        if ((normalized & (1 << (y * 3 + x))) != 0) restored |= 1 << ((minY + y) * width + minX + x);
                    }
                }
                assertEquals(mask, restored);
            }
            assertEquals(-1L, CompiledShapeGeometry.prepareQuery(width, width, 1L << (width * width)));
        }
    }

    @Test
    void retainsRawOccupancyForLargerGrids() {
        assertFalse(CompiledShapeGeometry.supports(4, 4));
        assertEquals(15L, CompiledShapeGeometry.prepareQuery(4, 4, 15L));
        ConstraintTable constraints = ConstraintTable.freeze(new RecipeConstraint[] {
            RecipeConstraint.builder().shape(4, 1, 15L, 15L).build()
        });
        assertTrue(constraints.accepts(0, 4, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 4, 4, 15L, 0L, 0L, 0));
        assertFalse(constraints.accepts(0, 4, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 4, 4, 23L, 0L, 0L, 0));
    }

    @Test
    void matchesReferencePlacementScanForAllSmallShapesAndQueryMasks() {
        List<Fixture> fixtures = new ArrayList<>();
        for (int w = 1; w <= 4; w++) {
            for (int h = 1; h <= 4; h++) {
                int maskCount = w <= 3 && h <= 3 ? 1 << (w * h) : 2;
                for (int mask = 0; mask < maskCount; mask++) {
                    fixtures.add(new Fixture(w, h, mask, new int[0], new int[0]));
                    if (mask == 0) {
                        fixtures.add(new Fixture(w, h, 0, new int[] {0, 0}, new int[] {0, 0}));
                        fixtures.add(new Fixture(w, h, 0, new int[] {0}, new int[] {0x11}));
                        continue;
                    }
                    List<Integer> occupied = new ArrayList<>();
                    for (int bit = 0; bit < w * h; bit++) {
                        if ((mask & (1 << bit)) == 0) continue;
                        occupied.add(bit);
                        // Check every occupied probe position, not just the first three cells.
                        fixtures.add(new Fixture(w, h, mask, new int[] {bit}, new int[] {0x11}));
                        fixtures.add(new Fixture(w, h, mask, new int[] {bit}, new int[] {0x1100}));
                    }
                    int count = Math.min(3, occupied.size());
                    int[] indices = {occupied.get(0), occupied.get(occupied.size() - 1), occupied.get(occupied.size() / 2)};
                    int[][] signatures = {{0x11, 0x22, 0x1100}, {0x22, 0x11, 0x44}, {0, 0x22, 0x2211}, {0, 0, 0}};
                    for (int[] signature : signatures) {
                        int[] selected = new int[count], sigs = new int[count];
                        System.arraycopy(indices, 0, selected, 0, count);
                        System.arraycopy(signature, 0, sigs, 0, count);
                        fixtures.add(new Fixture(w, h, mask, selected, sigs));
                    }
                }
            }
        }
        CompiledShapeGeometry geometry = compile(fixtures.toArray(new Fixture[0]));
        int[] samples = {0x11, 0x22, 0x44, 0x1100, 0x2211, 0xffff};
        long comparisons = 0;
        for (int width = 2; width <= 3; width++) {
            for (int mask = 0; mask < (1 << (width * width)); mask++) {
                long query = CompiledShapeGeometry.prepareQuery(width, width, mask);
                for (int pattern = 0; pattern < 4; pattern++) {
                    int[] tokens = new int[width * width];
                    for (int s = 0; s < tokens.length; s++) {
                        if ((mask & (1 << s)) != 0) tokens[s] = pattern == 3 ? 0xffff : samples[(s + pattern) % samples.length];
                    }
                    long t0 = pack(tokens, 0), t1 = pack(tokens, 4);
                    int t8 = tokens.length > 8 ? tokens[8] : 0;
                    for (int id = 0; id < fixtures.size(); id++) {
                        boolean expected = fixtures.get(id).referenceMatches(width, mask, tokens);
                        boolean actual = geometry.matches(id, width, width, query, t0, t1, t8);
                        if (expected != actual) fail("id=" + id + " grid=" + width + " mask=" + mask + " pattern=" + pattern);
                        comparisons++;
                    }
                }
            }
        }
        System.out.println("Normalized geometry: " + comparisons + " reference comparisons, zero mismatches");
    }

    private static CompiledShapeGeometry compile(Fixture... fixtures) {
        int n = fixtures.length;
        byte[] w = new byte[n], h = new byte[n], count = new byte[n];
        byte[] p1 = new byte[n], p2 = new byte[n], p3 = new byte[n];
        int[] s1 = new int[n], s2 = new int[n], s3 = new int[n];
        long[] mask = new long[n], mirror = new long[n];
        for (int id = 0; id < n; id++) {
            Fixture f = fixtures[id];
            w[id] = (byte) f.w; h[id] = (byte) f.h; count[id] = (byte) f.indices.length;
            mask[id] = f.mask;
            mirror[id] = place(f.mask, f.w, f.h, f.w, 0, 0, true);
            if (f.indices.length > 0) { p1[id] = (byte) f.indices[0]; s1[id] = f.signatures[0]; }
            if (f.indices.length > 1) { p2[id] = (byte) f.indices[1]; s2[id] = f.signatures[1]; }
            if (f.indices.length > 2) { p3[id] = (byte) f.indices[2]; s3[id] = f.signatures[2]; }
        }
        return CompiledShapeGeometry.compile(w, h, mask, mirror, count, p1, p2, p3, s1, s2, s3);
    }

    private static boolean matches(CompiledShapeGeometry geometry, int id, int width, long mask, int[] tokens) {
        return geometry.matches(id, width, width, CompiledShapeGeometry.prepareQuery(width, width, mask),
            pack(tokens, 0), pack(tokens, 4), tokens.length > 8 ? tokens[8] : 0);
    }

    private static long pack(int[] tokens, int start) {
        long packed = 0;
        for (int s = start; s < Math.min(start + 4, tokens.length); s++) packed |= ((long) tokens[s] & 0xffff) << ((s - start) * 16);
        return packed;
    }

    // Independent oracle: the previous placement scan, with no normalized bounds or packed headers.
    private static long place(long mask, int w, int h, int grid, int offsetX, int offsetY, boolean mirror) {
        long result = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((mask & (1L << (y * w + x))) != 0) result |= 1L << ((y + offsetY) * grid + offsetX + (mirror ? w - 1 - x : x));
            }
        }
        return result;
    }

    private static boolean probeMatches(int sig, int token) {
        return ((sig & 15) != 0 && (sig & token & 15) != 0 && ((sig & token & 0xf0) != 0))
            || ((sig & 0xf00) != 0 && (sig & token & 0xf00) != 0 && ((sig & token & 0xf000) != 0));
    }

    private static final class Fixture {
        final int w, h;
        final long mask;
        final int[] indices, signatures;
        final List<Placement> placements2, placements3;

        Fixture(int w, int h, long mask, int[] indices, int[] signatures) {
            this.w = w; this.h = h; this.mask = mask;
            this.indices = indices; this.signatures = signatures;
            placements2 = placements(2); placements3 = placements(3);
        }

        private List<Placement> placements(int grid) {
            List<Placement> placements = new ArrayList<>();
            for (int y = 0; y <= grid - h; y++) {
                for (int x = 0; x <= grid - w; x++) {
                    for (int m = 0; m < 2; m++) {
                        int[] slots = new int[indices.length];
                        for (int p = 0; p < slots.length; p++) {
                            int px = indices[p] % w, py = indices[p] / w;
                            slots[p] = (y + py) * grid + x + (m == 1 ? w - 1 - px : px);
                        }
                        placements.add(new Placement(place(mask, w, h, grid, x, y, m == 1), slots));
                    }
                }
            }
            return placements;
        }

        boolean referenceMatches(int grid, long query, int[] tokens) {
            for (Placement placement : grid == 2 ? placements2 : placements3) {
                if (placement.mask != query) continue;
                boolean accepts = true;
                for (int p = 0; p < signatures.length; p++) {
                    if (signatures[p] != 0 && !probeMatches(signatures[p], tokens[placement.slots[p]])) { accepts = false; break; }
                }
                if (accepts) return true;
            }
            return false;
        }
    }

    private static final class Placement {
        final long mask;
        final int[] slots;
        Placement(long mask, int[] slots) { this.mask = mask; this.slots = slots; }
    }
}
