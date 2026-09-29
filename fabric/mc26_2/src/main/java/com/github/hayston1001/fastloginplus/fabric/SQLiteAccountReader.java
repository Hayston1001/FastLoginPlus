/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.fabric;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/** Reads EasyAuth's pinned SQLite schema without treating a failed query as a missing account. */
final class SQLiteAccountReader {

    private SQLiteAccountReader() {
    }

    static Row read(Connection connection, String table, String name, boolean caseInsensitive) {
        if (connection == null || table == null || table.isEmpty()) {
            throw new IllegalStateException("EasyAuth SQLite connection or table is unavailable");
        }
        String column = caseInsensitive ? "username" : "username_lower";
        String lookup = caseInsensitive ? name : name.toLowerCase(Locale.ENGLISH);
        String sql = "SELECT username, username_lower, uuid, data FROM \""
                + table.replace("\"", "\"\"") + "\" WHERE " + column + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lookup);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return null;
                }
                Row selected = row(results);
                while (!name.equals(selected.username()) && results.next()) {
                    Row candidate = row(results);
                    if (name.equals(candidate.username())) {
                        selected = candidate;
                    }
                }
                return selected;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("EasyAuth SQLite account query failed", failure);
        }
    }

    private static Row row(ResultSet results) throws SQLException {
        return new Row(results.getString("username"), results.getString("username_lower"),
                results.getString("uuid"), results.getString("data"));
    }

    record Row(String username, String usernameLowerCase, String uuid, String data) {
    }
}
