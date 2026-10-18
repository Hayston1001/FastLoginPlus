/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Pure rename decision for an already verified premium identity. */
final class RenameFollow {
    enum Action {
        NONE, MIGRATE, CONFLICT
    }

    record Row(String name, String password, String onlineAccount, UUID uuid) {
    }

    record Result(Action action, List<String> names) {
    }

    private RenameFollow() {
    }

    static boolean needsLookup(Row current, UUID verifiedUuid) {
        if ("TRUE".equals(current.onlineAccount()) && verifiedUuid.equals(current.uuid())) {
            return false;
        }
        return "".equals(current.password()) && "UNKNOWN".equals(current.onlineAccount());
    }

    static Result decide(String currentName, Row current, Collection<Row> rows, UUID verifiedUuid) {
        if (!needsLookup(current, verifiedUuid)) {
            return new Result(Action.NONE, List.of());
        }
        List<String> matches = rows.stream()
                .filter(row -> !currentName.equalsIgnoreCase(row.name()))
                .filter(row -> "TRUE".equals(row.onlineAccount()) && verifiedUuid.equals(row.uuid()))
                .map(Row::name).sorted().toList();
        Action action = matches.isEmpty() ? Action.NONE : matches.size() == 1 ? Action.MIGRATE : Action.CONFLICT;
        return new Result(action, matches);
    }
}
