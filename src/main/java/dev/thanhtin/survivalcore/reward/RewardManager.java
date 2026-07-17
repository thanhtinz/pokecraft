package dev.thanhtin.survivalcore.reward;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.storage.Database.Daily;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.entity.Player;

/**
 * Daily login rewards with a consecutive-day streak. Claiming on consecutive
 * days grows the streak (and the payout); missing a day resets it. Every N
 * days also awards a crate key.
 */
public class RewardManager {

    private static final long DAY_MS = 86_400_000L;

    private final SurvivalCore plugin;

    public RewardManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public void claimDaily(Player player) {
        long now = System.currentTimeMillis();
        Daily d = plugin.db().getDaily(player.getUniqueId());
        long today = now / DAY_MS;
        long lastDay = d.lastClaim() == 0 ? -100 : d.lastClaim() / DAY_MS;

        if (d.lastClaim() != 0 && lastDay == today) {
            long msLeft = (today + 1) * DAY_MS - now;
            Msg.error(player, "Already claimed today. Come back in "
                    + dev.thanhtin.survivalcore.kit.KitManager.formatTime(msLeft / 1000) + ".");
            return;
        }

        int maxStreak = plugin.getConfig().getInt("daily.max-streak", 30);
        int streak;
        if (d.lastClaim() != 0 && lastDay == today - 1) {
            streak = Math.min(maxStreak, d.streak() + 1);
        } else {
            streak = 1;
        }

        double base = plugin.getConfig().getDouble("daily.base-money", 200.0);
        double perStreak = plugin.getConfig().getDouble("daily.per-streak-bonus", 50.0);
        double money = base + (streak - 1) * perStreak;
        plugin.economy().deposit(player.getUniqueId(), money);

        StringBuilder msg = new StringBuilder("Daily reward: " + plugin.economy().format(money)
                + " (day " + streak + " streak)");

        int every = plugin.getConfig().getInt("daily.streak-key.every", 7);
        if (every > 0 && streak % every == 0) {
            String crate = plugin.getConfig().getString("daily.streak-key.crate", "vote");
            int amount = plugin.getConfig().getInt("daily.streak-key.amount", 1);
            if (plugin.crates().giveKeys(player, crate.toLowerCase(), amount)) {
                msg.append(" + ").append(amount).append(" ").append(crate).append(" key");
            }
        }

        plugin.db().setDaily(player.getUniqueId(), now, streak);
        Msg.ok(player, msg.toString() + "!");
    }
}
