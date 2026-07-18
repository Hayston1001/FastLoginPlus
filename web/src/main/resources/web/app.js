// FastLoginPlus Web Panel JavaScript

// State
let token = localStorage.getItem('flp-token') || '';
let currentTab = 'online';
let onlinePlayers = [];
let playersData = { players: [], total: 0, page: 1, size: 20, totalPages: 1 };
let bansData = [];
let onlineRefreshInterval = null;

// API Helper
async function api(endpoint, options = {}) {
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
            showLoginScreen();
            throw new Error('认证失败，请重新登录');
        }
        throw error;
    }
}

// DOM Elements
const loginScreen = document.getElementById('login-screen');
const mainScreen = document.getElementById('main-screen');
const tokenInput = document.getElementById('token-input');
const loginBtn = document.getElementById('login-btn');
const loginError = document.getElementById('login-error');
const logoutBtn = document.getElementById('logout-btn');
const statusInfo = document.getElementById('status-info');
const tabs = document.querySelectorAll('.tab');
const tabContents = document.querySelectorAll('.tab-content');

// Online tab elements
const refreshOnlineBtn = document.getElementById('refresh-online-btn');
const onlineCount = document.getElementById('online-count');
const onlineTbody = document.getElementById('online-tbody');
const onlineEmpty = document.getElementById('online-empty');

// Players tab elements
const searchInput = document.getElementById('search-input');
const searchBtn = document.getElementById('search-btn');
const resetSearchBtn = document.getElementById('reset-search-btn');
const playersTbody = document.getElementById('players-tbody');
const prevPageBtn = document.getElementById('prev-page-btn');
const nextPageBtn = document.getElementById('next-page-btn');
const pageInfo = document.getElementById('page-info');

// Anti-bot tab elements
const banCount = document.getElementById('ban-count');
const limitAction = document.getElementById('limit-action');
const banIpInput = document.getElementById('ban-ip-input');
const banDurationInput = document.getElementById('ban-duration-input');
const banBtn = document.getElementById('ban-btn');
const refreshBansBtn = document.getElementById('refresh-bans-btn');
const bansTbody = document.getElementById('bans-tbody');
const bansEmpty = document.getElementById('bans-empty');

// Modal elements
const confirmModal = document.getElementById('confirm-modal');
const confirmMessage = document.getElementById('confirm-message');
const confirmYes = document.getElementById('confirm-yes');
const confirmNo = document.getElementById('confirm-no');

// Login
loginBtn.addEventListener('click', handleLogin);
tokenInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') handleLogin();
});

function handleLogin() {
    const inputToken = tokenInput.value.trim();
    if (inputToken.length < 16) {
        loginError.textContent = 'Token 至少需要 16 个字符';
        return;
    }

    token = inputToken;
    // 先验证 token 是否有效，再保存
    fetch('/api/status', {
        headers: { 'Authorization': 'Bearer ' + token }
    }).then(resp => {
        if (resp.ok) {
            localStorage.setItem('flp-token', token);
            showMainScreen();
        } else {
            token = '';
            loginError.textContent = 'Token 无效';
        }
    }).catch(() => {
        token = '';
        loginError.textContent = '无法连接到服务器';
    });
}

logoutBtn.addEventListener('click', () => {
    token = '';
    localStorage.removeItem('flp-token');
    showLoginScreen();
});

function showLoginScreen() {
    loginScreen.classList.add('active');
    mainScreen.classList.remove('active');
    stopOnlineRefresh();
}

async function showMainScreen() {
    loginScreen.classList.remove('active');
    mainScreen.classList.add('active');
    loginError.textContent = '';
    tokenInput.value = '';

    await loadStatus();
    startOnlineRefresh();
    switchTab('online');
}

// Tabs
tabs.forEach(tab => {
    tab.addEventListener('click', () => {
        switchTab(tab.dataset.tab);
    });
});

