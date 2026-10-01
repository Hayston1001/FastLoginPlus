/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.AccountStatusWire;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountStatusServerTest {

    @TempDir
    Path directory;

    @Test
    void closeClosesActiveAndQueuedSockets() throws Exception {
        Fixture fixture = start();
        List<Socket> clients = new ArrayList<>();
        try (fixture.service) {
            for (int i = 0; i < 12; i++) {
                clients.add(connect(fixture.port));
            }
            var field = AccountStatusServer.class.getDeclaredField("workers");
            field.setAccessible(true);
            ThreadPoolExecutor workers = (ThreadPoolExecutor) field.get(fixture.service);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (workers.getQueue().size() < 10 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertTrue(workers.getQueue().size() >= 10, "Requests must be queued before closing");
            fixture.service.close();
            for (Socket client : clients) {
                assertEquals(-1, client.getInputStream().read(), "Every accepted socket must close");
            }
        } finally {
            for (Socket client : clients) {
                client.close();
            }
        }
    }

    @Test
    void closeCancelsQueryBeforeItsMainThreadTaskRuns() throws Exception {
        Fixture fixture = start();
        List<Runnable> mainThreadTasks = new ArrayList<>();
        CountDownLatch submitted = new CountDownLatch(1);
        CompletableFuture<AccountStatusWire.Result> future = stubSubmission(fixture, mainThreadTasks, submitted);
        try (fixture.service; Socket client = connect(fixture.port)) {
            sendRequest(client);
            assertTrue(submitted.await(1, TimeUnit.SECONDS));
            fixture.service.close();
            assertEquals(-1, client.getInputStream().read());
            assertTrue(future.isCancelled(), "Shutdown must cancel the outstanding query");
            mainThreadTasks.forEach(Runnable::run);
            verifyNoInteractions(fixture.bridge);
        }
    }

    @Test
    void timeoutCancelsQueryBeforeItsMainThreadTaskRuns() throws Exception {
        Fixture fixture = start();
        List<Runnable> mainThreadTasks = new ArrayList<>();
        CountDownLatch submitted = new CountDownLatch(1);
        CompletableFuture<AccountStatusWire.Result> future = stubSubmission(fixture, mainThreadTasks, submitted);
        try (fixture.service; Socket client = connect(fixture.port)) {
            client.setSoTimeout(4000);
            sendRequest(client);
            assertTrue(submitted.await(1, TimeUnit.SECONDS));
            assertEquals(-1, client.getInputStream().read());
            assertTrue(future.isCancelled(), "A timed-out query must not remain queued");
            mainThreadTasks.forEach(Runnable::run);
            verifyNoInteractions(fixture.bridge);
        }
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<AccountStatusWire.Result> stubSubmission(
            Fixture fixture, List<Runnable> tasks, CountDownLatch submitted) {
        CompletableFuture<AccountStatusWire.Result> future = new CompletableFuture<>();
        when(fixture.server.submit(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<AccountStatusWire.Result> supplier = invocation.getArgument(0);
            tasks.add(() -> {
                if (!future.isDone()) {
                    future.complete(supplier.get());
                }
            });
            submitted.countDown();
            return future;
        });
        return future;
    }

    private void sendRequest(Socket client) throws Exception {
        String request = AccountStatusWire.request("Hayston1001", AccountStatusWire.nonce(), new byte[32]);
        client.getOutputStream().write((request + "\n").getBytes(StandardCharsets.US_ASCII));
    }

    private Socket connect(int port) throws Exception {
        Socket client = new Socket("127.0.0.1", port);
        client.setSoTimeout(500);
        return client;
    }

    private Fixture start() throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        Files.writeString(directory.resolve("fastloginplus-fabric.properties"),
                "account-status-port=" + port + "\n");
        Files.write(directory.resolve("fastloginplus-account.key"), new byte[32]);
        MinecraftServer server = mock(MinecraftServer.class);
        EasyAuth344Bridge bridge = mock(EasyAuth344Bridge.class);
        FabricLoader loader = mock(FabricLoader.class);
        when(loader.getConfigDir()).thenReturn(directory);
        try (var access = mockStatic(FabricLoader.class)) {
            access.when(FabricLoader::getInstance).thenReturn(loader);
            return new Fixture(new AccountStatusServer(server, bridge), server, bridge, port);
        }
    }

    private record Fixture(AccountStatusServer service, MinecraftServer server, EasyAuth344Bridge bridge, int port) {
    }
}
