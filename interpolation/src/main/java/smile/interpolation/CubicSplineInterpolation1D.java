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

package smile.interpolation;

/**
 * Cubic spline interpolation. Spline interpolation uses low-degree polynomials
 * in each of the intervals, and chooses the polynomial pieces such that they
 * fit smoothly together. The resulting function is called a spline.
 * <p>
 * The natural cubic spline is piecewise cubic and twice continuously
 * differentiable. Furthermore, its second derivative is zero at the end
 * points.
 * <p>
 * Like polynomial interpolation, spline interpolation incurs a smaller
 * error than linear interpolation and the interpolant is smoother. However,
 * the interpolant is easier to evaluate than the high-degree polynomials
 * used in polynomial interpolation. It also does not suffer from Runge's
 * phenomenon.
 * 
 * @author Haifeng Li
 */
public class CubicSplineInterpolation1D extends AbstractInterpolation {

    /**
     * Second derivatives of the interpolating function at the tabulated points.
     */
    private double[] y2;

    /**
     * Constructor.
     */
    public CubicSplineInterpolation1D(double[] x, double[] y) {
        super(x, y);
        y2 = new double[x.length];
        sety2(x, y);
    }

    @Override
    public double rawinterp(int j, double x) {
        int klo = j, khi = j + 1;
        double h = xx[khi] - xx[klo];

        if (h == 0.0) {
            throw new IllegalArgumentException("Nearby control points take same x value: " + xx[khi]);
        }

        double a = (xx[khi] - x) / h;
        double b = (x - xx[klo]) / h;
        double y = a * yy[klo] + b * yy[khi] + ((a * a * a - a) * y2[klo] + (b * b * b - b) * y2[khi]) * (h * h) / 6.0;

        return y;
    }

    /**
     * Calculate the second derivatives of the interpolating function at the
     * tabulated points. At the endpoints, we use a natural spline
     * with zero second derivative on that boundary.
     */
    private void sety2(double[] x, double[] y) {
        double p, qn, sig, un;
        double[] u = new double[n - 1];

        y2[0] = u[0] = 0.0;

        for (int i = 1; i < n - 1; i++) {
            sig = (x[i] - x[i - 1]) / (x[i + 1] - x[i - 1]);
            p = sig * y2[i - 1] + 2.0;
            y2[i] = (sig - 1.0) / p;
            u[i] = (y[i + 1] - y[i]) / (x[i + 1] - x[i]) - (y[i] - y[i - 1]) / (x[i] - x[i - 1]);
            u[i] = (6.0 * u[i] / (x[i + 1] - x[i - 1]) - sig * u[i - 1]) / p;
        }

        qn = un = 0.0;
        y2[n - 1] = (un - qn * u[n - 2]) / (qn * y2[n - 2] + 1.0);
        for (int k = n - 2; k >= 0; k--) {
            y2[k] = y2[k] * y2[k + 1] + u[k];
        }
    }

    @Override
    public String toString() {
        return "Cubic Spline Interpolation";
    }
}

