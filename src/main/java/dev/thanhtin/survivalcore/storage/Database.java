package dev.thanhtin.survivalcore.storage;

import dev.thanhtin.survivalcore.SurvivalCore;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite storage for all SurvivalCore data. One file, created on first run.
 * Every access is synchronized on the single connection - fine for the volumes
 * a survival server sees, and keeps the code simple and safe.
 */
public class Database {

    private final SurvivalCore plugin;
    private Connection conn;

    public Database(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public void open() throws SQLException {
        File file = new File(plugin.getDataFolder(), "survivalcore.db");
        file.getParentFile().mkdirs();
        conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("CREATE TABLE IF NOT EXISTS players(" +
                    "uuid TEXT PRIMARY KEY, name TEXT, balance REAL NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS homes(" +
                    "uuid TEXT, name TEXT, world TEXT, x REAL, y REAL, z REAL, yaw REAL, pitch REAL, " +
                    "PRIMARY KEY(uuid, name))");
            st.execute("CREATE TABLE IF NOT EXISTS warps(" +
                    "name TEXT PRIMARY KEY, world TEXT, x REAL, y REAL, z REAL, yaw REAL, pitch REAL)");
            st.execute("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT)");
            st.execute("CREATE TABLE IF NOT EXISTS claims(" +
                    "world TEXT, cx INTEGER, cz INTEGER, owner TEXT, " +
                    "PRIMARY KEY(world, cx, cz))");
            st.execute("CREATE TABLE IF NOT EXISTS claim_trust(" +
                    "owner TEXT, trusted TEXT, PRIMARY KEY(owner, trusted))");
            st.execute("CREATE TABLE IF NOT EXISTS auctions(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, seller TEXT, seller_name TEXT, " +
                    "item TEXT, price REAL, listed_at INTEGER)");
            st.execute("CREATE TABLE IF NOT EXISTS keys(" +
                    "uuid TEXT, crate TEXT, amount INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(uuid, crate))");
            st.execute("CREATE TABLE IF NOT EXISTS vaults(" +
                    "uuid TEXT, page INTEGER, data TEXT, PRIMARY KEY(uuid, page))");
            st.execute("CREATE TABLE IF NOT EXISTS kit_cooldowns(" +
                    "uuid TEXT, kit TEXT, next_time INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(uuid, kit))");
            st.execute("CREATE TABLE IF NOT EXISTS job_data(" +
                    "uuid TEXT, job TEXT, xp REAL NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(uuid, job))");
        }
    }

    // ---------- jobs ----------

