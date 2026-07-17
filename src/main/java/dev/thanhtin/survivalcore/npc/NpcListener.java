package dev.thanhtin.survivalcore.npc;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/** Routes interactions with SurvivalCore NPCs to their roleplay dialogue. */
public class NpcListener implements Listener {

    private final SurvivalCore plugin;

    public NpcListener(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity entity = e.getRightClicked();
        if (!plugin.npcs().isNpc(entity)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        String role = plugin.npcs().roleOf(entity);
        route(p, role);
    }

    /** Block players from damaging/removing NPCs. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (plugin.npcs().isNpc(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        plugin.npcs().respawnMissing();
    }

    private void route(Player p, String role) {
        if (role == null) return;
        switch (role.toLowerCase()) {
            case "kit_master" -> plugin.kits().openDialogue(p);
            case "job_board" -> plugin.jobs().openDialogue(p);
            case "shop" -> plugin.shopGui().open(p, 0);
            case "banker" -> plugin.bankerGui().openMain(p);
            default -> Msg.info(p, "This villager has nothing to say.");
        }
    }
}
