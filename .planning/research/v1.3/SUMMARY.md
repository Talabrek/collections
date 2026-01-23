# Research Summary: v1.3 Web Control Panel

**Synthesized:** 2026-01-23
**Research Files:** STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS-WEBSERVER.md
**Overall Confidence:** HIGH
**Target:** Embedded web admin panel for Collections plugin

---

## Executive Summary

Adding a web control panel to the Collections plugin is well-supported by existing tools and patterns. **Javalin 6.7.0** is the clear choice for the embedded web server - it has an official Minecraft plugin tutorial with tested classloader workarounds. **htmx + Alpine.js + SortableJS** provides a no-build-step frontend solution ideal for visual builders.

**The three highest-priority concerns are:**

1. **Classloader conflicts** - Bukkit's custom classloader breaks Javalin/Jetty startup. Must swap `Thread.currentThread().setContextClassLoader()` before instantiation. This is day-one blocking.

2. **Thread safety** - ALL Bukkit API calls from web request handlers must schedule back to main thread via `runTask()`. No exceptions. Web handlers run on Jetty threads.

3. **Dependency relocation** - Jetty/SLF4J conflicts with other web-enabled plugins (Dynmap, BlueMap). Must relocate in shadowJar.

**Recommended approach:** Build in phases - web infrastructure first (with classloader fix and auth), then read-only API, then write API with main thread bridging, then visual builder UI.

---

## Key Findings

### From STACK.md

| Component | Recommendation | Version | Confidence |
|-----------|----------------|---------|------------|
| Web Server | Javalin | 6.7.0 | HIGH |
| Frontend | htmx + Alpine.js | 2.0.8 / 3.15.3 | HIGH |
| Drag-Drop | SortableJS | 1.15.x | HIGH |
| Authentication | HTTP Basic Auth | N/A | HIGH |
| CSS | Tailwind CDN | N/A | MEDIUM |

**Critical build.gradle.kts additions:**
```kotlin
implementation("io.javalin:javalin:6.7.0")
implementation("org.slf4j:slf4j-simple:2.0.17")

// Relocations required:
relocate("io.javalin", "com.blockworlds.collections.lib.javalin")
relocate("org.eclipse.jetty", "com.blockworlds.collections.lib.jetty")
relocate("org.slf4j", "com.blockworlds.collections.lib.slf4j")
relocate("jakarta.servlet", "com.blockworlds.collections.lib.jakarta.servlet")
relocate("kotlin", "com.blockworlds.collections.lib.kotlin")
```

**JAR size impact:** ~4-5 MB addition (acceptable for feature-rich plugin).

### From FEATURES.md

**Table Stakes (must have):**
- Token/password authentication
- CRUD operations for collections
- YAML validation with error messages
- Responsive design with loading states
- Searchable item browser (1300+ Minecraft items)
- Drag-drop with visual affordances

**Differentiators (nice to have):**
- MiniMessage live preview
- Collection templates (forest, ocean, nether)
- Weight sum validation (100% check)
- Duplicate ID detection
- Hot reload trigger

**Anti-Features (skip for v1.3):**
- Real-time collaborative editing
- Multi-server dashboard
- Player data management in web UI
- World map visualization
- Full plugin settings editor

### From ARCHITECTURE.md

**Integration points:**
- `Collections.java` - lifecycle (start/stop web server)
- `CollectionManager` - read collection data, trigger reload
- YAML files in `plugins/Collections/collections/` - direct read/write

**Key insight:** The existing `reload()` method is the synchronization point. After web panel writes YAML files, it calls this method on the main thread to reload everything.

**New package structure:**
```
src/main/java/.../web/
├── WebPanelManager.java      # Javalin lifecycle
├── api/                      # REST controllers
├── auth/                     # Token validation
├── dto/                      # JSON transfer objects
└── util/                     # YAML writer
```

**Thread model:**
- Web requests arrive on Jetty threads
- Reading `ConcurrentHashMap` collections is thread-safe
- Writing YAML files is thread-safe (file system operation)
- Triggering reload MUST use main thread bridge

