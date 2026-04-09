/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.linalg;

/**
 * The option of eigenvalue range for LAPACK symmetric eigensolver routines
 * (e.g., {@code dsyevr}, {@code ssyevr}).
 *
 * <ul>
 *   <li>{@link #ALL}   — compute all eigenvalues.</li>
 *   <li>{@link #VALUE} — compute eigenvalues in the half-open interval {@code (VL, VU]}.</li>
 *   <li>{@link #INDEX} — compute the IL-th through IU-th eigenvalues.</li>
 * </ul>
 */
public enum EigenRange {
    /**
     * All eigenvalues will be found.
     */
    ALL((byte) 'A'),
    /**
     * All eigenvalues in the half-open interval (VL, VU] will be found.
     */
    VALUE((byte) 'V'),
    /**
     * The IL-th through IU-th eigenvalues will be found.
     */
    INDEX((byte) 'I');

    /** Byte value passed to LAPACK. */
    private final byte lapack;

    /** Constructor. */
    EigenRange(byte lapack) {
        this.lapack = lapack;
    }

    /**
     * Returns the byte value for LAPACK.
     * @return the byte value for LAPACK.
     */
    public byte lapack() { return lapack; }

    /**
     * Returns a human-readable description of this range type.
     * @return a human-readable description.
     */
    public String description() {
        return switch (this) {
            case ALL   -> "All eigenvalues";
            case VALUE -> "Eigenvalues in interval (VL, VU]";
            case INDEX -> "Eigenvalues with index IL through IU";
        };
    }

    /**
     * Returns the {@code EigenRange} constant corresponding to the given LAPACK byte value.
     * @param value the LAPACK byte value ({@code 'A'}, {@code 'V'}, or {@code 'I'}).
     * @return the matching {@code EigenRange} constant.
     * @throws IllegalArgumentException if the value does not match any constant.
     */
    public static EigenRange fromLapack(byte value) {
        for (EigenRange r : values()) {
            if (r.lapack == value) return r;
        }
        throw new IllegalArgumentException("Unknown LAPACK EigenRange value: " + (char) value);
    }
}
