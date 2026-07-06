package dev.thanhtin.pokecraft.party;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.storage.StorageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager implements Listener {
    private final PokeCraftPlugin plugin;
    private final Map<UUID, PlayerParty> cache = new ConcurrentHashMap<>();

    public PartyManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerParty get(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), this::load);
    }

    private PlayerParty load(UUID owner) {
        PlayerParty party = new PlayerParty();
        for (StorageManager.StoredPokemon sp : plugin.storage().loadAll(owner)) {
            if (sp.slot() == StorageManager.SLOT_DAYCARE) continue; // held by the daycare
            if (sp.slot() >= 0 && sp.slot() < PlayerParty.SIZE) party.set(sp.slot(), sp.pokemon());
            else party.pc().add(sp.pokemon());
        }
        return party;
    }

    public void saveParty(UUID owner) {
        PlayerParty party = cache.get(owner);
        if (party == null) return;
        PokemonInstance[] slots = party.rawSlots();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) plugin.storage().save(slots[i], i);
        }
        for (PokemonInstance p : party.pc()) plugin.storage().save(p, -1);
    }

    public void saveAll() {
        for (UUID owner : cache.keySet()) saveParty(owner);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // async pre-load, storage calls are synchronized
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> cache.computeIfAbsent(e.getPlayer().getUniqueId(), this::load));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            saveParty(id);
            cache.remove(id);
        });
    }
}
