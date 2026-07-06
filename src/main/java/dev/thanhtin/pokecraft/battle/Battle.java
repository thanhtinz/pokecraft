package dev.thanhtin.pokecraft.battle;

import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import org.bukkit.entity.Entity;

import java.util.UUID;

/** A single 1v1 wild battle session. */
public class Battle {
    public final UUID playerId;
    public final PokemonInstance playerPokemon;
    public final PokemonInstance wildPokemon;
    public final Entity wildEntity;
    public boolean finished;

    public Battle(UUID playerId, PokemonInstance playerPokemon,
                  PokemonInstance wildPokemon, Entity wildEntity) {
        this.playerId = playerId;
        this.playerPokemon = playerPokemon;
        this.wildPokemon = wildPokemon;
        this.wildEntity = wildEntity;
    }
}
