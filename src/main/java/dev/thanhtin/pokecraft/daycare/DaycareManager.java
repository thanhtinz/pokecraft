package dev.thanhtin.pokecraft.daycare;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.party.PlayerParty;
import dev.thanhtin.pokecraft.pokemon.Breeding;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Daycare: deposit up to 2 pokemon; they gain EXP passively while stored.
 * Two pokemon of the same evolution family may produce a level-1 baby
 * (base form, inherits some IVs, boosted shiny odds).
 */
public class DaycareManager {
    private final PokeCraftPlugin plugin;
    private BukkitRunnable task;

    public DaycareManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long interval = plugin.getConfig().getLong("daycare.breed-check-seconds", 60) * 20L;
        task = new BukkitRunnable() {
            @Override public void run() { breedingSweep(); }
        };
        task.runTaskTimer(plugin, interval, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private int maxSlots() {
        return Math.max(1, plugin.getConfig().getInt("daycare.max-slots", 2));
    }

    public void deposit(Player player, int partySlot) {
        PlayerParty party = plugin.parties().get(player);
        PokemonInstance p = party.get(partySlot);
        if (p == null) {
            player.sendMessage(Component.text("No pokemon in party slot " + (partySlot + 1) + ".",
                    NamedTextColor.RED));
            return;
        }
        if (party.partySize() <= 1) {
            player.sendMessage(Component.text("You must keep at least one pokemon in your party.",
                    NamedTextColor.RED));
            return;
        }
        List<StorageManager.DaycareEntry> entries = plugin.storage().daycareOf(player.getUniqueId());
        if (entries.size() >= maxSlots()) {
            player.sendMessage(Component.text("The daycare is full (" + maxSlots()
                    + " slots). Withdraw one first.", NamedTextColor.RED));
            return;
        }
        party.removeFromParty(partySlot);
        plugin.storage().save(p, StorageManager.SLOT_DAYCARE);
        plugin.storage().addDaycare(p.uuid, player.getUniqueId(), System.currentTimeMillis());
        plugin.parties().saveParty(player.getUniqueId());
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        player.sendMessage(Component.text(p.displayName(species)
                + " was left at the daycare. It will gain EXP over time.", NamedTextColor.GREEN));
    }

    public void withdraw(Player player, int daycareIndex) {
        List<StorageManager.DaycareEntry> entries = plugin.storage().daycareOf(player.getUniqueId());
        if (daycareIndex < 0 || daycareIndex >= entries.size()) {
            player.sendMessage(Component.text("No daycare pokemon #" + (daycareIndex + 1)
                    + ". See /poke daycare status.", NamedTextColor.RED));
            return;
        }
        StorageManager.DaycareEntry entry = entries.get(daycareIndex);
        PokemonInstance p = plugin.storage().loadPokemon(entry.pokemonUuid());
        if (p == null) {
            plugin.storage().removeDaycare(entry.pokemonUuid());
            player.sendMessage(Component.text("That pokemon could not be loaded.", NamedTextColor.RED));
            return;
        }
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        long minutes = Math.max(0, (System.currentTimeMillis() - entry.depositedAt()) / 60_000);
        long expPerMinute = plugin.getConfig().getLong("daycare.exp-per-minute", 20);
        int levels = 0;
        if (species != null && minutes > 0) {
            levels = p.addExp(species, minutes * expPerMinute);
        }
        plugin.storage().removeDaycare(entry.pokemonUuid());
        int slot = plugin.parties().get(player).add(p);
        plugin.storage().save(p, slot);
        plugin.parties().saveParty(player.getUniqueId());
        String name = species != null ? p.displayName(species) : p.speciesId;
        player.sendMessage(Component.text(name + " came back after " + minutes + "min"
                + (levels > 0 ? " and grew " + levels + " level(s) to Lv." + p.level : "")
                + (slot < 0 ? " (sent to PC)" : "") + ".", NamedTextColor.GREEN));
    }

    public void status(Player player) {
        List<StorageManager.DaycareEntry> entries = plugin.storage().daycareOf(player.getUniqueId());
        if (entries.isEmpty()) {
            player.sendMessage(Component.text("The daycare is empty. /poke daycare deposit <slot 1-6>",
                    NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text("Daycare (" + entries.size() + "/" + maxSlots() + "):",
                NamedTextColor.YELLOW));
        for (int i = 0; i < entries.size(); i++) {
            StorageManager.DaycareEntry entry = entries.get(i);
            PokemonInstance p = plugin.storage().loadPokemon(entry.pokemonUuid());
            long minutes = Math.max(0, (System.currentTimeMillis() - entry.depositedAt()) / 60_000);
            String name = "?";
            if (p != null) {
                PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
                name = (species != null ? p.displayName(species) : p.speciesId) + " Lv." + p.level;
            }
            player.sendMessage(Component.text("  " + (i + 1) + ". " + name + " - " + minutes
                    + "min (withdraw: /poke daycare withdraw " + (i + 1) + ")", NamedTextColor.GRAY));
        }
        if (entries.size() >= 2) {
            player.sendMessage(Component.text("  Two compatible pokemon may produce a baby - check back!",
                    NamedTextColor.LIGHT_PURPLE));
        }
    }

    /** Every interval, online owners with 2 compatible daycare pokemon may get a baby. */
    private void breedingSweep() {
        double chance = plugin.getConfig().getDouble("daycare.breed-chance", 0.15);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            List<StorageManager.DaycareEntry> entries = plugin.storage().daycareOf(player.getUniqueId());
            if (entries.size() < 2) continue;
            PokemonInstance a = plugin.storage().loadPokemon(entries.get(0).pokemonUuid());
            PokemonInstance b = plugin.storage().loadPokemon(entries.get(1).pokemonUuid());
            if (a == null || b == null) continue;
            Map<String, String> parents = plugin.species().childToParent();
            if (!Breeding.compatible(a.speciesId, b.speciesId, parents)) continue;
            if (ThreadLocalRandom.current().nextDouble() >= chance) continue;

            PokemonSpecies babySpecies = plugin.species().getSpecies(
                    Breeding.baseForm(a.speciesId, parents));
            if (babySpecies == null) continue;
            int shinyRate = plugin.getConfig().getInt("daycare.breed-shiny-rate", 2048);
            PokemonInstance baby = PokemonInstance.generate(babySpecies, 1, shinyRate);
            baby.ivs = Breeding.childIvs(a.ivs, b.ivs, ThreadLocalRandom.current());
            baby.currentHp = baby.maxHp(babySpecies);
            baby.owner = player.getUniqueId();
            int slot = plugin.parties().get(player).add(baby);
            plugin.storage().save(baby, slot);
            plugin.storage().markCaught(player.getUniqueId(), baby.speciesId);
            plugin.parties().saveParty(player.getUniqueId());
            player.sendMessage(Component.text("Surprise! The daycare found an egg - it hatched into "
                    + baby.displayName(babySpecies) + " Lv.1" + (slot < 0 ? " (sent to PC)" : "!"),
                    NamedTextColor.LIGHT_PURPLE));
        }
    }
}
