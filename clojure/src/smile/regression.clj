;   Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
;
;   Smile is free software: you can redistribute it and/or modify
;   it under the terms of the GNU Lesser General Public License as
;   published by the Free Software Foundation, either version 3 of
;   the License, or (at your option) any later version.
;
;   Smile is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;   GNU Lesser General Public License for more details.
;
;   You should have received a copy of the GNU Lesser General Public License
;   along with Smile.  If not, see <https://www.gnu.org/licenses/>.

(ns smile.regression
  "Regression Analysis"
  {:author "Haifeng Li"}
  (:import [smile.regression OLS RidgeRegression LASSO MLP RBFNetwork SVR
                             RegressionTree RandomForest GradientTreeBoost
                             GaussianProcessRegression]
           [smile.base.cart Loss]))

(defn lm
  "Fitting linear models (ordinary least squares).

  In linear regression, the model specification is that the dependent
  variable is a linear combination of the parameters (but need not be
  linear in the independent variables). The residual is the difference
  between the value of the dependent variable predicted by the model,
  and the true value of the dependent variable. Ordinary least squares
  obtains parameter estimates that minimize the sum of squared residuals,
  SSE (also denoted RSS).

  The OLS estimator is consistent when the independent variables are
  exogenous and there is no multicollinearity, and optimal in the class
  of linear unbiased estimators when the errors are homoscedastic and
  serially uncorrelated. Under these conditions, the method of OLS provides
  minimum-variance mean-unbiased estimation when the errors have finite
  variances.

  There are several different frameworks in which the linear regression
  model can be cast in order to make the OLS technique applicable. Each
  of these settings produces the same formulas and same results, the only
  difference is the interpretation and the assumptions which have to be
  imposed in order for the method to give meaningful results. The choice
  of the applicable framework depends mostly on the nature of data at hand,
  and on the inference task which has to be performed.

  Least squares corresponds to the maximum likelihood criterion if the
  experimental errors have a normal distribution and can also be derived
  as a method of moments estimator.

  Once a regression model has been constructed, it may be important to
  confirm the goodness of fit of the model and the statistical significance
  of the estimated parameters. Commonly used checks of goodness of fit
  include the R-squared, analysis of the pattern of residuals and hypothesis
  testing. Statistical significance can be checked by an F-test of the overall
  fit, followed by t-tests of individual parameters.

  Interpretations of these diagnostic tests rest heavily on the model
  assumptions. Although examination of the residuals can be used to
  invalidate a model, the results of a t-test or F-test are sometimes more
  difficult to interpret if the model's assumptions are violated.
  For example, if the error term does not have a normal distribution,
  in small samples the estimated parameters will not follow normal
  distributions and complicate inference. With relatively large samples,
  however, a central limit theorem can be invoked such that hypothesis
  testing may proceed using asymptotic approximations.

  `formula` is a symbolic description of the model to be fitted.
  `data` is the data frame of the explanatory and response variables.
  `method` is the fitting method ('qr' or 'svd').
  `recursive` is the flag if the return model supports recursive least squares."
  ([formula data] (lm formula data "qr" true true))
  ([formula data method, stderr recursive] (OLS/fit formula data method stderr recursive)))

(defn ridge
  "Ridge Regression.

  When the predictor variables are highly correlated amongst
  themselves, the coefficients of the resulting least squares fit may be very
  imprecise. By allowing a small amount of bias in the estimates, more
  reasonable coefficients may often be obtained. Ridge regression is one
  method to address these issues. Often, small amounts of bias lead to
  dramatic reductions in the variance of the estimated model coefficients.
  Ridge regression is such a technique which shrinks the regression
  coefficients by imposing a penalty on their size. Ridge regression was
  originally developed to overcome the singularity of the X'X matrix.
  This matrix is perturbed so as to make its determinant appreciably
  different from 0.

  Ridge regression is a kind of Tikhonov regularization, which is the most
  commonly used method of regularization of ill-posed problems. Another
  interpretation of ridge regression is available through Bayesian estimation.
  In this setting the belief that weight should be small is coded into a prior
  distribution.

  `formula` is a symbolic description of the model to be fitted.
  `data` is the data frame of the explanatory and response variables.
  `lambda` is the shrinkage/regularization parameter."
  [formula data lambda] (RidgeRegression/fit formula data lambda))

