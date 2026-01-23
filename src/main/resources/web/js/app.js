// Connection heartbeat
const HEARTBEAT_INTERVAL = 30000; // 30 seconds
let heartbeatTimer = null;

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
