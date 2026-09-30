# SMILE — Anomaly Detection

The package `smile.anomaly` provides three main approaches for unsupervised and
semi-supervised anomaly detection:

- **Isolation Forest** (`IsolationForest`) — tree-ensemble, unsupervised anomaly
  scoring for numeric tabular data.
- **One-Class SVM** (`SVM<T>`) — kernel-based novelty detection that learns the
  support of a high-dimensional distribution.
- **Local Outlier Factor** (`LOF<T>`) — density-based local outlier detection
  comparing an observation's local density to its k-nearest neighbors.

---

## Table of Contents

1. [Overview](#1-overview)
2. [When to Use Which Method](#2-when-to-use-which-method)
3. [Isolation Forest](#3-isolation-forest)
   - [3.1 Quick Start](#31-quick-start)
   - [3.2 Hyperparameters](#32-hyperparameters)
   - [3.3 Extension Level Semantics](#33-extension-level-semantics)
   - [3.4 Batch Scoring and Prediction](#34-batch-scoring-and-prediction)
   - [3.5 Persistence](#35-persistence)
4. [One-Class SVM](#4-one-class-svm)
   - [4.1 Quick Start](#41-quick-start)
   - [4.2 Hyperparameters](#42-hyperparameters)
   - [4.3 Batch Scoring and Prediction](#43-batch-scoring-and-prediction)
   - [4.4 Persistence](#44-persistence)
5. [Local Outlier Factor (LOF)](#5-local-outlier-factor-lof)
   - [5.1 Quick Start](#51-quick-start)
   - [5.2 Hyperparameters](#52-hyperparameters)
   - [5.3 Metric Spaces & Custom Neighborhood Search](#53-metric-spaces--custom-neighborhood-search)
   - [5.4 Batch Scoring and Prediction](#54-batch-scoring-and-prediction)
   - [5.5 Persistence](#55-persistence)
6. [Score Conventions and Thresholding](#6-score-conventions-and-thresholding)
7. [Validation and Error Handling](#7-validation-and-error-handling)
8. [End-to-End Examples](#8-end-to-end-examples)
   - [8.1 Isolation Forest with Extended Splits](#81-isolation-forest-with-extended-splits)
   - [8.2 One-Class SVM with a Gaussian Kernel](#82-one-class-svm-with-a-gaussian-kernel)
   - [8.3 Local Outlier Factor on Multi-Density Clusters](#83-local-outlier-factor-on-multi-density-clusters)
   - [8.4 Unsupervised Threshold Selection](#84-unsupervised-threshold-selection)
9. [API Quick Reference](#9-api-quick-reference)

---

## 1) Overview

Package: `smile.anomaly`

| Class | Algorithm | Score direction |
|---|---|---|
| `IsolationForest` | Random-partition tree ensemble | Higher → more anomalous |
| `SVM<T>` | One-class support vector machine | Lower (negative) → more anomalous |
| `LOF<T>` | Local reachability density ratio | Higher (>> 1.0) → more anomalous |

All three classes are `Serializable` and support round-trip persistence via
`smile.io.Write.object` / `smile.io.Read.object`.

---

## 2) When to Use Which Method

**Use Isolation Forest when:**
- Data is numeric tabular (`double[][]`).
- You need a fast, scalable, unsupervised baseline for large datasets.
- You want intuitive scores in `(0, 1]` where `> 0.5` roughly flags anomalies.
- You want to experiment with extended hyperplanes via `extensionLevel`.

**Use One-Class SVM when:**
- You need flexible, non-linear normality boundaries via custom kernels.
- The training data is uncontaminated (no outliers) — the SVM learns a tight
  hypersphere around it.
- Data size is moderate (kernel methods scale as O(n²) in memory/time).

**Use Local Outlier Factor (LOF) when:**
- Data contains clusters of **varying densities** (where global density or distance
  methods fail because normal points in a sparse cluster have lower density than
  outliers near a dense cluster).
- You want a non-parametric, local density-relative score where ~1.0 denotes normal
  inliers and values significantly above 1.0 (e.g., > 1.5) indicate local outliers.
- You are working in metric spaces or moderate-sized feature sets where spatial
  indexing (`KDTree`, `CoverTree`) delivers efficient neighbor queries.

---

## 3) Isolation Forest

Class: `smile.anomaly.IsolationForest`

### 3.1 Quick Start

```java
import smile.anomaly.IsolationForest;

double[][] train = {
    {0.0, 0.0}, {0.1, -0.1}, {-0.05, 0.05},
    {0.05, 0.08}, {-0.08, -0.03}
};

// Fit with default options (100 trees, extensionLevel=0)
IsolationForest model = IsolationForest.fit(train);

// Score individual points — higher means more anomalous
double inlierScore  = model.score(new double[] { 0.02,  0.01});
double outlierScore = model.score(new double[] { 6.0,  -6.0});

System.out.printf("inlier  score = %.4f%n", inlierScore);   // e.g. 0.38
System.out.printf("outlier score = %.4f%n", outlierScore);  // e.g. 0.82
```

### 3.2 Hyperparameters

All hyperparameters are captured in `IsolationForest.Options`:

```java
var options = new IsolationForest.Options(
    200,   // ntrees       – number of isolation trees
    0,     // maxDepth     – 0 = auto (log₂ of subsample size)
    0.7,   // subsample    – fraction of rows per tree (0 < subsample < 1)
    0      // extensionLevel – 0 = standard Isolation Forest
);
IsolationForest model = IsolationForest.fit(train, options);
```

`Options` supports lossless roundtrip via `java.util.Properties`:

```java
Properties props  = options.toProperties();
var        loaded = IsolationForest.Options.of(props);
assert options.equals(loaded);
```

Key property keys:

| Property key | Default |
|---|---|
| `smile.isolation_forest.trees` | `100` |
| `smile.isolation_forest.max_depth` | `0` |
| `smile.isolation_forest.sampling_rate` | `0.7` |
| `smile.isolation_forest.extension_level` | `0` |

### 3.3 Extension Level Semantics

The `extensionLevel` controls how many feature dimensions participate in the
random splitting hyperplane:

| `extensionLevel` | Behaviour |
|---|---|
| `0` | Standard Isolation Forest — axis-aligned splits |
| `1 … p-2` | Intermediate — hyperplanes in a `(extensionLevel+1)`-dimensional subspace |
| `p-1` | Fully extended — hyperplanes with random slopes in all dimensions |

Rules:
- Valid range: `[0, p-1]`, where `p` is input dimensionality.
- Setting `extensionLevel >= p` raises `IllegalArgumentException`.
- The current level can be read back with `model.extensionLevel()`.

```java
// Standard Isolation Forest (extensionLevel = 0)
var std = IsolationForest.fit(data, new IsolationForest.Options(100, 0, 0.7, 0));
System.out.println(std.extensionLevel()); // 0

// Extended Isolation Forest
var ext = IsolationForest.fit(data, new IsolationForest.Options(100, 0, 0.7, 1));
System.out.println(ext.extensionLevel()); // 1
```

### 3.4 Batch Scoring and Prediction

```java
// Batch score — runs in parallel
double[] scores = model.score(batchData);

// One-step predict: true → anomaly
boolean isAnomaly = model.predict(x, 0.6); // threshold in (0.5, 1.0)
```

`predict(double[] x, double threshold)` returns `true` when
`score(x) > threshold`. Typical thresholds are in `(0.5, 0.8)` depending on
the expected contamination rate.

### 3.5 Persistence

```java
import smile.io.Read;
import smile.io.Write;
import java.nio.file.Path;

Path path = Write.object(model);
IsolationForest loaded = (IsolationForest) Read.object(path);
```

---

## 4) One-Class SVM

Class: `smile.anomaly.SVM<T>` (extends `smile.model.svm.KernelMachine<T>`)

### 4.1 Quick Start

```java
import smile.anomaly.SVM;
import smile.math.kernel.GaussianKernel;

double[][] train = {
    {0.0, 0.0}, {0.1, -0.1}, {-0.05, 0.05},
    {0.05, 0.08}, {-0.08, -0.03}
};

SVM<double[]> model = SVM.fit(
    train,
    new GaussianKernel(1.0),
    new SVM.Options(0.2, 1E-3)
);

// Positive → inlier, Negative → anomaly
double inlierScore  = model.score(new double[] { 0.02,  0.01});
double outlierScore = model.score(new double[] { 4.0,  -4.0});

System.out.printf("inlier  score = %.4f%n", inlierScore);   // e.g.  0.45
System.out.printf("outlier score = %.4f%n", outlierScore);  // e.g. -0.72
```

> **Score convention:** Unlike `IsolationForest`, `SVM.score()` returns the raw
> decision function value: **positive = inlier**, **negative = anomaly**.

### 4.2 Hyperparameters

`SVM.Options(double nu, double tol)`:

| Parameter | Meaning | Default |
|---|---|---|
| `nu` | Upper bound on outlier fraction; lower bound on support-vector fraction. Range: `(0, 1]`. | `0.5` |
| `tol` | Solver convergence tolerance (`> 0`). | `1E-3` |

```java
var opts = new SVM.Options(0.1, 1E-4); // 10% contamination budget

Properties props   = opts.toProperties();
SVM.Options loaded = SVM.Options.of(props);
assert opts.equals(loaded);
```

Property keys:

| Property key | Default |
|---|---|
| `smile.svm.nu` | `0.5` |
| `smile.svm.tolerance` | `1E-3` |

**Kernel selection:** Any `smile.math.kernel.MercerKernel<T>` is accepted:
- `GaussianKernel(sigma)` — most common choice; controls locality of boundary.
- `PolynomialKernel(degree, scale, offset)` — for polynomial boundaries.
- `LinearKernel()` — rarely used for one-class SVM.

### 4.3 Batch Scoring and Prediction

```java
// Batch score — runs in parallel
double[] scores = model.score(batchSamples);

// One-step predict: true → anomaly
// threshold = 0.0 uses the natural SVM decision boundary
boolean isAnomaly = model.predict(x, 0.0);
```

`predict(T x, double threshold)` returns `true` when `score(x) < threshold`.
Use `0.0` as the natural decision boundary; lower (negative) thresholds tolerate
borderline cases.

### 4.4 Persistence

```java
Path path = Write.object(model);
@SuppressWarnings("unchecked")
SVM<double[]> loaded = (SVM<double[]>) Read.object(path);
```

---

## 5) Local Outlier Factor (LOF)

Class: `smile.anomaly.LOF<T>`

### 5.1 Quick Start

```java
import smile.anomaly.LOF;

double[][] train = {
    {0.0, 0.0}, {0.1, -0.1}, {-0.05, 0.05},
    {0.05, 0.08}, {-0.08, -0.03}, {0.02, 0.01},
    {10.0, 10.0}, {10.5, 9.8}, {9.7, 10.2}, {10.1, 10.3}
};

// Fit LOF with neighborhood size k = 4
LOF<double[]> lof = LOF.fit(train, 4);

// In-sample training scores
double[] inSampleScores = lof.scores();

// Score new queries — ~1.0 means inlier, > 1.5 indicates local anomaly
double inlierScore  = lof.score(new double[]{0.02, 0.01});  // ~1.0
double outlierScore = lof.score(new double[]{0.80, 0.80});  // > 2.0 (near dense cluster)

System.out.printf("inlier  LOF: %.4f%n", inlierScore);
System.out.printf("outlier LOF: %.4f%n", outlierScore);

// Predict with threshold
boolean isAnomaly = lof.predict(new double[]{0.80, 0.80}, 1.5);
```

### 5.2 Hyperparameters

Hyperparameters are encapsulated in `LOF.Options`:

| Parameter | Type | Default | Description |
|---|---|---|---|
| `k` | `int` | `20` | Number of nearest neighbors defining the local neighborhood ($MinPts$). Must be $\ge 1$ and $< n$. |

Rule of thumb for $k$:
- Lower bound: $k > 10$ to remove undesirable statistical fluctuations.
- Upper bound: $k < \text{minimum cluster size}$ so points inside a small cluster are not flagged as outliers. Typical default is $20$.

### 5.3 Metric Spaces & Custom Neighborhood Search

`LOF` supports arbitrary data types $T$:

```java
// With custom metric (e.g. Manhattan, Mahalanobis) using CoverTree:
LOF<double[]> lofMetric = LOF.fit(data, new ManhattanDistance(), 15);

// With custom nearest neighbor search:
LOF<double[]> lofCustom = LOF.fit(data, nns, 15);
```

### 5.4 Batch Scoring and Prediction

```java
double[][] testPoints = ...;
double[] batchScores = lof.score(testPoints);
```

### 5.5 Persistence

```java
Path path = Write.object(lof);
@SuppressWarnings("unchecked")
LOF<double[]> loaded = (LOF<double[]>) Read.object(path);
```

---

## 6) Score Conventions and Thresholding

| Model | `score()` return | Anomaly direction |
|---|---|---|
| `IsolationForest` | `(0, 1]` | Higher → anomaly |
| `SVM` | any real (`f(x) − b`) | Lower (negative) → anomaly |
| `LOF` | real $\ge 0$ | Higher ($\gg 1.0$) → anomaly |

### Data-driven threshold

When no labelled data is available, select a threshold from training scores
using the expected contamination rate:

```java
// IsolationForest or LOF — top `contamination` fraction flagged
double[] scores = model.score(train); // or lof.scores()
Arrays.sort(scores);
double contamination = 0.05;                                // 5% outliers
int    idx           = (int)((1.0 - contamination) * (scores.length - 1));
double threshold     = scores[idx];

// Flag new point
boolean flag = model.predict(xNew, threshold);
```

For `SVM`, sort in ascending order and take the `contamination`-th percentile
from the bottom (most-negative end), then use `score(x) < threshold`.

---

## 7) Validation and Error Handling

### `IsolationForest.fit`

| Condition | Exception |
|---|---|
| `data == null \|\| data.length < 2` | `IllegalArgumentException` |
| Any row is `null` | `IllegalArgumentException` |
| Rows have inconsistent length | `IllegalArgumentException` |
| `extensionLevel >= p` | `IllegalArgumentException` |
| `subsample` not in `(0, 1)` | `IllegalArgumentException` (from `Options`) |

### `LOF.fit`

| Condition | Exception |
|---|---|
| `data == null \|\| data.length == 0` | `IllegalArgumentException` |
| `k < 1` | `IllegalArgumentException` |
| `k >= data.length` | `IllegalArgumentException` |

### Scoring & Prediction

| Condition | Exception |
|---|---|
| Query vector is `null` | `IllegalArgumentException` |
| Vector dimension mismatch | `IllegalArgumentException` |

### `SVM.fit`

| Condition | Exception |
|---|---|
| `x == null \|\| x.length == 0` | `IllegalArgumentException` |
| `kernel == null` | `IllegalArgumentException` |
| `options == null` | `IllegalArgumentException` |
| `nu` not in `(0, 1]` | `IllegalArgumentException` (from `Options`) |
| `tol <= 0` | `IllegalArgumentException` (from `Options`) |

---

## 7) End-to-End Examples

### 7.1 Isolation Forest with Extended Splits

```java
import smile.anomaly.IsolationForest;
import smile.math.MathEx;
import java.util.Arrays;

MathEx.setSeed(42L);

double[][] train = loadNumericData();   // your 3-dimensional data

// Extended Isolation Forest (p = 3 → extensionLevel up to 2)
var options = new IsolationForest.Options(256, 0, 0.6, 2);
IsolationForest forest = IsolationForest.fit(train, options);

System.out.println("trees          : " + forest.size());
System.out.println("extension level: " + forest.extensionLevel());

// Batch score and count anomalies above threshold 0.6
double[] scores = forest.score(train);
long anomalyCount = Arrays.stream(scores)
    .filter(s -> s > 0.6)
    .count();
System.out.printf("anomalies (threshold 0.6): %d / %d%n", anomalyCount, train.length);
```

### 7.2 One-Class SVM with a Gaussian Kernel

```java
import smile.anomaly.SVM;
import smile.math.kernel.GaussianKernel;

double[][] train = loadCleanData();  // no outliers in training set

SVM<double[]> ocsvm = SVM.fit(
    train,
    new GaussianKernel(0.5),
    new SVM.Options(0.1, 1E-3)
);

double[] testPoint = {1.2, -0.7, 0.3};
boolean anomaly = ocsvm.predict(testPoint, 0.0);
System.out.printf("anomaly: %b  (score = %.4f)%n", anomaly, ocsvm.score(testPoint));
```

### 8.3 Local Outlier Factor on Multi-Density Clusters

```java
import smile.anomaly.LOF;

double[][] data = loadMultiDensityData();

// Fit LOF with neighborhood size k = 15
LOF<double[]> lof = LOF.fit(data, 15);

// Check in-sample scores
double[] lofScores = lof.scores();

// Predict outliers with score threshold > 1.5
for (int i = 0; i < data.length; i++) {
    if (lofScores[i] > 1.5) {
        System.out.printf("Local outlier at index %d: LOF = %.4f%n", i, lofScores[i]);
    }
}
```

### 8.4 Unsupervised Threshold Selection

```java
import smile.anomaly.IsolationForest;
import java.util.Arrays;

double[][] data = loadData();
IsolationForest model = IsolationForest.fit(data);

double[] trainScores = model.score(data);
Arrays.sort(trainScores);

double contamination = 0.02;                                          // 2% expected anomalies
int    cutIdx        = (int)((1.0 - contamination) * (trainScores.length - 1));
double threshold     = trainScores[cutIdx];

// Classify new batch
double[][] newBatch = loadNewData();
for (double[] x : newBatch) {
    if (model.predict(x, threshold)) {
        System.out.println("ANOMALY detected: " + Arrays.toString(x));
    }
}
```

---

## 9) API Quick Reference

```java
// ── IsolationForest ──────────────────────────────────────────────────────────

// Training
IsolationForest model = IsolationForest.fit(double[][] data);
IsolationForest model = IsolationForest.fit(double[][] data, IsolationForest.Options options);

// Options
new IsolationForest.Options()                                    // defaults
new IsolationForest.Options(int ntrees, int maxDepth,
                            double subsample, int extensionLevel)
IsolationForest.Options.of(Properties props)
options.toProperties()

// Inspection
int             model.size()                                     // number of trees
IsolationTree[] model.trees()                                    // defensive copy
int             model.extensionLevel()                           // 0 = standard IF

// Scoring
double   model.score(double[] x)                                 // single sample
double[] model.score(double[][] x)                               // batch (parallel)
boolean  model.predict(double[] x, double threshold)             // true = anomaly


// ── SVM (One-Class) ──────────────────────────────────────────────────────────

// Training
SVM<T> model = SVM.fit(T[] x, MercerKernel<T> kernel);
SVM<T> model = SVM.fit(T[] x, MercerKernel<T> kernel, SVM.Options options);

// Options
new SVM.Options()                                                // nu=0.5, tol=1E-3
new SVM.Options(double nu, double tol)
SVM.Options.of(Properties props)
options.toProperties()

// Scoring  (positive = inlier, negative = anomaly)
double   model.score(T x)                                        // single sample
double[] model.score(T[] x)                                      // batch (parallel)
boolean  model.predict(T x, double threshold)                    // true = anomaly
                                                                 // (score < threshold)


// ── LOF (Local Outlier Factor) ───────────────────────────────────────────────

// Training
LOF<double[]> model = LOF.fit(double[][] data);                 // k=20, KDTree
LOF<double[]> model = LOF.fit(double[][] data, int k);          // custom k, KDTree
LOF<T>        model = LOF.fit(T[] data, Distance<T> dist, int k);// metric / linear
LOF<T>        model = LOF.fit(T[] data, KNNSearch<T, T> nns, int k);

// Options
new LOF.Options()                                                // k=20
new LOF.Options(int k)
LOF.Options.of(Properties props)
options.toProperties()

// Inspection & Scoring (higher >> 1.0 = anomaly)
int      model.k()                                               // neighborhood size
double[] model.scores()                                          // in-sample scores
double   model.score(T x)                                        // single sample
double[] model.score(T[] x)                                      // batch (parallel)
boolean  model.predict(T x, double threshold)                    // true = anomaly
                                                                 // (score > threshold)
```

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
