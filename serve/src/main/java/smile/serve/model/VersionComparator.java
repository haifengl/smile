/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.serve.model;

import java.util.Comparator;

/**
 * Numeric- and SemVer-aware comparator for model version strings.
 *
 * <p>Versions are split on the first {@code '-'} into a core segment list and an
 * optional prerelease tag:
 * <ul>
 *   <li>Core segments (split on {@code '.'}) are compared numerically when both
 *       segments consist solely of ASCII digits (so {@code "2" < "10"} and
 *       {@code "1.2.0" < "1.10.0"}), and lexicographically otherwise.</li>
 *   <li>A version without a prerelease suffix sorts higher than one with a
 *       prerelease suffix sharing the same core (e.g. {@code "1.0.0-rc1" < "1.0.0"}).</li>
 *   <li>Build metadata after {@code '+'} is ignored during precedence comparison,
 *       with raw lexicographic comparison as a final tie-breaker so
 *       {@code compare(a, b) == 0} holds only when {@code a.equals(b)}.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
public final class VersionComparator implements Comparator<String> {

    /** Shared singleton instance. */
    public static final VersionComparator INSTANCE = new VersionComparator();

    private VersionComparator() {
    }

    @Override
    public int compare(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        String aNoBuild = stripBuildMetadata(a);
        String bNoBuild = stripBuildMetadata(b);

        int aDash = aNoBuild.indexOf('-');
        int bDash = bNoBuild.indexOf('-');
        String aCore = aDash >= 0 ? aNoBuild.substring(0, aDash) : aNoBuild;
        String bCore = bDash >= 0 ? bNoBuild.substring(0, bDash) : bNoBuild;
        String aPre = aDash >= 0 ? aNoBuild.substring(aDash + 1) : null;
        String bPre = bDash >= 0 ? bNoBuild.substring(bDash + 1) : null;

        int cmp = compareDotSegments(aCore, bCore);
        if (cmp != 0) {
            return cmp;
        }

        if (aPre == null && bPre != null) {
            return 1;
        }
        if (aPre != null && bPre == null) {
            return -1;
        }
        if (aPre != null) {
            cmp = compareDotSegments(aPre, bPre);
            if (cmp != 0) {
                return cmp;
            }
        }

        return a.compareTo(b);
    }

    private static String stripBuildMetadata(String v) {
        int plus = v.indexOf('+');
        return plus >= 0 ? v.substring(0, plus) : v;
    }

    private static int compareDotSegments(String a, String b) {
        String[] aParts = a.split("\\.", -1);
        String[] bParts = b.split("\\.", -1);
        int len = Math.min(aParts.length, bParts.length);
        for (int i = 0; i < len; i++) {
            int cmp = compareSegment(aParts[i], bParts[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(aParts.length, bParts.length);
    }

    private static int compareSegment(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        boolean aNum = isAllDigits(a);
        boolean bNum = isAllDigits(b);
        if (aNum && bNum) {
            return compareUnsignedDigits(a, b);
        }
        if (aNum) {
            return -1;
        }
        if (bNum) {
            return 1;
        }
        return a.compareTo(b);
    }

    private static boolean isAllDigits(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static int compareUnsignedDigits(String a, String b) {
        int aStart = skipLeadingZeros(a);
        int bStart = skipLeadingZeros(b);
        int aLen = a.length() - aStart;
        int bLen = b.length() - bStart;
        if (aLen != bLen) {
            return Integer.compare(aLen, bLen);
        }
        for (int i = 0; i < aLen; i++) {
            int diff = a.charAt(aStart + i) - b.charAt(bStart + i);
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    private static int skipLeadingZeros(String s) {
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') {
            i++;
        }
        return i;
    }
}