(defn lasso
  "Least absolute shrinkage and selection operator.

  The Lasso is a shrinkage and selection method for linear regression.
  It minimizes the usual sum of squared errors, with a bound on the sum
  of the absolute values of the coefficients (i.e. L<sub>1</sub>-regularized).
  It has connections to soft-thresholding of wavelet coefficients, forward
  stage-wise regression, and boosting methods.

  The Lasso typically yields a sparse solution, of which the parameter
  vector &beta; has relatively few nonzero coefficients. In contrast, the
  solution of L<sub>2</sub>-regularized least squares (i.e. ridge regression)
  typically has all coefficients nonzero. Because it effectively
  reduces the number of variables, the Lasso is useful in some contexts.

  For over-determined systems (more instances than variables, commonly in
  machine learning), we normalize variables with mean 0 and standard deviation
  1. For under-determined systems (less instances than variables, e.g.
  compressed sensing), we assume white noise (i.e. no intercept in the linear
  model) and do not perform normalization. Note that the solution
  is not unique in this case.

  There is no analytic formula or expression for the optimal solution to the
  L<sub>1</sub>-regularized least squares problems. Therefore, its solution
  must be computed numerically. The objective function in the
  L<sub>1</sub>-regularized least squares is convex but not differentiable,
  so solving it is more of a computational challenge than solving the
  L<sub>2</sub>-regularized least squares. The Lasso may be solved using
  quadratic programming or more general convex optimization methods, as well
  as by specific algorithms such as the least angle regression algorithm.

  `formula` is a symbolic description of the model to be fitted.
  `data` is the data frame of the explanatory and response variables.
  `lambda` is the shrinkage/regularization parameter.
  `tol` is the tolerance for stopping iterations (relative target duality gap).
  `max-iter` is the maximum number of iterations."
  ([formula data lambda] (lasso formula data lambda 0.001 5000))
  ([formula data lambda tol max-iter] (LASSO/fit formula data lambda tol max-iter)))

(defn mlp
  "Multilayer perceptron neural network.

  An MLP consists of several layers of nodes, interconnected through weighted
  acyclic arcs from each preceding layer to the following, without lateral or
  feedback connections. Each node calculates a transformed weighted linear
  combination of its inputs (output activations from the preceding layer), with
  one of the weights acting as a trainable bias connected to a constant input.
  The transformation, called activation function, is a bounded non-decreasing
  (non-linear) function, such as the sigmoid functions (ranges from 0 to 1).
  Another popular activation function is hyperbolic tangent which is actually
  equivalent to the sigmoid function in shape but ranges from -1 to 1.
  More specialized activation functions include radial basis functions which
  are used in RBF networks.

  The representational capabilities of a MLP are determined by the range of
  mappings it may implement through weight variation. Single layer perceptrons
  are capable of solving only linearly separable problems. With the sigmoid
  function as activation function, the single-layer network is identical
  to the logistic regression model.

  The universal approximation theorem for neural networks states that every
  continuous function that maps intervals of real numbers to some output
  interval of real numbers can be approximated arbitrarily closely by a
  multi-layer perceptron with just one hidden layer. This result holds only
  for restricted classes of activation functions, which are extremely complex
  and NOT smooth for subtle mathematical reasons. On the other hand, smoothness
  is important for gradient descent learning. Besides, the proof is not
  constructive regarding the number of neurons required or the settings of
  the weights. Therefore, complex systems will have more layers of neurons
  with some having increased layers of input neurons and output neurons
  in practice.

  The most popular algorithm to train MLPs is back-propagation, which is a
  gradient descent method. Based on chain rule, the algorithm propagates the
  error back through the network and adjusts the weights of each connection in
  order to reduce the value of the error function by some small amount.
  For this reason, back-propagation can only be applied on networks with
  differentiable activation functions.

  During error back propagation, we usually times the gradient with a small
  number &eta;, called learning rate, which is carefully selected to ensure
  that the network converges to a local minimum of the error function
  fast enough, without producing oscillations. One way to avoid oscillation
  at large &eta;, is to make the change in weight dependent on the past weight
  change by adding a momentum term.

  Although the back-propagation algorithm may performs gradient
  descent on the total error of all instances in a batch way,
  the learning rule is often applied to each instance separately in an online
  way or stochastic way. There exists empirical indication that the stochastic
  way results in faster convergence.

  In practice, the problem of over-fitting has emerged. This arises in
  convoluted or over-specified systems when the capacity of the network
  significantly exceeds the needed free parameters. There are two general
  approaches for avoiding this problem: The first is to use cross-validation
  and similar techniques to check for the presence of over-fitting and
  optimally select hyper-parameters such as to minimize the generalization
  error. The second is to use some form of regularization, which emerges
  naturally in a Bayesian framework, where the regularization can be
  performed by selecting a larger prior probability over simpler models;
  but also in statistical learning theory, where the goal is to minimize over
  the 'empirical risk' and the 'structural risk'.

  For neural networks, the input patterns usually should be
  scaled/standardized. Commonly, each input variable is scaled into
  interval `[0, 1]` or to have mean 0 and standard deviation 1.

  For penalty functions and output units, the following natural pairings are
  recommended:

   - linear output units and a least squares penalty function.
   - a two-class cross-entropy penalty function and a logistic
     activation function.
   - a multi-class cross-entropy penalty function and a softmax
     activation function.

  By assigning a softmax activation function on the output layer of
  the neural network for categorical target variables, the outputs
  can be interpreted as posterior probabilities, which are very useful.

  `x` is the training samples.
  `y` is the response variable.
  `builders` are the builders of layers from bottom to top.
  `epochs` is the the number of epochs of stochastic learning.
  `eta` is the the learning rate.
  `alpha` is the momentum factor.
  `lambda` is the weight decay for regularization."
  ([x y builders] (mlp x y builders 10 0.1 0.0 0.0))
  ([x y builders epochs eta alpha lambda]
   (let [net (MLP. (.length (aget x 0)) builders)]
     ((.setLearningRate net eta)
      (.setMomentum net alpha)
      (.setWeightDecay net lambda)
      (dotimes [i epochs] (.update net x, y))
      net))))

