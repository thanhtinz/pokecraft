package dev.thanhtin.survivalcore.bank;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.storage.Database.TopEntry;
import dev.thanhtin.survivalcore.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Banker: check your balance, see the richest players, and send money to
 * an online player entirely by clicking (pick a player, adjust the amount with
 * buttons, confirm) - no typing, so it works cleanly on Bedrock.
 */
public class BankerGui implements Listener {

    private final SurvivalCore plugin;
    private final NamespacedKey keyAction;
    // player -> in-progress transfer being composed
    private final Map<UUID, Transfer> composing = new HashMap<>();

    public BankerGui(SurvivalCore plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "bank_action");
    }

    private static final class Transfer {
        UUID target;
        String targetName;
        double amount;
    }

    private enum Screen { MAIN, PICK, AMOUNT, TOP }

    private static class Holder implements InventoryHolder {
        final Screen screen;
        Inventory inv;
        Holder(Screen screen) { this.screen = screen; }
        @Override public Inventory getInventory() { return inv; }
    }

    // ---------- screens ----------

    public void openMain(Player player) {
        Holder holder = new Holder(Screen.MAIN);
        Inventory inv = plugin.getServer().createInventory(holder, 27, Component.text("Banker"));
        holder.inv = inv;
        fill(inv);
        inv.setItem(11, button(Material.EMERALD, "&aYour balance",
                List.of("&f" + plugin.economy().format(plugin.economy().balance(player.getUniqueId()))), "none"));
        inv.setItem(13, button(Material.GOLD_INGOT, "&eSend money",
                List.of("&7Pick an online player to pay"), "send"));
        inv.setItem(15, button(Material.DIAMOND, "&bTop richest",
                List.of("&7See the wealthiest players"), "top"));
        player.openInventory(inv);
    }

    private void openPick(Player player) {
        Holder holder = new Holder(Screen.PICK);
        Inventory inv = plugin.getServer().createInventory(holder, 54, Component.text("Send money - pick"));
        holder.inv = inv;
        int slot = 0;
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.equals(player) || slot >= 45) continue;
            inv.setItem(slot++, head(online, "&f" + online.getName(),
                    List.of("&7Click to send money"), "pick:" + online.getUniqueId()));
        }
        inv.setItem(49, button(Material.BARRIER, "&cBack", List.of(), "back"));
        player.openInventory(inv);
    }

    private void openAmount(Player player) {
        Transfer t = composing.get(player.getUniqueId());
        if (t == null) { openMain(player); return; }
        Holder holder = new Holder(Screen.AMOUNT);
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text("Send to " + t.targetName));
        holder.inv = inv;
        fill(inv);
        inv.setItem(0, button(Material.RED_DYE, "&c-1000", List.of(), "amt:-1000"));
        inv.setItem(1, button(Material.RED_DYE, "&c-100", List.of(), "amt:-100"));
        inv.setItem(2, button(Material.RED_DYE, "&c-10", List.of(), "amt:-10"));
        inv.setItem(4, button(Material.PAPER, "&eAmount: &f" + plugin.economy().format(t.amount),
                List.of("&7Sending to &f" + t.targetName,
                        "&7Balance: &f" + plugin.economy().format(
                                plugin.economy().balance(player.getUniqueId()))), "none"));
        inv.setItem(6, button(Material.LIME_DYE, "&a+10", List.of(), "amt:10"));
        inv.setItem(7, button(Material.LIME_DYE, "&a+100", List.of(), "amt:100"));
        inv.setItem(8, button(Material.LIME_DYE, "&a+1000", List.of(), "amt:1000"));
        inv.setItem(22, button(Material.EMERALD_BLOCK, "&a&lConfirm & send", List.of(), "confirm"));
        inv.setItem(18, button(Material.BARRIER, "&cCancel", List.of(), "back"));
        player.openInventory(inv);
    }

    private void openTop(Player player) {
        Holder holder = new Holder(Screen.TOP);
        Inventory inv = plugin.getServer().createInventory(holder, 27, Component.text("Top richest"));
        holder.inv = inv;
        fill(inv);
        List<TopEntry> top = plugin.db().topBalances(9);
        int slot = 0;
        for (TopEntry te : top) {
            inv.setItem(slot++, button(Material.GOLD_NUGGET, "&e" + te.name(),
                    List.of("&f" + plugin.economy().format(te.balance())), "none"));
        }
        inv.setItem(22, button(Material.BARRIER, "&cBack", List.of(), "back"));
        player.openInventory(inv);
    }

    // ---------- click handling ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String act = clicked.getItemMeta().getPersistentDataContainer()
                .get(keyAction, PersistentDataType.STRING);
        if (act == null || act.equals("none")) return;

        if (act.equals("send")) { openPick(p); return; }
        if (act.equals("top")) { openTop(p); return; }
        if (act.equals("back")) { openMain(p); return; }
        if (act.startsWith("pick:")) {
            Player target = plugin.getServer().getPlayer(UUID.fromString(act.substring(5)));
            if (target == null) { Msg.error(p, "That player went offline."); openPick(p); return; }
            Transfer t = new Transfer();
            t.target = target.getUniqueId();
            t.targetName = target.getName();
            t.amount = 0;
            composing.put(p.getUniqueId(), t);
            openAmount(p);
            return;
        }
        if (act.startsWith("amt:")) {
            Transfer t = composing.get(p.getUniqueId());
            if (t == null) { openMain(p); return; }
            t.amount = Math.max(0, t.amount + Double.parseDouble(act.substring(4)));
            openAmount(p);
            return;
        }
        if (act.equals("confirm")) {
            confirm(p);
        }
    }

    private void confirm(Player p) {
        Transfer t = composing.get(p.getUniqueId());
        if (t == null) { openMain(p); return; }
        double min = plugin.getConfig().getDouble("economy.pay-minimum", 1.0);
        if (t.amount < min) { Msg.error(p, "Minimum is " + plugin.economy().format(min) + "."); return; }
        Player target = plugin.getServer().getPlayer(t.target);
        if (target == null) { Msg.error(p, "That player went offline."); p.closeInventory(); return; }
        if (!plugin.economy().withdraw(p.getUniqueId(), t.amount)) {
            Msg.error(p, "Insufficient funds.");
            return;
        }
        plugin.economy().deposit(t.target, t.amount);
        composing.remove(p.getUniqueId());
        p.closeInventory();
        Msg.ok(p, "Sent " + plugin.economy().format(t.amount) + " to " + t.targetName + ".");
        Msg.ok(target, "You received " + plugin.economy().format(t.amount) + " from " + p.getName() + ".");
    }

    // ---------- item helpers ----------

    private void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.text(" "));
        pane.setItemMeta(pm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private ItemStack button(Material mat, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.legacy(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(loreOf(lore));
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack head(OfflinePlayer owner, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        meta.displayName(Msg.legacy(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(loreOf(lore));
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> loreOf(List<String> lore) {
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Msg.legacy(s).decoration(TextDecoration.ITALIC, false));
        return l;
    }
}
