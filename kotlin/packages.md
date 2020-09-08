# Module smile-kotlin

[Smile (Statistical Machine Intelligence and Learning Engine)](https://haifengl.github.io/)
is a fast and comprehensive machine learning, NLP, linear algebra,
graph, interpolation, and visualization system in Java and Scala.
With advanced data structures and algorithms,
Smile delivers state-of-art performance.

Smile covers every aspect of machine learning, including classification,
regression, clustering, association rule mining, feature selection,
manifold learning, multidimensional scaling, genetic algorithms,
missing value imputation, efficient nearest neighbor search, etc.

# Package smile

Data loading/saving utilities.

# Package smile.association

Frequent item set mining and association rule mining.

Association rule learning is a popular and well researched method for
discovering interesting relations between variables in large databases.
Let I = {i<sub>1</sub>, i<sub>2</sub>,..., i<sub>n</sub>} be a set of n
binary attributes called items. Let D = {t<sub>1</sub>, t<sub>2</sub>,..., t<sub>m</sub>}
be a set of transactions called the database. Each transaction in D has a
unique transaction ID and contains a subset of the items in I.
An association rule is defined as an implication of the form X &rArr; Y
where X, Y &sube; I and X &cap; Y = &Oslash;. The item sets X and Y are called
antecedent (left-hand-side or LHS) and consequent (right-hand-side or RHS)
of the rule, respectively. The support supp(X) of an item set X is defined as
the proportion of transactions in the database which contain the item set.
Note that the support of an association rule X &rArr; Y is supp(X &cup; Y).
The confidence of a rule is defined conf(X &rArr; Y) = supp(X &cup; Y) / supp(X).
Confidence can be interpreted as an estimate of the probability P(Y | X),
the probability of finding the RHS of the rule in transactions under the
condition that these transactions also contain the LHS.

For example, the rule {onions, potatoes} &rArr; {burger} found in the sales
data of a supermarket would indicate that if a customer buys onions and
potatoes together, he or she is likely to also buy burger. Such information
can be used as the basis for decisions about marketing activities such as
promotional pricing or product placements.

Association rules are usually required to satisfy a user-specified minimum
support and a user-specified minimum confidence at the same time. Association
rule generation is usually split up into two separate steps:

- First, minimum support is applied to find all frequent item sets
  in a database (i.e. frequent item set mining).
- Second, these frequent item sets and the minimum confidence constraint
  are used to form rules.

Finding all frequent item sets in a database is difficult since it involves
searching all possible item sets (item combinations). The set of possible
item sets is the power set over I (the set of items) and has size 2<sup>n</sup> - 1
(excluding the empty set which is not a valid item set). Although the size
of the power set grows exponentially in the number of items n in I, efficient
search is possible using the downward-closure property of support
(also called anti-monotonicity) which guarantees that for a frequent item set
also all its subsets are frequent and thus for an infrequent item set, all
its supersets must be infrequent.

In practice, we may only consider the frequent item set that has the maximum
number of items bypassing all the sub item sets. An item set is maximal
frequent if none of its immediate supersets is frequent.

For a maximal frequent item set, even though we know that all the sub item
sets are frequent, we don't know the actual support of those sub item sets,
which are very important to find the association rules within the item sets.
If the final goal is association rule mining, we would like to discover
closed frequent item sets. An item set is closed if none of its immediate
supersets has the same support as the item set.

Some well known algorithms of frequent item set mining are Apriori,
Eclat and FP-Growth. Apriori is the best-known algorithm to mine association
rules. It uses a breadth-first search strategy to counting the support of
item sets and uses a candidate generation function which exploits the downward
closure property of support. Eclat is a depth-first search algorithm using
set intersection.

FP-growth (frequent pattern growth) uses an extended prefix-tree (FP-tree)
structure to store the database in a compressed form. FP-growth adopts a
divide-and-conquer approach to decompose both the mining tasks and the
databases. It uses a pattern fragment growth method to avoid the costly
process of candidate generation and testing used by Apriori.

# Package smile.classification

Classification algorithms.
 
In machine learning and pattern recognition,
classification refers to an algorithmic procedure for assigning a given
input object into one of a given number of categories. The input
object is formally termed an instance, and the categories are termed classes.

The instance is usually described by a vector of features, which together
constitute a description of all known characteristics of the instance.
Typically, features are either categorical (also known as nominal, i.e.
consisting of one of a set of unordered items, such as a gender of "male"
or "female", or a blood type of "A", "B", "AB" or "O"), ordinal (consisting
of one of a set of ordered items, e.g. "large", "medium" or "small"),
integer-valued (e.g. a count of the number of occurrences of a particular
word in an email) or real-valued (e.g. a measurement of blood pressure).

Classification normally refers to a supervised procedure, i.e. a procedure
that produces an inferred function to predict the output value of new
instances based on a training set of pairs consisting of an input object
and a desired output value. The inferred function is called a classifier
if the output is discrete or a regression function if the output is
continuous.

The inferred function should predict the correct output value for any valid
input object. This requires the learning algorithm to generalize from the
training data to unseen situations in a "reasonable" way.

A wide range of supervised learning algorithms is available, each with
its strengths and weaknesses. There is no single learning algorithm that
works best on all supervised learning problems. The most widely used
learning algorithms are AdaBoost and gradient boosting, support vector
machines, linear regression, linear discriminant analysis, logistic
regression, naive Bayes, decision trees, k-nearest neighbor algorithm,
and neural networks (multilayer perceptron).

If the feature vectors include features of many different kinds (discrete,
discrete ordered, counts, continuous values), some algorithms cannot be
easily applied. Many algorithms, including linear regression, logistic
regression, neural networks, and nearest neighbor methods, require that
the input features be numerical and scaled to similar ranges (e.g., to
the `[-1,1]` interval). Methods that employ a distance function, such as
nearest neighbor methods and support vector machines with Gaussian kernels,
are particularly sensitive to this. An advantage of decision trees (and
boosting algorithms based on decision trees) is that they easily handle
heterogeneous data.

If the input features contain redundant information (e.g., highly correlated
features), some learning algorithms (e.g., linear regression, logistic
regression, and distance based methods) will perform poorly because of
numerical instabilities. These problems can often be solved by imposing
some form of regularization.

If each of the features makes an independent contribution to the output,
then algorithms based on linear functions (e.g., linear regression,
logistic regression, linear support vector machines, naive Bayes) generally
perform well. However, if there are complex interactions among features,
then algorithms such as nonlinear support vector machines, decision trees
and neural networks work better. Linear methods can also be applied, but
the engineer must manually specify the interactions when using them.

There are several major issues to consider in supervised learning:

- **Features**: The accuracy of the inferred function depends strongly on how the input
object is represented. Typically, the input object is transformed into
a feature vector, which contains a number of features that are descriptive
of the object. The number of features should not be too large, because of
the curse of dimensionality; but should contain enough information to
accurately predict the output.
There are many algorithms for feature selection that seek to identify
the relevant features and discard the irrelevant ones. More generally,
dimensionality reduction may seek to map the input data into a lower
dimensional space prior to running the supervised learning algorithm.

- **Overfitting**:
Overfitting occurs when a statistical model describes random error
or noise instead of the underlying relationship. Overfitting generally
occurs when a model is excessively complex, such as having too many
parameters relative to the number of observations. A model which has
been overfit will generally have poor predictive performance, as it can
exaggerate minor fluctuations in the data.
The potential for overfitting depends not only on the number of parameters
and data but also the conformability of the model structure with the data
shape, and the magnitude of model error compared to the expected level
of noise or error in the data.
In order to avoid overfitting, it is necessary to use additional techniques
(e.g. cross-validation, regularization, early stopping, pruning, Bayesian
priors on parameters or model comparison), that can indicate when further
training is not resulting in better generalization. The basis of some
techniques is either (1) to explicitly penalize overly complex models,
or (2) to test the model's ability to generalize by evaluating its
performance on a set of data not used for training, which is assumed to
approximate the typical unseen data that a model will encounter.

- **Regularization**:
Regularization involves introducing additional information in order
to solve an ill-posed problem or to prevent over-fitting. This information
is usually of the form of a penalty for complexity, such as restrictions
for smoothness or bounds on the vector space norm.
A theoretical justification for regularization is that it attempts to impose
Occam's razor on the solution. From a Bayesian point of view, many
regularization techniques correspond to imposing certain prior distributions
on model parameters.

- **Bias-variance tradeoff**:
Mean squared error (MSE) can be broken down into two components:
variance and squared bias, known as the bias-variance decomposition.
Thus in order to minimize the MSE, we need to minimize both the bias and
the variance. However, this is not trivial. Therefore, there is a tradeoff
between bias and variance.

# Package smile.clustering

Clustering analysis.
 
Clustering is the assignment of a set of observations
into subsets (called clusters) so that observations in the same cluster are
similar in some sense. Clustering is a method of unsupervised learning,
and a common technique for statistical data analysis used in many fields.

Hierarchical algorithms find successive clusters using previously
established clusters. These algorithms usually are either agglomerative
("bottom-up") or divisive ("top-down"). Agglomerative algorithms begin
with each element as a separate cluster and merge them into successively
larger clusters. Divisive algorithms begin with the whole set and proceed
to divide it into successively smaller clusters.

Partitional algorithms typically determine all clusters at once, but can
also be used as divisive algorithms in the hierarchical clustering.
Many partitional clustering algorithms require the specification of
the number of clusters to produce in the input data set, prior to
execution of the algorithm. Barring knowledge of the proper value
beforehand, the appropriate value must be determined, a problem on
its own for which a number of techniques have been developed.

Density-based clustering algorithms are devised to discover
arbitrary-shaped clusters. In this approach, a cluster is regarded as
a region in which the density of data objects exceeds a threshold.

Subspace clustering methods look for clusters that can only be seen in
a particular projection (subspace, manifold) of the data. These methods
thus can ignore irrelevant attributes. The general problem is also known
as Correlation clustering while the special case of axis-parallel subspaces
is also known as two-way clustering, co-clustering or biclustering in
bioinformatics: in these methods not only the objects are clustered but
also the features of the objects, i.e., if the data is represented in
a data matrix, the rows and columns are clustered simultaneously. They
usually do not however work with arbitrary feature combinations as in general
subspace methods.
  
# Package smile.manifold

Manifold learning finds a low-dimensional basis for describing
high-dimensional data. 

Manifold learning is a popular approach to nonlinear
dimensionality reduction. Algorithms for this task are based on the idea
that the dimensionality of many data sets is only artificially high; though
each data point consists of perhaps thousands of features, it may be
described as a function of only a few underlying parameters. That is, the
data points are actually samples from a low-dimensional manifold that is
embedded in a high-dimensional space. Manifold learning algorithms attempt
to uncover these parameters in order to find a low-dimensional representation
of the data.

Some prominent approaches are locally linear embedding
(LLE), Hessian LLE, Laplacian eigenmaps, and LTSA. These techniques
construct a low-dimensional data representation using a cost function
that retains local properties of the data, and can be viewed as defining
a graph-based kernel for Kernel PCA. More recently, techniques have been
proposed that, instead of defining a fixed kernel, try to learn the kernel
using semidefinite programming. The most prominent example of such a
technique is maximum variance unfolding (MVU). The central idea of MVU
is to exactly preserve all pairwise distances between nearest neighbors
(in the inner product space), while maximizing the distances between points
that are not nearest neighbors.

An alternative approach to neighborhood preservation is through the
minimization of a cost function that measures differences between
distances in the input and output spaces. Important examples of such
techniques include classical multidimensional scaling (which is identical
to PCA), Isomap (which uses geodesic distances in the data space), diffusion
maps (which uses diffusion distances in the data space), t-SNE (which
minimizes the divergence between distributions over pairs of points),
and curvilinear component analysis.
  
# Package smile.mds

Multidimensional scaling.
 
MDS is a set of related statistical techniques
often used in information visualization for exploring similarities or
dissimilarities in data. An MDS algorithm starts with a matrix of item-item
similarities, then assigns a location to each item in N-dimensional space.
For sufficiently small N, the resulting locations may be displayed in a
graph or 3D visualization.

The major types of MDS algorithms include:

- **Classical multidimensional scaling** takes an input matrix giving
 dissimilarities between pairs of items and
outputs a coordinate matrix whose configuration minimizes a loss function
called strain.

- **Metric multidimensional scaling** is
a superset of classical MDS that generalizes the optimization procedure
to a variety of loss functions and input matrices of known distances with
weights and so on. A useful loss function in this context is called stress
which is often minimized using a procedure called stress majorization.

- **Non-metric multidimensional scaling** finds both a non-parametric
monotonic relationship between the dissimilarities in the item-item matrix
and the Euclidean distances between items, and the location of each item in
the low-dimensional space. The relationship is typically found using isotonic
regression.

- **Generalized multidimensional scaling** is
an extension of metric multidimensional scaling, in which the target
space is an arbitrary smooth non-Euclidean space. In case when the
dissimilarities are distances on a surface and the target space is another
surface, GMDS allows finding the minimum-distortion embedding of one surface
into another.
  
# Package smile.projection

Feature extraction.

Feature extraction transforms the data in the
high-dimensional space to a space of fewer dimensions. The data
transformation may be linear, as in principal component analysis (PCA),
but many nonlinear dimensionality reduction techniques also exist.

The main linear technique for dimensionality reduction, principal component
analysis, performs a linear mapping of the data to a lower dimensional
space in such a way that the variance of the data in the low-dimensional
representation is maximized. In practice, the correlation matrix of the
data is constructed and the eigenvectors on this matrix are computed.
The eigenvectors that correspond to the largest eigenvalues (the principal
components) can now be used to reconstruct a large fraction of the variance
of the original data. Moreover, the first few eigenvectors can often be
interpreted in terms of the large-scale physical behavior of the system.
The original space has been reduced (with data loss, but hopefully
retaining the most important variance) to the space spanned by a few
eigenvectors.

Compared to regular batch PCA algorithm, the generalized Hebbian algorithm
is an adaptive method to find the largest k eigenvectors of the covariance
matrix, assuming that the associated eigenvalues are distinct. GHA works
with an arbitrarily large sample size and the storage requirement is modest.
Another attractive feature is that, in a nonstationary environment, it
has an inherent ability to track gradual changes in the optimal solution
in an inexpensive way.

Random projection is a promising linear dimensionality reduction technique
for learning mixtures of Gaussians. The key idea of random projection arises
from the Johnson-Lindenstrauss lemma: if points in a vector space are
projected onto a randomly selected subspace of suitably high dimension,
then the distances between the points are approximately preserved.

Principal component analysis can be employed in a nonlinear way by means
of the kernel trick. The resulting technique is capable of constructing
nonlinear mappings that maximize the variance in the data. The resulting
technique is entitled Kernel PCA. Other prominent nonlinear techniques
include manifold learning techniques such as locally linear embedding
(LLE), Hessian LLE, Laplacian eigenmaps, and LTSA. These techniques
construct a low-dimensional data representation using a cost function
that retains local properties of the data, and can be viewed as defining
a graph-based kernel for Kernel PCA. More recently, techniques have been
proposed that, instead of defining a fixed kernel, try to learn the kernel
using semidefinite programming. The most prominent example of such a
technique is maximum variance unfolding (MVU). The central idea of MVU
is to exactly preserve all pairwise distances between nearest neighbors
(in the inner product space), while maximizing the distances between points
that are not nearest neighbors.

An alternative approach to neighborhood preservation is through the
minimization of a cost function that measures differences between
distances in the input and output spaces. Important examples of such
techniques include classical multidimensional scaling (which is identical
to PCA), Isomap (which uses geodesic distances in the data space), diffusion
maps (which uses diffusion distances in the data space), t-SNE (which
minimizes the divergence between distributions over pairs of points),
and curvilinear component analysis.

A different approach to nonlinear dimensionality reduction is through the
use of autoencoders, a special kind of feed-forward neural networks with
a bottle-neck hidden layer. The training of deep encoders is typically
performed using a greedy layer-wise pre-training (e.g., using a stack of
Restricted Boltzmann machines) that is followed by a finetuning stage based
on backpropagation.
  
# Package smile.regression

Regression analysis.

Regression analysis includes any
techniques for modeling and analyzing several variables, when the focus
is on the relationship between a dependent variable and one or more
independent variables. Most commonly, regression analysis estimates the
conditional expectation of the dependent variable given the independent
variables. Therefore, the estimation target is a function of the independent
variables called the regression function. Regression analysis is widely
used for prediction and forecasting.
  
# Package smile.nlp

Natural language processing.

# Package smile.wavelet

Wavelet analysis.

A wavelet is a wave-like oscillation with an amplitude that starts out at
zero, increases, and then decreases back to zero. Like the fast Fourier
transform (FFT), the discrete wavelet transform (DWT) is a fast, linear
operation that operates on a data vector whose length is an integer power
of 2, transforming it into a numerically different vector of the same length.
The wavelet transform is invertible and in fact orthogonal. Both FFT and DWT
can be viewed as a rotation in function space.
