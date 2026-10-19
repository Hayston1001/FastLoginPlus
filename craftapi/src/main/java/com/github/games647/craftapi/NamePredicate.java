/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Validate if it's a valid Mojang player account name.
 */
public class NamePredicate implements Predicate<String> {

    // this includes a-zA-Z0-9_
    // compile the pattern only on plugin enable -> and this have to be thread-safe
    private final Pattern validNameMatcher = Pattern.compile("^[a-zA-Z0-9_]{2,16}$");

    /**
     * @param playerName player name that should be checked
     * @return true if it's a valid Mojang account name
     */
    @Override
    public boolean test(String playerName) {
        return validNameMatcher.matcher(playerName).matches();
    }
}
