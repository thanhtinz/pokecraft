package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.battle.Battle;
import dev.thanhtin.pokecraft.battle.MoveData;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Chest-based battle menu. Works for Java and Bedrock players
 * (Geyser translates chest GUIs to Bedrock forms automatically).
 * When Floodgate is present, BedrockSupport can serve a native form instead.
 */
public class BattleGui implements Listener {
    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyMove;
    private final NamespacedKey keyAction;

    public BattleGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyMove = new NamespacedKey(plugin, "move");
        this.keyAction = new NamespacedKey(plugin, "action");
    }

    public void open(Player player, Battle battle) {
        if (plugin.bedrock().tryOpenBattleForm(player, battle)) return;

        PokemonSpecies mySpecies = plugin.species().getSpecies(battle.playerPokemon.speciesId);
        PokemonSpecies wildSpecies = plugin.species().getSpecies(battle.wildPokemon.speciesId);
        PokemonInstance mine = battle.playerPokemon;
        PokemonInstance wild = battle.wildPokemon;

        Inventory inv = plugin.getServer().createInventory(null, 9,
                Component.text(mine.displayName(mySpecies) + " " + mine.currentHp + "/" + mine.maxHp(mySpecies)
                        + "  vs  " + wild.displayName(wildSpecies) + " " + wild.currentHp + "/" + wild.maxHp(wildSpecies)));

        List<String> moves = mine.moves;
        for (int i = 0; i < Math.min(4, moves.size()); i++) {
            MoveData move = plugin.species().getMove(moves.get(i));
            if (move == null) continue;
            ItemStack item = new ItemStack(materialFor(move));
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(move.name, NamedTextColor.AQUA));
            meta.lore(List.of(
                    Component.text(move.type + " / " + move.category, NamedTextColor.GRAY),
                    Component.text("Power " + move.power + "  Acc " + move.accuracy, NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(keyMove, PersistentDataType.STRING, move.id);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        ItemStack run = new ItemStack(Material.BARRIER);
        ItemMeta runMeta = run.getItemMeta();
        runMeta.displayName(Component.text("Run", NamedTextColor.RED));
        runMeta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "run");
        run.setItemMeta(runMeta);
        inv.setItem(8, run);

        player.openInventory(inv);
    }

    private Material materialFor(MoveData move) {
        return switch (move.type) {
            case FIRE -> Material.BLAZE_POWDER;
            case WATER -> Material.HEART_OF_THE_SEA;
            case GRASS -> Material.OAK_SAPLING;
            case ELECTRIC -> Material.GLOWSTONE_DUST;
            case FLYING -> Material.FEATHER;
            case DARK -> Material.COAL;
            default -> Material.SLIME_BALL;
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        Battle battle = plugin.battles().get(player);
        if (battle == null) return;
        e.setCancelled(true);
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String moveId = item.getItemMeta().getPersistentDataContainer()
                .get(keyMove, PersistentDataType.STRING);
        String action = item.getItemMeta().getPersistentDataContainer()
                .get(keyAction, PersistentDataType.STRING);
        if (moveId == null && action == null) return;

        player.closeInventory();
        if ("run".equals(action)) plugin.battles().flee(player);
        else if (moveId != null) plugin.battles().useMove(player, moveId);
    }
}
