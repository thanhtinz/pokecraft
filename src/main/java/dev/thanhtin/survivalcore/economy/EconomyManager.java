package dev.thanhtin.survivalcore.economy;

import dev.thanhtin.survivalcore.SurvivalCore;

import java.util.UUID;

/** Money on top of the SQLite store, with a Vault bridge registered separately. */
public class EconomyManager {

    private final SurvivalCore plugin;

    public EconomyManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public double balance(UUID uuid) {
        return plugin.db().getBalance(uuid);
    }

    public void deposit(UUID uuid, double amount) {
        if (amount <= 0) return;
        plugin.db().setBalance(uuid, round(balance(uuid) + amount));
    }

    /** @return false when the balance is insufficient. */
    public boolean withdraw(UUID uuid, double amount) {
        if (amount <= 0) return true;
        double bal = balance(uuid);
        if (bal < amount) return false;
        plugin.db().setBalance(uuid, round(bal - amount));
        return true;
    }

    public void set(UUID uuid, double amount) {
        plugin.db().setBalance(uuid, round(Math.max(0, amount)));
    }

    public boolean has(UUID uuid, double amount) {
        return balance(uuid) >= amount;
    }

    public String symbol() {
        return plugin.getConfig().getString("economy.currency-symbol", "$");
    }

    public String format(double amount) {
        return symbol() + String.format("%,.2f", amount);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
