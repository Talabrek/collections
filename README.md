# Collections

An EverQuest 2-inspired collectibles plugin for Paper 1.21.4+ that brings the beloved "shiny" hunting experience to Minecraft.

[![Paper](https://img.shields.io/badge/Paper-1.21.4+-blue)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21+-orange)](https://adoptium.net/)
[![Folia](https://img.shields.io/badge/Folia-Compatible-green)](https://github.com/PaperMC/Folia)

## Overview

Players discover glowing collectible nodes scattered throughout the world, collect random items from zone-specific pools, and complete themed collections for rewards. The system features tiered visibility requiring special goggles, biome-driven theming, and a comprehensive journal GUI.

## Features

### Core Gameplay
- **World-Spawned Collectibles** - Glowing "?" markers with tier-colored particles appear throughout configured zones
- **Zone-Based Spawning** - Define spawn zones with biome, dimension, Y-level, time, and light conditions
- **Adaptive Spawn Algorithm** - Grid-based location finding with automatic radius expansion and condition relaxation
- **Collection Journal** - Beautiful GUI showing progress, missing items, and claimable rewards
- **Tiered Visibility** - Common collectibles visible to all; Uncommon/Rare require Collector's Goggles

### Collector's Goggles
Like EQ2's Expert Recognition Goggles, players must wear special helmets to see higher-tier collectibles:

| Tier | Requirement |
|------|-------------|
| Common | None - visible to all |
| Uncommon | Collector's Goggles |
| Rare | Master Collector's Goggles |
| Event | Any goggles OR during active event |

Goggles can be crafted (recipe unlocks on first collection), earned as rewards, or given by admins.

### Alternative Drop Sources
Collection items can also drop from:
- **Mob Kills** - Configurable per-entity with Looting bonus
- **Block Breaks** - Mining with Fortune bonus
- **Fishing** - Treasure catches with Luck of the Sea bonus
- **Container Loot** - Dungeon chests and structure loot

### Anti-Farming
- **Despawn Timer** - Uncollected collectibles despawn after 10 minutes (configurable)
- **Collection Cooldown** - Prevents macro/autoclicker abuse
- **AFK Detection** - AFK players excluded from spawn proximity calculations
- **Race Resolution** - When two players click simultaneously, closest player wins

### Technical
- **Folia Compatible** - Uses region schedulers for multi-threaded server support
- **SQLite Storage** - Efficient database with HikariCP connection pooling
- **Async Operations** - Database queries don't block the main thread
- **Chunk Persistence** - Collectibles survive chunk unloads and server restarts

## Installation

1. Download the latest JAR from [Releases](https://github.com/Talabrek/collections/releases)
2. Place in your `plugins/` folder
3. Restart the server
4. Configure zones in `plugins/Collections/zones.yml`
5. Add collections in `plugins/Collections/collections/`

## Configuration

### Main Config (`config.yml`)

```yaml
# Storage settings
storage:
  type: SQLITE  # SQLITE, MYSQL, or YAML

# Spawn algorithm tuning
spawn:
  grid-spacing: 8
  initial-radius: 32
  max-radius: 128
  despawn-after-minutes: 10  # Set to 0 to disable

# Visual settings
visuals:
  particle-interval: 10
  particle-count: 5
  render-distance: 32
```

### Spawn Zones (`zones.yml`)

```yaml
zones:
  dark_forest_surface:
    name: "Dark Forest Surface"
    enabled: true
    conditions:
      biomes: [DARK_FOREST]
      dimensions: [OVERWORLD]
      minY: 62
      maxY: 120
      requireSky: true
    collections:
      - darkwood_specimens
    maxCollectibles: 8
```

### Collections (`collections/*.yml`)

```yaml
id: "darkwood_specimens"
name: "Darkwood Specimens"
description: "Botanical samples from the darkest forests"
tier: UNCOMMON

conditions:
  biomes: [DARK_FOREST]

items:
  witherwood_bark:
    name: "Witherwood Bark"
    material: DARK_OAK_LOG
    weight: 30

  nightbloom_petal:
    name: "Nightbloom Petal"
    material: PINK_PETALS
    weight: 10
    conditions:
      time: NIGHT  # Only spawns at night

rewards:
  experience: 500
  items:
    - material: CHEST
      name: "&6Botanist's Satchel"
  messages:
    - "&aYou've completed the &eDarkwood Specimens &acollection!"
```

## Commands

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/collections` | Open collection journal | `collections.use` |
| `/collections list` | List all collections | `collections.use` |
| `/collections progress` | Show overall stats | `collections.use` |

### Admin Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/collections reload` | Reload configuration | `collections.admin` |
| `/collections spawn <zone>` | Force spawn in zone | `collections.admin` |
| `/collections clear [zone]` | Remove collectibles | `collections.admin` |
| `/collections give item <player> <collection> <item>` | Give collection item | `collections.admin` |
| `/collections give goggles <player> <tier>` | Give goggles | `collections.admin` |
| `/collections debug` | Toggle debug mode | `collections.admin` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `collections.use` | Basic access | true |
| `collections.admin` | Admin commands | op |
| `collections.craft.goggles` | Craft Collector's Goggles | true |
| `collections.craft.mastergoggles` | Craft Master Goggles | true |
| `collections.bypass.goggles` | See all tiers without goggles | op |

## Building from Source

```bash
# Clone the repository
git clone https://github.com/Talabrek/collections.git
cd collections

# Build the plugin
./gradlew build

# Run tests
./gradlew test

# Start development server
./gradlew runServer
```

The compiled JAR will be in `build/libs/Collections-1.0.0.jar`

## Requirements

- **Paper 1.21.4+** (or Folia)
- **Java 21+**

## Dependencies

All dependencies are shaded and relocated:
- [HikariCP](https://github.com/brettwooldridge/HikariCP) - Database connection pooling

## API

Other plugins can integrate via the Collections API:

```java
CollectionsAPI api = CollectionsAPIProvider.get();

// Check player progress
PlayerProgress progress = api.getPlayerProgress(player.getUniqueId());
boolean completed = api.hasPlayerCompletedCollection(uuid, "darkwood_specimens");

// Grant items programmatically
api.grantCollectionItem(uuid, "darkwood_specimens", "witherwood_bark");

// Listen for events
@EventHandler
public void onCollect(CollectibleCollectEvent event) {
    Player player = event.getPlayer();
    CollectionItem item = event.getItem();
}
```

## Credits

Inspired by EverQuest 2's collectible "shiny" system, which has entertained players since 2004.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
