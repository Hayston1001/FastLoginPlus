/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 Hayston1001
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
package com.github.hayston1001.fastloginplus.fabric;

import java.util.UUID;

/**
 * Classification of the local EasyAuth account behind one login name. The
 * backend reads this state itself and decides the login method; nothing here is
 * exposed to the proxy.
 */
enum AccountState {

    /** No account: an unclaimed name. */
    OPEN,
    /** Password account, still offline-owned. */
    PASSWORD,
    /** Password account with a live binding window. */
    BIND_PENDING,
    /** Premium-bound account (onlineAccount=TRUE). */
    BOUND,
    /** Account fields contradict the name or the expected shape. */
    CONFLICT;

    /** Account state plus the bound premium UUID, which only BOUND carries. */
    record Snapshot(AccountState state, UUID uuid) {
    }
}
