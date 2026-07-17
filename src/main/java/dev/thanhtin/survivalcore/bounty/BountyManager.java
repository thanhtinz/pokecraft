package dev.thanhtin.survivalcore.bounty;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.storage.Database.Bounty;
import dev.thanhtin.survivalcore.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Player bounties: put money on someone's head; whoever kills them in PvP
 * collects the pooled bounty. Multiple placers stack onto the same target.
 */
public class BountyManager {

    private final SurvivalCore plugin;

    public BountyManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public void place(Player placer, String targetName, double amount) {
        double min = plugin.getConfig().getDouble("bounty.min-amount", 100.0);
        if (amount < min) {
            Msg.error(placer, "Minimum bounty is " + plugin.economy().format(min) + ".");
            return;
        }
        UUID targetId = plugin.db().uuidByName(targetName);
        if (targetId == null) { Msg.error(placer, "Unknown player."); return; }
        if (targetId.equals(placer.getUniqueId())) {
            Msg.error(placer, "You can't put a bounty on yourself.");
            return;
        }
        if (!plugin.economy().withdraw(placer.getUniqueId(), amount)) {
            Msg.error(placer, "Insufficient funds.");
            return;
        }
        String properName = plugin.db().nameByUuid(targetId);
        plugin.db().addBounty(targetId, properName != null ? properName : targetName, amount);
        double total = plugin.db().getBounty(targetId);
        plugin.getServer().broadcast(Component.text(placer.getName() + " placed a "
                + plugin.economy().format(amount) + " bounty on "
                + (properName != null ? properName : targetName) + "! Total: "
                + plugin.economy().format(total)));
    }

    /** Called on a PvP kill; pays and clears any bounty on the victim. */
    public void onKill(Player killer, Player victim) {
        double amount = plugin.db().clearBounty(victim.getUniqueId());
        if (amount <= 0) return;
        plugin.economy().deposit(killer.getUniqueId(), amount);
        plugin.getServer().broadcast(Component.text(killer.getName() + " claimed the "
                + plugin.economy().format(amount) + " bounty on " + victim.getName() + "!"));
    }

    public void list(Player viewer) {
        List<Bounty> top = plugin.db().topBounties(10);
        if (top.isEmpty()) { Msg.info(viewer, "There are no active bounties."); return; }
        Msg.info(viewer, "Top bounties:");
        int i = 1;
        for (Bounty b : top) {
            Msg.plain(viewer, "  " + (i++) + ". " + b.targetName() + " - "
                    + plugin.economy().format(b.amount()),
                    net.kyori.adventure.text.format.NamedTextColor.GRAY);
        }
    }

    public double bountyOn(OfflinePlayer player) {
        return plugin.db().getBounty(player.getUniqueId());
    }
}
