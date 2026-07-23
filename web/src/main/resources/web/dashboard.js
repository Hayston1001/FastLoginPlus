// FastLoginPlus — Dashboard v2
// Improvements: toast notifications, skeleton loaders, debounced search, row flash

// ── State ──────────────────────────────────────────
const token = localStorage.getItem('flp-token') || '';
const isDemo = localStorage.getItem('flp-demo') === '1';

if (!token) {
    location.href = 'index.html';
}

let currentTab = 'online';
let onlinePlayers = [];
let playersData = { players: [], total: 0, page: 1, size: 20, totalPages: 1 };
let bansData = [];
let statusRefreshInterval = null;
let lastOnlineSnapshot = '';
let searchDebounceTimer = null;
let isConnected = true;
let connectionCheckFailures = 0;
const MAX_FAILURES = 3; // Show disconnect after 3 consecutive failures

// ── Countdown Timer ──────────────────────────────
let countdownTimer = null;
let countdownSeconds = 5;

function startCountdown() {
    stopCountdown();
    countdownSeconds = 5;
    updateTimerDisplay();
    countdownTimer = setInterval(() => {
        countdownSeconds--;
        if (countdownSeconds <= 0) {
            statusInfo.setAttribute('data-timer', 'R');
            // Trigger actual data refresh for current tab
            if (currentTab === 'online') loadOnlinePlayers();
            else if (currentTab === 'antibot') { loadAntiBotStats(); loadBans(); }
            countdownSeconds = 5;
            setTimeout(() => {
                if (countdownTimer) updateTimerDisplay();
            }, 500);
        } else {
            updateTimerDisplay();
        }
    }, 1000);
}

function stopCountdown() {
    if (countdownTimer) {
        clearInterval(countdownTimer);
        countdownTimer = null;
    }
}

function updateTimerDisplay() {
    if (statusInfo) {
        statusInfo.setAttribute('data-timer', String(countdownSeconds));
    }
}

