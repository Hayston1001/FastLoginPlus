/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.core.shared;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import org.slf4j.Logger;

import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.craftapi.resolver.http.RotatingProxySelector;
import com.github.games647.fastlogin.core.CommonUtil;
import com.github.games647.fastlogin.core.ProxyAgnosticMojangResolver;
import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;
import com.github.games647.fastlogin.core.antibot.IpBanManager;
import com.github.games647.fastlogin.core.antibot.PerIpRateLimiter;
import com.github.games647.fastlogin.core.antibot.RateLimiter;
import com.github.games647.fastlogin.core.antibot.TickingRateLimiter;
import com.github.games647.fastlogin.core.antibot.TrustedIpSet;
import com.github.games647.fastlogin.core.UpdateChecker;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.hooks.DefaultPasswordGenerator;
import com.github.games647.fastlogin.core.hooks.PasswordGenerator;
import com.github.games647.fastlogin.core.storage.MySQLStorage;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.SQLiteStorage;
import com.google.common.base.Ticker;
import com.zaxxer.hikari.HikariConfig;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

/**
 * @param <P> GameProfile class
 * @param <C> CommandSender
 * @param <T> Plugin class
 */
public class FastLoginCore<P extends C, C, T extends PlatformPlugin<C>> {

    private static final long MAX_EXPIRE_RATE = 1_000_000;

    private final Map<String, String> localeMessages = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> pendingLogin = CommonUtil.buildCache(
            Duration.ofMinutes(5), -1
    );

    // concurrent set: proxy-side plugin-message listeners run on Netty
    // event-loop threads of different players (0.5.0/F025)
    private final Collection<UUID> pendingConfirms = ConcurrentHashMap.newKeySet();
    private final T plugin;

    private MojangResolver resolver;

    private Configuration config;

    /**
     * Bundled config template resource name. Backends (bukkit/folia) use the
     * full "config.yml"; proxies (bungee/velocity) select the trimmed
     * "config-proxy.yml" before {@link #load()}.
     */
    private String configTemplate = "config.yml";
    private SQLStorage storage;
    private AntiBotService antiBot;
    private PasswordGenerator<P> passwordGenerator = new DefaultPasswordGenerator<>();
    private AuthPlugin<P> authPlugin;
    private UpdateChecker updateChecker;

    public FastLoginCore(T plugin) {
        this.plugin = plugin;
    }

    /**
     * Auto-generate the web panel token when the panel is enabled but the
     * token is still empty.
     *
     * @param config the configuration to inspect and update
     * @return the generated token, or {@code null} when no token was generated
     */
    private String maybeGenerateWebToken(Configuration config) {
        if (!config.getBoolean("web.enabled")) {
            return null;
        }

        String token = config.getString("web.token");
        if (token != null && !token.isEmpty()) {
            return null;
        }

        token = generateRandomToken();
        config.set("web.token", token);
        // 0.6.0/F022: the token is deliberately not logged at INFO;
        // admins read it from config.yml
        plugin.getLog().debug("Web panel token auto-generated");
        return token;
    }

