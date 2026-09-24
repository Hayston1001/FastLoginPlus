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
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Pre-creates the AuthMe premium record before the join on platforms without a
 * configuration phase.
 *
 * <p>The decision and the guard rails live in
 * {@link FastLoginBukkit#applyPremiumAtPreLogin(String, java.util.UUID)} — this listener only
 * carries the event, so both module copies stay identical.</p>
 */
public class PreLoginPremiumListener implements Listener {

    private final FastLoginBukkit plugin;

    /**
     * Creates the listener.
     *
     * @param plugin the plugin instance used to run the pre-login pre-creation
     */
    public PreLoginPremiumListener(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs while the connection is still in the login phase, i.e. before AuthMe's join
     * handler decides whether to open a dialog.
     *
     * @param event the pre-login event of the joining connection
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        plugin.applyPremiumAtPreLogin(event.getName(), event.getUniqueId());
    }
}
