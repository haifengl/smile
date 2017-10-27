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
 * Recursive least squares is an online model that approximates ordinary least squares
 * by updating the coefficients with each new learning instance. Recursive least
 * squares does this by initializing a matrix called gamma, which is the inverse
 * of the matrix (X<sup>T</sup> X). gamma is updated with the Sherman-Morrison 
 * formula for each new learning instance x. After the gamma matrix has been updated
 * the coefficients W are updated with with the rule W = W - gamma*x(x*W - y).
 * 
 * @author Sam Erickson
 */
public class RLS implements OnlineRegression<double[]>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(RLS.class);
    
    /**
     * The linear weights.
     */
    private double[] w;
    /**
     * Intercept
     */
    private double b;
    /**
     * The dimensionality.
     */
    private int p;
    /**
    * The gamma matrix for recursive least squares used in online updates. 
    * The gamma matrix is updated with each learning instance in the learn()
    * method, and it is used to update the coefficients in online learning.
    * Gamma is first initialized to the matrix (X<sup>T</sup>X)<sup>-1</sup>.
    * 
    * A more complete description can be found on the wikipedia article
    * about online machine learning.
    */
    private DenseMatrix gamma;
   /**
    * A single learning instance X
    */
    private double[] X;
   /**
    * The coefficients W
    */
    private double[] W;
    /**
     * A temporary array used in computing gamma * X * X^T * gamma
     */
    private double[] gammaX;
    /**
     * The forgetting factor. Values closer to 1 will have longer "memory"
     * and values closer to 0 will be have shorter "memory"
     */
    private double lambda;
    
    /**
     * Trainer for linear regression by ordinary least squares.
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
     * Constructor.
     * @param p the dimensions of input columns
     */
    public RLS(int p){
        this(p, DenseMatrix.eye(p+1));
    }
    /**
     * 
     * @param p the dimensions of input columns
     * @param gamma the matrix used to update the coefficients
     */
    public RLS(int p, DenseMatrix gamma){
        this (p, gamma, 1);
    }
    /**
     * Constructor.
     * @param p the dimensions of input columns
     * @param gamma the matrix used to update the coefficients
     * @param lambda the forgetting factor
     */
    public RLS(int p, DenseMatrix gamma, double lambda){
        if (gamma.nrows() != gamma.ncols()){
            throw new IllegalArgumentException(String.format("gamma is not square: %d != %d", gamma.ncols(), gamma.nrows()));
        }
        if (p + 1 != gamma.nrows()){
            throw new IllegalArgumentException(String.format("The dimensions of gamma don't match the input dimensions: %d != %d", p + 1, gamma.nrows()));
        }
        if (lambda<=0 || lambda>1){
           throw new IllegalArgumentException("The forgetting factor must be between 0 (exclusive) and 1 (inclusive)"); 
        }
        
        this.p = p;
        this.gamma = gamma;
        this.lambda = lambda;
        
        w = new double[p];
        W = new double[p+1];
        X = new double[p+1];
        gammaX = new double[p+1];
    }
    
    /**
     * Constructor. Learn the ordinary least squares model to initialize gamma and coefficients.
     * @param x a matrix containing the explanatory variables. NO NEED to include a constant column of 1s for bias.
     * @param y the response values.
     */
    public RLS(double[][] x, double[] y) {
        this(x, y, false, 1);
    }
    /**
     * Constructor. Learn the ordinary least squares model to initialize gamma and coefficients.
     * @param x a matrix containing the explanatory variables. NO NEED to include a constant column of 1s for bias.
     * @param y the response values.
     * @param If true, use SVD to fit the model. Otherwise, use QR decomposition. SVD is slower than QR but
     *            can handle rank-deficient matrix.
     * @param lambda the forgetting factor.
     */
    public RLS(double[][] x, double[] y, boolean SVD, double lambda) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("The sizes of X and Y don't match: %d != %d", x.length, y.length));
        }
        if (lambda<=0 || lambda>1){
           throw new IllegalArgumentException("The forgetting factor must be between 0 (exclusive) and 1 (inclusive)"); 
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

        // weights and intercept
        this.W = new double[p+1];
        QR qr = null;
        SVD svd = null;
        if (SVD) {
            svd = X.svd();
            svd.solve(y, W);
        } else {
            try {
                qr = X.qr();
                qr.solve(y, W);
            } catch (RuntimeException e) {
                logger.warn("Matrix is not of full rank, try SVD instead");
                SVD = true;
                svd = X.svd();
                Arrays.fill(W, 0.0);
                svd.solve(y, W);
            }
        }
        b = W[p];
        w = new double[p];
        System.arraycopy(W, 0, w, 0, p);
        
        this.X = new double[p+1];
        this.gammaX = new double[p+1];
        
        if (SVD) {
            Cholesky cholesky = svd.CholeskyOfAtA();
            gamma = cholesky.inverse();
        } else {
            Cholesky cholesky = qr.CholeskyOfAtA();
            gamma = cholesky.inverse();
        }
    }

    /**
     * Returns the linear coefficients (without intercept).
     */
    public double[] coefficients() {
        return w;
    }

    /**
     * Returns the intercept.
     */
    public double intercept() {
        return b;
    }

    @Override
    public double predict(double[] x) {
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        return b + smile.math.Math.dot(x, w);
    }
    
    /**
     * Learn a new instance with online regression.
     * @param x the training instance. 
     * @param y the target value.
     */
    @Override
    public void learn(double[] x, double y){
        if (x.length != p) {
            throw new IllegalArgumentException(String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }
        System.arraycopy(x, 0, X, 0, p);
        X[p] = 1;
        updateGamma();
        updateW(y);
    }
    
    private void updateGamma(){
        double v = 1 + gamma.xax(X);
        gamma.ax(X, gammaX);
        for (int i = 0; i < p + 1; i++){
            for (int j = 0; j < p + 1; j++){
                double tmp = (1/lambda)*(gamma.get(i, j) - ((gammaX[i] * gammaX[j])/v));
                gamma.set(i, j, tmp);
            }
        }
    }
    
    private void updateW(double y){
        gamma.ax(X, gammaX);
        double err = smile.math.Math.dot(X, W) - y;
        for (int i = 0; i < p; i++){
            w[i] -= gammaX[i] * err;
            W[i] = w[i];
        }
        b -= gammaX[p] * err;
        W[p] = b;
    }
    
    /**
     * Learn a new instance with online regression.
     * @param x the training instances. 
     * @param y the target values.
     */
    public void learn(double[][] x, double y[]){
        if (x.length != y.length) {
            throw new IllegalArgumentException(String.format("Input vector x of size %d not equal to length %d of y", x.length, y.length));
        }
        for (int i = 0; i < x.length; i++){
            learn(x[i], y[i]);
        }
    }
    
    public DenseMatrix getGamma(){
        return gamma;
    }
    /**
     * 
     * @param gamma the gamma matrix used to update the coefficients.
     */
    public void setGamma(DenseMatrix gamma){
        if (gamma.nrows() != gamma.ncols()){
            throw new IllegalArgumentException(String.format("gamma is not square: %d != %d", gamma.ncols(), gamma.nrows()));
        }
        if (p + 1 != gamma.nrows()){
            throw new IllegalArgumentException(String.format("The dimensions of gamma don't match the input dimensions: %d != %d", p + 1, gamma.nrows()));
        }
        this.gamma = gamma;
    }
    /**
     * Get the forgetting factor
     * @return the forgetting factor
     */
    public double getForgettingFactor(){
        return lambda;
    }
    
    /**
     * Set the forgetting factor
     * @param lambda the forgetting factor
     */
    public void setForgettingFactor(double lambda){
        if (lambda<=0 || lambda>1){
           throw new IllegalArgumentException("The forgetting factor is not between 0 (exclusive) and 1 (inclusive)"); 
        }
        this.lambda = lambda;
    }
}
