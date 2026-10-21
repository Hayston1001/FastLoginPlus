/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.protocol.IdentityClaim;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Reads the live login profile without linking CraftBukkit/Authlib or inspecting network packets. */
final class SpigotProfileReader {
    private SpigotProfileReader() {
    }

    static Snapshot read(Object player) throws ReflectiveOperationException {
        Method getter;
        try {
            // CraftPlayer's live profile. getPlayerProfile() may expose only name/id and textures.
            getter = player.getClass().getMethod("getProfile");
        } catch (NoSuchMethodException absent) {
            getter = player.getClass().getMethod("getPlayerProfile");
        }
        return readProfile(getter.invoke(player), getter.getName());
    }

    static List<String> claimValues(Object profile) throws ReflectiveOperationException {
        return readProfile(profile, "profile").claims;
    }

    static Snapshot readProfile(Object profile, String source) throws ReflectiveOperationException {
        Object properties = accessor(profile, "getProperties", "properties");
        if (!(properties instanceof Iterable)) {
            // Authlib's PropertyMap is a multimap, not an Iterable; values() is a flattened view.
            properties = properties.getClass().getMethod("values").invoke(properties);
        }
        List<String> values = new ArrayList<>();
        List<String> signatures = new ArrayList<>();
        for (Object property : (Iterable<?>) properties) {
            Object name = accessor(property, "getName", "name");
            if (IdentityClaim.PROPERTY_NAME.equals(name)) {
                values.add((String) accessor(property, "getValue", "value"));
            } else if (IdentityClaim.SIGNATURE_PROPERTY_NAME.equals(name)) {
                signatures.add((String) accessor(property, "getValue", "value"));
            }
        }
        return new Snapshot(source, values, signatures);
    }

    private static Object accessor(Object target, String bean, String record) throws ReflectiveOperationException {
        Method getter;
        try {
            getter = target.getClass().getMethod(bean);
        } catch (NoSuchMethodException absent) {
            getter = target.getClass().getMethod(record);
        }
        return getter.invoke(target);
    }

    static final class Snapshot {
        final String source;
        final List<String> claims;
        final List<String> signatures;

        private Snapshot(String source, List<String> claims, List<String> signatures) {
            this.source = source;
            this.claims = claims;
            this.signatures = signatures;
        }
    }
}
