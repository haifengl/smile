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

/** THe option of eigenvalue range. */
public enum EigenRange {
    /**
     * All eigenvalues will be found.
     */
    ALL((byte) 'A'),
    /**
     * All eigenvalues in the half-open interval (VL,VU]
     * will be found.
     */
    VALUE((byte) 'V'),
    /**
     * The IL-th through IU-th eigenvalues will be found.
     */
    INDEX((byte) 'I');

    /** Byte value passed to LAPACK. */
    private final byte lapack;

    /** Constructor. */
    EigenRange(byte lapack) {
        this.lapack = lapack;
    }

    /**
     * Returns the byte value for LAPACK.
     * @return the byte value for LAPACK.
     */
    public byte lapack() { return lapack; }
}
