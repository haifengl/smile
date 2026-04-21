# SMILE Kotlin API

The `smile-kotlin` module is a thin idiomatic shim over the SMILE Java
library.  It adds nothing algorithmic — every function ultimately delegates to
the same Java `fit`, `of`, or constructor call — but it replaces verbose Java
API patterns with concise, readable Kotlin idioms:

- **Short top-level functions** with sensible default arguments replace static
  factory methods with long argument lists.
- **`String` extension functions** turn NLP pipelines into left-to-right chains.
- **`read` / `write` objects** provide a namespace that avoids static imports.
- **Kotlin function types** replace `BiFunction` / `Trainer` SAM interfaces
  in multiclass wrappers.
- **`object` namespaces** (e.g. `gpr`) group related variants next to their
  primary function without cluttering the top-level package.

The module depends on `:core` (ML), `:base` (data and I/O), and `:nlp`.

---

## Table of Contents

1. [Installation](#installation)
2. [Data I/O — `read` and `write`](#data-io--read-and-write)
3. [Classification](#classification)
4. [Regression](#regression)
5. [Clustering](#clustering)
6. [Natural Language Processing](#natural-language-processing)
7. [Dimensionality Reduction and Projection](#dimensionality-reduction-and-projection)
8. [Manifold Learning](#manifold-learning)
9. [Association Rule Mining](#association-rule-mining)
10. [Wavelets](#wavelets)
11. [Complete Examples](#complete-examples)

---

## Installation

Add the module to your Gradle build alongside the rest of SMILE:

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":smile-kotlin"))  // or the published artifact
}
```

Each source file lives in its own package that mirrors the Java package, so
imports are natural:

```kotlin
import smile.classification.*   // knn, logit, cart, randomForest, …
import smile.regression.*       // lm, ridge, lasso, cart, randomForest, gpr, …
import smile.clustering.*       // kmeans, hclust, dbscan, …
import smile.nlp.*              // String.normalize(), String.bag(), …
import smile.*                  // read, write objects
```

---

## Data I/O — `read` and `write`

All I/O is grouped in two singleton objects defined in `package smile`.

### Reading data

```kotlin
import smile.*

// CSV — automatic header detection, comma delimiter (defaults)
val df = read.csv("data/iris.csv")

// CSV — tab-delimited, no header, comment lines start with '%'
val usps = read.csv("data/zip.train", delimiter = " ", header = false)
val arff = read.csv("data/weather.arff", comment = '%')

// CSV with explicit Apache Commons CSV format
val df2 = read.csv("data/myfile.csv", CSVFormat.EXCEL)

// Other tabular formats
val arff    = read.arff("data/weather.nominal.arff")
val json    = read.json("data/books.json")
val sas     = read.sas("data/airline.sas7bdat")
val arrow   = read.arrow("data/table.arrow")
val parquet = read.parquet("file:///abs/path/table.parquet")
val avro    = read.avro("data/users.avro", "data/users.avsc")

// LIBSVM sparse format
val sparse: SparseDataset<Int> = read.libsvm("data/train.svm")

// From a JDBC ResultSet
val db: DataFrame = read.jdbc(resultSet)
```

All overloads accept both `String` paths and `java.nio.file.Path` objects.
The `csv` overload accepts an optional `StructType` schema as its last
argument to enforce column types at parse time.

#### CSV options in full

```kotlin
read.csv(
    file      = "data/prices.csv",
    delimiter = ",",          // any single character or multi-char string
    header    = true,         // skip first row as column names
    quote     = '"',          // quote character
    escape    = '\\',         // escape character
    comment   = '#',          // lines starting with this char are skipped
    schema    = null          // optional StructType for type enforcement
)
```

### Writing data

```kotlin
import smile.*

write.csv(df, "output/results.csv")               // comma-separated
write.csv(df, "output/results.tsv", delimiter = '\t')
write.arff(df, "output/results.arff", "MyRelation")
write.arrow(df, "output/results.arrow")
```

---

## Classification

All functions live in `package smile.classification` and return the concrete
Java model type so you can call `.predict()`, `.score()`, and other Java
methods directly on the result.

### k-Nearest Neighbor

```kotlin
// Euclidean distance (most common)
val model: KNN<DoubleArray> = knn(x, y, k = 5)

// Custom distance
val model = knn(x, y, k = 5, distance = myDistance)

// Prebuilt KNN search structure (e.g. KD-tree)
val kdTree = KDTree.of(x)
val model  = knn(kdTree, y, k = 5)
```

### Logistic Regression

```kotlin
val model = logit(
    x       = x,
    y       = y,
    lambda  = 0.1,     // L2 regularization; default 0.0 (no penalty)
    tol     = 1E-5,    // convergence tolerance
    maxIter = 500
)
```

### Decision Tree (CART)

```kotlin
val model = cart(
    formula   = Formula.lhs("label"),
    data      = df,
    splitRule = SplitRule.GINI,   // or ENTROPY, CLASSIFICATION_ERROR
    maxDepth  = 20,
    maxNodes  = 0,                // 0 = unlimited
    nodeSize  = 5                 // minimum leaf size
)
```

### Random Forest

```kotlin
val model = randomForest(
    formula     = Formula.lhs("label"),
    data        = df,
    ntrees      = 500,
    mtry        = 0,              // 0 = sqrt(p) automatically
    splitRule   = SplitRule.GINI,
    maxDepth    = 20,
    maxNodes    = 500,
    nodeSize    = 1,
    subsample   = 1.0,            // < 1.0 = sampling without replacement
    classWeight = null,           // IntArray for imbalanced classes
    seeds       = null            // LongArray for reproducibility
)
```

### Gradient Boosted Trees

```kotlin
val model = gbm(
    formula   = Formula.lhs("label"),
    data      = df,
    ntrees    = 500,
    maxDepth  = 20,
    maxNodes  = 6,
    nodeSize  = 5,
    shrinkage = 0.05,   // learning rate
    subsample = 0.7
)
```

### AdaBoost

```kotlin
val model = adaboost(
    formula  = Formula.lhs("label"),
    data     = df,
    ntrees   = 500,
    maxDepth = 20,
    maxNodes = 6,
    nodeSize = 1
)
```

### Support Vector Machine

```kotlin
import smile.math.kernel.GaussianKernel

val model = svm(
    x      = x,
    y      = y,
    kernel = GaussianKernel(8.0),
    C      = 100.0,
    tol    = 1E-3,
    epochs = 1         // LASVM training passes
)
```

### Discriminant Analysis

```kotlin
val fld = fisher(x, y, L = -1, tol = 1E-4)   // Fisher's LDA, L = classes-1
val lda = lda(x, y, priori = null, tol = 1E-4)
val qda = qda(x, y, priori = null, tol = 1E-4)
val rda = rda(x, y, alpha = 0.9)              // alpha=0 → LDA, alpha=1 → QDA
```

### Multilayer Perceptron

```kotlin
import smile.model.mlp.*
import smile.util.function.TimeFunction

val model = mlp(
    x           = x,
    y           = y,
    builders    = arrayOf(ReLULayer(256), SigmoidLayer(128)),
    epochs      = 20,
    learningRate = TimeFunction.linear(0.01, 10_000.0, 0.001),
    momentum    = TimeFunction.constant(0.0),
    weightDecay = 1E-4,
    rho         = 0.9,    // RMSProp decay; 0.0 disables RMSProp
    epsilon     = 1E-7
)
```

### Radial Basis Function Network

```kotlin
// With k-means centers (automatic)
val model = rbfnet(x, y, k = 30, normalized = false)

// With explicit neuron definitions
val neurons: Array<RBF<DoubleArray>> = RBF.fit(x, 30)
val model = rbfnet(x, y, neurons)
```

### Naïve Bayes

```kotlin
// Discrete / document Naïve Bayes (bag-of-words features)
val model = naiveBayes(
    x     = intArrayFeatures,
    y     = labels,
    model = DiscreteNaiveBayes.Model.MULTINOMIAL,
    sigma = 1.0   // add-1 (Laplace) smoothing
)

// General Naïve Bayes with arbitrary per-feature distributions
val model = naiveBayes(
    priori   = doubleArrayOf(0.5, 0.5),
    condprob = arrayOf(/* Array<Distribution> per class */)
)
```

### Maximum Entropy (Sparse Binary Features)

```kotlin
val model = maxent(
    x       = sparseIntFeatures,  // Array<IntArray> — indices of active features
    y       = labels,
    p       = vocabSize,
    lambda  = 0.1,
    tol     = 1E-5,
    maxIter = 500
)
```

### Multiclass Wrappers (One-vs-One / One-vs-Rest)

Kotlin function types replace Java `Trainer` SAM interfaces, making the
trainer argument concise:

```kotlin
// One-vs-one
val model = ovo(x, y) { xi, yi ->
    svm(xi, yi, kernel = GaussianKernel(1.0), C = 10.0)
}

// One-vs-rest
val model = ovr(x, y) { xi, yi ->
    logit(xi, yi, lambda = 0.1)
}
```

---

## Regression

All functions live in `package smile.regression`.

### Ordinary Least Squares

```kotlin
val model: LinearModel = lm(
    formula   = Formula.lhs("y"),
    data      = df,
    method    = OLS.Method.QR,   // or SVD, Cholesky
    stderr    = true,            // compute standard errors
    recursive = true
)

println(model)    // coefficient table with std. errors and p-values
```

### Ridge Regression

```kotlin
val model: LinearModel = ridge(Formula.lhs("y"), df, lambda = 1.0)
```

### LASSO

```kotlin
val model: LinearModel = lasso(
    formula = Formula.lhs("y"),
    data    = df,
    lambda  = 0.5,
    tol     = 1E-3,
    maxIter = 5000
)
```

### Support Vector Regression (ε-SVR)

```kotlin
import smile.math.kernel.GaussianKernel

val model: KernelMachine<DoubleArray> = svm(
    x      = x,
    y      = y,
    kernel = GaussianKernel(6.0),
    eps    = 0.5,   // ε-insensitive tube width
    C      = 5.0,
    tol    = 1E-3
)
```

### Regression Tree (CART)

```kotlin
val model: RegressionTree = cart(
    formula  = Formula.lhs("y"),
    data     = df,
    maxDepth = 20,
    maxNodes = 0,
    nodeSize = 5
)
```

### Random Forest

```kotlin
val model: RandomForest = randomForest(
    formula   = Formula.lhs("y"),
    data      = df,
    ntrees    = 500,
    mtry      = 0,      // 0 = p/3 automatically
    maxDepth  = 20,
    maxNodes  = 500,
    nodeSize  = 5,
    subsample = 1.0,
    seeds     = null
)
```

### Gradient Boosted Trees

```kotlin
import smile.model.cart.Loss

val model: GradientTreeBoost = gbm(
    formula   = Formula.lhs("y"),
    data      = df,
    loss      = Loss.lad(),    // default: Least Absolute Deviation (robust)
    ntrees    = 500,
    maxDepth  = 20,
    maxNodes  = 6,
    nodeSize  = 5,
    shrinkage = 0.05,
    subsample = 0.7
)

// Other loss functions
gbm(formula, df, loss = Loss.ls())     // Least Squares
gbm(formula, df, loss = Loss.huber())  // Huber (default M-estimate)
```

### Gaussian Process Regression

The `gpr` top-level function trains a full GP.  The `gpr` object provides
two scalable approximate variants:

```kotlin
import smile.math.kernel.GaussianKernel

// Full GP (O(n³)) — suitable for up to ~10,000 points
val model = gpr(
    x         = x,
    y         = y,
    kernel    = GaussianKernel(1.0),
    noise     = 1E-6,       // observation noise / jitter
    normalize = true,
    tol       = 1E-5,
    maxIter   = 0           // > 0 enables hyperparameter optimization
)

// Sparse GP — subset of regressors (inducing inputs)
val inducing = x.take(200).toTypedArray()
val approx   = gpr.approx(x, y, t = inducing, kernel = GaussianKernel(1.0), noise = 1E-6)

// Nyström approximation
val nystrom  = gpr.nystrom(x, y, t = inducing, kernel = GaussianKernel(1.0), noise = 1E-6)
```

### Radial Basis Function Network

```kotlin
// k-means centers with Gaussian basis (convenient one-liner)
val model: RBFNetwork<DoubleArray> = rbfnet(x, y, k = 30)

// Explicit RBF neurons
val neurons: Array<RBF<DoubleArray>> = RBF.fit(x, 30)
val model = rbfnet(x, y, neurons, normalized = true)
```

---

## Clustering

All functions live in `package smile.clustering`.

### Hierarchical Clustering

```kotlin
// Numeric matrix
val model: HierarchicalClustering = hclust(x, method = "ward")

// Valid method strings:
// "single", "complete", "upgma" / "average", "upgmc" / "centroid",
// "wpgma", "wpgmc" / "median", "ward"

// Generic objects with a custom distance
val model = hclust(objects, distance = myDistance, method = "complete")

// Cut the dendrogram into k clusters
val labels: IntArray = model.partition(k = 10)
```

### K-Means and Variants

```kotlin
// K-means++ with multi-start (16 restarts by default)
val model = kmeans(x, k = 8, maxIter = 100, runs = 16)

// K-modes (categorical data, integer-encoded)
val model = kmodes(x, k = 4, maxIter = 100, runs = 10)

// X-means (automatically selects k up to the given maximum)
val model = xmeans(x, k = 100, maxIter = 100)

// G-means (Gaussian assumption test to grow k)
val model = gmeans(x, k = 100, maxIter = 100)

// Deterministic annealing
val model = dac(x, k = 8, alpha = 0.9, maxIter = 100, tol = 1E-4)

// k-Medoids / CLARANS (works with arbitrary distance)
val model = clarans(x, distance = myDistance, k = 5)
```

### DBSCAN

```kotlin
// Euclidean distance (most common)
val model: DBSCAN<DoubleArray> = dbscan(x, minPts = 5, radius = 0.5)

// Custom distance
val model = dbscan(x, distance = myDistance, minPts = 5, radius = 0.5)

// Prebuilt range-nearest-neighbor search structure
val rnn   = LinearSearch.of(x, EuclideanDistance())
val model = dbscan(x, nns = rnn, minPts = 5, radius = 0.5)
```

### Other Density-Based Algorithms

```kotlin
val model = denclue(x, sigma = 0.5, m = 25)

// Minimum Entropy Clustering
val model = mec(x, k = 8, radius = 1.0)                           // Euclidean
val model = mec(x, distance = myDistance, k = 8, radius = 1.0)    // Distance
val model = mec(x, distance = myMetric,   k = 8, radius = 1.0)    // Metric

// Spectral clustering (Nyström for large N)
val model = specc(x, k = 8, sigma = 1.0, l = 0, maxIter = 100)

// Sequential Information Bottleneck (sparse text bags)
val model = sib(sparseX, k = 8, maxIter = 100, runs = 8)
```

---

## Natural Language Processing

All extension functions and helpers live in `package smile.nlp`.

### String Extension Functions

These are the heart of the Kotlin NLP shim.  They can be chained left-to-right
like a pipeline:

```kotlin
import smile.nlp.*

val text = """
    The quick brown fox jumped over the lazy dogs.
    Dr. Smith lives at 3.14 Elm St.  She's a researcher.
""".trimIndent()

// 1. Unicode normalization (NFKC, whitespace compression, quote normalization)
val clean: String = text.normalize()

// 2. Sentence splitting
val sentences: Array<String> = text.sentences()
// → ["The quick brown fox jumped over the lazy dogs.",
//    "Dr. Smith lives at 3.14 Elm St.",
//    "She's a researcher."]

// 3. Word tokenization with stop word filtering
val words: Array<String> = sentences[0].words()
// filter = "default"  (removes common English stop words + punctuation)
// filter = "none"     (keep everything)
// filter = "comprehensive", "google", "mysql"
// filter = "the,a,an" (custom comma-separated list)
val wordsRaw: Array<String> = sentences[0].words("none")

// 4. Bag-of-words (normalize → split → tokenize → stem → lowercase → count)
val bag: Map<String, Int> = text.bag()
// Returns Map with default { 0 } for missing keys
val bow = text.bag(filter = "default", stemmer = porter)  // explicit args

// 5. Binary bag (presence/absence, no counts)
val bag2: Set<String> = text.bag2()

// 6. Part-of-speech tagging
val tags: Array<PennTreebankPOS> = sentences[0].postag()

// 7. Keyword extraction
val keywords: List<NGram> = text.keywords(k = 10)
```

#### Pipeline example

```kotlin
val features: Map<String, Int> = rawDocument
    .normalize()                    // clean
    .let { it.sentences()           // split
              .flatMap { s -> s.words().toList() }
              .groupBy { it }
              .mapValues { (_, v) -> v.size }
              .withDefault { 0 } }
```

Or use the built-in shortcut:

```kotlin
val bag = rawDocument.bag()   // normalize + sentences + words + stem in one call
```

### Stemming

Two stemmer functions are available at the top level, backed by module-level
singleton instances (no object allocation per call):

```kotlin
val stemmedWord = porter("running")    // → "run"
val stemmedWord = lancaster("running") // → "run" (more aggressive)
```

### Corpus and Collocations

```kotlin
// Build an in-memory corpus from a list of documents
val corp: SimpleCorpus = corpus(documents)

// Top-k bigram collocations by log-likelihood ratio
val bigrams: List<Bigram> = bigrams(k = 50, minFreq = 5, text = documents)

// Bigrams filtered by p-value
val bigrams = bigrams(p = 0.05, minFreq = 5, text = documents)

// Apriori n-gram phrase extraction
val ngrams: Array<Array<NGram>> = ngram(maxNGramSize = 3, minFreq = 5, text = documents)
```

### POS Tagging (standalone)

```kotlin
// Tag a pre-tokenized array of words
val tags: Array<PennTreebankPOS> = postag(arrayOf("The", "fox", "ran"))
```

### Vectorization

```kotlin
// Dense count vector (terms defines feature order)
val terms = arrayOf("fox", "run", "jump")
val vector: DoubleArray = vectorize(terms, bag)

// Sparse binary vector (sorted indices of present terms)
val indices: IntArray = vectorize(terms.toList(), bag.keys)
```

### TF-IDF

```kotlin
// Single document TF-IDF vector, L2-normalized
val df: IntArray = df(terms.toList(), corpus)        // document frequencies
val tfidfVec: DoubleArray = tfidf(bagVec, n = corpus.size, df = df)

// Scalar TF-IDF weight
val weight = tfidf(tf = 3.0, maxtf = 10.0, n = 1000, df = 50)

// Corpus-level: returns L2-normalized TF-IDF matrix
val matrix: List<DoubleArray> = tfidf(corpus.map { it.toDoubleArray() })
```

---

## Dimensionality Reduction and Projection

Functions live in `package smile.feature.extraction`.

### Principal Component Analysis

```kotlin
import smile.feature.extraction.*

// PCA on covariance matrix
val pca: PCA = pca(x, cor = false)

// PCA on correlation matrix (for variables on different scales)
val pca = pca(x, cor = true)

// Project to k dimensions
val projected: Array<DoubleArray> = pca.project(x, k = 2)

// Probabilistic PCA (handles missing values, gives Bayesian interpretation)
val ppca: ProbabilisticPCA = ppca(x, k = 10)
```

### Kernel PCA

```kotlin
import smile.math.kernel.GaussianKernel

val kpca: KernelPCA = kpca(
    data      = df,
    kernel    = GaussianKernel(1.0),
    k         = 20,
    threshold = 1E-4    // eigenvalue threshold for truncation
)
```

### Generalized Hebbian Algorithm (Online PCA)

```kotlin
// With a random initial weight matrix
val model: GHA = gha(
    data = x,
    k    = 10,         // number of components
    r    = TimeFunction.linear(0.01, 10_000.0, 0.001)
)

// With an explicit initial weight matrix (shape: inputDim × k)
val w = Array(inputDim) { DoubleArray(k) }
val model: GHA = gha(x, w, r = TimeFunction.constant(0.001))
```

---

## Manifold Learning

Functions live in `package smile.manifold`.

### Isomap

```kotlin
import smile.manifold.*

val coords: Array<DoubleArray> = isomap(
    data     = x,
    k        = 10,         // number of nearest neighbors
    d        = 2,          // output dimensionality
    CIsomap  = true        // use C-Isomap (landmark points)
)
```

### Locally Linear Embedding

```kotlin
val coords: Array<DoubleArray> = lle(x, k = 12, d = 2)
```

### Laplacian Eigenmaps

```kotlin
val coords: Array<DoubleArray> = laplacian(
    data = x,
    k    = 10,
    d    = 2,
    t    = -1.0    // kernel bandwidth; -1 uses heat kernel
)
```

### t-SNE

```kotlin
val model: TSNE = tsne(
    X                = x,
    d                = 2,
    perplexity       = 30.0,
    eta              = 200.0,    // learning rate
    earlyExaggeration = 12.0,
    maxIter          = 1000
)
val coords = model.coordinates
```

### UMAP

```kotlin
// Euclidean
val coords: Array<DoubleArray> = umap(
    data            = x,
    k               = 15,       // nearest neighbors
    d               = 2,        // output dimensions
    epochs          = 0,        // 0 = auto
    learningRate    = 1.0,
    minDist         = 0.1,
    spread          = 1.0,
    negativeSamples = 5,
    repulsionStrength = 1.0,
    localConnectivity = 1.0
)

// Custom metric
val coords = umap(data = objects, distance = myMetric, k = 15, d = 2)
```

### Multidimensional Scaling

```kotlin
val model: MDS = mds(
    proximity = distanceMatrix,   // pre-computed n×n distance/proximity
    d         = 2,
    positive  = false
)

val model: IsotonicMDS = isomds(
    proximity = distanceMatrix,
    d         = 2,
    tol       = 1E-4,
    maxIter   = 200
)

val model: SammonMapping = sammon(
    proximity = distanceMatrix,
    d         = 2,
    step      = 0.2,
    maxIter   = 100
)
```

---

## Association Rule Mining

Functions live in `package smile.association`.

The workflow is:

1. Build an FP-tree from transactions.
2. Mine frequent itemsets with FP-growth.
3. Derive association rules.

```kotlin
import smile.association.*
import java.util.function.Supplier
import java.util.stream.Stream

val transactions: Array<IntArray> = arrayOf(
    intArrayOf(1, 3),
    intArrayOf(2, 3, 4),
    intArrayOf(1, 2, 3),
    // …
)

// Build FP-tree from a data array
val tree: FPTree = fptree(
    minSupport = 3,
    supplier   = Supplier { Stream.of(*transactions) }
)

// Mine frequent itemsets
val itemsets: List<ItemSet> = fpgrowth(tree).toList()

// One-shot: build tree + mine in one call
val itemsets = fpgrowth(minSupport = 3, itemsets = transactions).toList()

// Association rules (confidence ≥ 0.5)
val rules: List<AssociationRule> = arm(confidence = 0.5, tree = tree).toList()

// One-shot ARM
val rules = arm(minSupport = 3, confidence = 0.5, itemsets = transactions).toList()

// Inspect rules
for (rule in rules) {
    println("${rule.antecedent().toList()} → ${rule.consequent().toList()}")
    println("  support=${rule.support()}, confidence=${rule.confidence()}")
}
```

---

## Wavelets

Functions live in `package smile.wavelet`.

```kotlin
import smile.wavelet.*

val signal: DoubleArray = doubleArrayOf(/* … */)

// Get a named Wavelet instance
val w: Wavelet = wavelet("haar")
// Other filters: "d4","d6","d8","d10","d12","d14","d16","d18","d20",
//                "la8","la10","la12","la14","la16","la18","la20",
//                "bl14","bl18","bl20", "c6","c12","c18","c24","c30"

// Forward discrete wavelet transform (in-place)
dwt(signal, filter = "d4")

// Inverse discrete wavelet transform (in-place)
idwt(signal, filter = "d4")

// Wavelet shrinkage denoising (in-place)
wsdenoise(
    t      = signal,
    filter = "haar",
    soft   = false    // true = soft thresholding, false = hard thresholding
)
```

---

## Complete Examples

### Example 1 — End-to-end text classification

```kotlin
import smile.*
import smile.classification.*
import smile.nlp.*

// Load data
val df = read.csv("data/20news.csv", header = true)

// Build vocabulary from training text
val documents: List<String> = df.column("text").toStringList()
val allBags   = documents.map { it.bag() }
val terms     = allBags.flatMap { it.keys }.distinct().sorted()

// TF-IDF features
val rawX = allBags.map { bag -> vectorize(terms.toTypedArray(), bag) }
val dfFreq = df(terms, allBags)
val x = rawX.map { vec -> tfidf(vec, rawX.size, dfFreq) }.toTypedArray()
val y = df.intColumn("label").toIntArray()

// Train a random forest
val model = randomForest(
    formula = Formula.lhs("label"),
    data    = df,         // or supply x, y directly with other classifiers
    ntrees  = 200
)

// Classify a new document
val newBag  = "Scientists discover faster algorithm".bag()
val newVec  = tfidf(vectorize(terms.toTypedArray(), newBag), rawX.size, dfFreq)
// val label = …  (depends on model type; call model.predict(newVec) for raw-array models)
```

---

### Example 2 — Regression with GP and approximate variants

```kotlin
import smile.regression.*
import smile.math.kernel.GaussianKernel

val kernel = GaussianKernel(1.0)

// Full GP (best accuracy, O(n³))
val fullGP = gpr(x, y, kernel, noise = 1E-6, normalize = true)

// Sparse GP — choose 200 inducing inputs at random
val inducing = x.toList().shuffled().take(200).toTypedArray()
val sparseGP = gpr.approx(x, y, t = inducing, kernel = kernel, noise = 1E-6)

// Nyström approximation
val nystromGP = gpr.nystrom(x, y, t = inducing, kernel = kernel, noise = 1E-6)

// Evaluate
val predictions = x.map { xi -> fullGP.predict(xi) }
```

---

### Example 3 — Clustering pipeline with UMAP visualization

```kotlin
import smile.clustering.*
import smile.manifold.*

// 1. Cluster
val clustering = kmeans(x, k = 8, runs = 20)
println("Cluster sizes: ${clustering.size.toList()}")

// 2. Embed into 2-D for visualization
val coords = umap(x, k = 15, d = 2, minDist = 0.1)
// coords[i] = DoubleArray(2), clustering.group[i] = cluster label

// 3. Print a few points
coords.zip(clustering.group.toList()).take(5).forEach { (point, cluster) ->
    println("(${point[0]}, ${point[1]}) → cluster $cluster")
}
```

---

### Example 4 — Multiclass SVM with one-vs-one

```kotlin
import smile.classification.*
import smile.math.kernel.GaussianKernel

val model = ovo(x, y) { xi, yi ->
    // This Kotlin lambda replaces a Java BiFunction or Trainer SAM type
    svm(xi, yi, kernel = GaussianKernel(2.0), C = 50.0)
}

val label = model.predict(testSample)
```

---

### Example 5 — NLP pipeline: bigrams, keywords, and vectorization

```kotlin
import smile.nlp.*

val docs = listOf(
    "Machine learning is a subfield of artificial intelligence.",
    "Deep learning uses neural networks with many layers.",
    "Natural language processing enables computers to understand text."
)

// Keyword extraction per document
docs.forEach { doc ->
    val keywords = doc.keywords(k = 5)
    println(keywords.map { it.text })
}

// Bigram collocations across the corpus
val topBigrams = bigrams(k = 10, minFreq = 1, text = docs)
topBigrams.forEach { println(it) }

// Bag-of-words + TF-IDF for all documents
val bags  = docs.map { it.bag() }
val terms = bags.flatMap { it.keys }.distinct().sorted()
val rawVectors = bags.map { bag -> vectorize(terms.toTypedArray(), bag) }
val dfFreq = df(terms, bags)
val tfidfMatrix = tfidf(rawVectors)   // List<DoubleArray>, L2-normalized
```

---

### Example 6 — Association rule mining from a transaction database

```kotlin
import smile.association.*
import java.util.function.Supplier
import java.util.stream.Stream

val transactions = arrayOf(
    intArrayOf(0, 1, 3),
    intArrayOf(1, 2),
    intArrayOf(0, 2, 3),
    intArrayOf(1, 3),
    intArrayOf(0, 1, 2, 3),
)

val tree  = fptree(minSupport = 2, Supplier { Stream.of(*transactions) })
val rules = arm(confidence = 0.6, tree = tree).toList()

rules.forEach { rule ->
    println(
        "${rule.antecedent().toList()} → ${rule.consequent().toList()}" +
        " [support=${rule.support()}, conf=${rule.confidence()}]"
    )
}
```

---

### Example 7 — Wavelet denoising

```kotlin
import smile.wavelet.*

// Simulate a noisy signal (in a real case, read from file / sensor)
val clean  = DoubleArray(256) { i -> Math.sin(2 * Math.PI * i / 64.0) }
val noise  = DoubleArray(256) { Math.random() * 0.3 }
val signal = DoubleArray(256) { i -> clean[i] + noise[i] }

// Denoise in-place with soft-thresholding Haar wavelet
wsdenoise(signal, filter = "haar", soft = true)

// signal now contains the denoised approximation
```

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
