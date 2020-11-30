/*******************************************************************************
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
 ******************************************************************************/

package smile.validation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.data.DataFrame;
import smile.regression.DataFrameRegression;
import smile.regression.Regression;
import smile.validation.metric.*;

/**
 * Regression model validation results.
 *
 * @param <M> the regression model type.
 *
 * @author Haifeng
 */
public class RegressionValidation<M> implements Serializable {
    private static final long serialVersionUID = 2L;

    /** The model. */
    public final M model;
    /** The regression metrics. */
    public final RegressionMetrics metrics;

    /** Constructor. */
    public RegressionValidation(M model, double fitTime, double scoreTime, double rss, double mse, double rmse, double mad, double r2) {
        this.model = model;
        this.metrics = new RegressionMetrics(fitTime, scoreTime, rss, mse, rmse, mad, r2);
    }

    @Override
    public String toString() {
        return metrics.toString();
    }

    /**
     * Trains and validates a model on a train/validation split.
     */
    public static <T, M extends Regression<T>> RegressionValidation<M> of(T[] x, double[] y, T[] testx, double[] testy, BiFunction<T[], double[], M> trainer) {
        long start = System.nanoTime();
        M model = trainer.apply(x, y);
        double fitTime = (System.nanoTime() - start) / 1E6;

        start = System.nanoTime();
        double[] prediction = model.predict(testx);
        double scoreTime = (System.nanoTime() - start) / 1E6;

        return new RegressionValidation<>(model, fitTime, scoreTime,
                RSS.of(testy, prediction),
                MSE.of(testy, prediction),
                RMSE.of(testy, prediction),
                MAD.of(testy, prediction),
                R2.of(testy, prediction)
        );
    }

    /**
     * Trains and validates a model on multiple train/validation split.
     */
    @SuppressWarnings("unchecked")
    public static <T, M extends Regression<T>> RegressionValidations<M> of(Split[] splits, T[] x, double[] y, BiFunction<T[], double[], M> trainer) {
        List<RegressionValidation<M>> rounds = new ArrayList<>(splits.length);

        for (Split split : splits) {
            T[] trainx = MathEx.slice(x, split.train);
            double[] trainy = MathEx.slice(y, split.train);
            T[] testx = MathEx.slice(x, split.test);
            double[] testy = MathEx.slice(y, split.test);

            rounds.add(of(trainx, trainy, testx, testy, trainer));
        }

        return new RegressionValidations<>(rounds);
    }

    /**
     * Trains and validates a model on a train/validation split.
     */
    public static <M extends DataFrameRegression> RegressionValidation<M> of(Formula formula, DataFrame train, DataFrame test, BiFunction<Formula, DataFrame, M> trainer) {
        double[] testy = formula.y(test).toDoubleArray();

        long start = System.nanoTime();
        M model = trainer.apply(formula, train);
        double fitTime = (System.nanoTime() - start) / 1E6;

        start = System.nanoTime();
        int n = test.nrows();
        double[] prediction = new double[n];
        for (int i = 0; i < n; i++) {
            prediction[i] = model.predict(test.get(i));
        }
        double scoreTime = (System.nanoTime() - start) / 1E6;

        return new RegressionValidation<>(model, fitTime, scoreTime,
                RSS.of(testy, prediction),
                MSE.of(testy, prediction),
                RMSE.of(testy, prediction),
                MAD.of(testy, prediction),
                R2.of(testy, prediction)
        );
    }

    /**
     * Trains and validates a model on multiple train/validation split.
     */
    @SuppressWarnings("unchecked")
    public static <M extends DataFrameRegression> RegressionValidations<M> of(Split[] splits, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        List<RegressionValidation<M>> rounds = new ArrayList<>(splits.length);

        for (Split split : splits) {
            rounds.add(of(formula, data.of(split.train), data.of(split.test), trainer));
        }

        return new RegressionValidations<>(rounds);
    }
}
