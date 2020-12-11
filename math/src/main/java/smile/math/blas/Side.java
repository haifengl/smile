/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.math.blas;

/**
 * The flag if the symmetric  matrix A appears on the left or right
 * in the matrix-matrix operation.
 */
public enum Side {
    /** A * B */
    LEFT (141, (byte) 'L'),
    /** B * A */
    RIGHT(142, (byte) 'R');

    /** Byte value passed to BLAS. */
    private final int blas;
    /** Byte value passed to LAPACK. */
    private final byte lapack;

    /** Constructor. */
    Side(int blas, byte lapack) {
        this.blas = blas;
        this.lapack = lapack;
    }

    /**
     * Returns the int value for BLAS.
     * @return the int value for BLAS.
     */
    public int blas() { return blas; }

    /**
     * Returns the byte value for LAPACK.
     * @return the byte value for LAPACK.
     */
    public byte lapack() { return lapack; }
}
