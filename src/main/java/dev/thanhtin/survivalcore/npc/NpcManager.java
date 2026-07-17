package dev.thanhtin.survivalcore.npc;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.storage.Database.Npc;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A lightweight NPC system (no Citizens dependency). NPCs are frozen, invulnerable
 * villagers tagged in their PDC and re-spawned from the database each start. They
 * are the roleplay surface for kits, jobs, and other services.
 */
public class NpcManager {

    private final SurvivalCore plugin;
    private final NamespacedKey keyId;
    private final NamespacedKey keyRole;
    private final Map<Long, UUID> spawned = new HashMap<>();

    public NpcManager(SurvivalCore plugin) {
        this.plugin = plugin;
        this.keyId = new NamespacedKey(plugin, "npc_id");
        this.keyRole = new NamespacedKey(plugin, "npc_role");
    }

    public NamespacedKey keyId() { return keyId; }
    public NamespacedKey keyRole() { return keyRole; }

    /** Remove any leftover tagged NPCs, then spawn all from the database. */
    public void spawnAll() {
        cleanupOrphans();
        spawned.clear();
        for (Npc npc : plugin.db().allNpcs()) spawn(npc);
        plugin.getLogger().info("[OK] Spawned " + spawned.size() + " NPC(s)");
    }

    public void despawnAll() {
        for (UUID id : spawned.values()) {
            Entity e = plugin.getServer().getEntity(id);
            if (e != null) e.remove();
        }
        spawned.clear();
    }

    private void cleanupOrphans() {
        plugin.getServer().getWorlds().forEach(w ->
                w.getEntities().forEach(e -> {
                    if (e.getPersistentDataContainer().has(keyId, PersistentDataType.LONG)) e.remove();
                }));
    }

    private void spawn(Npc npc) {
        if (npc == null) return;
        Location loc = npc.location();
        if (loc == null || loc.getWorld() == null) return;
        loc.getChunk().load();
        Villager v = loc.getWorld().spawn(loc, Villager.class, ent -> {
            ent.setAI(false);
            ent.setInvulnerable(true);
            ent.setSilent(true);
            ent.setCollidable(false);
            ent.setGravity(false);
            ent.setPersistent(false);
            ent.setRemoveWhenFarAway(false);
            ent.customName(Msg.legacy(npc.name()));
            ent.setCustomNameVisible(true);
            ent.getPersistentDataContainer().set(keyId, PersistentDataType.LONG, npc.id());
            ent.getPersistentDataContainer().set(keyRole, PersistentDataType.STRING, npc.role());
        });
        spawned.put(npc.id(), v.getUniqueId());
    }

    /** Respawn any NPC whose chunk just loaded but whose entity is gone. */
    public void respawnMissing() {
        for (Npc npc : plugin.db().allNpcs()) {
            UUID cur = spawned.get(npc.id());
            if (cur != null && plugin.getServer().getEntity(cur) != null) continue;
            if (npc.location() == null || npc.location().getWorld() == null) continue;
            if (!npc.location().isChunkLoaded()) continue;
            spawn(npc);
        }
    }

    public long create(String role, String name, Location loc) {
        long id = plugin.db().addNpc(role, name, loc);
        if (id > 0) spawn(plugin.db().allNpcs().stream()
                .filter(n -> n.id() == id).findFirst().orElse(null));
        return id;
    }

    public boolean remove(long id) {
        UUID ent = spawned.remove(id);
        if (ent != null) {
            Entity e = plugin.getServer().getEntity(ent);
            if (e != null) e.remove();
        }
        return plugin.db().removeNpc(id);
    }

    public String roleOf(Entity e) {
        return e.getPersistentDataContainer().get(keyRole, PersistentDataType.STRING);
    }

    public Long idOf(Entity e) {
        return e.getPersistentDataContainer().get(keyId, PersistentDataType.LONG);
    }

    public boolean isNpc(Entity e) {
        return e.getPersistentDataContainer().has(keyId, PersistentDataType.LONG);
    }
}
