/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.function.Consumer;

/** Classifies every rejected Fabric login so both stages answer and log it the same way. */
final class FabricLoginFailure {

    enum Kind {
        POLICY_DENIED,
        INVALID_CLAIM,
        AUTH_UNAVAILABLE,
        INTERNAL_ERROR
    }

    private FabricLoginFailure() {
    }

    static Kind classify(Throwable failure) {
        if (policyFailure(failure) != null) {
            return Kind.POLICY_DENIED;
        }
        if (causeOf(failure, BedrockClaimException.class) != null) {
            return Kind.INVALID_CLAIM;
        }
        if (causeOf(failure, AuthUnavailableException.class) != null) {
            return Kind.AUTH_UNAVAILABLE;
        }
        if (failure instanceof IllegalArgumentException) {
            // Java identity parsing throws plain IllegalArgumentException.
            return Kind.INVALID_CLAIM;
        }
        return Kind.INTERNAL_ERROR;
    }

    static String messageKey(Throwable failure) {
        Kind kind = classify(failure);
        if (kind == Kind.POLICY_DENIED) {
            return policyFailure(failure).key();
        }
        if (kind == Kind.INVALID_CLAIM) {
            return "invalid-identity";
        }
        return "auth-unavailable";
    }

    static Component clientMessage(Throwable failure) {
        if (classify(failure) == Kind.POLICY_DENIED) {
            return FabricMessages.reason(failure, "bedrock-unsupported");
        }
        return FabricMessages.text(messageKey(failure));
    }

    static void reject(Logger logger, String player, Throwable failure, Consumer<Component> disconnect) {
        log(logger, player, failure);
        disconnect.accept(clientMessage(failure));
    }

    static void log(Logger logger, String player, Throwable failure) {
        Kind kind = classify(failure);
        if (kind == Kind.POLICY_DENIED) {
            logger.warn("Denied login for {}: {}", player, describe(failure, kind));
        } else if (kind == Kind.INVALID_CLAIM) {
            logger.warn("Rejected identity claim for {}: {}", player, describe(failure, kind));
        } else if (kind == Kind.AUTH_UNAVAILABLE) {
            logger.warn("Could not verify the EasyAuth account of {}: {}", player, describe(failure, kind));
        } else {
            logger.error("Unexpected failure while verifying " + player, failure);
        }
    }

    private static String describe(Throwable failure, Kind kind) {
        if (kind == Kind.POLICY_DENIED) {
            return policyFailure(failure).key();
        }
        if (kind == Kind.INVALID_CLAIM) {
            BedrockClaimException claim = causeOf(failure, BedrockClaimException.class);
            return message(claim == null ? failure : claim);
        }
        String message = message(failure);
        if (kind != Kind.AUTH_UNAVAILABLE) {
            return message;
        }
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root == failure) {
            return message;
        }
        String rootMessage = root.getMessage();
        return message + " (caused by " + root.getClass().getSimpleName()
                + (rootMessage == null || rootMessage.isBlank() ? "" : ": " + rootMessage) + ")";
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static FabricMessages.PlayerFailure policyFailure(Throwable failure) {
        return causeOf(failure, FabricMessages.PlayerFailure.class);
    }

    private static <T extends Throwable> T causeOf(Throwable failure, Class<T> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        return null;
    }
}
