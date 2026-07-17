package dev.thanhtin.survivalcore.claim;

import dev.thanhtin.survivalcore.SurvivalCore;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Chunk-based land claims: claim the chunk you stand in, protect it from others. */
public class ClaimManager {

    private final SurvivalCore plugin;

    public ClaimManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public UUID ownerAt(Location loc) {
        Chunk c = loc.getChunk();
        return plugin.db().claimOwner(loc.getWorld().getName(), c.getX(), c.getZ());
    }

    /** True when the player may build/interact at loc (unclaimed, owner, trusted, or admin). */
    public boolean canBuild(Player player, Location loc) {
        if (player.hasPermission("survivalcore.claim.bypass")) return true;
        UUID owner = ownerAt(loc);
        if (owner == null) return true;
        if (owner.equals(player.getUniqueId())) return true;
        return plugin.db().isTrusted(owner, player.getUniqueId());
    }

    public int maxClaims(Player player) {
        int max = plugin.getConfig().getInt("claims.default-max", 8);
        for (int n = 500; n >= 1; n--) {
            if (player.hasPermission("survivalcore.claims.max." + n)) return Math.max(max, n);
        }
        return max;
    }

    public boolean claim(Player player) {
        Chunk c = player.getLocation().getChunk();
        String world = player.getWorld().getName();
        UUID existing = plugin.db().claimOwner(world, c.getX(), c.getZ());
        if (existing != null) return false; // already claimed
        if (plugin.db().claimCount(player.getUniqueId()) >= maxClaims(player)) return false;
        return plugin.db().addClaim(world, c.getX(), c.getZ(), player.getUniqueId());
    }

    public boolean unclaim(Player player) {
        Chunk c = player.getLocation().getChunk();
        String world = player.getWorld().getName();
        UUID owner = plugin.db().claimOwner(world, c.getX(), c.getZ());
        if (owner == null) return false;
        if (!owner.equals(player.getUniqueId()) && !player.hasPermission("survivalcore.claim.bypass")) {
            return false;
        }
        return plugin.db().removeClaim(world, c.getX(), c.getZ());
    }
}
