# Phase 22: Visual Builder Enhancements - Research

**Researched:** 2026-01-23
**Domain:** Browser-based MiniMessage preview, collection templates, weight validation UI
**Confidence:** MEDIUM

## Summary

Phase 22 enhances the web-based visual builder from Phase 21 with five key features: MiniMessage formatted text preview, collection templates, weight validation, percentage-based weight adjustment, and visual percentage display. The research identifies a mature JavaScript library (minimessage-js) for client-side MiniMessage rendering, standard patterns for weight sum validation with visual feedback, and auto-adjustment algorithms for percentage-based weight distribution. Collection templates can be implemented as static JSON objects with pre-filled biome/dimension/item configurations based on existing collection patterns in the codebase.

**Key findings:**
- minimessage-js library provides browser-ready MiniMessage→HTML rendering with styling
- Weight validation requires sum calculation with visual warning UI when total ≠ 100%
- Percentage adjustment needs proportional redistribution algorithm across remaining items
- Templates should follow existing collection patterns (forest, ocean, nether, cave, end, desert biomes)

**Primary recommendation:** Use minimessage-js for live preview, implement event-driven weight validation with `oninput` handlers, create templates from existing collection YAML patterns.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| minimessage-js | ^1.1 | MiniMessage→HTML rendering | Official-quality JS parser with HTML serializer, browser-ready |
| Browser fetch API | Native | Template loading | No additional dependency needed |
| Native input events | HTML5 | Live validation | `oninput` event for real-time recalculation |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| CSS linear-gradient | Native | Gradient preview | MiniMessage gradient tags visualization |
| contenteditable | HTML5 | Live text editing | Optional for advanced preview modes |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| minimessage-js | Server-side rendering | Requires API round-trip, increases latency |
| Custom parser | minimessage-js | Custom = missing edge cases, maintenance burden |
| onChange events | oninput events | onChange fires on blur, oninput fires immediately |

**Installation:**
```html
<!-- Via CDN (browser) -->
<script src="https://unpkg.com/minimessage-js@^1.1"></script>

<!-- Or NPM (if bundling) -->
npm install minimessage-js
```

## Architecture Patterns

### Recommended Project Structure
```
src/main/resources/web/
├── js/
│   ├── app.js              # Main app (existing, ~877 lines)
│   ├── minimessage.js      # MiniMessage preview handler (NEW)
│   ├── templates.js        # Template data and loader (NEW)
│   └── weight-validator.js # Weight validation logic (NEW)
├── data/
│   └── templates.json      # Collection templates (NEW)
└── index.html              # Already has form structure
```

### Pattern 1: MiniMessage Live Preview
**What:** Convert MiniMessage syntax to styled HTML in real-time as user types
**When to use:** Any text input that supports MiniMessage (name, lore, completion message)
**Example:**
```javascript
// Source: minimessage-js GitHub + research
import MiniMessage from 'minimessage-js';

function attachMiniMessagePreview(inputElement, previewElement) {
  const mm = MiniMessage.miniMessage();

  inputElement.addEventListener('input', function(e) {
    const text = e.target.value;
    try {
      const component = mm.deserialize(text);
      // Render to preview div
      mm.toHTML(component, previewElement);
    } catch (err) {
      // Invalid syntax - show error or original text
      previewElement.textContent = text;
      previewElement.style.color = '#ff4444';
    }
  });
}
```

### Pattern 2: Weight Sum Validation
**What:** Calculate total weight, show warning if ≠ 100%, display percentage for each item
**When to use:** Any time item weights can be edited
**Example:**
```javascript
// Source: Research + HTML5 form validation patterns
function validateWeights() {
  const weightInputs = document.querySelectorAll('[name^="item-weight-"]');
  const weights = Array.from(weightInputs).map(input => parseInt(input.value) || 0);
  const totalWeight = weights.reduce((sum, w) => sum + w, 0);

  const warningEl = document.getElementById('weight-warning');

  if (totalWeight === 100) {
    warningEl.classList.add('hidden');
  } else if (totalWeight === 0) {
    warningEl.textContent = 'Add items to see weight distribution';
    warningEl.className = 'weight-info';
  } else {
    warningEl.textContent = `⚠ Weights sum to ${totalWeight}% (should be 100%)`;
    warningEl.className = 'weight-warning';
  }

  // Update percentage display for each item
  weightInputs.forEach((input, idx) => {
    const weight = weights[idx];
    const percentage = totalWeight > 0 ? ((weight / totalWeight) * 100).toFixed(1) : 0;
    const percentageEl = input.closest('.item-row').querySelector('.weight-percentage');
    percentageEl.textContent = `${percentage}% drop chance`;
  });
}

// Attach to all weight inputs
document.querySelectorAll('[name^="item-weight-"]').forEach(input => {
  input.addEventListener('input', validateWeights);
});
```

