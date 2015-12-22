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
import smile.validation.AUC

/**
 *
 * @author Haifeng Li
 */
object Airline {

  def main(args: Array[String]): Unit = {
    benchmark
  }

  def benchmark() {
    println("Airline")
    val parser = new DelimitedTextParser()
    parser.setDelimiter(",")
    parser.setColumnNames(true)
    parser.setResponseIndex(new NominalAttribute("class"), 8)
    val attributes = new Array[Attribute](8)
    attributes(0) = new NominalAttribute("V1")
    attributes(1) = new NominalAttribute("V2")
    attributes(2) = new NominalAttribute("V3")
    attributes(3) = new NumericAttribute("V4")
    attributes(4) = new NominalAttribute("V5")
    attributes(5) = new NominalAttribute("V6")
    attributes(6) = new NominalAttribute("V7")
    attributes(7) = new NumericAttribute("V8")

    val train = parser.parse("Benchmark train", attributes, "test-data/src/main/resources/smile/data/airline/train-0.1m.csv")
    val test = parser.parse("Benchmark test", attributes, "test-data/src/main/resources//smile/data/airline/test.csv")
    println("class: " + train.response.asInstanceOf[NominalAttribute].values.mkString(", "))

    val x = train.toArray(new Array[Array[Double]](train.size))
    val y = train.toArray(new Array[Int](train.size))
    val testx = test.toArray(new Array[Array[Double]](test.size))
    val testy = test.toArray(new Array[Int](test.size))

    // Random Forest
    var start = System.currentTimeMillis()
    val forest = new RandomForest(attributes, x, y, 500, 2)
    var end = System.currentTimeMillis()
    println("Random Forest 500 trees training time: %.2fs" format ((end-start)/1000.0))

    val posteriori = Array(0.0, 0.0)
    val prob = new Array[Double](testx.length)
    var error = (0 until testx.length).foldLeft(0) { (e, i) =>
      val yi = forest.predict(testx(i), posteriori)
      prob(i) = posteriori(1)
      if (yi != testy(i)) e + 1 else e
    }

    var auc = 100.0 * new AUC().measure(testy, prob)
    println("Random Forest OOB error rate = %.2f%%" format (100.0 * forest.error()))
    println("Random Forest error rate = %.2f%%" format (100.0 * error / testx.length))
    println("Random Forest AUC = %.2f%%" format auc)

    // Gradient Tree Boost
    start = System.currentTimeMillis()
    val boost = new GradientTreeBoost(attributes, x, y, 300, 512, 0.01, 0.5)
    end = System.currentTimeMillis()
    println("Gradient Tree Boost 300 trees training time: %.2fs" format ((end-start)/1000.0))

    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      val yi = boost.predict(testx(i), posteriori)
      prob(i) = posteriori(1)
      if (yi != testy(i)) e + 1 else e
    }

    auc = 100.0 * new AUC().measure(testy, prob)
    println("Gradient Tree Boost error rate = %.2f%%" format (100.0 * error / testx.length))
    println("Gradient Tree Boost AUC = %.2f%%" format auc)
  }
}
