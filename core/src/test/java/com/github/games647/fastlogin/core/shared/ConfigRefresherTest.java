/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.hayston1001.fastloginplus.shared.config.ConfigRefresher;
import com.github.hayston1001.fastloginplus.shared.config.ConfigurationFiles;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigRefresherTest {

    private static final String FULL_TEMPLATE = "config.yml";
    private static final String PROXY_TEMPLATE = "config-proxy.yml";

    private final ConfigurationProvider provider =
            ConfigurationProvider.getProvider(YamlConfiguration.class);

    @TempDir
    Path tempDir;

    @Test
    void representativeOldConfigMatchesByteSnapshot() throws IOException {
        String template = "# Canonical template\n\n"
                + "language: 'en'\n"
                + "anti-bot:\n  enabled: true\n  trusted-ips: []\n"
                + "  limits:\n    burst: 10\n"
                + "quoted: 'default'\nmultiline: 'default'\n"
                + "ServerRSAPublicKeyFile:\nnew-key: 42 # Added default\n";
        String oldConfig = "# Old comment\nremoved-key: obsolete\n"
                + "language: zh\nanti-bot:\n  enabled: false\n"
                + "  trusted-ips: ['127.0.0.1', 'proxy:25565']\n"
                + "  limits:\n    burst: 7\n"
                + "quoted: \"owner's # key\"\nmultiline: |-\n  first\n  second\n"
                + "ServerRSAPublicKeyFile: ' /keys/custom.pem '\n";
        String expected = "# Canonical template\n\n"
                + "language: 'zh'\n"
                + "anti-bot:\n  enabled: false\n  trusted-ips: \n"
                + "    - 127.0.0.1\n    - 'proxy:25565'\n"
                + "  limits:\n    burst: 7\n"
                + "quoted: 'owner''s # key'\nmultiline: 'first\nsecond'\n"
                + "ServerRSAPublicKeyFile: ' /keys/custom.pem '\nnew-key: 42\n";
        Path config = tempDir.resolve("config.yml");
        Files.write(config, oldConfig.getBytes(StandardCharsets.UTF_8));
        Configuration defaults = provider.load(new StringReader(template));
        Configuration merged = ConfigurationFiles.load(
                config, defaults, FastLoginCore.configurationLoader(provider));

        ConfigRefresher.refresh(config, Arrays.asList(template.split("\n")),
                ConfigRefreshAdapter.flattenConfig(merged));

        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(config));
    }

    @Test
    void loadMaterializesDefaultsMissingFromOldConfig() throws IOException {
        Path config = tempDir.resolve("config.yml");
        Files.write(config, "language: zh\ncheck-update: false\n".getBytes(StandardCharsets.UTF_8));
        PlatformPlugin<Object> plugin = mock(PlatformPlugin.class);
        when(plugin.getLog()).thenReturn(mock(org.slf4j.Logger.class));
        when(plugin.getPluginFolder()).thenReturn(tempDir);
        FastLoginCore<Object, Object, PlatformPlugin<Object>> core = new FastLoginCore<>(plugin);

        core.load();

        String output = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        assertTrue(output.contains("  trusted-ips:\n"));
        assertTrue(output.contains("  per-ip-connections: 20\n"));
        assertEquals("zh", core.getConfig().getString("language"));
        assertEquals(20, core.getConfig().getInt("anti-bot.per-ip-connections"));
    }

    @Test
    void adapterPreservesFlattenedKeysOrderAndLeafValues() {
        Configuration config = provider.load(new StringReader("MixedCase: first\n"
                + "nested:\n  inner:\n    Value: 7\n  list: [one, two]\n"
                + "empty: {}\nnil: null\nlast: false\n"));

        Map<String, Object> values = ConfigRefreshAdapter.flattenConfig(config);

        assertEquals(Arrays.asList("MixedCase", "nested.inner.Value", "nested.list", "last"),
                new ArrayList<>(values.keySet()));
        assertEquals("first", values.get("MixedCase"));
        assertEquals(7, values.get("nested.inner.Value"));
        assertSame(config.get("nested.list"), values.get("nested.list"));
        assertEquals(false, values.get("last"));
        assertFalse(values.containsKey("empty"));
        assertFalse(values.containsKey("nil"));
    }

    @Test
    void missingTemplateStillLeavesFileUntouched() throws IOException {
        Path config = tempDir.resolve("config.yml");
        byte[] original = "# User comment\nlanguage: zh\n".getBytes(StandardCharsets.UTF_8);
        Files.write(config, original);

        assertNull(ConfigRefreshAdapter.readTemplateLines(getClass().getClassLoader(), "absent-template"));
        refresh(config, load(config), "absent-template");

        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void backendConfigMigratedToProxyTemplateDropsBackendOnlyKeysAndPreservesValues()
            throws IOException {
        // An admin's existing backend config, migrated to a proxy: the
        // backend-only keys must disappear while every user value survives.
        Path config = copyResource(FULL_TEMPLATE);
        Configuration userConfig = load(config);
        userConfig.set("language", "zh");
        userConfig.set("autoRegister", false);
        userConfig.set("verifyClientKeys", true);
        userConfig.set("useProxyAgnosticResolver", false);
        userConfig.set("ServerRSAPublicKeyFile", "/my/custom/key.pem");
        userConfig.set("anti-bot.trusted-ips", Arrays.asList("127.0.0.1", "10.0.0.5"));
        userConfig.set("proxies", Arrays.asList("proxy1.example:8080"));
        provider.save(userConfig, config.toFile());

        refresh(config, load(config), PROXY_TEMPLATE);

        String output = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        assertFalse(output.contains("verifyClientKeys:"));
        assertFalse(output.contains("respectIpLimit:"));
        assertFalse(output.contains("useProxyAgnosticResolver:"));
        // Comments come from the proxy template
        assertTrue(output.contains("FastLoginPlus Configuration (BungeeCord / Velocity proxy)"));

        Configuration refreshed = load(config);
        assertEquals("zh", refreshed.getString("language"));
        assertFalse(refreshed.getBoolean("autoRegister"));
        assertFalse(refreshed.contains("verifyClientKeys"));
        // Scalar key written without a template value keeps the user's value
        assertEquals("/my/custom/key.pem", refreshed.getString("ServerRSAPublicKeyFile"));
        assertEquals(Arrays.asList("127.0.0.1", "10.0.0.5"),
                refreshed.getStringList("anti-bot.trusted-ips"));
        assertEquals(Arrays.asList("proxy1.example:8080"), refreshed.getStringList("proxies"));
    }

    @Test
    void proxyConfigMigratedToBackendTemplateRestoresMissingKeys() throws IOException {
        // The reverse copy: keys absent from the trimmed proxy config come
        // back with the backend template defaults.
        Path config = copyResource(PROXY_TEMPLATE);

        refresh(config, load(config), FULL_TEMPLATE);

        String output = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        assertTrue(output.contains("verifyClientKeys: false"));
        assertTrue(output.contains("respectIpLimit: false"));
        assertTrue(output.contains("useProxyAgnosticResolver: true"));
        assertTrue(output.contains("FastLoginPlus Configuration (Bukkit / Folia)"));
    }

    @Test
    void freshProxyConfigRefreshIsStableAndStaysTrimmed() throws IOException {
        // A fresh proxy install must stay trimmed across refresh cycles.
        Path config = copyResource(PROXY_TEMPLATE);

        refresh(config, load(config), PROXY_TEMPLATE);

        String output = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        assertFalse(output.contains("verifyClientKeys:"));
        assertFalse(output.contains("respectIpLimit:"));
        assertFalse(output.contains("useProxyAgnosticResolver:"));

        Configuration refreshed = load(config);
        assertEquals("en", refreshed.getString("language"));
        assertTrue(refreshed.getBoolean("autoLogin"));
        assertTrue(refreshed.getBoolean("premiumUuid"));
    }

    @Test
    void sectionHeaderWithScalarUserValueKeepsTemplateStructure() throws IOException {
        // a user value that turns a template section header into a
        // scalar (e.g. a broken manual edit "anti-bot: false") must not
        // produce invalid YAML — the template structure wins.
        Path config = copyResource(FULL_TEMPLATE);
        Configuration userConfig = load(config);
        userConfig.set("anti-bot", false);
        provider.save(userConfig, config.toFile());

        refresh(config, load(config), FULL_TEMPLATE);

        String output = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        assertTrue(output.contains("anti-bot:"));
        assertTrue(output.contains("  enabled:"));
        Configuration refreshed = load(config);
        assertTrue(refreshed.contains("anti-bot.enabled"));
    }

    @Test
    void ambiguousScalarUserValuesStayQuotedStrings() throws IOException {
        // strings that YAML 1.1 would resolve as boolean/number/null
        // must be written quoted so they keep their string type across the
        // refresh cycle.
        Path config = copyResource(FULL_TEMPLATE);
        Configuration userConfig = load(config);
        userConfig.set("language", "no");
        userConfig.set("ServerRSAPublicKeyFile", "1_000");
        // no save/load round-trip here: the parsed config carries real Strings
        refresh(config, userConfig, FULL_TEMPLATE);

        String output = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        assertTrue(output.contains("language: 'no'"));
        assertTrue(output.contains("ServerRSAPublicKeyFile: '1_000'"));

        Configuration refreshed = load(config);
        assertEquals("no", refreshed.getString("language"));
    }

    @Test
    void refreshRewritesConfigAtomicallyWithoutLeftoverTempFiles() throws IOException {
        // the rewrite must go through a temp file + move so a crash
        // mid-write can never truncate the user's config.
        Path config = copyResource(FULL_TEMPLATE);
        Configuration userConfig = load(config);
        userConfig.set("language", "zh");

        refresh(config, userConfig, FULL_TEMPLATE);

        Configuration refreshed = load(config);
        assertEquals("zh", refreshed.getString("language"));
        // no leftover temp files in the config directory
        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            assertEquals(1, files.count());
        }
    }

    private Path copyResource(String resource) throws IOException {
        Path target = tempDir.resolve("config.yml");
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            Files.copy(Objects.requireNonNull(is, "missing resource " + resource), target);
        }
        return target;
    }

    private Configuration load(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return provider.load(reader);
        }
    }

    private void refresh(Path file, Configuration config, String template) throws IOException {
        List<String> lines = ConfigRefreshAdapter.readTemplateLines(getClass().getClassLoader(), template);
        if (lines != null) {
            ConfigRefresher.refresh(file, lines, ConfigRefreshAdapter.flattenConfig(config));
        }
    }

    @Test
    void refreshedConfigEndsWithSingleTrailingNewline() throws IOException {
        // The bundled templates end with a newline, so the rewritten file must
        // too — otherwise a git-managed config.yml gains a
        // "\ No newline at end of file" diff on every startup.
        for (String template : Arrays.asList(FULL_TEMPLATE, PROXY_TEMPLATE)) {
            // copyResource always targets the same file name, so clear it first
            Files.deleteIfExists(tempDir.resolve("config.yml"));
            Path config = copyResource(template);

            refresh(config, load(config), template);

            String output = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
            assertTrue(output.endsWith("\n"), template + " must end with a newline");
            assertFalse(output.endsWith("\n\n"), template + " must not end with a blank line");
        }
    }
}
