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
import com.github.games647.fastlogin.core.message.ChannelMessage;
import com.github.games647.fastlogin.core.message.DeletePremiumMessage;
import com.github.games647.fastlogin.core.shared.PendingRelayStore;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginPremiumToggleEvent;
import com.github.games647.fastlogin.bukkit.listener.ConnectionListener;
import com.github.games647.fastlogin.bukkit.listener.PaperCacheListener;
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
import com.github.games647.fastlogin.core.shared.JavaVersions;
import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.shared.PlatformPlugin;
import com.github.games647.fastlogin.core.shared.event.FastLoginPremiumToggleEvent.PremiumToggleReason;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.core.web.WebServer;

/**
 * This plugin checks if a player has a paid account and if so tries to skip offline mode authentication.
 */
public class FastLoginBukkit extends JavaPlugin implements PlatformPlugin<CommandSender> {

    //1 minutes should be enough as a timeout for bad internet connection (Server, Client and Mojang)
    private final ConcurrentMap<String, BukkitLoginSession> loginSession = CommonUtil.buildCache(
            Duration.ofMinutes(1), -1
    );

    private final Map<UUID, PremiumStatus> premiumPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, FloodgateState> playerFloodgateState = new ConcurrentHashMap<>();
    private final Logger logger;

    private boolean serverStarted;
    private BungeeManager bungeeManager;
    // 0.5.0/F014: stop relay retry tasks after ~5 minutes (1s interval)
    private static final int MAX_RELAY_ATTEMPTS = 300;

    private final BukkitScheduler scheduler;
    private FastLoginCore<Player, CommandSender, FastLoginBukkit> core;
    private FloodgateService floodgateService;
    private GeyserService geyserService;

    // 0.6.0/F003: kept as an instance field so onDisable() can stop the web
    // panel (releasing the port and threads). Declared without an initializer
    // on purpose — the WebServer class must only load behind the Java 17+
    // version gate inside startWebPanel() (0.6.0/F057).
    private WebServer webServer;
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

    /**
     * Relays a premium toggle to the proxy through any online player, or
     * queues it for later delivery when no player is online to relay the
     * plugin message.
     *
     * <p>This is the single relay path shared by the console
     * {@code /premium}/{@code /cracked} commands and the WebUI, so all
     * premium toggles use the identical proxy flow: proxy-side database
     * update, Mojang UUID resolution, premium toggle event and kick.</p>
     *
     * @param target   the player name to toggle
     * @param activate {@code true} for premium, {@code false} for cracked
     */
    public void relayToggleToProxy(String target, boolean activate) {
        Optional<? extends Player> optPlayer = Bukkit.getServer().getOnlinePlayers().stream().findFirst();
        if (!optPlayer.isPresent()) {
            logger.info("No player online to relay message — queuing pending toggle for {}", target);
            if (pendingRelayStore.queueToggle(target, activate)) {
                // schedule a retry only for a newly queued entry — an entry
                // already waiting has a live retry task, which picks up the
                // latest queued value at send time (0.5.0/P1,P6)
                scheduleToggleRelay(target);
            }
            return;
        }

        ChannelMessage message = new ChangePremiumMessage(target, activate, false);
        bungeeManager.sendPluginMessage(optPlayer.get(), message);
    }

