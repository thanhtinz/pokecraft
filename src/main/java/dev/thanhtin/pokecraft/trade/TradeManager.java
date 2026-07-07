package dev.thanhtin.pokecraft.trade;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.party.PlayerParty;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player-to-player trading. /poke trade <player> requests; on accept both
 * players open a trade GUI, each offers one pokemon, and the swap happens only
 * once both confirm. Ownership transfers and the receiver's Pokedex updates.
 */
public class TradeManager implements Listener {
    private static final long REQUEST_TIMEOUT_MILLIS = 60_000;

    private final PokeCraftPlugin plugin;
    private final Map<UUID, TradeSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Request> requests = new ConcurrentHashMap<>();

    private record Request(UUID target, long at) {}

    public TradeManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public TradeSession get(Player player) { return sessions.get(player.getUniqueId()); }

    private boolean busy(Player player) {
        return plugin.battles().get(player) != null
                || plugin.pvp().get(player) != null
                || sessions.containsKey(player.getUniqueId());
    }

    public void request(Player from, Player to) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            from.sendMessage(Component.text("You can't trade with yourself.", NamedTextColor.RED));
            return;
        }
        if (busy(from)) {
            from.sendMessage(Component.text("You're busy right now.", NamedTextColor.RED));
            return;
        }
        if (busy(to)) {
            from.sendMessage(Component.text(to.getName() + " is busy right now.", NamedTextColor.RED));
            return;
        }
        double maxDistance = plugin.getConfig().getDouble("trade.max-distance", 50);
        if (!from.getWorld().equals(to.getWorld())
                || from.getLocation().distance(to.getLocation()) > maxDistance) {
            from.sendMessage(Component.text("You must be within " + (int) maxDistance
                    + " blocks of each other.", NamedTextColor.RED));
            return;
        }
        requests.put(from.getUniqueId(), new Request(to.getUniqueId(), System.currentTimeMillis()));
        from.sendMessage(Component.text("Trade request sent to " + to.getName() + ".", NamedTextColor.GREEN));
        to.sendMessage(Component.text(from.getName() + " wants to trade! /poke trade accept (60s) "
                + "or /poke trade deny.", NamedTextColor.AQUA));
    }

    public String pendingRequesterName(Player target) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Request> e : requests.entrySet()) {
            if (!e.getValue().target().equals(target.getUniqueId())) continue;
            if (now - e.getValue().at() > REQUEST_TIMEOUT_MILLIS) continue;
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p != null) return p.getName();
        }
        return null;
    }

    public void accept(Player player) {
        for (Map.Entry<UUID, Request> e : requests.entrySet()) {
            Request r = e.getValue();
            if (!r.target().equals(player.getUniqueId())) continue;
            requests.remove(e.getKey());
            if (System.currentTimeMillis() - r.at() > REQUEST_TIMEOUT_MILLIS) continue;
            Player requester = plugin.getServer().getPlayer(e.getKey());
            if (requester == null) continue;
            if (busy(requester) || busy(player)) {
                player.sendMessage(Component.text("One of you is busy now.", NamedTextColor.RED));
                return;
            }
            TradeSession session = new TradeSession(requester.getUniqueId(), player.getUniqueId());
            sessions.put(requester.getUniqueId(), session);
            sessions.put(player.getUniqueId(), session);
            plugin.tradeUi().open(requester, session);
            plugin.tradeUi().open(player, session);
            return;
        }
        player.sendMessage(Component.text("No pending trade request.", NamedTextColor.RED));
    }

    public void deny(Player player) {
        for (Map.Entry<UUID, Request> e : requests.entrySet()) {
            if (!e.getValue().target().equals(player.getUniqueId())) continue;
            requests.remove(e.getKey());
            Player requester = plugin.getServer().getPlayer(e.getKey());
            if (requester != null) {
                requester.sendMessage(Component.text(player.getName() + " declined the trade.",
                        NamedTextColor.RED));
            }
            player.sendMessage(Component.text("Trade declined.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("No pending trade request.", NamedTextColor.RED));
    }

    /** Offer the pokemon in the given party slot (or clear if already offered). */
    public void offer(Player player, int partySlot) {
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null || session.finished) return;
        PokemonInstance p = plugin.parties().get(player).get(partySlot);
        if (p == null) return;
        if (p.uuid.equals(session.offerOf(player.getUniqueId()))) {
            session.setOffer(player.getUniqueId(), null); // toggle off
        } else {
            session.setOffer(player.getUniqueId(), p.uuid);
        }
        refresh(session);
    }

    public void confirm(Player player) {
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null || session.finished) return;
        if (!session.bothOffered()) {
            player.sendMessage(Component.text("Both players must offer a pokemon first.",
                    NamedTextColor.RED));
            return;
        }
        session.setConfirm(player.getUniqueId(), true);
        if (session.bothConfirmed()) {
            execute(session);
        } else {
            refresh(session);
        }
    }

    public void cancel(Player player) {
        TradeSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        sessions.remove(session.other(player.getUniqueId()));
        session.finished = true;
        for (UUID id : new UUID[]{session.p1, session.p2}) {
            Player pl = plugin.getServer().getPlayer(id);
            if (pl != null) {
                pl.sendMessage(Component.text("Trade cancelled.", NamedTextColor.GRAY));
                pl.closeInventory();
            }
        }
    }

    private void refresh(TradeSession session) {
        for (UUID id : new UUID[]{session.p1, session.p2}) {
            Player pl = plugin.getServer().getPlayer(id);
            if (pl != null) plugin.tradeUi().open(pl, session);
        }
    }

    private PokemonInstance findInParty(Player player, UUID pokemonUuid) {
        if (pokemonUuid == null) return null;
        PlayerParty party = plugin.parties().get(player);
        for (int i = 0; i < PlayerParty.SIZE; i++) {
            PokemonInstance p = party.get(i);
            if (p != null && p.uuid.equals(pokemonUuid)) return p;
        }
        return null;
    }

    private int slotOf(Player player, UUID pokemonUuid) {
        PlayerParty party = plugin.parties().get(player);
        for (int i = 0; i < PlayerParty.SIZE; i++) {
            PokemonInstance p = party.get(i);
            if (p != null && p.uuid.equals(pokemonUuid)) return i;
        }
        return -1;
    }

    private void execute(TradeSession session) {
        session.finished = true;
        sessions.remove(session.p1);
        sessions.remove(session.p2);
        Player pl1 = plugin.getServer().getPlayer(session.p1);
        Player pl2 = plugin.getServer().getPlayer(session.p2);
        if (pl1 == null || pl2 == null) {
            if (pl1 != null) pl1.sendMessage(Component.text("Trade failed: other player left.", NamedTextColor.RED));
            if (pl2 != null) pl2.sendMessage(Component.text("Trade failed: other player left.", NamedTextColor.RED));
            return;
        }

        PokemonInstance a = findInParty(pl1, session.offer1);
        PokemonInstance b = findInParty(pl2, session.offer2);
        if (a == null || b == null) {
            pl1.sendMessage(Component.text("Trade failed: an offered pokemon moved.", NamedTextColor.RED));
            pl2.sendMessage(Component.text("Trade failed: an offered pokemon moved.", NamedTextColor.RED));
            pl1.closeInventory();
            pl2.closeInventory();
            return;
        }

        int slotA = slotOf(pl1, a.uuid);
        int slotB = slotOf(pl2, b.uuid);
        plugin.parties().get(pl1).removeFromParty(slotA);
        plugin.parties().get(pl2).removeFromParty(slotB);

        a.owner = session.p2;
        b.owner = session.p1;
        int newA = plugin.parties().get(pl2).add(a); // a goes to player 2
        int newB = plugin.parties().get(pl1).add(b); // b goes to player 1
        plugin.storage().save(a, newA);
        plugin.storage().save(b, newB);
        plugin.parties().saveParty(session.p1);
        plugin.parties().saveParty(session.p2);

        plugin.storage().markCaught(session.p2, a.speciesId);
        plugin.storage().markCaught(session.p1, b.speciesId);

        PokemonSpecies sa = plugin.species().getSpecies(a.speciesId);
        PokemonSpecies sb = plugin.species().getSpecies(b.speciesId);
        pl1.sendMessage(Component.text("Trade complete! You received " + b.displayName(sb) + ".",
                NamedTextColor.GREEN));
        pl2.sendMessage(Component.text("Trade complete! You received " + a.displayName(sa) + ".",
                NamedTextColor.GREEN));
        pl1.playSound(pl1.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        pl2.playSound(pl2.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        pl1.closeInventory();
        pl2.closeInventory();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (sessions.containsKey(e.getPlayer().getUniqueId())) cancel(e.getPlayer());
    }
}
