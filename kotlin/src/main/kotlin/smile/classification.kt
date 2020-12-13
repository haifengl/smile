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

package smile.classification

import java.util.Optional
import java.util.stream.LongStream
import smile.base.cart.SplitRule
import smile.base.mlp.LayerBuilder
import smile.base.rbf.RBF
import smile.data.DataFrame
import smile.data.formula.Formula
import smile.math.MathEx
import smile.math.TimeFunction
import smile.math.distance.Distance
import smile.math.kernel.MercerKernel
import smile.neighbor.KNNSearch
import smile.stat.distribution.Distribution

/**
 * K-nearest neighbor classifier.
 * The k-nearest neighbor algorithm (k-NN) is
 * a method for classifying objects by a majority vote of its neighbors,
 * with the object being assigned to the class most common amongst its k
 * nearest neighbors (k is a positive integer, typically small).
 * k-NN is a type of instance-based learning, or lazy learning where the
 * function is only approximated locally and all computation
 * is deferred until classification.
 *
 * The best choice of k depends upon the data; generally, larger values of
 * k reduce the effect of noise on the classification, but make boundaries
 * between classes less distinct. A good k can be selected by various
 * heuristic techniques, e.g. cross-validation. In binary problems, it is
 * helpful to choose k to be an odd number as this avoids tied votes.
 *
 * A drawback to the basic majority voting classification is that the classes
 * with the more frequent instances tend to dominate the prediction of the
 * new object, as they tend to come up in the k nearest neighbors when
 * the neighbors are computed due to their large number. One way to overcome
 * this problem is to weight the classification taking into account the
 * distance from the test point to each of its k nearest neighbors.
 *
 * Often, the classification accuracy of k-NN can be improved significantly
 * if the distance metric is learned with specialized algorithms such as
 * Large Margin Nearest Neighbor or Neighborhood Components Analysis.
 *
 * Nearest neighbor rules in effect compute the decision boundary in an
 * implicit manner. It is also possible to compute the decision boundary
 * itself explicitly, and to do so in an efficient manner so that the
 * computational complexity is a function of the boundary complexity.
 *
 * The nearest neighbor algorithm has some strong consistency results. As
 * the amount of data approaches infinity, the algorithm is guaranteed to
 * yield an error rate no worse than twice the Bayes error rate (the minimum
 * achievable error rate given the distribution of the data). k-NN is
 * guaranteed to approach the Bayes error rate, for some value of k (where k
 * increases as a function of the number of data points).
 *
 * @param x k-nearest neighbor search data structure of training instances.
 * @param y training labels in [0, c), where c is the number of classes.
 * @param k the number of neighbors for classification.
 */
fun <T> knn(x: KNNSearch<T, T>, y: IntArray, k: Int): KNN<T> {
    return KNN(x, y, k)
}

/**
 * K-nearest neighbor classifier.
 *
 * @param x training samples.
 * @param y training labels in [0, c), where c is the number of classes.
 * @param distance the distance measure for finding nearest neighbors.
 * @param k the number of neighbors for classification.
 */
fun <T> knn(x: Array<T>, y: IntArray, k: Int, distance: Distance<T>): KNN<T> {
    return KNN.fit(x, y, k, distance)
}

/**
 * K-nearest neighbor classifier with Euclidean distance as the similarity measure.
 *
 * @param x training samples.
 * @param y training labels in [0, c), where c is the number of classes.
 * @param k the number of neighbors for classification.
 */
fun knn(x: Array<DoubleArray>, y: IntArray, k: Int): KNN<DoubleArray> {
    return KNN.fit(x, y, k)
}

/**
 * Logistic regression.
 * Logistic regression (logit model) is a generalized
 * linear model used for binomial regression. Logistic regression applies
 * maximum likelihood estimation after transforming the dependent into
 * a logit variable. A logit is the natural log of the odds of the dependent
 * equaling a certain value or not (usually 1 in binary logistic models,
 * the highest value in multinomial models). In this way, logistic regression
 * estimates the odds of a certain event (value) occurring.
 *
 * Goodness-of-fit tests such as the likelihood ratio test are available
 * as indicators of model appropriateness, as is the Wald statistic to test
 * the significance of individual independent variables.
 *
 * Logistic regression has many analogies to ordinary least squares (OLS)
 * regression. Unlike OLS regression, however, logistic regression does not
 * assume linearity of relationship between the raw values of the independent
 * variables and the dependent, does not require normally distributed variables,
 * does not assume homoscedasticity, and in general has less stringent
 * requirements.
 *
 * Compared with linear discriminant analysis, logistic regression has several
 * advantages:
 *
 *  - It is more robust: the independent variables don't have to be normally
 *    distributed, or have equal variance in each group
 *
 *  - It does not assume a linear relationship between the independent
 *    variables and dependent variable.
 *
 *  - It may handle nonlinear effects since one can add explicit interaction
 *    and power terms.
 *
 * However, it requires much more data to achieve stable, meaningful results.
 *
 * Logistic regression also has strong connections with neural network and
 * maximum entropy modeling. For example, binary logistic regression is
 * equivalent to a one-layer, single-output neural network with a logistic
 * activation function trained under log loss. Similarly, multinomial logistic
 * regression is equivalent to a one-layer, softmax-output neural network.
 *
 * Logistic regression estimation also obeys the maximum entropy principle, and
 * thus logistic regression is sometimes called "maximum entropy modeling",
 * and the resulting classifier the "maximum entropy classifier".
 *
 * @param x training samples.
 * @param y training labels in [0, k), where k is the number of classes.
 * @param lambda &lambda; &gt; 0 gives a "regularized" estimate of linear
 *               weights which often has superior generalization performance, especially
 *               when the dimensionality is high.
 * @param tol the tolerance for stopping iterations.
 * @param maxIter the maximum number of iterations.
 *
 * @return Logistic regression model.
 */
