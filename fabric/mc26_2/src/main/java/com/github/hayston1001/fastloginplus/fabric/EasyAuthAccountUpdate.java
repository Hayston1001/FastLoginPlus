/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import java.time.ZonedDateTime;
import java.lang.reflect.Field;
import java.util.UUID;

/** Keeps EasyAuth's live account entry intact when its synchronous update fails. */
final class EasyAuthAccountUpdate {

    @FunctionalInterface
    interface StorageUpdate {
        boolean save(Object entry);
    }

    private EasyAuthAccountUpdate() {
    }

    /**
     * Resolves and snapshots both rows before a proxy request can be sent.
     *
     * @param entryType EasyAuth account class
     * @param entry durable account snapshot to save
     * @param cached live account, if present
     * @param offlineUuid UUID for subsequent password logins
     * @param update synchronous storage operation
     * @return local unbind operation with rollback on storage failure
     */
    static Runnable prepareUnbind(Class<?> entryType, Object entry, Object cached, UUID offlineUuid,
                                  StorageUpdate update) {
        return prepareState(entryType, entry, cached, "FALSE", offlineUuid, "cracked-save-failed", update);
    }

    /**
     * Resets a passwordless bound account to normal registration without deleting its row.
     *
     * @param entryType EasyAuth account class
     * @param entry durable account snapshot to save
     * @param cached live account, if present
     * @param update synchronous storage operation
     * @return local release operation with rollback on storage failure
     */
    static Runnable prepareOpen(Class<?> entryType, Object entry, Object cached, StorageUpdate update) {
        return prepareState(entryType, entry, cached, "UNKNOWN", null, "cracked-release-failed", update);
    }

    private static Runnable prepareState(Class<?> entryType, Object entry, Object cached, String accountKind,
                                         UUID accountUuid, String failureKey, StorageUpdate update) {
        try {
            Field kind = entryType.getField("onlineAccount");
            Field uuid = entryType.getField("uuid");
            Object[] rows = cached == null || cached == entry ? new Object[]{entry} : new Object[]{entry, cached};
            Object[] oldKinds = new Object[rows.length];
            Object[] oldUuids = new Object[rows.length];
            for (int i = 0; i < rows.length; i++) {
                oldKinds[i] = kind.get(rows[i]);
                oldUuids[i] = uuid.get(rows[i]);
            }
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object nextKind = Enum.valueOf((Class<Enum>) oldKinds[0].getClass(), accountKind);
            return () -> {
                try {
                    for (Object row : rows) {
                        kind.set(row, nextKind);
                        uuid.set(row, accountUuid);
                    }
                    if (!update.save(entry)) {
                        throw new IllegalStateException("EasyAuth account unbind failed");
                    }
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    for (int i = 0; i < rows.length; i++) {
                        try {
                            kind.set(rows[i], oldKinds[i]);
                            uuid.set(rows[i], oldUuids[i]);
                        } catch (ReflectiveOperationException | RuntimeException restoreFailure) {
                            failure.addSuppressed(restoreFailure);
                        }
                    }
                    throw FabricMessages.failure(failureKey, failure);
                }
            };
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("EasyAuth account unbind API is unavailable", failure);
        }
    }

    static void authenticate(Class<?> entryType, Object entry, UUID verifiedUuid, ZonedDateTime now,
                             String ip, StorageUpdate update) {
        persist(entryType, entry, verifiedUuid, new LoginUpdate(now, ip), update);
    }

    private record LoginUpdate(ZonedDateTime now, String ip) {
    }

    private static void persist(Class<?> entryType, Object entry, UUID verifiedUuid, LoginUpdate login,
                                StorageUpdate update) {
        Object oldPassword = get(entryType, entry, "password");
        Object oldKind = get(entryType, entry, "onlineAccount");
        Object oldUuid = get(entryType, entry, "uuid");
        Object oldDate = get(entryType, entry, "lastAuthenticatedDate");
        Object oldTries = get(entryType, entry, "loginTries");
        Object oldIp = get(entryType, entry, "lastIp");
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object premium = Enum.valueOf((Class<Enum>) oldKind.getClass(), "TRUE");
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
