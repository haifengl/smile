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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature

import java.util.function.{Function => JFunction, ToDoubleFunction}
import smile.classification.{Classifier, DataFrameClassifier}
import smile.data.{DataFrame, Tuple}
import smile.regression.{DataFrameRegression, Regression}

/** Feature importance and model explanation methods.
  *
  * @author Haifeng Li
  */
package object importance {

  /** Creates a KernelSHAP explainer for a scalar function on `Array[Double]`.
    *
    * @param background the background reference dataset.
    * @param predictor the scalar prediction function.
    * @param numSamples maximum number of coalitions to evaluate per instance (0 = auto).
    * @param ridge L2 regularization parameter for numerical stability.
    * @return the KernelSHAP explainer.
    */
  def kernelShap(
      background: Array[Array[Double]],
      predictor: ToDoubleFunction[Array[Double]],
      numSamples: Int = 0,
      ridge: Double = 1e-8
  ): KernelSHAP[Array[Double]] = {
    KernelSHAP.of(background, predictor, new KernelSHAP.Options(numSamples, ridge))
  }

  /** Creates a KernelSHAP explainer for a vector-valued function on `Array[Double]`.
    *
    * @param background the background reference dataset.
    * @param predictor the vector prediction function.
    * @param numSamples maximum number of coalitions to evaluate per instance (0 = auto).
    * @param ridge L2 regularization parameter for numerical stability.
    * @return the KernelSHAP explainer.
    */
  def kernelShapVector(
      background: Array[Array[Double]],
      predictor: JFunction[Array[Double], Array[Double]],
      numSamples: Int = 0,
      ridge: Double = 1e-8
  ): KernelSHAP[Array[Double]] = {
    KernelSHAP.of(background, predictor, new KernelSHAP.Options(numSamples, ridge))
  }

  /** Creates a KernelSHAP explainer for a `Regression[Array[Double]]` model.
    *
    * @param background the background reference dataset.
    * @param model the regression model.
    * @param numSamples maximum number of coalitions to evaluate per instance (0 = auto).
    * @param ridge L2 regularization parameter for numerical stability.
    * @return the KernelSHAP explainer.
    */
  def kernelShap(
      background: Array[Array[Double]],
      model: Regression[Array[Double]],
      numSamples: Int,
      ridge: Double
  ): KernelSHAP[Array[Double]] = {
    KernelSHAP.of(background, model, new KernelSHAP.Options(numSamples, ridge))
  }

  /** Creates a KernelSHAP explainer for a `Regression[Array[Double]]` model with default options.
    *
    * @param background the background reference dataset.
    * @param model the regression model.
    * @return the KernelSHAP explainer.
    */
  def kernelShap(
      background: Array[Array[Double]],
      model: Regression[Array[Double]]
  ): KernelSHAP[Array[Double]] = {
    KernelSHAP.of(background, model)
  }

  /** Creates a KernelSHAP explainer for a `Classifier[Array[Double]]` model.
    *
    * @param background the background reference dataset.
    * @param model the classification model.
    * @param numSamples maximum number of coalitions to evaluate per instance (0 = auto).
    * @param ridge L2 regularization parameter for numerical stability.
    * @return the KernelSHAP explainer.
    */
  def kernelShap(
      background: Array[Array[Double]],
      model: Classifier[Array[Double]],
      numSamples: Int,
      ridge: Double
  ): KernelSHAP[Array[Double]] = {
    KernelSHAP.of(background, model, new KernelSHAP.Options(numSamples, ridge))
  }

  /** Creates a KernelSHAP explainer for a `Classifier[Array[Double]]` model with default options.
    *
    * @param background the background reference dataset.
    * @param model the classification model.
    * @return the KernelSHAP explainer.
    */
  def kernelShap(
      background: Array[Array[Double]],
      model: Classifier[Array[Double]]
  ): KernelSHAP[Array[Double]] = {
    KernelSHAP.of(background, model)
  }

  /** Creates a KernelSHAP explainer for a `DataFrameRegression` model.
    *
    * @param background the background reference data frame.
    * @param model the DataFrame regression model.
    * @param numSamples maximum number of coalitions to evaluate per instance (0 = auto).
    * @param ridge L2 regularization parameter for numerical stability.
    * @return the KernelSHAP explainer.
    */
  def kernelShap(
      background: DataFrame,
      model: DataFrameRegression,
      numSamples: Int,
      ridge: Double
  ): KernelSHAP[Tuple] = {
    KernelSHAP.of(background, model, new KernelSHAP.Options(numSamples, ridge))
  }

  /** Creates a KernelSHAP explainer for a `DataFrameRegression` model with default options.
    *
    * @param background the background reference data frame.
    * @param model the DataFrame regression model.
    * @return the KernelSHAP explainer.
    */
  def kernelShap(
      background: DataFrame,
      model: DataFrameRegression
  ): KernelSHAP[Tuple] = {
    KernelSHAP.of(background, model)
  }

  /** Creates a KernelSHAP explainer for a `DataFrameClassifier` model.
    *
    * @param background the background reference data frame.
    * @param model the DataFrame classifier model.
    * @param numSamples maximum number of coalitions to evaluate per instance (0 = auto).
    * @param ridge L2 regularization parameter for numerical stability.
    * @return the KernelSHAP explainer.
    */
  def kernelShap(
      background: DataFrame,
      model: DataFrameClassifier,
      numSamples: Int,
      ridge: Double
  ): KernelSHAP[Tuple] = {
    KernelSHAP.of(background, model, new KernelSHAP.Options(numSamples, ridge))
  }

  /** Creates a KernelSHAP explainer for a `DataFrameClassifier` model with default options.
    *
    * @param background the background reference data frame.
    * @param model the DataFrame classifier model.
    * @return the KernelSHAP explainer.
    */
  def kernelShap(
      background: DataFrame,
      model: DataFrameClassifier
  ): KernelSHAP[Tuple] = {
    KernelSHAP.of(background, model)
  }
}
