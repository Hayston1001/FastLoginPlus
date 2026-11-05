/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.storage;

/**
 * Signals that a profile lookup could not be completed.
 *
 * <p>This is not "no such profile": callers must fail closed instead of
 * treating the row as unknown. A name-change check that guesses "not renamed"
 * after a failed lookup stores the new name as a second row for the same UUID.</p>
 */
public class ProfileLookupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProfileLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
