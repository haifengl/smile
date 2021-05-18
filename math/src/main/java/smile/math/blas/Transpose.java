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

/** Matrix transpose. */
public enum Transpose {
    /** Normal operation on the matrix. */
    NO_TRANSPOSE(111, (byte) 'N'),
    /** Transpose operation on the matrix. */
    TRANSPOSE(112, (byte) 'T'),
    /** Conjugate transpose operation on the matrix. */
    CONJUGATE_TRANSPOSE(113, (byte) 'C');

    /** Byte value passed to BLAS. */
    private final int blas;
    /** Byte value passed to LAPACK. */
    private final byte lapack;

    /** Constructor. */
    Transpose(int blas, byte lapack) {
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
