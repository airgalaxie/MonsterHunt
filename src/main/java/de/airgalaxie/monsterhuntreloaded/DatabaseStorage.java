package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Single SQLite persistence boundary. All methods are serialized on the connection. */
public final class DatabaseStorage implements AutoCloseable {
    private static final int SCHEMA_VERSION = 1;
    private static final DateTimeFormatter BACKUP_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd_HHmmss").withZone(ZoneId.systemDefault());

    private final MonsterHuntPlugin plugin;
    private final Connection connection;
    private final Path databaseFile;
    private final boolean killEventsEnabled;

    private DatabaseStorage(MonsterHuntPlugin plugin, Connection connection, Path databaseFile,
                            boolean killEventsEnabled) {
        this.plugin = plugin;
        this.connection = connection;
        this.databaseFile = databaseFile;
        this.killEventsEnabled = killEventsEnabled;
    }

    public static DatabaseStorage open(MonsterHuntPlugin plugin) throws Exception {
        String type = plugin.getConfig().getString("storage.type", "sqlite");
        if (!"sqlite".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("Unsupported storage.type '" + type + "'; available: sqlite");
        }
        String configuredFile = plugin.getConfig().getString("storage.sqlite.file", "data/monsterhunt.db");
        if (configuredFile == null || configuredFile.isBlank()) {
            throw new IllegalArgumentException("storage.sqlite.file must not be empty");
        }
        Path dataRoot = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path databaseFile = dataRoot.resolve(configuredFile).normalize();
        if (!databaseFile.startsWith(dataRoot)) {
            throw new IllegalArgumentException("storage.sqlite.file must stay inside the plugin data directory");
        }
        Files.createDirectories(databaseFile.getParent());
        validateBackupConfiguration(plugin, dataRoot);

        String driverClass = plugin.getConfig().getString("storage.sqlite.driver-class", "org.sqlite.JDBC");
        if (driverClass == null || driverClass.isBlank()) {
            throw new IllegalArgumentException("storage.sqlite.driver-class must not be empty");
        }
        boolean registered = hasDriver("jdbc:sqlite:");
        if (!registered) Class.forName(driverClass);
        if (!hasDriver("jdbc:sqlite:")) {
            throw new SQLException("No SQLite JDBC driver is available after loading " + driverClass);
        }
        plugin.getLogger().info(registered
                ? "Using SQLite JDBC driver already available at runtime."
                : "Using Paper-provided fallback SQLite JDBC driver " + driverClass + ".");

        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
        DatabaseStorage storage = new DatabaseStorage(plugin, connection, databaseFile,
                plugin.getConfig().getBoolean("storage.history.kill-events.enabled", false));
        try {
            storage.configure();
            storage.createSchema();
            storage.migrateYamlOnce();
            storage.verify();
            return storage;
        } catch (Exception exception) {
            connection.close();
            throw exception;
        }
    }

    private static void validateBackupConfiguration(MonsterHuntPlugin plugin, Path dataRoot) {
        int hours = plugin.getConfig().getInt("storage.sqlite.backup.interval-hours", 24);
        int delay = plugin.getConfig().getInt("storage.sqlite.backup.initial-delay-minutes", 10);
        int maxFiles = plugin.getConfig().getInt("storage.sqlite.backup.retention.max-files", 14);
        int maxAge = plugin.getConfig().getInt("storage.sqlite.backup.retention.max-age-days", 30);
        if (hours < 1 || hours > 8760) throw new IllegalArgumentException("backup.interval-hours must be between 1 and 8760");
        if (delay < 0 || delay > 1440) throw new IllegalArgumentException("backup.initial-delay-minutes must be between 0 and 1440");
        if (maxFiles < 1 || maxFiles > 10_000) throw new IllegalArgumentException("backup.retention.max-files must be between 1 and 10000");
        if (maxAge < 1 || maxAge > 36_500) throw new IllegalArgumentException("backup.retention.max-age-days must be between 1 and 36500");
        String configured = plugin.getConfig().getString("storage.sqlite.backup.directory", "backups");
        if (configured == null || configured.isBlank() || !dataRoot.resolve(configured).normalize().startsWith(dataRoot))
            throw new IllegalArgumentException("backup.directory must stay inside the plugin data directory");
    }

