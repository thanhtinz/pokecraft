package dev.thanhtin.survivalcore.crate;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.crate.CrateManager.Crate;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Physical crates: right-click a crate block with its key to open it. */
public class CrateListener implements Listener {

    private final SurvivalCore plugin;

    public CrateListener(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        Location loc = block.getLocation();
        String crateId = plugin.db().getCrateBlock(loc);
        if (crateId == null) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        Crate crate = plugin.crates().get(crateId);
        if (crate == null) {
            Msg.error(p, "This crate (" + crateId + ") is no longer configured.");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        String keyFor = plugin.keyItem().crateId(hand);
        if (keyFor == null || !keyFor.equalsIgnoreCase(crateId)) {
            Msg.warn(p, "You need a " + crate.id() + " key to open this crate.");
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        plugin.crates().open(p, crate, loc);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();
        if (plugin.db().getCrateBlock(loc) == null) return;
        if (e.getPlayer().hasPermission("survivalcore.admin")) {
            plugin.db().removeCrateBlock(loc);
            Msg.ok(e.getPlayer(), "Removed the crate binding on this block.");
            return;
        }
        e.setCancelled(true);
        Msg.warn(e.getPlayer(), "This is a crate. Right-click it with a key to open it.");
    }
}
