package dev.sosea1.fastsuite112.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SaturatingCountSketchTest {
    @Test
    public void countsAndSaturates() {
        long a = 0L;
        long b = 0L;
        for (int i = 0; i < 20; i++) {
            a = SaturatingCountSketch.increment(a, 3);
            b = SaturatingCountSketch.increment(b, 11);
        }
        assertEquals(15, SaturatingCountSketch.estimate(a, 3, b, 11));
        assertEquals(15, SaturatingCountSketch.capRequirement(27));
    }

    @Test
    public void minLaneLimitsSingleLaneCollisions() {
        long a = 0L;
        long b = 0L;
        a = SaturatingCountSketch.increment(a, 4);
        b = SaturatingCountSketch.increment(b, 9);
        for (int i = 0; i < 6; i++) a = SaturatingCountSketch.increment(a, 4);
        assertEquals(1, SaturatingCountSketch.estimate(a, 4, b, 9));
    }
}
