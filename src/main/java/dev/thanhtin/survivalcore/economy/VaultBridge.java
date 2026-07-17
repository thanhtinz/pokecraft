package dev.thanhtin.survivalcore.economy;

import dev.thanhtin.survivalcore.SurvivalCore;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;

/** Exposes SurvivalCore money to Vault so shops/jobs/etc. read the same balance. */
public class VaultBridge implements Economy {

    private final SurvivalCore plugin;

    public VaultBridge(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    private UUID id(String name) {
        UUID byStore = plugin.db().uuidByName(name);
        return byStore != null ? byStore : Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "SurvivalCore"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }
    @Override public String format(double amount) { return plugin.economy().format(amount); }
    @Override public String currencyNamePlural() { return "coins"; }
    @Override public String currencyNameSingular() { return "coin"; }

    @Override public boolean hasAccount(String name) { return true; }
    @Override public boolean hasAccount(OfflinePlayer p) { return true; }
    @Override public boolean hasAccount(String name, String world) { return true; }
    @Override public boolean hasAccount(OfflinePlayer p, String world) { return true; }

    @Override public double getBalance(String name) { return plugin.economy().balance(id(name)); }
    @Override public double getBalance(OfflinePlayer p) { return plugin.economy().balance(p.getUniqueId()); }
    @Override public double getBalance(String name, String world) { return getBalance(name); }
    @Override public double getBalance(OfflinePlayer p, String world) { return getBalance(p); }

    @Override public boolean has(String name, double amount) { return getBalance(name) >= amount; }
    @Override public boolean has(OfflinePlayer p, double amount) { return getBalance(p) >= amount; }
    @Override public boolean has(String name, String world, double amount) { return has(name, amount); }
    @Override public boolean has(OfflinePlayer p, String world, double amount) { return has(p, amount); }

    @Override public EconomyResponse withdrawPlayer(String name, double amount) {
        return withdraw(id(name), amount);
    }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer p, double amount) {
        return withdraw(p.getUniqueId(), amount);
    }
    @Override public EconomyResponse withdrawPlayer(String name, String world, double amount) {
        return withdraw(id(name), amount);
    }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer p, String world, double amount) {
        return withdraw(p.getUniqueId(), amount);
    }

    @Override public EconomyResponse depositPlayer(String name, double amount) {
        return deposit(id(name), amount);
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer p, double amount) {
        return deposit(p.getUniqueId(), amount);
    }
    @Override public EconomyResponse depositPlayer(String name, String world, double amount) {
        return deposit(id(name), amount);
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer p, String world, double amount) {
        return deposit(p.getUniqueId(), amount);
    }

    private EconomyResponse withdraw(UUID uuid, double amount) {
        if (amount < 0) return fail("Cannot withdraw negative amounts");
        if (!plugin.economy().withdraw(uuid, amount)) return fail("Insufficient funds");
        return ok(amount, plugin.economy().balance(uuid));
    }

    private EconomyResponse deposit(UUID uuid, double amount) {
        if (amount < 0) return fail("Cannot deposit negative amounts");
        plugin.economy().deposit(uuid, amount);
        return ok(amount, plugin.economy().balance(uuid));
    }

    private EconomyResponse ok(double amount, double bal) {
        return new EconomyResponse(amount, bal, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse fail(String why) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, why);
    }

    // ---- accounts / banks: not needed ----
    @Override public boolean createPlayerAccount(String name) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer p) { return true; }
    @Override public boolean createPlayerAccount(String name, String world) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer p, String world) { return true; }
    @Override public EconomyResponse createBank(String name, String player) { return fail("No banks"); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return fail("No banks"); }
    @Override public EconomyResponse deleteBank(String name) { return fail("No banks"); }
    @Override public EconomyResponse bankBalance(String name) { return fail("No banks"); }
    @Override public EconomyResponse bankHas(String name, double amount) { return fail("No banks"); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return fail("No banks"); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return fail("No banks"); }
    @Override public EconomyResponse isBankOwner(String name, String player) { return fail("No banks"); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return fail("No banks"); }
    @Override public EconomyResponse isBankMember(String name, String player) { return fail("No banks"); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return fail("No banks"); }
    @Override public List<String> getBanks() { return List.of(); }
}
