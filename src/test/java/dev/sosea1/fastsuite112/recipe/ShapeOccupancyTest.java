package dev.sosea1.fastsuite112.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShapeOccupancyTest {
    @Test
    void translatesTwoByTwoPatternInsideThreeByThreeGrid() {
        long local = 0b1111L;
        long mirrored = ShapeOccupancy.mirror(local, 2, 2);
        long bottomRight = ShapeOccupancy.place(local, 2, 2, 3, 1, 1);
        assertTrue(ShapeOccupancy.matches(bottomRight, 3, 3, 2, 2, local, mirrored));
    }

    @Test
    void acceptsMirroredAsymmetricOccupancy() {
        // XX / X.  mirrored is XX / .X
        long local = 0b0111L;
        long mirrored = ShapeOccupancy.mirror(local, 2, 2);
        long mirroredPlaced = ShapeOccupancy.place(mirrored, 2, 2, 3, 0, 0);
        assertTrue(ShapeOccupancy.matches(mirroredPlaced, 3, 3, 2, 2, local, mirrored));
    }

    @Test
    void rejectsExtraOccupiedSlotAndOversizedRecipe() {
        long local = 0b1111L;
        long mirrored = ShapeOccupancy.mirror(local, 2, 2);
        long placed = ShapeOccupancy.place(local, 2, 2, 3, 0, 0);
        assertFalse(ShapeOccupancy.matches(placed | (1L << 8), 3, 3, 2, 2, local, mirrored));
        assertFalse(ShapeOccupancy.matches(placed, 2, 2, 3, 3, 0x1ffL, 0x1ffL));
    }
}