fun logit(x: Array<DoubleArray>, y: IntArray, lambda: Double = 0.0, tol: Double = 1E-5, maxIter: Int = 500): LogisticRegression {
    return LogisticRegression.fit(x, y, lambda, tol, maxIter)
}

/**
 * Maximum entropy classifier.
 * Maximum entropy is a technique for learning
 * probability distributions from data. In maximum entropy models, the
 * observed data itself is assumed to be the testable information. Maximum
 * entropy models don't assume anything about the probability distribution
 * other than what have been observed and always choose the most uniform
 * distribution subject to the observed constraints.
 *
 * Basically, maximum entropy classifier is another name of multinomial logistic
 * regression applied to categorical independent variables, which are
 * converted to binary dummy variables. Maximum entropy models are widely
 * used in natural language processing.  Here, we provide an implementation
 * which assumes that binary features are stored in a sparse array, of which
 * entries are the indices of nonzero features.
 *
 * ====References:====
 *  - A. L. Berger, S. D. Pietra, and V. J. D. Pietra. A maximum entropy approach to natural language processing. Computational Linguistics 22(1):39-71, 1996.
 *
 * @param x training samples. Each sample is represented by a set of sparse
 *          binary features. The features are stored in an integer array, of which
 *          are the indices of nonzero features.
 * @param y training labels in [0, k), where k is the number of classes.
 * @param p the dimension of feature space.
 * @param lambda &lambda; &gt; 0 gives a "regularized" estimate of linear
 *               weights which often has superior generalization performance, especially
 *               when the dimensionality is high.
 * @param tol tolerance for stopping iterations.
 * @param maxIter maximum number of iterations.
 * @return Maximum entropy model.
 */
fun maxent(x: Array<IntArray>, y: IntArray, p: Int, lambda: Double = 0.1, tol: Double = 1E-5, maxIter: Int = 500): Maxent {
    return Maxent.fit(p, x, y, lambda, tol, maxIter)
}

/**
 * Multilayer perceptron neural network.
 * An MLP consists of several layers of nodes, interconnected through weighted
 * acyclic arcs from each preceding layer to the following, without lateral or
 * feedback connections. Each node calculates a transformed weighted linear
 * combination of its inputs (output activations from the preceding layer), with
 * one of the weights acting as a trainable bias connected to a constant input.
 * The transformation, called activation function, is a bounded non-decreasing
 * (non-linear) function, such as the sigmoid functions (ranges from 0 to 1).
 * Another popular activation function is hyperbolic tangent which is actually
 * equivalent to the sigmoid function in shape but ranges from -1 to 1.
 * More specialized activation functions include radial basis functions which
 * are used in RBF networks.
 *
 * The representational capabilities of a MLP are determined by the range of
 * mappings it may implement through weight variation. Single layer perceptrons
 * are capable of solving only linearly separable problems. With the sigmoid
 * function as activation function, the single-layer network is identical
 * to the logistic regression model.
 *
 * The universal approximation theorem for neural networks states that every
 * continuous function that maps intervals of real numbers to some output
 * interval of real numbers can be approximated arbitrarily closely by a
 * multi-layer perceptron with just one hidden layer. This result holds only
 * for restricted classes of activation functions, which are extremely complex
 * and NOT smooth for subtle mathematical reasons. On the other hand, smoothness
 * is important for gradient descent learning. Besides, the proof is not
 * constructive regarding the number of neurons required or the settings of
 * the weights. Therefore, complex systems will have more layers of neurons
 * with some having increased layers of input neurons and output neurons
 * in practice.
 *
 * The most popular algorithm to train MLPs is back-propagation, which is a
 * gradient descent method. Based on chain rule, the algorithm propagates the
 * error back through the network and adjusts the weights of each connection in
 * order to reduce the value of the error function by some small amount.
 * For this reason, back-propagation can only be applied on networks with
 * differentiable activation functions.
 *
 * During error back propagation, we usually times the gradient with a small
 * number &eta;, called learning rate, which is carefully selected to ensure
 * that the network converges to a local minimum of the error function
 * fast enough, without producing oscillations. One way to avoid oscillation
 * at large &eta;, is to make the change in weight dependent on the past weight
 * change by adding a momentum term.
 *
 * Although the back-propagation algorithm may performs gradient
 * descent on the total error of all instances in a batch way,
 * the learning rule is often applied to each instance separately in an online
 * way or stochastic way. There exists empirical indication that the stochastic
 * way results in faster convergence.
 *
 * In practice, the problem of over-fitting has emerged. This arises in
 * convoluted or over-specified systems when the capacity of the network
 * significantly exceeds the needed free parameters. There are two general
 * approaches for avoiding this problem: The first is to use cross-validation
 * and similar techniques to check for the presence of over-fitting and
 * optimally select hyper-parameters such as to minimize the generalization
 * error. The second is to use some form of regularization, which emerges
 * naturally in a Bayesian framework, where the regularization can be
 * performed by selecting a larger prior probability over simpler models;
 * but also in statistical learning theory, where the goal is to minimize over
 * the "empirical risk" and the "structural risk".
 *
 * For neural networks, the input patterns usually should be scaled/standardized.
 * Commonly, each input variable is scaled into interval `[0, 1]` or to have
 * mean 0 and standard deviation 1.
 *
 * For penalty functions and output units, the following natural pairings are
 * recommended:
 *
 *  - linear output units and a least squares penalty function.
 *  - a two-class cross-entropy penalty function and a logistic
 *    activation function.
 *  - a multi-class cross-entropy penalty function and a softmax
 *    activation function.
 *
 * By assigning a softmax activation function on the output layer of
 * the neural network for categorical target variables, the outputs
 * can be interpreted as posterior probabilities, which are very useful.
 *
 * @param x training samples.
 * @param y training labels in [0, k), where k is the number of classes.
 * @param builders the builders of layers from bottom to top.
 * @param epochs the number of epochs of stochastic learning.
 * @param learningRate the learning rate.
 * @param momentum the momentum factor.
 * @param weightDecay the weight decay for regularization.
 * @param rho The RMSProp discounting factor for the history/coming gradient.
 * @param epsilon A small constant for RMSProp numerical stability.
 */
