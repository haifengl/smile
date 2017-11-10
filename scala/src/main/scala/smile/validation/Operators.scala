/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.validation

import smile.classification.{SoftClassifier, Classifier}
import smile.regression.Regression
import smile.math.Math
import smile.util._

/** Model validation.
  *
  * @author Haifeng Li
  */
trait Operators {

  /** Computes the confusion matrix. */
  def confusion(truth: Array[Int], prediction: Array[Int]): ConfusionMatrix = new ConfusionMatrix(truth, prediction)
  /** The accuracy is the proportion of true results (both true positives and
    * true negatives) in the population.
    */
  def accuracy(truth: Array[Int], prediction: Array[Int]): Double = new Accuracy().measure(truth, prediction)
  /** In information retrieval area, sensitivity is called recall. */
  def recall(truth: Array[Int], prediction: Array[Int]): Double = new Recall().measure(truth, prediction)
  /** The precision or positive predictive value (PPV) is ratio of true positives
    * to combined true and false positives, which is different from sensitivity.
    */
  def precision(truth: Array[Int], prediction: Array[Int]): Double = new Precision().measure(truth, prediction)
  /** Sensitivity or true positive rate (TPR) (also called hit rate, recall) is a
    * statistical measures of the performance of a binary classification test.
    * Sensitivity is the proportion of actual positives which are correctly
    * identified as such.
    */
  def sensitivity(truth: Array[Int], prediction: Array[Int]): Double = new Sensitivity().measure(truth, prediction)
  /** Specificity or True Negative Rate is a statistical measures of the
    * performance of a binary classification test. Specificity measures the
    * proportion of negatives which are correctly identified.
    */
  def specificity(truth: Array[Int], prediction: Array[Int]): Double = new Specificity().measure(truth, prediction)
  /** Fall-out, false alarm rate, or false positive rate (FPR).
    * Fall-out is actually Type I error and closely related to specificity
    * (1 - specificity).
    */
  def fallout(truth: Array[Int], prediction: Array[Int]): Double = new Fallout().measure(truth, prediction)
  /** The false discovery rate (FDR) is ratio of false positives
    * to combined true and false positives, which is actually 1 - precision.
    */
  def fdr(truth: Array[Int], prediction: Array[Int]): Double = new FDR().measure(truth, prediction)
  /** The F-score (or F-measure) considers both the precision and the recall of the test
    * to compute the score. The precision p is the number of correct positive results
    * divided by the number of all positive results, and the recall r is the number of
    * correct positive results divided by the number of positive results that should
    * have been returned.
    *
    * The traditional or balanced F-score (F1 score) is the harmonic mean of
    * precision and recall, where an F1 score reaches its best value at 1 and worst at 0.
    */
  def f1(truth: Array[Int], prediction: Array[Int]): Double = new FMeasure().measure(truth, prediction)

  /** The area under the curve (AUC). When using normalized units, the area under
    * the curve is equal to the probability that a classifier will rank a
    * randomly chosen positive instance higher than a randomly chosen negative
    * one (assuming 'positive' ranks higher than 'negative').
    */
  def auc(truth: Array[Int], probability: Array[Double]): Double = AUC.measure(truth, probability)

  /** Mean squared error. */
  def mse(truth: Array[Double], prediction: Array[Double]): Double = new MSE().measure(truth, prediction)
  /** Root mean squared error. */
  def rmse(truth: Array[Double], prediction: Array[Double]): Double = new RMSE().measure(truth, prediction)
  /** Residual sum of squares. */
  def rss(truth: Array[Double], prediction: Array[Double]): Double = new RSS().measure(truth, prediction)
  /** Mean absolute deviation error. */
  def mad(truth: Array[Double], prediction: Array[Double]): Double = new MeanAbsoluteDeviation().measure(truth, prediction)

  /** Rand index is defined as the number of pairs of objects
    * that are either in the same group or in different groups in both partitions
    * divided by the total number of pairs of objects. The Rand index lies between
    * 0 and 1. When two partitions agree perfectly, the Rand index achieves the
    * maximum value 1. A problem with Rand index is that the expected value of
    * the Rand index between two random partitions is not a constant. This problem
    * is corrected by the adjusted Rand index.
    */
  def randIndex(truth: Array[Int], prediction: Array[Int]): Double = new RandIndex().measure(truth, prediction)
  /** Adjusted Rand Index. Adjusted Rand Index assumes the generalized
    * hyper-geometric distribution as the model of randomness. The adjusted Rand
    * index has the maximum value 1, and its expected value is 0 in the case
    * of random clusters. A larger adjusted Rand index means a higher agreement
    * between two partitions. The adjusted Rand index is recommended for measuring
    * agreement even when the partitions compared have different numbers of clusters.
    */
  def adjustedRandIndex(truth: Array[Int], prediction: Array[Int]): Double = new AdjustedRandIndex().measure(truth, prediction)
  /** Normalized mutual information score between two clusterings. */
  def mutualInformationScore(truth: Array[Int], prediction: Array[Int]): Double = new MutualInformationScore().measure(truth, prediction)