### Pattern 3: Percentage-Based Weight Adjustment
**What:** Admin enters percentage directly, other weights auto-adjust proportionally
**When to use:** When admin wants to set exact drop chance instead of relative weight
**Example:**
```javascript
// Source: Research on weighted distribution + proportional adjustment
function setItemPercentage(itemIndex, targetPercentage) {
  const weightInputs = Array.from(document.querySelectorAll('[name^="item-weight-"]'));
  const currentWeights = weightInputs.map(input => parseInt(input.value) || 0);
  const currentTotal = currentWeights.reduce((sum, w) => sum + w, 0);

  // Calculate target weight (out of 100)
  const targetWeight = targetPercentage;

  // Calculate remaining percentage for other items
  const remainingPercentage = 100 - targetPercentage;

  // Get sum of other items' current weights
  const otherWeightsSum = currentTotal - currentWeights[itemIndex];

  if (otherWeightsSum === 0) {
    // No other items - just set this one
    weightInputs[itemIndex].value = targetWeight;
    return;
  }

  // Distribute remaining percentage proportionally
  weightInputs.forEach((input, idx) => {
    if (idx === itemIndex) {
      input.value = targetWeight;
    } else {
      const proportion = currentWeights[idx] / otherWeightsSum;
      const newWeight = Math.round(remainingPercentage * proportion);
      input.value = newWeight;
    }
  });

  // Trigger validation to update display
  validateWeights();
}
```

### Pattern 4: Collection Templates
**What:** Pre-configured collection structures with typical biomes, items, and settings
**When to use:** "New from template" button - faster than starting from scratch
**Example:**
```javascript
// Source: Research + existing collection YAML analysis
const templates = {
  forest: {
    name: "Forest Collection",
    tier: "COMMON",
    biomes: ["FOREST", "BIRCH_FOREST", "OLD_GROWTH_BIRCH_FOREST"],
    dimensions: ["NORMAL"],
    minY: 60,
    maxY: 100,
    items: [
      { id: "item_1", name: "Forest Item 1", material: "OAK_LOG", weight: 20 },
      { id: "item_2", name: "Forest Item 2", material: "MOSS_BLOCK", weight: 20 },
      { id: "item_3", name: "Forest Item 3", material: "JUNGLE_LEAVES", weight: 15 }
    ],
    experience: 150
  },
  ocean: {
    name: "Ocean Collection",
    tier: "COMMON",
    biomes: ["OCEAN", "DEEP_OCEAN", "COLD_OCEAN", "WARM_OCEAN"],
    dimensions: ["NORMAL"],
    minY: 20,
    maxY: 62,
    items: [
      { id: "item_1", name: "Ocean Item 1", material: "FIRE_CORAL", weight: 18 },
      { id: "item_2", name: "Ocean Item 2", material: "SPONGE", weight: 16 }
    ],
    experience: 175
  },
  nether: {
    name: "Nether Collection",
    tier: "COMMON",
    biomes: ["NETHER_WASTES", "CRIMSON_FOREST", "WARPED_FOREST"],
    dimensions: ["NETHER"],
    minY: 30,
    maxY: 120,
    items: [
      { id: "item_1", name: "Nether Item 1", material: "NETHERRACK", weight: 18 },
      { id: "item_2", name: "Nether Item 2", material: "GOLD_NUGGET", weight: 18 }
    ],
    experience: 200
  },
  cave: {
    name: "Cave Collection",
    tier: "COMMON",
    biomes: ["DRIPSTONE_CAVES", "LUSH_CAVES"],
    dimensions: ["NORMAL"],
    maxY: 50,
    items: [
      { id: "item_1", name: "Cave Item 1", material: "POINTED_DRIPSTONE", weight: 18 },
      { id: "item_2", name: "Cave Item 2", material: "CALCITE", weight: 16 }
    ],
    experience: 175
  },
  end: {
    name: "End Collection",
    tier: "COMMON",
    biomes: ["THE_END"],
    dimensions: ["THE_END"],
    minY: 0,
    maxY: 128,
    items: [
      { id: "item_1", name: "End Item 1", material: "END_STONE", weight: 18 },
      { id: "item_2", name: "End Item 2", material: "ENDER_PEARL", weight: 16 }
    ],
    experience: 225
  },
  desert: {
    name: "Desert Collection",
    tier: "COMMON",
    biomes: ["DESERT"],
    dimensions: ["NORMAL"],
    minY: 60,
    maxY: 100,
    items: [
      { id: "item_1", name: "Desert Item 1", material: "SAND", weight: 20 },
      { id: "item_2", name: "Desert Item 2", material: "SANDSTONE", weight: 18 }
    ],
    experience: 150
  }
};

function loadTemplate(templateName) {
  const template = templates[templateName];
  if (!template) return;

  // Populate form fields
  document.getElementById('form-name').value = template.name;
  document.getElementById('form-tier').value = template.tier;
  document.getElementById('form-biomes').value = template.biomes.join(', ');

  // Set dimensions
  document.getElementById('dim-normal').checked = template.dimensions.includes('NORMAL');
  document.getElementById('dim-nether').checked = template.dimensions.includes('NETHER');
  document.getElementById('dim-end').checked = template.dimensions.includes('THE_END');

  // Set Y levels
  document.getElementById('form-min-y').value = template.minY || -64;
  document.getElementById('form-max-y').value = template.maxY || 320;

  // Add items
  document.getElementById('items-container').innerHTML = '';
  template.items.forEach(item => addItemRow(item));

  // Set experience
  document.getElementById('form-reward-xp').value = template.experience;

  showToast(`Template "${templateName}" loaded`, 'success');
}
```

