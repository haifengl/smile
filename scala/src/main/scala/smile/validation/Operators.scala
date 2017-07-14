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