package dev.thanhtin.survivalcore.scoreboard;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

/**
 * A per-player sidebar scoreboard plus tab header/footer. Rebuilt on a timer so
 * dynamic values (balance, rank, online count) stay fresh. Geyser renders the
 * standard sidebar on Bedrock, so no client-specific handling is needed.
 */
public class ScoreboardService {

    private static final String CODES = "0123456789abcdef";

    private final SurvivalCore plugin;
    private BukkitTask task;

    public ScoreboardService(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        int period = Math.max(20, plugin.getConfig().getInt("scoreboard.update-ticks", 40));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) update(p);
        }, 40L, period);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public void update(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        Scoreboard board = plugin.getServer().getScoreboardManager().getNewScoreboard();
        Component title = Msg.legacy(apply(player,
                plugin.getConfig().getString("scoreboard.title", "&b&lSurvivalCore")));
        Objective obj = board.registerNewObjective("sc", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String text = color(apply(player, lines.get(i)));
            // ensure each entry is unique (scoreboard requires distinct entries)
            String entry = text + "§" + CODES.charAt(i % CODES.length());
            if (entry.length() > 64) entry = entry.substring(0, 64);
            obj.getScore(entry).setScore(score--);
        }
        player.setScoreboard(board);
        updateTab(player);
    }

    private void updateTab(Player player) {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) return;
        String header = color(apply(player, String.join("\n",
                plugin.getConfig().getStringList("tab.header"))));
        String footer = color(apply(player, String.join("\n",
                plugin.getConfig().getStringList("tab.footer"))));
        player.sendPlayerListHeaderAndFooter(Msg.legacy(header), Msg.legacy(footer));

        String rank = plugin.ranks().prefix(player.getUniqueId());
        if (rank != null && !rank.isBlank()) {
            player.playerListName(Msg.legacy(rank + " &f" + player.getName()));
        }
    }

    /** Replace SurvivalCore + PlaceholderAPI tokens in a line. */
    private String apply(Player player, String text) {
        if (text == null) return "";
        String rank = plugin.ranks().prefix(player.getUniqueId());
        text = text.replace("{name}", player.getName())
                .replace("{rank}", rank == null ? "" : rank)
                .replace("{balance}", plugin.economy().format(
                        plugin.economy().balance(player.getUniqueId())))
                .replace("{online}", String.valueOf(plugin.getServer().getOnlinePlayers().size()))
                .replace("{world}", player.getWorld().getName())
                .replace("{bounty}", plugin.economy().format(
                        plugin.db().getBounty(player.getUniqueId())));
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable ignored) {}
        }
        return text;
    }

    private String color(String text) {
        return text == null ? "" : text.replace('&', '§');
    }
}
