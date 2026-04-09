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
 * The option controlling whether eigenvectors are computed by LAPACK
 * eigenvalue routines (e.g., {@code dgeev}, {@code dsyevd}).
 *
 * <ul>
 *   <li>{@link #NO_VECTORS} — only eigenvalues are computed (faster, less memory).</li>
 *   <li>{@link #VECTORS}    — both eigenvalues and eigenvectors are computed.</li>
 * </ul>
 */
public enum EVDJob {
    /** Eigenvalues only are computed. */
    NO_VECTORS((byte) 'N'),
    /** Both eigenvalues and eigenvectors are computed. */
    VECTORS((byte) 'V');

    /** Byte value passed to LAPACK. */
    private final byte lapack;

    /** Constructor. */
    EVDJob(byte lapack) {
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
            case NO_VECTORS -> "Eigenvalues only";
            case VECTORS    -> "Eigenvalues and eigenvectors";
        };
    }

    /**
     * Returns the {@code EVDJob} constant corresponding to the given LAPACK byte value.
     * @param value the LAPACK byte value ({@code 'N'} or {@code 'V'}).
     * @return the matching {@code EVDJob} constant.
     * @throws IllegalArgumentException if the value does not match any constant.
     */
    public static EVDJob fromLapack(byte value) {
        for (EVDJob j : values()) {
            if (j.lapack == value) return j;
        }
        throw new IllegalArgumentException("Unknown LAPACK EVDJob value: " + (char) value);
    }

    /**
     * Returns the appropriate {@code EVDJob} for a boolean flag.
     * @param vectors {@code true} to compute eigenvectors, {@code false} for eigenvalues only.
     * @return {@link #VECTORS} or {@link #NO_VECTORS}.
     */
    public static EVDJob of(boolean vectors) {
        return vectors ? VECTORS : NO_VECTORS;
    }
}
