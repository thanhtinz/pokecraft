package dev.thanhtin.pokecraft.battle;

import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import org.bukkit.entity.Entity;

import java.util.Arrays;
import java.util.UUID;

/** A single 1v1 wild battle session. */
public class Battle {
    public final UUID playerId;
    public PokemonInstance playerPokemon;
    public final PokemonInstance wildPokemon;
    public final Entity wildEntity;
    public boolean finished;
    /** Set after the active pokemon faints: player must pick a replacement. */
    public boolean awaitingSwitch;
    /** Per-battle stat stages, indexed like PokemonInstance#stat (1-5). */
    public final int[] playerStages = new int[6];
    public final int[] wildStages = new int[6];

    public Battle(UUID playerId, PokemonInstance playerPokemon,
                  PokemonInstance wildPokemon, Entity wildEntity) {
        this.playerId = playerId;
        this.playerPokemon = playerPokemon;
        this.wildPokemon = wildPokemon;
        this.wildEntity = wildEntity;
    }

    /** Stages reset when the player's pokemon leaves the field. */
    public void resetPlayerStages() {
        Arrays.fill(playerStages, 0);
    }
}
