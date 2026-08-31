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
package com.github.games647.fastlogin.core.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;

import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.storage.SQLStorage;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Embedded HTTP server for the FastLoginPlus web management panel.
 *
 * <p>Provides REST API endpoints for player management, anti-bot statistics,
 * and a static web frontend. Authentication is handled via Bearer token.</p>
 */
public class WebServer {

    private static final int RATE_LIMIT_PER_SECOND = 10;

    private final Logger log;
    private final SQLStorage storage;
    private final AntiBotService antiBot;
    private final String pluginVersion;
    private final java.nio.file.Path pluginFolder;

    private Supplier<List<String>> onlinePlayersSupplier;
    private PremiumToggleListener premiumToggleListener;
    private Javalin app;

    // Simple per-IP rate limiting
    private final ConcurrentHashMap<String, RateCounter> rateLimiters = new ConcurrentHashMap<>();

    public WebServer(Logger log, SQLStorage storage, AntiBotService antiBot,
                     String pluginVersion, java.nio.file.Path pluginFolder) {
        this.log = log;
        this.storage = storage;
        this.antiBot = antiBot;
        this.pluginVersion = pluginVersion;
        this.pluginFolder = pluginFolder;
    }

    /**
     * Set the supplier that provides the list of currently online player names.
     *
     * <p>Each platform (Bukkit, BungeeCord, Velocity) injects its own implementation
     * when starting the web server.</p>
     *
     * @param supplier a function returning online player names
     */
    public void setOnlinePlayersSupplier(Supplier<List<String>> supplier) {
        this.onlinePlayersSupplier = supplier;
    }

    /**
     * Set the callback invoked when premium status is toggled via the WebUI.
     *
     * <p>Platform code should implement this to kick the player
     * (per {@code kick-toggle} config) and fire toggle events.</p>
     *
     * @param listener the callback, or {@code null} to disable
     */
    public void setPremiumToggleListener(PremiumToggleListener listener) {
        this.premiumToggleListener = listener;
    }

    /**
     * Start the HTTP server.
     *
     * @param host  the host to bind to (empty string or {@code null} for all interfaces)
     * @param port  the port to listen on
     * @param token the Bearer token for authentication
     */
    public void start(String host, int port, String token) {
        app = Javalin.create(config -> {
            // Serve static files from classpath
            config.staticFiles.add("/web", Location.CLASSPATH);

            // Configure Jackson for proper Instant/UUID serialization
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper.registerModule(new JavaTimeModule());
                mapper.registerModule(new Jdk8Module());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }));

            // CORS configuration (disabled by default)
            config.plugins.enableCors(cors -> cors.add(it -> it.anyHost()));
        });

        // Authentication middleware
        app.before(ctx -> {
            // Skip static file requests and language API (public, no auth needed)
            String path = ctx.path();
            if (!path.startsWith("/api/") || path.startsWith("/api/lang/")) {
                return;
            }

            // Rate limit BEFORE the token check so invalid-token brute forcing
            // is throttled too, not only correctly authenticated traffic.
            String clientIp = normalizeIp(ctx.ip());
            if (!checkRateLimit(clientIp)) {
                ctx.status(429).json(java.util.Collections.singletonMap("error", "Too many requests"));
                return;
            }

            // Constant-time comparison — a plain String.equals() on the
            // Authorization header leaks the token prefix through timing.
            String auth = ctx.header("Authorization");
            byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
            byte[] provided = (auth == null) ? new byte[0] : auth.getBytes(StandardCharsets.UTF_8);
            if (auth == null || !MessageDigest.isEqual(expected, provided)) {
                ctx.status(401).json(java.util.Collections.singletonMap("error", "Unauthorized"));
            }
        });

        // Register API routes
        registerRoutes();

        // Start server
        app.start(host, port);
        log.info("Web management panel started on {}:{}", host, port);
    }

    /**
     * Stop the HTTP server gracefully.
     */
    public void stop() {
        if (app != null) {
            app.stop();
            log.info("Web management panel stopped");
        }
    }

    private void registerRoutes() {
        // Language API
        app.get("/api/lang/{code}", ctx -> {
            LangApiHandler handler = new LangApiHandler(log, pluginFolder);
            handler.handle(ctx);
        });

        // Online players API
        app.get("/api/online", ctx -> {
            OnlineApiHandler handler = new OnlineApiHandler(storage, onlinePlayersSupplier);
            handler.handle(ctx);
        });

        // Player database API
        app.get("/api/players", ctx -> {
            PlayerApiHandler handler = new PlayerApiHandler(storage);
            handler.handleList(ctx);
        });

        app.get("/api/players/{name}", ctx -> {
            PlayerApiHandler handler = new PlayerApiHandler(storage);
            handler.handleGet(ctx);
        });

        app.put("/api/players/{name}/premium", ctx -> {
            PlayerApiHandler handler = new PlayerApiHandler(storage, premiumToggleListener);
            handler.handleSetPremium(ctx, true);
        });

        app.put("/api/players/{name}/cracked", ctx -> {
            PlayerApiHandler handler = new PlayerApiHandler(storage, premiumToggleListener);
            handler.handleSetPremium(ctx, false);
        });

        app.delete("/api/players/{name}", ctx -> {
            PlayerApiHandler handler = new PlayerApiHandler(storage);
            handler.handleDelete(ctx);
        });

        // Anti-bot API
        app.get("/api/antibot/stats", ctx -> {
            AntiBotApiHandler handler = new AntiBotApiHandler(antiBot);
            handler.handleStats(ctx);
        });

        app.get("/api/antibot/bans", ctx -> {
            AntiBotApiHandler handler = new AntiBotApiHandler(antiBot);
            handler.handleListBans(ctx);
        });

        app.post("/api/antibot/ban", ctx -> {
            AntiBotApiHandler handler = new AntiBotApiHandler(antiBot);
            handler.handleBan(ctx);
        });

        app.delete("/api/antibot/ban/{ip}", ctx -> {
            AntiBotApiHandler handler = new AntiBotApiHandler(antiBot);
            handler.handleUnban(ctx);
        });

        // Server status API
        app.get("/api/status", ctx -> {
            StatusApiHandler handler = new StatusApiHandler(pluginVersion, storage, antiBot, onlinePlayersSupplier);
            handler.handle(ctx);
        });
    }

    /**
     * Normalize IPv4-mapped IPv6 addresses (e.g. "::ffff:192.168.1.1") to plain IPv4 ("192.168.1.1").
     * Pure IPv6 addresses are returned unchanged.
     *
     * @param ip the IP address string to normalize
     * @return the normalized IP address string
     */
    private static String normalizeIp(String ip) {
        if (ip != null && ip.startsWith("::ffff:")) {
            return ip.substring(7);
        }
        return ip;
    }

    private boolean checkRateLimit(String ip) {
        RateCounter counter = rateLimiters.computeIfAbsent(ip, k -> new RateCounter());
        return counter.tryAcquire();
    }

    /**
     * Simple rate counter that allows up to {@link #RATE_LIMIT_PER_SECOND} requests per second.
     */
    private static final class RateCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= 1000) {
                // New window
                count.set(1);
                windowStart = now;
                return true;
            }

            return count.incrementAndGet() <= RATE_LIMIT_PER_SECOND;
        }
    }
}
