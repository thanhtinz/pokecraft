package dev.thanhtin.pokecraft.species;

import java.util.EnumMap;
import java.util.Map;

public enum PokemonType {
    NORMAL, FIRE, WATER, ELECTRIC, GRASS, ICE, FIGHTING, POISON, GROUND,
    FLYING, PSYCHIC, BUG, ROCK, GHOST, DRAGON, DARK, STEEL, FAIRY;

    private static final Map<PokemonType, Map<PokemonType, Double>> CHART = new EnumMap<>(PokemonType.class);

    private static void set(PokemonType atk, double mult, PokemonType... defs) {
        Map<PokemonType, Double> row = CHART.computeIfAbsent(atk, k -> new EnumMap<>(PokemonType.class));
        for (PokemonType d : defs) row.put(d, mult);
    }

    static {
        set(NORMAL, 0.5, ROCK, STEEL); set(NORMAL, 0.0, GHOST);
        set(FIRE, 2.0, GRASS, ICE, BUG, STEEL); set(FIRE, 0.5, FIRE, WATER, ROCK, DRAGON);
        set(WATER, 2.0, FIRE, GROUND, ROCK); set(WATER, 0.5, WATER, GRASS, DRAGON);
        set(ELECTRIC, 2.0, WATER, FLYING); set(ELECTRIC, 0.5, ELECTRIC, GRASS, DRAGON); set(ELECTRIC, 0.0, GROUND);
        set(GRASS, 2.0, WATER, GROUND, ROCK); set(GRASS, 0.5, FIRE, GRASS, POISON, FLYING, BUG, DRAGON, STEEL);
        set(ICE, 2.0, GRASS, GROUND, FLYING, DRAGON); set(ICE, 0.5, FIRE, WATER, ICE, STEEL);
        set(FIGHTING, 2.0, NORMAL, ICE, ROCK, DARK, STEEL); set(FIGHTING, 0.5, POISON, FLYING, PSYCHIC, BUG, FAIRY); set(FIGHTING, 0.0, GHOST);
        set(POISON, 2.0, GRASS, FAIRY); set(POISON, 0.5, POISON, GROUND, ROCK, GHOST); set(POISON, 0.0, STEEL);
        set(GROUND, 2.0, FIRE, ELECTRIC, POISON, ROCK, STEEL); set(GROUND, 0.5, GRASS, BUG); set(GROUND, 0.0, FLYING);
        set(FLYING, 2.0, GRASS, FIGHTING, BUG); set(FLYING, 0.5, ELECTRIC, ROCK, STEEL);
        set(PSYCHIC, 2.0, FIGHTING, POISON); set(PSYCHIC, 0.5, PSYCHIC, STEEL); set(PSYCHIC, 0.0, DARK);
        set(BUG, 2.0, GRASS, PSYCHIC, DARK); set(BUG, 0.5, FIRE, FIGHTING, POISON, FLYING, GHOST, STEEL, FAIRY);
        set(ROCK, 2.0, FIRE, ICE, FLYING, BUG); set(ROCK, 0.5, FIGHTING, GROUND, STEEL);
        set(GHOST, 2.0, PSYCHIC, GHOST); set(GHOST, 0.5, DARK); set(GHOST, 0.0, NORMAL);
        set(DRAGON, 2.0, DRAGON); set(DRAGON, 0.5, STEEL); set(DRAGON, 0.0, FAIRY);
        set(DARK, 2.0, PSYCHIC, GHOST); set(DARK, 0.5, FIGHTING, DARK, FAIRY);
        set(STEEL, 2.0, ICE, ROCK, FAIRY); set(STEEL, 0.5, FIRE, WATER, ELECTRIC, STEEL);
        set(FAIRY, 2.0, FIGHTING, DRAGON, DARK); set(FAIRY, 0.5, FIRE, POISON, STEEL);
    }

    public double effectivenessAgainst(PokemonType defender) {
        Map<PokemonType, Double> row = CHART.get(this);
        if (row == null) return 1.0;
        return row.getOrDefault(defender, 1.0);
    }
}
