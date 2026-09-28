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
package com.github.games647.fastlogin.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountStatusWireTest {

    private static final byte[] KEY = new byte[32];
    private static final byte[] WRONG_KEY = new byte[32];
    private static final UUID UUID_ONE = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    static {
        WRONG_KEY[0] = 1;
    }

    @Test
    void authenticatesOwnershipAndBoundUuid() {
        String nonce = AccountStatusWire.nonce();
        String request = AccountStatusWire.request("Player_1", nonce, KEY);
        AccountStatusWire.Request parsed = AccountStatusWire.parseRequest(request, KEY);
        assertEquals("Player_1", parsed.getName());
        assertEquals(nonce, parsed.getNonce());

        String response = AccountStatusWire.response(nonce, AccountStatusWire.Status.BOUND, UUID_ONE, KEY);
        AccountStatusWire.Result result = AccountStatusWire.parseResponse(response, nonce, KEY);
        assertEquals(AccountStatusWire.Status.BOUND, result.getStatus());
        assertEquals(UUID_ONE, result.getUuid());
    }

    @Test
    void rejectsWrongKeyReplayAndTampering() {
        String nonce = AccountStatusWire.nonce();
        String request = AccountStatusWire.request("Player_1", nonce, KEY);
        assertThrows(IllegalArgumentException.class, () -> AccountStatusWire.parseRequest(request, WRONG_KEY));
        assertThrows(IllegalArgumentException.class, () -> AccountStatusWire.parseRequest(
                request.replace("Player_1", "Player_2"), KEY));

        String response = AccountStatusWire.response(nonce, AccountStatusWire.Status.OPEN, null, KEY);
        assertThrows(IllegalArgumentException.class, () -> AccountStatusWire.parseResponse(
                response, AccountStatusWire.nonce(), KEY));
        assertThrows(IllegalArgumentException.class, () -> AccountStatusWire.parseResponse(
                response.replace("OPEN", "PASSWORD"), nonce, KEY));
        assertThrows(IllegalArgumentException.class, () -> AccountStatusWire.parseResponse(
                response, nonce, WRONG_KEY));
    }

    @Test
    void authenticatesPendingBindingWithoutClaimingOwnership() {
        String nonce = AccountStatusWire.nonce();
        String response = AccountStatusWire.response(nonce, AccountStatusWire.Status.BIND_PENDING, null, KEY);
        AccountStatusWire.Result result = AccountStatusWire.parseResponse(response, nonce, KEY);
        assertEquals(AccountStatusWire.Status.BIND_PENDING, result.getStatus());
        assertEquals(null, result.getUuid());
        assertThrows(IllegalArgumentException.class, () -> AccountStatusWire.response(
                nonce, AccountStatusWire.Status.BIND_PENDING, UUID_ONE, KEY));
    }
}
