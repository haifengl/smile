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
package smile.interpolation;

import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.LU;

/**
 * Kriging interpolation for the data points irregularly distributed in space.
 * Kriging belongs to the family of linear least squares estimation algorithms,
 * also known as Gauss-Markov estimation or Gaussian process regression.
 * This class implements ordinary kriging for interpolation with power variogram.
 *
 * @author Haifeng Li
 */
public class KrigingInterpolation1D implements Interpolation {

    private double[] x;
    private double[] yvi;
    private double[] vstar;
    private double alpha;
    private double beta;

    /**
     * Constructor. The power variogram is employed for interpolation.
     * @param x the point set.
     * @param y the function values at given points.
     */
    public KrigingInterpolation1D(double[] x, double[] y) {
        this.x = x;
        pow(x, y);

        int n = x.length;
        yvi = new double[n + 1];
        vstar = new double[n + 1];
        DenseMatrix v = Matrix.zeros(n + 1, n + 1);

        for (int i = 0; i < n; i++) {
            yvi[i] = y[i];

            for (int j = i; j < n; j++) {
                double var = variogram(Math.abs(x[i] - x[j]));
                v.set(i, j, var);
                v.set(j, i, var);
            }
            v.set(n, i, 1.0);
            v.set(i, n, 1.0);
        }

        yvi[n] = 0.0;
        v.set(n, n, 0.0);

        LU lu = v.lu(true);
        lu.solve(yvi);
    }

    @Override
    public double interpolate(double x) {
        int n = this.x.length;
        for (int i = 0; i < n; i++) {
            vstar[i] = variogram(Math.abs(x - this.x[i]));
        }
        vstar[n] = 1.0;
        
        double y = 0.0;
        for (int i = 0; i <= n; i++) {
            y += yvi[i] * vstar[i];
        }
        return y;
    }

    private void pow(double[] x, double[] y) {
        beta = 1.5;
        int n = x.length;

        double num = 0.0, denom = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double rb = Math.sqr(x[i] - x[j]);
                rb = Math.pow(rb, 0.5 * beta);
                num += rb * 0.5 * Math.sqr(y[i] - y[j]);
                denom += rb * rb;
            }
        }

        alpha = num / denom;
    }

    private double variogram(double r) {
        return alpha * Math.pow(r, beta);
    }
}
