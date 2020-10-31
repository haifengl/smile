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

package org.apache.spark.ml.classification

import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.sql.SparkSession
import org.specs2.mutable._
import org.specs2.specification.{AfterAll, BeforeAll}
import smile.classification.KNN

class SmileClassifierSpec extends Specification with BeforeAll with AfterAll{

  var spark:SparkSession = _

  def beforeAll(): Unit = {
    spark = SparkSession.builder().master("local[*]").getOrCreate
  }

  "SmileClassifier" should {
    "have the same performances after saving and loading back the model" in {

      val raw = spark.read.format("libsvm").load("spark/src/test/resources/mushrooms.svm")

      val scl = new SmileClassifier()
        .setTrainer({ (x, y) => KNN.fit(x, y, 3) })

      val bce = new BinaryClassificationEvaluator()
        .setLabelCol("label")
        .setRawPredictionCol("rawPrediction")

      val data = raw
      data.cache()

      time {

        val model = scl.fit(data)
        val res = bce.evaluate(model.transform(data))

        println(res)

        model.write.overwrite().save("/tmp/model")
        val loaded = SmileClassificationModel.load("/tmp/model")
        bce.evaluate(loaded.transform(data)) mustEqual bce.evaluate(model.transform(data))

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