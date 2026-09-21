/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.craftapi.resolver;

import java.util.UUID;

/**
 * Exception that occurs if we made too many requests against an online resolver.
 */
public class RateLimitException extends Exception {

    public static final int RATE_LIMIT_RESPONSE_CODE = 429;

    /**
     * Generic rate limitation
     */
    public RateLimitException() {
        super("Too many requests", null, true, false);
    }

    /**
     * Rate limitation for the given player name.
     *
     * @param playerName name of the player whose UUID could not be resolved
     */
    public RateLimitException(String playerName) {
        super("Too many requests for the UUID of player " + playerName, null, true, false);
    }

    /**
     * Rate limit for skin download of the specified account UUID.
     *
     * @param skinId UUID of the account whose skin could not be resolved
     */
    public RateLimitException(UUID skinId) {
        super("Too many requests for skin " + skinId, null, true, false);
    }
}
