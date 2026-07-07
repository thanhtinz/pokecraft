package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.npc.NpcManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * OP-only setup panel: toggle features, tune numbers and place NPCs live,
 * writing straight to config - no file editing or commands needed.
 */
public class AdminGui implements Listener {

    private record Toggle(int slot, String path, String label, boolean spawnRestart) {}
    private record Num(int slot, String path, String label, long step, long min, long max,
                       boolean spawnRestart) {}
    private record Act(int slot, String id, Material material, String label) {}

    private final List<Toggle> toggles = List.of(
            new Toggle(9, "spawning.enabled", "Wild spawning", true),
            new Toggle(10, "fishing.enabled", "Fishing", false),
            new Toggle(11, "bedrock.use-forms", "Bedrock native forms", false),
            new Toggle(12, "menu.give-item", "Give menu item on join", false));

    private final List<Num> nums = List.of(
            new Num(18, "spawning.interval-ticks", "Spawn interval (ticks)", 20, 20, 2000, true),
            new Num(19, "spawning.max-wild-per-player", "Max wild per player", 1, 1, 20, false),
            new Num(20, "battle.shiny-rate", "Shiny rate (1 / N)", 256, 64, 8192, false),
            new Num(21, "daily.base-reward", "Daily base reward", 50, 0, 100000, false),
            new Num(22, "dungeon.cost", "Dungeon entry cost", 500, 0, 100000, false),
            new Num(23, "dungeon.reward", "Dungeon clear reward", 1000, 0, 1000000, false));

    private final List<Act> acts = List.of(
            new Act(29, "npc_healer", Material.PINK_BED, "Place Healer NPC (here)"),
            new Act(30, "npc_vendor", Material.EMERALD, "Place Vendor NPC (here)"),
            new Act(31, "npc_trainer", Material.IRON_SWORD, "Place Trainer NPC (here)"),
            new Act(32, "npc_gym", Material.GOLDEN_HELMET, "Place Gym Leader (pick)"),
            new Act(33, "models", Material.ARMOR_STAND, "Pokemon 3D models"),
            new Act(34, "rankreset", Material.DIAMOND, "Reset rank season"),
            new Act(35, "heal", Material.GOLDEN_APPLE, "Heal my party"),
            new Act(36, "reload", Material.REDSTONE, "Reload config"));

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyToggle;
    private final NamespacedKey keyNum;
    private final NamespacedKey keyAct;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public AdminGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyToggle = new NamespacedKey(plugin, "admin_toggle");
        this.keyNum = new NamespacedKey(plugin, "admin_num");
        this.keyAct = new NamespacedKey(plugin, "admin_act");
    }

    public void open(Player player) {
        if (!player.hasPermission("pokecraft.admin")) {
            player.sendMessage(Component.text("Ops only.", NamedTextColor.RED));
            return;
        }
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 45,
                Component.text("PokeCraft Setup (OP)"));
        holder.inventory = inv;

        for (Toggle t : toggles) {
            boolean on = plugin.getConfig().getBoolean(t.path(), true);
            ItemStack item = new ItemStack(on ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(t.label() + ": " + (on ? "ON" : "OFF"),
                    on ? NamedTextColor.GREEN : NamedTextColor.RED));
            meta.lore(List.of(Component.text("Click to toggle", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(keyToggle, PersistentDataType.STRING, t.path());
            item.setItemMeta(meta);
            inv.setItem(t.slot(), item);
        }
        for (Num n : nums) {
            long val = plugin.getConfig().getLong(n.path(), 0);
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(n.label() + ": " + val, NamedTextColor.YELLOW));
            meta.lore(List.of(
                    Component.text("Left-click +" + n.step() + ", right-click -" + n.step(),
                            NamedTextColor.GRAY),
                    Component.text("Shift for x5", NamedTextColor.DARK_GRAY)));
            meta.getPersistentDataContainer().set(keyNum, PersistentDataType.STRING, n.path());
            item.setItemMeta(meta);
            inv.setItem(n.slot(), item);
        }
        for (Act a : acts) {
            ItemStack item = new ItemStack(a.material());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(a.label(), NamedTextColor.AQUA));
            meta.getPersistentDataContainer().set(keyAct, PersistentDataType.STRING, a.id());
            item.setItemMeta(meta);
            inv.setItem(a.slot(), item);
        }

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("pokecraft.admin")) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String toggle = pdc.get(keyToggle, PersistentDataType.STRING);
        String num = pdc.get(keyNum, PersistentDataType.STRING);
        String act = pdc.get(keyAct, PersistentDataType.STRING);
        ClickType click = e.getClick();

        if (toggle != null) {
            boolean now = !plugin.getConfig().getBoolean(toggle, true);
            plugin.getConfig().set(toggle, now);
            plugin.saveConfig();
            if (toggles.stream().anyMatch(t -> t.path().equals(toggle) && t.spawnRestart())) {
                restartSpawn();
            }
            open(player);
            return;
        }
        if (num != null) {
            Num n = nums.stream().filter(x -> x.path().equals(num)).findFirst().orElse(null);
            if (n == null) return;
            long step = n.step() * (click.isShiftClick() ? 5 : 1);
            long val = plugin.getConfig().getLong(num, 0);
            val += click.isRightClick() ? -step : step;
            val = Math.max(n.min(), Math.min(n.max(), val));
            plugin.getConfig().set(num, val);
            plugin.saveConfig();
            if (n.spawnRestart()) restartSpawn();
            open(player);
            return;
        }
        if (act != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> runAction(player, act));
        }
    }

    private void runAction(Player player, String act) {
        switch (act) {
            case "npc_healer" -> { plugin.npcs().create(player, NpcManager.NpcType.HEALER, "Nurse Joy", 0);
                player.closeInventory(); }
            case "npc_vendor" -> { plugin.npcs().create(player, NpcManager.NpcType.VENDOR, "Shop Keeper", 0);
                player.closeInventory(); }
            case "npc_trainer" -> { plugin.npcs().create(player, NpcManager.NpcType.TRAINER, "Trainer", 15);
                player.closeInventory(); }
            case "npc_gym" -> plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.gymPickerUi().open(player));
            case "models" -> plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.modelUi().open(player, 0, false));
            case "rankreset" -> { plugin.ranks().resetSeason(player); open(player); }
            case "heal" -> {
                for (var p : plugin.parties().get(player).party()) {
                    var s = plugin.species().getSpecies(p.speciesId);
                    if (s != null) p.heal(s);
                }
                plugin.parties().saveParty(player.getUniqueId());
                player.sendMessage(Component.text("Party healed.", NamedTextColor.GREEN));
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.species().load();
                plugin.fishing().invalidate();
                player.sendMessage(Component.text("Reloaded.", NamedTextColor.GREEN));
                open(player);
            }
            default -> {}
        }
    }

    private void restartSpawn() {
        plugin.spawns().stop();
        plugin.spawns().start();
    }
}
