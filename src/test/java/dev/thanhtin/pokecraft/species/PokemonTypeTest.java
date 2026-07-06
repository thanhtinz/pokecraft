package dev.thanhtin.pokecraft.species;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PokemonTypeTest {

    @Test
    void superEffective() {
        assertEquals(2.0, PokemonType.FIRE.effectivenessAgainst(PokemonType.GRASS));
        assertEquals(2.0, PokemonType.WATER.effectivenessAgainst(PokemonType.FIRE));
        assertEquals(2.0, PokemonType.ELECTRIC.effectivenessAgainst(PokemonType.WATER));
        assertEquals(2.0, PokemonType.FAIRY.effectivenessAgainst(PokemonType.DRAGON));
    }

    @Test
    void notVeryEffective() {
        assertEquals(0.5, PokemonType.FIRE.effectivenessAgainst(PokemonType.WATER));
        assertEquals(0.5, PokemonType.NORMAL.effectivenessAgainst(PokemonType.ROCK));
        assertEquals(0.5, PokemonType.GRASS.effectivenessAgainst(PokemonType.FLYING));
    }

    @Test
    void immunities() {
        assertEquals(0.0, PokemonType.NORMAL.effectivenessAgainst(PokemonType.GHOST));
        assertEquals(0.0, PokemonType.ELECTRIC.effectivenessAgainst(PokemonType.GROUND));
        assertEquals(0.0, PokemonType.GROUND.effectivenessAgainst(PokemonType.FLYING));
        assertEquals(0.0, PokemonType.GHOST.effectivenessAgainst(PokemonType.NORMAL));
        assertEquals(0.0, PokemonType.PSYCHIC.effectivenessAgainst(PokemonType.DARK));
        assertEquals(0.0, PokemonType.POISON.effectivenessAgainst(PokemonType.STEEL));
        assertEquals(0.0, PokemonType.DRAGON.effectivenessAgainst(PokemonType.FAIRY));
        assertEquals(0.0, PokemonType.FIGHTING.effectivenessAgainst(PokemonType.GHOST));
    }

    @Test
    void neutralByDefault() {
        assertEquals(1.0, PokemonType.NORMAL.effectivenessAgainst(PokemonType.NORMAL));
        assertEquals(1.0, PokemonType.FIRE.effectivenessAgainst(PokemonType.ELECTRIC));
    }
}
