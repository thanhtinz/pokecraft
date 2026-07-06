package dev.thanhtin.pokecraft.pokemon;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BreedingTest {

    /** child -> parent map for two families: bulbasaur line and pikachu line. */
    static final Map<String, String> PARENTS = Map.of(
            "ivysaur", "bulbasaur",
            "venusaur", "ivysaur",
            "raichu", "pikachu");

    @Test
    void baseFormWalksChainToRoot() {
        assertEquals("bulbasaur", Breeding.baseForm("venusaur", PARENTS));
        assertEquals("bulbasaur", Breeding.baseForm("ivysaur", PARENTS));
        assertEquals("bulbasaur", Breeding.baseForm("bulbasaur", PARENTS));
        assertEquals("pikachu", Breeding.baseForm("raichu", PARENTS));
        assertEquals("unknown", Breeding.baseForm("unknown", PARENTS));
    }

    @Test
    void baseFormSurvivesCycles() {
        Map<String, String> cyclic = Map.of("a", "b", "b", "a");
        String base = Breeding.baseForm("a", cyclic);
        assertTrue(base.equals("a") || base.equals("b")); // terminates, no infinite loop
    }

    @Test
    void compatibilityIsByFamily() {
        assertTrue(Breeding.compatible("venusaur", "bulbasaur", PARENTS));
        assertTrue(Breeding.compatible("ivysaur", "venusaur", PARENTS));
        assertTrue(Breeding.compatible("pikachu", "raichu", PARENTS));
        assertFalse(Breeding.compatible("venusaur", "raichu", PARENTS));
    }

    @Test
    void childIvsAreValidAndInherit() {
        int[] parentA = {31, 31, 31, 31, 31, 31};
        int[] parentB = {31, 31, 31, 31, 31, 31};
        Random rnd = new Random(42);
        for (int run = 0; run < 100; run++) {
            int[] child = Breeding.childIvs(parentA, parentB, rnd);
            assertEquals(6, child.length);
            int inherited = 0;
            for (int i = 0; i < 6; i++) {
                assertTrue(child[i] >= 0 && child[i] <= 31);
                if (child[i] == 31) inherited++;
            }
            // both parents perfect: at least the 3 inherited stats must be 31
            assertTrue(inherited >= Breeding.INHERITED_IVS,
                    "expected >= 3 inherited perfect IVs, got " + inherited);
        }
    }
}
