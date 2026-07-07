package dev.thanhtin.pokecraft.pokemon;

import dev.thanhtin.pokecraft.battle.MoveData;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.species.PokemonType;
import dev.thanhtin.pokecraft.species.StatBlock;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PokemonInstanceTest {

    static PokemonSpecies species(int base) {
        PokemonSpecies s = new PokemonSpecies();
        s.id = "test";
        s.name = "Test";
        s.types = List.of(PokemonType.NORMAL);
        s.baseStats = new StatBlock(base, base, base, base, base, base);
        s.expYield = 64;
        s.learnset = new HashMap<>();
        return s;
    }

    static PokemonInstance instance(PokemonSpecies s, int level, int iv, Nature nature) {
        PokemonInstance p = new PokemonInstance();
        p.uuid = UUID.randomUUID();
        p.speciesId = s.id;
        p.level = level;
        p.exp = ExperienceCurve.expForLevel(level);
        p.ivs = new int[]{iv, iv, iv, iv, iv, iv};
        p.nature = nature;
        p.moves = new java.util.ArrayList<>();
        p.currentHp = p.maxHp(s);
        return p;
    }

    @Test
    void statFormulaMatchesGen3() {
        PokemonSpecies s = species(100);
        PokemonInstance p = instance(s, 50, 31, Nature.HARDY);
        // HP: ((2*100+31)*50)/100 + 50 + 10 = 175
        assertEquals(175, p.stat(s, 0));
        // Other stats: ((2*100+31)*50)/100 + 5 = 120 (neutral nature)
        for (int i = 1; i <= 5; i++) assertEquals(120, p.stat(s, i));
    }

    @Test
    void natureScalesStats() {
        PokemonSpecies s = species(100);
        PokemonInstance p = instance(s, 50, 31, Nature.ADAMANT); // +atk -spa
        assertEquals((int) Math.floor(120 * 1.1), p.stat(s, 1));
        assertEquals((int) Math.floor(120 * 0.9), p.stat(s, 3));
        assertEquals(120, p.stat(s, 5));
    }

    @Test
    void latestMovesKeepsFourNewest() {
        PokemonSpecies s = species(50);
        s.learnset = Map.of(
                "1", List.of("a", "b"),
                "3", List.of("c"),
                "5", List.of("d"),
                "7", List.of("e"));
        assertEquals(List.of("a", "b", "c", "d"), PokemonInstance.latestMoves(s, 5));
        assertEquals(List.of("b", "c", "d", "e"), PokemonInstance.latestMoves(s, 7));
        // relearning an old move moves it to the back instead of duplicating
        s = species(50);
        s.learnset = Map.of("1", List.of("a", "b"), "4", List.of("a"));
        assertEquals(List.of("b", "a"), PokemonInstance.latestMoves(s, 4));
    }

    @Test
    void addExpLevelsUpAndAddsHpDelta() {
        PokemonSpecies s = species(100);
        PokemonInstance p = instance(s, 10, 31, Nature.HARDY);
        p.currentHp = 5; // damaged
        int before = p.maxHp(s);
        int levels = p.addExp(s, ExperienceCurve.expForLevel(12) - p.exp);
        assertEquals(2, levels);
        assertEquals(12, p.level);
        int gained = p.maxHp(s) - before;
        assertEquals(5 + gained, p.currentHp);
    }

    @Test
    void ppTracksPerMove() {
        MoveData move = new MoveData();
        move.id = "tackle";
        move.pp = 3;
        PokemonInstance p = instance(species(50), 5, 0, Nature.HARDY);
        assertEquals(3, p.ppFor(move));
        p.usePp(move);
        p.usePp(move);
        assertEquals(1, p.ppFor(move));
        p.usePp(move);
        p.usePp(move); // never below zero
        assertEquals(0, p.ppFor(move));
    }

    @Test
    void evsRaiseStatsAndRespectCaps() {
        PokemonSpecies s = species(100);
        PokemonInstance p = instance(s, 50, 31, Nature.HARDY);
        int before = p.stat(s, 1);
        p.evs = new int[]{0, 252, 0, 0, 0, 0};
        // 252 EV folds into the Gen-3 formula: ((2*100+31+63)*50)/100+5 = 152
        assertEquals(152, p.stat(s, 1));
        assertTrue(p.stat(s, 1) > before);

        // a defeated species with an EV yield adds toward the caps
        PokemonSpecies foe = species(50);
        foe.evYield = Map.of("atk", 2, "spe", 1);
        PokemonInstance q = instance(s, 50, 31, Nature.HARDY);
        q.evs = new int[6];
        assertTrue(q.gainEvs(foe));
        assertEquals(2, q.evs[1]);
        assertEquals(1, q.evs[5]);

        // per-stat cap of 252 is never exceeded
        q.evs = new int[]{0, 251, 0, 0, 0, 0};
        q.gainEvs(foe);
        assertEquals(PokemonInstance.EV_STAT_CAP, q.evs[1]);
    }

    @Test
    void addEvRespectsCaps() {
        PokemonInstance p = instance(species(100), 50, 31, Nature.HARDY);
        p.evs = new int[6];
        assertEquals(10, p.addEv(1, 10));        // normal add
        assertEquals(10, p.evs[1]);
        p.evs[1] = 250;
        assertEquals(2, p.addEv(1, 10));         // per-stat cap 252
        assertEquals(252, p.evs[1]);
        // total cap 510
        p.evs = new int[]{0, 252, 250, 0, 0, 0}; // 502 total
        assertEquals(8, p.addEv(3, 10));
        assertEquals(0, p.addEv(4, 10));         // already at 510
    }

    @Test
    void genderRollsFromRatio() {
        PokemonSpecies male = species(50);
        male.maleRatio = 1.0;
        assertEquals(Gender.MALE, Gender.roll(male));
        PokemonSpecies female = species(50);
        female.maleRatio = 0.0;
        assertEquals(Gender.FEMALE, Gender.roll(female));
        PokemonSpecies none = species(50);
        none.maleRatio = -1.0;
        assertEquals(Gender.GENDERLESS, Gender.roll(none));
    }

    @Test
    void healRestoresEverything() {
        PokemonSpecies s = species(100);
        PokemonInstance p = instance(s, 20, 31, Nature.HARDY);
        MoveData move = new MoveData();
        move.id = "tackle";
        move.pp = 35;
        p.usePp(move);
        p.currentHp = 1;
        p.status = StatusCondition.BURN;
        p.sleepTurns = 2;
        p.heal(s);
        assertEquals(p.maxHp(s), p.currentHp);
        assertNull(p.status);
        assertEquals(0, p.sleepTurns);
        assertEquals(35, p.ppFor(move));
    }
}
