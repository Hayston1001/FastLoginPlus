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
package com.github.games647.fastlogin.core.shared;

import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.shared.event.FastLoginAutoLoginEvent;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;

public abstract class ForceLoginManagement<P extends C, C, L extends LoginSession, T extends PlatformPlugin<C>>
        implements Runnable {

    protected final FastLoginCore<P, C, T> core;
    protected final P player;
    protected final L session;

    public ForceLoginManagement(FastLoginCore<P, C, T> core, P player, L session) {
        this.core = core;
        this.player = player;
        this.session = session;
    }

    @Override
    public void run() {
        if (!isOnline(player)) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info("Player {} disconnected", player);
            }
            return;
        }

        if (session == null) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info("No valid session found for {}", player);
            }
            return;
        }

        SQLStorage storage = core.getStorage();
        StoredProfile playerProfile = session.getProfile();
        try {
            if (isOnlineMode()) {
                //premium player
                AuthPlugin<P> authPlugin = core.getAuthPluginHook();
                if (authPlugin == null) {
                    // maybe only bungeecord plugin
                    // The proxy has already verified this session (online mode), so persist
                    // the premium row locally instead of relying on the backend's
                    // SuccessMessage ack. With AuthMe 6.0 the ack is never sent: on a
                    // REGISTER action the backend skips ForceLoginTask when the AuthMe record
                    // already exists (pre-created), and on a LOGIN action forceLogin() returns
                    // false because AsynchronousJoin already authenticated the player. Without
                    // a row every reconnect is treated as a new player (performNewPlayerLogin)
                    // and secondAttemptCracked can let a verified premium player join offline
                    // after a session expiry.
                    // Note: Floodgate forced-online sessions reach this branch too; their
                    // session UUID is never set (onGameProfileRequest only handles online-mode
                    // connections), so the row is written without a UUID — matching the
                    // existing Floodgate row convention.
                    onForceActionSuccess(session);

                    if (playerProfile != null) {
                        // 0.5.0/F020: persist under the name-level striped lock
                        storage.withNameLock(getName(player), () -> {
                            playerProfile.setId(session.getUuid());
                            playerProfile.setOnlinemodePreferred(true);
                            storage.save(playerProfile);
                        });
                    }
                } else {
                    boolean success = true;
                    String playerName = getName(player);
                    if ((boolean) core.getConfig().get("autoLogin")) {
                        if (session.needsRegistration()
                                || ((boolean) core.getConfig().get("auto-register-unknown")
                                && !authPlugin.isRegistered(playerName))) {
                            success = forceRegister(player);
                        } else if (!callFastLoginAutoLoginEvent(session, playerProfile).isCancelled()) {
                            success = forceLogin(player);
                        }
                    }

                    if (success) {
                        //update only on success to prevent corrupt data
                        if (playerProfile != null) {
                            // 0.5.0/F020: persist under the name-level striped lock
                            storage.withNameLock(getName(player), () -> {
                                playerProfile.setId(session.getUuid());
                                playerProfile.setOnlinemodePreferred(true);
                                storage.save(playerProfile);
                            });
                        }

                        onForceActionSuccess(session);
                    } else {
                        // forceLogin/forceRegister returned false but this is a verified
                        // premium session. This happens with AuthMe 6.0: AuthMe's
                        // AsynchronousJoin runs canBypassWithPremium() before FLP's
                        // ForceLoginTask, so the player is already authenticated when
                        // forceLogin() is called (returns false).
                        // We still need to persist onlinemodePreferred=true so that future
                        // reconnects route through requestPremiumLogin instead of
                        // startCrackedSession (direct mode; proxy-mode backends keep
                        // profile=null, so nothing is saved there).
                        if (playerProfile != null) {
                            // 0.5.0/F020: persist under the name-level striped lock
                            storage.withNameLock(getName(player), () -> {
                                playerProfile.setId(session.getUuid());
                                playerProfile.setOnlinemodePreferred(true);
                                storage.save(playerProfile);
                            });

                        }
                        // Ack the proxy even when the auth plugin reported failure: the
                        // session is already verified and the player is in game. Without
                        // this ack the proxy never persists the premium row and
                        // secondAttemptCracked can let the player join offline after a
                        // session expiry.
                        onForceActionSuccess(session);
                    }
                }
            } else if (playerProfile != null) {
                //cracked player
                // 0.5.0/F020: persist under the name-level striped lock
                storage.withNameLock(getName(player), () -> {
                    playerProfile.setId(null);
                    playerProfile.setOnlinemodePreferred(false);
                    storage.save(playerProfile);
                });
            }
        } catch (Exception ex) {
            core.getPlugin().getLog().warn("ERROR ON FORCE LOGIN of {}", getName(player), ex);
        }
    }

    public boolean forceRegister(P player) {
        if (core.isDebug()) {
            core.getPlugin().getLog().info("Register player {}", getName(player));
        }

        String generatedPassword = core.getPasswordGenerator().getRandomPassword(player);
        boolean success = core.getAuthPluginHook().forceRegister(player, generatedPassword);

        String message = core.getMessage("auto-register");
        if (success && message != null) {
            message = message.replace("%password", generatedPassword);
            core.getPlugin().sendMessage(player, message);
        }

        return success;
    }

    public boolean forceLogin(P player) {
        if (core.isDebug()) {
            core.getPlugin().getLog().info("Logging player {} in", getName(player));
        }

        boolean success = core.getAuthPluginHook().forceLogin(player);
        if (success) {
            core.sendLocaleMessage("auto-login", player);
        }

        return success;
    }

    public abstract FastLoginAutoLoginEvent callFastLoginAutoLoginEvent(LoginSession session, StoredProfile profile);

    public abstract void onForceActionSuccess(LoginSession session);

    public abstract String getName(P player);

    public abstract boolean isOnline(P player);

    public abstract boolean isOnlineMode();
}
