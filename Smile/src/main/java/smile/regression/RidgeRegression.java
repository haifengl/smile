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

package smile.regression;

import smile.math.Math;
import smile.math.matrix.CholeskyDecomposition;

/**
 * Ridge Regression. When the predictor variables are highly correlated amongst
 * themselves, the coefficients of the resulting least squares fit may be very
 * imprecise. By allowing a small amount of bias in the estimates, more
 * reasonable coefficients may often be obtained. Ridge regression is one
 * method to address these issues. Often, small amounts of bias lead to
 * dramatic reductions in the variance of the estimated model coefficients.
 * Ridge regression is such a technique which shrinks the regression
 * coefficients by imposing a penalty on their size. Ridge regression was
 * originally developed to overcome the singularity of the X'X matrix.
 * This matrix is perturbed so as to make its determinant appreciably
 * different from 0.
 * <p>
 * Ridge regression is a kind of Tikhonov regularization, which is the most
 * commonly used method of regularization of ill-posed problems. Another
 * interpretation of ridge regression is available through Bayesian estimation.
 * In this setting the belief that weight should be small is coded into a prior
 * distribution. 
 * 
 * @author Haifeng Li
 */
public class RidgeRegression implements Regression<double[]> {

    /**
     * The dimensionality.
     */
    private int p;
    /**
     * The shrinkage/regularization parameter.
     */
    private double lambda;
    /**
     * The centered intercept.
     */
    private double b;
    /**
     * The scaled linear coefficients.
     */
    private double[] w;
    /**
     * The mean of response variable.
     */
    private double ym;
    /**
     * The center of input vector. The input vector should be centered
     * before prediction.
     */
    private double[] center;
    /**
     * Scaling factor of input vector.
     */
    private double[] scale;

    /**
     * Trainer for ridge regression.
     */
    public static class Trainer extends RegressionTrainer<double[]> {

        /**
         * The shrinkage/regularization parameter.
         */
        private double lambda;

        /**
         * Constructor.
         * 
         * @param lambda the number of trees.
         */
        public Trainer(double lambda) {
            if (lambda < 0.0) {
                throw new IllegalArgumentException("Invalid shrinkage/regularization parameter lambda = " + lambda);
            }

            this.lambda = lambda;
        }

        @Override
        public RidgeRegression train(double[][] x, double[] y) {
            return new RidgeRegression(x, y, lambda);
        }
    }
    
    /**
     * Constructor. Learn the ridge regression model.
     * @param x a matrix containing the explanatory variables.
     * @param y the response values.
     * @param lambda the shrinkage/regularization parameter.
     */
    public RidgeRegression(double[][] x, double[] y, double lambda) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (lambda < 0.0) {
            throw new IllegalArgumentException("Invalid shrinkage/regularization parameter lambda = " + lambda);
        }

        int n = x.length;
        p = x[0].length;
        ym = Math.mean(y);                
        center = Math.colMean(x); 
        
        double[][] X = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                X[i][j] = x[i][j] - center[j];
            }
        }
        
        scale = new double[p];
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                scale[j] += Math.sqr(X[i][j]);
            }
            scale[j] = Math.sqrt(scale[j] / n);
        }
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                X[i][j] /= scale[j];
            }
        }

        w = new double[p];
        Math.atx(X, y, w);

        double[][] XtX = Math.atamm(X);
        for (int i = 0; i < p; i++) {
            XtX[i][i] += lambda;
        }
        CholeskyDecomposition cholesky = new CholeskyDecomposition(XtX);

        cholesky.solve(w);
        
        for (int j = 0; j < p; j++) {
            w[j] /= scale[j];
        }
        b = ym - Math.dot(w, center);
    }

    /**
     * Returns the (scaled) linear coefficients.
     */
    public double[] coefficients() {
        return w;
    }

    /**
     * Returns the (centered) intercept.
     */
    public double intercept() {
        return b;
    }

    /**
     * Returns the shrinkage parameter.
     */
    public double shrinkage() {
        return lambda;
    }

    @Override
    public double predict(double[] x) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        return Math.dot(x, w) + b;
    }
}
