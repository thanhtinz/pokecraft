package dev.thanhtin.pokecraft.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;

/**
 * WorldGuard region flags (soft-depend). Flags must be registered in the
 * plugin's {@code onLoad()} - before regions load - so this class is only ever
 * touched when WorldGuard is installed.
 *
 * <ul>
 *   <li>{@code pokecraft-spawns} - deny to stop wild pokemon spawning in a region</li>
 *   <li>{@code pokecraft-battles} - deny to block starting battles / duels there</li>
 * </ul>
 */
public final class WorldGuardHook {

    private static StateFlag spawnFlag;
    private static StateFlag battleFlag;

    private WorldGuardHook() {}

    /** Registers the flags. Call from onLoad() only when WorldGuard is present. */
    public static void registerFlags() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        spawnFlag = register(registry, "pokecraft-spawns");
        battleFlag = register(registry, "pokecraft-battles");
    }

    private static StateFlag register(FlagRegistry registry, String name) {
        try {
            StateFlag flag = new StateFlag(name, true); // default allow
            registry.register(flag);
            return flag;
        } catch (FlagConflictException e) {
            // already registered (e.g. after /reload) - reuse the existing one
            Flag<?> existing = registry.get(name);
            return existing instanceof StateFlag sf ? sf : null;
        }
    }

    public static boolean allowsSpawn(Location loc) {
        return test(spawnFlag, loc);
    }

    public static boolean allowsBattle(Location loc) {
        return test(battleFlag, loc);
    }

    private static boolean test(StateFlag flag, Location loc) {
        if (flag == null || loc == null || loc.getWorld() == null) return true;
        try {
            RegionQuery query = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer().createQuery();
            return query.testState(BukkitAdapter.adapt(loc), null, flag);
        } catch (Throwable t) {
            return true; // never let a WG hiccup block gameplay
        }
    }
}
