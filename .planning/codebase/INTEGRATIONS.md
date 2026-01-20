# External Integrations

**Analysis Date:** 2026-01-20

## APIs & External Services

**None:**
- This plugin is self-contained
- No external web APIs
- No third-party service integrations
- No metrics/analytics services

## Data Storage

**Databases:**
- SQLite (default, bundled)
  - Connection pool: HikariCP
  - Driver: `org.xerial:sqlite-jdbc`
  - Default path: `plugins/Collections/collections.db`
  - Tables: `players`, `collection_progress`, `collected_items`, `active_collectibles`

- MySQL (configurable, not bundled)
  - Config path: `database.mysql.*` in `config.yml`
  - Pool size: Configurable (default 10)
  - Not currently implemented (config placeholders only)

**File Storage:**
- YAML files for configuration
- Collection definitions: `plugins/Collections/collections/*.yml`
- Zone definitions: `plugins/Collections/zones.yml`
- Main config: `plugins/Collections/config.yml`

**Caching:**
- In-memory caching via `ConcurrentHashMap` for:
  - Collections (`CollectionManager`)
  - Zones (`ZoneManager`)
  - Player progress (`PlayerDataManager`)
  - Active collectibles (`SpawnManager`)
- No external cache (Redis, etc.)

## Authentication & Identity

**Auth Provider:**
- None (uses Minecraft player UUIDs)

**Permissions:**
- Bukkit/Paper permission system
- Key permissions defined in `paper-plugin.yml`:
  - `collections.use` - Basic access (default: true)
  - `collections.admin` - Admin commands (default: op)
  - `collections.craft.goggles` - Craft basic goggles (default: true)
  - `collections.craft.mastergoggles` - Craft master goggles (default: true)
  - `collections.bypass.goggles` - See all tiers without goggles (default: op)
  - `collections.bypass.cooldown` - No collection cooldown (default: op)

## Monitoring & Observability

**Error Tracking:**
- None (uses Java logging via `plugin.getLogger()`)

**Logs:**
- Standard Java logging to server console/logs
- Debug mode toggleable via config or `/collections debug`

**Metrics:**
- None (no bStats or similar)

## CI/CD & Deployment

**Hosting:**
- Any Paper 1.21.4+ compatible Minecraft server
- Folia servers supported

**CI Pipeline:**
- None configured in repository

**Deployment:**
- Manual: Copy JAR to `plugins/` folder
- Run Paper plugin for dev: `./gradlew runServer`

## Environment Configuration

**Required env vars:**
- None

**Optional config in `config.yml`:**
```yaml
database:
  type: sqlite              # or mysql
  path: plugins/Collections/data.db
  mysql:
    host: localhost
    port: 3306
    database: collections
    username: root
    password: ""
    pool-size: 10
```

**Secrets location:**
- MySQL password in `config.yml` (if MySQL is used)
- No other secrets required

## Webhooks & Callbacks

**Incoming:**
- None

**Outgoing:**
- None

## Platform Dependencies

**Paper API Features Used:**
- Adventure API (text components, MiniMessage)
- Brigadier commands (lifecycle events)
- PersistentDataContainer (item/entity metadata)
- Folia-compatible schedulers (region, entity, global, async)
- Custom recipes (shaped recipes for goggles)

**Bukkit Events Listened:**
- `PlayerJoinEvent`, `PlayerQuitEvent` - Player data management
- `ChunkLoadEvent`, `ChunkUnloadEvent` - Collectible spawning
- `PlayerInteractAtEntityEvent` - Collectible collection
- `InventoryClickEvent`, `InventoryCloseEvent` - GUI handling
- `EntityDeathEvent` - Mob drops
- `BlockBreakEvent` - Block drops
- `PlayerFishEvent` - Fishing drops
- `LootGenerateEvent` - Loot table drops
- `PlayerArmorChangeEvent` - Goggle detection

## Storage Schema

**Tables:**

```sql
-- Player statistics
players (
    uuid VARCHAR(36) PRIMARY KEY,
    total_collectibles_collected INT,
    total_collections_completed INT,
    first_collection_date BIGINT,
    last_activity_date BIGINT
)

-- Collection completion tracking
collection_progress (
    uuid VARCHAR(36),
    collection_id VARCHAR(64),
    reward_claimed BOOLEAN,
    completed_date BIGINT,
    PRIMARY KEY (uuid, collection_id)
)

-- Individual item collection tracking
collected_items (
    uuid VARCHAR(36),
    collection_id VARCHAR(64),
    item_id VARCHAR(64),
    collected_date BIGINT,
    PRIMARY KEY (uuid, collection_id, item_id)
)

-- Active world collectibles
active_collectibles (
    id VARCHAR(36) PRIMARY KEY,
    hitbox_id VARCHAR(36),
    zone_id VARCHAR(64),
    collection_id VARCHAR(64),
    item_id VARCHAR(64),
    world VARCHAR(64),
    x DOUBLE, y DOUBLE, z DOUBLE,
    tier VARCHAR(32),
    spawned_date BIGINT
)
```

---

*Integration audit: 2026-01-20*
