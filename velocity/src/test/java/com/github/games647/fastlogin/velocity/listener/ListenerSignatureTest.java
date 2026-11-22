/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Velocity's event manager reflects on every declared method of a listener at registration
 * time, and that reflection resolves each method signature. A Floodgate type in a signature
 * therefore aborts plugin initialisation when Floodgate is not installed, even though the
 * method is private and never called. Floodgate types must stay in {@link FloodgateAccess}.
 */
class ListenerSignatureTest {

    @Test
    void listenersNeverDeclareFloodgateTypes() {
        for (Class<?> listener : new Class<?>[]{
                ConnectListener.class, PluginMessageListener.class, CrackedNoticeDelivery.class}) {
            for (Method method : listener.getDeclaredMethods()) {
                assertNotFloodgate(listener, method.getReturnType().getTypeName());
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertNotFloodgate(listener, parameter.getTypeName());
                }
            }
        }
    }

    private static void assertNotFloodgate(Class<?> listener, String type) {
        assertFalse(type.startsWith("org.geysermc.floodgate"),
                listener.getName() + " declares " + type + " in a method signature");
    }
}
