package dev.thanhtin.survivalcore.reward;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Vote rewards. Kept dependency-free: any vote system (NuVotifier, VotingPlugin,
 * etc.) can be configured to run "svote &lt;player&gt;" on a vote, which routes here.
 */
public class VoteManager {

    private final SurvivalCore plugin;

    public VoteManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    /** Grant the configured vote reward to a player (money + crate key + broadcast). */
    public void reward(Player player) {
        double money = plugin.getConfig().getDouble("vote.money", 500.0);
        if (money > 0) plugin.economy().deposit(player.getUniqueId(), money);

        String crate = plugin.getConfig().getString("vote.crate", "vote");
        int amount = plugin.getConfig().getInt("vote.keys", 1);
        StringBuilder msg = new StringBuilder("Thanks for voting! " + plugin.economy().format(money));
        if (crate != null && amount > 0) {
            plugin.db().addKeys(player.getUniqueId(), crate.toLowerCase(), amount);
            msg.append(" + ").append(amount).append(" ").append(crate).append(" key");
        }
        Msg.ok(player, msg.toString());

        if (plugin.getConfig().getBoolean("vote.broadcast", true)) {
            plugin.getServer().broadcast(Component.text(player.getName()
                    + " voted for the server! Vote too with /vote"));
        }
        for (String cmd : plugin.getConfig().getStringList("vote.commands")) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    cmd.replace("%player%", player.getName()));
        }
    }

    public void showLinks(Player player) {
        List<String> links = plugin.getConfig().getStringList("vote.links");
        if (links.isEmpty()) {
            Msg.info(player, "Voting is not configured yet.");
            return;
        }
        Msg.info(player, "Vote for the server to earn rewards:");
        for (String link : links) {
            Msg.plain(player, "  " + link, net.kyori.adventure.text.format.NamedTextColor.AQUA);
        }
    }
}
