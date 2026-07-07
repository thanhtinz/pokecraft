package dev.thanhtin.pokecraft.activity;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Daily check-in: one reward per day, growing with a consecutive-day streak. */
public class DailyManager {
    private final PokeCraftPlugin plugin;

    public DailyManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public static long today() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    public boolean canClaim(Player player) {
        return plugin.storage().getDaily(player.getUniqueId()).lastDay() != today();
    }

    /** Current streak (for display), accounting for a missed day resetting it. */
    public int streakPreview(Player player) {
        StorageManager.DailyRow row = plugin.storage().getDaily(player.getUniqueId());
        long today = today();
        if (row.lastDay() == today) return row.streak();
        return row.lastDay() == today - 1 ? row.streak() + 1 : 1;
    }

    public void claim(Player player) {
        long today = today();
        StorageManager.DailyRow row = plugin.storage().getDaily(player.getUniqueId());
        if (row.lastDay() == today) {
            player.sendMessage(Component.text("You already claimed your daily reward. Come back tomorrow!",
                    NamedTextColor.RED));
            return;
        }
        int streak = row.lastDay() == today - 1 ? row.streak() + 1 : 1;
        int maxStreak = plugin.getConfig().getInt("daily.max-streak", 7);
        int effective = Math.min(streak, maxStreak);
        long base = plugin.getConfig().getLong("daily.base-reward", 200);
        long perStreak = plugin.getConfig().getLong("daily.streak-bonus", 100);
        long reward = base + (long) (effective - 1) * perStreak;

        plugin.storage().setDaily(player.getUniqueId(), today, streak);
        plugin.economy().deposit(player.getUniqueId(), reward);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        player.sendMessage(Component.text("Daily reward! Day " + streak + " streak: +"
                + plugin.economy().format(reward), NamedTextColor.GOLD));

        // bonus pokeball every 7-day streak
        if (streak % 7 == 0) {
            player.getInventory().addItem(plugin.pokeballs().create(
                    dev.thanhtin.pokecraft.capture.PokeballItem.BallType.ULTRA_BALL, 5));
            player.sendMessage(Component.text("7-day streak bonus: 5x Ultra Ball!", NamedTextColor.LIGHT_PURPLE));
        }
    }
}
