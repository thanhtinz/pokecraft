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
    private Method mGetBlueprint;

    // BetterModel (free alternative to ModelEngine, supports newer MC versions)
    private boolean betterModel;
    private Method mBmModel;

    public PokemonEntityManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyWild = new NamespacedKey(plugin, "wild");
        this.keyData = new NamespacedKey(plugin, "data");
        this.keySpawnTime = new NamespacedKey(plugin, "spawn_time");
        hookModelEngine();
        if (!modelEngine) hookBetterModel();
    }

    private void hookBetterModel() {
        for (String cn : new String[]{"kr.toxicity.model.api.BetterModel",
                "kr.toxicity.model.BetterModel"}) {
            try {
                Class<?> bm = Class.forName(cn);
                try {
                    mBmModel = bm.getMethod("modelOrNull", String.class);
                } catch (NoSuchMethodException e) {
                    mBmModel = bm.getMethod("model", String.class); // returns Optional
                }
                betterModel = true;
                plugin.getLogger().info("[OK] BetterModel hooked - custom models enabled");
                return;
            } catch (Throwable ignored) {
                // try the next candidate package
            }
        }
        betterModel = false;
        plugin.getLogger().info("[OK] No model engine found - wild pokemon use vanilla base entities");
    }

    private void hookModelEngine() {
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            mCreateModeledEntity = api.getMethod("createModeledEntity", Entity.class);
            mCreateActiveModel = api.getMethod("createActiveModel", String.class);
            Class<?> modeledEntity = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity");
            Class<?> activeModel = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            mAddModel = modeledEntity.getMethod("addModel", activeModel, boolean.class);
            try {
                mGetBlueprint = api.getMethod("getBlueprint", String.class);
            } catch (NoSuchMethodException ex) {
                mGetBlueprint = null; // older/newer API: enumeration unavailable, models still apply
            }
            modelEngine = true;
            plugin.getLogger().info("[OK] ModelEngine hooked - custom models enabled");
        } catch (Exception e) {
            modelEngine = false;
            plugin.getLogger().info("[..] ModelEngine not found - trying BetterModel");
        }
    }

    /**
     * The vanilla mob used to represent a species. Without a working 3D model
     * engine (e.g. on Bedrock/mobile, where custom models can't render), this
     * lets each pokemon look like a fitting vanilla mob instead of all being
     * the same husk. Order: mobs.by-species &gt; mobs.by-type &gt; capture.base-entity.
     */
    public EntityType baseEntityFor(PokemonSpecies species) {
        String pick = null;
        if (species != null && species.id != null) {
            pick = plugin.getConfig().getString("mobs.by-species." + species.id);
        }
        if (pick == null && species != null && species.types != null && !species.types.isEmpty()) {
            pick = plugin.getConfig().getString("mobs.by-type." + species.types.get(0).name());
        }
        if (pick == null) pick = plugin.getConfig().getString("capture.base-entity", "HUSK");
        try {
            EntityType t = EntityType.valueOf(pick.toUpperCase(Locale.ROOT));
            if (t.getEntityClass() != null && LivingEntity.class.isAssignableFrom(t.getEntityClass())
                    && t.isSpawnable()) {
                return t;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return EntityType.HUSK;
    }

    public LivingEntity spawnWild(PokemonSpecies species, PokemonInstance instance, Location loc) {
        EntityType base = baseEntityFor(species);
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

        applyModel(entity, plugin.models().blueprintFor(species));
        return entity;
    }

    /** True when a blueprint with this id is installed in the active model engine. */
    public boolean hasBlueprint(String id) {
        if (id == null || id.isEmpty()) return false;
        if (modelEngine && mGetBlueprint != null) {
            try {
                return mGetBlueprint.invoke(null, id) != null;
            } catch (Exception e) {
                return false;
            }
        }
        if (betterModel && mBmModel != null) {
            try {
                return unwrapOptional(mBmModel.invoke(null, id)) != null;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /** Spawn a plain base entity (no tags, no model) for model previews. */
    public LivingEntity spawnBareBase(Location loc) {
        EntityType base = EntityType.valueOf(
                plugin.getConfig().getString("capture.base-entity", "HUSK").toUpperCase(Locale.ROOT));
        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, base);
        entity.setPersistent(false);
        if (entity instanceof Mob mob) mob.setAware(false);
        return entity;
    }

    public void applyModel(Entity entity, String modelId) {
        if (modelId == null || modelId.isEmpty() || !plugin.models().enabled()) return;
        if (modelEngine) { applyModelEngine(entity, modelId); return; }
        if (betterModel) { applyBetterModel(entity, modelId); }
    }

    private void applyModelEngine(Entity entity, String modelId) {
        try {
            Object activeModel = mCreateActiveModel.invoke(null, modelId);
            if (activeModel == null) {
                plugin.getLogger().warning("[WARN] ModelEngine model not found: " + modelId);
                return;
            }
            Object modeledEntity = mCreateModeledEntity.invoke(null, entity);
            mAddModel.invoke(modeledEntity, activeModel, true);
            hideBaseAfterModel(entity);
        } catch (Exception e) {
            plugin.getLogger().warning("[WARN] Failed to apply model " + modelId + ": " + e.getMessage());
        }
    }

    /**
     * Hide the base mob now that a model is on it. Normally the whole entity is
     * made invisible; with models.hide-base-java-only, it's hidden only from Java
     * players (who can render the 3D model) so Bedrock/mobile players keep seeing
     * the mapped vanilla mob instead of an invisible pokemon.
     */
    private void hideBaseAfterModel(Entity entity) {
        if (plugin.getConfig().getBoolean("models.hide-base-java-only", false)) {
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (!plugin.bedrock().isBedrock(p)) p.hideEntity(plugin, entity);
            }
        } else if (entity instanceof LivingEntity le) {
            le.setInvisible(true);
        }
    }

    private void applyBetterModel(Entity entity, String modelId) {
        try {
            Object renderer = unwrapOptional(mBmModel.invoke(null, modelId));
            if (renderer == null) {
                plugin.getLogger().warning("[WARN] BetterModel model not found: " + modelId);
                return;
            }
            for (Method m : renderer.getClass().getMethods()) {
                if (m.getName().equals("getOrCreate") && m.getParameterCount() == 1) {
                    m.invoke(renderer, entity);
                    hideBaseAfterModel(entity);
                    return;
                }
            }
            plugin.getLogger().warning("[WARN] BetterModel getOrCreate() not found for " + modelId);
        } catch (Exception e) {
            plugin.getLogger().warning("[WARN] Failed to apply BetterModel " + modelId + ": " + e.getMessage());
        }
    }

    /** If o is an Optional, return its value (or null); otherwise return o. */
    private Object unwrapOptional(Object o) {
        if (o == null) return null;
        if (o instanceof java.util.Optional<?> opt) return opt.orElse(null);
        return o;
    }

    /**
     * Spawns a rideable owned pokemon (no wild tag: ignored by capture,
     * battles and the despawn sweep). Caller is responsible for removal.
     */
    public LivingEntity spawnMount(PokemonSpecies species, PokemonInstance instance, Location loc) {
        EntityType base = baseEntityFor(species);
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
        applyModel(entity, plugin.models().blueprintFor(species));
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

    /** True when any supported model engine (ModelEngine or BetterModel) is active. */
    public boolean hasModelEngine() { return modelEngine || betterModel; }
}
