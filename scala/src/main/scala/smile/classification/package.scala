/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile

import smile.classification._
import smile.data._
import smile.math._, kernel._
import smile.util._

/**
 * Classification shell commands.
 *
 * @author Haifeng Li
 */
package object classification {

  /**
   * Apply a classification model on a data sample.
   *
   * @param classifier classification model
   * @param x data sample
   * @param posteriori optional double array of posertiori probability output. Note not all models support it.
   * @tparam T the data type
   * @return the predicted class label
   */
  def predict[T <: AnyRef](classifier: Classifier[T], x: T, posteriori: Array[Double] = null): Int = {
    if (posteriori == null)
      classifier.predict(x)
    else
      classifier.predict(x, posteriori)
  }

  /**
   * Support vector machines for classification. The basic support vector machine
   * is a binary linear classifier which chooses the hyperplane that represents
   * the largest separation, or margin, between the two classes. If such a
   * hyperplane exists, it is known as the maximum-margin hyperplane and the
   * linear classifier it defines is known as a maximum margin classifier.
   * <p>
   * If there exists no hyperplane that can perfectly split the positive and
   * negative instances, the soft margin method will choose a hyperplane
   * that splits the instances as cleanly as possible, while still maximizing
   * the distance to the nearest cleanly split instances.
   * <p>
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
   * <p>
   * The effectiveness of SVM depends on the selection of kernel, the kernel's
   * parameters, and soft margin parameter C. Given a kernel, best combination
   * of C and kernel's parameters is often selected by a grid-search with
   * cross validation.
   * <p>
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
   * @param C Regularization parameter
   * @param strategy multi-class classification strategy, one vs all or one vs one.
   * @param epoch the number of training epochs
   * @tparam T the data type
   *
   * @return SVM model.
   */
  def svm[T <: AnyRef](x: Array[T], y: Array[Int], kernel: MercerKernel[T], C: Double, strategy: SVM.Multiclass = SVM.Multiclass.ONE_VS_ONE, epoch: Int = 1): SVM[T] = {
    val k = Math.max(y: _*) + 1
    val svm = new SVM[T](kernel, C, k, strategy)
    time {
      svm.learn(x, y)
      for (i <- 1 to epoch) {
        println(s"SVM training epoch $i...")
        svm.finish
      }
    }
    svm
  }

  /**
   * Random forest for classification. Random forest is an ensemble classifier
   * that consists of many decision trees and outputs the majority vote of
   * individual trees. The method combines bagging idea and the random
   * selection of features.
   * <p>
   * Each tree is constructed using the following algorithm:
   * <ol>
   * <li> If the number of cases in the training set is N, randomly sample N cases
   * with replacement from the original data. This sample will
   * be the training set for growing the tree.
   * <li> If there are M input variables, a number m &lt;&lt; M is specified such
   * that at each node, m variables are selected at random out of the M and
   * the best split on these m is used to split the node. The value of m is
   * held constant during the forest growing.
   * <li> Each tree is grown to the largest extent possible. There is no pruning.
   * </ol>
   * The advantages of random forest are:
   * <ul>
   * <li> For many data sets, it produces a highly accurate classifier.
   * <li> It runs efficiently on large data sets.
   * <li> It can handle thousands of input variables without variable deletion.
   * <li> It gives estimates of what variables are important in the classification.
   * <li> It generates an internal unbiased estimate of the generalization error
   * as the forest building progresses.
   * <li> It has an effective method for estimating missing data and maintains
   * accuracy when a large proportion of the data are missing.
   * </ul>
   * The disadvantages are
   * <ul>
   * <li> Random forests are prone to over-fitting for some datasets. This is
   * even more pronounced on noisy data.
   * <li> For data including categorical variables with different number of
   * levels, random forests are biased in favor of those attributes with more
   * levels. Therefore, the variable importance scores from random forest are
   * not reliable for this type of data.
   * </ul>
   *
   * @param x the training instances.
   * @param y the response variable.
   * @param attributes the attribute properties. If not provided, all attributes
   *                   are treated as numeric values.
   * @param T the number of trees.
   * @param mtry the number of random selected features to be used to determine
   *             the decision at a node of the tree. floor(sqrt(dim)) seems to give
   *             generally good performance, where dim is the number of variables.
   * @param J maximum number of leaf nodes.
   * @param splitRule Decision tree node split rule.
   * @param classWeight Priors of the classes.
   *
   * @return Random forest classification model.
   */
  def randomForest(x: Array[Array[Double]], y: Array[Int], attributes: Array[Attribute] = null, T: Int = 500, mtry: Int = -1, J: Int = -1, splitRule: DecisionTree.SplitRule = DecisionTree.SplitRule.GINI, classWeight: Array[Int] = null): RandomForest = {
    val k = Math.max(y: _*) + 1

    val attr = if (attributes == null) {
      val attr = new Array[Attribute](k)
      for (i <- 0 until k) attr(i) = new NumericAttribute(s"V$i")
      attr
    } else attributes

    val m = if (mtry <= 0) Math.floor(Math.sqrt(k)).toInt else mtry

    val j = if (J <= 1) Math.min(500, x.length / 50) else J

    val weight = if (classWeight == null) Array.fill[Int](k)(1) else classWeight

    time {
      new RandomForest(attr, x, y, T, m, j, splitRule, weight)
    }
  }

  /**
   * Gradient boosting for classification. Gradient boosting is typically used
   * with decision trees (especially CART regression trees) of a fixed size as
   * base learners. For this special case Friedman proposes a modification to
   * gradient boosting method which improves the quality of fit of each base
   * learner.
   * <p>
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
   * <p>
   * Fitting the training set too closely can lead to degradation of the model's
   * generalization ability. Several so-called regularization techniques reduce
   * this over-fitting effect by constraining the fitting procedure.
   * One natural regularization parameter is the number of gradient boosting
   * iterations T (i.e. the number of trees in the model when the base learner
   * is a decision tree). Increasing T reduces the error on training set,
   * but setting it too high may lead to over-fitting. An optimal value of T
   * is often selected by monitoring prediction error on a separate validation
   * data set.
   * <p>
   * Another regularization approach is the shrinkage which times a parameter
   * &eta; (called the "learning rate") to update term.
   * Empirically it has been found that using small learning rates (such as
   * &eta; &lt; 0.1) yields dramatic improvements in model's generalization ability
   * over gradient boosting without shrinking (&eta; = 1). However, it comes at
   * the price of increasing computational time both during training and
   * prediction: lower learning rate requires more iterations.
   * <p>
   * Soon after the introduction of gradient boosting Friedman proposed a
   * minor modification to the algorithm, motivated by Breiman's bagging method.
   * Specifically, he proposed that at each iteration of the algorithm, a base
   * learner should be fit on a subsample of the training set drawn at random
   * without replacement. Friedman observed a substantial improvement in
   * gradient boosting's accuracy with this modification.
   * <p>
   * Subsample size is some constant fraction f of the size of the training set.
   * When f = 1, the algorithm is deterministic and identical to the one
   * described above. Smaller values of f introduce randomness into the
   * algorithm and help prevent over-fitting, acting as a kind of regularization.
   * The algorithm also becomes faster, because regression trees have to be fit
   * to smaller datasets at each iteration. Typically, f is set to 0.5, meaning
   * that one half of the training set is used to build each base learner.
   * <p>
   * Also, like in bagging, sub-sampling allows one to define an out-of-bag
   * estimate of the prediction performance improvement by evaluating predictions
   * on those observations which were not used in the building of the next
   * base learner. Out-of-bag estimates help avoid the need for an independent
   * validation dataset, but often underestimate actual performance improvement
   * and the optimal number of iterations.
   * <p>
   * Gradient tree boosting implementations often also use regularization by
   * limiting the minimum number of observations in trees' terminal nodes.
   * It's used in the tree building process by ignoring any splits that lead
   * to nodes containing fewer than this number of training set instances.
   * Imposing this limit helps to reduce variance in predictions at leaves.
   *
   * <h2>References</h2>
   * <ol>
   * <li> J. H. Friedman. Greedy Function Approximation: A Gradient Boosting Machine, 1999.</li>
   * <li> J. H. Friedman. Stochastic Gradient Boosting, 1999.</li>
   * </ol>
   *
   * @param x the training instances.
   * @param y the class labels.
   * @param attributes the attribute properties. If not provided, all attributes
   *                   are treated as numeric values.
   * @param T the number of iterations (trees).
   * @param J the number of leaves in each tree.
   * @param eta the shrinkage parameter in (0, 1] controls the learning rate of procedure.
   * @param f the sampling fraction for stochastic tree boosting.
   *
   * @return Gradient boosted trees.
   */
  def gbm(x: Array[Array[Double]], y: Array[Int], attributes: Array[Attribute] = null, T: Int = 500, J: Int = 6, eta: Double = 0.05, f: Double = 0.7) {
    val k = Math.max(y: _*) + 1

    val attr = if (attributes == null) {
      val attr = new Array[Attribute](k)
      for (i <- 0 until k) attr(i) = new NumericAttribute(s"V$i")
      attr
    } else attributes

    time {
      new GradientTreeBoost(attr, x, y, T, J, eta, f)
    }
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
   * <p>
   * AdaBoost calls a weak classifier repeatedly in a series of rounds from
   * total T classifiers. For each call a distribution of weights is updated
   * that indicates the importance of examples in the data set for the
   * classification. On each round, the weights of each incorrectly classified
   * example are increased (or alternatively, the weights of each correctly
   * classified example are decreased), so that the new classifier focuses more
   * on those examples.
   * <p>
   * The basic AdaBoost algorithm is only for binary classification problem.
   * For multi-class classification, a common approach is reducing the
   * multi-class classification problem to multiple two-class problems.
   * This implementation is a multi-class AdaBoost without such reductions.
   *
   * <h2>References</h2>
   * <ol>
   * <li> Yoav Freund, Robert E. Schapire. A Decision-Theoretic Generalization of on-Line Learning and an Application to Boosting, 1995.</li>
   * <li> Ji Zhu, Hui Zhou, Saharon Rosset and Trevor Hastie. Multi-class Adaboost, 2009.</li>
   * </ol>
   *
   * @param x the training instances.
   * @param y the response variable.
   * @param attributes the attribute properties. If not provided, all attributes
   *                   are treated as numeric values.
   * @param T the number of trees.
   * @param J the maximum number of leaf nodes in the trees.
   *
   * @return AdaBoost model.
   */
  def adaboost(x: Array[Array[Double]], y: Array[Int], attributes: Array[Attribute] = null, T: Int = 500, J: Int = 2): AdaBoost = {
    val k = Math.max(y: _*) + 1

    val attr = if (attributes == null) {
      val attr = new Array[Attribute](k)
      for (i <- 0 until k) attr(i) = new NumericAttribute(s"V$i")
      attr
    } else attributes

    time {
      new AdaBoost(attr, x, y, T, J)
    }
  }

  /**
   * Fisher's linear discriminant. Fisher defined the separation between two
   * distributions to be the ratio of the variance between the classes to
   * the variance within the classes, which is, in some sense, a measure
   * of the signal-to-noise ratio for the class labeling. FLD finds a linear
   * combination of features which maximizes the separation after the projection.
   * The resulting combination may be used for dimensionality reduction
   * before later classification.
   * <p>
   * The terms Fisher's linear discriminant and LDA are often used
   * interchangeably, although FLD actually describes a slightly different
   * discriminant, which does not make some of the assumptions of LDA such
   * as normally distributed classes or equal class covariances.
   * When the assumptions of LDA are satisfied, FLD is equivalent to LDA.
   * <p>
   * FLD is also closely related to principal component analysis (PCA), which also
   * looks for linear combinations of variables which best explain the data.
   * As a supervised method, FLD explicitly attempts to model the
   * difference between the classes of data. On the other hand, PCA is a
   * unsupervised method and does not take into account any difference in class.
   * <p>
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
  def fisher(x: Array[Array[Double]], y: Array[Int], L: Int = -1, tol: Double = 1E-4): FLD = {
    time {
      new FLD(x, y, L, tol)
    }
  }

  /**
   * Linear discriminant analysis. LDA is based on the Bayes decision theory
   * and assumes that the conditional probability density functions are normally
   * distributed. LDA also makes the simplifying homoscedastic assumption (i.e.
   * that the class covariances are identical) and that the covariances have full
   * rank. With these assumptions, the discriminant function of an input being
   * in a class is purely a function of this linear combination of independent
   * variables.
   * <p>
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
   * <p>
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
  def lda(x: Array[Array[Double]], y: Array[Int], priori: Array[Double] = null, tol: Double = 1E-4): LDA = {
    time {
      new LDA(x, y, priori, tol)
    }
  }

  /**
   * Quadratic discriminant analysis. QDA is closely related to linear discriminant
   * analysis (LDA). Like LDA, QDA models the conditional probability density
   * functions as a Gaussian distribution, then uses the posterior distributions
   * to estimate the class for a given test data. Unlike LDA, however,
   * in QDA there is no assumption that the covariance of each of the classes
   * is identical. Therefore, the resulting separating surface between
   * the classes is quadratic.
   * <p>
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
  def qda(x: Array[Array[Double]], y: Array[Int], priori: Array[Double] = null, tol: Double = 1E-4): QDA = {
    time {
      new QDA(x, y, priori, tol)
    }
  }

  /**
   * Regularized discriminant analysis. RDA is a compromise between LDA and QDA,
   * which allows one to shrink the separate covariances of QDA toward a common
   * variance as in LDA. This method is very similar in flavor to ridge regression.
   * The regularized covariance matrices of each class is
   * &Sigma;<sub>k</sub>(&alpha;) = &alpha; &Sigma;<sub>k</sub> + (1 - &alpha;) &Sigma;.
   * The quadratic discriminant function is defined using the shrunken covariance
   * matrices &Sigma;<sub>k</sub>(&alpha;). The parameter &alpha; in [0, 1]
   * controls the complexity of the model. When &alpha; is one, RDA becomes QDA.
   * While &alpha; is zero, RDA is equivalent to LDA. Therefore, the
   * regularization factor &alpha; allows a continuum of models between LDA and QDA.
   *
   * @param x training samples.
   * @param y training labels in [0, k), where k is the number of classes.
   * @param alpha regularization factor in [0, 1] allows a continuum of models
   *              between LDA and QDA.
   * @param priori the priori probability of each class.
   * @param tol tolerance to decide if a covariance matrix is singular; it
   *            will reject variables whose variance is less than tol<sup>2</sup>.
   *
   * @return Regularized discriminant analysis model.
   */
  def rda(x: Array[Array[Double]], y: Array[Int], alpha: Double, priori: Array[Double] = null, tol: Double = 1E-4): RDA = {
    time {
      new RDA(x, y, priori, alpha, tol)
    }
  }
}