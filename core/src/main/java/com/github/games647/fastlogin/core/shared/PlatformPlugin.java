/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import com.github.games647.fastlogin.core.hooks.bedrock.BedrockService;
import com.github.games647.fastlogin.core.scheduler.AsyncScheduler;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.ThreadFactory;

public interface PlatformPlugin<C> {

    String getName();

    Path getPluginFolder();

    Logger getLog();

    void sendMessage(C receiver, String message);

    AsyncScheduler getScheduler();

    boolean isPluginInstalled(String name);

    default void sendMultiLineMessage(C receiver, String message) {
        // accept both newline conventions — locale files use %nl%,
        // some messages contain literal backslash-n
        for (String line : message.replace("\\n", "%nl%").split("%nl%")) {
            sendMessage(receiver, line);
        }
    }

    BedrockService<?> getBedrockService();

    /**
     * Closes a login connection whose premium verification expired without a verdict.
     * The default leaves the connection to the proxy; platforms that can close it
     * during login override this so the player gets a clear message instead of a hang.
     *
     * @param username exact login name
     * @param owner connection identity token used when the attempt was marked
     */
    default void disconnectPendingVerification(String username, Object owner) {
    }

    default ThreadFactory getThreadFactory() {
        return new ThreadFactoryBuilder()
                .setNameFormat(getName() + " Pool Thread #%1$d")
                // Hikari create daemons by default. We could use daemon threads for our own scheduler too
                // because we safely shut down
                .setDaemon(true)
                .build();
    }
}