fun mlp(x: Array<DoubleArray>, y: IntArray, builders: Array<LayerBuilder>, epochs: Int = 10,
       learningRate: TimeFunction = TimeFunction.linear(0.01, 10000.0, 0.001),
       momentum: TimeFunction = TimeFunction.constant(0.0),
       weightDecay: Double = 0.0, rho: Double = 0.0, epsilon: Double = 1E-7): MLP {
    val net = MLP(x[0].size, *builders)
    net.setLearningRate(learningRate)
    net.setMomentum(momentum)
    net.setWeightDecay(weightDecay)
    net.setRMSProp(rho, epsilon) 
    for (i in 1 .. epochs) net.update(x, y)
    return net
}

/**
 * Radial basis function networks.
 * A radial basis function network is an
 * artificial neural network that uses radial basis functions as activation
 * functions. It is a linear combination of radial basis functions. They are
 * used in function approximation, time series prediction, and control.
 *
 * In its basic form, radial basis function network is in the form
 *
 * y(x) = &Sigma; w<sub>i</sub> &phi;(||x-c<sub>i</sub>||)
 *
 * where the approximating function y(x) is represented as a sum of N radial
 * basis functions &phi;, each associated with a different center c<sub>i</sub>,
 * and weighted by an appropriate coefficient w<sub>i</sub>. For distance,
 * one usually chooses Euclidean distance. The weights w<sub>i</sub> can
 * be estimated using the matrix methods of linear least squares, because
 * the approximating function is linear in the weights.
 *
 * The centers c<sub>i</sub> can be randomly selected from training data,
 * or learned by some clustering method (e.g. k-means), or learned together
 * with weight parameters undergo a supervised learning processing
 * (e.g. error-correction learning).
 *
 * The popular choices for &phi; comprise the Gaussian function and the so
 * called thin plate splines. The advantage of the thin plate splines is that
 * their conditioning is invariant under scalings. Gaussian, multi-quadric
 * and inverse multi-quadric are infinitely smooth and and involve a scale
 * or shape parameter, r<sub><small>0</small></sub> &gt; 0. Decreasing
 * r<sub><small>0</small></sub> tends to flatten the basis function. For a
 * given function, the quality of approximation may strongly depend on this
 * parameter. In particular, increasing r<sub><small>0</small></sub> has the
 * effect of better conditioning (the separation distance of the scaled points
 * increases).
 *
 * A variant on RBF networks is normalized radial basis function (NRBF)
 * networks, in which we require the sum of the basis functions to be unity.
 * NRBF arises more naturally from a Bayesian statistical perspective. However,
 * there is no evidence that either the NRBF method is consistently superior
 * to the RBF method, or vice versa.
 *
 * SVMs with Gaussian kernel have similar structure as RBF networks with
 * Gaussian radial basis functions. However, the SVM approach "automatically"
 * solves the network complexity problem since the size of the hidden layer
 * is obtained as the result of the QP procedure. Hidden neurons and
 * support vectors correspond to each other, so the center problems of
 * the RBF network is also solved, as the support vectors serve as the
 * basis function centers. It was reported that with similar number of support
 * vectors/centers, SVM shows better generalization performance than RBF
 * network when the training data size is relatively small. On the other hand,
 * RBF network gives better generalization performance than SVM on large
 * training data.
 *
 * ====References:====
 *  - Simon Haykin. Neural Networks: A Comprehensive Foundation (2nd edition). 1999.
 *  - T. Poggio and F. Girosi. Networks for approximation and learning. Proc. IEEE 78(9):1484-1487, 1990.
 *  - Nabil Benoudjit and Michel Verleysen. On the kernel widths in radial-basis function networks. Neural Process, 2003.
 *
 * @param x training samples.
 * @param y training labels in [0, k), where k is the number of classes.
 * @param neurons the radial basis functions.
 * @param normalized train a normalized RBF network or not.
 */
