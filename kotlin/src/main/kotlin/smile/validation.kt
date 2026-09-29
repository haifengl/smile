/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.validation

import smile.classification.Classifier
import smile.classification.DataFrameClassifier
import smile.data.DataFrame
import smile.data.formula.Formula
import smile.regression.DataFrameRegression
import smile.regression.Regression
import smile.validation.metric.*

/** Computes the confusion matrix. */
fun confusion(truth: IntArray, prediction: IntArray): ConfusionMatrix = ConfusionMatrix.of(truth, prediction)

/**
 * The accuracy is the proportion of true results (both true positives and
 * true negatives) in the population.
 */
fun accuracy(truth: IntArray, prediction: IntArray): Double = Accuracy.of(truth, prediction)

/** In information retrieval area, sensitivity is called recall. */
fun recall(truth: IntArray, prediction: IntArray): Double = Recall.of(truth, prediction)

/**
 * The precision or positive predictive value (PPV) is ratio of true positives
 * to combined true and false positives, which is different from sensitivity.
 */
fun precision(truth: IntArray, prediction: IntArray): Double = Precision.of(truth, prediction)

/**
 * Sensitivity or true positive rate (TPR) (also called hit rate, recall) is a
 * statistical measure of the performance of a binary classification test.
 * Sensitivity is the proportion of actual positives which are correctly
 * identified as such.
 */
fun sensitivity(truth: IntArray, prediction: IntArray): Double = Sensitivity.of(truth, prediction)

/**
 * Specificity or True Negative Rate is a statistical measure of the
 * performance of a binary classification test. Specificity measures the
 * proportion of negatives which are correctly identified.
 */
fun specificity(truth: IntArray, prediction: IntArray): Double = Specificity.of(truth, prediction)

/**
 * Fall-out, false alarm rate, or false positive rate (FPR).
 * Fall-out is actually Type I error and closely related to specificity
 * (1 - specificity).
 */
fun fallout(truth: IntArray, prediction: IntArray): Double = Fallout.of(truth, prediction)

/**
 * The false discovery rate (FDR) is ratio of false positives
 * to combined true and false positives, which is actually 1 - precision.
 */
fun fdr(truth: IntArray, prediction: IntArray): Double = FDR.of(truth, prediction)

/**
 * The F-score (or F-measure) considers both the precision and the recall of the test
 * to compute the score. The precision p is the number of correct positive results
 * divided by the number of all positive results, and the recall r is the number of
 * correct positive results divided by the number of positive results that should
 * have been returned.
 *
 * The traditional or balanced F-score (F1 score) is the harmonic mean of
 * precision and recall, where an F1 score reaches its best value at 1 and worst at 0.
 */
fun f1(truth: IntArray, prediction: IntArray): Double = FScore.F1.score(truth, prediction)

/**
 * Calculates the general F-score with custom beta and optional averaging strategy for multi-class.
 *
 * @param truth the ground truth.
 * @param prediction the prediction.
 * @param beta a positive value such that F-score measures the effectiveness of
 *             retrieval with respect to a user who attaches beta times as much
 *             importance to recall as precision.
 * @param strategy the aggregating strategy for multi-classes.
 * @return the F-score.
 */
fun fscore(truth: IntArray, prediction: IntArray, beta: Double = 1.0, strategy: Averaging? = null): Double =
    FScore.of(truth, prediction, beta, strategy)

/**
 * The area under the curve (AUC). When using normalized units, the area under
 * the curve is equal to the probability that a classifier will rank a
 * randomly chosen positive instance higher than a randomly chosen negative
 * one (assuming 'positive' ranks higher than 'negative').
 */
fun auc(truth: IntArray, probability: DoubleArray): Double = AUC.of(truth, probability)

/**
 * Log loss is an evaluation metric for binary classifiers and it is sometimes
 * the optimization objective as well in case of logistic regression and neural
 * networks. Log Loss takes into account the uncertainty of the prediction
 * based on how much it varies from the actual label. This provides a more
 * nuanced view of the performance of the model. In general, minimizing
 * Log Loss gives greater accuracy for the classifier. However, it is
 * susceptible in case of imbalanced data.
 */
