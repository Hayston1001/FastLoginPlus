/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

/** A Bedrock claim that cannot be authenticated or is no longer usable. */
final class BedrockClaimException extends IllegalArgumentException {

    BedrockClaimException(String message) {
        super(message);
    }

    BedrockClaimException(String message, Throwable cause) {
        super(message, cause);
    }
}
