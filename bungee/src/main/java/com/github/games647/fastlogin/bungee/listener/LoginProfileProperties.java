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
package com.github.games647.fastlogin.bungee.listener;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.function.Consumer;

/**
 * Shape-independent access to the property array of a BungeeCord login profile
 * ({@code net.md_5.bungee.connection.LoginResult}).
 *
 * <p><b>Why this is reflective instead of a typed call.</b> BungeeCord #3855 (2025-07-14,
 * "Create protocol sub-packages data &amp; util") moved the element type of that array from
 * {@code net.md_5.bungee.protocol.Property} to {@code net.md_5.bungee.protocol.data.Property}
 * while this module compiles against a 2024 artifact. A call site compiled against the old
 * shape therefore fails to link on every proxy build released since — and, because the old
 * reference sat in a field initialiser, the failure was not limited to the feature that used
 * it: constructing the listener threw {@code NoClassDefFoundError} and took the whole plugin
 * down. Both shapes are still in the wild (Waterfall and pre-2025 BungeeCord
 * builds have the old one), so neither a typed old call nor a typed new call is correct.</p>
 *
 * <p>Everything here goes through the {@code properties} field: its declared type tells us
 * which {@code Property} class this build uses, and the replacement array is built from that
 * type. Property instances are created with the two-argument constructor, which both shapes
 * provide (the signature stays unset, which is what an unsigned property means).</p>
 *
 * <p>Every entry point reports failure through the given reporter instead of throwing: the
 * property list is an optimisation for the backend, never a requirement for the login, and a
 * proxy shape we do not recognise must not cost the player their connection.</p>
 */
final class LoginProfileProperties {

    /** Name of the {@code LoginResult} field holding the properties. */
    private static final String PROPERTIES_FIELD = "properties";

    private static final String NAME_GETTER = "getName";

    private LoginProfileProperties() {
    }

    /**
     * Replaces the profile's properties with an empty array of the type this proxy build uses.
     *
     * @param loginProfile the BungeeCord login profile; may be null
     * @param onFailure     receives the reason when the profile could not be updated
     * @return true when the profile was updated
     */
    static boolean clear(Object loginProfile, Consumer<Throwable> onFailure) {
        if (loginProfile == null) {
            return false;
        }

        try {
            Field field = propertiesField(loginProfile);
            field.set(loginProfile, Array.newInstance(field.getType().getComponentType(), 0));
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            onFailure.accept(failure);
            return false;
        }
    }

    /**
     * Adds a property to the profile, replacing a previous property of the same name.
     *
     * <p>Existing properties are preserved, and the same-named one is dropped rather than
     * duplicated, so a profile that already carries this attestation ends up with exactly one
     * copy whose value is the one passed here.</p>
     *
     * @param loginProfile the BungeeCord login profile; may be null
     * @param name         the property name
     * @param value        the property value
     * @param onFailure    receives the reason when the profile could not be updated
     * @return true when the profile was updated
     */
    static boolean attach(Object loginProfile, String name, String value,
                          Consumer<Throwable> onFailure) {
        if (loginProfile == null) {
            return false;
        }

        try {
            Field field = propertiesField(loginProfile);
            Class<?> propertyType = field.getType().getComponentType();
            Constructor<?> constructor = propertyType.getConstructor(String.class, String.class);

            Object existing = field.get(loginProfile);
            int length = existing == null ? 0 : Array.getLength(existing);

            // Two passes: the first one drops a previous copy of this attestation, so the
            // replacement array is allocated with exactly the number of entries that survive.
            int kept = 0;
            for (int i = 0; i < length; i++) {
                if (!name.equals(propertyName(Array.get(existing, i)))) {
                    kept++;
                }
            }

            Object updated = Array.newInstance(propertyType, kept + 1);
            int index = 0;
            for (int i = 0; i < length; i++) {
                Object property = Array.get(existing, i);
                if (name.equals(propertyName(property))) {
                    continue;
                }
                Array.set(updated, index++, property);
            }
            Array.set(updated, index, constructor.newInstance(name, value));

            field.set(loginProfile, updated);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            onFailure.accept(failure);
            return false;
        }
    }

    /**
     * Returns the accessible {@code properties} field of the profile's class.
     *
     * @param loginProfile the BungeeCord login profile
     * @return the field, made accessible
     * @throws NoSuchFieldException when this proxy build does not expose that field
     */
    private static Field propertiesField(Object loginProfile) throws NoSuchFieldException {
        Field field = loginProfile.getClass().getDeclaredField(PROPERTIES_FIELD);
        field.setAccessible(true);
        return field;
    }

    /**
     * Reads the name of a property instance, whichever {@code Property} class it belongs to.
     *
     * @param property a property instance from the profile's array
     * @return the property name
     * @throws ReflectiveOperationException when the instance does not expose a name getter
     */
    private static String propertyName(Object property) throws ReflectiveOperationException {
        return (String) property.getClass().getMethod(NAME_GETTER).invoke(property);
    }
}
