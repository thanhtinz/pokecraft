package dev.thanhtin.survivalcore.job;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Jobs are an RPG-style progression: doing a job's actions (mining, farming,
 * hunting, chopping, fishing) pays money and XP. Leveling up a job increases
 * its pay multiplier. Levels ARE the "skills" progression.
 */
public class JobManager {

    /** Money + XP awarded for one action (breaking a block, killing a mob, etc.). */
    public record Action(double money, double xp) {}

    public record Job(String id, String display, Material icon,
                      Map<String, Action> breakRewards,
                      Map<String, Action> killRewards,
                      Action fishReward) {}

    private final SurvivalCore plugin;
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private int maxJoined = 3;

    public JobManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        jobs.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("jobs");
        if (root == null) {
            plugin.getLogger().info("[OK] Loaded 0 job(s)");
            return;
        }
        maxJoined = root.getInt("max-joined", 3);
        ConfigurationSection list = root.getConfigurationSection("list");
        if (list == null) { plugin.getLogger().info("[OK] Loaded 0 job(s)"); return; }
        for (String id : list.getKeys(false)) {
            ConfigurationSection cs = list.getConfigurationSection(id);
            if (cs == null) continue;
            String display = cs.getString("display", id);
            Material icon = matOr(cs.getString("icon"), Material.IRON_PICKAXE);
            Map<String, Action> breakR = parseActions(cs.getConfigurationSection("break"));
            Map<String, Action> killR = parseActions(cs.getConfigurationSection("kill"));
            Action fish = null;
            ConfigurationSection fs = cs.getConfigurationSection("fish");
            if (fs != null) fish = new Action(fs.getDouble("money", 0), fs.getDouble("xp", 0));
            jobs.put(id.toLowerCase(), new Job(id.toLowerCase(), display, icon, breakR, killR, fish));
        }
        plugin.getLogger().info("[OK] Loaded " + jobs.size() + " job(s)");
    }

    private Map<String, Action> parseActions(ConfigurationSection cs) {
        Map<String, Action> out = new LinkedHashMap<>();
        if (cs == null) return out;
        for (String key : cs.getKeys(false)) {
            ConfigurationSection a = cs.getConfigurationSection(key);
            if (a == null) continue;
            out.put(key.toUpperCase(), new Action(a.getDouble("money", 0), a.getDouble("xp", 0)));
        }
        return out;
    }

    public Job get(String id) { return jobs.get(id.toLowerCase()); }

    public java.util.Collection<Job> all() { return jobs.values(); }

    public int maxJoined() { return maxJoined; }

    // ---------- leveling curve ----------

    /** XP required to go from (level) to (level+1). Grows each level. */
    public static double xpForNext(int level) {
        return 100.0 + (level - 1) * 50.0;
    }

    /** Total level given accumulated XP. Level starts at 1. */
    public static int levelFor(double totalXp) {
        int level = 1;
        double need = xpForNext(level);
        while (totalXp >= need) {
            totalXp -= need;
            level++;
            need = xpForNext(level);
        }
        return level;
    }

    /** XP progress into the current level (for display). */
    public static double xpInto(double totalXp) {
        int level = 1;
        double need = xpForNext(level);
        while (totalXp >= need) {
            totalXp -= need;
            level++;
            need = xpForNext(level);
        }
        return totalXp;
    }

    public int level(UUID uuid, String job) {
        return levelFor(plugin.db().getJobXp(uuid, job));
    }

    /** Pay multiplier from a job's level: +5% per level above 1. */
    public double multiplier(int level) {
        return 1.0 + (level - 1) * 0.05;
    }

    // ---------- payout ----------

    public void reward(Player player, Job job, Action action) {
        if (action == null || (action.money() <= 0 && action.xp() <= 0)) return;
        UUID id = player.getUniqueId();
        int before = level(id, job.id());
        if (action.money() > 0) {
            plugin.economy().deposit(id, action.money() * multiplier(before));
        }
        if (action.xp() > 0) {
            plugin.db().addJobXp(id, job.id(), action.xp());
            int after = level(id, job.id());
            if (after > before) {
                Msg.ok(player, job.display().replaceAll("&[0-9a-fk-or]", "")
                        + " reached level " + after + "!");
            }
        }
    }

    public boolean join(Player player, Job job) {
        UUID id = player.getUniqueId();
        if (plugin.db().hasJob(id, job.id())) {
            Msg.error(player, "You already have that job.");
            return false;
        }
        if (plugin.db().jobCount(id) >= maxJoined) {
            Msg.error(player, "You can only hold " + maxJoined + " jobs. Leave one first.");
            return false;
        }
        plugin.db().joinJob(id, job.id());
        Msg.ok(player, "You joined the " + job.id() + " job!");
        return true;
    }

    public void leave(Player player, Job job) {
        UUID id = player.getUniqueId();
        if (!plugin.db().hasJob(id, job.id())) {
            Msg.error(player, "You don't have that job.");
            return;
        }
        plugin.db().leaveJob(id, job.id());
        Msg.ok(player, "You left the " + job.id() + " job.");
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
