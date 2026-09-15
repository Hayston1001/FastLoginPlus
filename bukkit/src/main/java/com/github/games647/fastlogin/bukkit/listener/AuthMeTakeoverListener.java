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
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Re-asserts FLP's AuthMe premium-listener takeover after the events that can bring AuthMe's own
 * listener back (ISS-32).
 *
 * <p>AuthMe creates and recreates its PacketEvents premium listener from
 * {@code PacketEventsService.setup()}, which runs again on every {@code /authme reload} and again
 * whenever the {@code packetevents} plugin is enabled. The reload case is normally prevented at the
 * source: FLP leaves AuthMe's "listener registered" flag set, so {@code setup()} skips the
 * registration (see {@code AuthMePremiumIntegrator.unregisterPremiumPacketListener}). Two upstream
 * paths still reset that flag — {@code disable()} when {@code packetevents} is unloaded, and
 * {@code setup()}'s else-branch when {@code enablePremium} turns false — and enabling the packet
 * library re-runs {@code setup()} from scratch. Those are what this listener covers.
 *
 * <p><b>Why the handler only asks the plugin to re-assert one tick later.</b> Both events fire
 * <em>before</em> the work they announce: the command has not executed yet, and AuthMe's own
 * {@code PluginEnableEvent} handler ({@code ServerListener}, HIGHEST priority) runs after this one.
 * Re-asserting inside the event would undo nothing.
 *
 * <p><b>Why events instead of a periodic check.</b> A watchdog can only repair the state after the
 * fact, keeps running on servers where AuthMe's listener can never exist (proxy backends, no packet
 * library), and hides the cause: a reload is what re-armed the listener, and this listener records
 * that fact rather than silently papering over it. The login-time re-assert in
 * {@code VerifyResponseTask} stays as the last resort for a path that fires no event at all.
 */
public class AuthMeTakeoverListener implements Listener {

    /** AuthMe's main command label, as registered by its own plugin.yml. */
    private static final String AUTHME_COMMAND = "authme";

    /** The subcommand that re-reads AuthMe's configuration and reloads its services. */
    private static final String RELOAD_SUBCOMMAND = "reload";

    /** AuthMe's optional packet library; enabling it re-runs AuthMe's listener setup. */
    private static final String PACKET_EVENTS_PLUGIN = "packetevents";

    private final FastLoginBukkit plugin;

    /**
     * @param plugin the plugin owning the takeover, which schedules the re-assert
     */
    public AuthMeTakeoverListener(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    /**
     * Catches {@code /authme reload} typed by a player.
     *
     * @param event the command event about to be dispatched
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isAuthMeReload(event.getMessage())) {
            plugin.reassertAuthMeTakeover("/authme reload from " + event.getPlayer().getName());
        }
    }

    /**
     * Catches {@code /authme reload} typed in the console — the way admins actually run it, and a
     * spelling the player event never sees.
     *
     * @param event the console command event about to be dispatched
     */
    @EventHandler(ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (isAuthMeReload(event.getCommand())) {
            plugin.reassertAuthMeTakeover("/authme reload from the console");
        }
    }

    /**
     * Catches the packet library being enabled, which makes AuthMe run its listener setup again.
     *
     * @param event the plugin enable event
     */
    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (PACKET_EVENTS_PLUGIN.equalsIgnoreCase(event.getPlugin().getName())) {
            plugin.reassertAuthMeTakeover(PACKET_EVENTS_PLUGIN + " being enabled");
        }
    }

    /**
     * Whether a raw command line invokes AuthMe's configuration reload.
     *
     * <p>Kept a pure function so every spelling can be pinned by a unit test: the handler itself
     * needs a live plugin to construct, and a matcher that quietly stops matching is exactly as
     * good as no listener at all.
     *
     * @param commandLine the raw command line, with or without a leading slash
     * @return true when this is AuthMe's main command with the reload subcommand
     */
    static boolean isAuthMeReload(String commandLine) {
        if (commandLine == null) {
            return false;
        }

        String[] parts = commandLine.trim().split("\\s+");
        if (parts.length < 2) {
            return false;
        }

        return AUTHME_COMMAND.equalsIgnoreCase(label(parts[0]))
                && RELOAD_SUBCOMMAND.equalsIgnoreCase(parts[1]);
    }

    /**
     * Extracts the command label, tolerating the leading slash, the case, and the
     * {@code plugin:command} namespace form CraftBukkit registers for every plugin command.
     *
     * <p>Only AuthMe's own namespace is accepted, mirroring the command guard: stripping every
     * namespace would also accept another plugin's command that happens to be named
     * {@code authme}.
     *
     * @param rawLabel the first token of the command line
     * @return the bare label, or an empty string when the namespace is not AuthMe's
     */
    private static String label(String rawLabel) {
        String label = rawLabel.startsWith("/") ? rawLabel.substring(1) : rawLabel;
        int colon = label.indexOf(':');
        if (colon < 0) {
            return label;
        }

        return AUTHME_COMMAND.equalsIgnoreCase(label.substring(0, colon))
                ? label.substring(colon + 1)
                : "";
    }
}
