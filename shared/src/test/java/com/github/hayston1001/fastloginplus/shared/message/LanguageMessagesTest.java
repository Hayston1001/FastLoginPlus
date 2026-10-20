/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.shared.message;

import com.github.hayston1001.fastloginplus.shared.config.ConfigurationFiles.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LanguageMessagesTest {
    private static final String DEFAULTS = "defaults";
    private static final String BUNDLED = "bundled";

    @TempDir
    Path directory;

    @Test
    void successfulOverrideDoesNotInvokeFailureCallbackOrChangeFile() throws Exception {
        Path file = userFile();
        byte[] original = Files.readAllBytes(file);
        Loader<String> loader = loader();
        when(loader.load(any(Reader.class), eq(DEFAULTS))).thenReturn("override");
        Consumer<Throwable> callback = callback();

        LanguageMessages.Result<String> result = LanguageMessages.loadWithFallback(
                file, DEFAULTS, BUNDLED, loader, callback);

        assertSame("override", result.getValue());
        assertFalse(result.isFallback());
        verifyNoInteractions(callback);
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void missingOverrideUsesDefaultsWithoutReportingFailureOrCreatingFile() throws Exception {
        Path file = directory.resolve("missing");
        Loader<String> loader = loader();
        Consumer<Throwable> callback = callback();

        LanguageMessages.Result<String> result = LanguageMessages.loadWithFallback(
                file, DEFAULTS, BUNDLED, loader, callback);

        assertSame(DEFAULTS, result.getValue());
        assertFalse(result.isFallback());
        assertFalse(Files.exists(file));
        verify(loader, never()).load(any(Reader.class), any());
        verifyNoInteractions(callback);
    }

    @Test
    void checkedReadFailureReturnsBundledValueAndReportsOriginalFailureOnce() throws Exception {
        assertFallback(new IOException("unreadable override"));
    }

    @Test
    void runtimeParserFailureReturnsBundledValueAndReportsOriginalFailureOnce() throws Exception {
        assertFallback(new IllegalArgumentException("invalid override"));
    }

    @Test
    void defaultKeyPopulationFailureAlsoUsesBundledFallback() throws Exception {
        Loader<String> loader = loader();
        when(loader.load(any(Reader.class), eq(DEFAULTS))).thenReturn("override");
        RuntimeException failure = new IllegalStateException("invalid keys");
        when(loader.getKeys(DEFAULTS)).thenThrow(failure);
        Consumer<Throwable> callback = callback();

        LanguageMessages.Result<String> result = LanguageMessages.loadWithFallback(
                userFile(), DEFAULTS, BUNDLED, loader, callback);

        assertSame(BUNDLED, result.getValue());
        assertTrue(result.isFallback());
        verify(callback).accept(failure);
        verifyNoMoreInteractions(callback);
    }

    @Test
    void callbackRuntimeFailurePropagatesWithoutBeingRetried() throws Exception {
        assertCallbackFailure(new IllegalStateException("logger failed"));
    }

    @Test
    void callbackErrorPropagatesWithoutBeingRetried() throws Exception {
        assertCallbackFailure(new AssertionError("callback failed"));
    }

    @Test
    void parserErrorIsNotConvertedToFallback() throws Exception {
        Path file = userFile();
        Loader<String> loader = loader();
        AssertionError failure = new AssertionError("fatal parser failure");
        when(loader.load(any(Reader.class), eq(DEFAULTS))).thenThrow(failure);
        Consumer<Throwable> callback = callback();

        assertSame(failure, assertThrows(AssertionError.class, () -> LanguageMessages.loadWithFallback(
                file, DEFAULTS, BUNDLED, loader, callback)));
        verifyNoInteractions(callback);
    }

    @Test
    void nullCallbackIsRejectedBeforeLoadingEvenIfFileIsMissing() {
        Loader<String> loader = loader();

        assertThrows(NullPointerException.class, () -> LanguageMessages.loadWithFallback(
                directory.resolve("missing"), DEFAULTS, BUNDLED, loader, null));
        verifyNoInteractions(loader);
    }

    private void assertFallback(Exception failure) throws Exception {
        Path file = userFile();
        byte[] original = Files.readAllBytes(file);
        Loader<String> loader = loader();
        when(loader.load(any(Reader.class), eq(DEFAULTS))).thenThrow(failure);
        Consumer<Throwable> callback = callback();

        LanguageMessages.Result<String> result = LanguageMessages.loadWithFallback(
                file, DEFAULTS, BUNDLED, loader, callback);

        assertSame(BUNDLED, result.getValue());
        assertTrue(result.isFallback());
        verify(callback).accept(failure);
        verifyNoMoreInteractions(callback);
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    private void assertCallbackFailure(Throwable callbackFailure) throws Exception {
        Path file = userFile();
        Loader<String> loader = loader();
        IOException readFailure = new IOException("unreadable override");
        when(loader.load(any(Reader.class), eq(DEFAULTS))).thenThrow(readFailure);
        Consumer<Throwable> callback = callback();
        org.mockito.Mockito.doThrow(callbackFailure).when(callback).accept(readFailure);

        assertSame(callbackFailure, assertThrows(callbackFailure.getClass(), () -> LanguageMessages.loadWithFallback(
                file, DEFAULTS, BUNDLED, loader, callback)));
        verify(callback).accept(readFailure);
        verifyNoMoreInteractions(callback);
    }

    private Path userFile() throws IOException {
        Path file = directory.resolve("override");
        Files.write(file, "original override\n".getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @SuppressWarnings("unchecked")
    private Loader<String> loader() {
        Loader<String> loader = mock(Loader.class);
        when(loader.getKeys(DEFAULTS)).thenReturn(Collections.emptyList());
        return loader;
    }

    @SuppressWarnings("unchecked")
    private Consumer<Throwable> callback() {
        return mock(Consumer.class);
    }
}
