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
package com.github.games647.fastlogin.bungee;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Proxy baseline linkage guard.
 *
 * <p>bungee/velocity deliberately do NOT bundle guava/gson: the classes in the shipped jars resolve
 * them against the copies the proxy itself provides at runtime. Our own classes however are compiled
 * against the newer versions declared by craftapi, so a build can silently start using an API the
 * proxy's older copy does not have. Bukkit/Folia are unaffected (they shade and relocate their own
 * copy), which means CI would stay green while only proxy installs break at runtime.</p>
 *
 * <p>This test closes that gap: every class/member reference from our shipped classes into the
 * proxy-provided libraries must exist in the proxy's bundled versions. The baseline versions are
 * the ones the proxy declares (provenance below); changing them means changing the supported proxy
 * floor and must be a deliberate decision, not a side effect of an upgrade.</p>
 *
 * <p><strong>MIRROR FILE.</strong> {@code bungee} and {@code velocity} carry one copy each. The two
 * copies must stay identical except for the package name and the marked configuration block below.
 * Run a diff over both files after every edit.</p>
 *
 * <p>Baseline provenance (from the proxies' published POMs - the declaration that decides what the
 * proxy bundles):</p>
 * <ul>
 *   <li>BungeeCord 1.20-R0.2: {@code net.md-5:bungeecord-parent} dependencyManagement pins
 *       guava 32.1.2-jre; {@code bungeecord-chat} pins gson 2.10.1</li>
 *   <li>Velocity 3.3.0-SNAPSHOT: {@code com.velocitypowered:velocity-api} declares
 *       guava 25.1-jre, gson 2.10.1, slf4j-api 2.0.12</li>
 * </ul>
 *
 * <p>Out of scope on purpose: snakeyaml (no compile/runtime skew is left to guard there: the velocity jar
 * relocates it together with the shim, and on bungee the shim + snakeyaml pair both come from the proxy and
 * is consistent by construction), Guice annotations (missing annotations are
 * ignored at runtime), and behavioral changes of existing members (this guard proves the word exists in the
 * dictionary, not that its meaning did not change).</p>
 */
class ProxyBaselineLinkageTest {

    // ===== mirror configuration block (bungee/velocity differ HERE and in the package name only) =====

    /** Class roots to scan, located through one sentinel class each (core, craftapi, module, tests). */
    private static final String[] SCAN_SENTINELS = {
        "com/github/games647/fastlogin/core/CommonUtil.class",
        "com/github/games647/craftapi/UUIDAdapter.class",
        "com/github/games647/fastlogin/bungee/FastLoginBungee.class",
        "com/github/games647/fastlogin/bungee/ProxyBaselineLinkageTest.class",
    };

    /** Reference family (internal name prefix) -> version bundled by the supported proxy. */
    private static final Map<String, String> BASELINE_VERSIONS = Map.of(
            "com/google/common/", "32.1.2-jre",
            "com/google/gson/", "2.10.1");

    /** Human-readable description of the supported proxy floor these baselines were taken from. */
    private static final String PROXY_BASELINE = "BungeeCord 1.20-R0.2 (net.md-5:bungeecord-parent)";

    /** Reference family -> the artifact's pom.properties path used to read the resolved version. */
    private static final Map<String, String> BASELINE_POM_PROPERTIES = Map.of(
            "com/google/common/", "META-INF/maven/com.google.guava/guava/pom.properties",
            "com/google/gson/", "META-INF/maven/com.google.code.gson/gson/pom.properties");

    /**
     * Known references into the proxy-provided libraries that cannot resolve on the proxy, kept
     * dormant on purpose. Each entry must stay justified or the test fails, so the list cannot rot.
     */
    private static final Set<String> KNOWN_DORMANT_REFS = Set.of();

    // ===== end mirror configuration block =====

    private static final String SCAN_PREFIX = "com/github/games647/";

    private record MemberRef(String owner, String name, String desc) {

        @Override
        public String toString() {
            return owner + '.' + name + ':' + desc;
        }
    }

    private record ParsedRefs(Set<String> classes, Set<MemberRef> members) {
    }