fun <T> rbfnet(x: Array<T>, y: IntArray, neurons: Array<RBF<T>>, normalized: Boolean = false): RBFNetwork<T> {
    return RBFNetwork.fit(x, y, neurons, normalized)
}

/** Trains a Gaussian RBF network with k-means. */
fun rbfnet(x: Array<DoubleArray>, y: IntArray, k: Int, normalized: Boolean = false): RBFNetwork<DoubleArray> {
    val neurons = RBF.fit(x, k)
    return RBFNetwork.fit(x, y, neurons, normalized)
}

/**
 * Support vector machines for classification. The basic support vector machine
 * is a binary linear classifier which chooses the hyperplane that represents
 * the largest separation, or margin, between the two classes. If such a
 * hyperplane exists, it is known as the maximum-margin hyperplane and the
 * linear classifier it defines is known as a maximum margin classifier.
 *
 * If there exists no hyperplane that can perfectly split the positive and
 * negative instances, the soft margin method will choose a hyperplane
 * that splits the instances as cleanly as possible, while still maximizing
 * the distance to the nearest cleanly split instances.
 *
 * The nonlinear SVMs are created by applying the kernel trick to
 * maximum-margin hyperplanes. The resulting algorithm is formally similar,
 * except that every dot product is replaced by a nonlinear kernel function.
 * This allows the algorithm to fit the maximum-margin hyperplane in a
 * transformed feature space. The transformation may be nonlinear and
 * the transformed space be high dimensional. For example, the feature space
 * corresponding Gaussian kernel is a Hilbert space of infinite dimension.
 * Thus though the classifier is a hyperplane in the high-dimensional feature
 * space, it may be nonlinear in the original input space. Maximum margin
 * classifiers are well regularized, so the infinite dimension does not spoil
 * the results.
 *
 * The effectiveness of SVM depends on the selection of kernel, the kernel's
 * parameters, and soft margin parameter C. Given a kernel, best combination
 * of C and kernel's parameters is often selected by a grid-search with
 * cross validation.
 *
 * The dominant approach for creating multi-class SVMs is to reduce the
 * single multi-class problem into multiple binary classification problems.
 * Common methods for such reduction is to build binary classifiers which
 * distinguish between (i) one of the labels to the rest (one-versus-all)
 * or (ii) between every pair of classes (one-versus-one). Classification
 * of new instances for one-versus-all case is done by a winner-takes-all
 * strategy, in which the classifier with the highest output function assigns
 * the class. For the one-versus-one approach, classification
 * is done by a max-wins voting strategy, in which every classifier assigns
 * the instance to one of the two classes, then the vote for the assigned
 * class is increased by one vote, and finally the class with most votes
 * determines the instance classification.
 *
 * @param x training data
 * @param y training labels
 * @param kernel Mercer kernel
 * @param C the regularization parameter
 * @param tol the tolerance of convergence test.
 * @tparam T the data type
 *
 * @return SVM model.
 */
fun <T> svm(x: Array<T>, y: IntArray, kernel: MercerKernel<T>, C: Double, tol: Double = 1E-3): SVM<T> {
    return SVM.fit(x, y, kernel, C, tol)
}

/**
 * Decision tree. A classification/regression tree can be learned by
 * splitting the training set into subsets based on an attribute value
 * test. This process is repeated on each derived subset in a recursive
 * manner called recursive partitioning. The recursion is completed when
 * the subset at a node all has the same value of the target variable,
 * or when splitting no longer adds value to the predictions.
 *
 * The algorithms that are used for constructing decision trees usually
 * work top-down by choosing a variable at each step that is the next best
 * variable to use in splitting the set of items. "Best" is defined by how
 * well the variable splits the set into homogeneous subsets that have
 * the same value of the target variable. Different algorithms use different
 * formulae for measuring "best". Used by the CART algorithm, Gini impurity
 * is a measure of how often a randomly chosen element from the set would
 * be incorrectly labeled if it were randomly labeled according to the
 * distribution of labels in the subset. Gini impurity can be computed by
 * summing the probability of each item being chosen times the probability
 * of a mistake in categorizing that item. It reaches its minimum (zero) when
 * all cases in the node fall into a single target category. Information gain
 * is another popular measure, used by the ID3, C4.5 and C5.0 algorithms.
 * Information gain is based on the concept of entropy used in information
 * theory. For categorical variables with different number of levels, however,
 * information gain are biased in favor of those attributes with more levels.
 * Instead, one may employ the information gain ratio, which solves the drawback
 * of information gain.
 *
 * Classification and Regression Tree techniques have a number of advantages
 * over many of those alternative techniques.
 *  - '''Simple to understand and interpret:'''
 * In most cases, the interpretation of results summarized in a tree is
 * very simple. This simplicity is useful not only for purposes of rapid
 * classification of new observations, but can also often yield a much simpler
 * "model" for explaining why observations are classified or predicted in a
 * particular manner.
 *  - '''Able to handle both numerical and categorical data:'''
 * Other techniques are usually specialized in analyzing datasets that
 * have only one type of variable.
 *  - '''Nonparametric and nonlinear:'''
 * The final results of using tree methods for classification or regression
 * can be summarized in a series of (usually few) logical if-then conditions
 * (tree nodes). Therefore, there is no implicit assumption that the underlying
 * relationships between the predictor variables and the dependent variable
 * are linear, follow some specific non-linear link function, or that they
 * are even monotonic in nature. Thus, tree methods are particularly well
 * suited for data mining tasks, where there is often little a priori
 * knowledge nor any coherent set of theories or predictions regarding which
 * variables are related and how. In those types of data analytics, tree
 * methods can often reveal simple relationships between just a few variables
 * that could have easily gone unnoticed using other analytic techniques.
 *
 * One major problem with classification and regression trees is their high
 * variance. Often a small change in the data can result in a very different
 * series of splits, making interpretation somewhat precarious. Besides,
 * decision-tree learners can create over-complex trees that cause over-fitting.
 * Mechanisms such as pruning are necessary to avoid this problem.
 * Another limitation of trees is the lack of smoothness of the prediction
 * surface.
 *
 * Some techniques such as bagging, boosting, and random forest use more than
 * one decision tree for their analysis.
 *
 * @param formula a symbolic description of the model to be fitted.
 * @param data the data frame of the explanatory and response variables.
 * @param maxDepth the maximum depth of the tree.
 * @param maxNodes the maximum number of leaf nodes in the tree.
 * @param nodeSize the minimum size of leaf nodes.
 * @param splitRule the splitting rule.
 * @return Decision tree model.
 */
