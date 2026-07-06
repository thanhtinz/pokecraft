package dev.thanhtin.pokecraft.pokemon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperienceCurveTest {

    @Test
    void expForLevelIsCubic() {
        assertEquals(1, ExperienceCurve.expForLevel(1));
        assertEquals(8, ExperienceCurve.expForLevel(2));
        assertEquals(1000, ExperienceCurve.expForLevel(10));
        assertEquals(1_000_000, ExperienceCurve.expForLevel(100));
    }

    @Test
    void expForLevelClampsOutOfRange() {
        assertEquals(1, ExperienceCurve.expForLevel(0));
        assertEquals(1, ExperienceCurve.expForLevel(-5));
        assertEquals(1_000_000, ExperienceCurve.expForLevel(150));
    }

    @Test
    void levelForExpMatchesBoundaries() {
        assertEquals(1, ExperienceCurve.levelForExp(0));
        assertEquals(1, ExperienceCurve.levelForExp(7));
        assertEquals(2, ExperienceCurve.levelForExp(8));
        assertEquals(9, ExperienceCurve.levelForExp(999));
        assertEquals(10, ExperienceCurve.levelForExp(1000));
        assertEquals(100, ExperienceCurve.levelForExp(Long.MAX_VALUE));
    }

    @Test
    void curvesAreInverse() {
        for (int level = 1; level <= 100; level++) {
            assertEquals(level, ExperienceCurve.levelForExp(ExperienceCurve.expForLevel(level)));
        }
    }
}
