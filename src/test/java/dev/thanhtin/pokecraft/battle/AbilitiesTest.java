package dev.thanhtin.pokecraft.battle;

import dev.thanhtin.pokecraft.species.PokemonType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbilitiesTest {

    @Test
    void typeImmunities() {
        assertTrue(Abilities.immune("levitate", PokemonType.GROUND));
        assertFalse(Abilities.immune("levitate", PokemonType.WATER));
        assertTrue(Abilities.immune("water-absorb", PokemonType.WATER));   // hyphenated id
        assertTrue(Abilities.immune("Volt_Absorb", PokemonType.ELECTRIC)); // mixed case/underscore
        assertTrue(Abilities.immune("flashfire", PokemonType.FIRE));
        assertFalse(Abilities.immune("", PokemonType.GROUND));
        assertFalse(Abilities.immune(null, PokemonType.GROUND));
    }

    @Test
    void offensiveMultipliers() {
        // Huge Power doubles physical only
        assertEquals(2.0, Abilities.attackMultiplier("huge-power", PokemonType.NORMAL, true, false, 1.0));
        assertEquals(1.0, Abilities.attackMultiplier("huge-power", PokemonType.NORMAL, false, false, 1.0));
        // Guts: +50% while statused
        assertEquals(1.5, Abilities.attackMultiplier("guts", PokemonType.NORMAL, true, true, 1.0));
        assertEquals(1.0, Abilities.attackMultiplier("guts", PokemonType.NORMAL, true, false, 1.0));
        // Blaze: +50% to Fire moves only in the pinch (<= 1/3 HP)
        assertEquals(1.5, Abilities.attackMultiplier("blaze", PokemonType.FIRE, false, false, 0.3));
        assertEquals(1.0, Abilities.attackMultiplier("blaze", PokemonType.FIRE, false, false, 0.9));
        assertEquals(1.0, Abilities.attackMultiplier("blaze", PokemonType.WATER, false, false, 0.3));
    }

    @Test
    void defensiveMultipliers() {
        assertEquals(0.5, Abilities.defenseMultiplier("thick-fat", PokemonType.FIRE, 1.0, false));
        assertEquals(0.5, Abilities.defenseMultiplier("thick-fat", PokemonType.ICE, 1.0, false));
        assertEquals(1.0, Abilities.defenseMultiplier("thick-fat", PokemonType.WATER, 1.0, false));
        assertEquals(0.5, Abilities.defenseMultiplier("multiscale", PokemonType.NORMAL, 1.0, true));
        assertEquals(1.0, Abilities.defenseMultiplier("multiscale", PokemonType.NORMAL, 1.0, false));
        assertEquals(0.75, Abilities.defenseMultiplier("filter", PokemonType.FIRE, 2.0, false));
        assertEquals(1.0, Abilities.defenseMultiplier("filter", PokemonType.FIRE, 1.0, false));
    }

    @Test
    void gutsIgnoresBurn() {
        assertTrue(Abilities.ignoresBurn("guts"));
        assertFalse(Abilities.ignoresBurn("blaze"));
    }

    @Test
    void sturdyAndRoughSkinRecognised() {
        assertTrue(Abilities.sturdy("sturdy"));
        assertFalse(Abilities.sturdy("levitate"));
        assertTrue(Abilities.roughSkin("rough-skin"));
        assertTrue(Abilities.roughSkin("Iron_Barbs"));
        assertFalse(Abilities.roughSkin("static"));
    }
}