// ── Toast System ───────────────────────────────────
function showToast(type, message, duration) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    duration = duration || (type === 'success' ? 3000 : 5000);

    const icons = {
        success: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M16 6L8 14L4 10"/></svg>',
        error:   '<svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="10" cy="10" r="7"/><path d="M7 7l6 6M13 7l-6 6"/></svg>',
        warning: '<svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M10 2L2 18h16L10 2z"/><path d="M10 8v4M10 14.5v1"/></svg>',
        info:    '<svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="10" cy="10" r="7"/><path d="M10 6v5M10 13.5v1"/></svg>'
    };

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
        <div class="toast-icon">${icons[type] || icons.info}</div>
        <div class="toast-body">
            <div class="toast-message">${escapeHtml(message)}</div>
        </div>
        <button class="toast-close" aria-label="Close notification">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M3 3l8 8M11 3l-8 8"/>
            </svg>
        </button>
    `;

    container.appendChild(toast);

    const closeBtn = toast.querySelector('.toast-close');
    let timer;

    const remove = () => {
        clearTimeout(timer);
        toast.classList.add('removing');
        setTimeout(() => {
            if (toast.parentNode) toast.parentNode.removeChild(toast);
        }, 200);
    };

    closeBtn.addEventListener('click', remove);
    timer = setTimeout(remove, duration);
}

// ── Skeleton Loader ────────────────────────────────
function showTableSkeleton(tbody, columns) {
    columns = columns || 5;
    tbody.innerHTML = '';
    for (let i = 0; i < 5; i++) {
        const tr = document.createElement('tr');
        tr.className = 'skeleton-row';
        for (let j = 0; j < columns; j++) {
            const td = document.createElement('td');
            td.innerHTML = '<div class="skeleton-cell"></div>';
            tr.appendChild(td);
        }
        tbody.appendChild(tr);
    }
}

function hideTableSkeleton(tbody) {
    tbody.innerHTML = '';
}

// ── API Helper ─────────────────────────────────────
async function api(endpoint, options = {}) {
    if (isDemo) return mockApi(endpoint, options);

    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };

    try {
        const response = await fetch(`/api${endpoint}`, { ...options, headers });
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || `HTTP ${response.status}`);
        }

        // Connection successful
        updateConnectionStatus(true);
        return data;
    } catch (error) {
        if (error.message === 'Unauthorized') {
            logout();
            throw new Error(I18n.t('msg.authFailed'));
        }

        // Network error (not auth error) — track connection failures
        if (error.name === 'TypeError' || error.message.includes('Failed to fetch')) {
            connectionCheckFailures++;
            if (connectionCheckFailures >= MAX_FAILURES) {
                updateConnectionStatus(false);
            }
        }

        throw error;
    }
}

// ── Connection Status ──────────────────────────────
function updateConnectionStatus(connected) {
    const overlayEl = document.getElementById('disconnect-overlay');

    if (connected) {
        connectionCheckFailures = 0;
        if (!isConnected) {
            isConnected = true;
            if (statusInfo) {
                statusInfo.classList.remove('disconnected');
            }
            if (overlayEl) {
                overlayEl.style.display = 'none';
            }
            showToast('success', I18n.t('msg.connectionRestored'));
            startCountdown();
        }
    } else {
        isConnected = false;
        stopCountdown();
        if (statusInfo) {
            statusInfo.classList.add('disconnected');
            statusInfo.textContent = I18n.t('header.disconnected');
        }
        if (overlayEl) {
            overlayEl.style.display = 'flex';
        }
    }
}

// ── Reconnect ──────────────────────────────────────
function attemptReconnect() {
    const reconnectBtn = document.getElementById('reconnect-btn');
    if (reconnectBtn) {
        reconnectBtn.disabled = true;
        reconnectBtn.textContent = I18n.t('disconnect.reconnecting');
    }

    // Try to load status
    loadStatus().then(() => {
        updateConnectionStatus(true);
        if (reconnectBtn) {
            reconnectBtn.disabled = false;
            reconnectBtn.textContent = I18n.t('disconnect.reconnect');
        }
        // Reload current tab data
        switchTab(currentTab);
    }).catch(() => {
        if (reconnectBtn) {
            reconnectBtn.disabled = false;
            reconnectBtn.textContent = I18n.t('disconnect.reconnect');
        }
        showToast('error', I18n.t('msg.reconnectFailed'));
    });
}

// ── DOM Elements ───────────────────────────────────
const statusInfo = document.getElementById('status-info');
const logoutBtn = document.getElementById('logout-btn');
const tabs = document.querySelectorAll('.tab');
const tabContents = document.querySelectorAll('.tab-content');

// Sidebar (mobile)
const menuToggle = document.getElementById('menu-toggle');
const sidebar = document.getElementById('sidebar');
const sidebarOverlay = document.getElementById('sidebar-overlay');
const sidebarClose = document.getElementById('sidebar-close');

// Connection status
const disconnectOverlay = document.getElementById('disconnect-overlay');
const reconnectBtn = document.getElementById('reconnect-btn');

// Online tab
const refreshOnlineBtn = document.getElementById('refresh-online-btn');
const onlineCount = document.getElementById('online-count');
const onlineTbody = document.getElementById('online-tbody');
const onlineEmpty = document.getElementById('online-empty');

// Players tab
const searchInput = document.getElementById('search-input');
const searchBtn = document.getElementById('search-btn');
const resetSearchBtn = document.getElementById('reset-search-btn');
const playersTbody = document.getElementById('players-tbody');
const prevPageBtn = document.getElementById('prev-page-btn');
const nextPageBtn = document.getElementById('next-page-btn');
const pageInfo = document.getElementById('page-info');

// Anti-bot tab
const banCount = document.getElementById('ban-count');
const limitAction = document.getElementById('limit-action');
const banIpInput = document.getElementById('ban-ip-input');
const banDurationInput = document.getElementById('ban-duration-input');
const banBtn = document.getElementById('ban-btn');
const refreshBansBtn = document.getElementById('refresh-bans-btn');
const bansTbody = document.getElementById('bans-tbody');
const bansEmpty = document.getElementById('bans-empty');

// Modal
const confirmModal = document.getElementById('confirm-modal');
const confirmMessage = document.getElementById('confirm-message');
const confirmYes = document.getElementById('confirm-yes');
const confirmNo = document.getElementById('confirm-no');

// ── Init ───────────────────────────────────────────
(async () => {
    await I18n.init();

    // Language selector
    const langSelect = document.getElementById('lang-select');
    if (langSelect) {
        langSelect.value = I18n.getLang();
        langSelect.addEventListener('change', () => I18n.switchLang(langSelect.value));
    }

    // Escape key closes modal
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && confirmModal.classList.contains('active')) {
            closeModal();
        }
    });

    // Reconnect button
    if (reconnectBtn) {
        reconnectBtn.addEventListener('click', attemptReconnect);
    }

    loadStatus();
    // Restore saved tab, default to 'online'
    const savedTab = localStorage.getItem('flp-tab') || 'online';
    switchTab(savedTab);
    startCountdown();

    // Refresh status every 30s
    statusRefreshInterval = setInterval(loadStatus, 30000);
})();

// ── Logout ─────────────────────────────────────────
function logout() {
    stopCountdown();
    if (statusRefreshInterval) clearInterval(statusRefreshInterval);
    localStorage.removeItem('flp-token');
    localStorage.removeItem('flp-demo');
    localStorage.removeItem('flp-tab');
    location.href = 'index.html';
}

logoutBtn.addEventListener('click', logout);

// ── Sidebar (mobile) ───────────────────────────────
function openSidebar() {
    if (sidebar) sidebar.classList.add('open');
    if (sidebarOverlay) sidebarOverlay.classList.add('active');
}

function closeSidebar() {
    if (sidebar) sidebar.classList.remove('open');
    if (sidebarOverlay) sidebarOverlay.classList.remove('active');
}

if (menuToggle) menuToggle.addEventListener('click', openSidebar);
if (sidebarClose) sidebarClose.addEventListener('click', closeSidebar);
if (sidebarOverlay) sidebarOverlay.addEventListener('click', closeSidebar);

// ── Tabs ───────────────────────────────────────────
tabs.forEach(tab => {
    tab.addEventListener('click', () => {
        switchTab(tab.dataset.tab);
        closeSidebar(); // close sidebar on mobile when tab is clicked
    });
});

function switchTab(tabName) {
    currentTab = tabName;
    localStorage.setItem('flp-tab', tabName);
    tabs.forEach(t => {
        t.classList.toggle('active', t.dataset.tab === tabName);
        t.setAttribute('aria-selected', t.dataset.tab === tabName);
    });
    tabContents.forEach(tc => tc.classList.toggle('active', tc.id === `tab-${tabName}`));

    switch (tabName) {
        case 'online':
            loadOnlinePlayers();
            break;
        case 'players':
            loadPlayers();
            break;
        case 'antibot':
            loadAntiBotStats();
            loadBans();
            break;
    }
}

// ── Status ─────────────────────────────────────────
async function loadStatus() {
    try {
        const status = await api('/status');
        if (isConnected) {
            statusInfo.classList.remove('disconnected');
            statusInfo.setAttribute('data-timer', String(countdownSeconds));
            statusInfo.textContent = `v${status.version} | ${status.databaseType} | ${I18n.t('header.status.online')} ${status.onlinePlayers}`;
        }
    } catch (error) {
        console.error('Failed to load status:', error);
        // Directly mark as disconnected — don't rely on api()'s 3-failure counter
        // because the error may be HTTP 500 (not TypeError) or we may be on a tab
        // whose countdown timer doesn't trigger API calls.
        updateConnectionStatus(false);
    }
}

// ── Online Players ─────────────────────────────────
refreshOnlineBtn.addEventListener('click', loadOnlinePlayers);

async function loadOnlinePlayers() {
    try {
        onlinePlayers = await api('/online');
        renderOnlinePlayers();

        // Detect player list changes → refresh players database
        const snapshot = onlinePlayers.map(p => p.name).sort().join(',');
        if (snapshot !== lastOnlineSnapshot) {
            lastOnlineSnapshot = snapshot;
            loadPlayers();
        }
    } catch (error) {
        console.error('Failed to load online players:', error);
    }
}

function renderOnlinePlayers() {
    onlineCount.textContent = I18n.t('online.count', {count: onlinePlayers.length});

    if (onlinePlayers.length === 0) {
        onlineTbody.innerHTML = '';
        onlineEmpty.style.display = 'block';
        return;
    }

    onlineEmpty.style.display = 'none';
    onlineTbody.innerHTML = onlinePlayers.map(player => {
        const isPremium = player.type === 'premium';
        const isBedrock = player.type === 'bedrock';
        return `
        <tr>
            <td data-label="${I18n.t('online.col.name')}">${escapeHtml(player.name)}</td>
            <td data-label="${I18n.t('online.col.uuid')}"><code class="mono">${escapeHtml(player.uuid || '—')}</code></td>
            <td data-label="${I18n.t('online.col.mode')}">${isPremium ? `<span class="badge badge-premium">${I18n.t('badge.premium')}</span>` : `<span class="badge badge-cracked">${I18n.t('badge.cracked')}</span>`}</td>
            <td data-label="${I18n.t('online.col.client')}">${isBedrock ? `<span class="badge badge-bedrock">${I18n.t('floodgate.bedrock')}</span>` : `<span class="badge badge-java">${I18n.t('floodgate.java')}</span>`}</td>
            <td data-label="${I18n.t('online.col.ip')}" class="mono">${escapeHtml(player.lastIp || '-')}</td>
            <td data-label="${I18n.t('online.col.actions')}">${getOnlineActions(player)}</td>
        </tr>`;
    }).join('');

    onlineTbody.querySelectorAll('.btn-action').forEach(btn => {
        btn.addEventListener('click', () => handleOnlineAction(btn.dataset.action, btn.dataset.name));
    });
}

function getOnlineActions(player) {
    switch (player.type) {
        case 'premium':
            return `<button class="btn-action btn-warning" data-action="cracked" data-name="${escapeHtml(player.name)}">${I18n.t('online.action.switchCracked')}</button>`;
        case 'cracked':
            return `<span class="btn-group"><button class="btn-action btn-success" data-action="premium" data-name="${escapeHtml(player.name)}">${I18n.t('online.action.switchPremium')}</button><button class="btn-action btn-danger-action" data-action="delete" data-name="${escapeHtml(player.name)}">${I18n.t('online.action.delete')}</button></span>`;
        default:
            return '—';
    }
}

async function handleOnlineAction(action, name) {
    if (action === 'delete') {
        showConfirm(I18n.t('msg.deleteConfirm', {name}), async () => {
            try {
                await api(`/players/${encodeURIComponent(name)}`, { method: 'DELETE' });
                showToast('success', I18n.t('msg.deleteSuccess', {name: name}));
                loadOnlinePlayers();
            } catch (error) {
                showToast('error', I18n.t('msg.deleteFailed', {error: error.message}));
            }
        });
    } else {
        try {
            await api(`/players/${encodeURIComponent(name)}/${action}`, { method: 'PUT' });
            showToast('success', I18n.t('msg.updateSuccess', {name: name}));
            loadOnlinePlayers();
        } catch (error) {
            showToast('error', I18n.t('msg.actionFailed', {error: error.message}));
        }
    }
}

// ── Players Database ───────────────────────────────
searchBtn.addEventListener('click', () => { playersData.page = 1; loadPlayers(); });
resetSearchBtn.addEventListener('click', () => { searchInput.value = ''; playersData.page = 1; loadPlayers(); });

// Debounced search on input
searchInput.addEventListener('input', () => {
    clearTimeout(searchDebounceTimer);
    const query = searchInput.value.trim();
    if (query.length === 0 || query.length >= 2) {
        searchDebounceTimer = setTimeout(() => {
            playersData.page = 1;
            loadPlayers();
        }, 300);
    }
});

searchInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
        clearTimeout(searchDebounceTimer);
        playersData.page = 1;
        loadPlayers();
    }
});

prevPageBtn.addEventListener('click', () => { if (playersData.page > 1) { playersData.page--; loadPlayers(); } });
nextPageBtn.addEventListener('click', () => { if (playersData.page < playersData.totalPages) { playersData.page++; loadPlayers(); } });

async function loadPlayers() {
    try {
        showTableSkeleton(playersTbody, 7);

        const query = searchInput.value.trim();
        let endpoint = `/players?page=${playersData.page}&size=${playersData.size}`;
        if (query) endpoint += `&q=${encodeURIComponent(query)}`;
        playersData = await api(endpoint);

        hideTableSkeleton(playersTbody);
        renderPlayers();
    } catch (error) {
        hideTableSkeleton(playersTbody);
        showToast('error', I18n.t('msg.loadFailed'));
        console.error('Failed to load players:', error);
    }
}

function renderPlayers() {
    if (playersData.players.length === 0) {
        playersTbody.innerHTML = `<tr><td colspan="7" class="empty-state">${I18n.t('players.empty')}</td></tr>`;
    } else {
        playersTbody.innerHTML = playersData.players.map(player => `
            <tr>
                <td data-label="${I18n.t('players.col.name')}">${escapeHtml(player.name)}</td>
                <td data-label="${I18n.t('players.col.uuid')}"><code class="mono">${escapeHtml(player.id || '—')}</code></td>
                <td data-label="${I18n.t('players.col.mode')}">${player.premium ? `<span class="badge badge-premium">${I18n.t('badge.premium')}</span>` : `<span class="badge badge-cracked">${I18n.t('badge.cracked')}</span>`}</td>
                <td data-label="${I18n.t('players.col.floodgate')}">${getFloodgateBadge(player.floodgate)}</td>
                <td data-label="${I18n.t('players.col.ip')}" class="mono">${escapeHtml(player.lastIp || '—')}</td>
                <td data-label="${I18n.t('players.col.lastLogin')}">${formatDate(player.lastLogin)}</td>
                <td data-label="${I18n.t('players.col.actions')}">${getPlayerActions(player)}</td>
            </tr>
        `).join('');
    }

    pageInfo.textContent = I18n.t('players.page', {current: playersData.page, total: playersData.totalPages});
    prevPageBtn.disabled = playersData.page <= 1;
    nextPageBtn.disabled = playersData.page >= playersData.totalPages;

    playersTbody.querySelectorAll('.btn-action').forEach(btn => {
        btn.addEventListener('click', () => handlePlayerAction(btn.dataset.action, btn.dataset.name));
    });
}

function getPlayerActions(player) {
    const actions = [];
    if (player.premium) {
        actions.push(`<button class="btn-action btn-warning" data-action="cracked" data-name="${escapeHtml(player.name)}">${I18n.t('players.action.switchCracked')}</button>`);
    } else {
        actions.push(`<button class="btn-action btn-success" data-action="premium" data-name="${escapeHtml(player.name)}">${I18n.t('players.action.switchPremium')}</button>`);
        actions.push(`<button class="btn-action btn-danger-action" data-action="delete" data-name="${escapeHtml(player.name)}">${I18n.t('players.action.delete')}</button>`);
    }
    return actions.length > 1 ? `<span class="btn-group">${actions.join('')}</span>` : actions.join('');
}

async function handlePlayerAction(action, name) {
    if (action === 'delete') {
        showConfirm(I18n.t('msg.deleteConfirm', {name}), async () => {
            try {
                await api(`/players/${encodeURIComponent(name)}`, { method: 'DELETE' });
                showToast('success', I18n.t('msg.deleteSuccess', {name: name}));
                loadPlayers();
            } catch (error) {
                showToast('error', I18n.t('msg.deleteFailed', {error: error.message}));
            }
        });
    } else {
        try {
            await api(`/players/${encodeURIComponent(name)}/${action}`, { method: 'PUT' });
            showToast('success', I18n.t('msg.updateSuccess', {name: name}));
            loadPlayers();
        } catch (error) {
            showToast('error', I18n.t('msg.actionFailed', {error: error.message}));
        }
    }
}

// ── Anti-bot ───────────────────────────────────────
refreshBansBtn.addEventListener('click', loadBans);

banBtn.addEventListener('click', async () => {
    const ip = banIpInput.value.trim();
    const duration = parseInt(banDurationInput.value) || 300;

    if (!ip) {
        showToast('warning', I18n.t('msg.enterIp'));
        return;
    }

    try {
        await api('/antibot/ban', {
            method: 'POST',
            body: JSON.stringify({ ip, duration })
        });
        showToast('success', I18n.t('msg.banSuccess', {ip: ip}));
        banIpInput.value = '';
        banDurationInput.value = '';
        loadBans();
        loadAntiBotStats();
    } catch (error) {
        showToast('error', I18n.t('msg.banFailed', {error: error.message}));
    }
});

async function loadAntiBotStats() {
    try {
        const stats = await api('/antibot/stats');
        banCount.textContent = stats.banCount;
        limitAction.textContent = stats.action;
    } catch (error) {
        console.error('Failed to load anti-bot stats:', error);
    }
}

async function loadBans() {
    try {
        bansData = await api('/antibot/bans');
        renderBans();
    } catch (error) {
        console.error('Failed to load bans:', error);
    }
}

function renderBans() {
    if (bansData.length === 0) {
        bansTbody.innerHTML = '';
        bansEmpty.style.display = 'block';
        return;
    }

    bansEmpty.style.display = 'none';
    bansTbody.innerHTML = bansData.map(ban => `
        <tr>
            <td data-label="${I18n.t('antibot.bans.col.ip')}" class="mono">${escapeHtml(ban.ip)}</td>
            <td data-label="${I18n.t('antibot.bans.col.remaining')}">${formatDuration(ban.remainingMs)}</td>
            <td data-label="${I18n.t('antibot.bans.col.actions')}">
                <button class="btn-action btn-success btn-unban" data-ip="${escapeHtml(ban.ip)}">${I18n.t('antibot.bans.action.unban')}</button>
            </td>
        </tr>
    `).join('');

    bansTbody.querySelectorAll('.btn-unban').forEach(btn => {
        btn.addEventListener('click', async () => {
            try {
                await api(`/antibot/ban/${encodeURIComponent(btn.dataset.ip)}`, { method: 'DELETE' });
                showToast('success', I18n.t('msg.unbanSuccess', {ip: btn.dataset.ip}));
                loadBans();
                loadAntiBotStats();
            } catch (error) {
                showToast('error', I18n.t('msg.unbanFailed', {error: error.message}));
            }
        });
    });
}

// ── Modal ──────────────────────────────────────────
let currentConfirmCallback = null;

function showConfirm(message, onConfirm) {
    confirmMessage.textContent = message;
    confirmModal.classList.add('active');
    currentConfirmCallback = onConfirm;
    confirmYes.focus();
}

function closeModal() {
    confirmModal.classList.remove('active');
    currentConfirmCallback = null;
}

confirmYes.addEventListener('click', () => {
    if (currentConfirmCallback) {
        const cb = currentConfirmCallback;
        closeModal();
        cb();
    }
});

confirmNo.addEventListener('click', closeModal);

// Click backdrop to dismiss
confirmModal.querySelector('.modal-backdrop').addEventListener('click', closeModal);

// ── Mock Data (Demo Mode) ──────────────────────────
function mockApi(endpoint, options) {
    return new Promise(resolve => {
        setTimeout(() => resolve(mockData(endpoint, options)), 400);
    });
}

function mockData(endpoint, options) {
    if (endpoint === '/status') {
        return { version: '0.3.0-dev', databaseType: 'SQLite', onlinePlayers: 4 };
    }

    if (endpoint === '/online') {
        return [
            { name: 'Steve', uuid: '8667ba71-b85a-4004-af54-457a9734eed7', type: 'premium', lastIp: '192.168.1.100' },
            { name: 'Alex', uuid: '6ab43178-89fd-4905-97e5-7e71ef0632c2', type: 'cracked', lastIp: '10.0.0.5' },
            { name: 'Notch', uuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5', type: 'premium', lastIp: '172.16.0.1' },
            { name: 'SteveBedrock', uuid: null, type: 'bedrock', lastIp: null },
        ];
    }

    if (endpoint.startsWith('/players')) {
        const allPlayers = [
            { name: 'Steve', uuid: '8667ba71-b85a-4004-af54-457a9734eed7', premium: true, floodgate: 'FALSE', lastIp: '192.168.1.100', lastLogin: new Date().toISOString() },
            { name: 'Alex', uuid: '6ab43178-89fd-4905-97e5-7e71ef0632c2', premium: false, floodgate: 'FALSE', lastIp: '10.0.0.5', lastLogin: new Date(Date.now() - 120000).toISOString() },
            { name: 'Notch', uuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5', premium: true, floodgate: 'FALSE', lastIp: '172.16.0.1', lastLogin: new Date(Date.now() - 300000).toISOString() },
            { name: 'jeb_', uuid: '853c80ef-3c37-49fd-aa49-938b674adae6', premium: true, floodgate: 'FALSE', lastIp: '10.0.0.99', lastLogin: new Date(Date.now() - 86400000).toISOString() },
            { name: 'SteveBedrock', uuid: null, premium: false, floodgate: 'TRUE', lastIp: null, lastLogin: new Date(Date.now() - 600000).toISOString() },
            { name: 'TestPlayer', uuid: null, premium: false, floodgate: 'FALSE', lastIp: '192.168.1.200', lastLogin: new Date(Date.now() - 172800000).toISOString() },
        ];
        return { players: allPlayers, total: allPlayers.length, page: 1, size: 20, totalPages: 1 };
    }

    if (endpoint === '/antibot/stats') {
        return { banCount: 2, action: 'DELAY' };
    }

    if (endpoint === '/antibot/bans') {
        return [
            { ip: '45.33.32.156', remainingMs: 180000 },
            { ip: '104.236.228.48', remainingMs: 45000 },
        ];
    }

    return { success: true };
}

// ── Utility ────────────────────────────────────────
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function getLoginTypeBadge(type) {
    switch (type) {
        case 'premium':  return `<span class="badge badge-premium">${I18n.t('type.premium')}</span>`;
        case 'cracked':  return `<span class="badge badge-cracked">${I18n.t('type.cracked')}</span>`;
        case 'bedrock':  return `<span class="badge badge-bedrock">${I18n.t('type.bedrock')}</span>`;
        default:         return `<span class="badge badge-unknown">${I18n.t('msg.unknown')}</span>`;
    }
}

function getFloodgateBadge(state) {
    switch (state) {
        case 'FALSE':  return `<span class="badge badge-cracked">${I18n.t('floodgate.java')}</span>`;
        case 'TRUE':   return `<span class="badge badge-bedrock">${I18n.t('floodgate.bedrock')}</span>`;
        case 'LINKED': return `<span class="badge badge-bedrock">${I18n.t('floodgate.linked')}</span>`;
        default:       return `<span class="badge badge-unknown">${I18n.t('floodgate.unknown')}</span>`;
    }
}

function formatDate(instant) {
    if (!instant) return '—';
    return new Date(instant).toLocaleString();
}

function formatDuration(ms) {
    if (ms <= 0) return I18n.t('msg.expired');
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    if (hours > 0) return I18n.t('duration.hours', {h: hours, m: minutes % 60});
    if (minutes > 0) return I18n.t('duration.minutes', {m: minutes});
    return I18n.t('duration.seconds', {s: seconds});
}
