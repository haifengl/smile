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

import java.util.Arrays;
import java.util.Properties;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.QR;
import smile.math.matrix.SVD;
import smile.math.matrix.Cholesky;
import smile.math.special.Beta;

/**
 * Ordinary least squares. In linear regression,
 * the model specification is that the dependent variable is a linear
 * combination of the parameters (but need not be linear in the independent
 * variables). The residual is the difference between the value of the
 * dependent variable predicted by the model, and the true value of the
 * dependent variable. Ordinary least squares obtains parameter estimates
 * that minimize the sum of squared residuals, SSE (also denoted RSS).
 * <p>
 * The OLS estimator is consistent when the independent variables are
 * exogenous and there is no multicollinearity, and optimal in the class
 * of linear unbiased estimators when the errors are homoscedastic and
 * serially uncorrelated. Under these conditions, the method of OLS provides
 * minimum-variance mean-unbiased estimation when the errors have finite
 * variances.
 * <p>
 * There are several different frameworks in which the linear regression
 * model can be cast in order to make the OLS technique applicable. Each
 * of these settings produces the same formulas and same results, the only
 * difference is the interpretation and the assumptions which have to be
 * imposed in order for the method to give meaningful results. The choice
 * of the applicable framework depends mostly on the nature of data at hand,
 * and on the inference task which has to be performed.
 * <p>
 * Least squares corresponds to the maximum likelihood criterion if the
 * experimental errors have a normal distribution and can also be derived
 * as a method of moments estimator.
 * <p>
 * Once a regression model has been constructed, it may be important to
 * confirm the goodness of fit of the model and the statistical significance
 * of the estimated parameters. Commonly used checks of goodness of fit
 * include the R-squared, analysis of the pattern of residuals and hypothesis
 * testing. Statistical significance can be checked by an F-test of the overall
 * fit, followed by t-tests of individual parameters.
 * <p>
 * Interpretations of these diagnostic tests rest heavily on the model
 * assumptions. Although examination of the residuals can be used to
 * invalidate a model, the results of a t-test or F-test are sometimes more
 * difficult to interpret if the model's assumptions are violated.
 * For example, if the error term does not have a normal distribution,
 * in small samples the estimated parameters will not follow normal
 * distributions and complicate inference. With relatively large samples,
 * however, a central limit theorem can be invoked such that hypothesis
 * testing may proceed using asymptotic approximations.
 * 
 * @author Haifeng Li
 */
public class OLS {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OLS.class);

    /**
     * Fits an ordinary least squares model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     */
    public static LinearModel fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Properties());
    }

    /**
     * Fits an ordinary least squares model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param prop Training algorithm properties and hyper-parameters (if any) including "method" (svd or qr)
     *             for the fitting method, "standard.error" (boolean) to compute the estimated standard
     *             errors of the estimate of parameters, "eps" (default 1E-7) as the tolerance in SVD
     *             to detect if a singular value is zero.
     */
    public static LinearModel fit(Formula formula, DataFrame data, Properties prop) {
        DenseMatrix X = formula.matrix(data, true);
        double[] y = formula.response(data).toDoubleArray();

        int n = X.nrows();
        int p = X.ncols() - 1;
        
        if (n <= p) {
            throw new IllegalArgumentException(String.format("The input matrix is not over determined: %d rows, %d columns", n, p));
        }

        // weights and intercept
        double[] w1 = new double[p+1];


        QR qr = null;
        SVD svd = null;
        String method = prop.getProperty("method", "svd").toLowerCase();
        if (method.equalsIgnoreCase("svd")) {
            svd = X.svd(false);
            svd.solve(y, w1);
        } else {
            try {
                qr = X.qr(false);
                qr.solve(y, w1);
            } catch (RuntimeException e) {
                logger.warn("Matrix is not of full rank, try SVD instead");
                method = "svd";
                svd = X.svd(false);
                Arrays.fill(w1, 0.0);//re-init w1 with zero after exception caught
                svd.solve(y, w1);
            }
        }

        LinearModel model = new LinearModel();
        model.names = formula.predictors();
        model.formula = formula;
        model.p = p;
        model.b = w1[p];
        model.w = new double[p];
        System.arraycopy(w1, 0, model.w, 0, p);

        double[] fittedValues = new double[n];
        X.ax(w1, model.fittedValues);
        model.fitness(fittedValues, y, MathEx.mean(y));

        if (prop.getProperty("standard.error", "true").equalsIgnoreCase("true")) {
            double eps = Double.valueOf(prop.getProperty("eps", "1E-7"));
            double[][] ttest = new double[p + 1][4];
            model.ttest = ttest;
            if (method.equalsIgnoreCase("svd")) {
                for (int i = 0; i <= p; i++) {
                    ttest[i][0] = w1[i];
                    double s = svd.getSingularValues()[i];
                    if (!MathEx.isZero(s, eps)) {
                        double se = model.error / s;
                        ttest[i][1] = se;
                        double t = w1[i] / se;
                        ttest[i][2] = t;
                        ttest[i][3] = Beta.regularizedIncompleteBetaFunction(0.5 * model.df, 0.5, model.df / (model.df + t * t));
                    } else {
                        ttest[i][1] = Double.NaN;
                        ttest[i][2] = 0.0;
                        ttest[i][3] = 1.0;
                    }
                }
            } else {
                Cholesky cholesky = qr.CholeskyOfAtA();
                DenseMatrix inv = cholesky.inverse();

                for (int i = 0; i <= p; i++) {
                    ttest[i][0] = w1[i];
                    double se = model.error * Math.sqrt(inv.get(i, i));
                    ttest[i][1] = se;
                    double t = w1[i] / se;
                    ttest[i][2] = t;
                    ttest[i][3] = Beta.regularizedIncompleteBetaFunction(0.5 * model.df, 0.5, model.df / (model.df + t * t));
                }
            }
        }

        return model;
    }
}
