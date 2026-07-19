// FastLoginPlus — Login Page

const DEMO_TOKEN = 'demo';
const tokenInput = document.getElementById('token-input');
const loginBtn = document.getElementById('login-btn');
const loginError = document.getElementById('login-error');

// 已有 token → 直接跳 dashboard
if (localStorage.getItem('flp-token')) {
    location.href = 'dashboard.html';
}

loginBtn.addEventListener('click', handleLogin);
tokenInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') handleLogin();
    loginError.textContent = '';
});

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
        loginError.textContent = 'Token 至少需要 16 个字符';
        return;
    }

    loginBtn.disabled = true;
    loginBtn.textContent = '验证中...';

    fetch('/api/status', {
        headers: { 'Authorization': 'Bearer ' + input }
    }).then(resp => {
        if (resp.ok) {
            localStorage.setItem('flp-token', input);
            localStorage.removeItem('flp-demo');
            location.href = 'dashboard.html';
        } else {
            loginError.textContent = 'Token 无效';
        }
    }).catch(() => {
        loginError.textContent = '无法连接到服务器';
    }).finally(() => {
        loginBtn.disabled = false;
        loginBtn.textContent = '连接';
    });
}