    public synchronized boolean hasJob(UUID uuid, String job) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM job_data WHERE uuid=? AND job=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, job);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized void joinJob(UUID uuid, String job) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO job_data(uuid,job,xp) VALUES(?,?,0)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, job);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public synchronized void leaveJob(UUID uuid, String job) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM job_data WHERE uuid=? AND job=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, job);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public synchronized List<String> joinedJobs(UUID uuid) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT job FROM job_data WHERE uuid=? ORDER BY job")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException ignored) {}
        return out;
    }

    public synchronized int jobCount(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM job_data WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public synchronized double getJobXp(UUID uuid, String job) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT xp FROM job_data WHERE uuid=? AND job=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, job);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    /** Add XP to a joined job and return the new total (0 if not joined). */
    public synchronized double addJobXp(UUID uuid, String job, double xp) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE job_data SET xp=xp+? WHERE uuid=? AND job=?")) {
            ps.setDouble(1, xp);
            ps.setString(2, uuid.toString());
            ps.setString(3, job);
            if (ps.executeUpdate() == 0) return 0;
        } catch (SQLException e) {
            return 0;
        }
        return getJobXp(uuid, job);
    }

    // ---------- player vaults ----------

    public synchronized String getVault(UUID uuid, int page) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT data FROM vaults WHERE uuid=? AND page=?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, page);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized void setVault(UUID uuid, int page, String data) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO vaults(uuid,page,data) VALUES(?,?,?) " +
                        "ON CONFLICT(uuid,page) DO UPDATE SET data=excluded.data")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, page);
            ps.setString(3, data);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("setVault failed: " + e.getMessage());
        }
    }

    // ---------- kit cooldowns ----------

    public synchronized long getKitCooldown(UUID uuid, String kit) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT next_time FROM kit_cooldowns WHERE uuid=? AND kit=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public synchronized void setKitCooldown(UUID uuid, String kit, long nextTime) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO kit_cooldowns(uuid,kit,next_time) VALUES(?,?,?) " +
                        "ON CONFLICT(uuid,kit) DO UPDATE SET next_time=excluded.next_time")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            ps.setLong(3, nextTime);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // ---------- ranks (ladder) ----------

    public String getRank(UUID uuid) { return getMeta("rank:" + uuid); }

    public void setRank(UUID uuid, String rank) { setMeta("rank:" + uuid, rank); }

    // ---------- crate keys (virtual) ----------

    public synchronized int getKeys(UUID uuid, String crate) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT amount FROM keys WHERE uuid=? AND crate=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, crate);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public synchronized void addKeys(UUID uuid, String crate, int amount) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO keys(uuid,crate,amount) VALUES(?,?,?) " +
                        "ON CONFLICT(uuid,crate) DO UPDATE SET amount=amount+excluded.amount")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, crate);
            ps.setInt(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("addKeys failed: " + e.getMessage());
        }
    }

    /** Atomically consume one key; false when the player has none. */
    public synchronized boolean takeKey(UUID uuid, String crate) {
        if (getKeys(uuid, crate) <= 0) return false;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE keys SET amount=amount-1 WHERE uuid=? AND crate=? AND amount>0")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, crate);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // ---------- auction house ----------

    public record Auction(long id, UUID seller, String sellerName, String itemBase64,
                          double price, long listedAt) {}

    public synchronized long addAuction(UUID seller, String sellerName, String itemBase64, double price) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO auctions(seller,seller_name,item,price,listed_at) VALUES(?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, seller.toString());
            ps.setString(2, sellerName);
            ps.setString(3, itemBase64);
            ps.setDouble(4, price);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("addAuction failed: " + e.getMessage());
            return -1;
        }
    }

    public synchronized List<Auction> listAuctions(int limit, int offset) {
        List<Auction> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id,seller,seller_name,item,price,listed_at FROM auctions " +
                        "ORDER BY listed_at DESC LIMIT ? OFFSET ?")) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(readAuction(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("listAuctions failed: " + e.getMessage());
        }
        return out;
    }

    public synchronized List<Auction> auctionsBySeller(UUID seller) {
        List<Auction> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id,seller,seller_name,item,price,listed_at FROM auctions " +
                        "WHERE seller=? ORDER BY listed_at DESC")) {
            ps.setString(1, seller.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(readAuction(rs));
            }
        } catch (SQLException ignored) {}
        return out;
    }

    public synchronized Auction getAuction(long id) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id,seller,seller_name,item,price,listed_at FROM auctions WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readAuction(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    /** Delete a listing; returns true only if it still existed (guards double-buy). */
    public synchronized boolean removeAuction(long id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM auctions WHERE id=?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized int auctionCount() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM auctions")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private Auction readAuction(ResultSet rs) throws SQLException {
        return new Auction(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3),
                rs.getString(4), rs.getDouble(5), rs.getLong(6));
    }

    // ---------- land claims (chunk based) ----------

    public synchronized UUID claimOwner(String world, int cx, int cz) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT owner FROM claims WHERE world=? AND cx=? AND cz=?")) {
            ps.setString(1, world);
            ps.setInt(2, cx);
            ps.setInt(3, cz);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString(1)) : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized boolean addClaim(String world, int cx, int cz, UUID owner) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO claims(world,cx,cz,owner) VALUES(?,?,?,?)")) {
            ps.setString(1, world);
            ps.setInt(2, cx);
            ps.setInt(3, cz);
            ps.setString(4, owner.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean removeClaim(String world, int cx, int cz) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM claims WHERE world=? AND cx=? AND cz=?")) {
            ps.setString(1, world);
            ps.setInt(2, cx);
            ps.setInt(3, cz);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized int claimCount(UUID owner) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM claims WHERE owner=?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public synchronized void addTrust(UUID owner, UUID trusted) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO claim_trust(owner,trusted) VALUES(?,?)")) {
            ps.setString(1, owner.toString());
            ps.setString(2, trusted.toString());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public synchronized boolean removeTrust(UUID owner, UUID trusted) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM claim_trust WHERE owner=? AND trusted=?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, trusted.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean isTrusted(UUID owner, UUID trusted) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM claim_trust WHERE owner=? AND trusted=?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, trusted.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized List<String> trustedNames(UUID owner) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT p.name FROM claim_trust t JOIN players p ON p.uuid=t.trusted WHERE t.owner=?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException ignored) {}
        return out;
    }

    public synchronized String nameByUuid(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }

    // ---------- players / economy ----------

    public synchronized void ensurePlayer(UUID uuid, String name, double startBalance) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO players(uuid, name, balance) VALUES(?,?,?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setDouble(3, startBalance);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("ensurePlayer failed: " + e.getMessage());
        }
    }

    public synchronized double getBalance(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT balance FROM players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("getBalance failed: " + e.getMessage());
            return 0;
        }
    }

    public synchronized void setBalance(UUID uuid, double amount) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE players SET balance=? WHERE uuid=?")) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("setBalance failed: " + e.getMessage());
        }
    }

    public record TopEntry(String name, double balance) {}

    public synchronized List<TopEntry> topBalances(int limit) {
        List<TopEntry> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name, balance FROM players ORDER BY balance DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new TopEntry(rs.getString(1), rs.getDouble(2)));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("topBalances failed: " + e.getMessage());
        }
        return out;
    }

    public synchronized UUID uuidByName(String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid FROM players WHERE lower(name)=lower(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString(1)) : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- homes ----------

    public synchronized void setHome(UUID uuid, String name, Location loc) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO homes(uuid,name,world,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?,?) " +
                        "ON CONFLICT(uuid,name) DO UPDATE SET world=excluded.world, x=excluded.x, " +
                        "y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch")) {
            bindLoc(ps, 3, loc);
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("setHome failed: " + e.getMessage());
        }
    }

    public synchronized boolean deleteHome(UUID uuid, String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM homes WHERE uuid=? AND name=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized Location getHome(UUID uuid, String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT world,x,y,z,yaw,pitch FROM homes WHERE uuid=? AND name=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readLoc(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized List<String> homeNames(UUID uuid) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM homes WHERE uuid=? ORDER BY name")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException ignored) {}
        return out;
    }

    public synchronized int homeCount(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM homes WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    // ---------- warps ----------

    public synchronized void setWarp(String name, Location loc) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO warps(name,world,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?) " +
                        "ON CONFLICT(name) DO UPDATE SET world=excluded.world, x=excluded.x, " +
                        "y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch")) {
            bindLoc(ps, 2, loc);
            ps.setString(1, name.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("setWarp failed: " + e.getMessage());
        }
    }

    public synchronized boolean deleteWarp(String name) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM warps WHERE name=?")) {
            ps.setString(1, name.toLowerCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized Location getWarp(String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT world,x,y,z,yaw,pitch FROM warps WHERE name=?")) {
            ps.setString(1, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readLoc(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized List<String> warpNames() {
        List<String> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM warps ORDER BY name")) {
            while (rs.next()) out.add(rs.getString(1));
        } catch (SQLException ignored) {}
        return out;
    }

    // ---------- meta (spawn etc.) ----------

    public synchronized void setMeta(String key, String value) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO meta(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public synchronized String getMeta(String key) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT v FROM meta WHERE k=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public void setSpawn(Location loc) {
        setMeta("spawn", serializeLoc(loc));
    }

    public Location getSpawn() {
        String s = getMeta("spawn");
        return s == null ? null : deserializeLoc(s);
    }

    // ---------- location helpers ----------

    private void bindLoc(PreparedStatement ps, int from, Location loc) throws SQLException {
        ps.setString(from, loc.getWorld().getName());
        ps.setDouble(from + 1, loc.getX());
        ps.setDouble(from + 2, loc.getY());
        ps.setDouble(from + 3, loc.getZ());
        ps.setDouble(from + 4, loc.getYaw());
        ps.setDouble(from + 5, loc.getPitch());
    }

    private Location readLoc(ResultSet rs) throws SQLException {
        World world = plugin.getServer().getWorld(rs.getString(1));
        if (world == null) return null;
        return new Location(world, rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                (float) rs.getDouble(5), (float) rs.getDouble(6));
    }

    private String serializeLoc(Location loc) {
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ()
                + "," + loc.getYaw() + "," + loc.getPitch();
    }

    private Location deserializeLoc(String s) {
        String[] p = s.split(",");
        World world = plugin.getServer().getWorld(p[0]);
        if (world == null || p.length < 6) return null;
        return new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
    }
}
