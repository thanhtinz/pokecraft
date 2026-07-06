package dev.thanhtin.pokecraft.pokemon;

import dev.thanhtin.pokecraft.battle.MoveData;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.species.StatBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PokemonInstance {
    public UUID uuid;
    public String speciesId;
    public String nickname;
    public int level;
    public long exp;
    public int currentHp;
    public int[] ivs;          // hp atk def spa spd spe (0-31)
    public Nature nature;
    public boolean shiny;
    public List<String> moves; // up to 4 move ids
    public UUID owner;         // null = wild
    /** Remaining PP per move id. Missing entry = full PP. */
    public Map<String, Integer> pp;
    public StatusCondition status;
    /** Turns of sleep remaining while {@link #status} == SLEEP. */
    public int sleepTurns;

    public static PokemonInstance generate(PokemonSpecies species, int level, int shinyRate) {
        PokemonInstance p = new PokemonInstance();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        p.uuid = UUID.randomUUID();
        p.speciesId = species.id;
        p.level = Math.max(1, level);
        p.exp = ExperienceCurve.expForLevel(p.level);
        p.ivs = new int[6];
        for (int i = 0; i < 6; i++) p.ivs[i] = rnd.nextInt(32);
        p.nature = Nature.random();
        p.shiny = shinyRate > 0 && rnd.nextInt(shinyRate) == 0;
        p.moves = latestMoves(species, p.level);
        p.currentHp = p.maxHp(species);
        return p;
    }

    /** Last 4 moves learnable at the given level. */
    public static List<String> latestMoves(PokemonSpecies species, int level) {
        List<String> result = new ArrayList<>();
        if (species.learnset == null) return result;
        for (int l = 1; l <= level; l++) {
            List<String> learned = species.learnset.get(String.valueOf(l));
            if (learned == null) continue;
            for (String m : learned) {
                result.remove(m);
                result.add(m);
                while (result.size() > 4) result.remove(0);
            }
        }
        return result;
    }

    public int stat(PokemonSpecies species, int index) {
        StatBlock b = species.baseStats;
        int base = switch (index) {
            case 0 -> b.hp; case 1 -> b.atk; case 2 -> b.def;
            case 3 -> b.spa; case 4 -> b.spd; default -> b.spe;
        };
        if (index == 0) {
            return ((2 * base + ivs[0]) * level) / 100 + level + 10;
        }
        int raw = ((2 * base + ivs[index]) * level) / 100 + 5;
        return (int) Math.floor(raw * nature.multiplier(index));
    }

    public int maxHp(PokemonSpecies species) { return stat(species, 0); }

    public String displayName(PokemonSpecies species) {
        String base = nickname != null && !nickname.isEmpty() ? nickname : species.name;
        return shiny ? base + " (Shiny)" : base;
    }

    public int ppFor(MoveData move) {
        if (move == null) return 0;
        if (pp == null) pp = new HashMap<>();
        return pp.getOrDefault(move.id, move.pp);
    }

    public void usePp(MoveData move) {
        if (move == null) return;
        if (pp == null) pp = new HashMap<>();
        pp.put(move.id, Math.max(0, ppFor(move) - 1));
    }

    /** Full restore: HP, PP and status. */
    public void heal(PokemonSpecies species) {
        currentHp = maxHp(species);
        status = null;
        sleepTurns = 0;
        if (pp != null) pp.clear();
    }

    /** @return levels gained */
    public int addExp(PokemonSpecies species, long amount) {
        int before = level;
        exp += amount;
        int after = ExperienceCurve.levelForExp(exp);
        if (after > before) {
            int oldMax = maxHp(species);
            level = after;
            int newMax = maxHp(species);
            currentHp = Math.min(newMax, currentHp + (newMax - oldMax));
            moves = latestMoves(species, level);
        }
        return after - before;
    }
}
