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
import com.github.games647.craftapi.model.NameHistory;
import com.github.games647.craftapi.model.Profile;
import com.github.games647.craftapi.model.auth.Account;
import com.github.games647.craftapi.model.auth.AuthRequest;
import com.github.games647.craftapi.model.auth.AuthResponse;
import com.github.games647.craftapi.model.auth.Verification;
import com.github.games647.craftapi.model.skin.Model;
import com.github.games647.craftapi.model.skin.SkinProperty;
import com.github.games647.craftapi.model.skin.Textures;
import com.github.games647.craftapi.resolver.ratelimiter.RateLimiter;
import com.github.games647.craftapi.resolver.ratelimiter.TickingRateLimiter;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.awt.image.RenderedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

/**
 * Resolver that contacts Mojang.
 */
public class MojangResolver extends AbstractResolver implements AuthResolver, ProfileResolver {

    /**
     * Upper bound enforced by the {@code mojang-request-limit} option (and by Mojang itself): at most
     * this many direct name lookups within 10 minutes are allowed to be sent from one outgoing IPv4.
     */
    public static final int MAX_NAME_REQUESTS_LIMIT = 600;

    static {
        // Try to fix https://bugs.openjdk.org/browse/JDK-8197807: the first HTTPS request of the JVM
        // pays for creating the default SSLSocketFactory, which can delay the very first login.
        HttpsURLConnection.getDefaultSSLSocketFactory();
    }

    //profile
    private static final String UUID_URL = "https://api.mojang.com/users/profiles/minecraft/";
    //Mojang occasionally answers 403 for the endpoint above even though the request is fine
    //(WEB-7591 / WEB-7666) - the second host serves the same lookup and does not
    private static final String BACKUP_UUID_URL =
            "https://api.minecraftservices.com/minecraft/profile/lookup/name/";

    //skin
    private static final String CHANGE_SKIN_URL = "https://api.mojang.com/user/profile/%s/skin";
    private static final String RESET_SKIN_URL = "https://api.mojang.com/user/profile/%s/skin";
    private static final String SKIN_URL = "https://sessionserver.mojang.com/session/minecraft/profile/%s"
            + "?unsigned=false";

    //authentication
    private static final String AUTH_URL = "https://authserver.mojang.com/authenticate";
    private static final String HAS_JOINED_URL_PROXY_CHECK = "https://sessionserver.mojang.com/session/minecraft/"
        + "hasJoined?username=%s&serverId=%s&ip=%s";
    private static final String HAS_JOINED_URL_RAW = "https://sessionserver.mojang.com/session/minecraft/hasJoined?"
            + "username=%s&serverId=%s";

    /**
     * Active name-lookup endpoints.  Package-private and non-final so the offline tests can point them
     * at a local {@code HttpServer}; production code never changes them and FLP does not expose them.
     */
    String uuidUrl = UUID_URL;
    String backupUuidUrl = BACKUP_UUID_URL;

    /**
     * Sticky {@code 403} fallback: once the primary endpoint rejected us, this resolver instance keeps
     * using the backup endpoint for all following lookups.  See WEB-7591 / WEB-7666.
     * <p>
     * {@code volatile} because lookups run on concurrent login threads: the write is not synchronized with
     * the reads that select the endpoint for the next request.
     */
    volatile boolean useBackupUuidUrl;

    /**
     * Active session-server endpoints, package-private for the same reason as the lookup endpoints.
     */
    String hasJoinedUrlProxyCheck = HAS_JOINED_URL_PROXY_CHECK;
    String hasJoinedUrlRaw = HAS_JOINED_URL_RAW;

    private ProxySelector proxySelector = ProxySelector.getDefault();

    private int maxNameRequests = MAX_NAME_REQUESTS_LIMIT;
    private RateLimiter profileLimiter = new TickingRateLimiter(
            Ticker.systemTicker(), maxNameRequests,
            TimeUnit.MINUTES.toMillis(10)
    );

    @Override
    public Optional<Verification> hasJoined(String username, String serverHash, InetAddress hostIp)
            throws IOException {
        String url;
        if (hostIp == null || hostIp instanceof Inet6Address) {
            // No client IP to verify against: either the caller does not have one (transparent reverse
            // proxy) or it is IPv6, which Mojang does not check correctly - the vanilla prevent-proxy
            // feature does not work with IPv6 either, so the parameter is left out.
            url = String.format(hasJoinedUrlRaw, username, serverHash);
        } else {
            String encodedIP = URLEncoder.encode(hostIp.getHostAddress(), StandardCharsets.UTF_8.name());
            url = String.format(hasJoinedUrlProxyCheck, username, serverHash, encodedIP);
        }

        HttpURLConnection conn = getConnection(url);

        int responseCode;
        try {
            responseCode = conn.getResponseCode();
        } catch (IOException connectFailure) {
            conn.disconnect();
            throw connectFailure;
        }

        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
            // both carry a response the caller never reads, so consume it before leaving
            drainQuietly(conn);
            return Optional.empty();
        }

