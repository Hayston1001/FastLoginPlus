// FastLoginPlus — Login Page v3

const DEMO_TOKEN = 'demo';
const tokenInput = document.getElementById('token-input');
const loginBtn = document.getElementById('login-btn');
const loginError = document.getElementById('login-error');

// Simple inline toast for login page (before i18n is fully loaded)
function showLoginError(message) {
    loginError.textContent = message;
    // Shake animation for visual feedback
    loginBtn.style.animation = 'none';
    loginBtn.offsetHeight; // trigger reflow
    loginBtn.style.animation = 'shake 0.4s var(--ease)';
}

// Initialize i18n first, then set up event listeners
(async () => {
    await I18n.init();

    // Language selector
    const langSelect = document.getElementById('lang-select');
    if (langSelect) {
        langSelect.value = I18n.getLang();
        langSelect.addEventListener('change', () => I18n.switchLang(langSelect.value));
    }

    // Already have token → go to dashboard (0.6.0/F042: demo session counts)
    if (localStorage.getItem('flp-token') || sessionStorage.getItem('flp-demo')) {
        location.href = 'dashboard.html';
        return;
    }

    loginBtn.addEventListener('click', handleLogin);
    tokenInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') handleLogin();
        loginError.textContent = '';
    });
})();

function handleLogin() {
    const input = tokenInput.value.trim();

    // Demo mode (0.6.0/F042: session-scoped - no cross-session residue)
    if (input === DEMO_TOKEN) {
        sessionStorage.setItem('flp-token', DEMO_TOKEN);
        sessionStorage.setItem('flp-demo', '1');
        location.href = 'dashboard.html';
        return;
    }

    if (input.length < 16) {
        showLoginError(I18n.t('login.error.minLength'));
        return;
    }

    loginBtn.disabled = true;
    loginBtn.textContent = I18n.t('login.verifying');

    fetch('/api/status', {
        headers: { 'Authorization': 'Bearer ' + input }
    }).then(resp => {
        if (resp.ok) {
            localStorage.setItem('flp-token', input);
            sessionStorage.removeItem('flp-demo');
            location.href = 'dashboard.html';
        } else if (resp.status === 429) {
            // 0.6.0/F043: distinguish failure classes instead of a blanket
            // 'Invalid token'
            showLoginError(I18n.t('login.error.rateLimited'));
        } else if (resp.status === 401 || resp.status === 403) {
            showLoginError(I18n.t('login.error.invalid'));
        } else {
            showLoginError(I18n.t('login.error.connectionFailed'));
        }
    }).catch(() => {
        showLoginError(I18n.t('login.error.connectionFailed'));
    }).finally(() => {
        loginBtn.disabled = false;
        loginBtn.textContent = I18n.t('login.login');
    });
}