fun cart(formula: Formula, data: DataFrame, splitRule: SplitRule = SplitRule.GINI,
         maxDepth: Int = 20, maxNodes: Int = 0, nodeSize: Int = 5): DecisionTree {
    return DecisionTree.fit(formula, data, splitRule, maxDepth, if (maxNodes > 0) maxNodes else data.size() / nodeSize, nodeSize)
}

/**
 * Random forest for classification. Random forest is an ensemble classifier
 * that consists of many decision trees and outputs the majority vote of
 * individual trees. The method combines bagging idea and the random
 * selection of features.
 *
 * Each tree is constructed using the following algorithm:
 *
 *  1. If the number of cases in the training set is N, randomly sample N cases
 * with replacement from the original data. This sample will
 * be the training set for growing the tree.
 *  2. If there are M input variables, a number m &lt;&lt; M is specified such
 * that at each node, m variables are selected at random out of the M and
 * the best split on these m is used to split the node. The value of m is
 * held constant during the forest growing.
 *  3. Each tree is grown to the largest extent possible. There is no pruning.
 *
 * The advantages of random forest are:
 *
 *  - For many data sets, it produces a highly accurate classifier.
 *  - It runs efficiently on large data sets.
 *  - It can handle thousands of input variables without variable deletion.
 *  - It gives estimates of what variables are important in the classification.
 *  - It generates an internal unbiased estimate of the generalization error
 * as the forest building progresses.
 *  - It has an effective method for estimating missing data and maintains
 * accuracy when a large proportion of the data are missing.
 *
 * The disadvantages are
 *
 *  - Random forests are prone to over-fitting for some datasets. This is
 * even more pronounced on noisy data.
 *  - For data including categorical variables with different number of
 * levels, random forests are biased in favor of those attributes with more
 * levels. Therefore, the variable importance scores from random forest are
 * not reliable for this type of data.
 *
 * @param formula a symbolic description of the model to be fitted.
 * @param data the data frame of the explanatory and response variables.
 * @param ntrees the number of trees.
 * @param mtry the number of random selected features to be used to determine
 *             the decision at a node of the tree. floor(sqrt(dim)) seems to give
 *             generally good performance, where dim is the number of variables.
 * @param maxDepth the maximum depth of the tree.
 * @param maxNodes the maximum number of leaf nodes in the tree.
 * @param nodeSize the minimum size of leaf nodes.
 * @param subsample the sampling rate for training tree. 1.0 means sampling with replacement.
 *                  < 1.0 means sampling without replacement.
 * @param splitRule Decision tree node split rule.
 * @return Random forest classification model.
 */
fun randomForest(formula: Formula, data: DataFrame, ntrees: Int = 500, mtry: Int = 0,
                 splitRule: SplitRule = SplitRule.GINI, maxDepth: Int = 20, maxNodes: Int = 500,
                 nodeSize: Int = 1, subsample: Double = 1.0, classWeight: IntArray? = null,
                 seeds: LongStream? = null): RandomForest {
    return RandomForest.fit(formula, data, ntrees, mtry, splitRule, maxDepth, maxNodes, nodeSize, subsample, classWeight, seeds)
}

