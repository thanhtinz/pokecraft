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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The Pokedex is a book you carry in your bag - right-click / tap to open it.
 * Every other feature is reached through the main menu panel, so the Pokedex
 * book is the only dedicated carried item. Old items from earlier versions
 * (the Team bundle) are stripped from inventories on join.
 */
public class HandheldItems implements Listener {

    /** One carryable item: config key, feature id, material and display name. */
    private record Handheld(String configKey, String id, Material material, String name, String lore) {}

    private static final List<Handheld> ITEMS = List.of(
            new Handheld("items.give-pokedex", "pokedex", Material.BOOK,
                    "Pokedex", "Right-click / tap to open your Pokedex"));

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
        cleanupAndGive(e.getPlayer());
    }

    /** Strip retired hand-held items (e.g. the old Team bundle) and hand out the current ones. */
    public void cleanupAndGive(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            String id = idOf(item);
            if (id != null && ITEMS.stream().noneMatch(h -> h.id().equals(id))) {
                player.getInventory().remove(item);
            }
        }
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
            default -> {}
        }
    }

    /** Keep hand-held items from being dropped or stashed in containers. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (idOf(e.getItemDrop().getItemStack()) != null) e.setCancelled(true);
    }

    /** Don't drop the Pokedex on death - it's re-given on respawn. */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(item -> idOf(item) != null);
    }

    /** Hand the Pokedex back after respawning so death never loses it. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> cleanupAndGive(player));
    }

    @EventHandler
    public void onMove(InventoryClickEvent e) {
        if ((idOf(e.getCurrentItem()) != null || idOf(e.getCursor()) != null)
                && e.getInventory().getType() != InventoryType.CRAFTING) {
            e.setCancelled(true);
        }
    }
}
