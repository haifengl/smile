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
 * Rand Index. Rand index is defined as the number of pairs of objects
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
 * @author Haifeng Li
 */
public class RandIndex implements ClusterMeasure {

    @Override
    public double measure(int[] y1, int[] y2) {
        if (y1.length != y2.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", y1.length, y2.length));
        }

        // Get # of non-zero classes in each solution
        int n = y1.length;

        int[] label1 = MathEx.unique(y1);
        int n1 = label1.length;
        
        int[] label2 = MathEx.unique(y2);
        int n2 = label2.length;

        // Calculate N contingency matrix
        int[][] count = new int[n1][n2];
        for (int i = 0; i < n1; i++) {
            for (int j = 0; j < n2; j++) {
                int match = 0;

                for (int k = 0; k < n; k++) {
                    if (y1[k] == label1[i] && y2[k] == label2[j]) {
                        match++;
                    }
                }

                count[i][j] = match;
            }
        }

        // Marginals
        int[] count1 = new int[n1];
        int[] count2 = new int[n2];

        for (int i = 0; i < n1; i++) {
            for (int j = 0; j < n2; j++) {
                count1[i] += count[i][j];
                count2[j] += count[i][j];
            }
        }

        // Calculate RAND - Non-adjusted
        double rand_T = 0.0;
        for (int i = 0; i < n1; i++) {
            for (int j = 0; j < n2; j++) {
                rand_T += MathEx.sqr(count[i][j]);
            }
        }
        rand_T -= n;

        double rand_P = 0.0;
        for (int i = 0; i < n1; i++) {
            rand_P += MathEx.sqr(count1[i]);
        }
        rand_P -= n;

        double rand_Q = 0.0;
        for (int j = 0; j < n2; j++) {
            rand_Q += MathEx.sqr(count2[j]);
        }
        rand_Q -= n;

        double rand = (rand_T - 0.5 * rand_P - 0.5 * rand_Q + MathEx.choose(n, 2)) / MathEx.choose(n, 2);
        return rand;
    }

    @Override
    public String toString() {
        return "Rand Index";
    }
}