function switchTab(tabName) {
    currentTab = tabName;

    tabs.forEach(t => t.classList.toggle('active', t.dataset.tab === tabName));
    tabContents.forEach(tc => tc.classList.toggle('active', tc.id === `tab-${tabName}`));

    // Load data for the tab
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

// Status
async function loadStatus() {
    try {
        const status = await api('/status');
        statusInfo.textContent = `v${status.version} | ${status.databaseType} | 在线: ${status.onlinePlayers}`;
    } catch (error) {
        console.error('Failed to load status:', error);
    }
}

// Online Players
function startOnlineRefresh() {
    stopOnlineRefresh();
    onlineRefreshInterval = setInterval(() => {
        if (currentTab === 'online') {
            loadOnlinePlayers();
        }
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
    } catch (error) {
        console.error('Failed to load online players:', error);
    }
}

function renderOnlinePlayers() {
    onlineCount.textContent = `在线玩家: ${onlinePlayers.length}`;

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
            <td>${escapeHtml(player.lastIp || '-')}</td>
            <td>${formatDate(player.lastLogin)}</td>
            <td>${getOnlineActions(player)}</td>
        </tr>
    `).join('');

    // Add event listeners to action buttons
    onlineTbody.querySelectorAll('.btn-action').forEach(btn => {
        btn.addEventListener('click', () => handleOnlineAction(btn.dataset.action, btn.dataset.name));
    });
}

function getOnlineActions(player) {
    switch (player.type) {
        case 'Java 正版':
            return `<button class="btn-sm btn-warning btn-action" data-action="cracked" data-name="${escapeHtml(player.name)}">切换为离线</button>`;
        case 'Java 离线':
            return `
                <button class="btn-sm btn-success btn-action" data-action="premium" data-name="${escapeHtml(player.name)}">切换为正版</button>
                <button class="btn-sm btn-danger btn-action" data-action="delete" data-name="${escapeHtml(player.name)}">删除</button>
            `;
        default:
            return '-';
    }
}

async function handleOnlineAction(action, name) {
    if (action === 'delete') {
        showConfirm(`确定要删除玩家 ${name} 的记录吗？`, async () => {
            try {
                await api(`/players/${encodeURIComponent(name)}`, { method: 'DELETE' });
                loadOnlinePlayers();
            } catch (error) {
                alert(`删除失败: ${error.message}`);
            }
        });
    } else {
        try {
            await api(`/players/${encodeURIComponent(name)}/${action}`, { method: 'PUT' });
            loadOnlinePlayers();
        } catch (error) {
            alert(`操作失败: ${error.message}`);
        }
    }
}

// Players Database
searchBtn.addEventListener('click', () => {
    playersData.page = 1;
    loadPlayers();
});

resetSearchBtn.addEventListener('click', () => {
    searchInput.value = '';
    playersData.page = 1;
    loadPlayers();
});

searchInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        playersData.page = 1;
        loadPlayers();
    }
});

prevPageBtn.addEventListener('click', () => {
    if (playersData.page > 1) {
        playersData.page--;
        loadPlayers();
    }
});

nextPageBtn.addEventListener('click', () => {
    if (playersData.page < playersData.totalPages) {
        playersData.page++;
        loadPlayers();
    }
});

async function loadPlayers() {
    try {
        const query = searchInput.value.trim();
        let endpoint = `/players?page=${playersData.page}&size=${playersData.size}`;
        if (query) {
            endpoint += `&q=${encodeURIComponent(query)}`;
        }

        playersData = await api(endpoint);
        renderPlayers();
    } catch (error) {
        console.error('Failed to load players:', error);
    }
}

function renderPlayers() {
    if (playersData.players.length === 0) {
        playersTbody.innerHTML = '<tr><td colspan="7" class="empty-state">暂无数据</td></tr>';
    } else {
        playersTbody.innerHTML = playersData.players.map(player => `
            <tr>
                <td>${escapeHtml(player.name)}</td>
                <td><code>${escapeHtml(player.uuid || '-')}</code></td>
                <td>${player.premium ? '<span class="badge badge-premium">正版</span>' : '<span class="badge badge-cracked">离线</span>'}</td>
                <td>${getFloodgateBadge(player.floodgate)}</td>
                <td>${escapeHtml(player.lastIp || '-')}</td>
                <td>${formatDate(player.lastLogin)}</td>
                <td>${getPlayerActions(player)}</td>
            </tr>
        `).join('');
    }

    // Update pagination
    pageInfo.textContent = `第 ${playersData.page} 页，共 ${playersData.totalPages} 页`;
    prevPageBtn.disabled = playersData.page <= 1;
    nextPageBtn.disabled = playersData.page >= playersData.totalPages;

    // Add event listeners
    playersTbody.querySelectorAll('.btn-action').forEach(btn => {
        btn.addEventListener('click', () => handlePlayerAction(btn.dataset.action, btn.dataset.name));
    });
}

function getPlayerActions(player) {
    const actions = [];
    if (player.premium) {
        actions.push(`<button class="btn-sm btn-warning btn-action" data-action="cracked" data-name="${escapeHtml(player.name)}">切换为离线</button>`);
    } else {
        actions.push(`<button class="btn-sm btn-success btn-action" data-action="premium" data-name="${escapeHtml(player.name)}">切换为正版</button>`);
        actions.push(`<button class="btn-sm btn-danger btn-action" data-action="delete" data-name="${escapeHtml(player.name)}">删除</button>`);
    }
    return actions.join(' ');
}

async function handlePlayerAction(action, name) {
    if (action === 'delete') {
        showConfirm(`确定要删除玩家 ${name} 的记录吗？`, async () => {
            try {
                await api(`/players/${encodeURIComponent(name)}`, { method: 'DELETE' });
                loadPlayers();
            } catch (error) {
                alert(`删除失败: ${error.message}`);
            }
        });
    } else {
        try {
            await api(`/players/${encodeURIComponent(name)}/${action}`, { method: 'PUT' });
            loadPlayers();
        } catch (error) {
            alert(`操作失败: ${error.message}`);
        }
    }
}

// Anti-bot
refreshBansBtn.addEventListener('click', loadBans);

banBtn.addEventListener('click', async () => {
    const ip = banIpInput.value.trim();
    const duration = parseInt(banDurationInput.value) || 300;

    if (!ip) {
        alert('请输入 IP 地址');
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
        alert(`封禁失败: ${error.message}`);
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
            <td>${escapeHtml(ban.ip)}</td>
            <td>${formatDuration(ban.remainingMs)}</td>
            <td>
                <button class="btn-sm btn-primary btn-unban" data-ip="${escapeHtml(ban.ip)}">解封</button>
            </td>
        </tr>
    `).join('');

    // Add event listeners
    bansTbody.querySelectorAll('.btn-unban').forEach(btn => {
        btn.addEventListener('click', async () => {
            try {
                await api(`/antibot/ban/${encodeURIComponent(btn.dataset.ip)}`, { method: 'DELETE' });
                loadBans();
            } catch (error) {
                alert(`解封失败: ${error.message}`);
            }
        });
    });
}

// Modal
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

// Utility functions
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function getLoginTypeBadge(type) {
    switch (type) {
        case 'Java 正版':
            return '<span class="badge badge-premium">Java 正版</span>';
        case 'Java 离线':
            return '<span class="badge badge-cracked">Java 离线</span>';
        case '基岩版':
            return '<span class="badge badge-bedrock">基岩版</span>';
        default:
            return '<span class="badge badge-unknown">未知</span>';
    }
}

function getFloodgateBadge(state) {
    switch (state) {
        case 'FALSE':
            return '<span class="badge badge-cracked">Java</span>';
        case 'TRUE':
            return '<span class="badge badge-bedrock">Bedrock</span>';
        case 'LINKED':
            return '<span class="badge badge-bedrock">Linked</span>';
        default:
            return '<span class="badge badge-unknown">Unknown</span>';
    }
}

function formatDate(instant) {
    if (!instant) return '-';
    const date = new Date(instant);
    return date.toLocaleString('zh-CN');
}

function formatDuration(ms) {
    if (ms <= 0) return '已过期';
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);

    if (hours > 0) {
        return `${hours}小时${minutes % 60}分钟`;
    }
    if (minutes > 0) {
        return `${minutes}分钟`;
    }
    return `${seconds}秒`;
}

// Initialize
if (token) {
    showMainScreen();
} else {
    showLoginScreen();
}