### From PITFALLS.md

**Critical pitfalls to address in Phase 1:**
1. Classloader fix (day-one blocking)
2. Thread safety pattern establishment
3. Graceful shutdown in `onDisable()`
4. Dependency relocation in shadowJar
5. CORS configuration
6. Port binding with fallback
7. Reload vs shutdown handling
8. CVE-safe Jetty versions
9. SLF4J logging conflicts

**Security pitfalls for Phase 2:**
1. Rate limiting on authentication (5 attempts → 5 min lockout)
2. Password hashing (BCrypt) and secure generation
3. Session invalidation on reload

**Verified classloader fix pattern:**
```java
ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
Thread.currentThread().setContextClassLoader(this.getClassLoader());
Javalin app = Javalin.create().start(port);
Thread.currentThread().setContextClassLoader(classLoader);
```

---

## Risk Areas

### High Risk: Thread Safety Violations

Calling Bukkit API from Jetty threads causes undefined behavior:
```java
// WRONG
app.get("/players", ctx -> {
    ctx.result(Bukkit.getOnlinePlayers().toString()); // UNSAFE
});

// CORRECT
app.get("/players", ctx -> {
    CompletableFuture<String> future = new CompletableFuture<>();
    Bukkit.getScheduler().runTask(plugin, () -> {
        future.complete(Bukkit.getOnlinePlayers().toString());
    });
    ctx.result(future.get(5, TimeUnit.SECONDS));
});
```

### Medium Risk: YAML Concurrent Modification

Web server writes to YAML while game thread reads. Prevention: Use `ReentrantReadWriteLock` for all YAML access.

### Low Risk: Port Conflicts

Default port 8080 may conflict with other plugins. Mitigation: Configurable port with fallback range.

---

## Recommended Phase Structure

Based on architecture research and pitfall prioritization:

| Phase | Focus | Requirements Covered |
|-------|-------|---------------------|
| 18 | Web Infrastructure | Javalin setup, classloader fix, auth, static files |
| 19 | Read-Only API | Collection listing, item browser, material list |
| 20 | Write API + Reload | CRUD operations, main thread bridging, reload |
| 21 | Visual Builder UI | Drag-drop editor, item picker, form builder |
| 22 | Documentation | GitHub README update |

Each phase builds on the previous. Phase 18 establishes the foundation that all other phases depend on.

---

## Sources

### Official Documentation
- [Javalin and Minecraft Servers](https://javalin.io/tutorials/javalin-and-minecraft-servers) - Classloader fix
- [Javalin 6.7.0 Documentation](https://javalin.io/documentation)
- [htmx 2.0.8 Documentation](https://htmx.org/docs/)
- [Alpine.js 3.x Documentation](https://alpinejs.dev/)
- [PaperMC Scheduler](https://docs.papermc.io/paper/dev/scheduler/)

### Security
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [Eclipse Jetty CVE List](https://www.cvedetails.com/vulnerability-list/vendor_id-10410/product_id-34824/Eclipse-Jetty.html)

### Reference Implementations
- [Pterodactyl Panel](https://pterodactyl.io/) - UI patterns
- [MCSManager](https://www.mcsmanager.com/) - Dashboard layout
- [Minecraft Item IDs](https://minecraftitemids.com/) - Item browser reference

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Embedded Web Server | HIGH | Official Javalin Minecraft tutorial verified |
| Frontend Stack | HIGH | htmx/Alpine.js versions verified via official sources |
| Thread Safety | HIGH | Standard Bukkit scheduler pattern, well-documented |
| Security Patterns | HIGH | OWASP guidelines + Jetty CVE database |
| Shading/Relocation | HIGH | Shadow plugin docs + Javalin tutorial align |

---

## Next Steps

1. Define requirements based on table stakes + selected differentiators
2. Create roadmap with phases 18-22
3. Phase 18 is critical path - classloader fix must work before anything else
