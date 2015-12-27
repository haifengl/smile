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
   * @param ntrees the number of trees.
   * @param mtry the number of random selected features to be used to determine
   *             the decision at a node of the tree. floor(sqrt(dim)) seems to give
   *             generally good performance, where dim is the number of variables.
   * @param maxLeafNodes maximum number of leaf nodes.
   * @param splitRule Decision tree node split rule.
   * @param classWeight Priors of the classes.
   * @return Random forest model.
   */
  def randomForest(x: Array[Array[Double]], y: Array[Int], attributes: Array[Attribute] = null, ntrees: Int = 500, mtry: Int = -1, maxLeafNodes: Int = 500, splitRule: DecisionTree.SplitRule = DecisionTree.SplitRule.GINI, classWeight: Array[Int] = null): RandomForest = {
    val k = Math.max(y: _*) + 1

    val attr = if (attributes == null) {
      val attr = new Array[Attribute](k)
      for (i <- 0 until k) attr(i) = new NumericAttribute(s"V$i")
      attr
    } else attributes

    val m = if (mtry <= 0) Math.floor(Math.sqrt(k)).toInt else mtry

    val weight = if (classWeight == null) Array.fill[Int](k)(1) else classWeight

    time {
      new RandomForest(attr, x, y, ntrees, m, maxLeafNodes, splitRule, weight)
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
   * @param x the training instances.
   * @param y the class labels.
   * @param attributes the attribute properties. If not provided, all attributes
   *                   are treated as numeric values.
   * @param T the number of iterations (trees).
   * @param J the number of leaves in each tree.
   * @param eta the shrinkage parameter in (0, 1] controls the learning rate of procedure.
   * @param f the sampling fraction for stochastic tree boosting.
   */
  def gbm(x: Array[Array[Double]], y: Array[Int], attributes: Array[Attribute] = null, T: Int = 200, J: Int = 6, eta: Double = 0.05, f: Double = 0.7) {
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
}