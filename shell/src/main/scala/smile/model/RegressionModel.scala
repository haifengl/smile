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
import scala.jdk.CollectionConverters._
import smile.data.DataFrame
import smile.data.formula.Formula
import smile.data.`type`.StructType
import smile.regression._
import smile.validation.{CrossValidation, RegressionMetrics}

/**
  * The regression model.
  * @param algorithm the algorithm name.
  * @param schema the schema of input data (without response variable).
  * @param formula the model formula.
  * @param regression the regression model.
  * @param train the training metrics.
  * @param validation the cross-validation metrics.
  * @param test the test metrics.
  */
case class RegressionModel(override val algorithm: String,
                           override val schema: StructType,
                           override val formula: Formula,
                           regression: DataFrameRegression,
                           train: RegressionMetrics,
                           validation: Option[RegressionMetrics],
                           test: Option[RegressionMetrics]) extends DataFrameModel

object RegressionModel {
    /**
      * Trains a regression model.
      * @param algorithm the learning algorithm.
      * @param formula the model formula.
      * @param data the training data.
      * @param params the hyper-parameters.
      * @param kfold k-fold cross validation if kfold > 1.
      * @param round the number of repeated cross validation.
      * @param ensemble create the ensemble of cross validation models if true.
      * @param test the test data.
      * @return the regression model.
      */
    def apply(algorithm: String, formula: Formula, data: DataFrame, params: Properties,
              kfold: Int = 1, round: Int = 1, ensemble: Boolean = false,
              test: Option[DataFrame] = None): RegressionModel = {
        val start = System.nanoTime()
        val (model, validationMetrics) = if (kfold < 2) {
            val model = fit(algorithm, formula, data, params)
            (model, None)
        } else {
            val cv = CrossValidation.regression(round, kfold, formula, data, (f, d) => fit(algorithm, f, d, params))
            val models = cv.rounds.asScala.map(round => round.model).toArray
            val model = if (ensemble)
                DataFrameRegression.ensemble(models: _*)
            else
                fit(algorithm, formula, data, params)

            (model, Some(cv.avg))
        }

        val fitTime = (System.nanoTime() - start) / 1E6
        val trainMetrics = RegressionMetrics.of(fitTime, model, formula, data)
        val testMetrics = test.map(RegressionMetrics.of(model, formula, _))

        val y = formula.response().variables()
        val predictors = data.schema().fields().filter(field => !y.contains(field.name))
        val schema = new StructType(predictors: _*)
        RegressionModel(algorithm, schema, formula, model, trainMetrics, validationMetrics, testMetrics)
    }

    /**
      * Trains a regression model.
      * @param algorithm the learning algorithm.
      * @param formula the model formula.
      * @param data the training data.
      * @param params the hyper-parameters.
      * @return the regression model.
      */
    def fit(algorithm: String, formula: Formula, data: DataFrame, params: Properties): DataFrameRegression = {
        algorithm match {
            case "random_forest" =>
                RandomForest.fit(formula, data, params)
            case "gradient_boost" =>
                GradientTreeBoost.fit(formula, data, params)
            case "cart" =>
                RegressionTree.fit(formula, data, params)
            case "ols" =>
                OLS.fit(formula, data, params)
            case "lasso" =>
                LASSO.fit(formula, data, params)
            case "elastic_net" =>
                ElasticNet.fit(formula, data, params)
            case "ridge" =>
                RidgeRegression.fit(formula, data, params)
            case "gaussian_process" =>
                DataFrameRegression.of(formula, data, params, GaussianProcessRegression.fit(_, _, _))
            case "mlp" =>
                DataFrameRegression.of(formula, data, params, MLP.fit(_, _, _));
            case "svm" =>
                DataFrameRegression.of(formula, data, params, SVM.fit(_, _, _))
            case "rbf" =>
                DataFrameRegression.of(formula, data, params, RBFNetwork.fit(_, _, _))
            case _ =>
                throw new IllegalArgumentException("Unsupported algorithm: " + algorithm)
        }
    }
}