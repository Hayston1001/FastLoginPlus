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

import com.github.games647.craftapi.InstantAdapter;
import com.github.games647.craftapi.NamePredicate;
import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.craftapi.cache.Cache;
import com.github.games647.craftapi.cache.MemoryCache;
import com.github.games647.craftapi.model.skin.Skin;
import com.github.games647.craftapi.model.skin.SkinProperty;
import com.github.games647.craftapi.resolver.http.RotatingSourceFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Predicate;

import javax.net.ssl.HttpsURLConnection;

/**
 * Base class for fetching Minecraft related data.
 */
public abstract class AbstractResolver {

    private static final int TIMEOUT = 10_000;
    private static final String USER_AGENT = "CraftAPIClient";

    // skip only maximum amount to prevent too long transfers that will be just discarded
    private static final int MAX_SKIP = 8 * 1024;

    protected final Predicate<String> validNamePredicate = new NamePredicate();
    protected RotatingSourceFactory sslFactory;

    protected Cache cache = new MemoryCache();

    protected final Gson gson = new GsonBuilder()
            .registerTypeAdapter(UUID.class, new UUIDAdapter())
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    /**
     * Decodes the property from a skin request.
     *
     * @param property Base64 encoded skin property
     * @return decoded model
     */
    public Skin decodeSkin(SkinProperty property) {
        byte[] data = Base64.getDecoder().decode(property.getValue());
        String json = new String(data, StandardCharsets.UTF_8);

        Skin skinModel = gson.fromJson(json, Skin.class);
        skinModel.setSignature(Base64.getDecoder().decode(property.getSignature()));
        return skinModel;
    }

    /**
     * Decodes the skin for setting it in game.
     *
     * @param skinModel decoded skin model with signature
     * @return Base64 encoded skin property
     */
    public SkinProperty encodeSkin(Skin skinModel) {
        String json = gson.toJson(skinModel);

        String encodedValue = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = Base64.getEncoder().encodeToString(skinModel.getSignature());
        return new SkinProperty(encodedValue, encodedSignature);
    }

    /**
     * Buffers and parses all data in the input stream and closes it after it.
     *
     * @param inputStream unbuffered input stream
     * @param classOfT the class that should be parsed to
     * @param <T> type of the given class
     * @return the parsed representation
     * @throws IOException if an error occurs while reading the stream
     */
    protected <T> T readJson(InputStream inputStream, Class<T> classOfT) throws IOException {
        return readJson(new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)), classOfT);
    }

    /**
     * Parses all data in the input stream and closes it after it.
     *
     * @param reader data reader
     * @param classOfT the class that should be parsed to
     * @param <T> type of the given class
     * @return the parsed representation
     * @throws IOException if an error occurs while reading the stream
     */
    protected <T> T readJson(Reader reader, Class<T> classOfT) throws IOException {
        try {
            return gson.fromJson(reader, classOfT);
        } catch (JsonParseException malformedJson) {
            // a broken response body is an I/O failure for all callers - without this it would escape
            // as an unchecked JsonSyntaxException and could take the login flow down
            throw new IOException("Malformed JSON response: " + malformedJson.getMessage(), malformedJson);
        } finally {
            reader.close();
        }
    }

    /**
     * Create a new HTTPConnection with default timeout and HTTP-Headers for json accept and user-agent.
     *
     * @param url the complete url
     * @return an unestablished HTTPConnection
     * @throws IOException I/O exception on opening the data channel
     */
    protected HttpURLConnection getConnection(String url) throws IOException {
        return getConnection(url, Proxy.NO_PROXY);
    }

    /**
     * Create a new HTTPConnection with default timeout and HTTP-Headers for json accept and user-agent.
     *
     * @param url http connection URL
     * @param proxy HTTP or SOCKS proxy through this connection
     * @return an unestablished HTTPConnection
     * @throws IOException I/O exception on opening the data channel
     */
    protected HttpURLConnection getConnection(String url, Proxy proxy) throws IOException {
        // HttpURLConnection only: HttpsURLConnection cannot represent the plain-HTTP endpoints used by
        // the offline tests.  The SSL factory is installed below for HTTPS connections only.
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection(proxy);

        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(2 * TIMEOUT);

        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", USER_AGENT);

        // do not set factory if not necessary, because it could help to keep the connection alive
        if (sslFactory != null && conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(sslFactory);
        }
        return conn;
    }

    /**
     * Reads the input stream and error stream, but discards all data collected. This is necessary to make the
     * connection available for further requests using HTTP keep-alives.
     *
     * @param conn http connection
     * @throws IOException io exception on reading
     */
    protected void discard(HttpURLConnection conn) throws IOException {
        parseRequest(conn, in -> in.skip(MAX_SKIP));
    }

    /**
     * Consume input stream and discards every data in error stream if available. In order that HTTP connections can be
     * re-used (keep-alive), all output streams should be fully written and closed as well as all input streams
     * fully consumed and closed ({@link HttpURLConnection#getInputStream()} and
     * {@link HttpURLConnection#getErrorStream()}).
     * <p>
     * By default, Java will consume part of the input stream if not fully read, but it doesn't apply to error streams
     * and the connection still have to be closed nevertheless. Furthermore, a connection implicitly opened by
     * {@link HttpURLConnection#getResponseCode()} or {@link HttpURLConnection#getOutputStream()}} should also be
     * consumed and closed by this.
     *
     * @param conn http connection
     * @param consumer consumer that should fully read the input
     * @param <R> return type of the consumer
     * @return return value of the consumer
     * @throws IOException on reading the original input stream
     * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html">
     *     Peristent Connections
     *     </a>
     */
    protected <R> R parseRequest(HttpURLConnection conn, InputStreamAction<R> consumer) throws IOException {
        // execute-around pattern
        try (InputStream in = conn.getInputStream()) {
            // should read fully
            return consumer.useStream(in);
        } catch (IOException ioEx) {
            // error stream is only created on opening getInputStream while encountering an IOException
            try (InputStream errStream = conn.getErrorStream()) {
                // can be null if there is no content
                if (errStream != null) {
                    // should read fully
                    errStream.skip(MAX_SKIP);
                }
            } catch (IOException errIoEx) {
                // re-throw only the original exception, because we ignore the content of error stream completely
            }

            throw ioEx;
        }
    }

    /**
     * @return the current cache backend.
     */
    public Cache getCache() {
        return cache;
    }

    /**
     * Sets a new Mojang cache.
     *
     * @param cache cache implementation
     */
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    /**
     * Set the outgoing addresses. The rotating order will be the same as in the given collection.
     *
     * @param addresses all outgoing IPv4 addresses that are available or empty to disable it.
     */
    public void setOutgoingAddresses(Collection<InetAddress> addresses) {
        if (addresses.isEmpty()) {
            sslFactory = null;
            return;
        }

        if (sslFactory == null) {
            sslFactory = new RotatingSourceFactory();
        }

        sslFactory.setOutgoingAddresses(addresses);
    }

    @FunctionalInterface
    protected interface InputStreamAction<R> {
        R useStream(InputStream stream) throws IOException;
    }
}
