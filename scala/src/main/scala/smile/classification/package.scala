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

/** Classification algorithms. In machine learning and pattern recognition,
  * classification refers to an algorithmic procedure for assigning a given
  * input object into one of a given number of categories. The input
  * object is formally termed an instance, and the categories are termed classes.
  *
  * The instance is usually described by a vector of features, which together
  * constitute a description of all known characteristics of the instance.
  * Typically, features are either categorical (also known as nominal, i.e.
  * consisting of one of a set of unordered items, such as a gender of "male"
  * or "female", or a blood type of "A", "B", "AB" or "O"), ordinal (consisting
  * of one of a set of ordered items, e.g. "large", "medium" or "small"),
  * integer-valued (e.g. a count of the number of occurrences of a particular
  * word in an email) or real-valued (e.g. a measurement of blood pressure).
  *
  * Classification normally refers to a supervised procedure, i.e. a procedure
  * that produces an inferred function to predict the output value of new
  * instances based on a training set of pairs consisting of an input object
  * and a desired output value. The inferred function is called a classifier
  * if the output is discrete or a regression function if the output is
  * continuous.
  *
  * The inferred function should predict the correct output value for any valid
  * input object. This requires the learning algorithm to generalize from the
  * training data to unseen situations in a "reasonable" way.
  *
  * A wide range of supervised learning algorithms is available, each with
  * its strengths and weaknesses. There is no single learning algorithm that
  * works best on all supervised learning problems. The most widely used
  * learning algorithms are AdaBoost and gradient boosting, support vector
  * machines, linear regression, linear discriminant analysis, logistic
  * regression, naive Bayes, decision trees, k-nearest neighbor algorithm,
  * and neural networks (multilayer perceptron).
  *
  * If the feature vectors include features of many different kinds (discrete,
  * discrete ordered, counts, continuous values), some algorithms cannot be
  * easily applied. Many algorithms, including linear regression, logistic
  * regression, neural networks, and nearest neighbor methods, require that
  * the input features be numerical and scaled to similar ranges (e.g., to
  * the [-1,1] interval). Methods that employ a distance function, such as
  * nearest neighbor methods and support vector machines with Gaussian kernels,
  * are particularly sensitive to this. An advantage of decision trees (and
  * boosting algorithms based on decision trees) is that they easily handle
  * heterogeneous data.
  *
  * If the input features contain redundant information (e.g., highly correlated
  * features), some learning algorithms (e.g., linear regression, logistic
  * regression, and distance based methods) will perform poorly because of
  * numerical instabilities. These problems can often be solved by imposing
  * some form of regularization.
  *
  * If each of the features makes an independent contribution to the output,
  * then algorithms based on linear functions (e.g., linear regression,
  * logistic regression, linear support vector machines, naive Bayes) generally
  * perform well. However, if there are complex interactions among features,
  * then algorithms such as nonlinear support vector machines, decision trees
  * and neural networks work better. Linear methods can also be applied, but
  * the engineer must manually specify the interactions when using them.
  *
  * There are several major issues to consider in supervised learning:
  *
  *  - '''Features:'''
  * The accuracy of the inferred function depends strongly on how the input
  * object is represented. Typically, the input object is transformed into
  * a feature vector, which contains a number of features that are descriptive
  * of the object. The number of features should not be too large, because of
  * the curse of dimensionality; but should contain enough information to
  * accurately predict the output.
  * There are many algorithms for feature selection that seek to identify
  * the relevant features and discard the irrelevant ones. More generally,
  * dimensionality reduction may seek to map the input data into a lower
  * dimensional space prior to running the supervised learning algorithm.
  *
  *  - '''Overfitting:'''
  * Overfitting occurs when a statistical model describes random error
  * or noise instead of the underlying relationship. Overfitting generally
  * occurs when a model is excessively complex, such as having too many
  * parameters relative to the number of observations. A model which has
  * been overfit will generally have poor predictive performance, as it can
  * exaggerate minor fluctuations in the data.
  * The potential for overfitting depends not only on the number of parameters
  * and data but also the conformability of the model structure with the data
  * shape, and the magnitude of model error compared to the expected level
  * of noise or error in the data.
  * In order to avoid overfitting, it is necessary to use additional techniques
  * (e.g. cross-validation, regularization, early stopping, pruning, Bayesian
  * priors on parameters or model comparison), that can indicate when further
  * training is not resulting in better generalization. The basis of some
  * techniques is either (1) to explicitly penalize overly complex models,
  * or (2) to test the model's ability to generalize by evaluating its
  * performance on a set of data not used for training, which is assumed to
  * approximate the typical unseen data that a model will encounter.
  *
  *  - '''Regularization:'''
  * Regularization involves introducing additional information in order
  * to solve an ill-posed problem or to prevent over-fitting. This information
  * is usually of the form of a penalty for complexity, such as restrictions
  * for smoothness or bounds on the vector space norm.
  * A theoretical justification for regularization is that it attempts to impose
  * Occam's razor on the solution. From a Bayesian point of view, many
  * regularization techniques correspond to imposing certain prior distributions
  * on model parameters.
  *
  *  - '''Bias-variance tradeoff:'''
  * Mean squared error (MSE) can be broken down into two components:
  * variance and squared bias, known as the bias-variance decomposition.
  * Thus in order to minimize the MSE, we need to minimize both the bias and
  * the variance. However, this is not trivial. Therefore, there is a tradeoff
  * between bias and variance.
  *
  * @author Haifeng Li
  */
package object classification extends Operators {

}
