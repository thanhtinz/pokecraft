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
    public SpawnInfo spawn;

    public static class Evolution {
        public int level;
        public String to;
    }

    public static class SpawnInfo {
        public List<String> biomes;
        public int weight = 10;
        public int minLevel = 2;
        public int maxLevel = 8;
    }
}
