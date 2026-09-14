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
package com.github.games647.fastlogin.core.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PendingRelayStoreTest {

    @TempDir
    Path tempDir;

    private Logger logger() {
        return mock(Logger.class);
    }

    @Test
    void toggleQueuePersistsAcrossRestart() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());
        assertFalse(store.hasPending());

        store.queueToggle("Steve", true);
        store.queueToggle("Alex", false);
        assertTrue(Files.exists(tempDir.resolve("pending-relay.json")));

        // simulate a backend restart: new store instance, same folder
        PendingRelayStore restored = new PendingRelayStore(tempDir, logger());
        assertTrue(restored.load());
        assertEquals(Boolean.TRUE, restored.getToggle("Steve"));
        assertEquals(Boolean.FALSE, restored.getToggle("Alex"));
        assertTrue(restored.hasPending());
    }

    @Test
    void deleteQueuePersistsAcrossRestart() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());
        store.queueDelete("Notch");
        store.queueDelete("Herobrine");

        PendingRelayStore restored = new PendingRelayStore(tempDir, logger());
        assertTrue(restored.load());
        assertTrue(restored.containsDelete("Notch"));
        assertTrue(restored.containsDelete("Herobrine"));
        assertFalse(restored.containsDelete("Dinnerbone"));
    }

    @Test
    void clearToggleRemovesAndPersists() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());
        store.queueToggle("Steve", true);

        assertTrue(store.clearToggle("Steve"));
        assertFalse(store.hasPending());

        PendingRelayStore restored = new PendingRelayStore(tempDir, logger());
        assertFalse(restored.load());
        assertNull(restored.getToggle("Steve"));
    }

    @Test
    void clearMissingEntryReturnsFalseAndKeepsFile() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());
        store.queueToggle("Steve", true);

        assertFalse(store.clearToggle("SomeoneElse"));

        PendingRelayStore restored = new PendingRelayStore(tempDir, logger());
        assertTrue(restored.load());
        assertEquals(Boolean.TRUE, restored.getToggle("Steve"));
    }

    @Test
    void clearDeleteReturnsFalseForMissingEntry() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());
        store.queueDelete("Notch");

        assertFalse(store.clearDelete("Herobrine"));
        assertTrue(store.clearDelete("Notch"));
        assertFalse(store.hasPending());
    }

    @Test
    void corruptFileMovedAsideAndLoadsEmpty() throws Exception {
        Files.write(tempDir.resolve("pending-relay.json"), "{not valid json".getBytes("UTF-8"));

        PendingRelayStore store = new PendingRelayStore(tempDir, logger());
        assertFalse(store.load());
        assertFalse(store.hasPending());
        assertTrue(Files.exists(tempDir.resolve("pending-relay.json.corrupt")));
    }

    @Test
    void clearAllRemovesEverything() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());
        store.queueToggle("Steve", true);
        store.queueDelete("Notch");

        store.clearAll();
        assertFalse(store.hasPending());

        PendingRelayStore restored = new PendingRelayStore(tempDir, logger());
        assertFalse(restored.load());
    }

    @Test
    void removeToggleReturnsCurrentValueAndClearsEntry() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());
        store.queueToggle("Steve", true);
        // a newer command overwrote the value while the retry task was pending
        store.queueToggle("Steve", false);

        assertEquals(Boolean.FALSE, store.removeToggle("Steve"));
        assertFalse(store.hasPending());
        assertNull(store.removeToggle("Steve"));

        PendingRelayStore restored = new PendingRelayStore(tempDir, logger());
        assertFalse(restored.load());
    }

    @Test
    void queueToggleReturnsTrueOnlyForNewEntries() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());

        assertTrue(store.queueToggle("Steve", true));
        // same value again — a retry task already exists for the entry
        assertFalse(store.queueToggle("Steve", true));
        // value change — still covered by the existing retry task (it reads
        // the current value at send time), so no new task is needed
        assertFalse(store.queueToggle("Steve", false));

        assertEquals(Boolean.FALSE, store.getToggle("Steve"));
    }

    @Test
    void queueDeleteReturnsTrueOnlyForNewEntries() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());

        assertTrue(store.queueDelete("Notch"));
        assertFalse(store.queueDelete("Notch"));
        assertTrue(store.containsDelete("Notch"));
    }

    @Test
    void premiumNoticeQueuePersistsAcrossRestart() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());
        store.queuePremiumNotice("Steve", false);
        store.queuePremiumNotice("Alex", true);

        PendingRelayStore restored = new PendingRelayStore(tempDir, logger());
        assertTrue(restored.load());
        assertEquals(Boolean.FALSE, restored.getPremiumNotice("Steve"));
        assertEquals(Boolean.TRUE, restored.getPremiumNotice("Alex"));
        assertTrue(restored.hasPending());
    }

    @Test
    void queuePremiumNoticeReturnsTrueOnlyForNewEntries() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());

        assertTrue(store.queuePremiumNotice("Steve", false));
        // same state again — a retry task already exists for the entry
        assertFalse(store.queuePremiumNotice("Steve", false));
        // state change — still covered by the existing retry task (it reads the
        // current value at send time), so no new task is needed
        assertFalse(store.queuePremiumNotice("Steve", true));

        assertEquals(Boolean.TRUE, store.getPremiumNotice("Steve"));
    }

    @Test
    void removePremiumNoticeReturnsCurrentValueAndClearsEntry() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());
        store.queuePremiumNotice("Steve", false);
        // a newer toggle overwrote the state while the retry task was pending
        store.queuePremiumNotice("Steve", true);

        assertEquals(Boolean.TRUE, store.removePremiumNotice("Steve"));
        assertFalse(store.hasPending());
        assertNull(store.removePremiumNotice("Steve"));

        PendingRelayStore restored = new PendingRelayStore(tempDir, logger());
        assertFalse(restored.load());
    }

    @Test
    void premiumNoticeAndToggleShareNeitherSlotNorLifetime() {
        // The two messages go to different caches on the proxy via different channels,
        // so clearing one must never drop the other.
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());
        store.queueToggle("Steve", false);
        store.queuePremiumNotice("Steve", false);

        assertTrue(store.clearPremiumNotice("Steve"));
        assertEquals(Boolean.FALSE, store.getToggle("Steve"));
        assertTrue(store.hasPending());

        assertTrue(store.clearToggle("Steve"));
        assertFalse(store.hasPending());
    }

    @Test
    void clearAllRemovesPremiumNoticesToo() {
        PendingRelayStore store = new PendingRelayStore(tempDir, logger());
        store.queuePremiumNotice("Steve", false);

        store.clearAll();
        assertFalse(store.hasPending());

        PendingRelayStore restored = new PendingRelayStore(tempDir, logger());
        assertFalse(restored.load());
    }

    @Test
    void loadKeepsTogglesFromAFileWrittenBeforePremiumNoticesExisted() throws Exception {
        // Downgrade/rollback shape: an older build wrote the file without the
        // premiumNotices field. Reading it must not lose the entries it does have.
        Files.write(tempDir.resolve("pending-relay.json"),
            "{\"toggles\":{\"Steve\":true},\"deletes\":[\"Notch\"]}".getBytes("UTF-8"));

        PendingRelayStore restored = new PendingRelayStore(tempDir, logger());
        assertTrue(restored.load());
        assertEquals(Boolean.TRUE, restored.getToggle("Steve"));
        assertTrue(restored.containsDelete("Notch"));
        assertTrue(restored.premiumNotices().isEmpty());
    }
}
