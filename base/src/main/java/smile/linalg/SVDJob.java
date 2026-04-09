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
 * The option controlling which singular vectors are computed by LAPACK
 * routines such as {@code dgesdd} and {@code dgesvd}.
 *
 * <ul>
 *   <li>{@link #ALL}        — all left (or right) singular vectors are computed.</li>
 *   <li>{@link #COMPACT}    — the first {@code min(m,n)} singular vectors are computed.</li>
 *   <li>{@link #OVERWRITE}  — the first {@code min(m,n)} singular vectors overwrite matrix A.</li>
 *   <li>{@link #NO_VECTORS} — no singular vectors are computed (values only).</li>
 * </ul>
 */
public enum SVDJob {
    /** All left (or right) singular vectors are returned in the supplied matrix U (or Vt). */
    ALL((byte) 'A'),
    /** The first min(m, n) singular vectors are returned in the supplied matrix U (or Vt). */
    COMPACT((byte) 'S'),
    /** The first min(m, n) singular vectors are overwritten on the matrix A. */
    OVERWRITE((byte) 'O'),
    /** No singular vectors are computed. */
    NO_VECTORS((byte) 'N');

    /** Byte value passed to LAPACK. */
    private final byte lapack;

    /** Constructor. */
    SVDJob(byte lapack) {
        this.lapack = lapack;
    }

    /**
     * Returns the byte value for LAPACK.
     * @return the byte value for LAPACK.
     */
    public byte lapack() { return lapack; }

    /**
     * Returns a human-readable description of this job option.
     * @return a human-readable description.
     */
    public String description() {
        return switch (this) {
            case ALL        -> "All singular vectors computed";
            case COMPACT    -> "First min(m,n) singular vectors computed";
            case OVERWRITE  -> "First min(m,n) singular vectors overwrite A";
            case NO_VECTORS -> "No singular vectors (values only)";
        };
    }

    /**
     * Returns the {@code SVDJob} constant corresponding to the given LAPACK byte value.
     * @param value the LAPACK byte value ({@code 'A'}, {@code 'S'}, {@code 'O'}, or {@code 'N'}).
     * @return the matching {@code SVDJob} constant.
     * @throws IllegalArgumentException if the value does not match any constant.
     */
    public static SVDJob fromLapack(byte value) {
        for (SVDJob j : values()) {
            if (j.lapack == value) return j;
        }
        throw new IllegalArgumentException("Unknown LAPACK SVDJob value: " + (char) value);
    }

    /**
     * Returns the appropriate {@code SVDJob} for a boolean flag.
     * Chooses {@link #COMPACT} when vectors are requested (the most common case),
     * and {@link #NO_VECTORS} otherwise.
     * @param vectors {@code true} to compute the compact set of singular vectors.
     * @return {@link #COMPACT} or {@link #NO_VECTORS}.
     */
    public static SVDJob of(boolean vectors) {
        return vectors ? COMPACT : NO_VECTORS;
    }
}
