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

/** The option if computing singular vectors. */
public enum SVDJob {
    /** All left (or right) singular vectors are returned in supplied  matrix U (or Vt). */
    ALL((byte) 'A'),
    /** The first min(m, n) singular vectors are returned in supplied matrix U (or Vt). */
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
}
