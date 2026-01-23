# Architecture Research: Web Panel Integration

**Domain:** Minecraft Plugin Web Panel
**Researched:** 2026-01-23
**Confidence:** HIGH (Javalin+Minecraft pattern is well-documented officially)

## Executive Summary

Integrating a web panel into the existing Collections plugin requires an embedded web server (Javalin recommended) that runs on its own thread pool while bridging to the Bukkit main thread for any game state modifications. The core challenge is thread safety: web requests arrive on Jetty threads but Bukkit API calls must execute on the main server thread.

The existing architecture (CollectionManager, ConfigManager, YAML files) provides natural integration points. The web panel will read collection definitions from the same YAML files the plugin uses, and trigger the existing `reload()` method after modifications.

---

## Integration Points

### Existing Components to Interface With

| Component | Location | Web Panel Interaction |
|-----------|----------|----------------------|
| `Collections.java` | Main plugin class | Lifecycle (start/stop web server), reload trigger |
| `CollectionManager` | `manager/` | Read collection data, trigger reload after edits |
| `ConfigManager` | `config/` | Web panel configuration (port, auth, etc.) |
| YAML Files | `plugins/Collections/collections/*.yml` | Direct read/write for CRUD operations |

### Key Integration Method: `Collections.reload()`

The plugin already has a comprehensive reload mechanism (lines 244-286 in Collections.java):

```java
public void reload() {
    // Stop current tasks
    particleTask.stop();
    actionBarPromptTask.stop();

    // Reload config and managers
    reloadConfig();
    configManager.reload();
    collectionManager.loadCollections();  // <-- Reloads YAML files
    zoneManager.loadZones();

    // Rebuild indexes and restart tasks
    dropSourceManager.buildIndexes();
    spawnManager.resetRespawnTimers();
    particleTask.start();
    // ...
}
```

**This is the synchronization point.** After web panel writes YAML files, it calls this method on the main thread to reload everything.

### Data Flow for Collection CRUD

```
Web Browser                 Javalin (Jetty threads)              Bukkit Main Thread
     |                              |                                    |
     |-- POST /api/collections -->  |                                    |
     |                              |-- Validate JSON                    |
     |                              |-- Write YAML file (async OK)       |
     |                              |-- scheduler.callSyncMethod() ----> |
     |                              |                                    |-- plugin.reload()
     |                              | <-- Future.get() waits ----------- |
     | <-- 200 OK ----------------- |                                    |
```

---

## New Components

### Package Structure

```
src/main/java/com/blockworlds/collections/
├── web/                          # NEW PACKAGE
│   ├── WebPanelManager.java      # Lifecycle management, Javalin instance
│   ├── WebPanelConfig.java       # Configuration (port, auth, CORS, etc.)
│   ├── api/                      # REST API handlers
│   │   ├── CollectionController.java   # CRUD for collections
│   │   ├── ItemController.java         # Item management within collections
│   │   ├── MaterialController.java     # List all Minecraft materials
│   │   └── StatusController.java       # Health check, server info
│   ├── auth/                     # Authentication
│   │   └── TokenAuthHandler.java       # Bearer token validation
│   ├── dto/                      # Data transfer objects
│   │   ├── CollectionDTO.java          # Collection JSON representation
│   │   ├── CollectionItemDTO.java      # Item JSON representation
│   │   └── MaterialDTO.java            # Material info for frontend
│   └── util/
│       └── YamlCollectionWriter.java   # Write collections back to YAML

src/main/resources/
├── web/                          # NEW: Static files
│   ├── index.html
│   ├── app.js
│   └── style.css
```

### Key Classes

#### WebPanelManager.java

Handles Javalin lifecycle with classloader fix:

```java
public class WebPanelManager {
    private final Collections plugin;
    private Javalin app;

    public void start() {
        // CRITICAL: Classloader fix for Bukkit/Paper
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(plugin.getClass().getClassLoader());

        try {
            app = Javalin.create(config -> {
                config.staticFiles.add("/web", Location.CLASSPATH);
                config.jsonMapper(new GsonJsonMapper()); // Use Gson (bundled with Paper)
                config.router.contextPath = "/collections";
            }).start(getPort());

            registerRoutes();
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }
}
```

#### MainThreadBridge.java

Utility for executing Bukkit API calls from web threads:

```java
public class MainThreadBridge {
    private final Plugin plugin;

    /**
     * Execute a Runnable on the main thread and wait for completion.
     * Safe to call from Javalin request handlers.
     */
    public <T> T runSync(Supplier<T> task) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            return task.get();
        }

        Future<T> future = Bukkit.getScheduler()
            .callSyncMethod(plugin, task::get);

        return future.get(5, TimeUnit.SECONDS); // Timeout to prevent deadlock
    }

    /**
     * Fire-and-forget execution on main thread.
     */
    public void runSyncLater(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
```

---

## Data Flow Diagrams

### Read Operations (Safe from any thread)

