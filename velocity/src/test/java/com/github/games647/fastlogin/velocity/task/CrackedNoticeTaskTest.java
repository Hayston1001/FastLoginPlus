/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.task;

import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.velocity.FastLoginVelocity;
import com.github.games647.fastlogin.velocity.VelocityLoginSession;
import com.github.games647.fastlogin.velocity.listener.CrackedNoticeDelivery;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class CrackedNoticeTaskTest {
    @Test
    void offlineJoinDelegatesNoticeOwnershipWithoutConsumingOrSendingItTwice() {
        FastLoginCore core = mock(FastLoginCore.class);
        FastLoginVelocity plugin = mock(FastLoginVelocity.class);
        SQLStorage storage = mock(SQLStorage.class);
        Player player = mock(Player.class);
        StoredProfile profile = mock(StoredProfile.class);
        CrackedNoticeDelivery delivery = mock(CrackedNoticeDelivery.class);
        RegisteredServer server = mock(RegisteredServer.class);
        when(core.getPlugin()).thenReturn(plugin);
        when(core.getStorage()).thenReturn(storage);
        when(plugin.getCrackedNoticeDelivery()).thenReturn(delivery);
        when(player.isActive()).thenReturn(true);
        when(player.getUsername()).thenReturn("TestUser");
        when(profile.isSameNameCracked()).thenReturn(true);
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(1)).run(); return null; })
                .when(storage).withNameLock(eq("TestUser"), any(Runnable.class));
        VelocityLoginSession session = new VelocityLoginSession("TestUser", false, profile);
        new ForceLoginTask(core, player, server, session).run();
        verify(storage).save(profile);
        verify(delivery).deliver(player, server);
        verify(core, never()).consumeSameNameCrackedNotice(anyString());
        verify(core, never()).sendLocaleMessage(anyString(), any());
        verify(plugin, never()).sendPluginMessage(any(), any());
    }
}