(defn rbfnet
  "Radial basis function networks.

  A radial basis function network is an artificial neural network that uses
  radial basis functions as activation functions. It is a linear combination
  of radial basis functions. They are used in function approximation, time
  series prediction, and control.

  In its basic form, radial basis function network is in the form
  ```
  y(x) = &Sigma; w<sub>i</sub> &phi;(||x-c<sub>i</sub>||)
  ```
  where the approximating function y(x) is represented as a sum of N radial
  basis functions &phi;, each associated with a different center c<sub>i</sub>,
  and weighted by an appropriate coefficient w<sub>i</sub>. For distance,
  one usually chooses Euclidean distance. The weights w<sub>i</sub> can
  be estimated using the matrix methods of linear least squares, because
  the approximating function is linear in the weights.

  The centers c<sub>i</sub> can be randomly selected from training data,
  or learned by some clustering method (e.g. k-means), or learned together
  with weight parameters undergo a supervised learning processing
  (e.g. error-correction learning).

  The popular choices for &phi; comprise the Gaussian function and the so
  called thin plate splines. The advantage of the thin plate splines is that
  their conditioning is invariant under scalings. Gaussian, multi-quadric
  and inverse multi-quadric are infinitely smooth and and involve a scale
  or shape parameter, r<sub><small>0</small></sub> &gt; 0. Decreasing
  r<sub><small>0</small></sub> tends to flatten the basis function. For a
  given function, the quality of approximation may strongly depend on this
  parameter. In particular, increasing r<sub><small>0</small></sub> has the
  effect of better conditioning (the separation distance of the scaled points
  increases).

  A variant on RBF networks is normalized radial basis function (NRBF)
  networks, in which we require the sum of the basis functions to be unity.
  NRBF arises more naturally from a Bayesian statistical perspective. However,
  there is no evidence that either the NRBF method is consistently superior
  to the RBF method, or vice versa.

  SVMs with Gaussian kernel have similar structure as RBF networks with
  Gaussian radial basis functions. However, the SVM approach 'automatically'
  solves the network complexity problem since the size of the hidden layer
  is obtained as the result of the QP procedure. Hidden neurons and
  support vectors correspond to each other, so the center problems of
  the RBF network is also solved, as the support vectors serve as the
  basis function centers. It was reported that with similar number of support
  vectors/centers, SVM shows better generalization performance than RBF
  network when the training data size is relatively small. On the other hand,
  RBF network gives better generalization performance than SVM on large
  training data.

  `x` is the training samples.
  `y` is the response variable.
  `neurons` are the radial basis functions.
  If `normalized` is true, train a normalized RBF network."
  ([x y neurons] (rbfnet x y neurons false))
  ([x y neurons normalized] (RBFNetwork/fit x y neurons normalized)))

