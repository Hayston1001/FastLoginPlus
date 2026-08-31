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
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.FieldAccessException;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.Converters;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedProfilePublicKey.WrappedProfileKeyData;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.listener.protocollib.packet.ClientPublicKey;
import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;

import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginAntiBotEvent;
import com.mojang.datafixers.util.Either;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.util.AttributeKey;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.jetbrains.annotations.NotNull;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static com.comphenix.protocol.PacketType.Login.Client.ENCRYPTION_BEGIN;
import static com.comphenix.protocol.PacketType.Login.Client.START;

/**
 * Intercepts the login pipeline packets. The listener is deliberately
 * registered as a ProtocolLib async handler — see
 * PROTOCOLLIB-ASYNC-DESIGN.md at the repository root for the decision,
 * the compensating controls and the residual risk (0.5.0/F003).
 */
public class ProtocolLibListener extends PacketAdapter {

    private final FastLoginBukkit plugin;

    //just create a new once on plugin enable. This used for verify token generation
    private final SecureRandom random = new SecureRandom();
    private final KeyPair keyPair = EncryptionUtil.generateKeyPair();
    private final AntiBotService antiBotService;

    private final boolean verifyClientKeys;

    public ProtocolLibListener(FastLoginBukkit plugin, AntiBotService antiBotService, boolean verifyClientKeys) {
        //run async in order to not block the server, because we are making api calls to Mojang
        super(params()
                .plugin(plugin)
                .types(START, ENCRYPTION_BEGIN)
                .optionAsync());

        this.plugin = plugin;
        this.antiBotService = antiBotService;
        this.verifyClientKeys = verifyClientKeys;
    }

    public static void register(FastLoginBukkit plugin, AntiBotService antiBotService, boolean verifyClientKeys) {
        // they will be created with a static builder, because otherwise it will throw a NoClassDefFoundError
        // TODO: make synchronous processing, but do web or database requests async
        ProtocolLibrary.getProtocolManager()
                .getAsynchronousManager()
                .registerAsyncHandler(new ProtocolLibListener(plugin, antiBotService, verifyClientKeys))
                .start();

        // 0.5.0/F003: ENCRYPTION_BEGIN interception is known to silently fail on
        // some ProtocolLib/Minecraft combinations (e.g. Paper 1.21.11 + ProtocolLib
        // 5.5.0, where ServerboundKeyPacket is not registered). The runtime
        // getOverriddenType() fallback covers *some* of these — warn loudly when
        // even the static mapping is gone so operators can correlate login issues.
        try {
            if (!PacketType.Login.Client.ENCRYPTION_BEGIN.isSupported()) {
                plugin.getLog().warn("ProtocolLib does not statically resolve the encryption"
                        + " response packet on this server version — premium detection may"
                        + " not intercept logins. Verify ProtocolLib compatibility if premium"
                        + " logins fail with 'invalid-request' or missing 'Verifying session'"
                        + " log lines.");
            }
        } catch (Throwable th) {
            // diagnostic only — never break registration over it
            plugin.getLog().debug("Could not check ENCRYPTION_BEGIN packet type support", th);
        }
    }

    @Override
    public void onPacketReceiving(PacketEvent packetEvent) {
        if (packetEvent.isCancelled()
                || plugin.getCore().getAuthPluginHook() == null
                || !plugin.isServerFullyStarted()) {
            return;
        }

        Player sender = packetEvent.getPlayer();
        PacketType packetType = getOverriddenType(packetEvent.getPacketType());

        try {
            if (packetType == START) {
                if (plugin.getCore().isDebug()) {
                    plugin.getLog().info("Login request received from {}", sender.getAddress());
                }
                if (plugin.getFloodgateService() != null) {
                    boolean success = processFloodgateTasks(packetEvent);
                    if (!success) {
                        // don't continue execution if the player was kicked by Floodgate
                        return;
                    }
                }

                PacketContainer packet = packetEvent.getPacket();

                InetSocketAddress address = sender.getAddress();
                String username = getUsername(packet);

                Action action = antiBotService.onIncomingConnection(address, username);
                if (action != Action.Continue) {
                    BukkitFastLoginAntiBotEvent antiBotEvent =
                            new BukkitFastLoginAntiBotEvent(address, username, action);
                    plugin.getServer().getPluginManager().callEvent(antiBotEvent);
                    if (antiBotEvent.isCancelled()) {
                        action = Action.Continue;
                    }
                }

                switch (action) {
                    case Ignore:
                        // just ignore
                        return;
                    case Block:
                        String message = plugin.getCore().getMessage("kick-antibot");
                        sender.kickPlayer(message);
                        break;
                    case Continue:
                    default:
                        //player.getName() won't work at this state
                        onLoginStart(packetEvent, sender, username);
                        break;
                }
            } else if (packetType == ENCRYPTION_BEGIN) {
                onEncryptionBegin(packetEvent, sender);
            } else {
                plugin.getLog().warn("Unknown packet type received {}", packetType);
            }
        } catch (FieldAccessException fieldAccessEx) {
            plugin.getLog().error("Failed to parse packet {}", packetEvent.getPacketType(), fieldAccessEx);
        } catch (Exception unexpectedEx) {
            // 0.5.0/F007: an unexpected reflective failure must not abort the
            // remaining packet handling for this connection
            plugin.getLog().error("Unexpected error processing packet {}",
                    packetEvent.getPacketType(), unexpectedEx);
        }
    }

