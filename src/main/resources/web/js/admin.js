// Global state
let currentEditIndex = null;
let clientRunning = false;

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    loadRoutes();
    loadStatus();
    loadConfig();

    // Auto-refresh every 5 seconds
    setInterval(() => {
        loadRoutes();
        loadStatus();
    }, 5000);
});

// Domain Management
async function updateDomain() {
    const domainInput = document.getElementById('domain-input');
    const domain = domainInput.value.trim();

    if (!domain) {
        alert('Please enter a domain name');
        return;
    }

    try {
        const response = await fetch('/api/config/domain', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ domain })
        });

        const result = await response.json();

        if (response.ok) {
            showNotification('Domain updated successfully', 'success');
            loadStatus(); // Refresh to show new domain
        } else {
            showNotification(result.error || 'Failed to update domain', 'error');
        }
    } catch (error) {
        showNotification('Error updating domain: ' + error.message, 'error');
    }
}

// Client Control
async function startClient() {
    try {
        const response = await fetch('/api/client/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        const result = await response.json();

        if (response.ok) {
            showNotification('Client started successfully', 'success');
            document.getElementById('start-btn').disabled = true;
            document.getElementById('stop-btn').disabled = false;
            clientRunning = true;
            loadStatus();
        } else {
            showNotification(result.error || 'Failed to start client', 'error');
        }
    } catch (error) {
        showNotification('Error starting client: ' + error.message, 'error');
    }
}

async function stopClient() {
    try {
        const response = await fetch('/api/client/stop', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        const result = await response.json();

        if (response.ok) {
            showNotification('Client stopped successfully', 'success');
            document.getElementById('start-btn').disabled = false;
            document.getElementById('stop-btn').disabled = true;
            clientRunning = false;
            loadStatus();
        } else {
            showNotification(result.error || 'Failed to stop client', 'error');
        }
    } catch (error) {
        showNotification('Error stopping client: ' + error.message, 'error');
    }
}

// Settings Management
function toggleSettings() {
    const panel = document.getElementById('settings-panel');
    panel.style.display = panel.style.display === 'none' ? 'block' : 'none';
}

async function saveSettings() {
    const signalHost = document.getElementById('signal-host').value;
    const signalPort = parseInt(document.getElementById('signal-port').value);
    const dataPort = parseInt(document.getElementById('data-port').value);

    try {
        const response = await fetch('/api/config/signal', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ signalHost, signalPort, dataPort })
        });

        const result = await response.json();

        if (response.ok) {
            showNotification('Settings saved successfully', 'success');
            toggleSettings();
        } else {
            showNotification(result.error || 'Failed to save settings', 'error');
        }
    } catch (error) {
        showNotification('Error saving settings: ' + error.message, 'error');
    }
}

// Load configuration
async function loadConfig() {
    try {
        const response = await fetch('/api/config');
        const config = await response.json();

        // Populate form fields
        document.getElementById('domain-input').value = config.domain || '';
        document.getElementById('signal-host').value = config.signalHost || 'localtunnel.me';
        document.getElementById('signal-port').value = config.signalPort || 443;
        document.getElementById('data-port').value = config.dataPort || 443;
    } catch (error) {
        console.error('Error loading config:', error);
    }
}

// Load status
async function loadStatus() {
    try {
        const response = await fetch('/api/status');
        const status = await response.json();

        // Update status badge
        const statusBadge = document.getElementById('status-badge');
        const statusText = document.getElementById('status-text');

        if (status.running) {
            statusBadge.classList.add('active');
            statusText.textContent = 'Connected';
            clientRunning = true;
            document.getElementById('start-btn').disabled = true;
            document.getElementById('stop-btn').disabled = false;
        } else {
            statusBadge.classList.remove('active');
            statusText.textContent = 'Stopped';
            clientRunning = false;
            document.getElementById('start-btn').disabled = false;
            document.getElementById('stop-btn').disabled = true;
        }

        // Update stats
        document.getElementById('stat-routes').textContent = status.routeCount || 0;
        document.getElementById('stat-status').textContent = status.running ? 'Running' : 'Stopped';
        document.getElementById('stat-domain').textContent = status.domain || '-';
        document.getElementById('stat-mode').textContent = status.mode || '-';

        // Update domain display
        document.getElementById('domain-display').textContent = `Current: ${status.domain || 'Not set'}`;
    } catch (error) {
        console.error('Error loading status:', error);
    }
}

