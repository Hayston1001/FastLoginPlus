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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated one-shot EasyAuth ownership query made before proxy PreLogin completes. */
public final class AccountStatusWire {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern NONCE = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Backend account classification. */
    public enum Status {
        OPEN, PASSWORD, BIND_PENDING, BOUND, CONFLICT
    }

    private AccountStatusWire() {
    }

    /**
     * Creates a fresh nonce for one query.
     * @return URL-safe random nonce
     */
    public static String nonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Encodes an authenticated request.
     * @param name exact player name
     * @param nonce one-use nonce
     * @param key shared secret bytes
     * @return request line
     */
    public static String request(String name, String nonce, byte[] key) {
        requireName(name);
        requireNonce(nonce);
        String payload = "1:Q:" + name + ":" + nonce;
        return payload + ":" + tag(payload, key);
    }

    /**
     * Verifies and parses a request.
     * @param line bounded request line
     * @param key shared secret bytes
     * @return requested player name and nonce
     */
    public static Request parseRequest(String line, byte[] key) {
        String[] parts = split(line, 5);
        if (!"1".equals(parts[0]) || !"Q".equals(parts[1])) {
            throw new IllegalArgumentException("Unsupported account request");
        }
        requireName(parts[2]);
        requireNonce(parts[3]);
        verify(line, parts[4], key);
        return new Request(parts[2], parts[3]);
    }

    /**
     * Encodes a response bound to the original nonce.
     * @param nonce original request nonce
     * @param status classified account state
     * @param uuid bound premium UUID, only for BOUND
     * @param key shared secret bytes
     * @return response line
     */
    public static String response(String nonce, Status status, UUID uuid, byte[] key) {
        requireNonce(nonce);
        if ((status == Status.BOUND) != (uuid != null)) {
            throw new IllegalArgumentException("Bound account must carry exactly one UUID");
        }
        String payload = "1:R:" + nonce + ":" + status.name() + ":" + (uuid == null ? "-" : uuid);
        return payload + ":" + tag(payload, key);
    }

    /**
     * Builds a locally classified result before it is sent over the wire.
     * @param status account state
     * @param uuid bound UUID, only for BOUND
     * @return account result
     */
    public static Result result(Status status, UUID uuid) {
        if ((status == Status.BOUND) != (uuid != null)) {
            throw new IllegalArgumentException("Bound account must carry exactly one UUID");
        }
        return new Result(status, uuid);
    }

    /**
     * Verifies the response and the nonce chosen by this proxy connection.
     * @param line bounded response line
     * @param expectedNonce request nonce
     * @param key shared secret bytes
     * @return classified account state
     */
    public static Result parseResponse(String line, String expectedNonce, byte[] key) {
        String[] parts = split(line, 6);
        if (!"1".equals(parts[0]) || !"R".equals(parts[1]) || !expectedNonce.equals(parts[2])) {
            throw new IllegalArgumentException("Mismatched account response");
        }
        requireNonce(parts[2]);
        verify(line, parts[5], key);
        Status status = Status.valueOf(parts[3]);
        UUID uuid = "-".equals(parts[4]) ? null : UUID.fromString(parts[4]);
        if ((status == Status.BOUND) != (uuid != null)) {
            throw new IllegalArgumentException("Invalid account response UUID");
        }
        return new Result(status, uuid);
    }

    private static String[] split(String line, int fields) {
        if (line == null || line.length() > 160) {
            throw new IllegalArgumentException("Invalid account message length");
        }
        String[] parts = line.split(":", -1);
        if (parts.length != fields) {
            throw new IllegalArgumentException("Invalid account message shape");
        }
        return parts;
    }

    private static void verify(String line, String received, byte[] key) {
        if (!TAG.matcher(received).matches()) {
            throw new IllegalArgumentException("Invalid account message tag");
        }
        String payload = line.substring(0, line.lastIndexOf(':'));
        byte[] expected = Base64.getUrlDecoder().decode(tag(payload, key));
        byte[] actual = Base64.getUrlDecoder().decode(received);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("Unauthenticated account message");
        }
    }

    private static String tag(String payload, byte[] key) {
        if (key == null || key.length < 32) {
            throw new IllegalArgumentException("Account query key is too short");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(("FLP-ACCOUNT\n" + payload).getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Account HMAC unavailable", error);
        }
    }

    private static void requireName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid account name");
        }
    }

    private static void requireNonce(String nonce) {
        if (nonce == null || !NONCE.matcher(nonce).matches()) {
            throw new IllegalArgumentException("Invalid account nonce");
        }
    }

    /** Verified query coordinates. */
    public static final class Request {
        private final String name;
        private final String nonce;

        private Request(String name, String nonce) {
            this.name = name;
            this.nonce = nonce;
        }

        public String getName() {
            return name;
        }

        public String getNonce() {
            return nonce;
        }
    }

    /** Verified backend classification. */
    public static final class Result {
        private final Status status;
        private final UUID uuid;

        private Result(Status status, UUID uuid) {
            this.status = status;
            this.uuid = uuid;
        }

        public Status getStatus() {
            return status;
        }

        public UUID getUuid() {
            return uuid;
        }
    }
}
