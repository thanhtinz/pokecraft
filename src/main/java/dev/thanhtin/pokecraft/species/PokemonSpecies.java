package dev.thanhtin.pokecraft.species;

import java.util.List;
import java.util.Map;

public class PokemonSpecies {
    public String id;
    public int dex;
    public String name;
    public List<PokemonType> types;
    public StatBlock baseStats;
    public int catchRate;
    public int expYield;
    public String modelId;
    /** level -> move ids learned at that level */
    public Map<String, List<String>> learnset;
    public Evolution evolution;
    /** Multiple possible evolutions (e.g. Eevee); used alongside {@link #evolution}. */
    public List<Evolution> evolutions;
    public SpawnInfo spawn;

    /** Effort values a defeated pokemon of this species awards (hp/atk/def/spa/spd/spe). */
    public Map<String, Integer> evYield;
    /** Fraction male (0..1); -1 means genderless. Defaults to 0.5 when absent. */
    public Double maleRatio;
    /** Ordinary abilities (ids); one is chosen when the pokemon is generated. */
    public List<String> abilities;
    /** Hidden ability id, or null. */
    public String hiddenAbility;

    /** EV yield for a stat index (0=hp..5=spe), 0 when unset. */
    public int evYieldFor(int index) {
        if (evYield == null) return 0;
        String key = switch (index) {
            case 0 -> "hp"; case 1 -> "atk"; case 2 -> "def";
            case 3 -> "spa"; case 4 -> "spd"; default -> "spe";
        };
        return evYield.getOrDefault(key, 0);
    }

    public double genderMaleRatio() {
        return maleRatio == null ? 0.5 : maleRatio;
    }

    /** All evolution entries: {@link #evolution} plus {@link #evolutions}. */
    public List<Evolution> allEvolutions() {
        List<Evolution> out = new java.util.ArrayList<>();
        if (evolution != null) out.add(evolution);
        if (evolutions != null) out.addAll(evolutions);
        return out;
    }

    public static class Evolution {
        /** Level-based evolution threshold (ignored when {@link #item} is set). */
        public int level;
        public String to;
        /** Usable item id (e.g. "thunder_stone") that triggers this evolution. */
        public String item;
    }

    public static class SpawnInfo {
        public List<String> biomes;
        public int weight = 10;
        public int minLevel = 2;
        public int maxLevel = 8;
    }
}
