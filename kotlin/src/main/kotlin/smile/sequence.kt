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
package smile.sequence

import smile.data.Tuple
import smile.tensor.DenseMatrix

/**
 * Creates a first-order Hidden Markov Model from initial, transition, and emission probabilities.
 *
 * @param pi the initial state probabilities.
 * @param a the state transition probability matrix, where `a[i][j]` is `P(s_j | s_i)`.
 * @param b the symbol emission probability matrix, where `b[i][j]` is `P(o_j | s_i)`.
 * @return the Hidden Markov Model.
 */
fun hmm(pi: DoubleArray, a: DenseMatrix, b: DenseMatrix): HMM {
    return HMM(pi, a, b)
}

/**
 * Creates a first-order Hidden Markov Model from initial, transition, and emission probability arrays.
 *
 * @param pi the initial state probabilities.
 * @param a the state transition probability matrix, where `a[i][j]` is `P(s_j | s_i)`.
 * @param b the symbol emission probability matrix, where `b[i][j]` is `P(o_j | s_i)`.
 * @return the Hidden Markov Model.
 */
fun hmm(pi: DoubleArray, a: Array<DoubleArray>, b: Array<DoubleArray>): HMM {
    return HMM(pi, DenseMatrix.of(a), DenseMatrix.of(b))
}

/**
 * Fits a first-order Hidden Markov Model by maximum likelihood estimation.
 *
 * @param observations the observation sequences, of which symbols take
 *                     values in `[0, n)`, where `n` is the number of unique symbols.
 * @param labels the state labels of observations, of which states take
 *               values in `[0, p)`, where `p` is the number of hidden states.
 * @return the fitted Hidden Markov Model.
 */
fun hmm(observations: Array<IntArray>, labels: Array<IntArray>): HMM {
    return HMM.fit(observations, labels)
}

/**
 * Fits a first-order Hidden Markov Model sequence labeler for generic observation objects.
 *
 * @param observations the observation sequences.
 * @param labels the state labels of observations, of which states take
 *               values in `[0, p)`, where `p` is the number of hidden states.
 * @param ordinal a lambda returning the ordinal numbers of symbols in `[0, n)`.
 * @param T the data type of observations.
 * @return the HMM sequence labeler.
 */
fun <T> hmm(observations: Array<Array<T>>, labels: Array<IntArray>, ordinal: (T) -> Int): HMMLabeler<T> {
    return HMMLabeler.fit(observations, labels, ordinal)
}

/**
 * Fits a first-order linear conditional random field.
 *
 * @param sequences the observation attribute sequences.
 * @param labels sequence labels.
 * @param ntrees the number of trees/iterations.
 * @param maxDepth the maximum depth of the tree.
 * @param maxNodes the maximum number of leaf nodes in the tree.
 * @param nodeSize the number of instances in a node below which the tree will not split.
 * @param shrinkage the shrinkage parameter in `(0, 1]` controlling the learning rate.
 * @return the CRF model.
 */
fun crf(
    sequences: Array<Array<Tuple>>,
    labels: Array<IntArray>,
    ntrees: Int = 100,
    maxDepth: Int = 20,
    maxNodes: Int = 100,
    nodeSize: Int = 5,
    shrinkage: Double = 1.0
): CRF {
    return CRF.fit(sequences, labels, CRF.Options(ntrees, maxDepth, maxNodes, nodeSize, shrinkage))
}

/**
 * Fits a first-order linear conditional random field with given hyperparameters.
 *
 * @param sequences the observation attribute sequences.
 * @param labels sequence labels.
 * @param options the CRF hyperparameters.
 * @return the CRF model.
 */
fun crf(sequences: Array<Array<Tuple>>, labels: Array<IntArray>, options: CRF.Options): CRF {
    return CRF.fit(sequences, labels, options)
}

/**
 * Fits a first-order linear conditional random field labeler for generic observation objects.
 *
 * @param sequences the observation sequences.
 * @param labels sequence labels.
 * @param ntrees the number of trees/iterations.
 * @param maxDepth the maximum depth of the tree.
 * @param maxNodes the maximum number of leaf nodes in the tree.
 * @param nodeSize the number of instances in a node below which the tree will not split.
 * @param shrinkage the shrinkage parameter in `(0, 1]` controlling the learning rate.
 * @param features the feature extraction function converting each observation item to a [Tuple].
 * @param T the data type of observations.
 * @return the CRF sequence labeler.
 */
fun <T> crf(
    sequences: Array<Array<T>>,
    labels: Array<IntArray>,
    ntrees: Int = 100,
    maxDepth: Int = 20,
    maxNodes: Int = 100,
    nodeSize: Int = 5,
    shrinkage: Double = 1.0,
    features: (T) -> Tuple
): CRFLabeler<T> {
    return CRFLabeler.fit(sequences, labels, features, CRF.Options(ntrees, maxDepth, maxNodes, nodeSize, shrinkage))
}

/**
 * Fits a first-order linear conditional random field labeler for generic observation objects with given hyperparameters.
 *
 * @param sequences the observation sequences.
 * @param labels sequence labels.
 * @param options the CRF hyperparameters.
 * @param features the feature extraction function converting each observation item to a [Tuple].
 * @param T the data type of observations.
 * @return the CRF sequence labeler.
 */
fun <T> crf(
    sequences: Array<Array<T>>,
    labels: Array<IntArray>,
    options: CRF.Options,
    features: (T) -> Tuple
): CRFLabeler<T> {
    return CRFLabeler.fit(sequences, labels, features, options)
}

/**
 * Fits a first-order linear conditional random field labeler for generic observation objects.
 * This is an alias for [crf] matching the Scala API convention.
 *
 * @param sequences the observation sequences.
 * @param labels sequence labels.
 * @param ntrees the number of trees/iterations.
 * @param maxDepth the maximum depth of the tree.
 * @param maxNodes the maximum number of leaf nodes in the tree.
 * @param nodeSize the number of instances in a node below which the tree will not split.
 * @param shrinkage the shrinkage parameter in `(0, 1]` controlling the learning rate.
 * @param features the feature extraction function converting each observation item to a [Tuple].
 * @param T the data type of observations.
 * @return the CRF sequence labeler.
 */
fun <T> gcrf(
    sequences: Array<Array<T>>,
    labels: Array<IntArray>,
    ntrees: Int = 100,
    maxDepth: Int = 20,
    maxNodes: Int = 100,
    nodeSize: Int = 5,
    shrinkage: Double = 1.0,
    features: (T) -> Tuple
): CRFLabeler<T> {
    return crf(sequences, labels, ntrees, maxDepth, maxNodes, nodeSize, shrinkage, features)
}

/**
 * Fits a first-order linear conditional random field labeler for generic observation objects with given hyperparameters.
 * This is an alias for [crf] matching the Scala API convention.
 *
 * @param sequences the observation sequences.
 * @param labels sequence labels.
 * @param options the CRF hyperparameters.
 * @param features the feature extraction function converting each observation item to a [Tuple].
 * @param T the data type of observations.
 * @return the CRF sequence labeler.
 */
fun <T> gcrf(
    sequences: Array<Array<T>>,
    labels: Array<IntArray>,
    options: CRF.Options,
    features: (T) -> Tuple
): CRFLabeler<T> {
    return crf(sequences, labels, options, features)
}
