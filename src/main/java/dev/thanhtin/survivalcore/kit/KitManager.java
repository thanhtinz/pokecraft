package dev.thanhtin.survivalcore.kit;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Config-driven kits: items + money + commands, with a per-kit cooldown. */
public class KitManager {

    public record KitItem(Material material, int amount, String name) {}

    public record Kit(String id, String display, Material icon, String permission,
                      long cooldownSeconds, double money, List<KitItem> items,
                      List<String> commands) {}

    private final SurvivalCore plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        kits.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("kits");
        if (root == null) {
            plugin.getLogger().info("[OK] Loaded 0 kit(s)");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection cs = root.getConfigurationSection(id);
            if (cs == null) continue;
            String display = cs.getString("display", id);
            Material icon = matOr(cs.getString("icon"), Material.CHEST);
            String perm = cs.getString("permission", null);
            long cd = cs.getLong("cooldown-seconds", 0);
            double money = cs.getDouble("money", 0);
            List<KitItem> items = new ArrayList<>();
            for (Map<?, ?> raw : cs.getMapList("items")) {
                Material mat = raw.get("item") instanceof String s ? matOr(s, null) : null;
                if (mat == null) continue;
                int amount = raw.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
                String name = raw.get("name") instanceof String s ? s : null;
                items.add(new KitItem(mat, amount, name));
            }
            List<String> commands = cs.getStringList("commands");
            kits.put(id.toLowerCase(), new Kit(id.toLowerCase(), display, icon, perm, cd, money, items, commands));
        }
        plugin.getLogger().info("[OK] Loaded " + kits.size() + " kit(s)");
    }

    public Kit get(String id) { return kits.get(id.toLowerCase()); }

    public java.util.Collection<Kit> all() { return kits.values(); }

    /** Seconds remaining before the player may claim again (0 = ready). */
    public long cooldownLeft(Player player, Kit kit) {
        long next = plugin.db().getKitCooldown(player.getUniqueId(), kit.id());
        long now = System.currentTimeMillis();
        return next > now ? (next - now) / 1000L : 0;
    }

    public boolean canUse(Player player, Kit kit) {
        return kit.permission() == null || player.hasPermission(kit.permission());
    }

    public void give(Player player, Kit kit) {
        if (!canUse(player, kit)) {
            Msg.error(player, "You don't have access to the " + kit.id() + " kit.");
            return;
        }
        long left = cooldownLeft(player, kit);
        if (left > 0) {
            Msg.error(player, "Kit " + kit.id() + " is on cooldown for " + formatTime(left) + ".");
            return;
        }
        for (KitItem ki : kit.items()) {
            ItemStack item = new ItemStack(ki.material(), ki.amount());
            if (ki.name() != null) {
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(ki.name().replaceAll("&[0-9a-fk-or]", ""),
                        NamedTextColor.YELLOW));
                item.setItemMeta(meta);
            }
            player.getInventory().addItem(item).values()
                    .forEach(rem -> player.getWorld().dropItemNaturally(player.getLocation(), rem));
        }
        if (kit.money() > 0) plugin.economy().deposit(player.getUniqueId(), kit.money());
        for (String cmd : kit.commands()) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    cmd.replace("%player%", player.getName()));
        }
        if (kit.cooldownSeconds() > 0) {
            plugin.db().setKitCooldown(player.getUniqueId(), kit.id(),
                    System.currentTimeMillis() + kit.cooldownSeconds() * 1000L);
        }
        Msg.ok(player, "You claimed the " + kit.id() + " kit!");
    }

    /** Roleplay dialogue: the Kit Master offers kits as clickable chat lines. */
    public void openDialogue(Player player) {
        player.sendMessage(Msg.legacy("&8&m--------&r &b&lKit Master &8&m--------"));
        player.sendMessage(Msg.legacy("&7\"Take what you need, traveller.\""));
        boolean any = false;
        for (Kit kit : all()) {
            if (!canUse(player, kit)) continue;
            any = true;
            Component line = Component.text(" » ", NamedTextColor.DARK_GRAY)
                    .append(Msg.legacy(kit.display()));
            long left = cooldownLeft(player, kit);
            if (left > 0) {
                line = line.append(Component.text("  (ready in " + formatTime(left) + ")",
                        NamedTextColor.GRAY));
            } else {
                line = line.append(Component.text("  [Claim]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/kit " + kit.id()))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Click to claim the " + kit.id() + " kit"))));
            }
            player.sendMessage(line);
        }
        if (!any) player.sendMessage(Msg.legacy("&7There are no kits available to you."));
    }

    public static String formatTime(long seconds) {
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    private Material matOr(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
