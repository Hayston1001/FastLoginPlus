/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.util.GameProfile;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/** Checks the public early-carrier API and reports degradation without changing admission. */
final class EarlyDeclarationSupport {
    enum FailureKind {
        PREMIUM_CLAIM,
        UNVERIFIED_DECLARATION
    }

    private static final String UNVERIFIED_CONSEQUENCE =
            "Unverified connections onto premium-owned accounts can only be rejected "
            + "after joining; Paper + AuthMe 6 blocking pre-join dialogs may leave players waiting until timeout. "
            + "Late declarations remain enabled; proxy admission is unchanged.";
    private static final String PREMIUM_CONSEQUENCE = "Early premium claim could not be attached; "
            + "AuthMe 6 pre-join dialogs may block verified players until a late message can complete login. "
            + "The late declaration and proxy admission are unchanged.";
    private static final String PROBE_CONSEQUENCE = "Early premium login preparation and early rejection of "
            + "unverified connections onto premium-owned accounts may be unavailable. "
            + "Paper + AuthMe 6 blocking pre-join dialogs may leave players waiting until timeout. "
            + "Late declarations remain enabled; proxy admission is unchanged.";

    private final Logger logger;
    private final AtomicBoolean premiumFailedOnce = new AtomicBoolean();
    private final AtomicBoolean unverifiedFailedOnce = new AtomicBoolean();

    EarlyDeclarationSupport(Logger logger) {
        this(GameProfileRequestEvent.class, GameProfile.class, GameProfile.Property.class, logger);
    }

    EarlyDeclarationSupport(Class<?> eventType, Class<?> profileType, Class<?> propertyType, Logger logger) {
        this.logger = logger;
        try {
            eventType.getMethod("getGameProfile");
            eventType.getMethod("setGameProfile", profileType);
            profileType.getMethod("getId");
            profileType.getMethod("getProperties");
            profileType.getMethod("withProperties", java.util.List.class);
            profileType.getMethod("addProperty", propertyType);
            propertyType.getConstructor(String.class, String.class, String.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            logger.warn("This proxy version cannot carry early identity declarations. " + PROBE_CONSEQUENCE, failure);
        }
    }

    /**
     * Attempts early delivery; the separate connected-event sender is never disabled.
     *
     * @param kind the attempted declaration category, used only for diagnostics
     * @param injection the public-API profile update
     */
    void attempt(FailureKind kind, Runnable injection) {
        try {
            injection.run();
        } catch (RuntimeException | LinkageError failure) {
            boolean premium = kind == FailureKind.PREMIUM_CLAIM;
            AtomicBoolean failedOnce = premium ? premiumFailedOnce : unverifiedFailedOnce;
            if (failedOnce.compareAndSet(false, true)) {
                logger.warn(premium ? "First early premium claim injection failure. " + PREMIUM_CONSEQUENCE
                        : "First early unverified declaration injection failure. " + UNVERIFIED_CONSEQUENCE, failure);
            }
        }
    }
}
