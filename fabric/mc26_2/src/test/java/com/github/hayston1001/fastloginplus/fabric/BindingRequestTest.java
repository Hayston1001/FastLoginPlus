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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BindingRequestTest {
    @Test
    void verifiedPasswordHolderCanRequestConsent() {
        assertDoesNotThrow(() -> BindingRequest.requireBindable(
                Decision.PREMIUM_VERIFIED, true, AccountState.PASSWORD));
    }

    @Test
    void verifiedButUnauthenticatedConnectionStillCannotBind() {
        var error = assertThrows(FabricMessages.PlayerFailure.class,
                () -> BindingRequest.requireBindable(Decision.PREMIUM_VERIFIED, false, AccountState.PASSWORD));
        assertEquals("binding-login-required", error.getMessage());
    }

    @Test
    void offlineHolderCanRequestConsentButNeedsAuthentication() {
        assertDoesNotThrow(() -> BindingRequest.requireBindable(Decision.OFFLINE, true, AccountState.PASSWORD));
        assertThrows(FabricMessages.PlayerFailure.class,
                () -> BindingRequest.requireBindable(Decision.OFFLINE, false, AccountState.PASSWORD));
    }

    @Test
    void otherFailuresDoNotClaimThePlayerIsLoggedOut() {
        assertEquals("binding-identity-required", assertThrows(FabricMessages.PlayerFailure.class,
                () -> BindingRequest.requireBindable(Decision.UNDECIDED, true, AccountState.PASSWORD)).getMessage());
        for (AccountState state : new AccountState[]{AccountState.BOUND, AccountState.CONFLICT, AccountState.OPEN}) {
            assertEquals("binding-password-required", assertThrows(FabricMessages.PlayerFailure.class,
                    () -> BindingRequest.requireBindable(Decision.PREMIUM_VERIFIED, true, state)).getMessage());
        }
    }

    @Test
    void passwordLockExplanationMatchesPolicyOrRegistration() {
        UUID uuid = UUID.randomUUID();
        IdentityClaim claim = new IdentityClaim(Decision.PREMIUM_VERIFIED, "TestUser", uuid, uuid,
                new IdentityClaim.Facts(false, false, IdentityClaim.Consent.YES, true, false, false));
        assertEquals("registration-required", EasyAuth344Bridge.passwordLockMessage(claim,
                new AccountState.Snapshot(AccountState.OPEN, null)));
        IdentityClaim disabled = new IdentityClaim(Decision.PREMIUM_VERIFIED, "TestUser", uuid, uuid,
                new IdentityClaim.Facts(false, false, IdentityClaim.Consent.YES, false, false, false));
        assertEquals("premium-policy-disabled", EasyAuth344Bridge.passwordLockMessage(disabled,
                new AccountState.Snapshot(AccountState.PASSWORD, null)));
    }

    @Test
    void missingConsentHasItsOwnExplanationWithoutOverridingPolicyOrRegistration() {
        UUID uuid = UUID.randomUUID();
        for (IdentityClaim.Consent consent : new IdentityClaim.Consent[]{
                IdentityClaim.Consent.NO, IdentityClaim.Consent.UNKNOWN}) {
            IdentityClaim claim = new IdentityClaim(Decision.PREMIUM_VERIFIED, "TestUser", uuid, uuid,
                    new IdentityClaim.Facts(false, false, consent, true, false, false));
            assertEquals("binding-consent-required", EasyAuth344Bridge.passwordLockMessage(claim,
                    new AccountState.Snapshot(AccountState.PASSWORD, null)));
            assertEquals("registration-required", EasyAuth344Bridge.passwordLockMessage(claim,
                    new AccountState.Snapshot(AccountState.OPEN, null)));
            IdentityClaim disabled = new IdentityClaim(Decision.PREMIUM_VERIFIED, "TestUser", uuid, uuid,
                    new IdentityClaim.Facts(false, false, consent, false, false, false));
            assertEquals("premium-policy-disabled", EasyAuth344Bridge.passwordLockMessage(disabled,
                    new AccountState.Snapshot(AccountState.PASSWORD, null)));
        }
    }
}
