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
package smile.anomaly

import smile.math.distance.Distance
import smile.math.kernel.MercerKernel
import smile.neighbor.KNNSearch

/**
 * Fits an Isolation Forest model for anomaly detection.
 *
 * @param x training samples.
 * @param trees the number of trees.
 * @param maxDepth the maximum depth of an isolation tree (0 for log2(subsample)).
 * @param subsample the sampling rate for each tree.
 * @param extensionLevel extension level for hyperplanes (0 for standard axis-aligned).
 * @return the fitted model.
 */
fun isolationForest(
    x: Array<DoubleArray>,
    trees: Int = 100,
    maxDepth: Int = 0,
    subsample: Double = 0.7,
    extensionLevel: Int = 0
): IsolationForest = IsolationForest.fit(x, IsolationForest.Options(trees, maxDepth, subsample, extensionLevel))

/**
 * Fits a Local Outlier Factor (LOF) model using a KD-tree for spatial neighborhood search.
 *
 * @param x training observations.
 * @param k the number of nearest neighbors.
 * @return the fitted model.
 */
fun lof(x: Array<DoubleArray>, k: Int = 20): LOF<DoubleArray> = LOF.fit(x, k)

/**
 * Fits a Local Outlier Factor (LOF) model on arbitrary objects in a metric/distance space.
 *
 * @param x training observations.
 * @param distance the distance function.
 * @param k the number of nearest neighbors.
 * @return the fitted model.
 */
fun <T> lof(x: Array<T>, distance: Distance<T>, k: Int = 20): LOF<T> = LOF.fit(x, distance, k)

/**
 * Fits a Local Outlier Factor (LOF) model using a provided nearest neighbor search structure.
 *
 * @param x training observations.
 * @param nns the nearest neighbor search structure.
 * @param k the number of nearest neighbors.
 * @return the fitted model.
 */
fun <T> lof(x: Array<T>, nns: KNNSearch<T, T>, k: Int = 20): LOF<T> = LOF.fit(x, nns, k)

/**
 * Fits a one-class support vector machine (One-Class SVM).
 *
 * @param x training samples.
 * @param kernel the Mercer kernel function.
 * @param nu upper bound on the fraction of outliers.
 * @param tol tolerance of convergence test.
 * @return the fitted model.
 */
fun <T> ocsvm(
    x: Array<T>,
    kernel: MercerKernel<T>,
    nu: Double = 0.5,
    tol: Double = 1E-3
): SVM<T> = SVM.fit(x, kernel, SVM.Options(nu, tol))
