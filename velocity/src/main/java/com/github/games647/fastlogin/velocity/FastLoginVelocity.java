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
package com.github.games647.fastlogin.velocity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.GeyserImpl;
import org.slf4j.Logger;

import com.github.games647.fastlogin.core.hooks.bedrock.BedrockService;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.hooks.bedrock.GeyserService;
import com.github.games647.fastlogin.core.message.ChangePremiumMessage;
import com.github.games647.fastlogin.core.message.ChannelMessage;
import com.github.games647.fastlogin.core.message.DeletePremiumMessage;
import com.github.games647.fastlogin.core.message.SuccessMessage;
import com.github.games647.fastlogin.core.UpdateChecker;
import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import com.github.games647.fastlogin.core.shared.AuthMeProxyConfig;
import com.github.games647.fastlogin.core.shared.AuthMeProxyPin;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.PlatformPlugin;
import com.github.games647.fastlogin.velocity.listener.ConnectListener;
import com.github.games647.fastlogin.velocity.listener.PluginMessageListener;
import com.google.common.collect.MapMaker;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

@Plugin(id = "fastloginplus", name = "FastLoginPlus", description = "Login plugin for premium players", url = "",
        version = BuildInfo.VERSION, authors = {"Hayston", "games647"})
public class FastLoginVelocity implements PlatformPlugin<CommandSource> {

    private final ProxyServer server;
    private final Path dataDirectory;
    private final Logger logger;
    private final ConcurrentMap<InetSocketAddress, VelocityLoginSession> session = new MapMaker().weakKeys().makeMap();
    private static final String PROXY_ID_FILE = "proxyId.txt";

    /** AuthMe's Velocity proxy plugin — owns the config file and the reload command. */
    private static final String AUTHME_PLUGIN_ID = "authmevelocity";
    private static final String AUTHME_PLUGIN_NAME = "AuthMe Velocity";
    private static final String AUTHME_RELOAD_COMMAND = "avreloadproxy";
    /** AuthMe's own ConfigMe property holder — the source of truth for the flag. */
    private static final String AUTHME_CONFIG_PROPERTIES_CLASS =
            "fr.xephi.authme.velocity.config.VelocityConfigProperties";
    /** AuthMe Velocity registers the reload command from its own init — retry until it exists. */
    private static final int AUTHME_RELOAD_ATTEMPTS = 6;
    private static final Duration AUTHME_RELOAD_DELAY = Duration.ofSeconds(2);

    private FastLoginCore<Player, CommandSource, FastLoginVelocity> core;
    private AsyncScheduler scheduler;
    private FloodgateService floodgateService;
    private GeyserService geyserService;
    private UUID proxyId;

    @Inject
    public FastLoginVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        scheduler = new AsyncScheduler(logger, task -> server.getScheduler().buildTask(this, task).schedule());
        core = new FastLoginCore<>(this);
        // Proxies ship a trimmed config template without backend-only keys
        core.setConfigTemplate("config-proxy.yml");
        core.load();
        loadOrGenerateProxyId();
        if (!core.setupDatabase() || proxyId == null) {
            return;
        }

        if (isPluginInstalled("floodgate")) {
            floodgateService = new FloodgateService(FloodgateApi.getInstance(), core);
        }

        if (isPluginInstalled("Geyser-Velocity")) {
            geyserService = new GeyserService(GeyserImpl.getInstance(), core);
        }

        forceAuthMeProxyUuidMode();

        server.getEventManager().register(this, new ConnectListener(this, core.getAntiBotService()));
        server.getEventManager().register(this, new PluginMessageListener(this));

        ChannelRegistrar channelRegistry = server.getChannelRegistrar();
        channelRegistry.register(MinecraftChannelIdentifier.create(getName(), ChangePremiumMessage.CHANGE_CHANNEL));
        channelRegistry.register(MinecraftChannelIdentifier.create(getName(), SuccessMessage.SUCCESS_CHANNEL));
        channelRegistry.register(MinecraftChannelIdentifier.create(getName(), DeletePremiumMessage.DELETE_CHANNEL));

        scheduleUpdateCheck();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        // stop scheduling before closing shared resources
        scheduler.shutdown();

