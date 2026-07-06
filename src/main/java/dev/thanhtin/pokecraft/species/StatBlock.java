package dev.thanhtin.pokecraft.species;

public class StatBlock {
    public int hp, atk, def, spa, spd, spe;

    public StatBlock() {}

    public StatBlock(int hp, int atk, int def, int spa, int spd, int spe) {
        this.hp = hp; this.atk = atk; this.def = def;
        this.spa = spa; this.spd = spd; this.spe = spe;
    }
}
