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

import java.util.Arrays;
import smile.math.MathEx;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static smile.math.MathEx.lfactorial;

/**
 * Adjusted Mutual Information (AMI) for comparing clustering.
 * Like the Rand index, the baseline value of mutual information between two
 * random clusterings does not take on a constant value, and tends to be
 * larger when the two partitions have a larger number of clusters (with
 * a fixed number of observations). AMI adopts a hypergeometric model of
 * randomness to adjust for chance. The AMI takes a value of 1 when the
 * two partitions are identical and 0 when the MI between two partitions
 * equals the value expected due to chance alone.
 *
 * WARNING: The computation of adjustment is is really really slow.
 *
 * <h2>References</h2>
 * <ol>
 * <li>X. Vinh, J. Epps, J. Bailey. Information Theoretic Measures for Clusterings Comparison: Variants, Properties, Normalization and Correction for Chance. JMLR, 2010.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class AdjustedMutualInformation implements ClusterMeasure {
    /** The normalization method. */
    private final Method method;

    /** The normalization method. */
    public enum Method {
        /** I(y1, y2) / max(H(y1), H(y2)) */
        MAX,
        /** I(y1, y2) / min(H(y1), H(y2)) */
        MIN,
        /** 2 * I(y1, y2) / (H(y1) + H(y2)) */
        SUM,
        /** I(y1, y2) / sqrt(H(y1) * H(y2)) */
        SQRT
    }

    /**
     * Constructor.
     * @param method the normalization method.
     */
    public AdjustedMutualInformation(Method method) {
        this.method = method;
    }

    @Override
    public double measure(int[] y1, int[] y2) {
        switch (method) {
            case MAX: return max(y1, y2);
            case MIN: return min(y1, y2);
            case SUM: return sum(y1, y2);
            case SQRT: return sqrt(y1, y2);
            default: throw new IllegalStateException("Unknown normalization method: " + method);
        }
    }

    /** Calculates the adjusted mutual information of (I(y1, y2) - E(MI)) / (max(H(y1), H(y2)) - E(MI)). */
    public static double max(int[] y1, int[] y2) {
        ContingencyTable contingency = new ContingencyTable(y1, y2);
        double n = contingency.n;
        double[] p1 = Arrays.stream(contingency.a).mapToDouble(a -> a/n).toArray();
        double[] p2 = Arrays.stream(contingency.b).mapToDouble(b -> b/n).toArray();
        double h1 = MathEx.entropy(p1);
        double h2 = MathEx.entropy(p2);
        double I = MutualInformation.of(contingency.n, p1, p2, contingency.table);
        double E = E(contingency.n, contingency.a, contingency.b);
        return (I - E) / (Math.max(h1, h2) - E);
    }

    /** Calculates the adjusted mutual information of (I(y1, y2) - E(MI)) / (0.5 * (H(y1) + H(y2)) - E(MI)). */
    public static double sum(int[] y1, int[] y2) {
        ContingencyTable contingency = new ContingencyTable(y1, y2);
        double n = contingency.n;
        double[] p1 = Arrays.stream(contingency.a).mapToDouble(a -> a/n).toArray();
        double[] p2 = Arrays.stream(contingency.b).mapToDouble(b -> b/n).toArray();
        double h1 = MathEx.entropy(p1);
        double h2 = MathEx.entropy(p2);
        double I = MutualInformation.of(contingency.n, p1, p2, contingency.table);
        double E = E(contingency.n, contingency.a, contingency.b);
        return (I - E) / (0.5 * (h1 + h2) - E);
    }

    /** Calculates the adjusted mutual information of (I(y1, y2) - E(MI)) / (sqrt(H(y1) * H(y2)) - E(MI)). */
    public static double sqrt(int[] y1, int[] y2) {
        ContingencyTable contingency = new ContingencyTable(y1, y2);
        double n = contingency.n;
        double[] p1 = Arrays.stream(contingency.a).mapToDouble(a -> a/n).toArray();
        double[] p2 = Arrays.stream(contingency.b).mapToDouble(b -> b/n).toArray();
        double h1 = MathEx.entropy(p1);
        double h2 = MathEx.entropy(p2);
        double I = MutualInformation.of(contingency.n, p1, p2, contingency.table);
        double E = E(contingency.n, contingency.a, contingency.b);
        return (I - E) / (Math.sqrt(h1 * h2) - E);
    }

    /** Calculates the adjusted mutual information of (I(y1, y2) - E(MI)) / (min(H(y1), H(y2)) - E(MI)). */
    public static double min(int[] y1, int[] y2) {
        ContingencyTable contingency = new ContingencyTable(y1, y2);
        double n = contingency.n;
        double[] p1 = Arrays.stream(contingency.a).mapToDouble(a -> a/n).toArray();
        double[] p2 = Arrays.stream(contingency.b).mapToDouble(b -> b/n).toArray();
        double h1 = MathEx.entropy(p1);
        double h2 = MathEx.entropy(p2);
        double I = MutualInformation.of(contingency.n, p1, p2, contingency.table);
        double E = E(contingency.n, contingency.a, contingency.b);
        return (I - E) / (Math.min(h1, h2) - E);
    }

    /** Calculates the expected value of mutual information. */
    private static double E(int n, int[] a, int[] b) {
        int n1 = a.length;
        int n2 = b.length;
        double N = n;
        double E = 0.0;
        for (int i = 0; i < n1; i++) {
            int ai = a[i];
            for (int j = 0; j < n2; j++) {
                int bj = b[j];
                int begin = Math.max(1, ai + bj - n);
                int end = Math.min(ai, bj);
                for (int nij = begin; nij <= end; nij++) {
                    E += ((double) nij / N) * log(((double) nij * N) / (ai * bj))
                        * exp((lfactorial(ai) + lfactorial(bj) + lfactorial(n - ai) + lfactorial(n - bj))
                            - (lfactorial(n) + lfactorial(nij) + lfactorial(ai - nij) + lfactorial(bj - nij) + lfactorial(n - ai - bj + nij)));
                }
            }
        }

        return E;
    }

    @Override
    public String toString() {
        return "Adjusted Mutual Information";
    }
}
