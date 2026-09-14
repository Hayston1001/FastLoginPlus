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

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Blocks AuthMe's own {@code /premium} and {@code /freemium} while FLP owns premium handling,
 * and points the player at {@code /flp} instead.
 *
 * <p>Forcing AuthMe's {@code enablePremium} also activates its two player commands (their
 * permission nodes are declared {@code default: true}), so after the takeover they are a second,
 * silently diverging entry point for the same feature:
 * {@code /premium} on an offline UUID queues a pending verification and kicks the player, but
 * FLP removed the packet listener that would ever finalise it — nothing happens and the player
 * was disconnected for it. {@code /freemium} clears AuthMe's {@code premium_uuid}, which FLP's
 * own profile does not know about, so the next login writes it straight back.
 *
 * <p><b>The command is only intercepted, never forwarded.</b> Routing {@code /premium} into
 * {@code /flp premium} would mix two permission models (the AuthMe command acts on its sender,
 * {@code /flp} can target others) and change state on behalf of a player who never asked for
 * that spelling. A hint costs nothing and cannot drift; the player learns the command FLP wants
 * them to use.
 *
 * <p>Gated on {@link FastLoginBukkit#isPremiumTakeoverActive()}: on AuthMe 5.x, when AuthMe is
 * absent, or when the takeover failed, {@code /premium} is still AuthMe's own working command
 * and must not be touched.
 *
 * <p>Teamed with the README note (F7) that asks admins to revoke {@code authme.player.premium}
 * and {@code authme.player.freemium}: the note manages the admin, this guard manages the player.
 */
public class AuthMeCommandGuard implements Listener {

    /** Node of AuthMe's {@code /premium} ({@code PlayerPermission.USE_PREMIUM}). */
    private static final String PREMIUM_PERMISSION = "authme.player.premium";

    /** Node of AuthMe's {@code /freemium} ({@code PlayerPermission.USE_FREEMIUM}). */
    private static final String FREEMIUM_PERMISSION = "authme.player.freemium";

    // Keep these keys flat. Both configuration backends in play (the bundled BungeeCord shim,
    // and Bukkit's YamlConfiguration for anything that reads the file directly) resolve a
    // dotted path by splitting on '.' — a flat key that contains a dot is never found, so the
    // hint would silently never reach the player.
    private static final String PREMIUM_BLOCKED_KEY = "authme-premium-blocked";
    private static final String FREEMIUM_BLOCKED_KEY = "authme-freemium-blocked";

    private final FastLoginBukkit plugin;

    public AuthMeCommandGuard(FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    /**
     * Intercepts AuthMe's premium commands for players who would actually be able to run them.
     *
     * @param event the command event about to be dispatched
     */
    @EventHandler(ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String label = blockedLabel(plugin.isPremiumTakeoverActive(), event.getMessage(),
                node -> player.hasPermission(node));
        if (label == null) {
            return;
        }

        event.setCancelled(true);
        plugin.getCore().sendLocaleMessage(blockedMessageKey(label), player);
        plugin.getLog().info("{} used AuthMe's /{} — blocked and redirected to /flp (ISS-12)",
                player.getName(), label);
    }

    /**
     * The decision behind {@link #onCommandPreprocess}, as a pure function: no Bukkit types and no
     * side effects, so every branch is exercised by the unit tests while the handler itself only
     * performs the side effects of a non-null result.
     *
     * <p>The permission check is delegated so it can be resolved the way AuthMe would. AuthMe
     * checks an online player through its own {@code PermissionsManager}, which is literally
     * {@code player.hasPermission(node)}; without a permission plugin it falls back to
     * {@code PlayerPermission}'s default, which is ALLOWED — and since the nodes are declared
     * {@code default: true} in AuthMe's plugin.yml, Bukkit answers true as well. Both branches
     * therefore agree. The point of the delegation is that the caller reads the <em>effective</em>
     * value: never the declared default, which a permissions plugin may have revoked.
     *
     * @param takeoverActive  whether FLP owns premium handling in this server session
     * @param message         the raw message of the command event, e.g. {@code /premium}
     * @param permissionCheck resolves a permission node for this sender
     * @return {@code "premium"} or {@code "freemium"} when the command must be swallowed,
     *         or null when it has to run untouched
     */
    static String blockedLabel(boolean takeoverActive, String message,
            Predicate<String> permissionCheck) {
        if (!takeoverActive) {
            return null;
        }

        String label = normalizeLabel(message);
        if (label == null) {
            return null;
        }

        String node = "premium".equals(label) ? PREMIUM_PERMISSION : FREEMIUM_PERMISSION;
        return permissionCheck.test(node) ? label : null;
    }

    /**
     * Locale key of the hint shown for a label returned by {@link #blockedLabel}.
     *
     * @param label {@code "premium"} or {@code "freemium"}
     * @return the locale key of that command's hint
     */
    static String blockedMessageKey(String label) {
        return "premium".equals(label) ? PREMIUM_BLOCKED_KEY : FREEMIUM_BLOCKED_KEY;
    }

    /**
     * Extracts the AuthMe premium command from a raw command line.
     *
     * <p>CraftBukkit registers each plugin command under its bare label <em>and</em> under
     * {@code <plugin name>:<label>} ({@code SimpleCommandMap#register} puts the fallback-prefixed
     * name into {@code knownCommands} unconditionally), so {@code /authme:premium} runs the very
     * same command and would otherwise stay a way around this guard. Only AuthMe's own namespace
     * is stripped — removing every namespace would also swallow another plugin's
     * {@code /other:premium}.
     *
     * @param message the raw message of the command event, e.g. {@code /premium}
     * @return {@code "premium"} or {@code "freemium"}, or null when this is not AuthMe's command
     */
    static String normalizeLabel(String message) {
        String raw = message.split("\\s+", 2)[0];
        if (raw.isEmpty() || raw.charAt(0) != '/') {
            return null;
        }

        String label = raw.substring(1).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            if (!"authme".equals(label.substring(0, colon))) {
                return null;
            }
            label = label.substring(colon + 1);
        }

        return "premium".equals(label) || "freemium".equals(label) ? label : null;
    }
}
