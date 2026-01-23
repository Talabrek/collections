# Phase 19: Read-Only API - Research

**Researched:** 2026-01-23
**Domain:** REST API endpoints and JavaScript frontend for collection viewing
**Confidence:** HIGH

## Summary

Phase 19 implements read-only API endpoints for collection listing and detail viewing, plus the JavaScript frontend to display this data in the web panel. The work builds directly on Phase 18's Javalin infrastructure, following the established controller pattern from StatusController.

The primary technical domains are:
1. **Backend:** Javalin REST endpoints using ctx.json() and path parameters
2. **Frontend:** Vanilla JavaScript with fetch() for data loading and DOM manipulation
3. **Threading:** MainThreadBridge for accessing CollectionManager from Javalin threads

**Primary recommendation:** Follow the existing StatusController pattern for new API controllers. Use MainThreadBridge.callSync() for all CollectionManager access. Frontend uses vanilla JavaScript with periodic heartbeat polling for connection status.

---

## Standard Stack

### Core (Already in Place from Phase 18)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Javalin | 6.7.0 | REST API framework | Already integrated, proven patterns |
| Gson | (Paper bundled) | JSON serialization | Already configured in WebPanelManager |

### Frontend
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Vanilla JS | ES6+ | DOM manipulation, fetch API | No build step, works in all browsers |
| CSS | CSS3 | Styling | Already have admin.css base |

### No Additional Dependencies
Phase 19 requires NO new dependencies - all functionality can be achieved with existing stack.

---

## Architecture Patterns

### Backend: Controller Registration Pattern

Follow the existing StatusController pattern established in Phase 18.

**Structure:**
```
web/api/
├── StatusController.java      # Existing - health check
├── CollectionsController.java # NEW - list/detail endpoints
```

**Pattern:**
```java
public class CollectionsController {
    private final Collections plugin;
    private final MainThreadBridge mainThreadBridge;

    public CollectionsController(Collections plugin, MainThreadBridge mainThreadBridge) {
        this.plugin = plugin;
        this.mainThreadBridge = mainThreadBridge;
    }

    public void register(Javalin app) {
        app.get("/api/collections", this::listCollections);
        app.get("/api/collections/{id}", this::getCollection);
    }

    private void listCollections(Context ctx) {
        // Implementation
    }

    private void getCollection(Context ctx) {
        // Implementation
    }
}
```

**Registration in WebPanelManager.registerRoutes():**
```java
private void registerRoutes() {
    // ... existing auth registration ...

    MainThreadBridge bridge = new MainThreadBridge(plugin);
    new StatusController(plugin).register(app);
    new CollectionsController(plugin, bridge).register(app);
}
```

### Backend: Thread Safety with MainThreadBridge

**CRITICAL:** CollectionManager.getAllCollections() and getCollection() access ConcurrentHashMap but the Collection records contain Bukkit types (Material, etc.). For safety and consistency, always use MainThreadBridge.

**Pattern:**
```java
private void listCollections(Context ctx) {
    try {
        List<CollectionSummary> summaries = mainThreadBridge.callSync(() -> {
            return plugin.getCollectionManager().getAllCollections().values().stream()
                .map(this::toSummary)
                .sorted(Comparator.comparing(CollectionSummary::name))
                .toList();
        }, 2000); // 2 second timeout

        ctx.json(summaries);
    } catch (MainThreadBridge.MainThreadException e) {
        throw new InternalServerErrorResponse("Server busy");
    }
}
```

### Backend: DTO Pattern for JSON Responses

Create simple record DTOs to control JSON output and avoid exposing internal model structure.

**Pattern:**
```java
// Summary for list view
public record CollectionSummary(
    String id,
    String name,
    String tier,
    int itemCount,
    List<String> zones
) {}

// Detail view with full item data
public record CollectionDetail(
    String id,
    String name,
    String description,
    String tier,
    String icon,
    List<ItemSummary> items,
    RewardSummary rewards,
    List<String> zones,
    List<String> requires
) {}

public record ItemSummary(
    String id,
    String name,
    String material,
    int weight,
    boolean soulbound
) {}

public record RewardSummary(
    int experience,
    List<String> commands,
    boolean fireworks
) {}
```

### Frontend: Page Structure Pattern

**Pattern:** Single-page app with view switching via JavaScript.

