# Phase 8: MySQL Implementation - Research

**Researched:** 2026-01-21
**Domain:** MySQL/JDBC database abstraction and multi-database support
**Confidence:** HIGH

## Summary

This phase adds MySQL support as an alternative storage backend for multi-server network deployments. The existing codebase already has a clean `Storage` interface with a `SQLiteStorage` implementation using HikariCP connection pooling, which significantly simplifies this work.

The primary challenge is adapting SQLite-specific SQL syntax (like `INSERT OR REPLACE`) to MySQL equivalents (`INSERT ... ON DUPLICATE KEY UPDATE`). HikariCP already supports MySQL with slightly different configuration. The configuration infrastructure exists in `config.yml` and `ConfigManager` but the MySQL settings are not yet wired to a MySQL storage implementation.

**Primary recommendation:** Create a `MySQLStorage` class implementing the existing `Storage` interface, add a `StorageFactory` to instantiate the correct implementation based on config, and translate SQLite-specific SQL to MySQL-compatible statements.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| HikariCP | 5.1.0 | Connection pooling | Already in use for SQLite, industry standard |
| MySQL Connector/J | 9.1.0 | MySQL JDBC driver | Official Oracle/MySQL driver, Java 21 compatible |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| sqlite-jdbc | 3.45.3.0 | SQLite JDBC | Already included, keep for single-server |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| MySQL Connector/J 9.x | MariaDB Connector/J | MariaDB connector works with MySQL but 9.x is official |
| MySQL Connector/J 8.x | MySQL Connector/J 9.x | 9.x is current, same API, better TLS support |

**Installation (add to build.gradle.kts):**
```kotlin
// MySQL JDBC driver
implementation("com.mysql:mysql-connector-j:9.1.0") {
    exclude(group = "com.google.protobuf") // X DevAPI not needed
}
```

**Shadow relocation (add to shadowJar):**
```kotlin
relocate("com.mysql", "com.blockworlds.collections.lib.mysql")
```

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/com/blockworlds/collections/
├── storage/
│   ├── Storage.java              # Interface (exists)
│   ├── SQLiteStorage.java        # SQLite impl (exists)
│   ├── MySQLStorage.java         # NEW: MySQL impl
│   ├── StorageFactory.java       # NEW: Factory for storage selection
│   └── AbstractSQLStorage.java   # OPTIONAL: Shared SQL logic
```

### Pattern 1: Storage Factory Pattern
**What:** Factory class that instantiates correct Storage implementation based on configuration
**When to use:** Plugin initialization in Collections.java onEnable()
**Example:**
```java
// Source: Standard factory pattern for plugin storage
public class StorageFactory {

    public static Storage createStorage(Collections plugin) {
        String type = plugin.getConfigManager().getDatabaseType();

        return switch (type.toLowerCase()) {
            case "mysql" -> new MySQLStorage(plugin);
            case "sqlite" -> new SQLiteStorage(plugin);
            default -> {
                plugin.getLogger().warning("Unknown database type: " + type + ", defaulting to SQLite");
                yield new SQLiteStorage(plugin);
            }
        };
    }
}
```

### Pattern 2: SQL Dialect Abstraction
**What:** Abstract methods for database-specific SQL syntax
**When to use:** When SQL differs between SQLite and MySQL
**Example:**
```java
// SQLite uses: INSERT OR REPLACE INTO
// MySQL uses: INSERT INTO ... ON DUPLICATE KEY UPDATE

// MySQL upsert pattern
String sql = """
    INSERT INTO players (uuid, total_collectibles_collected, total_collections_completed,
                        first_collection_date, last_activity_date)
    VALUES (?, ?, ?, ?, ?)
    ON DUPLICATE KEY UPDATE
        total_collectibles_collected = VALUES(total_collectibles_collected),
        total_collections_completed = VALUES(total_collections_completed),
        first_collection_date = VALUES(first_collection_date),
        last_activity_date = VALUES(last_activity_date)
    """;
```

### Pattern 3: HikariCP Configuration for MySQL
**What:** Optimized HikariCP settings for MySQL connections
**When to use:** MySQLStorage.initialize()
**Example:**
```java
// Source: https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
config.setUsername(username);
config.setPassword(password);
config.setMaximumPoolSize(poolSize);
config.setMinimumIdle(poolSize); // Fixed pool size recommended

