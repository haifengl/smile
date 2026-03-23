/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package org.apache.spark.ml.regression

import java.nio.file.Files
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.sql.SparkSession
import org.specs2.mutable.*
import org.specs2.specification.{AfterAll, BeforeAll}
import smile.model.rbf.RBF
import smile.regression.RBFNetwork
import smile.io.Paths

class SmileRegressionSpec extends Specification with BeforeAll with AfterAll{

  var spark: SparkSession = _

  def beforeAll(): Unit = {
    spark = SparkSession.builder().master("local[*]").getOrCreate()
  }

  "SmileRegression" should {
    "have the same performances after saving and loading back the model" in {
      val path = "file:///" + Paths.getTestData("libsvm/mushrooms.svm").toAbsolutePath()
      val data = spark.read
        .format("libsvm")
        .load(path.replace("\\", "/"))
      data.cache()

      val trainer = (x: Array[Array[Double]], y: Array[Double]) => {
        val neurons = RBF.fit(x, 30)
        RBFNetwork.fit(x, y, neurons)
      }

      val rbf = new SmileRegression().setTrainer(trainer)
      val eval = new RegressionEvaluator().setLabelCol("label").setPredictionCol("prediction")

      val model = rbf.fit(data)
      val metric = eval.evaluate(model.transform(data))
      println(s"Evaluation result = $metric")

      val temp = Files.createTempFile("smile-test-", ".tmp")
      val modelPath = temp.normalize().toString
      model.write.overwrite().save(modelPath)
      temp.toFile.deleteOnExit()

      val loaded = SmileRegressionModel.load(modelPath)
      eval.evaluate(loaded.transform(data)) mustEqual eval.evaluate(model.transform(data))
    }
  }

  def afterAll(): Unit = {
    spark.stop()
  }
}