### Anti-Patterns to Avoid
- **Server-side MiniMessage preview:** Don't send each keystroke to server for rendering - use client-side library
- **Recalculating on form submit only:** Weight validation should be live, not on submit
- **Hardcoded percentage adjustment:** Don't use fixed redistribution - use proportional algorithm
- **Template loading from YAML files:** Don't read actual collection YAMLs as templates - use simplified JSON structures

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| MiniMessage parsing | Custom regex parser | minimessage-js | MiniMessage has complex nesting, gradients, hover events, scores, NBT - edge cases will break custom parser |
| Color gradient rendering | Canvas drawing or image generation | CSS linear-gradient with spans | Browser-native, performant, works with text selection |
| Weight percentage calculation | Manual loop with rounding | Reduce + proportional distribution | Rounding errors accumulate, need to handle edge cases (0 total, single item) |
| Form validation feedback | Alert() or console.log | HTML5 validation UI + aria-invalid | Accessibility requirements, screen reader support, consistent UX |

**Key insight:** MiniMessage specification is complex (supports hover, click, keybind, translation, selector, score, NBT tags) - parsing correctly requires understanding full spec. minimessage-js already handles all edge cases.

## Common Pitfalls

### Pitfall 1: MiniMessage Parsing Errors Breaking UI
**What goes wrong:** Invalid MiniMessage syntax (unclosed tags, typos) causes parser exceptions, breaking preview
**Why it happens:** User typing in real-time creates temporarily invalid states
**How to avoid:** Wrap parser in try-catch, show original text on parse failure, optionally highlight error position
**Warning signs:** Preview disappears while typing, console shows uncaught exceptions

### Pitfall 2: Weight Sum Not Accounting for Dynamic Item Addition/Removal
**What goes wrong:** Adding/removing items doesn't recalculate validation, stale percentage displays
**Why it happens:** Validation only attached to existing inputs, not new ones
**How to avoid:** Re-attach validation listeners when DOM changes (after addItemRow, removeItemRow), call validateWeights() after any item list mutation
**Warning signs:** Weight warning shows "100%" but items are added/removed, percentages don't update

### Pitfall 3: Percentage Adjustment Creating Infinite Loops
**What goes wrong:** Setting percentage triggers input event, which triggers adjustment, which triggers input event...
**Why it happens:** Input event listeners fire when JavaScript sets input.value
**How to avoid:** Use flag to disable validation during programmatic updates, or temporarily remove event listeners
**Warning signs:** Browser freezes, stack overflow errors, rapid UI flickering

