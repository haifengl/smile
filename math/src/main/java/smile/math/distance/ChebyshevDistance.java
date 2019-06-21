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

/**
 * Chebyshev distance (or Tchebychev distance), or L<sub>&infin;</sub> metric
 * is a metric defined on a vector space where the distance between two vectors
 * is the greatest of their differences along any coordinate dimension.
 * 
 * @author Haifeng Li
 */
public class ChebyshevDistance implements Metric<double[]> {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    public ChebyshevDistance() {
    }

    @Override
    public String toString() {
        return "Chebyshev Distance";
    }

    /**
     * Chebyshev distance between the two arrays of type integer.
     */
    public static double d(int[] x, int[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        double dist = 0.0;
        for (int i = 0; i < x.length; i++) {
            double d = Math.abs(x[i] - y[i]);
            if (dist < d)
                dist = d;
        }

        return dist;
    }

    /**
     * Chebyshev distance between the two arrays of type float.
     * NaN will be treated as missing values and will be excluded from the
     * calculation.
     */
    public static double d(float[] x, float[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        double dist = 0.0;
        for (int i = 0; i < x.length; i++) {
            if (!Float.isNaN(x[i]) && !Float.isNaN(y[i])) {
                double d = Math.abs(x[i] - y[i]);
                if (dist < d)
                    dist = d;
            }
        }

        return dist;
    }

    /**
     * Chebyshev distance between the two arrays of type double.
     * NaN will be treated as missing values and will be excluded from the
     * calculation.
     */
    @Override
    public double d(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        double dist = 0.0;
        for (int i = 0; i < x.length; i++) {
            if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                double d = Math.abs(x[i] - y[i]);
                if (dist < d)
                    dist = d;
            }
        }

        return dist;
    }
}