    public void load() {
        // 1. Load config first to determine language setting
        saveDefaultFile("config.yml", configTemplate);

        try {
            config = loadFile("config.yml");
        } catch (IOException ioEx) {
            plugin.getLog().error("Failed to load config.yml", ioEx);
            return;
        }

        // Auto-generate web panel token if empty (before ConfigRefresher so it gets persisted)
        String generatedWebToken = maybeGenerateWebToken(config);

        // Restore canonical comments and key order from the bundled template,
        // while preserving all user-modified values.
        try {
            ConfigRefresher.refresh(
                    getClass().getClassLoader(),
                    plugin.getPluginFolder().resolve("config.yml"),
                    config,
                    configTemplate);
        } catch (IOException ioEx) {
            plugin.getLog().warn("Could not refresh config.yml from template", ioEx);
            // non-fatal: continue with the config as-is
        }

        // Reload the refreshed config so the in-memory object matches the
        // canonical template structure (correct commenting, key order, etc.)
        try {
            config = loadFile("config.yml");
        } catch (IOException ioEx) {
            plugin.getLog().error("Failed to reload config.yml after refresh", ioEx);
            return;
        }

        // 0.6.0/F005: only claim the token was saved when it actually survived
        // the template refresh (a proxy template without a web section used to
        // drop it silently)
        if (generatedWebToken != null) {
            if (generatedWebToken.equals(config.getString("web.token"))) {
                plugin.getLog().info("Token has been saved to config.yml");
            } else {
                plugin.getLog().warn("Generated web panel token could not be written to config.yml");
            }
        }

        // 2. Determine language file based on config
        String language = config.getString("language");
        // 0.5.0/F049: the value is concatenated into a file path — reject
        // traversal/separator characters instead of writing outside the
        // plugin directory
        if (language == null || !language.matches("[a-zA-Z0-9_-]+")) {
            plugin.getLog().warn("Invalid language value '{}' — falling back to 'en'", language);
            language = "en";
        }
        String messagesFile = "messages_" + language + ".yml";
        String defaultMessagesFile = "messages_en.yml";

        // Always ensure the English default exists as fallback
        saveDefaultFile(defaultMessagesFile);

        // Save all built-in language files so users can see what's available
        saveDefaultFile("messages_zh.yml");

        // Save built-in webui language files
        saveDefaultFile("webui_en.json");
        saveDefaultFile("webui_zh.json");

        // Save the selected language file (falls back to English if not bundled)
        if (!language.equals("en") && !language.equals("zh")) {
            saveDefaultFile(messagesFile, defaultMessagesFile);
        }

        // Auto-fill missing keys from the English default
        appendMissingKeys(defaultMessagesFile, messagesFile);

        // 3. Load language file with English as default fallback
        try {
            Configuration messages = loadLanguageFile(messagesFile, defaultMessagesFile);

            messages.getKeys()
                    .stream()
                    .filter(key -> messages.get(key) != null)
                    .collect(toMap(identity(), messages::get))
                    .forEach((key, message) -> {
                        String colored = CommonUtil.translateColorCodes((String) message);
                        if (!colored.isEmpty()) {
                            localeMessages.put(key, colored.replace("/newline", "\n"));
                        }
                    });

            plugin.getLog().info("Loaded language file: {}", messagesFile);
        } catch (IOException ioEx) {
            plugin.getLog().error("Failed to load language file: {}", messagesFile, ioEx);
            return;
        }

        // Initialize the resolver based on the config parameter
        this.resolver = this.config.getBoolean("useProxyAgnosticResolver")
            ? new ProxyAgnosticMojangResolver() : new MojangResolver();

        antiBot = createAntiBotService(config.getSection("anti-bot"));
        // 0.5.0/F047: validate entries — a missing colon or non-numeric port
        // would otherwise crash the whole plugin startup
        Set<Proxy> proxies = new HashSet<>();
        for (String proxyEntry : config.getStringList("proxies")) {
            String[] parts = proxyEntry.split(":");
            if (parts.length != 2) {
                plugin.getLog().warn("Invalid proxies entry '{}' — expected host:port, skipping",
                        proxyEntry);
                continue;
            }
            try {
                proxies.add(new Proxy(Type.HTTP,
                        new InetSocketAddress(parts[0], Integer.parseInt(parts[1]))));
            } catch (NumberFormatException ex) {
                plugin.getLog().warn("Invalid proxies entry '{}' — port is not a number, skipping",
                        proxyEntry);
            }
        }

        Collection<InetAddress> addresses = new HashSet<>();
        for (String localAddress : config.getStringList("ip-addresses")) {
            try {
                addresses.add(InetAddress.getByName(localAddress.replace('-', '.')));
            } catch (UnknownHostException ex) {
                plugin.getLog().error("IP-Address is unknown to us", ex);
            }
        }

        // 0.5.0/F044: craftapi 0.8.1's setMaxNameRequests only stores the value
        // — its profile limiter keeps the built-in 600/10min.  Warn so admins
        // don't rely on the configured value.
        int mojangRequestLimit = config.getInt("mojang-request-limit");
        if (mojangRequestLimit != 600) {
            plugin.getLog().warn("mojang-request-limit is currently not applied by the bundled"
                    + " craftapi library (always {}) — configured value {} is ignored",
                    600, mojangRequestLimit);
        }
        resolver.setMaxNameRequests(mojangRequestLimit);
        resolver.setProxySelector(new RotatingProxySelector(proxies));
        resolver.setOutgoingAddresses(addresses);

        if (config.getBoolean("check-update")) {
            String currentVersion = plugin.getClass().getPackage().getImplementationVersion();
            if (currentVersion == null) {
                currentVersion = "unknown";
            }
            updateChecker = new UpdateChecker(plugin.getLog(), currentVersion, isDebug());
        }
    }

