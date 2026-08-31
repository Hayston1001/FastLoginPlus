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

import static com.github.games647.fastlogin.core.web.JsonUtil.of;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.IpBanManager;

import io.javalin.http.Context;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for the anti-bot API endpoints.
 */
public class AntiBotApiHandler {

    // 0.6.0/F050: InetAddress.getByName() performs blocking DNS for anything
    // that is not an IP literal, so a hostname input would stall a Jetty
    // request thread (thread-pool exhaustion). Inputs are validated against
    // these strict literal patterns BEFORE resolution — a string matching
    // either one is parsed by the JDK as a pure literal (no DNS).
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}$"
            + "|^([0-9a-fA-F]{1,4}:){1,7}:$"
            + "|^([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}$"
            + "|^([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}$"
            + "|^([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}$"
            + "|^([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}$"
            + "|^([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}$"
            + "|^[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})$"
            + "|^:((:[0-9a-fA-F]{1,4}){1,7}|:)$"
            + "|^::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}"
            + "(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])$"
            + "|^([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}"
            + "(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])$");

    // 0.6.0/F050: longest temporary ban the panel may set (30 days)
    static final long MAX_BAN_DURATION_SECONDS = 2_592_000L;

    private final AntiBotService antiBot;

    public AntiBotApiHandler(AntiBotService antiBot) {
        this.antiBot = antiBot;
    }

    /**
     * Handle GET /api/antibot/stats request.
     *
     * @param ctx the Javalin context
     */
    public void handleStats(Context ctx) {
        IpBanManager banManager = antiBot.getIpBanManager();

        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", true);
        stats.put("banCount", banManager.banCount());
        stats.put("action", antiBot.getLimitReachedAction().name());

        ctx.json(stats);
    }

    /**
     * Handle GET /api/antibot/bans request.
     *
     * @param ctx the Javalin context
     */
    public void handleListBans(Context ctx) {
        IpBanManager banManager = antiBot.getIpBanManager();
        List<Map<String, Object>> bans = banManager.getBannedIps();

        ctx.json(bans);
    }

    /**
     * Handle POST /api/antibot/ban request.
     *
     * <p>Expects JSON body: {"ip": "x.x.x.x", "duration": 300}</p>
     *
     * @param ctx the Javalin context
     */
    public void handleBan(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);

        String ip = (String) body.get("ip");
        Object durationObj = body.get("duration");

        if (ip == null || ip.isEmpty()) {
            ctx.status(400).json(of("error", "Missing 'ip' field"));
            return;
        }

        // 0.6.0/F050: hostnames are rejected before resolution — resolving
        // them would block the request thread on DNS
        if (!isIpLiteral(ip)) {
            ctx.status(400).json(of("error", "Invalid IP address (literal IP required)"));
            return;
        }

        long durationMs = 300_000; // Default 5 minutes
        if (durationObj instanceof Number) {
            long seconds = ((Number) durationObj).longValue();
            if (seconds <= 0 || seconds > MAX_BAN_DURATION_SECONDS) {
                // 0.6.0/F050: reject non-positive and absurd durations
                ctx.status(400).json(of("error", "duration must be between 1 and "
                        + MAX_BAN_DURATION_SECONDS + " seconds"));
                return;
            }
            durationMs = seconds * 1000;
        }

        try {
            InetAddress address = InetAddress.getByName(ip);
            IpBanManager banManager = antiBot.getIpBanManager();
            banManager.ban(address, durationMs);

            ctx.json(of("success", true, "ip", ip, "durationMs", durationMs));
        } catch (UnknownHostException e) {
            ctx.status(400).json(of("error", "Invalid IP address"));
        }
    }

    /**
     * Handle DELETE /api/antibot/ban/:ip request.
     *
     * @param ctx the Javalin context
     */
    public void handleUnban(Context ctx) {
        String ip = ctx.pathParam("ip");

        // 0.6.0/F050: same literal-only rule as ban — no DNS on request threads
        if (!isIpLiteral(ip)) {
            ctx.status(400).json(of("error", "Invalid IP address (literal IP required)"));
            return;
        }

        try {
            InetAddress address = InetAddress.getByName(ip);
            IpBanManager banManager = antiBot.getIpBanManager();
            boolean removed = banManager.unban(address);

            if (removed) {
                ctx.json(of("success", true, "ip", ip));
            } else {
                ctx.status(404).json(of("error", "IP not found in ban list"));
            }
        } catch (UnknownHostException e) {
            ctx.status(400).json(of("error", "Invalid IP address"));
        }
    }

    /**
     * Check whether the input is an IPv4 or IPv6 literal (0.6.0/F050).
     *
     * <p>Only literals are ever handed to {@code InetAddress.getByName()}, so
     * the call can never trigger a DNS lookup.</p>
     *
     * @param input the candidate IP string (never null or empty)
     * @return true when the input is a syntactically valid IP literal
     */
    private static boolean isIpLiteral(String input) {
        if (input.indexOf(':') >= 0) {
            return IPV6_PATTERN.matcher(input).matches();
        }

        Matcher matcher = IPV4_PATTERN.matcher(input);
        if (!matcher.matches()) {
            return false;
        }

        for (int i = 1; i <= matcher.groupCount(); i++) {
            if (Integer.parseInt(matcher.group(i)) > 255) {
                return false;
            }
        }
        return true;
    }
}
