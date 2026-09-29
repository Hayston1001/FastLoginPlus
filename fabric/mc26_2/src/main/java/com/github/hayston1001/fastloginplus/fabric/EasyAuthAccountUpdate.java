/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import java.time.ZonedDateTime;
import java.util.UUID;

/** Keeps EasyAuth's live account entry intact when its synchronous update fails. */
final class EasyAuthAccountUpdate {

    @FunctionalInterface
    interface StorageUpdate {
        boolean save(Object entry);
    }

    private EasyAuthAccountUpdate() {
    }

    static void bind(Class<?> entryType, Object entry, UUID verifiedUuid, StorageUpdate update) {
        persist(entryType, entry, verifiedUuid, null, true, update);
    }

    static void authenticate(Class<?> entryType, Object entry, UUID verifiedUuid, ZonedDateTime now,
                             String ip, boolean binding, StorageUpdate update) {
        persist(entryType, entry, verifiedUuid, new LoginUpdate(now, ip), binding, update);
    }

    private record LoginUpdate(ZonedDateTime now, String ip) {
    }

    private static void persist(Class<?> entryType, Object entry, UUID verifiedUuid, LoginUpdate login,
                                boolean clearPassword, StorageUpdate update) {
        Object oldPassword = get(entryType, entry, "password");
        Object oldKind = get(entryType, entry, "onlineAccount");
        Object oldUuid = get(entryType, entry, "uuid");
        Object oldDate = get(entryType, entry, "lastAuthenticatedDate");
        Object oldTries = get(entryType, entry, "loginTries");
        Object oldIp = get(entryType, entry, "lastIp");
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object premium = Enum.valueOf((Class<Enum>) oldKind.getClass(), "TRUE");
            if (clearPassword) {
                set(entryType, entry, "password", "");
            }
            set(entryType, entry, "onlineAccount", premium);
            set(entryType, entry, "uuid", verifiedUuid);
            if (login != null) {
                set(entryType, entry, "lastAuthenticatedDate", login.now());
                set(entryType, entry, "loginTries", 0L);
                set(entryType, entry, "lastIp", login.ip());
            }
            if (!update.save(entry)) {
                throw new IllegalStateException("EasyAuth account update failed");
            }
        } catch (RuntimeException failure) {
            set(entryType, entry, "password", oldPassword);
            set(entryType, entry, "onlineAccount", oldKind);
            set(entryType, entry, "uuid", oldUuid);
            set(entryType, entry, "lastAuthenticatedDate", oldDate);
            set(entryType, entry, "loginTries", oldTries);
            set(entryType, entry, "lastIp", oldIp);
            throw failure;
        }
    }

    private static Object get(Class<?> type, Object entry, String name) {
        try {
            return type.getField(name).get(entry);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("EasyAuth field is unavailable: " + name, error);
        }
    }

    private static void set(Class<?> type, Object entry, String name, Object value) {
        try {
            type.getField(name).set(entry, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("EasyAuth field cannot be updated: " + name, error);
        }
    }
}
