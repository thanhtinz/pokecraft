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

    public void swap(int a, int b) {
        if (a < 0 || a >= SIZE || b < 0 || b >= SIZE) return;
        PokemonInstance tmp = slots[a];
        slots[a] = slots[b];
        slots[b] = tmp;
    }

    /** Move a party member to the PC box. @return the moved pokemon, or null */
    public PokemonInstance depositToPc(int slot) {
        PokemonInstance p = get(slot);
        if (p == null) return null;
        slots[slot] = null;
        pcBox.add(p);
        return p;
    }

    /** Move a PC pokemon to the first free party slot. @return the party slot, or -1 if full */
    public int withdrawFromPc(int pcIndex) {
        if (pcIndex < 0 || pcIndex >= pcBox.size()) return -1;
        for (int i = 0; i < SIZE; i++) {
            if (slots[i] == null) {
                slots[i] = pcBox.remove(pcIndex);
                return i;
            }
        }
        return -1;
    }

    public PokemonInstance removeFromParty(int slot) {
        PokemonInstance p = get(slot);
        if (p != null) slots[slot] = null;
        return p;
    }

    public PokemonInstance removeFromPc(int pcIndex) {
        if (pcIndex < 0 || pcIndex >= pcBox.size()) return null;
        return pcBox.remove(pcIndex);
    }

    /** Number of pokemon currently in the party. */
    public int partySize() {
        int n = 0;
        for (PokemonInstance p : slots) if (p != null) n++;
        return n;
    }
}
