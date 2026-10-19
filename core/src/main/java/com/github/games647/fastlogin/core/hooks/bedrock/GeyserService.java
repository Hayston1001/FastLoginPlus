/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.hooks.bedrock;

import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.shared.LoginSource;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.session.GeyserSession;

import java.util.UUID;

public class GeyserService extends BedrockService<GeyserSession> {

    private final GeyserImpl geyser;
    private final FastLoginCore<?, ?, ?> core;
    private final AuthType authType;

    public GeyserService(GeyserImpl geyser, FastLoginCore<?, ?, ?> core) {
        super(core);
        this.geyser = geyser;
        this.core = core;
        this.authType = geyser.config().java().authType();
    }

    @Override
    public boolean performChecks(String username, LoginSource source) {
        // AuthType.FLOODGATE will be handled by FloodgateService
        if (authType == AuthType.ONLINE) {
            // authenticate everyone, as if they were Java players, since they have signed
            // in through Mojang
            return false;
        }
        if ("true".equals(allowConflict)) {
            if (core.isDebug()) {
                core.getPlugin().getLog().info("Skipping name conflict checking for player {}", username);
            }
        } else {
            super.checkNameConflict(username, source);
        }
        return true;
    }

    @Override
    public GeyserSession getBedrockPlayer(String username) {
        for (GeyserSession gSess : geyser.getSessionManager().getAllSessions()) {
            if (username.equals(gSess.getClientData().getUsername())) {
                return gSess;
            }
        }
        return null;
    }

    @Override
    public GeyserSession getBedrockPlayer(UUID uuid) {
        return geyser.connectionByUuid(uuid);
    }
}
