/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim.Decision;
import com.github.hayston1001.fastloginplus.fabric.protocol.IdentityClaim;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingRequestTest {
    @Test
    void verifiedPasswordHolderCanBindAfterExpiryOrRestart() {
        assertTrue(BindingRequest.completeNow(Decision.PREMIUM_VERIFIED, true, AccountState.PASSWORD));
        assertTrue(BindingRequest.completeNow(Decision.PREMIUM_VERIFIED, true, AccountState.BIND_PENDING));
    }

    @Test
    void verifiedButUnauthenticatedConnectionStillCannotBind() {
        var error = assertThrows(FabricMessages.PlayerFailure.class,
                () -> BindingRequest.completeNow(Decision.PREMIUM_VERIFIED, false, AccountState.PASSWORD));
        assertEquals("binding-login-required", error.getMessage());
    }

    @Test
    void offlineHolderStillQueuesVerificationAndNeedsAuthentication() {
        assertFalse(BindingRequest.completeNow(Decision.OFFLINE, true, AccountState.PASSWORD));
        assertThrows(FabricMessages.PlayerFailure.class,
                () -> BindingRequest.completeNow(Decision.OFFLINE, false, AccountState.PASSWORD));
    }

    @Test
    void otherFailuresDoNotClaimThePlayerIsLoggedOut() {
        assertEquals("binding-identity-required", assertThrows(FabricMessages.PlayerFailure.class,
                () -> BindingRequest.completeNow(Decision.UNDECIDED, true, AccountState.PASSWORD)).getMessage());
        for (AccountState state : new AccountState[]{AccountState.BOUND, AccountState.CONFLICT, AccountState.OPEN}) {
            assertEquals("binding-password-required", assertThrows(FabricMessages.PlayerFailure.class,
                    () -> BindingRequest.completeNow(Decision.PREMIUM_VERIFIED, true, state)).getMessage());
        }
    }

    @Test
    void passwordLockExplanationMatchesWindowPolicyOrRegistration() {
        UUID uuid = UUID.randomUUID();
        IdentityClaim claim = new IdentityClaim(Decision.PREMIUM_VERIFIED, "TestUser", uuid, uuid,
                new IdentityClaim.Facts(false, false, IdentityClaim.Consent.YES, true, false, false));
        assertEquals("binding-window-unavailable", EasyAuth344Bridge.passwordLockMessage(claim,
                new AccountState.Snapshot(AccountState.PASSWORD, null)));
        assertEquals("binding-window-unavailable", EasyAuth344Bridge.passwordLockMessage(claim,
                new AccountState.Snapshot(AccountState.BIND_PENDING, null)));
        assertEquals("registration-required", EasyAuth344Bridge.passwordLockMessage(claim,
                new AccountState.Snapshot(AccountState.OPEN, null)));
        IdentityClaim disabled = new IdentityClaim(Decision.PREMIUM_VERIFIED, "TestUser", uuid, uuid,
                new IdentityClaim.Facts(false, false, IdentityClaim.Consent.YES, false, false, false));
        assertEquals("premium-policy-disabled", EasyAuth344Bridge.passwordLockMessage(disabled,
                new AccountState.Snapshot(AccountState.PASSWORD, null)));
    }
}
