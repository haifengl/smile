/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.distance;

/**
 * Chebyshev distance (or Tchebychev distance), or L<sub>&infin;</sub> metric
 * is a metric defined on a vector space where the distance between two vectors
 * is the greatest of their differences along any coordinate dimension.
 * 
 * @author Haifeng Li
 */
public class ChebyshevDistance implements Metric<double[]> {

    /**
     * Constructor.
     */
    public ChebyshevDistance() {
    }

    @Override
    public String toString() {
        return "Chebyshev distance";
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