### Pitfall 4: Rounding Errors in Weight Distribution
**What goes wrong:** Proportional distribution creates weights that sum to 99% or 101% due to rounding
**Why it happens:** Each Math.round() operation loses precision
**How to avoid:** Calculate all weights, adjust largest weight by difference to ensure exact 100% sum
**Warning signs:** Weights show "99% total" or "101% total" after percentage adjustment

### Pitfall 5: Template ID Collision
**What goes wrong:** Loading template doesn't clear ID field, creating duplicate collection IDs
**Why it happens:** Templates populate all fields but ID should be unique per collection
**How to avoid:** Clear form before loading template, leave ID field empty for user to fill, or generate unique ID
**Warning signs:** 409 Conflict errors when creating collection from template, "Collection already exists" messages

### Pitfall 6: CSS Gradient Text Not Showing
**What goes wrong:** Gradient colors defined but text shows solid color
**Why it happens:** Missing `-webkit-background-clip: text` or `color: transparent` properties
**How to avoid:** Use complete CSS pattern: `background: linear-gradient(...); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;`
**Warning signs:** Gradient works in DevTools but not in browser, text shows default color

## Code Examples

Verified patterns from official sources:

### MiniMessage to HTML Rendering
```javascript
// Source: https://github.com/WasabiThumb/minimessage-js
const MiniMessage = window.MiniMessage; // Global from CDN

// Initialize parser
const mm = MiniMessage.miniMessage();

// Parse and render
function renderMiniMessage(text, targetElement) {
  try {
    const component = mm.deserialize(text);
    // Renders directly to DOM element (efficient)
    mm.toHTML(component, targetElement);
  } catch (error) {
    // Fallback for invalid syntax
    targetElement.textContent = text;
    targetElement.classList.add('parse-error');
  }
}

// Example: Preview lore line
const loreInput = document.querySelector('[name="item-lore"]');
const lorePreview = document.getElementById('lore-preview');
loreInput.addEventListener('input', (e) => {
  renderMiniMessage(e.target.value, lorePreview);
});
```

### CSS Gradient Text (for MiniMessage Gradient Preview)
```css
/* Source: https://fossheim.io/writing/posts/css-text-gradient/ */
.minimessage-gradient {
  background: linear-gradient(135deg, #43b8ff, #4ef8ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  display: inline-block; /* Required for gradient to work on inline elements */
}
```

### Weight Validation with Visual Feedback
```javascript
// Source: Research + MDN form validation patterns
function createWeightValidator() {
  const container = document.getElementById('items-container');
  const warningEl = document.createElement('div');
  warningEl.id = 'weight-validation';
  warningEl.className = 'validation-info';
  container.after(warningEl);

  function validate() {
    const inputs = container.querySelectorAll('[name^="item-weight-"]');
    const total = Array.from(inputs).reduce((sum, input) => {
      return sum + (parseInt(input.value) || 0);
    }, 0);

    // Update each item's percentage display
    inputs.forEach(input => {
      const weight = parseInt(input.value) || 0;
      const percentage = total > 0 ? ((weight / total) * 100).toFixed(1) : 0;
      const row = input.closest('.item-row');
      let percentageSpan = row.querySelector('.weight-percentage');
      if (!percentageSpan) {
        percentageSpan = document.createElement('span');
        percentageSpan.className = 'weight-percentage';
        input.after(percentageSpan);
      }
      percentageSpan.textContent = `(${percentage}% drop chance)`;
    });

    // Update validation message
    if (total === 0) {
      warningEl.textContent = 'Add items and set weights to see distribution';
      warningEl.className = 'validation-info';
    } else if (total === 100) {
      warningEl.textContent = '✓ Weights correctly sum to 100%';
      warningEl.className = 'validation-success';
    } else {
      warningEl.textContent = `⚠ Weights sum to ${total}% (should be 100%)`;
      warningEl.className = 'validation-warning';
      warningEl.setAttribute('aria-live', 'polite'); // Accessibility
    }
  }

  // Attach to container for event delegation (handles dynamic items)
  container.addEventListener('input', (e) => {
    if (e.target.matches('[name^="item-weight-"]')) {
      validate();
    }
  });

  return { validate };
}
```

