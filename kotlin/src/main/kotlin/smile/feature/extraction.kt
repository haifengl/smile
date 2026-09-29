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
package smile.feature.extraction

import smile.data.DataFrame
import smile.data.Tuple
import smile.data.type.StructType
import smile.math.kernel.MercerKernel
import smile.manifold.KPCA
import smile.util.SparseArray
import smile.util.function.TimeFunction

/**
 * Principal component analysis on a data frame.
 *
 * @param data training data.
 * @param cor true to use correlation matrix instead of covariance matrix.
 * @param columns the column names to use. If empty, all columns are used.
 * @return the PCA model.
 */
fun pca(data: DataFrame, cor: Boolean = false, vararg columns: String): PCA {
    return if (cor) PCA.cor(data, *columns) else PCA.fit(data, *columns)
}

/**
 * Principal component analysis on a raw matrix.
 *
 * @param data training data of which each row is a sample.
 * @param cor true to use correlation matrix instead of covariance matrix.
 * @param columns optional column names for projected data frame.
 * @return the PCA model.
 */
fun pca(data: Array<DoubleArray>, cor: Boolean = false, vararg columns: String): PCA {
    return if (cor) PCA.cor(data, *columns) else PCA.fit(data, *columns)
}

/**
 * Probabilistic principal component analysis on a data frame.
 *
 * @param data training data.
 * @param k the number of principal components to learn.
 * @param columns the column names to use. If empty, all columns are used.
 * @return the ProbabilisticPCA model.
 */
fun ppca(data: DataFrame, k: Int, vararg columns: String): ProbabilisticPCA {
    return ProbabilisticPCA.fit(data, k, *columns)
}

/**
 * Probabilistic principal component analysis on a raw matrix.
 *
 * @param data training data of which each row is a sample.
 * @param k the number of principal components to learn.
 * @param columns optional column names for projected data frame.
 * @return the ProbabilisticPCA model.
 */
fun ppca(data: Array<DoubleArray>, k: Int, vararg columns: String): ProbabilisticPCA {
    return ProbabilisticPCA.fit(data, k, *columns)
}

/**
 * Kernel principal component analysis on a data frame.
 *
 * @param data training data.
 * @param kernel Mercer kernel to compute kernel matrix.
 * @param k choose top k principal components used for projection.
 * @param threshold only principal components with eigenvalues larger than the threshold will be kept.
 * @param columns the column names to use. If empty, all columns are used.
 * @return the KernelPCA model.
 */
fun kpca(data: DataFrame, kernel: MercerKernel<DoubleArray>, k: Int, threshold: Double = 0.0001, vararg columns: String): KernelPCA {
    return KernelPCA.fit(data, kernel, KPCA.Options(k, threshold), *columns)
}

/**
 * Generalized Hebbian Algorithm with initial projection matrix.
 *
 * @param data training data.
 * @param w the initial projection matrix.
 * @param r the learning rate schedule.
 * @param columns optional column names for projected data frame.
 * @return the GHA model.
 */
fun gha(data: Array<DoubleArray>, w: Array<DoubleArray>, r: TimeFunction = TimeFunction.linear(0.01, 10_000.0, 0.001), vararg columns: String): GHA {
    val model = GHA(w, r, *columns)
    for (x in data) model.update(x)
    return model
}

/**
 * Generalized Hebbian Algorithm with initial projection matrix and constant learning rate.
 *
 * @param data training data.
 * @param w the initial projection matrix.
 * @param r the constant learning rate.
 * @param columns optional column names for projected data frame.
 * @return the GHA model.
 */
fun gha(data: Array<DoubleArray>, w: Array<DoubleArray>, r: Double, vararg columns: String): GHA {
    return gha(data, w, TimeFunction.constant(r), *columns)
}

/**
 * Generalized Hebbian Algorithm with random initial projection matrix.
 *
 * @param data training data.
 * @param k the dimension of feature space.
 * @param r the learning rate schedule.
 * @param columns optional column names for projected data frame.
 * @return the GHA model.
 */