  /** Test a generic classifier.
    * The accuracy will be measured and printed out on standard output.
    *
    * @param x training data.
    * @param y training labels.
    * @param testx test data.
    * @param testy test data labels.
    * @param parTest Parallel test if true.
    * @param trainer a code block to return a classifier trained on the given data.
    * @tparam T the type of training and test data.
    * @return the trained classifier.
    */
  def test[T,  C <: Classifier[T]](x: Array[T], y: Array[Int], testx: Array[T], testy: Array[Int], parTest: Boolean = true)(trainer: => (Array[T], Array[Int]) => C): C = {
    println("training...")
    val classifier = time {
      trainer(x, y)
    }

    println("testing...")
    val pred = time {
      if (parTest)
        testx.par.map(classifier.predict(_)).toArray
      else
        testx.map(classifier.predict(_))
    }

    println("Accuracy = %.2f%%" format (100.0 * new Accuracy().measure(testy, pred)))
    println("Confusion Matrix: " + new ConfusionMatrix(testy, pred))

    classifier
  }

  /** Test a binary classifier.
    * The accuracy, sensitivity, specificity, precision, F-1 score, F-2 score, and F-0.5 score will be measured
    * and printed out on standard output.
    *
    * @param x training data.
    * @param y training labels.
    * @param testx test data.
    * @param testy test data labels.
    * @param parTest Parallel test if true.
    * @param trainer a code block to return a binary classifier trained on the given data.
    * @tparam T the type of training and test data.
    * @return the trained classifier.
    */
  def test2[T,  C <: Classifier[T]](x: Array[T], y: Array[Int], testx: Array[T], testy: Array[Int], parTest: Boolean = true)(trainer: => (Array[T], Array[Int]) => C): C = {
    println("training...")
    val classifier = time {
      trainer(x, y)
    }

    println("testing...")
    val pred = time {
      if (parTest)
        testx.par.map(classifier.predict(_)).toArray
      else
        testx.map(classifier.predict(_))
    }

    println("Accuracy = %.2f%%" format (100.0 * new Accuracy().measure(testy, pred)))
    println("Sensitivity/Recall = %.2f%%" format (100.0 * new Sensitivity().measure(testy, pred)))
    println("Specificity = %.2f%%" format (100.0 * new Specificity().measure(testy, pred)))
    println("Precision = %.2f%%" format (100.0 * new Precision().measure(testy, pred)))
    println("F1-Score = %.2f%%" format (100.0 * new FMeasure().measure(testy, pred)))
    println("F2-Score = %.2f%%" format (100.0 * new FMeasure(2).measure(testy, pred)))
    println("F0.5-Score = %.2f%%" format (100.0 * new FMeasure(0.5).measure(testy, pred)))
    println("Confusion Matrix: " + new ConfusionMatrix(testy, pred))

    classifier
  }


  /** Test a binary soft classifier.
    * The accuracy, sensitivity, specificity, precision, F-1 score, F-2 score, F-0.5 score, and AUC will be measured
    * and printed out on standard output.
    *
    * @param x training data.
    * @param y training labels.
    * @param testx test data.
    * @param testy test data labels.
    * @param parTest Parallel test if true.
    * @param trainer a code block to return a binary classifier trained on the given data.
    * @tparam T the type of training and test data.
    * @return the trained classifier.
    */
  def test2soft[T,  C <: SoftClassifier[T]](x: Array[T], y: Array[Int], testx: Array[T], testy: Array[Int], parTest: Boolean = true)(trainer: => (Array[T], Array[Int]) => C): C = {
    println("training...")
    val classifier = time {
      trainer(x, y)
    }

    println("testing...")
    val results = time {
      if (parTest)
        testx.par.map { xi =>
          val posteriori = Array(0.0, 0.0)
          val yi = classifier.predict(xi, posteriori)
          (yi, posteriori(1))
        }.toArray
      else {
        val posteriori = Array(0.0, 0.0)
        testx.map { xi =>
          val yi = classifier.predict(xi, posteriori)
          (yi, posteriori(1))
        }
      }
    }

    val (pred, prob) = results.unzip
    val prediction = pred.toArray
    val probability = prob.toArray

    println("Accuracy = %.2f%%" format (100.0 * new Accuracy().measure(testy, prediction)))
    println("Sensitivity/Recall = %.2f%%" format (100.0 * new Sensitivity().measure(testy, prediction)))
    println("Specificity = %.2f%%" format (100.0 * new Specificity().measure(testy, prediction)))
    println("Precision = %.2f%%" format (100.0 * new Precision().measure(testy, prediction)))
    println("F1-Score = %.2f%%" format (100.0 * new FMeasure().measure(testy, prediction)))
    println("F2-Score = %.2f%%" format (100.0 * new FMeasure(2).measure(testy, prediction)))
    println("F0.5-Score = %.2f%%" format (100.0 * new FMeasure(0.5).measure(testy, prediction)))
    println("AUC = %.2f%%" format (100.0 * AUC.measure(testy, probability)))
    println("Confusion Matrix: " + new ConfusionMatrix(testy, prediction))

    classifier
  }

