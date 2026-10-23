/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import java.util.UUID;

/** Stored credentials mark completed registration, never authenticate the connection. */
record BedrockAccount(String name, UUID uuid, String password, String kind, String forcedUuid) {
    static BedrockAccount read(Class<?> type, Object entry) {
        if (entry == null || !type.isInstance(entry)) {
            return null;
        }
        try {
            return new BedrockAccount((String) type.getField("username").get(entry),
                    (UUID) type.getField("uuid").get(entry), (String) type.getField("password").get(entry),
                    java.util.Objects.toString(type.getField("onlineAccount").get(entry)),
                    (String) type.getField("forcedUuid").get(entry));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("EasyAuth Bedrock ownership fields are unavailable", failure);
        }
    }

    boolean owns(String exactName, UUID signedUuid) {
        return exactName.equals(name) && signedUuid.equals(uuid) && password != null && !password.isEmpty()
                && ("FALSE".equals(kind) || "UNKNOWN".equals(kind)) && forcedUuid == null;
    }

    @Override
    public String toString() {
        return "BedrockAccount[credentials redacted]";
    }
}
