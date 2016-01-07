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

import smile.classification.Classifier
import smile.validation.{AUC, Specificity, Sensitivity, Accuracy}

/** Utility functions.
  *
  * @author Haifeng Li
  */
package object util {

  /** Test a classifier. */
  def test[T](x: Array[T], y: Array[Int], testx: Array[T], testy: Array[Int])(trainer: => (Array[T], Array[Int]) => Classifier[T]): Classifier[T] = {
    val classifier = time {
      trainer(x, y)
    }

    val pred = testx.map(classifier.predict(_))

    println("Accuracy = %.2f%%" format (100.0 * new Accuracy().measure(testy, pred)))

    classifier
  }

  /** Test a binary classifier. */
  def test2[T](x: Array[T], y: Array[Int], testx: Array[T], testy: Array[Int], auc: Boolean = true)(trainer: => (Array[T], Array[Int]) => Classifier[T]): Classifier[T] = {
    val classifier = time {
      trainer(x, y)
    }

    if (auc) {
      val posteriori = Array(0.0, 0.0)
      val (pred, prob) = testx.map { xi =>
        val yi = classifier.predict(xi, posteriori)
        (yi, posteriori(1))
      }.unzip

      println("Accuracy = %.2f%%" format (100.0 * new Accuracy().measure(testy, pred)))
      println("Sensitivity = %.2f%%" format (100.0 * new Sensitivity().measure(testy, pred)))
      println("Specificity = %.2f%%" format (100.0 * new Specificity().measure(testy, pred)))
      println("AUC = %.2f%%" format (100.0 * AUC.measure(testy, prob)))
    } else {
      val pred = testx.map(classifier.predict(_))

      println("Accuracy = %.2f%%" format (100.0 * new Accuracy().measure(testy, pred)))
      println("Sensitivity = %.2f%%" format (100.0 * new Sensitivity().measure(testy, pred)))
      println("Specificity = %.2f%%" format (100.0 * new Specificity().measure(testy, pred)))
    }

    classifier
  }

  /** Measure running time of a function/block */
  object time {
    var echo = true

    def on = {
      echo = true
    }

    def off = {
      echo = false
    }

    def apply[A](f: => A) = {
      val s = System.nanoTime
      val ret = f
      if (echo) println("runtime: " + (System.nanoTime - s)/1e6 + " ms")
      ret
    }
  }
}
