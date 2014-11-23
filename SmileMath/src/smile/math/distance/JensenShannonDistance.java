/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.distance;

import smile.math.Math;

/**
 * The Jensen-Shannon divergence is a popular method of measuring the
 * similarity between two probability distributions. It is also known
 * as information radius or total divergence to the average.
 * <p>
 * The Jensen-Shannon divergence is a symmetrized and smoothed version of the
 * Kullback-Leibler divergence . It is defined by
 * <p>
 * J(P||Q) = (D(P||M) + D(Q||M)) / 2
 * <p>
 * where M = (P+Q)/2 and D(&middot;||&middot;) is KL divergence.
 * Different from the Kullback-Leibler divergence, it is always a finite value.
 * <p>
 * The square root of the Jensen-Shannon divergence is a metric, which is
 * calculated by this class.
 * 
 * @author Haifeng Li
 */
public class JensenShannonDistance implements Metric<double[]> {

    /**
     * Constructor.
     */
    private JensenShannonDistance() {
    }

    @Override
    public String toString() {
        return "Jensen-Shannon distance";
    }

    @Override
    public double d(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return Math.sqrt(Math.JensenShannonDivergence(x, y));
    }
}
