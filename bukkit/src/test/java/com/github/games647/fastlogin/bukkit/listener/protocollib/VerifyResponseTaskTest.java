/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.listener.protocollib;

import com.comphenix.protocol.injector.packet.PacketRegistry;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.utility.MinecraftReflection;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class VerifyResponseTaskTest {

    private static final String NETTY_INJECTOR_CLASS =
            "com.comphenix.protocol.injector.netty.channel.NettyChannelInjector";

    @Test
    void getNetworkManagerReflection() throws ClassNotFoundException {
        try (
                MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class);
                MockedStatic<MinecraftReflection> reflectionMock = mockStatic(MinecraftReflection.class);
                MockedStatic<PacketRegistry> registryMock = mockStatic(PacketRegistry.class)
        ) {
            bukkitMock.when(Bukkit::getVersion).thenReturn("git-Bukkit-18fbb24 (MC: 1.17)");
            reflectionMock.when(MinecraftReflection::getMinecraftPackage).thenReturn("xyz");
            reflectionMock.when(MinecraftReflection::getEnumProtocolClass).thenReturn(Object.class);

            registryMock.when(() -> PacketRegistry.tryGetPacketClass(any())).thenReturn(Optional.empty());
            
            
            Class<?> injectorClass = Class.forName(NETTY_INJECTOR_CLASS);
            FieldAccessor accessor = Accessors.getFieldAccessorOrNull(injectorClass, "networkManager", Object.class);
            assertNotNull(accessor.getField());
        }
    }
}