/**
 * Gradient boosted classification trees.
 *
 * Generic gradient boosting at the t-th step would fit a regression tree to
 * pseudo-residuals. Let J be the number of its leaves. The tree partitions
 * the input space into J disjoint regions and predicts a constant value in
 * each region. The parameter J controls the maximum allowed
 * level of interaction between variables in the model. With J = 2 (decision
 * stumps), no interaction between variables is allowed. With J = 3 the model
 * may include effects of the interaction between up to two variables, and
 * so on. Hastie et al. comment that typically 4 &le; J &le; 8 work well
 * for boosting and results are fairly insensitive to the choice of in
 * this range, J = 2 is insufficient for many applications, and J &gt; 10 is
 * unlikely to be required.
 *
 * Fitting the training set too closely can lead to degradation of the model's
 * generalization ability. Several so-called regularization techniques reduce
 * this over-fitting effect by constraining the fitting procedure.
 * One natural regularization parameter is the number of gradient boosting
 * iterations T (i.e. the number of trees in the model when the base learner
 * is a decision tree). Increasing T reduces the error on training set,
 * but setting it too high may lead to over-fitting. An optimal value of T
 * is often selected by monitoring prediction error on a separate validation
 * data set.
 *
 * Another regularization approach is the shrinkage which times a parameter
 * &eta; (called the "learning rate") to update term.
 * Empirically it has been found that using small learning rates (such as
 * &eta; &lt; 0.1) yields dramatic improvements in model's generalization ability
 * over gradient boosting without shrinking (&eta; = 1). However, it comes at
 * the price of increasing computational time both during training and
 * prediction: lower learning rate requires more iterations.
 *
 * Soon after the introduction of gradient boosting Friedman proposed a
 * minor modification to the algorithm, motivated by Breiman's bagging method.
 * Specifically, he proposed that at each iteration of the algorithm, a base
 * learner should be fit on a subsample of the training set drawn at random
 * without replacement. Friedman observed a substantial improvement in
 * gradient boosting's accuracy with this modification.
 *
 * Subsample size is some constant fraction f of the size of the training set.
 * When f = 1, the algorithm is deterministic and identical to the one
 * described above. Smaller values of f introduce randomness into the
 * algorithm and help prevent over-fitting, acting as a kind of regularization.
 * The algorithm also becomes faster, because regression trees have to be fit
 * to smaller datasets at each iteration. Typically, f is set to 0.5, meaning
 * that one half of the training set is used to build each base learner.
 *
 * Also, like in bagging, sub-sampling allows one to define an out-of-bag
 * estimate of the prediction performance improvement by evaluating predictions
 * on those observations which were not used in the building of the next
 * base learner. Out-of-bag estimates help avoid the need for an independent
 * validation dataset, but often underestimate actual performance improvement
 * and the optimal number of iterations.
 *
 * Gradient tree boosting implementations often also use regularization by
 * limiting the minimum number of observations in trees' terminal nodes.
 * It's used in the tree building process by ignoring any splits that lead
 * to nodes containing fewer than this number of training set instances.
 * Imposing this limit helps to reduce variance in predictions at leaves.
 *
 * ====References:====
 *  - J. H. Friedman. Greedy Function Approximation: A Gradient Boosting Machine, 1999.
 *  - J. H. Friedman. Stochastic Gradient Boosting, 1999.
 *
 * @param formula a symbolic description of the model to be fitted.
 * @param data the data frame of the explanatory and response variables.
 * @param ntrees the number of iterations (trees).
 * @param maxDepth the maximum depth of the tree.
 * @param maxNodes the maximum number of leaf nodes in the tree.
 * @param nodeSize the minimum size of leaf nodes.
 * @param shrinkage the shrinkage parameter in (0, 1] controls the learning rate of procedure.
 * @param subsample the sampling fraction for stochastic tree boosting.
 *
 * @return Gradient boosted trees.
 */
fun gbm(formula: Formula, data: DataFrame, ntrees: Int = 500, maxDepth: Int = 20, maxNodes: Int = 6,
        nodeSize: Int = 5, shrinkage: Double = 0.05, subsample: Double = 0.7): GradientTreeBoost {
    return GradientTreeBoost.fit(formula, data, ntrees, maxDepth, maxNodes, nodeSize, shrinkage, subsample)
}

/**
 * AdaBoost (Adaptive Boosting) classifier with decision trees. In principle,
 * AdaBoost is a meta-algorithm, and can be used in conjunction with many other
 * learning algorithms to improve their performance. In practice, AdaBoost with
 * decision trees is probably the most popular combination. AdaBoost is adaptive
 * in the sense that subsequent classifiers built are tweaked in favor of those
 * instances misclassified by previous classifiers. AdaBoost is sensitive to
 * noisy data and outliers. However in some problems it can be less susceptible
 * to the over-fitting problem than most learning algorithms.
 *
 * AdaBoost calls a weak classifier repeatedly in a series of rounds from
 * total T classifiers. For each call a distribution of weights is updated
 * that indicates the importance of examples in the data set for the
 * classification. On each round, the weights of each incorrectly classified
 * example are increased (or alternatively, the weights of each correctly
 * classified example are decreased), so that the new classifier focuses more
 * on those examples.
 *
 * The basic AdaBoost algorithm is only for binary classification problem.
 * For multi-class classification, a common approach is reducing the
 * multi-class classification problem to multiple two-class problems.
 * This implementation is a multi-class AdaBoost without such reductions.
 *
 * ====References:====
 *  - Yoav Freund, Robert E. Schapire. A Decision-Theoretic Generalization of on-Line Learning and an Application to Boosting, 1995.
 *  - Ji Zhu, Hui Zhou, Saharon Rosset and Trevor Hastie. Multi-class Adaboost, 2009.
 *
 * @param formula a symbolic description of the model to be fitted.
 * @param data the data frame of the explanatory and response variables.
 * @param ntrees the number of trees.
 * @param maxDepth the maximum depth of the tree.
 * @param maxNodes the maximum number of leaf nodes in the tree.
 * @param nodeSize the minimum size of leaf nodes.
 *
 * @return AdaBoost model.
 */