fun gha(data: Array<DoubleArray>, k: Int, r: TimeFunction = TimeFunction.linear(0.01, 10_000.0, 0.001), vararg columns: String): GHA {
    val model = GHA(data[0].size, k, r, *columns)
    for (x in data) model.update(x)
    return model
}

/**
 * Generalized Hebbian Algorithm with random initial projection matrix and constant learning rate.
 *
 * @param data training data.
 * @param k the dimension of feature space.
 * @param r the constant learning rate.
 * @param columns optional column names for projected data frame.
 * @return the GHA model.
 */
fun gha(data: Array<DoubleArray>, k: Int, r: Double, vararg columns: String): GHA {
    return gha(data, k, TimeFunction.constant(r), *columns)
}

/**
 * Random projection for dimensionality reduction.
 *
 * @param n the dimension of input space.
 * @param p the dimension of feature space.
 * @param sparse true to generate sparse random projection, false for Gaussian random projection.
 * @param columns optional column names for projected data frame.
 * @return the RandomProjection model.
 */
fun randomProjection(n: Int, p: Int, sparse: Boolean = false, vararg columns: String): RandomProjection {
    return if (sparse) RandomProjection.sparse(n, p, *columns) else RandomProjection.of(n, p, *columns)
}

/**
 * Encodes categorical features using sparse one-hot scheme.
 *
 * @param schema the data frame schema.
 * @param columns the column names of categorical variables. If empty, all categorical columns will be used.
 * @return the BinaryEncoder.
 */
fun binaryEncoder(schema: StructType, vararg columns: String): BinaryEncoder {
    return BinaryEncoder(schema, *columns)
}

/**
 * Creates a binary encoder for this DataFrame.
 *
 * @param columns the column names of categorical variables. If empty, all categorical columns will be used.
 * @return the BinaryEncoder.
 */
fun DataFrame.binaryEncoder(vararg columns: String): BinaryEncoder {
    return BinaryEncoder(schema(), *columns)
}

/**
 * Encodes numeric and categorical features into sparse array with one-hot encoding of categorical variables.
 *
 * @param schema the data frame schema.
 * @param columns the column names to encode. If empty, all numeric and categorical columns will be used.
 * @return the SparseEncoder.
 */
fun sparseEncoder(schema: StructType, vararg columns: String): SparseEncoder {
    return SparseEncoder(schema, *columns)
}

/**
 * Creates a sparse encoder for this DataFrame.
 *
 * @param columns the column names to encode. If empty, all numeric and categorical columns will be used.
 * @return the SparseEncoder.
 */
fun DataFrame.sparseEncoder(vararg columns: String): SparseEncoder {
    return SparseEncoder(schema(), *columns)
}

/**
 * Feature hashing (the hashing trick) vectorizer.
 *
 * @param numFeatures the number of features in the output space.
 * @param alternateSign when true, an alternating sign is added to approximately conserve inner product.
 * @param tokenizer the text tokenizer lambda.
 * @return the HashEncoder.
 */
fun hashEncoder(numFeatures: Int, alternateSign: Boolean = true, tokenizer: (String) -> Array<String>): HashEncoder {
    return HashEncoder(tokenizer, numFeatures, alternateSign)
}

/**
 * Bag-of-words feature transform.
 *
 * @param words the feature words.
 * @param binary true for presence/absence, false for word counts.
 * @param columns the input text column names in a DataFrame (or null if applied directly on text).
 * @param tokenizer the text tokenizer lambda.
 * @return the BagOfWords transform.
 */
fun bagOfWords(words: Array<String>, binary: Boolean = false, columns: Array<String>? = null, tokenizer: (String) -> Array<String>): BagOfWords {
    return BagOfWords(columns, tokenizer, words, binary)
}

/**
 * Learns a bag-of-words model of top-k frequent tokens in DataFrame text columns.
 *
 * @param data training data.
 * @param k the maximum vocabulary size.
 * @param columns the text column names.
 * @param tokenizer the text tokenizer lambda.
 * @return the BagOfWords transform.
 */
fun bagOfWords(data: DataFrame, k: Int, vararg columns: String, tokenizer: (String) -> Array<String>): BagOfWords {
    return BagOfWords.fit(data, tokenizer, k, *columns)
}
