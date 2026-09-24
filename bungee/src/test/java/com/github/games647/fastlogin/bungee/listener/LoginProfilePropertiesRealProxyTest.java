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

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises {@link LoginProfileProperties} against <em>real</em> BungeeCord classes instead of the
 * stand-ins used by {@link LoginProfilePropertiesTest}.
 *
 * <p><b>Why the stand-ins are not enough.</b> They pin the logic, not the contract: the helper
 * reads the {@code properties} field of whatever profile object it is handed and builds elements
 * with {@code (String, String)}. A proxy build that renames the field, drops the two-argument
 * constructor or moves the element type somewhere unanticipated would still pass a test that
 * defines those very shapes itself. Only the shipped classes can falsify that.</p>
 *
 * <p><b>Two generations, two sources.</b> The 2024 artifact this module compiles against is a real
 * old-shape build ({@code net.md_5.bungee.protocol.Property}), so that one is checked on every run
 * and needs no extra file. The new shape lives in builds released after BungeeCord #3855
 * (2025-07-14) - which, contrary to what the old-shape compile baseline suggests, includes
 * <em>current</em> Waterfall: it merges upstream, and its 1.21-615 build (2026-06-16) already ships
 * {@code net.md_5.bungee.protocol.data.Property} and no trace of the old class. Because that
 * artifact is 27 MB and cannot be committed, the check is opt-in:</p>
 *
 * <pre>{@code mvn test -pl bungee -Dflp.proxy.jar=/path/to/waterfall-1.21-615.jar}</pre>
 *
 * <p>It is skipped, not failed, when the property is missing, so the default build stays offline.
 * Everything is driven reflectively: the test class itself is compiled against the old shape and
 * must not link the new one. For the supplied jar the helper and the profile are loaded from a
 * child-first loader, so the profile's element type is the one that build ships, while this test
 * keeps resolving gson and JUnit from its own loader.</p>
 */
class LoginProfilePropertiesRealProxyTest {

    private static final String HELPER = "com.github.games647.fastlogin.bungee.listener.LoginProfileProperties";

    private static final String PROFILE = "net.md_5.bungee.connection.LoginResult";

    private static final String ATTACHMENT = "flp_premium_uuid";

    private static final String ATTACHMENT_VALUE = "115f0e9c-1b6b-4f6e-9d38-2a7bd4b0c4c1";

    @Test
    void realOldShapeFromTheCompileBaselineIsUpdated() throws Exception {
        assertProfileIsUpdated(getClass().getClassLoader(), "net.md_5.bungee.protocol.Property");
    }

    @Test
    void realNewShapeFromTheSuppliedProxyJarIsUpdated() throws Exception {
        String configured = System.getProperty("flp.proxy.jar", "");
        Path proxyJar = Paths.get(configured);
        assumeTrue(!configured.isEmpty() && Files.exists(proxyJar),
                "pass -Dflp.proxy.jar=<current proxy jar> to verify the post-#3855 shape");

        try (URLClassLoader loader = childFirstLoader(proxyJar)) {
            assertProfileIsUpdated(loader, "net.md_5.bungee.protocol.data.Property");
        }
    }

    /**
     * Attaches the attestation to a real profile of the given generation and asserts both the
     * property that ends up in the array and that {@code clear} empties it again.
     *
     * @param loader                the loader that provides the proxy classes and the helper
     * @param expectedPropertyClass fully qualified name the element must have on this build
     * @throws Exception when the reflective access itself fails - a failure the helper is
     *                   expected to report instead of throwing, so it is asserted below
     */
    private static void assertProfileIsUpdated(ClassLoader loader, String expectedPropertyClass)
            throws Exception {
        Class<?> profileClass = loader.loadClass(PROFILE);
        Field propertiesField = profileClass.getDeclaredField("properties");
        propertiesField.setAccessible(true);

        Object profile = profileClass
                .getConstructor(String.class, String.class, propertiesField.getType())
                .newInstance("069a79f444e94726a5befca90e38aaf5", "Notch", null);

        Class<?> helper = loader.loadClass(HELPER);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Method attach = helper.getDeclaredMethod("attach", Object.class, String.class, String.class,
                Consumer.class);
        attach.setAccessible(true);
        Object attached = attach.invoke(null, profile, ATTACHMENT, ATTACHMENT_VALUE,
                (Consumer<Throwable>) failure::set);
        assertEquals(Boolean.TRUE, attached, () -> "attach reported: " + failure.get());

        Object properties = propertiesField.get(profile);
        assertEquals(1, Array.getLength(properties), "exactly the attestation is expected");
        Object property = Array.get(properties, 0);
        assertEquals(expectedPropertyClass, property.getClass().getName());
        assertEquals(ATTACHMENT, property.getClass().getMethod("getName").invoke(property));
        assertEquals(ATTACHMENT_VALUE, property.getClass().getMethod("getValue").invoke(property));

        // The value is what the backend reads, so an empty signature is the documented outcome
        assertEquals(null, property.getClass().getMethod("getSignature").invoke(property));

        Method clear = helper.getDeclaredMethod("clear", Object.class, Consumer.class);
        clear.setAccessible(true);
        Object cleared = clear.invoke(null, profile, (Consumer<Throwable>) failure::set);
        assertEquals(Boolean.TRUE, cleared, () -> "clear reported: " + failure.get());
        assertEquals(0, Array.getLength(propertiesField.get(profile)));
    }

    /**
     * Builds a loader that takes the proxy and our own classes from the supplied jar and this
     * module's build output, while everything else (gson, slf4j, JUnit) keeps coming from the
     * test classpath.
     *
     * @param proxyJar the proxy artifact to load the profile classes from
     * @return the loader, to be closed by the caller
     * @throws Exception when the classpath cannot be turned into URLs
     */
    private static URLClassLoader childFirstLoader(Path proxyJar) throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(proxyJar.toUri().toURL());
        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(File.pathSeparator)) {
            if (!entry.isEmpty()) {
                urls.add(new File(entry).toURI().toURL());
            }
        }
        assertTrue(urls.size() > 1, "the test classpath is expected to be visible");

        return new URLClassLoader("flp-proxy-shape", urls.toArray(new URL[0]),
                LoginProfilePropertiesRealProxyTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.md_5.bungee.") || name.startsWith("com.github.games647.fastlogin.")) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded == null) {
                            loaded = findClass(name);
                        }
                        if (resolve) {
                            resolveClass(loaded);
                        }
                        return loaded;
                    }
                }
                return super.loadClass(name, resolve);
            }
        };
    }
}
