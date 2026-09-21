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
package com.github.games647.craftapi.resolver;

import com.github.games647.craftapi.UUIDAdapter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Offline HTTP test doubles: a local server standing in for the two Mojang name lookup endpoints, a local
 * HTTP proxy and a raw socket endpoint that counts connections.  Tests using these never touch the network.
 */
final class LocalHttp {

    static final UUID PREMIUM_ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private LocalHttp() {
        // test helper
    }

    @FunctionalInterface
    interface Responder {

        Reply respond(String name) throws IOException;
    }

    static final class Reply {

        private final int status;
        private final String body;

        private Reply(int status, String body) {
            this.status = status;
            this.body = body;
        }

        static Reply status(int status) {
            return new Reply(status, null);
        }

        static Reply profile(String name) {
            return new Reply(200, profileJson(name));
        }

        static Reply malformed(String name) {
            return new Reply(200, "{\"name\":\"" + name + "\"}");
        }

        static Reply raw(int status, String body) {
            return new Reply(status, body);
        }
    }

    static String profileJson(String name) {
        return "{\"id\":\"" + UUIDAdapter.toMojangId(PREMIUM_ID) + "\",\"name\":\"" + name + "\"}";
    }

    /**
     * Stands in for {@code api.mojang.com} (primary) and {@code api.minecraftservices.com} (backup).
     */
    static final class Endpoints implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor = Executors.newFixedThreadPool(4);
        private final List<String> primaryRequests = new CopyOnWriteArrayList<>();
        private final List<String> backupRequests = new CopyOnWriteArrayList<>();

        private volatile Responder primary = Reply::profile;
        private volatile Responder backup = Reply::profile;

        Endpoints() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            // a pool instead of the default serial dispatcher, so a test can hold two requests open at the
            // same time (the 403 fallback is racy and needs two concurrent lookups)
            server.setExecutor(executor);
            server.createContext("/primary/", exchange -> respond(exchange, primary, primaryRequests));
            server.createContext("/backup/", exchange -> respond(exchange, backup, backupRequests));
            server.start();
        }

        String primaryUrl() {
            return base() + "/primary/";
        }

        String backupUrl() {
            return base() + "/backup/";
        }

        List<String> primaryRequests() {
            return primaryRequests;
        }

        List<String> backupRequests() {
            return backupRequests;
        }

        void setPrimary(Responder responder) {
            this.primary = responder;
        }

        void setBackup(Responder responder) {
            this.backup = responder;
        }

        private String base() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    /**
     * Local HTTP proxy that records the absolute request URL it was asked to fetch.
     */
    static final class ProxyServer implements AutoCloseable {

        private final HttpServer server;
        private final List<String> requests = new CopyOnWriteArrayList<>();

        private volatile Responder responder = Reply::profile;

        ProxyServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            // a proxy serves absolute URLs; the root context matches every path
            server.createContext("/", exchange -> respond(exchange, responder, requests));
            server.start();
        }

        List<String> requests() {
            return requests;
        }

        void setResponder(Responder responder) {
            this.responder = responder;
        }

        Proxy asProxy() {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", server.getAddress().getPort()));
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    /**
     * Minimal HTTP/1.1 endpoint backed by a raw {@link ServerSocket} that counts the TCP connections it
     * accepted.  Keep-alive reuse is only observable at the connection level: a response body that the
     * client never consumed makes it drop the socket and open a fresh one, which shows up as a second
     * accept.  (Comparing the client's ephemeral port is not reliable - the OS may hand out the same port
     * again for a brand new socket.)
     *
     * <p>Every request is answered with {@code firstReply} at first and with {@code subsequentReply}
     * afterwards, and the connection is kept open in between.
     */
    static final class ConnectionCountingEndpoint implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final AtomicInteger connections = new AtomicInteger();
        private final AtomicInteger requests = new AtomicInteger();

        private final Reply firstReply;
        private final Reply subsequentReply;

        ConnectionCountingEndpoint(Reply firstReply, Reply subsequentReply) throws IOException {
            this.firstReply = firstReply;
            this.subsequentReply = subsequentReply;

            serverSocket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
            Thread server = new Thread(this::serve, "local-http-endpoint");
            server.setDaemon(true);
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort();
        }

        int connections() {
            return connections.get();
        }

        int requests() {
            return requests.get();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }

        private void serve() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    connections.incrementAndGet();
                    serveConnection(socket);
                } catch (IOException done) {
                    // closed server socket or an idle/broken connection: keep serving the remaining ones
                }
            }
        }

        private void serveConnection(Socket socket) throws IOException {
            socket.setSoTimeout(5_000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    continue;
                }

                Reply reply = requests.incrementAndGet() == 1 ? firstReply : subsequentReply;
                byte[] body = reply.body == null ? new byte[0] : reply.body.getBytes(StandardCharsets.UTF_8);
                out.write(("HTTP/1.1 " + reply.status + " Status\r\n"
                        + "Content-Length: " + body.length + "\r\n"
                        + "Content-Type: application/json\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(body);
                out.flush();
            }
        }
    }

    private static void respond(HttpExchange exchange, Responder responder, List<String> observed)
            throws IOException {
        observed.add(exchange.getRequestURI().toString());

        String path = exchange.getRequestURI().getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        Reply reply = responder.respond(name);

        byte[] body = reply.body == null ? new byte[0] : reply.body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(reply.status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            if (body.length > 0) {
                out.write(body);
            }
        }
    }
}
