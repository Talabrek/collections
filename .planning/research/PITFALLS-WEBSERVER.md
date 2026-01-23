# Pitfalls Research: Embedded Web Server in Minecraft Plugin

**Domain:** Adding embedded web server to existing Collections plugin
**Researched:** 2026-01-23
**Confidence:** HIGH (verified with official docs and community sources)

## Summary

Adding an embedded web server to a Minecraft plugin introduces unique challenges at the intersection of web development and Bukkit's threading model. The critical pitfalls are: (1) classloader conflicts that prevent web server startup, (2) thread safety violations when accessing Bukkit API from web request handlers, (3) resource leaks during plugin reload/shutdown, and (4) authentication vulnerabilities including brute force and session hijacking. These must be addressed in the first phase - getting the foundation wrong creates cascading problems.

---

## Critical Pitfalls

These mistakes cause rewrites, security breaches, or major instability.

### 1. Classloader Conflicts

**Risk:** Embedded web servers (Javalin, Jetty, Undertow) fail to start with `NoClassDefFoundError` or `WebSocketServerFactory` errors because Bukkit uses a custom classloader per plugin.

**Warning Signs:**
- `java.lang.NoClassDefFoundError` on server start
- `ClassNotFoundException` for Jetty/WebSocket classes
- Web server works in tests but fails in actual plugin environment

**Prevention:**
```java
// REQUIRED: Swap classloader before instantiating web server
ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
Thread.currentThread().setContextClassLoader(this.getClassLoader());

Javalin app = Javalin.create().start(port);

Thread.currentThread().setContextClassLoader(classLoader);
```

**Phase:** Address in Phase 1 (Web Server Foundation) - this is day-one blocking.

