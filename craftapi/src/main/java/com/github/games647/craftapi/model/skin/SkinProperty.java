/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi.model.skin;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.Objects;

/**
 * Base64 encoded skin property.
 */
public class SkinProperty {

    private static final String SIGNATURE_ALG = "SHA1withRSA";

    private final String value;
    private final String signature;

    /**
     * Creates a new skin property that can be assigned to in game profiles.
     *
     * @param value base64 encoded json skin data
     * @param signature base64 encoded signature
     */
    public SkinProperty(String value, String signature) {
        this.value = value;
        this.signature = signature;
    }

    /**
     * @return base64 encoded data of the skin, cape and elytra data. See {@link Skin} for an example how the
     *          skin would look like if it's decoded.
     */
    public String getValue() {
        return value;
    }

    /**
     * @return base64 encoded signature that the skin was uploaded to Mojang servers.
     */
    public String getSignature() {
        return signature;
    }

    /**
     * @param publicKey Mojang's public key (yggdrasil)
     * @return true if the signature is correctly signed by Mojang
     * @throws InvalidKeyException the public key wasn't correctly loaded
     * @throws SignatureException public key doesn't have the correct size
     */
    public boolean isValid(PublicKey publicKey) throws InvalidKeyException, SignatureException {
        Signature sign;
        try {
            sign = Signature.getInstance(SIGNATURE_ALG);
        } catch (NoSuchAlgorithmException noSuckAlgEx) {
            //SHA1withRSA should be present in all platforms
            throw new AssertionError("The signature algorithm " + SIGNATURE_ALG + " doesn't exist in this environment");
        }

        sign.initVerify(publicKey);
        sign.update(value.getBytes());

        byte[] decodedSignature = Base64.getDecoder().decode(signature);
        return sign.verify(decodedSignature);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof SkinProperty) {
            SkinProperty property = (SkinProperty) other;
            return Objects.equals(value, property.value)
                    && Objects.equals(signature, property.signature);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, signature);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
                + "value='" + value + '\''
                + ", signature='" + signature + '\''
                + '}';
    }
}
