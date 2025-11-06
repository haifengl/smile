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
package smile.swing.table;

import smile.tensor.DenseMatrix;
import smile.tensor.Matrix;

/**
 * A table model for matrices with paging.
 *
 * @author Haifeng Li
 */
public class MatrixTableModel extends PageTableModel {
    /** The matrix. */
    final Matrix matrix;

    /**
     * Constructor.
     * @param matrix the matrix.
     */
    public MatrixTableModel(Matrix matrix) {
        this.matrix = matrix;
    }

    @Override
    public int getColumnCount() {
        return matrix.ncol();
    }

    @Override
    public int getRealRowCount() {
        return matrix.nrow();
    }

    @Override
    public String getColumnName(int col) {
        if (matrix instanceof DenseMatrix dense) {
            if (dense.colNames() != null) {
                return dense.colNames()[col];
            }
        }
        return "V" + (col + 1);
    }

    @Override
    public Object getValueAtRealRow(int row, int col) {
        return matrix.get(row, col);
    }
}
