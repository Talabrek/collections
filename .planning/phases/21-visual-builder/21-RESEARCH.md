# Phase 21: Visual Builder - Research

**Researched:** 2026-01-23
**Domain:** Drag-and-drop visual item browser for collection editing (HTML5 DnD + CSS sprites)
**Confidence:** HIGH

## Summary

Phase 21 implements a visual drag-and-drop interface for building collections. This extends Phase 20's form-based editing by replacing the manual material text input with a searchable, visual item browser grid. Users drag Minecraft item icons from the browser into collection slots and can reorder items via drag-and-drop.

The primary technical components are:
1. **Item Browser Panel:** A searchable/filterable grid of all ~1400 Minecraft materials with visual icons
2. **Drag-and-Drop Sorting:** Enable reordering items within a collection via drag-and-drop
3. **Drag-and-Drop Transfer:** Drag items from browser into collection slots
4. **CSS Sprite Icons:** Display Minecraft item/block icons using CSS sprite sheets
5. **Form Fields:** Add tier, biomes, dimensions, y-level fields per requirements

The Visual Builder builds on the existing form infrastructure from Phase 20, adding a two-panel layout where the left panel shows the collection form with draggable item slots and the right panel shows the item browser.

**Primary recommendation:** Use SortableJS (MIT-licensed, 2KB gzipped, no dependencies) for drag-and-drop functionality and the minecraft-items-css library for item icons. Implement a two-panel layout with the item browser on the right side of the form view.

---

## Standard Stack

### Core (No New Backend Dependencies)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| SortableJS | 1.15.6 | Drag-and-drop lists | MIT license, 2KB, no dependencies, touch support |
| minecraft-items-css | Latest | Item/block CSS sprites | Community standard, parses Minecraft Wiki sprites |
| HTML5 DnD API | Native | Browser drag-and-drop | Standard, no library needed for basic operations |

### Frontend Additions (Vanilla JS - No Build Step)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| SortableJS | 1.15.6 | Sortable item lists + cross-list transfer | Item reordering and browser-to-slot drag |

### CDN Links (Add to index.html)
```html
<!-- SortableJS for drag-and-drop -->
<script src="https://cdn.jsdelivr.net/npm/sortablejs@1.15.6/Sortable.min.js"></script>
```

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| SortableJS | Native HTML5 DnD | More code, no touch support, cross-browser issues |
| SortableJS | dragula.js | Similar size (15KB), less active maintenance |
| minecraft-items-css | Custom sprite sheet | Requires manual extraction, maintenance burden |
| CDN-hosted sprites | Self-hosted sprites | CDN may be blocked, consider local fallback |

---

## Architecture Patterns

### Frontend: Two-Panel Layout
```
+------------------------------------------+
|          Collection Form                 |
+------------------------------------------+
| [Basic Info Fields]                      |
|------------------------------------------|
| Items Section          | Item Browser    |
| +------+  +------+    | [Search______]  |
| | Item |  | Item |    | +--+--+--+--+   |
| | 1    |  | 2    |    | |  |  |  |  |   |
| +------+  +------+    | |  |  |  |  |   |
|        [+ Add]        | +--+--+--+--+   |
|                       | [Grid of items] |
+------------------------------------------+
| [Zone/Dimension Fields]                  |
+------------------------------------------+
```

### Frontend: SortableJS Group Configuration

Enable dragging between the item browser and collection slots:

```javascript
// Item browser - source only (can drag out, cannot receive)
const browserSortable = Sortable.create(browserGrid, {
    group: {
        name: 'items',
        pull: 'clone',    // Clone items when dragging from browser
        put: false        // Cannot drop items back into browser
    },
    sort: false,          // Browser items don't reorder
    animation: 150
});

// Collection items - can receive and reorder
const collectionSortable = Sortable.create(itemsContainer, {
    group: {
        name: 'items',
        pull: true,       // Can drag items out (to trash or reorder)
        put: true         // Can receive items from browser
    },
    animation: 150,
    onAdd: function(evt) {
        // Convert browser item to collection item form
        const material = evt.item.dataset.material;
        convertToItemRow(evt.item, material);
    },
    onEnd: function(evt) {
        // Renumber items after reorder
        renumberItems();
    }
});
```

### Frontend: Item Browser Component Structure

