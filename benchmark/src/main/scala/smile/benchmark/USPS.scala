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
import smile.validation._
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

    val train = parser.parse(smile.data.parser.IOUtils.getTestDataFile("usps/zip.train"))
    val test = parser.parse(smile.data.parser.IOUtils.getTestDataFile("usps/zip.test"))
    val (x, y) = train.unzipInt
    val (testx, testy) = test.unzipInt
    val k = Math.max(y) + 1

    // Random Forest
    val forest = test2(x, y, testx, testy) { (x, y) =>
      println("Training Random Forest of 200 trees...")
      new RandomForest(x, y, 200)
    }.asInstanceOf[RandomForest]

    println("OOB error rate = %.2f%%" format (100.0 * forest.error()))

    // Gradient Tree Boost
    test2(x, y, testx, testy) { (x, y) =>
      println("Training Gradient Tree Boost of 200 trees...")
      new GradientTreeBoost(x, y, 200)
    }

    // SVM
    test2(x, y, testx, testy) { (x, y) =>
      println("Training SVM, one epoch...")
      val svm = new SVM[Array[Double]](new GaussianKernel(8.0), 5.0, k, SVM.Multiclass.ONE_VS_ONE)
      svm.learn(x, y)
      svm.finish
      svm
    }

    // RBF Network
    test2(x, y, testx, testy) { (x, y) =>
      println("Training RBF Network...")
      val centers = new Array[Array[Double]](200)
      val basis = SmileUtils.learnGaussianRadialBasis(x, centers)
      new RBFNetwork[Array[Double]](x, y, new EuclideanDistance, new GaussianRadialBasis(8.0), centers)
    }

    // Logistic Regression
    test2(x, y, testx, testy) { (x, y) =>
      println("Training Logistic regression...")
      new LogisticRegression(x, y, 0.3, 1E-3, 1000)
    }

    // Neural Network
    val p = x(0).length
    val mu = Math.colMeans(x)
    val sd = Math.colSds(x)
    x.foreach { xi =>
      (0 until p) foreach { j => xi(j) = (xi(j) - mu(j)) / sd(j)}
    }
    testx.foreach { xi =>
      (0 until p) foreach { j => xi(j) = (xi(j) - mu(j)) / sd(j)}
    }

    test2(x, y, testx, testy) { (x, y) =>
      println("Training Neural Network, 30 epoch...")
      val nnet = new NeuralNetwork(NeuralNetwork.ErrorFunction.LEAST_MEAN_SQUARES, NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, p, 40, k)
      (0 until 30) foreach { _ => nnet.learn(x, y) }
      nnet
    }
  }
}
