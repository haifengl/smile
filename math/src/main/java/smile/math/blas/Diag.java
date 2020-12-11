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
 * The flag if a triangular matrix has unit diagonal elements.
 * Some LAPACK routines have an option to handle unit triangular
 * matrices (that is, triangular matrices with diagonal elements = 1).
 * If the matrix is unit triangular, the diagonal elements of
 * the matrix need not be stored, and the corresponding array
 * elements are not referenced by the LAPACK routines.
 * The storage scheme for the rest of the matrix (whether
 * conventional, packed or band) remains unchanged.
 */
public enum Diag {
    /** Non-unit triangular. */
    NON_UNIT(131, (byte) 'N'),
    /** Unit triangular. */
    UNIT(132, (byte) 'U');

    /** The value passed to BLAS. */
    private final int blas;
    /** The value passed to LAPACK. */
    private final byte lapack;

    /**
     * Constructor.
     * @param blas The value passed to BLAS.
     * @param lapack The value passed to LAPACK.
     */
    Diag(int blas, byte lapack) {
        this.blas = blas;
        this.lapack = lapack;
    }

    /**
     * Returns the int value for BLAS.
     * @return the int value for BLAS.
     */
    public int blas() { return blas; }

    /**
     * Returns the value for LAPACK.
     * @return the value for LAPACK.
     */
    public byte lapack() { return lapack; }
}
