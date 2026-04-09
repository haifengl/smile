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
 * Matrix transpose operation passed to BLAS/LAPACK routines.
 *
 * <ul>
 *   <li>{@link #NO_TRANSPOSE}        — use the matrix as-is.</li>
 *   <li>{@link #TRANSPOSE}           — use the (real) transpose A<sup>T</sup>.</li>
 *   <li>{@link #CONJUGATE_TRANSPOSE} — use the conjugate transpose A<sup>H</sup>
 *       (same as transpose for real matrices).</li>
 * </ul>
 *
 * <p>Use {@link #flip(Transpose)} to toggle between {@code NO_TRANSPOSE} and
 * {@code TRANSPOSE}, which is needed when converting between row-major and
 * column-major representations.
 */
public enum Transpose {
    /** Normal (identity) operation on the matrix. */
    NO_TRANSPOSE(111, (byte) 'N'),
    /** Transpose the matrix: A becomes A<sup>T</sup>. */
    TRANSPOSE(112, (byte) 'T'),
    /** Conjugate-transpose the matrix: A becomes A<sup>H</sup>. */
    CONJUGATE_TRANSPOSE(113, (byte) 'C');

    /** Integer value passed to CBLAS. */
    private final int blas;
    /** Byte value passed to LAPACK. */
    private final byte lapack;

    /** Constructor. */
    Transpose(int blas, byte lapack) {
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
     * Returns a human-readable description of this transpose option.
     * @return a human-readable description.
     */
    public String description() {
        return switch (this) {
            case NO_TRANSPOSE        -> "No transpose (use A as-is)";
            case TRANSPOSE           -> "Transpose (use A^T)";
            case CONJUGATE_TRANSPOSE -> "Conjugate transpose (use A^H = conj(A^T))";
        };
    }

    /**
     * Toggles between {@link #NO_TRANSPOSE} and {@link #TRANSPOSE}, null-safe.
     * {@link #CONJUGATE_TRANSPOSE} is treated the same as {@link #TRANSPOSE} for
     * the purpose of flipping (returns {@link #NO_TRANSPOSE}).
     *
     * <p>This is useful when switching between row-major and column-major matrix
     * representations: a row-major {@code A} passed to a column-major BLAS call
     * requires flipping the transpose flag.
     *
     * @param value a {@code Transpose} value, may be {@code null}.
     * @return the flipped value, or {@code null} if the input is {@code null}.
     */
    public static Transpose flip(Transpose value) {
        return switch (value) {
            case null -> null;
            case NO_TRANSPOSE -> TRANSPOSE;
            case TRANSPOSE, CONJUGATE_TRANSPOSE -> NO_TRANSPOSE;
        };
    }

    /**
     * Returns the {@code Transpose} constant corresponding to the given CBLAS integer value.
     * @param value the CBLAS integer value ({@code 111}, {@code 112}, or {@code 113}).
     * @return the matching {@code Transpose} constant.
     * @throws IllegalArgumentException if the value does not match any constant.
     */
    public static Transpose fromBlas(int value) {
        for (Transpose t : values()) {
            if (t.blas == value) return t;
        }
        throw new IllegalArgumentException("Unknown CBLAS Transpose value: " + value);
    }

    /**
     * Returns the {@code Transpose} constant corresponding to the given LAPACK byte value.
     * @param value the LAPACK byte value ({@code 'N'}, {@code 'T'}, or {@code 'C'}).
     * @return the matching {@code Transpose} constant.
     * @throws IllegalArgumentException if the value does not match any constant.
     */
    public static Transpose fromLapack(byte value) {
        for (Transpose t : values()) {
            if (t.lapack == value) return t;
        }
        throw new IllegalArgumentException("Unknown LAPACK Transpose value: " + (char) value);
    }
}
