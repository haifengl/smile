/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.validation.metric;

import smile.math.MathEx;

import java.io.Serial;

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
public class RandIndex implements ClusteringMetric {
    @Serial
    private static final long serialVersionUID = 2L;
    /** Default instance. */
    public final static RandIndex instance = new RandIndex();

    @Override
    public double score(int[] truth, int[] cluster) {
        return of(truth, cluster);
    }

    /**
     * Calculates the rand index.
     * @param truth the ground truth (or simply a clustering labels).
     * @param cluster the alternative cluster labels.
     * @return the metric.
     */
    public static double of(int[] truth, int[] cluster) {
        ContingencyTable contingency = new ContingencyTable(truth, cluster);
        int n = contingency.n;
        int n1 = contingency.n1;
        int n2 = contingency.n2;
        int[] a = contingency.a;
        int[] b = contingency.b;
        int[][] count = contingency.table;

        // Calculate RAND - Non-adjusted
        double randT = 0.0;
        for (int i = 0; i < n1; i++) {
            for (int j = 0; j < n2; j++) {
                randT += MathEx.pow2(count[i][j]);
            }
        }
        randT -= n;

        double randP = 0.0;
        for (int i = 0; i < n1; i++) {
            randP += MathEx.pow2(a[i]);
        }
        randP -= n;

        double randQ = 0.0;
        for (int j = 0; j < n2; j++) {
            randQ += MathEx.pow2(b[j]);
        }
        randQ -= n;

        return (randT - 0.5 * randP - 0.5 * randQ + MathEx.choose(n, 2)) / MathEx.choose(n, 2);
    }

    @Override
    public String toString() {
        return "RandIndex";
    }
}
