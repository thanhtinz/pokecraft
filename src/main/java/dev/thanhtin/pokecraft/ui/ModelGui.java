package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * OP panel for 3D models: shows how many species have a ModelEngine blueprint
 * installed and lets the owner browse every species and preview its model live.
 * Assigning a blueprint to a mismatched species is done with
 * {@code /poke model set <species> <blueprint>}.
 */
public class ModelGui implements Listener {

    private static final int PAGE_SIZE = 45;

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keySpecies;
    private final NamespacedKey keyNav;

    private static class Holder implements InventoryHolder {
        int page;
        boolean missingOnly;
        Inventory inventory;
        Holder(int page, boolean missingOnly) { this.page = page; this.missingOnly = missingOnly; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public ModelGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keySpecies = new NamespacedKey(plugin, "model_species");
        this.keyNav = new NamespacedKey(plugin, "model_nav");
    }

    public void open(Player player, int page, boolean missingOnly) {
        if (openForm(player, page, missingOnly)) return;
        List<PokemonSpecies> list = new ArrayList<>(
                missingOnly ? plugin.models().speciesByModel(false) : plugin.species().all());
        list.sort(Comparator.comparingInt(s -> s.dex));

        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder(page, missingOnly);
        Inventory inv = plugin.getServer().createInventory(holder, 54,
                Component.text("Pokemon Models"));
        holder.inventory = inv;

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < list.size(); i++) {
            PokemonSpecies s = list.get(start + i);
            inv.setItem(i, speciesIcon(s));
        }

        int[] cov = plugin.models().coverage();
        ItemStack info = new ItemStack(plugin.entities().hasModelEngine()
                ? Material.ARMOR_STAND : Material.BARRIER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text(plugin.entities().hasModelEngine()
                ? "ModelEngine: hooked" : "ModelEngine: NOT installed",
                plugin.entities().hasModelEngine() ? NamedTextColor.GREEN : NamedTextColor.RED));
        infoMeta.lore(List.of(
                Component.text("Models with a blueprint: " + cov[0] + "/" + cov[1], NamedTextColor.GRAY),
                Component.text("Add a .bbmodel in ModelEngine named like the", NamedTextColor.DARK_GRAY),
                Component.text("species id; or /poke model set <species> <blueprint>", NamedTextColor.DARK_GRAY),
                Component.text("Click a pokemon to preview its model", NamedTextColor.YELLOW)));
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        if (page > 0) inv.setItem(45, nav("prev", Material.ARROW, "Previous page"));
        if (start + PAGE_SIZE < list.size()) inv.setItem(53, nav("next", Material.ARROW, "Next page"));
        inv.setItem(48, nav("all", Material.ENDER_EYE, "Show: all species"));
        inv.setItem(50, nav("missing", Material.SPYGLASS, "Show: missing a model"));

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    private ItemStack speciesIcon(PokemonSpecies s) {
        boolean has = plugin.models().hasModel(s);
        ItemStack item = new ItemStack(has ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("#" + s.dex + " " + s.name,
                has ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Blueprint: " + plugin.models().blueprintFor(s), NamedTextColor.AQUA));
        lore.add(has ? Component.text("Model installed - click to preview", NamedTextColor.GREEN)
                : Component.text("No blueprint found", NamedTextColor.RED));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keySpecies, PersistentDataType.STRING, s.id);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack nav(String id, Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        meta.getPersistentDataContainer().set(keyNav, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        var pdc = item.getItemMeta().getPersistentDataContainer();

        String nav = pdc.get(keyNav, PersistentDataType.STRING);
        if (nav != null) {
            switch (nav) {
                case "prev" -> open(player, holder.page - 1, holder.missingOnly);
                case "next" -> open(player, holder.page + 1, holder.missingOnly);
                case "all" -> open(player, 0, false);
                case "missing" -> open(player, 0, true);
                default -> {}
            }
            return;
        }
        String speciesId = pdc.get(keySpecies, PersistentDataType.STRING);
        if (speciesId == null) return;
        PokemonSpecies s = plugin.species().getSpecies(speciesId);
        if (s == null) return;
        clickSpecies(player, s);
    }

    private void clickSpecies(Player player, PokemonSpecies s) {
        player.closeInventory();
        plugin.models().preview(player, plugin.models().blueprintFor(s));
    }

    private boolean openForm(Player player, int page, boolean missingOnly) {
        if (!plugin.bedrock().isBedrock(player)) return false;

        List<PokemonSpecies> list = new ArrayList<>(
                missingOnly ? plugin.models().speciesByModel(false) : plugin.species().all());
        list.sort(Comparator.comparingInt(s -> s.dex));

        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, pages - 1));

        int start = page * PAGE_SIZE;
        boolean hasPrev = page > 0;
        boolean hasNext = start + PAGE_SIZE < list.size();

        int[] cov = plugin.models().coverage();
        String content = (plugin.entities().hasModelEngine()
                ? "ModelEngine: hooked" : "ModelEngine: NOT installed")
                + "\nModels with a blueprint: " + cov[0] + "/" + cov[1]
                + "\nShowing: " + (missingOnly ? "missing a model" : "all species")
                + "\nPage " + (page + 1) + "/" + pages;

        List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons = new ArrayList<>();

        final int fromPage = page;
        final boolean fromMissingOnly = missingOnly;
        if (hasPrev) {
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    "Previous page", () -> open(player, fromPage - 1, fromMissingOnly)));
        }

        for (int i = 0; i < PAGE_SIZE && start + i < list.size(); i++) {
            PokemonSpecies s = list.get(start + i);
            boolean has = plugin.models().hasModel(s);
            String label = "#" + s.dex + " " + s.name
                    + (has ? " - model installed" : " - no model");
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    label, () -> clickSpecies(player, s)));
        }

        if (hasNext) {
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    "Next page", () -> open(player, fromPage + 1, fromMissingOnly)));
        }

        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null));

        plugin.bedrock().openForm(player, "Pokemon Models", content, buttons);
        return true;
    }
}
