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
package com.github.games647.fastlogin.bungee;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.GeyserImpl;
import org.slf4j.Logger;

import com.github.games647.fastlogin.bungee.hook.BungeeAuthHook;
import com.github.games647.fastlogin.bungee.listener.ConnectListener;
import com.github.games647.fastlogin.bungee.listener.PluginMessageListener;
import com.github.games647.fastlogin.bungee.task.AsyncToggleMessage;
import com.github.games647.fastlogin.core.CommonUtil;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.hooks.bedrock.BedrockService;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.hooks.bedrock.GeyserService;
import com.github.games647.fastlogin.core.message.ChangePremiumMessage;
import com.github.games647.fastlogin.core.message.ChannelMessage;
import com.github.games647.fastlogin.core.message.DeletePremiumMessage;
import com.github.games647.fastlogin.core.message.NamespaceKey;
import com.github.games647.fastlogin.core.message.SuccessMessage;
import com.github.games647.fastlogin.core.UpdateChecker;
import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.JavaVersions;
import com.github.games647.fastlogin.core.shared.PlatformPlugin;
import com.github.games647.fastlogin.core.web.WebServer;
import com.google.common.collect.MapMaker;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.api.scheduler.GroupedThreadFactory;

/**
 * BungeeCord version of FastLogin. This plugin keeps track on online mode connections.
 */
public class FastLoginBungee extends Plugin implements PlatformPlugin<CommandSender> {

    private final ConcurrentMap<PendingConnection, BungeeLoginSession> session = new MapMaker().weakKeys().makeMap();

    private FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> core;
    private AsyncScheduler scheduler;

    // 0.6.0/F003: kept as an instance field so onDisable() can stop the web
    // panel (releasing the port and threads). Declared without an initializer
    // on purpose — the WebServer class must only load behind the Java 17+
    // version gate inside startWebPanel() (0.6.0/F057).
    private WebServer webServer;
    private FloodgateService floodgateService;
    private GeyserService geyserService;
    private Logger logger;