    @Test
    void baselineVersionsMatchProxyBundledVersions() {
        ClassLoader loader = getClass().getClassLoader();
        for (Map.Entry<String, String> entry : BASELINE_VERSIONS.entrySet()) {
            String family = entry.getKey();
            String expected = entry.getValue();
            String actual = readArtifactVersion(loader, BASELINE_POM_PROPERTIES.get(family));
            assertEquals(expected, actual, () -> "Baseline drift for " + family + ": the test classpath resolved "
                    + actual + " but the supported proxy (" + PROXY_BASELINE + ") bundles " + expected
                    + ". If the supported proxy floor moved, update this constant on purpose and record the"
                    + " new baseline; otherwise dependency resolution changed under us and the guard below"
                    + " would silently check against the wrong library.");
        }
    }

    @Test
    void everyReferenceResolvesAgainstTheProxyBaseline() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        Set<String> scannedClasses = new HashSet<>();
        Set<String> neededClasses = new TreeSet<>();
        Set<MemberRef> neededMembers = new TreeSet<>((a, b) -> a.toString().compareTo(b.toString()));
        Set<String> visitedRoots = new HashSet<>();

        for (String sentinel : SCAN_SENTINELS) {
            URL url = loader.getResource(sentinel);
            assertTrue(url != null, () -> "Scan sentinel not found on the test classpath: " + sentinel);
            scanRoot(loader, url, sentinel, visitedRoots, scannedClasses, neededClasses, neededMembers);
        }

        // Anti-vacuity: a broken parser that finds nothing must not pass silently.
        assertTrue(scannedClasses.size() >= 100,
                () -> "Only " + scannedClasses.size() + " classes were scanned; the classpath walk is broken");
        assertTrue(neededClasses.size() + neededMembers.size() >= 30,
                () -> "Only " + (neededClasses.size() + neededMembers.size()) + " external references were found;"
                        + " the constant pool parser is broken or the scan filters are wrong");

        List<String> problems = new ArrayList<>();
        Set<String> usedExemptions = new HashSet<>();

        for (String className : neededClasses) {
            if (KNOWN_DORMANT_REFS.contains(className)) {
                usedExemptions.add(className);
                continue;
            }
            if (loadClass(loader, className) == null) {
                problems.add("missing class " + className + " (" + describeFamily(className) + ')');
            }
        }

        for (MemberRef ref : neededMembers) {
            if (KNOWN_DORMANT_REFS.contains(ref.owner())) {
                usedExemptions.add(ref.owner());
                continue;
            }
            Class<?> owner = loadClass(loader, ref.owner());
            if (owner == null) {
                problems.add("missing class " + ref.owner() + " for " + ref + " (" + describeFamily(ref.owner()) + ')');
            } else if (!memberExists(owner, ref.name(), ref.desc())) {
                problems.add("missing member " + ref + " (" + describeFamily(ref.owner()) + ')');
            }
        }

        assertEquals(KNOWN_DORMANT_REFS, usedExemptions,
                "Stale KNOWN_DORMANT_REFS entries - remove exemptions that no longer occur, they hide future"
                        + " findings");

