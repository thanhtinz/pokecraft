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
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Preset-amount money transfer, so paying needs no typed numbers. */
public class PayAmountGui implements Listener {
    private static final long[] AMOUNTS = {100, 500, 1000, 5000, 10000, 50000};

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyAmount;

    private static class Holder implements InventoryHolder {
        final String target;
        Inventory inventory;
        Holder(String target) { this.target = target; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public PayAmountGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyAmount = new NamespacedKey(plugin, "pay_amount");
    }

    public void open(Player player, String targetName) {
        Holder holder = new Holder(targetName);
        long balance = plugin.economy().balance(player.getUniqueId());
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text("Pay " + targetName + " (you have "
                        + plugin.economy().format(balance) + ")"));
        holder.inventory = inv;

        int slot = 10;
        for (long amount : AMOUNTS) {
            boolean afford = balance >= amount;
            ItemStack item = new ItemStack(afford ? Material.GOLD_INGOT : Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Send " + plugin.economy().format(amount),
                    afford ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));
            meta.lore(List.of(Component.text(afford ? "Click to send" : "Not enough money",
                    afford ? NamedTextColor.GRAY : NamedTextColor.RED)));
            if (afford) meta.getPersistentDataContainer().set(keyAmount, PersistentDataType.LONG, amount);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        Long amount = item.getItemMeta().getPersistentDataContainer()
                .get(keyAmount, PersistentDataType.LONG);
        if (amount == null) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            Player target = plugin.getServer().getPlayerExact(holder.target);
            if (target == null || target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(Component.text("That player is not available.", NamedTextColor.RED));
                return;
            }
            if (!plugin.economy().withdraw(player.getUniqueId(), amount)) {
                player.sendMessage(Component.text("Not enough money.", NamedTextColor.RED));
                return;
            }
            plugin.economy().deposit(target.getUniqueId(), amount);
            player.sendMessage(Component.text("Sent " + plugin.economy().format(amount)
                    + " to " + target.getName() + ".", NamedTextColor.GREEN));
            target.sendMessage(Component.text(player.getName() + " sent you "
                    + plugin.economy().format(amount) + ".", NamedTextColor.GREEN));
        });
    }
}
