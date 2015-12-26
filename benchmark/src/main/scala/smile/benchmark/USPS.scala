/*******************************************************************************
  * Copyright (c) 2010 Haifeng Li
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
package smile.benchmark

import smile.data._
import smile.data.parser.DelimitedTextParser
import smile.classification._
import smile.math.Math
import smile.math.distance.EuclideanDistance
import smile.math.kernel.GaussianKernel
import smile.math.rbf.GaussianRadialBasis
import smile.validation.Accuracy
import smile.io._
import smile.util._

/**
 *
 * @author Haifeng Li
 */
object USPS {

  def main(args: Array[String]): Unit = {
    benchmark
  }

  def benchmark() {
    println("USPS")
    val parser = new DelimitedTextParser
    parser.setResponseIndex(new NominalAttribute("class"), 0)

    val train = parser.parse(smile.data.parser.IOUtils.getDataFile("usps/zip.train"))
    val test = parser.parse(smile.data.parser.IOUtils.getDataFile("usps/zip.test"))
    val (x, y) = train.unzip
    val (testx, testy) = test.unzip
    val c = Math.max(y: _*) + 1

    // Random Forest
    println("Training Random Forest of 200 trees...")
    val forest = time {
      new RandomForest(x, y, 200)
    }

    var pred = testx.map(forest.predict(_))
    println("Random Forest OOB error rate = %.2f%%" format (100.0 * forest.error()))
    println("Random Forest error rate = %.2f%%" format new Accuracy().measure(testy, pred))

    // Gradient Tree Boost
    println("Training Gradient Tree Boost...")
    val gbt = time {
      new GradientTreeBoost(x, y, 200)
    }

    pred = testx.map(gbt.predict(_))
    println("Gradient Tree Boost error rate = %.2f%%" format new Accuracy().measure(testy, pred))

    // SV
    println("Training SVM, one epoch...")
    val svm = time {
      val svm = new SVM[Array[Double]](new GaussianKernel(8.0), 5.0, c, SVM.Multiclass.ONE_VS_ONE)
      svm.learn(x, y)
      svm.finish
      svm
    }

    pred = testx.map(svm.predict(_))
    println("SVM error rate = %.2f%%" format new Accuracy().measure(testy, pred))

    println("Training SVM one more epoch...")
    time {
      svm.learn(x, y)
      svm.finish
    }

    pred = testx.map(svm.predict(_))
    println("SVM error rate = %.2f%%" format new Accuracy().measure(testy, pred))

    // RBF Network
    println("Training RBF Network...")
    val centers = new Array[Array[Double]](200)
    val basis = SmileUtils.learnGaussianRadialBasis(x, centers)
    val rbf = time {
      new RBFNetwork[Array[Double]](x, y, new EuclideanDistance, new GaussianRadialBasis(8.0), centers)
    }

    pred = testx.map(rbf.predict(_))
    println("RBF Network error rate = %.2f%%" format new Accuracy().measure(testy, pred))

    // Logistic Regression
    println("Training Logistic regression...")
    val logit = time {
      new LogisticRegression(x, y, 0.3, 1E-3, 1000)
    }

    pred = testx.map(logit.predict(_))
    println("Logistic Regression error rate = %.2f%%" format new Accuracy().measure(testy, pred))

    // Neural Network
    val p = x(0).length
    val mu = Math.colMean(x)
    val sd = Math.colSd(x)
    x.foreach { xi =>
      (0 until p) foreach { j => xi(j) = (xi(j) - mu(j)) / sd(j)}
    }
    testx.foreach { xi =>
      (0 until p) foreach { j => xi(j) = (xi(j) - mu(j)) / sd(j)}
    }

    println("Training Neural Network, 30 epoch...")
    val nnet = time {
      val nnet = new NeuralNetwork(NeuralNetwork.ErrorFunction.LEAST_MEAN_SQUARES, NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, p, 40, c)
      (0 until 30) foreach { _ => nnet.learn(x, y) }
      nnet
    }

    pred = testx.map(nnet.predict(_))
    println("Nuural Network error rate = %.2f%%" format new Accuracy().measure(testy, pred))
  }
}
