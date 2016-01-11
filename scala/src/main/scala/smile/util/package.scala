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

package smile

import smile.classification.{Classifier, SoftClassifier}
import smile.validation._

/** Utility functions.
  *
  * @author Haifeng Li
  */
package object util {

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
  def test[T](x: Array[T], y: Array[Int], testx: Array[T], testy: Array[Int], parTest: Boolean = true)(trainer: => (Array[T], Array[Int]) => Classifier[T]): Classifier[T] = {
    val classifier = time {
      println("training...")
      trainer(x, y)
    }

    val pred = time {
      println("testing...")
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
  def test2[T](x: Array[T], y: Array[Int], testx: Array[T], testy: Array[Int], parTest: Boolean = true)(trainer: => (Array[T], Array[Int]) => Classifier[T]): Classifier[T] = {
    val classifier = time {
      println("training...")
      trainer(x, y)
    }

    val pred = time {
      println("testing...")
      if (parTest)
        testx.par.map(classifier.predict(_)).toArray
      else
        testx.map(classifier.predict(_))
    }

    println("Accuracy = %.2f%%" format (100.0 * new Accuracy().measure(testy, pred)))
    println("Sensitivity/Recall = %.2f%%" format (100.0 * new Sensitivity().measure(testy, pred)))
    println("Specificity = %.2f%%" format (100.0 * new Specificity().measure(testy, pred)))
    println("Precision = %.2f%%" format (100.0 * new Precision().measure(testy, pred)))
    println("F1-Score = %.2f%%" format (100.0 * new FScore().measure(testy, pred)))
    println("F2-Score = %.2f%%" format (100.0 * new FScore(2).measure(testy, pred)))
    println("F0.5-Score = %.2f%%" format (100.0 * new FScore(0.5).measure(testy, pred)))

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
  def test3[T](x: Array[T], y: Array[Int], testx: Array[T], testy: Array[Int], parTest: Boolean = true)(trainer: => (Array[T], Array[Int]) => SoftClassifier[T]): SoftClassifier[T] = {
    val classifier = time {
      println("training...")
      trainer(x, y)
    }

    val results = time {
      println("testing...")
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

    println("Accuracy = %.2f%%" format (100.0 * new Accuracy().measure(testy, pred)))
    println("Sensitivity/Recall = %.2f%%" format (100.0 * new Sensitivity().measure(testy, pred)))
    println("Specificity = %.2f%%" format (100.0 * new Specificity().measure(testy, pred)))
    println("Precision = %.2f%%" format (100.0 * new Precision().measure(testy, pred)))
    println("F1-Score = %.2f%%" format (100.0 * new FScore().measure(testy, pred)))
    println("F2-Score = %.2f%%" format (100.0 * new FScore(2).measure(testy, pred)))
    println("F0.5-Score = %.2f%%" format (100.0 * new FScore(0.5).measure(testy, pred)))
    println("AUC = %.2f%%" format (100.0 * AUC.measure(testy, prob)))

    classifier
  }

  /** Measure running time of a function/block */
  object time {
    /** Print out switch. */
    var echo = true

    /** Turn on printing out running time. */
    def on = {
      echo = true
    }

    /** Turn on printing out running time. */
    def off = {
      echo = false
    }

    /**
      * Executes a code block and measure the running time.
      * @param f a code block to measure the running time.
      * @tparam A The output type of code block.
      * @return the code block expression result.
      */
    def apply[A](f: => A) = {
      val s = System.nanoTime
      val ret = f
      if (echo) println("runtime: " + (System.nanoTime - s)/1e6 + " ms")
      ret
    }
  }
}
