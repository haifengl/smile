/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.validation.metric;

import smile.math.MathEx;

import java.io.Serial;

/**
 * Root mean squared error.
 * 
 * @author Haifeng Li
 */
public class RMSE implements RegressionMetric {
    @Serial
    private static final long serialVersionUID = 2L;
    /** Default instance. */
    public static final RMSE instance = new RMSE();

    /** Constructor. */
    public RMSE() {

    }

    @Override
    public double score(double[] truth, double[] prediction) {
        return of(truth, prediction);
    }

    /**
     * Calculates the root mean squared error.
     * @param truth the ground truth.
     * @param prediction the prediction.
     * @return the metric.
     */
    public static double of(double[] truth, double[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int n = truth.length;
        double rss = 0.0;
        for (int i = 0; i < n; i++) {
            rss += MathEx.pow2(truth[i] - prediction[i]);
        }

        return Math.sqrt(rss/n);
    }

    @Override
    public String toString() {
        return "RMSE";
    }
}
