# SMILE — Model Validation

The package `smile.validation` provides model validation and model-selection utilities for:

- train/test validation
- cross-validation (`k`-fold, stratified, grouped/non-overlap, repeated)
- leave-one-out cross-validation (LOOCV)
- bootstrap validation
- metric aggregation and reporting

It supports both array-based workflows and `DataFrame` + `Formula` workflows.

## Table of Contents

- [1) Core types](#1-core-types)
- [2) Single train/test validation](#2-single-traintest-validation)
- [3) Cross-validation](#3-cross-validation)
- [4) Leave-one-out validation (LOOCV)](#4-leave-one-out-validation-loocv)
- [5) Bootstrap validation](#5-bootstrap-validation)
- [6) Reading validation results](#6-reading-validation-results)
- [7) Model selection criteria](#7-model-selection-criteria)
- [8) Input constraints and common pitfalls](#8-input-constraints-and-common-pitfalls)

## 1) Core types

Main split/result classes:

- `Bag`: a split with training indices (`samples()`) and validation indices (`oob()`)
- `ClassificationValidation<M>` / `RegressionValidation<M>`: one validation run
- `ClassificationValidations<M>` / `RegressionValidations<M>`: aggregated runs (e.g., CV/Bootstrap)
- `ClassificationMetrics` / `RegressionMetrics`: computed metrics for a run

Split generators:

- `CrossValidation`
- `LOOCV`
- `Bootstrap`

Model selection helpers:

- `ModelSelection.AIC(...)`
- `ModelSelection.BIC(...)`

## 2) Single train/test validation

### Classification (array-based)

```java
import smile.classification.DecisionTree;
import smile.validation.ClassificationValidation;

// trainX/trainY/testX/testY prepared elsewhere
var result = ClassificationValidation.of(trainX, trainY, testX, testY, DecisionTree::fit);

System.out.println(result.metrics());
System.out.println(result.confusion());
```

### Classification (`DataFrame` + `Formula`)

```java
import smile.classification.DecisionTree;
import smile.data.formula.Formula;
import smile.validation.ClassificationValidation;

Formula formula = Formula.lhs("class");
var result = ClassificationValidation.of(formula, trainDf, testDf, DecisionTree::fit);
```

### Regression (array-based)

```java
import smile.regression.RegressionTree;
import smile.validation.RegressionValidation;

var result = RegressionValidation.of(trainX, trainY, testX, testY, RegressionTree::fit);
System.out.println(result.metrics());
```

### Regression (`DataFrame` + `Formula`)

```java
import smile.data.formula.Formula;
import smile.regression.RegressionTree;
import smile.validation.RegressionValidation;

Formula formula = Formula.lhs("y");
var result = RegressionValidation.of(formula, trainDf, testDf, RegressionTree::fit);
```

## 3) Cross-validation

`CrossValidation` offers multiple split strategies:

- `CrossValidation.of(n, k)`: standard `k`-fold
- `CrossValidation.stratify(y, k)`: class-stratified `k`-fold
- `CrossValidation.nonoverlap(group, k)`: grouped CV (no group leakage across folds)

Validation helpers run training/scoring for each fold and aggregate:

### Classification CV

```java
import smile.classification.DecisionTree;
import smile.validation.CrossValidation;

var cv = CrossValidation.classification(5, x, y, DecisionTree::fit);
System.out.println(cv.avg());
System.out.println(cv.std());
```

### Repeated CV

```java
var repeated = CrossValidation.classification(3, 5, x, y, DecisionTree::fit); // 3 rounds of 5-fold
```

### Stratified CV

```java
var stratified = CrossValidation.stratify(5, x, y, DecisionTree::fit);
```

### Regression CV

```java
import smile.regression.RegressionTree;

var regCv = CrossValidation.regression(5, x, y, RegressionTree::fit);
```

## 4) Leave-one-out validation (LOOCV)

LOOCV uses one sample for validation each round and the rest for training.

```java
import smile.classification.DecisionTree;
import smile.validation.LOOCV;

var metrics = LOOCV.classification(x, y, DecisionTree::fit);
System.out.println(metrics);
```

Regression LOOCV:

```java
import smile.regression.RegressionTree;

var regMetrics = LOOCV.regression(x, y, RegressionTree::fit);
```

Use LOOCV when dataset size is small and you can afford higher compute cost.

## 5) Bootstrap validation

`Bootstrap` samples with replacement and evaluates on out-of-bag data.

### Classification bootstrap

```java
import smile.classification.DecisionTree;
import smile.validation.Bootstrap;

var result = Bootstrap.classification(100, x, y, DecisionTree::fit);
System.out.println(result.avg());
System.out.println(result.std());
```

### Regression bootstrap

```java
import smile.regression.RegressionTree;

var result = Bootstrap.regression(100, x, y, RegressionTree::fit);
```

Stratified bootstrap splits are available through `Bootstrap.of(int[] category, int k)`.

## 6) Reading validation results

### Classification metrics typically include

- `accuracy`, `error`
- binary metrics (when applicable): `sensitivity`, `specificity`, `precision`, `f1`, `mcc`
- probabilistic metrics (when soft classifier): `auc`, `logloss`, `crossEntropy`

### Regression metrics typically include

- `rss`, `mse`, `rmse`, `mad`, `r2`

Aggregated result types (`ClassificationValidations`, `RegressionValidations`) provide per-round results plus summary stats such as average and standard deviation.

## 7) Model selection criteria

`ModelSelection` contains information criteria helpers:

```java
import smile.validation.ModelSelection;

double aic = ModelSelection.AIC(logLikelihood, numParameters);
double bic = ModelSelection.BIC(logLikelihood, numParameters, sampleSize);
```

Lower values are preferred when comparing candidate models on the same dataset.

## 8) Input constraints and common pitfalls

Current validation APIs enforce strict argument checks in several split builders.

- `CrossValidation.of(n, k)`: requires `n > 0`, `k > 0`, and `k <= n`
- `CrossValidation.stratify(...)`: requires non-empty labels and valid `k`
- `CrossValidation.nonoverlap(...)`: requires non-empty group labels and valid `k`
- `LOOCV.of(n)`: requires `n > 0`
- `Bootstrap.of(n, k)`: requires `n > 0` and `k > 0`
- `Bootstrap.of(category, k)`: requires non-empty category labels and `k > 0`

Practical tips:

- Use stratified CV for imbalanced classification.
- Use grouped/non-overlap CV when leakage across entities (users/patients/sessions) is possible.
- Prefer repeated CV or bootstrap when one split is noisy.
- For soft classifiers, ensure posterior probabilities are valid and class-aligned.
- Report both average and variability (`std`) across folds/rounds.

---

*SMILE — Copyright © 2010-2026 Haifeng Li. GNU GPL licensed.*

