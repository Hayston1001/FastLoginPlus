/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.hooks.bedrock;

import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.LoginSource;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class FloodgateService extends BedrockService<FloodgatePlayer> {

    /** Mojang accounts only use these characters; a prefixed Bedrock name can never match. */
    private static final Pattern JAVA_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final FloodgateApi floodgate;
    private final Cache<FloodgatePlayer, String> preLoginRequests;

    public FloodgateService(FloodgateApi floodgate, FastLoginCore<?, ?, ?> core) {
        this(floodgate, core, Ticker.systemTicker());
    }

    FloodgateService(FloodgateApi floodgate, FastLoginCore<?, ?, ?> core, Ticker ticker) {
        super(core);
        this.floodgate = floodgate;
        // Match AuthMe 6.0.1's request lifetime. Weak identity keys cannot follow a reconnect.
        this.preLoginRequests = CacheBuilder.newBuilder().weakKeys().maximumSize(4096)
                .expireAfterWrite(15, TimeUnit.SECONDS).ticker(ticker).build();
    }

    /**
     * Remembers an accepted request solely to suppress late proxy duplicates.
     *
     * @param player the Floodgate object for this connection
     * @param username the backend name whose login was requested
     */
    public void rememberPreLoginRequest(FloodgatePlayer player, String username) {
        preLoginRequests.put(player, username);
    }

    /**
     * Checks a pending request, not authentication. New connection objects never inherit it.
     *
     * @param player the current Floodgate connection
     * @param username the current backend name
     * @return whether the same connection recently queued this name's login
     */
    public boolean hasPreLoginRequest(FloodgatePlayer player, String username) {
        return username.equals(preLoginRequests.getIfPresent(player));
    }

    /**
     * Checks if a config entry (related to Floodgate) is valid. <br>
     * Writes to Log if the value is invalid.
     * <p>
     * This should be used for:
     * <ul>
     * <li>allowFloodgateNameConflict
     * <li>autoLoginFloodgate
     * <li>autoRegisterFloodgate
     * </ul>
     *
     * @param key the key of the entry in config.yml
     * @return <b>true</b> if the entry's value is "true", "false", or "linked"
     */
    public boolean isValidFloodgateConfigString(String key) {
        String value = core.getConfig().get(key).toString().toLowerCase(Locale.ENGLISH);
        switch (value) {
            case "true":
            case "linked":
            case "false":
            case "no-conflict":
                return true;
            default:
                core.getPlugin().getLog().error("Invalid value detected for {} in FastLogin/config.yml.", key);
                return false;
        }
    }

    @Override
    public boolean isUsernameForbidden(StoredProfile profile) {
        String playerPrefix = floodgate.getPlayerPrefix();
        return profile.getName().startsWith(playerPrefix) && !playerPrefix.isEmpty();
    }

    @Override
    public boolean performChecks(String username, LoginSource source) {
        // check if the Bedrock player is linked to a Java account
        FloodgatePlayer floodgatePlayer = getBedrockPlayer(username);
        if (floodgatePlayer == null) {
            // the player disconnected between the two Bedrock
            // session lookups — treat as no Bedrock context and skip
            if (core.isDebug()) {
                core.getPlugin().getLog().info(
                        "No Bedrock session for {} anymore — skipping Floodgate checks", username);
            }
            return true;
        }
        enforceNameConflict(floodgatePlayer, source);

        //Floodgate users don't need Java specific checks
        return true;
    }

    /**
     * Applies the configured name-conflict policy to a Bedrock player. The caller supplies the
     * disconnect source, so a platform without a pre-login hook can enforce the policy before
     * any automatic account action.
     *
     * @param player the Floodgate player (non-null)
     * @param source the source used to kick the player on a conflict
     */
    public void enforceNameConflict(FloodgatePlayer player, LoginSource source) {
        // A linked player legitimately uses the Java account's own name; the conflict switch
        // only concerns unlinked Bedrock names, as the backend check and the docs already say.
        if (player.getLinkedPlayer() != null) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info(
                        "Skipping name conflict checking for linked player {}", player.getCorrectUsername());
            }
            return;
        }
        String name = player.getCorrectUsername();
        // A name outside the Java charset (for example the default "." Floodgate prefix) can
        // never match a Mojang account, so asking Mojang would only waste a lookup.
        if (!JAVA_NAME.matcher(name).matches()) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info(
                        "Skipping name conflict checking for non-Java name {}", name);
            }
            return;
        }
        if ("false".equals(allowConflict) || "linked".equals(allowConflict)) {
            checkNameConflict(name, source);
        } else if (core.isDebug()) {
            core.getPlugin().getLog().info("Skipping name conflict checking for player {}", name);
        }
    }

    /**
     * The FloodgateApi does not support querying players by name, so this function
     * iterates over every online FloodgatePlayer and checks if the requested
     * username can be found
     *
     * @param prefixedUsername the name of the player with the prefix appended
     * @return FloodgatePlayer if found, null otherwise
     */
    public FloodgatePlayer getBedrockPlayer(String prefixedUsername) {
        for (FloodgatePlayer floodgatePlayer : floodgate.getPlayers()) {
            if (floodgatePlayer.getCorrectUsername().equals(prefixedUsername)) {
                return floodgatePlayer;
            }
        }

        return null;
    }

    public FloodgatePlayer getBedrockPlayer(UUID uuid) {
        return floodgate.getPlayer(uuid);
    }

    public boolean isBedrockPlayer(UUID uuid) {
        return getBedrockPlayer(uuid) != null;
    }

    public boolean isBedrockConnection(String username) {
        return getBedrockPlayer(username) != null;
    }
}
