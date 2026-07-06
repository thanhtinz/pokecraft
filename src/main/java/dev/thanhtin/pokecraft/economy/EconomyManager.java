package dev.thanhtin.pokecraft.economy;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

/**
 * PokeDollar economy + per-player stats, persisted in the players table.
 * Money is earned from wild battle wins and PvP victories and spent in the
 * Pokemart (/poke shop).
 */
public class EconomyManager implements Listener {
    private final PokeCraftPlugin plugin;

    public EconomyManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public long balance(UUID player) {
        return plugin.storage().getBalance(player);
    }

    public void deposit(UUID player, long amount) {
        if (amount > 0) plugin.storage().addStat(player, "balance", amount);
    }

    /** @return false if the balance is insufficient */
    public boolean withdraw(UUID player, long amount) {
        if (amount <= 0) return true;
        if (balance(player) < amount) return false;
        plugin.storage().addStat(player, "balance", -amount);
        return true;
    }

    public void addCaught(UUID player) {
        plugin.storage().addStat(player, "caught", 1);
    }

    public void addWildWin(UUID player) {
        plugin.storage().addStat(player, "wild_wins", 1);
    }

    public void addPvpWin(UUID player) {
        plugin.storage().addStat(player, "pvp_wins", 1);
    }

    public String format(long amount) {
        return "$" + amount;
    }

    /** Money awarded for defeating a wild pokemon of the given level. */
    public long wildBattleReward(int wildLevel) {
        return Math.round(wildLevel * plugin.getConfig().getDouble("economy.wild-win-per-level", 5.0));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        long start = plugin.getConfig().getLong("economy.starting-balance", 500);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.storage().ensurePlayer(p.getUniqueId(), p.getName(), start));
    }
}
