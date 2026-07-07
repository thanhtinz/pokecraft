package dev.thanhtin.pokecraft.trade;

import java.util.UUID;

/** A live 1v1 trade between two players. Offers are stored by pokemon UUID. */
public class TradeSession {
    public final UUID p1, p2;
    public UUID offer1, offer2;      // offered pokemon uuid, or null
    public boolean confirm1, confirm2;
    public boolean finished;

    public TradeSession(UUID p1, UUID p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    public boolean isP1(UUID player) { return p1.equals(player); }

    public UUID other(UUID player) { return isP1(player) ? p2 : p1; }

    public UUID offerOf(UUID player) { return isP1(player) ? offer1 : offer2; }

    public boolean confirmedBy(UUID player) { return isP1(player) ? confirm1 : confirm2; }

    /** Setting/changing an offer clears both confirmations. */
    public void setOffer(UUID player, UUID pokemonUuid) {
        if (isP1(player)) offer1 = pokemonUuid;
        else offer2 = pokemonUuid;
        confirm1 = false;
        confirm2 = false;
    }

    public void setConfirm(UUID player, boolean value) {
        if (isP1(player)) confirm1 = value;
        else confirm2 = value;
    }

    public boolean bothOffered() { return offer1 != null && offer2 != null; }

    public boolean bothConfirmed() { return confirm1 && confirm2; }
}
