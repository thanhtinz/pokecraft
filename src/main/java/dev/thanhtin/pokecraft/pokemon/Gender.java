package dev.thanhtin.pokecraft.pokemon;

import dev.thanhtin.pokecraft.species.PokemonSpecies;

import java.util.concurrent.ThreadLocalRandom;

/** A pokemon's gender. Genderless species (e.g. Magnemite, most legendaries). */
public enum Gender {
    MALE("♂"), FEMALE("♀"), GENDERLESS("");

    public final String symbol;

    Gender(String symbol) { this.symbol = symbol; }

    /** Roll a gender from a species' male ratio (-1 => genderless). */
    public static Gender roll(PokemonSpecies species) {
        double ratio = species.genderMaleRatio();
        if (ratio < 0) return GENDERLESS;
        return ThreadLocalRandom.current().nextDouble() < ratio ? MALE : FEMALE;
    }
}