(defn svr
  "Support vector regression.

  Like SVM for classification, the model produced by SVR depends only on a
  subset of the training data, because the cost function ignores any training
  data close to the model prediction (within a threshold).

  `x` is the training data.
  `y` is the response variable.
  `kernel` is the kernel function.
  `eps` is the loss function error threshold.
  `C` is the soft margin penalty parameter.
  `tol` is the tolerance of convergence test."
  ([x y kernel eps C] (svr x y kernel eps C 1E-3))
  ([x y kernel eps C tol] (SVR/fit x y kernel eps C tol)))

(defn cart
  "Regression tree.

  A classification/regression tree can be learned by splitting the training
  set into subsets based on an attribute value test. This process is repeated
  on each derived subset in a recursive manner called recursive partitioning.
  The recursion is completed when the subset at a node all has the same value
  of the target variable, or when splitting no longer adds value to the
  predictions.

  The algorithms that are used for constructing decision trees usually
  work top-down by choosing a variable at each step that is the next best
  variable to use in splitting the set of items. 'Best' is defined by how
  well the variable splits the set into homogeneous subsets that have
  the same value of the target variable. Different algorithms use different
  formulae for measuring 'best'. Used by the CART algorithm, Gini impurity
  is a measure of how often a randomly chosen element from the set would
  be incorrectly labeled if it were randomly labeled according to the
  distribution of labels in the subset. Gini impurity can be computed by
  summing the probability of each item being chosen times the probability
  of a mistake in categorizing that item. It reaches its minimum (zero) when
  all cases in the node fall into a single target category. Information gain
  is another popular measure, used by the ID3, C4.5 and C5.0 algorithms.
  Information gain is based on the concept of entropy used in information
  theory. For categorical variables with different number of levels, however,
  information gain are biased in favor of those attributes with more levels.
  Instead, one may employ the information gain ratio, which solves the drawback
  of information gain.

  Classification and Regression Tree techniques have a number of advantages
  over many of those alternative techniques.
   - Simple to understand and interpret:
  In most cases, the interpretation of results summarized in a tree is
  very simple. This simplicity is useful not only for purposes of rapid
  classification of new observations, but can also often yield a much simpler
  'model' for explaining why observations are classified or predicted in a
  particular manner.
   - Able to handle both numerical and categorical data:
  Other techniques are usually specialized in analyzing datasets that
  have only one type of variable.
   - Nonparametric and nonlinear:
  The final results of using tree methods for classification or regression
  can be summarized in a series of (usually few) logical if-then conditions
  (tree nodes). Therefore, there is no implicit assumption that the underlying
  relationships between the predictor variables and the dependent variable
  are linear, follow some specific non-linear link function, or that they
  are even monotonic in nature. Thus, tree methods are particularly well
  suited for data mining tasks, where there is often little a priori
  knowledge nor any coherent set of theories or predictions regarding which
  variables are related and how. In those types of data analytics, tree
  methods can often reveal simple relationships between just a few variables
  that could have easily gone unnoticed using other analytic techniques.

  One major problem with classification and regression trees is their high
  variance. Often a small change in the data can result in a very different
  series of splits, making interpretation somewhat precarious. Besides,
  decision-tree learners can create over-complex trees that cause over-fitting.
  Mechanisms such as pruning are necessary to avoid this problem.
  Another limitation of trees is the lack of smoothness of the prediction
  surface.

  Some techniques such as bagging, boosting, and random forest use more than
  one decision tree for their analysis.

  `formula` is a symbolic description of the model to be fitted.
  `data` is the data frame of the explanatory and response variables.
  `max-depth` is the maximum depth of the tree.
  `max-nodes` is the maximum number of leaf nodes in the tree.
  `node-size` is the minimum size of leaf nodes."
  ([formula data] (cart formula data 20 0 5))
  ([formula data max-depth max-nodes node-size]
   (RegressionTree/fit formula data max-depth max-nodes node-size)))

