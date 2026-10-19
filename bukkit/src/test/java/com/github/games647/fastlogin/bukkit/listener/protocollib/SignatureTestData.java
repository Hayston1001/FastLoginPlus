/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.annotations.JsonAdapter;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SignatureTestData {

    public static SignatureTestData fromResource(String resourceName) throws IOException {
        URL keyUrl = Resources.getResource(resourceName);
        String encodedSignature = Resources.toString(keyUrl, StandardCharsets.US_ASCII);

        return new Gson().fromJson(encodedSignature, SignatureTestData.class);
    }

    @JsonAdapter(Base64Adapter.class)
    private byte[] nonce;

    private SignatureData signature;

    public byte[] getNonce() {
        return nonce;
    }

    public SignatureData getSignature() {
        return signature;
    }

    public static class SignatureData {

        private long salt;

        @JsonAdapter(Base64Adapter.class)
        private byte[] signature;

        public long getSalt() {
            return salt;
        }

        public byte[] getSignature() {
            return signature;
        }
    }
}
