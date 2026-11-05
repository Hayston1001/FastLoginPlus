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

    /**
     * Loads the profile owning the given UUID.
     *
     * @param uuid the UUID to look up
     * @return the matching profile, or null when no row holds this UUID
     * @throws ProfileLookupException when the lookup itself failed; callers must
     *         not treat this like an unknown UUID
     */
    StoredProfile loadProfile(UUID uuid);

    void save(StoredProfile playerProfile);

    boolean deleteProfile(String name);

    void close();
}
