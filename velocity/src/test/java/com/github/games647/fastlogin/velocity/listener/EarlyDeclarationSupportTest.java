/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.velocity.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EarlyDeclarationSupportTest {
    @Test
    void publicApiProbeSucceedsWithoutWarning() {
        Logger log = mock(Logger.class);
        EarlyDeclarationSupport support = new EarlyDeclarationSupport(log);
        Runnable injection = mock(Runnable.class);
        for (EarlyDeclarationSupport.FailureKind kind : EarlyDeclarationSupport.FailureKind.values()) {
            support.attempt(kind, injection);
        }
        verify(injection, times(2)).run();
        verifyNoInteractions(log);
    }

    @Test
    void startupProbeExplainsBothAffectedUsesWithoutConsumingRuntimeWarningBudget() {
        Logger log = mock(Logger.class);
        EarlyDeclarationSupport support = new EarlyDeclarationSupport(Object.class, Object.class, Object.class, log);
        ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
        verify(log).warn(warning.capture(), any(Throwable.class));
        assertTrue(warning.getValue().startsWith("This proxy version cannot carry early identity declarations."));
        assertTrue(warning.getValue().contains("Early premium login preparation"));
        assertTrue(warning.getValue().contains("early rejection of unverified connections"));
        assertTrue(warning.getValue().contains("Paper + AuthMe 6"));
        assertTrue(warning.getValue().contains("Late declarations remain enabled; proxy admission is unchanged."));
        Runnable failed = () -> { throw new LinkageError("injected incompatible event API"); };
        for (EarlyDeclarationSupport.FailureKind kind : EarlyDeclarationSupport.FailureKind.values()) {
            support.attempt(kind, failed);
            support.attempt(kind, failed);
        }
        verify(log).warn(startsWith("First early premium claim injection failure."), any(Throwable.class));
        verify(log).warn(startsWith("First early unverified declaration injection failure."), any(Throwable.class));
        verifyNoMoreInteractions(log);
    }

    @ParameterizedTest
    @EnumSource(EarlyDeclarationSupport.FailureKind.class)
    void runtimeWarningsHaveIndependentOnceGuardsAndCategorySpecificConsequences(
            EarlyDeclarationSupport.FailureKind first) {
        Logger log = mock(Logger.class);
        EarlyDeclarationSupport support = new EarlyDeclarationSupport(log);
        EarlyDeclarationSupport.FailureKind second = first == EarlyDeclarationSupport.FailureKind.PREMIUM_CLAIM
                ? EarlyDeclarationSupport.FailureKind.UNVERIFIED_DECLARATION
                : EarlyDeclarationSupport.FailureKind.PREMIUM_CLAIM;
        Runnable injection = mock(Runnable.class);
        doThrow(new IllegalStateException("injected profile API failure")).when(injection).run();
        support.attempt(first, injection);
        support.attempt(first, injection);
        support.attempt(second, injection);
        support.attempt(second, injection);
        support.attempt(first, injection);
        verify(injection, times(5)).run(); // Suppress duplicate logs, never the injection attempt.
        ArgumentCaptor<String> warnings = ArgumentCaptor.forClass(String.class);
        verify(log, times(2)).warn(warnings.capture(), any(Throwable.class));
        String premium = warnings.getAllValues().stream().filter(value -> value.startsWith(
                "First early premium claim injection failure.")).findFirst().orElseThrow(AssertionError::new);
        String unverified = warnings.getAllValues().stream().filter(value -> value.startsWith(
                "First early unverified declaration injection failure.")).findFirst().orElseThrow(AssertionError::new);
        assertTrue(premium.contains("Early premium claim could not be attached"));
        assertTrue(premium.contains("AuthMe 6 pre-join dialogs may block verified players"));
        assertTrue(premium.contains("until a late message can complete login"));
        assertTrue(premium.contains("The late declaration and proxy admission are unchanged."));
        assertFalse(premium.contains("can only be rejected after joining"));
        assertTrue(unverified.contains("Unverified connections onto premium-owned accounts can only be rejected after joining"));
        assertTrue(unverified.contains("waiting until timeout"));
        assertTrue(unverified.contains("Late declarations remain enabled; proxy admission is unchanged."));
        assertFalse(unverified.contains("may block verified players"));
        verifyNoMoreInteractions(log);
    }
}
