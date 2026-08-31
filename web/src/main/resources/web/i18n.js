// FastLoginPlus — i18n Module

const I18n = {
    _lang: {},
    _fallback: {},
    _currentLang: 'en',
    _supportedLangs: ['en', 'zh'],  // 内置语言

    /**
     * Initialize i18n. Called on page load.
     * 1. Read saved preference from localStorage
     * 2. Fetch language JSON from server
     * 3. Fill all data-i18n elements
     */
    async init() {
        // Determine language: localStorage > server default
        const saved = localStorage.getItem('flp-lang');
        if (saved) {
            this._currentLang = saved;
        }

        // Load fallback (English) first
        try {
            const resp = await fetch('/api/lang/en');
            if (resp.ok) this._fallback = await resp.json();
        } catch (e) {
            console.warn('Failed to load fallback language', e);
        }

        // Load current language
        if (this._currentLang !== 'en') {
            try {
                const resp = await fetch(`/api/lang/${this._currentLang}`);
                if (resp.ok) {
                    this._lang = await resp.json();
                } else {
                    // Fallback to English if language not found
                    this._lang = this._fallback;
                    this._currentLang = 'en';
                }
            } catch (e) {
                this._lang = this._fallback;
                this._currentLang = 'en';
            }
        } else {
            this._lang = this._fallback;
        }

        this.applyToDom();
    },

    /**
     * Translate a key. Supports {placeholder} substitution.
     * @param {string} key - translation key
     * @param {Object} [params] - placeholder values, e.g. {name: 'Steve'}
     * @returns {string}
     */
    t(key, params) {
        let text = this._lang[key] || this._fallback[key] || key;
        if (params) {
            Object.keys(params).forEach(k => {
                    // 0.6.0/F040: function replacer - the previous
                    // string replacement interpreted $& / $' in values
                    text = text.replace(new RegExp(`\\{${k}\\}`, 'g'),
                        () => String(params[k]));
            });
        }
        return text;
    },

    /**
     * Apply translations to all elements with data-i18n attribute.
     * Supports data-i18n="key" (textContent) and data-i18n-placeholder="key" (placeholder).
     */
    applyToDom() {
        document.querySelectorAll('[data-i18n]').forEach(el => {
            const key = el.getAttribute('data-i18n');
            el.textContent = this.t(key);
        });
        document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
            const key = el.getAttribute('data-i18n-placeholder');
            el.placeholder = this.t(key);
        });
        document.querySelectorAll('[data-i18n-html]').forEach(el => {
            const key = el.getAttribute('data-i18n-html');
            el.innerHTML = this.t(key);
        });
        // 0.6.0/F035: translated aria-labels
        document.querySelectorAll('[data-i18n-aria]').forEach(el => {
            const key = el.getAttribute('data-i18n-aria');
            el.setAttribute('aria-label', this.t(key));
        });
    },

    /**
     * Switch language, save preference, reload page.
     */
    async switchLang(lang) {
        localStorage.setItem('flp-lang', lang);
        location.reload();
    },

    /**
     * Get the current language code.
     */
    getLang() {
        return this._currentLang;
    }
};
