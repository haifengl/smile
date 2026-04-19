# SMILE — Validation Metrics

The package `smile.validation.metric` contains utility metrics for evaluating:

- classification
- probabilistic classification
- regression
- clustering

Most metrics are exposed as static `of(...)` methods and as metric objects implementing one of the metric interfaces.

## Table of Contents

- [1) Package overview](#1-package-overview)
- [2) Classification metrics](#2-classification-metrics)
- [3) Probabilistic classification metrics](#3-probabilistic-classification-metrics)
- [4) Regression metrics](#4-regression-metrics)
- [5) Clustering metrics](#5-clustering-metrics)
- [6) Confusion and contingency tables](#6-confusion-and-contingency-tables)
- [7) Averaging for multi-class metrics](#7-averaging-for-multi-class-metrics)
- [8) Common pitfalls and tips](#8-common-pitfalls-and-tips)

## 1) Package overview

Main interfaces:

- `ClassificationMetric`: scores from hard labels (`int[] truth`, `int[] prediction`)
- `ProbabilisticClassificationMetric`: scores from probabilities (`int[] truth`, `double[] probability`)
- `RegressionMetric`: scores from real-valued predictions (`double[] truth`, `double[] prediction`)
- `ClusteringMetric`: compares two partitions (`int[] truth`, `int[] cluster`)

Examples of concrete metric classes:

- Classification: `Accuracy`, `Precision`, `Recall`, `Sensitivity`, `Specificity`, `FScore`, `MatthewsCorrelation`, `Error`
- Probabilistic classification: `AUC`, `LogLoss`, `CrossEntropy`
- Regression: `MSE`, `RMSE`, `MAD`, `RSS`, `R2`
- Clustering: `RandIndex`, `AdjustedRandIndex`, `MutualInformation`, `NormalizedMutualInformation`, `AdjustedMutualInformation`

## 2) Classification metrics

Use hard-label metrics when your model outputs class IDs directly.

```java
import smile.validation.metric.Accuracy;
import smile.validation.metric.FScore;
import smile.validation.metric.Precision;
import smile.validation.metric.Recall;

int[] truth = {0, 1, 1, 0, 2};
int[] prediction = {0, 1, 0, 0, 2};

double acc = Accuracy.of(truth, prediction);
double precisionMacro = Precision.of(truth, prediction, smile.validation.metric.Averaging.Macro);
double recallMacro = Recall.of(truth, prediction, smile.validation.metric.Averaging.Macro);
double f1Macro = FScore.of(truth, prediction, 1.0, smile.validation.metric.Averaging.Macro);
```

For binary classification only, you can use `Sensitivity`/`Specificity` directly:

```java
import smile.validation.metric.Sensitivity;
import smile.validation.metric.Specificity;

double tpr = Sensitivity.of(truth, prediction);
double tnr = Specificity.of(truth, prediction);
```

## 3) Probabilistic classification metrics

Use these when your model returns probabilities/confidences.

### Binary case (`double[] probability` for positive class)

```java
import smile.validation.metric.AUC;
import smile.validation.metric.LogLoss;

int[] truth = {0, 1, 1, 0};
double[] prob1 = {0.10, 0.90, 0.35, 0.40};

double auc = AUC.of(truth, prob1);
double logloss = LogLoss.of(truth, prob1);
```

### Multi-class case (`double[][] probability`)

```java
import smile.validation.metric.CrossEntropy;

int[] truth = {2, 0, 1};
double[][] posterior = {
    {0.1, 0.2, 0.7},
    {0.8, 0.1, 0.1},
    {0.2, 0.6, 0.2}
};

double ce = CrossEntropy.of(truth, posterior);
```

## 4) Regression metrics

Use regression metrics for continuous targets.

```java
import smile.validation.metric.MSE;
import smile.validation.metric.RMSE;
import smile.validation.metric.MAD;
import smile.validation.metric.R2;

double[] truth = {3.2, 1.1, 2.9, 4.0};
double[] pred = {3.0, 1.4, 2.5, 4.1};

double mse = MSE.of(truth, pred);
double rmse = RMSE.of(truth, pred);
double mad = MAD.of(truth, pred);
double r2 = R2.of(truth, pred);
```

## 5) Clustering metrics

Use clustering metrics to compare two labelings of the same data.

```java
import smile.validation.metric.AdjustedRandIndex;
import smile.validation.metric.NormalizedMutualInformation;

int[] yTrue = {0, 0, 1, 1, 2, 2};
int[] yCluster = {1, 1, 0, 0, 2, 2};

double ari = AdjustedRandIndex.of(yTrue, yCluster);
double nmi = NormalizedMutualInformation.SUM.score(yTrue, yCluster);
```

`AdjustedRandIndex` is often a strong default for clustering comparison.

## 6) Confusion and contingency tables

### Confusion matrix (classification)

```java
import smile.validation.metric.ConfusionMatrix;

ConfusionMatrix cm = ConfusionMatrix.of(truth, prediction);
System.out.println(cm);
```

### Contingency table (clustering alignment)

`ContingencyTable` is used internally by several clustering metrics and can be useful when debugging cluster-label agreement.

## 7) Averaging for multi-class metrics

For precision/recall/F-score in multi-class problems, choose an averaging strategy via `Averaging`:

- `Macro`: equal weight per class
- `Micro`: aggregate counts globally (micro precision/recall often equals accuracy)
- `Weighted`: class-weighted macro average

Example:

```java
import smile.validation.metric.Averaging;
import smile.validation.metric.FScore;

double f1Weighted = FScore.of(truth, prediction, 1.0, Averaging.Weighted);
```

## 8) Common pitfalls and tips

- Input lengths must match. Most `of(...)` methods throw `IllegalArgumentException` on mismatch.
- `AUC` and `LogLoss` are binary-only in this package; labels must be `0/1`.
- `CrossEntropy` expects a proper posterior matrix where each row aligns with the truth label index.
- For imbalanced datasets, prefer `FScore` (with explicit averaging), `MatthewsCorrelation`, and probabilistic metrics over accuracy alone.
- For regression, pair scale-dependent metrics (`RMSE`, `MAD`) with scale-free context (`R2`) for better interpretation.

---

*SMILE — Copyright © 2010-2026 Haifeng Li. GNU GPL licensed.*

