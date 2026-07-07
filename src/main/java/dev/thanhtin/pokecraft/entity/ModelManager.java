package dev.thanhtin.pokecraft.entity;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps each pokemon species to a ModelEngine blueprint and lets a server owner
 * add / preview 3D models without touching code.
 *
 * <p>How models are added: build a {@code .bbmodel} in BlockBench, import it
 * into ModelEngine (that becomes a "blueprint" with an id). PokeCraft binds a
 * species to the blueprint whose id equals the species id (e.g. {@code pikachu}).
 * If your blueprint is named differently, set an override with
 * {@code /poke model set <species> <blueprint>} - stored in config, no restart.
 * Species with no matching blueprint just fall back to a vanilla base entity.</p>
 */
public class ModelManager {

    private final PokeCraftPlugin plugin;

    public ModelManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("models.enabled", true);
    }

    public double scale() {
        return plugin.getConfig().getDouble("models.default-scale", 1.0);
    }

    /** The blueprint id used for a species: override, else its modelId, else its id. */
    public String blueprintFor(PokemonSpecies species) {
        if (species == null) return null;
        String override = plugin.getConfig().getString("models.overrides." + species.id, null);
        if (override != null && !override.isBlank()) return override;
        return (species.modelId != null && !species.modelId.isBlank()) ? species.modelId : species.id;
    }

    public boolean hasModel(PokemonSpecies species) {
        return enabled() && plugin.entities().hasBlueprint(blueprintFor(species));
    }

    /** @return {speciesWithModel, total}. Zero-with when ModelEngine is absent. */
    public int[] coverage() {
        int with = 0, total = 0;
        for (PokemonSpecies s : plugin.species().all()) {
            total++;
            if (hasModel(s)) with++;
        }
        return new int[]{with, total};
    }

    public void setOverride(String speciesId, String blueprintId) {
        plugin.getConfig().set("models.overrides." + speciesId.toLowerCase(Locale.ROOT), blueprintId);
        plugin.saveConfig();
    }

    public void clearOverride(String speciesId) {
        plugin.getConfig().set("models.overrides." + speciesId.toLowerCase(Locale.ROOT), null);
        plugin.saveConfig();
    }

    /** Spawn a temporary entity wearing a blueprint so the owner can eyeball it. */
    public void preview(Player player, String blueprintId) {
        if (!plugin.entities().hasModelEngine()) {
            player.sendMessage(Component.text("ModelEngine is not installed - can't preview.",
                    NamedTextColor.RED));
            return;
        }
        Location loc = player.getLocation().add(
                player.getLocation().getDirection().setY(0).normalize().multiply(2.5));
        LivingEntity e = plugin.entities().spawnBareBase(loc);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setGravity(false);
        if (e instanceof Mob m) m.setAware(false);
        e.customName(Component.text(blueprintId, NamedTextColor.AQUA));
        e.setCustomNameVisible(true);
        plugin.entities().applyModel(e, blueprintId);
        int seconds = plugin.getConfig().getInt("models.preview-seconds", 8);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> { if (e.isValid()) e.remove(); }, Math.max(1, seconds) * 20L);
        player.sendMessage(Component.text("Previewing model \"" + blueprintId + "\" for "
                + seconds + "s.", NamedTextColor.GREEN));
    }

    /** Species that currently have (or lack) a usable blueprint - for the panel. */
    public List<PokemonSpecies> speciesByModel(boolean withModel) {
        List<PokemonSpecies> out = new ArrayList<>();
        for (PokemonSpecies s : plugin.species().all()) {
            if (hasModel(s) == withModel) out.add(s);
        }
        return out;
    }
}
