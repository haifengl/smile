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
import smile.util.SmileUtils

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
    val x = train.toArray(new Array[Array[Double]](train.size))
    val y = train.toArray(new Array[Int](train.size))
    val testx = test.toArray(new Array[Array[Double]](test.size))
    val testy = test.toArray(new Array[Int](test.size))
    val c = Math.max(y: _*) + 1

    // Random Forest
    var start = System.currentTimeMillis
    val forest = new RandomForest(x, y, 200)
    var end = System.currentTimeMillis
    println("Random Forest 200 trees training time: %.2fs" format ((end-start)/1000.0))

    var error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (forest.predict(testx(i)) != testy(i)) e + 1 else e
    }
    println("Random Forest OOB error rate = %.2f%%" format (100.0 * forest.error()))
    println("Random Forest error rate = %.2f%%" format (100.0 * error / testx.length))

    // Gradient Tree Boost
    start = System.currentTimeMillis
    val gbt = new GradientTreeBoost(x, y, 200)
    end = System.currentTimeMillis
    println("Gradient Tree Boost training time: %.2fs" format ((end-start)/1000.0))
    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (gbt.predict(testx(i)) != testy(i)) e + 1 else e
    }

    println("Gradient Tree Boost error rate = %.2f%%" format (100.0 * error / testx.length))

    // SVM
    start = System.currentTimeMillis
    val svm = new SVM[Array[Double]](new GaussianKernel(8.0), 5.0, c, SVM.Multiclass.ONE_VS_ONE)
    svm.learn(x, y)
    svm.finish
    end = System.currentTimeMillis
    println("SVM one epoch training time: %.2fs" format ((end-start)/1000.0))
    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (svm.predict(testx(i)) != testy(i)) e + 1 else e
    }

    println("SVM error rate = %.2f%%" format (100.0 * error / testx.length))

    println("SVM one more epoch...")
    start = System.currentTimeMillis
    svm.learn(x, y)
    svm.finish
    end = System.currentTimeMillis
    println("SVM one more epoch training time: %.2fs" format ((end-start)/1000.0))

    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (svm.predict(testx(i)) != testy(i)) e + 1 else e
    }
    println("SVM error rate = %.2f%%" format (100.0 * error / testx.length))

    // RBF Network
    start = System.currentTimeMillis
    val centers = new Array[Array[Double]](200)
    val basis = SmileUtils.learnGaussianRadialBasis(x, centers)
    val rbf = new RBFNetwork[Array[Double]](x, y, new EuclideanDistance, new GaussianRadialBasis(8.0), centers)
    end = System.currentTimeMillis
    println("RBF 200 centers training time: %.2fs" format ((end-start)/1000.0))

    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (rbf.predict(testx(i)) != testy(i)) e + 1 else e
    }
    println("RBF error rate = %.2f%%" format (100.0 * error / testx.length))

    // Logistic Regression
    start = System.currentTimeMillis
    val logit = new LogisticRegression(x, y, 0.3, 1E-3, 1000)
    end = System.currentTimeMillis
    println("Logistic regression training time: %.2fs" format ((end-start)/1000.0))

    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (logit.predict(testx(i)) != testy(i)) e + 1 else e
    }
    println("Logistic error rate = %.2f%%" format (100.0 * error / testx.length))

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

    start = System.currentTimeMillis
    val nnet = new NeuralNetwork(NeuralNetwork.ErrorFunction.LEAST_MEAN_SQUARES, NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, p, 40, c)
    (0 until 30) foreach { _ => nnet.learn(x, y) }
    end = System.currentTimeMillis
    println("Neural Network 30 epoch training time: %.2fs" format ((end-start)/1000.0))

    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (nnet.predict(testx(i)) != testy(i)) e + 1 else e
    }
    println("Neural Network error rate = %.2f%%" format (100.0 * error / testx.length))
  }
}
