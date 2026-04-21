# SMILE — Classification User Guide

The `smile.classification` package provides a comprehensive suite of classification algorithms.
This guide explains the core abstractions, describes each algorithm with its API, key parameters,
and usage patterns, and ends with a comparative summary and practical tips.

---

## Table of Contents

1. [Package Overview](#1-package-overview)
2. [Core Abstractions](#2-core-abstractions)
   - 2.1 [Classifier Interface](#21-classifier-interface)
   - 2.2 [AbstractClassifier](#22-abstractclassifier)
   - 2.3 [DataFrameClassifier](#23-dataframeclassifier)
   - 2.4 [ClassLabels](#24-classlabels)
3. [Discriminant Analysis Classifiers](#3-discriminant-analysis-classifiers)
   - 3.1 [LDA — Linear Discriminant Analysis](#31-lda--linear-discriminant-analysis)
   - 3.2 [QDA — Quadratic Discriminant Analysis](#32-qda--quadratic-discriminant-analysis)
   - 3.3 [RDA — Regularized Discriminant Analysis](#33-rda--regularized-discriminant-analysis)
   - 3.4 [FLD — Fisher's Linear Discriminant](#34-fld--fishers-linear-discriminant)
4. [Generative Text Classifiers](#4-generative-text-classifiers)
   - 4.1 [NaiveBayes](#41-naivebayes)
   - 4.2 [DiscreteNaiveBayes](#42-discretenaivebayes)
5. [Regression-based Classifiers](#5-regression-based-classifiers)
   - 5.1 [LogisticRegression](#51-logisticregression)
   - 5.2 [SparseLogisticRegression](#52-sparselogisticregression)
   - 5.3 [Maxent — Maximum Entropy](#53-maxent--maximum-entropy)
6. [Tree and Ensemble Classifiers](#6-tree-and-ensemble-classifiers)
   - 6.1 [DecisionTree](#61-decisiontree)
   - 6.2 [RandomForest](#62-randomforest)
   - 6.3 [AdaBoost](#63-adaboost)
   - 6.4 [GradientTreeBoost](#64-gradienttreeboost)
7. [Distance-based Classifiers](#7-distance-based-classifiers)
   - 7.1 [KNN — K-Nearest Neighbors](#71-knn--k-nearest-neighbors)
   - 7.2 [RBFNetwork — Radial Basis Function Network](#72-rbfnetwork--radial-basis-function-network)
8. [Support Vector Machines](#8-support-vector-machines)
   - 8.1 [SVM](#81-svm)
   - 8.2 [LinearSVM, SparseLinearSVM, BinarySparseLinearSVM](#82-linearsvm-sparselinearsvm-binarysparselinearsvm)
9. [Neural Network](#9-neural-network)
   - 9.1 [MLP — Multilayer Perceptron](#91-mlp--multilayer-perceptron)
10. [Probability Calibration](#10-probability-calibration)
    - 10.1 [PlattScaling](#101-plattscaling)
    - 10.2 [IsotonicRegressionScaling](#102-isotonicregressionsscaling)
11. [Multiclass Reduction Strategies](#11-multiclass-reduction-strategies)
    - 11.1 [OneVersusRest](#111-oneversusrest)
    - 11.2 [OneVersusOne](#112-oneversusone)
12. [Algorithm Comparison](#12-algorithm-comparison)
13. [Common Patterns and Tips](#13-common-patterns-and-tips)

---

## 1. Package Overview

The `smile.classification` package provides a complete suite of supervised classification
algorithms for labelling new instances with one of a discrete set of class labels. The
package follows a consistent design:

- **Static `fit(…)` factory methods** — every algorithm exposes one or more `fit` methods;
  there is no mutable builder to misconfigure.
- **Arbitrary integer labels** — class labels can be any `int` values, not just `0..k-1`.
  Internal encoding and decoding is handled automatically by `ClassLabels` and `IntSet`.
- **Uniform `Classifier<T>` interface** — hard prediction (`predict`), soft prediction
  (`predict` with a posteriori array), raw score (`score`), online update (`update`), and
  capability flags (`isSoft`, `isOnline`) are all part of one interface.
- **Serializable** — every model implements `java.io.Serializable` for persistence.
- **Properties round-trip** — hyperparameters can be read from and written to
  `java.util.Properties` via the nested `Options` record and the `Trainer` interface,
  enabling configuration-file-driven workflows.

---

## 2. Core Abstractions

### 2.1 `Classifier` Interface

```java
public interface Classifier<T> extends ToIntFunction<T>, ToDoubleFunction<T>, Serializable
```

The central contract that every classification model implements.

| Method | Description |
|--------|-------------|
| `int numClasses()` | Number of distinct classes. |
| `int[] classes()` | The original class labels (e.g. `{-1, +1}` or `{3, 7, 14}`). |
| `int predict(T x)` | Hard prediction — returns the predicted class label. |
| `double score(T x)` | Raw decision score (default: throws `UnsupportedOperationException`). |
| `boolean isSoft()` | `true` if `predict(T, double[])` is available. |
| `boolean isOnline()` | `true` if `update(T, int)` is available. |
| `int predict(T x, double[] posteriori)` | Soft prediction — fills `posteriori` with class probabilities and returns the predicted label. |
| `void update(T x, int y)` | Online learning — update the model with a single new labelled sample. |
| `void update(T[] x, int[] y)` | Batch online update. |
| `int[] predict(T[] x)` | Batch hard prediction. |
| `int applyAsInt(T x)` | Alias for `predict`; satisfies `ToIntFunction<T>`. |
| `double applyAsDouble(T x)` | Alias for `score`; satisfies `ToDoubleFunction<T>`. |

The interface also declares a nested `Trainer<T, M>`:

```java
interface Trainer<T, M extends Classifier<T>> {
    M fit(T[] x, int[] y);
    M fit(T[] x, int[] y, Properties params);
}
```

Use `Trainer` when you want to write generic code that works with any classifier, or when
hyperparameters come from a configuration file.

**Capability probing**

```java
if (model.isSoft()) {
    double[] prob = new double[model.numClasses()];
    int label = model.predict(sample, prob);
    // prob[i] is P(class_i | sample)
}

if (model.isOnline()) {
    model.update(newSample, newLabel);
}
```

### 2.2 `AbstractClassifier`

An abstract base class that every concrete model in the package extends (except `MLP`,
which stores its own `IntSet`). It stores the `IntSet classes` label encoder and
provides default implementations of `numClasses()` and `classes()`.

Constructors:

```java
// From the raw training label array — calls ClassLabels.fit internally.
protected AbstractClassifier(int[] y)

// From an IntSet built externally.
protected AbstractClassifier(IntSet classes)

// From a data-frame ValueVector column.
protected AbstractClassifier(ValueVector y)
```

Concrete subclasses call one of these from their own constructors and then use
`classes.indexOf(label)` to convert original labels to `0..k-1` indices, and
`classes.valueOf(index)` to convert back before returning from `predict`.

### 2.3 `DataFrameClassifier`

```java
public interface DataFrameClassifier extends Classifier<Tuple>
```

An extension of `Classifier<Tuple>` for models that are trained on `DataFrame` objects
via a `Formula`. It adds:

| Method | Description |
|--------|-------------|
| `Formula formula()` | The formula used at training time. |
| `StructType schema()` | The schema of the feature columns. |
| `int predict(Tuple x)` | Predicts using a named-column `Tuple`. |
| `int[] predict(DataFrame df)` | Predicts all rows of a `DataFrame`. |

All tree-based models (`DecisionTree`, `RandomForest`, `AdaBoost`, `GradientTreeBoost`)
implement `DataFrameClassifier`. This is also the target interface for the static
`fit(Formula, DataFrame, …)` overloads of `OneVersusRest` and `OneVersusOne`.

The static `DataFrameClassifier.of(formula, classifier)` helper adapts any
`Classifier<double[]>` into a `DataFrameClassifier`, applying the formula's feature
extraction automatically.

### 2.4 `ClassLabels`

A utility record that maps arbitrary integer class labels to the contiguous range
`[0, k)` required by internal array indexing.

```java
ClassLabels codec = ClassLabels.fit(y);        // from int[] labels
ClassLabels codec = ClassLabels.fit(response); // from a ValueVector column
```

| Field | Type | Description |
|-------|------|-------------|
| `k` | `int` | Number of classes. |
| `classes` | `IntSet` | Encoder: `classes.indexOf(label)` → index, `classes.valueOf(index)` → label. |
| `y` | `int[]` | The original label array remapped to `[0, k)`. |
| `ni` | `int[]` | Per-class sample counts. |
| `priori` | `double[]` | Estimated prior probabilities `ni[i] / n`. |

Useful methods:

```java
// Remap an entire label array at once.
int[] indices = codec.indexOf(originalLabels);

// Build a NominalScale from the labels (for DataFrame integration).
NominalScale scale = codec.scale();
```

`ClassLabels.fit` throws `IllegalArgumentException` if only one class is present.
Labels need not start from 0 or be dense — they can be any sorted set of integers.

---

## 3. Discriminant Analysis Classifiers

All four DA classifiers operate on `double[]` feature vectors, make Gaussian
assumptions, and produce soft posteriors via log-likelihood scoring.

### 3.1 LDA — Linear Discriminant Analysis

**Algorithm.** Assumes all classes share a single pooled covariance matrix (homoscedastic).
The decision boundary is linear. Uses eigendecomposition of the pooled within-class
scatter matrix for a numerically stable solution.

**When to use.** Works well when the Gaussian assumption is roughly correct, features are
continuous, and you want a fast, interpretable, linear model.

**API.**

```java
LDA model = LDA.fit(x, y);
LDA model = LDA.fit(x, y, priori);       // provide prior probabilities explicitly
LDA model = LDA.fit(x, y, priori, tol);  // tolerance for degenerate features

int label  = model.predict(sample);
int label  = model.predict(sample, posteriori);
double[]   = model.priori();
double[][] = model.means();              // per-class mean vectors
```

**Key parameter: `tol`** (default `1e-4`). Features with variance below `tol` are removed.
Increase it when features are highly correlated or near-singular.

**Limitation.** Fails (singular matrix) when the number of features exceeds the number of
training samples. Use `FLD` or `RDA` in that regime.

### 3.2 QDA — Quadratic Discriminant Analysis

**Algorithm.** Allows each class to have its own covariance matrix; the boundary is
quadratic. Each class requires at least `p + 1` samples to estimate its covariance.

**When to use.** When classes have different spreads or orientations.

```java
QDA model = QDA.fit(x, y);
QDA model = QDA.fit(x, y, priori, tol);
```

**Limitation.** Requires substantially more training data than LDA and is sensitive to
the Gaussian assumption on a per-class basis.

### 3.3 RDA — Regularized Discriminant Analysis

**Algorithm.** Blends LDA and QDA via a regularization parameter `alpha ∈ [0, 1]`.
`alpha = 0` gives LDA (pooled covariance), `alpha = 1` gives QDA (per-class covariance).
A second parameter `delta` further regularizes toward the identity matrix.

**When to use.** The go-to choice when LDA's homoscedastic assumption is too strong but
QDA's per-class estimation is unstable due to limited data.

```java
RDA model = RDA.fit(x, y, alpha);
RDA model = RDA.fit(x, y, alpha, priori, tol);
```

### 3.4 FLD — Fisher's Linear Discriminant

**Algorithm.** Finds a projection matrix `W` that maximises between-class scatter relative
to within-class scatter. Uses either direct inversion (large-`n`) or SVD (small-`n`,
when `n < p`). Prediction is by nearest projected class mean using Euclidean distance.

**When to use.** When `p > n` (more features than samples), for dimensionality reduction,
or when you want an explicit low-dimensional projection.

```java
FLD model = FLD.fit(x, y);
FLD model = FLD.fit(x, y, L, tol);  // L: max number of discriminant directions

int label     = model.predict(sample);
double[]      = model.project(sample);     // low-dimensional projection of one sample
double[][]    = model.project(samples);    // batch projection
double[][]    = model.getProjection();     // the W matrix itself
```

**Parameter `L`** (default: `k - 1`). The number of discriminant directions to retain.
At most `min(p, k-1)` directions are meaningful.

---

## 4. Generative Text Classifiers

### 4.1 `NaiveBayes`

The general Naive Bayes classifier that accepts arbitrary `Distribution[][]` objects for
each feature and class combination. This is the most flexible variant — any distribution
from `smile.stat.distribution` can be used.

```java
// d[c][f] is the distribution of feature f in class c.
Distribution[][] d = new Distribution[k][p];
// ... fill d with GaussianDistribution, PoissonDistribution, etc. ...
NaiveBayes model = new NaiveBayes(priori, d);

int label = model.predict(features);  // features: double[]
```

`NaiveBayes` supports soft prediction (`isSoft() == true`). A log-sum-exp trick is used
for numerical stability. It does **not** support online learning (`isOnline() == false`).

### 4.2 `DiscreteNaiveBayes`

Optimized for NLP document classification, operating on term-count vectors (`int[]` or
`SparseArray`). Supports six model variants:

| Model | Description | Online |
|-------|-------------|--------|
| `MULTINOMIAL` | Term-frequency bag-of-words | Yes |
| `BERNOULLI` | Binary term presence/absence | Yes |
| `POLYAURN` | Like MULTINOMIAL but counts each term twice | Yes |
| `CNB` | Complement Naive Bayes — uses counts from all other classes | Yes |
| `WCNB` | Weight-normalized CNB | Yes |
| `TWCNB` | Transformed WCNB with TF-IDF + length normalization | **Batch only** |

**Constructors.**

```java
// Learned priors (uniform initially)
DiscreteNaiveBayes nb = new DiscreteNaiveBayes(Model.MULTINOMIAL, k, p);
DiscreteNaiveBayes nb = new DiscreteNaiveBayes(Model.MULTINOMIAL, k, p, sigma, labels);

// Fixed priors
DiscreteNaiveBayes nb = new DiscreteNaiveBayes(Model.BERNOULLI, priori, p);
```

**Parameters.**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `k` | — | Number of classes. |
| `p` | — | Vocabulary size (feature dimension). |
| `sigma` | `1.0` | Add-`sigma` smoothing (Laplace smoothing when `sigma = 1`). Set to `0` to disable. |
| `labels` | `IntSet.of(k)` | Label encoder for arbitrary class labels. |

**Online learning.**

```java
// Dense array (single instance)
nb.update(int[] x, int y);

// Sparse format (single instance)
nb.update(SparseArray x, int y);

// Batch (dense or sparse)
nb.update(int[][] x, int[] y);
nb.update(SparseArray[] x, int[] y);
```

**Prediction.**

```java
int label = nb.predict(int[] x);
int label = nb.predict(SparseArray x);

double[] post = new double[k];
int label = nb.predict(int[] x, post);   // post[i] = P(class_i | x)
int label = nb.predict(SparseArray x, post);
```

If the input vector is all-zeros (no active terms), `predict` returns `Integer.MIN_VALUE`.

**Recommendation.** TWCNB generally achieves the best accuracy on text tasks but requires
batch training; start with MULTINOMIAL or BERNOULLI for online/streaming scenarios.

---

## 5. Regression-based Classifiers

### 5.1 `LogisticRegression`

Penalized maximum-likelihood logistic regression, optimized by L-BFGS. For `k = 2`
classes, the `Binomial` subclass is used; for `k > 2`, `Multinomial` is used.

```java
LogisticRegression model = LogisticRegression.fit(x, y);
LogisticRegression model = LogisticRegression.fit(x, y, lambda, tol, maxIter);
LogisticRegression model = LogisticRegression.fit(x, y, properties);
```

**Key parameters.**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `lambda` | `0.1` | L2 regularization coefficient. Larger values penalize large weights more. |
| `tol` | `1e-5` | Convergence tolerance for L-BFGS gradient norm. |
| `maxIter` | `500` | Maximum L-BFGS iterations. |

**API.**

```java
int    label  = model.predict(sample);
int    label  = model.predict(sample, posteriori);
double logit  = model.score(sample);   // Binomial: log-odds; Multinomial: not meaningful
double L      = model.logLikelihood(); // log-likelihood of training data
double aic    = model.AIC();
double bic    = model.BIC();

// Online SGD update
model.update(sample, label);
model.update(sample, label, learningRate);
```

**Properties round-trip.**

```java
Properties props = new Properties();
props.setProperty("smile.logistic.lambda", "0.01");
props.setProperty("smile.logistic.tolerance", "1e-6");
props.setProperty("smile.logistic.iterations", "1000");
LogisticRegression model = LogisticRegression.fit(x, y, props);
```

**Use cases.** Interpretable linear model; baseline for any binary or multiclass
classification task. The `score` output is meaningful for Platt scaling calibration.

### 5.2 `SparseLogisticRegression`

Functionally identical to `LogisticRegression` but accepts `SparseArray[]` input, making
it suitable for high-dimensional sparse feature spaces (e.g., bag-of-words with large
vocabularies).

```java
SparseLogisticRegression model = SparseLogisticRegression.fit(x, y);
// x: SparseArray[], p: vocabulary size
SparseLogisticRegression model = SparseLogisticRegression.fit(x, y, p, lambda, tol, maxIter);
```

Online update also accepts `SparseArray`:

```java
model.update(SparseArray x, int y);
```

### 5.3 `Maxent` — Maximum Entropy

The Maximum Entropy model (a.k.a. multinomial logistic regression) for text and
structured prediction tasks where features are described as sparse integer-indexed
feature vectors.

```java
Maxent model = Maxent.fit(p, x, y);        // p: feature space size, x: int[][]
Maxent model = Maxent.fit(p, x, y, lambda, tol, maxIter);

int    label = model.predict(int[] x);
double[]     = model.posteriori(int[] x);

// Sparse format
Maxent model = Maxent.fit(p, x, y);        // x: SparseArray[]
int    label = model.predict(SparseArray x);
```

Maxent also supports online SGD updates and, like logistic regression, is optimized by
L-BFGS in batch mode.

---

## 6. Tree and Ensemble Classifiers

### 6.1 `DecisionTree`

A single CART (Classification And Regression Tree) decision tree, operating on `Tuple`
(named-column) data through a `Formula`.

```java
DecisionTree model = DecisionTree.fit(formula, data);
DecisionTree model = DecisionTree.fit(formula, data, props);
```

**Options** (set via `Properties`)

| Property | Default | Description |
|----------|---------|-------------|
| `smile.cart.split.rule` | `GINI` | Splitting criterion: `GINI`, `ENTROPY`, or `CLASSIFICATION_ERROR`. |
| `smile.cart.node.size` | `5` | Minimum node size; nodes smaller than this are not split. |
| `smile.cart.max.depth` | `20` | Maximum tree depth. |
| `smile.cart.max.nodes` | `0` (unlimited) | Maximum number of leaf nodes. |

**API.**

```java
int      label    = model.predict(tuple);
int[]    labels   = model.predict(dataFrame);
double[] weights  = model.importance();   // Gini-importance per feature
String   text     = model.toString();     // printable tree representation
```

`DecisionTree` also implements `TreeSHAP` via `RandomForest`, though SHAP values are
more useful in ensemble contexts.

**Use cases.** Quick exploratory baseline; interpretable rules; building block for
ensemble methods. Prone to overfitting — use `RandomForest` or `GradientTreeBoost`
in production.

### 6.2 `RandomForest`

An ensemble of independently trained `DecisionTree`s. Each tree is grown on a bootstrap
sample with a random subset of `m` features considered at each split. Final prediction
uses majority voting, optionally weighted by OOB accuracy.

```java
RandomForest model = RandomForest.fit(formula, data);
RandomForest model = RandomForest.fit(formula, data, props);
```

**Options**

| Property | Default | Description |
|----------|---------|-------------|
| `smile.random.forest.trees` | `500` | Number of trees. |
| `smile.random.forest.mtry` | `√p` for classification | Features sampled per split. |
| `smile.random.forest.split.rule` | `GINI` | Splitting criterion. |
| `smile.random.forest.max.depth` | `20` | Maximum tree depth. |
| `smile.random.forest.node.size` | `5` | Minimum node size. |
| `smile.random.forest.sampling.rate` | `1.0` | Fraction of data sampled per tree (bootstrap). |

**API.**

```java
int      label    = model.predict(tuple);
int[]    labels   = model.predict(df);
int      label    = model.predict(tuple, posteriori);

ClassificationMetrics metrics = model.metrics(); // OOB error, accuracy, etc.
double[] imp      = model.importance();          // feature importance
double[][] shap   = model.shap(df);              // SHAP values
Formula  f        = model.formula();
```

**OOB metrics.** Each tree is tested on the samples not used in its bootstrap, providing
an almost free unbiased estimate of generalization error:

```java
System.out.println("OOB accuracy: " + model.metrics().accuracy());
```

**Feature importance** sums the impurity reduction from all splits on each feature,
weighted by the number of samples affected. Useful for variable selection.

### 6.3 `AdaBoost`

Adaptive Boosting — a boosting ensemble that iteratively trains decision trees (stumps
or shallow trees) and increases the weight of misclassified samples. This implementation
supports multiclass problems natively (SAMME algorithm).

```java
AdaBoost model = AdaBoost.fit(formula, data);
AdaBoost model = AdaBoost.fit(formula, data, props);
```

**Options**

| Property | Default | Description |
|----------|---------|-------------|
| `smile.adaboost.trees` | `500` | Number of boosting rounds. |
| `smile.adaboost.max.depth` | `2` | Max tree depth (stumps = depth 1). |
| `smile.adaboost.node.size` | `1` | Minimum node size. |

**API.**

```java
int      label    = model.predict(tuple);
int[]    labels   = model.predict(df);
int      label    = model.predict(tuple, posteriori);
double[] imp      = model.importance();
double[][] shap   = model.shap(df);
```

**Characteristics.** AdaBoost is highly sensitive to noisy labels and outliers (because
it continuously upweights hard samples), but can be less prone to overfitting than a
single deep tree.

### 6.4 `GradientTreeBoost`

Gradient boosting with regression trees. Each tree is fitted to the pseudo-residuals
(negative gradient of the loss) of the current ensemble prediction. Supports binary
and multiclass classification.

```java
GradientTreeBoost model = GradientTreeBoost.fit(formula, data);
GradientTreeBoost model = GradientTreeBoost.fit(formula, data, props);
```

**Options**

| Property | Default | Description |
|----------|---------|-------------|
| `smile.gbt.trees` | `500` | Number of boosting trees. |
| `smile.gbt.shrinkage` | `0.05` | Learning rate / shrinkage factor. |
| `smile.gbt.max.depth` | `5` | Max tree depth. |
| `smile.gbt.node.size` | `5` | Minimum node size. |
| `smile.gbt.sampling.rate` | `0.7` | Subsample fraction per tree (stochastic GBT). |

**API.**

```java
int      label = model.predict(tuple);
int[]    labels = model.predict(df);
int      label = model.predict(tuple, posteriori);
double[] imp   = model.importance();
double[][] shap = model.shap(df);
```

**Tuning advice.** Shrinkage and tree count are a trade-off: smaller shrinkage requires
more trees but usually gives better generalization. Subsampling (`sampling.rate < 1.0`)
adds stochasticity that helps avoid overfitting and reduces training time.

---

## 7. Distance-based Classifiers

### 7.1 `KNN` — K-Nearest Neighbors

Classifies by majority vote among the `k` nearest training points. Uses a `KDTree` for
low-dimensional dense data (< 10 features), a `CoverTree` for higher-dimensional data
with a `Metric`, and a `LinearSearch` for arbitrary non-metric distances.

```java
// Dense Euclidean, automatic KDTree or CoverTree selection
KNN<double[]> model = KNN.fit(x, y);       // 1-NN
KNN<double[]> model = KNN.fit(x, y, k);    // k-NN

// Arbitrary data type and custom distance
KNN<String> model = KNN.fit(x, y, distance);
KNN<String> model = KNN.fit(x, y, k, distance);
```

**Soft prediction.** Posteriori probabilities are the fraction of the `k` neighbors
belonging to each class:

```java
model.isSoft(); // true
double[] post = new double[model.numClasses()];
int label = model.predict(sample, post);
```

**Characteristics.**
- Non-parametric and can model any decision boundary.
- Prediction cost is `O(n)` without a spatial index, or `O(log n)` with `KDTree`.
- No training cost, but requires storing all training data.
- Sensitive to the scale of features — normalise features before using `KNN`.
- Good baseline; also effective as a binary classifier inside `OneVersusOne`.

### 7.2 `RBFNetwork` — Radial Basis Function Network

A two-layer network: the hidden layer computes RBF activations from cluster centers
(fitted by k-means), and the output layer is a linear least-squares fit.

```java
RBFNetwork<double[]> model = RBFNetwork.fit(x, y, rbf);
RBFNetwork<double[]> model = RBFNetwork.fit(x, y, centers, rbf, normalized);
```

- `rbf`: an array of `RadialBasisFunction` objects (one per center).
- `centers`: pre-computed cluster centers; if not provided, k-means initializes them.
- `normalized`: if `true`, activations are normalized to sum to 1 (normalized RBF).

**Prediction.**

```java
int label = model.predict(sample);
```

`RBFNetwork` does not support soft prediction or online updates.

---

## 8. Support Vector Machines

### 8.1 `SVM`

A kernel-based binary SVM (class labels must be `+1` and `-1`), implemented with the
LASVM online/active learning algorithm. Training is done via the `smile.model.svm.LASVM`
class.

```java
SVM<double[]> model = SVM.fit(x, y, kernel, C, tol);

// Multiclass via automatic OVR or OVO reduction
Classifier<double[]> mc = SVM.fit(x, y, kernel, C, tol, props);
// smile.svm.multiclass=OVO or OVR
```

**Parameters.**

| Parameter | Description                                                                                           |
|-----------|-------------------------------------------------------------------------------------------------------|
| `kernel` | Kernel function from `smile.math.kernel` (e.g. `GaussianKernel`, `PolynomialKernel`, `LinearKernel`). |
| `C` | Soft-margin regularisation. Larger = less regularization, sharper margin.                            |
| `tol` | Working set selection tolerance.                                                                      |

**Kernel choices.**

| Kernel | Class | When to use |
|--------|-------|-------------|
| Linear | `LinearKernel` | High-dimensional sparse data |
| Gaussian (RBF) | `GaussianKernel(sigma)` | General-purpose nonlinear; most popular |
| Polynomial | `PolynomialKernel(d)` | Image features; NLP n-grams |
| Laplacian | `LaplacianKernel(sigma)` | Robust to outliers |

**Score.** `model.score(x)` returns the raw decision function value (signed distance to
the hyperplane). The sign gives the class; the magnitude reflects confidence.

### 8.2 `LinearSVM`, `SparseLinearSVM`, `BinarySparseLinearSVM`

Thin wrappers around a trained `LinearKernelMachine` that expose explicit weight vectors
and intercepts. They are created from a trained `KernelMachine<double[]>` or
`KernelMachine<SparseArray>`:

```java
LinearSVM model = new LinearSVM(svm);
double[] w = model.weights();
double   b = model.intercept();
double   s = model.score(x);   // w·x + b
int   label = model.predict(x); // sign(w·x + b)
```

- `SparseLinearSVM` — accepts `SparseArray` inputs.
- `BinarySparseLinearSVM` — accepts `int[]` binary sparse inputs.

These are useful when you need to inspect or export the linear decision boundary.

---

## 9. Neural Network

### 9.1 `MLP` — Multilayer Perceptron

A fully connected feed-forward network trained by stochastic back-propagation with
optional momentum.

```java
MLP model = MLP.fit(x, y,
    new Layer.ReLU(128),
    new Layer.ReLU(64)
);

// Or from Properties
MLP model = MLP.fit(x, y, props);
```

**Layer builders** (from `smile.model.mlp`):

| Type | Description |
|------|-------------|
| `Layer.ReLU(units)` | Rectified linear activation |
| `Layer.Sigmoid(units)` | Logistic sigmoid |
| `Layer.Tanh(units)` | Hyperbolic tangent |
| `Layer.Mish(units)` | Mish activation |
| `Layer.SELU(units)` | SELU with self-normalising property |

The output layer is automatically added based on the number of classes (softmax for
`k > 2`, logistic sigmoid for `k = 2`).

**Options** (via `Properties` or `MLP.Options`)

| Property | Default | Description                |
|----------|---------|----------------------------|
| `smile.mlp.learning.rate` | `0.01` | SGD learning rate.         |
| `smile.mlp.momentum` | `0.0` | Momentum coefficient.      |
| `smile.mlp.weight.decay` | `0.0` | L2 weight regularization. |
| `smile.mlp.epochs` | `10` | Number of training epochs. |
| `smile.mlp.mini.batch` | `32` | Mini-batch size.           |

**Online learning** — `MLP` supports both single-sample and batch online updates:

```java
model.update(sample, label);
model.update(batchX, batchY);
```

**Soft prediction.**

```java
double[] post = new double[model.numClasses()];
int label = model.predict(sample, post);  // post = softmax outputs
```

---

## 10. Probability Calibration

Raw classifier scores are not always calibrated probabilities. Both calibrators map a
scalar decision score to `P(class = 1 | score)`.

### 10.1 `PlattScaling`

Fits a logistic function `P = 1 / (1 + exp(A·f + B))` to the model's scores using
Newton's method. This is the standard calibration for SVMs.

```java
// Fit from raw scores and labels
PlattScaling ps = PlattScaling.fit(scores, labels);      // default 100 iterations
PlattScaling ps = PlattScaling.fit(scores, labels, 50);  // custom iterations

// Fit directly from a classifier (calls model.score(x) internally)
PlattScaling ps = PlattScaling.fit(model, x, y);

// Apply
double prob = ps.scale(score);  // P(positive class | score)
```

**Labels** for fitting must use `+1` for the positive class and `-1` (or any negative
value) for the negative class. The model uses Platt's smoothed targets to avoid
overconfidence.

`PlattScaling` is used internally by `OneVersusRest.fit` and `OneVersusOne.fit` when
the base classifier supports `score()`.

### 10.2 `IsotonicRegressionScaling`

A non-parametric alternative to Platt scaling using the Pool Adjacent Violators (PAV)
algorithm. It finds a monotone non-decreasing step function that minimises mean squared
error against the binary labels.

```java
IsotonicRegressionScaling irs = IsotonicRegressionScaling.fit(scores, labels);
double prob = irs.scale(score);
```

Isotonic regression calibration is better when the score-to-probability relationship
is non-logistic (e.g., highly nonlinear outputs from tree ensembles). However, it
requires more data to reliably estimate than Platt scaling.

---

## 11. Multiclass Reduction Strategies

Both strategies reduce a `k`-class problem to a set of binary problems and combine
the binary results. The base classifiers are supplied as a lambda `trainer`.

### 11.1 `OneVersusRest`

Trains `k` binary classifiers: classifier `i` distinguishes class `i` from all others.
Prediction picks the class whose classifier reports the highest Platt-scaled score.

```java
// Default: use +1 / -1 as binary labels
OneVersusRest<double[]> model = OneVersusRest.fit(x, y, trainer);

// Custom positive/negative labels
OneVersusRest<double[]> model = OneVersusRest.fit(x, y, +1, -1, trainer);

// DataFrame variant
DataFrameClassifier model = OneVersusRest.fit(formula, data, dfTrainer);
```

**Requirements.**
- The binary `trainer` must produce classifiers that support `score()` for Platt scaling
  to be fitted. If `score()` is not supported, Platt fitting is silently skipped and the
  model's `isSoft()` returns `false`; in that case, **both** `predict(x)` and
  `predict(x, posteriori)` throw `UnsupportedOperationException`.
- At least 3 classes are required (`k <= 2` throws `IllegalArgumentException`).

```java
// Check before calling
if (model.isSoft()) {
    double[] post = new double[model.numClasses()];
    int label = model.predict(sample, post);
}
```

**When to use.** Works well with calibrated soft classifiers like `LogisticRegression`.
Requires fewer binary models than OVO for large `k`.

### 11.2 `OneVersusOne`

Trains `k(k-1)/2` binary classifiers: one for each pair of classes. Hard prediction
uses majority voting (which works even without Platt scaling). Soft prediction uses
the Hastie–Tibshirani coupling algorithm on Platt-scaled pairwise probabilities.

```java
// Default: use +1 / -1 as binary labels
OneVersusOne<double[]> model = OneVersusOne.fit(x, y, trainer);

// Custom positive/negative labels
OneVersusOne<double[]> model = OneVersusOne.fit(x, y, +1, -1, trainer);

// DataFrame variant
DataFrameClassifier model = OneVersusOne.fit(formula, data, dfTrainer);
```

**Key difference from OVR.** Hard prediction via `predict(x)` works with any binary
classifier, even those without `score()`, because it uses vote counting. Only
`predict(x, posteriori)` requires Platt.

```java
// Hard predict always works (voting)
int label = model.predict(sample);

// Soft predict requires Platt scaling
if (model.isSoft()) {
    int label = model.predict(sample, posteriori);
}
```

**When to use.** Preferred when using SVMs as base classifiers (since the `{-1, +1}`
labels align naturally). OVO trains more models but each on a smaller dataset, making
it faster per model for large `k`.

---

## 12. Algorithm Comparison

| Algorithm | Input | `isSoft` | `isOnline` | Multiclass | DataFrame | Notes |
|-----------|-------|----------|------------|------------|-----------|-------|
| LDA | `double[]` | ✓ | ✗ | ✓ | ✗ | Linear boundary; Gaussian + homoscedastic |
| QDA | `double[]` | ✓ | ✗ | ✓ | ✗ | Quadratic; each class has own covariance |
| RDA | `double[]` | ✓ | ✗ | ✓ | ✗ | Blends LDA and QDA via `alpha` |
| FLD | `double[]` | ✗ | ✗ | ✓ | ✗ | Best for `p > n`; produces projection |
| NaiveBayes | `double[]` | ✓ | ✗ | ✓ | ✗ | User-supplied distributions |
| DiscreteNaiveBayes | `int[]` / `SparseArray` | ✓ | ✓ | ✓ | ✗ | NLP; multinomial / Bernoulli / TWCNB |
| LogisticRegression | `double[]` | ✓ | ✓ | ✓ | ✗ | Linear; L2 penalised; AIC/BIC |
| SparseLogisticRegression | `SparseArray` | ✓ | ✓ | ✓ | ✗ | High-dimensional sparse |
| Maxent | `int[]` / `SparseArray` | ✓ | ✓ | ✓ | ✗ | NLP feature templates |
| DecisionTree | `Tuple` | ✓ | ✗ | ✓ | ✓ | Interpretable; overfits alone |
| RandomForest | `Tuple` | ✓ | ✗ | ✓ | ✓ | Best general-purpose; OOB error |
| AdaBoost | `Tuple` | ✓ | ✗ | ✓ | ✓ | Sensitive to noise; fast training |
| GradientTreeBoost | `Tuple` | ✓ | ✗ | ✓ | ✓ | Usually highest accuracy; tunable |
| KNN | `T` (generic) | ✓ | ✗ | ✓ | ✗ | Non-parametric; needs scaling |
| RBFNetwork | `T` (generic) | ✗ | ✗ | ✓ | ✗ | Two-layer; k-means centers |
| SVM | `T` (generic) | ✗ | ✗ | Binary | ✗ | Max-margin; kernel trick |
| LinearSVM | `double[]` | ✗ | ✗ | Binary | ✗ | Exposes weight vector |
| MLP | `double[]` | ✓ | ✓ | ✓ | ✗ | Deep learning; highly expressive |
| OneVersusRest | `T` | depends | ✗ | ✓ | ✓ | `isSoft` ↔ Platt available |
| OneVersusOne | `T` | depends | ✗ | ✓ | ✓ | Hard predict always works |

---

## 13. Common Patterns and Tips

### Checking available capabilities before calling

`isSoft()` and `isOnline()` must be checked before calling the corresponding methods.
Both return `false` by default in the interface; only models that genuinely support
the operation override them.

```java
// Safe soft-prediction pattern
double[] post = null;
if (model.isSoft()) {
    post = new double[model.numClasses()];
    model.predict(sample, post);
}

// Safe online-update pattern
if (model.isOnline()) {
    model.update(newX, newY);
}
```

### Working with arbitrary class labels

Class labels can be any integer values — they do not need to be consecutive or
zero-based. Every `AbstractClassifier` subclass handles the encoding transparently.

```java
int[] y = {-1, +1, -1, +1};          // binary ±1
int[] y = {3, 7, 14, 3, 7};          // non-contiguous
// All work identically with any fit() method.

// After prediction, the returned value is always the original label:
int label = model.predict(sample);    // returns -1, +1 or 3, 7, 14 — not 0/1/2
```

### Serialization

All classifiers implement `java.io.Serializable`. Use `smile.io.Read` and `smile.io.Write`:

```java
import smile.io.Write;
import smile.io.Read;

Write.object(model, Path.of("model.ser"));
Classifier<?> loaded = (Classifier<?>) Read.object(Path.of("model.ser"));
```

### Configuration via Properties

`LogisticRegression`, `RandomForest`, `GradientTreeBoost`, `AdaBoost`, `DecisionTree`,
`MLP`, and `Maxent` all expose a `fit(…, Properties)` overload and a nested `Options`
record with `toProperties()` / `of(Properties)` for round-tripping:

```java
Properties p = new Properties();
p.setProperty("smile.random.forest.trees", "200");
p.setProperty("smile.random.forest.max.depth", "10");

RandomForest model = RandomForest.fit(formula, data, p);
```

### Reproducibility

Tree ensembles, k-NN splitting, and anything involving random sampling should be made
reproducible by seeding the global RNG:

```java
smile.math.MathEx.setSeed(12345);
RandomForest model = RandomForest.fit(formula, data);
```

### Probability calibration workflow

For classifiers that return raw scores but not calibrated probabilities, use Platt
scaling or isotonic regression as a post-processing step:

```java
// 1. Train the base model
LogisticRegression base = LogisticRegression.fit(xTrain, yTrain);

// 2. Fit calibration on a held-out calibration set
PlattScaling ps = PlattScaling.fit(base, xCal, yCal);

// 3. At prediction time
double score = base.score(testSample);
double prob  = ps.scale(score);  // calibrated P(positive | sample)
```

### Choosing a multiclass strategy

For a multiclass problem where your preferred binary classifier supports `score()`:
- **OVR** is simpler and trains `k` models.
- **OVO** trains `k(k-1)/2` models but each on a smaller dataset; preferred for SVMs.

```java
// OVR with logistic regression
OneVersusRest<double[]> ovr = OneVersusRest.fit(x, y,
        (a, b) -> LogisticRegression.fit(a, b));

// OVO with SVM
OneVersusOne<double[]> ovo = OneVersusOne.fit(x, y,
        (a, b) -> SVM.fit(a, b, new GaussianKernel(1.0), 1.0));
```

### Choosing between tree ensembles

| Situation | Recommendation |
|-----------|----------------|
| Need a quick, interpretable baseline | `DecisionTree` |
| Large dataset, need OOB error estimate | `RandomForest` |
| Best possible accuracy, can tune | `GradientTreeBoost` |
| Noisy / outlier-heavy data | `RandomForest` (more robust than AdaBoost) |
| Speed is paramount | `RandomForest` (parallelises trivially) |

### Feature scaling

- **Scale-invariant:** `DecisionTree`, `RandomForest`, `AdaBoost`, `GradientTreeBoost`,
  `DiscreteNaiveBayes`, `NaiveBayes`.
- **Scale-sensitive (normalise features before use):** `LDA`, `QDA`, `RDA`, `FLD`,
  `LogisticRegression`, `KNN`, `SVM`, `MLP`, `RBFNetwork`.

### Memory and scale considerations

| Concern | Guidance |
|---------|---------|
| `n > 1M` rows | `DecisionTree`, `RandomForest`, or `GradientTreeBoost` scale well. `SVM` does not scale well with `n`. |
| `p > n` (more features than samples) | `FLD` (uses SVD), `SparseLogisticRegression`, or `Maxent`. `LDA` and `QDA` fail. |
| Streaming / incremental data | `DiscreteNaiveBayes`, `LogisticRegression`, `MLP` (online update). |
| Very high-dimensional sparse text | `DiscreteNaiveBayes`, `SparseLogisticRegression`, `Maxent`. |

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
