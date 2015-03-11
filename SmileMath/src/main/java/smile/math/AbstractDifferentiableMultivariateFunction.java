/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.math;

/**
 * An abstract implementation that uses finite differences to calculate the
 * partial derivatives instead of providing them analytically.
 *
 * @author Haifeng Li
 */
public abstract class AbstractDifferentiableMultivariateFunction implements DifferentiableMultivariateFunction {

    private static final double EPS = 1.0E-8;

    @Override
    public double f(double[] x, double[] gradient) {
        double f = f(x);

        double[] xh = x.clone();
        for (int j = 0; j < x.length; j++) {
            double temp = x[j];
            double h = EPS * Math.abs(temp);
            if (h == 0.0) {
                h = EPS;
            }
            xh[j] = temp + h; // trick to reduce finite-precision error.
            h = xh[j] - temp;
            double fh = f(xh);
            xh[j] = temp;
            gradient[j] = (fh - f) / h;
        }

        return f;
    }
}