  private def measuresOrAccuracy(measures: Seq[ClassificationMeasure]): Seq[ClassificationMeasure] = {
    if (measures.isEmpty) Seq(new Accuracy) else measures
  }

  private def measuresOrRMSE(measures: Seq[RegressionMeasure]): Seq[RegressionMeasure] = {
    if (measures.isEmpty) Seq(new RMSE) else measures
  }

  /** Leave-one-out cross validation on a generic classifier. LOOCV uses a single observation
    * from the original sample as the validation data, and the remaining
    * observations as the training data. This is repeated such that each
    * observation in the sample is used once as the validation data. This is
    * the same as a K-fold cross-validation with K being equal to the number of
    * observations in the original sample. Leave-one-out cross-validation is
    * usually very expensive from a computational point of view because of the
    * large number of times the training process is repeated.
    *
    * @param x data samples.
    * @param y sample labels.
    * @param measures validation measures such as accuracy, specificity, etc.
    * @param trainer a code block to return a classifier trained on the given data.
    * @return measure results.
    */
  def loocv[T <: Object](x: Array[T], y: Array[Int], measures: ClassificationMeasure*)(trainer: => (Array[T], Array[Int]) => Classifier[T]): Array[Double] = {
    val n = x.length
    val predictions = new Array[Int](n)

    val split = new LOOCV(n)
    for (i <- 0 until n) {
      print(s"loocv ${i+1}...")
      val trainx = Math.slice[T](x, split.train(i))
      val trainy = Math.slice(y, split.train(i))
      val model = trainer(trainx, trainy)
      predictions(split.test(i)) = model.predict(x(split.test(i)))
    }

    println("Confusion Matrix: " + new ConfusionMatrix(y, predictions))

    measuresOrAccuracy(measures).map { measure =>
      val result = measure.measure(y, predictions)
      println(f"$measure%s: ${100*result}%.2f%%")
      result
    }.toArray
  }

  /** Leave-one-out cross validation on a generic regression model.
    *
    * @param x data samples.
    * @param y response variable.
    * @param measures validation measures such as MSE, AbsoluteDeviation, etc.
    * @param trainer a code block to return a regression model trained on the given data.
    * @return measure results.
    */
  def loocv[T <: Object](x: Array[T], y: Array[Double], measures: RegressionMeasure*)(trainer: => (Array[T], Array[Double]) => Regression[T]): Array[Double] = {
    val n = x.length
    val predictions = new Array[Double](n)

    val split = new LOOCV(n)
    for (i <- 0 until n) {
      print(s"loocv ${i+1}...")
      val trainx = Math.slice[T](x, split.train(i))
      val trainy = Math.slice(y, split.train(i))
      val model = trainer(trainx, trainy)
      predictions(split.test(i)) = model.predict(x(split.test(i)))
    }

    measuresOrRMSE(measures).map { measure =>
      val result = measure.measure(y, predictions)
      println(f"$measure%s: $result%.4f")
      result
    }.toArray
  }

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
    * @param x data samples.
    * @param y sample labels.
    * @param k k-fold cross validation.
    * @param measures validation measures such as accuracy, specificity, etc.
    * @param trainer a code block to return a classifier trained on the given data.
    * @return measure results.
    */
  def cv[T <: Object](x: Array[T], y: Array[Int], k: Int, measures: ClassificationMeasure*)(trainer: => (Array[T], Array[Int]) => Classifier[T]): Array[Double] = {
    val n = x.length
    val predictions = new Array[Int](n)

    val split = new CrossValidation(n, k)
    for (i <- 0 until k) {
      print(s"cv ${i+1}...")
      val trainx = Math.slice[T](x, split.train(i))
      val trainy = Math.slice(y, split.train(i))
      val model = trainer(trainx, trainy)
      split.test(i).foreach { j =>
        predictions(j) = model.predict(x(j))
      }
    }

    println("Confusion Matrix: " + new ConfusionMatrix(y, predictions))

    measuresOrAccuracy(measures).map { measure =>
      val result = measure.measure(y, predictions)
      println(f"$measure%s: ${100*result}%.2f%%")
      result
    }.toArray
  }

