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
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS dailies (
                    uuid TEXT PRIMARY KEY,
                    last_day INTEGER NOT NULL DEFAULT 0,
                    streak INTEGER NOT NULL DEFAULT 0
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS quests (
                    owner TEXT NOT NULL,
                    quest_id TEXT NOT NULL,
                    day INTEGER NOT NULL,
                    progress INTEGER NOT NULL DEFAULT 0,
                    claimed INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(owner, quest_id)
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS guilds (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    owner TEXT NOT NULL,
                    bank INTEGER NOT NULL DEFAULT 0
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS guild_members (
                    uuid TEXT PRIMARY KEY,
                    guild_id INTEGER NOT NULL,
                    name TEXT NOT NULL DEFAULT ''
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ranks (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL DEFAULT '',
                    points INTEGER NOT NULL DEFAULT 0,
                    season INTEGER NOT NULL DEFAULT 1
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS meta (
                    k TEXT PRIMARY KEY,
                    v TEXT NOT NULL
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS farms (
                    loc TEXT PRIMARY KEY,
                    owner TEXT NOT NULL,
                    planted_at INTEGER NOT NULL
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

    /** Reads a stat column (balance/caught/wild_wins/pvp_wins) for one player. */
    public synchronized long getStat(UUID uuid, String column) {
        if (!STAT_COLUMNS.contains(column)) throw new IllegalArgumentException(column);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + column + " FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] getStat failed: " + e.getMessage());
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

    // ---------- daily check-in ----------

    public record DailyRow(long lastDay, int streak) {}

    public synchronized DailyRow getDaily(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT last_day, streak FROM dailies WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new DailyRow(rs.getLong(1), rs.getInt(2));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] getDaily failed: " + e.getMessage());
        }
        return new DailyRow(0, 0);
    }

    public synchronized void setDaily(UUID uuid, long day, int streak) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO dailies(uuid, last_day, streak) VALUES(?,?,?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET last_day=excluded.last_day, streak=excluded.streak")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, day);
            ps.setInt(3, streak);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] setDaily failed: " + e.getMessage());
        }
    }

    // ---------- quests ----------

    public record QuestRow(String questId, long day, int progress, boolean claimed) {}

    public synchronized List<QuestRow> questsOf(UUID owner) {
        List<QuestRow> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT quest_id, day, progress, claimed FROM quests WHERE owner=?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new QuestRow(rs.getString(1), rs.getLong(2),
                            rs.getInt(3), rs.getInt(4) == 1));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] questsOf failed: " + e.getMessage());
        }
        return out;
    }

    /** Insert a fresh quest for the day (progress 0, unclaimed), replacing any old row. */
    public synchronized void resetQuest(UUID owner, String questId, long day) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO quests(owner, quest_id, day, progress, claimed) VALUES(?,?,?,0,0) " +
                        "ON CONFLICT(owner, quest_id) DO UPDATE SET day=excluded.day, progress=0, claimed=0")) {
            ps.setString(1, owner.toString());
            ps.setString(2, questId);
            ps.setLong(3, day);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] resetQuest failed: " + e.getMessage());
        }
    }

    public synchronized void addQuestProgress(UUID owner, String questId, int delta) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE quests SET progress = progress + ? WHERE owner=? AND quest_id=? AND claimed=0")) {
            ps.setInt(1, delta);
            ps.setString(2, owner.toString());
            ps.setString(3, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] addQuestProgress failed: " + e.getMessage());
        }
    }

    public synchronized void claimQuest(UUID owner, String questId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE quests SET claimed=1 WHERE owner=? AND quest_id=?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, questId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] claimQuest failed: " + e.getMessage());
        }
    }

    // ---------- farms (berry plots) ----------

    public record FarmRow(String loc, UUID owner, long plantedAt) {}

    public synchronized void addFarm(String loc, UUID owner, long plantedAt) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO farms(loc, owner, planted_at) VALUES(?,?,?)")) {
            ps.setString(1, loc);
            ps.setString(2, owner.toString());
            ps.setLong(3, plantedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] addFarm failed: " + e.getMessage());
        }
    }

    public synchronized void removeFarm(String loc) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM farms WHERE loc=?")) {
            ps.setString(1, loc);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] removeFarm failed: " + e.getMessage());
        }
    }

    public synchronized List<FarmRow> allFarms() {
        List<FarmRow> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT loc, owner, planted_at FROM farms")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new FarmRow(rs.getString(1),
                        UUID.fromString(rs.getString(2)), rs.getLong(3)));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] allFarms failed: " + e.getMessage());
        }
        return out;
    }

    // ---------- meta (key/value) ----------

    public synchronized String getMeta(String key, String def) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT v FROM meta WHERE k=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] getMeta failed: " + e.getMessage());
        }
        return def;
    }

    public synchronized void setMeta(String key, String value) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO meta(k, v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] setMeta failed: " + e.getMessage());
        }
    }

    // ---------- guilds ----------

    public record GuildRow(int id, String name, UUID owner, long bank) {}

    public synchronized GuildRow createGuild(String name, UUID owner) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO guilds(name, owner, bank) VALUES(?,?,0)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, owner.toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return new GuildRow(keys.getInt(1), name, owner, 0);
            }
        } catch (SQLException e) {
            return null; // name taken, etc.
        }
        return null;
    }

    public synchronized GuildRow getGuild(int id) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, owner, bank FROM guilds WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new GuildRow(rs.getInt(1), rs.getString(2),
                        UUID.fromString(rs.getString(3)), rs.getLong(4));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] getGuild failed: " + e.getMessage());
        }
        return null;
    }

    public synchronized GuildRow getGuildByName(String name) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, owner, bank FROM guilds WHERE name=? COLLATE NOCASE")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new GuildRow(rs.getInt(1), rs.getString(2),
                        UUID.fromString(rs.getString(3)), rs.getLong(4));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] getGuildByName failed: " + e.getMessage());
        }
        return null;
    }

    public synchronized List<GuildRow> allGuilds(int limit) {
        List<GuildRow> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, owner, bank FROM guilds ORDER BY bank DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new GuildRow(rs.getInt(1), rs.getString(2),
                        UUID.fromString(rs.getString(3)), rs.getLong(4)));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] allGuilds failed: " + e.getMessage());
        }
        return out;
    }

    public synchronized void setGuildBank(int id, long bank) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE guilds SET bank=? WHERE id=?")) {
            ps.setLong(1, bank);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] setGuildBank failed: " + e.getMessage());
        }
    }

    public synchronized void deleteGuild(int id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM guilds WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] deleteGuild failed: " + e.getMessage());
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM guild_members WHERE guild_id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] deleteGuild members failed: " + e.getMessage());
        }
    }

    /** guild id the player belongs to, or 0. */
    public synchronized int guildOf(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT guild_id FROM guild_members WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] guildOf failed: " + e.getMessage());
        }
        return 0;
    }

    public synchronized void addGuildMember(UUID uuid, int guildId, String name) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO guild_members(uuid, guild_id, name) VALUES(?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, guildId);
            ps.setString(3, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] addGuildMember failed: " + e.getMessage());
        }
    }

    public synchronized void removeGuildMember(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM guild_members WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] removeGuildMember failed: " + e.getMessage());
        }
    }

    public synchronized List<String> guildMemberNames(int guildId) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name FROM guild_members WHERE guild_id=? ORDER BY name")) {
            ps.setInt(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] guildMemberNames failed: " + e.getMessage());
        }
        return out;
    }

    // ---------- rank ladder ----------

    public record RankRow(String name, int points, int season) {}

    public synchronized RankRow getRank(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name, points, season FROM ranks WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new RankRow(rs.getString(1), rs.getInt(2), rs.getInt(3));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] getRank failed: " + e.getMessage());
        }
        return new RankRow("", 0, 0);
    }

    public synchronized void setRank(UUID uuid, String name, int points, int season) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO ranks(uuid, name, points, season) VALUES(?,?,?,?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, points=excluded.points, "
                        + "season=excluded.season")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, points);
            ps.setInt(4, season);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] setRank failed: " + e.getMessage());
        }
    }

    public synchronized List<TopEntry> topRanks(int season, int limit) {
        List<TopEntry> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name, points FROM ranks WHERE season=? AND points > 0 " +
                        "ORDER BY points DESC LIMIT ?")) {
            ps.setInt(1, season);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new TopEntry(rs.getString(1), rs.getLong(2)));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ERR] topRanks failed: " + e.getMessage());
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
