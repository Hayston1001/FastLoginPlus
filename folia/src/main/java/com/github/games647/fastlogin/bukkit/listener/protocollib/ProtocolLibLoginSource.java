/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.listener.protocollib.packet.ClientPublicKey;
import com.github.games647.fastlogin.core.shared.LoginSource;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Random;

import static com.comphenix.protocol.PacketType.Login.Server.ENCRYPTION_BEGIN;

class ProtocolLibLoginSource implements LoginSource {

    private final FastLoginBukkit plugin;

    private final Player player;

    private final Random random;

    private final ClientPublicKey clientKey;
    private final PublicKey publicKey;

    private byte[] verifyToken;

    // Set to true when kick() is called, so NameCheckTask can check it
    // after onLogin() returns and cancel the START packet.
    private boolean kicked;

    ProtocolLibLoginSource(FastLoginBukkit plugin, Player player, Random random,
                           PublicKey serverPublicKey, ClientPublicKey clientKey) {
        this.plugin = plugin;
        this.player = player;
        this.random = random;
        this.publicKey = serverPublicKey;
        this.clientKey = clientKey;
    }

    @Override
    public void enableOnlinemode() {
        verifyToken = EncryptionUtil.generateVerifyToken(random);

        /*
         * Packet Information: https://wiki.vg/Protocol#Encryption_Request
         *
         * ServerID="" (String) key=public server key verifyToken=random 4 byte array
         */
        PacketContainer newPacket = new PacketContainer(ENCRYPTION_BEGIN);

        newPacket.getStrings().write(0, "");
        StructureModifier<PublicKey> keyModifier = newPacket.getSpecificModifier(PublicKey.class);
        int verifyField = 0;
        if (keyModifier.getFields().isEmpty()) {
            // Since 1.16.4 this is now a byte field
            newPacket.getByteArrays().write(0, publicKey.getEncoded());
            verifyField++;
        } else {
            keyModifier.write(0, publicKey);
        }

        newPacket.getByteArrays().write(verifyField, verifyToken);
        // shouldAuthenticate, but why does this field even exist?
        newPacket.getBooleans().writeSafely(0, true);

        //serverId is an empty string
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, newPacket);
    }

    @Override
    public void kick(String message) {
        kicked = true;
        // the login-phase DISCONNECT packet is the only way the client can show
        // the reason; every refusal in this package goes through it
        ProtocolLibCompat.refuseLogin(plugin.getLog(), plugin.getCore().isDebug(), player, message);
    }

    @Override
    public boolean isKicked() {
        return kicked;
    }

    @Override
    public InetSocketAddress getAddress() {
        return player.getAddress();
    }

    public ClientPublicKey getClientKey() {
        return clientKey;
    }

    public byte[] getVerifyToken() {
        return verifyToken.clone();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
            + "player=" + player
            + ", random=" + random
            + ", verifyToken=" + Arrays.toString(verifyToken)
            + '}';
    }
}
