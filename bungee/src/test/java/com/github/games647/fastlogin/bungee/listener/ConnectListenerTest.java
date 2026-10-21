/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.listener;

import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.protocol.IdentityClaim;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.conf.Configuration;
import net.md_5.bungee.connection.InitialHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class ConnectListenerTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void onlineAndOfflineLoginAttachExactlyOneClaimAndSignatureWithoutAProxyId() throws Exception {
        UUID proxyId = UUID.randomUUID();
        BungeeCord proxy = mock(BungeeCord.class);
        Configuration config = mock(Configuration.class);
        when(proxy.getConfig()).thenReturn(config);
        when(config.getUuid()).thenReturn(proxyId.toString());
        try (org.mockito.MockedStatic<ProxyServer> server = mockStatic(ProxyServer.class)) {
            server.when(ProxyServer::getInstance).thenReturn(proxy);
            for (boolean online : new boolean[]{true, false}) {
                FastLoginBungee plugin = mock(FastLoginBungee.class);
                FastLoginCore core = mock(FastLoginCore.class);
                when(plugin.getCore()).thenReturn(core);
                when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
                net.md_5.bungee.config.Configuration policy = new net.md_5.bungee.config.Configuration();
                policy.set("premiumUuid", true);
                policy.set("forwardSkin", true);
                when(core.getConfig()).thenReturn(policy);
                InitialHandler connection = mock(InitialHandler.class);
                UUID uuid = online ? UUID.randomUUID() : UUID.nameUUIDFromBytes(
                        "OfflinePlayer:TestUser".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                when(connection.getUniqueId()).thenReturn(uuid);
                when(connection.getName()).thenReturn("TestUser");
                when(connection.isOnlineMode()).thenReturn(online);
                Field profileField = InitialHandler.class.getDeclaredField("loginProfile");
                profileField.setAccessible(true);
                Class<?> type = profileField.getType();
                Field propertiesField = type.getDeclaredField("properties");
                propertiesField.setAccessible(true);
                Object profile = type.getConstructor(String.class, String.class, propertiesField.getType())
                        .newInstance(uuid.toString().replace("-", ""), "TestUser", null);
                profileField.set(connection, profile);
                doReturn(profile).when(connection).getLoginProfile();
                BungeeLoginSession session = mock(BungeeLoginSession.class);
                when(session.getProfile()).thenReturn(mock(StoredProfile.class));
                when(session.getFacts()).thenReturn(IdentityClaim.Facts.defaults());
                when(session.getIdentityDecision()).thenReturn(online
                        ? IdentityClaim.Decision.PREMIUM_VERIFIED : IdentityClaim.Decision.OFFLINE);
                when(plugin.getSession()).thenReturn(
                        new java.util.concurrent.ConcurrentHashMap<>(Collections.singletonMap(connection, session)));
                LoginEvent event = mock(LoginEvent.class);
                when(event.getConnection()).thenReturn(connection);
                ConnectListener listener = new ConnectListener(plugin, null);
                listener.onLogin(event);
                listener.onLogin(event);
                Object properties = propertiesField.get(profile);
                assertEquals(2, Array.getLength(properties));
                Object claimProperty = Array.get(properties, 0);
                Object signatureProperty = Array.get(properties, 1);
                assertEquals(IdentityClaim.PROPERTY_NAME,
                        claimProperty.getClass().getMethod("getName").invoke(claimProperty));
                assertEquals(IdentityClaim.SIGNATURE_PROPERTY_NAME,
                        signatureProperty.getClass().getMethod("getName").invoke(signatureProperty));
                String value = (String) claimProperty.getClass().getMethod("getValue").invoke(claimProperty);
                String signature = (String) signatureProperty.getClass().getMethod("getValue").invoke(signatureProperty);
                IdentityClaim claim = IdentityClaim.parseExactlyOne(Collections.singletonList(value), "TestUser", uuid);
                assertNull(claim.getProxyId());
                assertEquals(online ? IdentityClaim.Decision.PREMIUM_VERIFIED : IdentityClaim.Decision.OFFLINE,
                        claim.getDecision());
                assertTrue(IdentityClaim.verifySignature(value, signature, proxyId));
            }
        }
    }

    @Test
    void testUUIDSetter() throws Throwable {
        BungeeCord proxyMock = mock(BungeeCord.class);
        BungeeCord.setInstance(proxyMock);
        
        Configuration configMock = mock(Configuration.class);
        Field configField = proxyMock.getClass().getField("config");
        configField.setAccessible(true);
        configField.set(proxyMock, configMock);

        InitialHandler handler = new InitialHandler(proxyMock, null);

        UUID expectedUUID = UUID.randomUUID();
        ConnectListener.UNIQUE_ID_SETTER.invokeExact(handler, expectedUUID);
        assertEquals(expectedUUID, handler.getUniqueId());
    }
}
