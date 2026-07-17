package dev.thanhtin.survivalcore.vault;

import dev.thanhtin.survivalcore.SurvivalCore;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Opens a vault page as a chest GUI and persists it on close. Geyser-friendly. */
public class VaultGui implements Listener {

    private final SurvivalCore plugin;

    public VaultGui(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public static class Holder implements InventoryHolder {
        final UUID owner;
        final int page;
        Inventory inv;

        Holder(UUID owner, int page) {
            this.owner = owner;
            this.page = page;
        }

        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player, int page) {
        int max = plugin.vaults().maxPages(player);
        if (page < 1) page = 1;
        if (page > max) {
            dev.thanhtin.survivalcore.util.Msg.error(player,
                    "You can only use vault pages 1-" + max + ".");
            return;
        }
        Holder holder = new Holder(player.getUniqueId(), page);
        Inventory inv = plugin.getServer().createInventory(holder, VaultManager.SIZE,
                Component.text("Vault #" + page + " / " + max));
        holder.inv = inv;
        inv.setContents(plugin.vaults().load(player.getUniqueId(), page));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Holder h) {
            plugin.vaults().save(h.owner, h.page, e.getInventory().getContents());
        }
    }
}
