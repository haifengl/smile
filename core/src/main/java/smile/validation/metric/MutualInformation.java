/*
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
 */

package smile.validation.metric;

import java.util.Arrays;
import static java.lang.Math.log;

/**
 * Mutual Information for comparing clustering.
 *
 * <h2>References</h2>
 * <ol>
 * <li>X. Vinh, J. Epps, J. Bailey. Information Theoretic Measures for Clusterings Comparison: Variants, Properties, Normalization and Correction for Chance. JMLR, 2010.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class MutualInformation implements ClusteringMetric {
    private static final long serialVersionUID = 2L;
    /** Default instance. */
    public final static MutualInformation instance = new MutualInformation();

    @Override
    public double score(int[] truth, int[] cluster) {
        return of(truth, cluster);
    }

    /**
     * Calculates the mutual information.
     * @param truth the ground truth (or simply a clustering labels).
     * @param cluster the alternative cluster labels.
     * @return the metric.
     */
    public static double of(int[] truth, int[] cluster) {
        ContingencyTable contingency = new ContingencyTable(truth, cluster);
        double n = contingency.n;
        double[] p1 = Arrays.stream(contingency.a).mapToDouble(a -> a/n).toArray();
        double[] p2 = Arrays.stream(contingency.b).mapToDouble(b -> b/n).toArray();
        return of(n, p1, p2, contingency.table);
    }

    /**
     * Calculates the mutual information.
     * @param n the data size.
     * @param p1 the row marginal probability of contingency table.
     * @param p2 the column marginal probability of contingency table.
     * @param count the contingency table.
     * @return the metric.
     */
    static double of(double n, double[] p1, double[] p2, int[][] count) {
        int n1 = p1.length;
        int n2 = p2.length;

        double I = 0.0;
        for (int i = 0; i < n1; i++) {
            for (int j = 0; j < n2; j++) {
                if (count[i][j] > 0) {
                    double p = count[i][j] / n;
                    I += p * log(p/(p1[i]*p2[j]));
                }
            }
        }

        return I;
    }

    @Override
    public String toString() {
        return "MutualInformation";
    }
}
