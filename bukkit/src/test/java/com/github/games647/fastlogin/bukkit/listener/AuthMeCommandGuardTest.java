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
package com.github.games647.fastlogin.bukkit.listener;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the AuthMe premium command guard (ISS-12).
 *
 * <p>Two properties decide whether the guard helps or hurts. It must catch every spelling the
 * server accepts — including the {@code authme:} namespace form CraftBukkit registers for every
 * plugin command — while leaving another plugin's {@code /other:premium} and a player who could
 * not run AuthMe's command at all untouched. And it must stay inert unless FLP really owns
 * premium handling: on AuthMe 5.x or after a failed takeover, AuthMe's command is still the
 * working one.
 *
 * <p><b>Coverage boundary.</b> What is pinned here is the decision
 * ({@link AuthMeCommandGuard#blockedLabel}) and the label-to-hint mapping. The three statements
 * around it — cancelling the event, sending the hint, logging the interception — live in
 * {@code onCommandPreprocess}, which needs a live {@code FastLoginBukkit} to construct, and are
 * ensured by reading that method. The hint keys themselves are pinned against the bundled
 * language files, because a key that does not resolve is exactly as good as no guard at all.
 */
class AuthMeCommandGuardTest {

    @Test
    void recognisesBothAuthMeCommandsInEveryAcceptedSpelling() {
        assertEquals("premium", AuthMeCommandGuard.normalizeLabel("/premium"));
        assertEquals("freemium", AuthMeCommandGuard.normalizeLabel("/freemium"));
        assertEquals("premium", AuthMeCommandGuard.normalizeLabel("/PREMIUM"));
        assertEquals("premium", AuthMeCommandGuard.normalizeLabel("/Premium"));
        assertEquals("premium", AuthMeCommandGuard.normalizeLabel("/authme:premium"));
        assertEquals("freemium", AuthMeCommandGuard.normalizeLabel("/AUTHME:FREEMIUM"));
        assertEquals("premium", AuthMeCommandGuard.normalizeLabel("/authme:premium extra"));
        assertEquals("premium", AuthMeCommandGuard.normalizeLabel("/premium  \t"));
    }

    @Test
    void ignoresEveryOtherLabel() {
        assertNull(AuthMeCommandGuard.normalizeLabel("/other:premium"));
        assertNull(AuthMeCommandGuard.normalizeLabel("/authme.sub:premium"));
        assertNull(AuthMeCommandGuard.normalizeLabel("/authme:premiums"));
        assertNull(AuthMeCommandGuard.normalizeLabel("/authme:premium:extra"));
        assertNull(AuthMeCommandGuard.normalizeLabel("/premiums"));
        assertNull(AuthMeCommandGuard.normalizeLabel("/authme"));
        assertNull(AuthMeCommandGuard.normalizeLabel("premium"));
        assertNull(AuthMeCommandGuard.normalizeLabel(" //premium"));
        assertNull(AuthMeCommandGuard.normalizeLabel("/"));
        assertNull(AuthMeCommandGuard.normalizeLabel(""));
    }

    @Test
    void staysInertWithoutTheTakeover() {
        List<String> consulted = new ArrayList<>();
        assertNull(AuthMeCommandGuard.blockedLabel(false, "/premium", node -> {
            consulted.add(node);
            return true;
        }));
        // AuthMe's command is its own on AuthMe 5.x / after a failed takeover — not even the
        // permission is looked at
        assertTrue(consulted.isEmpty());
    }

    @Test
    void blocksPremiumForAPlayerWhoCouldRunIt() {
        assertEquals("premium", AuthMeCommandGuard.blockedLabel(true, "/premium",
                "authme.player.premium"::equals));
    }

    @Test
    void blocksFreemiumForAPlayerWhoCouldRunIt() {
        assertEquals("freemium", AuthMeCommandGuard.blockedLabel(true, "/freemium",
                "authme.player.freemium"::equals));
    }

    @Test
    void blocksTheNamespacedForm() {
        assertEquals("premium", AuthMeCommandGuard.blockedLabel(true, "/authme:premium",
                "authme.player.premium"::equals));
    }

    @Test
    void leavesAPlayerWithoutPermissionToAuthMesOwnMessage() {
        // AuthMe's own NO_PERMISSION message is the answer here; the guard adds nothing
        assertNull(AuthMeCommandGuard.blockedLabel(true, "/freemium", node -> false));
    }

    @Test
    void checksTheNodeThatMatchesTheCommand() {
        List<String> premiumChecks = new ArrayList<>();
        assertEquals("premium", AuthMeCommandGuard.blockedLabel(true, "/authme:premium", node -> {
            premiumChecks.add(node);
            return true;
        }));
        assertEquals(Collections.singletonList("authme.player.premium"), premiumChecks);

        List<String> freemiumChecks = new ArrayList<>();
        assertEquals("freemium", AuthMeCommandGuard.blockedLabel(true, "/freemium", node -> {
            freemiumChecks.add(node);
            return true;
        }));
        assertEquals(Collections.singletonList("authme.player.freemium"), freemiumChecks);
    }

    @Test
    void ignoresAnotherPluginsPremiumCommand() {
        List<String> consulted = new ArrayList<>();
        assertNull(AuthMeCommandGuard.blockedLabel(true, "/other:premium", node -> {
            consulted.add(node);
            return true;
        }));
        assertTrue(consulted.isEmpty());
    }

    @Test
    void mapsEachCommandToItsOwnHint() {
        assertEquals("authme-premium-blocked", AuthMeCommandGuard.blockedMessageKey("premium"));
        assertEquals("authme-freemium-blocked", AuthMeCommandGuard.blockedMessageKey("freemium"));
    }

    @Test
    void hintKeysResolveFromTheBundledLanguageFiles() throws IOException {
        for (String language : new String[]{"messages_en.yml", "messages_zh.yml"}) {
            Configuration messages = load(language);
            for (String label : new String[]{"premium", "freemium"}) {
                String key = AuthMeCommandGuard.blockedMessageKey(label);
                // a dotted key would be a false pass here: both configuration backends split
                // the path on '.' and never find a flat key that contains one
                assertNotNull(messages.get(key), language + " does not resolve " + key);
            }
        }
    }

    private static Configuration load(String resource) throws IOException {
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        try (InputStream stream = AuthMeCommandGuardTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " is not on the test classpath");
            return provider.load(stream);
        }
    }
}