(defn random-forest
  "Random forest.

  Random forest is an ensemble classifier that consists of many decision
  trees and outputs the majority vote of individual trees. The method
  combines bagging idea and the random selection of features.

  Each tree is constructed using the following algorithm:

   1. If the number of cases in the training set is N, randomly sample N cases
  with replacement from the original data. This sample will
  be the training set for growing the tree.
   2. If there are M input variables, a number m &lt;&lt; M is specified such
  that at each node, m variables are selected at random out of the M and
  the best split on these m is used to split the node. The value of m is
  held constant during the forest growing.
   3. Each tree is grown to the largest extent possible. There is no pruning.

  The advantages of random forest are:

   - For many data sets, it produces a highly accurate classifier.
   - It runs efficiently on large data sets.
   - It can handle thousands of input variables without variable deletion.
   - It gives estimates of what variables are important in the classification.
   - It generates an internal unbiased estimate of the generalization error
  as the forest building progresses.
   - It has an effective method for estimating missing data and maintains
  accuracy when a large proportion of the data are missing.

  The disadvantages are

   - Random forests are prone to over-fitting for some datasets. This is
  even more pronounced on noisy data.
   - For data including categorical variables with different number of
  levels, random forests are biased in favor of those attributes with more
  levels. Therefore, the variable importance scores from random forest are
  not reliable for this type of data.

  `formula` is a symbolic description of the model to be fitted.
  `data` is the data frame of the explanatory and response variables.
  `ntrees` is the number of trees.
  `mtry` is the number of random selected features to be used to determine
  the decision at a node of the tree. `dim/3` seems to give
  generally good performance, where `dim` is the number of variables.
  `max-depth` is the maximum depth of the tree.
  `max-nodes` is the maximum number of leaf nodes in the tree.
  `node-size` is the minimum size of leaf nodes.
  `subsample` is the sampling rate for training tree. 1.0 means sampling
  with replacement. < 1.0 means sampling without replacement."
  ([formula data] (random-forest formula data 500 0 20 500 5 1.0))
  ([formula data ntrees mtry max-depth max-nodes node-size subsample]
   (RandomForest/fit formula data ntrees mtry max-depth max-nodes node-size subsample)))

(defn gbm 
  "Gradient boosted classification trees.

  Generic gradient boosting at the t-th step would fit a regression tree to
  pseudo-residuals. Let J be the number of its leaves. The tree partitions
  the input space into J disjoint regions and predicts a constant value in
  each region. The parameter J controls the maximum allowed
  level of interaction between variables in the model. With J = 2 (decision
  stumps), no interaction between variables is allowed. With J = 3 the model
  may include effects of the interaction between up to two variables, and
  so on. Hastie et al. comment that typically 4 &le; J &le; 8 work well
  for boosting and results are fairly insensitive to the choice of in
  this range, J = 2 is insufficient for many applications, and J &gt; 10 is
  unlikely to be required.

  Fitting the training set too closely can lead to degradation of the model's
  generalization ability. Several so-called regularization techniques reduce
  this over-fitting effect by constraining the fitting procedure.
  One natural regularization parameter is the number of gradient boosting
  iterations T (i.e. the number of trees in the model when the base learner
  is a decision tree). Increasing T reduces the error on training set,
  but setting it too high may lead to over-fitting. An optimal value of T
  is often selected by monitoring prediction error on a separate validation
  data set.

  Another regularization approach is the shrinkage which times a parameter
  &eta; (called the 'learning rate') to update term.
  Empirically it has been found that using small learning rates (such as
  &eta; &lt; 0.1) yields dramatic improvements in model's generalization ability
  over gradient boosting without shrinking (&eta; = 1). However, it comes at
  the price of increasing computational time both during training and
  prediction: lower learning rate requires more iterations.

  Soon after the introduction of gradient boosting Friedman proposed a
  minor modification to the algorithm, motivated by Breiman's bagging method.
  Specifically, he proposed that at each iteration of the algorithm, a base
  learner should be fit on a subsample of the training set drawn at random
  without replacement. Friedman observed a substantial improvement in
  gradient boosting's accuracy with this modification.

  Subsample size is some constant fraction f of the size of the training set.
  When f = 1, the algorithm is deterministic and identical to the one
  described above. Smaller values of f introduce randomness into the
  algorithm and help prevent over-fitting, acting as a kind of regularization.
  The algorithm also becomes faster, because regression trees have to be fit
  to smaller datasets at each iteration. Typically, f is set to 0.5, meaning
  that one half of the training set is used to build each base learner.

  Also, like in bagging, sub-sampling allows one to define an out-of-bag
  estimate of the prediction performance improvement by evaluating predictions
  on those observations which were not used in the building of the next
  base learner. Out-of-bag estimates help avoid the need for an independent
  validation dataset, but often underestimate actual performance improvement
  and the optimal number of iterations.

  Gradient tree boosting implementations often also use regularization by
  limiting the minimum number of observations in trees' terminal nodes.
  It's used in the tree building process by ignoring any splits that lead
  to nodes containing fewer than this number of training set instances.
  Imposing this limit helps to reduce variance in predictions at leaves.

  `formula` is a symbolic description of the model to be fitted.
  `data` is the data frame of the explanatory and response variables.
  `loss` is the loss function for regression.
  `ntrees` is the number of iterations (trees).
  `max-depth` is the maximum depth of the tree.
  `max-nodes` is the maximum number of leaf nodes in the tree.
  `node-size` is the minimum size of leaf nodes.
  `shrinkage` is the shrinkage parameter in (0, 1] controls the learning
  rate of procedure.
  `subsample` is the sampling fraction for stochastic tree boosting."
  ([formula data] (gbm formula data (Loss/lad) 500 20 6 5 0.05 0.7))
  ([formula data loss ntrees max-depth max-nodes node-size shrinkage subsample]
   (GradientTreeBoost/fit formula data loss ntrees max-depth max-nodes node-size shrinkage subsample)))

