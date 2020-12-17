/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.regression;

import java.io.Serializable;
import java.util.function.ToDoubleFunction;
import smile.math.MathEx;

/**
 * Regression analysis includes any techniques for modeling and analyzing
 * the relationship between a dependent variable and one or more independent
 * variables. Most commonly, regression analysis estimates the conditional
 * expectation of the dependent variable given the independent variables.
 * Regression analysis is widely used for prediction and forecasting, where
 * its use has substantial overlap with the field of machine learning. 
 * 
 * @author Haifeng Li
 */
public interface Regression<T> extends ToDoubleFunction<T>, Serializable {
    /**
     * Predicts the dependent variable of an instance.
     * @param x an instance.
     * @return the predicted value of dependent variable.
     */
    double predict(T x);

    /**
     * Predicts the dependent variables of an array of instances.
     *
     * @param x the instances.
     * @return the predicted values.
     */
    default double[] predict(T[] x) {
        int n = x.length;
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = predict(x[i]);
        }
        return y;
    }

    @Override
    default double applyAsDouble(T x) {
        return predict(x);
    }

    /**
     * Regression metrics.
     */
    class Metric {
        /**
         * The values of responsible variable.
         */
        public final double[] y;
        /**
         * The fitted values.
         */
        public final double[] fittedValues;
        /**
         * The residuals, that is response minus fitted values.
         */
        public final double[] residuals;
        /**
         * Residual sum of squares.
         */
        public final double RSS;
        /**
         * R<sup>2</sup> coefficient of determination. An R<sup>2</sup>
         * of 1.0 indicates that the regression line perfectly fits the data.
         */
        public final double RSquared;
        /**
         * Mean squared error.
         */
        public final double MSE;
        /**
         * Root mean squared error.
         */
        public final double RMSE;
        /**
         * Mean absolute deviation error.
         */
        public final double MAD;

        /** Constructor. */
        private Metric(double[] y, double[] fittedValues) {
            this.y = y;
            this.fittedValues = fittedValues;

            int n = y.length;
            residuals = new double[n];

            double mad = 0.0;
            double rss = 0.0;
            double TSS = 0.0;
            double ybar = MathEx.mean(y);
            for (int i = 0; i < n; i++) {
                double r = y[i] - fittedValues[i];
                residuals[i] = r;
                rss += r * r;
                mad += Math.abs(r);

                double t = y[i] - ybar;
                TSS += t * t;
            }

            RSS = rss;
            RSquared = 1.0 - RSS / TSS;

            MSE = Math.sqrt(rss/n);
            RMSE = Math.sqrt(MSE);

            MAD = mad / n;
        }
    }

    /**
     * Returns the regression metrics.
     * @param x the training samples.
     * @param y the responsible variable.
     * @return the gression metrics.
     */
    default Metric metric(T[] x, double[] y) {
        int n = x.length;
        double[] fittedValues = new double[n];
        for (int i = 0; i < n; i++) {
            fittedValues[i] = predict(x[i]);
        }
        return new Metric(y, fittedValues);
    }

    /**
     * Returns the regression metrics.
     * @param y the responsible variable.
     * @param fittedValues the fitted values.
     * @return the gression metrics.
     */
    default Metric metric(double[] y, double[] fittedValues) {
        return new Metric(y, fittedValues);
    }
}