    /**
     * Validate an anti-bot limit value (0.5.0/F039): values below 1 either
     * dead-lock the check (limit 0 rejects everything) or are undefined —
     * fall back to the configured default instead.
     *
     * @param logger   the plugin logger
     * @param key      the config key (for the warning)
     * @param value    the configured value
     * @param fallback the default from the bundled config template
     * @return the validated value
     */
    static int validatedLimit(Logger logger, String key, int value, int fallback) {
        if (value < 1) {
            logger.warn("anti-bot.{} is {} — values below 1 break the check;"
                    + " falling back to {}", key, value, fallback);
            return fallback;
        }
        return value;
    }

    /**
     * Validate an anti-bot duration value (0.5.0/F039): zero or negative
     * durations make the associated window/ban ineffective — fall back to the
     * configured default instead.
     *
     * @param logger     the plugin logger
     * @param key        the config key (for the warning)
     * @param valueMs    the configured value in milliseconds
     * @param fallbackMs the default from the bundled config template
     * @return the validated value
     */
    static long validatedDurationMs(Logger logger, String key, long valueMs, long fallbackMs) {
        if (valueMs <= 0) {
            logger.warn("anti-bot.{} is {}ms — non-positive durations break the"
                    + " check; falling back to {}ms", key, valueMs, fallbackMs);
            return fallbackMs;
        }
        return valueMs;
    }

    private AntiBotService createAntiBotService(Configuration botSection) {
        Ticker ticker = Ticker.systemTicker();
        boolean enabled = botSection.getBoolean("enabled");

        // --- global rate limiter ---
        RateLimiter globalLimiter;
        if (enabled) {
            int maxCon = validatedLimit(plugin.getLog(), "connections",
                    botSection.getInt("connections"), 600);
            long expireTime = validatedDurationMs(plugin.getLog(), "expire",
                    botSection.getLong("expire") * 60 * 1_000L, 600_000L);
            if (expireTime > MAX_EXPIRE_RATE) {
                plugin.getLog().warn("anti-bot.expire is capped at {} minutes (was {} minutes)"
                                + " — the internal rate limiter cannot track longer windows",
                        MAX_EXPIRE_RATE / 60_000, expireTime / 60_000);
                expireTime = MAX_EXPIRE_RATE;
            }
            globalLimiter = new TickingRateLimiter(ticker, maxCon, expireTime);
        } else {
            globalLimiter = () -> true;
        }

        // --- action ---
        Action action = Action.Ignore;
        switch (botSection.getString("action")) {
            case "ignore":
                action = Action.Ignore;
                break;
            case "block":
                action = Action.Block;
                break;
            default:
                plugin.getLog().warn("Invalid anti bot action - defaulting to ignore");
        }

        // --- trusted IPs ---
        Set<InetAddress> trustedIps = new HashSet<>();
        for (String ip : botSection.getStringList("trusted-ips")) {
            try {
                trustedIps.add(InetAddress.getByName(ip));
            } catch (UnknownHostException ex) {
                plugin.getLog().warn("Invalid trusted-ips entry: {}", ip, ex);
            }
        }
        TrustedIpSet trustedIpSet = new TrustedIpSet(trustedIps);

        // --- per-IP rate limiter ---
        int burstLimit = validatedLimit(plugin.getLog(), "burst-limit",
                botSection.getInt("burst-limit"), 10);
        long burstWindowMs = validatedDurationMs(plugin.getLog(), "burst-window",
                botSection.getLong("burst-window") * 1_000L, 10_000L);
        int perIpConnLimit = validatedLimit(plugin.getLog(), "per-ip-connections",
                botSection.getInt("per-ip-connections"), 20);
        long perIpExpireMs = validatedDurationMs(plugin.getLog(), "per-ip-expire",
                botSection.getLong("per-ip-expire") * 60 * 1_000L, 300_000L);
        PerIpRateLimiter perIpLimiter = new PerIpRateLimiter(ticker, burstLimit, burstWindowMs,
                perIpConnLimit, perIpExpireMs);

        // --- IP ban manager ---
        long banDurationMs = validatedDurationMs(plugin.getLog(), "ban-duration",
                botSection.getLong("ban-duration") * 60 * 1_000L, 300_000L);
        IpBanManager ipBanManager = new IpBanManager(ticker);

        return new AntiBotService(plugin.getLog(), enabled, globalLimiter, action,
                trustedIpSet, ipBanManager, perIpLimiter, banDurationMs, ticker);
    }

