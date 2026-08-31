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
import java.util.function.Supplier;

import org.slf4j.Logger;

import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.storage.SQLStorage;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.config.RoutesConfig;
import io.javalin.http.HttpResponseException;
import io.javalin.http.TooManyRequestsResponse;
import io.javalin.http.UnauthorizedResponse;
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
     * Start the HTTP server with no extra CORS origins (same-origin only).
     *
     * @param host  the host to bind to (empty string or {@code null} for all interfaces)
     * @param port  the port to listen on
     * @param token the Bearer token for authentication
     */
    public void start(String host, int port, String token) {
        start(host, port, token, java.util.Collections.emptyList());
    }

    /**
     * Start the HTTP server.
     *
     * @param host                the host to bind to (empty string or {@code null} for all interfaces)
     * @param port                the port to listen on
     * @param token               the Bearer token for authentication
     * @param corsAllowedOrigins  extra browser origins allowed by CORS; the
     *                            panel is same-origin, so an empty list (the
     *                            default) registers no CORS at all (0.6.0/F013)
     */
    public void start(String host, int port, String token,
                      List<String> corsAllowedOrigins) {
        app = Javalin.create(config -> {
            // Serve static files from classpath
            config.staticFiles.add("/web", Location.CLASSPATH);

            // Configure Jackson for proper Instant/UUID serialization
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper.registerModule(new JavaTimeModule());
                mapper.registerModule(new Jdk8Module());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }));

            if (corsAllowedOrigins != null && !corsAllowedOrigins.isEmpty()) {
                // 0.6.0/F013: opt-in CORS whitelist. anyHost() (the previous
                // default) let any web page read the whole admin API cross-
                // origin. Origins without a scheme get http:// prefixed by
                // Javalin's default rule scheme.
                config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
                    for (String origin : corsAllowedOrigins) {
                        rule.allowHost(origin);
                    }
                }));
            }

            // Authentication middleware
            config.routes.before(ctx -> {
                // Skip static file requests and language API (public, no auth needed)
                String path = ctx.path();
                if (!path.startsWith("/api/") || path.startsWith("/api/lang/")) {
                    return;
                }

                // Rate limit BEFORE the token check so invalid-token brute forcing
                // is throttled too, not only correctly authenticated traffic.
                // (0.6.0/F001) Throwing is what actually interrupts the Javalin
                // pipeline — only writing a status and returning does not stop
                // the endpoint handler from running and overwriting the body.
                String clientIp = normalizeIp(ctx.ip());
                if (!checkRateLimit(clientIp)) {
                    throw new TooManyRequestsResponse("Too many requests");
                }

                // Constant-time comparison — a plain String.equals() on the
                // Authorization header leaks the token prefix through timing.
                String auth = ctx.header("Authorization");
                byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
                byte[] provided = (auth == null) ? new byte[0] : auth.getBytes(StandardCharsets.UTF_8);
                if (auth == null || !MessageDigest.isEqual(expected, provided)) {
                    throw new UnauthorizedResponse("Unauthorized");
                }
            });

            // (0.6.0/F001 companion) Render thrown HttpResponseExceptions as a
            // stable {"error": "<message>"} JSON body. Javalin's default error
            // format is {"title": ...}, which would break the dashboard's
            // logout contract (F052: error === 'Unauthorized') and the
            // frontend's data.error display for 429s.
            config.routes.exception(HttpResponseException.class, (ex, ctx) -> ctx
                    .status(ex.getStatus())
                    .json(java.util.Collections.singletonMap("error", ex.getMessage())));

            // Register API routes — Javalin 7 requires upfront registration in the
            // config block; routes cannot be added after create()
            registerRoutes(config.routes);
        });

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

    /**
     * Get the actual port the running HTTP server is bound to.
     *
     * <p>Mainly used with an ephemeral port ({@code 0}) to discover the
     * randomly assigned port, e.g. from tests.</p>
     *
     * @return the bound port, or {@code -1} if the server was never created
     */
    public int port() {
        return app == null ? -1 : app.port();
    }


    /**
     * Register all API routes on the given router.
     *
     * <p>Javalin 7 requires routes to be registered upfront inside the
     * {@code Javalin.create()} config block, so the router is passed in
     * instead of being accessed through the app field.</p>
     *
     * @param routes the Javalin router configuration from the config block
     */
    private void registerRoutes(RoutesConfig routes) {
        // Language API
        routes.get("/api/lang/{code}", ctx -> {
            LangApiHandler handler = new LangApiHandler(log, pluginFolder);
            handler.handle(ctx);
        });

        // Online players API
        routes.get("/api/online", ctx -> {
            OnlineApiHandler handler = new OnlineApiHandler(storage, onlinePlayersSupplier);
            handler.handle(ctx);
        });

        // Player database API
        routes.get("/api/players", ctx -> {
            PlayerApiHandler handler = new PlayerApiHandler(storage);
            handler.handleList(ctx);
        });

        routes.get("/api/players/{name}", ctx -> {
            PlayerApiHandler handler = new PlayerApiHandler(storage);
            handler.handleGet(ctx);
        });

        routes.put("/api/players/{name}/premium", ctx -> {
            PlayerApiHandler handler = new PlayerApiHandler(storage, premiumToggleListener);
            handler.handleSetPremium(ctx, true);
        });

        routes.put("/api/players/{name}/cracked", ctx -> {
            PlayerApiHandler handler = new PlayerApiHandler(storage, premiumToggleListener);
            handler.handleSetPremium(ctx, false);
        });

        routes.delete("/api/players/{name}", ctx -> {
            PlayerApiHandler handler = new PlayerApiHandler(storage);
            handler.handleDelete(ctx);
        });

        // Anti-bot API
        routes.get("/api/antibot/stats", ctx -> {
            AntiBotApiHandler handler = new AntiBotApiHandler(antiBot);
            handler.handleStats(ctx);
        });

        routes.get("/api/antibot/bans", ctx -> {
            AntiBotApiHandler handler = new AntiBotApiHandler(antiBot);
            handler.handleListBans(ctx);
        });

        routes.post("/api/antibot/ban", ctx -> {
            AntiBotApiHandler handler = new AntiBotApiHandler(antiBot);
            handler.handleBan(ctx);
        });

        routes.delete("/api/antibot/ban/{ip}", ctx -> {
            AntiBotApiHandler handler = new AntiBotApiHandler(antiBot);
            handler.handleUnban(ctx);
        });

        // Server status API
        routes.get("/api/status", ctx -> {
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

    // 0.6.0/F015: periodic sweep cadence and idle threshold for the per-IP
    // rate-limit table (computeIfAbsent alone would grow without bound)
    static final long SWEEP_INTERVAL_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(1);
    static final long IDLE_SWEEP_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
    // 0.6.0/F015: protection mode - above this many tracked IPs, entries
    // are evicted after only one minute of inactivity
    static final int MAX_TRACKED_IPS = 10_000;

    private volatile long nextSweepNanos = System.nanoTime() + SWEEP_INTERVAL_NANOS;

    /**
     * Get the number of per-IP rate-limit entries currently tracked.
     *
     * <p>Package-private - used by tests to verify the sweep logic.</p>
     *
     * @return the number of tracked IPs
     */
    int trackedIpCount() {
        return rateLimiters.size();
    }

    boolean checkRateLimit(String ip) {  // package-private for tests
        sweepRateLimiters(System.nanoTime());

        RateCounter counter = rateLimiters.computeIfAbsent(ip, k -> new RateCounter());
        return counter.tryAcquire();
    }

    /**
     * Evict idle per-IP rate-limit entries (0.6.0/F015).
     *
     * <p>Sweeps run at most once per sweep interval; when the table grows
     * beyond {@link #MAX_TRACKED_IPS} (a multi-IP flood), the idle threshold
     * tightens to one minute so the table shrinks instead of leaking.</p>
     *
     * @param nowNanos the current monotonic time (injected for tests)
     */
    void sweepRateLimiters(long nowNanos) {
        if (nowNanos - nextSweepNanos < 0) {
            return;
        }

        nextSweepNanos = nowNanos + SWEEP_INTERVAL_NANOS;

        long idleThreshold = rateLimiters.size() > MAX_TRACKED_IPS
                ? java.util.concurrent.TimeUnit.MINUTES.toNanos(1)
                : IDLE_SWEEP_NANOS;
        rateLimiters.values().removeIf(counter -> counter.isIdle(nowNanos, idleThreshold));
    }

    /**
     * Per-IP counter that allows up to {@link #RATE_LIMIT_PER_SECOND}
     * requests per second.
     */
    private static final class RateCounter {
        // 0.6.0/F016: monotonic clock (nanoTime) - a wall-clock rollback
        // (NTP) can no longer make active IPs receive endless 429s
        private long windowStartNanos = System.nanoTime();
        private volatile long lastAccessNanos = System.nanoTime();
        private int count;

        /**
         * Try to record a request in the current one-second window.
         *
         * <p>Synchronized (0.6.0/F016): the previous unsynchronized
         * check-then-act on count/windowStart let concurrent requests slip
         * past the window reset and under-count.</p>
         *
         * @return true if the request is within the rate limit
         */
        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            lastAccessNanos = now;

            if (now - windowStartNanos >= 1_000_000_000L) {
                // New window
                count = 1;
                windowStartNanos = now;
                return true;
            }

            count++;
            return count <= RATE_LIMIT_PER_SECOND;
        }

        /**
         * Check whether this counter was not used recently.
         *
         * @param nowNanos  the current monotonic time
         * @param idleNanos the idle threshold
         * @return true if the entry is idle long enough to be evicted
         */
        boolean isIdle(long nowNanos, long idleNanos) {
            return nowNanos - lastAccessNanos >= idleNanos;
        }
    }
}