```javascript
// Item browser state
const itemBrowser = {
    allMaterials: [],      // Full list from API
    filteredMaterials: [], // After search filter
    currentPage: 0,
    pageSize: 100          // Virtual scroll or pagination
};

// Initialize browser
async function initItemBrowser() {
    // Fetch all materials from backend API
    const response = await fetch('/api/materials');
    itemBrowser.allMaterials = await response.json();
    itemBrowser.filteredMaterials = [...itemBrowser.allMaterials];
    renderBrowserGrid();
}

// Search/filter
function filterMaterials(searchTerm) {
    const term = searchTerm.toLowerCase();
    itemBrowser.filteredMaterials = itemBrowser.allMaterials.filter(
        m => m.toLowerCase().includes(term)
    );
    itemBrowser.currentPage = 0;
    renderBrowserGrid();
}
```

### Backend: Materials API Endpoint

Add a new endpoint to expose all valid Minecraft materials:

```java
// GET /api/materials - List all valid Minecraft materials
private void listMaterials(Context ctx) {
    // Cache this - it doesn't change at runtime
    List<String> materials = Arrays.stream(Material.values())
        .filter(m -> !m.isLegacy())     // Exclude legacy materials
        .filter(Material::isItem)        // Only items (not just blocks)
        .map(Material::name)
        .sorted()
        .toList();

    ctx.json(materials);
}
```

### Frontend: CSS Sprite Icon Display

Using minecraft-items-css naming convention:

```html
<!-- Single item in browser grid -->
<div class="browser-item" data-material="DIAMOND" draggable="true">
    <span class="mc-icon mc-icon-diamond"></span>
    <span class="item-label">DIAMOND</span>
</div>
```

```css
/* Browser grid */
.item-browser-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(48px, 1fr));
    gap: 4px;
    max-height: 400px;
    overflow-y: auto;
}

.browser-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 4px;
    cursor: grab;
    border-radius: 4px;
    background: var(--bg-tertiary);
}

.browser-item:hover {
    background: var(--bg-secondary);
    border: 1px solid var(--accent-color);
}

/* Drag visual feedback */
.browser-item.sortable-ghost {
    opacity: 0.4;
}

.browser-item.sortable-chosen {
    border: 2px dashed var(--accent-color);
}
```

### Frontend: Draggable Item Slot in Form

```html
<!-- Item slot in collection form -->
<div class="item-slot" data-index="0">
    <div class="slot-icon">
        <span class="mc-icon mc-icon-diamond"></span>
    </div>
    <div class="slot-details">
        <input type="text" name="item-name-0" placeholder="Item Name" value="Diamond Shard">
        <input type="number" name="item-weight-0" min="1" value="10">
        <button type="button" class="btn-remove-slot">&times;</button>
    </div>
</div>
```

### Frontend: Collection Properties Form Fields

Per VB-06 requirements (tier, biomes, dimensions, y-level range):