  /** Cross validation on a generic regression model.
    *
    * @param x data samples.
    * @param y response variable.
    * @param k k-fold cross validation.
    * @param measures validation measures such as MSE, AbsoluteDeviation, etc.
    * @param trainer a code block to return a regression model trained on the given data.
    * @return measure results.
    */
  def cv[T <: Object](x: Array[T], y: Array[Double], k: Int, measures: RegressionMeasure*)(trainer: => (Array[T], Array[Double]) => Regression[T]): Array[Double] = {
    val n = x.length
    val predictions = new Array[Double](n)

    val split = new CrossValidation(n, k)
    for (i <- 0 until k) {
      print(s"cv ${i+1}...")
      val trainx = Math.slice[T](x, split.train(i))
      val trainy = Math.slice(y, split.train(i))
      val model = trainer(trainx, trainy)
      split.test(i).foreach { j =>
        predictions(j) = model.predict(x(j))
      }
    }

    measuresOrRMSE(measures).map { measure =>
      val result = measure.measure(y, predictions)
      println(f"$measure%s: $result%.4f")
      result
    }.toArray
  }

  /** Bootstrap validation on a generic classifier.
    * The bootstrap is a general tool for assessing statistical accuracy. The basic
    * idea is to randomly draw datasets with replacement from the training data,
    * each sample the same size as the original training set. This is done many
    * times (say k = 100), producing k bootstrap datasets. Then we refit the model
    * to each of the bootstrap datasets and examine the behavior of the fits over
    * the k replications.
    *
    * @param x data samples.
    * @param y sample labels.
    * @param k k-round bootstrap estimation.
    * @param measures validation measures such as accuracy, specificity, etc.
    * @param trainer a code block to return a classifier trained on the given data.
    * @return measure results.
    */
  def bootstrap[T <: Object](x: Array[T], y: Array[Int], k: Int, measures: ClassificationMeasure*)(trainer: => (Array[T], Array[Int]) => Classifier[T]): Array[Double] = {
    val split = new Bootstrap(x.length, k)

    val m = measuresOrAccuracy(measures)
    val results = (0 until k).map { i =>
      print(s"bootstrap ${i+1}...")
      val trainx = Math.slice[T](x, split.train(i))
      val trainy = Math.slice(y, split.train(i))
      val model = trainer(trainx, trainy)

      val nt = split.test(i).length
      val truth = new Array[Int](nt)
      val predictions = new Array[Int](nt)
      for (j <- 0 until nt) {
        val l = split.test(i)(j)
        truth(j) = y(l)
        predictions(j) = model.predict(x(l))
      }

      m.map { measure =>
        val result = measure.measure(truth, predictions)
        println(f"$measure%s: ${100*result}%.2f%%")
        result
      }.toArray
    }.toArray

    val avg = Math.colMeans(results)
    println("Bootstrap average:")
    for (i <- 0 until avg.length) {
      println(f"${m(i)}%s: ${100*avg(i)}%.2f%%")
    }

    avg
  }

  /** Bootstrap validation on a generic regression model.
    *
    * @param x data samples.
    * @param y response variable.
    * @param k k-round bootstrap estimation.
    * @param measures validation measures such as MSE, AbsoluteDeviation, etc.
    * @param trainer a code block to return a regression model trained on the given data.
    * @return measure results.
    */
  def bootstrap[T <: Object](x: Array[T], y: Array[Double], k: Int, measures: RegressionMeasure*)(trainer: => (Array[T], Array[Double]) => Regression[T]): Array[Double] = {
    val split = new Bootstrap(x.length, k)

    val m = measuresOrRMSE(measures)
    val results = (0 until k).map { i =>
      print(s"bootstrap ${i+1}...")
      val trainx = Math.slice[T](x, split.train(i))
      val trainy = Math.slice(y, split.train(i))
      val model = trainer(trainx, trainy)

      val nt = split.test(i).length
      val truth = new Array[Double](nt)
      val predictions = new Array[Double](nt)
      for (j <- 0 until nt) {
        val l = split.test(i)(j)
        truth(j) = y(l)
        predictions(j) = model.predict(x(l))
      }

      m.map { measure =>
        val result = measure.measure(truth, predictions)
        println(f"$measure%s: $result%.4f")
        result
      }.toArray
    }.toArray

    val avg = Math.colMeans(results)
    println("Bootstrap average:")
    for (i <- 0 until avg.length) {
      println(f"${m(i)}%s: ${avg(i)}%.4f")
    }

    avg
  }
}