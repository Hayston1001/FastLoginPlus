/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity;

import org.slf4j.Logger;

import java.util.function.BooleanSupplier;

/** Presence is observable; AuthMeVelocity's premium verification and premium list are not controlled here. */
final class AuthMeProxyDiagnostics {
    void warnAtStartup(BooleanSupplier probe, Logger logger) {
        if (probe.getAsBoolean()) {
            logger.warn("Detected AuthMeVelocity. FastLoginPlus disables its proxy auto-login and the "
                    + "backend proxy receiver, but not its premium verification or premium list. Check "
                    + "those AuthMeVelocity settings if passwordless login still occurs.");
        }
    }
}
