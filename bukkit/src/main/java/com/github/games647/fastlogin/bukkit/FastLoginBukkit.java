/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
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
import com.github.games647.fastlogin.bukkit.listener.FloodgatePreLoginListener;
import com.github.games647.fastlogin.bukkit.listener.PaperCacheListener;
import com.github.games647.fastlogin.bukkit.listener.PreLoginPremiumListener;
import com.github.games647.fastlogin.bukkit.listener.protocollib.ProtocolLibListener;
import com.github.games647.fastlogin.bukkit.listener.protocollib.SkinApplyListener;
import com.github.games647.fastlogin.bukkit.listener.UpdateNotifyListener;
import com.github.games647.fastlogin.bukkit.listener.protocolsupport.ProtocolSupportListener;
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
 * This plugin checks if a player has a paid account and if so tries to skip offline mode authentication.
 */
public class FastLoginBukkit extends JavaPlugin implements PlatformPlugin<CommandSender> {

    //1 minutes should be enough as a timeout for bad internet connection (Server, Client and Mojang)
    private final ConcurrentMap<String, BukkitLoginSession> loginSession = CommonUtil.buildCache(
            Duration.ofMinutes(1), -1
    );

    private final Map<UUID, PremiumStatus> premiumPlayers = new ConcurrentHashMap<>();
    /** Kick messages for logins refused during the configuration phase. */
    private final java.util.concurrent.ConcurrentMap<String, String> pendingKicks =
            new ConcurrentHashMap<>();
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
    private final ProxyBedrockPreLogin proxyBedrockPreLogin = new ProxyBedrockPreLogin(this);

    public ProxyBedrockPreLogin getProxyBedrockPreLogin() {
        return proxyBedrockPreLogin;
    }

    public boolean hasBackendFloodgate() {
        return getFloodgateService() != null;
    }

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

    private void debugLog(String message, Object... arguments) {
        if (core.isDebug()) {
            logger.info(message, arguments);
        }
    }

    private boolean serverStarted;
    private BungeeManager bungeeManager;
    // stop relay retry tasks after ~5 minutes (1s interval)
    private static final int MAX_RELAY_ATTEMPTS = 300;

    // True once FLP took over packet verification. On proxy backends the persisted
    // premium flag is left untouched until a policy is declared. AuthMe's commands stay executable in that
    // state but no longer converge with FLP's profile, so AuthMeCommandGuard intercepts
    // them for players who could actually run them.
    private boolean premiumTakeoverActive;

