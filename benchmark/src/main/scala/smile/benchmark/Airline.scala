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
import smile.validation._

/**
 *
 * @author Haifeng Li
 */
object Airline {

  def main(args: Array[String]): Unit = {
    benchmark("0.1m")
    benchmark("1m")
  }

  def benchmark: Unit = {
    benchmark("0.1m")
    benchmark("1m")
  }

  def benchmark(data: String): Unit = {
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

    val train = parser.parse(attributes, smile.data.parser.IOUtils.getTestDataFile(s"airline/train-${data}.csv"))
    val test  = parser.parse(attributes, smile.data.parser.IOUtils.getTestDataFile("airline/test.csv"))

    attributes.foreach { attr =>
      if (attr.isInstanceOf[NominalAttribute])
        println(attr.getName + attr.asInstanceOf[NominalAttribute].values.mkString(", "))
    }
    println("class: " + train.responseAttribute().asInstanceOf[NominalAttribute].values.mkString(", "))
    println("train data size: " + train.size + ", test data size: " + test.size)

    val (x, y) = train.unzipInt
    val (testx, testy) = test.unzipInt

    val pos = Math.sum(y)
    val testpos = Math.sum(testy)
    println(s"train data positive : negative =  $pos : ${y.length - pos}")
    println(s"test  data positive : negative =  $testpos : ${testy.length - testpos}")

    // The data is unbalanced. Large positive class weight of should improve sensitivity.
    val classWeight = Array(4, 1)

    // Random Forest
    val forest = test2soft(x, y, testx, testy) { (x, y) =>
      println("Training Random Forest of 500 trees...")
      // Roughly like max_depth = 20 in other packages
      randomForest(x, y, attributes, 500, 85, 50, 2, 0.632, DecisionTree.SplitRule.ENTROPY, classWeight)
    }

    val depth = forest.getTrees.map(_.maxDepth.toDouble)
    println("Tree Depth:")
    summary(depth)

    println("OOB error rate = %.2f%%" format (100.0 * forest.error()))
    for (i <- 0 until attributes.length) {
      println(s"importance of ${attributes(i).getName} = ${forest.importance()(i)}")
    }

    // Gradient Tree Boost
    test2soft(x, y, testx, testy) { (x, y) =>
      println("Training Gradient Boosted Trees of 300 trees...")
      gbm(x, y, attributes, 300, 6, 0.1, 0.5)
    }

    // AdaBoost
    test2soft(x, y, testx, testy) { (x, y) =>
      println("Training AdaBoost of 300 trees...")
      adaboost(x, y, attributes, 300, 6)
    }
  }
}
