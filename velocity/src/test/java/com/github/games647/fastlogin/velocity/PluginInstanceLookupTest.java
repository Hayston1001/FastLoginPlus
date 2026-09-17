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
package com.github.games647.fastlogin.velocity;

import java.lang.reflect.Proxy;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 0.7.0/F18: {@code PluginContainer#getInstance()} returns an {@link Optional}, so the lookup has
 * to be flattened. Using {@code map} handed {@code AuthMeProxyPin} the {@code Optional} wrapper
 * itself, which made the reflective pin report {@code UNSUPPORTED} on every Velocity startup and
 * fall back to rewriting AuthMe's config file. These assertions pin the flattened form.
 */
class PluginInstanceLookupTest {

    private static final Object PLUGIN_INSTANCE = new Object();

    @Test
    void returnsThePluginInstanceRatherThanTheOptionalWrapper() {
        PluginManager pluginManager = pluginManager(Optional.of(container(Optional.of(PLUGIN_INSTANCE))));

        assertSame(PLUGIN_INSTANCE,
                FastLoginVelocity.resolvePluginInstance(pluginManager, "authmevelocity"));
    }

    @Test
    void returnsNullWhenPluginIsAbsent() {
        assertNull(FastLoginVelocity.resolvePluginInstance(pluginManager(Optional.empty()), "authmevelocity"));
    }

    @Test
    void returnsNullWhenPluginHasNoInstanceYet() {
        PluginManager pluginManager = pluginManager(Optional.of(container(Optional.empty())));

        assertNull(FastLoginVelocity.resolvePluginInstance(pluginManager, "authmevelocity"));
    }

    private static PluginManager pluginManager(Optional<PluginContainer> lookupResult) {
        return (PluginManager) Proxy.newProxyInstance(PluginInstanceLookupTest.class.getClassLoader(),
                new Class<?>[]{PluginManager.class}, (proxy, method, args) -> {
                    if ("getPlugin".equals(method.getName())) {
                        return lookupResult;
                    }
                    return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });
    }

    private static PluginContainer container(Optional<?> instance) {
        return (PluginContainer) Proxy.newProxyInstance(PluginInstanceLookupTest.class.getClassLoader(),
                new Class<?>[]{PluginContainer.class}, (proxy, method, args)
                        -> "getInstance".equals(method.getName()) ? instance : null);
    }
}
