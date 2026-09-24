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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
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
import com.github.games647.fastlogin.core.shared.AuthMeProxyConfig;
import com.github.games647.fastlogin.core.shared.AuthMeProxyPin;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.PlatformPlugin;
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

    /** AuthMe's BungeeCord proxy plugin — owns the config file and the reload command. */
    private static final String AUTHME_PLUGIN_NAME = "AuthMeBungee";
    private static final String AUTHME_RELOAD_COMMAND = "abreloadproxy";
    /** AuthMe's own ConfigMe property holder — the source of truth for the flag. */
    private static final String AUTHME_CONFIG_PROPERTIES_CLASS =
            "fr.xephi.authme.bungee.config.BungeeConfigProperties";
    /** AuthMeBungee registers the reload command from its own onEnable — retry until it exists. */
    private static final int AUTHME_RELOAD_ATTEMPTS = 6;
    private static final Duration AUTHME_RELOAD_DELAY = Duration.ofSeconds(2);

    private final ConcurrentMap<PendingConnection, BungeeLoginSession> session = new MapMaker().weakKeys().makeMap();

    private FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> core;
    private AsyncScheduler scheduler;
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

        forceAuthMeProxyUuidMode();

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
    }

    @Override
    public void onDisable() {
        // stop scheduling before closing shared resources
        scheduler.shutdown();
        // release the global channel registrations so a reload
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

    /**
     * Pins AuthMe's proxy-side {@code premium.keepOfflineUuidCompatibility} to
     * {@code false} and reloads AuthMeBungee so the change takes effect.
     *
     * <p>With {@code true}, AuthMe performs its own login-phase handshake via
     * PacketEvents and resumes the login by re-injecting it into the Netty
     * pipeline. The re-injected login reaches BungeeCord's {@code PreLoginEvent},
     * where FastLoginPlus turns on online mode — so BungeeCord sends a second
     * encryption request on top of the cipher layer AuthMe already installed.
     * Two stacked AES/CFB8 stages corrupt the connection.
     *
     * <p>FastLoginPlus performs the same verification itself, so AuthMe's copy is
     * redundant. Pinning the flag off removes the conflict at its source instead
     * of racing against it at login time. Whether the backend receives the Mojang
     * UUID or the name-derived offline UUID is controlled by FastLoginPlus's own
     * {@code premiumUuid} setting — AuthMe's flag is no longer consulted.
     *
     * <p>No-op when AuthMeBungee is absent or the flag is already {@code false}.
     */
    private void forceAuthMeProxyUuidMode() {
        Plugin authMe = getProxy().getPluginManager().getPlugin(AUTHME_PLUGIN_NAME);
        if (authMe == null) {
            return;
        }

        AuthMeProxyPin.Outcome outcome =
                AuthMeProxyPin.forceOfflineUuidCompatibilityOff(authMe, AUTHME_CONFIG_PROPERTIES_CLASS);
        if (outcome == AuthMeProxyPin.Outcome.ALREADY_OFF) {
            return;
        }
        if (outcome == AuthMeProxyPin.Outcome.APPLIED) {
            logPinned();
            return;
        }

        logger.warn("Could not drive AuthMeBungee's own configuration objects ({}); falling back to "
                + "rewriting its config file and triggering /{}",
                outcome, AUTHME_RELOAD_COMMAND);
        forceAuthMeProxyUuidModeViaConfig(authMe);
    }

    /**
     * Fallback path for {@link #forceAuthMeProxyUuidMode()}: rewrite the config file and ask
     * AuthMe to reload itself through its own command.
     *
     * <p>Used only when AuthMe's object graph is not the expected shape. It is less reliable
     * than driving the objects — the reload travels through four indirections and BungeeCord
     * reports success even when the command body throws — but it keeps the fix in place for
     * the next proxy start.
     *
     * @param authMe the AuthMeBungee plugin instance
     */
    private void forceAuthMeProxyUuidModeViaConfig(Plugin authMe) {
        Path configFile = authMe.getDataFolder().toPath().resolve("config.yml");
        boolean changed;
        try {
            changed = AuthMeProxyConfig.forceOfflineUuidCompatibilityOff(configFile);
        } catch (IOException ex) {
            logger.warn("Could not pin AuthMeBungee's premium.{}=false in {}. If it is set to true, "
                    + "proxy premium logins will break with a double encryption handshake.",
                    AuthMeProxyConfig.OFFLINE_UUID_KEY, configFile, ex);
            return;
        }

        if (!changed) {
            return;
        }

        logPinned();
        reloadAuthMeProxy(1);
    }

    /** Logs what was pinned and why. */
    private void logPinned() {
        logger.warn("Forced AuthMeBungee's premium.{}=false (was true). FastLoginPlus performs "
                + "proxy-side premium verification itself — AuthMe's own login-phase verification "
                + "would install a second encryption layer on the same connection and corrupt it. "
                + "Use FastLoginPlus's 'premiumUuid' setting to choose between the Mojang UUID and "
                + "the offline UUID on the backend.",
                AuthMeProxyConfig.OFFLINE_UUID_KEY);
    }

    /**
     * Runs AuthMe's own reload command, retrying while the command is still unregistered.
     *
     * @param attempt the 1-based attempt number
     */
    private void reloadAuthMeProxy(int attempt) {
        scheduler.runAsyncDelayed(() -> {
            boolean dispatched = getProxy().getPluginManager()
                    .dispatchCommand(getProxy().getConsole(), AUTHME_RELOAD_COMMAND);
            if (dispatched) {
                logger.info("Reloaded AuthMeBungee; premium.{}=false is now active",
                        AuthMeProxyConfig.OFFLINE_UUID_KEY);
            } else if (attempt < AUTHME_RELOAD_ATTEMPTS) {
                reloadAuthMeProxy(attempt + 1);
            } else {
                logger.warn("Could not run /{} after {} attempts — AuthMeBungee still holds "
                        + "premium.{}=true in memory. Restart the proxy to apply the change.",
                        AUTHME_RELOAD_COMMAND, AUTHME_RELOAD_ATTEMPTS,
                        AuthMeProxyConfig.OFFLINE_UUID_KEY);
            }
        }, AUTHME_RELOAD_DELAY);
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