    @Override
    public void onEnable() {
        logger = CommonUtil.initializeLoggerService(getLogger());
        scheduler = new AsyncScheduler(logger, task -> getProxy().getScheduler().runAsync(this, task));

        core = new FastLoginCore<>(this);
        // Proxies ship a trimmed config template without backend-only keys
        core.setConfigTemplate("config-proxy.yml");
        core.load();
        if (!core.setupDatabase()) {
            return;
        }

        if (isPluginInstalled("floodgate")) {
            floodgateService = new FloodgateService(FloodgateApi.getInstance(), core);
        }

        if (isPluginInstalled("Geyser-BungeeCord")) {
            geyserService = new GeyserService(GeyserImpl.getInstance(), core);
        }

        //events
        PluginManager pluginManager = getProxy().getPluginManager();

        Listener connectListener = new ConnectListener(this, core.getAntiBotService());
        pluginManager.registerListener(this, connectListener);
        pluginManager.registerListener(this, new PluginMessageListener(this));

        //this is required to listen to incoming messages from the server
        getProxy().registerChannel(NamespaceKey.getCombined(getName(), ChangePremiumMessage.CHANGE_CHANNEL));
        getProxy().registerChannel(NamespaceKey.getCombined(getName(), SuccessMessage.SUCCESS_CHANNEL));
        getProxy().registerChannel(NamespaceKey.getCombined(getName(), DeletePremiumMessage.DELETE_CHANNEL));

        registerHook();
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
            webServer.setOnlinePlayersSupplier(() ->
                getProxy().getPlayers().stream()
                    .map(ProxiedPlayer::getName)
                    .collect(java.util.stream.Collectors.toList()));

            // Premium toggle listener: perform the full toggle exactly like
            // the /premium and /cracked command flow — database update,
            // Mojang UUID resolution, toggle event and kick. Feedback goes
            // to the proxy console.
            webServer.setPremiumToggleListener((playerName, premium) -> {
                Runnable task = new AsyncToggleMessage(core, "console", playerName, premium, false);
                getScheduler().runAsync(task);
            });

            // 0.6.0/F020: set the TCCL to the plugin classloader so
            // Javalin's ServiceLoader can discover SLF4J's SPI provider
            // inside the shaded JAR (same as the bukkit platform)
            ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            try {
                webServer.start(host, port, token,
                        config.getStringList("web.corsAllowedOrigins"));
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

    @Override
    public void onDisable() {
        // 0.6.0/F003: stop the web panel first so it cannot serve requests
        // against the closing storage/scheduler and releases its port before
        // a potential re-enable
        stopWebPanel();
        // 0.5.0/F046: stop scheduling before closing shared resources
        scheduler.shutdown();

        // 0.5.0/F074: release the global channel registrations so a reload
        // does not leak them
        getProxy().unregisterChannel(NamespaceKey.getCombined(getName(), ChangePremiumMessage.CHANGE_CHANNEL));
        getProxy().unregisterChannel(NamespaceKey.getCombined(getName(), SuccessMessage.SUCCESS_CHANNEL));
        getProxy().unregisterChannel(NamespaceKey.getCombined(getName(), DeletePremiumMessage.DELETE_CHANNEL));

        if (core != null) {
            core.close();
        }
    }

    public FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> getCore() {
        return core;
    }

    public ConcurrentMap<PendingConnection, BungeeLoginSession> getSession() {
        return session;
    }

    private void registerHook() {
        try {
            List<Class<? extends AuthPlugin<ProxiedPlayer>>> hooks = Collections.singletonList(
                    BungeeAuthHook.class
            );

            for (Class<? extends AuthPlugin<ProxiedPlayer>> clazz : hooks) {
                String pluginName = clazz.getSimpleName();
                pluginName = pluginName.substring(0, pluginName.length() - "Hook".length());
                //uses only member classes which uses AuthPlugin interface (skip interfaces)
                Plugin plugin = getProxy().getPluginManager().getPlugin(pluginName);
                if (plugin != null) {
                    logger.info("Hooking into auth plugin: {}", pluginName);
                    core.setAuthPluginHook(
                            clazz.getDeclaredConstructor(FastLoginBungee.class).newInstance(this));
                    break;
                }
            }
        } catch (ReflectiveOperationException ex) {
            logger.error("Couldn't load the auth hook class", ex);
        }
    }

    private void scheduleUpdateCheck() {
        UpdateChecker checker = core.getUpdateChecker();
        if (checker == null) {
            return;
        }

        long intervalSeconds = core.getUpdateCheckInterval() * 60L * 60L;
        getProxy().getScheduler().schedule(this, () -> {
            if (checker.checkForUpdates()) {
                String msg = core.getMessage("update-available");
                if (msg != null) {
                    logger.warn(msg.replace("%new%", checker.getLatestVersion())
                            .replace("%current%", checker.getCurrentVersion()));
                }
            }
        }, 3L, intervalSeconds, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void sendPluginMessage(Server server, ChannelMessage message) {
        if (server != null) {
            ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
            message.writeTo(dataOutput);

            NamespaceKey channel = new NamespaceKey(getName(), message.getChannelName());
            server.sendData(channel.getCombinedName(), dataOutput.toByteArray());
        }
    }

    @Override
    public String getName() {
        return getDescription().getName();
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
    public void sendMessage(CommandSender receiver, String message) {
        receiver.sendMessage(TextComponent.fromLegacyText(message));
    }

    @Override
    @SuppressWarnings("deprecation")
    public ThreadFactory getThreadFactory() {
        return new ThreadFactoryBuilder()
                .setNameFormat(getName() + " Pool Thread #%1$d")
                //Hikari create daemons by default
                .setDaemon(true)
                .setThreadFactory(new GroupedThreadFactory(this, getName()))
                .build();
    }

    @Override
    public AsyncScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public boolean isPluginInstalled(String name) {
        return getProxy().getPluginManager().getPlugin(name) != null;
    }

    public FloodgateService getFloodgateService() {
        return floodgateService;
    }

    public GeyserService getGeyserService() {
        return geyserService;
    }

    @Override
    public BedrockService<?> getBedrockService() {
        if (floodgateService != null) {
            return floodgateService;
        }
        return geyserService;
    }
}
