/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bungee.task;

import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class AsyncToggleMessageTest {

    @Test
    void saveFailureReportsDatabaseErrorInsteadOfClaimingPremium() throws Exception {
        FastLoginCore core = mock(FastLoginCore.class);
        FastLoginBungee plugin = mock(FastLoginBungee.class);
        SQLStorage storage = mock(SQLStorage.class);
        MojangResolver resolver = mock(MojangResolver.class);
        ProxiedPlayer sender = mock(ProxiedPlayer.class);
        StoredProfile profile = mock(StoredProfile.class);

        when(core.getStorage()).thenReturn(storage);
        when(core.getPlugin()).thenReturn(plugin);
        when(core.getResolver()).thenReturn(resolver);
        when(core.isDebug()).thenReturn(false);
        when(core.getMessage("database-error")).thenReturn("database error");
        when(sender.getName()).thenReturn("TestUser");
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(profile.isOnlinemodePreferred()).thenReturn(false);
        when(resolver.findProfile("TestUser")).thenReturn(Optional.empty());
        when(storage.saveQuietly(profile)).thenReturn(false);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(storage).withNameLock(eq("TestUser"), any(Runnable.class));

        new AsyncToggleMessage(core, sender, "TestUser", true, true).run();

        verify(storage).saveQuietly(profile);
        ArgumentCaptor<BaseComponent[]> message = ArgumentCaptor.forClass(BaseComponent[].class);
        verify(sender).sendMessage(message.capture());
        assertEquals("database error", BaseComponent.toPlainText(message.getValue()));
        verify(core, never()).getMessage("add-premium");
        verify(core, never()).getConfig();
        verifyNoInteractions(plugin);
    }

    @Test
    void disableSaveFailureReportsDatabaseErrorWithoutKickOrEvent() throws Exception {
        FastLoginCore core = mock(FastLoginCore.class);
        FastLoginBungee plugin = mock(FastLoginBungee.class);
        SQLStorage storage = mock(SQLStorage.class);
        ProxiedPlayer sender = mock(ProxiedPlayer.class);
        StoredProfile profile = mock(StoredProfile.class);

        when(core.getStorage()).thenReturn(storage);
        when(core.getPlugin()).thenReturn(plugin);
        when(core.isDebug()).thenReturn(false);
        when(core.getMessage("database-error")).thenReturn("database error");
        when(sender.getName()).thenReturn("TestUser");
        when(storage.loadProfile("TestUser")).thenReturn(profile);
        when(profile.isExistingPlayer()).thenReturn(true);
        when(profile.isOnlinemodePreferred()).thenReturn(false);
        when(profile.isSameNameCracked()).thenReturn(true);
        when(storage.saveQuietly(profile)).thenReturn(false);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(storage).withNameLock(eq("TestUser"), any(Runnable.class));

        new AsyncToggleMessage(core, sender, "TestUser", false, true).run();

        verify(storage).saveQuietly(profile);
        ArgumentCaptor<BaseComponent[]> message = ArgumentCaptor.forClass(BaseComponent[].class);
        verify(sender).sendMessage(message.capture());
        assertEquals("database error", BaseComponent.toPlainText(message.getValue()));
        verify(core, never()).getMessage("remove-premium");
        verify(core, never()).getConfig();
        verifyNoInteractions(plugin);
    }
}
