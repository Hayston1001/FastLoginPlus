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
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.core.message.ChangePremiumMessage;
import com.github.games647.fastlogin.core.message.DeletePremiumMessage;
import com.github.games647.fastlogin.core.shared.PendingRelayStore;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.GeyserImpl;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.comphenix.protocol.ProtocolLibrary;
import com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator;
import com.github.games647.fastlogin.bukkit.compat.AuthMeVersionDetector;
import com.github.games647.fastlogin.bukkit.command.FlpCommand;
import com.github.games647.fastlogin.bukkit.listener.AuthMeCommandGuard;
import com.github.games647.fastlogin.bukkit.listener.ConnectionListener;
import com.github.games647.fastlogin.bukkit.listener.protocollib.ProtocolLibListener;
import com.github.games647.fastlogin.bukkit.listener.protocolsupport.ProtocolSupportListener;
import com.github.games647.fastlogin.bukkit.listener.UpdateNotifyListener;
import com.github.games647.fastlogin.bukkit.task.DelayedAuthHook;
import com.github.games647.fastlogin.core.CommonUtil;
import com.github.games647.fastlogin.core.PremiumStatus;
import com.github.games647.fastlogin.core.UpdateChecker;
import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.hooks.bedrock.BedrockService;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.hooks.bedrock.GeyserService;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.shared.ForwardingAttributes;
import com.github.games647.fastlogin.core.shared.PlatformPlugin;

/**
 * Folia-adapted version of FastLoginBukkit.
 *
 * Uses FoliaScheduler (regionized schedulers) instead of BukkitScheduler.
 * Folia is based on Paper, so Paper-specific features (PaperCacheListener)
 * are always enabled.
 */
public class FastLoginBukkit extends JavaPlugin implements PlatformPlugin<CommandSender> {

    private final ConcurrentMap<String, BukkitLoginSession> loginSession = CommonUtil.buildCache(
            Duration.ofMinutes(1), -1
    );

    private final Map<UUID, PremiumStatus> premiumPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, FloodgateState> playerFloodgateState = new ConcurrentHashMap<>();
    private final Logger logger;

    private boolean serverStarted;
    private BungeeManager bungeeManager;
    private final FoliaScheduler scheduler;

    // 0.5.0/F073: the pending-relay retry chains are self-re-chaining virtual
    // threads the platform scheduler cannot cancel — they must stop themselves
    // when the plugin disables (a reload would otherwise accumulate chains of
    // dead plugin instances)
    private final AtomicBoolean relayChainsRunning = new AtomicBoolean(true);

    // 0.7.0/F16: true once FLP forced AuthMe's enablePremium and removed AuthMe's own
    // premium packet listener. AuthMe's /premium and /freemium stay executable in that
    // state but no longer converge with FLP's profile, so AuthMeCommandGuard intercepts
    // them for players who could actually run them.
    private boolean premiumTakeoverActive;

    // 0.7.0/F15: /authme reload re-registers AuthMe's own premium packet listener
    // behind FLP's back — the watchdog re-asserts the takeover every 5 seconds.
    // Folia cancels the task with the plugin, but a run that already passed its
    // flag check can still be about to hop to the global region; the second check
    // inside the sync task keeps that hop from throwing on a disabled plugin.
    private final AtomicBoolean watchdogRunning = new AtomicBoolean(true);
    private static final long WATCHDOG_PERIOD_SECONDS = 5L;

    // 0.5.0/F014: give up relaying after ~5 minutes (1s interval) and keep the
    // entry queued instead of retrying forever
    private static final int MAX_RELAY_ATTEMPTS = 300;
    private FastLoginCore<Player, CommandSender, FastLoginBukkit> core;
    private FloodgateService floodgateService;
    private GeyserService geyserService;

    private PremiumPlaceholder premiumPlaceholder;
    private SkinsRestorerCompat skinsRestorerCompat;

    private AuthMeVersionDetector authMeVersionDetector;
    private AuthMePremiumIntegrator authMePremiumIntegrator;

    // Durable queue for proxy relay messages that could not be sent because
    // no player was online to serve as the plugin-message carrier.
    private PendingRelayStore pendingRelayStore;

    public PendingRelayStore getPendingRelayStore() {
        return pendingRelayStore;
    }

    public FastLoginBukkit() {
        this.logger = CommonUtil.initializeLoggerService(getLogger());
        this.scheduler = new FoliaScheduler(this, logger);
    }

