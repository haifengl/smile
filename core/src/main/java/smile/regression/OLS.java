/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.regression;

import java.util.Properties;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.StructType;
import smile.math.special.Beta;
import smile.tensor.*;

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
 * exogenous and there is no multi-collinearity, and optimal in the class
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

    /** Computational methods to fit the model. */
    public enum Method {
        /** QR decomposition. */
        QR,
        /** Singular value decomposition. */
        SVD
    }

    /** Private constructor to prevent object creation. */
    private OLS() {

    }

    /**
     * Least squares hyperparameters.
     * @param method the fitting method.
     * @param stderr if true, compute the standard errors of the estimate of parameters.
     * @param recursive if true, the return model supports recursive least squares.
     */
    public record Options(Method method, boolean stderr, boolean recursive) {
        /** Constructor. */
        public Options() {
            this(Method.QR, true, true);
        }

        /**
         * Returns the persistent set of hyperparameters including
         * <ul>
         * <li><code>smile.ols.method</code> (default "svd") is a string (svd or qr) for the fitting method
         * <li><code>smile.ols.standard.error</code> (default true) is a boolean. If true, compute the estimated standard
         *     errors of the estimate of parameters
         * <li><code>smile.ols.recursive</code>  (default true) is a boolean. If true, the return model supports recursive least squares
         * </ul>
         * @return the persistent set.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("smile.ols.method", method.toString());
            props.setProperty("smile.ols.standard_error", Boolean.toString(stderr));
            props.setProperty("smile.ols.recursive", Boolean.toString(recursive));
            return props;
        }

        /**
         * Returns the options from properties.
         *
         * @param props the hyperparameters.
         * @return the options.
         */
        public static Options of(Properties props) {
            Method method = Method.valueOf(props.getProperty("smile.ols.method", "QR"));
            boolean stderr = Boolean.parseBoolean(props.getProperty("smile.ols.standard_error", "true"));
            boolean recursive = Boolean.parseBoolean(props.getProperty("smile.ols.recursive", "true"));
            return new Options(method, stderr, recursive);
        }
    }

    /**
     * Fits an ordinary least squares model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @return the model.
     */
    public static LinearModel fit(Formula formula, DataFrame data) {
        return fit(formula, data, new Options());
    }

    /**
     * Fits an ordinary least squares model.
     * @param formula a symbolic description of the model to be fitted.
     * @param data the data frame of the explanatory and response variables.
     *             NO NEED to include a constant column of 1s for bias.
     * @param options the hyperparameters.
     * @return the model.
     */
    public static LinearModel fit(Formula formula, DataFrame data, Options options) {
        formula = formula.expand(data.schema());
        StructType schema = formula.bind(data.schema());

        DenseMatrix X = formula.matrix(data);
        double[] y = formula.y(data).toDoubleArray();

        int n = X.nrow();
        int p = X.ncol();

        if (n <= p) {
            throw new IllegalArgumentException(String.format("The input matrix is not over determined: %d rows, %d columns", n, p));
        }

        QR qr = null;
        Vector w = switch (options.method) {
            case SVD -> X.copy().svd().solve(y);
            case QR -> {
                try {
                    qr = X.copy().qr();
                    yield qr.solve(y);
                } catch (RuntimeException e) {
                    logger.warn("Matrix is not of full rank, try SVD instead");
                    yield X.copy().svd().solve(y);
                }
            }
        };

        LinearModel model = new LinearModel(formula, schema, X, y, w, 0.0);
        if (options.stderr || options.recursive) {
            Cholesky cholesky = qr == null ? X.ata().cholesky() : qr.toCholesky();
            DenseMatrix inv = cholesky.inverse();
            model.V = inv;

            if (options.stderr) {
                double[][] ttest = new double[p][4];
                model.ttest = ttest;
                for (int i = 0; i < p; i++) {
                    ttest[i][0] = w.get(i);
                    double se = model.error * Math.sqrt(inv.get(i, i));
                    ttest[i][1] = se;
                    double t = w.get(i) / se;
                    ttest[i][2] = t;
                    ttest[i][3] = Beta.regularizedIncompleteBetaFunction(0.5 * model.df, 0.5, model.df / (model.df + t * t));
                }
            }
        }

        return model;
    }
}
