/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core;

import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

class CommonUtilTest {

    @Test
    void createJDKLogger() throws Exception {
        CommonUtil.createJDKLogger(Logger.getAnonymousLogger());
    }
}
