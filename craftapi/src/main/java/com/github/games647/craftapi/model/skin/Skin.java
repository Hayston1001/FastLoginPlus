/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi.model.skin;

import com.github.games647.craftapi.model.skin.Texture.Type;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class Skin {

    //the order and existence of these fields are relevant for GSON
    private final Instant timestamp;
    private final UUID profileId;
    private final String profileName;

    private final boolean signatureRequired = true;
    private final Map<Type, Texture> textures = new EnumMap<>(Type.class);

    private transient byte[] signature;

    /**
     * Creates a new decoded skin model
     * @param timestamp when was the skin fetched from Mojang
     * @param uuid premium UUID of the Owner
     * @param name case-sensitive name of the owner
     * @param skinHash short skin url or empty
     * @param skinModel true if slim model otherwise false for steve
     * @param capeHash short cape url or empty
     */
    public Skin(Instant timestamp, UUID uuid, String name, String skinHash, Model skinModel, String capeHash) {
        this.timestamp = timestamp;
        this.profileId = uuid;
        this.profileName = name;

        if (skinHash != null && !skinHash.isEmpty()) {
            textures.put(Type.SKIN, new Texture(skinHash, skinModel));
        }

        if (capeHash != null && !capeHash.isEmpty()) {
            textures.put(Type.CAPE, new Texture(Type.CAPE, capeHash));
        }
    }

    /**
     * @return time at the skin was fetched from Mojang
     */
    public Instant getTimeFetched() {
        return timestamp;
    }

    /**
     * @return user profile of the skin owner
     */
    public UUID getOwnerId() {
        return profileId;
    }

    /**
     * @return username of the skin owner
     */
    public String getOwnerName() {
        return profileName;
    }

    /**
     * @param type requesting skin component
     * @return skin, cape, elytra data or {@code Optional.empty()} if the player doesn't have this attribute
     */
    public Optional<Texture> getTexture(Type type) {
        Texture value = textures.get(type);
        if (value != null) {
            value.type = type;
        }

        return Optional.ofNullable(value);
    }

    /**
     * @return the raw signature to verify that the skin is from Mojang
     */
    public byte[] getSignature() {
        return signature;
    }

    /**
     * @param signature raw signature bytes
     */
    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof Skin) {
            Skin that = (Skin) other;
            return timestamp == that.timestamp
                    && Objects.equals(profileId, that.profileId)
                    && Objects.equals(profileName, that.profileName)
                    && Objects.equals(textures, that.textures);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, profileId, profileName, textures);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
                + "timestamp=" + timestamp
                + ", profileId=" + profileId
                + ", profileName='" + profileName + '\''
                + ", textures=" + textures
                + '}';
    }
}
