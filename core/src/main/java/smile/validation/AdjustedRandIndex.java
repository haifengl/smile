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

/**
 * Adjusted Rand Index. Rand index is defined as the number of pairs of objects
 * that are either in the same group or in different groups in both partitions
 * divided by the total number of pairs of objects. The Rand index lies between
 * 0 and 1. When two partitions agree perfectly, the Rand index achieves the
 * maximum value 1. A problem with Rand index is that the expected value of
 * the Rand index between two random partitions is not a constant. This problem
 * is corrected by the adjusted Rand index that assumes the generalized
 * hyper-geometric distribution as the model of randomness. The adjusted Rand
 * index has the maximum value 1, and its expected value is 0 in the case
 * of random clusters. A larger adjusted Rand index means a higher agreement
 * between two partitions. The adjusted Rand index is recommended for measuring
 * agreement even when the partitions compared have different numbers of clusters.
 *
 * @see RandIndex
 * 
 * @author Haifeng Li
 */
public class AdjustedRandIndex implements ClusterMeasure {
    public final static AdjustedRandIndex instance = new AdjustedRandIndex();

    @Override
    public double measure(int[] y1, int[] y2) {
        return of(y1, y2);
    }

    /** Calculates the adjusted rand index. */
    public static double of(int[] y1, int[] y2) {
        ContingencyTable contingency = new ContingencyTable(y1, y2);
        int n = contingency.n;
        int n1 = contingency.n1;
        int n2 = contingency.n2;
        int[] a = contingency.a;
        int[] b = contingency.b;
        int[][] count = contingency.table;

        // Calculate RAND - Adj
        double rand1 = 0.0;
        for (int i = 0; i < n1; i++) {
            for (int j = 0; j < n2; j++) {
                if (count[i][j] >= 2) {
                    rand1 += MathEx.choose(count[i][j], 2);
                }
            }
        }

        double rand2a = 0.0;
        for (int i = 0; i < n1; i++) {
            if (a[i] >= 2) {
                rand2a += MathEx.choose(a[i], 2);
            }
        }

        double rand2b = 0;
        for (int j = 0; j < n2; j++) {
            if (b[j] >= 2) {
                rand2b += MathEx.choose(b[j], 2);
            }
        }

        double rand3 = rand2a * rand2b;
        rand3 /= MathEx.choose(n, 2);
        double rand_N = rand1 - rand3;

        // D
        double rand4 = (rand2a + rand2b) / 2;
        double randD = rand4 - rand3;

        double rand = rand_N / randD;
        return rand;
    }

    @Override
    public String toString() {
        return "Adjusted Rand Index";
    }
}
