/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

import org.slf4j.Logger;

import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.craftapi.resolver.http.RotatingProxySelector;
import com.github.games647.fastlogin.core.CommonUtil;
import com.github.games647.fastlogin.core.ProxyAgnosticMojangResolver;
import com.github.games647.fastlogin.core.antibot.AntiBotService;
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
import com.github.hayston1001.fastloginplus.shared.config.ConfigurationFiles;
import com.github.hayston1001.fastloginplus.shared.config.ConfigRefresher;
import com.github.hayston1001.fastloginplus.shared.message.LanguageMessages;
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

    /** How often the settlement sweep looks for pending verifications. */
    private static final Duration VERIFICATION_SETTLEMENT_SWEEP = Duration.ofSeconds(5);

    /** How long one session-server reachability answer is reused. */
    static final long SESSION_PROBE_CACHE_MS = 60_000L;

    static final long VERIFICATION_GRACE_MS = 30_000L;

    private volatile Map<String, String> localeMessages = Collections.emptyMap();

    // One pending mark per case-insensitive name. Per-key compute operations
    // serialize settlement and success so a late success clears counted failures.
    private final ConcurrentMap<String, PendingVerification> pendingVerifications = new ConcurrentHashMap<>();
    private final LongSupplier verificationClock;

    // Settled definitive "not premium" verdicts — the only thing
    // that counts towards the same-name cracked observation. Bounded with a
    // long TTL: the confirmed observation itself lives in the storage row.
    private final ConcurrentMap<String, Integer> definiteFailures = CommonUtil.buildCache(
            Duration.ofHours(1), 10_000
    );

    // Session-server reachability probe (see SessionServerProbe);
    // created with the resolver in load()
    private SessionServerProbe sessionProbe;

    // The settlement sweep is a self-chaining delayed task (the same
    // idiom as the relay retry), started with the storage and stopped at close
    private volatile boolean settling;

    // one-time chat notices owed for a freshly confirmed same-name cracked
    // observation: queued at the first confirmation, delivered when
    // the player actually reaches the game — a login-phase connection cannot
    // see chat yet. The proxy's join task consumes it for proxy chat or hands
    // it to a capable Fabric backend for backend-local rendering. Keys are
    // names and an entry is only created
    // when the flag flips (one per name at a time, consumed at the first
    // completed join), so the map never expires — a player whose first attempt
    // died before joining still gets the notice on the next one. The size cap
    // is only a leak fuse for the theoretical flood of confirmed-but-never-
    // joining names; it should never trigger in practice.
    private final ConcurrentMap<String, Boolean> sameNameCrackedNotices = CommonUtil.buildCache(
            null, 100_000
    );

    // concurrent set: proxy-side plugin-message listeners run on Netty
    // event-loop threads of different players
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
        this(plugin, System::currentTimeMillis);
    }

    FastLoginCore(T plugin, LongSupplier verificationClock) {
        this.plugin = plugin;
        this.verificationClock = verificationClock;
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

        if (config.getSection("anti-bot").getKeys().contains("action")) {
            plugin.getLog().warn("anti-bot.action is no longer supported; anti-bot always blocks");
        }

        // Restore canonical comments and key order from the bundled template,
        // while preserving all user-modified values.
        try {
            List<String> templateLines = ConfigRefreshAdapter.readTemplateLines(
                    getClass().getClassLoader(), configTemplate);
            if (templateLines != null) {
                ConfigRefresher.refresh(plugin.getPluginFolder().resolve("config.yml"),
                        templateLines, ConfigRefreshAdapter.flattenConfig(config));
            }
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

        // 2. Determine language file based on config
        String language = config.getString("language");
        // the value is concatenated into a file path — reject
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

        // Save the selected language file (falls back to English if not bundled)
        if (!language.equals("en") && !language.equals("zh")) {
            saveDefaultFile(messagesFile, defaultMessagesFile);
        }

        // Upgrade from bundled selected-language values, with English only as fallback.
        appendMissingKeys(messagesFile, defaultMessagesFile);

        // 3. Load language file with English as default fallback
        loadLanguageMessages(messagesFile, defaultMessagesFile);

        // Initialize the resolver based on the config parameter
        this.resolver = this.config.getBoolean("useProxyAgnosticResolver")
            ? new ProxyAgnosticMojangResolver() : new MojangResolver();

        // the reachability probe uses the session server of that resolver
        this.sessionProbe = new SessionServerProbe(resolver, SESSION_PROBE_CACHE_MS);

        antiBot = createAntiBotService(config.getSection("anti-bot"));
        // validate entries — a missing colon or non-numeric port
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

        // the vendored craftapi honours this value now, so the old
        // "library ignores it, always 600" warning is gone.  Values outside 0..600 are clamped, and
        // 0 (never query Mojang directly) needs at least one proxy: without one every uncached
        // lookup would fail as rate-limited, i.e. every new premium player would be taken for cracked.
        int configuredLimit = config.getInt("mojang-request-limit", MojangResolver.MAX_NAME_REQUESTS_LIMIT);
        int mojangRequestLimit = Math.max(0,
                Math.min(MojangResolver.MAX_NAME_REQUESTS_LIMIT, configuredLimit));
        if (mojangRequestLimit != configuredLimit) {
            plugin.getLog().warn("mojang-request-limit {} is outside 0..{}, using {} instead",
                    configuredLimit, MojangResolver.MAX_NAME_REQUESTS_LIMIT, mojangRequestLimit);
        }

        if (mojangRequestLimit == 0 && proxies.isEmpty()) {
            plugin.getLog().warn("mojang-request-limit is 0 (never query Mojang directly) but no"
                    + " proxies are configured - using {} instead", MojangResolver.MAX_NAME_REQUESTS_LIMIT);
            mojangRequestLimit = MojangResolver.MAX_NAME_REQUESTS_LIMIT;
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
     * Validate an anti-bot limit value: values below 1 either
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
     * Validate an anti-bot duration value: zero or negative
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

        return new AntiBotService(plugin.getLog(), enabled, globalLimiter,
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

        return ConfigurationFiles.load(file, defaults, configurationLoader(configProvider));
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

    // Floor for the HikariCP maxLifetime setting
    private static final long MIN_LIFETIME_MS = 300_000L;

    public boolean setupDatabase() {
        String type = config.getString("driver");

        HikariConfig databaseConfig = new HikariConfig();
        String database = config.getString("database");

        databaseConfig.setConnectionTimeout(config.getInt("timeout") * 1_000L);

        // HikariCP enforces a 30s minimum maxLifetime — values at
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
            // config and storage are both in place now: align the stored
            // observation flags with the configured fallback semantics
            reconcileSameNameCracked();
            // the settlement sweep judges unverified attempts in time,
            // independent of when (or whether) the player reconnects
            startVerificationSettlement();
            return true;
        } catch (Exception ex) {
            plugin.getLog().warn("Failed to setup database. Disabling plugin...", ex);
            // the HikariDataSource was already constructed — close
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

    /**
     * Aligns the stored same-name cracked observations with the
     * configured {@code secondAttemptCracked} semantics.
     *
     * <p>The read side consults the config wherever the observation is used and
     * is authoritative ("switch off = as if never recorded"). This method is the
     * bookkeeping side: the config can change while the server is down, so there
     * is no runtime "changed true -&gt; false" event to catch — only reconciling
     * whenever config and storage are (re)initialized keeps the database
     * consistent, and keeps stale marks from resurfacing when an administrator
     * toggles the switch back and forth. With the fallback disabled the marks
     * are cleared ({@code SameNameCracked = 0}, only that column); with it
     * enabled nothing is touched.</p>
     */
    public void reconcileSameNameCracked() {
        reconcileSameNameCracked(storage,
                config != null && (boolean) config.get("secondAttemptCracked"), plugin.getLog());
    }

    /**
     * Decision half of {@link #reconcileSameNameCracked()}, split out so the
     * "cleared when off, untouched when on" contract is unit-testable.
     *
     * @param storage the storage holding the observation flags, may be null
     * @param fallbackEnabled the configured {@code secondAttemptCracked} value
     * @param log the logger for the reconciliation report
     */
    public static void reconcileSameNameCracked(SQLStorage storage, boolean fallbackEnabled,
                                                org.slf4j.Logger log) {
        if (storage == null || fallbackEnabled) {
            return;
        }

        int cleared = storage.clearSameNameCrackedObservations();
        if (cleared > 0) {
            log.info("Cleared {} same-name cracked observation(s): secondAttemptCracked is disabled,"
                    + " such names verify again", cleared);
        }
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

    /**
     * Records a requested verification for settlement after the authentication grace period.
     * Repeated requests within one connection share the original start time.
     *
     * @param username exact login name of the attempt
     */
    public void markPendingVerification(String username) {
        pendingVerifications.putIfAbsent(attemptKey(username), new PendingVerification(verificationClock.getAsLong()));
    }

    /**
     * Records a definitive "not premium" verdict for the name: the
     * session server explicitly rejected the verification. This is the only
     * observation that counts towards the same-name cracked record.
     *
     * @param username exact login name that failed the verification
     */
    public void recordDefiniteFailure(String username) {
        pendingVerifications.compute(attemptKey(username), (key, pending) -> {
            definiteFailures.merge(key, 1, Integer::sum);
            return null;
        });
    }

    /**
     * Drops a pending verification without counting it: the outcome was
     * "could not tell" — an authentication outage, or a player who left
     * mid-verification. Nothing is recorded.
     *
     * @param username exact login name whose attempt ended without a verdict
     */
    public void dropPendingVerification(String username) {
        pendingVerifications.remove(attemptKey(username));
    }

    /**
     * A passing verification refutes everything recorded for the name: the
     * pending mark and every counted failure.
     *
     * @param username exact login name that verified
     */
    public void resetVerificationState(String username) {
        pendingVerifications.compute(attemptKey(username), (key, pending) -> {
            definiteFailures.remove(key);
            return null;
        });
    }

    /**
     * How many definitive "not premium" verdicts were settled for the name
     * since the last passing verification. One is enough to confirm the
     * same-name cracked observation.
     *
     * @param username exact login name
     * @return the count of settled definitive failures
     */
    public int getDefiniteFailures(String username) {
        return definiteFailures.getOrDefault(attemptKey(username), 0);
    }

    /**
     * Settles pending verifications only after the authentication grace period.
     * The settlement is the moment the attempt is judged failed, and
     * it decides with a session-server reachability probe of that moment: the
     * server answers (the client itself could not verify) -> count one
     * definitive failure; it cannot be reached (an authentication outage) ->
     * drop the attempt without counting and without any action.
     */
    public void settlePendingVerifications() {
        long now = verificationClock.getAsLong();
        if (sessionProbe == null || pendingVerifications.values().stream().noneMatch(pending -> pending.isDue(now))) {
            return;
        }

        settlePendingVerifications(sessionProbe.isReachable(), now);
    }

    /**
     * Settles only this name's previous attempt before routing a new connection.
     * A previous attempt inside the grace period is dropped without counting.
     * The new attempt is marked later, only if routing requests verification.
     *
     * @param username exact login name of the returning player
     */
    public void settlePendingVerification(String username) {
        String key = attemptKey(username);
        PendingVerification pending = pendingVerifications.get(key);
        if (pending != null) {
            long now = verificationClock.getAsLong();
            boolean reachable = pending.isDue(now) && sessionProbe != null && sessionProbe.isReachable();
            settlePendingVerification(key, pending, reachable, now, true);
        }
    }

    /**
     * Decision half of {@link #settlePendingVerifications()}, split out so the
     * counting rules are unit-testable without waiting for real time.
     *
     * @param sessionServerReachable whether the session server answered the probe
     */
    void settlePendingVerifications(boolean sessionServerReachable) {
        settlePendingVerifications(sessionServerReachable, verificationClock.getAsLong());
    }

    private void settlePendingVerifications(boolean sessionServerReachable, long now) {
        pendingVerifications.forEach((username, pending) ->
                settlePendingVerification(username, pending, sessionServerReachable, now, false));
    }

    private void settlePendingVerification(String username, PendingVerification attempt,
                                           boolean sessionServerReachable, long now, boolean reconnect) {
        pendingVerifications.computeIfPresent(username, (key, pending) -> {
            // A probe result for an old attempt must not consume a replacement.
            if (pending != attempt) {
                return pending;
            }
            if (!pending.isDue(now)) {
                return reconnect ? null : pending;
            }
            if (sessionServerReachable) {
                definiteFailures.merge(key, 1, Integer::sum);
                plugin.getLog().warn("Premium verification for {} never completed while the session "
                        + "server answers: counted as a failed verification", key);
            } else {
                plugin.getLog().warn("Premium verification for {} never completed and the session "
                        + "server is unreachable: treated as an authentication outage, nothing recorded", key);
            }
            return null;
        });
    }

    private static final class PendingVerification {
        private final long startedAt;

        private PendingVerification(long startedAt) {
            this.startedAt = startedAt;
        }

        private boolean isDue(long now) {
            return now - startedAt >= VERIFICATION_GRACE_MS;
        }
    }

    /**
     * Starts the settlement sweep: a self-chaining delayed task, so the
     * settlement never degenerates into "when the player next connects".
     */
    public void startVerificationSettlement() {
        if (settling) {
            return;
        }

        settling = true;
        scheduleVerificationSettlement();
    }

    private void scheduleVerificationSettlement() {
        plugin.getScheduler().runAsyncDelayed(() -> {
            if (!settling) {
                return;
            }

            try {
                settlePendingVerifications();
            } catch (Exception ex) {
                plugin.getLog().warn("Failed to settle pending verifications", ex);
            }

            scheduleVerificationSettlement();
        }, VERIFICATION_SETTLEMENT_SWEEP);
    }

    private static String attemptKey(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }

    /**
     * Owes the name its one-time "recorded as cracked" chat notice: called when
     * the same-name cracked observation is confirmed for the first time.
     *
     * @param username exact login name
     */
    public void queueSameNameCrackedNotice(String username) {
        sameNameCrackedNotices.put(attemptKey(username), Boolean.TRUE);
    }

    /**
     * Whether the name still owes its one-time notice; the entry is consumed
     * either way so a notice is never delivered twice for one confirmation.
     *
     * @param username exact login name
     * @return true when the notice was queued and not yet delivered
     */
    public boolean consumeSameNameCrackedNotice(String username) {
        return sameNameCrackedNotices.remove(attemptKey(username)) != null;
    }

    /**
     * Checks pending notification work without consuming it during capability negotiation.
     *
     * @param username exact login name
     * @return whether the first-confirmation notice is still owed
     */
    public boolean hasSameNameCrackedNotice(String username) {
        return sameNameCrackedNotices.containsKey(attemptKey(username));
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
     * Appends localized missing keys and refreshes untouched defaults from older message versions.
     * @param userFile the user's language file in the plugin folder (e.g. messages_zh.yml)
     * @param defaultFile the English fallback resource in the jar
     */
    private void appendMissingKeys(String userFile, String defaultFile) {
        Path file = plugin.getPluginFolder().resolve(userFile);
        if (!Files.exists(file)) {
            return;
        }
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        try (InputStream english = getClass().getClassLoader().getResourceAsStream(defaultFile);
             InputStream selected = getClass().getClassLoader().getResourceAsStream(userFile)) {
            if (english == null) {
                throw new IOException("Missing bundled English messages");
            }
            Configuration defaults = provider.load(new InputStreamReader(english, StandardCharsets.UTF_8));
            Configuration localized = selected == null ? defaults
                    : provider.load(new InputStreamReader(selected, StandardCharsets.UTF_8));
            updateLanguageFile(userFile, defaultFile, defaults, localized);
        } catch (IOException | RuntimeException failure) {
            plugin.getLog().error("Cannot upgrade language file {}; it will be ignored",
                    userFile, failure);
        }
    }

    private void updateLanguageFile(String userFile, String defaultFile, Configuration defaults,
                                    Configuration localized) throws IOException {
        Path file = plugin.getPluginFolder().resolve(userFile);
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        int userVersion;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            userVersion = LanguageFileUpdater.version(provider.load(reader));
        }
        int currentVersion = LanguageFileUpdater.version(localized);
        Configuration previous = null;
        if (currentVersion > userVersion) {
            previous = loadMessageSnapshot(provider, userVersion, userFile, defaultFile);
            if (userVersion > 0 && previous == null) {
                plugin.getLog().warn("Cannot refresh {}: no snapshot for messages v{}", userFile, userVersion);
            }
        }
        LanguageFileUpdater.Result result = LanguageFileUpdater.update(
                file, defaults, localized, currentVersion, previous);
        if (result.added > 0) {
            plugin.getLog().info("Appended {} missing keys to {}", result.added, userFile);
        }
        if (result.refreshed > 0) {
            plugin.getLog().info("Refreshed {} unchanged message values to messages v{} in {}",
                    result.refreshed, currentVersion, userFile);
        }
    }

    private Configuration loadMessageSnapshot(ConfigurationProvider provider, int version,
                                              String userFile, String defaultFile) throws IOException {
        String archive = "messages/archive/" + version + "/";
        try (InputStream selected = getClass().getClassLoader().getResourceAsStream(archive + userFile)) {
            if (selected != null) {
                return provider.load(new InputStreamReader(selected, StandardCharsets.UTF_8));
            }
        }
        try (InputStream english = getClass().getClassLoader().getResourceAsStream(archive + defaultFile)) {
            return english == null ? null : provider.load(new InputStreamReader(english, StandardCharsets.UTF_8));
        }
    }

    /**
     * Loads the selected language and falls back to the bundled language when the user file
     * is missing or not valid YAML. A broken language file must not disable the plugin.
     *
     * @param messagesFile the user's language file name
     * @param defaultFile the English fallback resource in the jar
     */
    void loadLanguageMessages(String messagesFile, String defaultFile) {
        Path file = plugin.getPluginFolder().resolve(messagesFile);
        try {
            ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
            Configuration defaults;
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(defaultFile)) {
                if (input == null) {
                    throw new IOException("Missing bundled language defaults: " + defaultFile);
                }
                defaults = provider.load(input);
            }
            Configuration bundled = loadBundledLanguage(messagesFile, defaults, provider);
            ConfigurationFiles.Loader<Configuration> loader = new BungeeConfigurationLoader(provider) {
                @Override
                public Configuration load(Reader reader, Configuration fallback) throws IOException {
                    Configuration loaded = super.load(reader, fallback);
                    // Rendering validates message types before publishing any locale state.
                    renderLanguageMessages(loaded);
                    return loaded;
                }
            };
            LanguageMessages.Result<Configuration> result = LanguageMessages.loadWithFallback(
                    file, defaults, bundled, loader,
                    failure -> plugin.getLog().error(
                            "Invalid language file {}; falling back to bundled defaults", file, failure));
            localeMessages = renderLanguageMessages(result.getValue());
            if (result.isFallback()) {
                plugin.getLog().warn("Loaded bundled language fallback for {}", file);
            } else {
                plugin.getLog().info("Loaded language file: {}", messagesFile);
            }
        } catch (IOException | RuntimeException failure) {
            localeMessages = Collections.emptyMap();
            plugin.getLog().error("Failed to load bundled language messages for {}", file, failure);
        }
    }

    private Map<String, String> renderLanguageMessages(Configuration messages) {
        Map<String, String> rendered = new HashMap<>();
        LanguageMessages.populate(messages.getKeys(), messages::get, rendered);
        return Collections.unmodifiableMap(rendered);
    }

    private Configuration loadBundledLanguage(String messagesFile, Configuration defaults,
                                              ConfigurationProvider provider) throws IOException {
        try (InputStream selected = getClass().getClassLoader().getResourceAsStream(messagesFile)) {
            if (selected == null) {
                return defaults;
            }
            try (Reader reader = new InputStreamReader(selected, StandardCharsets.UTF_8)) {
                return ConfigurationFiles.includeDefaultKeys(provider.load(reader, defaults), defaults,
                        configurationLoader(provider));
            }
        }
    }

    static ConfigurationFiles.Loader<Configuration> configurationLoader(ConfigurationProvider provider) {
        return new BungeeConfigurationLoader(provider);
    }

    private static class BungeeConfigurationLoader implements ConfigurationFiles.Loader<Configuration> {
        private final ConfigurationProvider provider;

        BungeeConfigurationLoader(ConfigurationProvider provider) {
            this.provider = provider;
        }

        @Override
        public Configuration load(Reader reader, Configuration defaults) throws IOException {
            return provider.load(reader, defaults);
        }

        @Override
        public Collection<String> getKeys(Configuration config) {
            return config.getKeys();
        }

        @Override
        public Object get(Configuration config, String key) {
            return config.get(key);
        }

        @Override
        public void set(Configuration config, String key, Object value) {
            config.set(key, value);
        }
    }

    public void close() {
        settling = false;
        plugin.getLog().info("Safely shutting down scheduler. This could take up to one minute.");

        if (storage != null) {
            storage.close();
        }
    }
}
