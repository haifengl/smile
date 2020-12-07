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

package smile

import java.util.Properties
import java.util.function.BiFunction
import scala.reflect.ClassTag
import org.apache.spark.sql.SparkSession
import smile.classification.{Classifier, DataFrameClassifier}
import smile.data.DataFrame
import smile.data.formula.Formula
import smile.regression.{Regression, DataFrameRegression}
import smile.validation._

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

  /** Hyper-parameter optimization. */
  object hpo {
    /**
      * Distributed hyper-parameter optimization with cross validation for classification.
      *
      * @param spark          spark session.
      * @param k              k-fold cross validation.
      * @param x              training samples.
      * @param y              training labels.
      * @param configurations hyper-parameter configurations.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def classification[T <: AnyRef : ClassTag, M <: Classifier[T]](k: Int, x: Array[T], y: Array[Int], configurations: Seq[Properties])
                                                       (trainer: (Array[T], Array[Int], Properties) => M)
                                                       (implicit spark: SparkSession): Array[ClassificationValidations[M]] = {
      val sc = spark.sparkContext

      val xBroadcasted = sc.broadcast(x)
      val yBroadcasted = sc.broadcast(y)

      val scores = sc.parallelize(configurations).map(prop => {
        val biFunctionTrainer = new BiFunction[Array[T], Array[Int], M] {
          override def apply(x: Array[T], y: Array[Int]): M = trainer(x, y, prop)
        }

        val x = xBroadcasted.value
        val y = yBroadcasted.value

        CrossValidation.classification(k, x, y, biFunctionTrainer)
      }).collect()

      xBroadcasted.destroy()
      yBroadcasted.destroy()

      scores
    }

    /**
      * Distributed hyper-parameter optimization with cross validation for classification.
      *
      * @param spark          spark session.
      * @param k              k-fold cross validation.
      * @param formula        model formula.
      * @param data           training data.
      * @param configurations hyper-parameter configurations.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def classification[M <: DataFrameClassifier](k: Int, formula: Formula, data: DataFrame, configurations: Seq[Properties])
                                              (trainer: (Formula, DataFrame, Properties) => M)
                                              (implicit spark: SparkSession): Array[ClassificationValidations[M]] = {
      val sc = spark.sparkContext

      val formulaBroadcasted = sc.broadcast(formula)
      val dataBroadcasted = sc.broadcast(data)

      val scores = sc.parallelize(configurations).map(prop => {
        val biFunctionTrainer = new BiFunction[Formula, DataFrame, M] {
          override def apply(formula: Formula, data: DataFrame): M = trainer(formula, data, prop)
        }

        val formula = formulaBroadcasted.value
        val data = dataBroadcasted.value

        CrossValidation.classification(k, formula, data, biFunctionTrainer)
      }).collect()

      formulaBroadcasted.destroy()
      dataBroadcasted.destroy()

      scores
    }

    /**
      * Distributed hyper-parameter optimization for classification.
      *
      * @param spark          spark session.
      * @param x              training samples.
      * @param y              training labels.
      * @param testx          test samples.
      * @param testy          test labels.
      * @param configurations hyper-parameter configurations.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def classification[T <: AnyRef : ClassTag, M <: Classifier[T]](x: Array[T], y: Array[Int], testx: Array[T], testy: Array[Int], configurations: Seq[Properties])
                                                       (trainer: (Array[T], Array[Int], Properties) => M)
                                                       (implicit spark: SparkSession): Array[ClassificationValidation[M]] = {
      val sc = spark.sparkContext

      val xBroadcasted = sc.broadcast(x)
      val yBroadcasted = sc.broadcast(y)
      val testxBroadcasted = sc.broadcast(testx)
      val testyBroadcasted = sc.broadcast(testy)

      val scores = sc.parallelize(configurations).map(prop => {
        val biFunctionTrainer = new BiFunction[Array[T], Array[Int], M] {
          override def apply(x: Array[T], y: Array[Int]): M = trainer(x, y, prop)
        }

        val x = xBroadcasted.value
        val y = yBroadcasted.value
        val testx = xBroadcasted.value
        val testy = yBroadcasted.value

        ClassificationValidation.of(x, y, testx, testy, biFunctionTrainer)
      }).collect()

      xBroadcasted.destroy()
      yBroadcasted.destroy()
      testxBroadcasted.destroy()
      testyBroadcasted.destroy()

      scores
    }

    /**
      * Distributed hyper-parameter optimization for classification.
      *
      * @param spark          spark session.
      * @param formula        model formula.
      * @param train          training data.
      * @param test           test data.
      * @param configurations hyper-parameter configurations.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def classification[M <: DataFrameClassifier](formula: Formula, train: DataFrame, test: DataFrame, configurations: Seq[Properties])
                                              (trainer: (Formula, DataFrame, Properties) => M)
                                              (implicit spark: SparkSession): Array[ClassificationValidation[M]] = {
      val sc = spark.sparkContext

      val formulaBroadcasted = sc.broadcast(formula)
      val trainBroadcasted = sc.broadcast(train)
      val testBroadcasted = sc.broadcast(test)

      val scores = sc.parallelize(configurations).map(prop => {
        val biFunctionTrainer = new BiFunction[Formula, DataFrame, M] {
          override def apply(formula: Formula, data: DataFrame): M = trainer(formula, data, prop)
        }

        val formula = formulaBroadcasted.value
        val train = trainBroadcasted.value
        val test = testBroadcasted.value

        ClassificationValidation.of(formula, train, test, biFunctionTrainer)
      }).collect()

      formulaBroadcasted.destroy()
      trainBroadcasted.destroy()
      testBroadcasted.destroy()

      scores
    }

    /**
      * Distributed hyper-parameter optimization with cross validation for regression.
      *
      * @param spark          spark session.
      * @param k              k-fold cross validation.
      * @param x              training samples.
      * @param y              response variable.
      * @param configurations hyper-parameter configurations.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def regression[T <: AnyRef : ClassTag, M <: Regression[T]](k: Int, x: Array[T], y: Array[Double], configurations: Seq[Properties])
                                                   (trainer: (Array[T], Array[Double], Properties) => M)
                                                   (implicit spark: SparkSession): Array[RegressionValidations[M]] = {
      val sc = spark.sparkContext

      val xBroadcasted = sc.broadcast(x)
      val yBroadcasted = sc.broadcast(y)

      val scores = sc.parallelize(configurations).map(prop => {
        val biFunctionTrainer = new BiFunction[Array[T], Array[Double], M] {
          override def apply(x: Array[T], y: Array[Double]): M = trainer(x, y, prop)
        }

        val x = xBroadcasted.value
        val y = yBroadcasted.value

        CrossValidation.regression(k, x, y, biFunctionTrainer)
      }).collect()

      xBroadcasted.destroy()
      yBroadcasted.destroy()

      scores
    }

    /**
      * Distributed hyper-parameter optimization with cross validation for regression.
      *
      * @param spark          spark session.
      * @param k              k-fold cross validation.
      * @param formula        model formula.
      * @param data           training data.
      * @param configurations hyper-parameter configurations.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def regression[M <: DataFrameRegression](k: Int, formula: Formula, data: DataFrame, configurations: Seq[Properties])
                                          (trainer: (Formula, DataFrame, Properties) => M)
                                          (implicit spark: SparkSession): Array[RegressionValidations[M]] = {
      val sc = spark.sparkContext

      val formulaBroadcasted = sc.broadcast(formula)
      val dataBroadcasted = sc.broadcast(data)

      val scores = sc.parallelize(configurations).map(prop => {
        val biFunctionTrainer = new BiFunction[Formula, DataFrame, M] {
          override def apply(formula: Formula, data: DataFrame): M = trainer(formula, data, prop)
        }

        val formula = formulaBroadcasted.value
        val data = dataBroadcasted.value
        val y = formula.y(data).toDoubleArray

        CrossValidation.regression(k, formula, data, biFunctionTrainer)
      }).collect()

      formulaBroadcasted.destroy()
      dataBroadcasted.destroy()

      scores
    }

    /**
      * Distributed hyper-parameter optimization for regression.
      *
      * @param spark          spark session.
      * @param x              training samples.
      * @param y              response variable.
      * @param testx          test samples.
      * @param testy          test labels.
      * @param configurations hyper-parameter configurations.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def regression[T <: AnyRef : ClassTag, M <: Regression[T]](x: Array[T], y: Array[Double], testx: Array[T], testy: Array[Double], configurations: Seq[Properties])
                                                   (trainer: (Array[T], Array[Double], Properties) => M)
                                                   (implicit spark: SparkSession): Array[RegressionValidation[M]] = {
      val sc = spark.sparkContext

      val xBroadcasted = sc.broadcast(x)
      val yBroadcasted = sc.broadcast(y)
      val testxBroadcasted = sc.broadcast(testx)
      val testyBroadcasted = sc.broadcast(testy)

      val scores = sc.parallelize(configurations).map(prop => {
        val biFunctionTrainer = new BiFunction[Array[T], Array[Double], M] {
          override def apply(x: Array[T], y: Array[Double]): M = trainer(x, y, prop)
        }

        val x = xBroadcasted.value
        val y = yBroadcasted.value
        val testx = testxBroadcasted.value
        val testy = testyBroadcasted.value

        RegressionValidation.of(x, y, testx, testy, biFunctionTrainer)
      }).collect()

      xBroadcasted.destroy()
      yBroadcasted.destroy()
      testxBroadcasted.destroy()
      testyBroadcasted.destroy()

      scores
    }

    /**
      * Distributed hyper-parameter optimization for regression.
      *
      * @param spark          spark session.
      * @param formula        model formula.
      * @param train          training data.
      * @param test           test data.
      * @param configurations hyper-parameter configurations.
      * @param trainer        classifier trainer.
      * @return a matrix of classification metrics. The rows are per model.
      *         The columns are per metric.
      */
    def regression[M <: DataFrameRegression](formula: Formula, train: DataFrame, test: DataFrame, configurations: Seq[Properties])
                                          (trainer: (Formula, DataFrame, Properties) => M)
                                          (implicit spark: SparkSession): Array[RegressionValidation[M]] = {
      val sc = spark.sparkContext

      val formulaBroadcasted = sc.broadcast(formula)
      val trainBroadcasted = sc.broadcast(train)
      val testBroadcasted = sc.broadcast(test)

      val scores = sc.parallelize(configurations).map(prop => {
        val biFunctionTrainer = new BiFunction[Formula, DataFrame, M] {
          override def apply(formula: Formula, data: DataFrame): M = trainer(formula, data, prop)
        }

        val formula = formulaBroadcasted.value
        val train = trainBroadcasted.value
        val test = trainBroadcasted.value

        RegressionValidation.of(formula, train, test, biFunctionTrainer)
      }).collect()

      formulaBroadcasted.destroy()
      trainBroadcasted.destroy()
      testBroadcasted.destroy()

      scores
    }
  }
}
