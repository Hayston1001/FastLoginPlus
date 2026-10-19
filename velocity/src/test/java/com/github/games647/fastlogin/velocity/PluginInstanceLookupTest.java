/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
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
 * {@code PluginContainer#getInstance()} returns an {@link Optional}, so the lookup has
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
