package dev.thanhtin.pokecraft.pokemon;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fossils, integrated into normal play - no commands or menus. Mining stone deep
 * underground has a small chance to unearth a fossil item; right-click the
 * fossil to revive it into its ancient pokemon. Which fossils exist and what
 * they revive into is config-driven (fossils.types).
 */
public class FossilManager implements Listener {

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keySpecies;

    public FossilManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keySpecies = new NamespacedKey(plugin, "fossil_species");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("fossils.enabled", true);
    }

    /** name -> species id pairs from config, keeping only species that exist. */
    private List<String[]> types() {
        List<String[]> out = new ArrayList<>();
        var sec = plugin.getConfig().getConfigurationSection("fossils.types");
        if (sec == null) return out;
        for (String name : sec.getKeys(false)) {
            String sp = sec.getString(name);
            if (sp != null && plugin.species().getSpecies(sp) != null) out.add(new String[]{name, sp});
        }
        return out;
    }

    private ItemStack fossilItem(String name, String speciesId) {
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();
        PokemonSpecies sp = plugin.species().getSpecies(speciesId);
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("An ancient fossil.", NamedTextColor.GRAY),
                Component.text("Right-click to revive"
                        + (sp != null ? " " + sp.name : ""), NamedTextColor.YELLOW)));
        meta.getPersistentDataContainer().set(keySpecies, PersistentDataType.STRING, speciesId);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isStone(Material m) {
        return switch (m) {
            case STONE, DEEPSLATE, TUFF, ANDESITE, DIORITE, GRANITE, CALCITE, DRIPSTONE_BLOCK -> true;
            default -> false;
        };
    }

    @EventHandler
    public void onMine(BlockBreakEvent e) {
        if (!enabled() || e.isCancelled()) return;
        Block b = e.getBlock();
        if (!isStone(b.getType())) return;
        if (b.getY() > plugin.getConfig().getInt("fossils.max-y", 0)) return;
        double chance = plugin.getConfig().getDouble("fossils.drop-chance", 0.003);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        List<String[]> types = types();
        if (types.isEmpty()) return;
        String[] pick = types.get(ThreadLocalRandom.current().nextInt(types.size()));
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), fossilItem(pick[0], pick[1]));
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_BONE_BLOCK_BREAK, 1f, 0.8f);
    }

    @EventHandler
    public void onRevive(PlayerInteractEvent e) {
        if (!enabled()) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String speciesId = item.getItemMeta().getPersistentDataContainer()
                .get(keySpecies, PersistentDataType.STRING);
        if (speciesId == null) return;
        e.setCancelled(true);
        Player player = e.getPlayer();
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null) {
            player.sendMessage(Component.text("Not during a battle.", NamedTextColor.RED));
            return;
        }
        PokemonSpecies species = plugin.species().getSpecies(speciesId);
        if (species == null) return;

        int level = plugin.getConfig().getInt("fossils.revive-level", 20);
        int shinyRate = plugin.getConfig().getInt("battle.shiny-rate", 4096);
        PokemonInstance instance = PokemonInstance.generate(species, level, shinyRate);
        instance.owner = player.getUniqueId();
        int slot = plugin.parties().get(player).add(instance);
        plugin.storage().save(instance, slot);
        plugin.storage().markCaught(player.getUniqueId(), instance.speciesId);

        ItemStack hand = player.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);

        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
        player.sendMessage(Component.text("The fossil was revived into "
                + instance.displayName(species) + (slot < 0 ? " (sent to the PC)!" : "!"),
                NamedTextColor.GREEN));
    }

    /** Public so an admin command / shop could hand out a fossil if desired. */
    public ItemStack give(String name) {
        for (String[] t : types()) {
            if (t[0].equalsIgnoreCase(name)) return fossilItem(t[0], t[1]);
        }
        return null;
    }

    public String key() { return keySpecies.getKey().toLowerCase(Locale.ROOT); }
}