    private @NotNull PacketType getOverriddenType(PacketType packetType) {
        if (packetType.isDynamic()) {
            String vanillaName = packetType.getPacketClass().getName();
            if (plugin.getCore().isDebug()) {
                plugin.getLog().info("Overriding packet type for unregistered packet type to fix ProtocolLib bug");
            }
            if (vanillaName.endsWith("ServerboundHelloPacket")) {
                return START;
            }

            if (vanillaName.endsWith("ServerboundKeyPacket")) {
                return ENCRYPTION_BEGIN;
            }
        }

        return packetType;
    }

    private void onEncryptionBegin(PacketEvent packetEvent, Player sender) {
        byte[] sharedSecret = packetEvent.getPacket().getByteArrays().read(0);

        BukkitLoginSession session = plugin.getSession(sender.getAddress());
        if (session == null) {
            plugin.getLog().warn("Profile {} tried to send encryption response at invalid state", sender.getAddress());
            sender.kickPlayer(plugin.getCore().getMessage("invalid-request"));
        } else {
            byte[] expectedVerifyToken = session.getVerifyToken();
            if (verifyNonce(sender, packetEvent.getPacket(), session.getClientPublicKey(), expectedVerifyToken)) {
                // 0.5.0/F001: only one verification per session — duplicates are
                // a replay/DoS attempt and get kicked
                if (!session.startVerification()) {
                    plugin.getLog().warn("Duplicate encryption response from {} — ignoring",
                            sender.getAddress());
                    sender.kickPlayer(plugin.getCore().getMessage("invalid-request"));
                    return;
                }

                packetEvent.getAsyncMarker().incrementProcessingDelay();

                Runnable verifyTask = new VerifyResponseTask(
                        plugin, packetEvent, sender, session, sharedSecret, keyPair
                );
                plugin.getScheduler().runAsync(verifyTask);
            } else {
                sender.kickPlayer(plugin.getCore().getMessage("invalid-verify-token"));
            }
        }
    }

