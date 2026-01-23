// Connection heartbeat
const HEARTBEAT_INTERVAL = 30000; // 30 seconds
let heartbeatTimer = null;

// Form state
let currentEditId = null; // null for create, ID for edit
let itemRowCounter = 0;
let deleteTargetId = null;

// Item browser state
let allMaterials = [];
let browserSortable = null;
let collectionSortable = null;

// Weight adjustment state
let isAdjustingWeights = false;

// Collection templates
const templates = {
    forest: {
        name: "Forest Collection",
        tier: "COMMON",
        icon: "OAK_SAPLING",
        biomes: ["FOREST", "BIRCH_FOREST", "FLOWER_FOREST"],
        dimensions: ["NORMAL"],
        minY: 60,
        maxY: 120,
        rewardXp: 100,
        items: [
            { id: "oak_log", name: "Oak Log", material: "OAK_LOG", weight: 15 },
            { id: "birch_log", name: "Birch Log", material: "BIRCH_LOG", weight: 15 },
            { id: "oak_sapling", name: "Oak Sapling", material: "OAK_SAPLING", weight: 10 },
            { id: "fern", name: "Fern", material: "FERN", weight: 8 },
            { id: "dandelion", name: "Dandelion", material: "DANDELION", weight: 8 }
        ]
    },
    ocean: {
        name: "Ocean Collection",
        tier: "UNCOMMON",
        icon: "TROPICAL_FISH",
        biomes: ["OCEAN", "DEEP_OCEAN", "WARM_OCEAN", "LUKEWARM_OCEAN", "COLD_OCEAN"],
        dimensions: ["NORMAL"],
        minY: -64,
        maxY: 63,
        rewardXp: 150,
        items: [
            { id: "tropical_fish", name: "Tropical Fish", material: "TROPICAL_FISH", weight: 12 },
            { id: "cod", name: "Cod", material: "COD", weight: 12 },
            { id: "kelp", name: "Kelp", material: "KELP", weight: 10 },
            { id: "seagrass", name: "Seagrass", material: "SEAGRASS", weight: 10 },
            { id: "prismarine_shard", name: "Prismarine Shard", material: "PRISMARINE_SHARD", weight: 8 }
        ]
    },
    nether: {
        name: "Nether Collection",
        tier: "RARE",
        icon: "NETHERRACK",
        biomes: ["NETHER_WASTES", "CRIMSON_FOREST", "WARPED_FOREST", "BASALT_DELTAS", "SOUL_SAND_VALLEY"],
        dimensions: ["NETHER"],
        minY: 0,
        maxY: 128,
        rewardXp: 250,
        items: [
            { id: "netherrack", name: "Netherrack", material: "NETHERRACK", weight: 15 },
            { id: "soul_sand", name: "Soul Sand", material: "SOUL_SAND", weight: 12 },
            { id: "crimson_fungus", name: "Crimson Fungus", material: "CRIMSON_FUNGUS", weight: 10 },
            { id: "warped_fungus", name: "Warped Fungus", material: "WARPED_FUNGUS", weight: 10 },
            { id: "blaze_rod", name: "Blaze Rod", material: "BLAZE_ROD", weight: 5 }
        ]
    },
    cave: {
        name: "Cave Collection",
        tier: "COMMON",
        icon: "COAL",
        biomes: ["DRIPSTONE_CAVES", "LUSH_CAVES", "DEEP_DARK"],
        dimensions: ["NORMAL"],
        minY: -64,
        maxY: 0,
        rewardXp: 120,
        items: [
            { id: "coal", name: "Coal", material: "COAL", weight: 15 },
            { id: "iron_ore", name: "Iron Ore", material: "IRON_ORE", weight: 12 },
            { id: "dripstone", name: "Pointed Dripstone", material: "POINTED_DRIPSTONE", weight: 10 },
            { id: "glow_berries", name: "Glow Berries", material: "GLOW_BERRIES", weight: 8 },
            { id: "amethyst", name: "Amethyst Shard", material: "AMETHYST_SHARD", weight: 6 }
        ]
    },
    end: {
        name: "End Collection",
        tier: "EPIC",
        icon: "END_STONE",
        biomes: ["THE_END", "END_HIGHLANDS", "END_MIDLANDS", "SMALL_END_ISLANDS"],
        dimensions: ["THE_END"],
        minY: 0,
        maxY: 256,
        rewardXp: 500,
        items: [
            { id: "end_stone", name: "End Stone", material: "END_STONE", weight: 15 },
            { id: "chorus_fruit", name: "Chorus Fruit", material: "CHORUS_FRUIT", weight: 12 },
            { id: "ender_pearl", name: "Ender Pearl", material: "ENDER_PEARL", weight: 10 },
            { id: "shulker_shell", name: "Shulker Shell", material: "SHULKER_SHELL", weight: 5 },
            { id: "dragon_breath", name: "Dragon's Breath", material: "DRAGON_BREATH", weight: 3 }
        ]
    },
    desert: {
        name: "Desert Collection",
        tier: "COMMON",
        icon: "SAND",
        biomes: ["DESERT", "BADLANDS", "SAVANNA"],
        dimensions: ["NORMAL"],
        minY: 60,
        maxY: 100,
        rewardXp: 100,
        items: [
            { id: "sand", name: "Sand", material: "SAND", weight: 15 },
            { id: "cactus", name: "Cactus", material: "CACTUS", weight: 12 },
            { id: "dead_bush", name: "Dead Bush", material: "DEAD_BUSH", weight: 10 },
            { id: "terracotta", name: "Terracotta", material: "TERRACOTTA", weight: 8 },
            { id: "rabbit_hide", name: "Rabbit Hide", material: "RABBIT_HIDE", weight: 6 }
        ]
    }
};

