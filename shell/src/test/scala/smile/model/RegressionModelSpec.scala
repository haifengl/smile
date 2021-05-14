/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.model

import java.util.Properties
import org.specs2.mutable._
import smile.read
import smile.data.DataFrame
import smile.data.formula._
import smile.math.MathEx
import smile.util.Paths

class RegressionModelSpec extends Specification {
  val train: DataFrame = read.csv(Paths.getTestData("regression/prostate-train.csv").toString, delimiter = '\t')
  val test: DataFrame = read.csv(Paths.getTestData("regression/prostate-train.csv").toString, delimiter = '\t')
  val formula: Formula = "lpsa" ~ "."

  "RegressionModel" should {
    "random.forest" in {
      MathEx.setSeed(19650217) // to get repeatable results.
      val prop = new Properties()
      prop.setProperty("smile.random.forest.ntrees", "100")
      prop.setProperty("smile.random.forest.max.nodes", "100")
      val model = RegressionModel("random.forest", formula, train, prop, test = Some(test))
      println(s"Training metrics: ${model.train}")
      println(s"Validation metrics: ${model.validation}")
      println(s"Test metrics: ${model.test}")
      model.test.get.r2 must beCloseTo(0.746 +/- 0.01)
    }
    "svm" in {
      MathEx.setSeed(19650217)
      val prop = new Properties()
      prop.setProperty("smile.svm.kernel", "Gaussian(6.0)")
      prop.setProperty("smile.svm.C", "5")
      prop.setProperty("smile.svm.epsilon", "0.5")
      val model = RegressionModel("svm", formula, train, prop, test = Some(test))
      println(s"Training metrics: ${model.train}")
      println(s"Validation metrics: ${model.validation}")
      println(s"Test metrics: ${model.test}")
      model.test.get.r2 must beCloseTo(0.738 +/- 0.01)
    }
    "svm with ensemble" in {
      MathEx.setSeed(19650217)
      val prop = new Properties()
      prop.setProperty("smile.svm.kernel", "Gaussian(6.0)")
      prop.setProperty("smile.svm.C", "5")
      prop.setProperty("smile.svm.epsilon", "0.5")
      val model = RegressionModel("svm", formula, train, prop, kfold = 5, round = 3, ensemble = true, test = Some(test))
      println(s"Training metrics: ${model.train}")
      println(s"Validation metrics: ${model.validation}")
      println(s"Test metrics: ${model.test}")
      model.test.get.r2 must beCloseTo(0.697 +/- 0.01)
    }
    /*
    "mlp" in {
      MathEx.setSeed(19650217)
      val prop = new Properties()
      prop.setProperty("smile.mlp.epochs", "30")
      prop.setProperty("smile.mlp.layers", "50|30")
      prop.setProperty("smile.mlp.activation", "sigmoid")
      prop.setProperty("smile.mlp.learning_rate", "0.2")
      val model = RegressionModel("mlp", formula, train, prop, test = Some(test))
      println(s"Training metrics: ${model.train}")
      println(s"Validation metrics: ${model.validation}")
      println(s"Test metrics: ${model.test}")
      model.test.get.rmse must beCloseTo(0.926 +/- 0.01)
    }
     */
  }
}
