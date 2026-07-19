// FastLoginPlus — Login Page

const DEMO_TOKEN = 'demo';
const tokenInput = document.getElementById('token-input');
const loginBtn = document.getElementById('login-btn');
const loginError = document.getElementById('login-error');

// Initialize i18n first, then set up event listeners
(async () => {
    await I18n.init();

    // 已有 token → 直接跳 dashboard
    if (localStorage.getItem('flp-token')) {
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

    // Demo mode
    if (input === DEMO_TOKEN) {
        localStorage.setItem('flp-token', DEMO_TOKEN);
        localStorage.setItem('flp-demo', '1');
        location.href = 'dashboard.html';
        return;
    }

    if (input.length < 16) {
        loginError.textContent = I18n.t('login.error.minLength');
        return;
    }

    loginBtn.disabled = true;
    loginBtn.textContent = I18n.t('login.verifying');

    fetch('/api/status', {
        headers: { 'Authorization': 'Bearer ' + input }
    }).then(resp => {
        if (resp.ok) {
            localStorage.setItem('flp-token', input);
            localStorage.removeItem('flp-demo');
            location.href = 'dashboard.html';
        } else {
            loginError.textContent = I18n.t('login.error.invalid');
        }
    }).catch(() => {
        loginError.textContent = I18n.t('login.error.connectionFailed');
    }).finally(() => {
        loginBtn.disabled = false;
        loginBtn.textContent = I18n.t('login.connect');
    });
}
