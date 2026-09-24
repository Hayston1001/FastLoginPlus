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
package com.github.games647.fastlogin.core.message;

import com.github.games647.fastlogin.core.message.LoginActionMessage.Type;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * — the proxy-verified UUID is an <em>optional trailing field</em>, which makes the
 * compatibility matrix asymmetric and worth pinning down:
 *
 * <ul>
 *   <li>new proxy → old backend: the backend stops reading after proxyId and never looks at the
 *       extra bytes, so the frame must stay prefix-compatible.</li>
 *   <li>old proxy → new backend: nothing follows proxyId, so the reader hits EOF mid-field. That
 *       is a normal state, not an error — it means "this proxy declares nothing".</li>
 * </ul>
 */
class LoginActionMessageTest {

    private static final UUID PROXY_ID = UUID.fromString("c1a0d5d2-9e5b-4d0a-9c1e-2b4f6a8c0d1e");
    private static final UUID PREMIUM_UUID = UUID.fromString("272cb3e9-24d3-4dcd-b47c-4b786e7421f8");

    @Test
    void roundTripPreservesVerifiedPremiumUuid() {
        LoginActionMessage original = new LoginActionMessage(Type.LOGIN, "Steve", PROXY_ID, PREMIUM_UUID);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        original.writeTo(out);

        LoginActionMessage decoded = new LoginActionMessage();
        decoded.readFrom(ByteStreams.newDataInput(out.toByteArray()));

        assertEquals(Type.LOGIN, decoded.getType());
        assertEquals("Steve", decoded.getPlayerName());
        assertEquals(PROXY_ID, decoded.getProxyId());
        assertEquals(PREMIUM_UUID, decoded.getVerifiedPremiumUuid());
        assertEquals(LoginActionMessage.FORCE_CHANNEL, decoded.getChannelName());
    }

    @Test
    void declaredNothingRoundTripsAsNull() {
        // cracked login, Floodgate or a forced login: the proxy verified no Mojang UUID
        LoginActionMessage original = new LoginActionMessage(Type.LOGIN, "Alex", PROXY_ID, null);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        original.writeTo(out);

        LoginActionMessage decoded = new LoginActionMessage();
        decoded.readFrom(ByteStreams.newDataInput(out.toByteArray()));

        assertEquals("Alex", decoded.getPlayerName());
        assertNull(decoded.getVerifiedPremiumUuid());
    }

    @Test
    void legacyFrameWithoutVerifiedUuidReadsAsNull() {
        // legacy payload from a pre-0.7.0 proxy: type + name + proxyId, nothing more.
        // The reader must treat the resulting EOF as "nothing declared", not as corruption.
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(Type.REGISTER.ordinal());
        out.writeUTF("Steve");
        out.writeLong(PROXY_ID.getMostSignificantBits());
        out.writeLong(PROXY_ID.getLeastSignificantBits());

        LoginActionMessage decoded = new LoginActionMessage();
        decoded.readFrom(ByteStreams.newDataInput(out.toByteArray()));

        assertEquals(Type.REGISTER, decoded.getType());
        assertEquals("Steve", decoded.getPlayerName());
        assertEquals(PROXY_ID, decoded.getProxyId());
        assertNull(decoded.getVerifiedPremiumUuid());
    }

    @Test
    void legacyFrameWithOneTrailingLongAlsoReadsAsNull() {
        // half the field present — still EOF, still "nothing declared"
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(Type.LOGIN.ordinal());
        out.writeUTF("Steve");
        out.writeLong(PROXY_ID.getMostSignificantBits());
        out.writeLong(PROXY_ID.getLeastSignificantBits());
        out.writeLong(PREMIUM_UUID.getMostSignificantBits());

        LoginActionMessage decoded = new LoginActionMessage();
        decoded.readFrom(ByteStreams.newDataInput(out.toByteArray()));

        assertNull(decoded.getVerifiedPremiumUuid());
    }

    @Test
    void newFrameStaysReadableByLegacyReader() {
        // a pre-0.7.0 backend reads only the first three fields and ignores the rest,
        // so the new frame must remain prefix-compatible
        LoginActionMessage original = new LoginActionMessage(Type.LOGIN, "Steve", PROXY_ID, PREMIUM_UUID);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        original.writeTo(out);

        ByteArrayDataInput legacyReader = ByteStreams.newDataInput(out.toByteArray());
        assertEquals(Type.LOGIN.ordinal(), legacyReader.readByte());
        assertEquals("Steve", legacyReader.readUTF());
        UUID proxyId = new UUID(legacyReader.readLong(), legacyReader.readLong());

        assertEquals(PROXY_ID, proxyId);
        // the legacy reader is done here; the trailing 16 bytes are simply never consumed
    }

    @Test
    void invalidTypeByteIsRejected() {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(Type.values().length);
        out.writeUTF("Steve");
        out.writeLong(0L);
        out.writeLong(0L);

        LoginActionMessage decoded = new LoginActionMessage();
        assertThrows(IllegalArgumentException.class,
                () -> decoded.readFrom(ByteStreams.newDataInput(out.toByteArray())));
    }
}
