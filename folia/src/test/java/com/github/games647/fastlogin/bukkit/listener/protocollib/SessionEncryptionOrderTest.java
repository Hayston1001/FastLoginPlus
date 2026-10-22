/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.comphenix.protocol.AsynchronousManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.async.AsyncMarker;
import com.comphenix.protocol.events.PacketEvent;
import com.github.games647.craftapi.model.auth.Verification;
import com.github.games647.craftapi.model.skin.SkinProperty;
import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import io.netty.channel.Channel;
import io.netty.channel.DefaultEventLoop;
import net.md_5.bungee.config.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.slf4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class SessionEncryptionOrderTest {

    @ParameterizedTest
    @ValueSource(strings = {"invalid-session", "auth-server-unreachable", "interrupted", "missing-name",
            "success", "retry-success"})
    void installsCipherBeforeVerificationAndEveryLaterDisconnectOrLogin(String scenario) throws Exception {
        FastLoginBukkit plugin = mock(FastLoginBukkit.class);
        FastLoginCore core = mock(FastLoginCore.class);
        Logger logger = mock(Logger.class);
        Player player = mock(Player.class);
        Channel channel = mock(Channel.class);
        MojangResolver resolver = mock(MojangResolver.class);
        PacketEvent packet = mock(PacketEvent.class);
        AsyncMarker marker = mock(AsyncMarker.class);
        ProtocolManager manager = mock(ProtocolManager.class);
        AsynchronousManager async = mock(AsynchronousManager.class);
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);
        UUID uuid = UUID.randomUUID();
        BukkitLoginSession session = new BukkitLoginSession("TestUser", false);
        Configuration config = new Configuration();
        config.set("mojang-retry-count", 2);
        config.set("mojang-retry-delay", 0);
        when(plugin.getCore()).thenReturn(core);
        when(plugin.getLog()).thenReturn(logger);
        when(plugin.getConfig()).thenReturn(mock(FileConfiguration.class));
        when(core.getConfig()).thenReturn(config);
        when(core.getResolver()).thenReturn(resolver);
        when(core.getMessage(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(player.getAddress()).thenReturn(address);
        when(channel.isActive()).thenReturn(true);
        when(packet.getAsyncMarker()).thenReturn(marker);
        when(marker.getProcessingLock()).thenReturn(new Object());
        when(manager.getAsynchronousManager()).thenReturn(async);

        KeyPair keyPair = EncryptionUtil.generateKeyPair();
        SecretKey key = new SecretKeySpec(new byte[16], "AES");
        Cipher rsa = Cipher.getInstance("RSA");
        rsa.init(Cipher.ENCRYPT_MODE, keyPair.getPublic());
        VerifyResponseTask task = spy(new VerifyResponseTask(plugin, packet, player, session,
                rsa.doFinal(key.getEncoded()), keyPair));
        AtomicBoolean installed = new AtomicBoolean();
        AtomicBoolean handedOff = new AtomicBoolean();
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch installing = new CountDownLatch(1);
        CountDownLatch releaseInstall = new CountDownLatch(1);

        DefaultEventLoop eventLoop = new DefaultEventLoop();
        when(channel.eventLoop()).thenReturn(eventLoop);
        doAnswer(invocation -> {
            assertTrue(eventLoop.inEventLoop());
            assertArrayEquals(key.getEncoded(), ((SecretKey) invocation.getArgument(0)).getEncoded());
            installing.countDown();
            assertTrue(releaseInstall.await(5, TimeUnit.SECONDS));
            installed.set(true);
            return true;
        }).when(task).enableEncryption(any());
        doAnswer(invocation -> {
            assertTrue(installed.get(), "Disconnect must use the installed cipher");
            return null;
        }).when(task).kickPlayer(anyString());
        doAnswer(invocation -> {
            assertTrue(installed.get());
            assertTrue(eventLoop.inEventLoop());
            handedOff.set(true);
            return null;
        }).when(task).receiveFakeStartPacket(anyString(), any(), any());
        when(resolver.hasJoined(eq("TestUser"), anyString(), eq(address.getAddress())))
                .thenAnswer(invocation -> {
                    assertTrue(installed.get(), "Mojang must not be contacted before the cipher is installed");
                    assertFalse(eventLoop.inEventLoop(), "Network requests must stay off the Netty event loop");
                    int attempt = requests.incrementAndGet();
                    if (scenario.equals("auth-server-unreachable") || scenario.equals("interrupted")
                            || (scenario.equals("retry-success") && attempt == 1)) {
                        throw new IOException("Injected session server outage");
                    }
                    if (scenario.equals("invalid-session")) {
                        return Optional.empty();
                    }
                    return Optional.of(new Verification(uuid, scenario.equals("missing-name") ? null : "TestUser",
                            new SkinProperty[0]));
                });

        CompletableFuture<Void> observer = CompletableFuture.runAsync(() -> {
            try {
                assertTrue(installing.await(5, TimeUnit.SECONDS));
                verifyNoInteractions(resolver);
                verify(task, never()).kickPlayer(anyString());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            } finally {
                releaseInstall.countDown();
            }
        });

        try (MockedStatic<ProtocolLibCompat> compat = mockStatic(ProtocolLibCompat.class);
             MockedStatic<ProtocolLibrary> protocol = mockStatic(ProtocolLibrary.class)) {
            compat.when(() -> ProtocolLibCompat.getChannel(logger, false, player)).thenReturn(channel);
            compat.when(() -> ProtocolLibCompat.isConnectionActive(logger, false, player)).thenReturn(true);
            protocol.when(ProtocolLibrary::getProtocolManager).thenReturn(manager);
            if (scenario.equals("interrupted")) {
                Thread.currentThread().interrupt();
            }
            task.run();
            assertEquals(scenario.equals("interrupted"), Thread.interrupted());
            observer.get(10, TimeUnit.SECONDS);
            eventLoop.submit(() -> { }).syncUninterruptibly();

            verify(task, times(1)).enableEncryption(any());
            InOrder cancellation = inOrder(packet, async);
            cancellation.verify(packet).setCancelled(true);
            cancellation.verify(async).signalPacketTransmission(packet);
            boolean success = scenario.endsWith("success");
            assertEquals(success, handedOff.get());
            assertEquals(success, session.isVerifiedPremium());
            if (success) {
                verify(task, never()).kickPlayer(anyString());
            } else {
                String expected = scenario.equals("missing-name") ? "invalid-session"
                        : scenario.equals("interrupted") ? "auth-server-unreachable" : scenario;
                verify(task).kickPlayer(expected);
            }
            verify(resolver, times(scenario.equals("auth-server-unreachable") || scenario.equals("retry-success")
                    ? 2 : 1)).hasJoined(eq("TestUser"), anyString(), eq(address.getAddress()));
        } finally {
            Thread.interrupted();
            releaseInstall.countDown();
            eventLoop.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}
