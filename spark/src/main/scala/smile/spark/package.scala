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

package smile

import java.util.function.BiFunction
import org.apache.spark.sql.SparkSession
import smile.classification.Classifier
import smile.data.DataFrame
import smile.regression.Regression
import smile.validation.{Accuracy, ClassificationMetric, CrossValidation, RMSE, RegressionMetric}
import scala.reflect.ClassTag

/**
  * Package for better integration of Spark MLLib Pipelines and SMILE
  */
package object spark {
  /**
    * Extension method to Spark [[org.apache.spark.sql.DataFrame]] to convert them to SMILE [[DataFrame]]
    */
  implicit class SparkDataFrameOps(df: org.apache.spark.sql.DataFrame) {
    def toSmile: DataFrame = SparkDataFrame(df)
  }

  /**
    * Extension method to SMILE [[DataFrame]] to convert them to Spark [[org.apache.spark.sql.DataFrame]]
    */
  implicit class SmileDataFrameOps(df: DataFrame) {
    def toSpark(implicit spark:SparkSession): org.apache.spark.sql.DataFrame = SmileDataFrame(df)
  }

  /**
    * Distributed GridSearch Cross Validation for [[Classifier]].
    *
    * @param spark running spark session
    * @param k number of round of cross validation
    * @param x instances
    * @param y labels
    * @param metrics classification metrics
    * @param trainers classification trainers
    *
    * @return an array of array of classification metrics, the first layer has the same size as the number of trainers,
    *         the second has the same size as the number of metrics.
    */
  def grid[T <: Object: ClassTag](k: Int, x: Array[T], y: Array[Int], metrics: ClassificationMetric*)
                                 (trainers: ((Array[T], Array[Int]) => Classifier[T])*)
                                 (implicit spark: SparkSession): Array[Array[Double]] = {

    val sc = spark.sparkContext

    val xBroadcasted = sc.broadcast[Array[T]](x)
    val yBroadcasted = sc.broadcast[Array[Int]](y)
    val metricsBroadcasted = metrics.map(sc.broadcast)

    val trainersRDD = sc.parallelize(trainers)
    val res = trainersRDD
      .map(trainer => {
        //TODO: add smile-scala dependency and import the implicit conversion
        val biFunctionTrainer = new BiFunction[Array[T],Array[Int],Classifier[T]] {
          override def apply(x: Array[T], y:Array[Int]): Classifier[T] = trainer(x,y)
        }
        val x = xBroadcasted.value
        val y = yBroadcasted.value
        val metrics = metricsBroadcasted.map(_.value)
        //TODO: add smile-scala dependency and use smile.validation.cv
        val prediction =  CrossValidation.classification(k, x, y, biFunctionTrainer)
        val metricsOrAccuracy = if (metrics.isEmpty) Seq(Accuracy.instance) else metrics
        metricsOrAccuracy.map { metric =>
          val result = metric.score(y, prediction)
          result
        }.toArray
      })
      .collect()

    xBroadcasted.destroy()
    yBroadcasted.destroy()

    res
  }

  /**
    * Distributed GridSearch Cross Validation for [[Regression]].
    *
    * @param spark running spark session
    * @param k number of round of cross validation
    * @param x instances
    * @param y labels
    * @param metrics regression metrics
    * @param trainers regression trainers
    *
    * @return an array of array of regression metrics, the first layer has the same size as the number of trainers,
    *         the second has the same size as the number of metrics.
    */
  def grid[T <: Object: ClassTag](k: Int, x: Array[T], y: Array[Double], metrics: RegressionMetric*)
                                 (trainers: ((Array[T], Array[Double]) => Regression[T])*)
                                 (implicit spark: SparkSession): Array[Array[Double]] = {

    val sc = spark.sparkContext

    val xBroadcasted = sc.broadcast[Array[T]](x)
    val yBroadcasted = sc.broadcast[Array[Double]](y)
    val metricsBroadcasted = metrics.map(sc.broadcast)

    val trainersRDD = sc.parallelize(trainers)
    val res = trainersRDD
      .map(trainer => {
        //TODO: add smile-scala dependency and import the implicit conversion
        val biFunctionTrainer = new BiFunction[Array[T],Array[Double],Regression[T]] {
          override def apply(x: Array[T], y:Array[Double]): Regression[T] = trainer(x,y)
        }
        val x = xBroadcasted.value
        val y = yBroadcasted.value
        val metrics = metricsBroadcasted.map(_.value)
        //TODO: add smile-scala dependency and use smile.validation.cv
        val prediction =  CrossValidation.regression(k, x, y, biFunctionTrainer)
        val metricsOrRMSE = if (metrics.isEmpty) Seq(RMSE.instance) else metrics
        metricsOrRMSE.map { metric =>
          val result = metric.score(y, prediction)
          result
        }.toArray
      })
      .collect()

    xBroadcasted.destroy()
    yBroadcasted.destroy()

    res
  }
}
