/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 Hayston1001
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
package com.github.hayston1001.fastloginplus.fabric;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;

/** Matches one Mojang-verified transfer to the client that requested binding. */
public final class BindingCookieProof {

    private static final Logger LOGGER = LoggerFactory.getLogger("fastloginplus_fabric");
    static final Identifier KEY = Identifier.fromNamespaceAndPath("fastloginplus", "bind");
    private static final ConcurrentHashMap<Connection, StoreRequest> STORE_REQUESTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Connection, String> REQUESTED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Connection, Response> RESPONSES = new ConcurrentHashMap<>();

    private BindingCookieProof() {
    }

    public interface ConnectionAccess {
        Connection bindingConnection();
    }

    static void request(ServerConfigurationPacketListenerImpl listener, String name) {
        Connection connection = ((ConnectionAccess) listener).bindingConnection();
        REQUESTED.put(connection, name);
        listener.send(new ClientboundCookieRequestPacket(KEY));
        LOGGER.info("Requested binding cookie during verified reconnect for {}", name);
    }

    static StoreRequest confirmStoredCookie(ServerPlayer player, String name, byte[] expected,
                                            Runnable onVerified, Runnable onFailure) {
        Connection connection = ((ConnectionAccess) player.connection).bindingConnection();
        StoreRequest request = registerStoreRequest(connection, name, expected, onVerified, onFailure);
        player.connection.send(new ClientboundCookieRequestPacket(KEY));
        return request;
    }

    static StoreRequest registerStoreRequest(Connection connection, String name, byte[] expected,
                                             Runnable onVerified, Runnable onFailure) {
        StoreRequest request = new StoreRequest(name, expected.clone(), onVerified, onFailure);
        STORE_REQUESTS.put(connection, request);
        return request;
    }

    static void expireStoreRequest(ServerPlayer player, StoreRequest request) {
        Connection connection = ((ConnectionAccess) player.connection).bindingConnection();
        expireStoreRequest(connection, request);
    }

    static void expireStoreRequest(Connection connection, StoreRequest request) {
        if (STORE_REQUESTS.remove(connection, request)) {
            request.onFailure.run();
        }
    }

    /**
     * Consumes only the response to FLP's own outstanding cookie request.
     *
     * @param connection player connection
     * @param packet received cookie response
     * @return true if vanilla must not reject this expected response
     */
    public static boolean accept(Connection connection, ServerboundCookieResponsePacket packet) {
        if (!KEY.equals(packet.key())) {
            return false;
        }
        StoreRequest store = STORE_REQUESTS.remove(connection);
        if (store != null) {
            boolean valid = packet.payload() != null && MessageDigest.isEqual(store.expected, packet.payload());
            LOGGER.info("Client stored binding cookie for {}: {}", store.name, valid);
            if (valid) {
                store.onVerified.run();
            } else {
                store.onFailure.run();
            }
            return true;
        }
        String name = REQUESTED.remove(connection);
        if (name == null) {
            // A response can arrive after our five-second confirmation deadline.
            // It carries no authority without an outstanding request, but vanilla
            // would disconnect the player for an unexpected cookie response.
            return true;
        }
        byte[] payload = packet.payload();
        RESPONSES.put(connection, new Response(name, payload == null ? null : payload.clone()));
        LOGGER.info("Received binding cookie on verified reconnect for {}: present={}", name, payload != null);
        return true;
    }

    static boolean consume(Object listener, String name, byte[] expected) {
        Connection connection = ((ConnectionAccess) listener).bindingConnection();
        REQUESTED.remove(connection);
        Response response = RESPONSES.remove(connection);
        boolean valid = response != null && name.equals(response.name) && response.payload != null
                && MessageDigest.isEqual(expected, response.payload);
        LOGGER.info("Binding cookie matched for {}: {}", name, valid);
        return valid;
    }

    static void drop(Object listener) {
        Connection connection = ((ConnectionAccess) listener).bindingConnection();
        StoreRequest store = STORE_REQUESTS.remove(connection);
        if (store != null) {
            store.onFailure.run();
        }
        REQUESTED.remove(connection);
        RESPONSES.remove(connection);
    }

    record StoreRequest(String name, byte[] expected, Runnable onVerified, Runnable onFailure) {
    }

    private record Response(String name, byte[] payload) {
    }
}