### Proportional Weight Redistribution
```javascript
// Source: Research on weighted distribution algorithms
function adjustWeightByPercentage(itemIndex, targetPercent) {
  const inputs = Array.from(document.querySelectorAll('[name^="item-weight-"]'));
  if (inputs.length === 0) return;

  // Get current weights
  const weights = inputs.map(i => parseInt(i.value) || 0);
  const currentTotal = weights.reduce((a, b) => a + b, 0);

  // Target weight for this item
  const targetWeight = Math.round(targetPercent);

  // Remaining to distribute
  const remaining = 100 - targetWeight;

  if (inputs.length === 1) {
    // Only one item - set to 100
    inputs[0].value = 100;
    return;
  }

  // Get weights of other items
  const otherTotal = currentTotal - weights[itemIndex];

  if (otherTotal === 0) {
    // Other items have no weight - distribute evenly
    const evenSplit = Math.floor(remaining / (inputs.length - 1));
    inputs.forEach((input, idx) => {
      if (idx === itemIndex) {
        input.value = targetWeight;
      } else {
        input.value = evenSplit;
      }
    });
    // Adjust last item to hit exactly 100
    const lastIdx = inputs.length - 1 === itemIndex ? inputs.length - 2 : inputs.length - 1;
    inputs[lastIdx].value = parseInt(inputs[lastIdx].value) + (remaining - evenSplit * (inputs.length - 1));
  } else {
    // Distribute proportionally based on current weights
    let distributed = 0;
    inputs.forEach((input, idx) => {
      if (idx === itemIndex) {
        input.value = targetWeight;
      } else {
        const proportion = weights[idx] / otherTotal;
        const newWeight = Math.round(remaining * proportion);
        input.value = newWeight;
        distributed += newWeight;
      }
    });

    // Fix rounding error by adjusting largest weight
    const difference = remaining - distributed;
    if (difference !== 0) {
      const largestOtherIdx = weights
        .map((w, i) => i === itemIndex ? -1 : w)
        .indexOf(Math.max(...weights.filter((_, i) => i !== itemIndex)));
      inputs[largestOtherIdx].value = parseInt(inputs[largestOtherIdx].value) + difference;
    }
  }

  // Trigger validation update
  inputs[0].dispatchEvent(new Event('input', { bubbles: true }));
}
```