// MySQL-specific optimizations
config.addDataSourceProperty("cachePrepStmts", "true");
config.addDataSourceProperty("prepStmtCacheSize", "250");
config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
config.addDataSourceProperty("useServerPrepStmts", "true");
config.addDataSourceProperty("useLocalSessionState", "true");
config.addDataSourceProperty("rewriteBatchedStatements", "true");
config.addDataSourceProperty("cacheResultSetMetadata", "true");
config.addDataSourceProperty("cacheServerConfiguration", "true");
config.addDataSourceProperty("elideSetAutoCommits", "true");
config.addDataSourceProperty("maintainTimeStats", "false");
```

### Anti-Patterns to Avoid
- **Using autoReconnect=true:** Deprecated since Connector/J 8.0.16. Use connection pool validation instead.
- **REPLACE INTO for upserts:** Causes DELETE+INSERT, breaks foreign keys, fragments InnoDB tablespace. Use INSERT ON DUPLICATE KEY UPDATE.
- **Not setting maxLifetime:** Must be shorter than MySQL wait_timeout (default 8h). Set to 30 minutes (1800000ms).
- **Sharing DataSource between threads unsafely:** HikariDataSource is thread-safe, but ensure Connection objects are not shared.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Connection pooling | Custom pool logic | HikariCP | Already in use, handles all edge cases |
| Reconnection logic | autoReconnect=true | HikariCP connection validation | autoReconnect is deprecated and unsafe |
| SQL dialect abstraction | String concatenation | Separate implementation classes | Type-safe, testable, maintainable |
| Data migration tool | Manual SQL export/import | Plugin command with JDBC | Consistent behavior, error handling |

**Key insight:** HikariCP already handles connection lifecycle, validation, and reconnection. Focus on SQL translation and configuration wiring.

## Common Pitfalls

### Pitfall 1: SQLite Syntax in MySQL
**What goes wrong:** `INSERT OR REPLACE` and `INSERT OR IGNORE` are SQLite-only syntax
**Why it happens:** Copy-pasting from SQLiteStorage
**How to avoid:** Use `INSERT ... ON DUPLICATE KEY UPDATE` for upserts, `INSERT IGNORE` for ignore
**Warning signs:** SQLException with syntax error on MySQL

### Pitfall 2: Connection Timeout on Network Failure
**What goes wrong:** MySQL connections die after network blip, operations fail
**Why it happens:** MySQL server closes idle connections after wait_timeout (default 8h)
**How to avoid:** Set HikariCP maxLifetime < MySQL wait_timeout (e.g., 30 minutes)
**Warning signs:** "Connection is closed" exceptions after idle periods

### Pitfall 3: BOOLEAN Type Differences
**What goes wrong:** SQLite BOOLEAN is 0/1 integers, MySQL BOOLEAN is TINYINT(1)
**Why it happens:** Different type handling between databases
**How to avoid:** Use setBoolean/getBoolean consistently - JDBC handles translation
**Warning signs:** Works on SQLite, fails on MySQL or vice versa

### Pitfall 4: VARCHAR Length Requirements
**What goes wrong:** MySQL requires VARCHAR length, SQLite does not
**Why it happens:** SQLite ignores length constraints, MySQL enforces them
**How to avoid:** Specify lengths in CREATE TABLE: VARCHAR(36) for UUIDs, VARCHAR(64) for IDs
**Warning signs:** Already correct in SQLiteStorage, just verify

### Pitfall 5: Transaction Isolation Level
**What goes wrong:** Different default isolation levels cause unexpected behavior
**Why it happens:** SQLite defaults to SERIALIZABLE, MySQL InnoDB defaults to REPEATABLE-READ
**How to avoid:** Explicit transaction management already in place, behavior should match
**Warning signs:** Phantom reads or non-repeatable reads on MySQL

### Pitfall 6: Case Sensitivity in Table/Column Names
**What goes wrong:** MySQL on Windows is case-insensitive, Linux is case-sensitive by default
**Why it happens:** MySQL follows filesystem case sensitivity
**How to avoid:** Use lowercase for all table/column names consistently
**Warning signs:** "Table doesn't exist" on Linux but works on Windows

## Code Examples

Verified patterns from official sources:

### MySQL HikariCP Connection Setup
```java
// Source: https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
private void initializeMySQL() {
    FileConfiguration config = plugin.getConfig();
    String host = config.getString("database.mysql.host", "localhost");
    int port = config.getInt("database.mysql.port", 3306);
    String database = config.getString("database.mysql.database", "collections");
    String username = config.getString("database.mysql.username", "root");
    String password = config.getString("database.mysql.password", "");
    int poolSize = config.getInt("database.mysql.pool-size", 10);

    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
            "?useSSL=false&allowPublicKeyRetrieval=true");
    hikariConfig.setUsername(username);
    hikariConfig.setPassword(password);
    hikariConfig.setMaximumPoolSize(poolSize);
    hikariConfig.setMinimumIdle(poolSize); // Fixed size pool
    hikariConfig.setMaxLifetime(1800000);  // 30 minutes
    hikariConfig.setConnectionTimeout(30000);
    hikariConfig.setPoolName("Collections-MySQL");

    // MySQL performance optimizations
    hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
    hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
    hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
    hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
    hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
    hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
    hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");

    dataSource = new HikariDataSource(hikariConfig);
}
```

### MySQL Upsert for Player Data
```java
// Source: MySQL 8.0 Reference Manual - INSERT ON DUPLICATE KEY UPDATE
private void savePlayerBase(Connection conn, PlayerProgress progress) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement("""
            INSERT INTO players
            (uuid, total_collectibles_collected, total_collections_completed,
             first_collection_date, last_activity_date)
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
```

### MySQL INSERT IGNORE for Collected Items
```java
// Source: MySQL Reference - INSERT IGNORE
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
```

### MySQL Table Creation with Engine Specification
```java
// Source: MySQL best practices - use InnoDB engine
private void createTables() throws SQLException {
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS players (
                uuid VARCHAR(36) PRIMARY KEY,
                total_collectibles_collected INT DEFAULT 0,
                total_collections_completed INT DEFAULT 0,
                first_collection_date BIGINT DEFAULT 0,
                last_activity_date BIGINT DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS collection_progress (
                uuid VARCHAR(36),
                collection_id VARCHAR(64),
                reward_claimed BOOLEAN DEFAULT FALSE,
                completed_date BIGINT DEFAULT 0,
                PRIMARY KEY (uuid, collection_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        // ... similar for other tables
    }
}
```

## SQL Syntax Translation Reference

| Operation | SQLite | MySQL |
|-----------|--------|-------|
| Upsert (replace) | `INSERT OR REPLACE INTO` | `INSERT INTO ... ON DUPLICATE KEY UPDATE` |
| Upsert (ignore) | `INSERT OR IGNORE INTO` | `INSERT IGNORE INTO` |
| Boolean type | INTEGER (0/1) | BOOLEAN/TINYINT(1) |
| Auto-increment | `INTEGER PRIMARY KEY` | `INT AUTO_INCREMENT PRIMARY KEY` |
| String concat | `\|\|` | `CONCAT()` |
| LIMIT with offset | `LIMIT n OFFSET m` | `LIMIT m, n` or `LIMIT n OFFSET m` |
| Table engine | N/A | `ENGINE=InnoDB` |
| Character set | N/A | `DEFAULT CHARSET=utf8mb4` |

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| mysql-connector-java | com.mysql:mysql-connector-j | 2023 | Maven coordinates changed |
| autoReconnect=true | HikariCP connection validation | Connector/J 8.0.16 | Deprecated, use pool validation |
| Connector/J 8.x | Connector/J 9.x | 2024 | Same API, better TLS support |

**Deprecated/outdated:**
- `mysql:mysql-connector-java` groupId - Use `com.mysql:mysql-connector-j`
- `autoReconnect=true` JDBC parameter - Use HikariCP maxLifetime
- MySQL 5.x compatibility - Focus on MySQL 8.0+

## Open Questions

Things that couldn't be fully resolved:

1. **UTF8MB4 vs UTF8 for Minecraft IDs**
   - What we know: utf8mb4 supports full Unicode including emoji
   - What's unclear: Whether Minecraft item/collection IDs ever contain non-ASCII
   - Recommendation: Use utf8mb4 to be safe, no performance impact for ASCII-only data

2. **Connection pool size for network deployments**
   - What we know: Config allows pool-size: 10 by default
   - What's unclear: Optimal size for 50+ concurrent players across multiple servers
   - Recommendation: Default 10 is reasonable, document that users should tune based on server count

3. **SSL/TLS for production MySQL**
   - What we know: useSSL=false in example for simplicity
   - What's unclear: User's production MySQL SSL configuration
   - Recommendation: Add useSSL config option, default to false for development convenience

## Migration Strategy

### SQLite to MySQL Data Transfer

**Approach:** Add admin command to export SQLite data and import to MySQL

**Recommended implementation:**
1. Read all data from source (SQLite) using loadPlayer/loadAllCollectibles
2. Write to destination (MySQL) using savePlayer/saveCollectible
3. Batch operations for performance
4. Transaction wrap for atomicity
5. Report progress to operator

**Command structure:**
```
/collections admin migrate <sqlite-to-mysql|mysql-to-sqlite>
```

**Steps:**
1. Verify both database configurations are valid
2. Create backup of destination database (if exists)
3. Load all players from source
4. Save all players to destination (batched)
5. Load all collectibles from source
6. Save all collectibles to destination (batched)
7. Report counts and success

## Sources

### Primary (HIGH confidence)
- [HikariCP MySQL Configuration Wiki](https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration) - Recommended MySQL settings
- [MySQL Connector/J Versions](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html) - Version compatibility
- [MySQL INSERT ON DUPLICATE KEY UPDATE](https://dev.mysql.com/doc/refman/8.0/en/insert-on-duplicate.html) - Upsert syntax

### Secondary (MEDIUM confidence)
- [SpigotMC HikariCP Guide](https://www.spigotmc.org/threads/hikaricp-guide.159480/) - Minecraft-specific patterns
- [Maven Central mysql-connector-j](https://central.sonatype.com/artifact/com.mysql/mysql-connector-j) - Current version info

### Tertiary (LOW confidence)
- WebSearch on connection pool sizing - Varies by deployment

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - HikariCP/MySQL Connector well documented
- Architecture: HIGH - Factory pattern is standard, Storage interface exists
- Pitfalls: HIGH - Well-documented SQLite/MySQL differences
- Migration: MEDIUM - Command approach is standard but implementation details TBD

**Research date:** 2026-01-21
**Valid until:** 2026-02-21 (30 days - stable domain)
