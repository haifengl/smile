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

package org.apache.spark.ml.regression

import java.nio.file.Files
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.sql.SparkSession
import org.specs2.mutable._
import org.specs2.specification.{AfterAll, BeforeAll}
import smile.base.rbf.RBF
import smile.regression.RBFNetwork
import smile.util.Paths

class SmileRegressionSpec extends Specification with BeforeAll with AfterAll{

  var spark: SparkSession = _

  def beforeAll(): Unit = {
    spark = SparkSession.builder().master("local[*]").getOrCreate
  }

  "SmileRegression" should {
    "have the same performances after saving and loading back the model" in {
      val data = spark.read
        .format("libsvm")
        .load(Paths.getTestData("libsvm/mushrooms.svm").normalize().toString)
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
      val path = temp.normalize().toString
      model.write.overwrite().save(path)
      temp.toFile.deleteOnExit()

      val loaded = SmileRegressionModel.load(path)
      eval.evaluate(loaded.transform(data)) mustEqual eval.evaluate(model.transform(data))
    }
  }

  def afterAll(): Unit = {
    spark.stop()
  }
}