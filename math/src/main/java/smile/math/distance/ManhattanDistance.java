/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.math.distance;

import java.io.Serializable;

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
public class ManhattanDistance implements Metric<double[]>, Serializable {
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
            return "Weighted Manhattan distance";
        else
            return "Manhattan distance";
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
