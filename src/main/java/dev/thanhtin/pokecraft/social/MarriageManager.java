package dev.thanhtin.pokecraft.social;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marriage between two players: propose/accept/divorce, persisted in the
 * marriages table. Married couples earn bonus battle EXP while the spouse
 * is online.
 */
public class MarriageManager {
    private static final long PROPOSAL_TIMEOUT_MILLIS = 60_000;

    private final PokeCraftPlugin plugin;
    /** proposer -> (target, time) */
    private final Map<UUID, Proposal> proposals = new ConcurrentHashMap<>();

    private record Proposal(UUID target, long at) {}

    public MarriageManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public UUID spouseOf(UUID player) {
        return plugin.storage().spouseOf(player);
    }

    /** Battle EXP multiplier: config bonus while the spouse is online. */
    public double expMultiplier(Player player) {
        UUID spouse = spouseOf(player.getUniqueId());
        if (spouse == null) return 1.0;
        if (plugin.getServer().getPlayer(spouse) == null) return 1.0;
        return plugin.getConfig().getDouble("marriage.exp-bonus", 1.1);
    }

    public void propose(Player from, Player to) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            from.sendMessage(Component.text("You can't marry yourself.", NamedTextColor.RED));
            return;
        }
        if (spouseOf(from.getUniqueId()) != null) {
            from.sendMessage(Component.text("You are already married. /poke divorce first.", NamedTextColor.RED));
            return;
        }
        if (spouseOf(to.getUniqueId()) != null) {
            from.sendMessage(Component.text(to.getName() + " is already married.", NamedTextColor.RED));
            return;
        }
        proposals.put(from.getUniqueId(), new Proposal(to.getUniqueId(), System.currentTimeMillis()));
        from.sendMessage(Component.text("Proposal sent to " + to.getName() + ".", NamedTextColor.GREEN));
        to.sendMessage(Component.text(from.getName() + " proposes to you! Type /poke marry accept "
                + "within 60s (or /poke marry deny).", NamedTextColor.LIGHT_PURPLE));
    }

    public void accept(Player player) {
        UUID accepter = player.getUniqueId();
        for (Map.Entry<UUID, Proposal> e : proposals.entrySet()) {
            Proposal p = e.getValue();
            if (!p.target().equals(accepter)) continue;
            if (System.currentTimeMillis() - p.at() > PROPOSAL_TIMEOUT_MILLIS) {
                proposals.remove(e.getKey());
                continue;
            }
            Player proposer = plugin.getServer().getPlayer(e.getKey());
            if (proposer == null) {
                proposals.remove(e.getKey());
                continue;
            }
            if (spouseOf(accepter) != null || spouseOf(proposer.getUniqueId()) != null) {
                proposals.remove(e.getKey());
                player.sendMessage(Component.text("One of you is already married.", NamedTextColor.RED));
                return;
            }
            proposals.remove(e.getKey());
            plugin.storage().setMarriage(proposer.getUniqueId(), accepter);
            double bonus = (plugin.getConfig().getDouble("marriage.exp-bonus", 1.1) - 1.0) * 100;
            Component msg = Component.text(proposer.getName() + " and " + player.getName()
                    + " are now married! (+" + Math.round(bonus) + "% battle EXP while together online)",
                    NamedTextColor.LIGHT_PURPLE);
            plugin.getServer().broadcast(msg);
            return;
        }
        player.sendMessage(Component.text("No pending proposal for you.", NamedTextColor.RED));
    }

    public void deny(Player player) {
        UUID accepter = player.getUniqueId();
        for (Map.Entry<UUID, Proposal> e : proposals.entrySet()) {
            if (!e.getValue().target().equals(accepter)) continue;
            proposals.remove(e.getKey());
            Player proposer = plugin.getServer().getPlayer(e.getKey());
            if (proposer != null) {
                proposer.sendMessage(Component.text(player.getName() + " declined your proposal.",
                        NamedTextColor.RED));
            }
            player.sendMessage(Component.text("Proposal declined.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("No pending proposal for you.", NamedTextColor.RED));
    }

    public void divorce(Player player) {
        UUID spouse = spouseOf(player.getUniqueId());
        if (spouse == null) {
            player.sendMessage(Component.text("You are not married.", NamedTextColor.RED));
            return;
        }
        plugin.storage().removeMarriage(player.getUniqueId());
        player.sendMessage(Component.text("You are now divorced.", NamedTextColor.GRAY));
        Player other = plugin.getServer().getPlayer(spouse);
        if (other != null) {
            other.sendMessage(Component.text(player.getName() + " divorced you.", NamedTextColor.RED));
        }
    }
}
