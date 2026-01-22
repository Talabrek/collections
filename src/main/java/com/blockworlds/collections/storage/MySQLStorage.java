package com.blockworlds.collections.storage;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.model.Collectible;
import com.blockworlds.collections.model.CollectibleTier;
import com.blockworlds.collections.model.PlayerProgress;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * MySQL implementation of the Storage interface using HikariCP connection pooling.
 *
 * <p>Exception handling policy:</p>
 * <ul>
 *   <li>SEVERE: Player data mutations that could cause data loss (savePlayer, saveCollectedItem, updateCollectionStatus)</li>
 *   <li>WARNING: Reads (return defaults), admin operations (retriable), collectible data (non-critical)</li>
 * </ul>
 *
 * <p>This class is designed for multi-server network deployments where multiple
 * servers share a MySQL database for player collection progress.</p>
 */
public class MySQLStorage implements Storage {

    private final Collections plugin;
    private HikariDataSource dataSource;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MySQLStorage(Collections plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        // Read configuration values
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "collections");
        String username = plugin.getConfig().getString("database.mysql.username", "root");
        String password = plugin.getConfig().getString("database.mysql.password", "");
        int poolSize = plugin.getConfig().getInt("database.mysql.pool-size", 10);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&allowPublicKeyRetrieval=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(poolSize); // Fixed pool size
        config.setMaxLifetime(1800000);  // 30 minutes (less than MySQL wait_timeout)
        config.setConnectionTimeout(30000);
        config.setPoolName("Collections-MySQL");

        // MySQL performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");

        dataSource = new HikariDataSource(config);

        createTables();
        plugin.getLogger().info("MySQL storage initialized");
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            // Players table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    total_collectibles_collected INT DEFAULT 0,
                    total_collections_completed INT DEFAULT 0,
                    first_collection_date BIGINT DEFAULT 0,
                    last_activity_date BIGINT DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Collection progress table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS collection_progress (
                    uuid VARCHAR(36),
                    collection_id VARCHAR(64),
                    reward_claimed BOOLEAN DEFAULT FALSE,
                    completed_date BIGINT DEFAULT 0,
                    PRIMARY KEY (uuid, collection_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Collected items table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS collected_items (
                    uuid VARCHAR(36),
                    collection_id VARCHAR(64),
                    item_id VARCHAR(64),
                    collected_date BIGINT DEFAULT 0,
                    PRIMARY KEY (uuid, collection_id, item_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Active collectibles table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS active_collectibles (
                    id VARCHAR(36) PRIMARY KEY,
                    hitbox_id VARCHAR(36),
                    zone_id VARCHAR(64),
                    collection_id VARCHAR(64),
                    item_id VARCHAR(64),
                    world VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    tier VARCHAR(32),
                    spawned_date BIGINT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Metrics table for counter persistence
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS metrics (
                    `key` VARCHAR(64) PRIMARY KEY,
                    `value` BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            // Indexes for performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_collected_items_uuid ON collected_items(uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_collectibles_world ON active_collectibles(world)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_collectibles_zone ON active_collectibles(zone_id)");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", e);
        }
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL storage shut down");
        }
    }

    // Player Data Operations

    @Override
    public CompletableFuture<PlayerProgress> loadPlayer(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerProgress progress = new PlayerProgress(playerId);

            try (Connection conn = dataSource.getConnection()) {
                // Load base player data
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT * FROM players WHERE uuid = ?")) {
                    stmt.setString(1, playerId.toString());
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        progress.setTotalCollectiblesCollected(rs.getInt("total_collectibles_collected"));
                        progress.setTotalCollectionsCompleted(rs.getInt("total_collections_completed"));
                        progress.setFirstCollectionDate(rs.getLong("first_collection_date"));
                        progress.setLastActivityDate(rs.getLong("last_activity_date"));
                    }
                }

                // Load collection progress
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT * FROM collection_progress WHERE uuid = ?")) {
                    stmt.setString(1, playerId.toString());
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        String collectionId = rs.getString("collection_id");
                        PlayerProgress.CollectionProgress colProgress = progress.getProgress(collectionId);
                        colProgress.setRewardClaimed(rs.getBoolean("reward_claimed"));
                        colProgress.setCompletedDate(rs.getLong("completed_date"));
                        if (colProgress.getCompletedDate() > 0) {
                            colProgress.setComplete(true);
                        }
                    }
                }

                // Load collected items
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT * FROM collected_items WHERE uuid = ?")) {
                    stmt.setString(1, playerId.toString());
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        String collectionId = rs.getString("collection_id");
                        String itemId = rs.getString("item_id");
                        progress.getProgress(collectionId).addItemDirect(itemId);
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load player data: " + playerId, e);
            }

