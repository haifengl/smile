/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.linalg;

/**
 * The format of packed matrix storage. The packed storage format compactly
 * stores matrix elements when only one part of the matrix, the upper or lower
 * triangle, is necessary to determine all the elements of the matrix.
 * This is the case when the matrix is upper triangular, lower triangular,
 * symmetric, or Hermitian.
 */
public enum UPLO {
    /**
     * Upper triangle is stored. The packed storage format compactly stores
     * matrix elements when only one part of the matrix, the upper or lower
     * triangle, is necessary to determine all the elements of the matrix.
     * This is the case when the matrix is upper triangular, lower triangular,
     * symmetric, or Hermitian.
     */
    UPPER(121, (byte) 'U'),
    /**
     * Lower triangle is stored. The packed storage format compactly stores
     * matrix elements when only one part of the matrix, the upper or lower
     * triangle, is necessary to determine all the elements of the matrix.
     * This is the case when the matrix is upper triangular, lower triangular,
     * symmetric, or Hermitian.
     */
    LOWER(122, (byte) 'L');

    /** Byte value passed to BLAS. */
    private final int blas;
    /** Byte value passed to LAPACK. */
    private final byte lapack;

    /** Constructor. */
    UPLO(int blas, byte lapack) {
        this.blas = blas;
        this.lapack = lapack;
    }

    /**
     * Returns the int value for BLAS.
     * @return the int value for BLAS.
     */
    public int blas() { return lapack; }

    /**
     * Returns the byte value for LAPACK.
     * @return the byte value for LAPACK.
     */
    public byte lapack() { return lapack; }

    /** Flips the value, null safe. */
    public static UPLO flip(UPLO value) {
        return switch (value) {
            case null -> null;
            case UPPER -> LOWER;
            case LOWER -> UPPER;
        };
    }
}
