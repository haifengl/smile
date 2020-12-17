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

import smile.math.MathEx;

/**
 * R<sup>2</sup>. R<sup>2</sup> coefficient of determination measures how well
 * the regression line approximates the real data points. An R<sup>2</sup>
 * of 1.0 indicates that the regression line perfectly fits the data.
 *
 * @author Haifeng Li
 */
public class R2 implements RegressionMetric {
    private static final long serialVersionUID = 2L;
    /** Default instance. */
    public final static R2 instance = new R2();

    @Override
    public double score(double[] truth, double[] prediction) {
        return of(truth, prediction);
    }

    /**
     * Calculates the R squared coefficient.
     * @param truth the ground truth.
     * @param prediction the prediction.
     * @return the metric.
     */
    public static double of(double[] truth, double[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        double RSS = 0.0;
        double TSS = 0.0;
        double ybar = MathEx.mean(truth);

        int n = truth.length;
        for (int i = 0; i < n; i++) {
            double r = truth[i] - prediction[i];
            RSS += r * r;
            double t = truth[i] - ybar;
            TSS += t * t;
        }

        return 1.0 - RSS / TSS;
    }

    @Override
    public String toString() {
        return "R2";
    }
}
