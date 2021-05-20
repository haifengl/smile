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
import smile.validation.{ClassificationMetrics, CrossValidation}

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
    * @param algorithm the learning algorithm.
    * @param formula the model formula.
    * @param data the training data.
    * @param params the hyper-parameters.
    * @param kfold k-fold cross validation if kfold > 1.
    * @param round the number of repeated cross validation.
    * @param ensemble create the ensemble of cross validation models if true.
    * @param test the test data.
    * @return the classification model.
    */
  def apply(algorithm: String, formula: Formula, data: DataFrame, params: Properties,
            kfold: Int = 1, round: Int = 1, ensemble: Boolean = false,
            test: Option[DataFrame] = None): ClassificationModel = {
    val start = System.nanoTime()
    val (model, validationMetrics) = if (kfold < 2) {
      val model = fit(algorithm, formula, data, params)
      (model, None)
    } else {
      val cv = CrossValidation.stratify(round, kfold, formula, data, (f, d) => fit(algorithm, f, d, params))
      val models = cv.rounds.asScala.map(round => round.model).toArray
      val model = if (ensemble)
        DataFrameClassifier.ensemble(models: _*)
      else
        fit(algorithm, formula, data, params)

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
    * @param algorithm the learning algorithm.
    * @param formula the model formula.
    * @param data the training data.
    * @param params the hyper-parameters.
    * @return the classification model.
    */
  def fit(algorithm: String, formula: Formula, data: DataFrame, params: Properties): DataFrameClassifier = {
    algorithm match {
      case "random_forest" =>
        smile.classification.RandomForest.fit(formula, data, params)
      case "gradient_boost" =>
        smile.classification.GradientTreeBoost.fit(formula, data, params)
      case "adaboost" =>
        AdaBoost.fit(formula, data, params)
      case "cart" =>
        DecisionTree.fit(formula, data, params)
      case "logistic" =>
        DataFrameClassifier.of(formula, data, params, LogisticRegression.fit(_, _, _))
      case "fisher" =>
        DataFrameClassifier.of(formula, data, params, FLD.fit(_, _, _))
      case "lda" =>
        DataFrameClassifier.of(formula, data, params, LDA.fit(_, _, _))
      case "qda" =>
        DataFrameClassifier.of(formula, data, params, QDA.fit(_, _, _))
      case "rda" =>
        DataFrameClassifier.of(formula, data, params, RDA.fit(_, _, _))
      case "mlp" =>
        DataFrameClassifier.of(formula, data, params, MLP.fit(_, _, _));
      case "svm" =>
        DataFrameClassifier.of(formula, data, params, SVM.fit(_, _, _));
      case "rbf" =>
        DataFrameClassifier.of(formula, data, params, RBFNetwork.fit(_, _, _))
      case _ =>
        throw new IllegalArgumentException("Unsupported algorithm: " + algorithm)
    }
  }
}
