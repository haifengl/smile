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
import smile.io._
import smile.math.Math
import smile.validation._
import smile.util._

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
    attributes(0) = new NominalAttribute("V0")
    attributes(1) = new NominalAttribute("V1")
    attributes(2) = new NominalAttribute("V2")
    attributes(3) = new NumericAttribute("V3")
    attributes(4) = new NominalAttribute("V4")
    attributes(5) = new NominalAttribute("V5")
    attributes(6) = new NominalAttribute("V6")
    attributes(7) = new NumericAttribute("V7")

    val train = parser.parse(attributes, smile.data.parser.IOUtils.getDataFile("airline/train-0.1m.csv"))
    val test  = parser.parse(attributes, smile.data.parser.IOUtils.getDataFile("airline/test.csv"))
    attributes.foreach { attr =>
      if (attr.isInstanceOf[NominalAttribute])
        println(attr.getName + attr.asInstanceOf[NominalAttribute].values.mkString(", "))
    }
    println("class: " + train.response.asInstanceOf[NominalAttribute].values.mkString(", "))
    println("train data size: " + train.size + ", test data size: " + test.size)

    val (x, y) = train.unzip
    val (testx, testy) = test.unzip
    println("train data positive : negative = " + Math.sum(y) + " : " + (y.length - Math.sum(y)))
    println("test data positive : negative = " + Math.sum(testy) + " : " + (testy.length - Math.sum(testy)))

    // The data is highly unbalanced. class weight 1 : 4 should improve sensitivity.
    // To match other tests, we keep it 1 : 1 here though.
    val classWeight = Array(1, 1)

    // Random Forest
    println("Training Random Forest of 500 trees...")
    val forest = time {
      new RandomForest(attributes, x, y, 500, 2, 300, DecisionTree.SplitRule.ENTROPY, classWeight)
    }

    val pred = new Array[Int](testy.length)
    val prob = new Array[Double](testy.length)

    val posteriori = Array(0.0, 0.0)
    val (rfpred, rfprob) = (0 until testx.length).map { i =>
      val yi = forest.predict(testx(i), posteriori)
      (yi, posteriori(1))
    }.unzip

    rfpred.copyToArray(pred, 0, testy.length)
    rfprob.copyToArray(prob, 0, testy.length)
    println("Random Forest OOB error rate = %.2f%%" format (100.0 * forest.error()))
    println("Random Forest accuracy = %.2f%%" format (100.0 * new Accuracy().measure(testy, pred)))
    println("Random Forest sensitivity = %.2f%%" format (100.0 * new Sensitivity().measure(testy, pred)))
    println("Random Forest specificity = %.2f%%" format (100.0 * new Specificity().measure(testy, pred)))
    println("Random Forest AUC = %.2f%%" format (100.0 * AUC.measure(testy, prob)))

    for (i <- 0 until attributes.length) {
      println(s"importance of ${attributes(i).getName} = ${forest.importance()(i)}")
    }

    // Gradient Tree Boost
    println("Training Gradient Boosted Trees of 300 trees...")
    val gbt = time {
      new GradientTreeBoost(attributes, x, y, 300, 6, 0.1, 0.5)
    }

    val (gbtpred, gbtprob) = (0 until testx.length).map { i =>
      val yi = gbt.predict(testx(i), posteriori)
      (yi, posteriori(1))
    }.unzip

    gbtpred.copyToArray(pred, 0, testy.length)
    gbtprob.copyToArray(prob, 0, testy.length)
    println("Gradient Tree Boost accuracy = %.2f%%" format (100.0 * new Accuracy().measure(testy, pred)))
    println("Gradient Tree Boost sensitivity = %.2f%%" format (100.0 * new Sensitivity().measure(testy, pred)))
    println("Gradient Tree Boost specificity = %.2f%%" format (100.0 * new Specificity().measure(testy, pred)))
    println("Gradient Tree Boost AUC = %.2f%%" format (100.0 * AUC.measure(testy, prob)))
  }
}
