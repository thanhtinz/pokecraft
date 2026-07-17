package dev.thanhtin.survivalcore.crate;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import net.kyori.adventure.text.Component;
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
import java.util.concurrent.ThreadLocalRandom;

/** Config-driven crates. Keys are virtual (DB); opening rolls a weighted reward. */
public class CrateManager {

    public record Reward(int weight, Material item, int amount, String itemName,
                         double money, String command) {
        public boolean isMoney() { return money > 0; }
        public boolean isCommand() { return command != null && !command.isBlank(); }
    }

    public record Crate(String id, String display, List<Reward> rewards, int totalWeight) {}

    private final SurvivalCore plugin;
    private final Map<String, Crate> crates = new LinkedHashMap<>();

    public CrateManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        crates.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("crates");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection cs = root.getConfigurationSection(id);
            if (cs == null) continue;
            String display = cs.getString("display", id);
            List<Reward> rewards = new ArrayList<>();
            int total = 0;
            for (Map<?, ?> raw : cs.getMapList("rewards")) {
                int weight = raw.get("chance") instanceof Number n ? n.intValue() : 1;
                double money = raw.get("money") instanceof Number n ? n.doubleValue() : 0;
                String command = raw.get("command") instanceof String s ? s : null;
                Material mat = null;
                int amount = 1;
                if (raw.get("item") instanceof String s) {
                    try { mat = Material.valueOf(s.toUpperCase()); } catch (IllegalArgumentException ignored) {}
                    if (raw.get("amount") instanceof Number n) amount = Math.max(1, n.intValue());
                }
                String name = raw.get("name") instanceof String s ? s : null;
                rewards.add(new Reward(Math.max(1, weight), mat, amount, name, money, command));
                total += Math.max(1, weight);
            }
            if (!rewards.isEmpty()) crates.put(id.toLowerCase(), new Crate(id.toLowerCase(), display, rewards, total));
        }
        plugin.getLogger().info("[OK] Loaded " + crates.size() + " crate(s)");
    }

    public Crate get(String id) { return crates.get(id.toLowerCase()); }

    public java.util.Collection<Crate> all() { return crates.values(); }

    public Reward roll(Crate crate) {
        int r = ThreadLocalRandom.current().nextInt(crate.totalWeight());
        int acc = 0;
        for (Reward reward : crate.rewards()) {
            acc += reward.weight();
            if (r < acc) return reward;
        }
        return crate.rewards().get(crate.rewards().size() - 1);
    }

    /** Build the visual item that represents a reward (for the spin GUI). */
    public ItemStack icon(Reward reward) {
        ItemStack item;
        if (reward.isMoney()) {
            item = new ItemStack(Material.SUNFLOWER, 1);
            setName(item, reward.itemName() != null ? reward.itemName()
                    : plugin.economy().format(reward.money()));
        } else if (reward.isCommand()) {
            item = new ItemStack(Material.COMMAND_BLOCK, 1);
            setName(item, reward.itemName() != null ? reward.itemName() : "Special reward");
        } else if (reward.item() != null) {
            item = new ItemStack(reward.item(), reward.amount());
            if (reward.itemName() != null) setName(item, reward.itemName());
        } else {
            item = new ItemStack(Material.BARRIER);
        }
        return item;
    }

    public void grant(Player player, Reward reward) {
        if (reward.isMoney()) {
            plugin.economy().deposit(player.getUniqueId(), reward.money());
            Msg.ok(player, "You won " + plugin.economy().format(reward.money()) + "!");
        } else if (reward.isCommand()) {
            String cmd = reward.command().replace("%player%", player.getName());
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
            Msg.ok(player, "You won " + (reward.itemName() != null
                    ? reward.itemName() : "a special reward") + "!");
        } else if (reward.item() != null) {
            ItemStack give = new ItemStack(reward.item(), reward.amount());
            player.getInventory().addItem(give).values()
                    .forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
            Msg.ok(player, "You won " + reward.amount() + "x "
                    + reward.item().name().toLowerCase().replace('_', ' ') + "!");
        }
    }

    private void setName(ItemStack item, String legacyColored) {
        ItemMeta meta = item.getItemMeta();
        // support &-codes in config names
        String plain = legacyColored.replaceAll("&[0-9a-fk-or]", "");
        meta.displayName(Component.text(plain, NamedTextColor.YELLOW));
        item.setItemMeta(meta);
    }
}
