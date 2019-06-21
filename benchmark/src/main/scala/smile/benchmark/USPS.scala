/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/
 
package smile.benchmark

import smile.data._
import smile.data.parser.DelimitedTextParser
import smile.classification._
import smile.math.MathEx
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
    val k = MathEx.max(y) + 1

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
    val mu = MathEx.colMeans(x)
    val sd = MathEx.colSds(x)
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
