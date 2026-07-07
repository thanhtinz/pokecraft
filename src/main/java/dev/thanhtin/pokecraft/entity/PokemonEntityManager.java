package dev.thanhtin.pokecraft.entity;

import com.google.gson.Gson;
import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Spawns wild pokemon as vanilla base entities and applies ModelEngine models
 * via reflection (soft-depend, works without ModelEngine installed).
 * Bedrock players see the models through the GeyserModelEngine extension.
 */
public class PokemonEntityManager {
    private final PokeCraftPlugin plugin;
    private final Gson gson = new Gson();
    public final NamespacedKey keyWild;
    public final NamespacedKey keyData;
    public final NamespacedKey keySpawnTime;

    private boolean modelEngine;
    private Method mCreateModeledEntity;
    private Method mCreateActiveModel;
    private Method mAddModel;

    public PokemonEntityManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyWild = new NamespacedKey(plugin, "wild");
        this.keyData = new NamespacedKey(plugin, "data");
        this.keySpawnTime = new NamespacedKey(plugin, "spawn_time");
        hookModelEngine();
    }

    private void hookModelEngine() {
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            mCreateModeledEntity = api.getMethod("createModeledEntity", Entity.class);
            mCreateActiveModel = api.getMethod("createActiveModel", String.class);
            Class<?> modeledEntity = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity");
            Class<?> activeModel = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            mAddModel = modeledEntity.getMethod("addModel", activeModel, boolean.class);
            modelEngine = true;
            plugin.getLogger().info("[OK] ModelEngine hooked - custom models enabled");
        } catch (Exception e) {
            modelEngine = false;
            plugin.getLogger().warning("[WARN] ModelEngine not found - wild pokemon use vanilla base entities");
        }
    }

    public LivingEntity spawnWild(PokemonSpecies species, PokemonInstance instance, Location loc) {
        EntityType base = EntityType.valueOf(
                plugin.getConfig().getString("capture.base-entity", "HUSK").toUpperCase(Locale.ROOT));
        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, base);
        entity.setSilent(true);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(false);
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.setAware(true);
        }
        if (entity instanceof org.bukkit.entity.Zombie zombie) zombie.setAdult();
        entity.customName(net.kyori.adventure.text.Component.text(
                instance.displayName(species) + " Lv." + instance.level));
        entity.setCustomNameVisible(true);

        entity.getPersistentDataContainer().set(keyWild, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(keyData, PersistentDataType.STRING, gson.toJson(instance));
        entity.getPersistentDataContainer().set(keySpawnTime, PersistentDataType.LONG, System.currentTimeMillis());

        applyModel(entity, species.modelId);
        return entity;
    }

    public void applyModel(Entity entity, String modelId) {
        if (!modelEngine || modelId == null || modelId.isEmpty()) return;
        try {
            Object activeModel = mCreateActiveModel.invoke(null, modelId);
            if (activeModel == null) {
                plugin.getLogger().warning("[WARN] ModelEngine model not found: " + modelId);
                return;
            }
            Object modeledEntity = mCreateModeledEntity.invoke(null, entity);
            mAddModel.invoke(modeledEntity, activeModel, true);
            if (entity instanceof LivingEntity le) le.setInvisible(true);
        } catch (Exception e) {
            plugin.getLogger().warning("[WARN] Failed to apply model " + modelId + ": " + e.getMessage());
        }
    }

    /**
     * Spawns a rideable owned pokemon (no wild tag: ignored by capture,
     * battles and the despawn sweep). Caller is responsible for removal.
     */
    public LivingEntity spawnMount(PokemonSpecies species, PokemonInstance instance, Location loc) {
        EntityType base = EntityType.valueOf(
                plugin.getConfig().getString("capture.base-entity", "HUSK").toUpperCase(Locale.ROOT));
        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, base);
        entity.setSilent(true);
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.setAware(false); // no AI wandering - the rider steers
        }
        if (entity instanceof org.bukkit.entity.Zombie zombie) zombie.setAdult();
        entity.customName(net.kyori.adventure.text.Component.text(
                instance.displayName(species) + " Lv." + instance.level));
        entity.setCustomNameVisible(true);
        applyModel(entity, species.modelId);
        return entity;
    }

    /**
     * Spawns a cosmetic follower pokemon (no wild tag, no AI, no gravity,
     * non-collidable). The caller drives its position and removes it.
     */
    public LivingEntity spawnFollower(PokemonSpecies species, PokemonInstance instance, Location loc) {
        LivingEntity entity = spawnMount(species, instance, loc);
        entity.setGravity(false);
        entity.setCollidable(false);
        return entity;
    }

    /** Epoch millis when the wild entity spawned, or 0 when unknown. */
    public long spawnTime(Entity entity) {
        Long t = entity.getPersistentDataContainer().get(keySpawnTime, PersistentDataType.LONG);
        return t == null ? 0L : t;
    }

    public boolean isWild(Entity entity) {
        return entity.getPersistentDataContainer().has(keyWild, PersistentDataType.BYTE);
    }

    public PokemonInstance readData(Entity entity) {
        String json = entity.getPersistentDataContainer().get(keyData, PersistentDataType.STRING);
        return json == null ? null : gson.fromJson(json, PokemonInstance.class);
    }

    public void writeData(Entity entity, PokemonInstance instance) {
        entity.getPersistentDataContainer().set(keyData, PersistentDataType.STRING, gson.toJson(instance));
    }

    public boolean hasModelEngine() { return modelEngine; }
}