    private boolean verifyNonce(Player sender, PacketContainer packet,
                                ClientPublicKey clientPublicKey, byte[] expectedToken) {
        try {
            if (new MinecraftVersion(1, 19, 0).atOrAbove()
                    && !new MinecraftVersion(1, 19, 3).atOrAbove()) {
                Either<byte[], ?> either = packet.getSpecificModifier(Either.class).read(0);
                if (clientPublicKey == null) {
                    Optional<byte[]> left = either.left();
                    if (!left.isPresent()) {
                        plugin.getLog().error("No verify token sent if requested without player signed key {}", sender);
                        return false;
                    }

                    return EncryptionUtil.verifyNonce(expectedToken, keyPair.getPrivate(), left.get());
                } else {
                    Optional<?> optSignatureData = either.right();
                    if (!optSignatureData.isPresent()) {
                        plugin.getLog().error("No signature given to sent player signing key {}", sender);
                        return false;
                    }

                    Object signatureData = optSignatureData.get();
                    long salt = FuzzyReflection.getFieldValue(signatureData, Long.TYPE, true);
                    byte[] signature = FuzzyReflection.getFieldValue(signatureData, byte[].class, true);

                    PublicKey publicKey = clientPublicKey.key();
                    return EncryptionUtil.verifySignedNonce(expectedToken, publicKey, salt, signature);
                }
            } else {
                byte[] nonce = packet.getByteArrays().read(1);
                return EncryptionUtil.verifyNonce(expectedToken, keyPair.getPrivate(), nonce);
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | NoSuchPaddingException
                 | IllegalBlockSizeException | BadPaddingException signatureEx) {
            plugin.getLog().error("Invalid signature from player {}", sender, signatureEx);
            return false;
        }
    }

    private void onLoginStart(PacketEvent packetEvent, Player player, String username) {
        //this includes ip:port. Should be unique for an incoming login request with a timeout of 2 minutes
        String sessionKey = player.getAddress().toString();

        //remove old data every time on a new login in order to keep the session only for one person
        plugin.removeSession(player.getAddress());

        PacketContainer packet = packetEvent.getPacket();
        Optional<ClientPublicKey> clientKey;
        if (new MinecraftVersion(1, 19, 3).atOrAbove()) {
            // public key is sent separate
            clientKey = Optional.empty();
        } else {
            Optional<Optional<WrappedProfileKeyData>> profileKey = packet.getOptionals(
                            BukkitConverters.getWrappedPublicKeyDataConverter()
                    ).optionRead(0);

            clientKey = profileKey.flatMap(Function.identity()).flatMap(data -> {
                Instant expires = data.getExpireTime();
                PublicKey key = data.getKey();
                byte[] signature = data.getSignature();
                return Optional.of(ClientPublicKey.of(expires, key, signature));
            });

            // start reading from index 1, because 0 is already used by the public key
            Optional<UUID> sessionUUID = packet.getOptionals(Converters.passthrough(UUID.class)).readSafely(1);
            if (verifyClientKeys && sessionUUID.isPresent() && clientKey.isPresent()
                    && verifyPublicKey(clientKey.get(), sessionUUID.get())) {
                // missing or incorrect
                // expired always not allowed
                player.kickPlayer(plugin.getCore().getMessage("invalid-public-key"));
                plugin.getLog().warn("Invalid public key from player {}", username);
                return;
            }
        }

        plugin.getLog().trace("GameProfile {} with {} connecting", sessionKey, username);

        packetEvent.getAsyncMarker().incrementProcessingDelay();
        Runnable nameCheckTask = new NameCheckTask(
                plugin, random, player, packetEvent, username, clientKey.orElse(null), keyPair.getPublic()
        );
        plugin.getScheduler().runAsync(nameCheckTask);
    }

    private boolean verifyPublicKey(ClientPublicKey clientKey, UUID sessionPremiumUUID) {
        try {
            return EncryptionUtil.verifyClientKey(clientKey, Instant.now(), sessionPremiumUUID);
        } catch (SignatureException | InvalidKeyException | NoSuchAlgorithmException ex) {
            return false;
        }
    }

    private String getUsername(PacketContainer packet) {
        WrappedGameProfile profile = packet.getGameProfiles().readSafely(0);
        if (profile == null) {
            return packet.getStrings().read(0);
        }

        //player.getName() won't work at this state
        return profile.getName();
    }

    private FloodgatePlayer getFloodgatePlayer(Player player) {
        Channel channel = ProtocolLibCompat.getChannel(plugin.getLog(), plugin.getCore().isDebug(), player);
        if (channel == null) {
            // connection already gone — no Floodgate data to read
            return null;
        }

        AttributeKey<FloodgatePlayer> floodgateAttribute = AttributeKey.valueOf("floodgate-player");
        return channel.attr(floodgateAttribute).get();
    }

    /**
     * Reimplementation of the tasks injected Floodgate in ProtocolLib that are not run due to a bug
     * @see <a href="https://github.com/GeyserMC/Floodgate/issues/143">Issue Floodgate#143</a>
     * @see <a href="https://github.com/GeyserMC/Floodgate/blob/5d5713ed9e9eeab0f4abdaa9cf5cd8619dc1909b/spigot/src/main/java/org/geysermc/floodgate/addon/data/SpigotDataHandler.java#L121-L175">Floodgate/SpigotDataHandler</a>
     * @param packetEvent the PacketEvent that won't be processed by Floodgate
     * @return false if the player was kicked
     */
    private boolean processFloodgateTasks(PacketEvent packetEvent) {
        PacketContainer packet = packetEvent.getPacket();
        Player player = packetEvent.getPlayer();
        FloodgatePlayer floodgatePlayer = getFloodgatePlayer(player);
        if (floodgatePlayer == null) {
            return true;
        }

        // kick the player, if necessary
        Channel channel = ProtocolLibCompat.getChannel(plugin.getLog(),
                plugin.getCore().isDebug(), packetEvent.getPlayer());
        if (channel == null) {
            // connection already gone between the two lookups — nothing left to process
            return true;
        }

        AttributeKey<String> kickMessageAttribute = AttributeKey.valueOf("floodgate-kick-message");
        String kickMessage = channel.attr(kickMessageAttribute).get();
        if (kickMessage != null) {
            player.kickPlayer(kickMessage);
            return false;
        }

        // add prefix
        String username = floodgatePlayer.getCorrectUsername();
        if (packet.getGameProfiles().size() > 0) {
            packet.getGameProfiles().write(0,
                    new WrappedGameProfile(floodgatePlayer.getCorrectUniqueId(), username));
        } else {
            packet.getStrings().write(0, username);
        }

        // remove real Floodgate data handler
        ChannelHandler floodgateHandler = channel.pipeline().get("floodgate_data_handler");
        if (floodgateHandler != null) {
            channel.pipeline().remove(floodgateHandler);
        }

        return true;
    }
}
