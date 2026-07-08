package dev.thanhtin.pokecraft.integration;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.storage.StorageManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * PlaceholderAPI expansion exposing PokeCraft data. Registered only when
 * PlaceholderAPI is installed. Use %pokecraft_&lt;id&gt;% in any PAPI-aware
 * plugin (scoreboard, tab, chat, holograms):
 * balance, balance_formatted, caught, wild_wins, pvp_wins, dex_seen,
 * dex_caught, dex_total, badges, rank_points, guild, streak, lead, lead_level.
 */
public class PokeCraftPlaceholders extends PlaceholderExpansion {

    private final PokeCraftPlugin plugin;

    public PokeCraftPlaceholders(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "pokecraft"; }
    @Override public String getAuthor() { return "thanhtinz"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; } // survive PAPI reloads

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        UUID id = player.getUniqueId();
        StorageManager st = plugin.storage();
        switch (params.toLowerCase()) {
            case "balance": return String.valueOf(plugin.economy().balance(id));
            case "balance_formatted": return plugin.economy().format(plugin.economy().balance(id));
            case "caught": return String.valueOf(st.getStat(id, "caught"));
            case "wild_wins": return String.valueOf(st.getStat(id, "wild_wins"));
            case "pvp_wins": return String.valueOf(st.getStat(id, "pvp_wins"));
            case "dex_seen": return String.valueOf(st.pokedexOf(id).size());
            case "dex_caught": return String.valueOf(countCaught(st.pokedexOf(id)));
            case "dex_total": return String.valueOf(plugin.species().all().size());
            case "badges": return String.valueOf(plugin.badges().badgesOf(id).size());
            case "rank_points": return String.valueOf(st.getRank(id).points());
            case "streak": return String.valueOf(st.getDaily(id).streak());
            case "guild": return guildName(id);
            case "lead": return leadName(player);
            case "lead_level": return leadLevel(player);
            default: return null;
        }
    }

    private int countCaught(Map<String, Boolean> dex) {
        int n = 0;
        for (Boolean caught : dex.values()) if (Boolean.TRUE.equals(caught)) n++;
        return n;
    }

    private String guildName(UUID id) {
        int gid = plugin.storage().guildOf(id);
        if (gid <= 0) return "";
        StorageManager.GuildRow g = plugin.storage().getGuild(gid);
        return g == null ? "" : g.name();
    }

    private PokemonInstance lead(OfflinePlayer player) {
        Player online = player.getPlayer();
        if (online == null) return null;
        return plugin.parties().get(online).firstAlive();
    }

    private String leadName(OfflinePlayer player) {
        PokemonInstance lead = lead(player);
        if (lead == null) return "";
        PokemonSpecies species = plugin.species().getSpecies(lead.speciesId);
        return lead.displayName(species);
    }

    private String leadLevel(OfflinePlayer player) {
        PokemonInstance lead = lead(player);
        return lead == null ? "" : String.valueOf(lead.level);
    }
}
