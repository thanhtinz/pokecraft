package dev.thanhtin.survivalcore.vault;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Physical vaults: right-click a bound block to open your personal storage. */
public class VaultBlockListener implements Listener {

    private final SurvivalCore plugin;

    public VaultBlockListener(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        Location loc = block.getLocation();
        if (!plugin.db().isVaultBlock(loc)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        p.playSound(loc, Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1f);
        plugin.vaultGui().open(p, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();
        if (!plugin.db().isVaultBlock(loc)) return;
        if (e.getPlayer().hasPermission("survivalcore.admin")) {
            plugin.db().removeVaultBlock(loc);
            Msg.ok(e.getPlayer(), "Removed the vault binding on this block.");
            return;
        }
        e.setCancelled(true);
        Msg.warn(e.getPlayer(), "This is a vault. Right-click it to open your storage.");
    }
}