function loadTemplate(templateName) {
    const template = templates[templateName];
    if (!template) return;

    // Clear the form
    resetForm();

    // Generate unique ID suggestion (template name + timestamp suffix)
    const timestamp = Date.now().toString().slice(-4);
    document.getElementById('form-id').value = templateName + '_' + timestamp;

    // Populate basic info
    document.getElementById('form-name').value = template.name;
    document.getElementById('form-tier').value = template.tier;
    document.getElementById('form-icon').value = template.icon;

    // Populate spawn conditions
    document.getElementById('form-biomes').value = template.biomes.join(', ');

    // Set dimension checkboxes
    document.getElementById('dim-normal').checked = template.dimensions.includes('NORMAL');
    document.getElementById('dim-nether').checked = template.dimensions.includes('NETHER');
    document.getElementById('dim-end').checked = template.dimensions.includes('THE_END');

    // Set Y levels
    document.getElementById('form-min-y').value = template.minY;
    document.getElementById('form-max-y').value = template.maxY;

    // Set reward experience
    document.getElementById('form-reward-xp').value = template.rewardXp;

    // Clear items container and add template items
    document.getElementById('items-container').innerHTML = '';
    itemRowCounter = 0;
    template.items.forEach(item => {
        addItemRow(item);
    });

    showToast('Template loaded', 'success');
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    // Initialize MiniMessage parser
    initMiniMessageParser();

    // Attach preview to name and message fields
    attachMiniMessagePreview('form-name', 'preview-name');
    attachMiniMessagePreview('form-reward-message', 'preview-reward-message');

    startHeartbeat();
    handleRoute();

    // Handle browser back/forward
    window.addEventListener('hashchange', handleRoute);

    // Back button handler
    document.getElementById('back-btn').addEventListener('click', function() {
        window.location.hash = '';
    });

    // New collection button
    document.getElementById('new-collection-btn').addEventListener('click', showCreateForm);

    // Edit button in detail view
    document.getElementById('edit-btn').addEventListener('click', function() {
        const id = window.location.hash.substring('#collection/'.length);
        showEditForm(decodeURIComponent(id));
    });

    // Delete button
    document.getElementById('delete-btn').addEventListener('click', function() {
        const id = window.location.hash.substring('#collection/'.length);
        showDeleteModal(decodeURIComponent(id));
    });

    // Reload button
    document.getElementById('reload-btn').addEventListener('click', reloadCollections);

    // Form handlers
    document.getElementById('collection-form').addEventListener('submit', handleSubmit);
    document.getElementById('form-back-btn').addEventListener('click', cancelForm);
    document.getElementById('form-cancel-btn').addEventListener('click', cancelForm);
    document.getElementById('add-item-btn').addEventListener('click', function() { addItemRow(); });

    // Modal handlers
    document.getElementById('modal-cancel-btn').addEventListener('click', hideDeleteModal);
    document.getElementById('modal-confirm-btn').addEventListener('click', confirmDelete);

    // Item browser search
    const searchInput = document.getElementById('item-search');
    if (searchInput) {
        let searchTimeout = null;
        searchInput.addEventListener('input', function(e) {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                filterBrowserItems(e.target.value);
            }, 150);
        });
    }

    // Drop zone visual feedback
    const itemsContainer = document.getElementById('items-container');
    if (itemsContainer) {
        itemsContainer.addEventListener('dragenter', function() {
            this.classList.add('drag-over');
        });
        itemsContainer.addEventListener('dragleave', function(e) {
            // Only remove if leaving the container entirely
            if (!this.contains(e.relatedTarget)) {
                this.classList.remove('drag-over');
            }
        });
        itemsContainer.addEventListener('drop', function() {
            this.classList.remove('drag-over');
        });

        // Weight validation via event delegation
        itemsContainer.addEventListener('input', function(e) {
            if (e.target.matches('[name^="item-weight-"]')) {
                // Clear the corresponding percentage input when weight is manually changed
                const idx = e.target.name.replace('item-weight-', '');
                const percentInput = document.querySelector('[name="item-percent-' + idx + '"]');
                if (percentInput) percentInput.value = '';
                validateWeights();
            } else if (e.target.matches('[name^="item-percent-"]')) {
                // Find index and trigger adjustment
                const idx = e.target.name.replace('item-percent-', '');
                const rows = Array.from(document.querySelectorAll('.item-row'));
                const rowIndex = rows.findIndex(row => row.querySelector('[name="item-percent-' + idx + '"]'));
                if (rowIndex >= 0) {
                    const percent = parseFloat(e.target.value);
                    if (!isNaN(percent)) {
                        adjustWeightByPercentage(rowIndex, percent);
                    }
                }
            }
        });
    }

    // Template selector handlers
    document.getElementById('start-blank-btn').addEventListener('click', startBlankCollection);

    document.querySelectorAll('.template-btn').forEach(btn => {
        btn.addEventListener('click', function() {
            const templateName = this.dataset.template;
            startFromTemplate(templateName);
        });
    });
});

// Connection status heartbeat
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
            indicator.title = 'Connected to server';
        } else if (response.status === 401) {
            indicator.classList.remove('connected', 'disconnected');
            indicator.classList.add('warning');
            indicator.title = 'Authentication required';
        }
    } catch (error) {
        indicator.classList.remove('connected');
        indicator.classList.add('disconnected');
        indicator.title = 'Disconnected from server';
    }
}