    private Configuration loadFile(String fileName) throws IOException {
        ConfigurationProvider configProvider = ConfigurationProvider.getProvider(YamlConfiguration.class);

        // Defaults come from the SELECTED template (config.yml on backends,
        // config-proxy.yml on proxies) so the in-memory config matches the
        // trimmed on-disk file — dropped backend-only keys must not sneak
        // back in via the full template's defaults.
        Configuration defaults;
        try (InputStream defaultStream = getClass().getClassLoader().getResourceAsStream(configTemplate)) {
            defaults = configProvider.load(defaultStream);
        }

        Path file = plugin.getPluginFolder().resolve(fileName);

        Configuration config;
        try (Reader reader = Files.newBufferedReader(file)) {
            config = configProvider.load(reader, defaults);
        }

        // explicitly add keys here, because Configuration.getKeys doesn't return the keys from the default config
        for (String key : defaults.getKeys()) {
            config.set(key, config.get(key));
        }

        return config;
    }

    public MojangResolver getResolver() {
        return resolver;
    }

    public SQLStorage getStorage() {
        return storage;
    }

    public T getPlugin() {
        return plugin;
    }

    public void sendLocaleMessage(String key, C receiver) {
        String message = localeMessages.get(key);
        if (message != null) {
            plugin.sendMultiLineMessage(receiver, message);
        }
    }

    public String getMessage(String key) {
        return localeMessages.get(key);
    }

    // 0.5.0/F019 — floor for the HikariCP maxLifetime setting
    private static final long MIN_LIFETIME_MS = 300_000L;

