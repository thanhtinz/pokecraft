package dev.thanhtin.survivalcore.job;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.job.JobManager.Action;
import dev.thanhtin.survivalcore.job.JobManager.Job;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Awards job money/XP for player actions. Blocks the player placed themselves
 * are not paid out (a lightweight anti place-and-break guard held in memory).
 */
public class JobListener implements Listener {

    private static final int MAX_TRACKED = 20_000;

    private final SurvivalCore plugin;
    // recently placed blocks (bounded, insertion-ordered so we can evict oldest)
    private final Set<String> placed = Collections.synchronizedSet(new LinkedHashSet<>());

    public JobListener(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    private String key(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        String k = key(e.getBlock().getLocation());
        placed.add(k);
        if (placed.size() > MAX_TRACKED) {
            synchronized (placed) {
                var it = placed.iterator();
                if (it.hasNext()) { it.next(); it.remove(); }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        String k = key(e.getBlock().getLocation());
        if (placed.remove(k)) return; // player placed it - no payout
        String mat = e.getBlock().getType().name();
        for (String jobId : plugin.db().joinedJobs(p.getUniqueId())) {
            Job job = plugin.jobs().get(jobId);
            if (job == null) continue;
            Action action = job.breakRewards().get(mat);
            if (action != null) plugin.jobs().reward(p, job, action);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null) return;
        String type = e.getEntityType().name();
        for (String jobId : plugin.db().joinedJobs(p.getUniqueId())) {
            Job job = plugin.jobs().get(jobId);
            if (job == null) continue;
            Action action = job.killRewards().get(type);
            if (action != null) plugin.jobs().reward(p, job, action);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();
        for (String jobId : plugin.db().joinedJobs(p.getUniqueId())) {
            Job job = plugin.jobs().get(jobId);
            if (job == null || job.fishReward() == null) continue;
            plugin.jobs().reward(p, job, job.fishReward());
        }
    }
}
