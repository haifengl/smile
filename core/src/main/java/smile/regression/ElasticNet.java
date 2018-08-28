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

import java.io.Serializable;
import smile.math.Math;
import smile.math.matrix.Matrix;
import smile.math.matrix.DenseMatrix;

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
public class ElasticNet implements Regression<double[]>, Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * hyperparameter for regularization
	 */
	private double lambda = 0.1;
	/**
	 * hyperparameter for L1 regularization portion
	 */
	private double lambda1p = 1;
	/**
	 * hyperparameter for L2 regularization portion
	 */
	private double lambda2p = 0;
	/**
	 * parameter for L1 regularization
	 */
	private double lambda1 = lambda1p * lambda;
	/**
	 * parameter for L2 regularization
	 */
	private double lambda2 = (1 - lambda1p) * lambda;
	/**
	 * The dimensionality.
	 */
	private int p;
	/**
	 * corrected coefficients
	 */
	private double[] correctedW = null;
	/**
	 * corrected intercept
	 */
	private double correctedIntercept;

	private LASSO modifiedLasso = null;

	/**
	 * Constructor. Learn the Elastic Net regularized least squares model.
	 * 
	 * @param x
	 *            a matrix containing the explanatory variables. NO NEED to include
	 *            a constant column of 1s for bias.
	 * @param y
	 *            the response values.
	 * @param lambda
	 *            the shrinkage/regularization parameter.
	 * @param lambda1p
	 *            the shrinkage/regularization portion for L1.
	 */
	public ElasticNet(double[][] x, double[] y, double lambda, double lambda1p) {
		this(x, y, lambda, lambda1p, 1E-4, 1000);
	}

	/**
	 * Constructor. Learn the Elastic Net regularized least squares model.
	 * 
	 * @param x
	 *            a matrix containing the explanatory variables. NO NEED to include
	 *            a constant column of 1s for bias.
	 * @param y
	 *            the response values.
	 * @param lambda
	 *            the shrinkage/regularization parameter.
	 * @param lambda1p
	 *            the shrinkage/regularization portion for L1.
	 * @param tol
	 *            the tolerance for stopping iterations (relative target duality
	 *            gap).
	 * @param maxIter
	 *            the maximum number of IPM (Newton) iterations.
	 */
	public ElasticNet(double[][] x, double[] y, double lambda, double lambda1p, double tol, int maxIter) {
		if (lambda1p <= 0) {
			throw new IllegalArgumentException("wrong L1 regularization setting:" + lambda1p);
		}
		if (lambda < 0) {
			throw new IllegalArgumentException("wrong regularization setting:" + lambda);
		}
		this.lambda1p = lambda1p;
		this.lambda2p = 1 - lambda1p;
		this.lambda1 = lambda1p * lambda;
		this.lambda2 = lambda2p * lambda;
		this.p = x[0].length;
		modifiedLasso = new LASSO(getModifiedData(x), getModifiedResponse(y), getModifiedLambda(), tol, maxIter);

		correctedW = new double[modifiedLasso.coefficients().length];
		for (int i = 0; i < correctedW.length; i++) {
			correctedW[i] = c() * modifiedLasso.coefficients()[i];
		}
		correctedIntercept = c() * modifiedLasso.intercept();
	}

	/**
	 * Constructor. Learn the Elastic Net regularized least squares model.
	 * 
	 * @param x
	 *            a matrix containing the explanatory variables. The variables
	 *            should be centered and standardized. NO NEED to include a constant
	 *            column of 1s for bias.
	 * @param y
	 *            the response values.
	 * @param lambda
	 *            the shrinkage/regularization parameter.
	 * @param lambda1p
	 *            the shrinkage/regularization portion for L1.
	 */
	public ElasticNet(Matrix x, double[] y, double lambda, double lambda1p) {
		this(x, y, lambda, lambda1p, 1E-4, 1000);
	}

	/**
	 * Constructor. Learn the Elastic Net regularized least squares model.
	 * 
	 * @param x
	 *            a matrix containing the explanatory variables. The variables
	 *            should be centered and standardized. NO NEED to include a constant
	 *            column of 1s for bias.
	 * @param y
	 *            the response values.
	 * @param lambda
	 *            the shrinkage/regularization parameter.
	 * @param lambda1p
	 *            the shrinkage/regularization portion for L1.
	 * @param tol
	 *            the tolerance for stopping iterations (relative target duality
	 *            gap).
	 * @param maxIter
	 *            the maximum number of IPM (Newton) iterations.
	 */
	public ElasticNet(Matrix x, double[] y, double lambda, double lambda1p, double tol, int maxIter) {
		if (lambda1p <= 0) {
			throw new IllegalArgumentException("wrong L1 regularization setting:" + lambda1p);
		}
		if (lambda < 0) {
			throw new IllegalArgumentException("wrong regularization setting:" + lambda);
		}
		this.lambda1p = lambda1p;
		this.lambda2p = 1 - lambda1p;
		this.lambda1 = lambda1p * lambda;
		this.lambda2 = lambda2p * lambda;
		this.p = x.ncols();
		modifiedLasso = new LASSO(getModifiedData(x), getModifiedResponse(y), getModifiedLambda(), tol, maxIter);

		correctedW = new double[modifiedLasso.coefficients().length];
		for (int i = 0; i < correctedW.length; i++) {
			correctedW[i] = c() * modifiedLasso.coefficients()[i];
		}
		correctedIntercept = c() * modifiedLasso.intercept();
	}

	@Override
	public double predict(double[] x) {
		if (lambda2 == 0) {
			return modifiedLasso.predict(x);
		}

		if (x.length != p) {
			throw new IllegalArgumentException(
					String.format("Invalid input vector size: %d, expected: %d", x.length, p));
		}

		return Math.dot(x, correctedW) + correctedIntercept;
	}

	public double[] getCorrectedW() {
		return correctedW;
	}

	public double getLambda() {
		return lambda;
	}

	public void setLambda(double lambda) {
		this.lambda = lambda;
	}

	public double getLambda1() {
		return lambda1;
	}

	public void setLambda1(double lambda1) {
		this.lambda1 = lambda1;
	}

	public double getLambda2() {
		return lambda2;
	}

	public void setLambda2(double lambda2) {
		this.lambda2 = lambda2;
	}

	public double getLambda1p() {
		return lambda1p;
	}

	public void setLambda1p(double lambda1p) {
		this.lambda1p = lambda1p;
	}

	public double getLambda2p() {
		return lambda2p;
	}

	public void setLambda2p(double lambda2p) {
		this.lambda2p = lambda2p;
	}

	public int getP() {
		return p;
	}

	public LASSO getModifiedLasso() {
		return modifiedLasso;
	}
	
	private double c() {
		return 1 / Math.sqrt(1 + lambda2);
	}

	private double getModifiedLambda() {
		return lambda1 * c();
	}

	private double[] getModifiedResponse(double[] y) {
		if (lambda2 == 0) {
			return y;
		}

		double[] ret = new double[y.length + p];
		for (int i = 0; i < ret.length; i++) {
			if (i <= y.length - 1) {
				ret[i] = y[i];
			} else {
				ret[i] = 0;
			}
		}
		return ret;
	}

	private double[][] getModifiedData(double[][] x) {
		if (lambda2 == 0) {
			return x;
		}

		double[][] ret = new double[x.length + p][p];
		for (int i = 0; i < ret.length; i++) {
			if (i <= x.length - 1) {
				for (int j = 0; j < p; j++) {
					ret[i][j] = c() * x[i][j];
				}
			} else {
				ret[i][i - x.length] = c() * Math.sqrt(lambda2);
			}
		}
		return ret;
	}

	private Matrix getModifiedData(Matrix x) {
		if (lambda2 == 0) {
			return x;
		}

		DenseMatrix ret = DenseMatrix.zeros(x.nrows() + p, p);
		for (int i = 0; i < ret.nrows(); i++) {
			if (i <= x.nrows() - 1) {
				for (int j = 0; j < p; j++) {
					ret.set(i, j, c() * x.get(i, j));
				}
			} else {
				ret.set(i, i - x.nrows(), c() * Math.sqrt(lambda2));
			}
		}
		return ret;
	}
}
