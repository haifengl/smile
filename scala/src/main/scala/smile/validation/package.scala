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

package smile

import smile.classification.{Classifier, DataFrameClassifier}
import smile.data.DataFrame
import smile.data.formula.Formula
import smile.regression.{DataFrameRegression, Regression}
import smile.util.toJavaBiFunction
import smile.validation.metric._

/** Model validation.
  *
  * @author Haifeng Li
  */
package object validation {
  /** Computes the confusion matrix. */
  def confusion(truth: Array[Int], prediction: Array[Int]): ConfusionMatrix = ConfusionMatrix.of(truth, prediction)
  /** The accuracy is the proportion of true results (both true positives and
    * true negatives) in the population.
    */
  def accuracy(truth: Array[Int], prediction: Array[Int]): Double = Accuracy.of(truth, prediction)
  /** In information retrieval area, sensitivity is called recall. */
  def recall(truth: Array[Int], prediction: Array[Int]): Double = Recall.of(truth, prediction)
  /** The precision or positive predictive value (PPV) is ratio of true positives
    * to combined true and false positives, which is different from sensitivity.
    */
  def precision(truth: Array[Int], prediction: Array[Int]): Double = Precision.of(truth, prediction)
  /** Sensitivity or true positive rate (TPR) (also called hit rate, recall) is a
    * statistical measures of the performance of a binary classification test.
    * Sensitivity is the proportion of actual positives which are correctly
    * identified as such.
    */
  def sensitivity(truth: Array[Int], prediction: Array[Int]): Double = Sensitivity.of(truth, prediction)
  /** Specificity or True Negative Rate is a statistical measures of the
    * performance of a binary classification test. Specificity measures the
    * proportion of negatives which are correctly identified.
    */
  def specificity(truth: Array[Int], prediction: Array[Int]): Double = Specificity.of(truth, prediction)
  /** Fall-out, false alarm rate, or false positive rate (FPR).
    * Fall-out is actually Type I error and closely related to specificity
    * (1 - specificity).
    */
  def fallout(truth: Array[Int], prediction: Array[Int]): Double = Fallout.of(truth, prediction)
  /** The false discovery rate (FDR) is ratio of false positives
    * to combined true and false positives, which is actually 1 - precision.
    */
  def fdr(truth: Array[Int], prediction: Array[Int]): Double = FDR.of(truth, prediction)
  /** The F-score (or F-measure) considers both the precision and the recall of the test
    * to compute the score. The precision p is the number of correct positive results
    * divided by the number of all positive results, and the recall r is the number of
    * correct positive results divided by the number of positive results that should
    * have been returned.
    *
    * The traditional or balanced F-score (F1 score) is the harmonic mean of
    * precision and recall, where an F1 score reaches its best value at 1 and worst at 0.
    */
  def f1(truth: Array[Int], prediction: Array[Int]): Double = FScore.F1.score(truth, prediction)

  /** The area under the curve (AUC). When using normalized units, the area under
    * the curve is equal to the probability that a classifier will rank a
    * randomly chosen positive instance higher than a randomly chosen negative
    * one (assuming 'positive' ranks higher than 'negative').
    */
  def auc(truth: Array[Int], probability: Array[Double]): Double = AUC.of(truth, probability)

  /** Log loss is a evaluation metric for binary classifiers and it is sometimes
    * the optimization objective as well in case of logistic regression and neural
    * networks. Log Loss takes into account the uncertainty of the prediction
    * based on how much it varies from the actual label. This provides a more
    * nuanced view of the performance of the model. In general, minimizing
    * Log Loss gives greater accuracy for the classifier. However, it is
    * susceptible in case of imbalanced data.
    */
  def logloss(truth: Array[Int], probability: Array[Double]): Double = LogLoss.of(truth, probability)
  /** Cross entropy generalizes the log loss metric to multiclass problems. */
  def crossentropy(truth: Array[Int], probability: Array[Array[Double]]): Double = CrossEntropy.of(truth, probability)

  /** MCC is a correlation coefficient between prediction and actual values.
    * It is considered as a balanced measure for binary classification, even in unbalanced data sets.
    * It varies between -1 and +1. 1 when there is perfect agreement between ground truth and prediction,
    * -1 when there is a perfect disagreement between ground truth and predictions.
    * MCC of 0 means the model is not better then random.
    */
  def mcc(truth: Array[Int], prediction: Array[Int]): Double = MatthewsCorrelation.of(truth, prediction)

  /** Mean squared error. */
  def mse(truth: Array[Double], prediction: Array[Double]): Double = MSE.of(truth, prediction)
  /** Root mean squared error. */
  def rmse(truth: Array[Double], prediction: Array[Double]): Double = RMSE.of(truth, prediction)
  /** Residual sum of squares. */
  def rss(truth: Array[Double], prediction: Array[Double]): Double = RSS.of(truth, prediction)
  /** Mean absolute deviation error. */
  def mad(truth: Array[Double], prediction: Array[Double]): Double = MAD.of(truth, prediction)

  /** Rand index is defined as the number of pairs of objects
    * that are either in the same group or in different groups in both partitions
    * divided by the total number of pairs of objects. The Rand index lies between
    * 0 and 1. When two partitions agree perfectly, the Rand index achieves the
    * maximum value 1. A problem with Rand index is that the expected value of
    * the Rand index between two random partitions is not a constant. This problem
    * is corrected by the adjusted Rand index.
    */
  def randIndex(y1: Array[Int], y2: Array[Int]): Double = RandIndex.of(y1, y2)
  /** Adjusted Rand Index. Adjusted Rand Index assumes the generalized
    * hyper-geometric distribution as the model of randomness. The adjusted Rand
    * index has the maximum value 1, and its expected value is 0 in the case
    * of random clusters. A larger adjusted Rand index means a higher agreement
    * between two partitions. The adjusted Rand index is recommended for measuring
    * agreement even when the partitions compared have different numbers of clusters.
    */
  def adjustedRandIndex(y1: Array[Int], y2: Array[Int]): Double = AdjustedRandIndex.of(y1, y2)
  /** Normalized mutual information (normalized by max(H(y1), H(y2)) between two clusterings. */
  def nmi(y1: Array[Int], y2: Array[Int]): Double = NormalizedMutualInformation.max(y1, y2)

  object validate {
    /** Test a generic classifier.
      * The accuracy will be measured and printed out on standard output.
      *
      * @param x       training data.
      * @param y       training labels.
      * @param testx   test data.
      * @param testy   test data labels.
      * @param trainer a code block to return a classifier trained on the given data.
      * @tparam T the type of training and test data.
      * @return the trained classifier.
      */
    def classification[T <: AnyRef, M <: Classifier[T]](x: Array[T], y: Array[Int], testx: Array[T], testy: Array[Int])
                                             (trainer: (Array[T], Array[Int]) => M): ClassificationValidation[M] = {
      ClassificationValidation.of(x, y, testx, testy, trainer)
    }

    /** Test a generic classifier.
      * The accuracy will be measured and printed out on standard output.
      *
      * @param train   training data.
      * @param test    test data.
      * @param trainer a code block to return a classifier trained on the given data.
      * @return the trained classifier.
      */
    def classification[M <: DataFrameClassifier](formula: Formula, train: DataFrame, test: DataFrame)
                                    (trainer: (Formula, DataFrame) => M): ClassificationValidation[M] = {
      ClassificationValidation.of(formula, train, test, trainer)
    }

    /** Test a generic classifier.
      * The accuracy will be measured and printed out on standard output.
      *
      * @param x       training data.
      * @param y       training labels.
      * @param testx   test data.
      * @param testy   test data labels.
      * @param trainer a code block to return a classifier trained on the given data.
      * @tparam T the type of training and test data.
      * @return the trained classifier.
      */
    def regression[T <: AnyRef, M <: Regression[T]](x: Array[T], y: Array[Double], testx: Array[T], testy: Array[Double])
                                             (trainer: (Array[T], Array[Double]) => M): RegressionValidation[M] = {
      RegressionValidation.of(x, y, testx, testy, trainer)
    }

    /** Test a generic classifier.
      * The accuracy will be measured and printed out on standard output.
      *
      * @param train   training data.
      * @param test    test data.
      * @param trainer a code block to return a classifier trained on the given data.
      * @return the trained classifier.
      */
    def regression[M <: DataFrameRegression](formula: Formula, train: DataFrame, test: DataFrame)
                                    (trainer: (Formula, DataFrame) => M): RegressionValidation[M] = {
      RegressionValidation.of(formula, train, test, trainer)
    }
  }

  object loocv {
    /** Leave-one-out cross validation on a generic classifier. LOOCV uses a single observation
      * from the original sample as the validation data, and the remaining
      * observations as the training data. This is repeated such that each
      * observation in the sample is used once as the validation data. This is
      * the same as a K-fold cross-validation with K being equal to the number of
      * observations in the original sample. Leave-one-out cross-validation is
      * usually very expensive from a computational point of view because of the
      * large number of times the training process is repeated.
      *
      * @param x        data samples.
      * @param y        sample labels.
      * @param trainer  a code block to return a classifier trained on the given data.
      * @return metric scores.
      */
    def classification[T <: AnyRef](x: Array[T], y: Array[Int])
                                   (trainer: (Array[T], Array[Int]) => Classifier[T]): ClassificationMetrics = {
      LOOCV.classification(x, y, trainer)
    }

    /** Leave-one-out cross validation on a data frame classifier.
      *
      * @param formula  model formula.
      * @param data     data samples.
      * @param trainer  a code block to return a classifier trained on the given data.
      * @return metric scores.
      */
    def classification(formula: Formula, data: DataFrame)
                      (trainer: (Formula, DataFrame) => DataFrameClassifier): ClassificationMetrics = {
      LOOCV.classification(formula, data, trainer)
    }

    /** Leave-one-out cross validation on a generic regression model.
      *
      * @param x        data samples.
      * @param y        response variable.
      * @param trainer  a code block to return a regression model trained on the given data.
      * @return metric scores.
      */
    def regression[T <: AnyRef](x: Array[T], y: Array[Double])
                               (trainer: (Array[T], Array[Double]) => Regression[T]): RegressionMetrics = {
      LOOCV.regression(x, y, trainer)
    }

    /** Leave-one-out cross validation on a data frame regression model.
      *
      * @param formula  model formula.
      * @param data     data samples.
      * @param trainer  a code block to return a regression model trained on the given data.
      * @return metric scores.
      */
    def regression(formula: Formula, data: DataFrame)
                  (trainer: (Formula, DataFrame) => DataFrameRegression): RegressionMetrics = {
      LOOCV.regression(formula, data, trainer)
    }
  }

  object cv {
    /** Cross validation on a generic classifier.
      * Cross-validation is a technique for assessing how the results of a
      * statistical analysis will generalize to an independent data set.
      * It is mainly used in settings where the goal is prediction, and one
      * wants to estimate how accurately a predictive model will perform in
      * practice. One round of cross-validation involves partitioning a sample
      * of data into complementary subsets, performing the analysis on one subset
      * (called the training set), and validating the analysis on the other subset
      * (called the validation set or testing set). To reduce variability, multiple
      * rounds of cross-validation are performed using different partitions, and the
      * validation results are averaged over the rounds.
      *
      * @param x        data samples.
      * @param y        sample labels.
      * @param k        k-fold cross validation.
      * @param trainer  a code block to return a classifier trained on the given data.
      * @return metric scores.
      */
    def classification[T <: AnyRef, M <: Classifier[T]](k: Int, x: Array[T], y: Array[Int])
                                                       (trainer: (Array[T], Array[Int]) => M): ClassificationValidations[M] = {
      CrossValidation.classification(k, x, y, trainer)
    }

    /** Cross validation on a data frame classifier.
      *
      * @param formula  model formula.
      * @param data     data samples.
      * @param k        k-fold cross validation.
      * @param trainer  a code block to return a classifier trained on the given data.
      * @return metric scores.
      */
    def classification[M <: DataFrameClassifier](k: Int, formula: Formula, data: DataFrame)
                                              (trainer: (Formula, DataFrame) => M): ClassificationValidations[M] = {
      CrossValidation.classification(k, formula, data, trainer)
    }

    /** Cross validation on a generic regression model.
      *
      * @param x        data samples.
      * @param y        response variable.
      * @param k        k-fold cross validation.
      * @param trainer  a code block to return a regression model trained on the given data.
      * @return metric scores.
      */
    def regression[T <: AnyRef, M <: Regression[T]](k: Int, x: Array[T], y: Array[Double])
                                                   (trainer: (Array[T], Array[Double]) => M): RegressionValidations[M] = {
      CrossValidation.regression(k, x, y, trainer)
    }

    /** Cross validation on a data frame regression model.
      *
      * @param formula  model formula.
      * @param data     data samples.
      * @param k        k-fold cross validation.
      * @param trainer  a code block to return a regression model trained on the given data.
      * @return metric scores.
      */
    def regression[M <: DataFrameRegression](k: Int, formula: Formula, data: DataFrame)
                                          (trainer: (Formula, DataFrame) => M): RegressionValidations[M] = {
      CrossValidation.regression(k, formula, data, trainer)
    }
  }

  object bootstrap {
    /** Bootstrap validation on a generic classifier.
      * The bootstrap is a general tool for assessing statistical accuracy. The basic
      * idea is to randomly draw datasets with replacement from the training data,
      * each sample the same size as the original training set. This is done many
      * times (say k = 100), producing k bootstrap datasets. Then we refit the model
      * to each of the bootstrap datasets and examine the behavior of the fits over
      * the k replications.
      *
      * @param x       data samples.
      * @param y       sample labels.
      * @param k       k-round bootstrap estimation.
      * @param trainer a code block to return a classifier trained on the given data.
      * @return the error rates of each round.
      */
    def classification[T <: AnyRef, M <: Classifier[T]](k: Int, x: Array[T], y: Array[Int])
                                                       (trainer: (Array[T], Array[Int]) => M): ClassificationValidations[M] = {
      Bootstrap.classification(k, x, y, trainer)
    }

    /** Bootstrap validation on a data frame classifier.
      *
      * @param data    data samples.
      * @param k       k-round bootstrap estimation.
      * @param trainer a code block to return a classifier trained on the given data.
      * @return the error rates of each round.
      */
    def classification[M <: DataFrameClassifier](k: Int, formula: Formula, data: DataFrame)
                                              (trainer: (Formula, DataFrame) => M): ClassificationValidations[M] = {
      Bootstrap.classification(k, formula, data, trainer)
    }

    /** Bootstrap validation on a generic regression model.
      *
      * @param x        data samples.
      * @param y        response variable.
      * @param k        k-round bootstrap estimation.
      * @param trainer  a code block to return a regression model trained on the given data.
      * @return the root mean squared error of each round.
      */
    def regression[T <: AnyRef, M <: Regression[T]](k: Int, x: Array[T], y: Array[Double])
                                                   (trainer: (Array[T], Array[Double]) => M): RegressionValidations[M] = {
      Bootstrap.regression(k, x, y, trainer)
    }

    /** Bootstrap validation on a data frame regression model.
      *
      * @param data    data samples.
      * @param k       k-round bootstrap estimation.
      * @param trainer  a code block to return a regression model trained on the given data.
      * @return the root mean squared error of each round.
      */
    def regression[M <: DataFrameRegression](k: Int, formula: Formula, data: DataFrame)
                                          (trainer: (Formula, DataFrame) => M): RegressionValidations[M] = {
      Bootstrap.regression(k, formula, data, trainer)
    }
  }
}