/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
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
package com.github.games647.fastlogin.core.shared;

import java.net.InetSocketAddress;

public interface LoginSource {

    void enableOnlinemode() throws Exception;

    void kick(String message);

    InetSocketAddress getAddress();

    /**
     * Returns true if {@link #kick(String)} was called on this source.
     * Used by the caller (e.g. NameCheckTask) to know whether to cancel
     * the intercepted packet instead of forwarding it to the vanilla server.
     *
     * <p>Default implementation returns {@code false}. Only ProtocolLib-based
     * sources need to override this, because they intercept packets at the
     * Netty level and must explicitly cancel the START packet after a kick
     * to prevent the vanilla server from sending its own disconnect message.
     *
     * @return true if kick was called
     */
    default boolean isKicked() {
        return false;
    }
}