```html
<div class="form-section">
    <h3>Spawn Conditions</h3>

    <!-- Tier selection -->
    <div class="form-group">
        <label for="form-tier">Tier</label>
        <select id="form-tier" name="tier">
            <option value="COMMON">Common</option>
            <option value="UNCOMMON">Uncommon</option>
            <option value="RARE">Rare</option>
            <option value="EPIC">Epic</option>
            <option value="LEGENDARY">Legendary</option>
            <option value="EVENT">Event</option>
        </select>
    </div>

    <!-- Biome multi-select (tag input style) -->
    <div class="form-group">
        <label for="form-biomes">Biomes</label>
        <input type="text" id="form-biomes" name="biomes"
               placeholder="FOREST, PLAINS, JUNGLE">
        <small>Comma-separated biome names. Leave empty for all biomes.</small>
    </div>

    <!-- Dimension checkboxes -->
    <div class="form-group">
        <label>Dimensions</label>
        <div class="checkbox-row">
            <label><input type="checkbox" name="dim-normal" checked> Overworld</label>
            <label><input type="checkbox" name="dim-nether"> Nether</label>
            <label><input type="checkbox" name="dim-end"> End</label>
        </div>
    </div>

    <!-- Y-level range -->
    <div class="form-row">
        <div class="form-group">
            <label for="form-min-y">Min Y</label>
            <input type="number" id="form-min-y" name="min-y"
                   value="-64" min="-64" max="320">
        </div>
        <div class="form-group">
            <label for="form-max-y">Max Y</label>
            <input type="number" id="form-max-y" name="max-y"
                   value="320" min="-64" max="320">
        </div>
    </div>
</div>
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Drag-and-drop sorting | Custom mouse event handlers | SortableJS | Touch support, animation, cross-list drag |
| Item icons | Manual sprite extraction | minecraft-items-css | 1400+ icons pre-extracted, CSS classes ready |
| Cross-list drag | Complex dataTransfer handling | SortableJS groups | Handles cloning, validation, events |
| Touch device support | Touch event polyfill | SortableJS | Built-in touch support |
| Drag visual feedback | Manual element cloning | SortableJS ghost/chosen | Configurable with CSS classes |

**Key insight:** SortableJS handles the complex cases: touch devices, animation during drag, cross-list transfers with cloning, and scroll containers. Native HTML5 DnD would require 3-4x more code and still lack touch support.

---

## Common Pitfalls

### Pitfall 1: Sprite Sheet Loading Delay
**What goes wrong:** Item icons appear as broken images before sprite sheet loads
**Why it happens:** CSS sprite sheet is a large image (~500KB-2MB)
**How to avoid:**
- Preload sprite sheet in page head: `<link rel="preload" href="/css/sprites.webp" as="image">`
- Show loading spinner while sprites load
- Use webp format for smaller size
**Warning signs:** Flickering icons on page load

### Pitfall 2: Performance with 1400+ Items
**What goes wrong:** Browser freezes when rendering all items at once
**Why it happens:** DOM thrashing with 1400+ elements
**How to avoid:**
- Implement virtual scrolling or pagination (show 100 items at a time)
- Filter aggressively - most users search by name
- Lazy-load items outside viewport
**Warning signs:** Slow initial render, unresponsive search

### Pitfall 3: Dragged Item Loses Form Data
**What goes wrong:** When dragging item from slot A to slot B, form inputs reset
**Why it happens:** SortableJS moves DOM elements, not JavaScript state
**How to avoid:**
- Serialize form state before drag, restore after
- Or use data attributes instead of form inputs during drag
- Re-bind event handlers after DOM move
**Warning signs:** User enters item name, drags to reorder, name disappears

### Pitfall 4: Clone vs Move Confusion
**What goes wrong:** Dragging from browser removes the item from browser
**Why it happens:** Default SortableJS behavior moves elements
**How to avoid:** Use `pull: 'clone'` on browser Sortable
**Warning signs:** Browser empties as user builds collection

### Pitfall 5: Material Name Mapping
**What goes wrong:** Minecraft material names don't match CSS class names
**Why it happens:** Different naming conventions (DIAMOND_SWORD vs diamond-sword)
**How to avoid:**
```javascript
function materialToCssClass(material) {
    return material.toLowerCase().replace(/_/g, '-');
}
```
**Warning signs:** Some icons show, others don't

### Pitfall 6: Drop Zone Not Receiving Drops
**What goes wrong:** Dragging over collection slot doesn't highlight or accept drop
**Why it happens:** SortableJS groups misconfigured
**How to avoid:**
- Both source and target must have same group name
- Target must have `put: true`
- Verify with console.log in `onAdd` callback
**Warning signs:** Items snap back to original position

### Pitfall 7: Mobile/Touch Not Working
**What goes wrong:** Drag doesn't start on mobile devices
**Why it happens:** Native HTML5 DnD has no touch support
**How to avoid:** Use SortableJS which handles touch natively
**Warning signs:** Works on desktop, fails on tablet/phone

---

## Code Examples

### SortableJS: Two-List Configuration

```javascript
// Source: https://sortablejs.github.io/Sortable/
// Item browser (source only)
const browserSortable = Sortable.create(document.getElementById('item-browser-grid'), {
    group: {
        name: 'collection-items',
        pull: 'clone',
        put: false
    },
    sort: false,
    animation: 150,
    ghostClass: 'sortable-ghost',
    chosenClass: 'sortable-chosen',
    filter: '.disabled',  // Elements with .disabled won't be draggable

    onStart: function(evt) {
        document.body.classList.add('dragging');
    },

    onEnd: function(evt) {
        document.body.classList.remove('dragging');
    }
});

// Collection items (sortable + droppable)
const collectionSortable = Sortable.create(document.getElementById('items-container'), {
    group: {
        name: 'collection-items',
        pull: true,
        put: true
    },
    animation: 150,
    ghostClass: 'sortable-ghost',
    handle: '.drag-handle',  // Only drag via handle

    onAdd: function(evt) {
        // Item dropped from browser
        const clonedEl = evt.item;
        const material = clonedEl.dataset.material;

        // Convert browser item to full item row
        convertBrowserItemToFormRow(clonedEl, material);
        renumberItems();
    },

    onUpdate: function(evt) {
        // Items reordered within collection
        renumberItems();
    },

    onRemove: function(evt) {
        // Item dragged out (to trash)
        renumberItems();
    }
});
```

### Item Browser: Search Filter

```javascript
// Source: Standard DOM filtering pattern
const searchInput = document.getElementById('item-search');
let searchTimeout = null;