// View routing based on URL hash
function handleRoute() {
    const hash = window.location.hash;
    if (hash.startsWith('#collection/')) {
        const id = decodeURIComponent(hash.substring('#collection/'.length));
        showView('view-detail');
        loadCollectionDetail(id);
    } else {
        showView('view-list');
        loadCollections();
    }
}

function showView(viewId) {
    document.querySelectorAll('.view').forEach(v => v.classList.add('hidden'));
    document.getElementById(viewId).classList.remove('hidden');
}

// Load and render collection list
async function loadCollections() {
    const container = document.getElementById('collection-list');
    const countBadge = document.getElementById('collection-count');
    container.innerHTML = '<p class="loading">Loading collections...</p>';

    try {
        const response = await fetch('/api/collections');
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const collections = await response.json();
        countBadge.textContent = collections.length + ' collections';
        renderCollectionList(collections);
    } catch (error) {
        container.innerHTML = '<p class="error">Failed to load collections. Please try again.</p>';
        countBadge.textContent = '';
    }
}

function renderCollectionList(collections) {
    const container = document.getElementById('collection-list');

    if (collections.length === 0) {
        container.innerHTML = '<p class="empty">No collections configured.</p>';
        return;
    }

    const html = collections.map(c => `
        <div class="collection-card" data-id="${escapeHtml(c.id)}">
            <div class="card-header">
                <span class="tier-badge tier-${c.tier.toLowerCase()}">${c.tier}</span>
            </div>
            <h3 class="card-title">${escapeHtml(c.name)}</h3>
            <div class="card-meta">
                <span class="item-count">${c.itemCount} items</span>
                ${c.zones.length > 0 ? `<span class="zone-count">${c.zones.length} zones</span>` : ''}
            </div>
        </div>
    `).join('');

    container.innerHTML = html;

    // Add click handlers for navigation
    container.querySelectorAll('.collection-card').forEach(card => {
        card.addEventListener('click', function() {
            window.location.hash = '#collection/' + encodeURIComponent(this.dataset.id);
        });
    });
}

// Load and render collection detail
async function loadCollectionDetail(id) {
    const container = document.getElementById('collection-detail');
    const title = document.getElementById('detail-title');
    container.innerHTML = '<p class="loading">Loading collection...</p>';
    title.textContent = 'Loading...';

    try {
        const response = await fetch('/api/collections/' + encodeURIComponent(id));
        if (response.status === 404) {
            container.innerHTML = '<p class="error">Collection not found.</p>';
            title.textContent = 'Not Found';
            return;
        }
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const collection = await response.json();
        title.textContent = collection.name;
        renderCollectionDetail(collection);
    } catch (error) {
        container.innerHTML = '<p class="error">Failed to load collection. Please try again.</p>';
        title.textContent = 'Error';
    }
}

function renderCollectionDetail(c) {
    const container = document.getElementById('collection-detail');

    // Build items table
    const itemsHtml = c.items.map(item => `
        <tr>
            <td>${escapeHtml(item.name)}</td>
            <td><code>${item.material}</code></td>
            <td>${item.weight}</td>
            <td>${item.soulbound ? 'Yes' : 'No'}</td>
        </tr>
    `).join('');

    // Build zones list
    const zonesHtml = c.zones.length > 0
        ? c.zones.map(z => `<span class="zone-tag">${escapeHtml(z)}</span>`).join('')
        : '<span class="none">All zones</span>';

    // Build requirements list
    const reqsHtml = c.requires.length > 0
        ? c.requires.map(r => `<span class="req-tag">${escapeHtml(r)}</span>`).join('')
        : '<span class="none">None</span>';

    // Build rewards section
    const rewardsHtml = buildRewardsHtml(c.rewards);

    container.innerHTML = `
        <div class="detail-section">
            <div class="detail-header">
                <span class="tier-badge tier-${c.tier.toLowerCase()}">${c.tier}</span>
                <span class="icon-badge">${c.icon}</span>
            </div>
            ${c.description ? `<p class="description">${escapeHtml(c.description)}</p>` : ''}
        </div>

        <div class="detail-section">
            <h3>Items (${c.items.length})</h3>
            <table class="items-table">
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Material</th>
                        <th>Weight</th>
                        <th>Soulbound</th>
                    </tr>
                </thead>
                <tbody>
                    ${itemsHtml}
                </tbody>
            </table>
        </div>

        <div class="detail-section">
            <h3>Zones</h3>
            <div class="tag-list">${zonesHtml}</div>
        </div>

        <div class="detail-section">
            <h3>Requirements</h3>
            <div class="tag-list">${reqsHtml}</div>
        </div>

        <div class="detail-section">
            <h3>Rewards</h3>
            ${rewardsHtml}
        </div>
    `;
}

function buildRewardsHtml(rewards) {
    const parts = [];

    if (rewards.experience > 0) {
        parts.push(`<li><strong>Experience:</strong> ${rewards.experience} XP</li>`);
    }
    if (rewards.fireworks) {
        parts.push(`<li><strong>Fireworks:</strong> Yes</li>`);
    }
    if (rewards.commands && rewards.commands.length > 0) {
        parts.push(`<li><strong>Commands:</strong> ${rewards.commands.length} command(s)</li>`);
    }

    if (parts.length === 0) {
        return '<p class="none">No rewards configured</p>';
    }

    return `<ul class="rewards-list">${parts.join('')}</ul>`;
}

// Utility: escape HTML to prevent XSS
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ========== Form Functions ==========

function showCreateForm() {
    currentEditId = null;
    document.getElementById('form-title').textContent = 'New Collection';
    document.getElementById('form-id').disabled = false;

    // Show template selector
    document.getElementById('template-selector').classList.remove('hidden');
    document.getElementById('collection-form').classList.add('hidden');

    showView('view-form');
    initItemBrowser();
}