```
src/main/resources/web/
├── index.html          # Main entry, includes navigation
├── css/
│   └── admin.css       # Extend existing styles
└── js/
    └── app.js          # All JavaScript in one file
```

**HTML Structure:**
```html
<div class="container">
    <!-- Connection status indicator -->
    <div id="connection-status" class="status-indicator"></div>

    <!-- Navigation/header -->
    <header>
        <h1>Collections Admin</h1>
        <nav>...</nav>
    </header>

    <!-- View containers (shown/hidden) -->
    <main id="content">
        <div id="view-list" class="view">...</div>
        <div id="view-detail" class="view hidden">...</div>
    </main>
</div>
```

### Frontend: Connection Status Heartbeat Pattern

**Pattern:** Periodic polling to /api/status with visual indicator.

```javascript
const HEARTBEAT_INTERVAL = 30000; // 30 seconds
let heartbeatTimer = null;
let lastSuccessTime = null;

function startHeartbeat() {
    checkConnection();
    heartbeatTimer = setInterval(checkConnection, HEARTBEAT_INTERVAL);
}

async function checkConnection() {
    const indicator = document.getElementById('connection-status');
    try {
        const response = await fetch('/api/status');
        if (response.ok) {
            indicator.classList.remove('disconnected');
            indicator.classList.add('connected');
            indicator.title = 'Connected';
            lastSuccessTime = Date.now();
        } else if (response.status === 401) {
            // Auth required - redirect or show login
            indicator.classList.add('warning');
        }
    } catch (error) {
        indicator.classList.remove('connected');
        indicator.classList.add('disconnected');
        indicator.title = 'Disconnected';
    }
}
```

### Frontend: Data Fetching Pattern

**Pattern:** Async/await with error handling and loading states.

```javascript
async function loadCollections() {
    showLoading('view-list');
    try {
        const response = await fetch('/api/collections');
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const collections = await response.json();
        renderCollectionList(collections);
    } catch (error) {
        showError('view-list', 'Failed to load collections');
    }
}

async function loadCollectionDetail(id) {
    showLoading('view-detail');
    try {
        const response = await fetch(`/api/collections/${encodeURIComponent(id)}`);
        if (response.status === 404) {
            showError('view-detail', 'Collection not found');
            return;
        }
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const collection = await response.json();
        renderCollectionDetail(collection);
    } catch (error) {
        showError('view-detail', 'Failed to load collection');
    }
}
```

### Frontend: View Switching Pattern

**Pattern:** Hide/show views, update URL hash for bookmarkability.

