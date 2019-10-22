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

import java.util.Optional
import smile.base.cart.SplitRule
import smile.classification._
import smile.data.summary
import smile.data.formula.Formula
import smile.read
import smile.util.{Paths, time}
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

  def benchmark(dataSize: String): Unit = {
    println("Airline")

    val formula = Formula.lhs("dep_delayed_15min")
    val data = read.csv(Paths.getTestData(s"airline/train-${dataSize}.csv").toString)
    val train = data.factorize("Month", "DayofMonth", "DayOfWeek", "UniqueCarrier", "Origin", "Dest", "dep_delayed_15min")
    //val test = read.csv(Paths.getTestData("airline/test.csv"), schema = data.schema)
    //val testy = formula.y(test).toIntArray

    println("----- train data -----")
    println(train)
    println("----- test  data -----")
    //println(test)

    // The data is unbalanced. Large positive class weight of should improve sensitivity.
    val classWeight = Array(4, 1)

    // Random Forest
    println("Training Random Forest of 500 trees...")
    val forest = time {
      RandomForest.fit(formula, train, 500, 2, SplitRule.ENTROPY, 85, 50, 0.632, Optional.of(classWeight))
    }
    //val p1 = forest.predict(test)
    //measure(testy, p1)

    val depth = forest.trees.map(_.root.depth.toDouble)
    println("Tree Depth:")
    summary(depth)

    println("OOB error rate = %.2f%%" format (100.0 * forest.error()))

    // Gradient Tree Boost
    println("Training Gradient Tree Boost of 300 trees...")
    val boost = time {
      gbm(formula, train, 300, 6, 5, 0.1, 0.5)
    }
    //val p2 = boost.predict(test)
    //measure(testy, p2)

    // AdaBoost
    println("Training AdaBoost of 300 trees...")
    val ada = time {
      adaboost(formula, train, 300, 6, 5)
    }
    //val p3 = ada.predict(test)
    //measure(testy, p3)
  }

  def measure(y: Array[Int], prediction: Array[Int]): Unit = {
    println("Accuracy = %.2f%%" format (100.0 * Accuracy.apply(y, prediction)))
    println("Sensitivity/Recall = %.2f%%" format (100.0 * Sensitivity.apply(y, prediction)))
    println("Specificity = %.2f%%" format (100.0 * Specificity.apply(y, prediction)))
    println("Precision = %.2f%%" format (100.0 * Precision.apply(y, prediction)))
    println("F1-Score = %.2f%%" format (100.0 * FMeasure.apply(y, prediction)))
    println("F2-Score = %.2f%%" format (100.0 * new FMeasure(2).measure(y, prediction)))
    println("F0.5-Score = %.2f%%" format (100.0 * new FMeasure(0.5).measure(y, prediction)))
    println("Confusion Matrix: " + new ConfusionMatrix(y, prediction))
  }
}
