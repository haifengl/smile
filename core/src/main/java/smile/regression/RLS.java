/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 * Modifications copyright (C) 2017 Sam Erickson
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

import java.io.Serializable;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.matrix.Cholesky;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.Matrix;
import smile.math.matrix.QR;
import smile.math.matrix.SVD;

/**
 * Recursive least squares. RLS updates an ordinary least squares with
 * samples that arrive sequentially. To initialize RLS, we typically
 * train an OLS model with a batch of samples.
 *
 * In some adaptive configurations it can be useful not to give equal
 * importance to all the historical data but to assign higher weights
 * to the most recent data (and then to forget the oldest one). This
 * may happen when the phenomenon underlying the data is non stationary
 * or when we want to approximate a nonlinear dependence by using a
 * linear model which is local in time. Both these situations are common
 * in adaptive control problems.
 *
 * <h2>References</h2>
 * <ol>
 * <li> https://www.otexts.org/1582 </li>
 * </ol>
 *
 * @author Sam Erickson
 */
public class RLS implements OnlineRegression<double[]>, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The dimensionality.
     */
    private int p;
    /**
     * The coefficients with intercept.
     */
    private double[] w;
    /**
     * The forgetting factor in (0, 1]. Values closer to 1 will have
     * longer memory and values closer to 0 will be have shorter memory.
     */
    private double lambda;
    /**
     * First initialized to the matrix (X<sup>T</sup>X)<sup>-1</sup>,
     * it is updated with each new learning instance.
     */
    private DenseMatrix V;
    /**
     * A single learning instance X, padded with 1 for intercept.
     */
    private double[] x1;
    /**
     * A temporary array used in computing V * X .
     */
    private double[] Vx;

    /**
     * Trainer for linear regression by recursive least squares.
     */
    public static class Trainer extends RegressionTrainer<double[]> {
        /**
         * Constructor.
         */
        public Trainer() {

        }

        @Override
        public RLS train(double[][] x, double[] y) {
            return new RLS(x, y);
        }
    }

    /**
     * Constructor. Learn the ordinary least squares model to initialize gamma and coefficients.
     * @param x a matrix containing the explanatory variables. NO NEED to include a constant column of 1s for bias.
     * @param y the response values.
     */
    public RLS(double[][] x, double[] y) {
        this(x, y, 1);
    }

    /**
     * Constructor. Learn the ordinary least squares model to initialize gamma and coefficients.
     * @param x a matrix containing the explanatory variables. NO NEED to include a constant column of 1s for bias.
     * @param y the response values.
     * @param lambda the forgetting factor.
     */
    public RLS(double[][] x, double[] y, double lambda) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }

        if (lambda <= 0 || lambda > 1){
           throw new IllegalArgumentException("The forgetting factor must be in (0, 1]");
        }
        
        this.lambda = lambda;

        int n = x.length;
        p = x[0].length;
        
        if (n <= p) {
            throw new IllegalArgumentException(String.format("The input matrix is not over determined: %d rows, %d columns", n, p));
        }

        DenseMatrix X = Matrix.zeros(n, p+1);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++)
                X.set(i, j, x[i][j]);
            X.set(i, p, 1.0);
        }

        // Always use SVD instead of QR because it is more stable
        // when the data is close to rank deficient, which is more
        // likely in RLS as the initial data size may be small.
        this.w = new double[p+1];
        SVD svd = X.svd();
        svd.solve(y, w);

        Cholesky cholesky = svd.CholeskyOfAtA();
        this.V = cholesky.inverse();

        this.Vx = new double[p+1];
        this.x1 = new double[p+1];
        x1[p] = 1;
    }

    /**
     * Returns the linear coefficients, of which the last element is the intercept.
     */
    public double[] coefficients() {
        return w;
    }

    @Override
    public double predict(double[] x) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        double y = w[p];
        for (int i = 0; i < x.length; i++) {
            y += x[i] * w[i];
        }

        return y;
    }
    
    /**
     * Learn a new instance with online regression.
     * @param x the training instances. 
     * @param y the target values.
     */
    public void learn(double[][] x, double y[]) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Input vector x of size %d not equal to length %d of y", x.length, y.length));
        }

        for (int i = 0; i < x.length; i++){
            learn(x[i], y[i]);
        }
    }
    
    /**
     * Learn a new instance with online regression.
     * @param x the training instance. 
     * @param y the target value.
     */
    @Override
    public void learn(double[] x, double y) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        System.arraycopy(x, 0, x1, 0, p);
        double v = 1 + V.xax(x1);
        // If 1/v is NaN, then the update to V will no longer be invertible.
        // See https://en.wikipedia.org/wiki/Sherman%E2%80%93Morrison_formula#Statement
        if (Double.isNaN(1/v)){
            throw new IllegalStateException("The updated V matrix is no longer invertible.");
        }

        V.ax(x1, Vx);
        for (int j = 0; j <= p; j++) {
            for (int i = 0; i <= p; i++) {
                double tmp = V.get(i, j) - ((Vx[i] * Vx[j])/v);
                V.set(i, j, tmp/lambda);
            }
        }

        // V has been updated. Compute Vx again.
        V.ax(x1, Vx);
        
        double err = y - predict(x);
        for (int i = 0; i <= p; i++){
            w[i] += Vx[i] * err;
        }
    }

    /**
     * Get the forgetting factor
     * @return the forgetting factor
     */
    public double getForgettingFactor() {
        return lambda;
    }
    
    /**
     * Set the forgetting factor
     * @param lambda the forgetting factor
     */
    public void setForgettingFactor(double lambda) {
        if (lambda <= 0 || lambda > 1){
           throw new IllegalArgumentException("The forgetting factor must be in (0, 1]");
        }
        this.lambda = lambda;
    }
}
