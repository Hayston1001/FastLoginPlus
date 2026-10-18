/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenameFollowTest {
    private static final UUID VERIFIED = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final RenameFollow.Row DEFAULT = row("NewName", "", "UNKNOWN", null);
    private static final RenameFollow.Row OLD = row("OldName", "", "TRUE", VERIFIED);

    @Test
    void noOldAccountLeavesDefaultAlone() {
        assertResult(RenameFollow.Action.NONE, List.of(), DEFAULT, List.of(DEFAULT));
    }

    @Test
    void oneMatchingBoundAccountMigratesItsActualName() {
        assertResult(RenameFollow.Action.MIGRATE, List.of("OldName"), DEFAULT, List.of(DEFAULT, OLD));
    }

    @Test
    void multipleMatchingAccountsConflictRegardlessOfOrder() {
        var another = row("AnotherName", "", "TRUE", VERIFIED);
        assertResult(RenameFollow.Action.CONFLICT, List.of("AnotherName", "OldName"),
                DEFAULT, List.of(OLD, DEFAULT, another));
    }

    @Test
    void currentBoundIdentityIsAlreadyDoneEvenWithDuplicateOldRows() {
        assertResult(RenameFollow.Action.NONE, List.of(), row("NewName", "", "TRUE", VERIFIED),
                List.of(OLD, row("Duplicate", "", "TRUE", VERIFIED)));
    }

    @ParameterizedTest
    @CsvSource({"hash, UNKNOWN", "hash, FALSE", "hash, TRUE", "'', FALSE", "'', TRUE", "'', invalid"})
    void nonReplaceableCurrentAccountNeverMigrates(String password, String kind) {
        assertResult(RenameFollow.Action.NONE, List.of(), row("NewName", password, kind, OTHER), List.of(OLD));
    }

    @Test
    void nullPasswordIsNotAnEmptyPassword() {
        assertResult(RenameFollow.Action.NONE, List.of(), row("NewName", null, "UNKNOWN", null), List.of(OLD));
    }

    @Test
    void currentNameComparisonIgnoresCase() {
        assertResult(RenameFollow.Action.NONE, List.of(), DEFAULT,
                List.of(row("nEwNaMe", "", "TRUE", VERIFIED)));
    }

    @Test
    void onlyTrueRowsWithTheVerifiedUuidMatch() {
        assertResult(RenameFollow.Action.NONE, List.of(), DEFAULT, List.of(
                row("Password", "hash", "FALSE", VERIFIED), row("Unknown", "", "UNKNOWN", VERIFIED),
                row("OtherUuid", "", "TRUE", OTHER), row("NoUuid", "", "TRUE", null)));
    }

    @Test
    void boundSourcePasswordIsPreservedRatherThanUsedToFilterOwnership() {
        assertResult(RenameFollow.Action.MIGRATE, List.of("OldName"), DEFAULT,
                List.of(row("OldName", "hash", "TRUE", VERIFIED)));
    }

    private static RenameFollow.Row row(String name, String password, String kind, UUID uuid) {
        return new RenameFollow.Row(name, password, kind, uuid);
    }

    private static void assertResult(RenameFollow.Action action, List<String> names, RenameFollow.Row current,
                                     List<RenameFollow.Row> rows) {
        assertEquals(new RenameFollow.Result(action, names), RenameFollow.decide("NewName", current, rows, VERIFIED));
    }
}
