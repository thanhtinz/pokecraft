package dev.thanhtin.pokecraft.capture;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

public class CaptureListener implements Listener {
    private final PokeCraftPlugin plugin;

    public CaptureListener(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onThrow(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof Snowball ball)) return;
        if (!(ball.getShooter() instanceof Player player)) return;
        PokeballItem.BallType type = plugin.pokeballs().read(player.getInventory().getItemInMainHand());
        if (type == null) type = plugin.pokeballs().read(player.getInventory().getItemInOffHand());
        if (type == null) return;
        ball.getPersistentDataContainer().set(
                plugin.pokeballs().key(), PersistentDataType.STRING, type.name());
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball ball)) return;
        String ballName = ball.getPersistentDataContainer()
                .get(plugin.pokeballs().key(), PersistentDataType.STRING);
        if (ballName == null) return;
        Entity hit = e.getHitEntity();
        if (hit == null || !plugin.entities().isWild(hit)) return;
        if (!(ball.getShooter() instanceof Player player)) return;

        e.setCancelled(true);
        ball.remove();

        PokemonInstance instance = plugin.entities().readData(hit);
        PokemonSpecies species = instance == null ? null : plugin.species().getSpecies(instance.speciesId);
        if (instance == null || species == null) return;

        PokeballItem.BallType type = PokeballItem.BallType.valueOf(ballName);
        double hpFraction = hit instanceof LivingEntity le && instance.maxHp(species) > 0
                ? Math.max(0.05, (double) instance.currentHp / instance.maxHp(species))
                : 1.0;
        // Simplified capture formula: catchRate scaled by ball bonus, remaining HP and status
        double statusBonus = instance.status == null ? 1.0 : instance.status.catchBonus();
        double chance = Math.min(1.0, (species.catchRate * type.bonus * statusBonus * (1.6 - hpFraction)) / 255.0);
        boolean success = type == PokeballItem.BallType.MASTER_BALL
                || ThreadLocalRandom.current().nextDouble() < chance;

        if (success) {
            hit.remove();
            plugin.battles().onWildCaptured(player);
            instance.owner = player.getUniqueId();
            instance.currentHp = Math.max(1, instance.currentHp);
            int slot = plugin.parties().get(player).add(instance);
            plugin.storage().save(instance, slot);
            plugin.economy().addCaught(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            player.sendMessage(Component.text("Captured " + instance.displayName(species)
                            + " Lv." + instance.level + (slot < 0 ? " (sent to PC)" : "!"),
                    NamedTextColor.GREEN));
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.8f);
            player.sendMessage(Component.text(instance.displayName(species) + " broke free!",
                    NamedTextColor.RED));
        }
    }

    /** Melee-hitting a wild pokemon opens a battle instead of damaging it. */
    @EventHandler
    public void onPunch(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!plugin.entities().isWild(e.getEntity())) return;
        e.setCancelled(true);
        plugin.battles().startWildBattle(player, e.getEntity());
    }
}