    private static boolean hasDriver(String url) {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            try {
                if (drivers.nextElement().acceptsURL(url)) return true;
            } catch (SQLException ignored) {
                // Try remaining registered drivers.
            }
        }
        return false;
    }

    private void configure() throws SQLException {
        String journalMode = plugin.getConfig().getString("storage.sqlite.pragmas.journal-mode", "WAL");
        if (!List.of("WAL", "DELETE", "TRUNCATE", "PERSIST", "MEMORY", "OFF")
                .contains(journalMode.toUpperCase())) {
            throw new IllegalArgumentException("Invalid storage.sqlite.pragmas.journal-mode: " + journalMode);
        }
        String synchronous = plugin.getConfig().getString("storage.sqlite.pragmas.synchronous", "FULL");
        if (!List.of("OFF", "NORMAL", "FULL", "EXTRA").contains(synchronous.toUpperCase())) {
            throw new IllegalArgumentException("Invalid storage.sqlite.pragmas.synchronous: " + synchronous);
        }
        int timeout = plugin.getConfig().getInt("storage.sqlite.pragmas.busy-timeout-millis", 5000);
        if (timeout < 0 || timeout > 600_000) {
            throw new IllegalArgumentException("busy-timeout-millis must be between 0 and 600000");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA journal_mode=" + journalMode);
            statement.execute("PRAGMA synchronous=" + synchronous);
            statement.execute("PRAGMA busy_timeout=" + timeout);
        }
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL, description TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS players (player_uuid TEXT PRIMARY KEY, last_name TEXT, first_seen_at TEXT NOT NULL, last_seen_at TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS hunts (hunt_id INTEGER PRIMARY KEY AUTOINCREMENT, world_uuid TEXT NOT NULL, world_name TEXT, started_at TEXT NOT NULL, ended_at TEXT, start_type TEXT NOT NULL, completion_state TEXT NOT NULL, participant_count INTEGER NOT NULL DEFAULT 0, metadata_json TEXT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS hunt_results (hunt_id INTEGER NOT NULL, player_uuid TEXT NOT NULL, score INTEGER NOT NULL, placement INTEGER, joined_at TEXT, finished_at TEXT, PRIMARY KEY(hunt_id, player_uuid), FOREIGN KEY(hunt_id) REFERENCES hunts(hunt_id) ON DELETE CASCADE, FOREIGN KEY(player_uuid) REFERENCES players(player_uuid))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS pending_actions (action_id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, action_type TEXT NOT NULL, world_uuid TEXT, x REAL, y REAL, z REAL, yaw REAL, pitch REAL, reward_place INTEGER, created_at TEXT NOT NULL, completed_at TEXT, attempts INTEGER NOT NULL DEFAULT 0, last_error TEXT, FOREIGN KEY(player_uuid) REFERENCES players(player_uuid))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS kill_events (event_id INTEGER PRIMARY KEY AUTOINCREMENT, hunt_id INTEGER NOT NULL, player_uuid TEXT NOT NULL, entity_key TEXT NOT NULL, points INTEGER NOT NULL, occurred_at TEXT NOT NULL, FOREIGN KEY(hunt_id) REFERENCES hunts(hunt_id) ON DELETE CASCADE, FOREIGN KEY(player_uuid) REFERENCES players(player_uuid))");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_hunt_results_player ON hunt_results(player_uuid)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_hunt_results_score ON hunt_results(score DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_hunts_started ON hunts(started_at DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_player ON pending_actions(player_uuid, completed_at)");
            statement.executeUpdate("INSERT OR IGNORE INTO schema_version(version, applied_at, description) VALUES(" + SCHEMA_VERSION + ", '" + Instant.now() + "', 'Initial SQLite schema')");
        }
    }

    public synchronized long startHunt(World world, boolean manual) {
        String sql = "INSERT INTO hunts(world_uuid,world_name,started_at,start_type,completion_state) VALUES(?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, world.getUID().toString());
            statement.setString(2, world.getName());
            statement.setString(3, Instant.now().toString());
            statement.setString(4, manual ? "MANUAL" : "AUTOMATIC");
            statement.setString(5, "RUNNING");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            throw new SQLException("SQLite returned no hunt id");
        } catch (SQLException exception) {
            throw failure("start hunt", exception);
        }
    }

    public synchronized void recordKill(long huntId, UUID playerId, String name, String entityKey, int points) {
        if (!killEventsEnabled || huntId <= 0) return;
        try {
            upsertPlayer(playerId, name);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO kill_events(hunt_id,player_uuid,entity_key,points,occurred_at) VALUES(?,?,?,?,?)")) {
                statement.setLong(1, huntId);
                statement.setString(2, playerId.toString());
                statement.setString(3, entityKey);
                statement.setInt(4, points);
                statement.setString(5, Instant.now().toString());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not record kill event", exception);
        }
    }

    public synchronized void completeHunt(long huntId, Map<UUID, Integer> scores,
                                          Map<UUID, Integer> placements, Map<UUID, String> names,
                                          Map<UUID, Integer> pendingRewards,
                                          Map<UUID, Location> pendingReturns, boolean completed) {
        try {
            connection.setAutoCommit(false);
            for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
                UUID playerId = entry.getKey();
                upsertPlayer(playerId, names.get(playerId));
                try (PreparedStatement result = connection.prepareStatement(
                        "INSERT OR REPLACE INTO hunt_results(hunt_id,player_uuid,score,placement,finished_at) VALUES(?,?,?,?,?)")) {
                    result.setLong(1, huntId);
                    result.setString(2, playerId.toString());
                    result.setInt(3, entry.getValue());
                    Integer place = placements.get(playerId);
                    if (place == null) result.setNull(4, java.sql.Types.INTEGER); else result.setInt(4, place);
                    result.setString(5, Instant.now().toString());
                    result.executeUpdate();
                }
            }
            for (Map.Entry<UUID, Integer> reward : pendingRewards.entrySet()) queueRewardInternal(reward.getKey(), reward.getValue());
            for (Map.Entry<UUID, Location> returning : pendingReturns.entrySet()) queueReturnInternal(returning.getKey(), returning.getValue());
            try (PreparedStatement hunt = connection.prepareStatement(
                    "UPDATE hunts SET ended_at=?,completion_state=?,participant_count=? WHERE hunt_id=?")) {
                hunt.setString(1, Instant.now().toString());
                hunt.setString(2, completed ? "COMPLETED" : "INTERRUPTED");
                hunt.setInt(3, scores.size());
                hunt.setLong(4, huntId);
                hunt.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            rollback();
            throw failure("complete hunt", exception);
        } finally {
            autoCommit();
        }
    }

    public synchronized int highScore(UUID playerId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(score),0) FROM hunt_results WHERE player_uuid=?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        } catch (SQLException exception) { throw failure("read high score", exception); }
    }

    public synchronized Map<String, Integer> top(int limit) {
        Map<String, Integer> values = new LinkedHashMap<>();
        String sql = "SELECT COALESCE(p.last_name,r.player_uuid),MAX(r.score) best FROM hunt_results r JOIN players p ON p.player_uuid=r.player_uuid GROUP BY r.player_uuid ORDER BY best DESC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, Math.min(limit, 100)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.put(result.getString(1), result.getInt(2));
            }
            return values;
        } catch (SQLException exception) { throw failure("read top scores", exception); }
    }

    public synchronized void queueReturn(UUID playerId, Location location) {
        try { ensurePlayer(playerId); queueReturnInternal(playerId, location); }
        catch (SQLException exception) { throw failure("queue return", exception); }
    }

    public synchronized void queueReward(UUID playerId, int place) {
        try { ensurePlayer(playerId); queueRewardInternal(playerId, place); }
        catch (SQLException exception) { throw failure("queue reward", exception); }
    }

    public record PendingAction(long id, String type, UUID worldId, double x, double y, double z,
                                float yaw, float pitch, int rewardPlace) { }

    public synchronized List<PendingAction> pendingActions(UUID playerId) {
        List<PendingAction> actions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT action_id,action_type,world_uuid,x,y,z,yaw,pitch,reward_place FROM pending_actions WHERE player_uuid=? AND completed_at IS NULL ORDER BY action_id")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) actions.add(new PendingAction(result.getLong(1), result.getString(2),
                        result.getString(3) == null ? null : UUID.fromString(result.getString(3)), result.getDouble(4),
                        result.getDouble(5), result.getDouble(6), result.getFloat(7), result.getFloat(8), result.getInt(9)));
            }
            return actions;
        } catch (SQLException exception) { throw failure("read pending actions", exception); }
    }

    public synchronized void completeAction(long id) {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pending_actions SET completed_at=? WHERE action_id=?")) {
            statement.setString(1, Instant.now().toString()); statement.setLong(2, id); statement.executeUpdate();
        } catch (SQLException exception) { throw failure("complete pending action", exception); }
    }

    public synchronized Path backup() throws SQLException, IOException {
        if (!plugin.getConfig().getBoolean("storage.sqlite.backup.enabled", true)) return null;
        Path root = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        String configured = plugin.getConfig().getString("storage.sqlite.backup.directory", "backups");
        Path directory = root.resolve(configured == null ? "backups" : configured).normalize();
        if (!directory.startsWith(root)) throw new IOException("Backup directory must stay inside plugin data directory");
        Files.createDirectories(directory);
        Path target = directory.resolve("monsterhunt-" + BACKUP_TIME.format(Instant.now()) + ".db");
        Path temporary = directory.resolve(target.getFileName() + ".tmp");
        Files.deleteIfExists(temporary);
        try (PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
            statement.setString(1, temporary.toString());
            statement.execute();
        }
        try (Connection check = DriverManager.getConnection("jdbc:sqlite:" + temporary);
             Statement statement = check.createStatement(); ResultSet result = statement.executeQuery("PRAGMA quick_check")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) throw new SQLException("Backup integrity check failed");
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target);
        }
        pruneBackups(directory);
        return target;
    }

    private void pruneBackups(Path directory) throws IOException {
        int maxFiles = Math.max(1, plugin.getConfig().getInt("storage.sqlite.backup.retention.max-files", 14));
        int maxAgeDays = Math.max(1, plugin.getConfig().getInt("storage.sqlite.backup.retention.max-age-days", 30));
        Instant oldest = Instant.now().minusSeconds(maxAgeDays * 86400L);
        List<Path> backups;
        try (var files = Files.list(directory)) {
            backups = files.filter(p -> p.getFileName().toString().matches("monsterhunt-.*\\.db"))
                    .sorted(Comparator.comparingLong(this::modified).reversed()).toList();
        }
        for (int i = 0; i < backups.size(); i++) {
            if (i >= maxFiles || Files.getLastModifiedTime(backups.get(i)).toInstant().isBefore(oldest))
                Files.deleteIfExists(backups.get(i));
        }
    }

    private long modified(Path path) { try { return Files.getLastModifiedTime(path).toMillis(); } catch (IOException e) { return 0; } }

    private void migrateYamlOnce() throws SQLException, IOException {
        if (migrationDone()) return;
        File highscores = new File(plugin.getDataFolder(), "highscores.yml");
        File reconnect = new File(plugin.getDataFolder(), "reconnect.yml");
        if (!highscores.exists() && !reconnect.exists()) { markMigrationDone(); return; }
        connection.setAutoCommit(false);
        try {
            long legacyHunt = createLegacyHunt();
            YamlConfiguration highData = YamlConfiguration.loadConfiguration(highscores);
            ConfigurationSection players = highData.getConfigurationSection("players");
            if (players != null) for (String key : players.getKeys(false)) {
                UUID id = UUID.fromString(key); String name = highData.getString("players." + key + ".name", key);
                upsertPlayer(id, name);
                try (PreparedStatement statement = connection.prepareStatement("INSERT OR REPLACE INTO hunt_results(hunt_id,player_uuid,score,finished_at) VALUES(?,?,?,?)")) {
                    statement.setLong(1, legacyHunt); statement.setString(2, key);
                    statement.setInt(3, highData.getInt("players." + key + ".score")); statement.setString(4, Instant.now().toString()); statement.executeUpdate();
                }
            }
            YamlConfiguration reconnectData = YamlConfiguration.loadConfiguration(reconnect);
            ConfigurationSection queued = reconnectData.getConfigurationSection("players");
            if (queued != null) for (String key : queued.getKeys(false)) {
                UUID id = UUID.fromString(key); ensurePlayer(id);
                Location location = reconnectData.getLocation("players." + key + ".return-location");
                int place = reconnectData.getInt("players." + key + ".reward-place");
                if (location != null) queueReturnInternal(id, location);
                if (place > 0) queueRewardInternal(id, place);
            }
            markMigrationDone(); connection.commit();
            archiveImported(highscores.toPath()); archiveImported(reconnect.toPath());
        } catch (Exception exception) {
            rollback();
            if (exception instanceof SQLException sql) throw sql;
            if (exception instanceof IOException io) throw io;
            throw new IOException("Invalid legacy YAML data", exception);
        } finally { autoCommit(); }
    }

    private void archiveImported(Path file) throws IOException {
        if (Files.exists(file)) Files.move(file, file.resolveSibling(file.getFileName() + ".imported"), StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean migrationDone() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS migration_state (name TEXT PRIMARY KEY, completed_at TEXT NOT NULL)");
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM migration_state WHERE name='yaml-v1'")) {
            return statement.executeQuery().next();
        }
    }

    private void markMigrationDone() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO migration_state(name,completed_at) VALUES('yaml-v1',?)")) {
            statement.setString(1, Instant.now().toString()); statement.executeUpdate();
        }
    }

    private long createLegacyHunt() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO hunts(world_uuid,world_name,started_at,ended_at,start_type,completion_state,metadata_json) VALUES('legacy','Legacy YAML import',?,?, 'MIGRATION','COMPLETED','{\"source\":\"highscores.yml\"}')", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, Instant.now().toString()); statement.setString(2, Instant.now().toString()); statement.executeUpdate();
            try (ResultSet result = statement.getGeneratedKeys()) { if (result.next()) return result.getLong(1); }
        }
        throw new SQLException("Could not create legacy hunt");
    }

    private void upsertPlayer(UUID id, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO players(player_uuid,last_name,first_seen_at,last_seen_at) VALUES(?,?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET last_name=COALESCE(excluded.last_name,players.last_name),last_seen_at=excluded.last_seen_at")) {
            String now = Instant.now().toString(); statement.setString(1, id.toString()); statement.setString(2, name);
            statement.setString(3, now); statement.setString(4, now); statement.executeUpdate();
        }
    }

    private void ensurePlayer(UUID id) throws SQLException { upsertPlayer(id, null); }

    private void queueReturnInternal(UUID id, Location location) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO pending_actions(player_uuid,action_type,world_uuid,x,y,z,yaw,pitch,created_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, id.toString()); statement.setString(2, "RETURN_TELEPORT");
            statement.setString(3, location.getWorld().getUID().toString()); statement.setDouble(4, location.x()); statement.setDouble(5, location.y());
            statement.setDouble(6, location.z()); statement.setFloat(7, location.getYaw()); statement.setFloat(8, location.getPitch());
            statement.setString(9, Instant.now().toString()); statement.executeUpdate();
        }
    }

    private void queueRewardInternal(UUID id, int place) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO pending_actions(player_uuid,action_type,reward_place,created_at) VALUES(?,?,?,?)")) {
            statement.setString(1, id.toString()); statement.setString(2, "PLACE_REWARD"); statement.setInt(3, place);
            statement.setString(4, Instant.now().toString()); statement.executeUpdate();
        }
    }

    private void verify() throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA quick_check")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) throw new SQLException("Database integrity check failed");
        }
    }

    private void rollback() { try { connection.rollback(); } catch (SQLException rollback) { plugin.getLogger().log(Level.SEVERE, "Rollback failed", rollback); } }
    private void autoCommit() { try { connection.setAutoCommit(true); } catch (SQLException exception) { plugin.getLogger().log(Level.SEVERE, "Could not restore auto-commit", exception); } }
    private IllegalStateException failure(String operation, SQLException exception) { return new IllegalStateException("Could not " + operation, exception); }

    @Override public synchronized void close() throws SQLException { connection.close(); }
    public Path databaseFile() { return databaseFile; }
}
