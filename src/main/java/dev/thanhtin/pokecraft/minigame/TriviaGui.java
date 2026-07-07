package dev.thanhtin.pokecraft.minigame;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.ui.GuiFiller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Single-player trivia quiz: answer a question for a money reward. */
public class TriviaGui implements Listener {

    private record Question(String q, List<String> answers, int correct) {}

    private static final List<Question> DEFAULTS = List.of(
            new Question("Which type is super effective against Water?",
                    List.of("Grass", "Fire", "Flying", "Normal"), 0),
            new Question("What does a Fire Stone evolve Vulpix into?",
                    List.of("Ninetales", "Arcanine", "Flareon", "Rapidash"), 0),
            new Question("How many pokemon fit in a party?",
                    List.of("6", "4", "8", "10"), 0),
            new Question("Which ball never fails to catch?",
                    List.of("Master Ball", "Ultra Ball", "Great Ball", "Poke Ball"), 0),
            new Question("Pikachu evolves into...?",
                    List.of("Raichu", "Pichu", "Jolteon", "Zapdos"), 0),
            new Question("Which type is Gengar?",
                    List.of("Ghost/Poison", "Dark/Ghost", "Ghost", "Poison"), 0),
            new Question("Status that halves physical damage?",
                    List.of("Burn", "Poison", "Paralysis", "Sleep"), 0),
            new Question("How many types are there?",
                    List.of("18", "15", "17", "20"), 0));

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyAnswer;
    private final NamespacedKey keyCorrect;
    private final java.util.Map<java.util.UUID, Long> cooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public TriviaGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyAnswer = new NamespacedKey(plugin, "trivia_answer");
        this.keyCorrect = new NamespacedKey(plugin, "trivia_correct");
    }

    public void open(Player player) {
        long cd = plugin.getConfig().getLong("trivia.cooldown-seconds", 60) * 1000L;
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < cd) {
            long s = (cd - (System.currentTimeMillis() - last)) / 1000 + 1;
            player.sendMessage(Component.text("Next question in " + s + "s.", NamedTextColor.RED));
            return;
        }
        Question question = DEFAULTS.get(ThreadLocalRandom.current().nextInt(DEFAULTS.size()));
        if (openForm(player, question)) return;
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 27, Component.text("Trivia Quiz"));
        holder.inventory = inv;

        ItemStack q = new ItemStack(Material.BOOK);
        ItemMeta qMeta = q.getItemMeta();
        qMeta.displayName(Component.text(question.q(), NamedTextColor.YELLOW));
        qMeta.lore(List.of(Component.text("Reward: " + plugin.economy().format(
                plugin.getConfig().getLong("trivia.reward", 300)), NamedTextColor.GRAY)));
        q.setItemMeta(qMeta);
        inv.setItem(4, q);

        int[] slots = {10, 12, 14, 16};
        Material[] mats = {Material.RED_WOOL, Material.GREEN_WOOL, Material.BLUE_WOOL, Material.YELLOW_WOOL};
        for (int i = 0; i < 4; i++) {
            ItemStack a = new ItemStack(mats[i]);
            ItemMeta meta = a.getItemMeta();
            meta.displayName(Component.text((char) ('A' + i) + ". " + question.answers().get(i),
                    NamedTextColor.AQUA));
            meta.getPersistentDataContainer().set(keyAnswer, PersistentDataType.INTEGER, i);
            meta.getPersistentDataContainer().set(keyCorrect, PersistentDataType.INTEGER, question.correct());
            a.setItemMeta(meta);
            inv.setItem(slots[i], a);
        }
        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    /** Native Bedrock version: the question as content, four answer buttons. */
    private boolean openForm(Player player, Question question) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons = new ArrayList<>();
        for (int i = 0; i < question.answers().size(); i++) {
            final int idx = i;
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    (char) ('A' + i) + ". " + question.answers().get(i),
                    () -> answer(player, idx, question.correct())));
        }
        long reward = plugin.getConfig().getLong("trivia.reward", 300);
        return plugin.bedrock().openForm(player, "Trivia Quiz",
                "§e" + question.q() + "§r\nReward: " + plugin.economy().format(reward), buttons);
    }

    private void answer(Player player, int chosen, int correct) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        player.closeInventory();
        if (chosen == correct) {
            long reward = plugin.getConfig().getLong("trivia.reward", 300);
            plugin.economy().deposit(player.getUniqueId(), reward);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
            player.sendMessage(Component.text("Correct! +" + plugin.economy().format(reward),
                    NamedTextColor.GREEN));
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            player.sendMessage(Component.text("Wrong answer! Try again soon.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        Integer answer = item.getItemMeta().getPersistentDataContainer()
                .get(keyAnswer, PersistentDataType.INTEGER);
        Integer correct = item.getItemMeta().getPersistentDataContainer()
                .get(keyCorrect, PersistentDataType.INTEGER);
        if (answer == null || correct == null) return;
        answer(player, answer, correct);
    }
}
