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

/**
 * Feature transformations for preprocessing numeric data before model training.
 *
 * <p>The package provides the following transformers:
 *
 * <table border="1">
 *   <caption>Feature transformers</caption>
 *   <tr><th>Class</th><th>Technique</th><th>Scope</th><th>Typical Use Case</th></tr>
 *   <tr>
 *     <td>{@link smile.feature.transform.Scaler}</td>
 *     <td>Min–max scaling to [0, 1]</td>
 *     <td>Column-wise</td>
 *     <td>Bounded features; sensitive to outliers</td>
 *   </tr>
 *   <tr>
 *     <td>{@link smile.feature.transform.WinsorScaler}</td>
 *     <td>Percentile-clipped min–max scaling to [0, 1]</td>
 *     <td>Column-wise</td>
 *     <td>Outlier-robust bounded scaling (default 5th–95th percentile)</td>
 *   </tr>
 *   <tr>
 *     <td>{@link smile.feature.transform.MaxAbsScaler}</td>
 *     <td>Divide by maximum absolute value; range [−1, 1]</td>
 *     <td>Column-wise</td>
 *     <td>Preserves sparsity; suitable for sparse data</td>
 *   </tr>
 *   <tr>
 *     <td>{@link smile.feature.transform.Standardizer}</td>
 *     <td>Zero mean, unit variance (z-score)</td>
 *     <td>Column-wise</td>
 *     <td>Gaussian features; distance-based models (KNN, SVM)</td>
 *   </tr>
 *   <tr>
 *     <td>{@link smile.feature.transform.RobustStandardizer}</td>
 *     <td>Subtract median, divide by IQR</td>
 *     <td>Column-wise</td>
 *     <td>Outlier-robust standardization</td>
 *   </tr>
 *   <tr>
 *     <td>{@link smile.feature.transform.Normalizer}</td>
 *     <td>Scale each row to unit L1 / L2 / L∞ norm</td>
 *     <td>Row-wise (stateless)</td>
 *     <td>Text classification, cosine-similarity models</td>
 *   </tr>
 * </table>
 *
 * <p>All column-wise transformers implement
 * {@link smile.data.transform.InvertibleColumnTransform} and can be composed
 * into a pipeline via {@link smile.data.transform.Transform#pipeline}.
 *
 * @author Haifeng Li
 */
package smile.feature.transform;