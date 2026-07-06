package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.battle.BattleManager;
import dev.thanhtin.pokecraft.battle.MoveData;
import dev.thanhtin.pokecraft.battle.pvp.PvpBattle;
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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Chest menus for PvP duels (Geyser translates them for Bedrock players). */
public class PvpGui implements Listener {
    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyMove;
    private final NamespacedKey keyAction;
    private final NamespacedKey keySlot;

    private static class Holder implements InventoryHolder {
        final boolean switchMenu;
        Inventory inventory;
        Holder(boolean switchMenu) { this.switchMenu = switchMenu; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public PvpGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyMove = new NamespacedKey(plugin, "pvp_move");
        this.keyAction = new NamespacedKey(plugin, "pvp_action");
        this.keySlot = new NamespacedKey(plugin, "pvp_slot");
    }

    public void openMoveMenu(Player player, PvpBattle battle) {
        PokemonInstance mine = battle.activeOf(player.getUniqueId());
        PokemonInstance theirs = battle.activeOf(battle.opponentOf(player.getUniqueId()));
        PokemonSpecies mySpecies = plugin.species().getSpecies(mine.speciesId);
        PokemonSpecies theirSpecies = plugin.species().getSpecies(theirs.speciesId);

        Holder holder = new Holder(false);
        Inventory inv = plugin.getServer().createInventory(holder, 9,
                Component.text(statusTag(mine) + mine.displayName(mySpecies)
                        + " " + mine.currentHp + "/" + mine.maxHp(mySpecies)
                        + "  vs  " + statusTag(theirs) + theirs.displayName(theirSpecies)
                        + " " + theirs.currentHp + "/" + theirs.maxHp(theirSpecies)));
        holder.inventory = inv;

        boolean anyPp = false;
        List<String> moves = mine.moves;
        for (int i = 0; i < Math.min(4, moves.size()); i++) {
            MoveData move = plugin.species().getMove(moves.get(i));
            if (move == null) continue;
            int pp = mine.ppFor(move);
            if (pp > 0) anyPp = true;
            ItemStack item = new ItemStack(pp > 0 ? Material.SLIME_BALL : Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(move.name, pp > 0 ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
            meta.lore(List.of(
                    Component.text(move.type + " / " + move.category, NamedTextColor.GRAY),
                    Component.text("Power " + move.power + "  Acc " + move.accuracy, NamedTextColor.GRAY),
                    Component.text("PP " + pp + "/" + move.pp, pp > 0 ? NamedTextColor.GRAY : NamedTextColor.RED)));
            if (pp > 0) meta.getPersistentDataContainer().set(keyMove, PersistentDataType.STRING, move.id);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        if (!anyPp) {
            ItemStack struggle = new ItemStack(Material.IRON_SWORD);
            ItemMeta meta = struggle.getItemMeta();
            meta.displayName(Component.text("Struggle", NamedTextColor.RED));
            meta.getPersistentDataContainer().set(keyMove, PersistentDataType.STRING, BattleManager.STRUGGLE_ID);
            struggle.setItemMeta(meta);
            inv.setItem(4, struggle);
        }

        ItemStack switchItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta switchMeta = switchItem.getItemMeta();
        switchMeta.displayName(Component.text("Switch Pokemon", NamedTextColor.YELLOW));
        switchMeta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "switch-menu");
        switchItem.setItemMeta(switchMeta);
        inv.setItem(7, switchItem);

        ItemStack forfeit = new ItemStack(Material.BARRIER);
        ItemMeta forfeitMeta = forfeit.getItemMeta();
        forfeitMeta.displayName(Component.text("Forfeit", NamedTextColor.RED));
        forfeitMeta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "forfeit");
        forfeit.setItemMeta(forfeitMeta);
        inv.setItem(8, forfeit);

        player.openInventory(inv);
    }

    public void openSwitchMenu(Player player, PvpBattle battle, boolean forced) {
        Holder holder = new Holder(true);
        Inventory inv = plugin.getServer().createInventory(holder, 9,
                Component.text(forced ? "Choose your next pokemon" : "Switch to which pokemon?"));
        holder.inventory = inv;
        PlayerParty party = plugin.parties().get(player);
        PokemonInstance active = battle.activeOf(player.getUniqueId());
        for (int i = 0; i < PlayerParty.SIZE; i++) {
            PokemonInstance p = party.get(i);
            if (p == null) continue;
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            boolean usable = p.currentHp > 0 && p != active;
            ItemStack item = new ItemStack(usable ? Material.PLAYER_HEAD : Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(statusTag(p) + p.displayName(species) + " Lv." + p.level,
                    usable ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
            meta.lore(List.of(Component.text("HP " + p.currentHp + "/" + p.maxHp(species),
                    p.currentHp > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)));
            if (usable) meta.getPersistentDataContainer().set(keySlot, PersistentDataType.INTEGER, i);
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

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        PvpBattle battle = plugin.pvp().get(player);
        if (battle == null) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String moveId = item.getItemMeta().getPersistentDataContainer()
                .get(keyMove, PersistentDataType.STRING);
        String action = item.getItemMeta().getPersistentDataContainer()
                .get(keyAction, PersistentDataType.STRING);
        Integer slot = item.getItemMeta().getPersistentDataContainer()
                .get(keySlot, PersistentDataType.INTEGER);
        if (moveId == null && action == null && slot == null) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if ("forfeit".equals(action)) {
                plugin.pvp().forfeit(player);
            } else if ("switch-menu".equals(action)) {
                openSwitchMenu(player, battle, false);
            } else if ("back".equals(action)) {
                openMoveMenu(player, battle);
            } else if (slot != null) {
                plugin.pvp().chooseSwitch(player, slot);
            } else if (moveId != null) {
                plugin.pvp().chooseMove(player, moveId);
            }
        });
    }

    /** Forced switch menus may not be escaped. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        if (!holder.switchMenu) return;
        if (!(e.getPlayer() instanceof Player player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            PvpBattle battle = plugin.pvp().get(player);
            if (battle != null && !battle.finished && battle.awaitingSwitch(player.getUniqueId())) {
                openSwitchMenu(player, battle, true);
            }
        });
    }
}