fun logloss(truth: IntArray, probability: DoubleArray): Double = LogLoss.of(truth, probability)

/** Cross entropy generalizes the log loss metric to multiclass problems. */
fun crossentropy(truth: IntArray, probability: Array<DoubleArray>): Double = CrossEntropy.of(truth, probability)

/**
 * MCC is a correlation coefficient between prediction and actual values.
 * It is considered as a balanced measure for binary classification, even in unbalanced data sets.
 * It varies between -1 and +1. 1 when there is perfect agreement between ground truth and prediction,
 * -1 when there is a perfect disagreement between ground truth and predictions.
 * MCC of 0 means the model is not better than random.
 */
fun mcc(truth: IntArray, prediction: IntArray): Double = MatthewsCorrelation.of(truth, prediction)

/** Mean squared error. */
fun mse(truth: DoubleArray, prediction: DoubleArray): Double = MSE.of(truth, prediction)

/** Root mean squared error. */
fun rmse(truth: DoubleArray, prediction: DoubleArray): Double = RMSE.of(truth, prediction)

/** Residual sum of squares. */
fun rss(truth: DoubleArray, prediction: DoubleArray): Double = RSS.of(truth, prediction)

/** Mean absolute deviation error. */
fun mad(truth: DoubleArray, prediction: DoubleArray): Double = MAD.of(truth, prediction)

/**
 * R<sup>2</sup> coefficient of determination measures how well the regression
 * line approximates the real data points. An R<sup>2</sup> of 1.0 indicates
 * that the regression line perfectly fits the data.
 */
fun r2(truth: DoubleArray, prediction: DoubleArray): Double = R2.of(truth, prediction)

/**
 * Rand index is defined as the number of pairs of objects
 * that are either in the same group or in different groups in both partitions
 * divided by the total number of pairs of objects. The Rand index lies between
 * 0 and 1. When two partitions agree perfectly, the Rand index achieves the
 * maximum value 1. A problem with Rand index is that the expected value of
 * the Rand index between two random partitions is not a constant. This problem
 * is corrected by the adjusted Rand index.
 */
fun randIndex(y1: IntArray, y2: IntArray): Double = RandIndex.of(y1, y2)

/**
 * Adjusted Rand Index assumes the generalized hyper-geometric distribution
 * as the model of randomness. The adjusted Rand index has the maximum value 1,
 * and its expected value is 0 in the case of random clusters. A larger adjusted
 * Rand index means a higher agreement between two partitions. The adjusted
 * Rand index is recommended for measuring agreement even when the partitions
 * compared have different numbers of clusters.
 */
fun adjustedRandIndex(y1: IntArray, y2: IntArray): Double = AdjustedRandIndex.of(y1, y2)

/** Normalized mutual information (normalized by max(H(y1), H(y2))) between two clusterings. */
fun nmi(y1: IntArray, y2: IntArray): Double = NormalizedMutualInformation.max(y1, y2)

/** One-shot train/test evaluation runner. */
class ValidationRunner internal constructor() {
    /**
     * Test a generic classifier on a train/validation split.
     * The accuracy will be measured and printed out on standard output.
     *
     * @param x training data.
     * @param y training labels.
     * @param testx test data.
     * @param testy test data labels.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return the validation results.
     */
    fun <T, M : Classifier<T>> classification(
        x: Array<T>,
        y: IntArray,
        testx: Array<T>,
        testy: IntArray,
        trainer: (Array<T>, IntArray) -> M
    ): ClassificationValidation<M> {
        return ClassificationValidation.of(x, y, testx, testy, trainer)
    }

    /**
     * Test a data frame classifier on a train/validation split.
     * The accuracy will be measured and printed out on standard output.
     *
     * @param formula model formula.
     * @param train training data.
     * @param test test data.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return the validation results.
     */
    fun <M : DataFrameClassifier> classification(
        formula: Formula,
        train: DataFrame,
        test: DataFrame,
        trainer: (Formula, DataFrame) -> M
    ): ClassificationValidation<M> {
        return ClassificationValidation.of(formula, train, test, trainer)
    }

