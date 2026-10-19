/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageRecipient;

import com.github.games647.fastlogin.bukkit.listener.BungeeListener;
import com.github.games647.fastlogin.bukkit.listener.ProxyFeedbackListener;
import static com.github.games647.fastlogin.core.message.ChangePremiumMessage.CHANGE_CHANNEL;
import com.github.games647.fastlogin.core.message.ChannelMessage;
import static com.github.games647.fastlogin.core.message.DeletePremiumMessage.DELETE_CHANNEL;
import com.github.games647.fastlogin.core.message.LoginActionMessage;
import com.github.games647.fastlogin.core.message.ProxyAuthenticatedMessage;
import com.github.games647.fastlogin.core.message.ToggleFeedbackMessage;
import com.github.games647.fastlogin.core.message.NamespaceKey;
import static com.github.games647.fastlogin.core.message.SuccessMessage.SUCCESS_CHANNEL;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class BungeeManager {

    private static final String LEGACY_FILE_NAME = "proxy-whitelist.txt";
    private static final String FILE_NAME = "allowed-proxies.txt";

    // Null before initialization; an empty allowlist never authenticates proxy messages.
    private Set<UUID> proxyIds;

    private final FastLoginBukkit plugin;
    private boolean enabled;

    private final Collection<UUID> firedJoinEvents = new HashSet<>();

    public BungeeManager(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    public void cleanup() {
        //remove old blocked status
        Bukkit.getOnlinePlayers().forEach(player -> player.removeMetadata(plugin.getName(), plugin));
    }

    public void sendPluginMessage(PluginMessageRecipient player, ChannelMessage message) {
        if (player != null) {
            ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();

            // stamp backend -> proxy messages with the echoed proxy
            // allowlist so the proxy can authenticate the source
            if (message instanceof ProxyAuthenticatedMessage) {
                ((ProxyAuthenticatedMessage) message).setSourceProxyId(stampFor(proxyIds));
            }

            message.writeTo(dataOutput);

            NamespaceKey channel = new NamespaceKey(plugin.getName(), message.getChannelName());
            player.sendPluginMessage(plugin, channel.getCombinedName(), dataOutput.toByteArray());
        }
    }

    /**
     * Build the echoed proxy allowlist stamped onto backend -&gt; proxy plugin messages.
     *
     * <p>Exactly one trusted proxy -&gt; its ID; multiple -&gt; comma-joined; empty set (or
     * unknown state) -&gt; empty string, which the receiving side reads as "no trusted
     * proxy" and therefore as proxy support being effectively dead.</p>
     *
     * @param proxyIds the trusted proxy IDs configured on this backend
     * @return the echoed allowlist for the {@link ProxyAuthenticatedMessage} trailing field
     */
    static String stampFor(Set<UUID> proxyIds) {
        if (proxyIds == null || proxyIds.isEmpty()) {
            return "";
        }

        return proxyIds.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void initialize() {
        enabled = detectProxy();

        if (enabled) {
            proxyIds = loadBungeeCordIds();
            if (proxyIds.isEmpty()) {
                // an empty or unparsable allowed-proxies.txt makes
                // every incoming proxy ID untrusted — surface this at ERROR
                // level because proxy support is effectively dead
                plugin.getLog().error("allowed-proxies.txt contains no valid proxy IDs"
                        + " — proxy (BungeeCord/Velocity) support cannot work."
                        + " Add the proxy ID printed by the proxy plugin to the file.");
                warnIfUntrustedLegacy(isSignedForwarding(), plugin.getLog());
            }

            registerPluginChannels();
            plugin.getLog().info("Found enabled proxy configuration");
            plugin.getLog().info("Remember to follow the proxy guide to complete your setup");
        } else {
            plugin.getLog().warn("Disabling Minecraft proxy configuration. Assuming direct connections from now on.");
        }
    }

    // Called only after startup loaded an empty effective proxy allowlist.
    static void warnIfUntrustedLegacy(boolean signedForwarding, org.slf4j.Logger logger) {
        if (!signedForwarding) {
            logger.warn("Legacy proxy forwarding is unsigned and allowed-proxies.txt has no "
                    + "valid proxy IDs. The backend port must be reachable only by your proxy; "
                    + "otherwise direct connections can impersonate players. Proxy IDs are sent in "
                    + "plaintext and do not replace backend network isolation.");
        }
    }

    private boolean isProxySupported(String className, String fieldName)
        throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        return Class.forName(className).getDeclaredField(fieldName).getBoolean(null);
    }

    private boolean isVelocityEnabled()
        throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException, ClassNotFoundException,
        NoSuchMethodException, InvocationTargetException {
        try {
            Class<?> globalConfig = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            Object global = globalConfig.getDeclaredMethod("get").invoke(null);
            Object proxiesConfiguration = global.getClass().getDeclaredField("proxies").get(global);

            Field velocitySectionField = proxiesConfiguration.getClass().getDeclaredField("velocity");
            Object velocityConfig = velocitySectionField.get(proxiesConfiguration);

            return velocityConfig.getClass().getDeclaredField("enabled").getBoolean(velocityConfig);
        } catch (ClassNotFoundException classNotFoundException) {
            // try again using the older Paper configuration, because the old class file still exists in newer versions
            if (isProxySupported("com.destroystokyo.paper.PaperConfig", "velocitySupport")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether the forwarded profile attributes arrive over a signature-protected
     * channel (Velocity modern forwarding signs the whole forwarding payload with
     * the shared secret). Legacy forwarding (BungeeCord) carries unsigned
     * attributes, so their claims need the proxy ID token instead.
     *
     * <p>Both mechanisms enabled at once (a migration or a misconfiguration) is
     * handled as the legacy chain, the stricter side: only signature-protected
     * forwarding may skip the token check.</p>
     *
     * @return true when forwarded attributes are signature-protected
     */
    public boolean isSignedForwarding() {
        try {
            return isVelocityEnabled() && !isProxySupported("org.spigotmc.SpigotConfig", "bungee");
        } catch (Exception unavailable) {
            // undetectable: fall back to the stricter legacy handling
            plugin.getLog().warn("Cannot check the proxy forwarding setup", unavailable);
            return false;
        }
    }

    private boolean detectProxy() {
        try {
            if (isProxySupported("org.spigotmc.SpigotConfig", "bungee")) {
                return true;
            }
        } catch (ClassNotFoundException classNotFoundException) {
            // leave stacktrace for class not found out
            plugin.getLog().warn("Cannot check for BungeeCord support: {}", classNotFoundException.getMessage());
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            plugin.getLog().warn("Cannot check for BungeeCord support", ex);
        }

        try {
            return isVelocityEnabled();
        } catch (ClassNotFoundException classNotFoundException) {
            plugin.getLog().warn("Cannot check for Velocity support in Paper: {}", classNotFoundException.getMessage());
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException ex) {
            plugin.getLog().warn("Cannot check for Velocity support in Paper", ex);
        }

        return false;
    }

    private void registerPluginChannels() {
        Server server = Bukkit.getServer();

        // check for incoming messages from the bungeecord version of this plugin
        String groupId = plugin.getName();
        String forceChannel = NamespaceKey.getCombined(groupId, LoginActionMessage.FORCE_CHANNEL);
        server.getMessenger().registerIncomingPluginChannel(plugin, forceChannel, new BungeeListener(plugin));

        // incoming relay-result feedback (toggle/delete) sent back by the proxy
        String feedbackChannel = NamespaceKey.getCombined(groupId, ToggleFeedbackMessage.FEEDBACK_CHANNEL);
        server.getMessenger().registerIncomingPluginChannel(plugin, feedbackChannel,
                new ProxyFeedbackListener(plugin));
        // outgoing
        String successChannel = new NamespaceKey(groupId, SUCCESS_CHANNEL).getCombinedName();
        String changeChannel = new NamespaceKey(groupId, CHANGE_CHANNEL).getCombinedName();
        String deleteChannel = new NamespaceKey(groupId, DELETE_CHANNEL).getCombinedName();
        server.getMessenger().registerOutgoingPluginChannel(plugin, successChannel);
        server.getMessenger().registerOutgoingPluginChannel(plugin, changeChannel);
        server.getMessenger().registerOutgoingPluginChannel(plugin, deleteChannel);
    }

    private Set<UUID> loadBungeeCordIds() {
        Path proxiesFile = plugin.getPluginFolder().resolve(FILE_NAME);
        Path legacyFile = plugin.getPluginFolder().resolve(LEGACY_FILE_NAME);
        try {
            if (Files.notExists(proxiesFile)) {
                if (Files.exists(legacyFile)) {
                    Files.move(legacyFile, proxiesFile);
                }

                if (Files.notExists(legacyFile)) {
                    Files.createFile(proxiesFile);
                }
            }

            Files.deleteIfExists(legacyFile);
            try (Stream<String> lines = Files.lines(proxiesFile)) {
                return lines.map(String::trim).map(UUID::fromString).collect(toSet());
            }
        } catch (IOException ex) {
            plugin.getLog().error("Failed to read proxies", ex);
        } catch (Exception ex) {
            plugin.getLog().error("Failed to retrieve proxy Id. Disabling BungeeCord support", ex);
        }

        return Collections.emptySet();
    }

    public boolean isProxyAllowed(UUID proxyId) {
        return proxyIds != null && proxyIds.contains(proxyId);
    }

    /**
     * Mark the event to be fired including the task delay.
     *
     * @param player joining player
     */
    public synchronized void markJoinEventFired(Player player) {
        firedJoinEvents.add(player.getUniqueId());
    }

    /**
     * Check if the event fired including with the task delay. This necessary to restore the order of processing the
     * BungeeCord messages after the PlayerJoinEvent fires including the delay.
     * <p>
     * If the join event fired, the delay exceeded, but it ran earlier and couldn't find the recently started login
     * session. If not fired, we can start a new force login task. This will still match the requirement that we wait
     * a certain time after the player join event fired.
     *
     * @param player joining player
     * @return event fired including delay
     */
    public synchronized boolean didJoinEventFired(Player player) {
        return firedJoinEvents.contains(player.getUniqueId());
    }

    /**
     * Player quit clean up
     *
     * @param player joining player
     */
    public synchronized void cleanup(Player player) {
        firedJoinEvents.remove(player.getUniqueId());
    }
}
