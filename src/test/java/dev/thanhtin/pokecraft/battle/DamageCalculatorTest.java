package dev.thanhtin.pokecraft.battle;

import dev.thanhtin.pokecraft.pokemon.Nature;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.pokemon.StatusCondition;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.species.PokemonType;
import dev.thanhtin.pokecraft.species.StatBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DamageCalculatorTest {

    static PokemonSpecies species(String id, PokemonType... types) {
        PokemonSpecies s = new PokemonSpecies();
        s.id = id;
        s.name = id;
        s.types = List.of(types);
        s.baseStats = new StatBlock(100, 100, 100, 100, 100, 100);
        return s;
    }

    static PokemonInstance instance(PokemonSpecies s, int level) {
        PokemonInstance p = new PokemonInstance();
        p.uuid = UUID.randomUUID();
        p.speciesId = s.id;
        p.level = level;
        p.ivs = new int[]{31, 31, 31, 31, 31, 31};
        p.nature = Nature.HARDY;
        p.moves = new java.util.ArrayList<>();
        p.currentHp = p.maxHp(s);
        return p;
    }

    static MoveData move(PokemonType type, MoveData.Category category, int power, int accuracy) {
        MoveData m = new MoveData();
        m.id = "m";
        m.name = "m";
        m.type = type;
        m.category = category;
        m.power = power;
        m.accuracy = accuracy;
        m.pp = 10;
        return m;
    }

    /** Damage bounds for the given multipliers: random 0.85-1.0, crit up to 1.5. */
    static void assertDamageInRange(DamageCalculator.Result r, PokemonInstance attacker,
                                    double atk, double def, int power, double modifier) {
        double base = ((2.0 * attacker.level / 5.0 + 2.0) * power * atk / Math.max(1.0, def)) / 50.0 + 2.0;
        int min = (int) Math.max(1, Math.floor(base * modifier * 0.85));
        int max = (int) Math.floor(base * modifier * 1.5); // crit ceiling
        assertTrue(r.damage() >= min && r.damage() <= max,
                "damage " + r.damage() + " outside [" + min + "," + max + "]");
    }

    @Test
    void stageMultiplierFollowsGenFormula() {
        assertEquals(1.0, DamageCalculator.stageMultiplier(0));
        assertEquals(1.5, DamageCalculator.stageMultiplier(1));
        assertEquals(2.0, DamageCalculator.stageMultiplier(2));
        assertEquals(4.0, DamageCalculator.stageMultiplier(6));
        assertEquals(2.0 / 3.0, DamageCalculator.stageMultiplier(-1), 1e-9);
        assertEquals(0.5, DamageCalculator.stageMultiplier(-2));
        assertEquals(0.25, DamageCalculator.stageMultiplier(-6));
        // clamped outside -6..6
        assertEquals(4.0, DamageCalculator.stageMultiplier(9));
        assertEquals(0.25, DamageCalculator.stageMultiplier(-9));
    }

    @Test
    void statusMovesDealNoDamage() {
        PokemonSpecies s = species("a", PokemonType.NORMAL);
        PokemonInstance atk = instance(s, 20);
        PokemonInstance def = instance(s, 20);
        MoveData growl = move(PokemonType.NORMAL, MoveData.Category.STATUS, 0, 100);
        DamageCalculator.Result r = DamageCalculator.calculate(atk, s, null, def, s, null, growl);
        assertFalse(r.missed());
        assertEquals(0, r.damage());
    }

    @Test
    void immunityDealsZero() {
        PokemonSpecies normal = species("norm", PokemonType.NORMAL);
        PokemonSpecies ghost = species("ghost", PokemonType.GHOST);
        PokemonInstance atk = instance(normal, 20);
        PokemonInstance def = instance(ghost, 20);
        MoveData tackle = move(PokemonType.NORMAL, MoveData.Category.PHYSICAL, 40, 100);
        DamageCalculator.Result r = DamageCalculator.calculate(atk, normal, null, def, ghost, null, tackle);
        assertEquals(0.0, r.effectiveness());
        assertEquals(0, r.damage());
    }

    @Test
    void effectivenessMultipliesForDualTypes() {
        PokemonSpecies fire = species("fire", PokemonType.FIRE);
        PokemonSpecies grassPoison = species("bulba", PokemonType.GRASS, PokemonType.POISON);
        PokemonInstance atk = instance(fire, 20);
        PokemonInstance def = instance(grassPoison, 20);
        MoveData ember = move(PokemonType.FIRE, MoveData.Category.SPECIAL, 40, 100);
        DamageCalculator.Result r = DamageCalculator.calculate(atk, fire, null, def, grassPoison, null, ember);
        assertEquals(2.0, r.effectiveness()); // 2.0 (grass) * 1.0 (poison)
    }

    @Test
    void alwaysHitsAtFullAccuracyAndDealsAtLeastOne() {
        PokemonSpecies s = species("a", PokemonType.NORMAL);
        PokemonInstance atk = instance(s, 5);
        PokemonInstance def = instance(s, 5);
        MoveData tackle = move(PokemonType.FIGHTING, MoveData.Category.PHYSICAL, 40, 100);
        for (int i = 0; i < 200; i++) {
            DamageCalculator.Result r = DamageCalculator.calculate(atk, s, null, def, s, null, tackle);
            assertFalse(r.missed());
            assertTrue(r.damage() >= 1);
        }
    }

    @Test
    void damageWithinExpectedBounds() {
        PokemonSpecies s = species("a", PokemonType.NORMAL);
        PokemonInstance atk = instance(s, 50);
        PokemonInstance def = instance(s, 50);
        MoveData slash = move(PokemonType.NORMAL, MoveData.Category.PHYSICAL, 70, 100);
        double stat = atk.stat(s, 1);
        for (int i = 0; i < 200; i++) {
            DamageCalculator.Result r = DamageCalculator.calculate(atk, s, null, def, s, null, slash);
            assertDamageInRange(r, atk, stat, def.stat(s, 2), 70, 1.5); // STAB
        }
    }

    @Test
    void statStagesScaleDamage() {
        PokemonSpecies s = species("a", PokemonType.NORMAL);
        PokemonInstance atk = instance(s, 50);
        PokemonInstance def = instance(s, 50);
        MoveData hit = move(PokemonType.FIGHTING, MoveData.Category.PHYSICAL, 70, 100);
        int[] plusTwo = {0, 2, 0, 0, 0, 0};
        int[] minusTwoDef = {0, 0, -2, 0, 0, 0};
        double atkStat = atk.stat(s, 1);
        double defStat = def.stat(s, 2);
        for (int i = 0; i < 200; i++) {
            DamageCalculator.Result boosted = DamageCalculator.calculate(atk, s, plusTwo, def, s, null, hit);
            assertDamageInRange(boosted, atk, atkStat * 2.0, defStat, 70, 2.0); // fighting vs normal SE
            DamageCalculator.Result weakDef = DamageCalculator.calculate(atk, s, null, def, s, minusTwoDef, hit);
            assertDamageInRange(weakDef, atk, atkStat, defStat * 0.5, 70, 2.0);
        }
    }

    @Test
    void burnHalvesPhysicalButNotSpecial() {
        PokemonSpecies s = species("a", PokemonType.NORMAL);
        PokemonInstance atk = instance(s, 50);
        PokemonInstance def = instance(s, 50);
        atk.status = StatusCondition.BURN;
        MoveData physical = move(PokemonType.FIGHTING, MoveData.Category.PHYSICAL, 70, 100);
        MoveData special = move(PokemonType.FIRE, MoveData.Category.SPECIAL, 70, 100);
        double atkStat = atk.stat(s, 1);
        double spaStat = atk.stat(s, 3);
        for (int i = 0; i < 200; i++) {
            DamageCalculator.Result phys = DamageCalculator.calculate(atk, s, null, def, s, null, physical);
            assertDamageInRange(phys, atk, atkStat * 0.5, def.stat(s, 2), 70, 2.0);
            DamageCalculator.Result spec = DamageCalculator.calculate(atk, s, null, def, s, null, special);
            assertDamageInRange(spec, atk, spaStat, def.stat(s, 4), 70, 1.0);
        }
    }

    @Test
    void zeroAccuracyAlwaysMisses() {
        PokemonSpecies s = species("a", PokemonType.NORMAL);
        PokemonInstance atk = instance(s, 20);
        PokemonInstance def = instance(s, 20);
        MoveData wild = move(PokemonType.NORMAL, MoveData.Category.PHYSICAL, 40, 0);
        for (int i = 0; i < 50; i++) {
            assertTrue(DamageCalculator.calculate(atk, s, null, def, s, null, wild).missed());
        }
    }
}
