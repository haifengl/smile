/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.model

import java.util.Properties
import smile.data.DataFrame
import smile.data.formula.Formula
import smile.data.`type`.StructType
import smile.classification.{AdaBoost, DataFrameClassifier, DecisionTree}
import smile.regression.{DataFrameRegression, ElasticNet, LASSO, OLS, RegressionTree, RidgeRegression}

/**
  * The machine learning model applicable on a data frame.
  */
sealed trait DataFrameModel {
  /** The algorithm name. */
  val algorithm: String
  /** The schema of input data (without response variable). */
  val schema: StructType
  /** The model formula. */
  val formula: Formula
}

/**
  * The classification model.
  * @param algorithm the algorithm name.
  * @param schema the schema of input data (without response variable).
  * @param formula the model formula.
  * @param classifier the classification model.
  */
case class ClassificationModel(override val algorithm: String,
                               override val schema: StructType,
                               override val formula: Formula,
                               classifier: DataFrameClassifier) extends DataFrameModel

object ClassificationModel {
  /**
    * Trains a classification model.
    * @param algorithm the algorithm name.
    * @param formula the model formula.
    * @param data the training data.
    * @param props the hyperparameters.
    * @return the classification model.
    */
  def apply(algorithm: String, formula: Formula, data: DataFrame, props: Properties): ClassificationModel = {
    val model: DataFrameClassifier = algorithm match {
      case "random.forest" =>
        smile.classification.RandomForest.fit(formula, data, props)
      case "gbt" =>
        smile.classification.GradientTreeBoost.fit(formula, data, props)
      case "cart" =>
        DecisionTree.fit(formula, data, props)
      case "adaboost" =>
        AdaBoost.fit(formula, data, props)
      case _ =>
        throw new IllegalArgumentException("Unsupported algorithm: " + algorithm)
    }

    val y = formula.response().variables()
    val predictors = data.schema().fields().filter(field => !y.contains(field.name))
    val schema = new StructType(predictors: _*)
    ClassificationModel(algorithm, schema, formula, model)
  }
}

/**
  * The regression model.
  * @param algorithm the algorithm name.
  * @param schema the schema of input data (without response variable).
  * @param formula the model formula.
  * @param regression the regression model.
  */
case class RegressionModel(override val algorithm: String,
                           override val schema: StructType,
                           override val formula: Formula,
                           regression: DataFrameRegression) extends DataFrameModel

object RegressionModel {
  /**
    * Trains a regression model.
    * @param algorithm the algorithm name.
    * @param formula the model formula.
    * @param data the training data.
    * @param props the hyperparameters.
    * @return the regression model.
    */
  def apply(algorithm: String, formula: Formula, data: DataFrame, props: Properties): RegressionModel = {
    val model: DataFrameRegression = algorithm match {
      case "random.forest" =>
        smile.regression.RandomForest.fit(formula, data, props)
      case "gbt" =>
        smile.regression.GradientTreeBoost.fit(formula, data, props)
      case "cart" =>
        RegressionTree.fit(formula, data, props)
      case "ols" =>
        OLS.fit(formula, data, props)
      case "lasso" =>
        LASSO.fit(formula, data, props)
      case "elastic.net" =>
        ElasticNet.fit(formula, data, props)
      case "ridge" =>
        RidgeRegression.fit(formula, data, props)
      case _ =>
        throw new IllegalArgumentException("Unsupported algorithm: " + algorithm)
    }

    val y = formula.response().variables()
    val predictors = data.schema().fields().filter(field => !y.contains(field.name))
    val schema = new StructType(predictors: _*)
    RegressionModel(algorithm, schema, formula, model)
  }
}