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

import java.util.Arrays;

/**
 * Manhattan distance, also known as L<sub>1</sub> distance or L<sub>1</sub>
 * norm, is the sum of the (absolute) differences of their coordinates. Use
 * getInstance() to get the standard unweighted Manhattan distance. Or create
 * an instance with a specified weight vector. For float or double arrays,
 * missing values (i.e. NaN) are also handled. Also support sparse arrays
 * of which zeros are excluded to save space.
 *
 * @author Haifeng Li
 */
public class ManhattanDistance implements Metric<double[]> {
    private static final long serialVersionUID = 1L;

    /**
     * The weights used in weighted distance.
     */
    private double[] weight = null;

    /**
     * Constructor.
     */
    public ManhattanDistance() {
    }

    /**
     * Constructor.
     *
     * @param weight the weight vector.
     */
    public ManhattanDistance(double[] weight) {
        for (int i = 0; i < weight.length; i++) {
            if (weight[i] < 0)
                throw new IllegalArgumentException(String.format("Weight has to be nonnegative: %f", weight[i]));
        }

        this.weight = weight;
    }

    @Override
    public String toString() {
        if (weight != null)
            return String.format("Weighted Manhattan Distance(%s)", Arrays.toString(weight));
        else
            return "Manhattan Distance";
    }

    /**
     * Manhattan distance between two arrays of type integer.
     */
    public double d(int[] x, int[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        double dist = 0.0;

        if (weight == null) {
            for (int i = 0; i < x.length; i++) {
                dist += Math.abs(x[i] - y[i]);
            }
        } else {
            if (x.length != weight.length)
                throw new IllegalArgumentException(String.format("Input vectors and weight vector have different length: %d, %d", x.length, weight.length));

            for (int i = 0; i < x.length; i++) {
                dist += weight[i] * Math.abs(x[i] - y[i]);
            }
        }

        return dist;
    }

    /**
     * Manhattan distance between two arrays of type float.
     * NaN will be treated as missing values and will be excluded from the
     * calculation. Let m be the number non-missing values, and n be the
     * number of all values. The returned distance is n * d / m,
     * where d is the distance between non-missing values.
     */
    public double d(float[] x, float[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        int n = x.length;
        int m = 0;
        double dist = 0.0;

        if (weight == null) {
            for (int i = 0; i < n; i++) {
                if (!Float.isNaN(x[i]) && !Float.isNaN(y[i])) {
                    m++;
                    dist += Math.abs(x[i] - y[i]);
                }
            }
        } else {
            if (x.length != weight.length)
                throw new IllegalArgumentException(String.format("Input vectors and weight vector have different length: %d, %d", x.length, weight.length));

            for (int i = 0; i < n; i++) {
                if (!Float.isNaN(x[i]) && !Float.isNaN(y[i])) {
                    m++;
                    dist += weight[i] * Math.abs(x[i] - y[i]);
                }
            }
        }

        dist = n * dist / m;

        return dist;
    }

    /**
     * Manhattan distance between two arrays of type double.
     * NaN will be treated as missing values and will be excluded from the
     * calculation. Let m be the number non-missing values, and n be the
     * number of all values. The returned distance is n * d / m,
     * where d is the distance between non-missing values.
     */
    @Override
    public double d(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        int n = x.length;
        int m = 0;
        double dist = 0.0;

        if (weight == null) {
            for (int i = 0; i < n; i++) {
                if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                    m++;
                    dist += Math.abs(x[i] - y[i]);
                }
            }
        } else {
            if (x.length != weight.length)
                throw new IllegalArgumentException(String.format("Input vectors and weight vector have different length: %d, %d", x.length, weight.length));

            for (int i = 0; i < n; i++) {
                if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                    m++;
                    dist += weight[i] * Math.abs(x[i] - y[i]);
                }
            }
        }

        dist = n * dist / m;

        return dist;
    }
}
