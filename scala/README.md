# SMILE Scala API

The `smile-scala` module is an idiomatic Scala shim over the SMILE Java library.
Except `smile.cas` package (Computer Algebra System), it adds nothing algorithmic
— every function ultimately delegates to the same Java `fit`, `of`, or constructor
— but it replaces verbose Java patterns with concise, expressive Scala idioms:

- **Implicit conversions ("pimp-my-library")** enrich `DataFrame`, `Tuple`,
  arrays, and `String` with domain-specific methods.
- **Operator DSL** — R-style formula syntax (`y ~ x1 + x2`), NumPy-style array
  slicing (`0 ~ 9 ~ 2`), and linear-algebra operators (`%*%`, `\`).
- **Computer Algebra System** — symbolic differentiation and simplification on
  scalar, vector, and matrix expressions.
- **Top-level functions** with named default arguments replace long static-method
  argument lists.
- **Idiomatic `object` namespaces** (`read`, `write`, `gpr`, `validate`, `cv`,
  `loocv`, `bootstrap`) group related operations without polluting the package
  namespace.
- **Macro-backed rendering** in Scala 2.13 detects notebook environments
  (Zeppelin, Databricks) at compile time and routes `show(…)` to either an
  in-process Swing window or an HTML `<img>` element.

The module depends on `:core` (ML), `:base` (data and I/O), `:nlp`, `:plot`, and
`:json`.

---

## Table of Contents

1. [Installation](#installation)
2. [Data I/O — `read` and `write`](#data-io--read-and-write)
3. [DataFrame Extensions](#dataframe-extensions)
4. [Formula DSL](#formula-dsl)
5. [Math, Arrays, and Linear Algebra](#math-arrays-and-linear-algebra)
6. [Computer Algebra System (CAS)](#computer-algebra-system-cas)
7. [Classification](#classification)
8. [Regression](#regression)
9. [Clustering](#clustering)
10. [Dimensionality Reduction](#dimensionality-reduction)
11. [Manifold Learning](#manifold-learning)
12. [Natural Language Processing](#natural-language-processing)
13. [Sequence Labeling](#sequence-labeling)
14. [Association Rule Mining](#association-rule-mining)
15. [Wavelets](#wavelets)
16. [Model Validation](#model-validation)
17. [Plotting](#plotting)
18. [Utility Helpers](#utility-helpers)
19. [Complete Examples](#complete-examples)

---

## Installation

Add the module to your `build.gradle.kts` (for use inside this Gradle project):

```kotlin
dependencies {
    implementation(project(":scala"))
}
```

Or, from SBT in a standalone project:

```scala
libraryDependencies += "com.github.haifengl" %% "smile-scala" % "<version>"
```

Import the relevant package objects at the top of each file. The most common
imports are:

```scala
import smile.io.*           // read, write
import smile.data.*         // DataFrame implicits, summary
import smile.data.formula.* // formula DSL: ~, +, -, ::, &&, ^
import smile.math.*         // PimpedInt, PimpedDouble, array extensions, linalg
import smile.classification.*
import smile.regression.*
import smile.clustering.*
import smile.manifold.*
import smile.nlp.*
import smile.validation.*
```

---

## Data I/O — `read` and `write`

Both `read` and `write` are top-level Scala `object`s defined in `smile.io`.
They serve as namespaces so you can write `read.csv(…)` instead of importing a
static Java method.

### Loading data

```scala
import smile.io.*

// Auto-detect format from extension (.csv, .json, .arff, .parquet, .avro, .sas7bdat)
val df = read.data("path/to/file.csv")
val df = read.data("path/to/file.parquet")
val df = read.data("path/to/file.json", "multi-line")   // JSON mode hint

// CSV with options (all have defaults — delimiter=",", header=true, quote='"')
val df = read.csv("iris.csv")
val df = read.csv("data.tsv", delimiter = "\t")
val df = read.csv("data.csv", header = false, comment = '#')

// Other formats
val df = read.json("records.json")
val df = read.json("records.json", JSON.Mode.MULTI_LINE, schema)
val df = read.arff("weka.arff")
val df = read.sas("dataset.sas7bdat")
val df = read.arrow("data.arrow")
val df = read.avro("data.avro", schemaInputStream)
val df = read.parquet("data.parquet")
val ds = read.libsvm("data.libsvm")        // returns SparseDataset[Integer]
val (vertices, edges) = read.wavefront("mesh.obj")  // 3-D OBJ geometry

// JDBC result set
val df = read.jdbc(resultSet)

// Deserialize a previously serialized model
val model = read("model.bin")
```

### Saving data

```scala
import smile.io.*

// Serialize any Serializable object (e.g. a trained model)
write(model, "model.bin")

// Write a DataFrame
write.csv(df, "out.csv")
write.csv(df, "out.tsv", delimiter = "\t")
write.arff(df, "out.arff", "relation-name")
write.arrow(df, "out.arrow")

// Write raw arrays
write.array(predictions, "predictions.txt")          // one element per line
write.table(matrix, "matrix.csv", delimiter = ",")  // 2-D array to delimited file
```

---

## DataFrame Extensions

When you import `smile.data.*`, implicit conversions enrich `DataFrame` and
`Tuple` with Scala-idiomatic methods.

### `DataFrameOps` — enriches `DataFrame`

```scala
import smile.data.*

val df: DataFrame = read.csv("iris.csv")

// Select/drop columns by name or Range
val sub   = df.select("sepal.length", "sepal.width")
val fewer = df.drop("class")
val slice = df.of(0 until 100)         // row slice using Scala Range

// Functional operations
val row: Option[Tuple] = df.find(_.getInt("class") == 1)
val all: Boolean        = df.forall(_.getDouble("petal.length") > 0.0)
val any: Boolean        = df.exists(_.getDouble("sepal.length") > 7.0)
df.foreach(row => println(row))

val mapped: Array[Double] = df.map(_.getDouble(0))
val filtered: DataFrame   = df.filter(_.getDouble("sepal.length") > 5.0)
val (yes, no)             = df.partition(_.getInt("class") == 0)
val groups                = df.groupBy(_.getInt("class"))

// JSON conversion
val json: String = df.toJSON
```

### `TupleOps` — enriches `Tuple`

```scala
val t: Tuple = df.get(0)
val json: String = t.toJSON    // handles categorical fields correctly
```

### Summary statistics (top-level)

```scala
import smile.data.*

summary(intArray)     // prints min/Q1/median/mean/Q3/max for Array[Int]
summary(doubleArray)  // same for Array[Double]
```

---

## Formula DSL

Import `smile.data.formula.*` to unlock an R-style formula language for
specifying model structure.

### Basic syntax

```scala
import smile.data.formula.*

// y ~ x  means "predict y from x"
val f: Formula = "y" ~ "x"

// Include multiple terms
val f = "price" ~ "size" + "bedrooms" + "location"

// Exclude a term with unary -
val f = "y" ~ "." - "id"     // use all columns except "id"

// Intercept-only: just ". ~ ."
```

### Interaction and crossing

```scala
// Interaction term: a :: b  (a*b without main effects in R notation)
val f = "y" ~ "a" :: "b"

// Crossing (main effects + interactions): a && b
val f = "y" ~ "a" && "b"       // expands to a + b + a:b

// Degree on crossing
val f = "y" ~ ("a" && "b") ^ 3
```

### Function terms

All common `Math` functions are available as Formula terms:

```scala
val f = "y" ~ log("income") + sqrt("age") + "gender"
val f = "y" ~ abs("balance") + exp("rate")
// Available: abs, ceil, floor, round, rint, exp, expm1, log, log1p,
//            log10, log2, signum, sign, sqrt, cbrt, sin, cos, tan,
//            sinh, cosh, tanh, asin, acos, atan, ulp
```

---

## Math, Arrays, and Linear Algebra

Import `smile.math.*` to get enriched numeric types, operator overloading for
arrays and matrices, and many statistical/linear-algebra helpers.

### Enriched primitives

```scala
import smile.math.*

// PimpedInt — slice construction (Python-like)
val s: Slice = 0 ~ 9          // indices 0..9
val s: Slice = 0 ~ 9 ~ 2      // indices 0, 2, 4, 6, 8 (step 2)

// PimpedDouble — arithmetic with arrays and matrices
2.0 + someArray       // returns VectorExpression
3.0 * someMatrix      // returns MatrixExpression
```

### Array extensions (`PimpedDoubleArray`, `PimpedArray2D`)

```scala
import smile.math.*

val a = Array(1.0, 2.0, 3.0)
val b = Array(4.0, 5.0, 6.0)

a += b            // in-place element-wise addition
a -= b
a *= 2.0
a /= 2.0

// 2-D
val m: Array[Array[Double]] = …
m.toMatrix        // converts to DenseMatrix

// Sampling
val sample = a.sample(50)   // draw 50 elements without replacement
```

### `VectorExpression` operators

```scala
val u: VectorExpression = …
val v: VectorExpression = …

u + v           // element-wise addition  → VectorExpression
u - v
u * 3.0
u %*% v         // dot product           → Double (via simplify.toVector)
```

### `MatrixExpression` operators

```scala
val A: MatrixExpression = …
val B: MatrixExpression = …

A + B
A - B
A * B           // element-wise
A %*% B         // matrix multiplication (uses optimal chain order)
A.t             // transpose
A * v           // matrix-vector product

// Solve A x = b
val x = A \ b   // via LU or QR depending on shape
```

### Linear algebra helpers (top-level in `smile.math`)

```scala
import smile.math.*

zeros(3, 4)                   // 3×4 zero matrix
ones(3, 4)
eye(5)                        // identity
rand(3, 3)                    // uniform random
randn(3, 3)                   // Gaussian random

trace(A)
diag(A)                       // extract diagonal or build diagonal matrix
lu(A)                         // LU decomposition
qr(A)                         // QR decomposition
cholesky(A)
eig(A)                        // eigenvalues only
eigen(A)                      // full eigendecomposition
svd(A)
det(A)
rank(A)
inv(A)
```

### Statistical tests (top-level)

```scala
import smile.math.*

chisqtest(freq)               // Chi-squared goodness-of-fit
chisqtest2(x, y)              // Two-sample Chi-squared
ftest(x, y)                   // F-test for variance equality
ttest(x, mean)                // One-sample t-test
ttest2(x, y)                  // Two-sample t-test
ttest(x, y, paired = true)    // Paired t-test
kstest(x, dist)               // Kolmogorov-Smirnov
pearsontest(x, y)             // Pearson correlation
spearmantest(x, y)            // Spearman rank correlation
kendalltest(x, y)             // Kendall tau

// Contingency-table Chi-squared
chisqtest(table)
```

### Special functions (top-level)

```scala
import smile.math.*

beta(a, b); erf(x); erfc(x); gamma(x); lgamma(x); digamma(x)
inverf(p); inverfc(p); erfcc(x)
```

---

## Computer Algebra System (CAS)

The `smile.cas` package provides **symbolic** scalars, vectors, and matrices.
Import `smile.cas.*` to enable implicit conversions from Scala literals to CAS
nodes.

### Scalars

```scala
import smile.cas.*

// Literals become CAS nodes automatically
val x: Var = "x"          // Var — symbolic variable
val a: Val = 3.14          // Val — numeric constant
val n: IntVal = 2          // integer constant

// Arithmetic
val expr = x * x + 2 * x + 1      // Scalar expression
val diff = expr.d("x")             // symbolic derivative w.r.t. x: 2*x + 2
val simplified = diff.simplify     // simplification

// Helper functions
val f = exp(x) + log(x) + sqrt(x) + sin(x) + cos(x) + tan(x)
val g = abs("y") + ceil("z") + floor("w")
```

### Vectors

```scala
import smile.cas.*

val v = Vector("a", "b", "c")     // 3-element symbolic vector
val u = Vector("x", "y")
val dot = v * u                    // dot product expression
val jac = v.d("x")                 // Jacobian w.r.t. scalar
```

### Matrices

```scala
import smile.cas.*

val M = Matrix("M")                // symbolic matrix variable
val N = Matrix("N")
val prod = M * N                   // symbolic matrix product
val inv  = M.inv                   // symbolic inverse
val grad = M.d("alpha")            // derivative w.r.t. scalar parameter
```

---

## Classification

Import `smile.classification.*`.  Every function is wrapped with `time(…)`
which logs its wall-clock duration.

### K-Nearest Neighbors

```scala
import smile.classification.*

// From a pre-built KNN search structure
val model = knn(knnSearch, y, k = 5)

// Build automatically from feature matrix (custom distance)
val model = knn(x, y, k = 5, distance = new EuclideanDistance)

// Euclidean distance shortcut
val model = knn(x, y, k = 5)
```

### Logistic Regression

```scala
val model = logit(x, y,
  lambda  = 0.01,    // L2 regularization (0 = none)
  tol     = 1e-5,    // convergence tolerance
  maxIter = 500)
```

### Maximum Entropy (Multinomial Logistic for sparse features)

```scala
// x(i) is a sparse binary feature: array of non-zero feature indices
val model = maxent(x, y,
  p       = 50000,   // feature space dimension
  lambda  = 0.1,
  tol     = 1e-5,
  maxIter = 500)
```

### Multilayer Perceptron

```scala
import smile.model.mlp.*
import smile.util.function.TimeFunction

val layers = Array(
  Layer.input(4),
  Layer.sigmoid(20),
  Layer.mle(3, OutputFunction.SOFTMAX)
)

val model = mlp(x, y, layers,
  epochs      = 10,
  learningRate = TimeFunction.linear(0.01, 10000, 0.001),
  momentum     = TimeFunction.constant(0.0),
  weightDecay  = 0.0,
  rho          = 0.0,
  epsilon      = 1e-7)
```

### RBF Network

```scala
// Provide explicit RBF neurons
val neurons = RBF.fit(x, k = 10)
val model = rbfnet(x, y, neurons, normalized = false)

// Convenience: build Gaussian RBF with k-means automatically
val model = rbfnet(x, y, k = 10, normalized = false)
```

### Support Vector Machine

```scala
import smile.math.kernel.*

val kernel = new GaussianKernel(sigma = 1.0)

val model = svm(x, y, kernel,
  C      = 1.0,
  tol    = 1e-3,
  epochs = 1)
```

### Decision Tree (CART)

```scala
import smile.data.formula.*
import smile.model.cart.SplitRule

val model = cart(formula, data,
  splitRule = SplitRule.GINI,
  maxDepth  = 20,
  maxNodes  = 0,    // 0 = unlimited
  nodeSize  = 5)
```

### Random Forest

```scala
val model = randomForest(formula, data,
  ntrees      = 500,
  mtry        = 0,           // 0 = floor(sqrt(p))
  splitRule   = SplitRule.GINI,
  maxDepth    = 20,
  maxNodes    = 500,
  nodeSize    = 1,
  subsample   = 1.0,         // 1.0 = with replacement
  classWeight = null,
  seeds       = null)
```

### Gradient Boosted Trees

```scala
val model = gbm(formula, data,
  ntrees    = 500,
  maxDepth  = 20,
  maxNodes  = 6,
  nodeSize  = 5,
  shrinkage = 0.05,
  subsample = 0.7)
```

### AdaBoost

```scala
val model = adaboost(formula, data,
  ntrees   = 500,
  maxDepth = 20,
  maxNodes = 6,
  nodeSize = 1)
```

### Discriminant Analysis

```scala
// Fisher's Linear Discriminant
val model = fisher(x, y, L = -1, tol = 1e-4)

// Linear Discriminant Analysis
val model = lda(x, y, priori = null, tol = 1e-4)

// Quadratic Discriminant Analysis
val model = qda(x, y, priori = null, tol = 1e-4)

// Regularized Discriminant Analysis (blends LDA and QDA)
val model = rda(x, y,
  alpha  = 0.5,       // 0 = LDA, 1 = QDA
  priori = null,
  tol    = 1e-4)
```

### Naive Bayes

```scala
import smile.classification.DiscreteNaiveBayes

// Document classification with add-k smoothing
val model = naiveBayes(x, y,
  model  = DiscreteNaiveBayes.Model.MULTINOMIAL,
  priori = null,
  sigma  = 1.0)

// General form with continuous distributions
val model = naiveBayes(priori, condprob)
```

### Multiclass Wrappers

```scala
// One-vs-One (K*(K-1)/2 binary classifiers; max-wins voting)
val model = ovo(x, y) { (x, y) => svm(x, y, kernel, C = 1.0) }

// One-vs-Rest (K binary classifiers; highest confidence wins)
val model = ovr(x, y) { (x, y) => svm(x, y, kernel, C = 1.0) }
```

Both `ovo` and `ovr` accept any trainer function `(Array[T], Array[Int]) => Classifier[T]`,
expressed as a curried Scala lambda.

---

## Regression

Import `smile.regression.*`.

### Linear Models

```scala
import smile.data.formula.*
import smile.regression.*

// Ordinary Least Squares
val model = lm(formula, data,
  method    = OLS.Method.QR,  // "svd" or "qr"
  stderr    = true,
  recursive = true)

// Ridge Regression (L2 penalty)
val model = ridge(formula, data, lambda = 0.1)

// LASSO (L1 penalty; produces sparse solutions)
val model = lasso(formula, data,
  lambda  = 0.1,
  tol     = 1e-3,
  maxIter = 5000)
```

### Support Vector Regression

```scala
val model = svm(x, y, kernel,
  eps = 0.1,   // epsilon-insensitive loss threshold
  C   = 1.0,   // soft-margin penalty
  tol = 1e-3)
```

### Regression Tree and Ensembles

```scala
// Single regression tree
val model = cart(formula, data, maxDepth = 20, maxNodes = 0, nodeSize = 5)

// Random Forest
val model = randomForest(formula, data,
  ntrees    = 500,
  mtry      = 0,
  maxDepth  = 20,
  maxNodes  = 500,
  nodeSize  = 5,
  subsample = 1.0)

// Gradient Boosted Trees
import smile.model.cart.Loss

val model = gbm(formula, data,
  loss      = Loss.lad(),   // least absolute deviation (robust default)
  ntrees    = 500,
  maxDepth  = 20,
  maxNodes  = 6,
  nodeSize  = 5,
  shrinkage = 0.05,
  subsample = 0.7)
```

### Gaussian Process Regression

Grouped under the `gpr` object:

```scala
import smile.regression.gpr
import smile.math.kernel.GaussianKernel

val kernel = new GaussianKernel(sigma = 1.0)

// Full GP — O(n³) in training, exact inference
val model = gpr(x, y, kernel,
  noise     = 0.01,
  normalize = true,
  tol       = 1e-5,
  maxIter   = 0)    // maxIter=0 skips hyperparameter optimization

// Subset-of-Regressors approximation (inducing points t ⊂ x)
val model = gpr.approx(x, y, t, kernel, noise = 0.01)

// Nyström approximation (inducing points may be external)
val model = gpr.nystrom(x, y, t, kernel, noise = 0.01)
```

### RBF Network

```scala
// Provide explicit neurons
val model = rbfnet(x, y, neurons, normalized = false)

// Convenience: Gaussian RBF via k-means
val model = rbfnet(x, y, k = 10)
```

---

## Clustering

Import `smile.clustering.*`.

### Hierarchical Clustering

```scala
import smile.clustering.*

// Euclidean distance; method ∈ "single" | "complete" | "upgma" | "average" |
//                                      "upgmc" | "centroid" | "wpgma" |
//                                      "wpgmc" | "median" | "ward"
val hc = hclust(data, "ward")

// Custom distance
val hc = hclust(data, myDistance, "complete")

// Cut the dendrogram to obtain k clusters
val labels = hc.partition(k = 5)
```

### Partitional Clustering

```scala
// K-Means (best of 16 runs by default)
val km = kmeans(data, k = 5, maxIter = 100, runs = 16)
println(km.k)           // actual number of clusters
println(km.distortion)  // within-cluster sum of squared distances

// K-Modes (binary / categorical data)
val km = kmodes(data, k = 5, maxIter = 100, runs = 10)

// X-Means — automatically determines k using BIC
val xm = xmeans(data, k = 20)   // k is the upper bound

// G-Means — automatically determines k using Gaussian normality test
val gm = gmeans(data, k = 20)

// Deterministic Annealing
val da = dac(data, k = 10, alpha = 0.9)

// CLARANS (medoid-based; any distance)
val cl = clarans(data, myDistance, k = 5)
```

### Density-Based Clustering

```scala
// DBSCAN with Euclidean distance
val db = dbscan(data, minPts = 5, radius = 0.5)

// DBSCAN with custom distance
val db = dbscan(data, myDistance, minPts = 5, radius = 0.5)

// DBSCAN with pre-built RNN search structure
val db = dbscan(data, rnnSearch, minPts = 5, radius = 0.5)

// DENCLUE (kernel-density attractors)
val dc = denclue(data, sigma = 0.5, m = 50)
```

### Information-Theoretic Clustering

```scala
import smile.util.SparseArray

// SIB — co-occurrence data (e.g. document–word)
val sb = sib(sparseData, k = 10, maxIter = 100, runs = 8)

// MEC — minimum conditional entropy (works with any distance)
val mc = mec(data, myDistance, k = 10, radius = 0.5)
val mc = mec(data, myMetric,   k = 10, radius = 0.5)
val mc = mec(data,              k = 10, radius = 0.5)  // Euclidean shortcut

// Spectral Clustering
val sp = specc(data, k = 5, sigma = 1.0, l = 0, maxIter = 100)
```

Cluster assignments are accessed as:

```scala
model.y          // Array[Int] of cluster labels (-1 = noise in DBSCAN)
model.centroids  // cluster centres (for centroid-based models)
```

---

## Dimensionality Reduction

Import `smile.feature.extraction.*`.  All methods are wrapped with `time(…)`.

```scala
import smile.feature.extraction.*

// PCA — Principal Component Analysis
val pca = pca(data)
val pca = pca(data, cor = true)       // use correlation matrix

// Probabilistic PCA (handles missing values)
val ppca = ppca(data, k = 10)

// Kernel PCA
import smile.math.kernel.GaussianKernel
val kpca = kpca(data, kernel = new GaussianKernel(1.0), k = 10)
val kpca = kpca(data, new GaussianKernel(1.0), k = 10, threshold = 1e-4)

// Generalized Hebbian Algorithm (online / incremental PCA)
val gha = gha(data, k = 10)
val gha = gha(data, k = 10, r = 0.0001)
```

After fitting, project new data:

```scala
val embedding = pca.project(newData)
pca.setProjection(k)   // change number of retained components
```

---

## Manifold Learning

Import `smile.manifold.*`.  All methods return low-dimensional coordinate arrays
(`Array[Array[Double]]`) or dedicated result objects.

```scala
import smile.manifold.*

// Isomap — geodesic MDS (C-Isomap variant by default)
val coords = isomap(data, k = 10, d = 2, CIsomap = true)

// Locally Linear Embedding
val coords = lle(data, k = 10, d = 2)

// Laplacian Eigenmap
val coords = laplacian(data, k = 10, d = 2, t = -1.0)
// t > 0 uses Gaussian heat kernel; t ≤ 0 uses binary weights

// t-SNE (2-D or 3-D; input may be pre-computed distance matrix)
val result = tsne(data,
  d               = 2,
  perplexity      = 20.0,
  eta             = 200.0,
  earlyExaggeration = 12.0,
  maxIter         = 1000)
val coords = result.coordinates

// UMAP
val coords = umap(data,
  k                = 15,
  d                = 2,
  epochs           = 0,       // 0 = auto
  learningRate     = 1.0,
  minDist          = 0.1,
  spread           = 1.0,
  negativeSamples  = 5,
  repulsionStrength = 1.0)

// Classical MDS (equivalent to PCA when Euclidean distances are used)
val result = mds(proximity, d = 2)

// Non-metric (Kruskal) MDS
val result = isomds(proximity, d = 2, tol = 1e-4, maxIter = 200)

// Sammon Mapping
val result = sammon(proximity, d = 2, step = 0.2, maxIter = 100)
```

---

## Natural Language Processing

Import `smile.nlp.*`.  The implicit conversion `pimpString` enriches every
`String` with NLP pipeline methods.

### String extension methods

```scala
import smile.nlp.*

val text = "Dr. Smith went to Washington D.C. He arrived on Tuesday."

// Unicode normalization (NFKC, whitespace normalization, quote normalization)
val clean = text.normalize

// Sentence splitting
val sentences: Array[String] = text.sentences

// Tokenization with stop-word filtering
val words: Array[String] = text.words                     // default stop list
val words: Array[String] = text.words("comprehensive")    // larger stop list
val words: Array[String] = text.words("none")             // no filtering
val words: Array[String] = text.words("the,a,an")         // custom stop list

// Bag-of-words (word → count)
val bag: Map[String, Int] = text.bag()                      // Porter stemming
val bag: Map[String, Int] = text.bag(stemmer = None)        // no stemming
val bag: Map[String, Int] = text.bag(filter = "google")

// Binary bag-of-words (presence/absence)
val bag2: Set[String] = text.bag2()

// Part-of-speech tagging (returns word–POS pairs)
val tagged: Array[(String, PennTreebankPOS)] = "She sells seashells".postag

// Keyword extraction
val keywords: Seq[NGram] = text.keywords(k = 10)
```

### Corpus and n-gram utilities

```scala
import smile.nlp.*

// Build an in-memory corpus
val corp = corpus(Seq("First document text.", "Second document text."))

// Bigram collocations
val topBigrams: Seq[Bigram] = bigram(k = 100, minFreq = 5, docs: _*)
val sigBigrams: Seq[Bigram] = bigram(p = 0.01, minFreq = 5, docs: _*)

// N-gram extraction (Apriori-style)
val grams: Array[Array[NGram]] = ngram(maxNGramSize = 3, minFreq = 3, docs: _*)

// HMM POS tagging on a pre-tokenised sentence
val tags: Array[PennTreebankPOS] = postag(Array("She", "sells", "seashells"))
```

### Stemming

```scala
import smile.nlp.*

porter.stem("running")    // "run"
lancaster.stem("running") // "run" (more aggressive)
```

### Vectorization and TF-IDF

```scala
import smile.nlp.*

// Term-frequency feature vector
val vocab    = Array("machine", "learning", "deep")
val features = vectorize(vocab, bag)          // Array[Double]
val sparse   = vectorize(vocab, bag2)         // Array[Int] (indices of present terms)

// Document frequency array
val dfreq: Array[Int] = df(vocab, corpusOfBags)

// Whole-corpus TF-IDF normalized to unit L2 norm
val matrix: Array[Array[Double]] = tfidf(corpusOfBags)

// Single document
val vec: Array[Double] = tfidf(bag, n = corpusSize, df = dfreq)
```

---

## Sequence Labeling

Import `smile.sequence.*`.

```scala
import smile.sequence.*

// Hidden Markov Model
val model = hmm(pi, a, b)          // from initial / transition / emission
val model = hmm(observations, k)   // learns from observation sequences

// Conditional Random Field (linear-chain)
val model = crf(x, y, feature, k, eta = 0.1, lambda = 0.1)

// CRF with Gaussian process smoothing
val model = gcrf(x, y, feature, k, eta = 0.1, lambda = 0.1)
```

---

## Association Rule Mining

Import `smile.association.*`.

```scala
import smile.association.*

val itemsets: Array[Array[Int]] = …

// Build FP-tree
val tree = fptree(itemsets)
val tree = fptree(itemsets.toStream)  // streaming variant

// Mine frequent item sets
val frequent = fpgrowth(tree, minSupport = 3)
val frequent = fpgrowth(itemsets, minSupport = 3)

// Generate association rules
val rules = arm(tree, minSupport = 3, confidence = 0.5)
val rules = arm(itemsets, minSupport = 3, confidence = 0.5)
```

---

## Wavelets

Import `smile.wavelet.*`.

```scala
import smile.wavelet.*

val wt = wavelet("D4")   // Daubechies-4 filter
// Available filters include: "Haar", "D4"–"D20" (even), "Coiflet1"–"Coiflet5", etc.

val signal = Array(1.0, 2.0, 3.0, 4.0, 3.0, 2.0, 1.0, 0.0)

// In-place discrete wavelet transform
dwt(signal, wt)

// In-place inverse DWT
idwt(signal, wt)

// Wavelet shrinkage denoising (modifies in-place)
wsdenoise(signal, wt, soft = true)
```

---

## Model Validation

Import `smile.validation.*`.

### One-shot train/test evaluation

```scala
import smile.validation.*

// With raw arrays
val result = validate.classification(x, y, testX, testY) { (x, y) =>
  randomForest(Formula.lhs("label"), DataFrame.of(x, y), ntrees = 100)
}

// With DataFrame + Formula
val result = validate.classification(formula, trainDf, testDf) { (f, df) =>
  randomForest(f, df)
}

// Regression variants
val result = validate.regression(x, y, testX, testY) { (x, y) => lm(…) }
val result = validate.regression(formula, train, test) { (f, df) => lm(f, df) }
```

### Cross-Validation

```scala
val cv5 = cv.classification(k = 5, formula, data) { (f, df) =>
  randomForest(f, df)
}
println(cv5.avg.accuracy)

// With raw arrays
val cv5 = cv.classification(k = 5, x, y) { (x, y) =>
  lda(x, y)
}

// Regression
val cv5r = cv.regression(k = 5, formula, data) { (f, df) => lm(f, df) }
val cv5r = cv.regression(k = 5, x, y) { (x, y) => ridge(…) }
```

### Leave-One-Out CV

```scala
val loo = loocv.classification(formula, data) { (f, df) => cart(f, df) }
val loo = loocv.regression(x, y) { (x, y) => lasso(…) }
```

### Bootstrap

```scala
val boot = bootstrap.classification(k = 100, x, y) { (x, y) => knn(x, y, 5) }
val boot = bootstrap.regression(k = 100, formula, data) { (f, df) => gbm(f, df) }
```

### Individual metric functions

```scala
import smile.validation.*

// Classification
val cm   = confusion(truth, predictions)
val acc  = accuracy(truth, predictions)
val rec  = recall(truth, predictions)
val prec = precision(truth, predictions)
val f1   = f1(truth, predictions)
val auc  = auc(truth, probabilities)
val ll   = logloss(truth, probabilities)
val ce   = crossentropy(truth, probMatrix)
val mcc  = mcc(truth, predictions)
val sens = sensitivity(truth, predictions)
val spec = specificity(truth, predictions)
val fo   = fallout(truth, predictions)
val fdr  = fdr(truth, predictions)

// Regression
val mseVal  = mse(truth, predictions)
val rmseVal = rmse(truth, predictions)
val rssVal  = rss(truth, predictions)
val madVal  = mad(truth, predictions)

// Clustering
val ri  = randIndex(labels1, labels2)
val ari = adjustedRandIndex(labels1, labels2)
val nmiVal = nmi(labels1, labels2)
```

---

## Plotting

The module includes two complementary plot APIs:

- **`smile.plot.swing`** — traditional Swing-based `Canvas` charts for desktop
  use.
- **`smile.plot.vega`** — Vega-Lite declarative charts for notebooks and
  browser-based output.

### Displaying a chart (`show`)

```scala
import smile.plot.*

// Render a Canvas in a JFrame (desktop) or as HTML (notebook)
show(canvas)
show(multiFigurePane)
show(vegaLiteSpec)
```

In Scala 2.13 notebook environments the `show` implicit calls are backed by
macros that detect Zeppelin/Databricks context at compile time and emit HTML
`<img>` tags instead of opening a Swing window.

### Swing plots (`smile.plot.swing.*`)

Every chart returns a `Canvas` that can be passed to `show(…)`.

```scala
import smile.plot.swing.*

// Scatter plot
val c = plot(x, y, '.')             // Array[Double] x and y
val c = plot(data, labels, marks)   // colour-coded by class label

// Scatter-plot matrix
val c = splom(data, marks, colNames)

// Line plot
val c = line(x, y)
val c = staircase(x, y)

// Box plot
val c = boxplot(data)
val c = boxplot(groups, names)

// Histogram
val c = hist(data)
val c = hist(data, bins = 20)
val c = hist3(x, y, bins = 20)

// Q-Q plot
val c = qqplot(data)                    // vs normal
val c = qqplot(x, y)                    // two-sample
val c = qqplot(data, distribution)      // vs arbitrary distribution

// Heatmap and sparse matrix spy plot
val c = heatmap(matrix)
val c = spy(sparseMatrix)
val c = hexmap(data)

// Contour and surface
val c = contour(x, y, z)
val c = surface(z)
val c = wireframe(vertices, edges)
val c = grid(ax, ay, az)

// Dendrogram
val c = dendrogram(hierarchicalClustering)

// Scree plot (PCA)
val c = screeplot(pca)

// Text annotations
val c = text(coords, labels)
```

### Vega-Lite charts (`smile.plot.vega.*`)

Build declarative specs using a fluent Scala API. The `VegaLite` companion
object is the entry point.

```scala
import smile.plot.vega.*

// Single view
val view = VegaLite.view()
  .mark("point")
  .x(Field("sepalLength", "quantitative"))
  .y(Field("petalLength", "quantitative"))
  .color(Field("species",  "nominal"))
  .data(irisDataFrame)

show(view)

// Layered chart (multiple marks in the same coordinate system)
val chart = VegaLite.layer(view1, view2)

// Faceted chart
val faceted = VegaLite.facet(view).row("origin").column("cylinders")

// Concatenated charts
val hcat = VegaLite.hconcat(view1, view2, view3)
val vcat = VegaLite.vconcat(view1, view2)

// Scatter-plot matrix
val splomChart = VegaLite.splom(irisDataFrame)

// Fluent global properties
val chart = VegaLite.view()
  .background("#f5f5f5")
  .padding(10)
  .config(JsObject("view" -> JsObject("stroke" -> JsString("transparent"))))
```

---

## Utility Helpers

### `time` — measure and log execution time

```scala
import smile.util.time

// Block form — returns the value, logs elapsed time with a label
val model = time("Random Forest") {
  randomForest(formula, data, ntrees = 500)
}

// Toggle output
time.on()   // enable timing output (default)
time.off()  // suppress timing output
time.echo   // check current state
```

### Implicit Java function converters

```scala
import smile.util.{toJavaFunction, toJavaBiFunction}

// Convert Scala lambdas to java.util.function types automatically
val jf:   java.util.function.Function[Int, String]          = (i: Int) => i.toString
val jbf:  java.util.function.BiFunction[Int, Int, Int]      = (a: Int, b: Int) => a + b
```

These conversions are automatically applied wherever SMILE's Java API requires a
`Function` or `BiFunction` — for example, when passing trainers to `ovo`, `ovr`,
`validate.classification`, or `cv.classification`.

---

## Complete Examples

### Example 1 — Load data and train a classifier

```scala
import smile.io.*
import smile.data.formula.*
import smile.classification.*
import smile.validation.*

val df      = read.csv("iris.csv")
val formula = "class" ~ "."

// 5-fold cross-validation on a random forest
val result = cv.classification(k = 5, formula, df) { (f, d) =>
  randomForest(f, d, ntrees = 100)
}

println(s"CV accuracy: ${result.avg.accuracy * 100 %.1f %%}")
```

### Example 2 — Text classification pipeline

```scala
import smile.io.*
import smile.nlp.*
import smile.classification.*
import smile.validation.*

val texts  = Array("great product", "terrible service", "very happy")
val labels = Array(1, 0, 1)

// Build vocabulary from training data
val bags  = texts.map(_.bag())
val vocab = bags.flatMap(_.keys).distinct.sorted

// Vectorise
val x = bags.map(b => vectorize(vocab, b))
val y = labels

// Train and evaluate
val result = cv.classification(k = 3, x, y) { (x, y) =>
  logit(x, y, lambda = 0.01)
}
println(result.avg.accuracy)
```

### Example 3 — Regression with cross-validation

```scala
import smile.io.*
import smile.data.formula.*
import smile.regression.*
import smile.validation.*

val longley = read.arff("data/regression/longley.arff")
val formula = "Employed" ~ "."

val cv5 = cv.regression(k = 5, formula, longley) { (f, df) =>
  lm(f, df)
}
println(f"RMSE: ${cv5.avg.rmse}%.4f")
```

### Example 4 — Gaussian Process Regression with Nyström approximation

```scala
import smile.regression.gpr
import smile.math.kernel.GaussianKernel

val kernel = new GaussianKernel(sigma = 1.0)

// Inducing inputs (e.g. k-means centroids of x)
import smile.clustering.*
val km = kmeans(x, k = 200)
val t  = km.centroids

val model = gpr.nystrom(x, y, t, kernel, noise = 0.01, normalize = true)
val predictions = x.map(model.predict)
```

### Example 5 — NLP keyword extraction

```scala
import smile.nlp.*

val text = """
  Machine learning is a field of artificial intelligence. It enables computers
  to learn from experience without being explicitly programmed.
"""

val keywords = text.keywords(k = 5)
keywords.foreach(ng => println(ng.words.mkString(" ")))
```

### Example 6 — Manifold learning and visualization

```scala
import smile.io.*
import smile.manifold.*
import smile.plot.swing.*
import smile.plot.*

val (x, _) = read.csv("mnist.csv").toArray …  // high-dimensional data

val embedding = umap(x, k = 15, d = 2)

val canvas = plot(embedding.map(_(0)), embedding.map(_(1)), '.')
show(canvas)
```

### Example 7 — Symbolic differentiation with CAS

```scala
import smile.cas.*

val x = "x"
val y = "y"

// Define f(x, y) = x² y + sin(x) y
val f = (x ** 2) * y + sin(x) * y

// Partial derivatives
val df_dx = f.d("x").simplify   // 2 x y + cos(x) y
val df_dy = f.d("y").simplify   // x² + sin(x)

println(df_dx)
println(df_dy)

// Evaluate at x=1, y=2
val env = Map("x" -> 1.0, "y" -> 2.0)
println(df_dx.apply(env))
```

---

## Notable Differences from the Kotlin Shim

| Aspect | Scala | Kotlin |
|---|---|---|
| Extension mechanism | Implicit classes (`PimpedXxx`) via `implicit def` | Extension functions |
| Formula DSL | Rich operator DSL: `~`, `+`, `-`, `::`, `&&`, `^`, function terms | Not present |
| CAS | Full symbolic algebra (`smile.cas`) | Not present |
| Plotting | Both Swing (`smile.plot.swing`) and Vega-Lite (`smile.plot.vega`) | Not present |
| Notebook rendering | Macro-detected at compile time (Scala 2.13) | N/A |
| Validation API | Object-based: `validate`, `cv`, `loocv`, `bootstrap` | Top-level functions |
| Sequence models | HMM, CRF, GCRF | Not present |
| Operator DSL | `%*%` (dot/matmul), `\` (solve), `~` (slice) | N/A |
| Array slicing | `0 ~ 9 ~ 2` (Python-like with step) | N/A |
| `gpr` namespace | `object gpr { apply, approx, nystrom }` | `object gpr` (same) |

Both shims expose the same underlying Java algorithms.  The Kotlin shim focuses
on function-level conciseness; the Scala shim additionally provides a richer
operator language and is more appropriate for exploratory notebook workflows that
involve linear algebra, symbolic math, and interactive visualization.

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