function startBlankCollection() {
    // Hide template selector and show form
    document.getElementById('template-selector').classList.add('hidden');
    document.getElementById('collection-form').classList.remove('hidden');

    resetForm();
    addItemRow(); // Start with one empty item
    initCollectionSortable();
}

function startFromTemplate(templateName) {
    // Hide template selector and show form
    document.getElementById('template-selector').classList.add('hidden');
    document.getElementById('collection-form').classList.remove('hidden');

    loadTemplate(templateName);
    initCollectionSortable();
}

async function showEditForm(id) {
    currentEditId = id;
    document.getElementById('form-title').textContent = 'Edit Collection';
    document.getElementById('form-id').disabled = true; // Can't change ID

    // Hide template selector and show form directly for edit mode
    document.getElementById('template-selector').classList.add('hidden');
    document.getElementById('collection-form').classList.remove('hidden');

    resetForm();

    try {
        const response = await fetch('/api/collections/' + encodeURIComponent(id));
        if (!response.ok) throw new Error('Failed to load');
        const collection = await response.json();
        populateForm(collection);
        showView('view-form');
        initItemBrowser();
        initCollectionSortable();
    } catch (error) {
        showToast('Failed to load collection', 'error');
    }
}

function resetForm() {
    document.getElementById('collection-form').reset();
    document.getElementById('items-container').innerHTML = '';
    itemRowCounter = 0;
    clearValidationErrors();

    // Reset browser search
    const searchInput = document.getElementById('item-search');
    if (searchInput) {
        searchInput.value = '';
    }

    // Reset spawn conditions to defaults
    document.getElementById('form-biomes').value = '';
    document.getElementById('dim-normal').checked = true;
    document.getElementById('dim-nether').checked = false;
    document.getElementById('dim-end').checked = false;
    document.getElementById('form-min-y').value = '-64';
    document.getElementById('form-max-y').value = '320';
}

function populateForm(c) {
    document.getElementById('form-id').value = c.id;
    document.getElementById('form-name').value = c.name;
    document.getElementById('form-description').value = c.description || '';
    document.getElementById('form-tier').value = c.tier;
    document.getElementById('form-icon').value = c.icon || '';
    document.getElementById('form-zones').value = c.zones.join(', ');
    document.getElementById('form-requires').value = c.requires.join(', ');
    document.getElementById('form-reward-xp').value = c.rewards.experience || 0;
    document.getElementById('form-reward-fireworks').checked = c.rewards.fireworks || false;
    document.getElementById('form-reward-commands').value = (c.rewards.commands || []).join('\n');
    document.getElementById('form-reward-message').value = c.rewards.message || '';

    // Populate spawn conditions
    if (c.biomes && c.biomes.length > 0) {
        document.getElementById('form-biomes').value = c.biomes.join(', ');
    } else {
        document.getElementById('form-biomes').value = '';
    }

    // Set dimension checkboxes
    document.getElementById('dim-normal').checked = !c.dimensions || c.dimensions.length === 0 || c.dimensions.includes('NORMAL');
    document.getElementById('dim-nether').checked = c.dimensions && c.dimensions.includes('NETHER');
    document.getElementById('dim-end').checked = c.dimensions && c.dimensions.includes('THE_END');

    // Set Y levels
    document.getElementById('form-min-y').value = c.minY !== undefined && c.minY !== null ? c.minY : -64;
    document.getElementById('form-max-y').value = c.maxY !== undefined && c.maxY !== null ? c.maxY : 320;

    // Add item rows
    c.items.forEach(item => addItemRow(item));
}

function cancelForm() {
    if (currentEditId) {
        window.location.hash = '#collection/' + encodeURIComponent(currentEditId);
    } else {
        window.location.hash = '';
    }
}

// ========== Item Row Management ==========

function addItemRow(item) {
    const container = document.getElementById('items-container');
    const idx = itemRowCounter++;
    const row = document.createElement('div');
    row.className = 'item-row';
    row.dataset.index = idx;

    row.innerHTML = `
        <div class="item-row-header">
            <span class="drag-handle">&#9776;</span>
            <span class="item-number">Item ${container.children.length + 1}</span>
            <button type="button" class="btn-remove-item" onclick="removeItemRow(this)">&times;</button>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>ID</label>
                <input type="text" name="item-id-${idx}" placeholder="item_id" value="${item?.id || ''}">
                <span class="error-message" data-field="items[${idx}].id"></span>
            </div>
            <div class="form-group">
                <label>Name</label>
                <input type="text" name="item-name-${idx}" placeholder="Item Name" value="${escapeHtml(item?.name || '')}">
                <span class="error-message" data-field="items[${idx}].name"></span>
            </div>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>Material</label>
                <input type="text" name="item-material-${idx}" placeholder="DIAMOND" value="${item?.material || ''}">
                <span class="error-message" data-field="items[${idx}].material"></span>
            </div>
            <div class="form-group weight-group">
                <label>Weight</label>
                <div class="weight-inputs">
                    <input type="number" name="item-weight-${idx}" min="1" value="${item?.weight || 10}">
                    <span class="weight-or">or</span>
                    <input type="number" name="item-percent-${idx}" min="0" max="100" step="0.1" placeholder="%">
                    <span class="weight-percentage"></span>
                </div>
                <span class="error-message" data-field="items[${idx}].weight"></span>
            </div>
            <div class="form-group checkbox-group">
                <label><input type="checkbox" name="item-soulbound-${idx}" ${item?.soulbound ? 'checked' : ''}> Soulbound</label>
            </div>
        </div>
        <div class="form-group">
            <label>Lore</label>
            <textarea name="item-lore-${idx}" rows="2" placeholder="Line 1&#10;Line 2">${(item?.lore || []).join('\n')}</textarea>
            <div class="minimessage-preview lore-preview" data-lore-preview="${idx}"></div>
            <small>One line per line. Supports MiniMessage formatting.</small>
        </div>
    `;

    container.appendChild(row);

    // Attach lore preview
    const loreTextarea = row.querySelector('[name="item-lore-' + idx + '"]');
    const lorePreview = row.querySelector('[data-lore-preview="' + idx + '"]');
    attachLorePreview(loreTextarea, lorePreview);

    renumberItems();
    validateWeights();
}

