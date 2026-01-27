/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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

import org.bytedeco.pytorch.global.torch;

/** The memory layout of a Tensor. */
public enum Layout {
    /** Dense tensor. */
    Strided (torch.Layout.Strided),
    /** Sparse tensor in COO format. */
    SparseCOO(torch.Layout.Sparse),
    /** Sparse tensor in BSC format. */
    SparseBSC(torch.Layout.SparseBsc),
    /** Sparse tensor in BSR format. */
    SparseBSR(torch.Layout.SparseBsr),
    /** Sparse tensor in CSC format. */
    SparseCSC(torch.Layout.SparseCsc),
    /** Sparse tensor in CSR format. */
    SparseCSR(torch.Layout.SparseCsr);

    /** PyTorch tensor layout type. */
    final torch.Layout value;

    /** Constructor. */
    Layout(torch.Layout layout) {
        this.value = layout;
    }
}
