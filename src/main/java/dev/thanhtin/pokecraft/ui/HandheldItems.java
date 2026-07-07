package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Everything is opened from the main menu panel now, so there are no separate
 * carried "open a panel" items anymore. This listener just cleans up the old
 * hand-held items (Pokedex book, Team bundle) that earlier versions handed out,
 * removing them from players' inventories on join.
 */
public class HandheldItems implements Listener {

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyItem;

    public HandheldItems(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyItem = new NamespacedKey(plugin, "handheld_item");
    }

    private boolean isHandheld(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(keyItem, PersistentDataType.STRING);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        for (ItemStack item : player.getInventory().getContents()) {
            if (isHandheld(item)) player.getInventory().remove(item);
        }
    }
}
