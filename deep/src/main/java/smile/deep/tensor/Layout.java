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
package smile.deep.tensor;

/**
 * The memory layout of a Tensor. The codes map to the {@code ST_Layout} values
 * exposed by the {@code smile_torch} native API. Note that the native API
 * currently distinguishes only strided, sparse COO, and sparse CSR layouts; the
 * compressed-block sparse variants (BSC, BSR, CSC) are mapped to the sparse CSR
 * code.
 */
public enum Layout {
    /** Dense tensor. */
    Strided(0),
    /** Sparse tensor in COO format. */
    SparseCOO(1),
    /** Sparse tensor in BSC format. */
    SparseBSC(2),
    /** Sparse tensor in BSR format. */
    SparseBSR(2),
    /** Sparse tensor in CSC format. */
    SparseCSC(2),
    /** Sparse tensor in CSR format. */
    SparseCSR(2);

    /** The native {@code ST_Layout} code. */
    final int code;

    /** Constructor. */
    Layout(int code) {
        this.code = code;
    }

    /**
     * Returns the native {@code ST_Layout} code.
     * @return the native {@code ST_Layout} code.
     */
    public int code() {
        return code;
    }
}
