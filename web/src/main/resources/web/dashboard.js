// FastLoginPlus — Dashboard

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
let onlineRefreshInterval = null;
let antibotRefreshInterval = null;
let lastOnlineSnapshot = '';

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
        return data;
    } catch (error) {
        if (error.message === 'Unauthorized') {
            logout();
            throw new Error(I18n.t('msg.authFailed'));
        }
        throw error;
    }
}

// ── DOM Elements ───────────────────────────────────
const statusInfo = document.getElementById('status-info');
const logoutBtn = document.getElementById('logout-btn');
const tabs = document.querySelectorAll('.tab');
const tabContents = document.querySelectorAll('.tab-content');

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

    loadStatus();
    switchTab('online');
})();

// ── Logout ─────────────────────────────────────────
function logout() {
    stopOnlineRefresh();
    stopAntibotRefresh();
    localStorage.removeItem('flp-token');
    localStorage.removeItem('flp-demo');
    location.href = 'index.html';
}

logoutBtn.addEventListener('click', logout);

// ── Tabs ───────────────────────────────────────────
tabs.forEach(tab => {
    tab.addEventListener('click', () => switchTab(tab.dataset.tab));
});

function switchTab(tabName) {
    currentTab = tabName;
    tabs.forEach(t => {
        t.classList.toggle('active', t.dataset.tab === tabName);
        t.setAttribute('aria-selected', t.dataset.tab === tabName);
    });
    tabContents.forEach(tc => tc.classList.toggle('active', tc.id === `tab-${tabName}`));

    // Stop all auto-refresh, then start for the active tab
    stopOnlineRefresh();
    stopAntibotRefresh();

    switch (tabName) {
        case 'online':
            loadOnlinePlayers();
            startOnlineRefresh();
            break;
        case 'players':
            loadPlayers();
            break;
        case 'antibot':
            loadAntiBotStats();
            loadBans();
            startAntibotRefresh();
            break;
    }
}

// ── Status ─────────────────────────────────────────
async function loadStatus() {
    try {
        const status = await api('/status');
        statusInfo.textContent = `v${status.version} | ${status.databaseType} | ${I18n.t('header.status.online')}: ${status.onlinePlayers}`;
    } catch (error) {
        console.error('Failed to load status:', error);
    }
}

// ── Online Players ─────────────────────────────────
function startOnlineRefresh() {
    stopOnlineRefresh();
    onlineRefreshInterval = setInterval(() => {
        if (currentTab === 'online') loadOnlinePlayers();
    }, 5000);
}

function stopOnlineRefresh() {
    if (onlineRefreshInterval) {
        clearInterval(onlineRefreshInterval);
        onlineRefreshInterval = null;
    }
}

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
    onlineTbody.innerHTML = onlinePlayers.map(player => `
        <tr>
            <td>${escapeHtml(player.name)}</td>
            <td>${getLoginTypeBadge(player.type)}</td>
            <td class="mono">${escapeHtml(player.lastIp || '-')}</td>
            <td>${formatDate(player.lastLogin)}</td>
            <td>${getOnlineActions(player)}</td>
        </tr>
    `).join('');

    onlineTbody.querySelectorAll('.btn-action').forEach(btn => {
        btn.addEventListener('click', () => handleOnlineAction(btn.dataset.action, btn.dataset.name));
    });
}

function getOnlineActions(player) {
    switch (player.type) {
        case 'premium':
            return `<button class="btn-action btn-warning" data-action="cracked" data-name="${escapeHtml(player.name)}">${I18n.t('online.action.switchCracked')}</button>`;
        case 'cracked':
            return `
                <button class="btn-action btn-success" data-action="premium" data-name="${escapeHtml(player.name)}">${I18n.t('online.action.switchPremium')}</button>
                <button class="btn-action btn-danger-action" data-action="delete" data-name="${escapeHtml(player.name)}">${I18n.t('online.action.delete')}</button>
            `;
        default:
            return '—';
    }
}

async function handleOnlineAction(action, name) {
    if (action === 'delete') {
        showConfirm(I18n.t('msg.deleteConfirm', {name}), async () => {
            try {
                await api(`/players/${encodeURIComponent(name)}`, { method: 'DELETE' });
                loadOnlinePlayers();
            } catch (error) {
                alert(I18n.t('msg.deleteFailed', {error: error.message}));
            }
        });
    } else {
        try {
            await api(`/players/${encodeURIComponent(name)}/${action}`, { method: 'PUT' });
            loadOnlinePlayers();
        } catch (error) {
            alert(I18n.t('msg.actionFailed', {error: error.message}));
        }
    }
}

// ── Players Database ───────────────────────────────
searchBtn.addEventListener('click', () => { playersData.page = 1; loadPlayers(); });
resetSearchBtn.addEventListener('click', () => { searchInput.value = ''; playersData.page = 1; loadPlayers(); });
searchInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') { playersData.page = 1; loadPlayers(); } });

prevPageBtn.addEventListener('click', () => { if (playersData.page > 1) { playersData.page--; loadPlayers(); } });
nextPageBtn.addEventListener('click', () => { if (playersData.page < playersData.totalPages) { playersData.page++; loadPlayers(); } });

