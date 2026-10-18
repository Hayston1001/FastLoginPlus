/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared.event;

import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;

import java.net.InetSocketAddress;

/**
 * This event fires when the anti-bot service decides to block an incoming connection.
 * The action is always {@link Action#Block}; cancelling the event bypasses the block.
 * <p>
 * Other plugins can listen to this event to react to anti-bot decisions (e.g. logging) or
 * cancel the event to bypass the anti-bot block (e.g. whitelisted players).
 * <p>
 * This event may fire from an async thread (a ProtocolLib worker on Bukkit/Folia, a Netty
 * event loop on BungeeCord) — handlers must not call main-thread-only APIs.
 *
 * {@snippet :
 * @EventHandler()
 * public void onAntiBot(FastLoginAntiBotEvent event) {
 *     // Log the anti-bot action
 *     System.out.println("Anti-bot " + event.getAction() + " for " + event.getUsername());
 *
 *     // Cancel to bypass the anti-bot block and allow the connection
 *     if (isWhitelisted(event.getUsername())) {
 *         event.setCancelled(true);
 *     }
 * }
 * }
 */
public interface FastLoginAntiBotEvent extends FastLoginCancellableEvent {

    /**
     * @return the address of the connecting client
     */
    InetSocketAddress getAddress();

    /**
     * @return the username of the connecting player
     */
    String getUsername();

    /**
     * @return the anti-bot action, always {@link Action#Block}
     */
    Action getAction();
}
