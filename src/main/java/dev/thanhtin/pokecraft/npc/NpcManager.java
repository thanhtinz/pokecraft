package dev.thanhtin.pokecraft.npc;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Villager-based NPCs, no Citizens needed:
 * - HEALER: free Pokecenter nurse (heals HP/PP/status on right-click)
 * - VENDOR: opens the Pokemart shop GUI
 * - TRAINER: team battle with a money reward and a per-player cooldown
 *
 * The entity itself persists (Bukkit persistent entity + PDC marker); the
 * npcs DB table stores type/name/team keyed by the entity UUID.
 */
public class NpcManager implements Listener {

    public enum NpcType { HEALER, VENDOR, TRAINER, GYM, DAYCARE, PC, TUTOR }

    /** Serialized trainer data stored in the npcs table. */
    public static class TrainerData {
        public List<TeamEntry> team = new ArrayList<>();
        public long reward;
        /** Non-null on gym leaders: the badge id awarded when they are beaten. */
        public String badge;
    }

    public static class TeamEntry {
        public String species;
        public int level;
    }

    private final PokeCraftPlugin plugin;
    private final Gson gson = new Gson();
    public final NamespacedKey keyNpc;
    /** "npcUuid:playerUuid" -> last battle start millis */
    private final Map<String, Long> trainerCooldowns = new ConcurrentHashMap<>();

