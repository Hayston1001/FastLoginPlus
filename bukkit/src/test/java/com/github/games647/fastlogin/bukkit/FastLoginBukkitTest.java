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
package com.github.games647.fastlogin.bukkit;

import com.github.games647.fastlogin.core.CommonUtil;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastLoginBukkitTest {

    /**
     * 0.7.0/F7 (ISS-31). The warning exists for one situation only: a direct connection whose
     * AuthMe verification is off <em>because</em> PacketEvents is missing. Proxy backends were
     * added as an explicit exclusion on 2026-09-15 — there AuthMe's listener never registers,
     * upstream prints no misleading warning, and the command layer is already covered by
     * {@code AuthMeCommandGuard}, so the line was pure noise (plus one sentence about a warning
     * that cannot appear in that setup).
     */
    @Test
    void commandLayerWarningOnlyAppliesToDirectConnectionsWithoutPacketEvents() {
        // the only case it is meant for
        assertTrue(FastLoginBukkit.shouldWarnAboutCommandLayer(true, false, false));

        // proxy backend: nothing surprising happened, nothing upstream to disambiguate
        assertFalse(FastLoginBukkit.shouldWarnAboutCommandLayer(true, false, true));

        // PacketEvents present: AuthMe's own verification is active
        assertFalse(FastLoginBukkit.shouldWarnAboutCommandLayer(true, true, false));
        assertFalse(FastLoginBukkit.shouldWarnAboutCommandLayer(true, true, true));

        // no takeover (AuthMe 5.x, absent, or half-applied): the command layer is AuthMe's own
        assertFalse(FastLoginBukkit.shouldWarnAboutCommandLayer(false, false, false));
    }

    @Test
    void testRGB() {
        String message = "&x00002a00002b&lText";
        String msg = CommonUtil.translateColorCodes(message);
        assertEquals(msg, "§x00002a00002b§lText");

        @SuppressWarnings("deprecation")
        BaseComponent[] components = TextComponent.fromLegacyText(msg);
        String expected = "{\"bold\":true,\"color\":\"#00a00b\",\"text\":\"Text\"}";
        //noinspection deprecation
        assertEquals(ComponentSerializer.toString(components), expected);
    }
}
