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

package smile.tuning

import org.apache.spark.sql.SparkSession
import org.specs2.mutable._
import org.specs2.specification.{AfterAll, BeforeAll}
import smile.io.Read
import smile.validation.Accuracy
import smile.classification.KNN

class TuningSuite extends Specification with BeforeAll with AfterAll{

  var spark:SparkSession = _

  def beforeAll(): Unit = {
    spark = SparkSession.builder().master("local[*]").getOrCreate
  }

  "SparkGridSearchCrossValidation" should {
    "make KNN perfect on mushroom and return all runs" in {

      val mushrooms = Read.arff("spark/src/test/resources/mushrooms.arff")
      val x = mushrooms.select(1, 22).toArray
      val y = mushrooms("class").toIntArray

      val knn3 = (x:Array[Array[Double]], y:Array[Int]) => KNN.fit(x, y, 3)
      val knn5 = (x:Array[Array[Double]], y:Array[Int]) => KNN.fit(x, y, 5)

      val res = sparkgscv(spark)(5, x, y, Seq(new Accuracy()): _*) (Seq(knn3,knn5):_*)

      res(0)(0) mustEqual 1 and (res.length mustEqual 2) and (res(0).length mustEqual 5)

    }
  }

  def afterAll(): Unit = {
    spark.stop()
  }

}