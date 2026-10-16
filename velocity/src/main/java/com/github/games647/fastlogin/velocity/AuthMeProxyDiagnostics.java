/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity;

import org.slf4j.Logger;

import java.util.function.BooleanSupplier;

/** Presence is observable; AuthMeVelocity's independent authentication policy is not controlled here. */
final class AuthMeProxyDiagnostics {
    void warnAtStartup(BooleanSupplier probe, Logger logger) {
        if (probe.getAsBoolean()) {
            logger.warn("Detected AuthMeVelocity, which may run its own premium verification and premium list. "
                    + "FLP cannot disable that proxy-side behavior. To disable passwordless login, configure "
                    + "AuthMeVelocity as well; FLP autoLogin=false only controls FLP and the backend AuthMe mode.");
        }
    }
}