    private final BukkitScheduler scheduler;
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
        this.scheduler = new BukkitScheduler(this, logger);
    }

    @Override
    public void onEnable() {
        core = new FastLoginCore<>(this);
        core.load();

        // Detect AuthMe version and initialize compatibility layer
        authMeVersionDetector = new AuthMeVersionDetector();
        if (authMeVersionDetector.isAuthMePresent()) {
            authMePremiumIntegrator = new AuthMePremiumIntegrator(this, authMeVersionDetector);
            if (authMeVersionDetector.isAuthMe6()) {
                logger.info("AuthMe 6.0+ detected: v{}", authMeVersionDetector.getVersion());
            } else {
                logger.info("AuthMe 5.x detected: v{} — using standard FLP flow",
                    authMeVersionDetector.getVersion());
            }
        }

        if (getServer().getOnlineMode()) {
            //we need to require offline to prevent a loginSession request for an offline player
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

        // Proxy mode must be known before takeover: preserve its stored flag until trusted facts arrive.
        if (authMePremiumIntegrator != null && authMeVersionDetector.isAuthMe6()) {
            premiumTakeoverActive = authMePremiumIntegrator.enforceFlpPremiumControl(bungeeManager.isEnabled());
            authMePremiumIntegrator.pinProxyAutoLoginOff();
            if (!premiumTakeoverActive) {
                logger.warn("Failed to fully enforce FastLogin premium control in AuthMe 6.0"
                        + " — premium logins may conflict with AuthMe's own listener");
            }
        }

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

                //if server is using paper - we need to set the skin at pre login anyway, so no need for this listener
                if (!isPaper() && getConfig().getBoolean("forwardSkin")) {
                    pluginManager.registerEvents(new SkinApplyListener(this), this);
                }
            } else {
                logger.warn("Either ProtocolLib or ProtocolSupport have to be installed if you don't use BungeeCord");
                setEnabled(false);
                return;
            }
        }

        //delay dependency setup because we load the plugin very early where plugins are initialized yet
        getServer().getScheduler().runTaskLater(this, new DelayedAuthHook(this), 5L);

        ConnectionListener connectionListener = new ConnectionListener(this);
        pluginManager.registerEvents(connectionListener, this);

        // PlayerLoginEvent is Spigot-only: any listener disables Paper's configure API
        // through HorriblePlayerLoginEventHack, even without a proxy. Paper checks startup
        // in async pre-login and retains pending login/kick delivery at join time.
        if (isPaper()) {
            org.bukkit.event.player.PlayerLoginEvent.getHandlerList().unregister(connectionListener);
            logger.info("Unregistered PlayerLoginEvent listener to avoid HorriblePlayerLoginEventHack");
        }

        // Register for Paper's AsyncPlayerConnectionConfigureEvent via reflection
        // (Paper API is not in compile classpath — we target spigot-api).
        // This lets us pre-create AuthMe premium records during the configuration
        // phase, before AuthMe's own handler shows a blocking preJoin dialog.
        registerPaperConfigureListener();

        // Capture proxy Bedrock declarations before the optional backend Floodgate
        // compatibility listener, so backend policy cannot override declared policy.
        registerBedrockListeners(pluginManager);

        // Paper startup/admission checks run here instead of PlayerLoginEvent.
        // On Paper, profile.complete(true) is called right after AsyncPlayerPreLoginEvent.
        // This listener sets the skin during the event so that complete(true) sees textures
        // and skips Paper's filledProfileCache (which may hold a stale skin).
        // forwardSkin is checked inside the listener — when false, skin setting is skipped.
        if (isPaper()) {
            pluginManager.registerEvents(new PaperCacheListener(this), this);
        }

        // Spigot's async pre-login phase has no profile properties. Observe that phase for
        // diagnostics, but pre-create only at PlayerLoginEvent, after its live claim policy
        // is applied and still before AuthMe's join-time premium handling.
        pluginManager.registerEvents(new PreLoginPremiumListener(this), this);

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

        // the config value is hours — the tick API runs at 20
        // ticks/s, so hours*3600 ticks would fire 20x too often
        long intervalTicks = core.getUpdateCheckInterval() * 60L * 60L * 20L;
        getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
            if (checker.checkForUpdates()) {
                String msg = core.getMessage("update-available");
                if (msg != null) {
                    logger.warn(msg.replace("%new%", checker.getLatestVersion())
                            .replace("%current%", checker.getCurrentVersion()));
                }
            }

            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (checker.checkForUpdates()) {
                    String msg = core.getMessage("update-available");
                    if (msg != null) {
                        logger.warn(msg.replace("%new%", checker.getLatestVersion())
                                .replace("%current%", checker.getCurrentVersion()));
                    }
                }
            }, intervalTicks, intervalTicks);
        }, 60L);

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
        if (authMePremiumIntegrator == null || authMeVersionDetector == null || !authMeVersionDetector.isAuthMe6()) {
            return;
        }

        getServer().getPluginManager().registerEvents(new AuthMeTakeoverListener(this), this);
        if (premiumTakeoverActive && !bungeeManager.isEnabled()
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
     * priority. Re-asserting inside the event would undo nothing. It runs on the main thread, the
     * same thread as the startup call, because AuthMe's packet-listener registry is not verified
     * to be thread-safe.
     *
     * <p>Proxy backends instead reapply the last declared policy and refresh its consumers.
     * This assumes AuthMe completes its synchronous reload before the next tick. A connection
     * during that gap, or an asynchronously reloaded fork, still needs real-server validation.
     *
     * @param reason short description of the triggering event, used in the log line
     */
    public void reassertAuthMeTakeover(String reason) {
        if (authMePremiumIntegrator == null || bungeeManager == null) {
            return;
        }
        if (bungeeManager.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                authMePremiumIntegrator.pinProxyAutoLoginOff();
                if (authMePremiumIntegrator.hasProxyAutoLoginPolicy()) {
                    authMePremiumIntegrator.reassertProxyAutoLogin();
                }
            }, 1L);
            return;
        }
        Bukkit.getScheduler().runTaskLater(this,
                () -> authMePremiumIntegrator.pinProxyAutoLoginOff(), 1L);
        if (!getServer().getPluginManager().isPluginEnabled("packetevents")) {
            return;
        }

        logger.info("Detected {} — re-asserting FLP's AuthMe premium takeover", reason);
        Bukkit.getScheduler().runTaskLater(this,
            () -> authMePremiumIntegrator.unregisterPremiumPacketListener(), 1L);
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
     * Whether FLP has taken over AuthMe's packet verification. Standalone startup enables
     * premium; proxy startup preserves the stored flag until a trusted policy arrives.
     *
     * @return true if the AuthMe 6.0 takeover succeeded at startup
     */
    public boolean isPremiumTakeoverActive() {
        return premiumTakeoverActive;
    }

    /**
     * Gets a thread-safe map about players which are connecting to the server are being checked to be premium (paid
     * account)
     *
     * @return a thread-safe loginSession map
     */
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

    /**
     * Fetches the premium status of an online player.
     * {@snippet :
     * // Bukkit's players object after successful authentication i.e. PlayerJoinEvent
     * // except for proxies like BungeeCord and Velocity where the details are sent delayed (1-2 seconds)
     * Player player;
     * PremiumStatus status = JavaPlugin.getPlugin(FastLoginBukkit.class).getStatus(player.getUniqueId());
     * switch (status) {
     *     case CRACKED:
     *         // player is offline
     *         break;
     *     case PREMIUM:
     *         // account is premium and player passed the verification
     *         break;
     *     case UNKNOWN:
     *         // no record about this player
     * }
     * }
     *
     * @param onlinePlayer player that is currently online player (play state)
     * @return the online status or unknown if an error happened, the player isn't online or BungeeCord doesn't send
     * us the status message yet (This means you cannot check the login status on the PlayerJoinEvent).
     */
    public @NotNull PremiumStatus getStatus(@NotNull UUID onlinePlayer) {
        return premiumPlayers.getOrDefault(onlinePlayer, PremiumStatus.UNKNOWN);
    }

    /**
     * Wait before the server is fully started. This is workaround, because connections right on startup are not
     * injected by ProtocolLib
     *
     * @return true if ProtocolLib can now intercept packets
     */
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
    public BukkitScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public void sendMessage(CommandSender receiver, String message) {
        receiver.sendMessage(message);
    }

    /**
     * Checks if a plugin is installed on the server
     *
     * @param name the name of the plugin
     * @return true if the plugin is installed
     */
    @Override
    public boolean isPluginInstalled(String name) {
        // the plugin may be enabled after FastLogin, so isPluginEnabled() won't work here
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

    public boolean isPaper() {
        return isClassAvailable("com.destroystokyo.paper.PaperConfig").isPresent()
                || isClassAvailable("io.papermc.paper.configuration.Configuration").isPresent();
    }

    private Optional<Class<?>> isClassAvailable(String clazzName) {
        try {
            return Optional.of(Class.forName(clazzName));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    void registerBedrockListeners(PluginManager pluginManager) {
        if (isPaper()) {
            pluginManager.registerEvents(
                    new com.github.games647.fastlogin.bukkit.listener.ProxyBedrockPreLoginListener(this), this);
        } else {
            pluginManager.registerEvents(
                    new com.github.games647.fastlogin.bukkit.listener.SpigotBedrockPreLoginListener(this), this);
        }
        if (hasBackendFloodgate()) {
            pluginManager.registerEvents(new FloodgatePreLoginListener(this), this);
        }
    }

    /**
     * Dynamically registers a listener for Paper's
     * {@code AsyncPlayerConnectionConfigureEvent} (LOWEST priority).
     * <p>
     * During the configuration phase, this handler asynchronously looks up the
     * player's premium UUID from Mojang and pre-creates an AuthMe premium
     * record.  By the time AuthMe's own handler (HIGHEST) checks the database,
     * the record is ready and {@code shouldSkipPreJoinDialogForPremium()} returns
     * true — the blocking preJoin dialog is skipped entirely.
     * <p>
     * Requires Paper 1.21.7+ — the configuration-phase connection event does not exist before
     * that version. AuthMe 6.0's own pre-join dialogs require 1.21.11+, so on any server where
     * this handler matters the event is present. On other platforms (or older Paper) the event
     * class won't be found and this silently does nothing.
     */
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


    private boolean handleProxyBedrockConfigure(Object event) {
        try {
            Object connection = event.getClass().getMethod("getConnection").invoke(event);
            Object profile = connection.getClass().getMethod("getProfile").invoke(connection);
            String name = (String) profile.getClass().getMethod("getName").invoke(profile);
            UUID uuid = (UUID) profile.getClass().getMethod("getId").invoke(profile);
            java.net.SocketAddress raw = (java.net.SocketAddress) connection.getClass()
                    .getMethod("getAddress").invoke(connection);
            return proxyBedrockPreLogin.configure(profile, name, uuid, raw);
        } catch (ReflectiveOperationException failure) {
            debugLog("Could not read proxy Bedrock configure profile (errorType={})",
                    failure.getClass().getSimpleName());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void onPlayerConfigure(Object event) {
        if (bungeeManager.isEnabled() && handleProxyBedrockConfigure(event)) {
            return;
        }
        if (!runsProxyPremiumPath(bungeeManager.isEnabled(), getConfig().getBoolean("autoRegister"))) {
            return;
        }

        String playerName;
        UUID connectionUuid;
        java.net.InetSocketAddress address;
        // Retained for AuthMe 6.0.1's dialog session lookup — its pending responses
        // are keyed by a session id resolved from the connection object itself.
        final Object connection;
        // Null when the current login has no usable proxy-attested UUID property.
        UUID forwardedPremiumUuid = null;
        com.github.games647.fastlogin.protocol.IdentityClaim.Facts identityFacts = null;
        com.github.games647.fastlogin.protocol.IdentityClaim identityDeclaration = null;
        try {
            connection = event.getClass().getMethod("getConnection").invoke(event);
            Object profile = connection.getClass().getMethod("getProfile").invoke(connection);
            playerName = (String) profile.getClass().getMethod("getName").invoke(profile);
            connectionUuid = (UUID) profile.getClass().getMethod("getId").invoke(profile);
            address = (java.net.InetSocketAddress) connection.getClass()
                .getMethod("getClientAddress").invoke(connection);
            forwardedPremiumUuid = readForwardedPremiumUuid(profile);
            identityDeclaration = readIdentityDeclaration(profile, playerName, connectionUuid);
            identityFacts = identityDeclaration == null ? null : identityDeclaration.getFacts();
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

        synchronizeAuthMePremiumPolicy(identityFacts);

        final boolean trustedAttestation = trustsPremiumAttestation(playerName, identityDeclaration,
                forwardedPremiumUuid != null
                        || usesForwardedUuidAttestation(null, connectionUuid, playerName));
        if (!trustedAttestation) {
            forwardedPremiumUuid = null;
        }

        // Explicit OFFLINE wins over attestation, lookup and pending-toggle shortcuts.
        if (identityDeclaration != null && identityDeclaration.getDecision()
                == com.github.games647.fastlogin.protocol.IdentityClaim.Decision.OFFLINE) {
            allowsIdentityLogin(playerName, false, identityFacts, connection, address, null, connectionUuid);
            return;
        }

        // Pending cracked toggles keep the password flow. Premium toggles may bypass
        // UUID equality after trust checks; neither entry is removed before relay.
        Boolean pendingActivate = pendingRelayStore.getToggle(playerName);
        final boolean isPendingPremium = Boolean.TRUE.equals(pendingActivate);
        if (pendingActivate != null && !pendingActivate) {
            // The queued cracked toggle must still reach the proxy (its DB is
            // still premium) — do NOT consume the entry here.  Only skip the
            // AuthMe autoRegister; the message is delivered later by the retry
            // task (remove-if-present guards against double-send).
            debugLog("Skipping autoRegister for {}: pending cracked toggle (relay stays queued)",
                    playerName);
            // defensive: make sure a relay task exists for the still-queued entry
            scheduleToggleRelay(playerName);
            return;
        }

        // fast path. The proxy attested this connection as premium and forwarded
        // the Mojang UUID on the GameProfile, which the backend decoded in the login phase.
        // Run the whole auto-register synchronously: no Mojang lookup to wait for, so AuthMe's
        // HIGHEST handler cannot show a dialog first — the record exists and the dialogs are
        // closed before they are ever created. The UUID-equality guard below does not apply:
        // a mismatch is the entire point of premiumUuid:false, and the attestation came from
        // authenticated forwarding or the validated legacy declaration, not from a name lookup.
        if (forwardedPremiumUuid != null) {
            debugLog("Proxy attested {} as premium ({}) in the configure phase",
                    playerName, forwardedPremiumUuid);
            applyPremiumAtConfigure(playerName, forwardedPremiumUuid, connectionUuid,
                    connection, address, isPendingPremium, identityFacts);
            return;
        }

        // With premiumUuid: true the v4 UUID replaces the property signal, but only
        // after the transport/declaration gate. Mark before AuthMe can open its dialog.
        if (trustedAttestation
                && usesForwardedUuidAttestation(forwardedPremiumUuid, connectionUuid, playerName)) {
            debugLog("Proxy forwarded the Mojang UUID {} for {} in the configure phase — "
                    + "marking the AuthMe record synchronously (no Mojang lookup needed)",
                    connectionUuid, playerName);
            applyPremiumAtConfigure(playerName, connectionUuid, connectionUuid,
                    connection, address, isPendingPremium, identityFacts);
            return;
        }

        // Run Mojang lookup asynchronously — the configuration phase thread
        // must not be blocked.  If AuthMe's HIGHEST handler fires before our
        // async task completes, the dialog flashes briefly and is closed by
        // closePreJoinRegisterDialog().
        final com.github.games647.fastlogin.protocol.IdentityClaim.Facts declaredFacts = identityFacts;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                java.util.Optional<com.github.games647.craftapi.model.Profile> mojang =
                    core.getResolver().findProfile(playerName);
                if (!mojang.isPresent()) {
                    return;
                }
                UUID premiumUuid = mojang.get().getId();

                // Guard: if the player's connection UUID doesn't match the
                // Mojang premium UUID, the proxy assigned an offline UUID.
                // This means the player is either cracked, or the proxy uses
                // premiumUuid:false.  In either case we must NOT pre-create a
                // premium AuthMe record — that would re-register a cracked
                // player as premium behind the proxy's back.
                //
                // EXCEPTION: pending premium toggle — the proxy hasn't updated
                // yet but the admin explicitly asked to set this player as
                // premium.  Pre-create the record so AuthMe skips the login
                // dialog (which asks for the cracked-era password).
                if (!premiumUuid.equals(connectionUuid)) {
                    if (!isPendingPremium) {
                        // say why this is normal. The mismatch is expected whenever
                        // the proxy hands out the offline UUID — a cracked player, or a premium
                        // player under premiumUuid:false on a fallback path (Bungee, or a proxy
                        // too old for the profile attestation); it is not a failure.
                        debugLog(
                            "Skipping autoRegister for {}: connection UUID {} != premium UUID {}"
                                + " (expected when the proxy assigns the offline UUID — a cracked"
                                + " player, or premiumUuid:false)",
                            playerName, connectionUuid, premiumUuid);
                        return;
                    }
                    debugLog(
                        "Pending premium toggle for {}: allowing autoRegister "
                            + "despite UUID mismatch ({} vs {})",
                        playerName, connectionUuid, premiumUuid);
                }

                // A public name lookup cannot authenticate an unsigned connection or repair
                // a rejected attestation, even when the supplied UUID happens to match.
                if (!trustedAttestation) {
                    return;
                }
                applyPremiumAtConfigure(playerName, premiumUuid, connectionUuid,
                        connection, address, isPendingPremium, declaredFacts);
            } catch (Exception e) {
                logger.warn("AutoRegister in configure phase failed for {}: {}",
                    playerName, e.getMessage());
            }
        });
    }

    /**
     * Pre-creates the AuthMe premium record for a proxy-forwarded login before join.
     *
     * <p>Paper/Folia get this guarantee from the configuration phase, which runs before
     * AuthMe can show a dialog. Spigot uses its live PlayerLoginEvent profile, still before
     * AuthMe's join handling. The method name is retained for compatibility with older callers;
     * this two-argument form has no declaration and therefore cannot authorize unsigned forwarding.
     * Recognising the forwarded UUID structurally ({@link ProxyForwardedUuid#isForwardedMojangUuid})
     * also requires authenticated forwarding or a validated connection declaration to create the record
     * <em>before</em> the join, after which AuthMe authenticates the player itself through
     * {@code canBypassWithPremium}'s v4 branch and never creates the dialog at all.</p>
     *
     * <p>Only the database record is written here. No login session can be registered yet —
     * sessions are keyed by address <em>including the port</em>, which this event does not
     * carry — and none is needed, because AuthMe performs the login on its own.</p>
     *
     * @param playerName     the requested player name
     * @param connectionUuid the UUID the connection carries
     * @return true when a new AuthMe record was pre-created
     */
    public boolean applyPremiumAtPreLogin(String playerName, UUID connectionUuid) {
        return applyPremiumAtPreLogin(playerName, connectionUuid, null);
    }

    private boolean applyPremiumAtPreLogin(String playerName, UUID connectionUuid,
            com.github.games647.fastlogin.protocol.IdentityClaim declaration) {
        if (isPaper() || !runsProxyPremiumPath(bungeeManager.isEnabled(), getConfig().getBoolean("autoRegister"))) {
            return false;
        }

        if (!ProxyForwardedUuid.isForwardedMojangUuid(connectionUuid, playerName)) {
            return false;
        }
        if (!trustsPremiumAttestation(playerName, declaration, true)) {
            return false;
        }
        com.github.games647.fastlogin.protocol.IdentityClaim.Facts facts =
                declaration == null ? null : declaration.getFacts();

        // the proxy attested this connection as premium: it refutes any pending
        // same-name cracked suspicion for the name, even if the decision below
        // then rejects the login
        getCore().resetVerificationState(playerName);

        // A readable live login profile supplies the trusted policy. Signed forwarding
        // alone may retain the previous unknown-consent fallback.
        if (!allowsPremiumLogin(playerName, facts, null, null, connectionUuid, connectionUuid)) {
            return false;
        }

        // an administrator's /flp cracked wins over a premium marking that is still
        // in flight — the same guard the configuration-phase path applies.
        if (isCrackedOverrideActive(System.currentTimeMillis(), crackedOverrides.get(playerName),
                CRACKED_OVERRIDE_TTL_MILLIS)) {
            debugLog("Skipping premium pre-creation for {}: an administrator switched this player "
                    + "to cracked", playerName);
            return false;
        }

        AuthMePremiumIntegrator integrator = getAuthMePremiumIntegrator();
        if (integrator == null || !integrator.isAuthMePremiumEnabled()) {
            return false;
        }

        boolean created = integrator.markPlayerAsPremium(playerName, connectionUuid);
        debugLog("Proxy attested {} as premium ({}) at pre-login — pre-created the AuthMe "
                + "record before the join (no configuration phase on this platform)",
                playerName, connectionUuid);
        return created;
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

    private boolean trustsPremiumAttestation(String playerName,
            com.github.games647.fastlogin.protocol.IdentityClaim declaration, boolean hasAttestation) {
        if (bungeeManager.isSignedForwarding() || declaration != null) {
            return true;
        }
        if (hasAttestation) {
            logger.warn("Ignoring premium attestation for {}: unsigned forwarding has no validated "
                    + "flp_identity bound to this connection with an allowed proxy ID; "
                    + "name lookup cannot authenticate it, waiting for a trusted late message", playerName);
        }
        return false;
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
     * @param facts the proxy's declared identity facts, or null when none were declared
     */
    private void applyPremiumAtConfigure(String playerName, UUID premiumUuid, UUID connectionUuid,
                                         Object connection, java.net.InetSocketAddress address,
                                         boolean isPendingPremium,
                                         com.github.games647.fastlogin.protocol.IdentityClaim.Facts facts) {
        // the proxy attested this connection as premium: it refutes any pending
        // same-name cracked suspicion for the name, even if the decision below
        // then rejects the login
        getCore().resetVerificationState(playerName);

        // Login-method decision: the account state plus the declared facts
        // decide, never the attestation alone. A missing or untrusted declaration
        // leaves the consent unknown, which never unlocks a password account.
        if (!allowsPremiumLogin(playerName, facts, connection, address, premiumUuid, connectionUuid)) {
            return;
        }

        // an administrator's /flp cracked wins over a premium marking that is still in
        // flight. Skipping here is what stops the async task from re-creating the record the
        // command just deleted.
        if (isCrackedOverrideActive(System.currentTimeMillis(), crackedOverrides.get(playerName),
                CRACKED_OVERRIDE_TTL_MILLIS)) {
            debugLog("Skipping premium marking for {}: an administrator switched this player "
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

        // Create session so ForceLoginTask auto-logs the player after join
        putSession(address, newPremiumSession(playerName, premiumUuid, facts));

        // For pending premium toggles, send the proxy message now
        // (the player is connected in PLAY phase) and kick. This
        // avoids waiting for the retry and eliminates the no-auth
        // window between configure and proxy kick.
        if (isPendingPremium) {
            Bukkit.getScheduler().runTask(FastLoginBukkit.this, () -> {
                Player player = Bukkit.getPlayerExact(playerName);
                if (player == null) {
                    // defensive fallback mirroring the folia
                    // branch — the carrier vanished between the configure
                    // phase and this task.  The entry stays queued and is
                    // delivered by the retry relay task once any player
                    // reaches the PLAY phase.
                    scheduleToggleRelay(playerName);
                    return;
                }

                if (bungeeManager.isEnabled()) {
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
                    bungeeManager.sendPluginMessage(player, msg);
                    if (getConfig().getBoolean("kick-toggle")) {
                        logger.info(
                            "Relayed pending {} toggle for {} and kicking",
                            pendingValue ? "premium" : "cracked", playerName);
                        player.kickPlayer(core.getMessage(
                                pendingValue ? "add-premium" : "remove-premium"));
                    } else {
                        logger.info(
                            "Relayed pending {} toggle for {} (kick disabled)",
                            pendingValue ? "premium" : "cracked", playerName);
                    }
                }
            });
        }
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
    private UUID readForwardedPremiumUuid(Object profile) {
        try {
            Object props = profile.getClass().getMethod("getProperties").invoke(profile);
            for (Object property : (java.util.Collection<?>) props) {
                String name = (String) property.getClass().getMethod("getName").invoke(property);
                if (!ForwardingAttributes.isPremiumUuidProperty(name)) {
                    continue;
                }
                String value = (String) property.getClass().getMethod("getValue").invoke(property);
                UUID uuid = UUID.fromString(value);
                // a real Mojang UUID is version 4; anything else is not an attestation
                return uuid.version() == 4 ? uuid : null;
            }
        } catch (Exception ignored) {
            // reflective read failed — treat as absent and fall back
        }
        return null;
    }

    /**
     * Reads the proxy's identity facts from the forwarded profile (the early
     * channel). Trust depends on the chain: signature-protected forwarding
     * (Velocity modern forwarding) proves the declaration already, while legacy
     * forwarding (BungeeCord) is unsigned, so its claims must carry this proxy's
     * ID token and pass the proxy allowlist. Invalid claims provide no facts.
     * Unsigned v4 connections additionally require a valid claim at admission.
     *
     * @param profile        the forwarded profile
     * @param playerName     the logging-in player's name
     * @param connectionUuid the connection UUID the claim must bind to
     * @return the full declaration, or null when nothing trustworthy was declared
     */
    private com.github.games647.fastlogin.protocol.IdentityClaim readIdentityDeclaration(
            Object profile, String playerName, UUID connectionUuid) {
        try {
            SpigotProfileReader.Snapshot snapshot = SpigotProfileReader.readProfile(profile, "profile");
            return validateIdentityDeclaration(snapshot.claims, snapshot.signatures, playerName, connectionUuid);
        } catch (Exception untrustworthy) {
            return null;
        }
    }

    private com.github.games647.fastlogin.protocol.IdentityClaim validateIdentityDeclaration(
            java.util.List<String> values, String playerName, UUID connectionUuid) {
        return validateIdentityDeclaration(values, java.util.Collections.emptyList(), playerName, connectionUuid);
    }

    private com.github.games647.fastlogin.protocol.IdentityClaim validateIdentityDeclaration(
            java.util.List<String> values, java.util.List<String> signatures, String playerName, UUID connectionUuid) {
        try {
            com.github.games647.fastlogin.protocol.IdentityClaim claim =
                    com.github.games647.fastlogin.protocol.IdentityClaim.parseExactlyOne(
                            values, playerName, connectionUuid);
            if (!bungeeManager.isSignedForwarding()) {
                if (signatures.size() != 1) {
                    return null;
                }
                boolean verified = false;
                for (UUID proxyId : bungeeManager.getAllowedProxyIds()) {
                    verified |= com.github.games647.fastlogin.protocol.IdentityClaim.verifySignature(
                            values.get(0), signatures.get(0), proxyId);
                }
                if (!verified) {
                    return null;
                }
            }
            return claim;
        } catch (Exception untrustworthy) {
            // No claim, a malformed one, or one bound to another profile supplies no facts.
            return null;
        }
    }

    /**
     * Pure admission decision for the UUID branch AuthMe trusts as premium.
     *
     * @param proxyMode whether this server is a proxy backend
     * @param signedForwarding whether forwarding authenticates the connection
     * @param connectionUuid the UUID carried by this connection
     * @param trustedClaim whether exactly one connection-bound claim passed validation
     * @return whether the connection must be refused before AuthMe handles it
     */
    static boolean isUntrustedPremiumConnection(boolean proxyMode, boolean signedForwarding,
            UUID connectionUuid, boolean trustedClaim) {
        return proxyMode && !signedForwarding && connectionUuid != null
                && connectionUuid.version() == 4 && !trustedClaim;
    }

    /**
     * Rejects unsigned premium UUIDs before AuthMe can trust the handshake UUID.
     *
     * @param profile the current connection's forwarded profile
     * @param playerName the logging-in name
     * @param connectionUuid the connection UUID
     * @return whether admission must be refused
     */
    public boolean isUntrustedPremiumConnection(Object profile, String playerName, UUID connectionUuid) {
        if (proxyBedrockPreLogin.hasTrustedDeclaration(playerName, connectionUuid)) {
            return false;
        }
        if (!isUntrustedPremiumConnection(bungeeManager.isEnabled(), bungeeManager.isSignedForwarding(),
                connectionUuid, false)) {
            return false;
        }
        return rejectUntrustedPremiumConnection(playerName, connectionUuid,
                readIdentityDeclaration(profile, playerName, connectionUuid));
    }

    private boolean rejectUntrustedPremiumConnection(String playerName, UUID connectionUuid,
            com.github.games647.fastlogin.protocol.IdentityClaim claim) {
        // A valid signature must also prove this connection's premium identity.
        boolean trustedClaim = claim != null
                && claim.getDecision()
                        == com.github.games647.fastlogin.protocol.IdentityClaim.Decision.PREMIUM_VERIFIED
                && connectionUuid != null
                && connectionUuid.equals(claim.getVerifiedMojangUuid());
        boolean rejected = isUntrustedPremiumConnection(bungeeManager.isEnabled(),
                bungeeManager.isSignedForwarding(), connectionUuid, trustedClaim);
        if (rejected) {
            logger.warn("Refusing login for {} with connection UUID {}: no signature matched any of the {} "
                    + "loaded proxy IDs, or the signed claim did not prove this connection's premium identity. "
                    + "Restart the backend if allowed-proxies.txt changed.",
                    playerName, connectionUuid, bungeeManager.getAllowedProxyIds().size());
        }
        return rejected;
    }

    /**
     * Observes Spigot's phase visibility and synchronizes trusted claims at PlayerLoginEvent.
     * The live CraftPlayer profile exists here before AuthMe's join-time premium check.
     * Missing/unsupported profile APIs cannot admit an unsigned v4 connection.
     *
     * @param phase the event phase, never a claim payload
     * @param playerName the current connection name
     * @param player the event player, or null before the player object exists
     * @param synchronize whether this is the pre-join policy synchronization hook
     * @return whether the connection may continue
     */
    public boolean observeSpigotIdentity(String phase, String playerName, Player player, boolean synchronize) {
        if (isPaper() || bungeeManager == null || !bungeeManager.isEnabled()
                || (!synchronize && !core.isDebug())) {
            return true;
        }
        if (player == null) {
            logger.info("Spigot identity visibility: phase={} name={} source=no_player claimCount=unavailable",
                    phase, playerName);
            return true;
        }
        com.github.games647.fastlogin.protocol.IdentityClaim claim = null;
        try {
            SpigotProfileReader.Snapshot snapshot = SpigotProfileReader.read(player);
            claim = validateIdentityDeclaration(
                    snapshot.claims, snapshot.signatures, playerName, player.getUniqueId());
            if (core.isDebug()) {
                logger.info("Spigot identity visibility: phase={} name={} source={} claimCount={} trusted={}",
                        phase, playerName, snapshot.source, snapshot.claims.size(), claim != null);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            if (core.isDebug()) {
                logger.info("Spigot identity visibility: phase={} name={} source=unavailable reason={}",
                        phase, playerName, unavailable.getClass().getSimpleName());
            }
        }
        if (synchronize) {
            if (rejectUntrustedPremiumConnection(playerName, player.getUniqueId(), claim)) {
                return false;
            }
            if (claim != null) {
                synchronizeAuthMePremiumPolicy(claim.getFacts());
                if (claim.getDecision()
                        != com.github.games647.fastlogin.protocol.IdentityClaim.Decision.PREMIUM_VERIFIED) {
                    return true;
                }
            }
            applyPremiumAtPreLogin(playerName, player.getUniqueId(), claim);
        }
        return true;
    }

    /**
     * Whether the premium path runs for proxy-forwarded logins (the configure
     * phase on Paper, the pre-login pre-create on Spigot). Behind a proxy the
     * login policy arrives with the identity facts, so this server's local
     * autoRegister copy never gates the path — config.yml marks that copy
     * "ignored here" and only standalone deployments read it. The local value is
     * a parameter only to keep that contract visible and pinned where it is
     * decided.
     *
     * @param proxyMode           whether this server is a proxy backend
     * @param localAutoRegister   this server's local autoRegister copy (deliberately ignored)
     * @return true when the premium path handles this login
     */
    static boolean runsProxyPremiumPath(boolean proxyMode, boolean localAutoRegister) {
        return proxyMode;
    }

    /**
     * The login-method decision behind the configure-phase premium grant:
     * the account state plus the declared facts, never the attestation alone.
     *
     * @param playerName the logging-in player's name
     * @param facts      the declared facts, or null when none were declared
     * @return true when the premium path may handle this login
     */
    private boolean allowsPremiumLogin(String playerName,
            com.github.games647.fastlogin.protocol.IdentityClaim.Facts facts) {
        return allowsPremiumLogin(playerName, facts, null, null);
    }

    private boolean allowsPremiumLogin(String playerName,
            com.github.games647.fastlogin.protocol.IdentityClaim.Facts facts,
            Object connection, java.net.InetSocketAddress address) {
        return allowsPremiumLogin(playerName, facts, connection, address, null, null);
    }

    private boolean allowsPremiumLogin(String playerName,
            com.github.games647.fastlogin.protocol.IdentityClaim.Facts facts,
            Object connection, java.net.InetSocketAddress address, UUID verifiedPremiumUuid, UUID connectionUuid) {
        return allowsIdentityLogin(playerName, true, facts, connection, address, verifiedPremiumUuid, connectionUuid);
    }

    /**
     * Synchronizes only policy facts already validated by a proxy channel.
     * Paper/Folia call this before AuthMe's configure handler; Spigot can call at PlayerLoginEvent
     * when its live forwarded profile is readable. The trusted late message remains a fallback.
     *
     * @param facts trusted declared facts, or null/undeclared for a legacy proxy
     */
    public void synchronizeAuthMePremiumPolicy(
            com.github.games647.fastlogin.protocol.IdentityClaim.Facts facts) {
        if (facts != null && facts.policyDeclared() && authMePremiumIntegrator != null
                && bungeeManager != null && bungeeManager.isEnabled()) {
            authMePremiumIntegrator.applyProxyAutoLogin(facts.autoLogin());
        }
    }

    private boolean allowsIdentityLogin(String playerName, boolean verified,
            com.github.games647.fastlogin.protocol.IdentityClaim.Facts facts,
            Object connection, java.net.InetSocketAddress address, UUID verifiedPremiumUuid, UUID connectionUuid) {
        com.github.games647.fastlogin.core.shared.LoginMethodDecider.AccountState account;
        try {
            com.github.games647.fastlogin.core.hooks.AuthPlugin<Player> authPlugin = core.getAuthPluginHook();
            account = authPlugin == null
                    ? com.github.games647.fastlogin.core.shared.LoginMethodDecider.AccountState.NONE
                    : authPlugin.inspectAccount(playerName);
        } catch (Exception readError) {
            account = com.github.games647.fastlogin.core.shared.LoginMethodDecider.AccountState.READ_FAILURE;
            logger.warn("Could not read the account state of {}; keeping the password lock",
                    playerName, readError);
        }
        com.github.games647.fastlogin.protocol.IdentityClaim.Facts declared = facts == null
                ? com.github.games647.fastlogin.protocol.IdentityClaim.Facts.defaults() : facts;
        boolean autoLogin = com.github.games647.fastlogin.core.shared.LoginMethodDecider.resolvePolicyValue(
                declared.policyDeclared(), declared.autoLogin(),
                () -> getConfig().getBoolean("autoLogin"));
        boolean autoRegisterUnknown =
                com.github.games647.fastlogin.core.shared.LoginMethodDecider.resolvePolicyValue(
                        declared.policyDeclared(), declared.autoRegisterUnknown(),
                        () -> getConfig().getBoolean("auto-register-unknown"));
        com.github.games647.fastlogin.core.shared.LoginMethodDecider.Outcome outcome =
                com.github.games647.fastlogin.core.shared.LoginMethodDecider.decide(
                        verified, account, declared.consent(), autoLogin, autoRegisterUnknown);
        if (outcome == com.github.games647.fastlogin.core.shared.LoginMethodDecider.Outcome.REJECT) {
            String reason = core.getMessage("ownership-conflict");
            if (connection != null) {
                disconnectConfiguration(playerName, connection, reason);
            } else {
                denyLogin(playerName, reason);
            }
            return false;
        }
        if (outcome != com.github.games647.fastlogin.core.shared.LoginMethodDecider.Outcome.AUTO_REGISTER_LOGIN
                && outcome != com.github.games647.fastlogin.core.shared.LoginMethodDecider.Outcome.AUTO_LOGIN) {
            debugLog("Keeping the auth plugin's own login flow for {} (decision {})",
                    playerName, outcome);
            if (verified && facts != null && address != null
                    && account == com.github.games647.fastlogin.core.shared.LoginMethodDecider
                            .AccountState.PASSWORD_ACCOUNT) {
                passwordNotices.put(address, "premium-password-login");
            } else if (outcome == com.github.games647.fastlogin.core.shared.LoginMethodDecider.Outcome.PASSWORD
                    && verified && !autoLogin
                    && account == com.github.games647.fastlogin.core.shared.LoginMethodDecider
                            .AccountState.PREMIUM_OWNED) {
                debugLog("AuthMe placeholder release policy for {}: source={} autoLogin=false",
                        playerName, declared.policyDeclared() ? "proxy declaration" : "local configuration");
                AuthMePremiumIntegrator integrator = getAuthMePremiumIntegrator();
                AuthMePremiumIntegrator.ReleaseOutcome release = integrator == null
                        ? AuthMePremiumIntegrator.ReleaseOutcome.UNSUPPORTED
                        : integrator.releaseUnusablePlaceholder(playerName, verifiedPremiumUuid);
                if (release == AuthMePremiumIntegrator.ReleaseOutcome.RELEASED
                        || release == AuthMePremiumIntegrator.ReleaseOutcome.ALREADY_ABSENT) {
                    if (address != null) {
                        passwordNotices.invalidate(address);
                    }
                    integrator.closePreJoinRegisterDialog(connectionUuid, connection);
                    integrator.closePreJoinLoginDialog(connectionUuid, connection);
                } else if (release == AuthMePremiumIntegrator.ReleaseOutcome.UUID_MISMATCH) {
                    if (address != null) {
                        passwordNotices.invalidate(address);
                    }
                    integrator.closePreJoinRegisterDialog(connectionUuid, connection);
                    integrator.closePreJoinLoginDialog(connectionUuid, connection);
                    String reason = core.getMessage("ownership-conflict");
                    if (connection != null) {
                        disconnectConfiguration(playerName, connection, reason);
                    } else {
                        denyLogin(playerName, reason);
                    }
                } else if (facts != null && address != null) {
                    passwordNotices.put(address, "premium-policy-disabled");
                }
            }
            return false;
        }
        return true;
    }

    /**
     * Refuses a login on platforms without a configuration connection.
     *
     * @param playerName the player to refuse
     * @param message    the localized reason
     */
    void denyLogin(String playerName, String message) {
        pendingKicks.put(playerName, message);
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            online.kickPlayer(message);
        }
    }

    /**
     * Takes the pending kick message for a joining player, if any.
     *
     * @param playerName the joining player's name
     * @return the kick message, or null when this player may stay
     */
    public String takePendingKick(String playerName) {
        return pendingKicks.remove(playerName);
    }

    private final com.google.common.cache.Cache<java.net.InetSocketAddress, String> passwordNotices =
            com.google.common.cache.CacheBuilder.newBuilder().maximumSize(10000)
                    .expireAfterWrite(2, java.util.concurrent.TimeUnit.MINUTES).build();

    /**
     * Sends the explanation at most once for this player connection.
     *
     * @param player the player on its owning thread
     */
    public void sendPasswordNotice(Player player) {
        String key = "flp-password-notice";
        if (!player.hasMetadata(key)) {
            player.setMetadata(key, new org.bukkit.metadata.FixedMetadataValue(this, true));
            core.sendLocaleMessage("premium-password-login", player);
        }
    }

    /**
     * Explains policy-disabled premium recovery at most once per connection.
     *
     * @param player the player on its owning thread
     */
    public void sendPremiumPolicyNotice(Player player) {
        String key = "flp-premium-policy-notice";
        if (!player.hasMetadata(key)) {
            player.setMetadata(key, new org.bukkit.metadata.FixedMetadataValue(this, true));
            core.sendLocaleMessage("premium-policy-disabled", player);
        }
    }

    /**
     * Consumes configure-phase work on the joining player's owning thread.
     *
     * @param player the joining player
     * @return whether the player may continue to the auth flow
     */
    public boolean deliverPendingLogin(Player player) {
        String denied = takePendingKick(player.getName());
        if (denied != null) {
            player.kickPlayer(denied);
            return false;
        }
        String notice = passwordNotices.asMap().remove(player.getAddress());
        if ("premium-policy-disabled".equals(notice)) {
            sendPremiumPolicyNotice(player);
        } else if (notice != null) {
            sendPasswordNotice(player);
        }
        return true;
    }

    private void disconnectConfiguration(String playerName, Object connection, String reason) {
        try {
            Class<?> component = Class.forName("net.kyori.adventure.text.Component");
            Class<?> serializer = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Object legacy = serializer.getMethod("legacySection").invoke(null);
            Object message = serializer.getMethod("deserialize", String.class).invoke(legacy, reason);
            Class.forName("io.papermc.paper.connection.PlayerConnection")
                    .getMethod("disconnect", component).invoke(connection, message);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            logger.error("Could not disconnect the configuration connection for {}; deferring rejection to join",
                    playerName, failure);
            denyLogin(playerName, reason);
        }
    }

    /**
     * Seeds the login session for a granted premium login. The facts may be
     * absent (no early channel, or a claim that did not pass validation): the
     * session then keeps the conservative defaults instead of failing.
     *
     * @param playerName the player name
     * @param premiumUuid the verified Mojang UUID
     * @param facts      the declared facts, or null when none were declared
     * @return the session to register for the force-login task
     */
    static BukkitLoginSession newPremiumSession(String playerName, java.util.UUID premiumUuid,
            com.github.games647.fastlogin.protocol.IdentityClaim.Facts facts) {
        BukkitLoginSession session = new BukkitLoginSession(playerName, true);
        session.setUuid(premiumUuid);
        session.setVerifiedPremium(true);
        if (facts != null) {
            session.setFacts(facts);
        }
        return session;
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
     * Retries relaying a queued premium/cracked toggle message every 20 ticks
     * (1 second) until a player is online to serve as the relay channel.
     *
     * @param target the player name to toggle
     */
    public void scheduleToggleRelay(String target) {
        final int[] taskIdHolder = new int[1];
        final int[] attempts = new int[1];
        taskIdHolder[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                // stop after ~5 minutes of an empty server — the
                // entry stays queued and is retried after a restart
                if (++attempts[0] >= MAX_RELAY_ATTEMPTS) {
                    logger.warn("Gave up relaying pending toggle for {} after {} attempts"
                            + " — the entry stays queued and is retried after a restart",
                            target, attempts[0]);
                    Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
                    return;
                }
                Optional<? extends Player> optPlayer =
                    Bukkit.getServer().getOnlinePlayers().stream().findFirst();
                if (!optPlayer.isPresent()) {
                    return;
                }
                Player sender = optPlayer.get();
                // remove-if-present AND take the CURRENT queued value: the entry
                // may have been overwritten by a newer toggle command since this
                // task was scheduled — never send a stale captured value
                Boolean pendingValue = pendingRelayStore.removeToggle(target);
                if (pendingValue != null) {
                    ChangePremiumMessage message = new ChangePremiumMessage(target, pendingValue, false);
                    bungeeManager.sendPluginMessage(sender, message);
                    logger.info("Relayed pending {} toggle for {}",
                        pendingValue ? "premium" : "cracked", target);
                }
                Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
            }
        }, 20L, 20L);
    }

    /**
     * Retries relaying a queued delete message every 20 ticks (1 second) until
     * a player is online to serve as the relay channel.
     *
     * @param targetName the player name to delete
     */
    public void scheduleDeleteRelay(String targetName) {
        final int[] taskIdHolder = new int[1];
        final int[] attempts = new int[1];
        taskIdHolder[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                // stop after ~5 minutes of an empty server — the
                // entry stays queued and is retried after a restart
                if (++attempts[0] >= MAX_RELAY_ATTEMPTS) {
                    logger.warn("Gave up relaying pending delete for {} after {} attempts"
                            + " — the entry stays queued and is retried after a restart",
                            targetName, attempts[0]);
                    Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
                    return;
                }
                Optional<? extends Player> optPlayer =
                    Bukkit.getServer().getOnlinePlayers().stream().findFirst();
                if (!optPlayer.isPresent()) {
                    return;
                }
                Player sender = optPlayer.get();
                // remove-if-present: never double-send after another retry already relayed it
                if (pendingRelayStore.clearDelete(targetName)) {
                    bungeeManager.sendPluginMessage(sender, new DeletePremiumMessage(targetName, false));
                    logger.info("Relayed pending delete for {}", targetName);
                }
                Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
            }
        }, 20L, 20L);
    }

    /**
     * Retries relaying a queued AuthMe premium notice ({@code premium.set}/{@code premium.unset})
     * every 20 ticks (1 second) until a player is online to serve as the message carrier.
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
        final int[] taskIdHolder = new int[1];
        final int[] attempts = new int[1];
        taskIdHolder[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                Boolean isSet = pendingRelayStore.getPremiumNotice(target);
                if (isSet == null) {
                    // delivered by the other call site (or by an admin toggle)
                    Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
                    return;
                }
                // stop after ~5 minutes of an empty server — the
                // entry stays queued and is retried after a restart
                if (++attempts[0] >= MAX_RELAY_ATTEMPTS) {
                    logger.warn("Gave up relaying the pending AuthMe premium notice for {} after"
                            + " {} attempts — the entry stays queued and is retried after a restart",
                            target, attempts[0]);
                    Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
                    return;
                }
                if (Bukkit.getServer().getOnlinePlayers().isEmpty()) {
                    return;
                }
                if (relayPremiumNotice(target, isSet)) {
                    Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
                }
            }
        }, 20L, 20L);
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
