package dev.thanhtin.survivalcore.kit;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.kit.KitManager.Kit;
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

import java.util.ArrayList;
import java.util.List;

/** A chest GUI listing every kit; click one to claim it. */
public class KitGui implements Listener {

    private final SurvivalCore plugin;
    private final NamespacedKey keyKit;

    public KitGui(SurvivalCore plugin) {
        this.plugin = plugin;
        this.keyKit = new NamespacedKey(plugin, "kit");
    }

    private static class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        List<Kit> kits = new ArrayList<>(plugin.kits().all());
        int rows = Math.max(1, (kits.size() + 8) / 9);
        int size = Math.min(54, rows * 9);
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, size, Component.text("Kits"));
        holder.inv = inv;
        int slot = 0;
        for (Kit kit : kits) {
            if (slot >= size) break;
            inv.setItem(slot++, icon(player, kit));
        }
        player.openInventory(inv);
    }

    private ItemStack icon(Player player, Kit kit) {
        ItemStack item = new ItemStack(kit.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(kit.display().replaceAll("&[0-9a-fk-or]", ""),
                NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        for (KitManager.KitItem ki : kit.items()) {
            lore.add(Component.text("- " + ki.amount() + "x "
                    + ki.material().name().toLowerCase().replace('_', ' '), NamedTextColor.GRAY));
        }
        if (kit.money() > 0) {
            lore.add(Component.text("- " + plugin.economy().format(kit.money()), NamedTextColor.GREEN));
        }
        long left = plugin.kits().cooldownLeft(player, kit);
        if (!plugin.kits().canUse(player, kit)) {
            lore.add(Component.text("No access", NamedTextColor.RED));
        } else if (left > 0) {
            lore.add(Component.text("Cooldown: " + KitManager.formatTime(left), NamedTextColor.RED));
        } else {
            lore.add(Component.text("Click to claim!", NamedTextColor.YELLOW));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keyKit, PersistentDataType.STRING, kit.id());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(keyKit, PersistentDataType.STRING);
        if (id == null) return;
        Kit kit = plugin.kits().get(id);
        if (kit == null || !(e.getWhoClicked() instanceof Player p)) return;
        plugin.kits().give(p, kit);
        // refresh the icon to show the new cooldown
        e.getInventory().setItem(e.getSlot(), icon(p, kit));
    }
}
