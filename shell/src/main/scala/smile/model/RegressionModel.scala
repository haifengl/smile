/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.model

import java.util.Properties
import scala.jdk.CollectionConverters.*
import smile.data.{DataFrame, Tuple}
import smile.data.formula.Formula
import smile.data.`type`.StructType
import smile.regression.*
import smile.validation.{CrossValidation, RegressionMetrics}
import spray.json.{JsNumber, JsValue}

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
@SerialVersionUID(1L)
case class RegressionModel(override val algorithm: String,
                           override val schema: StructType,
                           override val formula: Formula,
                           regression: DataFrameRegression,
                           train: RegressionMetrics,
                           validation: Option[RegressionMetrics],
                           test: Option[RegressionMetrics]) extends SmileModel {
    override def predict(x: Tuple, options: Properties): JsValue = {
        JsNumber.apply(regression.predict(x))
    }
}

object RegressionModel {
    /**
      * Trains a regression model.
      * @param algorithm the learning algorithm.
      * @param formula the model formula.
      * @param data the training data.
      * @param params the hyperparameters.
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
                DataFrameRegression.ensemble(models*)
            else
                fit(algorithm, formula, data, params)

            (model, Some(cv.avg))
        }

        val fitTime = (System.nanoTime() - start) / 1E6
        val trainMetrics = RegressionMetrics.of(fitTime, model, formula, data)
        val testMetrics = test.map(RegressionMetrics.of(model, formula, _))

        val y = formula.response().variables()
        val predictors = data.schema().fields().stream().filter(field => !y.contains(field.name))
        val schema = new StructType(predictors.toList)
        RegressionModel(algorithm, schema, formula, model, trainMetrics, validationMetrics, testMetrics)
    }

    /**
      * Trains a regression model.
      * @param algorithm the learning algorithm.
      * @param formula the model formula.
      * @param data the training data.
      * @param params the hyperparameters.
      * @return the regression model.
      */
    def fit(algorithm: String, formula: Formula, data: DataFrame, params: Properties): DataFrameRegression = {
        algorithm match {
            case "random_forest" =>
                RandomForest.fit(formula, data, RandomForest.Options.of(params))
            case "gradient_boost" =>
                GradientTreeBoost.fit(formula, data, GradientTreeBoost.Options.of(params))
            case "cart" =>
                RegressionTree.fit(formula, data, RegressionTree.Options.of(params))
            case "ols" =>
                OLS.fit(formula, data, OLS.Options.of(params))
            case "lasso" =>
                LASSO.fit(formula, data, LASSO.Options.of(params))
            case "elastic_net" =>
                ElasticNet.fit(formula, data, ElasticNet.Options.of(params))
            case "ridge" =>
                RidgeRegression.fit(formula, data, Array.fill[Double](data.size())(1), RidgeRegression.Options.of(params))
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
