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
import com.github.games647.fastlogin.core.shared.FloodgateState;
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
    private final Map<UUID, FloodgateState> playerFloodgateState = new ConcurrentHashMap<>();
    private final Logger logger;

    private boolean serverStarted;
    private BungeeManager bungeeManager;
    private final BukkitScheduler scheduler;
    private FastLoginCore<Player, CommandSender, FastLoginBukkit> core;
    private FloodgateService floodgateService;
    private GeyserService geyserService;

    private PremiumPlaceholder premiumPlaceholder;
    private SkinsRestorerCompat skinsRestorerCompat;

    private AuthMeVersionDetector authMeVersionDetector;
    private AuthMePremiumIntegrator authMePremiumIntegrator;

    // Toggles queued while no player was online to relay the proxy message.
    // Key = player name, Value = true for premium, false for cracked.
    // The configure listener detects pending toggles on reconnect, relays
    // the message through the connecting player, and kicks them from Paper.
    private final java.util.Map<String, Boolean> pendingOfflineToggles =
        new java.util.concurrent.ConcurrentHashMap<>();

    public java.util.Map<String, Boolean> getPendingOfflineToggles() {
        return pendingOfflineToggles;
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
                authMePremiumIntegrator.enforceFlpPremiumControl();
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
        }

        bungeeManager = new BungeeManager(this);
        bungeeManager.initialize();

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
            logger.info("[FLP] Unregistered PlayerLoginEvent listener to avoid HorriblePlayerLoginEventHack");
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
    }

    private void registerCommands() {
        Optional.ofNullable(getCommand("flp")).ifPresent(c -> {
            FlpCommand flpCommand = new FlpCommand(this);
            c.setExecutor(flpCommand);
            c.setTabCompleter(flpCommand);
        });
    }

    private void scheduleUpdateCheck() {
        UpdateChecker checker = core.getUpdateChecker();
        if (checker == null) {
            return;
        }

        long intervalTicks = core.getUpdateCheckInterval() * 60L * 60L;
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
        if (getServer().getPluginManager().getPlugin("Geyser-Spigot") != null) {
            geyserService = new GeyserService(GeyserImpl.getInstance(), core);
        }

        if (getServer().getPluginManager().getPlugin("floodgate") != null) {
            floodgateService = new FloodgateService(FloodgateApi.getInstance(), core);

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
        logger.info("[FLP] Attempting to register Paper configure listener...");
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
        // - Premium: proceed with autoRegister despite UUID mismatch, then
        //   schedule a PLAY-phase self-relay + kick.  Don't remove from the
        //   pending map yet — only clear it once the proxy message is sent.
        Boolean pendingActivate = pendingOfflineToggles.get(playerName);
        final boolean isPendingPremium = Boolean.TRUE.equals(pendingActivate);
        if (pendingActivate != null && !pendingActivate) {
            pendingOfflineToggles.remove(playerName);
            logger.info("Skipping autoRegister for {}: pending cracked toggle", playerName);
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
                        if (player != null && bungeeManager.isEnabled()) {
                            ChangePremiumMessage msg = new ChangePremiumMessage(
                                playerName, true, false);
                            bungeeManager.sendPluginMessage(player, msg);
                            pendingOfflineToggles.remove(playerName);
                            logger.info(
                                "Relayed pending premium toggle for {} and kicking",
                                playerName);
                            player.kickPlayer(core.getMessage("add-premium"));
                        }
                    });
                }
            } catch (Exception e) {
                logger.warn("AutoRegister in configure phase failed for {}: {}",
                    playerName, e.getMessage());
            }
        });
    }
}
