// FastLoginPlus — Theme Module
// Handles dark/light theme switching with localStorage persistence

const Theme = {
    _currentTheme: 'dark',

    /**
     * Initialize theme from localStorage or system preference.
     */
    init() {
        const saved = localStorage.getItem('flp-theme');
        if (saved) {
            this._currentTheme = saved;
        } else {
            // Check system preference
            this._currentTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
        }
        this._apply();
        this._setupToggle();
        // Enable theme-change transitions only after the first paint — applying
        // them earlier makes the initial dark->theme render fade in (flash)
        requestAnimationFrame(() => {
            requestAnimationFrame(() => document.documentElement.classList.add('theme-anim'));
        });
    },

    /**
     * Apply theme to document.
     */
    _apply() {
        document.documentElement.setAttribute('data-theme', this._currentTheme);
    },

    /**
     * Set up theme toggle button listener.
     */
    _setupToggle() {
        const toggleBtn = document.getElementById('theme-toggle');
        if (toggleBtn) {
            toggleBtn.addEventListener('click', () => this.toggle());
        }
    },

    /**
     * Toggle between dark and light themes.
     */
    toggle() {
        this._currentTheme = this._currentTheme === 'dark' ? 'light' : 'dark';
        localStorage.setItem('flp-theme', this._currentTheme);
        this._apply();
    },

    /**
     * Get current theme.
     */
    getTheme() {
        return this._currentTheme;
    }
};

// Initialize theme immediately
Theme.init();
