package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.battle.MoveData;
import dev.thanhtin.pokecraft.pokemon.ExperienceCurve;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed summary screen for one party pokemon - the chest-GUI equivalent
 * of a Cobblemon/Pixelmon summary: info header, moves with PP and effect,
 * computed stats with IVs, evolution requirements.
 */
public class SummaryGui implements Listener {
    private static final int SLOT_HEADER = 4;
    private static final int[] MOVE_SLOTS = {10, 11, 12, 13};
    private static final int SLOT_STATS = 15;
    private static final int SLOT_EVOLVE = 16;
    private static final int SLOT_HELD = 19;
    private static final int SLOT_RENAME = 25;
    private static final int SLOT_RELEASE = 26;
    private static final int SLOT_SELL = 24;
    private static final int SLOT_BACK = 22;

    private final PokeCraftPlugin plugin;
    /** players who armed the release button (need a second click to confirm) */
    private final java.util.Set<java.util.UUID> releaseArmed =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** players who armed the sell button (need a second click to confirm) */
    private final java.util.Set<java.util.UUID> sellArmed =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static class Holder implements InventoryHolder {
        final int partySlot;
        Inventory inventory;
        Holder(int partySlot) { this.partySlot = partySlot; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public SummaryGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int partySlot) {
        if (openForm(player, partySlot)) return;
        PokemonInstance p = plugin.parties().get(player).get(partySlot);
        if (p == null) {
            plugin.partyUi().open(player);
            return;
        }
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        if (species == null) return;

        Holder holder = new Holder(partySlot);
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text("#" + String.format("%03d", species.dex) + " "
                        + p.displayName(species) + " Lv." + p.level));
        holder.inventory = inv;

        // ---- info header ----
        ItemStack head = new ItemStack(p.shiny ? Material.NETHER_STAR : Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        meta.displayName(Component.text((p.status != null ? "[" + p.status.tag + "] " : "")
                        + p.displayName(species) + " Lv." + p.level,
                p.shiny ? NamedTextColor.GOLD : NamedTextColor.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Type: " + species.types, NamedTextColor.GRAY));
        dev.thanhtin.pokecraft.pokemon.Gender g = p.gender(species);
        if (g != dev.thanhtin.pokecraft.pokemon.Gender.GENDERLESS) {
            lore.add(Component.text("Gender: " + g.symbol + " " + g.name().charAt(0)
                    + g.name().substring(1).toLowerCase(),
                    g == dev.thanhtin.pokecraft.pokemon.Gender.MALE
                            ? NamedTextColor.AQUA : NamedTextColor.LIGHT_PURPLE));
        }
        String ability = p.ability(species);
        if (ability != null && !ability.isBlank()) {
            String pretty = ability.substring(0, 1).toUpperCase()
                    + ability.substring(1).replace('-', ' ').replace('_', ' ');
            lore.add(Component.text("Ability: " + pretty, NamedTextColor.GOLD));
        }
        lore.add(Component.text("Nature: " + p.nature
                + (p.nature.up != p.nature.down
                        ? " (+" + statName(p.nature.up) + " -" + statName(p.nature.down) + ")"
                        : ""), NamedTextColor.GRAY));
        lore.add(Component.text("HP " + p.currentHp + "/" + p.maxHp(species),
                p.currentHp > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        long next = ExperienceCurve.expForLevel(Math.min(p.level + 1, ExperienceCurve.MAX_LEVEL));
        lore.add(Component.text("EXP " + p.exp + " / " + next + " for next level", NamedTextColor.GRAY));
        if (p.shiny) lore.add(Component.text("SHINY!", NamedTextColor.GOLD));
        meta.lore(lore);
        head.setItemMeta(meta);
        inv.setItem(SLOT_HEADER, head);

        // ---- moves ----
        for (int i = 0; i < MOVE_SLOTS.length; i++) {
            if (p.moves == null || i >= p.moves.size()) continue;
            MoveData move = plugin.species().getMove(p.moves.get(i));
            if (move == null) continue;
            int pp = p.ppFor(move);
            ItemStack item = new ItemStack(materialFor(move));
            ItemMeta moveMeta = item.getItemMeta();
            moveMeta.displayName(Component.text(move.name,
                    pp > 0 ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
            List<Component> moveLore = new ArrayList<>();
            moveLore.add(Component.text(move.type + " / " + move.category, NamedTextColor.GRAY));
            moveLore.add(Component.text("Power " + move.power + "  Accuracy " + move.accuracy
                    + (move.priority != 0 ? "  Priority " + move.priority : ""), NamedTextColor.GRAY));
            moveLore.add(Component.text("PP " + pp + "/" + move.pp,
                    pp > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (move.effect != null) {
                if (move.effect.status != null) {
                    moveLore.add(Component.text(move.effect.statusChance + "% chance to inflict "
                            + move.effect.status.tag, NamedTextColor.LIGHT_PURPLE));
                }
                if (move.effect.stat >= 1 && move.effect.stat <= 5 && move.effect.stages != 0) {
                    moveLore.add(Component.text((move.effect.target == MoveData.Target.SELF
                            ? "Raises own " : "Lowers foe's ") + statName(move.effect.stat)
                            + " by " + Math.abs(move.effect.stages), NamedTextColor.LIGHT_PURPLE));
                }
            }
            moveMeta.lore(moveLore);
            item.setItemMeta(moveMeta);
            inv.setItem(MOVE_SLOTS[i], item);
        }

        // ---- stats ----
        ItemStack stats = new ItemStack(Material.COMPARATOR);
        ItemMeta statsMeta = stats.getItemMeta();
        statsMeta.displayName(Component.text("Stats", NamedTextColor.YELLOW));
        List<Component> statsLore = new ArrayList<>();
        String[] names = {"HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed"};
        for (int i = 0; i < 6; i++) {
            int ev = (p.evs != null && i < p.evs.length) ? p.evs[i] : 0;
            statsLore.add(Component.text(names[i] + ": " + p.stat(species, i)
                    + "  (IV " + p.ivs[i] + "/31, EV " + ev + ")", NamedTextColor.GRAY));
        }
        statsMeta.lore(statsLore);
        stats.setItemMeta(statsMeta);
        inv.setItem(SLOT_STATS, stats);

        // ---- evolution ----
        ItemStack evolve = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta evoMeta = evolve.getItemMeta();
        evoMeta.displayName(Component.text("Evolution", NamedTextColor.LIGHT_PURPLE));
        List<Component> evoLore = new ArrayList<>();
        List<PokemonSpecies.Evolution> evolutions = species.allEvolutions();
        if (evolutions.isEmpty()) {
            evoLore.add(Component.text("This pokemon does not evolve.", NamedTextColor.GRAY));
        }
        for (PokemonSpecies.Evolution evo : evolutions) {
            PokemonSpecies to = plugin.species().getSpecies(evo.to);
            if (to == null) continue;
            if (evo.item != null) {
                evoLore.add(Component.text("-> " + to.name + ": use a "
                        + evo.item.replace('_', ' ') + " (shop)", NamedTextColor.GRAY));
            } else {
                evoLore.add(Component.text("-> " + to.name + " at Lv." + evo.level
                        + (p.level >= evo.level ? " (next level-up!)" : ""), NamedTextColor.GRAY));
            }
        }
        evoMeta.lore(evoLore);
        evolve.setItemMeta(evoMeta);
        inv.setItem(SLOT_EVOLVE, evolve);

        // ---- held item ----
        var held = plugin.heldItems().byId(p.heldItem);
        ItemStack heldStack = new ItemStack(held != null ? held.material : Material.ITEM_FRAME);
        ItemMeta heldMeta = heldStack.getItemMeta();
        heldMeta.displayName(Component.text(held != null ? "Holding: " + held.display : "Held Item: none",
                held != null ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));
        heldMeta.lore(List.of(Component.text(held != null
                ? held.description : "Buy held items in the shop,", NamedTextColor.GRAY),
                Component.text(held != null
                ? "Click to take it back" : "then right-click one to equip", NamedTextColor.GRAY)));
        heldStack.setItemMeta(heldMeta);
        inv.setItem(SLOT_HELD, heldStack);

        ItemStack rename = new ItemStack(Material.NAME_TAG);
        ItemMeta renameMeta = rename.getItemMeta();
        renameMeta.displayName(Component.text("Set nickname", NamedTextColor.YELLOW));
        renameMeta.lore(List.of(Component.text("Type a name (PC & mobile)", NamedTextColor.GRAY)));
        rename.setItemMeta(renameMeta);
        inv.setItem(SLOT_RENAME, rename);

        boolean armed = releaseArmed.contains(player.getUniqueId());
        ItemStack release = new ItemStack(armed ? Material.LAVA_BUCKET : Material.BUCKET);
        ItemMeta releaseMeta = release.getItemMeta();
        releaseMeta.displayName(Component.text(armed ? "Click again to CONFIRM release"
                : "Release", armed ? NamedTextColor.RED : NamedTextColor.GRAY));
        releaseMeta.lore(List.of(Component.text(armed
                ? "This is permanent!" : "Let this pokemon go free", NamedTextColor.GRAY)));
        release.setItemMeta(releaseMeta);
        inv.setItem(SLOT_RELEASE, release);

        boolean sellReady = sellArmed.contains(player.getUniqueId());
        long sellValue = sellPrice(p);
        ItemStack sell = new ItemStack(Material.EMERALD);
        ItemMeta sellMeta = sell.getItemMeta();
        sellMeta.displayName(Component.text(sellReady ? "Click again to CONFIRM sale"
                : "Sell for " + plugin.economy().format(sellValue),
                sellReady ? NamedTextColor.RED : NamedTextColor.GREEN));
        sellMeta.lore(List.of(Component.text(sellReady
                ? "This is permanent!" : "Sell this pokemon for money", NamedTextColor.GRAY)));
        sell.setItemMeta(sellMeta);
        inv.setItem(SLOT_SELL, sell);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back to party", NamedTextColor.GRAY));
        back.setItemMeta(backMeta);
        inv.setItem(SLOT_BACK, back);

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    private String statName(int index) {
        return switch (index) {
            case 1 -> "Atk"; case 2 -> "Def"; case 3 -> "SpA";
            case 4 -> "SpD"; case 5 -> "Spe"; default -> "HP";
        };
    }

    private Material materialFor(MoveData move) {
        return switch (move.type) {
            case FIRE -> Material.BLAZE_POWDER;
            case WATER -> Material.HEART_OF_THE_SEA;
            case GRASS -> Material.OAK_SAPLING;
            case ELECTRIC -> Material.GLOWSTONE_DUST;
            case FLYING -> Material.FEATHER;
            case DARK -> Material.COAL;
            case POISON -> Material.SPIDER_EYE;
            case PSYCHIC -> Material.AMETHYST_SHARD;
            case ICE -> Material.SNOWBALL;
            case ROCK, GROUND -> Material.COBBLESTONE;
            case FIGHTING -> Material.IRON_SWORD;
            case GHOST -> Material.ENDER_EYE;
            case BUG -> Material.COBWEB;
            case DRAGON -> Material.DRAGON_BREATH;
            case STEEL -> Material.IRON_INGOT;
            case FAIRY -> Material.PINK_PETALS;
            default -> Material.SLIME_BALL;
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        int raw = e.getRawSlot();
        java.util.UUID id = player.getUniqueId();
        boolean busy = plugin.battles().get(player) != null || plugin.pvp().get(player) != null
                || plugin.trades().get(player) != null;

        // any button other than the two confirm buttons disarms both
        if (raw != SLOT_RELEASE) releaseArmed.remove(id);
        if (raw != SLOT_SELL) sellArmed.remove(id);

        if (raw == SLOT_BACK) {
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.partyUi().open(player));
            return;
        }
        if (raw == SLOT_HELD) {
            if (busy) return;
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> doHeldItem(player, holder.partySlot));
            return;
        }
        if (raw == SLOT_RENAME) {
            if (busy) return;
            releaseArmed.remove(id);
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.nicknameInput().open(player, holder.partySlot));
            return;
        }
        if (raw == SLOT_RELEASE) {
            if (busy) return;
            if (!releaseArmed.contains(id)) {
                releaseArmed.add(id);
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, holder.partySlot));
                return;
            }
            releaseArmed.remove(id);
            plugin.getServer().getScheduler().runTask(plugin, () -> doRelease(player, holder.partySlot));
            return;
        }
        if (raw == SLOT_SELL) {
            if (busy) return;
            if (!sellArmed.contains(id)) {
                sellArmed.add(id);
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, holder.partySlot));
                return;
            }
            sellArmed.remove(id);
            plugin.getServer().getScheduler().runTask(plugin, () -> doSell(player, holder.partySlot));
        }
    }

    /** Sell value of a pokemon: a base plus level, tripled if shiny. */
    private long sellPrice(PokemonInstance p) {
        long base = plugin.getConfig().getLong("shop.pokemon-sell-base", 200);
        long perLevel = plugin.getConfig().getLong("shop.pokemon-sell-per-level", 20);
        long value = base + perLevel * Math.max(1, p.level);
        if (p.shiny) value *= 3;
        return value;
    }

    /**
     * Bedrock players get a native Cumulus form instead of the chest GUI.
     * Returns true if a form was sent (so {@link #open} should stop); false
     * for Java players, who fall through to the chest GUI.
     */
    private boolean openForm(Player player, int partySlot) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        PokemonInstance p = plugin.parties().get(player).get(partySlot);
        if (p == null) {
            plugin.partyUi().open(player);
            return true;
        }
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        if (species == null) return true;

        String name = p.displayName(species);
        String title = "#" + String.format("%03d", species.dex) + " " + name + " Lv." + p.level;

        // ---- content mirroring the chest header / stats / moves ----
        StringBuilder sb = new StringBuilder();
        sb.append("§b").append(name).append(" §7Lv.").append(p.level);
        if (p.shiny) sb.append(" §6✦");
        sb.append("\n");
        sb.append("§7Type: §f").append(species.types).append("\n");
        dev.thanhtin.pokecraft.pokemon.Gender g = p.gender(species);
        if (g != dev.thanhtin.pokecraft.pokemon.Gender.GENDERLESS) {
            sb.append("§7Gender: §f").append(g.symbol).append(" ")
                    .append(g.name().charAt(0)).append(g.name().substring(1).toLowerCase())
                    .append("\n");
        }
        String ability = p.ability(species);
        if (ability != null && !ability.isBlank()) {
            String pretty = ability.substring(0, 1).toUpperCase()
                    + ability.substring(1).replace('-', ' ').replace('_', ' ');
            sb.append("§7Ability: §6").append(pretty).append("\n");
        }
        sb.append("§7Nature: §f").append(p.nature)
                .append(p.nature.up != p.nature.down
                        ? " §8(+" + statName(p.nature.up) + " -" + statName(p.nature.down) + ")"
                        : "")
                .append("\n");
        sb.append("§7HP: §f").append(p.currentHp).append("/").append(p.maxHp(species)).append("\n");

        sb.append("\n§eStats:\n");
        String[] names = {"HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed"};
        for (int i = 0; i < 6; i++) {
            int ev = (p.evs != null && i < p.evs.length) ? p.evs[i] : 0;
            sb.append("§7").append(names[i]).append(": §f").append(p.stat(species, i))
                    .append(" §8(IV ").append(p.ivs[i]).append("/31, EV ").append(ev).append(")\n");
        }

        sb.append("\n§eMoves:\n");
        if (p.moves != null) {
            for (String moveId : p.moves) {
                MoveData move = plugin.species().getMove(moveId);
                if (move == null) continue;
                int pp = p.ppFor(move);
                sb.append("§7- §b").append(move.name)
                        .append(" §8").append(move.type).append("/").append(move.category)
                        .append(" §7PP ").append(pp).append("/").append(move.pp).append("\n");
            }
        }

        // ---- buttons mirroring the chest actions ----
        List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons = new ArrayList<>();
        var held = plugin.heldItems().byId(p.heldItem);
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                held != null ? "Take back held item (" + held.display + ")" : "Give/Change held item",
                () -> doHeldItem(player, partySlot)));
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                "Rename", () -> plugin.nicknameInput().open(player, partySlot)));
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                "Release", () -> plugin.bedrock().openForm(player, "Confirm",
                        "Release " + name + "? This is permanent!",
                        List.of(
                                new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                                        "Yes, do it", () -> doRelease(player, partySlot)),
                                new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                                        "Cancel", () -> open(player, partySlot))))));
        long sellValue = sellPrice(p);
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                "Sell for " + plugin.economy().format(sellValue),
                () -> plugin.bedrock().openForm(player, "Confirm",
                        "Sell " + name + " for " + plugin.economy().format(sellValue)
                                + "? This is permanent!",
                        List.of(
                                new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                                        "Yes, do it", () -> doSell(player, partySlot)),
                                new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                                        "Cancel", () -> open(player, partySlot))))));
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                "Back", () -> plugin.partyUi().open(player)));

        plugin.bedrock().openForm(player, title, sb.toString(), buttons);
        return true;
    }

    /** Take back the held item (if any) then refresh the summary. */
    private void doHeldItem(Player player, int slot) {
        PokemonInstance target = plugin.parties().get(player).get(slot);
        if (target != null && target.heldItem != null) {
            plugin.heldItems().unequip(player, target);
        }
        open(player, slot);
    }

    private void doSell(Player player, int slot) {
        var party = plugin.parties().get(player);
        PokemonInstance p = party.get(slot);
        if (p == null) { plugin.partyUi().open(player); return; }
        if (party.partySize() <= 1) {
            player.sendMessage(Component.text("You can't sell your last party pokemon.",
                    NamedTextColor.RED));
            open(player, slot);
            return;
        }
        long value = sellPrice(p);
        PokemonInstance removed = party.removeFromParty(slot);
        if (removed != null) {
            plugin.storage().delete(removed.uuid);
            plugin.parties().saveParty(player.getUniqueId());
            plugin.economy().deposit(player.getUniqueId(), value);
            PokemonSpecies species = plugin.species().getSpecies(removed.speciesId);
            player.sendMessage(Component.text("Sold "
                    + (species != null ? removed.displayName(species) : removed.speciesId)
                    + " for " + plugin.economy().format(value) + "!", NamedTextColor.GREEN));
        }
        plugin.partyUi().open(player);
    }

    private void doRelease(Player player, int slot) {
        var party = plugin.parties().get(player);
        PokemonInstance p = party.get(slot);
        if (p == null) { plugin.partyUi().open(player); return; }
        if (party.partySize() <= 1) {
            player.sendMessage(Component.text("You can't release your last party pokemon.",
                    NamedTextColor.RED));
            open(player, slot);
            return;
        }
        PokemonInstance removed = party.removeFromParty(slot);
        if (removed != null) {
            plugin.storage().delete(removed.uuid);
            plugin.parties().saveParty(player.getUniqueId());
            PokemonSpecies species = plugin.species().getSpecies(removed.speciesId);
            player.sendMessage(Component.text("Bye bye, "
                    + (species != null ? removed.displayName(species) : removed.speciesId) + "!",
                    NamedTextColor.GREEN));
        }
        plugin.partyUi().open(player);
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        if (!(e.getPlayer() instanceof Player player)) return;
        // a reopen (arm -> refresh) also fires close; only disarm when the
        // summary truly stays closed
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)) {
                releaseArmed.remove(player.getUniqueId());
                sellArmed.remove(player.getUniqueId());
            }
        });
    }
}
