/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi.model.skin;

import com.github.games647.craftapi.model.skin.Texture.Type;

import java.net.URL;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextureTest {

    private static final String URL_PREFIX = "http://textures.minecraft.net/texture/";

    @Test
    void testLongUrl() throws Exception {
        Texture model = new Texture(Type.SKIN, "52847ba3eb656e7ac69f2af9cec58d4ec2f5a2ea7e18968c97907e87efa9cc4");
        URL url = new URL(URL_PREFIX + "52847ba3eb656e7ac69f2af9cec58d4ec2f5a2ea7e18968c97907e87efa9cc4");
        assertEquals(model.getURL(), url);
    }
}
