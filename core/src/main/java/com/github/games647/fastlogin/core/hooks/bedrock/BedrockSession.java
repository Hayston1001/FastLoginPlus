/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.hooks.bedrock;

import net.md_5.bungee.config.Configuration;

import java.util.Locale;

/** Trusted connection facts and policy, without linking the optional Floodgate API. */
public final class BedrockSession {
    private final boolean linked;
    private final String autoLogin;
    private final String autoRegister;
    private final String allowConflict;

    public BedrockSession(boolean linked, String autoLogin, String autoRegister, String allowConflict) {
        this.linked = linked;
        this.autoLogin = autoLogin;
        this.autoRegister = autoRegister;
        this.allowConflict = allowConflict;
    }

    public static BedrockSession fromConfig(Configuration config, boolean linked) {
        return new BedrockSession(linked, value(config, "autoLoginFloodgate"),
                value(config, "autoRegisterFloodgate"), value(config, "allowFloodgateNameConflict"));
    }

    private static String value(Configuration config, String key) {
        Object value = config.get(key);
        return value == null ? "false" : value.toString().toLowerCase(Locale.ROOT);
    }

    public boolean isLinked() {
        return linked;
    }

    public static boolean isAutoAuthAllowed(String value, boolean linked) {
        return "true".equals(value) || "no-conflict".equals(value) || "linked".equals(value) && linked;
    }
    public String getAutoLogin() {
        return autoLogin;
    }

    public String getAutoRegister() {
        return autoRegister;
    }

    public String getAllowConflict() {
        return allowConflict;
    }
}