searchInput.addEventListener('input', function(e) {
    // Debounce search
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => {
        filterBrowserItems(e.target.value);
    }, 150);
});

function filterBrowserItems(searchTerm) {
    const term = searchTerm.toLowerCase().trim();
    const items = document.querySelectorAll('#item-browser-grid .browser-item');

    let visibleCount = 0;
    items.forEach(item => {
        const material = item.dataset.material.toLowerCase();
        const matches = term === '' || material.includes(term);
        item.style.display = matches ? '' : 'none';
        if (matches) visibleCount++;
    });

    // Update result count
    document.getElementById('search-results-count').textContent =
        `${visibleCount} items`;
}
```

### Converting Browser Item to Form Row

```javascript
// Source: Extending Phase 20 addItemRow pattern
function convertBrowserItemToFormRow(browserElement, material) {
    // Get current index
    const container = document.getElementById('items-container');
    const idx = itemRowCounter++;

    // Replace browser item HTML with full form row
    browserElement.classList.remove('browser-item');
    browserElement.classList.add('item-row');
    browserElement.dataset.index = idx;

    const cssClass = materialToCssClass(material);

    browserElement.innerHTML = `
        <div class="item-row-header">
            <span class="drag-handle">&#9776;</span>
            <span class="item-number">Item ${container.children.length}</span>
            <button type="button" class="btn-remove-item" onclick="removeItemRow(this)">&times;</button>
        </div>
        <div class="item-row-content">
            <div class="item-icon">
                <span class="mc-icon mc-icon-${cssClass}"></span>
            </div>
            <div class="item-fields">
                <div class="form-row">
                    <div class="form-group">
                        <label>ID</label>
                        <input type="text" name="item-id-${idx}" placeholder="item_id"
                               value="${material.toLowerCase()}">
                    </div>
                    <div class="form-group">
                        <label>Name</label>
                        <input type="text" name="item-name-${idx}" placeholder="Item Name"
                               value="${formatMaterialName(material)}">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Material</label>
                        <input type="text" name="item-material-${idx}" value="${material}" readonly>
                    </div>
                    <div class="form-group">
                        <label>Weight</label>
                        <input type="number" name="item-weight-${idx}" min="1" value="10">
                    </div>
                </div>
                <div class="form-group">
                    <label>Lore</label>
                    <textarea name="item-lore-${idx}" rows="2" placeholder="Line 1&#10;Line 2"></textarea>
                </div>
            </div>
        </div>
    `;
}

function formatMaterialName(material) {
    // DIAMOND_SWORD -> Diamond Sword
    return material.split('_')
        .map(word => word.charAt(0) + word.slice(1).toLowerCase())
        .join(' ');
}

function materialToCssClass(material) {
    // DIAMOND_SWORD -> diamond-sword (for CSS sprite class)
    return material.toLowerCase().replace(/_/g, '-');
}
```

### Backend: Materials Endpoint

```java
// Add to CollectionsController.register()
app.get("/api/materials", this::listMaterials);

// Implementation
private void listMaterials(Context ctx) {
    // No need for main thread - Material enum is static
    List<String> materials = Arrays.stream(Material.values())
        .filter(m -> !m.isLegacy())
        .filter(Material::isItem)
        .map(Material::name)
        .sorted()
        .toList();

    ctx.json(materials);
}
```

### CSS: Visual Builder Layout

```css
/* Two-panel layout for visual builder */
.form-section.items-section {
    display: flex;
    gap: 1rem;
}

.collection-items-panel {
    flex: 2;
    min-width: 300px;
}

.item-browser-panel {
    flex: 1;
    min-width: 250px;
    background: var(--bg-tertiary);
    border-radius: 8px;
    padding: 1rem;
    position: sticky;
    top: 1rem;
    max-height: calc(100vh - 2rem);
    display: flex;
    flex-direction: column;
}

.item-browser-search {
    margin-bottom: 1rem;
}

.item-browser-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(48px, 1fr));
    gap: 4px;
    overflow-y: auto;
    flex: 1;
}

.browser-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 4px;
    cursor: grab;
    border-radius: 4px;
    background: var(--bg-secondary);
    transition: all 0.15s;
}

.browser-item:hover {
    transform: scale(1.1);
    box-shadow: 0 2px 8px rgba(0, 217, 255, 0.3);
}

