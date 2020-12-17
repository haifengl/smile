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

package smile.projection

import smile.math.kernel.MercerKernel
import smile.math.TimeFunction

/**
 * Principal component analysis. PCA is an orthogonal
 * linear transformation that transforms a number of possibly correlated
 * variables into a smaller number of uncorrelated variables called principal
 * components. The first principal component accounts for as much of the
 * variability in the data as possible, and each succeeding component accounts
 * for as much of the remaining variability as possible. PCA is theoretically
 * the optimum transform for given data in least square terms.
 * PCA can be thought of as revealing the internal structure of the data in
 * a way which best explains the variance in the data. If a multivariate
 * dataset is visualized as a set of coordinates in a high-dimensional data
 * space, PCA supplies the user with a lower-dimensional picture when viewed
 * from its (in some sense) most informative viewpoint.
 *
 * PCA is mostly used as a tool in exploratory data analysis and for making
 * predictive models. PCA involves the calculation of the eigenvalue
 * decomposition of a data covariance matrix or singular value decomposition
 * of a data matrix, usually after mean centering the data for each attribute.
 * The results of a PCA are usually discussed in terms of component scores and
 * loadings.
 *
 * As a linear technique, PCA is built for several purposes: first, it enables us to
 * decorrelate the original variables; second, to carry out data compression,
 * where we pay decreasing attention to the numerical accuracy by which we
 * encode the sequence of principal components; third, to reconstruct the
 * original input data using a reduced number of variables according to a
 * least-squares criterion; and fourth, to identify potential clusters in the data.
 *
 * In certain applications, PCA can be misleading. PCA is heavily influenced
 * when there are outliers in the data. In other situations, the linearity
 * of PCA may be an obstacle to successful data reduction and compression.
 *
 * @param data training data. If the sample size
 *             is larger than the data dimension and cor = false, SVD is employed for
 *             efficiency. Otherwise, eigen decomposition on covariance or correlation
 *             matrix is performed.
 * @param cor true if use correlation matrix instead of covariance matrix if ture.
 */
fun pca(data: Array<DoubleArray>, cor: Boolean = false): PCA {
    return if (cor) PCA.cor(data) else PCA.fit(data)
}

/**
 * Probabilistic principal component analysis. PPCA is a simplified factor analysis
 * that employs a latent variable model with linear relationship:
 * ```
 *     y &sim; W * x + &mu; + &epsilon;
 * ```
 * where latent variables x &sim; N(0, I), error (or noise) &epsilon; &sim; N(0, &Psi;),
 * and &mu; is the location term (mean). In PPCA, an isotropic noise model is used,
 * i.e., noise variances constrained to be equal (&Psi;<sub>i</sub> = &sigma;<sup>2</sup>).
 * A close form of estimation of above parameters can be obtained
 * by maximum likelihood method.
 *
 * ====References:====
 *  - Michael E. Tipping and Christopher M. Bishop. Probabilistic Principal Component Analysis. Journal of the Royal Statistical Society. Series B (Statistical Methodology) 61(3):611-622, 1999.
 *
 * @param data training data.
 * @param k the number of principal component to learn.
 */
fun ppca(data: Array<DoubleArray>, k: Int): ProbabilisticPCA  {
    return ProbabilisticPCA.fit(data, k)
}

/**
 * Kernel principal component analysis. Kernel PCA is an extension of
 * principal component analysis (PCA) using techniques of kernel methods.
 * Using a kernel, the originally linear operations of PCA are done in a
 * reproducing kernel Hilbert space with a non-linear mapping.
 *
 * In practice, a large data set leads to a large Kernel/Gram matrix K, and
 * storing K may become a problem. One way to deal with this is to perform
 * clustering on your large dataset, and populate the kernel with the means
 * of those clusters. Since even this method may yield a relatively large K,
 * it is common to compute only the top P eigenvalues and eigenvectors of K.
 *
 * Kernel PCA with an isotropic kernel function is closely related to metric MDS.
 * Carrying out metric MDS on the kernel matrix K produces an equivalent configuration
 * of points as the distance (2(1 - K(x<sub>i</sub>, x<sub>j</sub>)))<sup>1/2</sup>
 * computed in feature space.
 *
 * Kernel PCA also has close connections with Isomap, LLE, and Laplacian eigenmaps.
 *
 * ====References:====
 *  - Bernhard Scholkopf, Alexander Smola, and Klaus-Robert Muller. Nonlinear Component Analysis as a Kernel Eigenvalue Problem. Neural Computation, 1998.
 *
 * @param data training data.
 * @param kernel Mercer kernel to compute kernel matrix.
 * @param k choose top k principal components used for projection.
 * @param threshold only principal components with eigenvalues larger than
 *                  the given threshold will be kept.
 */
fun <T> kpca(data: Array<T>, kernel: MercerKernel<T>, k: Int, threshold: Double = 0.0001): KPCA<T> {
    return KPCA.fit(data, kernel, k, threshold)
}

/**
 * Generalized Hebbian Algorithm. GHA is a linear feed-forward neural
 * network model for unsupervised learning with applications primarily in
 * principal components analysis. It is single-layer process -- that is, a
 * synaptic weight changes only depending on the response of the inputs and
 * outputs of that layer.
 *
 * It guarantees that GHA finds the first k eigenvectors of the covariance matrix,
 * assuming that the associated eigenvalues are distinct. The convergence theorem
 * is forumulated in terms of a time-varying learning rate &eta;. In practice, the
 * learning rate &eta; is chosen to be a small constant, in which case convergence is
 * guaranteed with mean-squared error in synaptic weights of order &eta;.
 *
 * It also has a simple and predictable trade-off between learning speed and
 * accuracy of convergence as set by the learning rate parameter &eta;. It was
 * shown that a larger learning rate &eta; leads to faster convergence
 * and larger asymptotic mean-square error, which is intuitively satisfying.
 *
 * Compared to regular batch PCA algorithm based on eigen decomposition, GHA is
 * an adaptive method and works with an arbitrarily large sample size. The storage
 * requirement is modest. Another attractive feature is that, in a nonstationary
 * environment, it has an inherent ability to track gradual changes in the
 * optimal solution in an inexpensive way.
 *
 * ====References:====
 *  - Terence D. Sanger. Optimal unsupervised learning in a single-layer linear feedforward neural network. Neural Networks 2(6):459-473, 1989.
 *  - Simon Haykin. Neural Networks: A Comprehensive Foundation (2 ed.). 1998.
 *
 * @param data training data.
 * @param w the initial projection matrix.
 * @param r the learning rate.
 */
fun gha(data: Array<DoubleArray>, w: Array<DoubleArray>, r: TimeFunction): GHA {
    val model = GHA(w, r)
    for (x in data) model.update(x)
    return model
}

/**
 * Generalized Hebbian Algorithm with random initial projection matrix.
 *
 * @param data training data.
 * @param k the dimension of feature space.
 * @param r the learning rate.
 */
fun gha(data: Array<DoubleArray>, k: Int, r: TimeFunction): GHA {
    val model = GHA(data[0].size, k, r)
    for (x in data) model.update(x)
    return model
}
