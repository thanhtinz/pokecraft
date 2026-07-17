package dev.thanhtin.survivalcore.teleport;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /tpa and /tpahere requests. A request records who teleports to whom; the
 * target accepts or denies. Requests expire after tpa-expire-seconds. Also
 * records the death location so /back can return the player after dying.
 */
public class TpaManager implements Listener {

    private record Request(UUID from, boolean here, long at) {}

    private final SurvivalCore plugin;
    // keyed by the TARGET who must accept
    private final Map<UUID, Request> pending = new ConcurrentHashMap<>();

    public TpaManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    /** requester wants to teleport TO target. */
    public void request(Player from, Player target, boolean here) {
        if (from.equals(target)) {
            Msg.error(from, "You can't teleport to yourself.");
            return;
        }
        pending.put(target.getUniqueId(), new Request(from.getUniqueId(), here, System.currentTimeMillis()));
        int exp = plugin.getConfig().getInt("teleport.tpa-expire-seconds", 60);
        Msg.ok(from, "Request sent to " + target.getName() + " (expires in " + exp + "s).");
        if (here) {
            Msg.info(target, from.getName() + " wants YOU to teleport to them. /tpaccept or /tpdeny");
        } else {
            Msg.info(target, from.getName() + " wants to teleport to you. /tpaccept or /tpdeny");
        }
    }

    public void accept(Player target) {
        Request req = pending.remove(target.getUniqueId());
        if (req == null || expired(req)) {
            Msg.error(target, "You have no pending teleport request.");
            return;
        }
        Player from = plugin.getServer().getPlayer(req.from());
        if (from == null) {
            Msg.error(target, "That player is offline.");
            return;
        }
        if (req.here()) {
            // target teleports to requester
            plugin.teleports().teleport(target, from.getLocation(), from.getName());
        } else {
            // requester teleports to target
            plugin.teleports().teleport(from, target.getLocation(), target.getName());
        }
        Msg.ok(target, "Request accepted.");
    }

    public void deny(Player target) {
        Request req = pending.remove(target.getUniqueId());
        if (req == null) {
            Msg.error(target, "You have no pending teleport request.");
            return;
        }
        Player from = plugin.getServer().getPlayer(req.from());
        if (from != null) Msg.warn(from, target.getName() + " denied your request.");
        Msg.ok(target, "Request denied.");
    }

    private boolean expired(Request req) {
        long exp = plugin.getConfig().getLong("teleport.tpa-expire-seconds", 60) * 1000L;
        return System.currentTimeMillis() - req.at() > exp;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.teleports().recordBack(e.getEntity());
    }
}
