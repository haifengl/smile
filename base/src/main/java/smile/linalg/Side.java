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
 * The side on which a symmetric matrix appears in a matrix-matrix operation.
 *
 * <p>Used by BLAS routines such as {@code dsymm} / {@code ssymm}:
 * <ul>
 *   <li>{@link #LEFT}  — the symmetric matrix multiplies on the left:  {@code C := alpha*A*B + beta*C}</li>
 *   <li>{@link #RIGHT} — the symmetric matrix multiplies on the right: {@code C := alpha*B*A + beta*C}</li>
 * </ul>
 */
public enum Side {
    /** Symmetric matrix A appears on the left: {@code C = A * B}. */
    LEFT (141, (byte) 'L'),
    /** Symmetric matrix A appears on the right: {@code C = B * A}. */
    RIGHT(142, (byte) 'R');

    /** Integer value passed to CBLAS. */
    private final int blas;
    /** Byte value passed to LAPACK. */
    private final byte lapack;

    /** Constructor. */
    Side(int blas, byte lapack) {
        this.blas = blas;
        this.lapack = lapack;
    }

    /**
     * Returns the integer value for CBLAS.
     * @return the CBLAS integer value.
     */
    public int blas() { return blas; }

    /**
     * Returns the byte value for LAPACK.
     * @return the LAPACK byte value.
     */
    public byte lapack() { return lapack; }

    /**
     * Returns a human-readable description of this side option.
     * @return a human-readable description.
     */
    public String description() {
        return switch (this) {
            case LEFT  -> "Symmetric matrix on the left: C = alpha*A*B + beta*C";
            case RIGHT -> "Symmetric matrix on the right: C = alpha*B*A + beta*C";
        };
    }

    /**
     * Returns the {@code Side} constant corresponding to the given CBLAS integer value.
     * @param value the CBLAS integer value ({@code 141} or {@code 142}).
     * @return the matching {@code Side} constant.
     * @throws IllegalArgumentException if the value does not match any constant.
     */
    public static Side fromBlas(int value) {
        for (Side s : values()) {
            if (s.blas == value) return s;
        }
        throw new IllegalArgumentException("Unknown CBLAS Side value: " + value);
    }

    /**
     * Returns the {@code Side} constant corresponding to the given LAPACK byte value.
     * @param value the LAPACK byte value ({@code 'L'} or {@code 'R'}).
     * @return the matching {@code Side} constant.
     * @throws IllegalArgumentException if the value does not match any constant.
     */
    public static Side fromLapack(byte value) {
        for (Side s : values()) {
            if (s.lapack == value) return s;
        }
        throw new IllegalArgumentException("Unknown LAPACK Side value: " + (char) value);
    }
}