    public NpcManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyNpc = new NamespacedKey(plugin, "npc");
    }

    public boolean isNpc(Entity entity) {
        return entity.getPersistentDataContainer().has(keyNpc, PersistentDataType.BYTE);
    }

    /** Apply the configured ModelEngine blueprint for this NPC type, if any. */
    private void applyModel(LivingEntity entity, NpcType type) {
        String bp = plugin.getConfig().getString("models.npc-blueprints." + type.name());
        if (bp != null && !bp.isBlank()) plugin.entities().applyModel(entity, bp);
    }

    /** Re-apply NPC models when their chunks load (persistent villagers). */
    @EventHandler
    public void onEntitiesLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (Entity ent : e.getEntities()) {
            if (!(ent instanceof LivingEntity le) || !isNpc(ent)) continue;
            StorageManager.NpcRow row = plugin.storage().getNpc(ent.getUniqueId());
            if (row == null) continue;
            try {
                applyModel(le, NpcType.valueOf(row.type()));
            } catch (IllegalArgumentException ignore) {
            }
        }
    }

    /** Spawns and persists a new NPC at the given location. */
    public void create(Player creator, NpcType type, String name, int trainerLevel) {
        Location loc = creator.getLocation();
        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        entity.setSilent(true);
        entity.setInvulnerable(true);
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        if (entity instanceof Mob mob) {
            mob.setAware(false); // stands still
        }
        entity.customName(Component.text(name, switch (type) {
            case HEALER -> NamedTextColor.LIGHT_PURPLE;
            case VENDOR -> NamedTextColor.GREEN;
            case TRAINER -> NamedTextColor.RED;
            case GYM -> NamedTextColor.GOLD;
            case DAYCARE -> NamedTextColor.YELLOW;
            case PC -> NamedTextColor.AQUA;
            case TUTOR -> NamedTextColor.BLUE;
        }));
        entity.setCustomNameVisible(true);
        entity.getPersistentDataContainer().set(keyNpc, PersistentDataType.BYTE, (byte) 1);
        applyModel(entity, type);

        String data = "";
        if (type == NpcType.TRAINER) {
            TrainerData trainer = randomTrainerData(trainerLevel);
            data = gson.toJson(trainer);
        }
        plugin.storage().saveNpc(entity.getUniqueId(), type.name(), name, data);
        creator.sendMessage(Component.text("Created " + type.name().toLowerCase(Locale.ROOT)
                + " NPC \"" + name + "\".", NamedTextColor.GREEN));
    }

    /** Places a gym-leader NPC that awards its badge when beaten. */
    public void createGym(Player creator, String badgeId) {
        dev.thanhtin.pokecraft.gym.BadgeService.Gym gym =
                dev.thanhtin.pokecraft.gym.BadgeService.gym(badgeId);
        if (gym == null) {
            creator.sendMessage(Component.text("Unknown gym badge.", NamedTextColor.RED));
            return;
        }
        Location loc = creator.getLocation();
        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        entity.setSilent(true);
        entity.setInvulnerable(true);
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        if (entity instanceof Mob mob) mob.setAware(false);
        String name = "Leader " + gym.leader();
        entity.customName(Component.text(name, NamedTextColor.GOLD));
        entity.setCustomNameVisible(true);
        entity.getPersistentDataContainer().set(keyNpc, PersistentDataType.BYTE, (byte) 1);
        applyModel(entity, NpcType.GYM);

        TrainerData data = new TrainerData();
        data.badge = gym.badge();
        for (String species : gym.team()) {
            TeamEntry entry = new TeamEntry();
            entry.species = species;
            entry.level = gym.level();
            data.team.add(entry);
        }
        long perLevel = plugin.getConfig().getLong("npc.trainer-reward-per-level", 10);
        data.reward = data.team.stream().mapToLong(t -> t.level).sum() * perLevel;

        plugin.storage().saveNpc(entity.getUniqueId(), NpcType.GYM.name(), name, gson.toJson(data));
        creator.sendMessage(Component.text("Placed gym leader " + gym.leader() + " ("
                + gym.badgeName() + ").", NamedTextColor.GREEN));
    }

    /** Removes the NPC nearest to the player within 5 blocks. */
    public void removeNearest(Player player) {
        Entity nearest = null;
        double best = 25; // 5 blocks squared
        for (Entity e : player.getNearbyEntities(5, 5, 5)) {
            if (!isNpc(e)) continue;
            double d = e.getLocation().distanceSquared(player.getLocation());
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        if (nearest == null) {
            player.sendMessage(Component.text("No NPC within 5 blocks.", NamedTextColor.RED));
            return;
        }
        plugin.storage().deleteNpc(nearest.getUniqueId());
        nearest.remove();
        player.sendMessage(Component.text("NPC removed.", NamedTextColor.GREEN));
    }

    private TrainerData randomTrainerData(int level) {
        TrainerData data = new TrainerData();
        List<PokemonSpecies> pool = new ArrayList<>();
        for (PokemonSpecies s : plugin.species().all()) {
            if (s.spawn != null) pool.add(s);
        }
        if (pool.isEmpty()) pool.addAll(plugin.species().all());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int size = 2 + rnd.nextInt(3); // 2-4 pokemon
        for (int i = 0; i < size; i++) {
            PokemonSpecies species = pool.get(rnd.nextInt(pool.size()));
            int memberLevel = Math.max(2, level + rnd.nextInt(5) - 2);
            TeamEntry entry = new TeamEntry();
            entry.species = chainEvolve(species, memberLevel);
            entry.level = memberLevel;
            data.team.add(entry);
        }
        long perLevel = plugin.getConfig().getLong("npc.trainer-reward-per-level", 10);
        data.reward = data.team.stream().mapToLong(t -> t.level).sum() * perLevel;
        return data;
    }

    /** Follow level evolutions so a Lv.40 team member is the evolved form. */
    private String chainEvolve(PokemonSpecies species, int level) {
        PokemonSpecies current = species;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (PokemonSpecies.Evolution evo : current.allEvolutions()) {
                if (evo.item != null || evo.to == null || level < evo.level) continue;
                PokemonSpecies next = plugin.species().getSpecies(evo.to);
                if (next != null) {
                    current = next;
                    changed = true;
                }
                break;
            }
        }
        return current.id;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!isNpc(e.getRightClicked())) return;
        e.setCancelled(true);
        Player player = e.getPlayer();
        StorageManager.NpcRow row = plugin.storage().getNpc(e.getRightClicked().getUniqueId());
        if (row == null) { // orphaned entity, DB row was deleted
            e.getRightClicked().remove();
            return;
        }
        NpcType type;
        try { type = NpcType.valueOf(row.type()); }
        catch (IllegalArgumentException ex) { return; }

        switch (type) {
            case HEALER -> heal(player, row.name());
            case VENDOR -> {
                if (inBattle(player)) return;
                plugin.shop().open(player);
            }
            case TRAINER, GYM -> startTrainer(player, e.getRightClicked(), row);
            case DAYCARE -> {
                if (inBattle(player)) return;
                plugin.daycareUi().open(player);
            }
            case PC -> {
                if (inBattle(player)) return;
                plugin.pcUi().open(player, 0);
            }
            case TUTOR -> {
                if (inBattle(player)) return;
                plugin.partyUi().open(player); // pick a pokemon -> its Move Tutor button
            }
        }
    }

    private void heal(Player player, String nurseName) {
        if (inBattle(player)) return;
        for (PokemonInstance p : plugin.parties().get(player).party()) {
            PokemonSpecies s = plugin.species().getSpecies(p.speciesId);
            if (s != null) p.heal(s);
        }
        plugin.parties().saveParty(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.4f);
        player.sendMessage(Component.text(nurseName
                + ": Your pokemon are fully healed. We hope to see you again!",
                NamedTextColor.LIGHT_PURPLE));
    }

    private void startTrainer(Player player, Entity npcEntity, StorageManager.NpcRow row) {
        if (inBattle(player)) return;
        long cooldownMillis = plugin.getConfig().getLong("npc.trainer-cooldown-minutes", 30) * 60_000L;
        String key = npcEntity.getUniqueId() + ":" + player.getUniqueId();
        Long last = trainerCooldowns.get(key);
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldownMillis) {
            long minutesLeft = (cooldownMillis - (now - last)) / 60_000 + 1;
            player.sendMessage(Component.text(row.name() + ": Come back in " + minutesLeft
                    + " minute(s) for a rematch!", NamedTextColor.RED));
            return;
        }
        TrainerData data;
        try {
            data = gson.fromJson(row.data(), new TypeToken<TrainerData>() {}.getType());
        } catch (Exception ex) {
            data = null;
        }
        if (data == null || data.team == null || data.team.isEmpty()) {
            player.sendMessage(Component.text(row.name() + " has no team configured.", NamedTextColor.RED));
            return;
        }
        List<PokemonInstance> team = new ArrayList<>();
        int shinyRate = plugin.getConfig().getInt("battle.shiny-rate", 4096);
        for (TeamEntry entry : data.team) {
            PokemonSpecies species = plugin.species().getSpecies(entry.species);
            if (species == null) continue;
            team.add(PokemonInstance.generate(species, entry.level, shinyRate));
        }
        if (team.isEmpty()) return;
        trainerCooldowns.put(key, now);
        plugin.battles().startTrainerBattle(player, npcEntity, team, row.name(), data.reward, data.badge);
    }

    private boolean inBattle(Player player) {
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null) {
            player.sendMessage(Component.text("Finish your battle first.", NamedTextColor.RED));
            return true;
        }
        return false;
    }

    /** NPCs never take damage (backup for non-invulnerable damage paths). */
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (isNpc(e.getEntity())) e.setCancelled(true);
    }
}