fun adaboost(formula: Formula, data: DataFrame, ntrees: Int = 500, maxDepth: Int = 20,
             maxNodes: Int = 6, nodeSize: Int = 1): AdaBoost {
    return AdaBoost.fit(formula, data, ntrees, maxDepth, maxNodes, nodeSize)
}

/**
 * Fisher's linear discriminant. Fisher defined the separation between two
 * distributions to be the ratio of the variance between the classes to
 * the variance within the classes, which is, in some sense, a measure
 * of the signal-to-noise ratio for the class labeling. FLD finds a linear
 * combination of features which maximizes the separation after the projection.
 * The resulting combination may be used for dimensionality reduction
 * before later classification.
 *
 * The terms Fisher's linear discriminant and LDA are often used
 * interchangeably, although FLD actually describes a slightly different
 * discriminant, which does not make some of the assumptions of LDA such
 * as normally distributed classes or equal class covariances.
 * When the assumptions of LDA are satisfied, FLD is equivalent to LDA.
 *
 * FLD is also closely related to principal component analysis (PCA), which also
 * looks for linear combinations of variables which best explain the data.
 * As a supervised method, FLD explicitly attempts to model the
 * difference between the classes of data. On the other hand, PCA is a
 * unsupervised method and does not take into account any difference in class.
 *
 * One complication in applying FLD (and LDA) to real data
 * occurs when the number of variables/features does not exceed
 * the number of samples. In this case, the covariance estimates do not have
 * full rank, and so cannot be inverted. This is known as small sample size
 * problem.
 *
 * @param x training instances.
 * @param y training labels in [0, k), where k is the number of classes.
 * @param L the dimensionality of mapped space. The default value is the number of classes - 1.
 * @param tol a tolerance to decide if a covariance matrix is singular; it
 *            will reject variables whose variance is less than tol<sup>2</sup>.
 *
 * @return fisher discriminant analysis model.
 */
fun fisher(x: Array<DoubleArray>, y: IntArray, L: Int = -1, tol: Double = 0.0001): FLD {
    return FLD.fit(x, y, L, tol)
}

/**
 * Linear discriminant analysis. LDA is based on the Bayes decision theory
 * and assumes that the conditional probability density functions are normally
 * distributed. LDA also makes the simplifying homoscedastic assumption (i.e.
 * that the class covariances are identical) and that the covariances have full
 * rank. With these assumptions, the discriminant function of an input being
 * in a class is purely a function of this linear combination of independent
 * variables.
 *
 * LDA is closely related to ANOVA (analysis of variance) and linear regression
 * analysis, which also attempt to express one dependent variable as a
 * linear combination of other features or measurements. In the other two
 * methods, however, the dependent variable is a numerical quantity, while
 * for LDA it is a categorical variable (i.e. the class label). Logistic
 * regression and probit regression are more similar to LDA, as they also
 * explain a categorical variable. These other methods are preferable in
 * applications where it is not reasonable to assume that the independent
 * variables are normally distributed, which is a fundamental assumption
 * of the LDA method.
 *
 * One complication in applying LDA (and Fisher's discriminant) to real data
 * occurs when the number of variables/features does not exceed
 * the number of samples. In this case, the covariance estimates do not have
 * full rank, and so cannot be inverted. This is known as small sample size
 * problem.
 *
 * @param x training samples.
 * @param y training labels in [0, k), where k is the number of classes.
 * @param priori the priori probability of each class. If null, it will be
 *               estimated from the training data.
 * @param tol a tolerance to decide if a covariance matrix is singular; it
 *            will reject variables whose variance is less than tol<sup>2</sup>.
 *
 * @return linear discriminant analysis model.
 */
fun lda(x: Array<DoubleArray>, y: IntArray, priori: DoubleArray? = null, tol: Double = 0.0001): LDA {
    return LDA.fit(x, y, priori, tol)
}

/**
 * Quadratic discriminant analysis. QDA is closely related to linear discriminant
 * analysis (LDA). Like LDA, QDA models the conditional probability density
 * functions as a Gaussian distribution, then uses the posterior distributions
 * to estimate the class for a given test data. Unlike LDA, however,
 * in QDA there is no assumption that the covariance of each of the classes
 * is identical. Therefore, the resulting separating surface between
 * the classes is quadratic.
 *
 * The Gaussian parameters for each class can be estimated from training data
 * with maximum likelihood (ML) estimation. However, when the number of
 * training instances is small compared to the dimension of input space,
 * the ML covariance estimation can be ill-posed. One approach to resolve
 * the ill-posed estimation is to regularize the covariance estimation.
 * One of these regularization methods is {@link rda regularized discriminant analysis}.
 *
 * @param x training samples.
 * @param y training labels in [0, k), where k is the number of classes.
 * @param priori the priori probability of each class. If null, it will be
 *               estimated from the training data.
 * @param tol a tolerance to decide if a covariance matrix is singular; it
 *            will reject variables whose variance is less than tol<sup>2</sup>.
 *
 * @return Quadratic discriminant analysis model.
 */
