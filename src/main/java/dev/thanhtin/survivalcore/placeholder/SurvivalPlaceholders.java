package dev.thanhtin.survivalcore.placeholder;

import dev.thanhtin.survivalcore.SurvivalCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Exposes SurvivalCore data to other plugins via PlaceholderAPI, e.g.
 * %survivalcore_balance%, %survivalcore_rank%, %survivalcore_bounty%.
 * Only registered when PlaceholderAPI is installed.
 */
public class SurvivalPlaceholders extends PlaceholderExpansion {

    private final SurvivalCore plugin;

    public SurvivalPlaceholders(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "survivalcore"; }
    @Override public String getAuthor() { return "thanhtinz"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        switch (params.toLowerCase()) {
            case "balance":
                return plugin.economy().format(plugin.economy().balance(player.getUniqueId()));
            case "balance_raw":
                return String.valueOf(plugin.economy().balance(player.getUniqueId()));
            case "rank":
                return strip(plugin.ranks().prefix(player.getUniqueId()));
            case "rank_prefix":
                return plugin.ranks().prefix(player.getUniqueId());
            case "bounty":
                return plugin.economy().format(plugin.db().getBounty(player.getUniqueId()));
            case "claims":
                return String.valueOf(plugin.db().claimCount(player.getUniqueId()));
            case "jobs":
                return String.valueOf(plugin.db().jobCount(player.getUniqueId()));
            default:
                break;
        }
        return null;
    }

    private String strip(String s) { return s == null ? "" : s.replaceAll("[&§][0-9a-fk-or]", ""); }
}
