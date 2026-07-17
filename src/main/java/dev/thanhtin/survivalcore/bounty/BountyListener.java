package dev.thanhtin.survivalcore.bounty;

import dev.thanhtin.survivalcore.SurvivalCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Pays out a victim's bounty to their PvP killer. */
public class BountyListener implements Listener {

    private final SurvivalCore plugin;

    public BountyListener(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        plugin.bounties().onKill(killer, victim);
    }
}
