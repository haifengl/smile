/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.distance;

import smile.math.Math;

/**
 * Correlation distance is defined as 1 - correlation coefficient.
 *
 * @author Haifeng Li
 */
public class CorrelationDistance implements Distance<double[]> {

    /**
     * Constructor.
     */
    public CorrelationDistance() {

    }

    @Override
    public String toString() {
        return "Correlation distance";
    }

    /**
     * Pearson correlation  distance between the two arrays of type double.
     */
    @Override
    public double d(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return 1 - Math.cor(x, y);
    }

    /**
     * Pearson correlation distance between the two arrays of type int.
     */
    public static double pearson(int[] x, int[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return 1 - Math.cor(x, y);
    }

    /**
     * Pearson correlation distance between the two arrays of type float.
     */
    public static double pearson(float[] x, float[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return 1 - Math.cor(x, y);
    }

    /**
     * Pearson correlation  distance between the two arrays of type double.
     */
    public static double pearson(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return 1 - Math.cor(x, y);
    }

    /**
     * Spearman correlation distance between the two arrays of type int.
     */
    public static double spearman(int[] x, int[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return 1 - Math.spearman(x, y);
    }

    /**
     * Spearman correlation distance between the two arrays of type float.
     */
    public static double spearman(float[] x, float[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return 1 - Math.spearman(x, y);
    }

    /**
     * Spearman correlation distance between the two arrays of type double.
     */
    public static double spearman(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return 1 - Math.spearman(x, y);
    }

    /**
     * Kendall rank correlation distance between the two arrays of type int.
     */
    public static double kendall(int[] x, int[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return 1 - Math.kendall(x, y);
    }

    /**
     * Kendall rank correlation distance between the two arrays of type float.
     */
    public static double kendall(float[] x, float[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return 1 - Math.kendall(x, y);
    }

    /**
     * Kendall rank correlation distance between the two arrays of type double.
     */
    public static double kendall(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        return 1 - Math.kendall(x, y);
    }
}
