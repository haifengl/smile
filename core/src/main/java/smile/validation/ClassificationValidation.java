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
import java.util.Arrays;
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
import smile.validation.metric.*;
import smile.validation.metric.Error;

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

    /** Constructor. */
    public ClassificationValidation(M model, int[] truth, int[] prediction, double fitTime, double scoreTime) {
        this(model, truth, prediction, null, fitTime, scoreTime);
    }

    /** Constructor of soft classifier validation. */
    public ClassificationValidation(M model, int[] truth, int[] prediction, double[][] posteriori, double fitTime, double scoreTime) {
        this.model = model;
        this.truth = truth;
        this.prediction = prediction;
        this.posteriori = posteriori;
        this.confusion = ConfusionMatrix.of(truth, prediction);

        int k = MathEx.unique(truth).length;
        if (k == 2) {
            if (posteriori == null) {
                metrics = new ClassificationMetrics(fitTime, scoreTime, truth.length,
                        Error.of(truth, prediction),
                        Accuracy.of(truth, prediction),
                        Sensitivity.of(truth, prediction),
                        Specificity.of(truth, prediction),
                        Precision.of(truth, prediction),
                        FScore.F1.score(truth, prediction),
                        MatthewsCorrelation.of(truth, prediction)
                );
            } else {
                double[] probability = Arrays.stream(posteriori).mapToDouble(p -> p[1]).toArray();
                metrics = new ClassificationMetrics(fitTime, scoreTime, truth.length,
                        Error.of(truth, prediction),
                        Accuracy.of(truth, prediction),
                        Sensitivity.of(truth, prediction),
                        Specificity.of(truth, prediction),
                        Precision.of(truth, prediction),
                        FScore.F1.score(truth, prediction),
                        MatthewsCorrelation.of(truth, prediction),
                        AUC.of(truth, probability),
                        LogLoss.of(truth, probability)
                );
            }
        } else {
            if (posteriori == null) {
                metrics = new ClassificationMetrics(fitTime, scoreTime, truth.length,
                        Error.of(truth, prediction),
                        Accuracy.of(truth, prediction));
            } else {
                metrics = new ClassificationMetrics(fitTime, scoreTime, truth.length,
                        Error.of(truth, prediction),
                        Accuracy.of(truth, prediction),
                        CrossEntropy.of(truth, posteriori)
                );
            }
        }
    }

    @Override
    public String toString() {
        return metrics.toString();
    }

    /**
     * Trains and validates a model on a train/validation split.
     */
    public static <T, M extends Classifier<T>> ClassificationValidation<M> of(T[] x, int[] y, T[] testx, int[] testy, BiFunction<T[], int[], M> trainer) {
        int k = MathEx.unique(y).length;
        long start = System.nanoTime();
        M model = trainer.apply(x, y);
        double fitTime = (System.nanoTime() - start) / 1E6;

        if (model instanceof SoftClassifier) {
            start = System.nanoTime();
            double[][] posteriori = new double[testx.length][k];
            int[] prediction = ((SoftClassifier<T>) model).predict(testx, posteriori);
            double scoreTime = (System.nanoTime() - start) / 1E6;

            return new ClassificationValidation<>(model, testy, prediction, posteriori, fitTime, scoreTime);
        } else {
            start = System.nanoTime();
            int[] prediction = model.predict(testx);
            double scoreTime = (System.nanoTime() - start) / 1E6;

            return new ClassificationValidation<>(model, testy, prediction, fitTime, scoreTime);
        }
    }

    /**
     * Trains and validates a model on multiple train/validation split.
     */
    @SuppressWarnings("unchecked")
    public static <T, M extends Classifier<T>> ClassificationValidations<M> of(Split[] splits, T[] x, int[] y, BiFunction<T[], int[], M> trainer) {
        List<ClassificationValidation<M>> rounds = new ArrayList<>(splits.length);

        for (Split split : splits) {
            T[] trainx = MathEx.slice(x, split.train);
            int[] trainy = MathEx.slice(y, split.train);
            T[] testx = MathEx.slice(x, split.test);
            int[] testy = MathEx.slice(y, split.test);

            rounds.add(of(trainx, trainy, testx, testy, trainer));
        }

        return new ClassificationValidations<>(rounds);
    }

    /**
     * Trains and validates a model on a train/validation split.
     */
    @SuppressWarnings("unchecked")
    public static <M extends DataFrameClassifier> ClassificationValidation<M> of(Formula formula, DataFrame train, DataFrame test, BiFunction<Formula, DataFrame, M> trainer) {
        int[] y = formula.y(train).toIntArray();
        int[] testy = formula.y(test).toIntArray();

        int k = MathEx.unique(y).length;
        long start = System.nanoTime();
        M model = trainer.apply(formula, train);
        double fitTime = (System.nanoTime() - start) / 1E6;

        int n = test.nrows();
        if (model instanceof SoftClassifier) {
            start = System.nanoTime();
            int[] prediction = new int[n];
            double[][] posteriori = new double[n][k];
            for (int i = 0; i < n; i++) {
                prediction[i] = ((SoftClassifier<Tuple>) model).predict(test.get(i), posteriori[i]);
            }
            double scoreTime = (System.nanoTime() - start) / 1E6;

            return new ClassificationValidation<>(model, testy, prediction, posteriori, fitTime, scoreTime);
        } else {
            start = System.nanoTime();
            int[] prediction = new int[n];
            for (int i = 0; i < n; i++) {
                prediction[i] = model.predict(test.get(i));
            }
            double scoreTime = (System.nanoTime() - start) / 1E6;

            return new ClassificationValidation<>(model, testy, prediction, fitTime, scoreTime);
        }
    }

    /**
     * Trains and validates a model on multiple train/validation split.
     */
    @SuppressWarnings("unchecked")
    public static <M extends DataFrameClassifier> ClassificationValidations<M> of(Split[] splits, Formula formula, DataFrame data, BiFunction<Formula, DataFrame, M> trainer) {
        List<ClassificationValidation<M>> rounds = new ArrayList<>(splits.length);

        for (Split split : splits) {
            rounds.add(of(formula, data.of(split.train), data.of(split.test), trainer));
        }

        return new ClassificationValidations<>(rounds);
    }
}
