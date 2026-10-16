/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.listener;

import com.github.games647.fastlogin.protocol.IdentityClaim;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EarlyDeclarationSupportTest {
    @Test
    void unavailableCapabilityWarnsAtStartupAndOnlyAtFirstActualFailure() {
        Logger log = mock(Logger.class);
        EarlyDeclarationSupport support = new EarlyDeclarationSupport(Object.class, log);
        verify(log).warn(startsWith("This proxy version cannot carry early unverified declarations."),
                any(Throwable.class));
        assertFalse(support.attach(new Object(), offlineClaim()));
        assertFalse(support.attach(new Object(), offlineClaim()));
        verify(log).warn(startsWith("First early unverified declaration injection failure."), any(Throwable.class));
        ArgumentCaptor<String> warnings = ArgumentCaptor.forClass(String.class);
        verify(log, times(2)).warn(warnings.capture(), any(Throwable.class));
        for (String warning : warnings.getAllValues()) {
            assertTrue(warning.contains("after joining"));
            assertTrue(warning.contains("Paper + AuthMe 6"));
            assertTrue(warning.contains("timeout"));
            assertTrue(warning.contains("Late declarations remain enabled"));
        }
    }

    @Test
    void supportedShapeNeedsNoWarningAndCarriesTheCompleteClaim() {
        Logger log = mock(Logger.class);
        EarlyDeclarationSupport support = new EarlyDeclarationSupport(Connection.class, log);
        Connection connection = new Connection();
        IdentityClaim claim = offlineClaim();
        assertTrue(support.attach(connection, claim));
        assertEquals(claim.encode(), connection.loginProfile.properties[0].value);
        assertEquals("flp_identity", connection.loginProfile.properties[0].name);
        assertTrue(support.attach(connection, claim));
        assertEquals(1, connection.loginProfile.properties.length);
        verifyNoInteractions(log);
    }

    @Test
    void successfulProbeDoesNotHideAnActualGetterFailure() {
        Logger log = mock(Logger.class);
        EarlyDeclarationSupport support = new EarlyDeclarationSupport(BrokenConnection.class, log);
        verifyNoInteractions(log);
        assertFalse(support.attach(new BrokenConnection(), offlineClaim()));
        assertFalse(support.attach(new BrokenConnection(), offlineClaim()));
        verify(log, times(1)).warn(startsWith("First early unverified"), any(Throwable.class));
        verifyNoMoreInteractions(log);
    }

    private static IdentityClaim offlineClaim() {
        return new IdentityClaim(IdentityClaim.Decision.OFFLINE, "TestUser", UUID.randomUUID(), null,
                new IdentityClaim.Facts(false, true, IdentityClaim.Consent.NO, false, false, false),
                UUID.randomUUID());
    }

    public static class Connection {
        private Profile loginProfile;
        public Profile getLoginProfile() { return loginProfile; }
    }

    public static final class BrokenConnection extends Connection {
        @Override
        public Profile getLoginProfile() { throw new IllegalStateException("injected getter failure"); }
    }

    public static final class Profile {
        private Property[] properties;
        public Profile(String id, String name, Property[] properties) { this.properties = properties; }
    }

    public static final class Property {
        private final String name;
        private final String value;
        public Property(String name, String value) { this.name = name; this.value = value; }
        public String getName() { return name; }
    }
}
