# Stack Research: Web Control Panel

**Project:** Collections Plugin - Web Admin Panel
**Researched:** 2026-01-23
**Overall Confidence:** HIGH

## Executive Summary

Adding an embedded web control panel to the Collections plugin requires careful library selection due to Bukkit's custom classloader and the need for fat JAR shading. After evaluating options, **Javalin 6.7.0** is the clear choice for the embedded web server due to its explicit Minecraft plugin support, lightweight footprint, and comprehensive documentation for classloader workarounds.

For the frontend, **htmx + Alpine.js** provides a no-build-step solution ideal for an embedded admin panel, avoiding the complexity of Node.js toolchains while enabling rich interactivity.

---

## Recommended Stack

### Embedded Web Server: Javalin 6.7.0

**Version:** 6.7.0 (current stable, verified 2026-01-23)
**Confidence:** HIGH (official documentation + Minecraft-specific tutorial)

**Why Javalin:**
1. **Official Minecraft plugin tutorial** - Javalin maintains a dedicated guide at [javalin.io/tutorials/javalin-and-minecraft-servers](https://javalin.io/tutorials/javalin-and-minecraft-servers) with tested classloader fixes
2. **Lightweight** - Runs on embedded Jetty, single dependency pulls in everything needed
3. **Java 21 support** - Includes `config.useVirtualThreads` for Project Loom
4. **Active development** - Regular releases, semantic versioning
5. **Static file serving** - Built-in support via `config.staticFiles.add("/public", Location.CLASSPATH)`

**Dependency:**
```kotlin
implementation("io.javalin:javalin:6.7.0")
implementation("org.slf4j:slf4j-simple:2.0.17")
```

**Critical: Classloader Fix Required**
Minecraft's custom classloader breaks Javalin's dependency loading. The fix:
```java
// In plugin enable
ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
Thread.currentThread().setContextClassLoader(this.getClassLoader());
Javalin app = Javalin.create(config -> {
    config.staticFiles.add("/web", Location.CLASSPATH);
}).start(configuredPort);
Thread.currentThread().setContextClassLoader(classLoader);
```

**Shading/Relocation Required:**
```kotlin
// build.gradle.kts additions
relocate("io.javalin", "com.blockworlds.collections.lib.javalin")
relocate("org.eclipse.jetty", "com.blockworlds.collections.lib.jetty")
relocate("org.slf4j", "com.blockworlds.collections.lib.slf4j")
relocate("jakarta.servlet", "com.blockworlds.collections.lib.jakarta.servlet")
```

### Alternatives Considered

| Library | Version | Why Not |
|---------|---------|---------|
| SparkJava | 2.9.4 | Appears abandoned; latest release is old; no Java 21 explicit support |
| Undertow | 2.3.x | More complex setup, no Minecraft-specific docs, overkill for admin panel |
| NanoHTTPD | 2.3.1 | Abandoned with unpatched security vulnerabilities |
| Ktor | 2.x | Kotlin-first, adds Kotlin runtime dependency bloat for Java project |

---

### Frontend Framework: htmx 2.0.8 + Alpine.js 3.15.3

**Confidence:** HIGH (verified via official sources)

**Why htmx + Alpine.js:**
1. **No build step** - CDN scripts or embedded in JAR, no Node.js/npm required
2. **Tiny footprint** - ~14KB (htmx) + ~15KB (Alpine.js) = ~29KB total
3. **Server-driven** - htmx fetches HTML fragments, perfect for Javalin templating
4. **Rich interactivity** - Alpine.js handles client-side state (modals, dropdowns, drag state)
5. **No SPA complexity** - Traditional request/response model, easier to debug

**CDN Links (for development/fallback):**
```html
<script src="https://cdn.jsdelivr.net/npm/htmx.org@2.0.8/dist/htmx.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/alpinejs@3.15.3/dist/cdn.min.js" defer></script>
```

**Recommended: Bundle in JAR**
For offline/airgapped servers, embed minified scripts in `src/main/resources/web/js/`:
- `htmx.min.js` (2.0.8)
- `alpine.min.js` (3.15.3)

### Alternatives Considered

| Framework | Why Not |
|-----------|---------|
| React/Vue/Angular | Requires Node.js build step, overkill for admin panel |
| jQuery | Older paradigm, htmx is more expressive for AJAX |
| Vanilla JS only | More boilerplate for AJAX/state management |
| Svelte | Requires build step |

---

### Drag-Drop: SortableJS 1.15.x

**Confidence:** HIGH (verified via GitHub/npm)

**Why SortableJS:**
1. **No dependencies** - Pure JavaScript, no jQuery required
2. **Touch support** - Works on mobile admin access
3. **Alpine.js integration** - Alpine has a `@alpinejs/sort` plugin built on SortableJS
4. **Reorder events** - Fires events when items reordered, easy to POST to Javalin

**CDN:**
```html
<script src="https://cdn.jsdelivr.net/npm/sortablejs@latest/Sortable.min.js"></script>
```

**Bundle in JAR recommended** for offline servers.

---

### Authentication: Basic HTTP Auth via Javalin Before Handler

**Confidence:** HIGH (documented in Javalin 6.x)

**Why Basic Auth:**
1. **Simple** - Password in config.yml, no session management needed
2. **Browser-native** - Shows login prompt automatically
3. **Sufficient** - Admin panel is localhost/LAN access typically
4. **No dependencies** - Built into HTTP standard

**Implementation pattern:**
```java
app.beforeMatched("/admin/*", ctx -> {
    String authHeader = ctx.header("Authorization");
    if (!isValidAuth(authHeader, configPassword)) {
        ctx.header("WWW-Authenticate", "Basic realm=\"Collections Admin\"");
        ctx.status(401);
        throw new UnauthorizedResponse("Unauthorized");
    }
});
```

**Configuration:**
```yaml
# config.yml
web-panel:
  enabled: false
  port: 8080
  password: "change-me-in-production"
  bind-address: "127.0.0.1"  # localhost only by default
```

### Alternatives Considered

| Approach | Why Not |
|----------|---------|
| JWT tokens | Overkill for single-password admin panel |
| Session cookies | Adds complexity, persistence concerns |
| OAuth | Way overkill, requires external providers |
| javalin-pac4j | Heavy dependency for simple use case |

---

### CSS Framework: Tailwind CSS (CDN Play)

**Confidence:** MEDIUM (preference, not critical)

**Why Tailwind CDN:**
1. **No build step** - CDN script works instantly
2. **Utility-first** - Fast prototyping for admin UI
3. **Consistent styling** - Professional look without custom CSS

**CDN (dev/play mode):**
```html
<script src="https://cdn.tailwindcss.com"></script>
```

**Note:** For production, could embed a pre-built CSS file, but CDN is acceptable for admin panels with internet access.

### Alternative: Plain CSS
If avoiding CDN, write minimal custom CSS. Admin panels don't need to be beautiful.

---

## Build Integration

### Gradle Changes Required

```kotlin
// build.gradle.kts additions

dependencies {
    // Existing dependencies...

    // Web Panel
    implementation("io.javalin:javalin:6.7.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

tasks.shadowJar {
    // Existing relocations...

    // Web Panel relocations (prevent conflicts with other plugins)
    relocate("io.javalin", "com.blockworlds.collections.lib.javalin")
    relocate("org.eclipse.jetty", "com.blockworlds.collections.lib.jetty")
    relocate("org.slf4j", "com.blockworlds.collections.lib.slf4j")
    relocate("jakarta.servlet", "com.blockworlds.collections.lib.jakarta.servlet")
    relocate("kotlin", "com.blockworlds.collections.lib.kotlin") // Javalin pulls in Kotlin stdlib
}
```

### JAR Size Impact

| Component | Approximate Size |
|-----------|------------------|
| Javalin + Jetty | ~3-4 MB |
| SLF4J Simple | ~15 KB |
| Frontend JS (htmx + Alpine + Sortable) | ~60 KB |
| HTML templates | ~50 KB |
| **Total Addition** | **~4-5 MB** |

Current plugin JAR is likely ~5-10 MB with SQLite/MySQL drivers. Expect ~10-15 MB total.

### Resource Structure

```
src/main/resources/
├── web/
│   ├── index.html
│   ├── collections.html
│   ├── js/
│   │   ├── htmx.min.js
│   │   ├── alpine.min.js
│   │   └── sortable.min.js
│   ├── css/
│   │   └── admin.css
│   └── fragments/
│       ├── collection-list.html
│       ├── collection-edit.html
│       └── item-browser.html
```

---

## Integration Notes

### Thread Safety

Javalin runs on its own thread pool (Jetty threads). All Bukkit API access from web handlers MUST be scheduled back to the main thread:

```java
app.post("/api/collection/{id}/reload", ctx -> {
    String collectionId = ctx.pathParam("id");

    // Schedule on main thread
    Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
        collectionManager.reloadCollection(collectionId);
    });

    ctx.result("Reload scheduled");
});
```

### Lifecycle Management

```java
// In onEnable()
if (config.getBoolean("web-panel.enabled")) {
    webServer = new WebPanelServer(this);
    webServer.start(config.getInt("web-panel.port"));
}

// In onDisable()
if (webServer != null) {
    webServer.stop();
}
```

### Port Conflicts

Web panel port (default 8080) may conflict with:
- Other plugins with web panels
- Dynmap (default 8123)
- Other services

Configuration should allow custom port and bind address.

---

## Not Recommended

### Do NOT Use

| Library/Approach | Reason |
|------------------|--------|
| Spring Boot embedded | Massive overkill, huge JAR size increase, classloader nightmares |
| NanoHTTPD | Abandoned, security vulnerabilities, no SSL |
| Raw ServerSocket | Reinventing the wheel, no HTTP parsing |
| Separate process | Complicates deployment, IPC overhead |
| WebSocket-only | Adds complexity, htmx handles SSE if needed |
| React/Vue SPA | Build step required, overkill for admin panel |
| Paper's `libraries` loader | Only loads from Maven Central, doesn't solve shading for public distribution |

### Do NOT Shade

| Library | Reason |
|---------|--------|
| Paper API | Provided at runtime |
| Adventure API | Provided by Paper |
| Gson | Provided by Paper (use for JSON in web handlers) |

---

## Sources

### HIGH Confidence (Official Documentation)
- [Javalin Documentation](https://javalin.io/documentation) - Current 6.7.0 docs
- [Javalin Minecraft Tutorial](https://javalin.io/tutorials/javalin-and-minecraft-servers) - Classloader fix
- [htmx Documentation](https://htmx.org/docs/) - Current 2.0.8
- [Alpine.js Documentation](https://alpinejs.dev/) - Current 3.15.3
- [SortableJS GitHub](https://github.com/SortableJS/Sortable) - Official repo
- [Shadow Plugin Documentation](https://gradleup.com/shadow/) - Relocation guide

### MEDIUM Confidence (Community/Search)
- [ITNEXT: Spring Boot Embedded Server Comparison](https://itnext.io/what-is-the-best-embedded-web-server-for-spring-boot-version-3-4-4-tomcat-vs-jetty-vs-undertow-c9186a510301) - Server benchmarks
- [InfoWorld: HTMX and Alpine.js](https://www.infoworld.com/article/3856520/htmx-and-alpine-js-how-to-combine-two-great-lean-front-ends.html) - Frontend integration patterns
- [NashTech: HTMX + AlpineJS](https://blog.nashtechglobal.com/introducing-htmx-alpinejs-a-lightweight-approach-to-building-web-apps-faster/) - No-build-step approach

---

## Summary for Roadmap

**Required additions to existing stack:**
1. Javalin 6.7.0 (embedded web server)
2. SLF4J Simple 2.0.17 (logging for Javalin)
3. htmx 2.0.8, Alpine.js 3.15.3, SortableJS (frontend, bundled as static files)

**Critical implementation notes:**
1. Classloader context switch required when creating Javalin instance
2. All Jetty/Javalin packages must be relocated in shadowJar
3. All Bukkit API access from web handlers must be scheduled to main thread
4. Basic HTTP auth sufficient for admin panel security

**Phase structure implications:**
1. Phase 1: Web server infrastructure (Javalin setup, auth, static files)
2. Phase 2: Read-only API (list/view collections, item browser)
3. Phase 3: Write API (CRUD operations, reload mechanism)
4. Phase 4: Visual builder UI (drag-drop with SortableJS)
