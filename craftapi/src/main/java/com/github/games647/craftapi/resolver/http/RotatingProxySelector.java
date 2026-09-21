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
package com.github.games647.craftapi.resolver.http;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Proxy selector that rotates through the given collection.
 */
public class RotatingProxySelector extends ProxySelector {

    private final ProxySelector defaultSelector;
    private final Iterator<Proxy> proxies;

    /**
     * Creates a new proxy selector
     *
     * @param proxies all HTTP or SOCKS proxies
     * @param oldSelector selector for connection failed callback
     */
    public RotatingProxySelector(Iterable<Proxy> proxies, ProxySelector oldSelector) {
        Set<Proxy> copy = ImmutableSet.copyOf(proxies);

        this.defaultSelector = oldSelector;
        this.proxies = Iterables.cycle(copy).iterator();
    }

    /**
     * Creates a new proxy selector using {@link ProxySelector#getDefault()} for connection failed delegation
     *
     * @param proxies all HTTP or SOCKS proxies
     */
    public RotatingProxySelector(Iterable<Proxy> proxies) {
        this(proxies, ProxySelector.getDefault());
    }

    @Override
    public List<Proxy> select(URI uri) {
        synchronized (proxies) {
            if (proxies.hasNext()) {
                return Collections.singletonList(proxies.next());
            }

            return Collections.singletonList(Proxy.NO_PROXY);
        }
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        defaultSelector.connectFailed(uri, sa, ioe);
    }
}
