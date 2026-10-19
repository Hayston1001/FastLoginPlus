/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.storage;

import java.util.UUID;

public interface AuthStorage {
    StoredProfile loadProfile(String name);

    StoredProfile loadProfile(UUID uuid);

    void save(StoredProfile playerProfile);

    boolean deleteProfile(String name);

    void close();
}
