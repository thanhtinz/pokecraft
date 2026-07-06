package dev.thanhtin.pokecraft.entity;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * Keeps wild pokemon passive and safe: no burning in daylight (HUSK base
 * entity), no chasing or hurting players, no dying to the environment.
 * Their HP is battle state, not entity health.
 */
public class WildEntityListener implements Listener {
    private final PokeCraftPlugin plugin;

    public WildEntityListener(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        if (plugin.entities().isWild(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (plugin.entities().isWild(e.getEntity())) e.setCancelled(true);
    }

    /** Wild pokemon never take entity damage directly (punches open a battle instead). */
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (plugin.entities().isWild(e.getEntity())) e.setCancelled(true);
    }

    /** Wild pokemon never hurt players outside of a battle. */
    @EventHandler
    public void onAttackPlayer(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player && plugin.entities().isWild(e.getDamager())) {
            e.setCancelled(true);
        }
    }
}
