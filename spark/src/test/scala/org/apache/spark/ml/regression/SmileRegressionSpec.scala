/*******************************************************************************
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
 ******************************************************************************/

package org.apache.spark.ml.regression

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

      val raw = spark.read.format("libsvm").load(Paths.getTestData("libsvm/mushrooms.svm").normalize().toString)

      val trainer = { (x: Array[Array[Double]], y: Array[Double]) => {
        val neurons = RBF.fit(x, 3)
        RBFNetwork.fit(x, y, neurons)
        }
      }

      val sr = new SmileRegression()
        .setTrainer(trainer)

      val re = new RegressionEvaluator()
        .setLabelCol("label")
        .setPredictionCol("prediction")

      val data = raw
      data.cache()

      time {
        val model = sr.fit(data)
        val res = re.evaluate(model.transform(data))

        println(res)

        model.write.overwrite().save("/tmp/bonjour")
        val loaded = SmileRegressionModel.load("/tmp/bonjour")
        re.evaluate(loaded.transform(data)) mustEqual re.evaluate(model.transform(data))
      }
    }
  }

  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block // call-by-name
    val t1 = System.nanoTime()
    println("Elapsed time: " + (t1 - t0) + "ns")
    result
  }

  def afterAll(): Unit = {
    spark.stop()
  }
}