/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.velocitypowered.api.proxy.InboundConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Settles a login-phase attempt as soon as its connection closes, using Velocity's
 * own session handler as the phase marker.
 *
 * <p>{@code AuthSessionHandler} means the login was accepted and Mojang
 * verification was running when the connection closed: the proxy turned a
 * rejection into this close, so the attempt counts as a failed verification.
 * A rejected attempt is still handled by {@code InitialLoginSessionHandler}:
 * its login state tells whether the client answered encryption
 * ({@code ENCRYPTION_RESPONSE_RECEIVED}) or not. Everything else, including a
 * client that never answered, is left to the core's grace-period sweep.</p>
 *
 * <p>These are proxy implementation classes, reached reflectively like the
 * existing Floodgate lookups. When a version moves them, attaching silently
 * stops and the grace-period settlement remains the fallback.</p>
 */
public final class VerificationCloseWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(VerificationCloseWatcher.class);
    private static final String INITIAL_LOGIN_HANDLER = "InitialLoginSessionHandler";
    private static final String AUTH_HANDLER = "AuthSessionHandler";

    private VerificationCloseWatcher() {
    }

    /**
     * Attaches the close listener for one attempt.
     *
     * @param plugin     the plugin instance
     * @param username   exact login name of the attempt
     * @param connection the connection used as the attempt's owner
     */
    public static void attach(FastLoginVelocity plugin, String username, InboundConnection connection) {
        try {
            Object sessionConnection = resolveSessionConnection(connection);
            Object channel = sessionConnection.getClass().getMethod("getChannel")
                    .invoke(sessionConnection);
            if (channel == null) {
                return;
            }
            listenForClose(channel, () -> settle(plugin, username, connection, sessionConnection));
        } catch (ReflectiveOperationException | InaccessibleObjectException | LinkageError ex) {
            // the proxy moved its internals: the grace-period settlement still applies
            LOG.debug("Cannot classify the login phase of {}", username, ex);
        }
    }

    /**
     * Registers a Netty close listener without linking against Netty: the plugin
     * only sees the proxy's own classes, so the listener interface is created
     * reflectively.
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

    private static Object resolveSessionConnection(InboundConnection connection)
            throws ReflectiveOperationException {
        try {
            Method delegated = connection.getClass().getDeclaredMethod("delegatedConnection");
            delegated.setAccessible(true);
            return delegated.invoke(connection);
        } catch (NoSuchMethodException missing) {
            Field delegate = connection.getClass().getDeclaredField("delegate");
            delegate.setAccessible(true);
            Object initial = delegate.get(connection);
            return initial.getClass().getMethod("getConnection").invoke(initial);
        }
    }

    private static void settle(FastLoginVelocity plugin, String username,
                               InboundConnection connection, Object sessionConnection) {
        try {
            Object handler = sessionConnection.getClass().getMethod("getActiveSessionHandler")
                    .invoke(sessionConnection);
            switch (classifyHandler(handler)) {
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
     * Maps the active session handler to the phase it represents.
     *
     * <p>The proxy installs {@code AuthSessionHandler} only after the login was
     * accepted, so a rejected attempt is still handled by
     * {@code InitialLoginSessionHandler}. Its own login state tells whether the
     * client answered the encryption request before the connection closed.</p>
     *
     * @param handler the active handler instance, may be null
     * @return the classification
     */
    static Outcome classifyHandler(Object handler) {
        String handlerName = handler == null ? null : handler.getClass().getSimpleName();
        if (AUTH_HANDLER.equals(handlerName)) {
            return Outcome.AUTH_FAILED;
        }
        if (INITIAL_LOGIN_HANDLER.equals(handlerName)) {
            return classifyLoginState(readLoginState(handler));
        }
        return Outcome.UNKNOWN;
    }

    /**
     * Reads the login state of {@code InitialLoginSessionHandler} reflectively.
     *
     * @param handler the login handler instance
     * @return the state name, or null when it cannot be read
     */
    private static String readLoginState(Object handler) {
        try {
            Field stateField = handler.getClass().getDeclaredField("currentState");
            stateField.setAccessible(true);
            Object state = stateField.get(handler);
            return state instanceof Enum<?> ? ((Enum<?>) state).name() : null;
        } catch (ReflectiveOperationException | InaccessibleObjectException | LinkageError ex) {
            // a moved field leaves the attempt to the grace-period sweep
            return null;
        }
    }

    /**
     * Maps the proxy's login state to what it says about the encryption answer.
     *
     * @param stateName name of the login state, may be null
     * @return the classification
     */
    static Outcome classifyLoginState(String stateName) {
        if (stateName == null) {
            return Outcome.UNKNOWN;
        }
        if ("ENCRYPTION_RESPONSE_RECEIVED".equals(stateName)) {
            // the client answered; only a failed session check ends the login here
            return Outcome.AUTH_FAILED;
        }
        switch (stateName) {
            case "LOGIN_PACKET_EXPECTED":
            case "LOGIN_PACKET_RECEIVED":
            case "ENCRYPTION_REQUEST_SENT":
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
