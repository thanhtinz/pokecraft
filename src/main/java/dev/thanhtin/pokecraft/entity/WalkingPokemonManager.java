package dev.thanhtin.pokecraft.entity;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets a player's lead pokemon walk beside them. The follower is a cosmetic,
 * AI-less entity (with the species' BetterModel model when installed) that is
 * glided toward a point behind the player each tick, so it works the same on
 * PC and mobile. The toggle is persisted in the meta table, so the follower
 * comes back on relog.
 */
public class WalkingPokemonManager implements Listener {

    private final PokeCraftPlugin plugin;
    private final Map<UUID, LivingEntity> followers = new ConcurrentHashMap<>();
    private BukkitTask task;

    public WalkingPokemonManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 2L);
        // restore for players already online (e.g. a /reload)
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (isEnabled(p.getUniqueId())) spawn(p);
        }
    }

    public void stop() {
        if (task != null) task.cancel();
        for (LivingEntity e : followers.values()) if (e != null && !e.isDead()) e.remove();
        followers.clear();
    }

    private String metaKey(UUID player) {
        return "walk:" + player;
    }

    private boolean isEnabled(UUID player) {
        return "1".equals(plugin.storage().getMeta(metaKey(player), "0"));
    }

    public boolean isFollowing(Player player) {
        return followers.containsKey(player.getUniqueId());
    }

    /** Panel action: turn the walking pokemon on or off. */
    public void toggle(Player player) {
        if (followers.containsKey(player.getUniqueId())) {
            despawn(player.getUniqueId());
            plugin.storage().setMeta(metaKey(player.getUniqueId()), "0");
            player.sendMessage(Component.text("Your pokemon went back into its ball.",
                    NamedTextColor.GRAY));
        } else {
            if (!spawn(player)) {
                player.sendMessage(Component.text("You have no pokemon to walk with.",
                        NamedTextColor.RED));
                return;
            }
            plugin.storage().setMeta(metaKey(player.getUniqueId()), "1");
            player.sendMessage(Component.text("Your pokemon is now following you!",
                    NamedTextColor.GREEN));
        }
    }

    private boolean spawn(Player player) {
        return spawnAt(player, behind(player));
    }

    private boolean spawnAt(Player player, Location loc) {
        despawn(player.getUniqueId());
        List<PokemonInstance> party = plugin.parties().get(player).party();
        if (party.isEmpty()) return false;
        PokemonInstance lead = party.get(0);
        PokemonSpecies species = plugin.species().getSpecies(lead.speciesId);
        if (species == null) return false;
        LivingEntity follower = plugin.entities().spawnFollower(species, lead, loc);
        followers.put(player.getUniqueId(), follower);
        return true;
    }

    /**
     * Send the lead pokemon out at the given spot (where its ball landed) so it
     * follows the player, or recall it if it's already out. This is what
     * throwing a Poke Ball into the open does - your buddy pops out and walks
     * with you by default.
     * @return true if a pokemon was sent out, false if it was recalled/failed
     */
    public boolean throwOut(Player player, Location loc) {
        if (followers.containsKey(player.getUniqueId())) {
            despawn(player.getUniqueId());
            plugin.storage().setMeta(metaKey(player.getUniqueId()), "0");
            player.sendMessage(Component.text("Your pokemon returned to its ball.",
                    NamedTextColor.GRAY));
            return false;
        }
        if (!spawnAt(player, loc)) {
            player.sendMessage(Component.text("You have no pokemon to send out.",
                    NamedTextColor.RED));
            return false;
        }
        plugin.storage().setMeta(metaKey(player.getUniqueId()), "1");
        PokemonInstance lead = plugin.parties().get(player).party().get(0);
        PokemonSpecies species = plugin.species().getSpecies(lead.speciesId);
        player.sendMessage(Component.text("Go, " + lead.displayName(species) + "!",
                NamedTextColor.GREEN));
        return true;
    }

    private void despawn(UUID player) {
        LivingEntity e = followers.remove(player);
        if (e != null && !e.isDead()) e.remove();
    }

    /**
     * Temporarily put the walking pokemon away without turning the buddy off
     * (the meta flag stays on). Used when the player climbs onto their buddy to
     * ride it; {@link #resume(Player)} brings it back out afterwards.
     */
    public void suspend(Player player) {
        despawn(player.getUniqueId());
    }

    /** Bring the walking pokemon back out after a ride, if it's still enabled. */
    public void resume(Player player) {
        if (isEnabled(player.getUniqueId()) && !isFollowing(player)) spawn(player);
    }

    /** Refresh the follower species (e.g. after the lead changed). */
    public void refresh(Player player) {
        if (followers.containsKey(player.getUniqueId())) spawn(player);
    }

    private Location behind(Player player) {
        Location base = player.getLocation();
        Vector back = base.getDirection().setY(0);
        if (back.lengthSquared() < 1e-6) back = new Vector(0, 0, 1);
        back.normalize().multiply(-1.8);
        return base.clone().add(back);
    }

    private void tick() {
        for (Map.Entry<UUID, LivingEntity> entry : followers.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            LivingEntity follower = entry.getValue();
            if (player == null || !player.isOnline()) { despawn(entry.getKey()); continue; }
            if (follower == null || follower.isDead() || !follower.isValid()) {
                followers.remove(entry.getKey());
                if (isEnabled(entry.getKey())) spawn(player);
                continue;
            }
            Location target = behind(player);
            if (follower.getWorld() != target.getWorld()
                    || follower.getLocation().distanceSquared(target) > 40 * 40) {
                follower.teleport(target);
                continue;
            }
            Location cur = follower.getLocation();
            double dist = cur.distance(target);
            if (dist < 1.0) continue;
            Vector dir = target.toVector().subtract(cur.toVector());
            double step = Math.min(0.55, dist);
            Location next = cur.add(dir.normalize().multiply(step));
            Vector look = player.getLocation().toVector().subtract(next.toVector());
            if (look.lengthSquared() > 1e-6) next.setDirection(look);
            follower.teleport(next);
        }
    }

    /**
     * Tap your walking pokemon to hop on and ride it; sneak-tap to recall it.
     * This makes riding a natural interaction with the pokemon in the world
     * instead of a menu toggle.
     */
    @EventHandler
    public void onInteract(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = e.getPlayer();
        LivingEntity follower = followers.get(player.getUniqueId());
        if (follower == null || !follower.equals(e.getRightClicked())) return;
        e.setCancelled(true);
        if (player.isSneaking()) {
            toggle(player); // recall the buddy into its ball
        } else {
            plugin.rides().rideFollower(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (isEnabled(id)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player p = plugin.getServer().getPlayer(id);
                if (p != null && p.isOnline()) spawn(p);
            }, 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        despawn(e.getPlayer().getUniqueId());
    }
}