```
GET /api/collections
        |
        v
CollectionController.getAll()
        |
        v
collectionManager.getAllCollections()  // ConcurrentHashMap - thread-safe
        |
        v
Convert to DTO list
        |
        v
Return JSON
```

**Thread Safety Note:** `CollectionManager.collections` is a `ConcurrentHashMap` (line 38), so reads are already thread-safe without main thread bridging.

### Write Operations (Require main thread sync)

```
POST /api/collections
        |
        v
CollectionController.create()
        |
        v
Validate DTO
        |
        v
YamlCollectionWriter.write(dto, file)  // File I/O - async OK
        |
        v
mainThreadBridge.runSync(() -> {
    plugin.getCollectionManager().reload();
    return null;
})
        |
        v
Return 201 Created
```

### Material List (One-time generation, cacheable)

```
GET /api/materials
        |
        v
MaterialController.getAll()
        |
        v
Cache check (static final list)
        |
        v
If not cached:
    Arrays.stream(Material.values())
          .filter(Material::isItem)      // Only items, not blocks-only
          .map(m -> new MaterialDTO(m.name(), m.isBlock(), ...))
          .toList()
        |
        v
Return JSON (1500+ materials)
```

---

## Thread Model

### Thread Types

| Thread | Owner | Safe Operations |
|--------|-------|-----------------|
| Main Thread | Bukkit | All Bukkit API, entity manipulation, world access |
| Jetty Threads | Javalin | HTTP handling, file I/O, pure computation |
| Async Scheduler | Bukkit | Database operations (existing pattern) |

### Thread Safety Rules

1. **NEVER call Bukkit API from Jetty threads** (except `Bukkit.getScheduler()`)
2. **Reading `ConcurrentHashMap` collections is safe** from any thread
3. **Writing YAML files is safe** from any thread (file system operation)
4. **Triggering reload MUST use main thread** bridge

### Synchronization Pattern

```java
// In CollectionController.java
public void handleUpdate(Context ctx) {
    CollectionDTO dto = ctx.bodyAsClass(CollectionDTO.class);

    // 1. Validate (any thread)
    ValidationResult result = validator.validate(dto);
    if (!result.isValid()) {
        ctx.status(400).json(result.getErrors());
        return;
    }

    // 2. Write file (any thread - just file I/O)
    Path yamlPath = collectionsFolder.resolve(dto.id() + ".yml");
    yamlWriter.write(dto, yamlPath);

    // 3. Reload on main thread (blocking wait)
    try {
        mainThreadBridge.runSync(() -> {
            plugin.getCollectionManager().reload();
            return null;
        });
    } catch (TimeoutException e) {
        ctx.status(503).result("Server busy, please retry");
        return;
    }

    // 4. Return success
    ctx.status(200).json(dto);
}
```

---

## Architecture Patterns

### Pattern 1: DTO Separation

Keep Bukkit types out of web layer:

```java
// BAD - Leaks Bukkit types to JSON layer
public record CollectionDTO(String id, Material icon) {}

// GOOD - String-based, web-safe
public record CollectionDTO(
    String id,
    String name,
    String iconMaterial,  // "DIAMOND" not Material.DIAMOND
    String tier,          // "COMMON" not CollectibleTier.COMMON
    List<CollectionItemDTO> items
) {}
```

### Pattern 2: Validation Before Write

Validate material names, biome names, etc. before writing YAML:

```java
public ValidationResult validate(CollectionDTO dto) {
    List<String> errors = new ArrayList<>();

    // Validate material exists
    try {
        Material.valueOf(dto.iconMaterial().toUpperCase());
    } catch (IllegalArgumentException e) {
        errors.add("Invalid material: " + dto.iconMaterial());
    }

    // Validate tier
    try {
        CollectibleTier.valueOf(dto.tier().toUpperCase());
    } catch (IllegalArgumentException e) {
        errors.add("Invalid tier: " + dto.tier());
    }

    return new ValidationResult(errors.isEmpty(), errors);
}
```

### Pattern 3: YAML Preservation (Comment Strategy)

Since SnakeYAML doesn't preserve comments, use one of:

**Option A: Accept comment loss** (simplest)
- Parse existing YAML, merge changes, write back
- Comments are lost but functional

**Option B: Template-based regeneration**
- Store original YAML as template
- Generate comments programmatically when writing

**Option C: Use yaml-updater library** (most complex)
- Preserves comments during merge
- Additional dependency

**Recommendation:** Start with Option A. Most users won't hand-edit YAML if web panel exists.

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Calling Bukkit API from Jetty Thread

```java
// BAD - Will throw exception or cause undefined behavior
app.get("/api/reload", ctx -> {
    plugin.reload();  // Called on Jetty thread!
    ctx.result("OK");
});

// GOOD - Bridge to main thread
app.get("/api/reload", ctx -> {
    mainThreadBridge.runSync(() -> {
        plugin.reload();
        return null;
    });
    ctx.result("OK");
});
```

