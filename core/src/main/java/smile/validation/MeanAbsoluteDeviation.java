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

/**
 * Mean absolute deviation error.
 * 
 * @author Haifeng Li
 */
public class MeanAbsoluteDeviation implements RegressionMeasure {
    public final static MeanAbsoluteDeviation instance = new MeanAbsoluteDeviation();

    @Override
    public double measure(double[] truth, double[] prediction) {
        return of(truth, prediction);
    }

    /** Calculates the mean absolute deviation error. */
    public static double of(double[] truth, double[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        }

        int n = truth.length;
        double error = 0.0;
        for (int i = 0; i < n; i++) {
            error += Math.abs(truth[i] - prediction[i]);
        }

        return error/n;
    }

    @Override
    public String toString() {
        return "Mean Absolute Deviation";
    }
}
