# Collections - EQ2-Style Collectibles for Paper

A Paper 1.21.4 plugin that brings EverQuest 2's beloved "shiny" collectibles system to Minecraft. Players discover glowing spawn points in the world, collect random items from zone-specific pools, and complete collections for rewards.

## Table of Contents

1. [Core Concepts](#core-concepts)
2. [Player Experience Flow](#player-experience-flow)
3. [Collectible Spawns](#collectible-spawns)
4. [Collections & Items](#collections--items)
5. [Collection Journal GUI](#collection-journal-gui)
6. [Rewards System](#rewards-system)
7. [Admin Configuration](#admin-configuration)
8. [Commands & Permissions](#commands--permissions)
9. [Data Storage](#data-storage)
10. [Technical Implementation](#technical-implementation)
11. [Future Considerations](#future-considerations)

---

## Core Concepts

### Terminology

| Term | Description |
|------|-------------|
| **Collectible** | A world-spawned collectible node (armor stand with particles) |
| **Collection** | A themed set of items to complete (e.g., "Forest Fungi" with 6 mushroom items) |
| **Collection Item** | A single piece belonging to a collection |
| **Spawn Zone** | A configured region where collectibles can appear |
| **Tier** | Rarity/visibility level (Common, Uncommon, Rare, Event) |

### Design Principles

1. **World-shared spawns** - All players see the same collectibles (first-come-first-served with proximity-based race resolution)
2. **Biome-driven theming** - Collections feel native to where they spawn
3. **Trade-friendly** - Items are tradeable until added to journal (per-item soulbound option available)
4. **Admin flexibility** - Highly configurable spawn conditions and rewards
5. **Performance conscious** - Efficient spawning, minimal tick overhead
6. **Folia-ready** - Day-one support for Folia's multithreaded architecture

---

## Player Experience Flow

### Discovery → Collection → Reward

```
┌─────────────────────────────────────────────────────────────────────────┐
│  1. DISCOVER                                                            │
│     Player explores world, sees glowing "?" with particles              │
│                              ↓                                          │
│  2. COLLECT                                                             │
│     Right-click collectible → Random item from zone's collection pool   │
│     Item goes to inventory (tradeable unless soulbound, examinable)     │
│     If inventory full, item drops at player's feet                      │
│                              ↓                                          │
│  3. DECIDE                                                              │
│     ├─ Right-click item → GUI confirmation → Add to journal (consumed)  │
│     ├─ Trade/sell to other players (if not soulbound)                   │
│     └─ Hold for later                                                   │
│                              ↓                                          │
│  4. COMPLETE                                                            │
│     All items in collection obtained → "Complete" status                │
│                              ↓                                          │
│  5. CLAIM                                                               │
│     Open journal GUI → Click "Claim Reward" on completed collection     │
│     (Button blocked if insufficient inventory space)                    │
│     Receive configured rewards (items, XP, commands, etc.)              │
└─────────────────────────────────────────────────────────────────────────┘
```

### Example Scenario

1. Alex is exploring a Dark Forest biome
2. They spot a glowing purple "?" floating above the ground with swirling particles
3. Right-clicking it, they receive "Witherwood Bark" - a collection item
4. Hovering over the item shows: *"Part of the 'Darkwood Specimens' collection. Right-click to add to your journal."*
5. Alex right-clicks the item; a confirmation GUI appears with the item preview
6. They click "Confirm" - the item is consumed and added to their journal
7. Opening `/collections`, they see "Darkwood Specimens" is now 1/5 complete (showing "4 common, 0 rare missing")
8. After finding all 5 items, they click "Claim Reward" and receive a custom "Botanist's Satchel" item

---

## Collectible Spawns

### Visual Appearance

Each collectible consists of:

1. **Interaction Entity** - Invisible clickable hitbox (1.0 x 1.5 blocks) for player interaction
2. **Particle Effect** - Tier-colored particles floating at the spawn location (player-specific packets for visibility control)
3. **Action Bar Prompt** - "Right-click to collect" appears when a player is:
   - Within 5 blocks of the collectible
   - Looking directly at it (~18 degree cone)
   - Able to see the tier (Common always visible, others require goggles)

This minimal visual system keeps collectibles lightweight while providing clear feedback to players when they can interact.

**Tier Visual Distinction:**

| Tier | Particle | Color |
|------|----------|-------|
| Common | HAPPY_VILLAGER | Green |
| Uncommon | ENCHANT | Blue |
| Rare | END_ROD | Gold/Purple |
| Event | FIREWORK | Varies by event |

### Tier Visibility (Collector's Goggles)

Like EQ2's Expert Recognition Goggles and Earring of the Solstice, players must **wear specific helmet items** to see higher-tier collectibles. This creates natural progression and valuable craftable/reward items.

**Visibility Requirements:**

| Tier | Requirement |
|------|-------------|
| Common | None - visible to all players |
| Uncommon | Wearing **Collector's Goggles** |
| Rare | Wearing **Master Collector's Goggles** |
| Event | Wearing any Collector's Goggles OR during active event |

**Important Visibility Behavior:**
- Collectibles outside player's tier are **completely invisible** (no particles, no armor stand rendered client-side)
- Players without goggles cannot interact with invisible collectibles even if they know the location
- Equipping/unequipping goggles triggers immediate visibility refresh
- If a player removes goggles mid-interaction, the interaction **completes** if initiated while visible (grace period)

**Goggle Items:**

```yaml
# Collector's Goggles - Reveals Uncommon collectibles
collectors_goggles:
  material: LEATHER_HELMET
  name: "&bCollector's Goggles"
  lore:
    - "&7Specially crafted lenses that"
    - "&7reveal hidden collectibles."
    - ""
    - "&eReveals: &bUncommon &7collectibles"
  color: "#3399FF"  # Leather armor color
  armor_points: 0   # No protection - meaningful tradeoff vs diamond helmet
  enchantments:
    - UNBREAKING:3
  flags:
    - HIDE_ENCHANTS
  allow_enchanting: true  # Can receive Protection, Respiration, etc.
  soulbound: true  # Configurable - survives death
  persistent_data:
    collections:goggle_tier: "UNCOMMON"

# Master Collector's Goggles - Reveals Uncommon AND Rare collectibles
master_collectors_goggles:
  material: LEATHER_HELMET
  name: "&6Master Collector's Goggles"
  lore:
    - "&7Ancient lenses imbued with"
    - "&7the wisdom of master collectors."
    - ""
    - "&eReveals: &bUncommon &7+ &6Rare &7collectibles"
  color: "#FFD700"  # Gold color
  armor_points: 0
  enchantments:
    - UNBREAKING:3
    - MENDING:1
  flags:
    - HIDE_ENCHANTS
  allow_enchanting: true
  soulbound: true
  persistent_data:
    collections:goggle_tier: "RARE"
```

**Obtaining Goggles:**

Admins configure how players obtain goggles:

1. **Crafting Recipe** (default) - Custom shaped recipe, **unlocks in recipe book when player first collects any collectible**
2. **Collection Reward** - Reward for completing a "Collector's Initiation" collection
3. **Shop/Economy** - Purchasable via economy plugin
4. **Admin Grant** - `/collections give <player> goggles [tier]`

**Default Crafting Recipes:**

Master Collector's Goggles **require Collector's Goggles as ingredient** (upgrade path):

```
Collector's Goggles:
[GLASS_PANE] [LEATHER] [GLASS_PANE]
[GOLD_INGOT] [       ] [GOLD_INGOT]

Master Collector's Goggles:
[ENDER_EYE] [COLLECTOR'S_GOGGLES] [ENDER_EYE]
[DIAMOND  ] [     NETHERITE      ] [DIAMOND  ]
```

### Spawn Mechanics

**Zone-Based Spawning:**
- Admins define **Spawn Zones** with boundaries and conditions (supports WorldGuard regions for complex shapes)
- Each zone has a pool of collections that can spawn there
- The plugin maintains `maxCollectiblesPerZone` active spawns per zone
- When a collectible is collected, a new one spawns after `respawnDelay` seconds (per-zone configurable)

**Spawn Point Selection:**
```
1. Select random zone with < maxCollectibles
2. Find valid spawn location using AdaptiveSpawnFinder:
   - Grid-based search starting from zone center
   - Expanding radius if initial attempts fail
   - Condition relaxation fallback (disable sky-access)
   - Detailed failure tracking for debugging
3. Spawn Interaction entity with appropriate tier/collection metadata
4. Register in spawn tracker
5. Collectibles can appear while players watch (magical pop-in effect)
```

### Spawn Algorithm

The plugin uses an **adaptive grid-based algorithm** to reliably find valid spawn locations. This replaces simple random sampling which often fails in zones with restrictive conditions.

**Algorithm Steps:**
```
1. Calculate zone center (from bounds or nearest player)
2. Generate grid points at configurable spacing (default 8 blocks)
3. Shuffle points for randomness, then test each:
   - Find surface Y at X,Z
   - Check zone bounds containment
   - Check all spawn conditions with detailed failure tracking
   - Return immediately on success
4. If no valid location found, double search radius (32 → 64 → 128)
5. If still failing with strict conditions:
   - Retry with relaxed conditions (sky-access disabled)
6. Return SpawnResult with location or detailed failure statistics
```

**Configuration Options (config.yml):**
```yaml
spawn:
  grid-spacing: 8                    # Distance between grid points (blocks)
  initial-radius: 32                 # Starting search area (blocks from center)
  max-radius: 128                    # Maximum search area before giving up
  max-attempts-per-pass: 200         # Locations tested per radius expansion
  allow-condition-relaxation: true   # Enable fallback with relaxed conditions
  debug: false                       # Log detailed spawn attempt info
```

**Failure Tracking:**

When spawns fail, the system tracks why each location was rejected:
- `biome` - Wrong biome for zone/collection/item
- `time-day`/`time-night` - Time of day mismatch
- `light` - Light level out of range
- `sky-access` - No sky access when required
- `y-level` - Outside Y bounds
- `dimension` - Wrong dimension
- `out-of-bounds` - Location outside zone bounds
- `no-surface` - No valid surface block found

The `/collections spawn <zone>` command now shows detailed failure info:
```
Failed to spawn collectible after 400 attempts.
Top failure reason: biome
Details: biome: 180, sky-access: 120, time-night: 100
```

**Performance Considerations:**
- Grid spacing of 8 blocks balances coverage vs computation
- Max 200 attempts per pass prevents blocking on huge zones
- Early exit on success minimizes unnecessary checks
- Shuffled grid points maintain spawn randomness

**Chunk Loading Behavior:**
- Collectibles persist when chunks unload (stored in database with location)
- On chunk load, recreate Interaction entities for any collectibles in that chunk
- **Immediate despawn** on chunk unload (but keep database record)

**Periodic Validity Check:**
- Every 5 minutes, validate all active collectibles
- If spawn location becomes invalid (block placed on top, water flows in), despawn and respawn elsewhere

**Collectible Despawn Timer:**

Collectibles automatically despawn if not collected within a configurable time period:

- **Default**: 10 minutes
- **Timer continues** even when chunks unload (timestamp-based, not tick-based)
- **On despawn**: Zone respawn timer starts, allowing new collectibles to spawn

This prevents:
- Players "saving" collectibles by leaving them uncollected
- Stale collectibles in unvisited areas
- Farming strategies that rely on ignoring certain spawns

Configuration:
```yaml
spawn:
  despawn-after-minutes: 10  # Set to 0 to disable
```

**Zone Disable Behavior:**
- If a zone is disabled via config, existing collectibles **relocate** to nearest enabled zone with same collection

### Spawn Conditions

Admins can configure spawn zones with any combination of:

| Condition | Description | Example |
|-----------|-------------|---------|
| `biomes` | List of allowed biomes | `[DARK_FOREST, FOREST, BIRCH_FOREST]` |
| `dimensions` | Allowed dimensions | `[OVERWORLD]` |
| `worlds` | Specific world names | `[world, world_custom]` |
| `minY` / `maxY` | Height range | `minY: 60, maxY: 100` |
| `timeRange` | Tick range (0-24000) | `0-12000` (daytime only) |
| `weather` | Required weather | `[CLEAR, RAIN]` |
| `lightLevel` | Min/max light | `minLight: 0, maxLight: 7` |
| `moonPhase` | Specific moon phases | `[FULL_MOON]` |
| `boundingBox` | Coordinate bounds | `minX, maxX, minZ, maxZ` |
| `worldguardRegion` | WorldGuard region name | `forest_zone_1` |
| `requiredBlocks` | Must be near blocks | `[MYCELIUM, PODZOL]` |

**Special Conditions:**
- `requireSky: true` - Must have sky access
- `underground: true` - Must be below sea level with no sky
- `nearWater: 5` - Must be within N blocks of water

**Block Handling:**
- Barrier blocks are treated as air (collectibles cannot spawn on them)
- Only visible solid blocks are valid spawn surfaces

### Collection & Item Level Conditions

In addition to zone-level conditions, spawn conditions can be defined at the **collection** and **item** levels. This allows fine-tuned control over where and when specific collectibles can spawn.

**Condition Hierarchy:**
```
Zone Conditions → Collection Conditions → Item Conditions
     ↓                    ↓                     ↓
  (broadest)          (narrower)           (most specific)
```

All conditions at each level must pass for a collectible to spawn. Item conditions override collection conditions when specified.

**Available Conditions (all levels):**

| Condition | Description | Example |
|-----------|-------------|---------|
| `biomes` | List of allowed biomes | `[DESERT, BADLANDS]` |
| `dimensions` | Allowed dimensions | `[NORMAL, NETHER]` |
| `min-y` / `max-y` | Height range | `min-y: 64, max-y: 128` |
| `min-light` / `max-light` | Light level range (0-15) | `min-light: 0, max-light: 7` |
| `require-sky` | Must have sky access | `require-sky: true` |
| `underground` | Must be below solid ceiling | `underground: true` |
| `time` | Time of day condition | `time: NIGHT` |

**Time Conditions:**

| Value | Description |
|-------|-------------|
| `ALWAYS` | No time restriction (default) |
| `DAY` | Only spawns during day (ticks 0-12000) |
| `NIGHT` | Only spawns during night (ticks 12000-24000) |

**Collection-Level Example:**
```yaml
# collections/desert_treasures.yml
id: "desert_treasures"
name: "Desert Treasures"
tier: UNCOMMON

# Collection-level conditions (applies to ALL items)
conditions:
  biomes:
    - DESERT
    - BADLANDS
  dimensions:
    - NORMAL
  min-y: 64
  require-sky: true

items:
  ancient_pottery:
    name: "Ancient Pottery"
    material: DECORATED_POT
    weight: 10
    # No item conditions = spawns anytime in desert

  moonlit_scarab:
    name: "Moonlit Scarab"
    material: SPIDER_EYE
    weight: 5
    # Item-level conditions (additional restrictions)
    conditions:
      time: NIGHT        # Only spawns at night
      max-light: 4       # Must be dark
```

**Condition Override Behavior:**

When an item specifies a condition, it overrides the collection-level value for that specific condition:

| Scenario | Result |
|----------|--------|
| Collection has `biomes: [DESERT]`, Item has no biomes | DESERT applies |
| Collection has `biomes: [DESERT]`, Item has `biomes: [FOREST]` | FOREST applies |
| Collection has `require-sky: true`, Item has `underground: true` | underground applies |
| Collection has `min-y: 64`, Item has `min-y: 32` | min-y: 32 applies |

**Item Pre-Selection:**

Items are selected at spawn time based on spawn conditions. This ensures that an item requiring night conditions is only spawned at night. The item ID is stored in the collectible record, so the same item is given to the player regardless of when they collect it.

**WorldGuard Integration:**
- Zones can reference WorldGuard regions for complex polygon shapes
- Collectible spawning **ignores** WG flags (only plugin zone config matters)
- Use `/collections createzone` for interactive zone creation

### Alternative Drop Sources

In addition to natural world spawning, collection items can drop from **alternative sources**:

| Source | Event | Example |
|--------|-------|---------|
| **Mob Kills** | EntityDeathEvent | Rare item from zombies at night |
| **Block Breaks** | BlockBreakEvent | Item from desert sand above Y=64 |
| **Fishing** | PlayerFishEvent | Treasure catch in ocean biome |
| **Container Loot** | LootGenerateEvent | Dungeon chests in strongholds |

**Key Features:**
- **No goggles required** - Alternative drops bypass tier visibility
- **Configurable per-item** - Each item can have different drop sources
- **Stacks with natural spawning** - Items can have both methods
- **Enchantment bonuses** - Looting/Fortune can increase drop rates
- **Very low probabilities** - Supports rates like 0.00001 (0.001%)

**Drop Source Configuration:**

```yaml
items:
  cursed_tooth:
    name: "Cursed Tooth"
    material: BONE
    weight: 0  # No natural spawning - drops only

    drop-sources:
      mobs:
        - entities: [ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED]
          chance: 0.00001        # 0.001% base chance
          player-kill-required: true
          conditions:
            time: NIGHT
          enchant-bonus:
            looting: 0.000005    # +0.0005% per looting level

      blocks:
        - types: [SAND, RED_SAND, SANDSTONE]
          chance: 0.0001         # 0.01% chance
          conditions:
            biomes: [DESERT, BADLANDS]
            min-y: 64
          enchant-bonus:
            fortune: 0.00002

      fishing:
        - chance: 0.001          # 0.1% chance
          catch-type: TREASURE   # FISH, TREASURE, JUNK, or ANY
          conditions:
            biomes: [OCEAN, DEEP_OCEAN]
          enchant-bonus:
            luck-of-the-sea: 0.0005

      loot:
        - chance: 0.02           # 2% per chest
          loot-tables: [NETHER_BRIDGE, BASTION_TREASURE]
          conditions:
            dimensions: [THE_NETHER]
```

**Supported Entity Types:**
- Standard: `ZOMBIE`, `SKELETON`, `CREEPER`, `SPIDER`, `ENDERMAN`
- Nether: `WITHER_SKELETON`, `BLAZE`, `GHAST`, `PIGLIN`, `HOGLIN`
- Bosses: `ENDER_DRAGON`, `WITHER`, `ELDER_GUARDIAN`

**Supported Loot Tables:**
- Structures: `BURIED_TREASURE`, `DESERT_PYRAMID`, `END_CITY_TREASURE`
- Nether: `NETHER_BRIDGE`, `BASTION_TREASURE`, `BASTION_OTHER`
- Dungeons: `SIMPLE_DUNGEON`, `STRONGHOLD_CORRIDOR`, `STRONGHOLD_LIBRARY`
- Villages: `VILLAGE_ARMORER`, `VILLAGE_WEAPONSMITH`, `VILLAGE_TEMPLE`

**Enchantment Bonus Caps:**

To prevent excessive stacking, enchantment bonuses are capped:

| Enchantment | Default Cap |
|-------------|-------------|
| Looting | +50% max |
| Fortune | +50% max |
| Luck of the Sea | +30% max |

---

## Collections & Items

### Collection Structure

```yaml
collection:
  id: "darkwood_specimens"
  name: "Darkwood Specimens"
  description: "Botanical samples from the darkest forests"
  tier: UNCOMMON

  # Collection-level spawn conditions (optional)
  conditions:
    biomes:
      - DARK_FOREST
    dimensions:
      - NORMAL
    min-y: 62
    max-y: 120

  items:
    - id: "witherwood_bark"
      name: "Witherwood Bark"
      material: OAK_BARK          # Per-item material - use any valid Material
      lore:
        - "&7A fragment of bark from a"
        - "&7tree that never sees sunlight."
      weight: 30  # Relative spawn weight
      soulbound: false  # Per-item tradeable control

    - id: "gloomcap_spores"
      name: "Gloomcap Spores"
      material: BROWN_MUSHROOM    # Thematic material choice
      lore:
        - "&7Phosphorescent fungal spores."
      weight: 30
      soulbound: false

    - id: "shadowmoss_sample"
      name: "Shadowmoss Sample"
      material: MOSS_BLOCK
      lore:
        - "&7Moss that grows only in"
        - "&7complete darkness."
      weight: 25
      soulbound: false

    - id: "nightbloom_petal"
      name: "Nightbloom Petal"
      material: PINK_PETALS       # Fits the flower theme
      lore:
        - "&7A rare flower that blooms"
        - "&7under the new moon."
      weight: 10
      soulbound: false
      # Item-level spawn conditions (optional)
      conditions:
        time: NIGHT              # Only spawns at night
        max-light: 7             # Must be relatively dark

    - id: "ancient_amber"
      name: "Ancient Amber"
      material: ORANGE_STAINED_GLASS  # Amber-like appearance
      lore:
        - "&7Fossilized tree resin containing"
        - "&7a perfectly preserved moth."
      weight: 5  # Rare piece
      soulbound: true  # This specific item is soulbound

  rewards:
    experience: 500
    items:
      - material: CHEST
        name: "&6Botanist's Satchel"
    commands:
      - "lp user %player% permission set collections.title.botanist"
    messages:
      - "&aYou've completed the &eDarkwood Specimens &acollection!"
      - "&7You've earned the title: &2[Botanist]"
```

### Item Properties

**Each collection item specifies its own material**, allowing thematic variety within collections. The material should be chosen to visually represent the item (e.g., BONE for fossils, AMETHYST_SHARD for crystals, GOLD_NUGGET for coins). No material validation is performed - admin responsibility.

**Collection items are normal inventory items with:**
- **Per-item material** - Any valid Minecraft material
- Custom display name and lore
- Optional enchantment glint (`glowing: true`)
- Optional custom model data (for servers with resource packs)
- **Modification blocked** - Cannot be renamed via anvil, enchanted, or modified
- PersistentDataContainer tags:
  - `collections:collection_id` - Which collection this belongs to
  - `collections:item_id` - Which item in the collection
  - `collections:soulbound` - Whether it can be traded (per-item flag)
- **Normal container behavior** - Can be stored in chests, hoppers, any container

**Item States:**
1. **In Inventory** - Tradeable (unless soulbound), can be added to journal via right-click → GUI confirmation
2. **In Journal** - Tracked in player data, original item destroyed

### Rarity Weights

Each item has a `weight` value determining drop chance:
```
Drop chance = item_weight / sum(all_item_weights_in_collection)
```

Example with weights 30, 30, 25, 10, 5 (total 100):
- Common items: 30% each
- Uncommon: 25%
- Rare: 10%
- Ultra-rare: 5%

### Race Condition Handling

When two players click the same collectible within milliseconds:
- **Proximity priority** (2D horizontal distance) - closer player wins
- Losing player receives "Already collected!" message
- Distance calculated at click time, ignoring Y axis for fairness

### Collection Cooldown

- **Small cooldown** (0.5-1 second) between collection interactions per player to prevent macro/autoclicker abuse
- No per-zone or global farming cooldowns - first-come-first-served is sufficient

---

## Collection Journal GUI

### Main Menu (`/collections`)

```
╔═══════════════════════════════════════════════════════════════════════╗
║                     📖 Collection Journal                              ║
╠═══════════════════════════════════════════════════════════════════════╣
║                                                                        ║
║  [Forest    ] [Ocean     ] [Cave      ] [Nether    ] [End       ]     ║
║  [Specimens ] [Treasures ] [Crystals  ] [Relics    ] [Artifacts ]     ║
║   3/6 ████░░   0/8 ░░░░░░   5/5 ██████   2/7 ██░░░░   0/4 ░░░░░░      ║
║   (2 common,   (8 missing)   ✓ COMPLETE   (3 common,   (4 missing)    ║
║    1 rare)                                  2 rare)                    ║
║                                                                        ║
║  [Darkwood  ] [Ancient   ] [Frozen    ] [Mesa      ] [Swamp     ]     ║
║  [Specimens ] [Pottery   ] [Fossils   ] [Artifacts ] [Curios    ]     ║
║   4/5 █████░   1/6 █░░░░░   0/5 ░░░░░░   3/4 █████░   2/5 ███░░░      ║
║                                                                        ║
║  [Page <] [1/3] [Page >]   [Sort ▼] [Filter: All ▼] [Stats 📊]        ║
║                                                                        ║
╚═══════════════════════════════════════════════════════════════════════╝
```

**Features:**
- Collections shown as **actual material icons** with progress bar in lore
- Progress shows rarity breakdown: "3/6 (2 common, 1 rare missing)"
- Completed collections have enchant glint
- Click to view collection details (visual only - no action on item click)
- **Sort button**: Alphabetical, Progress %, Completion Date, Tier
- **Filter dropdown** (tag-based): By Tier, By Zone, By Completion Status
- Pagination for large numbers of collections
- Stats button shows overall completion percentage, rank on leaderboard, rarest item found
- **Configurable sounds** per GUI action (open, click, claim) in config.yml

### Collection Detail View

```
╔═══════════════════════════════════════════════════════════════════════╗
║              🍄 Darkwood Specimens (4/5)                               ║
║        "Botanical samples from the darkest forests"                    ║
╠═══════════════════════════════════════════════════════════════════════╣
║                                                                        ║
║     [Witherwood ] [Gloomcap  ] [Shadowmoss] [Nightbloom] [???    ]    ║
║     [Bark     ✓] [Spores  ✓] [Sample   ✓] [Petal    ✓] [       ?]    ║
║                                                                        ║
║─────────────────────────────────────────────────────────────────────── ║
║  Status: 4/5 Items Collected (1 common missing)                        ║
║  Tier: Uncommon                                                        ║
║  Zones: Dark Forest, Roofed Forest                                     ║
║  Hint: Found in: Dark Forest                                           ║
║                                                                        ║
║  Rewards:                                                              ║
║  • 500 Experience                                                      ║
║  • Botanist's Satchel                                                  ║
║  • ??? (Rare collection - hidden until complete)                       ║
║                                                                        ║
║  [← Back]                              [Claim Reward] (disabled)       ║
╚═══════════════════════════════════════════════════════════════════════╝
```

**Features:**
- Collected items shown as **actual materials** from config
- Missing items shown as gray "?" placeholder
- Hover over collected item to see its lore
- Hover over missing item to see biome-only hint: "Found in: Dark Forest"
- **Reward preview**: Common collection rewards visible, Rare collection rewards hidden until complete
- "Claim Reward" button enabled only when 100% complete **and** sufficient inventory space
- After claiming, collection shows "✓ Completed" badge
- Clicking "Back" or closing confirmation GUI returns to main journal

---

## Rewards System

### Reward Types

| Type | Description | Configuration |
|------|-------------|---------------|
| **Experience** | Vanilla XP points | `experience: 500` |
| **Items** | Physical items given to player | List of item configurations |
| **Commands** | Console commands executed | `%player%` placeholder, run as console |
| **Money** | Economy plugin integration | `money: 1000` (requires Vault) |
| **Permissions** | Grant permissions | Via command (e.g., LuckPerms) |
| **Titles** | Prefix/suffix titles | Via command or direct plugin support |
| **Messages** | Congratulatory text | Sent to player on claim |
| **Fireworks** | Celebration effect | `fireworks: true` |
| **Sound** | Play sound effect | `sound: UI_TOAST_CHALLENGE_COMPLETE` |

**Rewards are fixed only** - no randomization support (predictable for players).

**Re-claiming**: If a collection is reset via admin command, rewards can be claimed again each time it's re-completed.

### Meta-Collections

Collections can require other collections as prerequisites. Meta-collections **show in journal with progress** even before all prerequisites are met:

```yaml
meta_collection:
  id: "master_botanist"
  name: "Master Botanist"
  description: "Complete all botanical collections"
  required_collections:
    - "forest_specimens"
    - "darkwood_specimens"
    - "swamp_flora"
    - "jungle_botanicals"
  rewards:
    experience: 5000
    commands:
      - "lp user %player% permission set collections.title.master_botanist"
    items:
      - material: WRITTEN_BOOK
        name: "&6Encyclopedia Botanica"
```

Display in journal: "3/4 required collections complete"

---

## Admin Configuration

### Main Config (`config.yml`)

```yaml
# Collections Configuration

# Database settings
storage:
  type: SQLITE  # SQLITE, MYSQL, or YAML
  scope: GLOBAL  # GLOBAL (single file) or PER_WORLD (default: GLOBAL)
  mysql:
    host: localhost
    port: 3306
    database: collections
    username: root
    password: ""

# Global spawn settings
spawning:
  enabled: true
  tickInterval: 100           # Check for respawns every N ticks (5 seconds)
  maxCollectiblesPerZone: 5   # Maximum active collectibles per spawn zone
  respawnDelay: 60            # Seconds before a new collectible spawns after collection
  minDistanceBetween: 10      # Minimum blocks between collectibles
  despawnOnRestart: false     # Clear all collectibles on server restart
  collectionCooldown: 500     # Milliseconds between collection interactions (anti-macro)
  afkTimeout: 600             # Seconds (10 min) of no movement before player is AFK
  validityCheckInterval: 300  # Seconds (5 min) between validity checks

# Visual settings
visuals:
  particleIntensity: MEDIUM   # LOW, MEDIUM, HIGH (server-side control)
  particleInterval: 10        # Ticks between particle updates
  particleCount: 5            # Particles per update
  useHologram: true           # Show tier name above collectible
  hologramOffset: 1.5         # Blocks above collectible
  hologramVisibleDistance: 8  # Blocks - fades beyond this
  renderDistance: 32          # Blocks - how far players can see collectibles
  collectAnimation: POP       # Particle burst + sound on collection

# Tier visibility (goggles)
goggles:
  enabled: true
  soulbound: true             # Goggles survive death
  # Head textures for collectible "?" markers (Base64 from minecraft-heads.com)
  collectibleHeads:
    common: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2YyNzA..."
    uncommon: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGE5OTM..."
    rare: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzM2ODY..."
    event: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTY0N2I..."
  # Goggle item definitions
  items:
    collectors_goggles:
      material: LEATHER_HELMET
      name: "&bCollector's Goggles"
      color: "#3399FF"
      revealsTiers: [UNCOMMON]
      recipe:
        enabled: true
        unlockOnFirstCollect: true  # Recipe appears in book after first collectible
        shape: ["GPG", "G G"]
        ingredients:
          G: GLASS_PANE
          P: LEATHER
    master_collectors_goggles:
      material: LEATHER_HELMET
      name: "&6Master Collector's Goggles"
      color: "#FFD700"
      revealsTiers: [UNCOMMON, RARE]
      recipe:
        enabled: true
        unlockOnFirstCollect: true
        requiresBasicGoggles: true  # Upgrade path
        shape: ["EGE", "DND"]
        ingredients:
          E: ENDER_EYE
          G: "collections:collectors_goggles"  # Reference to basic goggles
          D: DIAMOND
          N: NETHERITE_INGOT

# Item settings
items:
  glowCollected: true         # Enchant glint on collected-status items in GUI
  preventModification: true   # Block anvil/enchanting on collection items

# GUI settings
gui:
  collectionsPerPage: 21      # Items per page in main menu (multiple of 7)
  showHints: true             # Show location hints for missing items
  hintDetail: BIOME_ONLY      # BIOME_ONLY - vague hints like "Found in: Dark Forest"
  confirmAddToJournal: true   # Show GUI confirmation before consuming item
  cancelReturnsToJournal: true  # Closing confirmation returns to main journal

# GUI sounds (all configurable)
sounds:
  guiOpen: ITEM_BOOK_PAGE_TURN
  guiClick: UI_BUTTON_CLICK
  collectCollectible: ENTITY_EXPERIENCE_ORB_PICKUP
  addToJournal: ITEM_BOOK_PAGE_TURN
  collectionComplete: UI_TOAST_CHALLENGE_COMPLETE
  claimReward: ENTITY_PLAYER_LEVELUP

# Messages (supports MiniMessage format with legacy & fallback)
messages:
  collectibleCollected: "<green>You found a <yellow><item></yellow>!"
  alreadyHaveItem: "<yellow>You already have this in your journal. Consider trading it!"
  itemAddedToJournal: "<green>Added <yellow><item></yellow> to your collection journal."
  duplicateInJournal: "<red>You already have this item in your journal."
  collectionComplete: "<gold>★ Collection Complete: <yellow><collection></yellow>!"
  rewardClaimed: "<green>Rewards claimed for <yellow><collection></yellow>!"
  inventoryFull: "<red>Your inventory is full! Item dropped at your feet."
  rewardInventoryFull: "<red>You need at least <count> free inventory slots to claim rewards."

# Player data settings
data:
  loadOnJoin: true            # Async load on PlayerJoinEvent
  progressScope: GLOBAL       # GLOBAL across all worlds

# Admin settings
admin:
  autoBackupBeforeReset: true  # Backup player data before /collections reset commands
  debugDisplay: SCOREBOARD     # SCOREBOARD sidebar for debug mode

# Integration settings
integrations:
  worldguard: true            # Enable WorldGuard region support for zones
  dynmap: true                # Enable Dynmap layer for admin (shows collectible locations)
  bluemap: true               # Enable BlueMap layer for admin
```

### Spawn Zones (`zones.yml`)

```yaml
zones:
  dark_forest_surface:
    name: "Dark Forest Surface"
    enabled: true

    # Location bounds (optional - if omitted, uses conditions only)
    # Can also use WorldGuard region instead
    bounds:
      world: "world"
      minX: -10000
      maxX: 10000
      minZ: -10000
      maxZ: 10000
    # OR:
    # worldguardRegion: "dark_forest_zone"

    # Spawn conditions
    conditions:
      biomes:
        - DARK_FOREST
      dimensions:
        - OVERWORLD
      minY: 62
      maxY: 120
      requireSky: true

    # Collections that can spawn here
    collections:
      - darkwood_specimens
      - forest_fungi

    # Zone-specific overrides
    maxCollectibles: 8
    respawnDelay: 45

  deep_caves:
    name: "Deep Caves"
    enabled: true
    conditions:
      dimensions:
        - OVERWORLD
      maxY: 0
      lightLevel:
        max: 4
      underground: true
    collections:
      - cave_crystals
      - ancient_fossils
    maxCollectibles: 10

  nether_wastes:
    name: "Nether Wastes"
    enabled: true
    conditions:
      dimensions:
        - THE_NETHER
      biomes:
        - NETHER_WASTES
        - SOUL_SAND_VALLEY
    collections:
      - nether_relics
      - soul_fragments
```

### Collections (`collections/`)

Each collection in its own file for organization:

```
plugins/Collections/
├── config.yml
├── zones.yml
├── collections/
│   ├── darkwood_specimens.yml
│   ├── forest_fungi.yml
│   ├── cave_crystals.yml
│   ├── ocean_treasures.yml
│   └── ...
└── data/
    ├── collections.db (SQLite)
    └── players/
        └── <uuid>.yml (if using YAML storage)
```

---

## Commands & Permissions

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/collections` | Open collection journal GUI | `collections.use` |
| `/collections list` | List all collections (text) | `collections.use` |
| `/collections view <id>` | View specific collection | `collections.use` |
| `/collections progress` | Show overall progress stats | `collections.use` |

### Admin Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/collections reload` | Reload all configuration (immediate full - despawns and respawns all) | `collections.admin` |
| `/collections spawn <zone>` | Force spawn a collectible in zone | `collections.admin` |
| `/collections clear [zone]` | Remove all/zone collectibles | `collections.admin` |
| `/collections give item <player> <collection> <item>` | Give collection item (physical, tradeable) | `collections.admin` |
| `/collections give progress <player> <collection> <item>` | Add directly to journal (bypasses item) | `collections.admin` |
| `/collections give goggles <player> <tier>` | Give goggles to player | `collections.admin` |
| `/collections complete <player> <collection>` | Complete collection for player | `collections.admin` |
| `/collections reset <player> [collection]` | Reset player progress (auto-backup first) | `collections.admin` |
| `/collections stats` | Show global statistics | `collections.admin` |
| `/collections leaderboard` | Show top collectors | `collections.admin` |
| `/collections debug` | Toggle debug mode (scoreboard sidebar) | `collections.admin` |
| `/collections createzone` | Interactive zone creation | `collections.admin` |
| `/collections event start <name>` | Start an event manually | `collections.admin` |
| `/collections event end [name]` | End an event manually | `collections.admin` |

**Tab completion** with exact IDs is supported for all commands referencing collections/items.

### Permissions

```yaml
permissions:
  collections.use:
    description: Basic access to collections
    default: true

  collections.admin:
    description: Administrative commands
    default: op

  collections.craft.goggles:
    description: Can craft Collector's Goggles
    default: true

  collections.craft.mastergoggles:
    description: Can craft Master Collector's Goggles
    default: true

  collections.bypass.goggles:
    description: See all tier collectibles without goggles
    default: op

  collections.bypass.cooldown:
    description: No collection cooldown
    default: op
```

---

## Data Storage

### Player Data Structure

```java
public class PlayerCollectionData {
    private UUID playerId;
    private Map<String, CollectionProgress> collections;
    private int totalCollectiblesCollected;
    private int totalCollectionsCompleted;
    private long firstCollectionDate;
    private long lastActivityDate;
}

public class CollectionProgress {
    private String collectionId;
    private Set<String> collectedItems;
    private boolean rewardClaimed;
    private long completedDate;  // null if incomplete
}
```

### Database Schema (SQLite/MySQL)

```sql
-- Player base data
CREATE TABLE players (
    uuid VARCHAR(36) PRIMARY KEY,
    total_collectibles_collected INT DEFAULT 0,
    total_collections_completed INT DEFAULT 0,
    first_collection_date BIGINT,
    last_activity_date BIGINT
);

-- Collection progress
CREATE TABLE collection_progress (
    uuid VARCHAR(36),
    collection_id VARCHAR(64),
    reward_claimed BOOLEAN DEFAULT FALSE,
    completed_date BIGINT,
    PRIMARY KEY (uuid, collection_id),
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);

-- Individual items collected
CREATE TABLE collected_items (
    uuid VARCHAR(36),
    collection_id VARCHAR(64),
    item_id VARCHAR(64),
    collected_date BIGINT,
    PRIMARY KEY (uuid, collection_id, item_id),
    FOREIGN KEY (uuid, collection_id) REFERENCES collection_progress(uuid, collection_id)
);

-- Active collectibles in world
CREATE TABLE active_collectibles (
    id VARCHAR(36) PRIMARY KEY,  -- Collectible UUID
    hitbox_id VARCHAR(36),       -- UUID of Interaction entity
    zone_id VARCHAR(64),
    collection_id VARCHAR(64),
    item_id VARCHAR(64),         -- Pre-selected item ID
    world VARCHAR(64),
    x DOUBLE,
    y DOUBLE,
    z DOUBLE,
    tier VARCHAR(32),
    spawned_date BIGINT
);
```

**Startup Validation:** On server start, validate all active collectibles against current collection definitions. Remove orphaned collectibles referencing deleted/renamed collections.

**Schema Migration:** When collection definitions change (items added/removed), use **additive-only** migration - new items added to existing progress, removed items stay "collected" but hidden in UI.

---

## Technical Implementation

### Project Structure

```
src/main/java/com/blockworlds/collections/
├── Collections.java                 # Main plugin class
├── command/
│   ├── CollectionsCommand.java      # Player commands (Brigadier)
│   └── CollectionsAdminCommand.java # Admin commands (Brigadier)
├── config/
│   ├── ConfigManager.java           # Main config handling
│   ├── CollectionConfig.java        # Collection YAML parsing
│   └── ZoneConfig.java              # Zone configuration
├── listener/
│   ├── CollectibleInteractListener.java  # Right-click collectible collection
│   ├── ItemUseListener.java         # Right-click item to add to journal
│   ├── ItemModifyListener.java      # Block anvil/enchanting on collection items
│   ├── ChunkListener.java           # Handle chunk load/unload
│   ├── ArmorChangeListener.java     # Goggle equip/unequip visibility
│   ├── PlayerListener.java          # Join/quit data handling
│   ├── MobDropListener.java         # Alternative drops from mob kills
│   ├── BlockDropListener.java       # Alternative drops from block breaks
│   ├── FishingDropListener.java     # Alternative drops from fishing
│   └── LootDropListener.java        # Alternative drops from container loot
├── manager/
│   ├── CollectibleManager.java      # Spawn/despawn logic
│   ├── CollectionManager.java       # Collection/item definitions
│   ├── PlayerDataManager.java       # Player progress tracking
│   ├── GoggleManager.java           # Goggle visibility logic
│   ├── EventManager.java            # Event system (date-based + manual)
│   ├── DropSourceManager.java       # Alternative drop source handling
│   └── RewardManager.java           # Reward distribution
├── model/
│   ├── Collection.java              # Collection definition
│   ├── CollectionItem.java          # Item within collection
│   ├── DropSources.java             # Container for drop source configs
│   ├── MobDropSource.java           # Mob kill drop configuration
│   ├── BlockDropSource.java         # Block break drop configuration
│   ├── FishingDropSource.java       # Fishing drop configuration
│   ├── LootDropSource.java          # Container loot drop configuration
│   ├── SpawnZone.java               # Zone definition
│   ├── Collectible.java             # Active collectible instance
│   ├── CollectibleTier.java         # Tier enum
│   ├── GoggleType.java              # Goggle definitions
│   └── PlayerProgress.java          # Player's collection data
├── gui/
│   ├── CollectionMenuGUI.java       # Main menu
│   ├── CollectionDetailGUI.java     # Single collection view
│   ├── ConfirmAddGUI.java           # Confirmation before journal add
│   └── GUIManager.java              # GUI utilities
├── storage/
│   ├── Storage.java                 # Interface
│   ├── SQLiteStorage.java
│   ├── MySQLStorage.java
│   └── YAMLStorage.java
├── recipe/
│   └── GoggleRecipeManager.java     # Custom crafting recipes with unlock
├── spawn/
│   ├── AdaptiveSpawnFinder.java     # Grid-based spawn location finding
│   ├── SpawnFailureStats.java       # Failure reason tracking
│   └── SpawnResult.java             # Spawn attempt result type
├── task/
│   ├── CollectibleSpawnTask.java    # Periodic spawn checker (Folia-compatible)
│   ├── ValidityCheckTask.java       # Periodic location validation
│   ├── ParticleTask.java            # Particle animations (player-specific)
│   └── ActionBarPromptTask.java     # Action bar prompts when looking at collectibles
├── integration/
│   ├── WorldGuardHook.java          # WG region support
│   ├── DynmapHook.java              # Admin map layer
│   └── BlueMapHook.java             # Admin map layer
├── api/
│   ├── CollectionsAPI.java          # Public API for other plugins
│   ├── event/
│   │   ├── CollectibleCollectEvent.java
│   │   ├── CollectionCompleteEvent.java
│   │   ├── ItemAddedToJournalEvent.java
│   │   └── RewardClaimedEvent.java
│   └── CollectionsAPIProvider.java  # API access point
└── util/
    ├── ItemBuilder.java             # Item creation utilities
    ├── HeadUtil.java                # Player head texture utilities
    ├── MessageUtil.java             # MiniMessage with legacy fallback
    └── LocationUtil.java            # Spawn point finding
```

### Key Implementation Details

**Interaction Entity Collectibles:**
```java
public Collectible spawnCollectible(Location location, Collection collection, CollectibleTier tier) {
    UUID collectibleId = UUID.randomUUID();

    // Spawn Interaction entity (invisible hitbox for clicking)
    // Particles are handled by ParticleTask, action bar prompt by ActionBarPromptTask
    Interaction hitbox = location.getWorld().spawn(location, Interaction.class, interaction -> {
        interaction.setInteractionWidth(1.0f);
        interaction.setInteractionHeight(1.5f);
        interaction.setPersistent(false);  // We manage persistence ourselves

        // Store metadata on the hitbox
        PersistentDataContainer pdc = interaction.getPersistentDataContainer();
        pdc.set(COLLECTIBLE_KEY, PersistentDataType.BOOLEAN, true);
        pdc.set(COLLECTIBLE_ID_KEY, PersistentDataType.STRING, collectibleId.toString());
        pdc.set(COLLECTION_KEY, PersistentDataType.STRING, collection.getId());
        pdc.set(TIER_KEY, PersistentDataType.STRING, tier.name());
    });

    Collectible collectible = new Collectible(collectibleId, hitbox.getUniqueId(),
        location, collection, tier);
    activeCollectibles.put(collectibleId, collectible);
    storage.saveCollectible(collectible);

    return collectible;
}
```

**Goggle Visibility System:**
```java
public class GoggleManager {

    public boolean canPlayerSeeCollectible(Player player, Collectible collectible) {
        CollectibleTier tier = collectible.getTier();

        // Common tier always visible
        if (tier == CollectibleTier.COMMON) return true;

        // Event tier visible during events or with any goggles
        if (tier == CollectibleTier.EVENT) {
            return eventManager.isEventActive() || hasAnyGoggles(player);
        }

        // Check bypass permission
        if (player.hasPermission("collections.bypass.goggles")) return true;

        // Check equipped goggles
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null) return false;

        PersistentDataContainer pdc = helmet.getItemMeta().getPersistentDataContainer();
        String goggleTier = pdc.get(GOGGLE_TIER_KEY, PersistentDataType.STRING);

        if (goggleTier == null) return false;

        return getVisibleTiers(goggleTier).contains(tier);
    }

    private Set<CollectibleTier> getVisibleTiers(String goggleTier) {
        return switch (goggleTier) {
            case "UNCOMMON" -> Set.of(CollectibleTier.UNCOMMON);
            case "RARE" -> Set.of(CollectibleTier.UNCOMMON, CollectibleTier.RARE);
            default -> Set.of();
        };
    }
}

// Hide/show collectibles based on goggles - player-specific packets
@EventHandler
public void onArmorChange(PlayerArmorChangeEvent event) {
    if (event.getSlotType() != EquipmentSlot.HEAD) return;

    Player player = event.getPlayer();
    // Refresh visibility for all nearby collectibles
    Bukkit.getRegionScheduler().run(plugin, player.getLocation(), task -> {
        collectibleManager.refreshVisibilityForPlayer(player);
    });
}

// Use player.hideEntity/showEntity for visibility control
public void refreshVisibilityForPlayer(Player player) {
    for (Collectible collectible : getNearbyCollectibles(player.getLocation(), renderDistance)) {
        boolean canSee = goggleManager.canPlayerSeeCollectible(player, collectible);
        Entity hitboxEntity = collectible.getHitbox();

        if (canSee) {
            player.showEntity(plugin, hitboxEntity);
        } else {
            player.hideEntity(plugin, hitboxEntity);
        }
    }
}
```

**Player-Specific Particle Packets:**
```java
// Only send particles to players who can see the collectible
public void spawnParticles(Collectible collectible) {
    Location loc = collectible.getLocation();
    CollectibleTier tier = collectible.getTier();
    Particle particle = tier.getParticle();

    for (Player player : loc.getWorld().getPlayers()) {
        if (player.getLocation().distance(loc) > renderDistance) continue;
        if (!goggleManager.canPlayerSeeCollectible(player, collectible)) continue;

        // Send particle packet only to this player
        player.spawnParticle(particle, loc, particleCount, 0.2, 0.3, 0.2);
    }
}
```

**Chunk Load Handling:**
```java
@EventHandler
public void onChunkLoad(ChunkLoadEvent event) {
    Bukkit.getRegionScheduler().runDelayed(plugin, event.getChunk().getBlock(0,0,0).getLocation(), task -> {
        List<Collectible> collectiblesInChunk = storage.getCollectiblesInChunk(
            event.getWorld().getName(),
            event.getChunk().getX(),
            event.getChunk().getZ()
        );

        for (Collectible collectible : collectiblesInChunk) {
            if (!collectible.isSpawned()) {
                collectibleManager.recreateEntities(collectible);
            }
        }
    }, 1L);
}

@EventHandler
public void onChunkUnload(ChunkUnloadEvent event) {
    // Immediate removal
    for (Collectible collectible : getCollectiblesInChunk(event.getChunk())) {
        collectible.removeEntities();  // Remove interaction entity
        collectible.setSpawned(false);
        // Keep database record - will respawn on chunk load
    }
}
```

**Folia-Compatible Spawn Task:**
```java
public void startSpawnTask() {
    Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
        for (SpawnZone zone : zoneManager.getEnabledZones()) {
            int currentCount = getActiveCollectiblesInZone(zone);
            if (currentCount < zone.getMaxCollectibles()) {
                // Check respawn cooldown
                if (canSpawnInZone(zone)) {
                    attemptSpawnInZone(zone);
                }
            }
        }
    }, 20L, config.getSpawnTickInterval());
}

private boolean isPlayerAFK(Player player) {
    Long lastMove = lastMovementTime.get(player.getUniqueId());
    if (lastMove == null) return false;
    return System.currentTimeMillis() - lastMove > config.getAfkTimeout() * 1000L;
}

private boolean attemptSpawnInZone(SpawnZone zone) {
    Location loc = findValidSpawnLocation(zone);
    if (loc == null) return false;

    // Check for nearby non-AFK players
    for (Player player : loc.getWorld().getPlayers()) {
        if (player.getLocation().distance(loc) < renderDistance && !isPlayerAFK(player)) {
            // Player nearby - spawn anyway (magical pop-in is OK)
        }
    }

    Collection collection = selectRandomCollection(zone);
    CollectibleTier tier = collection.getTier();
    spawnCollectible(loc, collection, tier);
    return true;
}
```

**Race Condition Handling (2D Proximity):**
```java
private final Map<UUID, Long> collectionCooldowns = new ConcurrentHashMap<>();
private final Map<UUID, AtomicBoolean> collectionLocks = new ConcurrentHashMap<>();

@EventHandler
public void onCollectibleInteract(PlayerInteractAtEntityEvent event) {
    Entity entity = event.getRightClicked();
    Collectible collectible = getCollectibleFromEntity(entity);
    if (collectible == null) return;

    Player player = event.getPlayer();
    event.setCancelled(true);

    // Check cooldown (anti-macro)
    Long lastCollect = collectionCooldowns.get(player.getUniqueId());
    if (lastCollect != null && System.currentTimeMillis() - lastCollect < config.getCollectionCooldown()) {
        return;
    }

    // Check visibility (grace period - if they started while visible, allow completion)
    if (!goggleManager.canPlayerSeeCollectible(player, collectible)) {
        return;
    }

    // Attempt to acquire lock
    AtomicBoolean lock = collectionLocks.computeIfAbsent(collectible.getId(), k -> new AtomicBoolean(false));
    if (!lock.compareAndSet(false, true)) {
        // Another player is collecting - check proximity for race condition
        handleRaceCondition(collectible, player);
        return;
    }

    try {
        processCollection(player, collectible);
    } finally {
        collectionLocks.remove(collectible.getId());
    }
}

private void handleRaceCondition(Collectible collectible, Player player) {
    // 2D horizontal distance (ignore Y)
    Location cLoc = collectible.getLocation();
    double playerDist2D = Math.sqrt(
        Math.pow(player.getLocation().getX() - cLoc.getX(), 2) +
        Math.pow(player.getLocation().getZ() - cLoc.getZ(), 2)
    );

    // Store pending claim with distance
    pendingClaims.compute(collectible.getId(), (k, existing) -> {
        if (existing == null || playerDist2D < existing.distance()) {
            return new PendingClaim(player, playerDist2D);
        }
        return existing;
    });

    // The winning player (closest) gets processed in processCollection
}
```

**Developer API:**
```java
public interface CollectionsAPI {
    // Query methods
    PlayerProgress getPlayerProgress(UUID playerId);
    Collection getCollection(String collectionId);
    List<Collection> getAllCollections();
    boolean hasPlayerCompletedCollection(UUID playerId, String collectionId);

    // Modification methods
    void grantCollectionItem(UUID playerId, String collectionId, String itemId);
    void completeCollection(UUID playerId, String collectionId);
    void resetPlayerProgress(UUID playerId, String collectionId);

    // Spawn methods
    Collectible spawnCollectible(Location location, String collectionId);
    void despawnCollectible(UUID collectibleId);
    List<Collectible> getActiveCollectibles();
}

// Custom events
public class CollectibleCollectEvent extends PlayerEvent implements Cancellable {
    private final Collectible collectible;
    private final CollectionItem item;
    // ...
}

public class CollectionCompleteEvent extends PlayerEvent {
    private final Collection collection;
    // ...
}

public class RewardClaimedEvent extends PlayerEvent {
    private final Collection collection;
    private final List<Reward> rewards;
    // ...
}
```

---

## Future Considerations

### Phase 2 Features

1. **Citizens Integration** - NPC collectors that players can turn in completed collections to (alternative to GUI)
2. **Mythic Mobs Integration** - Collectibles that spawn from mob kills
3. **PlaceholderAPI Support** - `%collections_completed%`, `%collections_total%`, etc.
4. **Collection Sharing** - See what collections friends have completed
5. **Seasonal Events** - Time-limited collections with unique rewards (date-based + admin control already implemented)

### Phase 3 Features

1. **Aerial Collectibles** - Floating collectibles in the sky (Elytra zones)
2. **Dungeon Integration** - Collections tied to custom dungeons
3. **Discovery Log** - First player to complete a collection gets announcement
4. **Collection Trading** - Trade completed collections (not just items)
5. **Resource Pack Generator** - Auto-generate resource pack for custom items
6. **Prestige System** - Reset completed collections for bonus rewards

### Performance Notes

- Particle tasks use player-specific packets, not global iteration
- Chunk load/unload carefully manages entity lifecycle to prevent duplication
- Database operations are async except for critical reads
- Player data loaded async on join, cached for session
- Validity check runs every 5 minutes, not per-tick
- Consider global collectible cap for large servers
- AFK players excluded from spawn proximity (10 min no-movement)

---

## Appendix: Example Collections Pack

A starter pack of collections showcasing different themes and per-item materials:

| Collection | Items (Material) | Zone | Tier |
|------------|------------------|------|------|
| **Forest Specimens** | Oak Leaf (OAK_LEAVES), Acorn (BEETROOT_SEEDS), Fern Frond (FERN), Wild Berry (SWEET_BERRIES), Bird Feather (FEATHER), Amber Dewdrop (HONEY_BOTTLE) | Forest biomes | Common |
| **Darkwood Specimens** | Witherwood Bark (DARK_OAK_LOG), Gloomcap Spores (BROWN_MUSHROOM), Shadowmoss (MOSS_BLOCK), Nightbloom Petal (PINK_PETALS), Ancient Amber (ORANGE_STAINED_GLASS) | Dark Forest | Uncommon |
| **Ocean Treasures** | Sea Glass (LIGHT_BLUE_STAINED_GLASS_PANE), Coral Fragment (BRAIN_CORAL), Pearl (ENDER_PEARL), Message Bottle (GLASS_BOTTLE), Golden Doubloon (GOLD_NUGGET), Kraken Tooth (BONE), Neptune's Ring (GOLD_INGOT) | Ocean/Beach | Common |
| **Cave Crystals** | Quartz Shard (QUARTZ), Amethyst Chip (AMETHYST_SHARD), Glow Crystal (GLOW_BERRIES), Deep Sapphire (LAPIS_LAZULI), Void Opal (PRISMARINE_SHARD) | Underground | Uncommon |
| **Nether Relics** | Blaze Core (BLAZE_POWDER), Soul Fragment (SOUL_SAND), Wither Bone (WITHER_SKELETON_SKULL), Ghast Tear Crystal (GHAST_TEAR), Ancient Netherite Coin (NETHERITE_SCRAP) | Nether | Rare |
| **End Artifacts** | Chorus Bloom (CHORUS_FLOWER), Shulker Pearl (SHULKER_SHELL), End Crystal Shard (END_CRYSTAL), Dragon Scale (DRAGON_BREATH) | The End | Rare |
| **Frozen Fossils** | Ice Age Bone (BONE), Frozen Flower (BLUE_ORCHID), Mammoth Tusk (POINTED_DRIPSTONE), Prehistoric Amber (YELLOW_STAINED_GLASS) | Snowy biomes | Common |
| **Desert Antiquities** | Scarab Beetle (SPIDER_EYE), Pharaoh's Coin (GOLD_NUGGET), Sandstone Tablet (SANDSTONE), Mummy Wrapping (STRING), Sun Disk (SUNFLOWER) | Desert | Uncommon |

### Starter Goggles Collection

A special "bootstrapping" collection that rewards Collector's Goggles:

```yaml
collection:
  id: "collectors_initiation"
  name: "Collector's Initiation"
  description: "Prove your dedication to discover what's hidden..."
  tier: COMMON

  items:
    - id: "first_find"
      name: "First Find"
      material: FLINT
      lore: ["&7Your first step into collecting."]
      weight: 25
      soulbound: false
    - id: "lucky_clover"
      name: "Lucky Clover"
      material: FERN
      lore: ["&7Fortune favors the curious."]
      weight: 25
      soulbound: false
    - id: "wanderers_compass"
      name: "Wanderer's Compass"
      material: COMPASS
      lore: ["&7Always points to adventure."]
      weight: 25
      soulbound: false
    - id: "old_map_fragment"
      name: "Old Map Fragment"
      material: MAP
      lore: ["&7Part of something larger..."]
      weight: 25
      soulbound: false

  rewards:
    experience: 100
    items:
      - material: LEATHER_HELMET
        name: "&bCollector's Goggles"
        color: "#3399FF"
        lore:
          - "&7Specially crafted lenses that"
          - "&7reveal hidden collectibles."
          - ""
          - "&eReveals: &bUncommon &7collectibles"
        persistent_data:
          collections:goggle_tier: "UNCOMMON"
    messages:
      - "&a✦ You've earned your &bCollector's Goggles&a!"
      - "&7You can now see &bUncommon&7 collectibles in the world."
```

---

*Document Version: 2.0*
*Target: Paper 1.21.4 / Java 21 / Folia-compatible*
*Author: BlockWorlds Development*