    public boolean setupDatabase() {
        String type = config.getString("driver");

        HikariConfig databaseConfig = new HikariConfig();
        String database = config.getString("database");

        databaseConfig.setConnectionTimeout(config.getInt("timeout") * 1_000L);

        // 0.5.0/F019: HikariCP enforces a 30s minimum maxLifetime — values at
        // or near it retire every pooled connection almost immediately
        // (constant reconnect churn).  Clamp to a sane floor (0 = infinite,
        // which HikariCP supports and is left untouched).
        long lifetimeMs = config.getInt("lifetime") * 1_000L;
        if (lifetimeMs > 0 && lifetimeMs < MIN_LIFETIME_MS) {
            plugin.getLog().warn("Database lifetime is {}s — below the recommended minimum"
                            + " of {}s, causing constant connection churn; using {}s instead.",
                    lifetimeMs / 1_000, MIN_LIFETIME_MS / 1_000, MIN_LIFETIME_MS / 1_000);
            lifetimeMs = MIN_LIFETIME_MS;
        }
        databaseConfig.setMaxLifetime(lifetimeMs);

        if (type.contains("sqlite")) {
            storage = new SQLiteStorage(plugin, database, databaseConfig);
        } else {
            String host = (String) config.get("host");
            int port = ((Number) config.get("port")).intValue();
            boolean useSSL = (boolean) config.get("useSSL");

            if (useSSL) {
                boolean publicKeyRetrieval = config.getBoolean("allowPublicKeyRetrieval");
                String rsaPublicKeyFile = config.getString("ServerRSAPublicKeyFile");
                String sslMode = config.getString("sslMode");

                databaseConfig.addDataSourceProperty("allowPublicKeyRetrieval", publicKeyRetrieval);
                databaseConfig.addDataSourceProperty("serverRSAPublicKeyFile", rsaPublicKeyFile);
                databaseConfig.addDataSourceProperty("sslMode", sslMode);
            }

            databaseConfig.setUsername((String) config.get("username"));
            databaseConfig.setPassword(config.getString("password"));
            storage = new MySQLStorage(plugin, type, host, port, database, databaseConfig, useSSL);
        }

        try {
            storage.createTables();
            return true;
        } catch (Exception ex) {
            plugin.getLog().warn("Failed to setup database. Disabling plugin...", ex);
            // 0.5.0/F021: the HikariDataSource was already constructed — close
            // it here, because setEnabled(false) during onEnable suppresses
            // onDisable (and with it core.close()) on bukkit/folia
            if (storage != null) {
                storage.close();
                storage = null;
            }
            return false;
        }
    }

    public Configuration getConfig() {
        return config;
    }

    public boolean isDebug() {
        return config != null && (boolean) config.get("debug");
    }

    public PasswordGenerator<P> getPasswordGenerator() {
        return passwordGenerator;
    }

    @SuppressWarnings("unused")
    public void setPasswordGenerator(PasswordGenerator<P> passwordGenerator) {
        this.passwordGenerator = passwordGenerator;
    }

    public void addLoginAttempt(String ip, String username) {
        pendingLogin.put(ip + username, new Object());
    }

    public boolean hasFailedLogin(String ip, String username) {
        if (!(boolean) config.get("secondAttemptCracked")) {
            return false;
        }

        return pendingLogin.remove(ip + username) != null;
    }

    public Collection<UUID> getPendingConfirms() {
        return pendingConfirms;
    }

    public AuthPlugin<P> getAuthPluginHook() {
        return authPlugin;
    }

