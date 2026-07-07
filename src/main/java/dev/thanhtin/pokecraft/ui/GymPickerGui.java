package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.gym.BadgeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

/** OP tool: pick which gym leader to place at your location. */
public class GymPickerGui implements Listener {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 22};

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyBadge;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public GymPickerGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyBadge = new NamespacedKey(plugin, "gym_badge");
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text("Place a Gym Leader"));
        holder.inventory = inv;
        List<BadgeService.Gym> gyms = BadgeService.GYMS;
        for (int i = 0; i < gyms.size() && i < SLOTS.length; i++) {
            BadgeService.Gym g = gyms.get(i);
            ItemStack item = new ItemStack(g.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(g.leader() + " - " + g.badgeName(),
                    NamedTextColor.GOLD));
            meta.lore(List.of(
                    Component.text("Team Lv." + g.level(), NamedTextColor.GRAY),
                    Component.text("Click to place here", NamedTextColor.YELLOW)));
            meta.getPersistentDataContainer().set(keyBadge, PersistentDataType.STRING, g.badge());
            item.setItemMeta(meta);
            inv.setItem(SLOTS[i], item);
        }
        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String badge = item.getItemMeta().getPersistentDataContainer()
                .get(keyBadge, PersistentDataType.STRING);
        if (badge == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            plugin.npcs().createGym(player, badge);
        });
    }
}
