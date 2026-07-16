# SMILE — Feature Engineering

The `smile.feature.*` packages provide a complete toolkit for
preparing raw data for machine learning: scaling, encoding, dimensionality
reduction, missing-value imputation, feature selection, and model explainability.

---

## Table of Contents

1. [Overview](#overview)
2. [Feature Transformation (`smile.feature.transform`)](#feature-transformation)
   - [Scaler](#scaler)
   - [WinsorScaler](#winsorscaler)
   - [MaxAbsScaler](#maxabsscaler)
   - [Standardizer](#standardizer)
   - [RobustStandardizer](#robuststandarizer)
   - [Normalizer](#normalizer)
   - [Composing Transforms into a Pipeline](#composing-transforms-into-a-pipeline)
3. [Feature Extraction (`smile.feature.extraction`)](#feature-extraction)
   - [PCA](#pca--principal-component-analysis)
   - [ProbabilisticPCA](#probabilisticpca)
   - [KernelPCA](#kernelpca)
   - [GHA – Generalized Hebbian Algorithm](#gha--generalized-hebbian-algorithm)
   - [RandomProjection](#randomprojection)
   - [BagOfWords](#bagofwords)
   - [BinaryEncoder](#binaryencoder)
   - [SparseEncoder](#sparseencoder)
   - [HashEncoder](#hashencoder)
4. [Missing Value Imputation (`smile.feature.imputation`)](#missing-value-imputation)
   - [SimpleImputer](#simpleimputer)
   - [KNNImputer](#knnimputer)
   - [KMedoidsImputer](#kmedoidsimputer)
   - [SVDImputer](#svdimputer)
5. [Feature Selection (`smile.feature.selection`)](#feature-selection)
   - [SumSquaresRatio](#sumsquaresratio)
   - [SignalNoiseRatio](#signalnoiseratio)
   - [FRegression](#fregression)
   - [InformationValue](#informationvalue)
   - [GAFE – Genetic Algorithm Feature Selection](#gafe--genetic-algorithm-feature-selection)
6. [Feature Importance (`smile.feature.importance`)](#feature-importance)
   - [SHAP](#shap)
   - [TreeSHAP](#treeshap)
7. [Choosing the Right Technique](#choosing-the-right-technique)

---

## Overview

| Subpackage | Purpose |
|---|---|
| `smile.feature.transform` | Scale, standardize, or row-normalize numeric columns |
| `smile.feature.extraction` | Reduce dimensionality or convert raw data (text, categoricals) to numeric vectors |
| `smile.feature.imputation` | Fill missing values before training |
| `smile.feature.selection` | Rank or search for the most informative features |
| `smile.feature.importance` | Explain how much each feature contributes to model predictions (SHAP) |

All column-wise transformers return an `InvertibleColumnTransform` (which
extends `Transform`) and can be chained into a pipeline with
`Transform.pipeline(...)`.

---

## Feature Transformation

The `smile.feature.transform` package contains six transformers. Five are
column-wise (fit statistics over training data; transform individual columns
independently) and one is row-wise (stateless; normalizes each row vector).

### Scaler

**Min–max scaling** maps each column to **[0, 1]** using the training-set
minimum and maximum.

```
scaled = (x − min) / (max − min)
```

Values outside the training range are **clamped** to [0, 1] at inference time,
so `invert()` is *lossy* for out-of-range inputs.

```java
// Fit on training data; transform test data
InvertibleColumnTransform scaler = Scaler.fit(trainDf);
DataFrame scaledTest = scaler.apply(testDf);

// Only scale specific columns
InvertibleColumnTransform partial = Scaler.fit(trainDf, "age", "income");

// Roundtrip (exact within training range)
DataFrame restored = scaler.invert(scaledTest);
```

**When to use:** when the algorithm requires bounded inputs (e.g., neural
networks, k-NN) and your data contains no significant outliers.

---

### WinsorScaler

**Outlier-robust min–max scaling**. Quantile bounds (default: 5th–95th
percentile) replace the absolute min/max, so outliers do not compress the
normal data into a tiny interval. After Winsorization, values are scaled to
[0, 1] and clamped.

```java
// Default: 5th–95th percentile
InvertibleColumnTransform t = WinsorScaler.fit(trainDf);

// Custom percentile bounds; transform only selected columns
InvertibleColumnTransform t2 = WinsorScaler.fit(trainDf, 0.01, 0.99, "salary");

// Column-subset overload (default percentiles)
InvertibleColumnTransform t3 = WinsorScaler.fit(trainDf, "salary", "age");
```

> **Note:** Percentile quantiles are computed via `IQAgent` (an approximate
> streaming quantile algorithm). On very small datasets (< 20 rows) the result
> may deviate slightly from exact sort-based quantiles.

**When to use:** same as `Scaler` but when your dataset contains outliers that
would otherwise crush the range of regular data.

---

### MaxAbsScaler

**Divide by the maximum absolute value** — maps each column to **[−1, 1]**
without any centering. This preserves sparsity (zero entries remain zero).

```
scaled = x / max(|x|)
```

All-zero columns fall back to scale = 1.0 so values stay 0.

```java
InvertibleColumnTransform t = MaxAbsScaler.fit(trainDf);
DataFrame scaled = t.apply(testDf);
DataFrame restored = t.invert(scaled);
```

**When to use:** sparse feature matrices (e.g., TF-IDF vectors), or any
setting where centering is undesirable (e.g., SVMs with a sparse kernel).

---

### Standardizer

**Z-score standardization** — subtracts the column mean and divides by the
sample standard deviation (N−1 denominator):

```
scaled = (x − μ) / σ
```

For constant columns (σ = 0), the scale falls back to 1.0 so the output is
simply `x − μ` (all zeros for training data). A single-row frame is treated
the same way.

```java
InvertibleColumnTransform t = Standardizer.fit(trainDf);
DataFrame standardized = t.apply(testDf);

// Single-column
InvertibleColumnTransform t2 = Standardizer.fit(trainDf, "temperature");
```

**When to use:** distance-based algorithms (k-NN, SVM, k-Means), linear
models, and neural networks when features follow approximately Gaussian
distributions. Not robust to outliers — prefer `RobustStandardizer` when
outliers are present.

---

### RobustStandardizer

**Median and IQR standardization** — subtracts the column median and divides
by the inter-quartile range (IQR = Q75 − Q25):

```
scaled = (x − median) / IQR
```

For zero-IQR columns the scale falls back to 1.0 (only centering applied).
Quantiles are approximate (via `IQAgent`); for very small datasets consider
sorting-based exact quantiles.

```java
InvertibleColumnTransform t = RobustStandardizer.fit(trainDf);
DataFrame robust = t.apply(testDf);
```

**When to use:** same use cases as `Standardizer` but when the data contains
outliers that would inflate the standard deviation and skew the z-scores.

---

### Normalizer

**Row-wise normalization** — rescales each row independently so its selected
columns have unit norm. This is a *stateless* transform (no fitting required).

Three norm types are available:

| Enum | Formula |
|---|---|
| `Norm.L1` | `x_i / Σ|x_j|` |
| `Norm.L2` | `x_i / sqrt(Σx_j²)` |
| `Norm.L_INF` | `x_i / max(|x_j|)` |

Rows with an all-zero selected subvector are passed through unchanged
(the scale falls back to 1.0).

```java
// Normalize every column with L2 norm
Normalizer l2 = new Normalizer(Normalizer.Norm.L2, df.names());
DataFrame normalized = l2.apply(df);

// Normalize only specific numeric columns, leave others untouched
Normalizer partial = new Normalizer(Normalizer.Norm.L1, "feat1", "feat2");
Tuple normalizedRow = partial.apply(someTuple);
```

**When to use:** text classification (TF vectors), cosine-similarity models,
or any model where the direction of a feature vector matters more than its
magnitude.

---

### Composing Transforms into a Pipeline

All column-wise transforms implement `InvertibleColumnTransform` (and therefore
`Transform`). You can chain multiple transforms with `Transform.pipeline(...)`:

```java
// Standardize, then scale to max-abs = 1
Transform pipeline = Transform.pipeline(
        Standardizer.fit(trainDf),
        MaxAbsScaler.fit(Standardizer.fit(trainDf).apply(trainDf))
);
DataFrame result = pipeline.apply(testDf);
```

Or use `Transform.fit(...)` to apply a sequence of fit-and-transform steps in
a single expression when each stage needs the output of the previous stage.

---

## Feature Extraction

The `smile.feature.extraction` package provides dimensionality reduction and
vectorization utilities.

### PCA – Principal Component Analysis

PCA is an orthogonal linear transformation that projects data onto the
directions of maximum variance (the principal components).

```java
// Fit using covariance matrix (default)
PCA pca = PCA.fit(trainDf);               // auto-selects top PCs ≥ 95% variance
PCA pca = PCA.fit(trainDf, "f1","f2",...); // subset of columns

// Fit using correlation matrix (useful when features have different scales)
PCA pcaCor = PCA.cor(trainDf);

// Inspect
Vector varProp = pca.varianceProportion();
Vector cumProp = pca.cumulativeVarianceProportion();

// Choose a projection
PCA pca5  = pca.getProjection(5);     // keep top 5 PCs
PCA pca90 = pca.getProjection(0.90);  // keep enough PCs for 90% variance

// Apply to data
double[] projected = pca5.apply(row);
DataFrame projectedDf = pca5.apply(df);
```

For m >> n (more samples than features), the implementation uses SVD; for
n > m, it uses explicit covariance matrix EVD to save memory.

**When to use:** high-dimensional data with correlated features (gene
expression, image pixels). Note: PCA is sensitive to outliers and assumes
linear structure.

---

### ProbabilisticPCA

A probabilistic generative model for PCA. It uses a latent variable model
`y ~ W·x + μ + ε` where noise `ε ~ N(0, σ²I)` (isotropic). Estimated by
maximum likelihood; useful when you need probabilistic interpretations or
want to handle noise explicitly.

```java
ProbabilisticPCA ppca = ProbabilisticPCA.fit(trainDf, k); // k latent dims
double noiseVariance = ppca.variance();
DataFrame projected   = ppca.apply(trainDf);
```

**When to use:** when a probabilistic model of the data distribution is needed,
or as an alternative to EM-based factor analysis.

---

### KernelPCA

Applies a non-linear kernel mapping before PCA, allowing extraction of
non-linear structure.

```java
import smile.math.kernel.GaussianKernel;
import smile.manifold.KPCA;

MercerKernel<double[]> kernel = new GaussianKernel(1.0);
KPCA.Options opts = new KPCA.Options(20); // keep 20 components
KernelPCA kpca = KernelPCA.fit(trainDf, kernel, opts);
DataFrame projected = kpca.apply(testDf);
```

**When to use:** non-linearly separable data. Closely related to Isomap, LLE,
and Laplacian eigenmaps for manifold learning.

---

### GHA – Generalized Hebbian Algorithm

An online / incremental neural-network algorithm for computing the top *k*
principal components without forming the full covariance matrix. It is
suitable for streaming data or very large datasets where batch PCA is
infeasible.

```java
// p = 10 output components, n = 256 input dimensions
TimeFunction lr = TimeFunction.of(0.01); // constant learning rate
GHA gha = new GHA(256, 10, lr);

// Stream samples (must be pre-centered, E[x] = 0)
for (double[] x : centeredSamples) {
    double error = gha.update(x); // returns squared reconstruction error
}

// Apply to new data
double[] features = gha.apply(newSample);
DataFrame features = gha.apply(df);
```

**When to use:** large-scale or streaming settings where batch PCA is too
expensive. Requires pre-centered data and careful learning-rate tuning.

---

### RandomProjection

Compresses high-dimensional data to a lower-dimensional space using a random
projection matrix. The Johnson–Lindenstrauss lemma guarantees approximate
pairwise distance preservation. No training data is needed.

```java
// Dense Gaussian random projection: n=1000 → p=50
RandomProjection rp = RandomProjection.of(1000, 50);

// Sparse random projection (faster; each entry is {-√3, 0, +√3})
RandomProjection rps = RandomProjection.sparse(1000, 50);

double[] projected = rp.apply(highDimVector);
DataFrame projectedDf = rp.apply(df, "f0", "f1", ...);
```

**When to use:** very high-dimensional data (e.g., bag-of-words), preprocessing
before k-NN or k-Means clustering, or any setting where approximate distance
preservation at drastically reduced cost is acceptable.

---

### BagOfWords

Converts a text column into a dense integer count (or binary presence) vector
over a fixed vocabulary.

```java
// Build vocabulary from corpus
String[] vocabulary = ...;
Function<String, String[]> tokenizer = text ->
        text.toLowerCase().split("\\s+");

BagOfWords bow = new BagOfWords(tokenizer, vocabulary);
// or binary (presence/absence, not count):
BagOfWords bowBinary = new BagOfWords(tokenizer, vocabulary, true);

// Apply to a single text
Tuple result = bow.apply(tuple);  // adds count columns

// Apply to a data frame
DataFrame features = bow.apply(textDf);
```

**When to use:** text classification and clustering when the order of words
is not important (Naive Bayes, Logistic Regression over sparse features).

---

### BinaryEncoder

Converts categorical columns to sparse one-hot binary arrays (`int[]`), used
by the Maximum Entropy Classifier and other models expecting sparse feature
indices.

```java
BinaryEncoder enc = new BinaryEncoder(schema);     // all categorical columns
BinaryEncoder enc = new BinaryEncoder(schema, "color", "size");

int[] binaryFeatures = enc.apply(tuple);
```

---

### SparseEncoder

Encodes both numeric and categorical columns into a `SparseArray` (indices +
values), with one-hot encoding for categorical variables and direct values for
numerics.

```java
SparseEncoder enc = new SparseEncoder(schema);
SparseArray sparse = enc.apply(tuple);
```

**When to use:** models that accept sparse input arrays (e.g., linear models
on mixed numeric/categorical data), or when memory efficiency matters.

---

### HashEncoder

Feature hashing ("hashing trick") — maps tokenized text or feature strings
directly to hash-based indices, avoiding the need to build an explicit
vocabulary dictionary. The output is a `SparseArray`.

```java
Function<String, String[]> tokenizer = text -> text.toLowerCase().split("\\s+");
HashEncoder enc = new HashEncoder(tokenizer, 1 << 18); // 2^18 feature buckets

// With alternating sign to reduce inner-product bias from collisions
HashEncoder encSigned = new HashEncoder(tokenizer, 1 << 18, true);

SparseArray features = enc.apply(documentText);
```

**When to use:** very large or open-ended vocabularies, online learning with
continuously arriving new terms, or when memory for a vocabulary dictionary
is unavailable.

---

## Missing Value Imputation

The `smile.feature.imputation` package provides four strategies for replacing
`NaN` / `null` values. All imputers implement `Transform` and can be used with
`transform.apply(df)`.

### SimpleImputer

Replaces each missing value in a column with a fixed constant. Factory
methods compute the constant from training data.

```java
// Mean imputation for numeric columns; mode for categorical
SimpleImputer imputer = SimpleImputer.fit(trainDf);

// Median imputation
SimpleImputer median = SimpleImputer.median(trainDf);

// Mode imputation (most frequent value)
SimpleImputer mode = SimpleImputer.mode(trainDf);

// Custom constant per column
SimpleImputer custom = new SimpleImputer(Map.of("age", 30.0, "city", "Unknown"));

DataFrame clean = imputer.apply(dfWithMissing);
```

Check for missing values first:

```java
boolean hasMissing = SimpleImputer.hasMissing(tuple);
```

**When to use:** quick baseline imputation; when missingness is completely at
random (MCAR) and you want a computationally cheap strategy.

---

### KNNImputer

Imputes each missing value with the (distance-weighted) average of the k
nearest complete neighbors.

```java
// Use Euclidean distance on Tuples
Distance<Tuple> dist = new EuclideanDistance();
KNNImputer imputer = new KNNImputer(trainDf, 5, dist);

DataFrame clean = imputer.apply(dfWithMissing);
```

**When to use:** when missingness has structure related to nearby points;
better accuracy than `SimpleImputer` at a higher computational cost. Works
well for continuous features.

---

### KMedoidsImputer

Imputes each missing row with the values of its nearest cluster medoid. Fit a
`KMedoids` clustering first, then wrap it.

```java
Distance<Tuple> dist = new EuclideanDistance();
CentroidClustering<Tuple, Tuple> kmed = KMedoids.fit(trainDf, 10, dist);
KMedoidsImputer imputer = new KMedoidsImputer(kmed);

DataFrame clean = imputer.apply(dfWithMissing);
```

**When to use:** mixed-type data (categorical + numeric) where a proper
distance can be defined between Tuples; useful when data has natural cluster
structure.

---

### SVDImputer

Iterative EM-style imputation using the top *k* singular vectors of the data
matrix. Works on purely numeric `double[][]` data.

```java
// k=10 eigenvectors, up to 100 EM iterations
double[][] imputed = SVDImputer.impute(dataWithNaN, 10, 100);
```

Algorithm: initialize missing values with column means, compute SVD of the
complete matrix, regress each row against the top *k* right singular vectors
(excluding the missing column), reconstruct the missing value, and repeat
until convergence.

**When to use:** low-rank or highly correlated numeric matrices (e.g., gene
expression, collaborative filtering). More accurate but significantly more
expensive than `SimpleImputer`.

---

## Feature Selection

The `smile.feature.selection` package provides univariate and evolutionary
methods to rank or select the most informative features before model training.

### SumSquaresRatio

Univariate filter for **multi-class classification**. For each feature *j*,
computes the ratio of between-group sum-of-squares to within-group
sum-of-squares (BSS/WSS). Higher values indicate better class separability.

```java
SumSquaresRatio[] scores = SumSquaresRatio.fit(df, "classLabel");

// Sort ascending (lowest discriminative power first)
Arrays.sort(scores);

// Drop the bottom 20% of features
String[] toDrop = Arrays.stream(scores)
        .limit(scores.length / 5)
        .map(SumSquaresRatio::feature)
        .toArray(String[]::new);
DataFrame reduced = df.drop(toDrop);
```

| Return type | Access |
|---|---|
| `feature()` | Column name |
| `ratio()` | BSS/WSS ratio (higher is better) |

**Edge cases:** zero BSS+WSS → ratio = 0; zero WSS with positive BSS →
ratio = `Double.MAX_VALUE`.

---

### SignalNoiseRatio

Univariate filter for **binary classification**. Computes
`|μ₁ − μ₂| / (σ₁ + σ₂)` for each feature. Larger values indicate stronger
class separation.

```java
SignalNoiseRatio[] scores = SignalNoiseRatio.fit(df, "label");
Arrays.sort(scores);

// Keep top 100 features
String[] top100 = Arrays.stream(scores)
        .sorted(Comparator.reverseOrder())
        .limit(100)
        .map(SignalNoiseRatio::feature)
        .toArray(String[]::new);
```

**When to use:** gene-expression studies (Golub's method) and other binary
classification scenarios.

---

### FRegression

Univariate F-statistic filter for **regression problems**. Computes the
Pearson correlation–based F-statistic between each feature and the continuous
response variable.

```java
FRegression[] scores = FRegression.fit(df, "price");

// Sort ascending (lowest F-stat first — least relevant)
Arrays.sort(scores);

// Use features with p-value < 0.05
String[] significant = Arrays.stream(scores)
        .filter(r -> r.pvalue() < 0.05)
        .map(FRegression::feature)
        .toArray(String[]::new);
```

Both numeric and categorical features are handled:
- **Numeric:** Pearson correlation F-test
- **Categorical:** one-way ANOVA F-test

---

### InformationValue

**Binary classification** feature scoring using Information Value (IV) and
Weight of Evidence (WoE). IV measures the overall predictive power of a
feature; WoE captures the predictive direction within each bin/category.

| IV Range | Predictive Power |
|---|---|
| < 0.02 | Useless |
| 0.02 – 0.1 | Weak |
| 0.1 – 0.3 | Medium |
| 0.3 – 0.5 | Strong |
| > 0.5 | Suspicious (possible data leakage) |

```java
InformationValue[] ivs = InformationValue.fit(df, "default", 10); // 10 bins
Arrays.sort(ivs, Comparator.reverseOrder()); // highest IV first

// The fit also returns a ColumnTransform that applies WoE encoding
ColumnTransform woeTransform = ivs[0].encoder(); // access WoE encoder per feature
```

**When to use:** credit scoring, fraud detection, and other binary outcome
models where interpretable WoE-encoded features are needed.

---

### GAFE – Genetic Algorithm Feature Selection

**Wrapper method** that uses a genetic algorithm to search for the subset of
features with the best cross-validated model performance.

```java
// Classification with KNN fitness
BiFunction<DataFrame, Formula, Double> fitness = (data, formula) -> {
    // train a classifier on the subset, return CV accuracy
    ...
};

GAFE gafe = new GAFE(Selection.Tournament(3, 0.95), 2,
                     Crossover.SinglePoint, 0.9, 0.01);
int[] selectedIndices = gafe.apply(100 /*generations*/, 20 /*population*/,
                                   formula, df, fitness);
```

**When to use:** small-to-medium dimensional data where filter methods are
insufficient and a thorough wrapper search is computationally feasible.
Significantly slower than univariate methods but can find synergistic feature
subsets.

---

## Feature Importance

The `smile.feature.importance` package contains the **SHAP** (SHapley
Additive exPlanations) framework for explaining model predictions.

### SHAP

`SHAP<T>` is a generic interface implemented by any model that supports
Shapley-value attribution. SHAP values answer: *"How much did feature j
contribute to this specific prediction, compared to the average prediction?"*

```java
// Any model implementing SHAP<double[]>:
double[] shapValues = model.shap(inputVector);

// Aggregate over many samples to get global feature importance
double[] importance = Stream.of(testData)
        .map(model::shap)
        .reduce(new double[p], (acc, s) -> { ... });
```

The interface also provides `shap(Stream<T>)` for batch processing.

---

### TreeSHAP

Exact, fast SHAP implementation for tree ensembles (Random Forest, Gradient
Boosted Trees, etc.). `TreeSHAP` is an interface implemented by all SMILE
tree-ensemble classifiers and regressors.

```java
RandomForest rf = RandomForest.fit(formula, trainDf);

// SHAP for a single prediction
double[] phi = rf.shap(testTuple);
int p = testDf.ncol() - 1; // number of features

// Per-class SHAP for classification: phi.length = p * k
// For regression: phi.length = p

// Average magnitude over the test set (global importance proxy)
double[] importance = rf.shap(testDf.stream())
        .reduce(new double[phi.length],
                (acc, s) -> { for (int i=0; i<acc.length; i++) acc[i] += Math.abs(s[i]); return acc; },
                (a, b) -> { for (int i=0; i<a.length; i++) a[i] += b[i]; return a; });
```

TreeSHAP is implemented by `RandomForest`, `GradientTreeBoost`,
`AdaBoost`, and `DecisionTree`.

---

## Choosing the Right Technique

### Scaling / Normalization

| Situation | Recommended Transformer |
|---|---|
| No outliers, bounded features needed | `Scaler` |
| Outliers present, bounded features needed | `WinsorScaler` |
| Sparse features, no centering | `MaxAbsScaler` |
| Gaussian-like features, distance-based model | `Standardizer` |
| Outliers present, distance-based model | `RobustStandardizer` |
| Row-vector direction matters (text, cosine sim) | `Normalizer` |

### Dimensionality Reduction

| Situation | Recommended Method |
|---|---|
| Linear structure, moderate size | `PCA` (cov) or `PCA.cor()` |
| Probabilistic / generative model needed | `ProbabilisticPCA` |
| Non-linear structure | `KernelPCA` |
| Streaming / very large data | `GHA` |
| Fast approximate projection | `RandomProjection` |

### Imputation

| Situation | Recommended Imputer |
|---|---|
| Fast baseline, MCAR assumption | `SimpleImputer` (mean/median/mode) |
| Continuous features with local structure | `KNNImputer` |
| Mixed-type data with cluster structure | `KMedoidsImputer` |
| Low-rank numeric matrix | `SVDImputer` |

### Feature Selection

| Situation | Recommended Method |
|---|---|
| Multi-class classification, numeric | `SumSquaresRatio` |
| Binary classification, numeric | `SignalNoiseRatio` |
| Regression, numeric/categorical | `FRegression` |
| Binary classification, interpretable WoE | `InformationValue` |
| Wrapper search (any model) | `GAFE` |

### Feature Importance

| Situation | Recommended Method |
|---|---|
| Any model (local explanation) | `SHAP` interface |
| Tree ensemble (exact, fast) | `TreeSHAP` |


---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
