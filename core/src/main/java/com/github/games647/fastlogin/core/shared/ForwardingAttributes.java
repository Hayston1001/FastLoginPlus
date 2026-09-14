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
package com.github.games647.fastlogin.core.shared;

/**
 * Name of the custom GameProfile property the proxy uses to hand the verified Mojang UUID to
 * the backend.
 *
 * <p>0.7.0/F13. With {@code premiumUuid: false} the proxy rewrites the forwarded UUID to the
 * offline one, so the backend cannot tell a verified premium connection from a cracked one and
 * AuthMe shows its preJoin dialog on the very first login. Rather than shipping the value in a
 * separate plugin message — which cannot reach the backend before the configuration phase (see
 * the F10 experiment) — the proxy attaches it as a property on the GameProfile it is already
 * rewriting, riding Velocity's modern player-information forwarding. The backend reads it back
 * in the configuration phase, before any dialog exists.</p>
 *
 * <p>Trust boundary: the forwarding handshake's HMAC protects the payload in transit —
 * nothing between proxy and backend can tamper with it — but the authority it certifies is
 * <em>the proxy process as a whole</em>, not this plugin: any code running on the proxy can
 * inject the same property. The backend therefore reads this as "the proxy attests", never
 * "FLP attests". Note also that the capability this property enables is not new: the
 * backend's configure-phase auto-register already trusted the payload's connection UUID
 * (a holder of the forwarding secret could set it to the victim's public Mojang UUID and
 * pass the existing equality guard), so the property grants no power the UUID field did not
 * already carry. Keeping that boundary honest is why the property carries no second
 * signature scheme — one that would not close the pre-existing path anyway.
 * BungeeCord has no equivalent injection point, so it keeps using the F10 plugin message and
 * therefore still shows the dialog on the first login.</p>
 */
public final class ForwardingAttributes {

    /**
     * Carries the Mojang UUID the proxy verified for this connection. Written only when the
     * connection actually was verified as premium; absent otherwise (cracked, Floodgate).
     */
    public static final String PREMIUM_UUID = "flp-premium-uuid";

    private ForwardingAttributes() {
    }
}
