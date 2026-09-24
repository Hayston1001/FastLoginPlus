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
import com.github.games647.fastlogin.core.shared.ProxyForwardedUuid;

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
import com.github.games647.fastlogin.bukkit.listener.AuthMeTakeoverListener;
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

    /**
     * Premium UUIDs observed on the profile at {@code AsyncPlayerPreLoginEvent}, keyed by player
     * name. Consumed during the configuration phase to prove that an attestation seen there
     * actually arrived with this login.
     *
     * <p>Paper keeps the fully filled profile — properties included — in its
     * {@code filledProfileCache}, indexed by both name and UUID. With {@code premiumUuid: false}
     * a premium login is rewritten to the name-derived offline UUID, i.e. the very UUID a cracked
     * login with the same name carries, so a later cracked connection can be handed that cached
     * profile and would be mistaken for proxy-attested premium. The pre-login profile is not
     * affected by that cache, so requiring the two to agree makes an attestation single-use.
     *
     * <p>The entry is overwritten or cleared on every pre-login and removed when consumed; a name
     * that never reaches configuration leaves at most one stale entry behind.
     */
    private final ConcurrentMap<String, UUID> preLoginAttestations = new ConcurrentHashMap<>();

    /**
     * How long an administrator's {@code /flp cracked} keeps suppressing premium marking for the
     * affected player, see {@link #crackedOverrides}.
     */
    private static final long CRACKED_OVERRIDE_TTL_MILLIS = 30_000L;

    /**
     * Names an administrator explicitly switched to cracked, with the instant it happened.
     *
     * <p>A login that was already in flight keeps a premium marking running on an async task; that
     * task can reach {@link #applyPremiumAtConfigure} <em>after</em> {@code /flp cracked} deleted
     * the record, and would then re-create it — with the Mojang UUID and no password, leaving the
     * player unable to log in or to register. The command therefore wins for a short window, which
     * only has to outlive a single login.
     */
    private final ConcurrentMap<String, Long> crackedOverrides = new ConcurrentHashMap<>();
    private final Logger logger;

    private boolean serverStarted;
    private BungeeManager bungeeManager;
    private final FoliaScheduler scheduler;

    // the pending-relay retry chains are self-re-chaining virtual
    // threads the platform scheduler cannot cancel — they must stop themselves
    // when the plugin disables (a reload would otherwise accumulate chains of
    // dead plugin instances)
    private final AtomicBoolean relayChainsRunning = new AtomicBoolean(true);

    // true once FLP forced AuthMe's enablePremium and removed AuthMe's own
    // premium packet listener. AuthMe's /premium and /freemium stay executable in that
    // state but no longer converge with FLP's profile, so AuthMeCommandGuard intercepts
    // them for players who could actually run them.
    private boolean premiumTakeoverActive;

    // give up relaying after ~5 minutes (1s interval) and keep the
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
                // surface partial failures — AuthMe's packet listener may
                // still be registered, causing a double-interception conflict
                premiumTakeoverActive = authMePremiumIntegrator.enforceFlpPremiumControl();
                if (!premiumTakeoverActive) {
                    logger.warn("Failed to fully enforce FastLogin premium control in AuthMe 6.0"
                            + " — premium logins may conflict with AuthMe's own listener");
                }

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
            // setEnabled(false) invokes onDisable synchronously —
            // without this return the rest of onEnable would keep initializing
            // listeners/commands on a plugin Bukkit considers disabled
            return;
        }

        bungeeManager = new BungeeManager(this);
        bungeeManager.initialize();

        // AuthMe's own verification is off while its command layer stays
        // registered. Direct connections only — see the method's javadoc. Proxy mode is part of
        // that decision, so this has to run after the manager above is initialized.
        warnOnAuthMeCommandLayerWithoutVerification();

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

        // intentionally not mirrored from the bukkit module: that change adds a
        // pre-login pre-create fallback for platforms WITHOUT a configuration phase (Spigot).
        // Folia is Paper-based and therefore always has the configuration phase, where
        // applyPremiumAtConfigure() already pre-creates the AuthMe record before AuthMe's join
        // handler can open its dialog - the fallback would be dead code here.

        // while the takeover is active, AuthMe's own /premium and /freemium
        // are a second entry point that silently diverges from FLP — intercept
        // them and point the player at /flp. Inert on AuthMe 5.x and without AuthMe.
        pluginManager.registerEvents(new AuthMeCommandGuard(this), this);

        // keep the takeover asserted across the events that can start
        // AuthMe's own premium listener again — see AuthMeTakeoverListener
        registerAuthMeTakeoverListener();

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

        // hours -> seconds (this API takes a TimeUnit, unlike the
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
     * Registers the hooks that keep FLP's AuthMe premium takeover asserted after the events that
     * can start AuthMe's own premium listener again.
     *
     * <p>The reload case is normally prevented at the source: FLP leaves AuthMe's "listener
     * registered" flag set, so {@code PacketEventsService.setup()} skips re-registration on
     * {@code /authme reload}. Two upstream paths still reset that flag (the packet library being
     * unloaded, and {@code enablePremium} turning false), and enabling the packet library re-runs
     * {@code setup()} from scratch — so the takeover is re-asserted after those events instead of
     * being polled for.
     *
     * <p>Registered whenever the takeover is active; the per-event conditions live in
     * {@link #reassertAuthMeTakeover(String)}, because a server can gain the packet library while
     * it is running.
     */
    private void registerAuthMeTakeoverListener() {
        if (!premiumTakeoverActive) {
            return;
        }

        getServer().getPluginManager().registerEvents(new AuthMeTakeoverListener(this), this);
        if (!bungeeManager.isEnabled()
                && getServer().getPluginManager().isPluginEnabled("packetevents")) {
            // only true here: on a proxy backend AuthMe never registers that listener, and
            // without the packet library it cannot
            logger.info("Took over AuthMe's premium packet verification — FLP is the sole "
                    + "Mojang verification source on this server");
        }
    }

    /**
     * Re-asserts FLP's AuthMe premium takeover one tick after an event that may have started
     * AuthMe's own packet listener.
     *
     * <p>The delay is the point: both event hooks fire <em>before</em> the work they announce —
     * the command has not executed yet, and AuthMe's own plugin-enable handler runs at HIGHEST
     * priority. Re-asserting inside the event would undo nothing. It runs in the global region,
     * the same thread as the startup call, because AuthMe's packet-listener registry is not
     * verified to be thread-safe.
     *
     * <p>Silent and cheap when AuthMe's listener cannot exist on this server (proxy backend, or no
     * packet library): there is nothing to keep removed, so nothing is logged and no task is
     * scheduled. Only reachable while the takeover is active — the listener is registered under
     * that condition.
     *
     * @param reason short description of the triggering event, used in the log line
     */
    public void reassertAuthMeTakeover(String reason) {
        if (authMePremiumIntegrator == null || bungeeManager == null || bungeeManager.isEnabled()
                || !getServer().getPluginManager().isPluginEnabled("packetevents")) {
            return;
        }

        logger.info("Detected {} — re-asserting FLP's AuthMe premium takeover", reason);
        Bukkit.getGlobalRegionScheduler().runDelayed(this,
            task -> authMePremiumIntegrator.unregisterPremiumPacketListener(), 1L);
    }

    /**
     * Warns when AuthMe's own premium verification is inactive while its command layer is
     * still registered: FLP forced {@code enablePremium=true}, but PacketEvents is
     * absent, so AuthMe's {@code setup()} gave up without resetting the setting and its
     * {@code /premium} and {@code /freemium} commands stay usable. FastLoginPlus verifies
     * premium logins itself and {@link AuthMeCommandGuard} redirects those commands to
     * {@code /flp}, but upstream's "Premium auto-login is disabled" warning reads as if the
     * whole feature were off — this line states what actually happened, so admins do not
     * chase a FastLoginPlus failure that does not exist.
     *
     * <p><b>Direct connections only (2026-09-15).</b> Proxy backends are excluded on purpose.
     * There AuthMe's listener never registers at all ({@code setup()} requires
     * {@code !isProxyMode}), so nothing surprising happened; the upstream warning this message
     * exists to disambiguate is not printed there either; and the command layer is already
     * covered by {@link AuthMeCommandGuard}. Emitting it anyway put a WARN on every startup of
     * an AuthMe 6.0 proxy network without PacketEvents, including one sentence about an
     * upstream warning that cannot appear in that setup.
     *
     * <p>Called after {@code bungeeManager.initialize()}, because proxy mode is part of the
     * decision. A server that disables FLP before that point (online mode, Floodgate failure)
     * therefore gets no such line — which is correct: "FLP intercepts them" would not be true
     * there, and those paths log an ERROR of their own.
     */
    private void warnOnAuthMeCommandLayerWithoutVerification() {
        if (!shouldWarnAboutCommandLayer(premiumTakeoverActive,
                getServer().getPluginManager().isPluginEnabled("packetevents"),
                bungeeManager.isEnabled())) {
            return;
        }

        logger.warn("AuthMe's enablePremium was forced to true, but PacketEvents is "
                + "not installed — AuthMe's own premium verification stays off. "
                + "FastLoginPlus verifies premium logins itself, so this is not a "
                + "FastLoginPlus failure. AuthMe's /premium and /freemium remain "
                + "registered; FastLoginPlus intercepts them and points players at "
                + "/flp. An upstream 'Premium auto-login is disabled' warning refers "
                + "to AuthMe's own verification, not to FastLoginPlus. Consider "
                + "denying authme.player.premium and authme.player.freemium.");
    }

    /**
     * Whether the PacketEvents warning applies on this server.
     *
     * <p>Extracted as a pure function for the same reason as this codebase's other decision
     * helpers: the test setup cannot mock {@code FastLoginBukkit} (ByteBuddy cannot instrument
     * the {@code JavaPlugin} hierarchy on this JDK), so a three-way condition can only be
     * pinned by a static predicate.
     *
     * @param takeoverActive      whether FLP forced {@code enablePremium} and removed AuthMe's listener
     * @param packetEventsEnabled whether the {@code packetevents} plugin is enabled
     * @param proxyMode           whether this backend sits behind a proxy
     * @return true only for a direct connection whose AuthMe verification is off <em>because</em>
     *         PacketEvents is missing
     */
    static boolean shouldWarnAboutCommandLayer(boolean takeoverActive,
            boolean packetEventsEnabled, boolean proxyMode) {
        return takeoverActive && !packetEventsEnabled && !proxyMode;
    }

    private boolean initializeFloodgate() {
        // a plugin being present is not the same as being enabled —
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

        // stop scheduling before closing shared resources
        scheduler.shutdown();
        // stop the self-chaining relay retry tasks
        relayChainsRunning.set(false);

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
        // the proxy's verified Mojang UUID, forwarded as a GameProfile property.
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
            // an attestation only counts when this very login already carried it,
            // before Paper's profile cache could have supplied a stale profile.
            UUID configureAttestation = forwardedPremiumUuid;
            UUID preLoginAttestation = preLoginAttestations.remove(playerName);
            forwardedPremiumUuid = resolveAttestedUuid(configureAttestation, preLoginAttestation);
            if (configureAttestation != null && forwardedPremiumUuid == null) {
                logger.warn("Ignoring premium attestation for {} — it was not present on this "
                        + "login's pre-login profile; refusing a possible profile-cache replay",
                        playerName);
            }
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

        // fast path. The proxy attested this connection as premium and forwarded
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

        // with premiumUuid: true the proxy keeps the Mojang UUID and therefore attaches
        // no attestation property (see the Velocity ConnectListener), so the attestation is the
        // UUID itself — an offline UUID is always version 3, so a version-4 connection UUID can
        // only have come from the proxy. Mark the record synchronously here, exactly like the
        // property path above: the asynchronous Mojang lookup below is what used to let AuthMe's
        // HIGHEST handler open its preJoin dialog before the record existed, which the player saw
        // as the dialog staying on screen for the length of that lookup. The same structural
        // signal already drives the pre-login path on Spigot, where this phase does not exist.
        if (usesForwardedUuidAttestation(forwardedPremiumUuid, connectionUuid, playerName)) {
            logger.info("Proxy forwarded the Mojang UUID {} for {} in the configure phase — "
                    + "marking the AuthMe record synchronously (no Mojang lookup needed)",
                    connectionUuid, playerName);
            applyPremiumAtConfigure(playerName, connectionUuid, connectionUuid,
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
                        // say why this is normal. The mismatch is expected whenever
                        // the proxy hands out the offline UUID — a cracked player, or a premium
                        // player under premiumUuid:false on a fallback path (Bungee, or a proxy
                        // too old for the profile attestation); it is not a failure.
                        logger.info(
                            "Skipping autoRegister for {}: connection UUID {} != premium UUID {}"
                                + " (expected when the proxy assigns the offline UUID — a cracked"
                                + " player, or premiumUuid:false)",
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
     * <p>Read reflectively: this module compiles against spigot-api, while the
     * profile and its properties are Paper types. Returns null unless the property is present
     * and holds a version-4 UUID — a malformed or non-v4 value is treated as "not attested",
     * falling back to the Mojang-lookup path, exactly as if the property were absent.</p>
     *
     * <p>Both proxy transports use this carrier but spell the name differently: Velocity's
     * modern forwarding keeps {@link ForwardingAttributes#PREMIUM_UUID}, while BungeeCord's
     * legacy handshake needs {@link ForwardingAttributes#PREMIUM_UUID_LEGACY} because Paper
     * filters legacy property names. Either one counts as an attestation.</p>
     *
     * @param profile the Paper player profile from the configure event
     * @return the attested premium UUID, or null if the proxy attested nothing
     */
    private java.util.UUID readForwardedPremiumUuid(Object profile) {
        try {
            Object props = profile.getClass().getMethod("getProperties").invoke(profile);
            for (Object property : (java.util.Collection<?>) props) {
                String name = (String) property.getClass().getMethod("getName").invoke(property);
                if (!ForwardingAttributes.isPremiumUuidProperty(name)) {
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
     * Whether the configuration phase can mark the player as premium from the UUID the connection
     * carries, without the asynchronous Mojang lookup.
     * <p>
     * This is the configuration-phase counterpart of the pre-login path used on platforms without
     * a configuration phase: the property the proxy attaches is only present when it rewrote the
     * UUID ({@code premiumUuid: false}), while with {@code premiumUuid: true} the forwarded Mojang
     * UUID is the attestation. Callers only reach it when the property was absent.
     *
     * @param forwardedPremiumUuid the attestation read from the profile, null when absent
     * @param connectionUuid       the UUID the connection carries
     * @param playerName           the connecting player's name
     * @return true when the synchronous path may be taken from the connection UUID
     */
    static boolean usesForwardedUuidAttestation(UUID forwardedPremiumUuid, UUID connectionUuid,
                                                String playerName) {
        return forwardedPremiumUuid == null
                && ProxyForwardedUuid.isForwardedMojangUuid(connectionUuid, playerName);
    }

    /**
     * Marks the player as premium in AuthMe and seeds the login session, all inside the
     * configuration phase so AuthMe's preJoin dialogs are closed before they are created.
     * Shared by the profile-attestation fast path and the async Mojang-lookup path.
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
        // an administrator's /flp cracked wins over a premium marking that is still in
        // flight. Skipping here is what stops the async task from re-creating the record the
        // command just deleted.
        if (isCrackedOverrideActive(System.currentTimeMillis(), crackedOverrides.get(playerName),
                CRACKED_OVERRIDE_TTL_MILLIS)) {
            logger.info("Skipping premium marking for {}: an administrator switched this player "
                    + "to cracked", playerName);
            return;
        }

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
     * Marks a player as explicitly switched to cracked by an administrator, so that a premium
     * marking already in flight does not resurrect the record ({@link #crackedOverrides}).
     *
     * @param playerName the player the administrator switched
     */
    public void markCrackedOverride(String playerName) {
        long now = System.currentTimeMillis();
        crackedOverrides.entrySet().removeIf(entry
                -> now - entry.getValue() > CRACKED_OVERRIDE_TTL_MILLIS);
        crackedOverrides.put(playerName, now);
    }

    /**
     * Whether an administrator's cracked switch still suppresses premium marking.
     *
     * @param now        current time in milliseconds
     * @param markedAt   when the switch happened, or {@code null} when there was none
     * @param ttlMillis  how long the switch stays authoritative
     * @return {@code true} while the switch must win over premium marking
     */
    static boolean isCrackedOverrideActive(long now, Long markedAt, long ttlMillis) {
        return markedAt != null && now - markedAt <= ttlMillis;
    }

    /**
     * Records — or clears — the premium attestation carried by the profile at the pre-login stage.
     *
     * <p>Must be called for every login attempt, including ones without an attestation: clearing
     * on absence is what stops a stale entry from authorising a later cached profile.
     *
     * @param playerName the logging-in player's name
     * @param profile    the Paper player profile from {@code AsyncPlayerPreLoginEvent}
     */
    public void recordPreLoginAttestation(String playerName, Object profile) {
        UUID attested = readForwardedPremiumUuid(profile);
        if (attested == null) {
            preLoginAttestations.remove(playerName);
        } else {
            preLoginAttestations.put(playerName, attested);
        }
    }

    /**
     * Accepts a configuration-phase attestation only when the same UUID was already present on the
     * profile at the pre-login stage.
     *
     * @param configurePhase UUID read from the configure-phase profile, may be {@code null}
     * @param preLogin       UUID recorded at {@code AsyncPlayerPreLoginEvent}, may be {@code null}
     * @return the attested UUID when the two agree, otherwise {@code null}
     */
    static UUID resolveAttestedUuid(UUID configurePhase, UUID preLogin) {
        return configurePhase != null && configurePhase.equals(preLogin) ? configurePhase : null;
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
            // stop after ~5 minutes of an empty server — the
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
