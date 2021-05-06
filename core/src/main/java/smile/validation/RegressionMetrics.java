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

package smile.validation;

import java.io.Serializable;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.regression.DataFrameRegression;
import smile.regression.Regression;
import smile.validation.metric.*;

/** The regression validation metrics. */
public class RegressionMetrics implements Serializable {
    private static final long serialVersionUID = 2L;

    /** The time in milliseconds of fitting the model. */
    public final double fitTime;
    /** The time in milliseconds of scoring the validation data. */
    public final double scoreTime;
    /** The validation data size. */
    public final int size;
    /** The residual sum of squares on validation data. */
    public final double rss;
    /** The mean squared error on validation data. */
    public final double mse;
    /** The root mean squared error on validation data. */
    public final double rmse;
    /** The mean absolute deviation on validation data. */
    public final double mad;
    /** The R-squared score on validation data. */
    public final double r2;

    /**
     * Constructor.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param scoreTime the time in milliseconds of scoring the validation data.
     * @param size the validation data size.
     * @param rss the residual sum of squares on validation data.
     * @param mse the mean squared error on validation data.
     * @param rmse the root mean squared error on validation data.
     * @param mad the mean absolute deviation on validation data.
     * @param r2 the R-squared score on validation data.
     */
    public RegressionMetrics(double fitTime, double scoreTime, int size, double rss, double mse, double rmse, double mad, double r2) {
        this.fitTime = fitTime;
        this.scoreTime = scoreTime;
        this.size = size;
        this.rss = rss;
        this.mse = mse;
        this.rmse = rmse;
        this.mad = mad;
        this.r2 = r2;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{\n");
        if (!Double.isNaN(fitTime)) sb.append(String.format("  fit time: %.3f ms,\n", fitTime));
        sb.append(String.format("  score time: %.3f ms,\n", scoreTime));
        sb.append(String.format("  validation data size:: %d,\n", size));
        sb.append(String.format("  RSS: %.4f,\n", rss));
        sb.append(String.format("  MSE: %.4f,\n", mse));
        sb.append(String.format("  RMSE: %.4f,\n", rmse));
        sb.append(String.format("  MAD: %.4f,\n", mad));
        sb.append(String.format("  R2: %.2f%%\n}", 100 * r2));
        return sb.toString();
    }

    /**
     * Computes the regression metrics.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param scoreTime the time in milliseconds of scoring the validation data.
     * @param truth the ground truth.
     * @param prediction the predictions.
     * @return the validation metrics.
     */
    public static RegressionMetrics of(double fitTime, double scoreTime, double[] truth, double[] prediction) {
        return new RegressionMetrics(
                fitTime, scoreTime, truth.length,
                RSS.of(truth, prediction),
                MSE.of(truth, prediction),
                RMSE.of(truth, prediction),
                MAD.of(truth, prediction),
                R2.of(truth, prediction));
    }

    /**
     * Validates a model on a test data.
     * @param model the model.
     * @param testx the validation data.
     * @param testy the responsible variable of validation data.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation metrics.
     */
    public static <T, M extends Regression<T>> RegressionMetrics of(M model, T[] testx, double[] testy) {
        return of(Double.NaN, model, testx, testy);
    }

    /**
     * Validates a model on a test data.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param model the model.
     * @param testx the validation data.
     * @param testy the responsible variable of validation data.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation metrics.
     */
    public static <T, M extends Regression<T>> RegressionMetrics of(double fitTime, M model, T[] testx, double[] testy) {
        long start = System.nanoTime();
        double[] prediction = model.predict(testx);
        double scoreTime = (System.nanoTime() - start) / 1E6;

        return new RegressionMetrics(
                fitTime, scoreTime, testy.length,
                RSS.of(testy, prediction),
                MSE.of(testy, prediction),
                RMSE.of(testy, prediction),
                MAD.of(testy, prediction),
                R2.of(testy, prediction));
    }

    /**
     * Trains and validates a model on a train/validation split.
     * @param model the model.
     * @param formula the model formula.
     * @param test the validation data.
     * @param <M> the model type.
     * @return the validation metrics.
     */
    public static <M extends DataFrameRegression> RegressionMetrics of(M model, Formula formula, DataFrame test) {
        return of(Double.NaN, model, formula, test);
    }

    /**
     * Trains and validates a model on a train/validation split.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param model the model.
     * @param formula the model formula.
     * @param test the validation data.
     * @param <M> the model type.
     * @return the validation metrics.
     */
    public static <M extends DataFrameRegression> RegressionMetrics of(double fitTime, M model, Formula formula, DataFrame test) {
        double[] testy = formula.y(test).toDoubleArray();

        long start = System.nanoTime();
        int n = test.nrow();
        double[] prediction = new double[n];
        for (int i = 0; i < n; i++) {
            prediction[i] = model.predict(test.get(i));
        }
        double scoreTime = (System.nanoTime() - start) / 1E6;

        return new RegressionMetrics(
                fitTime, scoreTime, testy.length,
                RSS.of(testy, prediction),
                MSE.of(testy, prediction),
                RMSE.of(testy, prediction),
                MAD.of(testy, prediction),
                R2.of(testy, prediction));
    }
}
