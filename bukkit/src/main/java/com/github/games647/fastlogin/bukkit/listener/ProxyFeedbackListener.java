/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.message.ToggleFeedbackMessage;
import com.google.common.io.ByteStreams;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

/**
 * Receives toggle/delete result feedback sent back by the proxy over the
 * carrier player's server connection and logs it on this backend's console.
 * <p>
 * Backend-issued {@code /flp premium|cracked|delete <name>} commands relayed
 * through a player connection report their result on the proxy console by
 * default; this listener mirrors the result to the backend console where the
 * command was typed. The payload carries the proxy's UUID, validated with the
 * same trust model as {@link BungeeListener} (LoginActionMessage).
 */
public class ProxyFeedbackListener implements PluginMessageListener {

    private final FastLoginBukkit plugin;

    public ProxyFeedbackListener(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player,
                                        byte @NotNull [] message) {
        ToggleFeedbackMessage feedback = new ToggleFeedbackMessage();
        feedback.readFrom(ByteStreams.newDataInput(message));

        // same anti-forgery check as the force-login channel: only accept
        // feedback from a proxy listed in allowed-proxies.txt
        if (!plugin.getBungeeManager().isProxyAllowed(feedback.getProxyId())) {
            plugin.getLog().warn("Received relay feedback from unknown proxy id: {}",
                    feedback.getProxyId());
            return;
        }

        String text = plugin.getCore().getMessage(feedback.getLocaleId());
        if (text == null) {
            // proxy and backend versions may disagree on available locale keys
            text = feedback.getLocaleId();
        }

        plugin.getLog().info("Proxy result for {}: {}", feedback.getPlayerName(), text);
    }
}