### Anti-Pattern 2: Blocking Main Thread on Web I/O

```java
// BAD - Blocks main thread on network call
Bukkit.getScheduler().runTask(plugin, () -> {
    HttpClient.newHttpClient().send(...);  // Network I/O on main thread!
});

// GOOD - Web operations stay on Jetty threads
// Main thread only used for Bukkit API calls
```

### Anti-Pattern 3: Storing Bukkit Objects in DTO

```java
// BAD - Material enum doesn't serialize cleanly to JSON
public record ItemDTO(String id, Material material) {}

// GOOD - Use string representation
public record ItemDTO(String id, String material) {}
```

---

## Build Order

Based on dependencies between components, suggested implementation phases:

### Phase 1: Web Infrastructure
1. Add Javalin dependency to `build.gradle.kts`
2. Create `WebPanelManager` with classloader fix
3. Create `WebPanelConfig` for port/auth settings
4. Test: Web server starts and stops with plugin

### Phase 2: Read-Only API
1. Create `CollectionDTO` and `CollectionItemDTO`
2. Create `CollectionController.getAll()` and `.getOne()`
3. Create `MaterialController.getAll()` (static material list)
4. Test: Can fetch collections via REST API

### Phase 3: Write API + Main Thread Bridge
1. Create `MainThreadBridge` utility
2. Create `YamlCollectionWriter`
3. Implement `CollectionController.create()`, `.update()`, `.delete()`
4. Wire up `plugin.reload()` after writes
5. Test: CRUD operations work, plugin reloads correctly

### Phase 4: Static File Serving + Frontend
1. Configure Javalin static file serving from `/web`
2. Create basic HTML/JS/CSS frontend
3. Implement collection list view
4. Implement collection editor with material picker

### Phase 5: Authentication + Security
1. Implement bearer token authentication
2. Add CORS configuration for development
3. Add rate limiting (optional)
4. Add HTTPS support (optional, usually handled by reverse proxy)

---

## Dependency Impact

### New Dependencies

```kotlin
// build.gradle.kts
dependencies {
    // Javalin web framework
    implementation("io.javalin:javalin:6.7.0")

    // SLF4J for Javalin logging (required)
    implementation("org.slf4j:slf4j-simple:2.0.17")

    // Note: Jackson NOT needed - use Gson (bundled with Paper)
}

tasks.shadowJar {
    // Add relocations to avoid conflicts
    relocate("io.javalin", "com.blockworlds.collections.lib.javalin")
    relocate("org.eclipse.jetty", "com.blockworlds.collections.lib.jetty")
    relocate("jakarta.servlet", "com.blockworlds.collections.lib.servlet")
}
```

### JAR Size Impact

- Javalin + Jetty: ~3-4 MB
- Current plugin: ~2 MB (estimated)
- New total: ~5-6 MB

This is acceptable for a feature-rich plugin.

---

## Configuration Schema

Add to `config.yml`:

```yaml
# Web Panel Settings
web-panel:
  enabled: true
  port: 8080

  # Authentication
  auth:
    enabled: true
    # Token for API access (generate with /collections webtoken)
    token: ""

  # CORS settings (for development)
  cors:
    enabled: false
    allowed-origins:
      - "http://localhost:3000"
```

---

## Scalability Considerations

| Concern | Current Scale | At 100 Users | Notes |
|---------|---------------|--------------|-------|
| Concurrent requests | N/A | Jetty handles | Jetty thread pool (default 200) |
| Collection count | ~70 files | ~70 files | File count unlikely to grow much |
| Material list | ~1500 items | ~1500 items | Static, cache forever |
| Main thread blocking | N/A | Minimal | Only during reload (~100ms) |

---

## Sources

**HIGH Confidence (Official Documentation):**
- [Javalin and Minecraft Servers Tutorial](https://javalin.io/tutorials/javalin-and-minecraft-servers) - Official Javalin tutorial for Bukkit/Paper integration
- [Javalin Documentation](https://javalin.io/documentation) - Routing, handlers, configuration
- [Paper Material API](https://jd.papermc.io/paper/1.21.4/org/bukkit/Material.html) - Material enum documentation

**MEDIUM Confidence (Verified with Multiple Sources):**
- [Javalin Spigot/Bungeecord Guide](https://gist.github.com/RezzedUp/d7957af10bfbfc6837ae1a4b55975f40) - Classloader fix pattern
- [Bukkit Scheduler Thread Safety](https://riptutorial.com/bukkit/example/32701/running-thread-safe-code-from-an-asynchronous-task) - Main thread bridging patterns

**LOW Confidence (General Background):**
- [Undertow Performance Comparison](https://itnext.io/what-is-the-best-embedded-web-server-for-spring-boot-version-3-4-4-tomcat-vs-jetty-vs-undertow-c9186a510301) - Why Javalin (Jetty-based) is suitable
- [SnakeYAML Comment Preservation](https://github.com/xvik/yaml-updater) - Options for YAML comment handling