// Route Management
async function loadRoutes() {
    try {
        const response = await fetch('/api/routes');
        let routes = await response.json();

        // Sort routes by priority (ascending - lowest number = highest priority)
        routes.sort((a, b) => a.priority - b.priority);

        const tbody = document.getElementById('routes-body');

        if (routes.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="7" style="text-align: center; padding: 2rem; color: #8892b0;">
                        No routes configured. Click "Add Route" to get started.
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = routes.map((route, index) => `
            <tr>
                <td><code class="path-pattern">${escapeHtml(route.pathPattern)}</code></td>
                <td>${escapeHtml(route.targetHost)}</td>
                <td>${route.targetPort}</td>
                <td><span class="priority-badge priority-${getPriorityClass(route.priority)}">${route.priority}</span></td>
                <td>
                    ${route.useSSL ? '<span class="feature-badge ssl">🔒 SSL</span>' : ''}
                    ${route.stripPrefix ? '<span class="feature-badge">Strip</span>' : ''}
                    ${route.forwardHost ? '<span class="feature-badge">Forward</span>' : ''}
                </td>
                <td>${escapeHtml(route.description || '-')}</td>
                <td>
                    <button class="btn-icon" onclick="editRoute(${index})" title="Edit">✏️</button>
                    <button class="btn-icon" onclick="deleteRoute(${index})" title="Delete">🗑️</button>
                </td>
            </tr>
        `).join('');
    } catch (error) {
        console.error('Error loading routes:', error);
        showNotification('Failed to load routes', 'error');
    }
}

function showAddModal() {
    currentEditIndex = null;
    document.getElementById('modal-title').textContent = 'Add Route';
    document.getElementById('route-form').reset();
    document.getElementById('priority').value = 100;
    document.getElementById('modal').style.display = 'flex';
}

function editRoute(index) {
    currentEditIndex = index;
    fetch('/api/routes')
        .then(response => response.json())
        .then(routes => {
            const route = routes[index];
            document.getElementById('modal-title').textContent = 'Edit Route';
            document.getElementById('pathPattern').value = route.pathPattern;
            document.getElementById('targetHost').value = route.targetHost;
            document.getElementById('targetPort').value = route.targetPort;
            document.getElementById('priority').value = route.priority;
            document.getElementById('description').value = route.description || '';
            document.getElementById('useSSL').checked = route.useSSL;
            document.getElementById('stripPrefix').checked = route.stripPrefix;
            document.getElementById('forwardHost').checked = route.forwardHost;
            document.getElementById('modal').style.display = 'flex';
        });
}

async function saveRoute(event) {
    event.preventDefault();

    const route = {
        pathPattern: document.getElementById('pathPattern').value,
        targetHost: document.getElementById('targetHost').value,
        targetPort: parseInt(document.getElementById('targetPort').value),
        priority: parseInt(document.getElementById('priority').value),
        description: document.getElementById('description').value,
        useSSL: document.getElementById('useSSL').checked,
        stripPrefix: document.getElementById('stripPrefix').checked,
        forwardHost: document.getElementById('forwardHost').checked
    };

    try {
        let response;

        if (currentEditIndex !== null) {
            // Update existing route
            response = await fetch(`/api/routes/${currentEditIndex}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(route)
            });
        } else {
            // Add new route
            response = await fetch('/api/routes', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(route)
            });
        }

        if (response.ok) {
            showNotification(`Route ${currentEditIndex !== null ? 'updated' : 'added'} successfully`, 'success');
            hideModal();
            loadRoutes();
        } else {
            const error = await response.json();
            showNotification(error.error || 'Failed to save route', 'error');
        }
    } catch (error) {
        showNotification('Error saving route: ' + error.message, 'error');
    }
}

async function deleteRoute(index) {
    if (!confirm('Are you sure you want to delete this route?')) {
        return;
    }

    try {
        const response = await fetch(`/api/routes/${index}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            showNotification('Route deleted successfully', 'success');
            loadRoutes();
        } else {
            const error = await response.json();
            showNotification(error.error || 'Failed to delete route', 'error');
        }
    } catch (error) {
        showNotification('Error deleting route: ' + error.message, 'error');
    }
}

function hideModal() {
    document.getElementById('modal').style.display = 'none';
    document.getElementById('route-form').reset();
    currentEditIndex = null;
}

// Utility functions
function getPriorityClass(priority) {
    if (priority >= 200) return 'high';
    if (priority >= 100) return 'medium';
    return 'low';
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showNotification(message, type = 'info') {
    // Create notification element
    const notification = document.createElement('div');
    notification.className = `notification notification-${type}`;
    notification.textContent = message;
    notification.style.cssText = 'position:fixed;top:20px;right:20px;padding:1rem 1.5rem;background:#00f5ff;color:#0a0e27;border-radius:8px;z-index:10000;animation:slideIn 0.3s;box-shadow:0 4px 20px rgba(0, 245, 255, 0.3);';

    if (type === 'error') {
        notification.style.background = '#ff4444';
        notification.style.color = 'white';
    } else if (type === 'success') {
        notification.style.background = '#00ff88';
    }

    document.body.appendChild(notification);

    setTimeout(() => {
        notification.remove();
    }, 3000);
}

// Close modal on outside click
document.getElementById('modal').addEventListener('click', (e) => {
    if (e.target.id === 'modal') {
        hideModal();
    }
});

// Add CSS animation
const style = document.createElement('style');
style.textContent = '@keyframes slideIn { from { transform: translateX(100%); } to { transform: translateX(0); } }';
document.head.appendChild(style);