function removeItemRow(button) {
    const row = button.closest('.item-row');
    row.remove();
    renumberItems();
    validateWeights();
}

function renumberItems() {
    const rows = document.querySelectorAll('.item-row');
    rows.forEach((row, i) => {
        row.querySelector('.item-number').textContent = 'Item ' + (i + 1);
    });
}

// ========== Form Collection and Submission ==========

function collectFormData() {
    const items = [];
    document.querySelectorAll('.item-row').forEach((row) => {
        const idx = row.dataset.index;
        items.push({
            id: row.querySelector(`[name="item-id-${idx}"]`).value,
            name: row.querySelector(`[name="item-name-${idx}"]`).value,
            material: row.querySelector(`[name="item-material-${idx}"]`).value || 'PAPER',
            weight: parseInt(row.querySelector(`[name="item-weight-${idx}"]`).value) || 10,
            soulbound: row.querySelector(`[name="item-soulbound-${idx}"]`).checked,
            lore: row.querySelector(`[name="item-lore-${idx}"]`).value.split('\n').filter(l => l.trim())
        });
    });

    const zones = document.getElementById('form-zones').value;
    const requires = document.getElementById('form-requires').value;
    const commands = document.getElementById('form-reward-commands').value;
    const biomes = document.getElementById('form-biomes').value;

    // Collect checked dimensions
    const dimensions = [];
    if (document.getElementById('dim-normal').checked) dimensions.push('NORMAL');
    if (document.getElementById('dim-nether').checked) dimensions.push('NETHER');
    if (document.getElementById('dim-end').checked) dimensions.push('THE_END');

    // Get Y levels (null if default)
    const minY = parseInt(document.getElementById('form-min-y').value);
    const maxY = parseInt(document.getElementById('form-max-y').value);

    return {
        id: document.getElementById('form-id').value,
        name: document.getElementById('form-name').value,
        description: document.getElementById('form-description').value || null,
        tier: document.getElementById('form-tier').value,
        icon: document.getElementById('form-icon').value || null,
        items: items,
        rewards: {
            experience: parseInt(document.getElementById('form-reward-xp').value) || 0,
            fireworks: document.getElementById('form-reward-fireworks').checked,
            commands: commands ? commands.split('\n').filter(l => l.trim()) : [],
            message: document.getElementById('form-reward-message').value || null
        },
        zones: zones ? zones.split(',').map(z => z.trim()).filter(z => z) : [],
        requires: requires ? requires.split(',').map(r => r.trim()).filter(r => r) : [],
        biomes: biomes ? biomes.split(',').map(b => b.trim()).filter(b => b) : null,
        dimensions: dimensions.length > 0 ? dimensions : null,
        minY: (minY !== -64) ? minY : null,
        maxY: (maxY !== 320) ? maxY : null
    };
}

async function handleSubmit(event) {
    event.preventDefault();
    clearValidationErrors();

    const formData = collectFormData();
    const isNew = currentEditId === null;
    const url = isNew ? '/api/collections' : '/api/collections/' + encodeURIComponent(formData.id);
    const method = isNew ? 'POST' : 'PUT';

    try {
        const response = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(formData)
        });

        if (response.status === 400) {
            const errorData = await response.json();
            displayValidationErrors(errorData.errors);
            showToast('Please fix the validation errors', 'error');
            return;
        }

        if (response.status === 409) {
            displayValidationErrors([{ field: 'id', message: 'Collection ID already exists' }]);
            showToast('Collection ID already exists', 'error');
            return;
        }

        if (!response.ok) {
            throw new Error('Server error');
        }

        showToast(isNew ? 'Collection created!' : 'Collection saved!', 'success');
        window.location.hash = '#collection/' + encodeURIComponent(formData.id);

    } catch (error) {
        showToast('Failed to save collection. Please try again.', 'error');
    }
}

// ========== Validation Error Display ==========

function clearValidationErrors() {
    document.querySelectorAll('.error-message').forEach(el => el.textContent = '');
    document.querySelectorAll('.form-group').forEach(el => el.classList.remove('has-error'));
}