(defn gpr
  "Gaussian process.

  A Gaussian process is a stochastic process whose realizations consist of
  random values associated with every point in a range of times (or of space)
  such that each such random variable has a normal distribution. Moreover,
  every finite collection of those random variables has a multivariate normal
  distribution.

  A Gaussian process can be used as a prior probability distribution over
  functions in Bayesian inference. Given any set of N points in the desired
  domain of your functions, take a multivariate Gaussian whose covariance
  matrix parameter is the Gram matrix of N points with some desired kernel,
  and sample from that Gaussian. Inference of continuous values with a
  Gaussian process prior is known as Gaussian process regression.

  The fitting is performed in the reproducing kernel Hilbert space with
  the 'kernel trick'. The loss function is squared-error. This also arises
  as the kriging estimate of a Gaussian random field in spatial statistics.

  A significant problem with Gaussian process prediction is that it typically
  scales as O(n<sup>3</sup>). For large problems (e.g. n &gt; 10,000) both
  storing the Gram matrix and solving the associated linear systems are
  prohibitive on modern workstations. An extensive range of proposals have
  been suggested to deal with this problem. A popular approach is the
  reduced-rank Approximations of the Gram Matrix, known as Nystrom
  approximation. Greedy approximation is another popular approach that uses
  an active set of training points of size m selected from the training set
  of size n &gt; m. We assume that it is impossible to search for the optimal
  subset of size m due to combinatorics. The points in the active set could
  be selected randomly, but in general we might expect better performance
  if the points are selected greedily w.r.t. some criterion. Recently,
  researchers had proposed relaxing the constraint that the inducing variables
  must be a subset of training/test cases, turning the discrete selection
  problem into one of continuous optimization.

  This method fits a regular Gaussian process model.

  `x` is the training dataset.
  `y` is the response variable.
  `kernel` is the Mercer kernel.
  `noise` is the noise variance, which also works as a regularization parameter.
  `normalize` is the option to normalize the response variable.
  `tol` is the stopping tolerance for HPO.
  `max-iter` is the maximum number of iterations for HPO. No HPO if maxIter <= 0."
  ([x y kernel noise] (GaussianProcessRegression/fit x y kernel noise))
  ([x y kernel noise normalize tol max-iter] (GaussianProcessRegression/fit x y kernel noise normalize tol max-iter)))

(defn gpr-approx
  "Approximate Gaussian process with a subset of regressors.
  `x` is the training dataset.
  `y` is the response variable.
  `t` is the inducing input, which are pre-selected or inducing samples
  acting as active set of regressors. In simple case, these can be chosen
  randomly from the training set or as the centers of k-means clustering.
  `kernel` is the Mercer kernel.
  `noise` is the noise variance, which also works as a regularization parameter.
  `normalize` is the option to normalize the response variable."
  ([x y t kernel noise] (GaussianProcessRegression/fit x y t kernel noise))
  ([x y t kernel noise normalize] (GaussianProcessRegression/fit x y t kernel noise normalize)))

(defn gpr-nystrom
  "Approximate Gaussian process with Nystrom approximation of kernel matrix.
  `x` is the training dataset.
  `y` is the response variable.
  `t` is the inducing input, which are pre-selected or inducing samples
  acting as active set of regressors. In simple case, these can be chosen
  randomly from the training set or as the centers of k-means clustering.
  `kernel` is the Mercer kernel.
  `noise` is the noise variance, which also works as a regularization parameter.
  `normalize` is the option to normalize the response variable."
  ([x y t kernel noise] (GaussianProcessRegression/nystrom x y t kernel noise))
  ([x y t kernel noise normalize] (GaussianProcessRegression/nystrom x y t kernel noise normalize)))