    public AntiBotService getAntiBotService() {
        return antiBot;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public int getUpdateCheckInterval() {
        return config.getInt("update-check-interval");
    }

    public void setAuthPluginHook(AuthPlugin<P> authPlugin) {
        this.authPlugin = authPlugin;
    }

    /**
     * Select which bundled template feeds config.yml generation and refresh.
     * Must be called before {@link #load()}.
     *
     * @param configTemplate resource name of the bundled template
     *                       (e.g. "config.yml", "config-proxy.yml")
     */
    public void setConfigTemplate(String configTemplate) {
        if (this.config != null) {
            throw new IllegalStateException(
                    "setConfigTemplate must be called before load()");
        }
        this.configTemplate = configTemplate;
    }

    public void saveDefaultFile(String fileName) {
        saveDefaultFile(fileName, fileName);
    }

    /**
     * Save a default file from the jar. If the file already exists, do nothing.
     * @param targetName the file name in the plugin folder
     * @param resourceFile the resource file name in the jar
     */
    public void saveDefaultFile(String targetName, String resourceFile) {
        Path dataFolder = plugin.getPluginFolder();

        try {
            Files.createDirectories(dataFolder);

            Path configFile = dataFolder.resolve(targetName);
            if (Files.notExists(configFile)) {
                try (InputStream defaultStream = getClass().getClassLoader().getResourceAsStream(resourceFile)) {
                    if (defaultStream != null) {
                        Files.copy(Objects.requireNonNull(defaultStream), configFile);
                        plugin.getLog().info("Created default file: {}", targetName);
                    } else {
                        plugin.getLog().warn("Bundled resource not found: {}", resourceFile);
                        // Only language files have an English fallback. A missing
                        // config template must not be replaced by a language file —
                        // let the subsequent load fail loudly instead.
                        if (targetName.startsWith("messages_")) {
                            saveDefaultFile(targetName, "messages_en.yml");
                        }
                    }
                }
            }
        } catch (IOException ioExc) {
            plugin.getLog().error("Cannot create plugin folder {}", dataFolder, ioExc);
        }
    }

    /**
     * Compare the user's language file against the default (English) and append any missing keys.
     * @param defaultFile the default resource file in the jar (e.g. messages_en.yml)
     * @param userFile the user's language file in the plugin folder (e.g. messages_zh.yml)
     */
    private void appendMissingKeys(String defaultFile, String userFile) {
        Path dataFolder = plugin.getPluginFolder();
        Path userFilePath = dataFolder.resolve(userFile);
        if (Files.notExists(userFilePath)) {
            return;
        }

        ConfigurationProvider configProvider = ConfigurationProvider.getProvider(YamlConfiguration.class);

        Configuration defaults;
        try (InputStream defaultStream = getClass().getClassLoader().getResourceAsStream(defaultFile)) {
            defaults = configProvider.load(defaultStream);
        } catch (IOException ioEx) {
            plugin.getLog().error("Cannot read default resource {}", defaultFile, ioEx);
            return;
        }

        Configuration userConfig;
        try (Reader reader = Files.newBufferedReader(userFilePath)) {
            userConfig = configProvider.load(reader);
        } catch (IOException ioEx) {
            plugin.getLog().error("Cannot read user file {}", userFile, ioEx);
            return;
        }

        Set<String> defaultKeys = new HashSet<>(defaults.getKeys());
        Set<String> userKeys = new HashSet<>(userConfig.getKeys());

        Set<String> missingKeys = new HashSet<>(defaultKeys);
        missingKeys.removeAll(userKeys);

        if (missingKeys.isEmpty()) {
            return;
        }

        StringBuilder appendBuilder = new StringBuilder();
        appendBuilder.append(System.lineSeparator());
        appendBuilder.append("# === Keys below are auto-added by FastLoginPlus ===").append(System.lineSeparator());
        for (String key : missingKeys) {
            Object value = defaults.get(key);
            String yamlValue = value instanceof String ? (String) value : String.valueOf(value);
            appendBuilder.append(key).append(": '").append(yamlValue.replace("'", "''")).append('\'')
                    .append(System.lineSeparator());
        }

        try {
            Files.write(userFilePath, appendBuilder.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND);
            plugin.getLog().info("Appended {} missing keys to {}", missingKeys.size(), userFile);
        } catch (IOException ioEx) {
            plugin.getLog().error("Cannot append missing keys to {}", userFile, ioEx);
        }
    }

    /**
     * Load a language file with the English default as fallback.
     * Keys from the default file are used when the language file doesn't have them.
     *
     * @param langFile the language file name in the plugin folder
     * @param defaultFile the default resource file name in the jar
     * @return the merged configuration
     * @throws IOException if the file cannot be read
     */
    private Configuration loadLanguageFile(String langFile, String defaultFile) throws IOException {
        ConfigurationProvider configProvider = ConfigurationProvider.getProvider(YamlConfiguration.class);

        // Load English defaults as the base
        Configuration defaults;
        try (InputStream defaultStream = getClass().getClassLoader().getResourceAsStream(defaultFile)) {
            defaults = configProvider.load(defaultStream);
        }

        Path filePath = plugin.getPluginFolder().resolve(langFile);

        Configuration config;
        if (Files.exists(filePath)) {
            try (Reader reader = Files.newBufferedReader(filePath)) {
                config = configProvider.load(reader, defaults);
            }
        } else {
            // Language file doesn't exist, use defaults only
            config = defaults;
        }

        // Explicitly add keys from defaults, because Configuration.getKeys()
        // doesn't return keys that only exist in the default config
        for (String key : defaults.getKeys()) {
            config.set(key, config.get(key));
        }

        return config;
    }

    private static String generateRandomToken() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void close() {
        plugin.getLog().info("Safely shutting down scheduler. This could take up to one minute.");

        if (storage != null) {
            storage.close();
        }
    }
}
