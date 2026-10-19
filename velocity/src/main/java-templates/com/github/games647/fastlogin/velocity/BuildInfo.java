/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity;

public class BuildInfo {
    private BuildInfo() {
    }

    public static final String DISPLAY_NAME = "${displayName}";
    public static final String NAME = "${pluginId}";
    public static final String VERSION = "${projectVersion}";
    public static final String DESCRIPTION = "${description}";
    public static final String URL = "${projectUrl}";
}
