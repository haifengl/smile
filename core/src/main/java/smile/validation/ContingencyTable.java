/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.validation;

import smile.math.MathEx;

import java.util.HashSet;
import java.util.Set;

/**
 * The contingency table of two clusterings.
 *
 * @author owlmsj
 */
class ContingencyTable {

    /** The number of observations. */
    public final int n;
    /** The number of clusters of first clustering. */
    public final int n1;
    /** The number of clusters of second clustering. */
    public final int n2;
    /** The row sum of contingency table. */
    public final int[] a;
    /** The column sum of contingency table. */
    public final int[] b;
    /** The contingency table. */
    public final int[][] table;

    /** Creates the contingency table. */
    public ContingencyTable(int[] y1, int[] y2) {
        if (y1.length != y2.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", y1.length, y2.length));
        }

        // Get # of non-zero classes in each solution
        n = y1.length;

        int[] label1 = MathEx.unique(y1);
        n1 = label1.length;

        int[] label2 = MathEx.unique(y2);
        n2 = label2.length;

        // Calculate N contingency table
        table = new int[n1][n2];
        for (int i = 0; i < n1; i++) {
            for (int j = 0; j < n2; j++) {
                int nij = 0;
                for (int k = 0; k < n; k++) {
                    if (y1[k] == label1[i] && y2[k] == label2[j]) {
                        nij++;
                    }
                }

                table[i][j] = nij;
            }
        }

        // Marginals
        a = new int[n1];
        b = new int[n2];

        for (int i = 0; i < n1; i++) {
            for (int j = 0; j < n2; j++) {
                a[i] += table[i][j];
                b[j] += table[i][j];
            }
        }
    }
}
