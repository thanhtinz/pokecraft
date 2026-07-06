package dev.thanhtin.pokecraft.party;

import dev.thanhtin.pokecraft.pokemon.PokemonInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlayerParty {
    public static final int SIZE = 6;
    private final PokemonInstance[] slots = new PokemonInstance[SIZE];
    private final List<PokemonInstance> pcBox = new ArrayList<>();

    public PokemonInstance get(int slot) {
        return slot >= 0 && slot < SIZE ? slots[slot] : null;
    }

    public void set(int slot, PokemonInstance p) {
        if (slot >= 0 && slot < SIZE) slots[slot] = p;
    }

    /** @return slot added to, or -1 if sent to PC */
    public int add(PokemonInstance p) {
        for (int i = 0; i < SIZE; i++) {
            if (slots[i] == null) { slots[i] = p; return i; }
        }
        pcBox.add(p);
        return -1;
    }

    public PokemonInstance firstAlive() {
        for (PokemonInstance p : slots) {
            if (p != null && p.currentHp > 0) return p;
        }
        return null;
    }

    public List<PokemonInstance> party() {
        List<PokemonInstance> out = new ArrayList<>();
        for (PokemonInstance p : slots) if (p != null) out.add(p);
        return out;
    }

    public PokemonInstance[] rawSlots() { return Arrays.copyOf(slots, SIZE); }
    public List<PokemonInstance> pc() { return pcBox; }
    public boolean isEmpty() { return party().isEmpty() && pcBox.isEmpty(); }
}
