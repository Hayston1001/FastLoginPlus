/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.resolver.RateLimitException;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public abstract class FloodgateManagement<P extends C, C, L extends LoginSession, T extends PlatformPlugin<C>>
        implements Runnable {

    protected final FastLoginCore<P, C, T> core;
    protected final P player;
    private final FloodgatePlayer floodgatePlayer;
    private final String username;

    //config.yml values that might be accessed by multiple methods
    protected final String autoLoginFloodgate;
    protected final String autoRegisterFloodgate;
    protected final String allowNameConflict;

    //variables initialized through run() and accesses by subclass
    protected boolean isRegistered;
    protected StoredProfile profile;
    protected boolean isLinked;

    public FloodgateManagement(FastLoginCore<P, C, T> core, P player, FloodgatePlayer floodgatePlayer) {
        this.core = core;
        this.player = player;
        this.floodgatePlayer = floodgatePlayer;
        this.username = getName(player);

        //load values from config.yml
        autoLoginFloodgate = core.getConfig().get("autoLoginFloodgate").toString().toLowerCase(Locale.ROOT);
        autoRegisterFloodgate = core.getConfig().get("autoRegisterFloodgate").toString().toLowerCase(Locale.ROOT);
        allowNameConflict = core.getConfig().get("allowFloodgateNameConflict").toString().toLowerCase(Locale.ROOT);
    }

    @Override
    public void run() {
        if (core.isDebug()) {
            core.getPlugin().getLog().info("Player {} is connecting through Geyser Floodgate.", username);
        }

        // check if the Bedrock player is linked to a Java account
        isLinked = floodgatePlayer.getLinkedPlayer() != null;

        //this happens on Bukkit if it's connected to Bungee
        //if that's the case, players will be logged in via plugin messages
        if (core.getStorage() == null) {
            return;
        }

        profile = core.getStorage().loadProfile(username);

        // loadProfile only returns null on a database error —
        // abort like JoinManagement does instead of NPE-ing the login task
        if (profile == null) {
            core.getPlugin().getLog().warn(
                    "Could not load profile for Bedrock player {} — is the database reachable?",
                    username);
            return;
        }

        if (profile.isExistingPlayer()) {
            if (profile.isFloodgateMigrated()) {
                if (profile.getFloodgate() == FloodgateState.TRUE && isLinked) {
                    if (core.isDebug()) {
                        core.getPlugin().getLog()
                                .info("Player {} is already stored by FastLogin as a non-linked Bedrock Edition player",
                                        username);
                    }
                    return;
                } else if (profile.getFloodgate() == FloodgateState.FALSE && isLinked) {
                    profile.setFloodgate(FloodgateState.LINKED);
                    if (core.isDebug()) {
                        core.getPlugin().getLog().info(
                                "Player {} will be changed from a Java player to a linked Floodgate player",
                                username);
                    }
                }
            } else {
                if (isLinked) {
                    profile.setFloodgate(FloodgateState.LINKED);
                    if (core.isDebug()) {
                        core.getPlugin().getLog().info(
                                "Player {} will be migrated to the v2 database schema as a linked Floodgate user",
                                username);
                    }
                } else {
                    profile.setFloodgate(FloodgateState.TRUE);
                    if (core.isDebug()) {
                        core.getPlugin().getLog().info(
                                "Player {} will be migrated to the v2 database schema as a Floodgate user", username);
                    }
                }
            }
        } else {
            if (isLinked) {
                profile.setFloodgate(FloodgateState.LINKED);
            } else {
                profile.setFloodgate(FloodgateState.TRUE);
            }
        }

        AuthPlugin<P> authPlugin = core.getAuthPluginHook();

        try {
            //maybe Bungee without auth plugin
            if (authPlugin == null) {
                // without an auth plugin only FastLogin's own record can say
                // "already known": the record's existence marks a name whose
                // auto-auth already ran once (Floodgate rows are created exactly
                // there). The consent flag is not that signal — it only records
                // the upgrade consent.
                isRegistered = profile != null && profile.isExistingPlayer();
            } else {
                isRegistered = authPlugin.isRegistered(username);
            }
        } catch (Exception ex) {
            core.getPlugin().getLog().error(
                    "An error has occurred while checking if player {} is registered",
                    username, ex);
            return;
        }

        //decide if checks should be made for conflicting Java player names
        if (isNameCheckRequired()) {
            // check for conflicting Premium Java name
            Optional<Profile> premiumUUID;
            try {
                premiumUUID = core.getResolver().findProfile(username);
            } catch (IOException | RateLimitException e) {
                core.getPlugin().getLog().error(
                        "Could not check whether Floodgate Player {}'s name conflicts a premium Java account's name.",
                        username, e);
                return;
            }

            //stop execution if player's name is conflicting
            if (premiumUUID.isPresent()) {
                return;
            }
        }

        // defer auto registration, if it's not enabled in the config
        if (!isRegistered && !isAutoAuthAllowed(autoRegisterFloodgate)) {
            return;
        }

        // stop the auto login procedure, if the current connection's parameters don't match the one stored in our
        // database
        // ex. we stored a LINKED account, but the current connection is not linked
        if ((profile.getFloodgate() == FloodgateState.LINKED && !isLinked)
            || (profile.getFloodgate() == FloodgateState.TRUE && isLinked)) {
            return;
        }

        //start Bukkit/Bungee specific tasks
        startLogin();

    }

    /**
     * Decide if the player can be automatically registered or logged in.<br>
     * The config option 'non-conflicting' is ignored by this function, as name
     * conflicts are checked by a different part of the code.
     *
     * @param configValue the value of either 'autoLoginFloodgate' or
     *                    'autoRegisterFloodgate' from config.yml
     * @return true if the Player can be registered automatically
     */
    protected boolean isAutoAuthAllowed(String configValue) {
        return isAutoAuthAllowed(configValue, isLinked);
    }

    /**
     * Decides if the player can be automatically registered or logged in.
     * <p>
     * This is the same decision as {@link #isAutoAuthAllowed(String)} without
     * requiring a player object. The backend pre-login Floodgate path has no
     * {@code Player} yet and reuses it so both paths read the config values the
     * same way.
     *
     * @param configValue the value of either 'autoLoginFloodgate' or
     *                    'autoRegisterFloodgate' from config.yml
     * @param isLinked whether the current Bedrock connection is linked to a Java account
     * @return true if the Player can be registered automatically
     */
    public static boolean isAutoAuthAllowed(String configValue, boolean isLinked) {
        return "true".equals(configValue)
                || "no-conflict".equals(configValue) // this was checked before
                || ("linked".equals(configValue) && isLinked);
    }

    /**
     * Decides whether checks for conflicting Java names should be made
     * @return ture if an API call to Mojang is needed
     */
    private boolean isNameCheckRequired() {
        //linked players have the same name as their Java profile
        //OR
        //if allowNameConflict is 'false' or 'linked' and the player had a conflicting
        //name, then they would have been kicked in FloodgateHook#checkNameConflict
        if (isLinked || !"true".equals(allowNameConflict)) {
            return false;
        }

        //autoRegisterFloodgate should only be checked if then player is not yet registered
        if (!isRegistered && "no-conflict".equals(autoRegisterFloodgate)) {
            return true;
        }

        return "no-conflict".equals(autoLoginFloodgate);
    }

    protected abstract void startLogin();
    protected abstract String getName(P player);
    protected abstract UUID getUUID(P player);
    protected abstract InetSocketAddress getAddress(P player);

}
