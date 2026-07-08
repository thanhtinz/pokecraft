package dev.thanhtin.pokecraft.dungeon;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MythicMobs integration (soft-depend via reflection). Build your dungeon with
 * MythicMobs/MythicDungeons as usual; map a mob's internal name to a pokemon in
 * {@code dungeon.mythic.battles}. When a player attacks that mob, PokeCraft
 * opens a pokemon battle against the mapped species instead of a melee fight.
 * Winning finishes the MythicMob off (its normal death fires, so MythicDungeons
 * loot / room progression still works). Losing or fleeing leaves it alive.
 *
 * <p>Runs fine without MythicMobs installed - the hook just stays disabled.</p>
 */
public class MythicBattleManager implements Listener {

    private final PokeCraftPlugin plugin;

    private boolean hooked;
    private Object apiHelper;        // MythicBukkit.inst().getAPIHelper()
    private Method mIsMythicMob;     // apiHelper.isMythicMob(Entity)
    private Method mGetInstance;     // apiHelper.getMythicMobInstance(Entity) -> ActiveMob

    /** player -> the mythic entity they are fighting via a pokemon battle */
    private final Map<UUID, UUID> links = new ConcurrentHashMap<>();

    public MythicBattleManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        hook();
    }

    private void hook() {
        try {
            Class<?> mb = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = mb.getMethod("inst").invoke(null);
            apiHelper = mb.getMethod("getAPIHelper").invoke(inst);
            mIsMythicMob = apiHelper.getClass().getMethod("isMythicMob", Entity.class);
            mGetInstance = apiHelper.getClass().getMethod("getMythicMobInstance", Entity.class);
            hooked = true;
            plugin.getLogger().info("[OK] MythicMobs hooked - dungeon pokemon battles enabled");
        } catch (Throwable t) {
            hooked = false;
            plugin.getLogger().info("[..] MythicMobs not found - dungeon pokemon battles disabled");
        }
    }

    public boolean enabled() {
        return hooked && plugin.getConfig().getBoolean("dungeon.mythic.enabled", true);
    }

    /** The MythicMob internal name of this entity, or null if it isn't one. */
    private String mobType(Entity e) {
        try {
            if (!(Boolean) mIsMythicMob.invoke(apiHelper, e)) return null;
            Object active = mGetInstance.invoke(apiHelper, e);
            if (active == null) return null;
            Object type = active.getClass().getMethod("getMobType").invoke(active);
            return type == null ? null : type.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!enabled()) return;
        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        String type = mobType(victim);
        if (type == null) return;
        String mapping = plugin.getConfig().getString("dungeon.mythic.battles." + type);
        if (mapping == null || mapping.isBlank()) return;

        e.setCancelled(true); // no melee - this is a pokemon fight

        // already fighting this mob, or busy in another battle
        if (links.containsKey(player.getUniqueId())) return;
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null) return;

        // mapping is "species" or "species:level"
        String speciesId = mapping;
        int level = plugin.getConfig().getInt("dungeon.mythic.default-level", 25);
        int colon = mapping.indexOf(':');
        if (colon > 0) {
            speciesId = mapping.substring(0, colon);
            try {
                level = Integer.parseInt(mapping.substring(colon + 1).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        PokemonSpecies species = plugin.species().getSpecies(speciesId.trim());
        if (species == null) {
            plugin.getLogger().warning("[WARN] dungeon.mythic.battles." + type
                    + " -> unknown species '" + speciesId.trim() + "'");
            return;
        }
        if (plugin.parties().get(player).firstAlive() == null) {
            player.sendMessage(Component.text("You need a usable pokemon to fight this!",
                    NamedTextColor.RED));
            return;
        }

        // freeze the mythic mob during the battle so it can't melee the player
        victim.setInvulnerable(true);
        if (victim instanceof Mob mob) mob.setAware(false);

        int shinyRate = plugin.getConfig().getInt("battle.shiny-rate", 4096);
        PokemonInstance wildInst = PokemonInstance.generate(species, level, shinyRate);
        Location loc = victim.getLocation();
        LivingEntity wild = plugin.entities().spawnWild(species, wildInst, loc);
        wild.setInvisible(true); // the mythic model stays the visible body
        links.put(player.getUniqueId(), victim.getUniqueId());
        plugin.battles().startWildBattle(player, wild);
    }

    /**
     * Called by the battle engine when a linked battle ends. On a win the mythic
     * mob is killed (so its death fires normally); otherwise it is un-frozen.
     */
    public void onResolved(Player player, boolean won) {
        UUID mobId = links.remove(player.getUniqueId());
        if (mobId == null) return;
        Entity mob = Bukkit.getEntity(mobId);
        if (!(mob instanceof LivingEntity le) || !le.isValid()) return;
        le.setInvulnerable(false);
        if (won) {
            le.setHealth(0); // let MythicMobs handle death: loot, room progression
        } else if (le instanceof Mob m) {
            m.setAware(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        onResolved(e.getPlayer(), false);
    }
}
