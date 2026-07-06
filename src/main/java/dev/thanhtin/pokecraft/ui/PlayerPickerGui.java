package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Picks an online player as the target of a duel challenge or a proposal. */
public class PlayerPickerGui implements Listener {

    public enum Purpose { DUEL, MARRY }

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyTarget;

    private static class Holder implements InventoryHolder {
        final Purpose purpose;
        Inventory inventory;
        Holder(Purpose purpose) { this.purpose = purpose; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public PlayerPickerGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyTarget = new NamespacedKey(plugin, "picker_target");
    }

    public void open(Player player, Purpose purpose) {
        Holder holder = new Holder(purpose);
        Inventory inv = plugin.getServer().createInventory(holder, 54,
                Component.text(purpose == Purpose.DUEL ? "Duel who?" : "Propose to whom?"));
        holder.inventory = inv;

        double maxDistance = plugin.getConfig().getDouble("pvp.max-distance", 50);
        int slot = 0;
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (slot >= 54) break;
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            if (purpose == Purpose.DUEL) {
                if (!other.getWorld().equals(player.getWorld())
                        || other.getLocation().distance(player.getLocation()) > maxDistance) continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(other);
            meta.displayName(Component.text(other.getName(), NamedTextColor.AQUA));
            meta.lore(List.of(Component.text(purpose == Purpose.DUEL
                    ? "Click to challenge" : "Click to propose", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(keyTarget, PersistentDataType.STRING, other.getName());
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }
        if (slot == 0) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta meta = none.getItemMeta();
            meta.displayName(Component.text(purpose == Purpose.DUEL
                    ? "No players nearby" : "No other players online", NamedTextColor.RED));
            none.setItemMeta(meta);
            inv.setItem(22, none);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String targetName = item.getItemMeta().getPersistentDataContainer()
                .get(keyTarget, PersistentDataType.STRING);
        if (targetName == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            Player target = plugin.getServer().getPlayerExact(targetName);
            if (target == null) {
                player.sendMessage(Component.text("Player is no longer online.", NamedTextColor.RED));
                return;
            }
            if (holder.purpose == Purpose.DUEL) plugin.pvp().challenge(player, target);
            else plugin.marriage().propose(player, target);
        });
    }
}
