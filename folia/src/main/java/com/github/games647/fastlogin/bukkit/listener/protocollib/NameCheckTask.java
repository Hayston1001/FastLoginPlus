/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketEvent;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginPreLoginEvent;
import com.github.games647.fastlogin.bukkit.listener.protocollib.packet.ClientPublicKey;
import com.github.games647.fastlogin.core.shared.JoinManagement;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.security.PublicKey;
import java.util.Random;

public class NameCheckTask extends JoinManagement<Player, CommandSender, ProtocolLibLoginSource>
    implements Runnable {

    private final FastLoginBukkit plugin;
    private final PacketEvent packetEvent;

    private final ClientPublicKey clientKey;
    private final PublicKey serverKey;

    private final Random random;

    private final Player player;
    private final String username;

    public NameCheckTask(FastLoginBukkit plugin, Random random, Player player, PacketEvent packetEvent,
                         String username, ClientPublicKey clientKey, PublicKey serverKey) {
        super(plugin.getCore(), plugin.getCore().getAuthPluginHook(), plugin.getBedrockService());

        this.plugin = plugin;
        this.packetEvent = packetEvent;
        this.clientKey = clientKey;
        this.serverKey = serverKey;
        this.random = random;
        this.player = player;
        this.username = username;
    }

    @Override
    public void run() {
        ProtocolLibLoginSource source = new ProtocolLibLoginSource(plugin, player, random, serverKey, clientKey);
        try {
            super.onLogin(username, source);
        } finally {
            if (source.isKicked()) {
                // source.kick() was called inside onLogin — the DISCONNECT packet
                // has already been sent. Cancel the START packet so the vanilla
                // server doesn't process it and overwrite our kick message with
                // "Failed to log in: Invalid session".
                synchronized (packetEvent.getAsyncMarker().getProcessingLock()) {
                    packetEvent.setCancelled(true);
                }
            } else {
                ProtocolLibrary.getProtocolManager().getAsynchronousManager()
                    .signalPacketTransmission(packetEvent);
            }
        }
    }

    @Override
    public FastLoginPreLoginEvent callFastLoginPreLoginEvent(String username, ProtocolLibLoginSource source,
                                                             StoredProfile profile) {
        BukkitFastLoginPreLoginEvent event = new BukkitFastLoginPreLoginEvent(username, source, profile);
        plugin.getServer().getPluginManager().callEvent(event);
        return event;
    }

    //Minecraft server implementation
    //https://github.com/bergerkiller/CraftSource/blob/master/net.minecraft.server/LoginListener.java#L161
    @Override
    public void requestPremiumLogin(ProtocolLibLoginSource source, StoredProfile profile,
                                    String username, boolean registered) {
        try {
            source.enableOnlinemode();
        } catch (Exception ex) {
            plugin.getLog().error("Cannot send encryption packet. Falling back to cracked login for: {}", profile, ex);
            return;
        }

        plugin.getCore().markPendingVerification(username);

        byte[] verify = source.getVerifyToken();
        ClientPublicKey clientKey = source.getClientKey();

        BukkitLoginSession playerSession = new BukkitLoginSession(username, verify, clientKey, registered, profile);
        plugin.putSession(player.getAddress(), playerSession);
        //cancel only if the player has a paid account otherwise login as normal offline player
        synchronized (packetEvent.getAsyncMarker().getProcessingLock()) {
            packetEvent.setCancelled(true);
        }
    }

    @Override
    public void startCrackedSession(ProtocolLibLoginSource source, StoredProfile profile, String username) {
        // Belt-and-suspenders: clear any stale AuthMe premium record left over
        // from a failed /cracked cleanup. Must run BEFORE the session is stored
        // so AuthMe's preJoin dialog (configuration phase) sees a clean state.
        com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator integrator =
            plugin.getAuthMePremiumIntegrator();
        if (integrator != null) {
            integrator.ensureNotPremium(username, profile.isExistingPlayer());
        }

        BukkitLoginSession loginSession = new BukkitLoginSession(username, profile);
        plugin.putSession(player.getAddress(), loginSession);
    }
}
