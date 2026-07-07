package dev.thanhtin.pokecraft.dungeon;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.capture.PokeballItem;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dungeon runs: a series of scaled trainer battles ending in a boss pokemon.
 * Winning each wave sends out the next; clearing the boss pays out. Reuses the
 * normal battle engine, so it works on PC and mobile alike.
 */
public class DungeonManager implements Listener {

    private static class Session {
        int wave = 1;   // 1..waves are trainers, waves+1 is the boss
    }

    private final PokeCraftPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public DungeonManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean inDungeon(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    private int waves() {
        return Math.max(1, plugin.getConfig().getInt("dungeon.waves", 3));
    }

    public void start(Player player) {
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null
                || plugin.trades().get(player) != null) {
            player.sendMessage(Component.text("Finish what you're doing first.", NamedTextColor.RED));
            return;
        }
        if (inDungeon(player)) return;
        if (plugin.parties().get(player).firstAlive() == null) {
            player.sendMessage(Component.text("You need a usable pokemon.", NamedTextColor.RED));
            return;
        }
        long cooldownMillis = plugin.getConfig().getLong("dungeon.cooldown-minutes", 30) * 60_000L;
        Long last = cooldowns.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldownMillis) {
            long min = (cooldownMillis - (now - last)) / 60_000 + 1;
            player.sendMessage(Component.text("The dungeon is recharging. Try again in "
                    + min + " min.", NamedTextColor.RED));
            return;
        }
        long cost = plugin.getConfig().getLong("dungeon.cost", 1000);
        if (!plugin.economy().withdraw(player.getUniqueId(), cost)) {
            player.sendMessage(Component.text("Entering the dungeon costs "
                    + plugin.economy().format(cost) + ".", NamedTextColor.RED));
            return;
        }
        cooldowns.put(player.getUniqueId(), now);
        Session session = new Session();
        sessions.put(player.getUniqueId(), session);
        player.sendMessage(Component.text("=== Dungeon entered! Clear " + waves()
                + " waves and the boss. ===", NamedTextColor.LIGHT_PURPLE));
        startWave(player, session);
    }

    private void startWave(Player player, Session session) {
        int baseLevel = plugin.getConfig().getInt("dungeon.base-level", 15);
        int step = plugin.getConfig().getInt("dungeon.level-step", 5);
        int shinyRate = plugin.getConfig().getInt("battle.shiny-rate", 4096);

        if (session.wave <= waves()) {
            int level = baseLevel + (session.wave - 1) * step;
            List<PokemonInstance> team = new ArrayList<>();
            List<PokemonSpecies> pool = spawnPool();
            int size = 1 + ThreadLocalRandom.current().nextInt(3);
            for (int i = 0; i < size && !pool.isEmpty(); i++) {
                PokemonSpecies s = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                team.add(PokemonInstance.generate(s, level, shinyRate));
            }
            player.sendMessage(Component.text("Wave " + session.wave + "/" + waves()
                    + " - a dungeon trainer appears!", NamedTextColor.YELLOW));
            plugin.battles().startTrainerBattle(player, null, team,
                    "Dungeon Wave " + session.wave, 0);
        } else {
            // boss wave
            int level = baseLevel + waves() * step
                    + plugin.getConfig().getInt("dungeon.boss-level-bonus", 10);
            PokemonSpecies boss = bossSpecies();
            List<PokemonInstance> team = new ArrayList<>();
            team.add(PokemonInstance.generate(boss, level, Math.max(1, shinyRate / 8)));
            player.sendMessage(Component.text("BOSS: a mighty " + boss.name + " Lv." + level
                    + " blocks your path!", NamedTextColor.RED));
            plugin.battles().startTrainerBattle(player, null, team, "Dungeon Boss", 0);
        }
    }

    private List<PokemonSpecies> spawnPool() {
        List<PokemonSpecies> pool = new ArrayList<>();
        for (PokemonSpecies s : plugin.species().all()) {
            if (s.spawn != null) pool.add(s);
        }
        if (pool.isEmpty()) pool.addAll(plugin.species().all());
        return pool;
    }

    private PokemonSpecies bossSpecies() {
        List<String> configured = plugin.getConfig().getStringList("dungeon.boss-species");
        List<PokemonSpecies> options = new ArrayList<>();
        for (String id : configured) {
            PokemonSpecies s = plugin.species().getSpecies(id);
            if (s != null) options.add(s);
        }
        if (options.isEmpty()) {
            // default: any low-catch-rate (rare/legendary-ish) species
            for (PokemonSpecies s : plugin.species().all()) {
                if (s.catchRate <= 45) options.add(s);
            }
        }
        if (options.isEmpty()) options.addAll(plugin.species().all());
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }

    /** Called by the battle engine when the player wins any battle. */
    public void onBattleWon(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        session.wave++;
        if (session.wave > waves() + 1) {
            complete(player);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (sessions.get(player.getUniqueId()) == session && player.isOnline()) {
                    startWave(player, session);
                }
            }, 40L); // brief pause between waves
        }
    }

    /** Called when the player loses or flees a battle. */
    public void onBattleLost(Player player) {
        if (sessions.remove(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("You were defeated in the dungeon. Better luck next time!",
                    NamedTextColor.RED));
        }
    }

    private void complete(Player player) {
        sessions.remove(player.getUniqueId());
        long reward = plugin.getConfig().getLong("dungeon.reward", 8000);
        plugin.economy().deposit(player.getUniqueId(), reward);
        player.getInventory().addItem(plugin.pokeballs().create(PokeballItem.BallType.ULTRA_BALL, 10));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.sendMessage(Component.text("DUNGEON CLEARED! +" + plugin.economy().format(reward)
                + " and 10x Ultra Ball!", NamedTextColor.GOLD));
        plugin.getServer().broadcast(Component.text(player.getName()
                + " cleared the dungeon!", NamedTextColor.LIGHT_PURPLE));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sessions.remove(e.getPlayer().getUniqueId());
    }
}