**Sources:**
- [Javalin and Minecraft Servers Tutorial](https://javalin.io/tutorials/javalin-and-minecraft-servers)
- [GitHub Gist: Using Javalin in Spigot](https://gist.github.com/RezzedUp/d7957af10bfbfc6837ae1a4b55975f40)

---

### 2. Bukkit API Access from Web Server Threads

**Risk:** Calling ANY Bukkit API from web server request handlers causes undefined behavior, data corruption, or server crashes. The Bukkit API is NOT thread-safe.

**Warning Signs:**
- Random `ConcurrentModificationException` errors
- Server tick lag spikes when web requests arrive
- Inconsistent data reads (sometimes works, sometimes doesn't)
- "Asynchronous X!" warnings in console

**Prevention:**
```java
// WRONG: Direct Bukkit API call from web handler
app.get("/players", ctx -> {
    ctx.result(Bukkit.getOnlinePlayers().toString()); // UNSAFE
});

// CORRECT: Schedule back to main thread
app.get("/players", ctx -> {
    CompletableFuture<String> future = new CompletableFuture<>();

    Bukkit.getScheduler().runTask(plugin, () -> {
        String players = Bukkit.getOnlinePlayers().toString();
        future.complete(players);
    });

    ctx.result(future.get(5, TimeUnit.SECONDS));
});
```

**Phase:** Address in Phase 1 - establish pattern from the start.

**Sources:**
- [SpigotMC Thread Safety Discussion](https://www.spigotmc.org/threads/issue-with-thread-safety-while-using-bukkit-api.178393/)
- [Bukkit Forums: Thread Safety](https://bukkit.org/threads/thread-safety.439207/)

---

### 3. Missing Graceful Shutdown

**Risk:** Plugin disables without stopping web server properly. Causes port binding errors on reload, resource leaks, and potential server hang on shutdown.

**Warning Signs:**
- `java.net.BindException: Address already in use` after `/reload`
- Server hangs on `/stop` command
- Memory usage increases with each reload

**Prevention:**
```java
private Javalin app;

@Override
public void onDisable() {
    if (app != null) {
        app.stop(); // REQUIRED: Stop web server
        app = null;
    }
}
```

For Jetty directly:
```java
server.setStopAtShutdown(true);
server.setStopTimeout(7000); // 7 second graceful shutdown
```

**Phase:** Address in Phase 1 - lifecycle management is foundational.

**Sources:**
- [Jetty Graceful Shutdown Issue](https://github.com/jetty/jetty.project/issues/2076)
- [SpigotMC: Detecting Shutdown vs Reload](https://www.spigotmc.org/threads/check-if-server-is-shutting-down-or-reloading-in-ondisable.59059/)

---

### 4. Dependency Conflicts with Other Plugins

**Risk:** If another plugin uses a different version of Jetty/SLF4J/etc., classloader conflicts cause failures. Common with Dynmap, BlueMap, or other web-enabled plugins.

**Warning Signs:**
- Works alone, fails when other plugins installed
- `LinkageError`, `NoSuchMethodError` on classes you didn't write
- Conflict errors mentioning `org.eclipse.jetty`

**Prevention:**
```kotlin
// build.gradle.kts
tasks.shadowJar {
    relocate("org.eclipse.jetty", "com.yourplugin.shadow.jetty")
    relocate("io.javalin", "com.yourplugin.shadow.javalin")
    relocate("org.slf4j", "com.yourplugin.shadow.slf4j")
}
```

**Phase:** Address in Phase 1 - configure build system correctly from start.

**Sources:**
- [Bukkit Wiki: Using External Libraries](https://bukkit.fandom.com/wiki/Using_External_Libraries_with_Plugins)
- [LuckPerms Classloader Issue](https://github.com/LuckPerms/LuckPerms/issues/648)

---

## Security Pitfalls

Authentication and access control issues that expose the server.

### 5. No Rate Limiting on Authentication

**Risk:** Brute force attacks can crack passwords quickly. A simple password with no rate limiting can be cracked in minutes.

**Warning Signs:**
- No failed login tracking
- Immediate response to all login attempts
- No account lockout mechanism

**Prevention:**
```java
// Track failed attempts per IP
private final Map<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();
private final Map<String, Long> lockoutUntil = new ConcurrentHashMap<>();

private boolean isRateLimited(String ip) {
    Long until = lockoutUntil.get(ip);
    if (until != null && System.currentTimeMillis() < until) {
        return true;
    }
    return false;
}

private void recordFailedAttempt(String ip) {
    int attempts = failedAttempts
        .computeIfAbsent(ip, k -> new AtomicInteger(0))
        .incrementAndGet();

    if (attempts >= 5) {
        lockoutUntil.put(ip, System.currentTimeMillis() + 300_000); // 5 min lockout
        failedAttempts.remove(ip);
    }
}
```

**Phase:** Address in Phase 2 (Authentication) - core security requirement.

**Sources:**
- [OWASP: Blocking Brute Force Attacks](https://owasp.org/www-community/controls/Blocking_Brute_Force_Attacks)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)

---

### 6. Directory Traversal in Static File Serving

**Risk:** Attackers use `../` sequences to access files outside the intended directory, potentially exposing server configs, plugin data, or system files.

**Warning Signs:**
- Static file paths built from user input without validation
- No path canonicalization
- File serving uses simple string concatenation

**Prevention:**
```java
// WRONG: Direct path concatenation
String filePath = webRoot + "/" + requestedPath; // Vulnerable to ../

// CORRECT: Canonicalize and validate
Path basePath = webRoot.toAbsolutePath().normalize();
Path requestedFile = basePath.resolve(requestedPath).normalize();

if (!requestedFile.startsWith(basePath)) {
    ctx.status(403).result("Access denied");
    return;
}
```

**Phase:** Address in Phase 3 (Static File Serving) - validate before serving.

**Sources:**
- [OWASP: Path Traversal](https://owasp.org/www-community/attacks/Path_Traversal)
- [PortSwigger: Path Traversal](https://portswigger.net/web-security/file-path-traversal)

---

### 7. Hardcoded or Weak Default Password

**Risk:** Users deploy with default password, or password is visible in config file on shared hosting.

**Warning Signs:**
- Default password in shipped config
- Password stored in plain text
- No password change enforcement on first run

**Prevention:**
```java
// Generate random password on first run
if (!config.contains("web-panel.password-hash")) {
    String generatedPassword = generateSecurePassword();
    config.set("web-panel.password-hash", BCrypt.hashpw(generatedPassword, BCrypt.gensalt()));
    saveConfig();

    getLogger().warning("===========================================");
    getLogger().warning("FIRST RUN: Your web panel password is: " + generatedPassword);
    getLogger().warning("This will only be shown once! Save it now!");
    getLogger().warning("===========================================");
}
```

**Phase:** Address in Phase 2 (Authentication) - secure defaults.

---

### 8. Missing CORS Configuration

**Risk:** Either too permissive (allows any origin) or too restrictive (breaks legitimate use). Misconfigured CORS can enable CSRF attacks or break the UI.

**Warning Signs:**
- `Access-Control-Allow-Origin: *` in production
- No CORS headers at all (browser blocks requests)
- CORS allows credentials with wildcard origin

**Prevention:**
```java
// Configure CORS explicitly
Javalin app = Javalin.create(config -> {
    config.bundledPlugins.enableCors(cors -> {
        cors.addRule(rule -> {
            // Only allow same-origin for embedded UI
            // API served from same origin as static files
            rule.allowHost("http://localhost:" + port);
        });
    });
});
```

Note: If serving UI from JAR and API on same port, same-origin is automatic. CORS only needed for cross-origin scenarios.

**Phase:** Address in Phase 1 (Web Server Foundation) - configure early.

**Sources:**
- [PortSwigger: CORS Security](https://portswigger.net/web-security/cors)
- [W3C CORS Spec](https://www.w3.org/wiki/CORS_Enabled)

---

## Performance Pitfalls

Resource usage, blocking, and scalability issues.

### 9. Blocking Main Thread for Web Operations

**Risk:** Performing file I/O, YAML parsing, or heavy operations on Bukkit main thread causes server lag and TPS drops.

**Warning Signs:**
- TPS drops when web panel is used
- Server freezes briefly during config saves
- Lag spikes correlate with web activity

**Prevention:**
```java
// WRONG: Save on main thread
app.post("/collection", ctx -> {
    Bukkit.getScheduler().runTask(plugin, () -> {
        saveCollection(data); // File I/O on main thread!
    });
});

// CORRECT: Async I/O, sync only for Bukkit API
app.post("/collection", ctx -> {
    // Parse request (already async - web thread)
    CollectionData data = parseRequest(ctx);

    // File I/O stays async
    CompletableFuture.runAsync(() -> {
        saveToFile(data);
    }).thenRun(() -> {
        // Only sync back for reload notification
        Bukkit.getScheduler().runTask(plugin, () -> {
            reloadCollection(data.name());
        });
    });
});
```

**Phase:** Address in Phase 4 (CRUD Operations) - establish I/O patterns.

---

### 10. YAML Concurrent Modification

**Risk:** Web server writes to YAML file while game thread reads it, causing corruption or `ConcurrentModificationException`.

**Warning Signs:**
- Random config corruption after web edits
- `ConcurrentModificationException` in stack traces
- Partial writes (truncated files)

**Prevention:**
```java
// Use a lock for all YAML access
private final ReentrantReadWriteLock configLock = new ReentrantReadWriteLock();

public void saveConfig(YamlConfiguration config, File file) {
    configLock.writeLock().lock();
    try {
        config.save(file);
    } finally {
        configLock.writeLock().unlock();
    }
}

public YamlConfiguration loadConfig(File file) {
    configLock.readLock().lock();
    try {
        return YamlConfiguration.loadConfiguration(file);
    } finally {
        configLock.readLock().unlock();
    }
}
```

**Phase:** Address in Phase 4 (CRUD Operations) - core data integrity.

**Sources:**
- [PaperMC: Plugin Configuration](https://docs.papermc.io/paper/dev/plugin-configurations/)

---

### 11. Port Already in Use

**Risk:** Configured port is used by another service, or port persists after failed shutdown. Common on shared hosting.

**Warning Signs:**
- `java.net.BindException: Address already in use`
- Works on one machine, fails on another
- Fails after reload but works after full restart

**Prevention:**
```java
// Try configured port, fallback to alternatives
private int startWebServer(int preferredPort) {
    for (int port = preferredPort; port < preferredPort + 10; port++) {
        try {
            app = Javalin.create().start(port);
            if (port != preferredPort) {
                getLogger().warning("Port " + preferredPort + " in use, using " + port);
            }
            return port;
        } catch (Exception e) {
            if (port == preferredPort + 9) {
                throw new RuntimeException("No available ports in range", e);
            }
        }
    }
    return -1;
}
```

Also verify port is actually released in `onDisable()`.

**Phase:** Address in Phase 1 (Web Server Foundation) - startup robustness.

**Sources:**
- [Red Hat: Bind Port Issues](https://access.redhat.com/solutions/18843)

---

## Integration Pitfalls

Lifecycle, plugin interaction, and Minecraft-specific issues.

### 12. Reload vs Shutdown Confusion

**Risk:** Plugin cannot distinguish between `/reload` (should clean up and restart) and `/stop` (should clean up and exit). May waste time during shutdown or leave state stale on reload.

**Warning Signs:**
- Unnecessary work during server shutdown
- State not properly reset on reload
- Resource leaks accumulate with each reload

**Prevention:**
```java
private volatile boolean serverShuttingDown = false;

@Override
public void onEnable() {
    // Listen for server shutdown
    Bukkit.getPluginManager().registerEvents(new Listener() {
        @EventHandler
        public void onServerCommand(ServerCommandEvent event) {
            if (event.getCommand().equalsIgnoreCase("stop")) {
                serverShuttingDown = true;
            }
        }
    }, this);
}

@Override
public void onDisable() {
    if (serverShuttingDown) {
        // Quick shutdown - just stop the server
        if (app != null) app.stop();
    } else {
        // Reload - full cleanup for restart
        if (app != null) {
            app.stop();
            app = null;
        }
        // Clear caches, reset state, etc.
    }
}
```

**Phase:** Address in Phase 1 (Web Server Foundation) - lifecycle handling.

**Sources:**
- [SpigotMC: Detecting Shutdown vs Reload](https://www.spigotmc.org/threads/check-if-server-is-shutting-down-or-reloading-in-ondisable.59059/)

---

### 13. Session State After Plugin Reload

**Risk:** Web sessions become invalid after plugin reload, forcing users to re-authenticate. Or worse, old sessions remain valid with stale state.

**Warning Signs:**
- Users logged out after every reload
- Stale data shown after reload
- Session tokens work across reloads when they shouldn't

**Prevention:**
```java
// Store sessions in memory with plugin instance tracking
private final String instanceId = UUID.randomUUID().toString();

// Include instance ID in session token
private String createSessionToken(String userId) {
    return instanceId + ":" + userId + ":" + generateSecureToken();
}

// Validate instance ID on each request
private boolean validateSession(String token) {
    String[] parts = token.split(":");
    if (parts.length < 3 || !parts[0].equals(instanceId)) {
        return false; // Different plugin instance - force re-auth
    }
    return validateTokenSignature(parts);
}
```

**Phase:** Address in Phase 2 (Authentication) - session management.

---

### 14. Jetty Security Vulnerabilities

**Risk:** Using outdated Jetty version with known CVEs exposes server to attacks (path traversal, HTTP/2 DoS, authentication bypass).

**Warning Signs:**
- Using Jetty < 10.0.16 or < 11.0.16 or < 12.0.21
- Security scanner flags vulnerabilities
- No dependency update process

**Prevention:**
```kotlin
// build.gradle.kts - Use latest stable Javalin (which uses latest Jetty)
dependencies {
    implementation("io.javalin:javalin:6.+") // Stay on latest 6.x
}
```

Check [Eclipse Jetty CVE List](https://www.cvedetails.com/vulnerability-list/vendor_id-10410/product_id-34824/Eclipse-Jetty.html) before release.

Known critical CVEs to avoid:
- CVE affecting OpenIdAuthenticator (9.4.21-9.4.51, 10.0.15, 11.0.15)
- HTTP/2 resource exhaustion (<=12.0.21)
- Path traversal authentication bypass (<=10.0.20, <=11.0.20, <=12.0.7)

**Phase:** Address in Phase 1 (Web Server Foundation) - dependency selection.

**Sources:**
- [Eclipse Jetty CVE List](https://www.cvedetails.com/vulnerability-list/vendor_id-10410/product_id-34824/Eclipse-Jetty.html)
- [Snyk: Jetty Vulnerabilities](https://security.snyk.io/package/maven/org.eclipse.jetty%3Ajetty-server)

---

## Minor Pitfalls

Annoyances that are fixable but waste time.

### 15. Logging Conflicts

**Risk:** Jetty/SLF4J logging conflicts with Bukkit logging, causing duplicate logs, missing logs, or log format inconsistencies.

**Warning Signs:**
- Double-printed log messages
- Missing web server logs
- Log format changes mid-file

**Prevention:**
```kotlin
// Relocate SLF4J with everything else
tasks.shadowJar {
    relocate("org.slf4j", "com.yourplugin.shadow.slf4j")
}
```

And configure Javalin to suppress default banner:
```java
Javalin.create(config -> {
    config.showJavalinBanner = false;
});
```

**Phase:** Address in Phase 1 - quality of life.

---

### 16. Static Files Not Updating

**Risk:** Embedded static files from JAR don't update when plugin is updated because browser caches them.

**Warning Signs:**
- UI looks old after plugin update
- Hard refresh fixes issues
- Users report "nothing changed"

**Prevention:**
```java
// Add version/hash to static file URLs
String version = getDescription().getVersion();

// Set appropriate cache headers
app.before("/static/*", ctx -> {
    ctx.header("Cache-Control", "public, max-age=3600");
    ctx.header("ETag", "\"" + version + "\"");
});

// Or serve with cache-busting query params in HTML
// <script src="/static/app.js?v=${version}"></script>
```

**Phase:** Address in Phase 3 (Static File Serving) - cache management.

---

## Phase-Specific Warning Summary

| Phase | Critical Pitfalls to Address |
|-------|------------------------------|
| Phase 1: Web Server Foundation | Classloader conflicts (#1), Thread safety pattern (#2), Graceful shutdown (#3), Dependency relocation (#4), CORS (#8), Port binding (#11), Reload handling (#12), Jetty CVEs (#14), Logging (#15) |
| Phase 2: Authentication | Rate limiting (#5), Password security (#7), Session management (#13) |
| Phase 3: Static Files | Directory traversal (#6), Cache busting (#16) |
| Phase 4: CRUD Operations | Main thread blocking (#9), YAML concurrency (#10) |
| Phase 5: Reload Mechanism | Session state (#13), Reload vs shutdown (#12) |

---

## Pre-Development Checklist

Before starting implementation:

- [ ] Understand classloader workaround (this is blocking)
- [ ] Plan thread safety strategy (web threads vs main thread)
- [ ] Configure shadowJar with all relocations
- [ ] Select Javalin/Jetty version (check CVEs)
- [ ] Design session/auth approach with rate limiting
- [ ] Plan YAML access synchronization
- [ ] Choose port and document fallback strategy

---

## Confidence Assessment

| Area | Confidence | Rationale |
|------|------------|-----------|
| Classloader/Threading | HIGH | Official Javalin tutorial + multiple community sources verify |
| Security | HIGH | OWASP guidelines + Jetty CVE database |
| Lifecycle | HIGH | Community discussions + established patterns |
| YAML Concurrency | MEDIUM | Paper docs mention concern; specific patterns extrapolated |
| Port Handling | HIGH | Standard Java networking, verified with Red Hat docs |

---

## Sources

### Official Documentation
- [Javalin and Minecraft Servers](https://javalin.io/tutorials/javalin-and-minecraft-servers)
- [PaperMC Plugin Configuration](https://docs.papermc.io/paper/dev/plugin-configurations/)
- [PaperMC Scheduler Docs](https://docs.papermc.io/paper/dev/scheduler/)

### Security References
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP Path Traversal](https://owasp.org/www-community/attacks/Path_Traversal)
- [OWASP Brute Force Prevention](https://owasp.org/www-community/controls/Blocking_Brute_Force_Attacks)
- [PortSwigger CORS Guide](https://portswigger.net/web-security/cors)
- [Eclipse Jetty CVEs](https://www.cvedetails.com/vulnerability-list/vendor_id-10410/product_id-34824/Eclipse-Jetty.html)

### Community Resources
- [GitHub Gist: Javalin in Spigot](https://gist.github.com/RezzedUp/d7957af10bfbfc6837ae1a4b55975f40)
- [SpigotMC Thread Safety](https://www.spigotmc.org/threads/issue-with-thread-safety-while-using-bukkit-api.178393/)
- [Bukkit Forums: Thread Safety](https://bukkit.org/threads/thread-safety.439207/)
- [Bukkit Wiki: External Libraries](https://bukkit.fandom.com/wiki/Using_External_Libraries_with_Plugins)
- [Jetty Graceful Shutdown](https://github.com/jetty/jetty.project/issues/2076)