.browser-item .mc-icon {
    width: 32px;
    height: 32px;
}

/* Drag feedback */
.sortable-ghost {
    opacity: 0.4;
}

.sortable-chosen {
    background: var(--accent-color);
}

body.dragging .items-container {
    background: rgba(0, 217, 255, 0.1);
    border: 2px dashed var(--accent-color);
}

/* Item row with drag handle */
.item-row {
    display: flex;
    flex-direction: column;
    background: var(--bg-tertiary);
    border: 1px solid var(--border-color);
    border-radius: 6px;
    margin-bottom: 0.5rem;
}

.drag-handle {
    cursor: grab;
    padding: 0 0.5rem;
    color: var(--text-muted);
}

.item-row-content {
    display: flex;
    gap: 1rem;
    padding: 1rem;
}

.item-icon {
    flex-shrink: 0;
}

.item-fields {
    flex: 1;
}

/* Drop zone indicator */
.items-container.drop-target {
    background: rgba(0, 217, 255, 0.05);
    border: 2px dashed var(--accent-color);
}

.items-container.drop-target::after {
    content: 'Drop item here';
    display: block;
    text-align: center;
    padding: 2rem;
    color: var(--text-muted);
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| jQuery UI Sortable | SortableJS | 2018+ | No jQuery dependency, touch support |
| Text input for material | Visual icon grid + drag | Phase 21 | Much better UX, fewer typos |
| Manual PNG sprites | CSS sprite sheets (webp) | 2022+ | Smaller files, easier maintenance |
| Individual icon files | Single sprite atlas | Always | Fewer HTTP requests, better performance |

**Current best practices:**
- Use CSS sprite sheets (webp format for 30% smaller than PNG)
- Implement search/filter before showing all 1400+ items
- Use SortableJS for cross-platform drag-and-drop
- Virtual scrolling for large item grids

---

## Open Questions

1. **Sprite Sheet Source**
   - What we know: minecraft-items-css parses Minecraft Wiki sprites
   - What's unclear: Is the sprite sheet current for 1.21.x?
   - Recommendation: Verify sprite sheet includes recent items; may need to generate updated sprites or self-host

2. **Biome/Dimension Autocomplete**
   - What we know: Collections use biome names from Minecraft Biome enum
   - What's unclear: Should we provide autocomplete for biome names?
   - Recommendation: Add /api/biomes and /api/dimensions endpoints for autocomplete data

3. **Icon Fallback**
   - What we know: Some materials may not have icons in sprite sheet
   - What's unclear: How many items are missing?
   - Recommendation: Use a generic fallback icon (BARRIER or PAPER) for missing sprites

4. **Mobile Responsiveness**
   - What we know: SortableJS supports touch
   - What's unclear: Is two-panel layout usable on mobile?
   - Recommendation: Switch to stacked layout on narrow screens, show browser as modal/overlay

---

## Sources

### Primary (HIGH confidence)
- [MDN HTML Drag and Drop API](https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API) - Core DnD events and patterns
- [SortableJS GitHub](https://github.com/SortableJS/Sortable) - Group configuration, events
- [jsDelivr SortableJS](https://www.jsdelivr.com/package/npm/sortablejs) - CDN URL, version 1.15.6
- [Paper API Material Javadocs](https://jd.papermc.io/paper/1.21.4/org/bukkit/Material.html) - Material enum reference
- Existing codebase: Phase 20 form patterns, app.js, admin.css

### Secondary (MEDIUM confidence)
- [minecraft-items-css GitHub](https://github.com/1e4/minecraft-items-css) - CSS sprite approach
- [DigitalOcean DnD Tutorial](https://www.digitalocean.com/community/tutorials/js-drag-and-drop-vanilla-js) - Vanilla JS patterns
- [Minecraft Wiki Texture Atlas](https://minecraft.wiki/w/Texture_atlas) - Sprite sheet concepts

### Tertiary (LOW confidence)
- Various drag-and-drop tutorials for visual feedback patterns

---

## Metadata

**Confidence breakdown:**
- Drag-and-drop library: HIGH - SortableJS well-documented, widely used
- CSS sprites: MEDIUM - minecraft-items-css community maintained, may need version verification
- Backend API: HIGH - Simple Material enum exposure, no complexity
- Two-panel layout: HIGH - Standard pattern, CSS flexbox
- Touch support: HIGH - SortableJS built-in
- Performance optimization: MEDIUM - Virtual scrolling may need tuning

**Research date:** 2026-01-23
**Valid until:** 2026-03-23 (60 days - stable patterns)