    @Override
    public void onEnable() {
        core = new FastLoginCore<>(this);
        core.load();

        authMeVersionDetector = new AuthMeVersionDetector();
        if (authMeVersionDetector.isAuthMePresent()) {
            authMePremiumIntegrator = new AuthMePremiumIntegrator(this, authMeVersionDetector);
            if (authMeVersionDetector.isAuthMe6()) {
                logger.info("AuthMe 6.0+ detected: v{}", authMeVersionDetector.getVersion());

                // FLP takes over premium verification: force enablePremium=true
                // (persisted to AuthMe's config.yml) and unregister AuthMe's
                // redundant PremiumVerificationPacketListener so FLP's ProtocolLib
                // listener is the sole Mojang verification source.
                // 0.5.0/F061: surface partial failures — AuthMe's packet listener may
                // still be registered, causing a double-interception conflict
                premiumTakeoverActive = authMePremiumIntegrator.enforceFlpPremiumControl();
                if (!premiumTakeoverActive) {
                    logger.warn("Failed to fully enforce FastLogin premium control in AuthMe 6.0"
                            + " — premium logins may conflict with AuthMe's own listener");
                }

                // 0.7.0/F15: /authme reload calls PacketEventsService.setup() again,
                // which re-registers the listener we just removed (its own
                // premiumVerificationRegistered flag is false). FLP cannot observe
                // that command and any login-time hook fires too late — the listener
                // acts on the first packets of the login. Poll instead.
                startPremiumListenerWatchdog();
            } else {
                logger.info("AuthMe 5.x detected: v{} — using standard FLP flow",
                    authMeVersionDetector.getVersion());
            }
        }

        if (getServer().getOnlineMode()) {
            logger.error("Server has to be in offline mode");
            setEnabled(false);
            return;
        }

        if (!initializeFloodgate()) {
            setEnabled(false);
            // 0.5.0/F009: setEnabled(false) invokes onDisable synchronously —
            // without this return the rest of onEnable would keep initializing
            // listeners/commands on a plugin Bukkit considers disabled
            return;
        }

        bungeeManager = new BungeeManager(this);
        bungeeManager.initialize();

        // Restore the durable relay queue after a restart and resume delivery.
        pendingRelayStore = new PendingRelayStore(getPluginFolder(), logger);
        if (pendingRelayStore.load()) {
            if (bungeeManager.isEnabled()) {
                logger.info("Restored {} pending toggle(s), {} pending delete(s) and {} pending"
                        + " premium notice(s) from disk; resuming relay",
                    pendingRelayStore.toggles().size(), pendingRelayStore.deletes().size(),
                    pendingRelayStore.premiumNotices().size());
                pendingRelayStore.toggles().keySet().forEach(this::scheduleToggleRelay);
                pendingRelayStore.deletes().forEach(this::scheduleDeleteRelay);
                pendingRelayStore.premiumNotices().keySet().forEach(this::schedulePremiumRelay);
            } else {
                logger.warn("Discarding {} pending relay(s): proxy support is disabled",
                    pendingRelayStore.toggles().size() + pendingRelayStore.deletes().size()
                        + pendingRelayStore.premiumNotices().size());
                pendingRelayStore.clearAll();
            }
        }

        PluginManager pluginManager = getServer().getPluginManager();
        if (bungeeManager.isEnabled()) {
            markInitialized();
        } else {
            if (!core.setupDatabase()) {
                setEnabled(false);
                return;
            }

            AntiBotService antiBotService = core.getAntiBotService();
            if (pluginManager.isPluginEnabled("ProtocolSupport")) {
                pluginManager.registerEvents(new ProtocolSupportListener(this, antiBotService), this);
            } else if (pluginManager.isPluginEnabled("ProtocolLib")) {
                ProtocolLibListener.register(this, antiBotService, core.getConfig().getBoolean("verifyClientKeys"));
            } else {
                logger.warn("Either ProtocolLib or ProtocolSupport have to be installed if you don't use BungeeCord");
                setEnabled(false);
                return;
            }
        }

        // Delay dependency setup using Folia's global region scheduler
        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> new DelayedAuthHook(this).run(), 5L);

        ConnectionListener connectionListener = new ConnectionListener(this);
        pluginManager.registerEvents(connectionListener, this);

        if (bungeeManager.isEnabled()) {
            org.bukkit.event.player.PlayerLoginEvent.getHandlerList().unregister(connectionListener);
            logger.info("Unregistered PlayerLoginEvent listener to avoid HorriblePlayerLoginEventHack");
        }

        registerPaperConfigureListener();

        // Folia is based on Paper — register PaperCacheListener to set skin during
        // AsyncPlayerPreLoginEvent before profile.complete(true) pulls from filledProfileCache.
        // forwardSkin is checked inside the listener — when false, skin setting is skipped.
        pluginManager.registerEvents(
                new com.github.games647.fastlogin.bukkit.listener.PaperCacheListener(this), this);