### Template Selector UI
```html
<!-- Source: Research on form template patterns -->
<div class="template-selector" style="display: none;" id="template-selector">
  <h3>Start from Template</h3>
  <div class="template-grid">
    <button type="button" class="template-btn" data-template="forest">
      <span class="template-icon">🌲</span>
      <span class="template-name">Forest</span>
    </button>
    <button type="button" class="template-btn" data-template="ocean">
      <span class="template-icon">🌊</span>
      <span class="template-name">Ocean</span>
    </button>
    <button type="button" class="template-btn" data-template="nether">
      <span class="template-icon">🔥</span>
      <span class="template-name">Nether</span>
    </button>
    <button type="button" class="template-btn" data-template="cave">
      <span class="template-icon">⛏️</span>
      <span class="template-name">Cave</span>
    </button>
    <button type="button" class="template-btn" data-template="end">
      <span class="template-icon">🟣</span>
      <span class="template-name">End</span>
    </button>
    <button type="button" class="template-btn" data-template="desert">
      <span class="template-icon">🏜️</span>
      <span class="template-name">Desert</span>
    </button>
  </div>
  <button type="button" class="btn-secondary" id="template-cancel">Start Blank</button>
</div>

<style>
.template-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin: 1rem 0;
}

.template-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 1.5rem;
  background: #0f3460;
  border: 2px solid #16213e;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.template-btn:hover {
  background: #16213e;
  border-color: #00d9ff;
  transform: translateY(-2px);
}

.template-icon {
  font-size: 3rem;
  margin-bottom: 0.5rem;
}

.template-name {
  color: #eee;
  font-weight: 500;
}
</style>

<script>
document.querySelectorAll('.template-btn').forEach(btn => {
  btn.addEventListener('click', function() {
    const templateName = this.dataset.template;
    loadTemplate(templateName);
    document.getElementById('template-selector').style.display = 'none';
    document.getElementById('view-form').style.display = 'block';
  });
});
</script>
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Server-side MiniMessage rendering | Client-side minimessage-js | 2020+ | Eliminates latency, enables live preview without API calls |
| Fixed weight inputs only | Percentage + weight dual input | Modern loot systems | Admin can think in drop chance (%) instead of relative weights |
| Manual template copying | UI-based template system | Modern CMS patterns | Reduces setup time from minutes to seconds |
| Static form validation | Live validation with visual feedback | HTML5 + modern JS | Immediate feedback prevents submission errors |

**Deprecated/outdated:**
- execCommand for contenteditable formatting: Deprecated, use modern contenteditable approach with DOM manipulation
- onChange for number inputs: Use `oninput` for immediate feedback instead of waiting for blur

## Open Questions

Things that couldn't be fully resolved:

1. **MiniMessage hover/click event preview**
   - What we know: minimessage-js parses hover/click events and stores as data attributes
   - What's unclear: Should preview show interactive tooltips, or just indicate "has hover" with icon?
   - Recommendation: Phase 22 focuses on text formatting preview (color, gradient, style), defer interactive preview to future enhancement

2. **Weight adjustment UX - dual input or modal?**
   - What we know: Need both weight input (current) and percentage input (new)
   - What's unclear: Show both inputs side-by-side, or percentage in modal/popover?
   - Recommendation: Add percentage input next to weight input with "(or X%)" label, auto-sync both directions

3. **Template customization depth**
   - What we know: Templates should include biomes, dimensions, Y-levels, sample items
   - What's unclear: Should templates include lore text, or just material/name?
   - Recommendation: Keep templates minimal (material + placeholder name), let admin customize lore - avoids overly prescriptive templates

4. **MiniMessage syntax help UI**
   - What we know: MiniMessage has complex tag syntax that admins may not know
   - What's unclear: Should builder include syntax reference, examples, or tag inserter buttons?
   - Recommendation: Add "?" help icon next to MiniMessage inputs, links to PaperMC docs, future phase could add tag inserter

## Sources

### Primary (HIGH confidence)
- minimessage-js GitHub: https://github.com/WasabiThumb/minimessage-js - Installation, API, toHTML method
- MDN Input Events: https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input/number - oninput vs onChange behavior
- MDN Client-side Form Validation: https://developer.mozilla.org/en-US/docs/Learn_web_development/Extensions/Forms/Form_validation - Validation UI patterns

### Secondary (MEDIUM confidence)
- PaperMC MiniMessage Format Docs: https://docs.papermc.io/adventure/minimessage/format/ - Tag syntax reference
- Adventure Documentation: https://docs.advntr.dev/minimessage/format.html - MiniMessage specification
- CSS Gradient Text Tutorial: https://fossheim.io/writing/posts/css-text-gradient/ - Gradient rendering technique
- PhatLoots Plugin: https://www.spigotmc.org/resources/phatloots-advanced-loot-tables-plugin.68925/ - Loot collection patterns

### Tertiary (LOW confidence - WebSearch only)
- React UI Libraries 2026: https://www.builder.io/blog/react-component-libraries-2026 - Modern UI component patterns
- JavaScript Weight Calculation Examples: Various blog posts and tutorials - General weighted distribution algorithms

### Codebase Analysis (HIGH confidence)
- Existing collection YAMLs: 55 COMMON tier, 6 UNCOMMON tier, 5 RARE tier collections analyzed
- Phase 21 implementation: SortableJS drag-drop, 150ms debounce, material browser with search
- Weight field pattern: Currently uses `<input type="number" min="1">` with no validation

## Metadata

**Confidence breakdown:**
- MiniMessage preview: MEDIUM - Library exists and works, but browser integration needs testing
- Weight validation: HIGH - Standard HTML5 patterns, straightforward implementation
- Percentage adjustment: MEDIUM - Algorithm is known, rounding edge cases need careful handling
- Templates: HIGH - Existing collections provide clear patterns, structure is well-defined

**Research date:** 2026-01-23
**Valid until:** 30 days (stable domain - libraries unlikely to change rapidly)

**Research completeness:**
- Standard stack: ✓ Complete
- Architecture patterns: ✓ Complete with code examples
- Common pitfalls: ✓ 6 pitfalls identified with solutions
- Don't hand-roll: ✓ Key areas identified
- Open questions: ✓ 4 questions flagged for planner decisions
