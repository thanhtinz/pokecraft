package dev.thanhtin.pokecraft.battle.pvp;

import dev.thanhtin.pokecraft.pokemon.PokemonInstance;

import java.util.Arrays;
import java.util.UUID;

/** A turn-based 1v1 duel between two players. */
public class PvpBattle {
    public final UUID p1, p2;
    public PokemonInstance active1, active2;
    public final int[] stages1 = new int[6];
    public final int[] stages2 = new int[6];
    /** Pending action for the turn: "move:<id>" or "switch:<slot>"; null = not chosen. */
    public String choice1, choice2;
    /** Set when that side's active fainted and must pick a replacement. */
    public boolean awaitingSwitch1, awaitingSwitch2;
    public boolean finished;

    public PvpBattle(UUID p1, PokemonInstance active1, UUID p2, PokemonInstance active2) {
        this.p1 = p1;
        this.active1 = active1;
        this.p2 = p2;
        this.active2 = active2;
    }

    public boolean isSideOne(UUID player) { return p1.equals(player); }

    public UUID opponentOf(UUID player) { return isSideOne(player) ? p2 : p1; }

    public PokemonInstance activeOf(UUID player) { return isSideOne(player) ? active1 : active2; }

    public int[] stagesOf(UUID player) { return isSideOne(player) ? stages1 : stages2; }

    public String choiceOf(UUID player) { return isSideOne(player) ? choice1 : choice2; }

    public void setChoice(UUID player, String choice) {
        if (isSideOne(player)) choice1 = choice;
        else choice2 = choice;
    }

    public boolean awaitingSwitch(UUID player) { return isSideOne(player) ? awaitingSwitch1 : awaitingSwitch2; }

    public void setAwaitingSwitch(UUID player, boolean value) {
        if (isSideOne(player)) awaitingSwitch1 = value;
        else awaitingSwitch2 = value;
    }

    public void setActive(UUID player, PokemonInstance pokemon) {
        if (isSideOne(player)) {
            active1 = pokemon;
            Arrays.fill(stages1, 0);
        } else {
            active2 = pokemon;
            Arrays.fill(stages2, 0);
        }
    }

    public boolean bothChosen() { return choice1 != null && choice2 != null; }

    public boolean anyAwaitingSwitch() { return awaitingSwitch1 || awaitingSwitch2; }

    public void clearChoices() { choice1 = null; choice2 = null; }
}