        // 0.7.0/F16: while the takeover is active, AuthMe's own /premium and /freemium
        // are a second entry point that silently diverges from FLP (ISS-12) — intercept
        // them and point the player at /flp. Inert on AuthMe 5.x and without AuthMe.
        pluginManager.registerEvents(new AuthMeCommandGuard(this), this);

        registerCommands();

        if (pluginManager.isPluginEnabled("PlaceholderAPI")) {
            premiumPlaceholder = new PremiumPlaceholder(this);
            premiumPlaceholder.register();
        }

        skinsRestorerCompat = new SkinsRestorerCompat(this);

        scheduleUpdateCheck();
    }

    private void registerCommands() {
        Optional.ofNullable(getCommand("flp")).ifPresent(c -> {
            FlpCommand flpCommand = new FlpCommand(this);
            c.setExecutor(flpCommand);
            c.setTabCompleter(flpCommand);
            logger.info("Registered /flp command (FlpCommand executor)");
        });
        if (getCommand("flp") == null) {
            logger.warn("Command /flp is not registered — plugin.yml 'commands: flp' missing "
                + "or the plugin was disabled before command registration");
        }
    }

    private void scheduleUpdateCheck() {
        UpdateChecker checker = core.getUpdateChecker();
        if (checker == null) {
            return;
        }

        // 0.5.0/F069: hours -> seconds (this API takes a TimeUnit, unlike the
        // bukkit tick-based one)
        long intervalSeconds = core.getUpdateCheckInterval() * 60L * 60L;

        // initial check after 3 seconds
        Bukkit.getAsyncScheduler().runDelayed(this, task -> {
            if (checker.checkForUpdates()) {
                String msg = core.getMessage("update-available");
                if (msg != null) {
                    logger.warn(msg.replace("%new%", checker.getLatestVersion())
                            .replace("%current%", checker.getCurrentVersion()));
                }
            }

            // schedule periodic re-check
            Bukkit.getAsyncScheduler().runAtFixedRate(this, t -> {
                if (checker.checkForUpdates()) {
                    String m = core.getMessage("update-available");
                    if (m != null) {
                        logger.warn(m.replace("%new%", checker.getLatestVersion())
                                .replace("%current%", checker.getCurrentVersion()));
                    }
                }
            }, intervalSeconds, intervalSeconds, java.util.concurrent.TimeUnit.SECONDS);
        }, 60L, java.util.concurrent.TimeUnit.SECONDS);

        getServer().getPluginManager().registerEvents(new UpdateNotifyListener(this), this);
    }

    /**
     * Re-asserts FLP's premium packet-listener takeover every 5 seconds.
     *
     * <p>AuthMe re-registers its own {@code PremiumVerificationPacketListener} on
     * {@code /authme reload}: {@code ReloadCommand} reloads the settings, which calls
     * {@code PacketEventsService.reload(settings)} and that ends in {@code setup()}. Since
     * FLP set {@code premiumVerificationRegistered} to false when it unregistered the
     * listener, {@code setup()} registers it again. FLP cannot observe the command, so the
     * takeover has to be re-asserted periodically.
     *
     * <p>Why poll instead of checking on login: the listener intercepts START and
     * ENCRYPTION_RESPONSE, the earliest login packets, so every event FLP could hook fires
     * too late for the connection being established. {@code unregisterPremiumPacketListener()}
     * is idempotent and stays silent when the listener is already gone, so a quiet server
     * logs nothing and a watchdog line always means a re-registration really happened.
     *
     * <p>Scheduled as a native fixed-rate async task (like the update check) rather than a
     * self-chaining {@code runAsyncDelayed} chain: the chain's delay is a {@code Thread.sleep}
     * on the async pool, so a permanent watchdog would occupy one Folia async thread forever.
     * The unregister itself hops to the global region — the same thread as the startup call,
     * since PacketEvents' listener registry is not verified to be thread-safe.
     */
    private void startPremiumListenerWatchdog() {
        Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
            if (!watchdogRunning.get()) {
                return;
            }
            scheduler.getSyncExecutor().execute(() -> {
                if (watchdogRunning.get()) {
                    authMePremiumIntegrator.unregisterPremiumPacketListener();
                }
            });
        }, WATCHDOG_PERIOD_SECONDS, WATCHDOG_PERIOD_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    private boolean initializeFloodgate() {
        // 0.5.0/F010: a plugin being present is not the same as being enabled —
        // a disabled (or not yet initialized) Geyser/floodgate leaves
        // getInstance() null and would NPE here, taking the whole plugin down.
        // Check the enabled state and degrade gracefully instead.
        if (getServer().getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            GeyserImpl geyser = GeyserImpl.getInstance();
            if (geyser != null) {
                geyserService = new GeyserService(geyser, core);
            } else {
                logger.warn("Geyser-Spigot is enabled but GeyserImpl is not initialized"
                        + " — skipping Geyser service integration");
            }
        }

        if (getServer().getPluginManager().isPluginEnabled("floodgate")) {
            FloodgateApi floodgateApi = FloodgateApi.getInstance();
            if (floodgateApi == null) {
                logger.warn("floodgate is enabled but FloodgateApi is not initialized"
                        + " — skipping Floodgate service integration");
                return true;
            }
            floodgateService = new FloodgateService(floodgateApi, core);

            // Check Floodgate config values and return
            return floodgateService.isValidFloodgateConfigString("autoLoginFloodgate")
                    && floodgateService.isValidFloodgateConfigString("allowFloodgateNameConflict");
        }

        return true;
    }

    @Override
    public void onDisable() {
        loginSession.clear();
        premiumPlayers.clear();
        playerFloodgateState.clear();

        // 0.5.0/F046: stop scheduling before closing shared resources
        scheduler.shutdown();
        // 0.5.0/F073: stop the self-chaining relay retry tasks
        relayChainsRunning.set(false);
        // 0.7.0/F15: stop the AuthMe premium-listener watchdog
        watchdogRunning.set(false);

        if (core != null) {
            core.close();
        }

        if (bungeeManager != null) {
            bungeeManager.cleanup();
        }

        if (premiumPlaceholder != null && getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                premiumPlaceholder.unregister();
            } catch (Exception | NoSuchMethodError exception) {
                logger.error("Failed to unregister placeholder", exception);
            }
        }

        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            ProtocolLibrary.getProtocolManager().getAsynchronousManager().unregisterAsyncHandlers(this);
        }
    }

    public FastLoginCore<Player, CommandSender, FastLoginBukkit> getCore() {
        return core;
    }

    /**
     * Whether FLP has taken over AuthMe's premium handling: {@code enablePremium} was forced
     * to true and AuthMe's own premium packet listener was removed.
     *
     * @return true if the AuthMe 6.0 takeover succeeded at startup
     */
    public boolean isPremiumTakeoverActive() {
        return premiumTakeoverActive;
    }

    public ConcurrentMap<String, BukkitLoginSession> getLoginSessions() {
        return loginSession;
    }

    public BukkitLoginSession getSession(InetSocketAddress address) {
        String id = getSessionId(address);
        return loginSession.get(id);
    }

    public String getSessionId(InetSocketAddress address) {
        return address.getAddress().getHostAddress() + ':' + address.getPort();
    }

    public void putSession(InetSocketAddress address, BukkitLoginSession session) {
        String id = getSessionId(address);
        loginSession.put(id, session);
    }

    public void removeSession(InetSocketAddress address) {
        String id = getSessionId(address);
        loginSession.remove(id);
    }

    public Map<UUID, PremiumStatus> getPremiumPlayers() {
        return premiumPlayers;
    }

    public Map<UUID, FloodgateState> getPlayerFloodgateState() {
        return playerFloodgateState;
    }

    public @NotNull PremiumStatus getStatus(@NotNull UUID onlinePlayer) {
        return premiumPlayers.getOrDefault(onlinePlayer, PremiumStatus.UNKNOWN);
    }

    public boolean isServerFullyStarted() {
        return serverStarted;
    }

    public void markInitialized() {
        this.serverStarted = true;
    }

    public BungeeManager getBungeeManager() {
        return bungeeManager;
    }

    @Override
    public Path getPluginFolder() {
        return getDataFolder().toPath();
    }

    @Override
    public Logger getLog() {
        return logger;
    }

    @Override
    public FoliaScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public void sendMessage(CommandSender receiver, String message) {
        receiver.sendMessage(message);
    }

    @Override
    public boolean isPluginInstalled(String name) {
        return Bukkit.getServer().getPluginManager().getPlugin(name) != null;
    }

    public FloodgateService getFloodgateService() {
        return floodgateService;
    }

    public GeyserService getGeyserService() {
        return geyserService;
    }

    public SkinsRestorerCompat getSkinsRestorerCompat() {
        return skinsRestorerCompat;
    }

    public AuthMeVersionDetector getAuthMeVersionDetector() {
        return authMeVersionDetector;
    }

    public AuthMePremiumIntegrator getAuthMePremiumIntegrator() {
        return authMePremiumIntegrator;
    }

    @Override
    public BedrockService<?> getBedrockService() {
        if (floodgateService != null) {
            return floodgateService;
        }
        return geyserService;
    }

    private void registerPaperConfigureListener() {
        logger.info("Attempting to register Paper configure listener...");
        try {
            Class<?> rawClass = Class.forName(
                "io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent");
            @SuppressWarnings("unchecked")
            Class<? extends org.bukkit.event.Event> eventClass =
                (Class<? extends org.bukkit.event.Event>) rawClass;
            Bukkit.getPluginManager().registerEvent(
                eventClass,
                new org.bukkit.event.Listener() { },
                EventPriority.LOWEST,
                (listener, event) -> onPlayerConfigure(event),
                this
            );
            logger.info("Registered Paper configure-phase listener for autoRegister");
        } catch (ClassNotFoundException e) {
            logger.info("Paper configure event not available — skipping");
        } catch (Exception e) {
            logger.warn("Failed to register Paper configure listener", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void onPlayerConfigure(Object event) {
        if (!bungeeManager.isEnabled() || !getConfig().getBoolean("autoRegister")) {
            return;
        }

        String playerName;
        java.util.UUID connectionUuid;
        java.net.InetSocketAddress address;
        // Retained for AuthMe 6.0.1's dialog session lookup — its pending responses
        // are keyed by a session id resolved from the connection object itself.
        final Object connection;
        // 0.7.0/F13: the proxy's verified Mojang UUID, forwarded as a GameProfile property.
        // Null when absent — cracked login, Floodgate, BungeeCord (no injection point) or an
        // older proxy.
        UUID forwardedPremiumUuid = null;
        try {
            connection = event.getClass().getMethod("getConnection").invoke(event);
            Object profile = connection.getClass().getMethod("getProfile").invoke(connection);
            playerName = (String) profile.getClass().getMethod("getName").invoke(profile);
            connectionUuid = (java.util.UUID) profile.getClass().getMethod("getId").invoke(profile);
            address = (java.net.InetSocketAddress) connection.getClass()
                .getMethod("getClientAddress").invoke(connection);
            forwardedPremiumUuid = readForwardedPremiumUuid(profile);
        } catch (Exception e) {
            logger.warn("Failed to extract player info from configure event", e);
            return;
        }

        // Pending toggles — cracked skips, premium allows despite UUID mismatch.
        // Cracked: the queued relay STAYS pending and is delivered by the retry
        // task once any player reaches the PLAY phase.
        Boolean pendingActivate = pendingRelayStore.getToggle(playerName);
        final boolean isPendingPremium = Boolean.TRUE.equals(pendingActivate);
        if (pendingActivate != null && !pendingActivate) {
            // The queued cracked toggle must still reach the proxy (its DB is
            // still premium) — do NOT consume the entry here.  Only skip the
            // AuthMe autoRegister; the message is delivered later by the retry
            // task (remove-if-present guards against double-send).
            logger.info("Skipping autoRegister for {}: pending cracked toggle (relay stays queued)",
                    playerName);
            // defensive: make sure a relay task exists for the still-queued entry
            scheduleToggleRelay(playerName);
            return;
        }

        // 0.7.0/F13 fast path. The proxy attested this connection as premium and forwarded
        // the Mojang UUID on the GameProfile, which the backend decoded in the login phase.
        // Run the whole auto-register synchronously: no Mojang lookup to wait for, so AuthMe's
        // HIGHEST handler cannot show a dialog first. The UUID-equality guard below does not
        // apply: a mismatch is the entire point of premiumUuid:false, and the attestation came
        // from the proxy (HMAC-protected by the forwarding secret), not from a name lookup.
        if (forwardedPremiumUuid != null) {
            logger.info("Proxy attested {} as premium ({}) in the configure phase",
                    playerName, forwardedPremiumUuid);
            applyPremiumAtConfigure(playerName, forwardedPremiumUuid, connectionUuid,
                    connection, address, isPendingPremium);
            return;
        }

        scheduler.runAsync(() -> {
            try {
                java.util.Optional<com.github.games647.craftapi.model.Profile> mojang =
                    core.getResolver().findProfile(playerName);
                if (!mojang.isPresent()) {
                    return;
                }
                java.util.UUID premiumUuid = mojang.get().getId();

                // Guard: if the player's connection UUID doesn't match the
                // Mojang premium UUID, the proxy assigned an offline UUID.
                // This means the player is either cracked, or the proxy uses
                // premiumUuid:false.  In either case we must NOT pre-create a
                // premium AuthMe record — that would re-register a cracked
                // player as premium behind the proxy's back.
                if (!premiumUuid.equals(connectionUuid)) {
                    if (!isPendingPremium) {
                        logger.info(
                            "Skipping autoRegister for {}: connection UUID {} != premium UUID {}",
                            playerName, connectionUuid, premiumUuid);
                        return;
                    }
                    logger.info(
                        "Pending premium toggle for {}: allowing autoRegister "
                            + "despite UUID mismatch ({} vs {})",
                        playerName, connectionUuid, premiumUuid);
                }

                applyPremiumAtConfigure(playerName, premiumUuid, connectionUuid,
                        connection, address, isPendingPremium);
            } catch (Exception e) {
                logger.warn("AutoRegister in configure phase failed for {}: {}",
                    playerName, e.getMessage());
            }
        });
    }

    /**
     * Reads the proxy-attested Mojang UUID off the forwarded GameProfile.
     *
     * <p>0.7.0/F13. Read reflectively: this module compiles against spigot-api, while the
     * profile and its properties are Paper types. Returns null unless the property is present
     * and holds a version-4 UUID — a malformed or non-v4 value is treated as "not attested",
     * falling back to the Mojang-lookup path, exactly as if the property were absent.</p>
     *
     * @param profile the Paper player profile from the configure event
     * @return the attested premium UUID, or null if the proxy attested nothing
     */
    private java.util.UUID readForwardedPremiumUuid(Object profile) {
        try {
            Object props = profile.getClass().getMethod("getProperties").invoke(profile);
            for (Object property : (java.util.Collection<?>) props) {
                String name = (String) property.getClass().getMethod("getName").invoke(property);
                if (!ForwardingAttributes.PREMIUM_UUID.equals(name)) {
                    continue;
                }
                String value = (String) property.getClass().getMethod("getValue").invoke(property);
                java.util.UUID uuid = java.util.UUID.fromString(value);
                // a real Mojang UUID is version 4; anything else is not an attestation
                return uuid.version() == 4 ? uuid : null;
            }
        } catch (Exception ignored) {
            // reflective read failed — treat as absent and fall back
        }
        return null;
    }

    /**
     * Marks the player as premium in AuthMe and seeds the login session, all inside the
     * configuration phase so AuthMe's preJoin dialogs are closed before they are created.
     * Shared by the F13 fast path (proxy-attested UUID) and the async Mojang-lookup path.
     *
     * @param playerName the connecting player's name
     * @param premiumUuid the verified Mojang UUID to stamp
     * @param connectionUuid the UUID the connection actually carries
     * @param connection the Paper connection object, for AuthMe's dialog session lookup
     * @param address the client address the session is keyed by
     * @param isPendingPremium whether a premium toggle is queued for relay
     */
    private void applyPremiumAtConfigure(String playerName, java.util.UUID premiumUuid,
                                         java.util.UUID connectionUuid, Object connection,
                                         java.net.InetSocketAddress address,
                                         boolean isPendingPremium) {
        com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator integrator =
            getAuthMePremiumIntegrator();
        if (integrator != null && integrator.isAuthMePremiumEnabled()) {
            integrator.injectVerifiedUuid(playerName, premiumUuid);
            integrator.markPlayerAsPremium(playerName, premiumUuid);
            // Close both register AND login dialogs.  AuthMe may show
            // a login dialog for existing records (cracked→premium)
            // if the async task hasn't updated the record yet.
            integrator.closePreJoinRegisterDialog(connectionUuid, connection);
            integrator.closePreJoinLoginDialog(connectionUuid, connection);
        }

        BukkitLoginSession session = new BukkitLoginSession(playerName, true);
        session.setUuid(premiumUuid);
        session.setVerifiedPremium(true);
        putSession(address, session);

        if (isPendingPremium) {
            Player carrier = Bukkit.getPlayerExact(playerName);
            if (carrier == null) {
                // Player not in the player list yet — the entry stays
                // queued and is delivered by the retry relay task once
                // any player reaches the PLAY phase.
                scheduleToggleRelay(playerName);
            } else {
                // Folia: EntityScheduler — kickPlayer/sendPluginMessage
                // must run on the player's own region thread.
                carrier.getScheduler().run(FastLoginBukkit.this, task -> {
                    if (carrier.isOnline() && bungeeManager.isEnabled()) {
                        // Read the CURRENT queued value at send time — the
                        // entry may have been overwritten by a newer toggle
                        // command, or already relayed by a retry task, since
                        // the configure phase ran.
                        Boolean pendingValue = pendingRelayStore.removeToggle(playerName);
                        if (pendingValue == null) {
                            return;
                        }
                        ChangePremiumMessage msg = new ChangePremiumMessage(
                                playerName, pendingValue, false);
                        bungeeManager.sendPluginMessage(carrier, msg);
                        if (getConfig().getBoolean("kick-toggle")) {
                            logger.info(
                                    "Relayed pending {} toggle for {} and kicking",
                                    pendingValue ? "premium" : "cracked", playerName);
                            carrier.kickPlayer(core.getMessage(
                                    pendingValue ? "add-premium" : "remove-premium"));
                        } else {
                            logger.info(
                                    "Relayed pending {} toggle for {} (kick disabled)",
                                    pendingValue ? "premium" : "cracked", playerName);
                        }
                    }
                }, null);
            }
        }
    }

    /**
     * Retries relaying a queued premium/cracked toggle message every second
     * until a player is online to serve as the relay channel. Folia has no
     * global repeating scheduler, so each retry chains a one-shot delayed task.
     *
     * @param target the player name to toggle
     */
    public void scheduleToggleRelay(String target) {
        scheduleToggleRelayAttempt(target, 0);
    }

    private void scheduleToggleRelayAttempt(String target, int attempt) {
        if (!relayChainsRunning.get()) {
            return;
        }
        scheduler.runAsyncDelayed(() -> {
            if (!relayChainsRunning.get()) {
                return;
            }
            Optional<? extends Player> optPlayer =
                getServer().getOnlinePlayers().stream().findFirst();
            if (!optPlayer.isPresent()) {
                // still nobody online — retry in another second, but stop after
                // MAX_RELAY_ATTEMPTS (the entry stays queued for the next start)
                if (attempt + 1 >= MAX_RELAY_ATTEMPTS) {
                    logger.warn("Gave up relaying pending toggle for {} after {} attempts"
                            + " — the entry stays queued and is retried after a restart",
                            target, attempt + 1);
                    return;
                }
                if (pendingRelayStore.containsToggle(target)) {
                    scheduleToggleRelayAttempt(target, attempt + 1);
                }
                return;
            }

            scheduler.getSyncExecutor().execute(() -> {
                Player sender = optPlayer.get();
                if (!sender.isOnline()) {
                    // The carrier quit between the async online check and this
                    // global-region task.  The entry is still queued (nothing
                    // was removed yet) — retry with a fresh task instead of
                    // sending into a dead connection and losing the toggle.
                    if (attempt + 1 >= MAX_RELAY_ATTEMPTS) {
                        logger.warn("Gave up relaying pending toggle for {} after {} attempts"
                                + " — the entry stays queued and is retried after a restart",
                                target, attempt + 1);
                        return;
                    }
                    if (pendingRelayStore.containsToggle(target)) {
                        scheduleToggleRelayAttempt(target, attempt + 1);
                    }
                    return;
                }
                // remove-if-present AND take the CURRENT queued value: the entry
                // may have been overwritten by a newer toggle command since this
                // task was scheduled — never send a stale captured value
                Boolean pendingValue = pendingRelayStore.removeToggle(target);
                if (pendingValue != null) {
                    try {
                        bungeeManager.sendPluginMessage(sender,
                                new ChangePremiumMessage(target, pendingValue, false));
                        logger.info("Relayed pending {} toggle for {}",
                            pendingValue ? "premium" : "cracked", target);
                    } catch (Exception ex) {
                        // send failed after removal — put the entry back and retry
                        pendingRelayStore.queueToggle(target, pendingValue);
                        scheduleToggleRelayAttempt(target, attempt + 1);
                        logger.warn("Failed to relay pending toggle for {} — requeued: {}",
                            target, ex.getMessage());
                    }
                }
            });
        }, Duration.ofSeconds(1));
    }

    /**
     * Retries relaying a queued delete message every second until a player is
     * online to serve as the relay channel. Folia has no global repeating
     * scheduler, so each retry chains a one-shot delayed task.
     *
     * @param targetName the player name to delete
     */
    public void scheduleDeleteRelay(String targetName) {
        scheduleDeleteRelayAttempt(targetName, 0);
    }

    private void scheduleDeleteRelayAttempt(String targetName, int attempt) {
        if (!relayChainsRunning.get()) {
            return;
        }
        scheduler.runAsyncDelayed(() -> {
            if (!relayChainsRunning.get()) {
                return;
            }
            Optional<? extends Player> optPlayer =
                getServer().getOnlinePlayers().stream().findFirst();
            if (!optPlayer.isPresent()) {
                // still nobody online — retry in another second, but stop after
                // MAX_RELAY_ATTEMPTS (the entry stays queued for the next start)
                if (attempt + 1 >= MAX_RELAY_ATTEMPTS) {
                    logger.warn("Gave up relaying pending delete for {} after {} attempts"
                            + " — the entry stays queued and is retried after a restart",
                            targetName, attempt + 1);
                    return;
                }
                if (pendingRelayStore.containsDelete(targetName)) {
                    scheduleDeleteRelayAttempt(targetName, attempt + 1);
                }
                return;
            }

            scheduler.getSyncExecutor().execute(() -> {
                Player sender = optPlayer.get();
                if (!sender.isOnline()) {
                    // carrier quit between the async check and this task — retry
                    if (attempt + 1 >= MAX_RELAY_ATTEMPTS) {
                        logger.warn("Gave up relaying pending delete for {} after {} attempts"
                                + " — the entry stays queued and is retried after a restart",
                                targetName, attempt + 1);
                        return;
                    }
                    if (pendingRelayStore.containsDelete(targetName)) {
                        scheduleDeleteRelayAttempt(targetName, attempt + 1);
                    }
                    return;
                }
                // remove-if-present: never double-send after another retry already relayed it
                if (pendingRelayStore.clearDelete(targetName)) {
                    try {
                        bungeeManager.sendPluginMessage(sender,
                                new DeletePremiumMessage(targetName, false));
                        logger.info("Relayed pending delete for {}", targetName);
                    } catch (Exception ex) {
                        // send failed after removal — put the entry back and retry
                        pendingRelayStore.queueDelete(targetName);
                        scheduleDeleteRelayAttempt(targetName, attempt + 1);
                        logger.warn("Failed to relay pending delete for {} — requeued: {}",
                            targetName, ex.getMessage());
                    }
                }
            });
        }, Duration.ofSeconds(1));
    }

    /**
     * Retries relaying a queued AuthMe premium notice ({@code premium.set}/{@code premium.unset})
     * until a player is online to serve as the message carrier. Folia has no global repeating
     * scheduler, so each retry chains a one-shot delayed task.
     *
     * <p>AuthMe's own notification cannot be delivered without a carrier and is dropped when
     * there is none, so this queue exists to keep FLP from reporting a sync that never happened.
     * The integrator owns the real decision: it re-checks the carrier and clears the entry only
     * on a successful send, so a player who disconnects mid-flight leaves the entry queued for
     * the next attempt.</p>
     *
     * @param target the player name the notice is about
     */
    public void schedulePremiumRelay(String target) {
        schedulePremiumRelayAttempt(target, 0);
    }

    private void schedulePremiumRelayAttempt(String target, int attempt) {
        if (!relayChainsRunning.get()) {
            return;
        }
        scheduler.runAsyncDelayed(() -> {
            if (!relayChainsRunning.get()) {
                return;
            }
            if (pendingRelayStore.getPremiumNotice(target) == null) {
                // delivered by the other call site (or by an admin toggle)
                return;
            }
            // 0.5.0/F014: stop after ~5 minutes of an empty server — the
            // entry stays queued and is retried after a restart
            if (attempt + 1 >= MAX_RELAY_ATTEMPTS) {
                logger.warn("Gave up relaying the pending AuthMe premium notice for {} after {}"
                        + " attempts — the entry stays queued and is retried after a restart",
                        target, attempt + 1);
                return;
            }

            Optional<? extends Player> optPlayer =
                getServer().getOnlinePlayers().stream().findFirst();
            if (!optPlayer.isPresent()) {
                // still nobody online — retry in another second
                schedulePremiumRelayAttempt(target, attempt + 1);
                return;
            }

            scheduler.getSyncExecutor().execute(() -> {
                Player sender = optPlayer.get();
                if (!sender.isOnline()) {
                    // The carrier quit between the async online check and this
                    // global-region task.  The entry is still queued — retry with
                    // a fresh task instead of sending into a dead connection.
                    schedulePremiumRelayAttempt(target, attempt + 1);
                    return;
                }
                Boolean isSet = pendingRelayStore.getPremiumNotice(target);
                if (isSet == null) {
                    return;
                }
                if (!relayPremiumNotice(target, isSet)) {
                    // the integrator re-queued it (carrier vanished, sender gone) — retry
                    schedulePremiumRelayAttempt(target, attempt + 1);
                }
            });
        }, Duration.ofSeconds(1));
    }

    /**
     * Hands one queued premium notice to the AuthMe integrator, which re-checks the carrier
     * itself and clears the queue entry when the message actually went out.
     *
     * @param target the player name the notice is about
     * @param isSet  true for {@code premium.set}, false for {@code premium.unset}
     * @return true if the entry is gone (delivered, or nothing left to talk to)
     */
    private boolean relayPremiumNotice(String target, boolean isSet) {
        AuthMePremiumIntegrator integrator = authMePremiumIntegrator;
        if (integrator == null) {
            // no AuthMe 6.0 on this server — the notice can never be delivered, so retire it
            // instead of retrying it for five minutes
            pendingRelayStore.removePremiumNotice(target);
            return true;
        }

        if (isSet) {
            integrator.notifyProxyPremiumSet(target);
        } else {
            integrator.notifyProxyPremiumUnset(target);
        }
        return !pendingRelayStore.containsPremiumNotice(target);
    }
}