    /**
     * Test a generic regression model on a train/validation split.
     * The RMSE will be measured and printed out on standard output.
     *
     * @param x training data.
     * @param y response variable of training data.
     * @param testx test data.
     * @param testy response variable of test data.
     * @param trainer a lambda to return a regression model trained on the given data.
     * @return the validation results.
     */
    fun <T, M : Regression<T>> regression(
        x: Array<T>,
        y: DoubleArray,
        testx: Array<T>,
        testy: DoubleArray,
        trainer: (Array<T>, DoubleArray) -> M
    ): RegressionValidation<M> {
        return RegressionValidation.of(x, y, testx, testy, trainer)
    }

    /**
     * Test a data frame regression model on a train/validation split.
     * The RMSE will be measured and printed out on standard output.
     *
     * @param formula model formula.
     * @param train training data.
     * @param test test data.
     * @param trainer a lambda to return a regression model trained on the given data.
     * @return the validation results.
     */
    fun <M : DataFrameRegression> regression(
        formula: Formula,
        train: DataFrame,
        test: DataFrame,
        trainer: (Formula, DataFrame) -> M
    ): RegressionValidation<M> {
        return RegressionValidation.of(formula, train, test, trainer)
    }
}

val validate = ValidationRunner()

/** Leave-one-out cross validation runner. */
class LOOCValidation internal constructor() {
    /**
     * Leave-one-out cross validation on a generic classifier.
     *
     * @param x data samples.
     * @param y sample labels.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return metric scores.
     */
    fun <T, M : Classifier<T>> classification(
        x: Array<T>,
        y: IntArray,
        trainer: (Array<T>, IntArray) -> M
    ): ClassificationMetrics {
        return LOOCV.classification(x, y, trainer)
    }

    /**
     * Leave-one-out cross validation on a data frame classifier.
     *
     * @param formula model formula.
     * @param data data samples.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return metric scores.
     */
    fun classification(
        formula: Formula,
        data: DataFrame,
        trainer: (Formula, DataFrame) -> DataFrameClassifier
    ): ClassificationMetrics {
        return LOOCV.classification(formula, data, trainer)
    }

    /**
     * Leave-one-out cross validation on a generic regression model.
     *
     * @param x data samples.
     * @param y response variable.
     * @param trainer a lambda to return a regression model trained on the given data.
     * @return metric scores.
     */
    fun <T, M : Regression<T>> regression(
        x: Array<T>,
        y: DoubleArray,
        trainer: (Array<T>, DoubleArray) -> M
    ): RegressionMetrics {
        return LOOCV.regression(x, y, trainer)
    }

    /**
     * Leave-one-out cross validation on a data frame regression model.
     *
     * @param formula model formula.
     * @param data data samples.
     * @param trainer a lambda to return a regression model trained on the given data.
     * @return metric scores.
     */
    fun regression(
        formula: Formula,
        data: DataFrame,
        trainer: (Formula, DataFrame) -> DataFrameRegression
    ): RegressionMetrics {
        return LOOCV.regression(formula, data, trainer)
    }
}

val loocv = LOOCValidation()

/** Cross-validation runner. */
class CrossValidationRunner internal constructor() {
    /**
     * Cross validation on a generic classifier.
     *
     * @param k k-fold cross validation.
     * @param x data samples.
     * @param y sample labels.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return validation results.
     */
    fun <T, M : Classifier<T>> classification(
        k: Int,
        x: Array<T>,
        y: IntArray,
        trainer: (Array<T>, IntArray) -> M
    ): ClassificationValidations<M> {
        return CrossValidation.classification(k, x, y, trainer)
    }

