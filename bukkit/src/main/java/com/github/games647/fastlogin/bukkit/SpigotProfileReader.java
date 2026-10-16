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
        return new Snapshot(getter.getName(), claimValues(getter.invoke(player)));
    }

    static List<String> claimValues(Object profile) throws ReflectiveOperationException {
        Object properties = accessor(profile, "getProperties", "properties");
        if (!(properties instanceof Iterable)) {
            // Authlib's PropertyMap is a multimap, not an Iterable; values() is a flattened view.
            properties = properties.getClass().getMethod("values").invoke(properties);
        }
        List<String> values = new ArrayList<>();
        for (Object property : (Iterable<?>) properties) {
            if (IdentityClaim.PROPERTY_NAME.equals(accessor(property, "getName", "name"))) {
                values.add((String) accessor(property, "getValue", "value"));
            }
        }
        return values;
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

        private Snapshot(String source, List<String> claims) {
            this.source = source;
            this.claims = claims;
        }
    }
}
