/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.hayston1001.fastloginplus.shared.update;

/**
 * Compares release tags without depending on a platform or JSON library.
 */
public final class VersionComparator {

    private VersionComparator() {
        // utility class
    }

    /**
     * Compares two version strings numerically, for example {@code 0.1.0} and
     * {@code v0.0.7}. An optional {@code v} prefix and a pre-release suffix are
     * accepted; missing components count as zero.
     *
     * @param remote the remote version tag
     * @param local the current local version
     * @return true if the remote version is newer than the local version
     */
    public static boolean isNewer(String remote, String local) {
        int[] remoteParts = parseVersion(remote);
        int[] localParts = parseVersion(local);
        int maxLength = Math.max(remoteParts.length, localParts.length);
        for (int i = 0; i < maxLength; i++) {
            int remotePart = i < remoteParts.length ? remoteParts[i] : 0;
            int localPart = i < localParts.length ? localParts[i] : 0;
            if (remotePart > localPart) {
                return true;
            }
            if (remotePart < localPart) {
                return false;
            }
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        String cleaned = version.startsWith("v") ? version.substring(1) : version;
        String numeric = cleaned.split("-")[0];
        String[] parts = numeric.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
                result[i] = 0;
            }
        }
        return result;
    }
}