    /**
     * Repeated cross validation on a generic classifier.
     *
     * @param round the number of rounds of repeated cross validation.
     * @param k k-fold cross validation.
     * @param x data samples.
     * @param y sample labels.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return validation results.
     */
    fun <T, M : Classifier<T>> classification(
        round: Int,
        k: Int,
        x: Array<T>,
        y: IntArray,
        trainer: (Array<T>, IntArray) -> M
    ): ClassificationValidations<M> {
        return CrossValidation.classification(round, k, x, y, trainer)
    }

    /**
     * Cross validation on a data frame classifier.
     *
     * @param k k-fold cross validation.
     * @param formula model formula.
     * @param data data samples.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return validation results.
     */
    fun <M : DataFrameClassifier> classification(
        k: Int,
        formula: Formula,
        data: DataFrame,
        trainer: (Formula, DataFrame) -> M
    ): ClassificationValidations<M> {
        return CrossValidation.classification(k, formula, data, trainer)
    }

    /**
     * Repeated cross validation on a data frame classifier.
     *
     * @param round the number of rounds of repeated cross validation.
     * @param k k-fold cross validation.
     * @param formula model formula.
     * @param data data samples.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return validation results.
     */
    fun <M : DataFrameClassifier> classification(
        round: Int,
        k: Int,
        formula: Formula,
        data: DataFrame,
        trainer: (Formula, DataFrame) -> M
    ): ClassificationValidations<M> {
        return CrossValidation.classification(round, k, formula, data, trainer)
    }

    /**
     * Stratified cross validation on a generic classifier.
     *
     * @param k k-fold cross validation.
     * @param x data samples.
     * @param y sample labels.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return validation results.
     */
    fun <T, M : Classifier<T>> stratify(
        k: Int,
        x: Array<T>,
        y: IntArray,
        trainer: (Array<T>, IntArray) -> M
    ): ClassificationValidations<M> {
        return CrossValidation.stratify(k, x, y, trainer)
    }

    /**
     * Repeated stratified cross validation on a generic classifier.
     *
     * @param round the number of rounds of repeated cross validation.
     * @param k k-fold cross validation.
     * @param x data samples.
     * @param y sample labels.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return validation results.
     */
    fun <T, M : Classifier<T>> stratify(
        round: Int,
        k: Int,
        x: Array<T>,
        y: IntArray,
        trainer: (Array<T>, IntArray) -> M
    ): ClassificationValidations<M> {
        return CrossValidation.stratify(round, k, x, y, trainer)
    }

    /**
     * Stratified cross validation on a data frame classifier.
     *
     * @param k k-fold cross validation.
     * @param formula model formula.
     * @param data data samples.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return validation results.
     */
    fun <M : DataFrameClassifier> stratify(
        k: Int,
        formula: Formula,
        data: DataFrame,
        trainer: (Formula, DataFrame) -> M
    ): ClassificationValidations<M> {
        return CrossValidation.stratify(k, formula, data, trainer)
    }

    /**
     * Repeated stratified cross validation on a data frame classifier.
     *
     * @param round the number of rounds of repeated cross validation.
     * @param k k-fold cross validation.
     * @param formula model formula.
     * @param data data samples.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return validation results.
     */
    fun <M : DataFrameClassifier> stratify(
        round: Int,
        k: Int,
        formula: Formula,
        data: DataFrame,
        trainer: (Formula, DataFrame) -> M
    ): ClassificationValidations<M> {
        return CrossValidation.stratify(round, k, formula, data, trainer)
    }

    /**
     * Cross validation on a generic regression model.
     *
     * @param k k-fold cross validation.
     * @param x data samples.
     * @param y response variable.
     * @param trainer a lambda to return a regression model trained on the given data.
     * @return validation results.
     */
    fun <T, M : Regression<T>> regression(
        k: Int,
        x: Array<T>,
        y: DoubleArray,
        trainer: (Array<T>, DoubleArray) -> M
    ): RegressionValidations<M> {
        return CrossValidation.regression(k, x, y, trainer)
    }

