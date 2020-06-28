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

package smile.math.matrix;

/**
 * Named matrix.
 *
 * @author Haifeng Li
 */
public abstract class MatrixBase {
    /**
     * The row names.
     */
    String[] rowNames;
    /**
     * The column names.
     */
    String[] colNames;

    /**
     * Returns the number of rows.
     */
    public abstract int nrows();

    /**
     * Returns the number of columns.
     */
    public abstract int ncols();

    /**
     * Returns the string representation of A[i, j].
     */
    public abstract String str(int i, int j);

    /** Returns the row names. */
    public String[] rowNames() {
        return rowNames;
    }

    /** Sets the row names. */
    public void rowNames(String[] names) {
        if (names != null && names.length != nrows()) {
            throw new IllegalArgumentException(String.format("Invalid row names length: %d != %d", names.length, nrows()));
        }
        rowNames = names;
    }

    /** Returns the column names. */
    public String[] colNames() {
        return colNames;
    }

    /** Sets the column names. */
    public void colNames(String[] names) {
        if (names != null && names.length != ncols()) {
            throw new IllegalArgumentException(String.format("Invalid column names length: %d != %d", names.length, ncols()));
        }
        colNames = names;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    /**
     * Returns the string representation of matrix.
     * @param full Print the full matrix if true. Otherwise,
     *             print only top left 7 x 7 submatrix.
     */
    public String toString(boolean full) {
        return full ? toString(nrows(), ncols()) : toString(7, 7);
    }

    /**
     * Returns the string representation of matrix.
     * @param m the number of rows to print.
     * @param n the number of columns to print.
     */
    public String toString(int m, int n) {
        StringBuilder sb = new StringBuilder(nrows() + " x " + ncols() + "\n");
        m = Math.min(m, nrows());
        n = Math.min(n, ncols());

        String newline = n < ncols() ? "...\n" : "\n";

        if (colNames != null) {
            sb.append(rowNames == null ? "   " : "          ");

            for (int j = 0; j < n; j++) {
                sb.append(String.format(" %10s", colNames[j]));
            }
            sb.append(newline);
        }

        for (int i = 0; i < m; i++) {
            sb.append(rowNames == null ? "   " : String.format("%-10s", rowNames[i]));

            for (int j = 0; j < n; j++) {
                sb.append(String.format(" %10s", str(i, j)));
            }
            sb.append(newline);
        }

        if (m < nrows()) {
            sb.append("  ...\n");
        }

        return sb.toString();
    }
}
