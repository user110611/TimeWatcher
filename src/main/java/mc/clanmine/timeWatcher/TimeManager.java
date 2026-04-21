package mc.clanmine.timeWatcher;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TimeManager {

    private final JavaPlugin plugin;
    private Connection connection;
    private final Map<UUID, Long> joinTimes     = new HashMap<>();
    private final Map<UUID, Long> tokenTimers   = new HashMap<>();
    private final Map<UUID, String> displayModes = new HashMap<>();

    public TimeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        File db = new File(plugin.getDataFolder(), "data.db");
        plugin.getDataFolder().mkdirs();
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
            Statement st = connection.createStatement();

            st.execute("""
                CREATE TABLE IF NOT EXISTS playtime (
                    uuid TEXT PRIMARY KEY,
                    name TEXT,
                    seconds INTEGER DEFAULT 0
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS tokens (
                    uuid TEXT PRIMARY KEY,
                    tokens REAL DEFAULT 0
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS links (
                    discord_id TEXT PRIMARY KEY,
                    minecraft_nick TEXT,
                    confirmed INTEGER DEFAULT 0
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS pending_links (
                    minecraft_nick TEXT PRIMARY KEY,
                    discord_id TEXT,
                    discord_name TEXT
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS token_timers (
                    uuid TEXT PRIMARY KEY,
                    last_token_time INTEGER DEFAULT 0
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS last_seen (
                    uuid TEXT PRIMARY KEY,
                    timestamp INTEGER DEFAULT 0
                )
            """);

            st.close();
            plugin.getLogger().info("[DB] Все таблицы успешно созданы/проверены");
        } catch (SQLException e) {
            plugin.getLogger().severe("[DB] Ошибка инициализации: " + e.getMessage());
        }
    }

    public void reload() {
        try { if (connection != null) connection.close(); } catch (SQLException ignored) {}
        init();
    }

    public void onJoin(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        joinTimes.put(uuid, now);

        plugin.getLogger().info("[TokenTimer] ---- " + player.getName() + " ВОШЁЛ ----");

        long savedTimer = loadTokenTimer(uuid);
        plugin.getLogger().info("[TokenTimer] savedTimer из БД: " + savedTimer);

        if (savedTimer == 0) {
            savedTimer = now;
            saveTokenTimer(uuid, savedTimer);
            plugin.getLogger().info("[TokenTimer] Первый вход — таймер начат с нуля");
        } else {
            long lastSeen = loadLastSeen(uuid);
            plugin.getLogger().info("[TokenTimer] lastSeen из БД: " + lastSeen);

            if (lastSeen > 0) {
                long offlineTime = now - lastSeen;
                plugin.getLogger().info("[TokenTimer] Оффлайн время (мс): " + offlineTime
                        + " (" + offlineTime / 1000 + " сек)");
                savedTimer += offlineTime;
                saveTokenTimer(uuid, savedTimer);
                plugin.getLogger().info("[TokenTimer] Таймер сдвинут на оффлайн время");
                plugin.getLogger().info("[TokenTimer] Новый savedTimer: " + savedTimer);
            } else {
                plugin.getLogger().warning("[TokenTimer] lastSeen = 0! Таблица last_seen пустая или не создалась!");
            }
        }

        tokenTimers.put(uuid, savedTimer);

        long remaining = 1_800_000L - (now - savedTimer);
        long minutes = Math.max(0, remaining) / 1000 / 60;
        long seconds = (Math.max(0, remaining) / 1000) % 60;
        plugin.getLogger().info("[TokenTimer] Осталось до токена: " + minutes + "м " + seconds + "с");
        plugin.getLogger().info("[TokenTimer] -------------------------");

        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO playtime (uuid, name, seconds) VALUES (?, ?, 0)");
            ps.setString(1, uuid.toString());
            ps.setString(2, player.getName());
            ps.executeUpdate(); ps.close();

            ps = connection.prepareStatement("UPDATE playtime SET name = ? WHERE uuid = ?");
            ps.setString(1, player.getName());
            ps.setString(2, uuid.toString());
            ps.executeUpdate(); ps.close();

            ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO tokens (uuid, tokens) VALUES (?, 0)");
            ps.setString(1, uuid.toString());
            ps.executeUpdate(); ps.close();

        } catch (SQLException e) {
            plugin.getLogger().severe("[TokenTimer] onJoin SQL error: " + e.getMessage());
        }
    }

    public void saveSession(Player player) {
        UUID uuid = player.getUniqueId();
        Long joinTime = joinTimes.remove(uuid);
        tokenTimers.remove(uuid);

        long now = System.currentTimeMillis();
        plugin.getLogger().info("[TokenTimer] ---- " + player.getName() + " ВЫШЕЛ ----");
        plugin.getLogger().info("[TokenTimer] Сохраняем lastSeen = " + now);
        saveLastSeen(uuid, now);
        plugin.getLogger().info("[TokenTimer] lastSeen сохранён успешно");
        plugin.getLogger().info("[TokenTimer] -------------------------");

        if (joinTime == null) return;

        long seconds = (now - joinTime) / 1000;
        plugin.getLogger().info("[TokenTimer] Сессия длилась: " + seconds + " сек");

        try {
            PreparedStatement ps = connection.prepareStatement(
                    "UPDATE playtime SET seconds = seconds + ? WHERE uuid = ?");
            ps.setLong(1, seconds);
            ps.setString(2, uuid.toString());
            ps.executeUpdate(); ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("[TokenTimer] saveSession SQL error: " + e.getMessage());
        }
    }

    public boolean shouldGetToken(Player player) {
        UUID uuid = player.getUniqueId();
        Long last = tokenTimers.get(uuid);
        if (last == null) return false;

        long elapsed = System.currentTimeMillis() - last;
        if (elapsed >= 1_800_000L) {
            long newTime = System.currentTimeMillis();
            tokenTimers.put(uuid, newTime);
            saveTokenTimer(uuid, newTime);
            plugin.getLogger().info("[TokenTimer] " + player.getName() + " получил токен!");
            return true;
        }
        return false;
    }

    public long getMillisUntilNextToken(Player player) {
        Long last = tokenTimers.get(player.getUniqueId());
        if (last == null) return -1;
        long remaining = 1_800_000L - (System.currentTimeMillis() - last);
        return Math.max(0, remaining);
    }

    // ── token timer ──────────────────────────────────────────────────

    private void saveTokenTimer(UUID uuid, long timestamp) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO token_timers (uuid, last_token_time) VALUES (?, ?)
                ON CONFLICT(uuid) DO UPDATE SET last_token_time = ?
            """);
            ps.setString(1, uuid.toString());
            ps.setLong(2, timestamp);
            ps.setLong(3, timestamp);
            ps.executeUpdate(); ps.close();
            plugin.getLogger().info("[TokenTimer] saveTokenTimer OK: " + timestamp);
        } catch (SQLException e) {
            plugin.getLogger().severe("[TokenTimer] saveTokenTimer error: " + e.getMessage());
        }
    }

    private long loadTokenTimer(UUID uuid) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT last_token_time FROM token_timers WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            long val = rs.next() ? rs.getLong("last_token_time") : 0;
            rs.close(); ps.close();
            plugin.getLogger().info("[TokenTimer] loadTokenTimer result: " + val);
            return val;
        } catch (SQLException e) {
            plugin.getLogger().severe("[TokenTimer] loadTokenTimer error: " + e.getMessage());
            return 0;
        }
    }

    // ── last seen ────────────────────────────────────────────────────

    private void saveLastSeen(UUID uuid, long timestamp) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO last_seen (uuid, timestamp) VALUES (?, ?)
                ON CONFLICT(uuid) DO UPDATE SET timestamp = ?
            """);
            ps.setString(1, uuid.toString());
            ps.setLong(2, timestamp);
            ps.setLong(3, timestamp);
            ps.executeUpdate(); ps.close();
            plugin.getLogger().info("[TokenTimer] saveLastSeen OK: " + timestamp);
        } catch (SQLException e) {
            plugin.getLogger().severe("[TokenTimer] saveLastSeen error: " + e.getMessage());
        }
    }

    private long loadLastSeen(UUID uuid) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT timestamp FROM last_seen WHERE uuid = ?");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            long val = rs.next() ? rs.getLong("timestamp") : 0;
            rs.close(); ps.close();
            plugin.getLogger().info("[TokenTimer] loadLastSeen result: " + val);
            return val;
        } catch (SQLException e) {
            plugin.getLogger().severe("[TokenTimer] loadLastSeen error: " + e.getMessage());
            return 0;
        }
    }

    // ── остальные методы ─────────────────────────────────────────────

    public long getSeconds(Player player) {
        long saved = 0;
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT seconds FROM playtime WHERE uuid = ?");
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) saved = rs.getLong("seconds");
            rs.close(); ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("getSeconds error: " + e.getMessage());
        }
        long live = 0;
        if (joinTimes.containsKey(player.getUniqueId())) {
            live = (System.currentTimeMillis() - joinTimes.get(player.getUniqueId())) / 1000;
        }
        return saved + live;
    }

    public double getTokens(Player player) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT tokens FROM tokens WHERE uuid = ?");
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            double val = rs.next() ? rs.getDouble("tokens") : 0;
            rs.close(); ps.close();
            return val;
        } catch (SQLException e) {
            plugin.getLogger().severe("getTokens error: " + e.getMessage());
            return 0;
        }
    }

    public void addTokens(Player player, double amount) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO tokens (uuid, tokens) VALUES (?, ?)
                ON CONFLICT(uuid) DO UPDATE SET tokens = tokens + ?
            """);
            ps.setString(1, player.getUniqueId().toString());
            ps.setDouble(2, amount);
            ps.setDouble(3, amount);
            ps.executeUpdate(); ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("addTokens error: " + e.getMessage());
        }
    }

    public boolean resetPlayer(String name) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid FROM playtime WHERE name = ?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { rs.close(); ps.close(); return false; }
            String uuid = rs.getString("uuid");
            rs.close(); ps.close();

            ps = connection.prepareStatement("UPDATE playtime SET seconds = 0 WHERE uuid = ?");
            ps.setString(1, uuid); ps.executeUpdate(); ps.close();
            ps = connection.prepareStatement("UPDATE tokens SET tokens = 0 WHERE uuid = ?");
            ps.setString(1, uuid); ps.executeUpdate(); ps.close();
            ps = connection.prepareStatement("DELETE FROM token_timers WHERE uuid = ?");
            ps.setString(1, uuid); ps.executeUpdate(); ps.close();
            ps = connection.prepareStatement("DELETE FROM last_seen WHERE uuid = ?");
            ps.setString(1, uuid); ps.executeUpdate(); ps.close();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("resetPlayer error: " + e.getMessage());
            return false;
        }
    }

    public boolean hasPendingLink(String nick) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM pending_links WHERE minecraft_nick = ?");
            ps.setString(1, nick);
            ResultSet rs = ps.executeQuery();
            boolean has = rs.next();
            rs.close(); ps.close();
            return has;
        } catch (SQLException e) { return false; }
    }

    public boolean confirmLink(Player player) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT discord_id FROM pending_links WHERE minecraft_nick = ?");
            ps.setString(1, player.getName());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { rs.close(); ps.close(); return false; }
            String discordId = rs.getString("discord_id");
            rs.close(); ps.close();

            ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO links (discord_id, minecraft_nick, confirmed) VALUES (?, ?, 1)");
            ps.setString(1, discordId);
            ps.setString(2, player.getName());
            ps.executeUpdate(); ps.close();

            ps = connection.prepareStatement(
                    "DELETE FROM pending_links WHERE minecraft_nick = ?");
            ps.setString(1, player.getName());
            ps.executeUpdate(); ps.close();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("confirmLink error: " + e.getMessage());
            return false;
        }
    }

    public String getMode(Player player) {
        return displayModes.getOrDefault(player.getUniqueId(), "default");
    }

    public void setMode(Player player, String mode) {
        displayModes.put(player.getUniqueId(), mode);
    }

    public Long getJoinTime(Player player) {
        return joinTimes.get(player.getUniqueId());
    }

    public boolean modifyTokensByNick(String name, double amount) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid FROM playtime WHERE name = ?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { rs.close(); ps.close(); return false; }
            String uuid = rs.getString("uuid");
            rs.close(); ps.close();

            ps = connection.prepareStatement("""
                INSERT INTO tokens (uuid, tokens) VALUES (?, ?)
                ON CONFLICT(uuid) DO UPDATE SET tokens = tokens + ?
            """);
            ps.setString(1, uuid);
            ps.setDouble(2, amount);
            ps.setDouble(3, amount);
            ps.executeUpdate(); ps.close();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("modifyTokensByNick error: " + e.getMessage());
            return false;
        }
    }

    public boolean setTokensByNick(String name, double amount) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid FROM playtime WHERE name = ?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { rs.close(); ps.close(); return false; }
            String uuid = rs.getString("uuid");
            rs.close(); ps.close();

            ps = connection.prepareStatement(
                    "INSERT INTO tokens (uuid, tokens) VALUES (?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET tokens = ?");
            ps.setString(1, uuid);
            ps.setDouble(2, amount);
            ps.setDouble(3, amount);
            ps.executeUpdate(); ps.close();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("setTokensByNick error: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        try { if (connection != null) connection.close(); } catch (SQLException ignored) {}
    }
}