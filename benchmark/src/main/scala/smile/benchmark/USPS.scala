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

import java.util
import scala.language.postfixOps
import smile.base.rbf.RBF
import smile.base.mlp.{Layer, OutputFunction}
import smile.classification._
import smile.clustering.KMeans
import smile.data.`type`.{DataTypes, StructField}
import smile.data.formula._
import smile.feature.Standardizer
import smile.read
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

  def benchmark(): Unit = {
    println("USPS")

    val fields = new util.ArrayList[StructField]
    fields.add(new StructField("class", DataTypes.ByteType))
    (1 to 256).foreach(i => fields.add(new StructField("V" + i, DataTypes.DoubleType)))
    val schema = DataTypes.struct(fields)

    val formula: Formula = "class" ~
    val zipTrain = read.csv(Paths.getTestData("usps/zip.train").toString, delimiter = ' ', header = false, schema = schema)
    val zipTest = read.csv(Paths.getTestData("usps/zip.test").toString, delimiter = ' ', header = false, schema = schema)
    val x = formula.x(zipTrain).toArray
    val y = formula.y(zipTrain).toIntArray
    val testx = formula.x(zipTest).toArray
    val testy = formula.y(zipTest).toIntArray

    val n = x.length
    val k = 10

    // Random Forest
    println("Training Random Forest of 200 trees...")
    val forest = smile.validation.test(formula, zipTrain, zipTest) { (formula, data) =>
      randomForest(formula, data, ntrees = 200)
    }
    println("OOB error rate = %.2f%%" format (100.0 * forest.error()))

    // Gradient Tree Boost
    println("Training Gradient Tree Boost of 200 trees...")
    test(formula, zipTrain, zipTest) { (formula, data) =>
      gbm(formula, data, ntrees = 200)
    }

    // SVM
    println("Training SVM, one epoch...")
    val kernel = new GaussianKernel(8.0)
    test(x, y, testx, testy) { (x, y) =>
      ovo(x, y) { (x, y) =>
        SVM.fit(x, y, kernel, 5, 1E-3)
      }
    }

    // RBF Network
    println("Training RBF Network...")
    val kmeans = KMeans.fit(x, 200)
    val distance = new EuclideanDistance
    val neurons = RBF.of(kmeans.centroids, new GaussianRadialBasis(8.0), distance)
    test(x, y, testx, testy) { (x, y) =>
      rbfnet(x, y, neurons, false)
    }

    // Logistic Regression
    println("Training Logistic regression...")
    test(x, y, testx, testy) { (x, y) =>
      logit(x, y, 0.3, 1E-3, 1000)
    }

    // Neural Network
    val scaler = Standardizer.fit(x)
    val scaledX = scaler.transform(x)
    val scaledTestX = scaler.transform(testx)

    println("Training Neural Network, 10 epoch...")
    val net = new MLP(256,
      Layer.sigmoid(768),
      Layer.sigmoid(192),
      Layer.sigmoid(30),
      Layer.mle(k, OutputFunction.SIGMOID)
    )
    net.setLearningRate(0.1)
    net.setMomentum(0.0)

    (0 until 10).foreach(epoch => {
      println("----- epoch %d -----" format epoch)
      MathEx.permutate(n).foreach(i =>
        net.update(scaledX(i), y(i))
      )
      val prediction = Validation.test(net, scaledTestX)
      println("Accuracy = %.2f%%" format (100.0 * Accuracy.of(testy, prediction)))
    })
  }
}
