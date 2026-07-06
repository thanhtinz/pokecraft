package dev.thanhtin.pokecraft.battle;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BattleManager {
    private final PokeCraftPlugin plugin;
    private final Map<UUID, Battle> battles = new ConcurrentHashMap<>();

    public BattleManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public Battle get(Player player) { return battles.get(player.getUniqueId()); }

    public void startWildBattle(Player player, Entity wildEntity) {
        if (battles.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("You are already in a battle.", NamedTextColor.RED));
            return;
        }
        PokemonInstance wild = plugin.entities().readData(wildEntity);
        if (wild == null) return;
        PokemonInstance mine = plugin.parties().get(player).firstAlive();
        if (mine == null) {
            player.sendMessage(Component.text(
                    "You have no usable pokemon. Catch one with a pokeball first (/poke give).",
                    NamedTextColor.RED));
            return;
        }
        PokemonSpecies wildSpecies = plugin.species().getSpecies(wild.speciesId);
        Battle battle = new Battle(player.getUniqueId(), mine, wild, wildEntity);
        battles.put(player.getUniqueId(), battle);
        player.sendMessage(Component.text("A wild " + wild.displayName(wildSpecies)
                + " Lv." + wild.level + " appeared!", NamedTextColor.YELLOW));
        plugin.battleUi().open(player, battle);
    }

    public void useMove(Player player, String moveId) {
        Battle battle = battles.get(player.getUniqueId());
        if (battle == null || battle.finished) return;
        PokemonSpecies mySpecies = plugin.species().getSpecies(battle.playerPokemon.speciesId);
        PokemonSpecies wildSpecies = plugin.species().getSpecies(battle.wildPokemon.speciesId);
        MoveData myMove = plugin.species().getMove(moveId);
        if (mySpecies == null || wildSpecies == null || myMove == null) return;

        MoveData wildMove = pickWildMove(battle.wildPokemon, wildSpecies);

        int mySpeed = battle.playerPokemon.stat(mySpecies, 5);
        int wildSpeed = battle.wildPokemon.stat(wildSpecies, 5);
        boolean playerFirst = mySpeed >= wildSpeed;

        if (playerFirst) {
            if (attack(player, battle, battle.playerPokemon, mySpecies, battle.wildPokemon, wildSpecies, myMove, true)) return;
            if (wildMove != null && attack(player, battle, battle.wildPokemon, wildSpecies, battle.playerPokemon, mySpecies, wildMove, false)) return;
        } else {
            if (wildMove != null && attack(player, battle, battle.wildPokemon, wildSpecies, battle.playerPokemon, mySpecies, wildMove, false)) return;
            if (attack(player, battle, battle.playerPokemon, mySpecies, battle.wildPokemon, wildSpecies, myMove, true)) return;
        }
        plugin.battleUi().open(player, battle);
    }

    /** @return true if the battle ended */
    private boolean attack(Player player, Battle battle,
                           PokemonInstance attacker, PokemonSpecies attackerSpecies,
                           PokemonInstance defender, PokemonSpecies defenderSpecies,
                           MoveData move, boolean byPlayer) {
        DamageCalculator.Result result = DamageCalculator.calculate(
                attacker, attackerSpecies, defender, defenderSpecies, move);

        String prefix = byPlayer ? "Your " : "Wild ";
        if (result.missed()) {
            player.sendMessage(Component.text(prefix + attackerSpecies.name + " used "
                    + move.name + " but missed!", NamedTextColor.GRAY));
            return false;
        }
        defender.currentHp = Math.max(0, defender.currentHp - result.damage());
        StringBuilder msg = new StringBuilder(prefix + attackerSpecies.name + " used " + move.name
                + " (" + result.damage() + " dmg)");
        if (result.critical()) msg.append(" - Critical hit!");
        if (result.effectiveness() > 1.0) msg.append(" It's super effective!");
        if (result.effectiveness() > 0 && result.effectiveness() < 1.0) msg.append(" Not very effective...");
        if (result.effectiveness() == 0) msg.append(" It had no effect.");
        player.sendMessage(Component.text(msg.toString(),
                byPlayer ? NamedTextColor.AQUA : NamedTextColor.LIGHT_PURPLE));

        if (defender.currentHp <= 0) {
            if (byPlayer) endWithVictory(player, battle);
            else endWithDefeat(player, battle);
            return true;
        }
        // keep entity data in sync so pokeball capture chance reflects damage
        if (!byPlayer || defender == battle.wildPokemon) {
            if (battle.wildEntity.isValid()) plugin.entities().writeData(battle.wildEntity, battle.wildPokemon);
        }
        return false;
    }

    private MoveData pickWildMove(PokemonInstance wild, PokemonSpecies species) {
        List<String> moves = wild.moves;
        if (moves == null || moves.isEmpty()) return plugin.species().getMove("tackle");
        String id = moves.get(ThreadLocalRandom.current().nextInt(moves.size()));
        MoveData m = plugin.species().getMove(id);
        return m != null ? m : plugin.species().getMove("tackle");
    }

    private void endWithVictory(Player player, Battle battle) {
        battle.finished = true;
        battles.remove(player.getUniqueId());
        PokemonSpecies wildSpecies = plugin.species().getSpecies(battle.wildPokemon.speciesId);
        PokemonSpecies mySpecies = plugin.species().getSpecies(battle.playerPokemon.speciesId);
        if (battle.wildEntity.isValid()) battle.wildEntity.remove();

        double mult = plugin.getConfig().getDouble("battle.exp-multiplier", 1.0);
        long gained = Math.round(wildSpecies.expYield * battle.wildPokemon.level / 7.0 * mult);
        int levels = battle.playerPokemon.addExp(mySpecies, gained);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.sendMessage(Component.text("Wild " + wildSpecies.name + " fainted! +"
                + gained + " EXP", NamedTextColor.GREEN));
        if (levels > 0) {
            player.sendMessage(Component.text(battle.playerPokemon.displayName(mySpecies)
                    + " grew to Lv." + battle.playerPokemon.level + "!", NamedTextColor.GOLD));
            checkEvolution(player, battle.playerPokemon, mySpecies);
        }
        plugin.parties().saveParty(player.getUniqueId());
        player.closeInventory();
    }

    private void endWithDefeat(Player player, Battle battle) {
        battle.finished = true;
        battles.remove(player.getUniqueId());
        PokemonSpecies mySpecies = plugin.species().getSpecies(battle.playerPokemon.speciesId);
        player.sendMessage(Component.text(battle.playerPokemon.displayName(mySpecies)
                + " fainted!", NamedTextColor.RED));
        PokemonInstance next = plugin.parties().get(player).firstAlive();
        if (next != null) {
            player.sendMessage(Component.text("Hit the wild pokemon again to send out "
                    + next.displayName(plugin.species().getSpecies(next.speciesId)) + ".",
                    NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("All your pokemon fainted. Heal at a Pokecenter (/poke heal - admin).",
                    NamedTextColor.RED));
        }
        plugin.parties().saveParty(player.getUniqueId());
        player.closeInventory();
    }

    private void checkEvolution(Player player, PokemonInstance p, PokemonSpecies species) {
        if (species.evolution == null || p.level < species.evolution.level) return;
        PokemonSpecies target = plugin.species().getSpecies(species.evolution.to);
        if (target == null) return; // evolution species file not installed yet
        p.speciesId = target.id;
        p.moves = PokemonInstance.latestMoves(target, p.level);
        p.currentHp = Math.min(p.currentHp, p.maxHp(target));
        player.sendMessage(Component.text("What? " + species.name + " is evolving... It became "
                + target.name + "!", NamedTextColor.LIGHT_PURPLE));
    }

    public void flee(Player player) {
        Battle battle = battles.remove(player.getUniqueId());
        if (battle != null) {
            player.sendMessage(Component.text("Got away safely!", NamedTextColor.GRAY));
            player.closeInventory();
        }
    }
}
