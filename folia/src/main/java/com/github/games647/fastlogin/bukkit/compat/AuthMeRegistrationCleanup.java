/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.compat;

import com.github.games647.fastlogin.core.hooks.PreLoginAuthPlugin.Registration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Synchronous row fingerprint and cache operations for failed pre-login registrations. */
final class AuthMeRegistrationCleanup {

    private AuthMeRegistrationCleanup() {
    }

    static Registration capture(Object source, String name) throws Exception {
        synchronized (source) {
            Object auth = source.getClass().getMethod("getAuth", String.class).invoke(source, name);
            Registration registration = snapshot(auth);
            return registration != null && name.equals(registration.getPlayerName()) ? registration : null;
        }
    }

    static boolean delete(Object source, Registration registration, Object players, Object pending, Object verifier)
            throws Exception {
        String name = registration.getPlayerName();
        Method getAuth = source.getClass().getMethod("getAuth", String.class);
        Method removeAuth = source.getClass().getMethod("removeAuth", String.class);
        synchronized (source) {
            // Resolve all cache boundaries before removing anything. Capture entries by identity:
            // PlayerAuth.equals() is not a row fingerprint, and a reconnect can replace a cache entry.
            CacheEntry playerEntry = new CacheEntry(playerMap(players), name);
            CacheEntry pendingEntry = new CacheEntry(fieldMap(pending, "pending"), name);
            CacheEntry verifiedEntry = new CacheEntry(fieldMap(verifier, "verified"), name);
            if (!registration.matches(snapshot(getAuth.invoke(source, name)))
                    || playerEntry.value != null && !registration.matches(snapshot(playerEntry.value))) {
                return false;
            }
            boolean removed = (boolean) removeAuth.invoke(source, name);
            boolean cleared = false;
            if (removed) {
                cleared = playerEntry.clear();
                cleared &= pendingEntry.clear();
                cleared &= verifiedEntry.clear();
            }
            // A false remove is failure even if another writer has meanwhile removed the row.
            Object remaining = getAuth.invoke(source, name);
            return removed && cleared && remaining == null;
        }
    }

    private static Registration snapshot(Object auth) throws Exception {
        if (auth == null) {
            return null;
        }
        Class<?> type = auth.getClass();
        String name = (String) type.getMethod("getNickname").invoke(auth);
        Object password = type.getMethod("getPassword").invoke(auth);
        if (password == null) {
            return null;
        }
        String hash = (String) password.getClass().getMethod("getHash").invoke(password);
        String salt = (String) password.getClass().getMethod("getSalt").invoke(password);
        if (name == null || hash == null || hash.isEmpty()) {
            return null;
        }
        UUID uuid = (UUID) type.getMethod("getUuid").invoke(auth);
        UUID premiumUuid = (UUID) type.getMethod("getPremiumUuid").invoke(auth);
        long date = ((Number) type.getMethod("getRegistrationDate").invoke(auth)).longValue();
        Long lastLogin = (Long) type.getMethod("getLastLogin").invoke(auth);
        return new Registration(name, hash, salt, uuid, date, premiumUuid, lastLogin);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<String, Object> playerMap(Object players) throws Exception {
        Object map = players.getClass().getMethod("getCache").invoke(players);
        if (!(map instanceof ConcurrentHashMap)) {
            throw new IllegalStateException("AuthMe player cache lacks the reviewed atomic map");
        }
        return (ConcurrentMap<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<String, Object> fieldMap(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        Object map = field.get(owner);
        if (!(map instanceof ConcurrentHashMap)) {
            throw new IllegalStateException("AuthMe registration cache lacks the reviewed atomic map");
        }
        return (ConcurrentMap<String, Object>) map;
    }

    private static final class CacheEntry {
        private final ConcurrentMap<String, Object> cache;
        private final String name;
        private final Object value;

        private CacheEntry(ConcurrentMap<String, Object> cache, String name) {
            this.cache = cache;
            this.name = name;
            value = cache.get(name);
        }

        private boolean clear() {
            AtomicBoolean unchanged = new AtomicBoolean(true);
            cache.compute(name, (key, current) -> {
                if (current != value) {
                    unchanged.set(false);
                    return current;
                }
                return null;
            });
            return unchanged.get();
        }
    }
}