    /**
     * Repeated cross validation on a generic regression model.
     *
     * @param round the number of rounds of repeated cross validation.
     * @param k k-fold cross validation.
     * @param x data samples.
     * @param y response variable.
     * @param trainer a lambda to return a regression model trained on the given data.
     * @return validation results.
     */
    fun <T, M : Regression<T>> regression(
        round: Int,
        k: Int,
        x: Array<T>,
        y: DoubleArray,
        trainer: (Array<T>, DoubleArray) -> M
    ): RegressionValidations<M> {
        return CrossValidation.regression(round, k, x, y, trainer)
    }

    /**
     * Cross validation on a data frame regression model.
     *
     * @param k k-fold cross validation.
     * @param formula model formula.
     * @param data data samples.
     * @param trainer a lambda to return a regression model trained on the given data.
     * @return validation results.
     */
    fun <M : DataFrameRegression> regression(
        k: Int,
        formula: Formula,
        data: DataFrame,
        trainer: (Formula, DataFrame) -> M
    ): RegressionValidations<M> {
        return CrossValidation.regression(k, formula, data, trainer)
    }

    /**
     * Repeated cross validation on a data frame regression model.
     *
     * @param round the number of rounds of repeated cross validation.
     * @param k k-fold cross validation.
     * @param formula model formula.
     * @param data data samples.
     * @param trainer a lambda to return a regression model trained on the given data.
     * @return validation results.
     */
    fun <M : DataFrameRegression> regression(
        round: Int,
        k: Int,
        formula: Formula,
        data: DataFrame,
        trainer: (Formula, DataFrame) -> M
    ): RegressionValidations<M> {
        return CrossValidation.regression(round, k, formula, data, trainer)
    }
}

val cv = CrossValidationRunner()

/** Bootstrap validation runner. */
class BootstrapValidation internal constructor() {
    /**
     * Bootstrap validation on a generic classifier.
     * The bootstrap is a general tool for assessing statistical accuracy. The basic
     * idea is to randomly draw datasets with replacement from the training data,
     * each sample the same size as the original training set.
     *
     * @param k k-round bootstrap estimation.
     * @param x data samples.
     * @param y sample labels.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return the error rates of each round.
     */
    fun <T, M : Classifier<T>> classification(
        k: Int,
        x: Array<T>,
        y: IntArray,
        trainer: (Array<T>, IntArray) -> M
    ): ClassificationValidations<M> {
        return Bootstrap.classification(k, x, y, trainer)
    }

    /**
     * Bootstrap validation on a data frame classifier.
     *
     * @param k k-round bootstrap estimation.
     * @param formula model formula.
     * @param data data samples.
     * @param trainer a lambda to return a classifier trained on the given data.
     * @return the error rates of each round.
     */
    fun <M : DataFrameClassifier> classification(
        k: Int,
        formula: Formula,
        data: DataFrame,
        trainer: (Formula, DataFrame) -> M
    ): ClassificationValidations<M> {
        return Bootstrap.classification(k, formula, data, trainer)
    }

    /**
     * Bootstrap validation on a generic regression model.
     *
     * @param k k-round bootstrap estimation.
     * @param x data samples.
     * @param y response variable.
     * @param trainer a lambda to return a regression model trained on the given data.
     * @return the root mean squared error of each round.
     */
    fun <T, M : Regression<T>> regression(
        k: Int,
        x: Array<T>,
        y: DoubleArray,
        trainer: (Array<T>, DoubleArray) -> M
    ): RegressionValidations<M> {
        return Bootstrap.regression(k, x, y, trainer)
    }

    /**
     * Bootstrap validation on a data frame regression model.
     *
     * @param k k-round bootstrap estimation.
     * @param formula model formula.
     * @param data data samples.
     * @param trainer a lambda to return a regression model trained on the given data.
     * @return the root mean squared error of each round.
     */
    fun <M : DataFrameRegression> regression(
        k: Int,
        formula: Formula,
        data: DataFrame,
        trainer: (Formula, DataFrame) -> M
    ): RegressionValidations<M> {
        return Bootstrap.regression(k, formula, data, trainer)
    }
}

val bootstrap = BootstrapValidation()