            return progress;
        }).orTimeout(30, TimeUnit.SECONDS).exceptionally(throwable -> {
            plugin.getLogger().log(Level.WARNING, "Timeout loading player data: " + playerId, throwable);
            return new PlayerProgress(playerId);
        });
    }

    @Override
    public CompletableFuture<Void> savePlayer(PlayerProgress progress) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // Upsert player base data
                    savePlayerBase(conn, progress);

                    // Save each collection progress
                    for (var entry : progress.getAllProgress().entrySet()) {
                        PlayerProgress.CollectionProgress colProgress = entry.getValue();
                        saveCollectionProgress(conn, progress.getPlayerId(), colProgress);
                        saveCollectedItems(conn, progress.getPlayerId(), colProgress);
                    }

                    conn.commit();

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                    "Failed to save player data: " + progress.getPlayerId(), e);
                throw new RuntimeException(e);
            }
        }).orTimeout(30, TimeUnit.SECONDS).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE,
                "Timeout or error saving player data: " + progress.getPlayerId(), throwable);
            return null;
        });
    }

    private void savePlayerBase(Connection conn, PlayerProgress progress) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO players
                (uuid, total_collectibles_collected, total_collections_completed, first_collection_date, last_activity_date)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    total_collectibles_collected = VALUES(total_collectibles_collected),
                    total_collections_completed = VALUES(total_collections_completed),
                    first_collection_date = VALUES(first_collection_date),
                    last_activity_date = VALUES(last_activity_date)
                """)) {
            stmt.setString(1, progress.getPlayerId().toString());
            stmt.setInt(2, progress.getTotalCollectiblesCollected());
            stmt.setInt(3, progress.getTotalCollectionsCompleted());
            stmt.setLong(4, progress.getFirstCollectionDate());
            stmt.setLong(5, progress.getLastActivityDate());
            stmt.executeUpdate();
        }
    }

    private void saveCollectionProgress(Connection conn, UUID playerId,
            PlayerProgress.CollectionProgress colProgress) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO collection_progress
                (uuid, collection_id, reward_claimed, completed_date)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    reward_claimed = VALUES(reward_claimed),
                    completed_date = VALUES(completed_date)
                """)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, colProgress.getCollectionId());
            stmt.setBoolean(3, colProgress.isRewardClaimed());
            stmt.setLong(4, colProgress.getCompletedDate());
            stmt.executeUpdate();
        }
    }

    /**
     * Save collected items using batch insert.
     * Uses INSERT IGNORE to skip duplicates without error.
     */
    private void saveCollectedItems(Connection conn, UUID playerId,
            PlayerProgress.CollectionProgress colProgress) throws SQLException {
        var items = colProgress.getCollectedItems();
        if (items.isEmpty()) {
            return;
        }

        try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT IGNORE INTO collected_items
                (uuid, collection_id, item_id, collected_date)
                VALUES (?, ?, ?, ?)
                """)) {
            long timestamp = System.currentTimeMillis();
            String playerIdStr = playerId.toString();
            String collectionId = colProgress.getCollectionId();

            for (String itemId : items) {
                stmt.setString(1, playerIdStr);
                stmt.setString(2, collectionId);
                stmt.setString(3, itemId);
                stmt.setLong(4, timestamp);
                stmt.addBatch();
            }

            stmt.executeBatch();
        }
    }

    @Override
    public CompletableFuture<Void> saveCollectedItem(UUID playerId, String collectionId, String itemId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("""
                         INSERT IGNORE INTO collected_items
                         (uuid, collection_id, item_id, collected_date)
                         VALUES (?, ?, ?, ?)
                         """)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, collectionId);
                stmt.setString(3, itemId);
                stmt.setLong(4, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }).orTimeout(30, TimeUnit.SECONDS).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE,
                "CRITICAL: Failed to save collected item '" + itemId +
                "' for collection '" + collectionId + "' for player " + playerId, throwable);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> updateCollectionStatus(UUID playerId, String collectionId, boolean complete, boolean rewardClaimed) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("""
                         INSERT INTO collection_progress
                         (uuid, collection_id, reward_claimed, completed_date)
                         VALUES (?, ?, ?, ?)
                         ON DUPLICATE KEY UPDATE
                             reward_claimed = VALUES(reward_claimed),
                             completed_date = VALUES(completed_date)
                         """)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, collectionId);
                stmt.setBoolean(3, rewardClaimed);
                stmt.setLong(4, complete ? System.currentTimeMillis() : 0);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }).orTimeout(30, TimeUnit.SECONDS).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE,
                "CRITICAL: Failed to update collection status for '" + collectionId +
                "' for player " + playerId + " (complete=" + complete + ", rewardClaimed=" + rewardClaimed + ")", throwable);
            return null;
        });
    }

    // Collectible Operations

    @Override
    public CompletableFuture<Void> saveCollectible(Collectible collectible) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("""
                         INSERT INTO active_collectibles
                         (id, hitbox_id, zone_id, collection_id, item_id, world, x, y, z, tier, spawned_date)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                         ON DUPLICATE KEY UPDATE
                             hitbox_id = VALUES(hitbox_id),
                             zone_id = VALUES(zone_id),
                             collection_id = VALUES(collection_id),
                             item_id = VALUES(item_id),
                             world = VALUES(world),
                             x = VALUES(x),
                             y = VALUES(y),
                             z = VALUES(z),
                             tier = VALUES(tier),
                             spawned_date = VALUES(spawned_date)
                         """)) {
                stmt.setString(1, collectible.id().toString());
                stmt.setString(2, collectible.hitboxId() != null ? collectible.hitboxId().toString() : null);
                stmt.setString(3, collectible.zoneId());
                stmt.setString(4, collectible.collectionId());
                stmt.setString(5, collectible.itemId());
                stmt.setString(6, collectible.getWorldName());
                stmt.setDouble(7, collectible.location().getX());
                stmt.setDouble(8, collectible.location().getY());
                stmt.setDouble(9, collectible.location().getZ());
                stmt.setString(10, collectible.tier().name());
                stmt.setLong(11, collectible.spawnedAt());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save collectible", e);
            }
        }).orTimeout(30, TimeUnit.SECONDS).exceptionally(throwable -> {
            plugin.getLogger().log(Level.WARNING, "Timeout saving collectible: " + collectible.id(), throwable);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> removeCollectible(UUID collectibleId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM active_collectibles WHERE id = ?")) {
                stmt.setString(1, collectibleId.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to remove collectible", e);
            }
        }).orTimeout(30, TimeUnit.SECONDS).exceptionally(throwable -> {
            plugin.getLogger().log(Level.WARNING, "Timeout removing collectible: " + collectibleId, throwable);
            return null;
        });
    }

    @Override
    public CompletableFuture<List<Collectible>> loadAllCollectibles() {
        return CompletableFuture.supplyAsync(() -> {
            List<Collectible> collectibles = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM active_collectibles")) {

                while (rs.next()) {
                    Collectible collectible = parseCollectible(rs);
                    if (collectible != null) {
                        collectibles.add(collectible);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load collectibles", e);
            }
            return collectibles;
        });
    }

    @Override
    public CompletableFuture<List<Collectible>> loadCollectiblesInChunk(String worldName, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            List<Collectible> collectibles = new ArrayList<>();

            // Calculate block bounds for the chunk
            int minX = chunkX << 4;
            int maxX = minX + 15;
            int minZ = chunkZ << 4;
            int maxZ = minZ + 15;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("""
                         SELECT * FROM active_collectibles
                         WHERE world = ? AND x >= ? AND x <= ? AND z >= ? AND z <= ?
                         """)) {
                stmt.setString(1, worldName);
                stmt.setDouble(2, minX);
                stmt.setDouble(3, maxX);
                stmt.setDouble(4, minZ);
                stmt.setDouble(5, maxZ);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Collectible collectible = parseCollectible(rs);
                    if (collectible != null) {
                        collectibles.add(collectible);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load collectibles in chunk", e);
            }
            return collectibles;
        });
    }

    private Collectible parseCollectible(ResultSet rs) throws SQLException {
        String worldName = rs.getString("world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null; // World not loaded
        }

        Location location = new Location(
                world,
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z")
        );

        String hitboxIdStr = rs.getString("hitbox_id");

        return new Collectible(
                UUID.fromString(rs.getString("id")),
                hitboxIdStr != null ? UUID.fromString(hitboxIdStr) : null,
                rs.getString("zone_id"),
                rs.getString("collection_id"),
                rs.getString("item_id"),
                location,
                CollectibleTier.fromString(rs.getString("tier")),
                rs.getLong("spawned_date"),
                false // Will be set to true when entities are spawned
        );
    }

    @Override
    public CompletableFuture<Void> clearAllCollectibles() {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM active_collectibles");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to clear collectibles", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> clearCollectiblesInZone(String zoneId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM active_collectibles WHERE zone_id = ?")) {
                stmt.setString(1, zoneId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to clear collectibles in zone", e);
            }
        });
    }

    // Statistics

    @Override
    public CompletableFuture<Integer> getTotalCollectiblesCollected() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT SUM(total_collectibles_collected) as total FROM players")) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get total collectibles", e);
            }
            return 0;
        });
    }

    @Override
    public CompletableFuture<Integer> getTotalCollectionsCompleted() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT SUM(total_collections_completed) as total FROM players")) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get total collections", e);
            }
            return 0;
        });
    }

    @Override
    public CompletableFuture<Void> backupPlayerData(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            // For Phase 1, we'll just log that a backup was requested
            // Full implementation would copy data to a backup table
            plugin.getLogger().info("Backup requested for player: " + playerId);
        });
    }

    @Override
    public CompletableFuture<Void> resetPlayer(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                // Delete from collected_items
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM collected_items WHERE uuid = ?")) {
                    stmt.setString(1, playerId.toString());
                    stmt.executeUpdate();
                }

                // Delete from collection_progress
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM collection_progress WHERE uuid = ?")) {
                    stmt.setString(1, playerId.toString());
                    stmt.executeUpdate();
                }

                // Reset player stats
                try (PreparedStatement stmt = conn.prepareStatement("""
                        UPDATE players SET
                        total_collectibles_collected = 0,
                        total_collections_completed = 0,
                        first_collection_date = 0,
                        last_activity_date = 0
                        WHERE uuid = ?
                        """)) {
                    stmt.setString(1, playerId.toString());
                    stmt.executeUpdate();
                }

                plugin.getLogger().info("Reset all progress for player: " + playerId);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to reset player data: " + playerId, e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> resetPlayerCollection(UUID playerId, String collectionId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                // Delete collected items for this collection
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM collected_items WHERE uuid = ? AND collection_id = ?")) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, collectionId);
                    stmt.executeUpdate();
                }

                // Delete collection progress
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM collection_progress WHERE uuid = ? AND collection_id = ?")) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, collectionId);
                    stmt.executeUpdate();
                }

                plugin.getLogger().info("Reset collection '" + collectionId + "' for player: " + playerId);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to reset collection for player: " + playerId, e);
            }
        });
    }

    // Metrics Operations

    @Override
    public CompletableFuture<Long> getMetric(String key) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `value` FROM metrics WHERE `key` = ?")) {
                stmt.setString(1, key);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getLong("value");
                }
                return 0L;
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to get metric " + key + ": " + e.getMessage());
                return 0L;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> setMetric(String key, long value) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO metrics (`key`, `value`) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE `value` = VALUES(`value`), updated_at = CURRENT_TIMESTAMP")) {
                stmt.setString(1, key);
                stmt.setLong(2, value);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to set metric " + key + ": " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<String, Long>> getAllMetrics() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Long> metrics = new HashMap<>();
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT `key`, `value` FROM metrics")) {
                while (rs.next()) {
                    metrics.put(rs.getString("key"), rs.getLong("value"));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to get all metrics: " + e.getMessage());
            }
            return metrics;
        }, executor);
    }

    // Export Operations

    @Override
    public CompletableFuture<List<UUID>> getAllPlayerUuids() {
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> uuids = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT uuid FROM players")) {
                while (rs.next()) {
                    uuids.add(UUID.fromString(rs.getString("uuid")));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get all player UUIDs", e);
            }
            return uuids;
        });
    }
}
