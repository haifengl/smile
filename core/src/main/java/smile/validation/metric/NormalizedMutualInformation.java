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
import smile.math.MathEx;
import static java.lang.Math.log;

/**
 * Normalized Mutual Information (NMI) for comparing clustering.
 * Normalized mutual information is between 0 (no mutual information)
 * and 1 (perfect correlation). Normalization has been
 * shown to improve the sensitiveness with respect to the
 * difference in cluster distribution in the two clusterings.
 *
 * <h2>References</h2>
 * <ol>
 * <li>X. Vinh, J. Epps, J. Bailey. Information Theoretic Measures for Clusterings Comparison: Variants, Properties, Normalization and Correction for Chance. JMLR, 2010.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class NormalizedMutualInformation implements ClusteringMetric {
    private static final long serialVersionUID = 2L;
    /** Default instance with max normalization. */
    public final static NormalizedMutualInformation JOINT = new NormalizedMutualInformation(Method.JOINT);
    /** Default instance with max normalization. */
    public final static NormalizedMutualInformation MAX = new NormalizedMutualInformation(Method.MAX);
    /** Default instance with min normalization. */
    public final static NormalizedMutualInformation MIN = new NormalizedMutualInformation(Method.MIN);
    /** Default instance with sum normalization. */
    public final static NormalizedMutualInformation SUM = new NormalizedMutualInformation(Method.SUM);
    /** Default instance with sqrt normalization. */
    public final static NormalizedMutualInformation SQRT = new NormalizedMutualInformation(Method.SQRT);

    /** The normalization method. */
    private final Method method;

    /** The normalization method. */
    public enum Method {
        /** I(y1, y2) / H(y1, y2) */
        JOINT,
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
    public NormalizedMutualInformation(Method method) {
        this.method = method;
    }

    @Override
    public double score(int[] y1, int[] y2) {
        switch (method) {
            case JOINT: return joint(y1, y2);
            case MAX: return max(y1, y2);
            case MIN: return min(y1, y2);
            case SUM: return sum(y1, y2);
            case SQRT: return sqrt(y1, y2);
            default: throw new IllegalStateException("Unknown normalization method: " + method);
        }
    }

    /**
     * Calculates the normalized mutual information of I(y1, y2) / H(y1, y2).
     * @param y1 the clustering labels.
     * @param y2 the alternative cluster labels.
     * @return the metric.
     */
    public static double joint(int[] y1, int[] y2) {
        ContingencyTable contingency = new ContingencyTable(y1, y2);
        double n = contingency.n;
        double[] p1 = Arrays.stream(contingency.a).mapToDouble(a -> a/n).toArray();
        double[] p2 = Arrays.stream(contingency.b).mapToDouble(b -> b/n).toArray();

        double I = MutualInformation.of(contingency.n, p1, p2, contingency.table);

        int n1 = p1.length;
        int n2 = p2.length;
        int[][] count = contingency.table;

        double H = 0.0;
        for (int i = 0; i < n1; i++) {
            for (int j = 0; j < n2; j++) {
                if (count[i][j] > 0) {
                    double p = count[i][j] / n;
                    H -= p * log(p);
                }
            }
        }
        return I / H;
    }

    /**
     * Calculates the normalized mutual information of I(y1, y2) / max(H(y1), H(y2)).
     * @param y1 the clustering labels.
     * @param y2 the alternative cluster labels.
     * @return the metric.
     */
    public static double max(int[] y1, int[] y2) {
        ContingencyTable contingency = new ContingencyTable(y1, y2);
        double n = contingency.n;
        double[] p1 = Arrays.stream(contingency.a).mapToDouble(a -> a/n).toArray();
        double[] p2 = Arrays.stream(contingency.b).mapToDouble(b -> b/n).toArray();
        double h1 = MathEx.entropy(p1);
        double h2 = MathEx.entropy(p2);
        double I = MutualInformation.of(contingency.n, p1, p2, contingency.table);
        return I / Math.max(h1, h2);
    }

    /**
     * Calculates the normalized mutual information of 2 * I(y1, y2) / (H(y1) + H(y2)).
     * @param y1 the clustering labels.
     * @param y2 the alternative cluster labels.
     * @return the metric.
     */
    public static double sum(int[] y1, int[] y2) {
        ContingencyTable contingency = new ContingencyTable(y1, y2);
        double n = contingency.n;
        double[] p1 = Arrays.stream(contingency.a).mapToDouble(a -> a/n).toArray();
        double[] p2 = Arrays.stream(contingency.b).mapToDouble(b -> b/n).toArray();
        double h1 = MathEx.entropy(p1);
        double h2 = MathEx.entropy(p2);
        double I = MutualInformation.of(contingency.n, p1, p2, contingency.table);
        return 2 * I / (h1 + h2);
    }

    /**
     * Calculates the normalized mutual information of I(y1, y2) / sqrt(H(y1) * H(y2)).
     * @param y1 the clustering labels.
     * @param y2 the alternative cluster labels.
     * @return the metric.
     */
    public static double sqrt(int[] y1, int[] y2) {
        ContingencyTable contingency = new ContingencyTable(y1, y2);
        double n = contingency.n;
        double[] p1 = Arrays.stream(contingency.a).mapToDouble(a -> a/n).toArray();
        double[] p2 = Arrays.stream(contingency.b).mapToDouble(b -> b/n).toArray();
        double h1 = MathEx.entropy(p1);
        double h2 = MathEx.entropy(p2);
        double I = MutualInformation.of(contingency.n, p1, p2, contingency.table);
        return I / Math.sqrt(h1 * h2);
    }

    /**
     * Calculates the normalized mutual information of I(y1, y2) / min(H(y1), H(y2)).
     * @param y1 the clustering labels.
     * @param y2 the alternative cluster labels.
     * @return the metric.
     */
    public static double min(int[] y1, int[] y2) {
        ContingencyTable contingency = new ContingencyTable(y1, y2);
        double n = contingency.n;
        double[] p1 = Arrays.stream(contingency.a).mapToDouble(a -> a/n).toArray();
        double[] p2 = Arrays.stream(contingency.b).mapToDouble(b -> b/n).toArray();
        double h1 = MathEx.entropy(p1);
        double h2 = MathEx.entropy(p2);
        double I = MutualInformation.of(contingency.n, p1, p2, contingency.table);
        return I / Math.min(h1, h2);
    }

    @Override
    public String toString() {
        return String.format("NormalizedMutualInformation(%s)", method);
    }
}
