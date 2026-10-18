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
package smile.feature.importance

import java.util.function.Function
import java.util.function.ToDoubleFunction
import smile.classification.Classifier
import smile.classification.DataFrameClassifier
import smile.data.DataFrame
import smile.data.Tuple
import smile.regression.DataFrameRegression
import smile.regression.Regression

/**
 * Creates a KernelSHAP explainer for a scalar function on [DoubleArray].
 *
 * @param background the background reference dataset.
 * @param numSamples maximum number of coalitions to evaluate per instance (0 = auto).
 * @param ridge L2 regularization parameter for numerical stability.
 * @param predictor the scalar prediction function.
 * @return the KernelSHAP explainer.
 */
fun kernelShap(
    background: Array<DoubleArray>,
    numSamples: Int = 0,
    ridge: Double = 1e-8,
    predictor: ToDoubleFunction<DoubleArray>
): KernelSHAP<DoubleArray> {
    return KernelSHAP.of(background, predictor, KernelSHAP.Options(numSamples, ridge))
}

/**
 * Creates a KernelSHAP explainer for a vector-valued function on [DoubleArray].
 *
 * @param background the background reference dataset.
 * @param numSamples maximum number of coalitions to evaluate per instance (0 = auto).
 * @param ridge L2 regularization parameter for numerical stability.
 * @param predictor the vector prediction function.
 * @return the KernelSHAP explainer.
 */
fun kernelShapVector(
    background: Array<DoubleArray>,
    numSamples: Int = 0,
    ridge: Double = 1e-8,
    predictor: Function<DoubleArray, DoubleArray>
): KernelSHAP<DoubleArray> {
    return KernelSHAP.of(background, predictor, KernelSHAP.Options(numSamples, ridge))
}

/**
 * Creates a KernelSHAP explainer for a [Regression] model on [DoubleArray].
 *
 * @param background the background reference dataset.
 * @param model the regression model.
 * @param numSamples maximum number of coalitions to evaluate per instance (0 = auto).
 * @param ridge L2 regularization parameter for numerical stability.
 * @return the KernelSHAP explainer.
 */
fun kernelShap(
    background: Array<DoubleArray>,
    model: Regression<DoubleArray>,
    numSamples: Int = 0,
    ridge: Double = 1e-8
): KernelSHAP<DoubleArray> {
    return KernelSHAP.of(background, model, KernelSHAP.Options(numSamples, ridge))
}

/**
 * Creates a KernelSHAP explainer for a [Classifier] model on [DoubleArray].
 *
 * @param background the background reference dataset.
 * @param model the classification model.
 * @param numSamples maximum number of coalitions to evaluate per instance (0 = auto).
 * @param ridge L2 regularization parameter for numerical stability.
 * @return the KernelSHAP explainer.
 */
fun kernelShap(
    background: Array<DoubleArray>,
    model: Classifier<DoubleArray>,
    numSamples: Int = 0,
    ridge: Double = 1e-8
): KernelSHAP<DoubleArray> {
    return KernelSHAP.of(background, model, KernelSHAP.Options(numSamples, ridge))
}

/**
 * Creates a KernelSHAP explainer for a [DataFrameRegression] model.
 *
 * @param background the background reference data frame.
 * @param model the DataFrame regression model.
 * @param numSamples maximum number of coalitions to evaluate per instance (0 = auto).
 * @param ridge L2 regularization parameter for numerical stability.
 * @return the KernelSHAP explainer.
 */
fun kernelShap(
    background: DataFrame,
    model: DataFrameRegression,
    numSamples: Int = 0,
    ridge: Double = 1e-8
): KernelSHAP<Tuple> {
    return KernelSHAP.of(background, model, KernelSHAP.Options(numSamples, ridge))
}

/**
 * Creates a KernelSHAP explainer for a [DataFrameClassifier] model.
 *
 * @param background the background reference data frame.
 * @param model the DataFrame classifier model.
 * @param numSamples maximum number of coalitions to evaluate per instance (0 = auto).
 * @param ridge L2 regularization parameter for numerical stability.
 * @return the KernelSHAP explainer.
 */
fun kernelShap(
    background: DataFrame,
    model: DataFrameClassifier,
    numSamples: Int = 0,
    ridge: Double = 1e-8
): KernelSHAP<Tuple> {
    return KernelSHAP.of(background, model, KernelSHAP.Options(numSamples, ridge))
}
