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
 * Which triangular part of a symmetric or triangular matrix is stored.
 *
 * <p>When a matrix is symmetric, Hermitian, or triangular, only one triangular
 * half needs to be stored. BLAS and LAPACK use this flag to indicate which half
 * is present in memory:
 * <ul>
 *   <li>{@link #UPPER} — only the upper triangular part (including the diagonal) is stored.</li>
 *   <li>{@link #LOWER} — only the lower triangular part (including the diagonal) is stored.</li>
 * </ul>
 *
 * <p>Use {@link #flip(UPLO)} to convert between the two, which is required when
 * switching between row-major and column-major layouts (the upper triangle of a
 * row-major matrix corresponds to the lower triangle in column-major layout).
 */
public enum UPLO {
    /**
     * Upper triangular part is stored (including diagonal).
     * The lower triangle is inferred by symmetry and need not be present in memory.
     */
    UPPER(121, (byte) 'U'),
    /**
     * Lower triangular part is stored (including diagonal).
     * The upper triangle is inferred by symmetry and need not be present in memory.
     */
    LOWER(122, (byte) 'L');

    /** Integer value passed to CBLAS. */
    private final int blas;
    /** Byte value passed to LAPACK. */
    private final byte lapack;

    /** Constructor. */
    UPLO(int blas, byte lapack) {
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
     * Returns {@code true} if the upper triangle is stored.
     * @return {@code true} if upper.
     */
    public boolean isUpper() { return this == UPPER; }

    /**
     * Returns {@code true} if the lower triangle is stored.
     * @return {@code true} if lower.
     */
    public boolean isLower() { return this == LOWER; }

    /**
     * Returns a human-readable description of this storage flag.
     * @return a human-readable description.
     */
    public String description() {
        return switch (this) {
            case UPPER -> "Upper triangular part stored";
            case LOWER -> "Lower triangular part stored";
        };
    }

    /**
     * Flips between {@link #UPPER} and {@link #LOWER}, null-safe.
     *
     * <p>This is useful when switching between row-major and column-major
     * representations: the upper triangle of a row-major matrix corresponds
     * to the lower triangle when the same data is interpreted in column-major.
     *
     * @param value an {@code UPLO} value, may be {@code null}.
     * @return the flipped value, or {@code null} if the input is {@code null}.
     */
    public static UPLO flip(UPLO value) {
        return switch (value) {
            case null  -> null;
            case UPPER -> LOWER;
            case LOWER -> UPPER;
        };
    }

    /**
     * Returns the {@code UPLO} constant corresponding to the given CBLAS integer value.
     * @param value the CBLAS integer value ({@code 121} for upper, {@code 122} for lower).
     * @return the matching {@code UPLO} constant.
     * @throws IllegalArgumentException if the value does not match any constant.
     */
    public static UPLO fromBlas(int value) {
        for (UPLO u : values()) {
            if (u.blas == value) return u;
        }
        throw new IllegalArgumentException("Unknown CBLAS UPLO value: " + value);
    }

    /**
     * Returns the {@code UPLO} constant corresponding to the given LAPACK byte value.
     * @param value the LAPACK byte value ({@code 'U'} or {@code 'L'}).
     * @return the matching {@code UPLO} constant.
     * @throws IllegalArgumentException if the value does not match any constant.
     */
    public static UPLO fromLapack(byte value) {
        for (UPLO u : values()) {
            if (u.lapack == value) return u;
        }
        throw new IllegalArgumentException("Unknown LAPACK UPLO value: " + (char) value);
    }
}
