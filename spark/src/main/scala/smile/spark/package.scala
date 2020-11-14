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

import java.util.Properties;
import java.util.function.BiFunction
import scala.reflect.ClassTag
import org.apache.spark.sql.SparkSession
import smile.classification.{Classifier, DataFrameClassifier}
import smile.data.DataFrame
import smile.data.formula.Formula
import smile.regression.{DataFrameRegression, Regression}
import smile.validation.{Accuracy, ClassificationMetric, CrossValidation, RMSE, RegressionMetric}

/**
  * Package for better integration of Spark MLLib Pipelines and SMILE
  */
package object spark {
  /**
    * Extension method to Spark [[org.apache.spark.sql.DataFrame]] to convert them to Smile [[DataFrame]]
    */
  implicit class SparkDataFrameOps(df: org.apache.spark.sql.DataFrame) {
    def toSmile: DataFrame = SparkDataFrame(df)
  }

  /**
    * Extension method to Smile [[DataFrame]] to convert them to Spark [[org.apache.spark.sql.DataFrame]]
    */
  implicit class SmileDataFrameOps(df: DataFrame) {
    def toSpark(implicit spark:SparkSession): org.apache.spark.sql.DataFrame = SmileDataFrame(df)
  }

  /** Classification hyper-parameter optimization. */
  object classification {
    /**
      * Distributed hyper-parameter optimization with cross validation.
      *
      * @param spark          spark session.
      * @param k              k-fold cross validation.
      * @param x              training samples.
      * @param y              training labels.
      * @param configurations hyper-parameter configurations.
      * @param metrics        classification metrics.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def hpo[T <: Object : ClassTag](k: Int, x: Array[T], y: Array[Int], configurations: Seq[Properties], metrics: ClassificationMetric*)
                                   (trainer: (Array[T], Array[Int], Properties) => Classifier[T])
                                   (implicit spark: SparkSession): Array[Array[Double]] = {
      val sc = spark.sparkContext

      val xBroadcasted = sc.broadcast(x)
      val yBroadcasted = sc.broadcast(y)
      val metricsBroadcasted = sc.broadcast(metrics)

      val hpRDD = sc.parallelize(configurations)
      val scores = hpRDD.map(prop => {
        val biFunctionTrainer = new BiFunction[Array[T], Array[Int], Classifier[T]] {
          override def apply(x: Array[T], y: Array[Int]): Classifier[T] = trainer(x, y, prop)
        }

        val x = xBroadcasted.value
        val y = yBroadcasted.value
        val metrics = metricsBroadcasted.value

        val prediction = CrossValidation.classification(k, x, y, biFunctionTrainer)
        val metricsOrAccuracy = if (metrics.isEmpty) Seq(Accuracy.instance) else metrics
        metricsOrAccuracy.map(_.score(y, prediction)).toArray
      }).collect()

      xBroadcasted.destroy()
      yBroadcasted.destroy()
      metricsBroadcasted.destroy()

      scores
    }

    /**
      * Distributed hyper-parameter optimization with cross validation.
      *
      * @param spark          spark session.
      * @param k              k-fold cross validation.
      * @param formula        model formula.
      * @param data           training data.
      * @param configurations hyper-parameter configurations.
      * @param metrics        classification metrics.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def hpo[T <: Object : ClassTag](k: Int, formula: Formula, data: DataFrame, configurations: Seq[Properties], metrics: ClassificationMetric*)
                                   (trainer: (Formula, DataFrame, Properties) => DataFrameClassifier)
                                   (implicit spark: SparkSession): Array[Array[Double]] = {
      val sc = spark.sparkContext

      val formulaBroadcasted = sc.broadcast(formula)
      val dataBroadcasted = sc.broadcast(data)
      val metricsBroadcasted = sc.broadcast(metrics)

      val hpRDD = sc.parallelize(configurations)
      val scores = hpRDD.map(prop => {
        val biFunctionTrainer = new BiFunction[Formula, DataFrame, DataFrameClassifier] {
          override def apply(formula: Formula, data: DataFrame): DataFrameClassifier = trainer(formula, data, prop)
        }

        val formula = formulaBroadcasted.value
        val data = dataBroadcasted.value
        val y = formula.y(data).toIntArray
        val metrics = metricsBroadcasted.value

        val prediction = CrossValidation.classification(k, formula, data, biFunctionTrainer)
        val metricsOrAccuracy = if (metrics.isEmpty) Seq(Accuracy.instance) else metrics
        metricsOrAccuracy.map(_.score(y, prediction)).toArray
      }).collect()

      formulaBroadcasted.destroy()
      dataBroadcasted.destroy()
      metricsBroadcasted.destroy()

      scores
    }
    /**
      * Distributed hyper-parameter optimization with cross validation.
      *
      * @param spark          spark session.
      * @param x              training samples.
      * @param y              training labels.
      * @param testx          test samples.
      * @param testy          test labels.
      * @param configurations hyper-parameter configurations.
      * @param metrics        classification metrics.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def hpo[T <: Object : ClassTag](x: Array[T], y: Array[Int], testx: Array[T], testy: Array[Int], configurations: Seq[Properties], metrics: ClassificationMetric*)
                                   (trainer: (Array[T], Array[Int], Properties) => Classifier[T])
                                   (implicit spark: SparkSession): Array[Array[Double]] = {
      val sc = spark.sparkContext

      val xBroadcasted = sc.broadcast(x)
      val yBroadcasted = sc.broadcast(y)
      val testxBroadcasted = sc.broadcast(testx)
      val testyBroadcasted = sc.broadcast(testy)
      val metricsBroadcasted = sc.broadcast(metrics)

      val hpRDD = sc.parallelize(configurations)
      val scores = hpRDD.map(prop => {
        val x = xBroadcasted.value
        val y = yBroadcasted.value
        val testx = xBroadcasted.value
        val testy = yBroadcasted.value
        val metrics = metricsBroadcasted.value

        val model = trainer(x, y, prop)
        val prediction = model.predict(testx)
        val metricsOrAccuracy = if (metrics.isEmpty) Seq(Accuracy.instance) else metrics
        metricsOrAccuracy.map(_.score(testy, prediction)).toArray
      }).collect()

      xBroadcasted.destroy()
      yBroadcasted.destroy()
      testxBroadcasted.destroy()
      testyBroadcasted.destroy()
      metricsBroadcasted.destroy()

      scores
    }

    /**
      * Distributed hyper-parameter optimization.
      *
      * @param spark          spark session.
      * @param formula        model formula.
      * @param train          training data.
      * @param test           test data.
      * @param configurations hyper-parameter configurations.
      * @param metrics        classification metrics.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def hpo[T <: Object : ClassTag](formula: Formula, train: DataFrame, test: DataFrame, configurations: Seq[Properties], metrics: ClassificationMetric*)
                                   (trainer: (Formula, DataFrame, Properties) => DataFrameClassifier)
                                   (implicit spark: SparkSession): Array[Array[Double]] = {
      val sc = spark.sparkContext

      val formulaBroadcasted = sc.broadcast(formula)
      val trainBroadcasted = sc.broadcast(train)
      val testBroadcasted = sc.broadcast(test)
      val metricsBroadcasted = sc.broadcast(metrics)

      val hpRDD = sc.parallelize(configurations)
      val scores = hpRDD.map(prop => {
        val formula = formulaBroadcasted.value
        val train = trainBroadcasted.value
        val test = testBroadcasted.value
        val y = formula.y(test).toIntArray
        val metrics = metricsBroadcasted.value

        val model = trainer(formula, train, prop)
        val prediction = model.predict(test)
        val metricsOrAccuracy = if (metrics.isEmpty) Seq(Accuracy.instance) else metrics
        metricsOrAccuracy.map(_.score(y, prediction)).toArray
      }).collect()

      formulaBroadcasted.destroy()
      trainBroadcasted.destroy()
      testBroadcasted.destroy()
      metricsBroadcasted.destroy()

      scores
    }
  }

  /** Regression hyper-parameter optimization. */
  object regression {
    /**
      * Distributed hyper-parameter optimization with cross validation.
      *
      * @param spark          spark session.
      * @param k              k-fold cross validation.
      * @param x              training samples.
      * @param y              response variable.
      * @param configurations hyper-parameter configurations.
      * @param metrics        classification metrics.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def hpo[T <: Object : ClassTag](k: Int, x: Array[T], y: Array[Double], configurations: Seq[Properties], metrics: RegressionMetric*)
                                   (trainer: (Array[T], Array[Double], Properties) => Regression[T])
                                   (implicit spark: SparkSession): Array[Array[Double]] = {
      val sc = spark.sparkContext

      val xBroadcasted = sc.broadcast(x)
      val yBroadcasted = sc.broadcast(y)
      val metricsBroadcasted = sc.broadcast(metrics)

      val hpRDD = sc.parallelize(configurations)
      val scores = hpRDD.map(prop => {
        val biFunctionTrainer = new BiFunction[Array[T], Array[Double], Regression[T]] {
          override def apply(x: Array[T], y: Array[Double]): Regression[T] = trainer(x, y, prop)
        }

        val x = xBroadcasted.value
        val y = yBroadcasted.value
        val metrics = metricsBroadcasted.value

        val prediction = CrossValidation.regression(k, x, y, biFunctionTrainer)
        val metricsOrRMSE = if (metrics.isEmpty) Seq(RMSE.instance) else metrics
        metricsOrRMSE.map(_.score(y, prediction)).toArray
      }).collect()

      xBroadcasted.destroy()
      yBroadcasted.destroy()
      metricsBroadcasted.destroy()

      scores
    }

    /**
      * Distributed hyper-parameter optimization with cross validation.
      *
      * @param spark          spark session.
      * @param k              k-fold cross validation.
      * @param formula        model formula.
      * @param data           training data.
      * @param configurations hyper-parameter configurations.
      * @param metrics        classification metrics.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def hpo[T <: Object : ClassTag](k: Int, formula: Formula, data: DataFrame, configurations: Seq[Properties], metrics: RegressionMetric*)
                                   (trainer: (Formula, DataFrame, Properties) => DataFrameRegression)
                                   (implicit spark: SparkSession): Array[Array[Double]] = {
      val sc = spark.sparkContext

      val formulaBroadcasted = sc.broadcast(formula)
      val dataBroadcasted = sc.broadcast(data)
      val metricsBroadcasted = sc.broadcast(metrics)

      val hpRDD = sc.parallelize(configurations)
      val scores = hpRDD.map(prop => {
        val biFunctionTrainer = new BiFunction[Formula, DataFrame, DataFrameRegression] {
          override def apply(formula: Formula, data: DataFrame): DataFrameRegression = trainer(formula, data, prop)
        }

        val formula = formulaBroadcasted.value
        val data = dataBroadcasted.value
        val y = formula.y(data).toDoubleArray
        val metrics = metricsBroadcasted.value

        val prediction = CrossValidation.regression(k, formula, data, biFunctionTrainer)
        val metricsOrRMSE = if (metrics.isEmpty) Seq(RMSE.instance) else metrics
        metricsOrRMSE.map(_.score(y, prediction)).toArray
      }).collect()

      formulaBroadcasted.destroy()
      dataBroadcasted.destroy()
      metricsBroadcasted.destroy()

      scores
    }
  }
}
