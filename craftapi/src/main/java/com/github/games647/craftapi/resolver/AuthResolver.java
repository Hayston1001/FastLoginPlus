/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.craftapi.resolver;

import com.github.games647.craftapi.model.auth.Account;
import com.github.games647.craftapi.model.auth.Verification;
import com.github.games647.craftapi.model.skin.Model;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.Optional;

/**
 * Resolver that handles authentication requests.
 */
public interface AuthResolver {

    /**
     * Verifies if a player requesting to join an online mode server is actually authenticated against Mojang.
     *
     * @param username the joining username
     * @param serverHash server id hash
     * @param hostIp the player connecting IP address
     * @return the verification response or empty if invalid
     * @throws IOException I/O exception contacting the server
     */
    Optional<Verification> hasJoined(String username, String serverHash, InetAddress hostIp) throws IOException;

    /**
     * Logs the given player account in.
     *
     * @param email email address
     * @param password plain text password
     * @return logged in account
     * @throws IOException I/O exception contacting the server
     * @throws InvalidCredentialsException invalid auth data
     */
    Account authenticate(String email, String password) throws IOException, InvalidCredentialsException;

    /**
     * Changes the skin to the image that can be downloaded from that URL. The URL have to be direct link without
     * things like HTML in it.
     *
     * @param account authenticated account
     * @param toUrl the URL that Mojang should use for downloading the skin from
     * @param skinModel skin arm model
     * @throws IOException I/O exception contacting the server
     */
    void changeSkin(Account account, URL toUrl, Model skinModel) throws IOException;

    /**
     * Changes the skin to the given image.
     *
     * @param account authenticated account
     * @param pngImage png image
     * @param skinModel skin arm model
     * @throws IOException I/O exception contacting the server
     */
    void changeSkin(Account account, RenderedImage pngImage, Model skinModel) throws IOException;

    /**
     * Clears the uploaded skin.
     *
     * @param account authenticated account
     * @throws IOException I/O exception contacting the server
     * @return true if successful
     */
    boolean resetSkin(Account account) throws IOException;

}