function displayValidationErrors(errors) {
    for (const error of errors) {
        // Handle array indices - items[0].name -> items[idx].name in actual DOM
        const field = error.field;
        const errorEl = document.querySelector(`[data-field="${field}"]`);
        if (errorEl) {
            errorEl.textContent = error.message;
            errorEl.closest('.form-group')?.classList.add('has-error');
        }
    }
    // Scroll to first error
    const firstError = document.querySelector('.form-group.has-error');
    if (firstError) {
        firstError.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
}

// ========== Delete Modal ==========

function showDeleteModal(id) {
    deleteTargetId = id;
    document.getElementById('delete-modal-message').textContent =
        `Are you sure you want to delete "${id}"? This cannot be undone.`;
    document.getElementById('delete-modal').classList.remove('hidden');
}

function hideDeleteModal() {
    deleteTargetId = null;
    document.getElementById('delete-modal').classList.add('hidden');
}

async function confirmDelete() {
    if (!deleteTargetId) return;

    try {
        const response = await fetch('/api/collections/' + encodeURIComponent(deleteTargetId), {
            method: 'DELETE'
        });

        if (!response.ok) {
            throw new Error('Delete failed');
        }

        showToast('Collection deleted', 'success');
        hideDeleteModal();
        window.location.hash = '';

    } catch (error) {
        showToast('Failed to delete collection', 'error');
        hideDeleteModal();
    }
}

// ========== Reload and Toast ==========

async function reloadCollections() {
    const btn = document.getElementById('reload-btn');
    btn.disabled = true;
    btn.textContent = 'Reloading...';

    try {
        const response = await fetch('/api/reload', { method: 'POST' });
        if (!response.ok) throw new Error('Reload failed');

        const data = await response.json();
        showToast(`Reloaded ${data.collectionCount} collections`, 'success');

        // Refresh current view
        handleRoute();
    } catch (error) {
        showToast('Failed to reload collections', 'error');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Reload';
    }
}

function showToast(message, type) {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.className = 'toast ' + (type || 'info');
    toast.classList.remove('hidden');

    setTimeout(() => {
        toast.classList.add('hidden');
    }, 3000);
}

// ========== Item Browser Functions ==========

async function initItemBrowser() {
    const grid = document.getElementById('item-browser-grid');
    const countEl = document.getElementById('browser-count');

    // Use cached materials if available
    if (allMaterials.length > 0) {
        countEl.textContent = allMaterials.length + ' items';
        renderBrowserGrid(allMaterials);
        return;
    }

    try {
        const response = await fetch('/api/materials');
        if (!response.ok) throw new Error('Failed to load materials');
        allMaterials = await response.json();
        countEl.textContent = allMaterials.length + ' items';
        renderBrowserGrid(allMaterials);
    } catch (error) {
        grid.innerHTML = '<p class="error">Failed to load items</p>';
    }
}

function renderBrowserGrid(materials) {
    const grid = document.getElementById('item-browser-grid');

    if (materials.length === 0) {
        grid.innerHTML = '<p class="empty">No matching items</p>';
        return;
    }

    // Render material items (limit to first 200 for performance, search narrows down)
    const displayMaterials = materials.slice(0, 200);

    grid.innerHTML = displayMaterials.map(material => `
        <div class="browser-item" data-material="${material}">
            <span class="item-icon">${getMaterialEmoji(material)}</span>
            <span class="item-label">${formatMaterialLabel(material)}</span>
        </div>
    `).join('');

    // Initialize SortableJS on browser grid after render
    initBrowserSortable();
}

function formatMaterialLabel(material) {
    // DIAMOND_SWORD -> Diamond S...
    const formatted = material.split('_')
        .map(word => word.charAt(0) + word.slice(1).toLowerCase())
        .join(' ');
    return formatted.length > 10 ? formatted.slice(0, 9) + '...' : formatted;
}

function formatMaterialName(material) {
    // DIAMOND_SWORD -> Diamond Sword (full name for form)
    return material.split('_')
        .map(word => word.charAt(0) + word.slice(1).toLowerCase())
        .join(' ');
}

function getMaterialEmoji(material) {
    // Simple emoji mapping for common materials
    const emojiMap = {
        'DIAMOND': '💎', 'EMERALD': '💚', 'GOLD_INGOT': '🥇', 'IRON_INGOT': '🔩',
        'COAL': '⚫', 'REDSTONE': '🔴', 'LAPIS_LAZULI': '🔵', 'AMETHYST_SHARD': '💜',
        'APPLE': '🍎', 'BREAD': '🍞', 'CARROT': '🥕', 'POTATO': '🥔',
        'BONE': '🦴', 'STRING': '🧵', 'FEATHER': '🪶', 'LEATHER': '🟤',
        'BOOK': '📖', 'PAPER': '📄', 'MAP': '🗺️', 'COMPASS': '🧭',
        'ENDER_PEARL': '🟣', 'BLAZE_ROD': '🔥', 'GHAST_TEAR': '💧',
        'SWORD': '⚔️', 'PICKAXE': '⛏️', 'AXE': '🪓', 'SHOVEL': '🔧',
        'BOW': '🏹', 'ARROW': '➡️', 'SHIELD': '🛡️', 'TRIDENT': '🔱'
    };

    // Check for exact match first
    if (emojiMap[material]) return emojiMap[material];

    // Check for partial match (e.g., DIAMOND_SWORD contains SWORD)
    for (const [key, emoji] of Object.entries(emojiMap)) {
        if (material.includes(key)) return emoji;
    }

    // Default: cube emoji for blocks, circle for others
    return material.includes('BLOCK') || material.includes('ORE') ? '🧱' : '⚪';
}

function filterBrowserItems(searchTerm) {
    const term = searchTerm.toLowerCase().trim();
    const countEl = document.getElementById('browser-count');

    if (term === '') {
        countEl.textContent = allMaterials.length + ' items';
        renderBrowserGrid(allMaterials);
        return;
    }

    const filtered = allMaterials.filter(m => m.toLowerCase().includes(term));
    countEl.textContent = filtered.length + ' matches';
    renderBrowserGrid(filtered);
}

// ========== SortableJS Integration ==========

function initBrowserSortable() {
    const grid = document.getElementById('item-browser-grid');

    // Destroy existing sortable if any
    if (browserSortable) {
        browserSortable.destroy();
    }

    browserSortable = Sortable.create(grid, {
        group: {
            name: 'collection-items',
            pull: 'clone',
            put: false
        },
        sort: false,
        animation: 150,
        ghostClass: 'sortable-ghost',
        chosenClass: 'sortable-chosen',

        onStart: function() {
            document.body.classList.add('is-dragging');
        },

        onEnd: function() {
            document.body.classList.remove('is-dragging');
        }
    });
}

function initCollectionSortable() {
    const container = document.getElementById('items-container');

    // Destroy existing sortable if any
    if (collectionSortable) {
        collectionSortable.destroy();
    }

    collectionSortable = Sortable.create(container, {
        group: {
            name: 'collection-items',
            pull: true,
            put: true
        },
        handle: '.drag-handle',
        animation: 150,
        ghostClass: 'sortable-ghost',
        chosenClass: 'sortable-chosen',

        onAdd: function(evt) {
            // Item dropped from browser - convert to form row
            const browserItem = evt.item;
            const material = browserItem.dataset.material;
            convertBrowserItemToFormRow(browserItem, material);
            renumberItems();
        },

        onUpdate: function() {
            // Items reordered within collection
            renumberItems();
        },

        onRemove: function() {
            // Item dragged out of collection (to trash)
            renumberItems();
        },

        onStart: function() {
            document.body.classList.add('is-dragging');
        },

        onEnd: function() {
            document.body.classList.remove('is-dragging');
        }
    });
}

function convertBrowserItemToFormRow(browserElement, material) {
    const container = document.getElementById('items-container');
    const idx = itemRowCounter++;

    // Replace the browser item with a full form row
    browserElement.className = 'item-row';
    browserElement.dataset.index = idx;
    delete browserElement.dataset.material;

    browserElement.innerHTML = `
        <div class="item-row-header">
            <span class="drag-handle">&#9776;</span>
            <span class="item-number">Item ${container.children.length}</span>
            <button type="button" class="btn-remove-item" onclick="removeItemRow(this)">&times;</button>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>ID</label>
                <input type="text" name="item-id-${idx}" placeholder="item_id" value="${material.toLowerCase()}">
                <span class="error-message" data-field="items[${idx}].id"></span>
            </div>
            <div class="form-group">
                <label>Name</label>
                <input type="text" name="item-name-${idx}" placeholder="Item Name" value="${formatMaterialName(material)}">
                <span class="error-message" data-field="items[${idx}].name"></span>
            </div>
        </div>
        <div class="form-row">
            <div class="form-group">
                <label>Material</label>
                <input type="text" name="item-material-${idx}" value="${material}" readonly>
                <span class="error-message" data-field="items[${idx}].material"></span>
            </div>
            <div class="form-group weight-group">
                <label>Weight</label>
                <div class="weight-inputs">
                    <input type="number" name="item-weight-${idx}" min="1" value="10">
                    <span class="weight-or">or</span>
                    <input type="number" name="item-percent-${idx}" min="0" max="100" step="0.1" placeholder="%">
                    <span class="weight-percentage"></span>
                </div>
                <span class="error-message" data-field="items[${idx}].weight"></span>
            </div>
            <div class="form-group checkbox-group">
                <label><input type="checkbox" name="item-soulbound-${idx}"> Soulbound</label>
            </div>
        </div>
        <div class="form-group">
            <label>Lore</label>
            <textarea name="item-lore-${idx}" rows="2" placeholder="Line 1&#10;Line 2"></textarea>
            <div class="minimessage-preview lore-preview" data-lore-preview="${idx}"></div>
            <small>One line per line. Supports MiniMessage formatting.</small>
        </div>
    `;

    // Attach lore preview
    const loreTextarea = browserElement.querySelector('[name="item-lore-' + idx + '"]');
    const lorePreview = browserElement.querySelector('[data-lore-preview="' + idx + '"]');
    attachLorePreview(loreTextarea, lorePreview);

    validateWeights();
}

// ========== Weight Validation ==========

function validateWeights() {
    if (isAdjustingWeights) return;

    const container = document.getElementById('items-container');
    const weightInputs = container.querySelectorAll('[name^="item-weight-"]');
    const weights = Array.from(weightInputs).map(input => parseInt(input.value) || 0);
    const totalWeight = weights.reduce((sum, w) => sum + w, 0);

    // Update or create validation message element
    let validationEl = document.getElementById('weight-validation');
    if (!validationEl) {
        validationEl = document.createElement('div');
        validationEl.id = 'weight-validation';
        const addBtn = document.getElementById('add-item-btn');
        if (addBtn && addBtn.parentNode) {
            addBtn.parentNode.insertBefore(validationEl, addBtn);
        }
    }

    // Set validation message
    if (weightInputs.length === 0) {
        validationEl.textContent = '';
        validationEl.className = 'weight-validation';
    } else if (totalWeight === 0) {
        validationEl.textContent = 'Set weights to see drop distribution';
        validationEl.className = 'weight-validation weight-info';
    } else if (totalWeight === 100) {
        validationEl.textContent = 'Weights sum to 100%';
        validationEl.className = 'weight-validation weight-success';
    } else {
        validationEl.textContent = 'Weights sum to ' + totalWeight + '% (should be 100%)';
        validationEl.className = 'weight-validation weight-warning';
    }

    // Update percentage display for each item
    weightInputs.forEach((input, idx) => {
        const weight = weights[idx];
        const percentage = totalWeight > 0 ? ((weight / totalWeight) * 100).toFixed(1) : '0.0';
        const row = input.closest('.item-row');

        // Find or create percentage span
        let percentSpan = row.querySelector('.weight-percentage');
        if (!percentSpan) {
            const weightGroup = input.closest('.weight-group') || input.parentNode;
            percentSpan = document.createElement('span');
            percentSpan.className = 'weight-percentage';
            weightGroup.appendChild(percentSpan);
        }
        percentSpan.textContent = '(' + percentage + '% drop)';
    });
}

function adjustWeightByPercentage(itemIndex, targetPercent) {
    if (targetPercent < 0 || targetPercent > 100) return;

    isAdjustingWeights = true;

    const container = document.getElementById('items-container');
    const weightInputs = Array.from(container.querySelectorAll('[name^="item-weight-"]'));
    const percentInputs = Array.from(container.querySelectorAll('[name^="item-percent-"]'));

    if (weightInputs.length === 0) {
        isAdjustingWeights = false;
        return;
    }

    // Get current weights
    const weights = weightInputs.map(input => parseInt(input.value) || 0);
    const currentTotal = weights.reduce((a, b) => a + b, 0);

    // Target weight (we work in integer weights that sum to 100)
    const targetWeight = Math.round(targetPercent);
    const remaining = 100 - targetWeight;

    if (weightInputs.length === 1) {
        // Only one item - set to 100
        weightInputs[0].value = 100;
        isAdjustingWeights = false;
        validateWeights();
        return;
    }

    // Get sum of other items' current weights
    const otherTotal = currentTotal - weights[itemIndex];

    if (otherTotal === 0) {
        // Other items have no weight - distribute evenly
        const evenSplit = Math.floor(remaining / (weightInputs.length - 1));
        let distributed = 0;

        weightInputs.forEach((input, idx) => {
            if (idx === itemIndex) {
                input.value = targetWeight;
            } else {
                input.value = evenSplit;
                distributed += evenSplit;
            }
        });

        // Fix rounding - add remainder to last non-target item
        const remainder = remaining - distributed;
        if (remainder !== 0) {
            for (let i = weightInputs.length - 1; i >= 0; i--) {
                if (i !== itemIndex) {
                    weightInputs[i].value = parseInt(weightInputs[i].value) + remainder;
                    break;
                }
            }
        }
    } else {
        // Distribute proportionally based on current weights
        let distributed = 0;
        const newWeights = [];

        weightInputs.forEach((input, idx) => {
            if (idx === itemIndex) {
                newWeights[idx] = targetWeight;
            } else {
                const proportion = weights[idx] / otherTotal;
                const newWeight = Math.round(remaining * proportion);
                newWeights[idx] = newWeight;
                distributed += newWeight;
            }
        });

        // Fix rounding error by adjusting the largest non-target weight
        const difference = remaining - distributed;
        if (difference !== 0) {
            let largestIdx = -1;
            let largestWeight = -1;
            weights.forEach((w, idx) => {
                if (idx !== itemIndex && w > largestWeight) {
                    largestWeight = w;
                    largestIdx = idx;
                }
            });
            if (largestIdx >= 0) {
                newWeights[largestIdx] += difference;
            }
        }

        // Apply new weights
        weightInputs.forEach((input, idx) => {
            input.value = Math.max(1, newWeights[idx]); // Ensure minimum of 1
        });
    }

    // Clear all percentage inputs (they're just for input, not display)
    percentInputs.forEach((input, idx) => {
        if (idx !== itemIndex) {
            input.value = '';
        }
    });

    isAdjustingWeights = false;
    validateWeights();
}

// ========== MiniMessage Preview Functions ==========

let miniMessageParser = null;

function initMiniMessageParser() {
    // Check if MiniMessage library is loaded
    if (typeof MiniMessage !== 'undefined') {
        try {
            miniMessageParser = MiniMessage.miniMessage();
        } catch (e) {
            console.warn('Failed to initialize MiniMessage parser:', e);
        }
    }
}

function renderMiniMessage(text, previewElement) {
    if (!previewElement) return;

    // Empty text - clear preview
    if (!text || text.trim() === '') {
        previewElement.innerHTML = '';
        previewElement.classList.remove('parse-error');
        return;
    }

    // No parser available - show plain text
    if (!miniMessageParser) {
        previewElement.textContent = text;
        previewElement.classList.remove('parse-error');
        return;
    }

    try {
        const component = miniMessageParser.deserialize(text);
        // Clear and render to element
        previewElement.innerHTML = '';
        miniMessageParser.toHTML(component, previewElement);
        previewElement.classList.remove('parse-error');
    } catch (error) {
        // Parse error - show original text with error styling
        previewElement.textContent = text;
        previewElement.classList.add('parse-error');
    }
}

function attachMiniMessagePreview(inputId, previewId) {
    const input = document.getElementById(inputId);
    const preview = document.getElementById(previewId);

    if (!input || !preview) return;

    // Initial render
    renderMiniMessage(input.value, preview);

    // Update on input
    input.addEventListener('input', function() {
        renderMiniMessage(this.value, preview);
    });
}

function attachLorePreview(loreTextarea, previewElement) {
    if (!loreTextarea || !previewElement) return;

    function updateLorePreview() {
        const lines = loreTextarea.value.split('\n').filter(l => l.trim());
        if (lines.length === 0) {
            previewElement.innerHTML = '';
            return;
        }

        previewElement.innerHTML = '';
        lines.forEach(line => {
            const lineDiv = document.createElement('div');
            lineDiv.className = 'lore-line';
            renderMiniMessage(line, lineDiv);
            previewElement.appendChild(lineDiv);
        });
    }

    updateLorePreview();
    loreTextarea.addEventListener('input', updateLorePreview);
}