        if (core != null) {
            core.close();
        }
    }

    private void scheduleUpdateCheck() {
        UpdateChecker checker = core.getUpdateChecker();
        if (checker == null) {
            return;
        }

        long intervalSeconds = core.getUpdateCheckInterval() * 60L * 60L;
        server.getScheduler().buildTask(this, () -> {
            if (checker.checkForUpdates()) {
                String msg = core.getMessage("update-available");
                if (msg != null) {
                    logger.warn(msg.replace("%new%", checker.getLatestVersion())
                            .replace("%current%", checker.getCurrentVersion()));
                }
            }
        }).delay(3L, java.util.concurrent.TimeUnit.SECONDS)
          .repeat(intervalSeconds, java.util.concurrent.TimeUnit.SECONDS)
          .schedule();
    }

    @Override
    public String getName() {
        return "fastloginplus";
    }

    @Override
    public Path getPluginFolder() {
        return dataDirectory;
    }

    @Override
    public Logger getLog() {
        return logger;
    }

    @Override
    public void sendMessage(CommandSource receiver, String message) {
        receiver.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    @Override
    public AsyncScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public boolean isPluginInstalled(String name) {
        return server.getPluginManager().isLoaded(name);
    }

    /**
     * Resolves the live instance of another plugin.
     *
     * <p>{@link PluginContainer#getInstance()} returns an {@link java.util.Optional}, so the lookup
     * has to be <em>flattened</em>: mapping the container with {@code map} hands the caller an
     * {@code Optional} wrapper instead of the plugin instance. That is exactly what used to happen
     * here — {@link AuthMeProxyPin} received an {@code Optional}, found none of the fields it looks
     * for on it and reported {@code UNSUPPORTED} on every startup, silently relegating the
     * reflective pin to its config-file fallback.
     *
     * @param pluginManager the proxy's plugin manager
     * @param pluginId      the id of the plugin to look up
     * @return the plugin instance, or {@code null} when the plugin is absent or not instantiated
     */
    static Object resolvePluginInstance(PluginManager pluginManager, String pluginId) {
        return pluginManager.getPlugin(pluginId)
                .flatMap(PluginContainer::getInstance)
                .orElse(null);
    }

    /**
     * Pins AuthMe's proxy-side {@code premium.keepOfflineUuidCompatibility} to
     * {@code false} and reloads AuthMe Velocity so the change takes effect.
     *
     * <p>The flag selects whether AuthMe rewrites the game profile back to the
     * name-derived offline UUID after it has verified the player. Leaving it
     * {@code true} would make AuthMe a second writer of the same profile field
     * that FastLoginPlus's {@code premiumUuid} setting controls, so the backend
     * UUID would depend on which plugin's handler happened to run last.
     *
     * <p>Pinning it off makes FastLoginPlus's {@code premiumUuid} the single
     * authority on both proxy platforms. AuthMe's own verification still runs
     * (it is gated by the premium name list, not by this flag), so its
     * compensating behaviour on the backend is preserved.
     *
     * <p>No-op when AuthMe Velocity is absent or the flag is already {@code false}.
     */
    private void forceAuthMeProxyUuidMode() {
        Object authMe = resolvePluginInstance(server.getPluginManager(), AUTHME_PLUGIN_ID);
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

        logger.warn("Could not drive {}'s own configuration objects ({}); falling back to "
                + "rewriting its config file and triggering /{}",
                AUTHME_PLUGIN_NAME, outcome, AUTHME_RELOAD_COMMAND);
        forceAuthMeProxyUuidModeViaConfig();
    }

    /**
     * Fallback path for {@link #forceAuthMeProxyUuidMode()}: rewrite the config file and ask
     * AuthMe Velocity to reload itself through its own command.
     *
     * <p>Used only when AuthMe's object graph is not the expected shape. It keeps the fix in
     * place for the next proxy start, but cannot confirm that the running instance picked it up.
     */
    private void forceAuthMeProxyUuidModeViaConfig() {
        // Velocity injects <plugins-dir>/<plugin-id> as the data directory, and ours
        // lives under the same plugins directory
        Path pluginsDir = dataDirectory.getParent();
        if (pluginsDir == null) {
            logger.warn("Could not locate {}'s data directory to pin premium.{}=false",
                    AUTHME_PLUGIN_NAME, AuthMeProxyConfig.OFFLINE_UUID_KEY);
            return;
        }

        Path configFile = pluginsDir.resolve(AUTHME_PLUGIN_ID).resolve("config.yml");
        if (!Files.isRegularFile(configFile)) {
            // The layout is derived, not read from AuthMe — say so instead of failing silently,
            // because a silent no-op leaves the double-handshake conflict in place.
            logger.warn("{} is installed but its config was not found at {} — premium.{} could not "
                    + "be pinned to false. If AuthMe runs keepOfflineUuidCompatibility=true, set it "
                    + "manually.",
                    AUTHME_PLUGIN_NAME, configFile, AuthMeProxyConfig.OFFLINE_UUID_KEY);
            return;
        }

        boolean changed;
        try {
            changed = AuthMeProxyConfig.forceOfflineUuidCompatibilityOff(configFile);
        } catch (IOException ex) {
            logger.warn("Could not pin {}'s premium.{}=false in {}",
                    AUTHME_PLUGIN_NAME, AuthMeProxyConfig.OFFLINE_UUID_KEY, configFile, ex);
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
        logger.warn("Forced {}'s premium.{}=false (was true). FastLoginPlus's own "
                + "'premiumUuid' setting decides whether the backend receives the Mojang UUID or "
                + "the offline UUID; two plugins writing that profile field would be decided by "
                + "handler order.",
                AUTHME_PLUGIN_NAME, AuthMeProxyConfig.OFFLINE_UUID_KEY);
    }

    /**
     * Runs AuthMe's own reload command, retrying while the command is still unregistered.
     *
     * @param attempt the 1-based attempt number
     */
    private void reloadAuthMeProxy(int attempt) {
        scheduler.runAsyncDelayed(() -> {
            CommandSource console = server.getConsoleCommandSource();
            server.getCommandManager().executeAsync(console, AUTHME_RELOAD_COMMAND)
                    .whenComplete((executed, error) -> {
                        if (error == null && Boolean.TRUE.equals(executed)) {
                            logger.info("Reloaded {}; premium.{}=false is now active",
                                    AUTHME_PLUGIN_NAME, AuthMeProxyConfig.OFFLINE_UUID_KEY);
                        } else if (attempt < AUTHME_RELOAD_ATTEMPTS) {
                            reloadAuthMeProxy(attempt + 1);
                        } else {
                            logger.warn("Could not run /{} after {} attempts — {} still holds "
                                    + "premium.{}=true in memory. Restart the proxy to apply.",
                                    AUTHME_RELOAD_COMMAND, AUTHME_RELOAD_ATTEMPTS,
                                    AUTHME_PLUGIN_NAME, AuthMeProxyConfig.OFFLINE_UUID_KEY);
                        }
                    });
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

    public FastLoginCore<Player, CommandSource, FastLoginVelocity> getCore() {
        return core;
    }

    public ConcurrentMap<InetSocketAddress, VelocityLoginSession> getSession() {
        return session;
    }

    public ProxyServer getProxy() {
        return server;
    }

    public void sendPluginMessage(ChannelMessageSink server, ChannelMessage message) {
        if (server != null) {
            ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
            message.writeTo(dataOutput);

            MinecraftChannelIdentifier channel = MinecraftChannelIdentifier.create(getName(), message.getChannelName());
            server.sendPluginMessage(channel, dataOutput.toByteArray());
        }
    }

    private void loadOrGenerateProxyId() {
        Path idFile = dataDirectory.resolve(PROXY_ID_FILE);
        boolean shouldGenerate = false;

        if (Files.exists(idFile)) {
            try {
                List<String> lines = Files.readAllLines(idFile, StandardCharsets.UTF_8);
                if (lines.isEmpty()) {
                    shouldGenerate = true;
                } else {
                    proxyId = UUID.fromString(lines.get(0));
                }
            } catch (IOException e) {
                logger.error("Unable to load proxy id from '{}'", idFile.toAbsolutePath());
                logger.error("Detailed exception:", e);
            } catch (IllegalArgumentException e) {
                Path filePath = idFile.toAbsolutePath();
                logger.error("'{}' contains an invalid uuid! FastLogin will not work without a valid id.", filePath);
            }
        } else {
            shouldGenerate = true;
        }

        if (shouldGenerate) {
            proxyId = UUID.randomUUID();
            try {
                Files.write(idFile, Collections.singletonList(proxyId.toString()), StandardOpenOption.CREATE);
            } catch (IOException e) {
                logger.error("Unable to save proxy id to '{}'", idFile.toAbsolutePath());
                logger.error("Detailed exception:", e);
            }
        }
    }

    public UUID getProxyId() {
        return proxyId;
    }

    public ProxyServer getServer() {
        return server;
    }
}
