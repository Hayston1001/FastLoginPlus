/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.message;

public class NamespaceKey {

    private static final char SEPARATOR_CHAR = ':';

    private final String namespace;
    private final String key;

    private final String combined;

    public NamespaceKey(String namespace, String key) {
        // Locale.ROOT: the default locale must not rewrite channel names
        // (e.g. Turkish 'I' -> 'ı' would break plugin messaging)
        this.namespace = namespace.toLowerCase(java.util.Locale.ROOT);
        this.key = key.toLowerCase(java.util.Locale.ROOT);

        this.combined = this.namespace + SEPARATOR_CHAR + this.key;
    }

    public String getCombinedName()  {
        return combined;
    }

    public static String getCombined(String namespace, String key) {
        return new NamespaceKey(namespace, key).combined;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getKey() {
        return key;
    }
}
