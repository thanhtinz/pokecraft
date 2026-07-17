package dev.thanhtin.survivalcore.claim;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Enforces claim protection: block edits, containers, and grief in claimed chunks. */
public class ClaimListener implements Listener {

    private final SurvivalCore plugin;

    public ClaimListener(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    private boolean deny(Player p, Block b) {
        if (plugin.claims().canBuild(p, b.getLocation())) return false;
        Msg.error(p, "This land is claimed.");
        return true;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (deny(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (deny(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (deny(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (deny(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Material t = e.getClickedBlock().getType();
        if (!isProtectedInteractive(t)) return;
        if (deny(e.getPlayer(), e.getClickedBlock())) e.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        // protect passive/utility entities (animals, item frames, armor stands) in claims
        if (e.getEntity() instanceof Player) return; // PvP handled elsewhere
        if (!plugin.claims().canBuild(p, e.getEntity().getLocation())) {
            Msg.error(p, "This land is claimed.");
            e.setCancelled(true);
        }
    }

    private boolean isProtectedInteractive(Material t) {
        String n = t.name();
        return n.contains("CHEST") || n.contains("SHULKER_BOX") || n.contains("BARREL")
                || n.contains("FURNACE") || n.contains("SMOKER") || n.contains("HOPPER")
                || n.contains("DISPENSER") || n.contains("DROPPER") || n.contains("BREWING")
                || n.contains("DOOR") || n.contains("TRAPDOOR") || n.contains("FENCE_GATE")
                || n.contains("BUTTON") || n.contains("LEVER") || n.contains("ANVIL")
                || n.contains("BEACON") || n.equals("CRAFTING_TABLE") || n.equals("ENCHANTING_TABLE")
                || n.contains("SIGN") || n.contains("BED") || n.contains("CANDLE")
                || n.contains("NOTE_BLOCK") || n.contains("JUKEBOX") || n.contains("LECTERN")
                || n.contains("COMPOSTER") || n.contains("CAKE") || n.contains("REPEATER")
                || n.contains("COMPARATOR");
    }
}
