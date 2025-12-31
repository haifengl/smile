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
package smile.validation;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import smile.classification.Classifier;
import smile.classification.DataFrameClassifier;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.math.MathEx;
import smile.validation.metric.*;
import smile.validation.metric.Error;

/**
 * The classification validation metrics.
 *
 * @param fitTime the time in milliseconds of fitting the model.
 * @param scoreTime the time in milliseconds of scoring the validation data.
 * @param size the validation data size.
 * @param error the number of errors.
 * @param accuracy the accuracy on validation data.
 * @param sensitivity the sensitivity on validation data.
 * @param specificity the specificity on validation data.
 * @param precision the precision on validation data.
 * @param f1 the F-1 score on validation data.
 * @param mcc the Matthews correlation coefficient on validation data.
 * @param auc the AUC on validation data.
 * @param logloss the log loss on validation data.
 * @param crossEntropy the cross entropy on validation data.
 *
 * @author Haifeng Li
 */
public record ClassificationMetrics(double fitTime, double scoreTime, int size, int error,
                                    double accuracy, double sensitivity, double specificity,
                                    double precision, double f1, double mcc, double auc,
                                    double logloss, double crossEntropy) implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;

    /**
     * Constructor.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param scoreTime the time in milliseconds of scoring the validation data.
     * @param size the validation data size.
     * @param error the number of errors.
     * @param accuracy the accuracy on validation data.
     */
    public ClassificationMetrics(double fitTime, double scoreTime, int size, int error, double accuracy) {
        this(fitTime, scoreTime, size, error, accuracy, Double.NaN);
    }

    /**
     * Constructor of multiclass soft classifier validation.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param scoreTime the time in milliseconds of scoring the validation data.
     * @param size the validation data size.
     * @param error the number of errors.
     * @param accuracy the accuracy on validation data.
     * @param crossEntropy the cross entropy on validation data.
     */
    public ClassificationMetrics(double fitTime, double scoreTime, int size, int error, double accuracy, double crossEntropy) {
        this(fitTime, scoreTime, size, error, accuracy, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, crossEntropy);
    }

    /**
     * Constructor of binary classifier validation.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param scoreTime the time in milliseconds of scoring the validation data.
     * @param size the validation data size.
     * @param error the number of errors.
     * @param accuracy the accuracy on validation data.
     * @param sensitivity the sensitivity on validation data.
     * @param specificity the specificity on validation data.
     * @param precision the precision on validation data.
     * @param f1 the F-1 score on validation data.
     * @param mcc the Matthews correlation coefficient on validation data.
     */
    public ClassificationMetrics(double fitTime, double scoreTime, int size, int error,
                                 double accuracy, double sensitivity, double specificity,
                                 double precision, double f1, double mcc) {
        this(fitTime, scoreTime, size, error, accuracy, sensitivity, specificity, precision, f1, mcc, Double.NaN, Double.NaN);
    }

    /**
     * Constructor of binary soft classifier validation.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param scoreTime the time in milliseconds of scoring the validation data.
     * @param size the validation data size.
     * @param error the number of errors.
     * @param accuracy the accuracy on validation data.
     * @param sensitivity the sensitivity on validation data.
     * @param specificity the specificity on validation data.
     * @param precision the precision on validation data.
     * @param f1 the F-1 score on validation data.
     * @param mcc the Matthews correlation coefficient on validation data.
     * @param auc the AUC on validation data.
     * @param logloss the log loss on validation data.
     */
    public ClassificationMetrics(double fitTime, double scoreTime, int size, int error,
                                 double accuracy, double sensitivity, double specificity,
                                 double precision, double f1, double mcc, double auc,
                                 double logloss) {
        this(fitTime, scoreTime, size, error, accuracy, sensitivity, specificity, precision, f1, mcc, auc, logloss, logloss);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{\n");
        if (!Double.isNaN(fitTime)) sb.append(String.format("  fit time: %.3f ms,\n", fitTime));
        sb.append(String.format("  score time: %.3f ms,\n", scoreTime));
        sb.append(String.format("  validation data size: %d,\n", size));
        sb.append(String.format("  error: %d,\n", error));
        sb.append(String.format("  accuracy: %.2f%%", 100 * accuracy));
        if (!Double.isNaN(sensitivity)) sb.append(String.format(",\n  sensitivity: %.2f%%", 100 * sensitivity));
        if (!Double.isNaN(specificity)) sb.append(String.format(",\n  specificity: %.2f%%", 100 * specificity));
        if (!Double.isNaN(precision)) sb.append(String.format(",\n  precision: %.2f%%", 100 * precision));
        if (!Double.isNaN(f1)) sb.append(String.format(",\n  F1 score: %.2f%%", 100 * f1));
        if (!Double.isNaN(mcc)) sb.append(String.format(",\n  MCC: %.2f%%", 100 * mcc));
        if (!Double.isNaN(auc)) sb.append(String.format(",\n  AUC: %.2f%%", 100 * auc));
        if (!Double.isNaN(logloss)) sb.append(String.format(",\n  log loss: %.4f", logloss));
        else if (!Double.isNaN(crossEntropy)) sb.append(String.format(",\n  cross entropy: %.4f", crossEntropy));
        sb.append("\n}");
        return sb.toString();
    }

    /**
     * Computes the basic classification metrics.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param scoreTime the time in milliseconds of scoring the validation data.
     * @param truth the ground truth.
     * @param prediction the predictions.
     * @return the classification metrics.
     */
    public static ClassificationMetrics of(double fitTime, double scoreTime, int[] truth, int[] prediction) {
        return new ClassificationMetrics(fitTime, scoreTime, truth.length,
                Error.of(truth, prediction),
                Accuracy.of(truth, prediction));
    }

    /**
     * Computes the binary classification metrics.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param scoreTime the time in milliseconds of scoring the validation data.
     * @param truth the ground truth.
     * @param prediction the predictions.
     * @return the classification metrics.
     */
    public static ClassificationMetrics binary(double fitTime, double scoreTime, int[] truth, int[] prediction) {
        return new ClassificationMetrics(fitTime, scoreTime, truth.length,
                Error.of(truth, prediction),
                Accuracy.of(truth, prediction),
                Sensitivity.of(truth, prediction),
                Specificity.of(truth, prediction),
                Precision.of(truth, prediction),
                FScore.F1.score(truth, prediction),
                MatthewsCorrelation.of(truth, prediction));
    }

    /**
     * Computes the soft classification metrics.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param scoreTime the time in milliseconds of scoring the validation data.
     * @param truth the ground truth.
     * @param prediction the predictions.
     * @param posteriori the posteriori probabilities of predictions.
     * @return the classification metrics.
     */
    public static ClassificationMetrics of(double fitTime, double scoreTime, int[] truth, int[] prediction, double[][] posteriori) {
        if (posteriori[0].length == 2) {
            double[] probability = Arrays.stream(posteriori).mapToDouble(p -> p[1]).toArray();
            return new ClassificationMetrics(fitTime, scoreTime, truth.length,
                    Error.of(truth, prediction),
                    Accuracy.of(truth, prediction),
                    Sensitivity.of(truth, prediction),
                    Specificity.of(truth, prediction),
                    Precision.of(truth, prediction),
                    FScore.F1.score(truth, prediction),
                    MatthewsCorrelation.of(truth, prediction),
                    AUC.of(truth, probability),
                    LogLoss.of(truth, probability));
        } else {
            return new ClassificationMetrics(fitTime, scoreTime, truth.length,
                    Error.of(truth, prediction),
                    Accuracy.of(truth, prediction),
                    CrossEntropy.of(truth, posteriori));
        }
    }

    /**
     * Computes the binary soft classification metrics.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param scoreTime the time in milliseconds of scoring the validation data.
     * @param truth the ground truth.
     * @param prediction the predictions.
     * @param probability the probabilities of positive predictions.
     * @return the classification metrics.
     */
    public static ClassificationMetrics binary(double fitTime, double scoreTime, int[] truth, int[] prediction, double[] probability) {
        return new ClassificationMetrics(fitTime, scoreTime, truth.length,
                Error.of(truth, prediction),
                Accuracy.of(truth, prediction),
                Sensitivity.of(truth, prediction),
                Specificity.of(truth, prediction),
                Precision.of(truth, prediction),
                FScore.F1.score(truth, prediction),
                MatthewsCorrelation.of(truth, prediction),
                AUC.of(truth, probability),
                LogLoss.of(truth, probability));
    }

    /**
     * Validates a model on a test data.
     * @param model the model.
     * @param testx the validation data.
     * @param testy the class labels of validation data.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation results.
     */
    public static <T, M extends Classifier<T>> ClassificationMetrics of(M model, T[] testx, int[] testy) {
        return of(Double.NaN, model, testx, testy);
    }

    /**
     * Validates a model on a test data.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param model the model.
     * @param testx the validation data.
     * @param testy the class labels of validation data.
     * @param <T> the data type of samples.
     * @param <M> the model type.
     * @return the validation results.
     */
    public static <T, M extends Classifier<T>> ClassificationMetrics of(double fitTime, M model, T[] testx, int[] testy) {
        int k = MathEx.unique(testy).length;
        long start = System.nanoTime();
        if (model.isSoft()) {
            double[][] posteriori = new double[testx.length][k];
            int[] prediction = model.predict(testx, posteriori);
            double scoreTime = (System.nanoTime() - start) / 1E6;

            return ClassificationMetrics.of(fitTime, scoreTime, testy, prediction, posteriori);
        } else {
            int[] prediction = model.predict(testx);
            double scoreTime = (System.nanoTime() - start) / 1E6;

            return ClassificationMetrics.of(fitTime, scoreTime, testy, prediction);
        }
    }

    /**
     * Validates a model on a test data.
     * @param model the model.
     * @param formula the model formula.
     * @param test the validation data.
     * @param <M> the model type.
     * @return the validation results.
     */
    public static <M extends DataFrameClassifier> ClassificationMetrics of(M model, Formula formula, DataFrame test) {
        return of(Double.NaN, model, formula, test);
    }

    /**
     * Validates a model on a test data.
     * @param fitTime the time in milliseconds of fitting the model.
     * @param model the model.
     * @param formula the model formula.
     * @param test the validation data.
     * @param <M> the model type.
     * @return the validation results.
     */
    public static <M extends DataFrameClassifier> ClassificationMetrics of(double fitTime, M model, Formula formula, DataFrame test) {
        int[] testy = formula.y(test).toIntArray();

        int k = MathEx.unique(testy).length;
        long start = System.nanoTime();
        int n = test.size();
        int[] prediction = new int[n];
        if (model.isSoft()) {
            double[][] posteriori = new double[n][k];
            for (int i = 0; i < n; i++) {
                prediction[i] = model.predict(test.get(i), posteriori[i]);
            }
            double scoreTime = (System.nanoTime() - start) / 1E6;

            return ClassificationMetrics.of(fitTime, scoreTime, testy, prediction, posteriori);
        } else {
            for (int i = 0; i < n; i++) {
                prediction[i] = model.predict(test.get(i));
            }
            double scoreTime = (System.nanoTime() - start) / 1E6;

            return ClassificationMetrics.of(fitTime, scoreTime, testy, prediction);
        }
    }
}
