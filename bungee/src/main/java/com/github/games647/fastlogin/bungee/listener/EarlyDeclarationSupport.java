/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.listener;

import com.github.games647.fastlogin.core.shared.ForwardingAttributes;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/** Best-effort early carrier; never changes admission or disables the late declaration. */
final class EarlyDeclarationSupport {
    static final String CONSEQUENCE = "Unverified connections onto premium-owned accounts can only be rejected "
            + "after joining; Paper + AuthMe 6 blocking pre-join dialogs may leave players waiting until timeout. "
            + "Late declarations remain enabled; proxy admission is unchanged.";

    private final Logger logger;
    private final AtomicBoolean failedOnce = new AtomicBoolean();

    EarlyDeclarationSupport(Class<?> handlerClass, Logger logger) {
        this.logger = logger;
        LoginProfileProperties.probeOfflineCarrier(handlerClass, failure -> logger.warn(
                "This proxy version cannot carry early unverified declarations. " + CONSEQUENCE, failure));
    }

    /**
     * Attempts the early property even after a negative startup probe (custom proxy shapes may work).
     *
     * @param connection the actual proxy login connection
     * @param claim the unverified connection declaration
     * @return whether the property was attached
     */
    boolean attach(Object connection, IdentityClaim claim) {
        try {
            if (!LoginProfileProperties.ensureOfflineProfile(connection, claim.getPlayerName(),
                    claim.getForwardedUuid(), this::reportFailure)) {
                return false;
            }
            Object profile = connection.getClass().getMethod("getLoginProfile").invoke(connection);
            if (profile == null) {
                throw new IllegalStateException("Login profile getter returned null");
            }
            return LoginProfileProperties.attach(profile, ForwardingAttributes.IDENTITY,
                    claim.encode(), this::reportFailure);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            reportFailure(failure);
            return false;
        }
    }

    private void reportFailure(Throwable failure) {
        if (failedOnce.compareAndSet(false, true)) {
            logger.warn("First early unverified declaration injection failure. " + CONSEQUENCE, failure);
        }
    }
}