```javascript
function showView(viewId) {
    document.querySelectorAll('.view').forEach(v => v.classList.add('hidden'));
    document.getElementById(viewId).classList.remove('hidden');
}

// Handle hash navigation
window.addEventListener('hashchange', handleRoute);

function handleRoute() {
    const hash = window.location.hash;
    if (hash.startsWith('#collection/')) {
        const id = hash.substring('#collection/'.length);
        showView('view-detail');
        loadCollectionDetail(id);
    } else {
        showView('view-list');
        loadCollections();
    }
}
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON serialization | Manual string building | ctx.json() with records | Handles escaping, content-type, edge cases |
| 404 responses | ctx.status(404).result("...") | throw new NotFoundResponse() | Consistent error format, JSON if client accepts |
| Thread safety | Raw executor service | MainThreadBridge.callSync() | Already handles timeouts, exceptions, cancellation |
| URL encoding | Manual replace | encodeURIComponent() | Handles all special characters correctly |
| Loading states | Custom spinner library | CSS + class toggle | Simple, no dependencies |

---

## Common Pitfalls

### Pitfall 1: Accessing CollectionManager Without MainThreadBridge
**What goes wrong:** Race conditions or Bukkit API calls from Jetty threads
**Why it happens:** CollectionManager looks thread-safe (uses ConcurrentHashMap) but returns records containing Bukkit types
**How to avoid:** Always wrap CollectionManager access in mainThreadBridge.callSync()
**Warning signs:** Intermittent errors, inconsistent data, async thread warnings in console

### Pitfall 2: Exposing Internal Model Structure in JSON
**What goes wrong:** API response includes Bukkit types (Material enum), internal fields, or circular references
**Why it happens:** Using ctx.json(collection) directly on model records
**How to avoid:** Create DTO records that map model to JSON-safe structures
**Warning signs:** Gson serialization errors, huge JSON responses, weird field names

### Pitfall 3: Missing Error Handling in fetch()
**What goes wrong:** Unhandled promise rejections, silent failures, blank UI
**Why it happens:** fetch() only rejects on network errors, not HTTP errors
**How to avoid:** Always check response.ok before calling response.json()
**Warning signs:** 404s showing as successes, error messages not displayed

### Pitfall 4: Blocking MainThreadBridge Too Long
**What goes wrong:** Web requests timeout, server TPS drops
**Why it happens:** Complex operations inside callSync() block main thread
**How to avoid:** Keep callSync() operations simple and fast (< 50ms), use 2000ms timeout
**Warning signs:** "Task timed out" exceptions, server lag during API calls

### Pitfall 5: Path Parameter Injection
**What goes wrong:** Security issues if collection ID is used unsafely
**Why it happens:** User-controlled input passed to internal methods
**How to avoid:** CollectionManager.getCollection() returns null for invalid IDs (safe), never use ID in file paths
**Warning signs:** N/A - current architecture is safe

### Pitfall 6: Heartbeat Hammering Server
**What goes wrong:** Too many /api/status requests causing load
**Why it happens:** Heartbeat interval too short or not cleared on page unload
**How to avoid:** Use 30-second interval minimum, clear interval on page hide
**Warning signs:** Server log flooded with /api/status requests

---

## Code Examples

### Backend: Collection List Endpoint

```java
// Source: Based on Javalin documentation and existing StatusController
private void listCollections(Context ctx) {
    try {
        List<CollectionSummary> summaries = mainThreadBridge.callSync(() -> {
            return plugin.getCollectionManager().getAllCollections().values().stream()
                .map(c -> new CollectionSummary(
                    c.id(),
                    c.name(),
                    c.tier().name(),
                    c.items().size(),
                    c.allowedZones()
                ))
                .sorted(Comparator.comparing(CollectionSummary::name))
                .toList();
        }, 2000);

        ctx.json(summaries);
    } catch (MainThreadBridge.MainThreadException e) {
        plugin.getLogger().warning("Failed to fetch collections: " + e.getMessage());
        throw new InternalServerErrorResponse("Server busy, please retry");
    }
}
```

### Backend: Collection Detail Endpoint with NotFoundResponse

```java
// Source: Javalin documentation - NotFoundResponse pattern
private void getCollection(Context ctx) {
    String id = ctx.pathParam("id");

    try {
        CollectionDetail detail = mainThreadBridge.callSync(() -> {
            Collection c = plugin.getCollectionManager().getCollection(id);
            if (c == null) {
                return null;
            }
            return toDetail(c);
        }, 2000);

        if (detail == null) {
            throw new NotFoundResponse("Collection not found: " + id);
        }

        ctx.json(detail);
    } catch (MainThreadBridge.MainThreadException e) {
        plugin.getLogger().warning("Failed to fetch collection " + id + ": " + e.getMessage());
        throw new InternalServerErrorResponse("Server busy, please retry");
    }
}

