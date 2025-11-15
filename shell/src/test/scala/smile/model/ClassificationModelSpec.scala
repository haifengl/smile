/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.model

import java.util.Properties
import org.specs2.mutable.*
import smile.read
import smile.data.DataFrame
import smile.data.formula.*
import smile.feature.transform.{Standardizer, WinsorScaler}
import smile.math.MathEx
import smile.io.Paths

class ClassificationModelSpec extends Specification {
  val train: DataFrame = read.arff(Paths.getTestData("weka/segment-challenge.arff"))
  val test: DataFrame = read.arff(Paths.getTestData("weka/segment-test.arff"))
  val formula: Formula = "class" ~ "."

  "ClassificationModel" should {
    "random.forest" in {
      MathEx.setSeed(19650218) // to get repeatable results.
      val params = new Properties()
      params.setProperty("smile.random_forest.trees", "100")
      params.setProperty("smile.random_forest.max_nodes", "100")
      val model = Model.classification("random_forest", formula, train, params, 1, 1, false, test)
      println(s"Training metrics: ${model.train}")
      println(s"Validation metrics: ${model.validation}")
      println(s"Test metrics: ${model.test}")
      model.test.error must beCloseTo(34 +/- 3)
    }
    "svm" in {
      MathEx.setSeed(19650218)
      val scaler = Standardizer.fit(train)
      val params = new Properties()
      // This property is not supported now.
      // params.setProperty("smile.feature.transform", "Standardizer")
      params.setProperty("smile.svm.kernel", "Gaussian(6.4)")
      params.setProperty("smile.svm.C", "100")
      params.setProperty("smile.svm.type", "ovo")
      val model = Model.classification("svm", formula, scaler(train), params, 1, 1, false, scaler(test))
      println(s"Training metrics: ${model.train}")
      println(s"Validation metrics: ${model.validation}")
      println(s"Test metrics: ${model.test}")
      model.test.error must beCloseTo(33 +/- 3)
    }
    "svm with ensemble" in {
      MathEx.setSeed(19650218)
      val scaler = Standardizer.fit(train)
      val params = new Properties()
      // This property is not supported now.
      // params.setProperty("smile.feature.transform", "Standardizer")
      params.setProperty("smile.svm.kernel", "Gaussian(6.4)")
      params.setProperty("smile.svm.C", "100")
      params.setProperty("smile.svm.type", "ovo")
      val model = Model.classification("svm", formula, scaler(train), params, 5, 3, true, scaler(test))
      println(s"Training metrics: ${model.train}")
      println(s"Validation metrics: ${model.validation}")
      println(s"Test metrics: ${model.test}")
      model.test.error must beCloseTo(30 +/- 3)
    }
    "mlp" in {
      MathEx.setSeed(19650218)
      val scaler = WinsorScaler.fit(train, 0.01, 0.99)
      val params = new Properties()
      // This property is not supported now.
      // params.setProperty("smile.feature.transform", "winsor(0.01, 0.99)")
      params.setProperty("smile.mlp.epochs", "13")
      params.setProperty("smile.mlp.mini_batch", "20")
      params.setProperty("smile.mlp.layers", "Sigmoid(50)")
      params.setProperty("smile.mlp.learning_rate", "linear(0.1, 10000, 0.01)")
      params.setProperty("smile.mlp.RMSProp.rho", "0.9")
      val model = Model.classification("mlp", formula, scaler(train), params, 1, 1, false, scaler(test))
      println(s"Training metrics: ${model.train}")
      println(s"Validation metrics: ${model.validation}")
      println(s"Test metrics: ${model.test}")
      model.test.error mustEqual 32
    }
  }
}