    /**
     * Performs a premium toggle against the local database, mirroring the
     * standalone (no proxy) command path: profile update, premium toggle
     * event and kick when {@code kick-toggle} is enabled.
     *
     * <p>Used by the WebUI when this server is not behind a proxy. When the
     * player is unknown or already in the requested state, this is a no-op.</p>
     *
     * @param playerName the player name to toggle
     * @param premium    {@code true} to set premium, {@code false} for cracked
     */
    public void performLocalPremiumToggle(String playerName, boolean premium) {
        // 0.6.0/F008: run the whole load-modify-save window under the
        // name-level striped lock, exactly like the /premium and /cracked
        // command paths, so a concurrent login flow cannot interleave
        core.getStorage().withNameLock(playerName, () -> {
            // 0.6.0/F046: strict lookup - unknown players stay unknown, the
            // WebUI never inserts a fresh row for a typo'd name
            StoredProfile profile = core.getStorage().findProfileByName(playerName);
            if (profile == null) {
                return;
            }

            if (profile.isExistingPlayer() && profile.isOnlinemodePreferred() == premium) {
                // Already in the requested state
                return;
            }

            profile.setOnlinemodePreferred(premium);
            if (!premium) {
                // Clear the premium UUID so the player uses the offline-mode
                // UUID on their next login
                profile.setId(null);
            }

            getScheduler().runAsync(() -> {
                core.getStorage().save(profile);
                getServer().getPluginManager().callEvent(new BukkitFastLoginPremiumToggleEvent(
                        Bukkit.getConsoleSender(), profile, PremiumToggleReason.COMMAND_OTHER));

                getScheduler().getSyncExecutor().execute(() -> {
                    if (core.getConfig().getBoolean("kick-toggle")) {
                        Player target = Bukkit.getPlayerExact(playerName);
                        if (target != null) {
                            target.kickPlayer(
                                core.getMessage(premium ? "add-premium" : "remove-premium"));
                        }
                    }
                });
            });
        });
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

                // FLP takes over premium verification: force enablePremium=true
                // (persisted to AuthMe's config.yml) and unregister AuthMe's
                // redundant PremiumVerificationPacketListener so FLP's ProtocolLib
                // listener is the sole Mojang verification source.
                // 0.5.0/F061: surface partial failures — AuthMe's packet listener may
                // still be registered, causing a double-interception conflict
                if (!authMePremiumIntegrator.enforceFlpPremiumControl()) {
                    logger.warn("Failed to fully enforce FastLogin premium control in AuthMe 6.0"
                            + " — premium logins may conflict with AuthMe's own listener");
                }
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
                logger.info("Restored {} pending toggle(s) and {} pending delete(s) from disk; resuming relay",
                    pendingRelayStore.toggles().size(), pendingRelayStore.deletes().size());
                pendingRelayStore.toggles().keySet().forEach(this::scheduleToggleRelay);
                pendingRelayStore.deletes().forEach(this::scheduleDeleteRelay);
            } else {
                logger.warn("Discarding {} pending relay(s): proxy support is disabled",
                    pendingRelayStore.toggles().size() + pendingRelayStore.deletes().size());
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

        // On Paper with a proxy, unregister PlayerLoginEvent to avoid
        // HorriblePlayerLoginEventHack which disables re-configuration APIs
        // (including AsyncPlayerConnectionConfigureEvent).
        if (isPaper() && bungeeManager.isEnabled()) {
            org.bukkit.event.player.PlayerLoginEvent.getHandlerList().unregister(connectionListener);
            logger.info("Unregistered PlayerLoginEvent listener to avoid HorriblePlayerLoginEventHack");
        }

        // Register for Paper's AsyncPlayerConnectionConfigureEvent via reflection
        // (Paper API is not in compile classpath — we target spigot-api).
        // This lets us pre-create AuthMe premium records during the configuration
        // phase, before AuthMe's own handler shows a blocking preJoin dialog.
        registerPaperConfigureListener();

        // On Paper, profile.complete(true) is called right after AsyncPlayerPreLoginEvent.
        // This listener sets the skin during the event so that complete(true) sees textures
        // and skips Paper's filledProfileCache (which may hold a stale skin).
        // forwardSkin is checked inside the listener — when false, skin setting is skipped.
        if (isPaper()) {
            pluginManager.registerEvents(new PaperCacheListener(this), this);
        }

        registerCommands();

        if (pluginManager.isPluginEnabled("PlaceholderAPI")) {
            premiumPlaceholder = new PremiumPlaceholder(this);
            premiumPlaceholder.register();
        }

        skinsRestorerCompat = new SkinsRestorerCompat(this);

        scheduleUpdateCheck();

        // Start web management panel if enabled
        startWebPanel();
    }

    private void startWebPanel() {
        // 0.6.0/F003: guard against a double enable — restart the panel
        // instead of leaking the previous instance
        if (webServer != null) {
            stopWebPanel();
        }

        try {
            // Read web config from core config
            net.md_5.bungee.config.Configuration config = core.getConfig();
            if (config == null) {
                return;
            }

            boolean enabled = config.get("web.enabled", false);
            if (!enabled) {
                return;
            }

            // 0.6.0/F057: the web stack (Javalin 7 / Jetty 12) is Java 17
            // bytecode. On an older JVM loading it would throw
            // UnsupportedClassVersionError (an Error the catch below cannot
            // cover) and disable the whole plugin - skip the panel instead.
            if (!JavaVersions.isAtLeast(JavaVersions.MINIMUM_WEB_JAVA)) {
                logger.warn("Web management panel requires Java {}+ (found {}). "
                        + "Panel skipped; other features unaffected.",
                        JavaVersions.MINIMUM_WEB_JAVA, System.getProperty("java.version"));
                return;
            }

            String host = config.get("web.host", "127.0.0.1");
            int port = config.get("web.port", 8080);
            String token = config.get("web.token", "");

            if (token.length() < 16) {
                logger.warn("Web panel token is too short (minimum 16 characters). Disabling web panel.");
                return;
            }

            String version = getClass().getPackage().getImplementationVersion();
            if (version == null) {
                version = "unknown";
            }

            webServer = new WebServer(logger,
                    core.getStorage(), core.getAntiBotService(),
                    version, getPluginFolder());
            // `web.lang` is the panel's default language (browser choice still wins)
            webServer.setPanelLang(config.get("web.lang", "en"));
            // 0.6.0/F067: copy the live player view into a snapshot first —
            // streaming the view directly on a Jetty thread races with joins
            ///quits (index shifts cause random 500s)
            webServer.setOnlinePlayersSupplier(() -> {
                List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
                return snapshot.stream()
                    .map(Player::getName)
                    .collect(java.util.stream.Collectors.toList());
            });

            // Premium toggle listener: perform the full toggle exactly like
            // the /premium and /cracked commands — relay to the proxy (with
            // offline queueing) or local database update + event + kick.
            webServer.setPremiumToggleListener((playerName, premium) -> {
                if (bungeeManager.isEnabled()) {
                    relayToggleToProxy(playerName, premium);
                } else {
                    performLocalPremiumToggle(playerName, premium);
                }
            });

            // Set TCCL to the plugin classloader so Javalin's ServiceLoader
            // can discover SLF4J's SPI provider inside the shaded JAR.
            // Bukkit's TCCL is the server classloader, which cannot see
            // META-INF/services files bundled in plugin JARs.
            ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            try {
                webServer.start(host, port, token, config.getStringList("web.corsAllowedOrigins"));
            } finally {
                Thread.currentThread().setContextClassLoader(originalTccl);
            }
        } catch (LinkageError | Exception e) {
            // 0.6.0/F057: LinkageError covers UnsupportedClassVersionError and
            // NoClassDefFoundError so a broken web stack cannot disable the
            // whole plugin
            logger.error("Failed to start web management panel", e);
            webServer = null;
        }
    }

