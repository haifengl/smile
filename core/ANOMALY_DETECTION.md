# SMILE — Anomaly Detection

The package `smile.anomaly` provides two main approaches for unsupervised and
semi-supervised anomaly detection:

- **Isolation Forest** (`IsolationForest`) — tree-ensemble, unsupervised anomaly
  scoring for numeric tabular data.
- **One-Class SVM** (`SVM<T>`) — kernel-based novelty detection that learns the
  support of a high-dimensional distribution.

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
5. [Score Conventions and Thresholding](#5-score-conventions-and-thresholding)
6. [Validation and Error Handling](#6-validation-and-error-handling)
7. [End-to-End Examples](#7-end-to-end-examples)
   - [7.1 Isolation Forest with Extended Splits](#71-isolation-forest-with-extended-splits)
   - [7.2 One-Class SVM with a Gaussian Kernel](#72-one-class-svm-with-a-gaussian-kernel)
   - [7.3 Unsupervised Threshold Selection](#73-unsupervised-threshold-selection)
8. [API Quick Reference](#8-api-quick-reference)

---

## 1) Overview

Package: `smile.anomaly`

| Class | Algorithm | Score direction |
|---|---|---|
| `IsolationForest` | Random-partition tree ensemble | Higher → more anomalous |
| `SVM<T>` | One-class support vector machine | Lower (negative) → more anomalous |

Both classes are `Serializable` and support round-trip persistence via
`smile.io.Write.object` / `smile.io.Read.object`.

---

## 2) When to Use Which Method

**Use Isolation Forest when:**
- Data is numeric tabular (`double[][]`).
- You need a fast, scalable, unsupervised baseline.
- You want intuitive scores in `(0, 1]` where `> 0.5` roughly flags anomalies.
- You want to experiment with extended hyperplanes via `extensionLevel`.

**Use One-Class SVM when:**
- You need flexible, non-linear normality boundaries via custom kernels.
- The training data is uncontaminated (no outliers) — the SVM learns a tight
  hypersphere around it.
- Data size is moderate (kernel methods scale as O(n²) in memory/time).

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

## 5) Score Conventions and Thresholding

| Model | `score()` return | Anomaly direction |
|---|---|---|
| `IsolationForest` | `(0, 1]` | Higher → anomaly |
| `SVM` | any real (`f(x) − b`) | Lower (negative) → anomaly |

### Data-driven threshold

When no labelled data is available, select a threshold from training scores
using the expected contamination rate:

```java
// IsolationForest — top `contamination` fraction flagged
double[] scores = model.score(train);
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

## 6) Validation and Error Handling

### `IsolationForest.fit`

| Condition | Exception |
|---|---|
| `data == null \|\| data.length < 2` | `IllegalArgumentException` |
| Any row is `null` | `IllegalArgumentException` |
| Rows have inconsistent length | `IllegalArgumentException` |
| `extensionLevel >= p` | `IllegalArgumentException` |
| `subsample` not in `(0, 1)` | `IllegalArgumentException` (from `Options`) |

### `IsolationForest.score`

| Condition | Exception |
|---|---|
| `x == null` | `IllegalArgumentException` |
| `x.length != p` | `IllegalArgumentException` — message: `"Invalid input dimension: expected <p>, actual <n>"` |

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

### 7.3 Unsupervised Threshold Selection

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

## 8) API Quick Reference

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
```

---

*SMILE — Copyright © 2010–2026 Haifeng Li. Licensed under the GNU GPL.*