fun qda(x: Array<DoubleArray>, y: IntArray, priori: DoubleArray? = null, tol: Double = 0.0001): QDA {
    return QDA.fit(x, y, priori, tol)
}

/**
 * Regularized discriminant analysis. RDA is a compromise between LDA and QDA,
 * which allows one to shrink the separate covariances of QDA toward a common
 * variance as in LDA. This method is very similar in flavor to ridge regression.
 * The regularized covariance matrices of each class is
 * &Sigma;<sub>k</sub>(&alpha;) = &alpha; &Sigma;<sub>k</sub> + (1 - &alpha;) &Sigma;.
 * The quadratic discriminant function is defined using the shrunken covariance
 * matrices &Sigma;<sub>k</sub>(&alpha;). The parameter &alpha; in `[0, 1]`
 * controls the complexity of the model. When &alpha; is one, RDA becomes QDA.
 * While &alpha; is zero, RDA is equivalent to LDA. Therefore, the
 * regularization factor &alpha; allows a continuum of models between LDA and QDA.
 *
 * @param x training samples.
 * @param y training labels in `[0, k)`, where k is the number of classes.
 * @param alpha regularization factor in `[0, 1]` allows a continuum of models
 *              between LDA and QDA.
 * @param priori the priori probability of each class.
 * @param tol tolerance to decide if a covariance matrix is singular; it
 *            will reject variables whose variance is less than tol<sup>2</sup>.
 *
 * @return Regularized discriminant analysis model.
 */
fun rda(x: Array<DoubleArray>, y: IntArray, alpha: Double, priori: DoubleArray? = null, tol: Double = 0.0001): RDA {
    return RDA.fit(x, y, alpha, priori, tol)
}

/**
 * Creates a naive Bayes classifier for document classification.
 * Add-k smoothing.
 *
 * @param x training samples.
 * @param y training labels in `[0, k)`, where k is the number of classes.
 * @param model the generation model of naive Bayes classifier.
 * @param priori the priori probability of each class. If null, equal probability is assume for each class.
 * @param sigma the prior count of add-k smoothing of evidence.
 */
fun naiveBayes(x: Array<IntArray>, y: IntArray, model: DiscreteNaiveBayes.Model, priori: DoubleArray? = null, sigma: Double = 1.0): DiscreteNaiveBayes {
    val p = x[0].size
    val k = MathEx.max(y) + 1
    val labels = ClassLabels.fit(y).labels
    val naive = if (priori == null)
        DiscreteNaiveBayes(model, k, p, sigma, labels)
    else
        DiscreteNaiveBayes(model, priori, p, sigma, labels)
    naive.update(x, y)
    return naive
}

/**
 * Creates a general naive Bayes classifier.
 *
 * @param priori the priori probability of each class.
 * @param condprob the conditional distribution of each variable in
 *                 each class. In particular, `condprob[i][j]` is the conditional
 *                 distribution P(x<sub>j</sub> | class i).
 */
fun naiveBayes(priori: DoubleArray, condprob: Array<Array<Distribution>>): NaiveBayes {
return NaiveBayes(priori, condprob)
}

/**
 * One-vs-one strategy for reducing the problem of
 * multiclass classification to multiple binary classification problems.
 * This approach trains K (K − 1) / 2 binary classifiers for a
 * K-way multiclass problem; each receives the samples of a pair of
 * classes from the original training set, and must learn to distinguish
 * these two classes. At prediction time, a voting scheme is applied:
 * all K (K − 1) / 2 classifiers are applied to an unseen sample and the
 * class that got the highest number of positive predictions gets predicted
 * by the combined classifier.
 * Like One-vs-rest, one-vs-one suffers from ambiguities in that some
 * regions of its input space may receive the same number of votes.
 */
fun <T> ovo(x: Array<T>, y: IntArray, trainer: (Array<T>, IntArray) -> Classifier<T>): OneVersusOne<T> {
    return OneVersusOne.fit(x, y, trainer)
}

/**
 * One-vs-rest (or one-vs-all) strategy for reducing the problem of
 * multiclass classification to multiple binary classification problems.
 * It involves training a single classifier per class, with the samples
 * of that class as positive samples and all other samples as negatives.
 * This strategy requires the base classifiers to produce a real-valued
 * confidence score for its decision, rather than just a class label;
 * discrete class labels alone can lead to ambiguities, where multiple
 * classes are predicted for a single sample.
 * <p>
 * Making decisions means applying all classifiers to an unseen sample
 * x and predicting the label k for which the corresponding classifier
 * reports the highest confidence score.
 * <p>
 * Although this strategy is popular, it is a heuristic that suffers
 * from several problems. Firstly, the scale of the confidence values
 * may differ between the binary classifiers. Second, even if the class
 * distribution is balanced in the training set, the binary classification
 * learners see unbalanced distributions because typically the set of
 * negatives they see is much larger than the set of positives.
 */
fun <T> ovr(x: Array<T>, y: IntArray, trainer: (Array<T>, IntArray) -> Classifier<T>): OneVersusRest<T> {
    return OneVersusRest.fit(x, y, trainer)
}
