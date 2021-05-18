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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.data.DataFrame;
import smile.regression.DataFrameRegression;
import smile.regression.Regression;

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
    /** The true response variable of validation data. */
    public final double[] truth;
    /** The model prediction. */
    public final double[] prediction;
    /** The regression metrics. */
    public final RegressionMetrics metrics;

    /**
     * Constructor.
     * @param model the model.
     * @param truth the ground truth.
     * @param prediction the predictions.
     * @param metrics the validation metrics.
     */
    public RegressionValidation(M model, double[] truth, double[] prediction, RegressionMetrics metrics) {
        this.model = model;
        this.truth = truth;
        this.prediction = prediction;
        this.metrics = metrics;
    }

    @Override
    public String toString() {
        return metrics.toString();
    }

    /**
     * Trains and validates a model on a train/validation split.
     * @param x the training data.
     * @param y the responsible variable of training data.
     * @param testx the validation data.
     * @param testy the responsible variable of validation data.
     * @param trainer the lambda to train the model.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation results.
     */
    public static <T, M extends Regression<T>> RegressionValidation<M> of(T[] x, double[] y, T[] testx, double[] testy, BiFunction<T[], double[], M> trainer) {
        long start = System.nanoTime();
        M model = trainer.apply(x, y);
        double fitTime = (System.nanoTime() - start) / 1E6;

        start = System.nanoTime();
        double[] prediction = model.predict(testx);
        double scoreTime = (System.nanoTime() - start) / 1E6;

        RegressionMetrics metrics = RegressionMetrics.of(fitTime, scoreTime, testy, prediction);
        return new RegressionValidation<>(model, testy, prediction, metrics);
    }

    /**
     * Trains and validates a model on multiple train/validation split.
     * @param bags the data splits.
     * @param x the training data.
     * @param y the responsible variable.
     * @param trainer the lambda to train the model.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation results.
     */
    public static <T, M extends Regression<T>> RegressionValidations<M> of(Bag[] bags, T[] x, double[] y, BiFunction<T[], double[], M> trainer) {
        List<RegressionValidation<M>> rounds = new ArrayList<>(bags.length);

        for (Bag bag : bags) {
            T[] trainx = MathEx.slice(x, bag.samples);
            double[] trainy = MathEx.slice(y, bag.samples);
            T[] testx = MathEx.slice(x, bag.oob);
            double[] testy = MathEx.slice(y, bag.oob);

            rounds.add(of(trainx, trainy, testx, testy, trainer));
        }

        return new RegressionValidations<>(rounds);
    }

    /**
     * Trains and validates a model on a train/validation split.
     * @param formula the model formula.
     * @param train the training data.
     * @param test the validation data.
     * @param trainer the lambda to train the model.
     * @param <M> the model type.
     * @return the validation results.
     */
    public static <M extends DataFrameRegression> RegressionValidation<M> of(Formula formula, DataFrame train, DataFrame test, BiFunction<Formula, DataFrame, M> trainer) {
        double[] testy = formula.y(test).toDoubleArray();

        long start = System.nanoTime();
        M model = trainer.apply(formula, train);
        double fitTime = (System.nanoTime() - start) / 1E6;

        start = System.nanoTime();
        int n = test.nrow();
        double[] prediction = new double[n];
        for (int i = 0; i < n; i++) {
            prediction[i] = model.predict(test.get(i));
        }
        double scoreTime = (System.nanoTime() - start) / 1E6;

        RegressionMetrics metrics = RegressionMetrics.of(fitTime, scoreTime, testy, prediction);
        return new RegressionValidation<>(model, testy, prediction, metrics);
    }

    /**
     * Trains and validates a model on multiple train/validation split.
     * @param bags the data splits.
     * @param formula the model formula.
     * @param data the data.
     * @param trainer the lambda to train the model.
     * @param <M> the model type.
     * @return the validation results.
     */
    public static <M extends DataFrameRegression> RegressionValidations<M> of(Bag[] bags, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        List<RegressionValidation<M>> rounds = new ArrayList<>(bags.length);

        for (Bag bag : bags) {
            rounds.add(of(formula, data.of(bag.samples), data.of(bag.oob), trainer));
        }

        return new RegressionValidations<>(rounds);
    }
}
