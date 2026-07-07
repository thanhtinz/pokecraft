package dev.thanhtin.pokecraft.storage;

import com.google.gson.Gson;
import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL DEFAULT '',
                    balance INTEGER NOT NULL DEFAULT 0,
                    caught INTEGER NOT NULL DEFAULT 0,
                    wild_wins INTEGER NOT NULL DEFAULT 0,
                    pvp_wins INTEGER NOT NULL DEFAULT 0
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS marriages (
                    a TEXT PRIMARY KEY,
                    b TEXT NOT NULL
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS daycare (
                    pokemon_uuid TEXT PRIMARY KEY,
                    owner TEXT NOT NULL,
                    deposited_at INTEGER NOT NULL
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS npcs (
                    entity_uuid TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    name TEXT NOT NULL,
                    data TEXT NOT NULL DEFAULT ''
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pokedex (
                    owner TEXT NOT NULL,
                    species TEXT NOT NULL,
                    caught INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(owner, species)
                )""");
        }
        plugin.getLogger().info("[OK] Storage initialized (" + type + ")");
    }

    public void shutdown() {
        try { if (connection != null) connection.close(); }
        catch (SQLException e) { plugin.getLogger().warning("[WARN] Close failed: " + e.getMessage()); }
    }

    /** Pokemon row slot meaning: 0-5 = party, -1 = PC box, -2 = daycare. */
    public static final int SLOT_PC = -1;
    public static final int SLOT_DAYCARE = -2;

    /** slot 0-5 = party, -1 = PC box, -2 = daycare */
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

    // ---------- players (economy + stats) ----------

    /** Valid stat columns for addStat/top - guards against SQL injection. */
    public static final java.util.Set<String> STAT_COLUMNS =
            java.util.Set.of("balance", "caught", "wild_wins", "pvp_wins");

    public synchronized void ensurePlayer(UUID uuid, String name, long startBalance) {
        String sql = "INSERT INTO players(uuid, name, balance) VALUES(?,?,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, startBalance);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] ensurePlayer failed: " + e.getMessage());
        }
    }

    public synchronized long getBalance(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT balance FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] getBalance failed: " + e.getMessage());
            return 0;
        }
    }

    /** Adds delta to a stat column (balance/caught/wild_wins/pvp_wins). */
    public synchronized void addStat(UUID uuid, String column, long delta) {
        if (!STAT_COLUMNS.contains(column)) throw new IllegalArgumentException(column);
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE players SET " + column + " = " + column + " + ? WHERE uuid=?")) {
            ps.setLong(1, delta);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] addStat failed: " + e.getMessage());
        }
    }

    public record TopEntry(String name, long value) {}

    public synchronized List<TopEntry> top(String column, int limit) {
        if (!STAT_COLUMNS.contains(column)) throw new IllegalArgumentException(column);
        List<TopEntry> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name, " + column + " FROM players WHERE " + column + " > 0 " +
                        "ORDER BY " + column + " DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new TopEntry(rs.getString(1), rs.getLong(2)));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] top failed: " + e.getMessage());
        }
        return out;
    }

    // ---------- marriages ----------

    /** @return spouse UUID or null */
    public synchronized UUID spouseOf(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT a, b FROM marriages WHERE a=? OR b=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID a = UUID.fromString(rs.getString(1));
                    UUID b = UUID.fromString(rs.getString(2));
                    return a.equals(uuid) ? b : a;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] spouseOf failed: " + e.getMessage());
        }
        return null;
    }

    public synchronized void setMarriage(UUID a, UUID b) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO marriages(a, b) VALUES(?,?)")) {
            ps.setString(1, a.toString());
            ps.setString(2, b.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] setMarriage failed: " + e.getMessage());
        }
    }

    public synchronized void removeMarriage(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM marriages WHERE a=? OR b=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] removeMarriage failed: " + e.getMessage());
        }
    }

    // ---------- daycare ----------

    public record DaycareEntry(UUID pokemonUuid, long depositedAt) {}

    public synchronized List<DaycareEntry> daycareOf(UUID owner) {
        List<DaycareEntry> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pokemon_uuid, deposited_at FROM daycare WHERE owner=? ORDER BY deposited_at")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DaycareEntry(UUID.fromString(rs.getString(1)), rs.getLong(2)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] daycareOf failed: " + e.getMessage());
        }
        return out;
    }

    public synchronized void addDaycare(UUID pokemonUuid, UUID owner, long depositedAt) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO daycare(pokemon_uuid, owner, deposited_at) VALUES(?,?,?)")) {
            ps.setString(1, pokemonUuid.toString());
            ps.setString(2, owner.toString());
            ps.setLong(3, depositedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] addDaycare failed: " + e.getMessage());
        }
    }

    public synchronized void removeDaycare(UUID pokemonUuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM daycare WHERE pokemon_uuid=?")) {
            ps.setString(1, pokemonUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] removeDaycare failed: " + e.getMessage());
        }
    }

    // ---------- pokedex ----------

    public synchronized void markSeen(UUID owner, String species) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO pokedex(owner, species, caught) VALUES(?,?,0)")) {
            ps.setString(1, owner.toString());
            ps.setString(2, species);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] markSeen failed: " + e.getMessage());
        }
    }

    public synchronized void markCaught(UUID owner, String species) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pokedex(owner, species, caught) VALUES(?,?,1) " +
                        "ON CONFLICT(owner, species) DO UPDATE SET caught=1")) {
            ps.setString(1, owner.toString());
            ps.setString(2, species);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] markCaught failed: " + e.getMessage());
        }
    }

    /** species id -> caught (true) or only seen (false) */
    public synchronized Map<String, Boolean> pokedexOf(UUID owner) {
        Map<String, Boolean> out = new java.util.HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT species, caught FROM pokedex WHERE owner=?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString(1), rs.getInt(2) == 1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] pokedexOf failed: " + e.getMessage());
        }
        return out;
    }

    // ---------- npcs ----------

    public record NpcRow(String type, String name, String data) {}

    public synchronized void saveNpc(UUID entityUuid, String type, String name, String data) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO npcs(entity_uuid, type, name, data) VALUES(?,?,?,?)")) {
            ps.setString(1, entityUuid.toString());
            ps.setString(2, type);
            ps.setString(3, name);
            ps.setString(4, data == null ? "" : data);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] saveNpc failed: " + e.getMessage());
        }
    }

    public synchronized NpcRow getNpc(UUID entityUuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT type, name, data FROM npcs WHERE entity_uuid=?")) {
            ps.setString(1, entityUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new NpcRow(rs.getString(1), rs.getString(2), rs.getString(3));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] getNpc failed: " + e.getMessage());
        }
        return null;
    }

    public synchronized void deleteNpc(UUID entityUuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM npcs WHERE entity_uuid=?")) {
            ps.setString(1, entityUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] deleteNpc failed: " + e.getMessage());
        }
    }

    /** Load one pokemon row by uuid (used by daycare withdraw). */
    public synchronized PokemonInstance loadPokemon(UUID pokemonUuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT data FROM pokemon WHERE uuid=?")) {
            ps.setString(1, pokemonUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return gson.fromJson(rs.getString(1), PokemonInstance.class);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] loadPokemon failed: " + e.getMessage());
        }
        return null;
    }
}
