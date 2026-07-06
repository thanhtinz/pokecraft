package dev.thanhtin.pokecraft.storage;

import com.google.gson.Gson;
import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StorageManager {
    private final PokeCraftPlugin plugin;
    private final Gson gson = new Gson();
    private Connection connection;

    public StorageManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() throws SQLException {
        String type = plugin.getConfig().getString("storage.type", "sqlite");
        if (type.equalsIgnoreCase("mysql")) {
            connection = DriverManager.getConnection(plugin.getConfig().getString("storage.mysql-url", ""));
        } else {
            File db = new File(plugin.getDataFolder(), "pokecraft.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
        }
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pokemon (
                    uuid TEXT PRIMARY KEY,
                    owner TEXT NOT NULL,
                    slot INTEGER NOT NULL DEFAULT -1,
                    data TEXT NOT NULL
                )""");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_owner ON pokemon(owner)");
        }
        plugin.getLogger().info("[OK] Storage initialized (" + type + ")");
    }

    public void shutdown() {
        try { if (connection != null) connection.close(); }
        catch (SQLException e) { plugin.getLogger().warning("[WARN] Close failed: " + e.getMessage()); }
    }

    /** slot 0-5 = party, -1 = PC box */
    public synchronized void save(PokemonInstance p, int slot) {
        String sql = "INSERT INTO pokemon(uuid, owner, slot, data) VALUES(?,?,?,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET owner=excluded.owner, slot=excluded.slot, data=excluded.data";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, p.uuid.toString());
            ps.setString(2, p.owner == null ? "" : p.owner.toString());
            ps.setInt(3, slot);
            ps.setString(4, gson.toJson(p));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] Save pokemon failed: " + e.getMessage());
        }
    }

    public synchronized void delete(UUID pokemonId) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM pokemon WHERE uuid=?")) {
            ps.setString(1, pokemonId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] Delete pokemon failed: " + e.getMessage());
        }
    }

    public synchronized List<StoredPokemon> loadAll(UUID owner) {
        List<StoredPokemon> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT slot, data FROM pokemon WHERE owner=? ORDER BY slot")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PokemonInstance p = gson.fromJson(rs.getString("data"), PokemonInstance.class);
                    out.add(new StoredPokemon(p, rs.getInt("slot")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] Load pokemon failed: " + e.getMessage());
        }
        return out;
    }

    public record StoredPokemon(PokemonInstance pokemon, int slot) {}
}
