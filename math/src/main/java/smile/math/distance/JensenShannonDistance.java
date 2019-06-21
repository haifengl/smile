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

package smile.math.distance;

import smile.math.MathEx;

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
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    public JensenShannonDistance() {
    }

    @Override
    public String toString() {
        return "Jensen-Shannon Distance";
    }

    @Override
    public double d(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return Math.sqrt(MathEx.JensenShannonDivergence(x, y));
    }
}
