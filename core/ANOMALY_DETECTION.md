# SMILE — Anomaly Detection

The module `smile.anomaly` currently provides two main approaches for anomaly detection:

- **Isolation Forest** (`IsolationForest`): tree-based, unsupervised anomaly scoring.
- **One-Class SVM** (`SVM<T>`): kernel-based novelty detection.

---

## Table of Contents

1. [Overview](#1-overview)
2. [When to Use Which Method](#2-when-to-use-which-method)
3. [Isolation Forest](#3-isolation-forest)
4. [One-Class SVM](#4-one-class-svm)
5. [Score Interpretation and Thresholding](#5-score-interpretation-and-thresholding)
6. [Validation and Error Handling](#6-validation-and-error-handling)
7. [End-to-End Examples](#7-end-to-end-examples)
8. [API Quick Reference](#8-api-quick-reference)

---

## 1) Overview

Package: `smile.anomaly`

Main classes:

- `IsolationForest`
- `IsolationTree`
- `SVM<T>`

Conceptually:

- **Isolation-based detection** isolates points via random partitioning; points
  that isolate quickly are likely anomalies.
- **Boundary-based detection** (one-class SVM) learns a region of normality;
  points outside receive lower decision values.

---

## 2) When to Use Which Method

- Use **Isolation Forest** when:
  - you need a scalable unsupervised baseline,
  - data is tabular numeric (`double[][]`),
  - you want simple anomaly scores in `(0, 1]`.

- Use **One-Class SVM** when:
  - you need flexible non-linear normal boundaries,
  - you can choose a good kernel/hyperparameters,
  - data size is moderate (kernel methods can be expensive at scale).

---

## 3) Isolation Forest

Class: `smile.anomaly.IsolationForest`

### 3.1 Quick start

```java
import smile.anomaly.IsolationForest;

double[][] x = {
    {0.0, 0.0}, {0.1, -0.1}, {-0.1, 0.05}, {0.05, 0.08},
    {5.0, 5.0} // potential outlier
};

IsolationForest model = IsolationForest.fit(x);

double s1 = model.score(new double[] {0.03, 0.01});
double s2 = model.score(new double[] {6.0, 6.0});

System.out.printf("inlier score  = %.4f%n", s1);
System.out.printf("outlier score = %.4f%n", s2);
```

### 3.2 Hyperparameters

Use `IsolationForest.Options`:

```java
var options = new IsolationForest.Options(
    200,   // ntrees
    0,     // maxDepth (0 = auto)
    0.7,   // subsample rate
    0      // extensionLevel
);
IsolationForest model = IsolationForest.fit(x, options);
```

Fields:

- `ntrees`: number of trees.
- `maxDepth`: tree depth cap (`0` -> auto as `log2(subsample_size)`).
- `subsample`: fraction of training rows used per tree (`0 < subsample < 1`).
- `extensionLevel`: hyperplane extension level.

### 3.3 Extension level semantics (important)

`extensionLevel` strictly follows docs:

- `0` = **standard Isolation Forest**.
- `1..p-1` = increasingly extended splitting hyperplanes.
- Invalid if `extensionLevel >= p`, where `p` is input dimension.

You can read it back with:

```java
int level = model.getExtensionLevel();
```

### 3.4 Other useful methods

```java
int nTrees = model.size();
IsolationTree[] trees = model.trees(); // defensive copy of internal array

double[] scores = model.score(xBatch);
```

---

## 4) One-Class SVM

Class: `smile.anomaly.SVM<T>` (extends `KernelMachine<T>`)

### 4.1 Quick start

```java
import smile.anomaly.SVM;
import smile.math.kernel.GaussianKernel;

double[][] x = {
    {0.0, 0.0}, {0.1, -0.1}, {-0.1, 0.05}, {0.05, 0.08}
};

SVM<double[]> model = SVM.fit(
    x,
    new GaussianKernel(1.0),
    new SVM.Options(0.2, 1E-3)
);

double inlier = model.score(new double[] {0.03, 0.01});
double outlier = model.score(new double[] {4.0, 4.0});

System.out.printf("inlier score  = %.4f%n", inlier);
System.out.printf("outlier score = %.4f%n", outlier);
```

### 4.2 Hyperparameters

`SVM.Options(nu, tol)`:

- `nu` in `(0, 1]`: upper bound on outlier fraction and lower bound on support-vector fraction.
- `tol > 0`: solver convergence tolerance.

`Options` also supports property roundtrip:

```java
var opts = new SVM.Options(0.3, 1E-4);
var props = opts.toProperties();
var restored = SVM.Options.of(props);
```

---

## 5) Score Interpretation and Thresholding

### IsolationForest score

- Score is approximately in `(0, 1]`.
- **Higher score => more anomalous**.

### One-Class SVM score

- Score is the decision function value (`KernelMachine.score`).
- **Lower score => more anomalous** (commonly negative outside learned support).

### Practical thresholding (unsupervised)

A common pattern: choose a contamination rate and set percentile threshold.

```java
double[] scores = model.score(xTrain);
java.util.Arrays.sort(scores);

double contamination = 0.05; // top 5% anomalies
int idx = (int) Math.floor((1.0 - contamination) * (scores.length - 1));
double threshold = scores[idx];

boolean isAnomaly = model.score(xNew) >= threshold; // IF convention
```

For one-class SVM, invert the direction (`<= threshold`).

---

## 6) Validation and Error Handling

### IsolationForest

- `fit` requires at least 2 samples.
- All rows must be non-null and have equal dimensionality.
- `extensionLevel` must be in `[0, p-1]`.
- `score(x)` validates non-null and exact dimension.

Dimension mismatch message format:

```text
Invalid input dimension: expected <p>, actual <n>
```

### SVM

- `fit` rejects null/empty training data.
- `kernel` and `options` must be non-null.
- `Options` validates `nu` and `tol`.

---

## 7) End-to-End Examples

### 7.1 IsolationForest with custom options

```java
import smile.anomaly.IsolationForest;

double[][] train = loadTrainingData();

var options = new IsolationForest.Options(256, 0, 0.6, 0);
IsolationForest forest = IsolationForest.fit(train, options);

double[] trainScores = forest.score(train);

// Fit-time metadata
System.out.println("trees: " + forest.size());
System.out.println("extension level: " + forest.getExtensionLevel());

// Score new sample
double score = forest.score(new double[] {1.2, -0.7, 0.3});
System.out.println("anomaly score: " + score);
```

### 7.2 One-Class SVM with Gaussian kernel

```java
import smile.anomaly.SVM;
import smile.math.kernel.GaussianKernel;

double[][] train = loadTrainingData();

SVM<double[]> ocsvm = SVM.fit(
    train,
    new GaussianKernel(0.5),
    new SVM.Options(0.1, 1E-3)
);

double score = ocsvm.score(new double[] {1.2, -0.7, 0.3});
System.out.println("decision score: " + score);
```

---

## 8) API Quick Reference

```java
// IsolationForest
IsolationForest.fit(double[][] data)
IsolationForest.fit(double[][] data, IsolationForest.Options options)

new IsolationForest.Options(int ntrees, int maxDepth, double subsample, int extensionLevel)

int size()
IsolationTree[] trees()
int getExtensionLevel()

double score(double[] x)
double[] score(double[][] x)

// SVM (one-class)
SVM.fit(T[] x, MercerKernel<T> kernel)
SVM.fit(T[] x, MercerKernel<T> kernel, SVM.Options options)

new SVM.Options(double nu, double tol)

double score(T x)
```

---

*SMILE — Copyright © 2010-2026 Haifeng Li. GNU GPL licensed.*

