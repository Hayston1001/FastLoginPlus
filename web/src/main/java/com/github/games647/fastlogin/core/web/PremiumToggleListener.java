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
package com.github.games647.fastlogin.core.web;

/**
 * Callback invoked when a player's premium status is toggled via the WebUI.
 *
 * <p>Platform implementations perform the <b>complete</b> toggle operation —
 * the same flow used by the {@code /premium} and {@code /cracked} commands:</p>
 * <ul>
 * <li>Behind a proxy: relay a {@code ChangePremiumMessage} to the proxy
 *     (queueing the toggle when no player is online to relay it), so the
 *     proxy updates its database, resolves the Mojang UUID for premium
 *     activations, fires the premium toggle event and kicks the player.</li>
 * <li>Without a proxy: update the local database, fire the toggle event and
 *     kick the player (per {@code kick-toggle}).</li>
 * </ul>
 *
 * <p>When a listener is registered, {@code PlayerApiHandler} delegates the
 * whole operation to it and never writes the database itself.</p>
 */
@FunctionalInterface
public interface PremiumToggleListener {

    /**
     * Performs the premium toggle for the given player.
     *
     * @param playerName the player's name
     * @param premium    {@code true} if the player should be set to premium
     *                   (online mode), {@code false} if set to cracked
     *                   (offline mode)
     */
    void onPremiumToggle(String playerName, boolean premium);
}
