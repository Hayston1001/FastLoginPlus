/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package fr.xephi.authme.libs.ch.jalu.injector;

/**
 * Stand-in for a relocated {@code ch.jalu.injector.Injector} interface.
 *
 * <p>Exists only so the field-type probe can be exercised against a type whose name carries a
 * relocation prefix, which is how some AuthMe forks ship their bundled libraries.
 */
public interface Injector {

    Object getSingleton(Class<?> type);
}
