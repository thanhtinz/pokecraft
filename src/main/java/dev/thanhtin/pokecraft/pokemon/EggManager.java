package dev.thanhtin.pokecraft.pokemon;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pokemon eggs that hatch by walking. All egg data lives inside the egg
 * ItemStack (species, IVs, shininess, steps left), so an egg survives being
 * stored, dropped or traded as an item without any database rows. As the
 * holder walks, the first egg in their inventory loses "steps"; at zero it
 * hatches into a level-{@code egg.hatch-level} pokemon.
 */
public class EggManager implements Listener {

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyEgg;
    private final NamespacedKey keySpecies;
    private final NamespacedKey keyIvs;
    private final NamespacedKey keyShiny;
    private final NamespacedKey keySteps;
    private final Map<UUID, Location> lastLoc = new ConcurrentHashMap<>();
    private final Map<UUID, Double> walked = new ConcurrentHashMap<>();

    public EggManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyEgg = new NamespacedKey(plugin, "egg");
        this.keySpecies = new NamespacedKey(plugin, "egg_species");
        this.keyIvs = new NamespacedKey(plugin, "egg_ivs");
        this.keyShiny = new NamespacedKey(plugin, "egg_shiny");
        this.keySteps = new NamespacedKey(plugin, "egg_steps");
    }

    private int totalSteps() {
        return plugin.getConfig().getInt("egg.steps", 300);
    }

    private int hatchLevel() {
        return plugin.getConfig().getInt("egg.hatch-level", 1);
    }

    /** Build an egg item that will hatch into {@code speciesId}. */
    public ItemStack createEgg(String speciesId, int[] ivs, boolean shiny) {
        ItemStack egg = new ItemStack(Material.TURTLE_EGG);
        ItemMeta meta = egg.getItemMeta();
        PokemonSpecies species = plugin.species().getSpecies(speciesId);
        String name = species != null ? species.name : speciesId;
        meta.displayName(Component.text("Mystery Egg" + (shiny ? " ✦" : ""),
                shiny ? NamedTextColor.GOLD : NamedTextColor.YELLOW));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyEgg, PersistentDataType.INTEGER, 1);
        pdc.set(keySpecies, PersistentDataType.STRING, speciesId);
        pdc.set(keyIvs, PersistentDataType.STRING, ivsToString(ivs));
        pdc.set(keyShiny, PersistentDataType.INTEGER, shiny ? 1 : 0);
        pdc.set(keySteps, PersistentDataType.INTEGER, totalSteps());
        meta.lore(List.of(
                Component.text("Carry it and walk to hatch.", NamedTextColor.GRAY),
                Component.text(totalSteps() + " steps to go", NamedTextColor.GRAY)));
        egg.setItemMeta(meta);
        return egg;
    }

    /** Give the player an egg, dropping it at their feet if the inventory is full. */
    public void giveEgg(Player player, String speciesId, int[] ivs, boolean shiny) {
        ItemStack egg = createEgg(speciesId, ivs, shiny);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(egg);
        for (ItemStack left : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        player.sendMessage(Component.text("You received an Egg! Walk around to hatch it.",
                NamedTextColor.LIGHT_PURPLE));
    }

    private boolean isEgg(ItemStack item) {
        return item != null && item.getType() == Material.TURTLE_EGG && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                        .has(keyEgg, PersistentDataType.INTEGER);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        Location to = e.getTo();
        Location prev = lastLoc.get(player.getUniqueId());
        lastLoc.put(player.getUniqueId(), to);
        if (prev == null || prev.getWorld() != to.getWorld()) return;
        double dist = prev.distance(to);
        if (dist <= 0 || dist > 20) return; // ignore teleports
        double acc = walked.merge(player.getUniqueId(), dist, Double::sum);
        if (acc < 1.0) return;
        int blocks = (int) acc;
        walked.put(player.getUniqueId(), acc - blocks);
        stepEgg(player, blocks);
    }

    private void stepEgg(Player player, int blocks) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isEgg(item)) continue;
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            int steps = pdc.getOrDefault(keySteps, PersistentDataType.INTEGER, totalSteps()) - blocks;
            if (steps <= 0) {
                hatch(player, item, i);
            } else {
                pdc.set(keySteps, PersistentDataType.INTEGER, steps);
                boolean shiny = pdc.getOrDefault(keyShiny, PersistentDataType.INTEGER, 0) == 1;
                meta.lore(List.of(
                        Component.text("Carry it and walk to hatch.", NamedTextColor.GRAY),
                        Component.text(steps + " steps to go", NamedTextColor.GRAY)));
                item.setItemMeta(meta);
                player.getInventory().setItem(i, item);
            }
            return; // only the first egg advances per step, like the games
        }
    }

    private void hatch(Player player, ItemStack egg, int slot) {
        PersistentDataContainer pdc = egg.getItemMeta().getPersistentDataContainer();
        String speciesId = pdc.get(keySpecies, PersistentDataType.STRING);
        PokemonSpecies species = speciesId == null ? null : plugin.species().getSpecies(speciesId);
        if (species == null) { // corrupt egg - just remove it
            consumeOne(player, slot);
            return;
        }
        boolean shiny = pdc.getOrDefault(keyShiny, PersistentDataType.INTEGER, 0) == 1;
        int[] ivs = ivsFromString(pdc.get(keyIvs, PersistentDataType.STRING));

        PokemonInstance baby = PokemonInstance.generate(species, hatchLevel(), 0);
        baby.shiny = shiny;
        if (ivs != null) baby.ivs = ivs;
        baby.owner = player.getUniqueId();
        baby.currentHp = baby.maxHp(species);

        consumeOne(player, slot);
        int partySlot = plugin.parties().get(player).add(baby);
        plugin.storage().save(baby, partySlot);
        plugin.storage().markCaught(player.getUniqueId(), baby.speciesId);
        plugin.parties().saveParty(player.getUniqueId());

        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1f, 1.2f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.sendMessage(Component.text("Your egg hatched into "
                + baby.displayName(species) + " Lv." + baby.level
                + (partySlot < 0 ? " (sent to PC)!" : "!"), NamedTextColor.LIGHT_PURPLE));
    }

    private void consumeOne(Player player, int slot) {
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null) return;
        if (item.getAmount() <= 1) player.getInventory().setItem(slot, null);
        else { item.setAmount(item.getAmount() - 1); player.getInventory().setItem(slot, item); }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastLoc.remove(e.getPlayer().getUniqueId());
        walked.remove(e.getPlayer().getUniqueId());
    }

    private String ivsToString(int[] ivs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ivs.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(ivs[i]);
        }
        return sb.toString();
    }

    private int[] ivsFromString(String s) {
        if (s == null || s.isBlank()) return null;
        String[] parts = s.split(",");
        if (parts.length != 6) return null;
        int[] ivs = new int[6];
        try {
            for (int i = 0; i < 6; i++) ivs[i] = Integer.parseInt(parts[i].trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        return ivs;
    }
}
