# Phase 18: Web Infrastructure - Research

**Researched:** 2026-01-23
**Domain:** Embedded web server for Minecraft plugin
**Confidence:** HIGH

## Summary

This phase implements the embedded web server foundation for the Collections plugin. The core challenge is integrating Javalin 6.7.0 within Bukkit's custom classloader environment while maintaining thread safety between Jetty threads and Bukkit's main thread.

Prior v1.3 research already established the technology stack (Javalin 6.7.0, htmx, Alpine.js) and documented 16 potential pitfalls. This research focuses on **exact implementation patterns** ready for the planner to convert into tasks.

**Primary recommendation:** Implement a thin `WebPanelManager` class that handles the classloader fix, starts/stops Javalin, registers auth middleware, and provides a main-thread bridge utility. Password hashing uses `at.favre.lib:bcrypt:0.10.2` (standalone, no Spring dependency).

---

## Implementation Patterns

### Pattern 1: Classloader Fix with try-finally

**Source:** [Javalin Minecraft Tutorial](https://javalin.io/tutorials/javalin-and-minecraft-servers)

```java
public class WebPanelManager {
    private final Collections plugin;
    private Javalin app;

    public void start(int port) {
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(plugin.getClass().getClassLoader());
        try {
            app = Javalin.create(this::configureJavalin).start(port);
            registerRoutes();
            plugin.getLogger().info("Web panel started on port " + port);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    private void configureJavalin(JavalinConfig config) {
        // Static files from classpath (inside JAR)
        config.staticFiles.add(staticFiles -> {
            staticFiles.hostedPath = "/";
            staticFiles.directory = "/web";
            staticFiles.location = Location.CLASSPATH;
        });

        // Custom JSON mapper using Gson (provided by Paper)
        config.jsonMapper(createGsonMapper());

        // Suppress Javalin banner
        config.showJavalinBanner = false;

        // Graceful shutdown timeout
        config.jetty.modifyServer(server -> {
            server.setStopTimeout(5000); // 5 seconds
        });
    }
}
```

**Key points:**
- `try-finally` ensures classloader is always restored
- Classloader fix ONLY needed during Javalin instantiation, not for route handlers
- Static files use `Location.CLASSPATH` to serve from JAR

---

### Pattern 2: Graceful Shutdown

**Source:** [Jetty Graceful Shutdown](https://github.com/jetty/jetty.project/issues/2076)

```java
public class WebPanelManager {
    private Javalin app;

    public void stop() {
        if (app != null) {
            try {
                app.stop();
                plugin.getLogger().info("Web panel stopped");
            } catch (Exception e) {
                plugin.getLogger().warning("Error stopping web panel: " + e.getMessage());
            }
            app = null;
        }
    }
}

// In Collections.java onDisable():
@Override
public void onDisable() {
    // Stop web panel BEFORE other shutdown tasks
    if (webPanelManager != null) {
        webPanelManager.stop();
    }

    // ... rest of shutdown
}
```

**Requirement WEB-03 verification:** After `app.stop()`, the port is released. No `BindException` on plugin reload.

---

### Pattern 3: HTTP Basic Auth with BCrypt

**Source:** [Javalin Auth Example](https://javalin.io/tutorials/auth-example), [BCrypt Library](https://github.com/patrickfav/bcrypt)

```java
public class WebAuthHandler {
    private final String passwordHash;

    public WebAuthHandler(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Registers authentication middleware for all routes.
     */
    public void register(Javalin app) {
        app.beforeMatched(ctx -> {
            // Skip auth for static files (served before route matching)
            if (ctx.path().startsWith("/api/")) {
                validateAuth(ctx);
            }
        });
    }

    private void validateAuth(Context ctx) {
        if (!ctx.basicAuthCredentialsExist()) {
            challengeAuth(ctx);
            return;
        }

        BasicAuthCredentials creds = ctx.basicAuthCredentials();

        // Verify password against stored hash
        BCrypt.Result result = BCrypt.verifyer()
            .verify(creds.getPassword().toCharArray(), passwordHash);

        if (!result.verified) {
            challengeAuth(ctx);
        }
    }

    private void challengeAuth(Context ctx) {
        ctx.header("WWW-Authenticate", "Basic realm=\"Collections Admin\", charset=\"UTF-8\"");
        ctx.status(401);
        throw new UnauthorizedResponse("Authentication required");
    }

    /**
     * Hash a plaintext password for storage.
     */
    public static String hashPassword(String password) {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray());
    }
}
```

**Requirements covered:**
- AUTH-01: Password required from config.yml
- AUTH-02: Password stored as BCrypt hash (cost factor 12)

---

### Pattern 4: Main Thread Bridge

**Source:** [Bukkit Thread Safety](https://www.spigotmc.org/threads/issue-with-thread-safety-while-using-bukkit-api.178393/)

```java
public class MainThreadBridge {
    private final Plugin plugin;

    public MainThreadBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Execute task on main thread and wait for result.
     * Use for Bukkit API calls that return data.
     *
     * @throws TimeoutException if main thread is blocked
     */
    public <T> T callSync(Supplier<T> task, long timeoutMs) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            return task.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(task.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Execute task on main thread without waiting.
     * Use for fire-and-forget operations like triggering reload.
     */
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
```

**Usage in route handler:**

```java
app.post("/api/reload", ctx -> {
    mainThreadBridge.runSync(() -> plugin.reload());
    ctx.result("{\"status\":\"ok\"}");
});
```

**Requirement INT-03:** All Bukkit API calls routed through this bridge.

---

### Pattern 5: Gson JsonMapper for Javalin

**Source:** [Javalin Documentation](https://javalin.io/documentation)

Paper bundles Gson at runtime, so we use `compileOnly` and avoid shading it.

```java
private JsonMapper createGsonMapper() {
    Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    return new JsonMapper() {
        @NotNull
        @Override
        public String toJsonString(@NotNull Object obj, @NotNull Type type) {
            return gson.toJson(obj, type);
        }

        @NotNull
        @Override
        public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
            return gson.fromJson(json, targetType);
        }
    };
}
```

---

### Pattern 6: First-Run Password Generation

**Requirement AUTH-02:** Generate random password on first run, log it once.

```java
public class WebPanelConfig {
    private static final String PASSWORD_HASH_KEY = "web-panel.password-hash";

    public void ensurePasswordConfigured(FileConfiguration config, Plugin plugin) {
        if (!config.contains(PASSWORD_HASH_KEY) ||
            config.getString(PASSWORD_HASH_KEY, "").isEmpty()) {

            // Generate secure random password
            String plainPassword = generateSecurePassword(16);
            String hash = WebAuthHandler.hashPassword(plainPassword);

            config.set(PASSWORD_HASH_KEY, hash);
            plugin.saveConfig();

            // Log the password once (user must save it)
            plugin.getLogger().warning("============================================");
            plugin.getLogger().warning("WEB PANEL FIRST RUN");
            plugin.getLogger().warning("Your password is: " + plainPassword);
            plugin.getLogger().warning("This will only be shown ONCE. Save it now!");
            plugin.getLogger().warning("============================================");
        }
    }

    private String generateSecurePassword(int length) {
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
```

---

## Configuration Schema

### config.yml additions

```yaml
# Web Panel Settings
web-panel:
  # Enable/disable the web panel
  enabled: false

  # Port for the web server
  port: 8080

  # Bind address (127.0.0.1 = localhost only, 0.0.0.0 = all interfaces)
  bind-address: "127.0.0.1"

  # BCrypt password hash (auto-generated on first run if empty)
  # To reset: delete this line and restart - new password will be generated
  password-hash: ""
```

### ConfigManager additions

```java
// In ConfigManager.java
public boolean isWebPanelEnabled() {
    return config.getBoolean("web-panel.enabled", false);
}

public int getWebPanelPort() {
    return config.getInt("web-panel.port", 8080);
}

public String getWebPanelBindAddress() {
    return config.getString("web-panel.bind-address", "127.0.0.1");
}

public String getWebPanelPasswordHash() {
    return config.getString("web-panel.password-hash", "");
}
```

---

## Build Configuration

### Gradle additions for build.gradle.kts

```kotlin
dependencies {
    // Existing dependencies...

    // Web Panel - Javalin embedded web server
    implementation("io.javalin:javalin:6.7.0")

    // SLF4J for Javalin logging (required)
    implementation("org.slf4j:slf4j-simple:2.0.17")

    // BCrypt for password hashing (standalone, no Spring)
    implementation("at.favre.lib:bcrypt:0.10.2")
}

tasks.shadowJar {
    // Existing relocations...

    // Web Panel relocations (WEB-04)
    relocate("io.javalin", "com.blockworlds.collections.lib.javalin")
    relocate("org.eclipse.jetty", "com.blockworlds.collections.lib.jetty")
    relocate("org.slf4j", "com.blockworlds.collections.lib.slf4j")
    relocate("jakarta.servlet", "com.blockworlds.collections.lib.jakarta.servlet")
    relocate("kotlin", "com.blockworlds.collections.lib.kotlin")
    relocate("at.favre.lib", "com.blockworlds.collections.lib.favre")
}
```

**Requirement WEB-04:** All Javalin/Jetty dependencies relocated to avoid conflicts with other plugins.

---

## Static File Structure

### Resource layout (WEB-05)

```
src/main/resources/
├── web/                     # Served at /
│   ├── index.html           # Main page (placeholder for now)
│   ├── css/
│   │   └── admin.css        # Minimal styles
│   └── js/
│       └── app.js           # Minimal JS (placeholder)
├── paper-plugin.yml
└── config.yml
```

### Minimal index.html for verification

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Collections Admin</title>
    <link rel="stylesheet" href="/css/admin.css">
</head>
<body>
    <h1>Collections Web Panel</h1>
    <p>Server is running. API endpoints coming in later phases.</p>
    <script src="/js/app.js"></script>
</body>
</html>
```

---

## Test Verification

### Manual verification checklist

| Requirement | Verification Steps |
|-------------|-------------------|
| WEB-01 | Start server, check log for "Web panel started on port 8080" |
| WEB-02 | No `NoClassDefFoundError` or `ClassNotFoundException` on startup |
| WEB-03 | Run `/collections reload`, verify no `BindException` |
| WEB-04 | Install alongside Dynmap, verify no `LinkageError` |
| WEB-05 | Browse to `http://localhost:8080/`, verify index.html displays |
| AUTH-01 | Browse to `http://localhost:8080/api/status`, verify 401 response |
| AUTH-02 | Check config.yml for `password-hash` starting with `$2a$` |
| INT-03 | Call `/api/reload`, verify no async thread warnings in console |

### Automated test approach

```java
@Test
void testPasswordHashing() {
    String password = "test123";
    String hash = WebAuthHandler.hashPassword(password);

    BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash);
    assertTrue(result.verified);

    // Verify wrong password fails
    BCrypt.Result wrongResult = BCrypt.verifyer().verify("wrong".toCharArray(), hash);
    assertFalse(wrongResult.verified);
}

@Test
void testMainThreadBridgeOnMainThread() throws Exception {
    // When already on main thread, should execute immediately
    MainThreadBridge bridge = new MainThreadBridge(mockPlugin);

    when(Bukkit.isPrimaryThread()).thenReturn(true);

    String result = bridge.callSync(() -> "test", 1000);
    assertEquals("test", result);
}
```

---

## Class Structure

### New package: `com.blockworlds.collections.web`

```
web/
├── WebPanelManager.java      # Lifecycle: start/stop Javalin
├── WebPanelConfig.java       # Configuration loading, password generation
├── WebAuthHandler.java       # HTTP Basic Auth middleware
├── MainThreadBridge.java     # Thread safety utility
└── api/
    └── StatusController.java # Health check endpoint (for verification)
```

### WebPanelManager lifecycle integration

```java
// In Collections.java

private WebPanelManager webPanelManager;

@Override
public void onEnable() {
    // ... existing initialization ...

    // Initialize web panel (after configManager)
    if (configManager.isWebPanelEnabled()) {
        this.webPanelManager = new WebPanelManager(this);
        webPanelManager.start(configManager.getWebPanelPort());
    }
}

@Override
public void onDisable() {
    // Stop web panel FIRST (before other cleanup)
    if (webPanelManager != null) {
        webPanelManager.stop();
    }

    // ... existing shutdown ...
}

public void reload() {
    // ... existing reload code ...

    // Note: Web panel does NOT restart on reload (stateless server)
    // Password changes require full server restart
}
```

---

## Common Pitfalls (from prior research)

| Pitfall | Prevention |
|---------|------------|
| Classloader conflicts | Use try-finally classloader swap pattern |
| Thread safety | ALL Bukkit API through MainThreadBridge |
| Port binding on reload | Call `app.stop()` in onDisable() |
| Dependency conflicts | Relocate ALL transitive deps in shadowJar |
| Weak passwords | BCrypt with cost 12, random generation |
| Plaintext storage | Never store plain password, only hash |

---

## State of the Art

| Old Approach | Current Approach | Notes |
|--------------|------------------|-------|
| jBCrypt 0.4 | at.favre.lib:bcrypt 0.10.2 | Modern API, actively maintained |
| Javalin 4.x AccessManager | Javalin 6.x beforeMatched | AccessManager is deprecated |
| SparkJava | Javalin 6.7.0 | SparkJava appears abandoned |
| Manual Base64 decode | ctx.basicAuthCredentials() | Javalin provides helper method |

---

## Open Questions

1. **HTTPS support:** Not addressed in this phase. Typically handled by reverse proxy (nginx) in production. Could add in future phase if needed.

2. **Rate limiting:** Not in requirements, but recommended for production. Consider adding in future phase to prevent brute force.

3. **Session-based auth alternative:** HTTP Basic works but requires re-authentication per browser session. Token-based auth could be added later.

---

## Sources

### PRIMARY (HIGH confidence)
- [Javalin Documentation](https://javalin.io/documentation) - Static files, JSON mapper, lifecycle
- [Javalin Minecraft Tutorial](https://javalin.io/tutorials/javalin-and-minecraft-servers) - Classloader fix
- [patrickfav/bcrypt GitHub](https://github.com/patrickfav/bcrypt) - BCrypt API, version 0.10.2
- [Javalin Auth Example](https://javalin.io/tutorials/auth-example) - beforeMatched pattern

### SECONDARY (MEDIUM confidence)
- [RezzedUp Gist](https://gist.github.com/RezzedUp/d7957af10bfbfc6837ae1a4b55975f40) - Additional classloader context
- [Bukkit Thread Safety](https://www.spigotmc.org/threads/issue-with-thread-safety-while-using-bukkit-api.178393/) - Main thread patterns

### TERTIARY (existing project research)
- `.planning/research/v1.3/STACK.md` - Stack decisions
- `.planning/research/v1.3/ARCHITECTURE.md` - Integration points
- `.planning/research/PITFALLS-WEBSERVER.md` - 16 documented pitfalls

---

## Metadata

**Confidence breakdown:**
- Classloader fix: HIGH - Official Javalin tutorial
- BCrypt library: HIGH - Official GitHub, Maven Central
- Authentication pattern: HIGH - Official Javalin examples
- Build configuration: HIGH - Standard shadowJar patterns
- Thread bridge: HIGH - Standard Bukkit scheduling

**Research date:** 2026-01-23
**Valid until:** 2026-03-23 (60 days - Javalin is stable)