async function loadPlayers() {
    try {
        const query = searchInput.value.trim();
        let endpoint = `/players?page=${playersData.page}&size=${playersData.size}`;
        if (query) endpoint += `&q=${encodeURIComponent(query)}`;
        playersData = await api(endpoint);
        renderPlayers();
    } catch (error) {
        console.error('Failed to load players:', error);
    }
}

function renderPlayers() {
    if (playersData.players.length === 0) {
        playersTbody.innerHTML = `<tr><td colspan="7" class="empty-state">${I18n.t('players.empty')}</td></tr>`;
    } else {
        playersTbody.innerHTML = playersData.players.map(player => `
            <tr>
                <td>${escapeHtml(player.name)}</td>
                <td><code class="mono">${escapeHtml(player.uuid || '—')}</code></td>
                <td>${player.premium ? `<span class="badge badge-premium">${I18n.t('badge.premium')}</span>` : `<span class="badge badge-cracked">${I18n.t('badge.cracked')}</span>`}</td>
                <td>${getFloodgateBadge(player.floodgate)}</td>
                <td class="mono">${escapeHtml(player.lastIp || '—')}</td>
                <td>${formatDate(player.lastLogin)}</td>
                <td>${getPlayerActions(player)}</td>
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
    return actions.join(' ');
}

async function handlePlayerAction(action, name) {
    if (action === 'delete') {
        showConfirm(I18n.t('msg.deleteConfirm', {name}), async () => {
            try {
                await api(`/players/${encodeURIComponent(name)}`, { method: 'DELETE' });
                loadPlayers();
            } catch (error) {
                alert(I18n.t('msg.deleteFailed', {error: error.message}));
            }
        });
    } else {
        try {
            await api(`/players/${encodeURIComponent(name)}/${action}`, { method: 'PUT' });
            loadPlayers();
        } catch (error) {
            alert(I18n.t('msg.actionFailed', {error: error.message}));
        }
    }
}

// ── Anti-bot ───────────────────────────────────────
function startAntibotRefresh() {
    stopAntibotRefresh();
    antibotRefreshInterval = setInterval(() => {
        if (currentTab === 'antibot') {
            loadAntiBotStats();
            loadBans();
        }
    }, 5000);
}

function stopAntibotRefresh() {
    if (antibotRefreshInterval) {
        clearInterval(antibotRefreshInterval);
        antibotRefreshInterval = null;
    }
}

refreshBansBtn.addEventListener('click', loadBans);

banBtn.addEventListener('click', async () => {
    const ip = banIpInput.value.trim();
    const duration = parseInt(banDurationInput.value) || 300;

    if (!ip) {
        alert(I18n.t('msg.enterIp'));
        return;
    }

    try {
        await api('/antibot/ban', {
            method: 'POST',
            body: JSON.stringify({ ip, duration })
        });
        banIpInput.value = '';
        loadBans();
    } catch (error) {
        alert(I18n.t('msg.banFailed', {error: error.message}));
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
            <td class="mono">${escapeHtml(ban.ip)}</td>
            <td>${formatDuration(ban.remainingMs)}</td>
            <td>
                <button class="btn-action btn-success btn-unban" data-ip="${escapeHtml(ban.ip)}">${I18n.t('antibot.bans.action.unban')}</button>
            </td>
        </tr>
    `).join('');

    bansTbody.querySelectorAll('.btn-unban').forEach(btn => {
        btn.addEventListener('click', async () => {
            try {
                await api(`/antibot/ban/${encodeURIComponent(btn.dataset.ip)}`, { method: 'DELETE' });
                loadBans();
            } catch (error) {
                alert(I18n.t('msg.unbanFailed', {error: error.message}));
            }
        });
    });
}

// ── Modal ──────────────────────────────────────────
function showConfirm(message, onConfirm) {
    confirmMessage.textContent = message;
    confirmModal.classList.add('active');

    const handleYes = () => {
        confirmModal.classList.remove('active');
        confirmYes.removeEventListener('click', handleYes);
        onConfirm();
    };

    const handleNo = () => {
        confirmModal.classList.remove('active');
        confirmNo.removeEventListener('click', handleNo);
    };

    confirmYes.addEventListener('click', handleYes);
    confirmNo.addEventListener('click', handleNo);
}

// ── Mock Data (Demo Mode) ──────────────────────────
function mockApi(endpoint, options) {
    return new Promise(resolve => {
        setTimeout(() => resolve(mockData(endpoint, options)), 80);
    });
}

function mockData(endpoint, options) {
    if (endpoint === '/status') {
        return { version: '0.3.0-dev', databaseType: 'SQLite', onlinePlayers: 4 };
    }

    if (endpoint === '/online') {
        return [
            { name: 'Steve', type: 'premium', lastIp: '192.168.1.100', lastLogin: new Date().toISOString() },
            { name: 'Alex', type: 'cracked', lastIp: '10.0.0.5', lastLogin: new Date(Date.now() - 120000).toISOString() },
            { name: 'Notch', type: 'premium', lastIp: '172.16.0.1', lastLogin: new Date(Date.now() - 300000).toISOString() },
            { name: 'SteveBedrock', type: 'bedrock', lastIp: null, lastLogin: new Date(Date.now() - 600000).toISOString() },
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
