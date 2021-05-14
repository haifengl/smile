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

class ClassificationModelSpec extends Specification {
  val train: DataFrame = read.arff(Paths.getTestData("weka/segment-challenge.arff"))
  val test: DataFrame = read.arff(Paths.getTestData("weka/segment-test.arff"))
  val formula: Formula = "class" ~ "."

  "ClassificationModel" should {
    "random.forest" in {
      MathEx.setSeed(19650217) // to get repeatable results.
      val prop = new Properties()
      prop.setProperty("smile.random.forest.ntrees", "100")
      prop.setProperty("smile.random.forest.max.nodes", "100")
      val model = ClassificationModel("random.forest", formula, train, prop, test = Some(test))
      println(s"Training metrics: ${model.train}")
      println(s"Validation metrics: ${model.validation}")
      println(s"Test metrics: ${model.test}")
      model.validation.isDefined must beFalse
      model.test.get.error must beCloseTo(33 +/- 3)
    }
    "svm" in {
      MathEx.setSeed(19650217)
      val prop = new Properties()
      prop.setProperty("smile.svm.kernel", "Gaussian(6.4)")
      prop.setProperty("smile.svm.C", "100")
      val model = ClassificationModel("svm", formula, train, prop, test = Some(test))
      println(s"Training metrics: ${model.train}")
      println(s"Validation metrics: ${model.validation}")
      println(s"Test metrics: ${model.test}")
      model.test.get.error mustEqual 93
    }
    "svm with ensemble" in {
      MathEx.setSeed(19650217)
      val prop = new Properties()
      prop.setProperty("smile.svm.kernel", "Gaussian(6.4)")
      prop.setProperty("smile.svm.C", "100")
      val model = ClassificationModel("svm", formula, train, prop, kfold = 5, round = 3, ensemble = true, test = Some(test))
      println(s"Training metrics: ${model.train}")
      println(s"Validation metrics: ${model.validation}")
      println(s"Test metrics: ${model.test}")
      model.test.get.error mustEqual 89
    }
    /*
    "mlp" in {
      MathEx.setSeed(19650217)
      val prop = new Properties()
      prop.setProperty("smile.mlp.epochs", "30")
      prop.setProperty("smile.mlp.layers", "ReLU(50)|Sigmoid(30)")
      prop.setProperty("smile.mlp.learning_rate", "0.2")
      val model = ClassificationModel("mlp", formula, train, prop, test = Some(test))
      println(s"Training metrics: ${model.train}")
      println(s"Validation metrics: ${model.validation}")
      println(s"Test metrics: ${model.test}")
      model.test.get.error mustEqual 89
    }
     */
  }
}
