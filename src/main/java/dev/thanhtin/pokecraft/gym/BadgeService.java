package dev.thanhtin.pokecraft.gym;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The eight Kanto gym badges. Badges a player owns are stored as a
 * comma-separated list in the meta table (key {@code badges:<uuid>}), so no
 * schema change is needed. Beating a gym-leader NPC awards its badge.
 */
public class BadgeService {

    /** One gym: its badge, leader, themed team (species ids present in the dex) and level. */
    public record Gym(String badge, String badgeName, String leader, Material icon,
                      int level, List<String> team) {}

    /** Ordered Kanto gym progression. Team species all exist in the bundled dex. */
    public static final List<Gym> GYMS = List.of(
            new Gym("boulder", "Boulder Badge", "Brock", Material.STONE, 12,
                    List.of("geodude", "onix")),
            new Gym("cascade", "Cascade Badge", "Misty", Material.PRISMARINE_SHARD, 18,
                    List.of("staryu", "starmie")),
            new Gym("thunder", "Thunder Badge", "Lt. Surge", Material.LIGHTNING_ROD, 24,
                    List.of("voltorb", "pikachu", "raichu")),
            new Gym("rainbow", "Rainbow Badge", "Erika", Material.OXEYE_DAISY, 29,
                    List.of("tangela", "gloom", "vileplume")),
            new Gym("soul", "Soul Badge", "Koga", Material.FERMENTED_SPIDER_EYE, 37,
                    List.of("koffing", "muk", "weezing")),
            new Gym("marsh", "Marsh Badge", "Sabrina", Material.AMETHYST_SHARD, 43,
                    List.of("kadabra", "mr_mime", "alakazam")),
            new Gym("volcano", "Volcano Badge", "Blaine", Material.BLAZE_POWDER, 47,
                    List.of("growlithe", "ponyta", "rapidash", "arcanine")),
            new Gym("earth", "Earth Badge", "Giovanni", Material.DIRT, 53,
                    List.of("rhyhorn", "dugtrio", "nidoqueen", "nidoking")));

    private final PokeCraftPlugin plugin;

    public BadgeService(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public static Gym gym(String badgeId) {
        for (Gym g : GYMS) if (g.badge().equals(badgeId)) return g;
        return null;
    }

    private String key(UUID player) {
        return "badges:" + player;
    }

    public Set<String> badgesOf(UUID player) {
        Set<String> out = new LinkedHashSet<>();
        String raw = plugin.storage().getMeta(key(player), "");
        if (raw != null && !raw.isBlank()) {
            for (String b : raw.split(",")) if (!b.isBlank()) out.add(b.trim());
        }
        return out;
    }

    public boolean has(UUID player, String badgeId) {
        return badgesOf(player).contains(badgeId);
    }

    public int count(UUID player) {
        return badgesOf(player).size();
    }

    /** Award a badge; announces and returns true only if it was newly earned. */
    public boolean award(Player player, String badgeId) {
        Gym g = gym(badgeId);
        if (g == null) return false;
        Set<String> owned = badgesOf(player.getUniqueId());
        if (!owned.add(badgeId)) return false;
        plugin.storage().setMeta(key(player.getUniqueId()), String.join(",", owned));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        player.sendMessage(Component.text("You earned the " + g.badgeName() + "! ("
                + owned.size() + "/8 badges)", NamedTextColor.GOLD));
        return true;
    }
}
