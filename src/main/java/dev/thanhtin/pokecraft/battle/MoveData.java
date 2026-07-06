package dev.thanhtin.pokecraft.battle;

import dev.thanhtin.pokecraft.species.PokemonType;

public class MoveData {
    public transient String id;
    public String name;
    public PokemonType type;
    public Category category;
    public int power;
    public int accuracy;
    public int pp;

    public enum Category { PHYSICAL, SPECIAL, STATUS }
}
