/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the tolerance for AuthMe builds that relocate their bundled libraries.
 *
 * <p>The reflection probes originally compared the exact upstream class name, so a build shipping
 * {@code fr.xephi.authme.libs.ch.jalu.*} made the injector undiscoverable and every premium action
 * fail closed. These tests keep the lookup narrow: the upstream name still wins, an unrelated name
 * is still rejected, and only a name that carries the full upstream name as a suffix is accepted.
 */
class AuthMeRelocationTest {

    private static final String INJECTOR = "ch.jalu.injector.Injector";
    private static final String RELOCATED_INJECTOR =
            "fr.xephi.authme.libs.ch.jalu.injector.Injector";
    private static final String RELOCATED_PROPERTY =
            "fr.xephi.authme.libs.ch.jalu.configme.properties.Property";

    @Test
    void upstreamTypeNameIsAccepted() {
        assertTrue(AuthMePremiumIntegrator.isJaluType(INJECTOR, INJECTOR));
    }

    @Test
    void relocatedTypeNameIsAccepted() {
        assertTrue(AuthMePremiumIntegrator.isJaluType(RELOCATED_INJECTOR, INJECTOR));
    }

    @Test
    void unrelatedTypeNameIsRejected() {
        assertFalse(AuthMePremiumIntegrator.isJaluType("com.example.injector.Injector", INJECTOR));
        assertFalse(AuthMePremiumIntegrator.isJaluType("ch.jalu.injector.InjectorX", INJECTOR));
        assertFalse(AuthMePremiumIntegrator.isJaluType(null, INJECTOR));
    }

    @Test
    void injectorFieldWithRelocatedTypeIsFound() throws Exception {
        fr.xephi.authme.libs.ch.jalu.injector.Injector injector = type -> null;

        assertSame(injector, AuthMePremiumIntegrator.findInjector(new RelocatedInjectorHolder(injector)));
    }

    @Test
    void upstreamClassWinsOverARelocatedCandidate() throws Exception {
        assertSame(String.class, AuthMePremiumIntegrator.resolveRelocated(
                "java.lang.String", RELOCATED_PROPERTY));
    }

    @Test
    void relocatedClassResolvesWhenTheOriginalIsAbsent() throws Exception {
        Class<?> resolved = AuthMePremiumIntegrator.resolveRelocated(
                "com.example.missing.Property", RELOCATED_PROPERTY);

        assertEquals(RELOCATED_PROPERTY, resolved.getName());
    }

    @Test
    void unresolvedClassReportsTheOriginalFailure() {
        ClassNotFoundException failure = assertThrows(ClassNotFoundException.class,
                () -> AuthMePremiumIntegrator.resolveRelocated(
                        "com.example.missing.Property", "com.example.missing.Other"));

        assertEquals("com.example.missing.Property", failure.getMessage());
    }

    /** A plugin-like object whose injector field carries the relocated library type. */
    static final class RelocatedInjectorHolder {

        private final fr.xephi.authme.libs.ch.jalu.injector.Injector injector;

        RelocatedInjectorHolder(fr.xephi.authme.libs.ch.jalu.injector.Injector injector) {
            this.injector = injector;
        }
    }
}