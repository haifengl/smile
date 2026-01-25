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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.spark

import java.util.Properties
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*
import org.apache.spark.sql.SparkSession
import org.specs2.mutable.*
import org.specs2.specification.{AfterAll, BeforeAll}
import smile.classification.RandomForest
import smile.data.DataFrame
import smile.data.formula.Formula
import smile.hpo.Hyperparameters
import smile.io.Read
import smile.io.Paths

class HpoSpec extends Specification with BeforeAll with AfterAll{

  implicit var spark: SparkSession = _

  def beforeAll(): Unit = {
    spark = SparkSession.builder().master("local[*]").getOrCreate()
  }

  "SparkCrossValidation" should {
    "Random search on mushrooms" in {
      val mushrooms = Read.arff(Paths.getTestData("weka/mushrooms.arff")).dropna()
      val formula = Formula.lhs("class")

      val hp = new Hyperparameters()
        .add("smile.random.forest.trees", 100) // a fixed value
        .add("smile.random.forest.mtry", Array(2, 3, 4)) // an array of values to choose
        .add("smile.random.forest.max.nodes", 100, 500, 50); // range [100, 500] with step 50

      val configurations = hp.random().limit(10).collect(Collectors.toList()).asScala.toSeq
      val scores = hpo.classification(5, formula, mushrooms, configurations) {
        (formula: Formula, data: DataFrame, params: Properties) => RandomForest.fit(formula, data, RandomForest.Options.of(params))
      }

      configurations.indices foreach { i =>
        print(configurations(i))
        println(scores(i))
      }

      scores.length mustEqual configurations.length
    }
  }

  def afterAll(): Unit = {
    spark.stop()
  }
}
