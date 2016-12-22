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
 * Minkowski distance of order p or L<sub>p</sub>-norm, is a generalization of
 * Euclidean distance that is actually L<sub>2</sub>-norm. You may also provide
 * a specified weight vector. For float or double arrays, missing values (i.e. NaN)
 * are also handled. Also support sparse arrays of which zeros are excluded
 * to save space.
 *
 * @author Haifeng Li
 */
public class MinkowskiDistance implements Metric<double[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The order of Minkowski distance.
     */
    private int p;

    /**
     * The weights used in weighted distance.
     */
    private double[] weight = null;

    /**
     * Constructor.
     */
    public MinkowskiDistance(int p) {
        if (p <= 0)
            throw new IllegalArgumentException(String.format("The order p has to be larger than 0: p = d", p));

        this.p = p;
    }

    /**
     * Constructor.
     *
     * @param weight the weight vector.
     */
    public MinkowskiDistance(int p, double[] weight) {
        if (p <= 0)
            throw new IllegalArgumentException(String.format("The order p has to be larger than 0: p = d", p));

        for (int i = 0; i < weight.length; i++) {
            if (weight[i] < 0)
                throw new IllegalArgumentException(String.format("Weight has to be nonnegative: %f", weight[i]));
        }

        this.p = p;
        this.weight = weight;
    }

    @Override
    public String toString() {
        return String.format("Minkowski distance (p = %d)", p);
    }

    /**
     * Minkowski distance between the two arrays of type integer.
     */
    public double d(int[] x, int[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        double dist = 0.0;

        if (weight == null) {
            for (int i = 0; i < x.length; i++) {
                double d = Math.abs(x[i] - y[i]);
                dist += Math.pow(d, p);
            }
        } else {
            if (x.length != weight.length)
                throw new IllegalArgumentException(String.format("Input vectors and weight vector have different length: %d, %d", x.length, weight.length));

            for (int i = 0; i < x.length; i++) {
                double d = Math.abs(x[i] - y[i]);
                dist += weight[i] * Math.pow(d, p);
            }
        }

        return Math.pow(dist, 1.0/p);
    }

    /**
     * Minkowski distance between the two arrays of type float.
     * NaN will be treated as missing values and will be excluded from the
     * calculation. Let m be the number non-missing values, and n be the
     * number of all values. The returned distance is pow(n * d / m, 1/p),
     * where d is the p-pow of distance between non-missing values.
     */
    public double d(float[] x, float[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        int n = x.length;
        int m = 0;
        double dist = 0.0;

        if (weight == null) {
            for (int i = 0; i < x.length; i++) {
                if (!Float.isNaN(x[i]) && !Float.isNaN(y[i])) {
                    m++;
                    double d = Math.abs(x[i] - y[i]);
                    dist += Math.pow(d, p);
                }
            }
        } else {
            if (x.length != weight.length)
                throw new IllegalArgumentException(String.format("Input vectors and weight vector have different length: %d, %d", x.length, weight.length));

            for (int i = 0; i < x.length; i++) {
                if (!Float.isNaN(x[i]) && !Float.isNaN(y[i])) {
                    m++;
                    double d = Math.abs(x[i] - y[i]);
                    dist += weight[i] * Math.pow(d, p);
                }
            }
        }

        dist = n * dist / m;

        return Math.pow(dist, 1.0/p);
    }

    /**
     * Minkowski distance between the two arrays of type double.
     * NaN will be treated as missing values and will be excluded from the
     * calculation. Let m be the number non-missing values, and n be the
     * number of all values. The returned distance is pow(n * d / m, 1/p),
     * where d is the p-pow of distance between non-missing values.
     */
    @Override
    public double d(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        int n = x.length;
        int m = 0;
        double dist = 0.0;

        if (weight == null) {
            for (int i = 0; i < x.length; i++) {
                if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                    m++;
                    double d = Math.abs(x[i] - y[i]);
                    dist += Math.pow(d, p);
                }
            }
        } else {
            if (x.length != weight.length)
                throw new IllegalArgumentException(String.format("Input vectors and weight vector have different length: %d, %d", x.length, weight.length));

            for (int i = 0; i < x.length; i++) {
                if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                    m++;
                    double d = Math.abs(x[i] - y[i]);
                    dist += weight[i] * Math.pow(d, p);
                }
            }
        }

        dist = n * dist / m;

        return Math.pow(dist, 1.0/p);
    }
}
