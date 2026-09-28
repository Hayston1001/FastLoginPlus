/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 Hayston1001
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
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.AccountStatusWire;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Small authenticated side channel for the existing Velocity PreLogin decision. */
final class AccountStatusServer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("fastloginplus_fabric");
    private final MinecraftServer server;
    private final EasyAuth344Bridge bridge;
    private final byte[] key;
    private final ServerSocket listener;
    private final ThreadPoolExecutor workers;
    private final Thread acceptor;

    AccountStatusServer(MinecraftServer server, EasyAuth344Bridge bridge) {
        this.server = server;
        this.bridge = bridge;
        try {
            Path directory = FabricLoader.getInstance().getConfigDir();
            Files.createDirectories(directory);
            Properties config = new Properties();
            Path configFile = directory.resolve("fastloginplus-fabric.properties");
            if (Files.exists(configFile)) {
                try (var input = Files.newInputStream(configFile)) {
                    config.load(input);
                }
            }
            String bind = config.getProperty("account-status-bind", "127.0.0.1");
            int port = Integer.parseInt(config.getProperty("account-status-port", "25578"));
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Invalid account status port");
            }
            Path keyPath = directory.resolve("fastloginplus-account.key");
            if (!Files.exists(keyPath)) {
                byte[] generated = new byte[32];
                new SecureRandom().nextBytes(generated);
                try {
                    Files.write(keyPath, generated, StandardOpenOption.CREATE_NEW);
                } catch (java.nio.file.FileAlreadyExistsException concurrentCreate) {
                    // Another startup created it; use the completed file below.
                }
            }
            key = Files.readAllBytes(keyPath);
            if (key.length != 32) {
                throw new IllegalStateException("Account status key must contain exactly 32 bytes");
            }
            listener = new ServerSocket();
            listener.bind(new InetSocketAddress(InetAddress.getByName(bind), port), 32);
            workers = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(32), task -> {
                        Thread thread = new Thread(task, "flp-account-status-worker");
                        thread.setDaemon(true);
                        return thread;
                    });
            acceptor = new Thread(this::acceptLoop, "flp-account-status-listener");
            acceptor.setDaemon(true);
            acceptor.start();
            LOGGER.info("FastLoginPlus account status listening on {}:{}", bind, port);
        } catch (IOException | NumberFormatException error) {
            throw new IllegalStateException("Cannot start Fabric account status service", error);
        }
    }

    private void acceptLoop() {
        while (!listener.isClosed()) {
            try {
                Socket socket = listener.accept();
                socket.setSoTimeout(1500);
                try {
                    workers.execute(() -> handle(socket));
                } catch (java.util.concurrent.RejectedExecutionException full) {
                    socket.close();
                }
            } catch (IOException error) {
                if (!listener.isClosed()) {
                    LOGGER.error("Account status listener failed", error);
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            String line = readLine(socket);
            AccountStatusWire.Request request = AccountStatusWire.parseRequest(line, key);
            AccountStatusWire.Result result = server.submit(() -> bridge.accountStatus(request.getName()))
                    .get(2, TimeUnit.SECONDS);
            String response = AccountStatusWire.response(request.getNonce(), result.getStatus(), result.getUuid(), key);
            socket.getOutputStream().write((response + "\n").getBytes(StandardCharsets.US_ASCII));
        } catch (Exception failure) {
            // Invalid MAC, unavailable database and timeout all close without an answer.
            // The proxy fails this login closed rather than guessing account ownership.
        }
    }

    private static String readLine(Socket socket) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int next; (next = socket.getInputStream().read()) != -1;) {
            if (next == '\n') {
                return bytes.toString(StandardCharsets.US_ASCII);
            }
            if (next < 32 || next > 126 || bytes.size() >= 160) {
                throw new IOException("Malformed account query");
            }
            bytes.write(next);
        }
        throw new IOException("Incomplete account query");
    }

    @Override
    public void close() {
        try {
            listener.close();
        } catch (IOException error) {
            LOGGER.warn("Could not close account status listener", error);
        }
        workers.shutdownNow();
    }
}
