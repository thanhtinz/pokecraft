package dev.thanhtin.pokecraft.battle;

import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import org.bukkit.entity.Entity;

import java.util.Arrays;
import java.util.UUID;

/** A single 1v1 battle session against a wild pokemon or an NPC trainer. */
public class Battle {
    public final UUID playerId;
    public PokemonInstance playerPokemon;
    /** The opposing pokemon currently on the field. */
    public PokemonInstance wildPokemon;
    /** Wild pokemon entity, or the NPC trainer entity for trainer battles. */
    public final Entity wildEntity;
    /** Non-null for NPC trainer battles: the trainer's remaining team. */
    public java.util.List<PokemonInstance> npcTeam;
    public int npcIndex;
    public String npcName;
    public long npcReward;
    /** Non-null on gym leaders: the badge id awarded on victory. */
    public String npcBadge;
    public boolean finished;
    /** Set after the active pokemon faints: player must pick a replacement. */
    public boolean awaitingSwitch;
    /** Per-battle stat stages, indexed like PokemonInstance#stat (1-5). */
    public final int[] playerStages = new int[6];
    public final int[] wildStages = new int[6];
    /** Accuracy/evasion stages (-6..+6), kept separate from the stat array. */
    public int playerAcc, playerEva, wildAcc, wildEva;

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
        playerAcc = 0;
        playerEva = 0;
    }

    public boolean isTrainerBattle() {
        return npcTeam != null;
    }

    /** Opposing side reset when the NPC sends out its next pokemon. */
    public void resetWildStages() {
        Arrays.fill(wildStages, 0);
        wildAcc = 0;
        wildEva = 0;
    }
}
