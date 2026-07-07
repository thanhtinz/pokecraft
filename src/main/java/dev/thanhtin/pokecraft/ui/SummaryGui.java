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
    private static final int SLOT_HELD = 19;
    private static final int SLOT_RENAME = 25;
    private static final int SLOT_RELEASE = 26;
    private static final int SLOT_BACK = 22;

    private final PokeCraftPlugin plugin;
    /** players who armed the release button (need a second click to confirm) */
    private final java.util.Set<java.util.UUID> releaseArmed =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static class Holder implements InventoryHolder {
        final int partySlot;
        Inventory inventory;
        Holder(int partySlot) { this.partySlot = partySlot; }
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

        Holder holder = new Holder(partySlot);
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

        // ---- held item ----
        var held = plugin.heldItems().byId(p.heldItem);
        ItemStack heldStack = new ItemStack(held != null ? held.material : Material.ITEM_FRAME);
        ItemMeta heldMeta = heldStack.getItemMeta();
        heldMeta.displayName(Component.text(held != null ? "Holding: " + held.display : "Held Item: none",
                held != null ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));
        heldMeta.lore(List.of(Component.text(held != null
                ? held.description : "Buy held items in the shop,", NamedTextColor.GRAY),
                Component.text(held != null
                ? "Click to take it back" : "then right-click one to equip", NamedTextColor.GRAY)));
        heldStack.setItemMeta(heldMeta);
        inv.setItem(SLOT_HELD, heldStack);

        ItemStack rename = new ItemStack(Material.NAME_TAG);
        ItemMeta renameMeta = rename.getItemMeta();
        renameMeta.displayName(Component.text("Set nickname", NamedTextColor.YELLOW));
        renameMeta.lore(List.of(Component.text("Type a name (PC & mobile)", NamedTextColor.GRAY)));
        rename.setItemMeta(renameMeta);
        inv.setItem(SLOT_RENAME, rename);

        boolean armed = releaseArmed.contains(player.getUniqueId());
        ItemStack release = new ItemStack(armed ? Material.LAVA_BUCKET : Material.BUCKET);
        ItemMeta releaseMeta = release.getItemMeta();
        releaseMeta.displayName(Component.text(armed ? "Click again to CONFIRM release"
                : "Release", armed ? NamedTextColor.RED : NamedTextColor.GRAY));
        releaseMeta.lore(List.of(Component.text(armed
                ? "This is permanent!" : "Let this pokemon go free", NamedTextColor.GRAY)));
        release.setItemMeta(releaseMeta);
        inv.setItem(SLOT_RELEASE, release);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back to party", NamedTextColor.GRAY));
        back.setItemMeta(backMeta);
        inv.setItem(SLOT_BACK, back);

        GuiFiller.fill(inv);
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
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        int raw = e.getRawSlot();
        java.util.UUID id = player.getUniqueId();
        boolean busy = plugin.battles().get(player) != null || plugin.pvp().get(player) != null
                || plugin.trades().get(player) != null;

        if (raw == SLOT_BACK) {
            releaseArmed.remove(id);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.partyUi().open(player));
            return;
        }
        if (raw == SLOT_HELD) {
            if (busy) return;
            releaseArmed.remove(id);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                PokemonInstance target = plugin.parties().get(player).get(holder.partySlot);
                if (target != null && target.heldItem != null) {
                    plugin.heldItems().unequip(player, target);
                }
                open(player, holder.partySlot);
            });
            return;
        }
        if (raw == SLOT_RENAME) {
            if (busy) return;
            releaseArmed.remove(id);
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.nicknameInput().open(player, holder.partySlot));
            return;
        }
        if (raw == SLOT_RELEASE) {
            if (busy) return;
            if (!releaseArmed.contains(id)) {
                releaseArmed.add(id);
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, holder.partySlot));
                return;
            }
            releaseArmed.remove(id);
            plugin.getServer().getScheduler().runTask(plugin, () -> doRelease(player, holder.partySlot));
        }
    }

    private void doRelease(Player player, int slot) {
        var party = plugin.parties().get(player);
        PokemonInstance p = party.get(slot);
        if (p == null) { plugin.partyUi().open(player); return; }
        if (party.partySize() <= 1) {
            player.sendMessage(Component.text("You can't release your last party pokemon.",
                    NamedTextColor.RED));
            open(player, slot);
            return;
        }
        PokemonInstance removed = party.removeFromParty(slot);
        if (removed != null) {
            plugin.storage().delete(removed.uuid);
            plugin.parties().saveParty(player.getUniqueId());
            PokemonSpecies species = plugin.species().getSpecies(removed.speciesId);
            player.sendMessage(Component.text("Bye bye, "
                    + (species != null ? removed.displayName(species) : removed.speciesId) + "!",
                    NamedTextColor.GREEN));
        }
        plugin.partyUi().open(player);
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        if (!(e.getPlayer() instanceof Player player)) return;
        // a reopen (arm -> refresh) also fires close; only disarm when the
        // summary truly stays closed
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)) {
                releaseArmed.remove(player.getUniqueId());
            }
        });
    }
}