    /**
     * Stop the web management panel, if one is running (0.6.0/F003).
     *
     * <p>Releases the HTTP port and the Jetty threads so a following
     * re-enable (e.g. after /reload) can bind the same port again.</p>
     */
    private void stopWebPanel() {
        if (webServer != null) {
            try {
                webServer.stop();
            } catch (Exception e) {
                logger.error("Failed to stop web management panel", e);
            } finally {
                webServer = null;
            }
        }
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

        // 0.5.0/F045: the config value is hours — the tick API runs at 20
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
        // 0.6.0/F003: stop the web panel first so it cannot serve requests
        // against the closing storage/scheduler and releases its port before
        // a potential re-enable
        stopWebPanel();
        loginSession.clear();
        premiumPlayers.clear();
        playerFloodgateState.clear();

        // 0.5.0/F046: stop scheduling before closing shared resources
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

    private boolean isPaper() {
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
     * Requires Paper 1.20.5+.  On other platforms (or older Paper) the event
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

    @SuppressWarnings("unchecked")
    private void onPlayerConfigure(Object event) {
        if (!bungeeManager.isEnabled() || !getConfig().getBoolean("autoRegister")) {
            return;
        }

        String playerName;
        UUID connectionUuid;
        java.net.InetSocketAddress address;
        try {
            Object conn = event.getClass().getMethod("getConnection").invoke(event);
            Object profile = conn.getClass().getMethod("getProfile").invoke(conn);
            playerName = (String) profile.getClass().getMethod("getName").invoke(profile);
            connectionUuid = (UUID) profile.getClass().getMethod("getId").invoke(profile);
            address = (java.net.InetSocketAddress) conn.getClass().getMethod("getClientAddress").invoke(conn);
        } catch (Exception e) {
            logger.warn("Failed to extract player info from configure event", e);
            return;
        }

        // Pending toggles queued while no relay player was online.
        // - Cracked: skip autoRegister — the player should see the register dialog.
        //   The queued relay stays pending: the retry task delivers it once any
        //   player reaches the PLAY phase, and the proxy then marks the profile
        //   cracked (and applies kick-toggle).
        // - Premium: proceed with autoRegister despite UUID mismatch, then
        //   schedule a PLAY-phase self-relay + kick.  Don't remove from the
        //   pending map yet — only clear it once the proxy message is sent.
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

        // Run Mojang lookup asynchronously — the configuration phase thread
        // must not be blocked.  If AuthMe's HIGHEST handler fires before our
        // async task completes, the dialog flashes briefly and is closed by
        // closePreJoinRegisterDialog().
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

                com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator integrator =
                    getAuthMePremiumIntegrator();
                if (integrator != null && integrator.isAuthMePremiumEnabled()) {
                    integrator.injectVerifiedUuid(playerName, premiumUuid);
                    integrator.markPlayerAsPremium(playerName, premiumUuid);
                    // Close both register AND login dialogs.  AuthMe may show
                    // a login dialog for existing records (cracked→premium)
                    // if the async task hasn't updated the record yet.
                    integrator.closePreJoinRegisterDialog(connectionUuid);
                    integrator.closePreJoinLoginDialog(connectionUuid);
                }

                // Create session so ForceLoginTask auto-logs the player after join
                BukkitLoginSession session = new BukkitLoginSession(playerName, true);
                session.setUuid(premiumUuid);
                session.setVerifiedPremium(true);
                putSession(address, session);

                // For pending premium toggles, send the proxy message now
                // (the player is connected in PLAY phase) and kick. This
                // avoids waiting for the retry and eliminates the no-auth
                // window between configure and proxy kick.
                if (isPendingPremium) {
                    Bukkit.getScheduler().runTask(FastLoginBukkit.this, () -> {
                        Player player = Bukkit.getPlayerExact(playerName);
                        if (player == null) {
                            // 0.5.0/R1: defensive fallback mirroring the folia
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
            } catch (Exception e) {
                logger.warn("AutoRegister in configure phase failed for {}: {}",
                    playerName, e.getMessage());
            }
        });
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
                // 0.5.0/F014: stop after ~5 minutes of an empty server — the
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
                // 0.5.0/F014: stop after ~5 minutes of an empty server — the
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
}
