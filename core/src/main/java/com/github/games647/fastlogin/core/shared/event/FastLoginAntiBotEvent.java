/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
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
package com.github.games647.fastlogin.core.shared.event;

import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;

import java.net.InetSocketAddress;

/**
 * This event fires when the anti-bot service decides to block or ignore an incoming connection.
 * <p>
 * Other plugins can listen to this event to react to anti-bot decisions (e.g. logging) or
 * cancel the event to bypass the anti-bot block (e.g. whitelisted players).
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
     * @return the anti-bot action that was decided (Block or Ignore)
     */
    Action getAction();
}
