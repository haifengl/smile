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
import smile.classification.Classifier;
import smile.classification.DataFrameClassifier;
import smile.classification.SoftClassifier;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.validation.metric.ConfusionMatrix;

/**
 * Classification model validation results.
 *
 * @param <M> the model type.
 *
 * @author Haifeng
 */
public class ClassificationValidation<M> implements Serializable {
    private static final long serialVersionUID = 2L;

    /** The model. */
    public final M model;
    /** The true class labels of validation data. */
    public final int[] truth;
    /** The model prediction. */
    public final int[] prediction;
    /** The posteriori probability of prediction if the model is a soft classifier. */
    public final double[][] posteriori;
    /** The confusion matrix. */
    public final ConfusionMatrix confusion;
    /** The classification metrics. */
    public final ClassificationMetrics metrics;

    /**
     * Constructor.
     * @param model the model.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param scoreTime the time in milliseconds of scoring the validation data.
     * @param truth the ground truth.
     * @param prediction the predictions.
     */
    public ClassificationValidation(M model, double fitTime, double scoreTime, int[] truth, int[] prediction) {
        this.model = model;
        this.truth = truth;
        this.prediction = prediction;
        this.posteriori = null;
        this.confusion = ConfusionMatrix.of(truth, prediction);
        this.metrics = ClassificationMetrics.of(fitTime, scoreTime, truth, prediction);
    }

    /**
     * Constructor of soft classifier validation.
     * @param model the model.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param scoreTime the time in milliseconds of scoring the validation data.
     * @param truth the ground truth.
     * @param prediction the predictions.
     * @param posteriori the posteriori probabilities of predictions.
     */
    public ClassificationValidation(M model, double fitTime, double scoreTime, int[] truth, int[] prediction, double[][] posteriori) {
        this.model = model;
        this.truth = truth;
        this.prediction = prediction;
        this.posteriori = posteriori;
        this.confusion = ConfusionMatrix.of(truth, prediction);
        this.metrics = ClassificationMetrics.of(fitTime, scoreTime, truth, prediction, posteriori);
    }

    @Override
    public String toString() {
        return metrics.toString();
    }

    /**
     * Trains and validates a model on a train/validation split.
     * @param x the training data.
     * @param y the class labels of training data.
     * @param testx the validation data.
     * @param testy the class labels of validation data.
     * @param trainer the lambda to train the model.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation results.
     */
    public static <T, M extends Classifier<T>> ClassificationValidation<M> of(T[] x, int[] y, T[] testx, int[] testy, BiFunction<T[], int[], M> trainer) {
        int k = MathEx.unique(y).length;
        long start = System.nanoTime();
        M model = trainer.apply(x, y);
        double fitTime = (System.nanoTime() - start) / 1E6;

        start = System.nanoTime();
        if (model instanceof SoftClassifier) {
            double[][] posteriori = new double[testx.length][k];
            int[] prediction = ((SoftClassifier<T>) model).predict(testx, posteriori);
            double scoreTime = (System.nanoTime() - start) / 1E6;

            return new ClassificationValidation<>(model, fitTime, scoreTime, testy, prediction, posteriori);
        } else {
            int[] prediction = model.predict(testx);
            double scoreTime = (System.nanoTime() - start) / 1E6;

            return new ClassificationValidation<>(model, fitTime, scoreTime, testy, prediction);
        }
    }

    /**
     * Trains and validates a model on multiple train/validation split.
     * @param bags the data splits.
     * @param x the training data.
     * @param y the class labels.
     * @param trainer the lambda to train the model.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation results.
     */
    public static <T, M extends Classifier<T>> ClassificationValidations<M> of(Bag[] bags, T[] x, int[] y, BiFunction<T[], int[], M> trainer) {
        List<ClassificationValidation<M>> rounds = new ArrayList<>(bags.length);

        for (Bag bag : bags) {
            T[] trainx = MathEx.slice(x, bag.samples);
            int[] trainy = MathEx.slice(y, bag.samples);
            T[] testx = MathEx.slice(x, bag.oob);
            int[] testy = MathEx.slice(y, bag.oob);

            rounds.add(of(trainx, trainy, testx, testy, trainer));
        }

        return new ClassificationValidations<>(rounds);
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
    @SuppressWarnings("unchecked")
    public static <M extends DataFrameClassifier> ClassificationValidation<M> of(Formula formula, DataFrame train, DataFrame test, BiFunction<Formula, DataFrame, M> trainer) {
        int[] y = formula.y(train).toIntArray();
        int[] testy = formula.y(test).toIntArray();

        int k = MathEx.unique(y).length;
        long start = System.nanoTime();
        M model = trainer.apply(formula, train);
        double fitTime = (System.nanoTime() - start) / 1E6;

        int n = test.nrow();
        int[] prediction = new int[n];
        if (model instanceof SoftClassifier) {
            double[][] posteriori = new double[n][k];
            start = System.nanoTime();
            for (int i = 0; i < n; i++) {
                prediction[i] = ((SoftClassifier<Tuple>) model).predict(test.get(i), posteriori[i]);
            }
            double scoreTime = (System.nanoTime() - start) / 1E6;

            return new ClassificationValidation<>(model, fitTime, scoreTime, testy, prediction, posteriori);
        } else {
            start = System.nanoTime();
            for (int i = 0; i < n; i++) {
                prediction[i] = model.predict(test.get(i));
            }
            double scoreTime = (System.nanoTime() - start) / 1E6;

            return new ClassificationValidation<>(model, fitTime, scoreTime, testy, prediction);
        }
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
    public static <M extends DataFrameClassifier> ClassificationValidations<M> of(Bag[] bags, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        List<ClassificationValidation<M>> rounds = new ArrayList<>(bags.length);

        for (Bag bag : bags) {
            rounds.add(of(formula, data.of(bag.samples), data.of(bag.oob), trainer));
        }

        return new ClassificationValidations<>(rounds);
    }
}
