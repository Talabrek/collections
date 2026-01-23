---
phase: 18-web-infrastructure
plan: 02
subsystem: web
tags: [javalin, jetty, bcrypt, web-server, classloader, thread-safety]

# Dependency graph
requires:
  - phase: 18-01
    provides: Javalin dependencies and shadowJar relocations
provides:
  - WebPanelManager with Javalin lifecycle and classloader fix
  - WebPanelConfig with BCrypt password generation/verification
  - MainThreadBridge for thread-safe Bukkit API calls from web routes
  - Config.yml web-panel section with enabled, port, bind-address, password-hash
  - ConfigManager web panel methods
affects: [18-03, web-routes, authentication-endpoints]

# Tech tracking
tech-stack:
  added: []
  patterns: [classloader-context-swap-for-bukkit-embedded-servers, main-thread-bridge-for-async-web-handlers]

key-files:
  created:
    - src/main/java/com/blockworlds/collections/web/WebPanelManager.java
    - src/main/java/com/blockworlds/collections/web/WebPanelConfig.java
    - src/main/java/com/blockworlds/collections/web/MainThreadBridge.java
  modified:
    - src/main/java/com/blockworlds/collections/Collections.java
    - src/main/java/com/blockworlds/collections/config/ConfigManager.java
    - src/main/resources/config.yml

key-decisions:
  - "WEB-02: Classloader context swap during Javalin instantiation for Bukkit compatibility"
  - "WEB-03: Web panel stops FIRST on disable to release port for clean reload"
  - "AUTH-02: BCrypt cost factor 12 for password hashing (secure but not slow)"

patterns-established:
  - "Classloader fix: save/swap/restore around Javalin.create() for ServiceLoader compatibility"
  - "MainThreadBridge pattern: callSync() for blocking, runSync() for fire-and-forget"

# Metrics
duration: 5min
completed: 2026-01-23
---

# Phase 18 Plan 02: WebPanelManager Core Summary

**Javalin web server with classloader fix, BCrypt password config, and MainThreadBridge for thread-safe Bukkit API access**

## Performance

- **Duration:** 5 min
- **Started:** 2026-01-23T16:00:00Z
- **Completed:** 2026-01-23T16:05:00Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments
- WebPanelManager with classloader context swap for Bukkit-embedded Javalin
- WebPanelConfig with SecureRandom password generation and BCrypt hashing
- MainThreadBridge utility for safe Bukkit API calls from Jetty threads
- Full plugin lifecycle integration (start on enable, stop first on disable)
- Config.yml web-panel section with all required settings

## Task Commits

Each task was committed atomically:

1. **Task 1: Create web package with core classes** - `076bc9b` (feat)
2. **Task 2: Add config.yml settings and ConfigManager methods** - `53860a8` (feat)
3. **Task 3: Integrate WebPanelManager into plugin lifecycle** - `b7b4c65` (feat)

## Files Created/Modified
- `src/main/java/com/blockworlds/collections/web/WebPanelManager.java` - Javalin lifecycle with classloader fix
- `src/main/java/com/blockworlds/collections/web/WebPanelConfig.java` - BCrypt password generation/verification
- `src/main/java/com/blockworlds/collections/web/MainThreadBridge.java` - Thread-safe Bukkit API bridge
- `src/main/java/com/blockworlds/collections/Collections.java` - Web panel lifecycle integration
- `src/main/java/com/blockworlds/collections/config/ConfigManager.java` - Web panel config methods
- `src/main/resources/config.yml` - Web panel configuration section

## Decisions Made
- Used SecureRandom with non-ambiguous character set for password generation (no 0/O, 1/l/I confusion)
- BCrypt cost factor 12 balances security with acceptable verification time
- Web panel does NOT restart on `/collections reload` - requires full server restart for config changes (simpler, avoids state issues)
- Password-hash auto-generated on first run and logged once as WARNING so admin sees it

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
None - compilation succeeded on all tasks.

## User Setup Required
None - no external service configuration required. Web panel disabled by default; enable in config.yml when ready.

## Next Phase Readiness
- WEB-01 satisfied: Javalin web server starts on configurable port
- WEB-02 satisfied: Classloader fix applied during Javalin instantiation
- WEB-03 satisfied: Web server stops gracefully on plugin disable
- INT-03 foundation: MainThreadBridge ready for route handlers
- AUTH-02 foundation: Password hash generation and verification working
- Ready for Phase 18-03: Authentication and API routes

---
*Phase: 18-web-infrastructure*
*Completed: 2026-01-23*
