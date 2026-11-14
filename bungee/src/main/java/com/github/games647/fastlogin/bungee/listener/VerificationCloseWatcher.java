/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.listener;

import com.github.games647.fastlogin.bungee.FastLoginBungee;
import net.md_5.bungee.api.connection.PendingConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Settles a login-phase attempt as soon as its connection closes, using the
 * proxy's own login state as the phase marker.
 *
 * <p>{@code FINISHING} means the encrypted response was accepted and Mojang
 * verification was running when the connection closed: the proxy turned a
 * rejection into this close, so the attempt counts as a failed verification.
 * {@code ENCRYPT} and every earlier state mean the client never answered; like
 * an unknown state, such an attempt is left to the core's grace-period sweep.</p>
 *
 * <p>The login handler is a proxy implementation class, reached reflectively
 * like the existing {@code InitialHandler} lookups. When a version moves its
 * fields, attaching silently stops and the grace-period settlement remains the
 * fallback.</p>
 */
public final class VerificationCloseWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(VerificationCloseWatcher.class);
    private static final String FINISHING = "FINISHING";

    private VerificationCloseWatcher() {
    }

    /**
     * Attaches the close listener for one attempt.
     *
     * @param plugin     the plugin instance
     * @param username   exact login name of the attempt
     * @param connection the connection used as the attempt's owner
     */
    public static void attach(FastLoginBungee plugin, String username, PendingConnection connection) {
        try {
            Field channelWrapperField = connection.getClass().getDeclaredField("ch");
            channelWrapperField.setAccessible(true);
            Object channelWrapper = channelWrapperField.get(connection);
            if (channelWrapper == null) {
                return;
            }
            Method getHandle = channelWrapper.getClass().getMethod("getHandle");
            Object channel = getHandle.invoke(channelWrapper);
            if (channel == null) {
                return;
            }
            listenForClose(channel, () -> settle(plugin, username, connection));
        } catch (ReflectiveOperationException | InaccessibleObjectException | LinkageError ex) {
            // the proxy moved its internals: the grace-period settlement still applies
            LOG.debug("Cannot classify the login phase of {}", username, ex);
        }
    }

    /**
     * Registers a Netty close listener without linking against Netty: the plugin
     * module only compiles against the published proxy API, so the listener
     * interface is created reflectively.
     *
     * @param channel the Netty channel as a runtime object
     * @param onClose what to run once the channel is closed
     * @throws ReflectiveOperationException when the proxy's channel is not a Netty one
     */
    private static void listenForClose(Object channel, Runnable onClose)
            throws ReflectiveOperationException {
        Object closeFuture = channel.getClass().getMethod("closeFuture").invoke(channel);
        Class<?> listenerType = Class.forName("io.netty.util.concurrent.GenericFutureListener");
        Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(),
                new Class<?>[]{listenerType}, (proxy, method, args) -> {
                    if ("operationComplete".equals(method.getName())) {
                        onClose.run();
                        return null;
                    }
                    switch (method.getName()) {
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "FastLoginPlus verification close listener";
                        default:
                            return null;
                    }
                });
        closeFuture.getClass().getMethod("addListener", listenerType).invoke(closeFuture, listener);
    }

    private static void settle(FastLoginBungee plugin, String username, PendingConnection connection) {
        try {
            Field stateField = connection.getClass().getDeclaredField("thisState");
            stateField.setAccessible(true);
            Object state = stateField.get(connection);
            String stateName = state instanceof Enum<?> ? ((Enum<?>) state).name() : null;
            switch (classify(stateName)) {
                case AUTH_FAILED:
                    plugin.getCore().settleVerificationAsFailure(username, connection);
                    break;
                case NOT_ANSWERED:
                default:
                    // the client never answered or the phase is unknown: the
                    // core's grace-period sweep decides, as before
                    break;
            }
        } catch (ReflectiveOperationException | InaccessibleObjectException | LinkageError ex) {
            LOG.debug("Cannot classify the closed login of {}", username, ex);
        }
    }

    /**
     * Maps a login state name to the phase it represents.
     *
     * @param stateName name of the proxy's login state, may be null
     * @return the classification
     */
    static Outcome classify(String stateName) {
        if (FINISHING.equals(stateName)) {
            return Outcome.AUTH_FAILED;
        }
        if (stateName == null) {
            return Outcome.UNKNOWN;
        }
        switch (stateName) {
            case "HANDSHAKE":
            case "STATUS":
            case "PING":
            case "USERNAME":
            case "ENCRYPT":
                return Outcome.NOT_ANSWERED;
            default:
                return Outcome.UNKNOWN;
        }
    }

    enum Outcome {
        NOT_ANSWERED,
        AUTH_FAILED,
        UNKNOWN
    }
}