private CollectionDetail toDetail(Collection c) {
    List<ItemSummary> items = c.items().stream()
        .map(i -> new ItemSummary(
            i.id(),
            i.name(),
            i.material().name(),
            i.weight(),
            i.soulbound()
        ))
        .toList();

    RewardSummary rewards = new RewardSummary(
        c.rewards().experience(),
        c.rewards().commands(),
        c.rewards().fireworks()
    );

    return new CollectionDetail(
        c.id(),
        c.name(),
        c.description(),
        c.tier().name(),
        c.icon().name(),
        items,
        rewards,
        c.allowedZones(),
        c.requiredCollections()
    );
}
```

### Frontend: Collection List Rendering

```javascript
// Source: MDN Fetch API documentation
function renderCollectionList(collections) {
    const container = document.getElementById('collection-list');

    if (collections.length === 0) {
        container.innerHTML = '<p class="empty">No collections configured.</p>';
        return;
    }

    const html = collections.map(c => `
        <div class="collection-card" data-id="${escapeHtml(c.id)}">
            <div class="collection-header">
                <h3 class="collection-name">${escapeHtml(c.name)}</h3>
                <span class="tier tier-${c.tier.toLowerCase()}">${c.tier}</span>
            </div>
            <div class="collection-meta">
                <span class="item-count">${c.itemCount} items</span>
                ${c.zones.length > 0 ? `<span class="zones">${c.zones.length} zones</span>` : ''}
            </div>
        </div>
    `).join('');

    container.innerHTML = html;

    // Add click handlers
    container.querySelectorAll('.collection-card').forEach(card => {
        card.addEventListener('click', () => {
            window.location.hash = `#collection/${card.dataset.id}`;
        });
    });
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
```

### Frontend: Connection Status Indicator CSS

```css
/* Source: Standard CSS patterns */
.status-indicator {
    position: fixed;
    top: 1rem;
    right: 1rem;
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: #888;
    transition: background-color 0.3s;
}

.status-indicator.connected {
    background: #44ff44;
    box-shadow: 0 0 8px #44ff44;
}

.status-indicator.disconnected {
    background: #ff4444;
    box-shadow: 0 0 8px #ff4444;
}

.status-indicator::before {
    content: '';
    position: absolute;
    top: -4px;
    left: -4px;
    right: -4px;
    bottom: -4px;
    border-radius: 50%;
    border: 2px solid currentColor;
    opacity: 0;
}

.status-indicator.disconnected::before {
    animation: pulse 1.5s infinite;
    border-color: #ff4444;
    opacity: 1;
}

@keyframes pulse {
    0% { transform: scale(1); opacity: 1; }
    100% { transform: scale(1.5); opacity: 0; }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| jQuery $.ajax() | Native fetch() | ES6 (2015) | No jQuery dependency needed |
| XMLHttpRequest | fetch() + async/await | ES2017 | Cleaner syntax, Promises |
| Manual JSON.stringify for response | ctx.json(object) | Javalin 3+ | Automatic content-type, safer |
| ctx.param("id") | ctx.pathParam("id") | Javalin 4+ | Clearer method naming |

**Notes:**
- Javalin 6.x uses `ctx.pathParam()` not `ctx.param()` (4.x change)
- `ctx.basicAuthCredentialsExist()` was removed in Javalin 6.x (Phase 18 already handled this)

---

## Open Questions

1. **Pagination for large collection lists**
   - What we know: Current codebase has ~70 collection files
   - What's unclear: Will there ever be hundreds/thousands?
   - Recommendation: Skip pagination for Phase 19 (CRUD-01 doesn't require it), add in future if needed

2. **Caching collection data**
   - What we know: Collections are loaded once on startup and cached in ConcurrentHashMap
   - What's unclear: Should API responses be cached too?
   - Recommendation: No HTTP caching needed - data already in memory, requests are fast

3. **Search/filter for collections**
   - What we know: Not in CRUD-01/CRUD-02 requirements
   - Recommendation: Defer to future phase (CRUD operations)

---

## Sources

### Primary (HIGH confidence)
- [Javalin Documentation](https://javalin.io/documentation) - ctx.json(), pathParam(), NotFoundResponse
- Existing codebase: StatusController.java, WebPanelManager.java, MainThreadBridge.java
- Existing codebase: Collection.java, CollectionItem.java model records
- [MDN Fetch API](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch) - fetch() patterns

### Secondary (MEDIUM confidence)
- [Baeldung Javalin REST](https://www.baeldung.com/javalin-rest-microservices) - Controller patterns
- [Go Make Things fetch](https://gomakethings.com/how-to-use-the-fetch-api-with-vanilla-js/) - Vanilla JS patterns

### Project Context
- `.planning/phases/18-web-infrastructure/18-RESEARCH.md` - Javalin setup, auth patterns
- `.planning/phases/18-web-infrastructure/18-03-SUMMARY.md` - Phase 18 completion status

---

## Metadata

**Confidence breakdown:**
- API endpoint patterns: HIGH - Based on existing StatusController and Javalin docs
- DTO pattern: HIGH - Standard Java practice, proven in many projects
- MainThreadBridge usage: HIGH - Already implemented and tested in Phase 18
- Frontend patterns: HIGH - Standard vanilla JS, no unusual requirements
- Connection heartbeat: MEDIUM - Standard pattern, exact timing may need tuning

**Research date:** 2026-01-23
**Valid until:** 2026-03-23 (60 days - stable APIs, no major changes expected)
