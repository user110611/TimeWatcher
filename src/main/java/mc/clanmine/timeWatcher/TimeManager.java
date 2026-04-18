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
    private final Map<UUID, Long> joinTimes = new HashMap<>();
    private final Map<UUID, String> displayModes = new HashMap<>();

    public TimeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "data.db");
            plugin.getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            Statement stmt = connection.createStatement();
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS playtime (
                    uuid TEXT PRIMARY KEY,
                    name TEXT,
                    seconds INTEGER DEFAULT 0
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tokens (
                    uuid TEXT PRIMARY KEY,
                    tokens REAL DEFAULT 0
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS links (
                    discord_id TEXT PRIMARY KEY,
                    minecraft_nick TEXT,
                    confirmed INTEGER DEFAULT 0
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pending_links (
                    minecraft_nick TEXT PRIMARY KEY,
                    discord_id TEXT,
                    discord_name TEXT
                )
            """);
            stmt.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка БД: " + e.getMessage());
        }
    }

    public void reload() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {}
        init();
    }

    public void onJoin(Player player) {
        joinTimes.put(player.getUniqueId(), System.currentTimeMillis());
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO playtime (uuid, name, seconds) VALUES (?, ?, 0)"
            );
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, player.getName());
            ps.executeUpdate();
            ps.close();

            PreparedStatement upd = connection.prepareStatement(
                    "UPDATE playtime SET name = ? WHERE uuid = ?"
            );
            upd.setString(1, player.getName());
            upd.setString(2, player.getUniqueId().toString());
            upd.executeUpdate();
            upd.close();

            PreparedStatement tok = connection.prepareStatement(
                    "INSERT OR IGNORE INTO tokens (uuid, tokens) VALUES (?, 0)"
            );
            tok.setString(1, player.getUniqueId().toString());
            tok.executeUpdate();
            tok.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при входе: " + e.getMessage());
        }
    }

    public void saveSession(Player player) {
        Long joinTime = joinTimes.remove(player.getUniqueId());
        if (joinTime == null) return;
        long sessionSeconds = (System.currentTimeMillis() - joinTime) / 1000;
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "UPDATE playtime SET seconds = seconds + ? WHERE uuid = ?"
            );
            ps.setLong(1, sessionSeconds);
            ps.setString(2, player.getUniqueId().toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка сохранения: " + e.getMessage());
        }
    }

    public long getSeconds(Player player) {
        try {
            long saved = 0;
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT seconds FROM playtime WHERE uuid = ?"
            );
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) saved = rs.getLong("seconds");
            rs.close(); ps.close();
            long sessionSeconds = 0;
            if (joinTimes.containsKey(player.getUniqueId())) {
                sessionSeconds = (System.currentTimeMillis() - joinTimes.get(player.getUniqueId())) / 1000;
            }
            return saved + sessionSeconds;
        } catch (SQLException e) { return 0; }
    }

    public double getTokens(Player player) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT tokens FROM tokens WHERE uuid = ?"
            );
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            double result = rs.next() ? rs.getDouble("tokens") : 0;
            rs.close(); ps.close();
            return result;
        } catch (SQLException e) { return 0; }
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
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка токенов: " + e.getMessage());
        }
    }

    public boolean resetPlayer(String nick) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid FROM playtime WHERE name = ?"
            );
            ps.setString(1, nick);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { rs.close(); ps.close(); return false; }
            String uuid = rs.getString("uuid");
            rs.close(); ps.close();

            PreparedStatement r1 = connection.prepareStatement(
                    "UPDATE playtime SET seconds = 0 WHERE uuid = ?"
            );
            r1.setString(1, uuid);
            r1.executeUpdate();
            r1.close();

            PreparedStatement r2 = connection.prepareStatement(
                    "UPDATE tokens SET tokens = 0 WHERE uuid = ?"
            );
            r2.setString(1, uuid);
            r2.executeUpdate();
            r2.close();

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка сброса: " + e.getMessage());
            return false;
        }
    }

    public boolean hasPendingLink(String nick) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT minecraft_nick FROM pending_links WHERE minecraft_nick = ?"
            );
            ps.setString(1, nick);
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next();
            rs.close(); ps.close();
            return exists;
        } catch (SQLException e) { return false; }
    }

    public boolean confirmLink(Player player) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT discord_id FROM pending_links WHERE minecraft_nick = ?"
            );
            ps.setString(1, player.getName());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { rs.close(); ps.close(); return false; }
            String discordId = rs.getString("discord_id");
            rs.close(); ps.close();

            PreparedStatement ins = connection.prepareStatement("""
                INSERT INTO links (discord_id, minecraft_nick, confirmed) VALUES (?, ?, 1)
                ON CONFLICT(discord_id) DO UPDATE SET minecraft_nick = ?, confirmed = 1
            """);
            ins.setString(1, discordId);
            ins.setString(2, player.getName());
            ins.setString(3, player.getName());
            ins.executeUpdate();
            ins.close();

            PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM pending_links WHERE minecraft_nick = ?"
            );
            del.setString(1, player.getName());
            del.executeUpdate();
            del.close();

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка подтверждения: " + e.getMessage());
            return false;
        }
    }

    public String getMode(Player player) {
        return displayModes.getOrDefault(player.getUniqueId(), "menu");
    }

    public void setMode(Player player, String mode) {
        displayModes.put(player.getUniqueId(), mode);
    }

    public Long getJoinTime(Player player) {
        return joinTimes.get(player.getUniqueId());
    }

    public void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка закрытия БД: " + e.getMessage());
        }
    }
}