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

/**
 * Elastic Net regularization. The elastic net is a regularized regression
 * method that linearly combines the L1 and L2 penalties of the lasso and ridge
 * methods.
 * <p>
 * The elastic net problem can be reduced to a lasso problem on modified data
 * and response. And note that the penalty function of Elastic Net is strictly
 * convex so there is a unique global minimum, even if input data matrix is not
 * full rank.
 * 
 * <h2>References</h2>
 * <ol>
 * <li>Kevin P. Murphy: Machine Learning A Probabilistic Perspective, Section
 * 13.5.3, 2012</li>
 * <li>Zou, Hui, Hastie, Trevor: Regularization and Variable Selection via the
 * Elastic Net, 2005</li>
 * </ol>
 * 
 * @author rayeaster
 */
public class ElasticNet implements Regression<double[]> {
    private static final long serialVersionUID = 1L;
    /**
     * parameter for L1 regularization
     */
    private double lambda1 = 0.1;
    /**
     * parameter for L2 regularization
     */
    private double lambda2 = 0.1;
    /**
     * The dimensionality.
     */
    private int p;
    /**
     * corrected coefficients
     */
    private double[] w;
    /**
     * corrected intercept
     */
    private double b;

    /** reduced to lasso problem */
    private LASSO lasso;

    /** scaling calculated from lambda2 */
    private double c;

    /**
     * Constructor. Learn the Elastic Net regularized least squares model.
     * 
     * @param x
     *            a matrix containing the explanatory variables. NO NEED to include
     *            a constant column of 1s for bias.
     * @param y
     *            the response values.
     * @param lambda1
     *            the shrinkage/regularization parameter for L1
     * @param lambda2
     *            the shrinkage/regularization parameter for L2
     */
    public ElasticNet(double[][] x, double[] y, double lambda1, double lambda2) {
        this(x, y, lambda1, lambda2, 1E-4, 1000);
    }

    /**
     * Constructor. Learn the Elastic Net regularized least squares model.
     * 
     * @param x
     *            a matrix containing the explanatory variables. NO NEED to include
     *            a constant column of 1s for bias.
     * @param y
     *            the response values.
     * @param lambda1
     *            the shrinkage/regularization parameter for L1
     * @param lambda2
     *            the shrinkage/regularization parameter for L2
     * @param tol
     *            the tolerance for stopping iterations (relative target duality
     *            gap).
     * @param maxIter
     *            the maximum number of IPM (Newton) iterations.
     */
    public ElasticNet(double[][] x, double[] y, double lambda1, double lambda2, double tol, int maxIter) {
        if (lambda1 <= 0) {
            throw new IllegalArgumentException("Please use Ridge instead, wrong L1 portion setting:" + lambda1);
        }
        if (lambda2 <= 0) {
            throw new IllegalArgumentException("Please use LASSO instead, wrong L2 portion setting:" + lambda2);
        }

        this.lambda1 = lambda1;
        this.lambda2 = lambda2;
        this.c = 1 / Math.sqrt(1 + lambda2);
        this.p = x[0].length;
        lasso = new LASSO(getAugmentedData(x), getAugmentedResponse(y), this.lambda1 * c, tol, maxIter);

        w = new double[lasso.coefficients().length];
        double rescale = (1 / c);
        for (int i = 0; i < w.length; i++) {
            w[i] = rescale * lasso.coefficients()[i];
        }
        b = rescale * lasso.intercept();
    }

    @Override
    public double predict(double[] x) {
        if (x.length != p) {
            throw new IllegalArgumentException(
                    String.format("Invalid input vector size: %d, expected: %d", x.length, p));
        }

        return smile.math.Math.dot(x, w) + b;
    }

    /**
     * @return the linear coefficients.
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

    /**
     * @return the reduced {@link LASSO} model
     */
    public LASSO lasso() {
        return lasso;
    }

    /**
     * transform the original response array by padding 0 at the tail
     * 
     * @param y
     *            original response array
     * @return response array with padding 0 at tail
     */
    private double[] getAugmentedResponse(double[] y) {
        double[] ret = new double[y.length + p];
        System.arraycopy(y, 0, ret, 0, y.length);
        return ret;
    }

    /**
     * transform the original data array by padding a weighted identity matrix and
     * multiply a scaling
     * 
     * @param x
     *            the original data array
     * @return data with padding
     */
    private double[][] getAugmentedData(double[][] x) {
        double[][] ret = new double[x.length + p][p];
        double padding = c * Math.sqrt(lambda2);
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < p; j++) {
                ret[i][j] = c * x[i][j];
            }
        }
        for (int i = x.length; i < ret.length; i++) {
            ret[i][i - x.length] = padding;
        }
        return ret;
    }

}
