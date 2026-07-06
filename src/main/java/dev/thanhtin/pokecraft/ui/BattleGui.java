package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.battle.Battle;
import dev.thanhtin.pokecraft.battle.BattleManager;
import dev.thanhtin.pokecraft.battle.MoveData;
import dev.thanhtin.pokecraft.party.PlayerParty;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Chest-based battle menu. Works for Java and Bedrock players
 * (Geyser translates chest GUIs to Bedrock forms automatically).
 * When Floodgate is present, BedrockSupport can serve a native form instead.
 */
public class BattleGui implements Listener {
    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyMove;
    private final NamespacedKey keyAction;
    private final NamespacedKey keySlot;

    public BattleGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyMove = new NamespacedKey(plugin, "move");
        this.keyAction = new NamespacedKey(plugin, "action");
        this.keySlot = new NamespacedKey(plugin, "slot");
    }

    public void open(Player player, Battle battle) {
        if (plugin.bedrock().tryOpenBattleForm(player, battle)) return;

        PokemonSpecies mySpecies = plugin.species().getSpecies(battle.playerPokemon.speciesId);
        PokemonSpecies wildSpecies = plugin.species().getSpecies(battle.wildPokemon.speciesId);
        PokemonInstance mine = battle.playerPokemon;
        PokemonInstance wild = battle.wildPokemon;

        Inventory inv = plugin.getServer().createInventory(null, 9,
                Component.text(statusTag(mine) + mine.displayName(mySpecies) + " " + mine.currentHp + "/" + mine.maxHp(mySpecies)
                        + "  vs  " + statusTag(wild) + wild.displayName(wildSpecies) + " " + wild.currentHp + "/" + wild.maxHp(wildSpecies)));

        List<String> moves = mine.moves;
        boolean anyPp = false;
        for (int i = 0; i < Math.min(4, moves.size()); i++) {
            MoveData move = plugin.species().getMove(moves.get(i));
            if (move == null) continue;
            int pp = mine.ppFor(move);
            if (pp > 0) anyPp = true;
            ItemStack item = new ItemStack(pp > 0 ? materialFor(move) : Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(move.name, pp > 0 ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
            meta.lore(List.of(
                    Component.text(move.type + " / " + move.category, NamedTextColor.GRAY),
                    Component.text("Power " + move.power + "  Acc " + move.accuracy, NamedTextColor.GRAY),
                    Component.text("PP " + pp + "/" + move.pp, pp > 0 ? NamedTextColor.GRAY : NamedTextColor.RED)));
            if (pp > 0) {
                meta.getPersistentDataContainer().set(keyMove, PersistentDataType.STRING, move.id);
            }
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        if (!anyPp) {
            ItemStack struggle = new ItemStack(Material.IRON_SWORD);
            ItemMeta meta = struggle.getItemMeta();
            meta.displayName(Component.text("Struggle", NamedTextColor.RED));
            meta.lore(List.of(Component.text("No PP left on any move - hurts you too!", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(keyMove, PersistentDataType.STRING, BattleManager.STRUGGLE_ID);
            struggle.setItemMeta(meta);
            inv.setItem(4, struggle);
        }

        ItemStack party = new ItemStack(Material.ENDER_PEARL);
        ItemMeta partyMeta = party.getItemMeta();
        partyMeta.displayName(Component.text("Switch Pokemon", NamedTextColor.YELLOW));
        partyMeta.lore(List.of(Component.text("The wild pokemon gets a free hit", NamedTextColor.GRAY)));
        partyMeta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "switch-menu");
        party.setItemMeta(partyMeta);
        inv.setItem(7, party);

        ItemStack run = new ItemStack(Material.BARRIER);
        ItemMeta runMeta = run.getItemMeta();
        runMeta.displayName(Component.text("Run", NamedTextColor.RED));
        runMeta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "run");
        run.setItemMeta(runMeta);
        inv.setItem(8, run);

        player.openInventory(inv);
    }

    /** Party picker used for both voluntary switches and forced after-faint switches. */
    public void openSwitchMenu(Player player, Battle battle, boolean forced) {
        if (plugin.bedrock().tryOpenSwitchForm(player, battle, forced)) return;

        Inventory inv = plugin.getServer().createInventory(null, 9,
                Component.text(forced ? "Choose your next pokemon" : "Switch to which pokemon?"));
        PlayerParty partyData = plugin.parties().get(player);
        for (int i = 0; i < PlayerParty.SIZE; i++) {
            PokemonInstance p = partyData.get(i);
            if (p == null) continue;
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            boolean usable = p.currentHp > 0 && p != battle.playerPokemon;
            ItemStack item = new ItemStack(usable ? Material.PLAYER_HEAD : Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(statusTag(p) + p.displayName(species) + " Lv." + p.level,
                    usable ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
            meta.lore(List.of(Component.text("HP " + p.currentHp + "/" + p.maxHp(species),
                    p.currentHp > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)));
            if (usable) {
                meta.getPersistentDataContainer().set(keySlot, PersistentDataType.INTEGER, i);
            }
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        if (!forced) {
            ItemStack back = new ItemStack(Material.ARROW);
            ItemMeta meta = back.getItemMeta();
            meta.displayName(Component.text("Back", NamedTextColor.GRAY));
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "back");
            back.setItemMeta(meta);
            inv.setItem(8, back);
        }
        player.openInventory(inv);
    }

    private String statusTag(PokemonInstance p) {
        return p.status == null ? "" : "[" + p.status.tag + "] ";
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
            default -> Material.SLIME_BALL;
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        Battle battle = plugin.battles().get(player);
        if (battle == null) return;
        e.setCancelled(true);
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String moveId = item.getItemMeta().getPersistentDataContainer()
                .get(keyMove, PersistentDataType.STRING);
        String action = item.getItemMeta().getPersistentDataContainer()
                .get(keyAction, PersistentDataType.STRING);
        Integer slot = item.getItemMeta().getPersistentDataContainer()
                .get(keySlot, PersistentDataType.INTEGER);
        if (moveId == null && action == null && slot == null) return;

        final Integer targetSlot = slot;
        final String targetMove = moveId;
        // defer: opening/closing inventories inside a click handler is unsafe
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if ("run".equals(action)) {
                plugin.battles().flee(player);
            } else if ("switch-menu".equals(action)) {
                openSwitchMenu(player, battle, false);
            } else if ("back".equals(action)) {
                open(player, battle);
            } else if (targetSlot != null) {
                plugin.battles().switchPokemon(player, targetSlot);
            } else if (targetMove != null) {
                plugin.battles().useMove(player, targetMove);
            }
        });
    }

    /** A forced switch menu (after a faint) may not be escaped - reopen it. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        Battle battle = plugin.battles().get(player);
        if (battle == null || battle.finished || !battle.awaitingSwitch) return;
        plugin.getServer().getScheduler().runTask(plugin,
                () -> {
                    Battle current = plugin.battles().get(player);
                    if (current != null && current.awaitingSwitch) openSwitchMenu(player, current, true);
                });
    }
}
