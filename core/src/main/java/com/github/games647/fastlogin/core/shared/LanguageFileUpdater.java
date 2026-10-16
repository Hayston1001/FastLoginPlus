/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Repairs language upgrades without replacing customized or unknown message values. */
final class LanguageFileUpdater {
    private LanguageFileUpdater() {
    }

    static Result update(Path file, Configuration english, Configuration localized) throws IOException {
        if (!Files.exists(file)) {
            return new Result(0, 0);
        }
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        Configuration user;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            user = provider.load(reader);
        }
        Set<String> userKeys = new LinkedHashSet<>(user.getKeys());
        Set<String> localKeys = new LinkedHashSet<>(localized.getKeys());
        Set<String> defaults = new LinkedHashSet<>(english.getKeys());
        defaults.addAll(localKeys);
        int added = 0;
        int migrated = 0;
        Configuration additions = new Configuration();
        for (String key : defaults) {
            Object translated = localKeys.contains(key) ? localized.get(key) : english.get(key);
            if (translated == null) {
                continue;
            }
            if (!userKeys.contains(key)) {
                user.set(key, translated);
                additions.set(key, translated);
                added++;
            } else if (translated instanceof String && english.get(key) instanceof String
                    && !Objects.equals(translated, english.get(key))
                    && Objects.equals(user.get(key), english.get(key))) {
                user.set(key, translated);
                migrated++;
            }
        }
        if (added == 0 && migrated == 0) {
            return new Result(0, 0);
        }
        String output;
        if (migrated == 0) {
            // Ordinary upgrades remain append-only, retaining the user's formatting/comments.
            StringWriter appended = new StringWriter();
            provider.save(additions, appended);
            output = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                    + "\n# === Keys below are auto-added by FastLoginPlus ===\n" + appended;
        } else {
            // Let the YAML serializer preserve customized values, including multiline scalars,
            // rather than trying to replace quoted text with a regex. This may reformat YAML.
            StringWriter writer = new StringWriter();
            provider.save(user, writer);
            output = writer.toString();
        }
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "messages-", ".tmp");
        try {
            Files.write(temporary, output.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new Result(added, migrated);
    }

    static final class Result {
        final int added;
        final int migrated;

        private Result(int added, int migrated) {
            this.added = added;
            this.migrated = migrated;
        }
    }
}
