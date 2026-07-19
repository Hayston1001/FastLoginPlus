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
package com.github.games647.fastlogin.core.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;

import io.javalin.http.ContentType;
import io.javalin.http.Context;

/**
 * Serves webui language files from the plugin config directory.
 * Falls back to classpath resources if the file doesn't exist on disk.
 */
public class LangApiHandler {

    private final Logger log;
    private final Path pluginFolder;

    public LangApiHandler(Logger log, Path pluginFolder) {
        this.log = log;
        this.pluginFolder = pluginFolder;
    }

    public void handle(Context ctx) {
        String lang = ctx.pathParam("code");

        // Validate language code: only allow alphanumeric + hyphen
        if (!lang.matches("[a-zA-Z_-]+")) {
            ctx.status(400).json(java.util.Collections.singletonMap("error", "Invalid language code"));
            return;
        }

        String fileName = "webui_" + lang + ".json";

        // 1. Try plugin config directory first
        Path fileOnDisk = pluginFolder.resolve(fileName);
        if (Files.exists(fileOnDisk)) {
            try {
                byte[] bytes = Files.readAllBytes(fileOnDisk);
                ctx.contentType(ContentType.APPLICATION_JSON).result(new String(bytes, StandardCharsets.UTF_8));
                return;
            } catch (IOException e) {
                log.warn("Failed to read language file: {}", fileOnDisk, e);
            }
        }

        // 2. Fall back to classpath (bundled in JAR)
        // Language files are in core/src/main/resources/ so the classloader can find them
        String resourcePath = "/" + fileName;
        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                ctx.status(404).json(java.util.Collections.singletonMap("error", "Language not found: " + lang));
                return;
            }
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int bytesRead;
            while ((bytesRead = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            ctx.contentType(ContentType.APPLICATION_JSON)
                .result(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Failed to read bundled language file: {}", resourcePath, e);
            ctx.status(500).json(java.util.Collections.singletonMap("error", "Internal error"));
        }
    }
}
