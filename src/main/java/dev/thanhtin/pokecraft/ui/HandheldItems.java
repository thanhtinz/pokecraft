package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Dedicated hand-held items that open a single feature panel each - e.g. a
 * Pokedex book you carry in your bag - rather than routing everything through
 * one shared menu. Right-click / tap to open (works on PC and mobile). The
 * items are handed out on join (configurable) and can't be dropped or stashed.
 */
public class HandheldItems implements Listener {

    /** One carryable item: config key, feature id, material and display name. */
    private record Handheld(String configKey, String id, Material material, String name, String lore) {}

    private static final List<Handheld> ITEMS = List.of(
            new Handheld("items.give-pokedex", "pokedex", Material.BOOK,
                    "Pokedex", "Right-click / tap to open your Pokedex"),
            new Handheld("items.give-party", "party", Material.BUNDLE,
                    "Team", "Right-click / tap to view your party"));

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyItem;

    public HandheldItems(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyItem = new NamespacedKey(plugin, "handheld_item");
    }

    private ItemStack build(Handheld h) {
        ItemStack item = new ItemStack(h.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(h.name(), NamedTextColor.AQUA));
        meta.lore(List.of(Component.text(h.lore(), NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(keyItem, PersistentDataType.STRING, h.id());
        item.setItemMeta(meta);
        return item;
    }

    private String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(keyItem, PersistentDataType.STRING);
    }

    private boolean hasItem(Player player, String id) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (id.equals(idOf(item))) return true;
        }
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        for (Handheld h : ITEMS) {
            if (!plugin.getConfig().getBoolean(h.configKey(), true)) continue;
            if (hasItem(player, h.id())) continue;
            player.getInventory().addItem(build(h));
        }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = idOf(e.getItem());
        if (id == null) return;
        e.setCancelled(true);
        Player player = e.getPlayer();
        switch (id) {
            case "pokedex" -> plugin.pokedexUi().open(player, 0);
            case "party" -> plugin.partyUi().open(player);
            default -> {}
        }
    }

    /** Keep hand-held items from being dropped or stashed in containers. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (idOf(e.getItemDrop().getItemStack()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onMove(InventoryClickEvent e) {
        if ((idOf(e.getCurrentItem()) != null || idOf(e.getCursor()) != null)
                && e.getInventory().getType() != InventoryType.CRAFTING) {
            e.setCancelled(true);
        }
    }
}
