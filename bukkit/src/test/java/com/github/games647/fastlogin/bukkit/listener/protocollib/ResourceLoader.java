/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.github.games647.fastlogin.bukkit.listener.protocollib.packet.ClientPublicKey;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

public class ResourceLoader {

    public static RSAPrivateKey parsePrivateKey(String keySpec)
        throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        try (
            Reader reader = new StringReader(keySpec);
            PemReader pemReader = new PemReader(reader)
        ) {
            PemObject pemObject = pemReader.readPemObject();
            byte[] content = pemObject.getContent();
            KeySpec privateKeySpec = new PKCS8EncodedKeySpec(content);

            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) factory.generatePrivate(privateKeySpec);
        }
    }

    protected static ClientPublicKey loadClientKey(String path)
        throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        URL keyUrl = Resources.getResource(path);

        String lines = Resources.toString(keyUrl, StandardCharsets.US_ASCII);
        JsonObject object = new Gson().fromJson(lines, JsonObject.class);

        Instant expires = Instant.parse(object.getAsJsonPrimitive("expires_at").getAsString());
        String key = object.getAsJsonPrimitive("key").getAsString();
        RSAPublicKey publicKey = parsePublicKey(key);

        byte[] signature = Base64.getDecoder().decode(object.getAsJsonPrimitive("signature").getAsString());
        return ClientPublicKey.of(expires, publicKey, signature);
    }

    private static RSAPublicKey parsePublicKey(String keySpec)
        throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        try (
            Reader reader = new StringReader(keySpec);
            PemReader pemReader = new PemReader(reader)
        ) {
            PemObject pemObject = pemReader.readPemObject();
            byte[] content = pemObject.getContent();
            KeySpec pubKeySpec = new X509EncodedKeySpec(content);

            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) factory.generatePublic(pubKeySpec);
        }
    }
}
