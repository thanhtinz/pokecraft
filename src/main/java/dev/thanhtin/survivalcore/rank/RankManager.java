package dev.thanhtin.survivalcore.rank;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A linear rank ladder. Players pay to advance to the next rank; their current
 * rank drives chat prefixes and can grant kits/commands on promotion.
 */
public class RankManager {

    public record Rank(String id, String display, double cost, List<String> commands) {}

    private final SurvivalCore plugin;
    private final List<Rank> ladder = new ArrayList<>();

    public RankManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ladder.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("ranks");
        if (root == null) {
            plugin.getLogger().info("[OK] Loaded 0 rank(s)");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection cs = root.getConfigurationSection(id);
            if (cs == null) continue;
            String display = cs.getString("display", id);
            double cost = cs.getDouble("cost", 0);
            List<String> commands = cs.getStringList("commands");
            ladder.add(new Rank(id.toLowerCase(), display, cost, commands));
        }
        plugin.getLogger().info("[OK] Loaded " + ladder.size() + " rank(s)");
    }

    public List<Rank> ladder() { return ladder; }

    public boolean isEmpty() { return ladder.isEmpty(); }

    /** The player's current rank id, or the first rung if unset. */
    public String currentId(UUID uuid) {
        String r = plugin.db().getRank(uuid);
        if (r != null) return r;
        return ladder.isEmpty() ? null : ladder.get(0).id();
    }

    public Rank current(UUID uuid) {
        String id = currentId(uuid);
        if (id == null) return null;
        for (Rank r : ladder) if (r.id().equals(id)) return r;
        return ladder.isEmpty() ? null : ladder.get(0);
    }

    public int indexOf(String id) {
        for (int i = 0; i < ladder.size(); i++) if (ladder.get(i).id().equals(id)) return i;
        return -1;
    }

    public Rank next(UUID uuid) {
        int idx = indexOf(currentId(uuid));
        if (idx < 0) return ladder.isEmpty() ? null : ladder.get(0);
        return idx + 1 < ladder.size() ? ladder.get(idx + 1) : null;
    }

    public void rankup(Player player) {
        if (ladder.isEmpty()) { Msg.error(player, "No ranks are configured."); return; }
        // ensure the player has a starting rank recorded
        if (plugin.db().getRank(player.getUniqueId()) == null) {
            plugin.db().setRank(player.getUniqueId(), ladder.get(0).id());
        }
        Rank next = next(player.getUniqueId());
        if (next == null) {
            Msg.info(player, "You're already at the highest rank!");
            return;
        }
        if (!plugin.economy().has(player.getUniqueId(), next.cost())) {
            Msg.error(player, "You need " + plugin.economy().format(next.cost())
                    + " to rank up to " + strip(next.display()) + ".");
            return;
        }
        plugin.economy().withdraw(player.getUniqueId(), next.cost());
        plugin.db().setRank(player.getUniqueId(), next.id());
        for (String cmd : next.commands()) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    cmd.replace("%player%", player.getName()));
        }
        Msg.ok(player, "You ranked up to " + strip(next.display()) + "!");
        plugin.getServer().broadcast(net.kyori.adventure.text.Component.text(
                player.getName() + " ranked up to " + strip(next.display()) + "!"));
    }

    public String prefix(UUID uuid) {
        Rank r = current(uuid);
        return r == null ? "" : r.display();
    }

    private String strip(String s) { return s.replaceAll("&[0-9a-fk-or]", ""); }
}
