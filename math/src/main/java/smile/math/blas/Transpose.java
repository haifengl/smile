/*******************************************************************************
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
 ******************************************************************************/

package smile.math.blas;

/** Matrix transpose. */
public enum Transpose {
    /** Normal operation on the matrix. */
    NO_TRANSPOSE((byte) 111),
    /** Transpose operation on the matrix. */
    TRANSPOSE((byte) 112),
    /** Conjugate transpose operation on the matrix. */
    CONJUGATE_TRANSPOSE((byte) 113);

    /** Byte value passed to CBLAS. */
    private final byte value;

    /** Constructor. */
    Transpose(byte value) {
        this.value = value;
    }

    /** Returns the byte value for BLAS. */
    public byte getValue() { return value; }
}
