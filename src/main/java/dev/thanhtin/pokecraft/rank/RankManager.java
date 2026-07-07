package dev.thanhtin.pokecraft.rank;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Seasonal PvP rank ladder: win/lose points, tiers, and a season reset. */
public class RankManager {

    public record Tier(String name, int min, NamedTextColor color) {}

    private static final Tier[] TIERS = {
            new Tier("Bronze", 0, NamedTextColor.GOLD),
            new Tier("Silver", 200, NamedTextColor.GRAY),
            new Tier("Gold", 500, NamedTextColor.YELLOW),
            new Tier("Platinum", 900, NamedTextColor.AQUA),
            new Tier("Diamond", 1400, NamedTextColor.BLUE),
            new Tier("Master", 2000, NamedTextColor.LIGHT_PURPLE),
    };

    private final PokeCraftPlugin plugin;

    public RankManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public int season() {
        try {
            return Integer.parseInt(plugin.storage().getMeta("rank.season", "1"));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Points for the player this season (0 if their row is from an old season). */
    public int points(UUID uuid) {
        StorageManager.RankRow row = plugin.storage().getRank(uuid);
        return row.season() == season() ? row.points() : 0;
    }

    public Tier tier(int points) {
        Tier current = TIERS[0];
        for (Tier t : TIERS) if (points >= t.min()) current = t;
        return current;
    }

    public Tier nextTier(int points) {
        for (Tier t : TIERS) if (points < t.min()) return t;
        return null;
    }

    /** Record a duel result: winner gains, loser loses (floored at 0). */
    public void recordResult(Player winner, UUID loserId) {
        int win = plugin.getConfig().getInt("rank.win-points", 25);
        int loss = plugin.getConfig().getInt("rank.loss-points", 15);
        int season = season();

        int wp = points(winner.getUniqueId()) + win;
        Tier before = tier(points(winner.getUniqueId()));
        plugin.storage().setRank(winner.getUniqueId(), winner.getName(), wp, season);
        Tier after = tier(wp);
        winner.sendMessage(Component.text("Rank +" + win + " (" + wp + " pts, " + after.name() + ")",
                after.color()));
        if (!before.name().equals(after.name())) {
            plugin.getServer().broadcast(Component.text(winner.getName()
                    + " ranked up to " + after.name() + "!", after.color()));
        }

        int lp = Math.max(0, points(loserId) - loss);
        Player loser = plugin.getServer().getPlayer(loserId);
        String loserName = loser != null ? loser.getName() : plugin.storage().getRank(loserId).name();
        plugin.storage().setRank(loserId, loserName, lp, season);
        if (loser != null) {
            loser.sendMessage(Component.text("Rank -" + loss + " (" + lp + " pts)", NamedTextColor.GRAY));
        }
    }

    /** Ends the season: announces and rewards the top players, bumps the season. */
    public void resetSeason(Player admin) {
        int season = season();
        var top = plugin.storage().topRanks(season, 3);
        long[] rewards = {
                plugin.getConfig().getLong("rank.reward-1st", 20000),
                plugin.getConfig().getLong("rank.reward-2nd", 10000),
                plugin.getConfig().getLong("rank.reward-3rd", 5000),
        };
        plugin.getServer().broadcast(Component.text("=== Rank Season " + season
                + " has ended! ===", NamedTextColor.GOLD));
        for (int i = 0; i < top.size(); i++) {
            String name = top.get(i).name();
            plugin.getServer().broadcast(Component.text("  " + (i + 1) + ". " + name
                    + " - " + top.get(i).value() + " pts (+"
                    + plugin.economy().format(rewards[i]) + ")", NamedTextColor.YELLOW));
            Player p = plugin.getServer().getPlayerExact(name);
            if (p != null) plugin.economy().deposit(p.getUniqueId(), rewards[i]);
        }
        plugin.storage().setMeta("rank.season", String.valueOf(season + 1));
        if (admin != null) {
            admin.sendMessage(Component.text("Season " + season + " reset. Now on season "
                    + (season + 1) + ".", NamedTextColor.GREEN));
        }
    }
}
