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
import smile.classification._
import smile.regression.{DataFrameRegression, ElasticNet, GaussianProcessRegression, LASSO, OLS, RegressionTree, RidgeRegression, SVR}
import smile.validation.{ClassificationMetrics, CrossValidation, RegressionMetrics}

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
  * @param train the training metrics.
  * @param validation the cross-validation metrics.
  * @param test the test metrics.
  */
case class ClassificationModel(override val algorithm: String,
                               override val schema: StructType,
                               override val formula: Formula,
                               classifier: DataFrameClassifier,
                               train: ClassificationMetrics,
                               validation: Option[ClassificationMetrics],
                               test: Option[ClassificationMetrics]) extends DataFrameModel

object ClassificationModel {
  /**
    * Trains a classification model.
    * @param algorithm the algorithm name.
    * @param formula the model formula.
    * @param data the training data.
    * @param prop the hyperparameters.
    * @param kfold k-fold cross validation if kfold > 1.
    * @param round the number of repeated cross validation.
    * @param ensemble create the ensemble of cross validation models if true.
    * @param test the test data.
    * @return the classification model.
    */
  def apply(algorithm: String, formula: Formula, data: DataFrame, prop: Properties,
            kfold: Int = 1, round: Int = 1, ensemble: Boolean = false,
            test: Option[DataFrame] = None): ClassificationModel = {
    val start = System.nanoTime()
    val (model, validationMetrics) = if (kfold < 2) {
      val model = fit(algorithm, formula, data, prop)
      (model, None)
    } else {
      val cv = CrossValidation.stratify(round, kfold, formula, data, (f, d) => fit(algorithm, f, d, prop))
      val models = cv.rounds.asScala.map(round => round.model).toArray
      val model = if (ensemble)
        DataFrameClassifier.ensemble(models: _*)
      else
        fit(algorithm, formula, data, prop)

      (model, Some(cv.avg))
    }

    val fitTime = (System.nanoTime() - start) / 1E6
    val trainMetrics = ClassificationMetrics.of(fitTime, model, formula, data)
    val testMetrics = test.map(ClassificationMetrics.of(model, formula, _))

    val y = formula.response().variables()
    val predictors = data.schema().fields().filter(field => !y.contains(field.name))
    val schema = new StructType(predictors: _*)
    ClassificationModel(algorithm, schema, formula, model, trainMetrics, validationMetrics, testMetrics)
  }

  /**
    * Trains a classification model.
    * @param algorithm the algorithm name.
    * @param formula the model formula.
    * @param data the training data.
    * @param prop the hyperparameters.
    * @return the classification model.
    */
  def fit(algorithm: String, formula: Formula, data: DataFrame, prop: Properties): DataFrameClassifier = {
    algorithm match {
      case "random.forest" =>
        smile.classification.RandomForest.fit(formula, data, prop)
      case "gradient.boost" =>
        smile.classification.GradientTreeBoost.fit(formula, data, prop)
      case "cart" =>
        DecisionTree.fit(formula, data, prop)
      case "adaboost" =>
        AdaBoost.fit(formula, data, prop)
      case "logit" =>
        DataFrameClassifier.of(formula, data, (x, y) => LogisticRegression.fit(x, y, prop))
      case "fld" =>
        DataFrameClassifier.of(formula, data, (x, y) => FLD.fit(x, y, prop))
      case "lda" =>
        DataFrameClassifier.of(formula, data, (x, y) => LDA.fit(x, y, prop))
      case "qda" =>
        DataFrameClassifier.of(formula, data, (x, y) => QDA.fit(x, y, prop))
      case "rda" =>
        DataFrameClassifier.of(formula, data, (x, y) => RDA.fit(x, y, prop))
      case "mlp" =>
        DataFrameClassifier.of(formula, data, (x, y) => MLP.fit(x, y, prop));
      case "svm" =>
        DataFrameClassifier.of(formula, data, (x, y) => SVM.fit(x, y, prop));
      case "rbf" =>
        DataFrameClassifier.of(formula, data, (x, y) => RBFNetwork.fit(x, y, prop))
      case _ =>
        throw new IllegalArgumentException("Unsupported algorithm: " + algorithm)
    }
  }
}

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
    * @param algorithm the algorithm name.
    * @param formula the model formula.
    * @param data the training data.
    * @param prop the hyperparameters.
    * @param kfold k-fold cross validation if kfold > 1.
    * @param round the number of repeated cross validation.
    * @param ensemble create the ensemble of cross validation models if true.
    * @param test the test data.
    * @return the regression model.
    */
  def apply(algorithm: String, formula: Formula, data: DataFrame, prop: Properties,
            kfold: Int = 1, round: Int = 1, ensemble: Boolean = false,
            test: Option[DataFrame] = None): RegressionModel = {
    val start = System.nanoTime()
    val (model, validationMetrics) = if (kfold < 2) {
      val model = fit(algorithm, formula, data, prop)
      (model, None)
    } else {
      val cv = CrossValidation.regression(round, kfold, formula, data, (f, d) => fit(algorithm, f, d, prop))
      val models = cv.rounds.asScala.map(round => round.model).toArray
      val model = if (ensemble)
        DataFrameRegression.ensemble(models: _*)
      else
        fit(algorithm, formula, data, prop)

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
    * @param algorithm the algorithm name.
    * @param formula the model formula.
    * @param data the training data.
    * @param prop the hyperparameters.
    * @return the regression model.
    */
  def fit(algorithm: String, formula: Formula, data: DataFrame, prop: Properties): DataFrameRegression = {
    algorithm match {
      case "random.forest" =>
        smile.regression.RandomForest.fit(formula, data, prop)
      case "gradient.boost" =>
        smile.regression.GradientTreeBoost.fit(formula, data, prop)
      case "cart" =>
        RegressionTree.fit(formula, data, prop)
      case "ols" =>
        OLS.fit(formula, data, prop)
      case "lasso" =>
        LASSO.fit(formula, data, prop)
      case "elastic.net" =>
        ElasticNet.fit(formula, data, prop)
      case "ridge" =>
        RidgeRegression.fit(formula, data, prop)
      case "gaussian.process" =>
        DataFrameRegression.of(formula, data, (x, y) => GaussianProcessRegression.fit(x, y, prop))
      case "mlp" =>
        DataFrameRegression.of(formula, data, (x, y) => smile.regression.MLP.fit(x, y, prop));
      case "svm" =>
        DataFrameRegression.of(formula, data, (x, y) => SVR.fit(x, y, prop))
      case "rbf" =>
        DataFrameRegression.of(formula, data, (x, y) => smile.regression.RBFNetwork.fit(x, y, prop))
      case _ =>
        throw new IllegalArgumentException("Unsupported algorithm: " + algorithm)
    }
  }
}