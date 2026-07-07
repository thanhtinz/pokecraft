package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.battle.MoveData;
import dev.thanhtin.pokecraft.pokemon.ExperienceCurve;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed summary screen for one party pokemon - the chest-GUI equivalent
 * of a Cobblemon/Pixelmon summary: info header, moves with PP and effect,
 * computed stats with IVs, evolution requirements.
 */
public class SummaryGui implements Listener {
    private static final int SLOT_HEADER = 4;
    private static final int[] MOVE_SLOTS = {10, 11, 12, 13};
    private static final int SLOT_STATS = 15;
    private static final int SLOT_EVOLVE = 16;
    private static final int SLOT_BACK = 22;

    private final PokeCraftPlugin plugin;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public SummaryGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int partySlot) {
        PokemonInstance p = plugin.parties().get(player).get(partySlot);
        if (p == null) {
            plugin.partyUi().open(player);
            return;
        }
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        if (species == null) return;

        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text("#" + String.format("%03d", species.dex) + " "
                        + p.displayName(species) + " Lv." + p.level));
        holder.inventory = inv;

        // ---- info header ----
        ItemStack head = new ItemStack(p.shiny ? Material.NETHER_STAR : Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        meta.displayName(Component.text((p.status != null ? "[" + p.status.tag + "] " : "")
                        + p.displayName(species) + " Lv." + p.level,
                p.shiny ? NamedTextColor.GOLD : NamedTextColor.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Type: " + species.types, NamedTextColor.GRAY));
        lore.add(Component.text("Nature: " + p.nature
                + (p.nature.up != p.nature.down
                        ? " (+" + statName(p.nature.up) + " -" + statName(p.nature.down) + ")"
                        : ""), NamedTextColor.GRAY));
        lore.add(Component.text("HP " + p.currentHp + "/" + p.maxHp(species),
                p.currentHp > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        long next = ExperienceCurve.expForLevel(Math.min(p.level + 1, ExperienceCurve.MAX_LEVEL));
        lore.add(Component.text("EXP " + p.exp + " / " + next + " for next level", NamedTextColor.GRAY));
        if (p.shiny) lore.add(Component.text("SHINY!", NamedTextColor.GOLD));
        meta.lore(lore);
        head.setItemMeta(meta);
        inv.setItem(SLOT_HEADER, head);

        // ---- moves ----
        for (int i = 0; i < MOVE_SLOTS.length; i++) {
            if (p.moves == null || i >= p.moves.size()) continue;
            MoveData move = plugin.species().getMove(p.moves.get(i));
            if (move == null) continue;
            int pp = p.ppFor(move);
            ItemStack item = new ItemStack(materialFor(move));
            ItemMeta moveMeta = item.getItemMeta();
            moveMeta.displayName(Component.text(move.name,
                    pp > 0 ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
            List<Component> moveLore = new ArrayList<>();
            moveLore.add(Component.text(move.type + " / " + move.category, NamedTextColor.GRAY));
            moveLore.add(Component.text("Power " + move.power + "  Accuracy " + move.accuracy
                    + (move.priority != 0 ? "  Priority " + move.priority : ""), NamedTextColor.GRAY));
            moveLore.add(Component.text("PP " + pp + "/" + move.pp,
                    pp > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (move.effect != null) {
                if (move.effect.status != null) {
                    moveLore.add(Component.text(move.effect.statusChance + "% chance to inflict "
                            + move.effect.status.tag, NamedTextColor.LIGHT_PURPLE));
                }
                if (move.effect.stat >= 1 && move.effect.stat <= 5 && move.effect.stages != 0) {
                    moveLore.add(Component.text((move.effect.target == MoveData.Target.SELF
                            ? "Raises own " : "Lowers foe's ") + statName(move.effect.stat)
                            + " by " + Math.abs(move.effect.stages), NamedTextColor.LIGHT_PURPLE));
                }
            }
            moveMeta.lore(moveLore);
            item.setItemMeta(moveMeta);
            inv.setItem(MOVE_SLOTS[i], item);
        }

        // ---- stats ----
        ItemStack stats = new ItemStack(Material.COMPARATOR);
        ItemMeta statsMeta = stats.getItemMeta();
        statsMeta.displayName(Component.text("Stats", NamedTextColor.YELLOW));
        List<Component> statsLore = new ArrayList<>();
        String[] names = {"HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed"};
        for (int i = 0; i < 6; i++) {
            statsLore.add(Component.text(names[i] + ": " + p.stat(species, i)
                    + "  (IV " + p.ivs[i] + "/31)", NamedTextColor.GRAY));
        }
        statsMeta.lore(statsLore);
        stats.setItemMeta(statsMeta);
        inv.setItem(SLOT_STATS, stats);

        // ---- evolution ----
        ItemStack evolve = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta evoMeta = evolve.getItemMeta();
        evoMeta.displayName(Component.text("Evolution", NamedTextColor.LIGHT_PURPLE));
        List<Component> evoLore = new ArrayList<>();
        List<PokemonSpecies.Evolution> evolutions = species.allEvolutions();
        if (evolutions.isEmpty()) {
            evoLore.add(Component.text("This pokemon does not evolve.", NamedTextColor.GRAY));
        }
        for (PokemonSpecies.Evolution evo : evolutions) {
            PokemonSpecies to = plugin.species().getSpecies(evo.to);
            if (to == null) continue;
            if (evo.item != null) {
                evoLore.add(Component.text("-> " + to.name + ": use a "
                        + evo.item.replace('_', ' ') + " (shop)", NamedTextColor.GRAY));
            } else {
                evoLore.add(Component.text("-> " + to.name + " at Lv." + evo.level
                        + (p.level >= evo.level ? " (next level-up!)" : ""), NamedTextColor.GRAY));
            }
        }
        evoMeta.lore(evoLore);
        evolve.setItemMeta(evoMeta);
        inv.setItem(SLOT_EVOLVE, evolve);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back to party", NamedTextColor.GRAY));
        back.setItemMeta(backMeta);
        inv.setItem(SLOT_BACK, back);

        player.openInventory(inv);
    }

    private String statName(int index) {
        return switch (index) {
            case 1 -> "Atk"; case 2 -> "Def"; case 3 -> "SpA";
            case 4 -> "SpD"; case 5 -> "Spe"; default -> "HP";
        };
    }

    private Material materialFor(MoveData move) {
        return switch (move.type) {
            case FIRE -> Material.BLAZE_POWDER;
            case WATER -> Material.HEART_OF_THE_SEA;
            case GRASS -> Material.OAK_SAPLING;
            case ELECTRIC -> Material.GLOWSTONE_DUST;
            case FLYING -> Material.FEATHER;
            case DARK -> Material.COAL;
            case POISON -> Material.SPIDER_EYE;
            case PSYCHIC -> Material.AMETHYST_SHARD;
            case ICE -> Material.SNOWBALL;
            case ROCK, GROUND -> Material.COBBLESTONE;
            case FIGHTING -> Material.IRON_SWORD;
            case GHOST -> Material.ENDER_EYE;
            case BUG -> Material.COBWEB;
            case DRAGON -> Material.DRAGON_BREATH;
            case STEEL -> Material.IRON_INGOT;
            case FAIRY -> Material.PINK_PETALS;
            default -> Material.SLIME_BALL;
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getRawSlot() == SLOT_BACK) {
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.partyUi().open(player));
        }
    }
}
