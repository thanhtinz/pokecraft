package dev.thanhtin.pokecraft.integration;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.storage.StorageManager.TopEntry;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Leaderboard holograms via DecentHolograms (soft-depend). An admin places an
 * auto-updating top-10 hologram with {@code /poke hologram add <money|caught|wins>}.
 * Definitions persist in the meta store and are recreated + refreshed on a timer.
 * Only ever instantiated when DecentHolograms is installed.
 */
public class HologramManager {

    private static class Def {
        String name, world, type, title;
        double x, y, z;
    }

    private static final String META_KEY = "holograms";
    private static final int TOP = 10;

    private final PokeCraftPlugin plugin;
    private final Gson gson = new Gson();
    private final List<Def> defs = new ArrayList<>();

    public HologramManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /** Recreate saved holograms and start the refresh loop. Call on enable. */
    public void start() {
        for (Def d : defs) createOrUpdate(d, true);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, 600L, 1200L); // ~1 min
    }

    public void add(Player player, String type, String title) {
        type = normalize(type);
        if (type == null) {
            player.sendMessage(Component.text("Type must be money, caught or wins.", NamedTextColor.RED));
            return;
        }
        Def d = new Def();
        d.name = "pokecraft_" + type + "_" + System.identityHashCode(player) + "_" + defs.size();
        Location loc = player.getLocation();
        d.world = loc.getWorld().getName();
        d.x = loc.getX(); d.y = loc.getY() + 2.5; d.z = loc.getZ();
        d.type = type;
        d.title = (title == null || title.isBlank()) ? defaultTitle(type) : title;
        defs.add(d);
        save();
        createOrUpdate(d, false);
        player.sendMessage(Component.text("Leaderboard hologram placed (" + type + ").",
                NamedTextColor.GREEN));
    }

    public void removeNearest(Player player) {
        Def best = null;
        double bestDist = 16; // within 4 blocks
        for (Def d : defs) {
            if (!d.world.equals(player.getWorld().getName())) continue;
            double dist = player.getLocation().distanceSquared(
                    new Location(player.getWorld(), d.x, d.y, d.z));
            if (dist < bestDist) { bestDist = dist; best = d; }
        }
        if (best == null) {
            player.sendMessage(Component.text("No PokeCraft hologram nearby.", NamedTextColor.RED));
            return;
        }
        try { DHAPI.removeHologram(best.name); } catch (Throwable ignored) {}
        defs.remove(best);
        save();
        player.sendMessage(Component.text("Hologram removed.", NamedTextColor.GREEN));
    }

    private void createOrUpdate(Def d, boolean removeFirst) {
        try {
            if (removeFirst) {
                try { DHAPI.removeHologram(d.name); } catch (Throwable ignored) {}
            }
            Location loc = new Location(plugin.getServer().getWorld(d.world), d.x, d.y, d.z);
            if (loc.getWorld() == null) return;
            DHAPI.createHologram(d.name, loc, lines(d));
        } catch (Throwable t) {
            plugin.getLogger().warning("[WARN] Hologram create failed: " + t.getMessage());
        }
    }

    private void refreshAll() {
        for (Def d : defs) {
            try {
                Hologram h = DHAPI.getHologram(d.name);
                if (h != null) DHAPI.setHologramLines(h, lines(d));
                else createOrUpdate(d, false);
            } catch (Throwable ignored) {
            }
        }
    }

    private List<String> lines(Def d) {
        List<String> out = new ArrayList<>();
        out.add("&6&l" + d.title);
        out.add("&7&m----------------");
        List<TopEntry> top = plugin.storage().top(column(d.type), TOP);
        int rank = 1;
        for (TopEntry e : top) {
            String value = d.type.equals("money")
                    ? plugin.economy().format(e.value()) : String.valueOf(e.value());
            out.add("&e#" + rank + " &f" + e.name() + " &7- &a" + value);
            rank++;
        }
        if (top.isEmpty()) out.add("&7(no data yet)");
        return out;
    }

    private String column(String type) {
        return switch (type) {
            case "money" -> "balance";
            case "wins" -> "wild_wins";
            default -> "caught";
        };
    }

    private String defaultTitle(String type) {
        return switch (type) {
            case "money" -> "Richest Trainers";
            case "wins" -> "Top Battlers";
            default -> "Top Catchers";
        };
    }

    private String normalize(String type) {
        if (type == null) return null;
        type = type.toLowerCase();
        return switch (type) {
            case "money", "balance", "rich" -> "money";
            case "wins", "battles", "wild_wins" -> "wins";
            case "caught", "catches", "dex" -> "caught";
            default -> null;
        };
    }

    private void load() {
        String json = plugin.storage().getMeta(META_KEY, null);
        if (json == null || json.isBlank()) return;
        try {
            List<Def> saved = gson.fromJson(json, new TypeToken<List<Def>>() {}.getType());
            if (saved != null) defs.addAll(saved);
        } catch (Throwable ignored) {
        }
    }

    private void save() {
        plugin.storage().setMeta(META_KEY, gson.toJson(defs));
    }
}
