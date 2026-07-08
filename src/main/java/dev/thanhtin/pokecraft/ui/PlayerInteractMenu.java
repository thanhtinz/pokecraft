package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Right-click another player to duel, trade or propose to them - the in-world
 * replacement for those main-menu entries. Native Cumulus form on Bedrock, a
 * small chest on Java.
 */
public class PlayerInteractMenu implements Listener {

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyAction;
    private final NamespacedKey keyTarget;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public PlayerInteractMenu(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "pim_action");
        this.keyTarget = new NamespacedKey(plugin, "pim_target");
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!(e.getRightClicked() instanceof Player target)) return;
        Player player = e.getPlayer();
        if (player.equals(target)) return;
        e.setCancelled(true);
        open(player, target);
    }

    private void run(Player player, Player target, String action) {
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("That player went offline.", NamedTextColor.RED));
            return;
        }
        switch (action) {
            case "duel" -> plugin.pvp().challenge(player, target);
            case "trade" -> plugin.trades().request(player, target);
            case "marry" -> plugin.marriage().propose(player, target);
            default -> {}
        }
    }

    public void open(Player player, Player target) {
        UUID id = target.getUniqueId();
        if (plugin.bedrock().openForm(player, target.getName(),
                "What would you like to do with " + target.getName() + "?", List.of(
                        new FormButton("Duel", () -> run(player, target, "duel")),
                        new FormButton("Trade", () -> run(player, target, "trade")),
                        new FormButton("Propose", () -> run(player, target, "marry"))))) {
            return;
        }
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 9,
                Component.text(target.getName()));
        holder.inventory = inv;
        inv.setItem(2, action(id, "duel", Material.IRON_SWORD, "Duel", NamedTextColor.RED));
        inv.setItem(4, action(id, "trade", Material.EMERALD, "Trade", NamedTextColor.GREEN));
        inv.setItem(6, action(id, "marry", Material.POPPY, "Propose", NamedTextColor.LIGHT_PURPLE));
        player.openInventory(inv);
    }

    private ItemStack action(UUID target, String id, Material mat, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(keyTarget, PersistentDataType.STRING, target.toString());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(keyAction, PersistentDataType.STRING);
        String targetId = clicked.getItemMeta().getPersistentDataContainer()
                .get(keyTarget, PersistentDataType.STRING);
        if (action == null || targetId == null) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;
        player.closeInventory();
        Player target = plugin.getServer().getPlayer(UUID.fromString(targetId));
        run(player, target, action);
    }
}
