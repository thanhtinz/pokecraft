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
    public int[] evs;          // hp atk def spa spd spe (0-252, sum <= 510)
    public Nature nature;
    public boolean shiny;
    public Gender gender;
    /** Ability id (from the species' ability list, occasionally the hidden one). */
    public String ability;
    public List<String> moves; // up to 4 move ids
    public UUID owner;         // null = wild
    /** Remaining PP per move id. Missing entry = full PP. */
    public Map<String, Integer> pp;
    public StatusCondition status;
    /** Turns of sleep remaining while {@link #status} == SLEEP. */
    public int sleepTurns;
    /** Held item id (see item.HeldItems), or null. */
    public String heldItem;

    public boolean holds(String itemId) {
        return itemId.equals(heldItem);
    }

    public static PokemonInstance generate(PokemonSpecies species, int level, int shinyRate) {
        PokemonInstance p = new PokemonInstance();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        p.uuid = UUID.randomUUID();
        p.speciesId = species.id;
        p.level = Math.max(1, level);
        p.exp = ExperienceCurve.expForLevel(p.level);
        p.ivs = new int[6];
        for (int i = 0; i < 6; i++) p.ivs[i] = rnd.nextInt(32);
        p.evs = new int[6];
        p.nature = Nature.random();
        p.shiny = shinyRate > 0 && rnd.nextInt(shinyRate) == 0;
        p.gender = Gender.roll(species);
        p.ability = pickAbility(species, rnd);
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
        int ev = (evs != null && index < evs.length) ? evs[index] : 0;
        if (index == 0) {
            return ((2 * base + ivs[0] + ev / 4) * level) / 100 + level + 10;
        }
        int raw = ((2 * base + ivs[index] + ev / 4) * level) / 100 + 5;
        return (int) Math.floor(raw * nature.multiplier(index));
    }

    /** Cap for a single EV stat and for the total across all stats. */
    public static final int EV_STAT_CAP = 252;
    public static final int EV_TOTAL_CAP = 510;

    /**
     * Add EVs to a single stat (0=hp..5=spe), respecting the per-stat and total
     * caps. @return how many EVs were actually added (0 if already capped).
     */
    public int addEv(int index, int amount) {
        if (index < 0 || index > 5 || amount <= 0) return 0;
        if (evs == null) evs = new int[6];
        int total = 0;
        for (int e : evs) total += e;
        int room = Math.min(EV_STAT_CAP - evs[index], EV_TOTAL_CAP - total);
        int add = Math.max(0, Math.min(amount, room));
        evs[index] += add;
        return add;
    }

    /**
     * Award the EV yield of a defeated species, respecting the per-stat and
     * total caps. @return true if any EV was actually gained.
     */
    public boolean gainEvs(PokemonSpecies defeated) {
        if (defeated == null) return false;
        if (evs == null) evs = new int[6];
        int total = 0;
        for (int e : evs) total += e;
        boolean changed = false;
        for (int i = 0; i < 6; i++) {
            int yield = defeated.evYieldFor(i);
            if (yield <= 0 || total >= EV_TOTAL_CAP) continue;
            int room = Math.min(EV_STAT_CAP - evs[i], EV_TOTAL_CAP - total);
            int add = Math.min(yield, room);
            if (add > 0) { evs[i] += add; total += add; changed = true; }
        }
        return changed;
    }

    public int maxHp(PokemonSpecies species) { return stat(species, 0); }

    /** Gender, lazily assigned for pokemon created before genders existed. */
    public Gender gender(PokemonSpecies species) {
        if (gender == null) gender = Gender.roll(species);
        return gender;
    }

    /** Pick an ability for a fresh pokemon: 1/20 hidden, else a random normal one. */
    private static String pickAbility(PokemonSpecies species, ThreadLocalRandom rnd) {
        if (species.hiddenAbility != null && !species.hiddenAbility.isBlank() && rnd.nextInt(20) == 0) {
            return species.hiddenAbility;
        }
        if (species.abilities != null && !species.abilities.isEmpty()) {
            return species.abilities.get(rnd.nextInt(species.abilities.size()));
        }
        return species.hiddenAbility;
    }

    /** Ability, lazily assigned for pokemon created before abilities existed. */
    public String ability(PokemonSpecies species) {
        if ((ability == null || ability.isBlank()) && species != null) {
            ability = pickAbility(species, ThreadLocalRandom.current());
        }
        return ability == null ? "" : ability;
    }

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
