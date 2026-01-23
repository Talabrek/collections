// Connection heartbeat
const HEARTBEAT_INTERVAL = 30000; // 30 seconds
let heartbeatTimer = null;

// Form state
let currentEditId = null; // null for create, ID for edit
let itemRowCounter = 0;
let deleteTargetId = null;

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
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
    resetForm();
    addItemRow(); // Start with one empty item
    showView('view-form');
}

async function showEditForm(id) {
    currentEditId = id;
    document.getElementById('form-title').textContent = 'Edit Collection';
    document.getElementById('form-id').disabled = true; // Can't change ID
    resetForm();

    try {
        const response = await fetch('/api/collections/' + encodeURIComponent(id));
        if (!response.ok) throw new Error('Failed to load');
        const collection = await response.json();
        populateForm(collection);
        showView('view-form');
    } catch (error) {
        showToast('Failed to load collection', 'error');
    }
}

function resetForm() {
    document.getElementById('collection-form').reset();
    document.getElementById('items-container').innerHTML = '';
    itemRowCounter = 0;
    clearValidationErrors();
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
            <div class="form-group">
                <label>Weight</label>
                <input type="number" name="item-weight-${idx}" min="1" value="${item?.weight || 10}">
                <span class="error-message" data-field="items[${idx}].weight"></span>
            </div>
            <div class="form-group checkbox-group">
                <label><input type="checkbox" name="item-soulbound-${idx}" ${item?.soulbound ? 'checked' : ''}> Soulbound</label>
            </div>
        </div>
        <div class="form-group">
            <label>Lore</label>
            <textarea name="item-lore-${idx}" rows="2" placeholder="Line 1&#10;Line 2">${(item?.lore || []).join('\n')}</textarea>
        </div>
    `;

    container.appendChild(row);
    renumberItems();
}

function removeItemRow(button) {
    const row = button.closest('.item-row');
    row.remove();
    renumberItems();
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
        requires: requires ? requires.split(',').map(r => r.trim()).filter(r => r) : []
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
