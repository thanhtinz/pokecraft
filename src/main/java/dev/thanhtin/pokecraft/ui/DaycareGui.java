package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.party.PlayerParty;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Daycare panel: top row = your party (click to deposit), bottom row = the
 * daycare pens (click to withdraw).
 */
public class DaycareGui implements Listener {
    private static final int SLOT_INFO = 13;
    private static final int[] PEN_SLOTS = {21, 23};

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyDeposit;
    private final NamespacedKey keyWithdraw;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public DaycareGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyDeposit = new NamespacedKey(plugin, "daycare_deposit");
        this.keyWithdraw = new NamespacedKey(plugin, "daycare_withdraw");
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 27, Component.text("Daycare"));
        holder.inventory = inv;

        PlayerParty party = plugin.parties().get(player);
        for (int i = 0; i < PlayerParty.SIZE; i++) {
            PokemonInstance p = party.get(i);
            if (p == null) continue;
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            if (species == null) continue;
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(p.displayName(species) + " Lv." + p.level,
                    NamedTextColor.AQUA));
            meta.lore(List.of(Component.text("Click to leave at the daycare", NamedTextColor.YELLOW)));
            meta.getPersistentDataContainer().set(keyDeposit, PersistentDataType.INTEGER, i);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        List<StorageManager.DaycareEntry> entries = plugin.storage().daycareOf(player.getUniqueId());
        for (int pen = 0; pen < PEN_SLOTS.length; pen++) {
            if (pen < entries.size()) {
                StorageManager.DaycareEntry entry = entries.get(pen);
                PokemonInstance p = plugin.storage().loadPokemon(entry.pokemonUuid());
                long minutes = Math.max(0, (System.currentTimeMillis() - entry.depositedAt()) / 60_000);
                ItemStack item = new ItemStack(Material.TURTLE_EGG);
                ItemMeta meta = item.getItemMeta();
                String name = "?";
                if (p != null) {
                    PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
                    name = (species != null ? p.displayName(species) : p.speciesId) + " Lv." + p.level;
                }
                meta.displayName(Component.text(name, NamedTextColor.GREEN));
                meta.lore(List.of(
                        Component.text("In daycare for " + minutes + "min", NamedTextColor.GRAY),
                        Component.text("Click to take it back", NamedTextColor.YELLOW)));
                meta.getPersistentDataContainer().set(keyWithdraw, PersistentDataType.INTEGER, pen);
                item.setItemMeta(meta);
                inv.setItem(PEN_SLOTS[pen], item);
            } else {
                ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = empty.getItemMeta();
                meta.displayName(Component.text("Empty pen", NamedTextColor.DARK_GRAY));
                empty.setItemMeta(meta);
                inv.setItem(PEN_SLOTS[pen], empty);
            }
        }

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(Component.text("How it works", NamedTextColor.YELLOW));
        meta.lore(List.of(
                Component.text("Deposited pokemon gain EXP over time", NamedTextColor.GRAY),
                Component.text("Two pokemon of the same family", NamedTextColor.GRAY),
                Component.text("may produce a baby!", NamedTextColor.GRAY)));
        info.setItemMeta(meta);
        inv.setItem(SLOT_INFO, info);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        Integer deposit = item.getItemMeta().getPersistentDataContainer()
                .get(keyDeposit, PersistentDataType.INTEGER);
        Integer withdraw = item.getItemMeta().getPersistentDataContainer()
                .get(keyWithdraw, PersistentDataType.INTEGER);
        if (deposit == null && withdraw == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (deposit != null) plugin.daycare().deposit(player, deposit);
            else plugin.daycare().withdraw(player, withdraw);
            open(player); // refresh
        });
    }
}