        // a 200 with a malformed body must not turn into an NPE - it simply is not a verification
        return Optional.ofNullable(parseRequest(conn, in -> readJson(in, Verification.class)));
    }

    @Override
    public Account authenticate(String email, String password) throws IOException, InvalidCredentialsException {
        HttpURLConnection conn = getConnection(AUTH_URL);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        try (
                OutputStream out = conn.getOutputStream();
                OutputStreamWriter outWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                BufferedWriter writer = new BufferedWriter(outWriter)
        ) {
            writer.append(gson.toJson(new AuthRequest(email, password)));
        }

        AuthResponse authResponse = parseRequest(conn, in -> readJson(in, AuthResponse.class));
        return new Account(authResponse.getSelectedProfile(), authResponse.getAccessToken());
    }

    @Override
    public void changeSkin(Account account, URL toUrl, Model skinModel) throws IOException {
        String url = String.format(CHANGE_SKIN_URL, UUIDAdapter.toMojangId(account.getProfile().getId()));

        HttpURLConnection conn = getConnection(url);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        conn.addRequestProperty("Authorization", "Bearer " + account.getAccessToken());
        try (
                OutputStream out = conn.getOutputStream();
                OutputStreamWriter outWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                BufferedWriter writer = new BufferedWriter(outWriter)
        ) {
            writer.write("model=");
            if (skinModel == Model.SLIM) {
                writer.write("slim");
            }

            final String skinUrl = toUrl.toExternalForm();
            writer.write("&url=" + URLEncoder.encode(skinUrl, StandardCharsets.UTF_8.name()));
        }

        int responseCode = conn.getResponseCode();
        discard(conn);
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Response code is not Ok: " + responseCode);
        }
    }

    @Override
    public void changeSkin(Account account, RenderedImage pngImage, Model skinModel) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean resetSkin(Account account) throws IOException {
        String url = String.format(RESET_SKIN_URL, account.getProfile().getId());

        HttpURLConnection conn = getConnection(url);
        conn.setRequestMethod("DELETE");
        conn.addRequestProperty("Authorization", "Bearer " + account.getAccessToken());

        int responseCode = conn.getResponseCode();
        discard(conn);
        return responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT;
    }

    @Override
    public ImmutableSet<Profile> findProfiles(String... names) throws IOException, RateLimitException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ImmutableList<NameHistory> findNames(UUID uuid) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional<Profile> findProfile(String name) throws IOException, RateLimitException {
        Optional<Profile> optProfile = cache.getByName(name);
        if (optProfile.isPresent() || !validNamePredicate.test(name)) {
            return optProfile;
        }

        // a configured limit of 0 means: never send a direct lookup, always go through a proxy
        boolean viaProxy = !profileLimiter.tryAcquire();
        // The sticky flag only selects the endpoint for a *new* request.  A request already in flight keeps
        // the endpoint it started with, so two lookups that both hit 403 on the primary cannot make each
        // other believe that "both endpoints" were already tried.
        boolean viaBackupEndpoint = useBackupUuidUrl;
        String url = (viaBackupEndpoint ? backupUuidUrl : uuidUrl) + name;
        return queryProfile(name, url, viaProxy, viaBackupEndpoint);
    }

    /**
     * Performs a single name lookup and applies the response handling to it.
     *
     * @param name the requested player name
     * @param url the complete lookup url, including the player name
     * @param viaProxy whether the request has to be sent through a configured proxy
     * @param viaBackupEndpoint whether the given url points at the backup endpoint
     * @return the resolved profile, or empty if the name does not belong to a premium account
     * @throws IOException if the request failed or Mojang answered with an unexpected status
     * @throws RateLimitException if no proxy is available or the proxy is limited as well
     */
    private Optional<Profile> queryProfile(String name, String url, boolean viaProxy, boolean viaBackupEndpoint)
            throws IOException, RateLimitException {
        HttpURLConnection conn = viaProxy ? getProxyConnection(url) : getConnection(url);

        int responseCode;
        try {
            responseCode = conn.getResponseCode();
        } catch (IOException connectFailure) {
            // no response was ever produced, so there is nothing to keep alive - release the socket now
            // instead of leaving it to the JDK's cleanup
            conn.disconnect();
            throw connectFailure;
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            // Every non-200 response still carries a body (or at least an error stream), and an
            // unread stream pins the socket until it times out.  Consuming it here - a single exit
            // point for all error paths - is what keeps the connection reusable for the next lookup.
            drainQuietly(conn);
        }

        if (responseCode == RateLimitException.RATE_LIMIT_RESPONSE_CODE) {
            if (viaProxy) {
                // the proxy is limited as well - there is nothing left to try
                throw new RateLimitException(name);
            }

            // a proxy uses a different outgoing IP, so the very same request may succeed there
            return queryProfile(name, url, true, viaBackupEndpoint);
        }

        if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
            if (viaBackupEndpoint) {
                throw new IOException("Both Mojang APIs returned 403 Forbidden");
            }

            // known misconfiguration of the primary endpoint (WEB-7591 / WEB-7666) - keep using
            // the backup endpoint for this resolver instance (volatile: other threads read it)
            useBackupUuidUrl = true;
            return queryProfile(name, backupUuidUrl + name, viaProxy, true);
        }

        if (responseCode == HttpURLConnection.HTTP_NO_CONTENT
                || responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            // 204 means "no such premium player", 404 is the same answer of the newer API
            return Optional.empty();
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Unexpected response code " + responseCode + " for " + url);
        }

        //todo: print errorstream on IOException
        // parseRequest consumes and closes the stream (and drains the error stream if the read fails),
        // which is the same contract the non-200 branches above rely on
        Profile profile = parseRequest(conn, in -> readJson(in, Profile.class));
        if (profile == null || profile.getName() == null || profile.getId() == null) {
            // a malformed 200 must never be mistaken for a premium profile
            throw new IOException("Malformed profile response for " + name);
        }

        cache.add(profile);
        return Optional.of(profile);
    }

    @Override
    public Optional<Profile> findProfile(String name, Instant time) throws IOException, RateLimitException {
        Optional<Profile> optProfile = cache.getByName(name);
        if (optProfile.isPresent() || !validNamePredicate.test(name)) {
            return optProfile;
        }

        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional<SkinProperty> downloadSkin(UUID uuid) throws IOException, RateLimitException {
        Optional<SkinProperty> optSkin = cache.getSkin(uuid);
        if (optSkin.isPresent()) {
            return optSkin;
        }

        String url = String.format(SKIN_URL, UUIDAdapter.toMojangId(uuid));
        HttpURLConnection conn = getConnection(url);

        int responseCode = conn.getResponseCode();
        if (responseCode == RateLimitException.RATE_LIMIT_RESPONSE_CODE) {
            discard(conn);
            throw new RateLimitException();
        }

        if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
            discard(conn);
            return Optional.empty();
        }

        Textures texturesModel = parseRequest(conn, in -> readJson(in, Textures.class));
        SkinProperty property = texturesModel.getProperties()[0];

        cache.addSkin(uuid, property);
        return Optional.of(property);
    }

    /**
     * Consumes and closes the response of the given connection, ignoring what it contains.  A non-200
     * response has no input stream, so {@link AbstractResolver#discard(HttpURLConnection)} raises - but it
     * drains the error stream before it does, and that is all this call is after.
     *
     * @param conn connection whose response has not been read yet
     */
    private void drainQuietly(HttpURLConnection conn) {
        try {
            discard(conn);
        } catch (IOException errorResponse) {
            // expected for every error status: the error stream has been drained on the way out and the
            // status code already tells us what happened
        }
    }

    private HttpURLConnection getProxyConnection(String url) throws RateLimitException, IOException {
        List<Proxy> selectedProxies = proxySelector.select(URI.create(url));
        if (selectedProxies.isEmpty() || selectedProxies.get(0).type() == Type.DIRECT) {
            // no proxy configured or the selector resolved to a direct connection
            throw new RateLimitException();
        }

        // the complete url - including the requested player name - has to be forwarded to the proxy
        return getConnection(url, selectedProxies.get(0));
    }

    /**
     * @return maximum amount of name to UUID requests that will be established to Mojang directly without proxies.
     * (Between 0 and 600 within 10 minutes). Default is 600.
     */
    public int getMaxNameRequests() {
        return maxNameRequests;
    }

    /**
     * @return the current proxy selector or {@link ProxySelector#getDefault()} if none
     */
    public ProxySelector getProxySelector() {
        return proxySelector;
    }

    /**
     * @param proxySelector proxy selector that should be used
     */
    public void setProxySelector(ProxySelector proxySelector) {
        this.proxySelector = proxySelector;
    }

    /**
     * @param maxNameRequests maximum amount of name to UUID requests that will be established to Mojang directly
     *                        without proxies. (Between 0 and 600 within 10 minutes). A value of 0 means that all
     *                        lookups have to go through a configured proxy.
     */
    public void setMaxNameRequests(int maxNameRequests) {
        // clamp into the valid range: the old implementation used Math.max(600, value), which turned
        // every configured value - including smaller ones - into the hard limit of 600
        int clampedRequests = Math.max(0, Math.min(MAX_NAME_REQUESTS_LIMIT, maxNameRequests));
        if (clampedRequests != this.maxNameRequests) {
            this.maxNameRequests = clampedRequests;
            // the limit is baked into the sliding window, so the limiter has to be rebuilt
            this.profileLimiter = new TickingRateLimiter(
                    Ticker.systemTicker(), clampedRequests,
                    TimeUnit.MINUTES.toMillis(10)
            );
        }
    }
}