        if (!problems.isEmpty()) {
            fail("Our shipped classes reference API that the proxy's bundled libraries do not provide.\n"
                    + "These would fail on a user's proxy with NoSuchMethodError/NoClassDefFoundError while the"
                    + " build stays green (bukkit/folia are unaffected - they ship their own relocated copy).\n"
                    + "Either avoid the new API, or raise the supported proxy floor deliberately and update the"
                    + " baseline constants above.\n" + String.join("\n", problems));
        }
    }

    private static String describeFamily(String className) {
        for (Map.Entry<String, String> entry : BASELINE_VERSIONS.entrySet()) {
            if (className.startsWith(entry.getKey())) {
                return "proxy baseline " + entry.getValue();
            }
        }
        return "not in a checked family";
    }

    private static String readArtifactVersion(ClassLoader loader, String pomPropertiesPath) {
        try (InputStreamHolder holder = InputStreamHolder.open(loader, pomPropertiesPath)) {
            Properties properties = new Properties();
            properties.load(holder.stream());
            String version = properties.getProperty("version");
            assertTrue(version != null, () -> "No version property in " + pomPropertiesPath);
            return version;
        } catch (IOException ioEx) {
            throw new UncheckedIOException("Cannot read " + pomPropertiesPath, ioEx);
        }
    }

    private record InputStreamHolder(java.io.InputStream stream) implements AutoCloseable {

        static InputStreamHolder open(ClassLoader loader, String path) throws IOException {
            java.io.InputStream in = loader.getResourceAsStream(path);
            assertTrue(in != null, () -> "Resource not found on the test classpath: " + path);
            return new InputStreamHolder(in);
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    private static void scanRoot(ClassLoader loader, URL sentinelUrl, String sentinel, Set<String> visitedRoots,
                                 Set<String> scannedClasses, Set<String> neededClasses, Set<MemberRef> neededMembers) {
        String protocol = sentinelUrl.getProtocol();
        try {
            if ("file".equals(protocol)) {
                Path classFile = Paths.get(sentinelUrl.toURI());
                Path root = classFile;
                for (int i = 0; i < sentinel.split("/").length; i++) {
                    root = root.getParent();
                }
                if (!visitedRoots.add(root.toString())) {
                    return;
                }
                Path base = root;
                try (Stream<Path> paths = Files.walk(base)) {
                    paths.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".class"))
                            .forEach(path -> {
                                Path relative = base.relativize(path);
                                String name = relative.toString().replace('\\', '/');
                                if (name.startsWith(SCAN_PREFIX)) {
                                    readAndParse(path, name, scannedClasses, neededClasses, neededMembers);
                                }
                            });
                }
            } else if ("jar".equals(protocol)) {
                String path = sentinelUrl.getPath();
                int separator = path.indexOf('!');
                String jarPath = path.substring(0, separator);
                if (!visitedRoots.add(jarPath)) {
                    return;
                }
                Path jar = Paths.get(URI.create("file:" + jarPath));
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                        String name = entry.getName();
                        if (!entry.isDirectory() && name.startsWith(SCAN_PREFIX) && name.endsWith(".class")) {
                            readAndParse(zip, entry, name, scannedClasses, neededClasses, neededMembers);
                        }
                    }
                }
            } else {
                fail("Unsupported classpath protocol for sentinel " + sentinel + ": " + protocol);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot scan classpath root of " + sentinel, ex);
        }
    }

    private static void readAndParse(Path file, String name, Set<String> scannedClasses,
                                     Set<String> neededClasses, Set<MemberRef> neededMembers) {
        try {
            accumulate(Files.readAllBytes(file), name, scannedClasses, neededClasses, neededMembers);
        } catch (IOException ioEx) {
            throw new UncheckedIOException("Cannot read " + file, ioEx);
        }
    }

    private static void readAndParse(ZipFile zip, ZipEntry entry, String name, Set<String> scannedClasses,
                                     Set<String> neededClasses, Set<MemberRef> neededMembers) {
        try (java.io.InputStream in = zip.getInputStream(entry)) {
            accumulate(in.readAllBytes(), name, scannedClasses, neededClasses, neededMembers);
        } catch (IOException ioEx) {
            throw new UncheckedIOException("Cannot read " + name, ioEx);
        }
    }

    private static void accumulate(byte[] bytes, String name, Set<String> scannedClasses,
                                   Set<String> neededClasses, Set<MemberRef> neededMembers) {
        if (!scannedClasses.add(name)) {
            return;
        }
        ParsedRefs refs = parseReferences(bytes);
        for (String className : refs.classes()) {
            if (isBaselineFamily(className)) {
                neededClasses.add(className);
            }
        }
        for (MemberRef ref : refs.members()) {
            if (isBaselineFamily(ref.owner())) {
                neededMembers.add(ref);
            }
        }
    }

    private static boolean isBaselineFamily(String internalName) {
        for (String family : BASELINE_VERSIONS.keySet()) {
            if (internalName.startsWith(family)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts the external linkage surface of a class file from its constant pool: CONSTANT_Class
     * entries (super types, casts, class literals, exception types), member references
     * (CONSTANT_Fieldref/Methodref/InterfaceMethodref) and class names embedded in descriptors.
     */
    private static ParsedRefs parseReferences(byte[] bytes) {
        Set<String> classes = new HashSet<>();
        Set<MemberRef> members = new HashSet<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            in.readInt();
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            int[] first = new int[count];
            int[] second = new int[count];
            int[] tags = new int[count];
            String[] utf8 = new String[count];
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                tags[i] = tag;
                switch (tag) {
                    case 1:
                        utf8[i] = in.readUTF();
                        break;
                    case 3, 4:
                        in.readInt();
                        break;
                    case 5, 6:
                        in.readLong();
                        i++;
                        break;
                    case 7, 8, 16, 19, 20:
                        first[i] = in.readUnsignedShort();
                        break;
                    case 9, 10, 11, 12, 17, 18:
                        first[i] = in.readUnsignedShort();
                        second[i] = in.readUnsignedShort();
                        break;
                    case 15:
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                        break;
                    default:
                        throw new IllegalStateException("Unknown constant pool tag " + tag);
                }
            }

            for (int i = 1; i < count; i++) {
                if (tags[i] == 7) {
                    String name = utf8[first[i]];
                    if (name != null) {
                        classes.add(name);
                    }
                } else if (tags[i] == 9 || tags[i] == 10 || tags[i] == 11) {
                    String owner = utf8[first[first[i]]];
                    String name = utf8[first[second[i]]];
                    String desc = utf8[second[second[i]]];
                    if (owner != null && name != null && desc != null) {
                        members.add(new MemberRef(owner, name, desc));
                        classes.addAll(descriptorClasses(desc));
                    }
                } else if (tags[i] == 12) {
                    String desc = utf8[second[i]];
                    if (desc != null) {
                        classes.addAll(descriptorClasses(desc));
                    }
                }
            }

            // Own declarations do not go through NameAndType entries; their signatures are Utf8
            // constants that look like descriptors. This catches types that only appear in the
            // signature of one of our methods.
            for (String value : utf8) {
                if (value != null && looksLikeDescriptor(value)) {
                    classes.addAll(descriptorClasses(value));
                }
            }
        } catch (IOException ioEx) {
            throw new UncheckedIOException("Cannot parse class file", ioEx);
        }
        return new ParsedRefs(classes, members);
    }

    private static boolean looksLikeDescriptor(String value) {
        return value.startsWith("(") || value.startsWith("[")
                || (value.startsWith("L") && value.endsWith(";"));
    }

    private static Set<String> descriptorClasses(String desc) {
        Set<String> result = new HashSet<>();
        int index = 0;
        while (true) {
            int start = desc.indexOf("L", index);
            if (start < 0) {
                break;
            }
            int end = desc.indexOf(';', start);
            if (end < 0) {
                break;
            }
            String name = desc.substring(start + 1, end);
            if (name.matches("[\\w$/.]+")) {
                result.add(name);
            }
            index = end + 1;
        }
        return result;
    }

    private static Class<?> loadClass(ClassLoader loader, String internalName) {
        try {
            return Class.forName(internalName.replace('/', '.'), false, loader);
        } catch (ClassNotFoundException | LinkageError missing) {
            return null;
        }
    }

    private static boolean memberExists(Class<?> owner, String name, String desc) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            if (declaredHere(current, name, desc) || interfaceChainMatches(current.getInterfaces(), name, desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean interfaceChainMatches(Class<?>[] interfaces, String name, String desc) {
        for (Class<?> iface : interfaces) {
            if (declaredHere(iface, name, desc) || interfaceChainMatches(iface.getInterfaces(), name, desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean declaredHere(Class<?> type, String name, String desc) {
        if ("<init>".equals(name)) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (descriptorOf(constructor).equals(desc)) {
                    return true;
                }
            }
            return false;
        }
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && descriptorOf(method).equals(desc)) {
                return true;
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals(name) && descriptorOf(field.getType()).equals(desc)) {
                return true;
            }
        }
        return false;
    }

    private static String descriptorOf(Method method) {
        StringBuilder builder = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes()) {
            builder.append(descriptorOf(parameter));
        }
        return builder.append(')').append(descriptorOf(method.getReturnType())).toString();
    }

    private static String descriptorOf(Constructor<?> constructor) {
        StringBuilder builder = new StringBuilder("(");
        for (Class<?> parameter : constructor.getParameterTypes()) {
            builder.append(descriptorOf(parameter));
        }
        return builder.append(")V").toString();
    }

    private static String descriptorOf(Class<?> type) {
        if (type.isArray()) {
            return "[" + descriptorOf(type.getComponentType());
        }
        if (type.isPrimitive()) {
            return switch (type.getName()) {
                case "void" -> "V";
                case "boolean" -> "Z";
                case "byte" -> "B";
                case "char" -> "C";
                case "short" -> "S";
                case "int" -> "I";
                case "long" -> "J";
                case "float" -> "F";
                case "double" -> "D";
                default -> throw new IllegalStateException("Unknown primitive " + type);
            };
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }
}
