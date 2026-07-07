package dev.thanhtin.pokecraft.ride;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.species.PokemonType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ride your own pokemon: walk up to your buddy (the pokemon following you)
 * and tap it to hop on, or /poke ride &lt;slot&gt;. It walks (or flies, for
 * FLYING types) wherever you look. Sneak to dismount. Look straight down to
 * stop. When you dismount a buddy you climbed onto, it goes back to walking.
 */
public class RideManager implements Listener {
    private final PokeCraftPlugin plugin;
    private final Map<UUID, Ride> rides = new ConcurrentHashMap<>();
    /** riders who mounted their walking buddy - their buddy resumes walking on dismount */
    private final Set<UUID> resumeWalk = ConcurrentHashMap.newKeySet();
    private BukkitRunnable task;

    private record Ride(LivingEntity mount, boolean flying) {}

    public RideManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = new BukkitRunnable() {
            @Override public void run() { tick(); }
        };
        task.runTaskTimer(plugin, 1, 1);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (UUID id : rides.keySet().toArray(new UUID[0])) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) dismount(player);
        }
    }

    public boolean isRiding(Player player) {
        return rides.containsKey(player.getUniqueId());
    }

    public void ride(Player player, int slot) {
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null) {
            player.sendMessage(Component.text("You can't ride during a battle.", NamedTextColor.RED));
            return;
        }
        if (isRiding(player)) dismount(player);
        PokemonInstance p = plugin.parties().get(player).get(slot);
        if (p == null) {
            player.sendMessage(Component.text("No pokemon in party slot " + (slot + 1) + ".",
                    NamedTextColor.RED));
            return;
        }
        if (p.currentHp <= 0) {
            player.sendMessage(Component.text("That pokemon has fainted.", NamedTextColor.RED));
            return;
        }
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        if (species == null) return;
        boolean flying = species.types != null && species.types.contains(PokemonType.FLYING)
                && plugin.getConfig().getBoolean("ride.allow-fly", true);
        LivingEntity mount = plugin.entities().spawnMount(species, p, player.getLocation());
        if (flying) mount.setGravity(false);
        mount.addPassenger(player);
        rides.put(player.getUniqueId(), new Ride(mount, flying));
        player.sendMessage(Component.text("Riding " + p.displayName(species)
                + (flying ? " - look up/down to fly, " : " - ") + "sneak to dismount.",
                NamedTextColor.GREEN));
    }

    /**
     * Mount the pokemon that is currently walking beside the player (slot 0).
     * The buddy is put away for the ride and comes back out when you dismount,
     * so climbing on and off your pokemon is a natural in-world interaction.
     */
    public void rideFollower(Player player) {
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null) {
            player.sendMessage(Component.text("You can't ride during a battle.", NamedTextColor.RED));
            return;
        }
        if (isRiding(player)) { dismount(player); return; }
        boolean wasWalking = plugin.walkers().isFollowing(player);
        plugin.walkers().suspend(player);
        ride(player, 0);
        if (isRiding(player) && wasWalking) resumeWalk.add(player.getUniqueId());
    }

    public void dismount(Player player) {
        Ride ride = rides.remove(player.getUniqueId());
        if (ride == null) return;
        if (ride.mount().isValid()) {
            ride.mount().eject();
            ride.mount().remove();
        }
        player.sendMessage(Component.text("Dismounted.", NamedTextColor.GRAY));
        if (resumeWalk.remove(player.getUniqueId())) plugin.walkers().resume(player);
    }

    private void tick() {
        for (Map.Entry<UUID, Ride> e : rides.entrySet()) {
            Player player = plugin.getServer().getPlayer(e.getKey());
            Ride ride = e.getValue();
            LivingEntity mount = ride.mount();
            if (player == null || !mount.isValid() || !mount.getPassengers().contains(player)) {
                rides.remove(e.getKey());
                if (mount.isValid()) mount.remove();
                continue;
            }
            float pitch = player.getLocation().getPitch();
            if (!ride.flying() && pitch > 60) continue; // look down to stop
            Vector dir = player.getLocation().getDirection();
            if (ride.flying()) {
                double speed = plugin.getConfig().getDouble("ride.fly-speed", 0.6);
                mount.setVelocity(dir.clone().multiply(speed));
                mount.setRotation(player.getLocation().getYaw(), 0);
            } else {
                double speed = plugin.getConfig().getDouble("ride.speed", 0.45);
                Vector horizontal = dir.clone().setY(0);
                if (horizontal.lengthSquared() < 0.01) continue;
                horizontal.normalize().multiply(speed);
                Vector velocity = mount.getVelocity();
                mount.setVelocity(new Vector(horizontal.getX(), velocity.getY(), horizontal.getZ()));
                mount.setRotation(player.getLocation().getYaw(), 0);
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking() && isRiding(e.getPlayer())) {
            dismount(e.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dismount(e.getPlayer());
    }
}
