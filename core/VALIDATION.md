# SMILE — Model Validation

The `smile.validation` package provides everything needed to estimate how well a model
generalizes to unseen data. It is built around three orthogonal concerns:

1. **Data splitting** — `Bag`, `Bootstrap`, `CrossValidation`, `LOOCV`
2. **Model evaluation** — `ClassificationValidation`, `RegressionValidation` and their
   aggregating counterparts `ClassificationValidations`, `RegressionValidations`
3. **Model selection** — `ModelSelection` (AIC / BIC)

All types are serializable records or static-method-only interfaces, so they carry no
mutable state and compose freely.

---

## Table of Contents

1. [Concepts](#concepts)
2. [Data Splitting](#data-splitting)
   - [Holdout (`Bag.split`)](#holdout-bagsplit)
   - [Stratified Holdout (`Bag.stratify`)](#stratified-holdout-bagstratify)
   - [Bootstrap](#bootstrap)
   - [K-Fold Cross-Validation](#k-fold-cross-validation)
   - [Stratified K-Fold](#stratified-k-fold)
   - [Group (Non-Overlapping) K-Fold](#group-non-overlapping-k-fold)
   - [Leave-One-Out CV (LOOCV)](#leave-one-out-cv-loocv)
3. [Classification Validation](#classification-validation)
   - [Single Split](#single-split)
   - [Multiple Splits](#multiple-splits)
   - [Understanding `ClassificationMetrics`](#understanding-classificationmetrics)
4. [Regression Validation](#regression-validation)
   - [Single Split](#single-split-1)
   - [Multiple Splits](#multiple-splits-1)
   - [Understanding `RegressionMetrics`](#understanding-regressionmetrics)
5. [Model Selection (AIC / BIC)](#model-selection-aic--bic)
6. [Workflows](#workflows)
   - [Quick Holdout Smoke Test](#quick-holdout-smoke-test)
   - [10-Fold CV with Aggregation](#10-fold-cv-with-aggregation)
   - [Repeated CV](#repeated-cv)
   - [Stratified Bootstrap](#stratified-bootstrap)
   - [Group K-Fold for Time-Series-Style Data](#group-k-fold-for-time-series-style-data)
   - [LOOCV for Small Datasets](#loocv-for-small-datasets)
   - [Comparing Models with AIC/BIC](#comparing-models-with-aicbic)
7. [Quick API Reference](#quick-api-reference)
8. [Common Pitfalls](#common-pitfalls)

---

## Concepts

### The `Bag` record

Every splitting strategy returns one or more `Bag` objects.

```java
public record Bag(int[] samples, int[] oob)
```

| Field | Meaning |
|---|---|
| `samples()` | Training indices into the original dataset |
| `oob()` | Held-out (out-of-bag / test) indices |

Indices are into the **original** array, not a copy — no data is ever duplicated.

### Hard vs. Soft classifiers

The validation layer distinguishes two classifier flavours:

- **Hard** (`Classifier.isSoft() == false`) — predicts a single class label. Metrics
  that require probability estimates (`AUC`, `LogLoss`, cross-entropy) are reported as
  `Double.NaN`.
- **Soft** (`Classifier.isSoft() == true`) — also provides posterior probabilities.
  All metrics are computed and reported.

---

## Data Splitting

### Holdout (`Bag.split`)

A single random train / test split. The test proportion is set with `holdout` ∈ (0, 1).

```java
// 80% train, 20% test on 1000 raw samples
Bag bag = Bag.split(1000, 0.2);
int[] trainIdx = bag.samples();
int[] testIdx  = bag.oob();
```

For `DataFrame` inputs a convenience overload returns a typed pair:

```java
var iris = new Iris();
Tuple2<DataFrame, DataFrame> split = Bag.split(iris.data(), 0.2);
DataFrame train = split._1;
DataFrame test  = split._2;
```

`n` and `holdout` are validated; `holdout` must be strictly between 0 and 1.

### Stratified Holdout (`Bag.stratify`)

Ensures the class distribution in each split mirrors the full dataset — essential
when classes are imbalanced.

```java
// Stratified 70/30 split for a DataFrame, using "species" as the class column
Tuple2<DataFrame, DataFrame> split =
        Bag.stratify(iris.data(), "species", 0.3);
```

The low-level `int[]` overload is package-private and used internally by validation
runners.

### Bootstrap

Bootstrap sampling draws `n` samples **with replacement** from `n` originals, so
roughly 63.2% of originals appear in the training set and ~36.8% appear only in the
out-of-bag test set.

```java
// 100 rounds of plain bootstrap for 500 samples
Bag[] bags = Bootstrap.of(500, 100);
```

**Stratified bootstrap** preserves class proportions in each bag:

```java
int[] labels = ...; // class label per sample
Bag[] bags = Bootstrap.of(labels, 100);
```

Bootstrap runners for classifiers and regressors train and evaluate automatically:

```java
var result = Bootstrap.classification(100, iris.formula(), iris.data(),
        DecisionTree::fit);
System.out.println("Accuracy: " + result.avg().accuracy()
        + " ± " + result.std().accuracy());
```

### K-Fold Cross-Validation

Partitions the data into `k` equal folds; each fold serves as the test set exactly
once while the remaining `k−1` folds are used for training.

```java
// 5-fold CV splits for 500 samples
Bag[] folds = CrossValidation.of(500, 5);
```

`k` must satisfy `1 ≤ k ≤ n`. The last fold absorbs any remainder when `n` is not
divisible by `k`.

### Stratified K-Fold

Guarantees that each fold preserves the original class proportions:

```java
int[] labels = ...; // one per sample
Bag[] folds = CrossValidation.stratify(labels, 5);
```

A warning is logged (SLF4J) if any class has fewer examples than `k`, which would
produce degenerate folds.

### Group (Non-Overlapping) K-Fold

Used when samples belong to groups (e.g. subject IDs, document IDs, time windows) and
leaking information across groups would inflate results. Each group appears entirely
in either the training set or the test set for any given fold.

```java
// group[i] is the group identifier for sample i
int[] group = {0, 0, 1, 1, 1, 2, 2, 3, 3, 3};
Bag[] folds = CrossValidation.nonoverlap(group, 3);
```

Groups are balanced across folds greedily by size. `k` must not exceed the number of
distinct groups.

### Leave-One-Out CV (LOOCV)

In LOOCV every sample serves as the test set exactly once, making it the most
data-efficient but computationally expensive strategy.

```java
// Raw index splits: train[i] contains all indices except i
int[][] trainSets = LOOCV.of(100);
// trainSets[i].length == 99 for every i
```

Full classification and regression training loops are also available and return the
same `ClassificationMetrics` / `RegressionMetrics` records as the other strategies.

---

## Classification Validation

### Single Split

Train on an explicit train/test pair and get back a `ClassificationValidation` record
containing the model, the truth labels, predictions, optional posteriors, the
confusion matrix, and the computed metrics:

```java
// Array-based trainer
ClassificationValidation<DecisionTree> result =
        ClassificationValidation.of(trainX, trainY, testX, testY, DecisionTree::fit);

System.out.println(result.metrics().accuracy());
System.out.println(result.confusion());
```

With a `Formula` and `DataFrame` the API is symmetric:

```java
var usps = new USPS();
ClassificationValidation<DecisionTree> result =
        ClassificationValidation.of(usps.formula(), usps.train(), usps.test(),
                                    DecisionTree::fit);
System.out.println(result);
```

### Multiple Splits

Pass a `Bag[]` to train and evaluate over many folds and receive a
`ClassificationValidations` that aggregates per-fold results:

```java
Bag[] folds = CrossValidation.of(x.length, 10);
ClassificationValidations<DecisionTree> cv =
        ClassificationValidation.of(folds, x, y, DecisionTree::fit);

ClassificationMetrics avg = cv.avg();
ClassificationMetrics std = cv.std();

System.out.printf("Accuracy: %.2f%% ± %.2f%n",
        100 * avg.accuracy(), 100 * std.accuracy());
```

The `std` metrics represent the standard deviation across folds. With a single fold,
`std` is `0.0` everywhere (instead of throwing an exception).

Bootstrap and LOOCV runners follow the same pattern:

```java
// Bootstrap
var bs = Bootstrap.classification(100, formula, data, DecisionTree::fit);
System.out.println(bs.avg().accuracy());

// Stratified CV
var scv = CrossValidation.classification(5, formula, data, DecisionTree::fit);

// Repeated CV (3 repetitions × 5 folds = 15 training runs)
var rcv = CrossValidation.classification(3, 5, formula, data, DecisionTree::fit);
```

### Understanding `ClassificationMetrics`

```java
public record ClassificationMetrics(
    double fitTime,       // ms to train
    double scoreTime,     // ms to score the test set
    int    size,          // number of test samples
    int    error,         // number of misclassified samples
    double accuracy,      // correct / total
    double sensitivity,   // TP / (TP + FN)  — binary or NaN for multiclass hard
    double specificity,   // TN / (TN + FP)  — binary or NaN
    double precision,     // TP / (TP + FP)  — binary or NaN
    double f1,            // 2·P·R / (P+R)   — binary or NaN
    double mcc,           // Matthews Correlation Coefficient — binary or NaN
    double auc,           // Area Under ROC   — soft binary or NaN
    double logloss,       // -log(p_correct)  — soft binary or NaN
    double crossEntropy   // mean cross-entropy — soft multiclass or NaN
)
```

Which fields are populated depends on the classifier and data:

| Scenario | Populated |
|---|---|
| Hard binary | accuracy, error, sensitivity, specificity, precision, F1, MCC |
| Soft binary | all of the above, plus AUC, log loss, cross-entropy |
| Hard multiclass | accuracy, error |
| Soft multiclass | accuracy, error, cross-entropy |

`Double.NaN` is used for metrics that are not meaningful in the current scenario. Always
guard display code with `!Double.isNaN(m.auc())` before printing probability-based
metrics.

---

## Regression Validation

### Single Split

```java
RegressionValidation<RegressionTree> result =
        RegressionValidation.of(abalone.formula(), abalone.train(), abalone.test(),
                                RegressionTree::fit);
System.out.println(result);
// Prints: RSS, MSE, RMSE, MAD, R²
```

### Multiple Splits

```java
Bag[] folds = CrossValidation.of(x.length, 10);
RegressionValidations<RegressionTree> cv =
        RegressionValidation.of(folds, x, y, RegressionTree::fit);

System.out.printf("RMSE: %.4f ± %.4f%n",
        cv.avg().rmse(), cv.std().rmse());
```

Bootstrap and LOOCV variants are also available:

```java
var bs = Bootstrap.regression(100, formula, data, RegressionTree::fit);
```

### Understanding `RegressionMetrics`

```java
public record RegressionMetrics(
    double fitTime,    // ms to train
    double scoreTime,  // ms to score
    int    size,       // test set size
    double rss,        // Residual Sum of Squares
    double mse,        // Mean Squared Error
    double rmse,       // Root Mean Squared Error
    double mad,        // Mean Absolute Error (MAE)
    double r2          // Coefficient of Determination
)
```

All regression metrics are always populated — there is no hard/soft distinction.

---

## Model Selection (AIC / BIC)

`ModelSelection` provides two static criteria for comparing models fit to the **same**
dataset. Both penalize model complexity to prevent overfitting:

| Criterion | Formula | Penalty |
|---|---|---|
| AIC (Akaike) | `2k − 2 log L` | `2k` |
| BIC (Bayesian) | `k log n − 2 log L` | `k log n` |

Here `L` is the maximised likelihood, `k` is the number of free parameters, and `n`
is the sample size (BIC only).

**Lower is better** for both AIC and BIC.

```java
double logL1 = -120.0;  // log-likelihood of model 1
double logL2 = -125.0;  // log-likelihood of model 2 (simpler, fewer params)

int k1 = 10, k2 = 4, n = 500;

double aic1 = ModelSelection.AIC(logL1, k1);
double aic2 = ModelSelection.AIC(logL2, k2);
System.out.println(aic1 < aic2 ? "Model 1 preferred by AIC"
                               : "Model 2 preferred by AIC");

double bic1 = ModelSelection.BIC(logL1, k1, n);
double bic2 = ModelSelection.BIC(logL2, k2, n);
System.out.println(bic1 < bic2 ? "Model 1 preferred by BIC"
                               : "Model 2 preferred by BIC");
```

**When to use which:**

- **AIC** favours predictive accuracy; it is more suitable when the goal is to select
  a model that predicts well, even if it is slightly over-parameterised.
- **BIC** is consistent — it selects the true model as `n → ∞` if the true model is
  among the candidates. It is more conservative and tends to prefer smaller models.
  The `log n` factor means that BIC penalizes complexity more than AIC whenever
  `n > e² ≈ 7.4`.

---

## Workflows

### Quick Holdout Smoke Test

Use a holdout split when you want the fastest possible sanity check before committing
to a full CV run:

```java
var iris = new Iris();
Tuple2<DataFrame, DataFrame> split = Bag.split(iris.data(), 0.2);

var result = ClassificationValidation.of(
        iris.formula(), split._1, split._2, DecisionTree::fit);
System.out.println(result);
```

### 10-Fold CV with Aggregation

The idiomatic workflow for a thorough, low-variance estimate:

```java
var iris = new Iris();
var cv = CrossValidation.classification(10, iris.formula(), iris.data(),
        DecisionTree::fit);

System.out.printf("Accuracy: %.2f%% ± %.2f%n",
        100 * cv.avg().accuracy(),
        100 * cv.std().accuracy());
```

The `std` field lets you report confidence intervals around each metric.

### Repeated CV

Repeated CV runs standard k-fold multiple times with different random permutations,
giving a more stable estimate at the cost of `round × k` training runs:

```java
// 5 repetitions of 5-fold CV = 25 training runs
var rcv = CrossValidation.classification(5, 5, iris.formula(), iris.data(),
        DecisionTree::fit);
System.out.printf("Accuracy: %.2f%% ± %.2f%n",
        100 * rcv.avg().accuracy(),
        100 * rcv.std().accuracy());
```

### Stratified Bootstrap

Bootstrap is often preferred for small datasets because the test set size varies per
round (unlike fixed-fold CV). The stratified variant is recommended whenever classes
are imbalanced:

```java
int[] y = formula.y(data).toIntArray();
var bs = Bootstrap.classification(100, formula, data, DecisionTree::fit);

System.out.printf("Accuracy: %.2f%% ± %.2f%n",
        100 * bs.avg().accuracy(),
        100 * bs.std().accuracy());
```

### Group K-Fold for Time-Series-Style Data

When samples are grouped (e.g. multiple measurements per patient, or overlapping time
windows), standard CV leaks information between folds. Use group k-fold:

```java
// subjectId[i] == the subject/group to which sample i belongs
int[] subjectId = ...;
Bag[] folds = CrossValidation.nonoverlap(subjectId, 5);

var cv = ClassificationValidation.of(folds, x, y, SVM::fit);
System.out.println(cv.avg());
```

### LOOCV for Small Datasets

LOOCV is unbiased and uses almost all data for training in each round, making it
the right choice when data is scarce:

```java
// Array-based
var metrics = LOOCV.classification(x, y, LogisticRegression::fit);
System.out.printf("Accuracy: %.2f%%%n", 100 * metrics.accuracy());

// Formula / DataFrame-based
var metrics2 = LOOCV.classification(formula, data, DecisionTree::fit);
```

Prefer `CrossValidation.stratify` for datasets larger than ~200 samples, since the
compute cost of LOOCV is `O(n)` training runs.

### Comparing Models with AIC/BIC

Fit both models to the same training set, extract their log-likelihoods, and compare:

```java
GaussianMixture m1 = GaussianMixture.fit(x, 2);  // 2 components
GaussianMixture m2 = GaussianMixture.fit(x, 5);  // 5 components

double aic1 = ModelSelection.AIC(m1.logLikelihood(), m1.numParameters());
double aic2 = ModelSelection.AIC(m2.logLikelihood(), m2.numParameters());

System.out.println("Preferred by AIC: " + (aic1 < aic2 ? "2-component" : "5-component"));
```

---

## Quick API Reference

### Data Splitting

| Method | Description |
|---|---|
| `Bag.split(n, holdout)` | Random holdout split on `n` raw indices |
| `Bag.split(data, holdout)` | Random holdout split returning two `DataFrame`s |
| `Bag.stratify(data, column, holdout)` | Stratified holdout split on a `DataFrame` |
| `Bootstrap.of(n, k)` | `k` bootstrap bags from `n` samples |
| `Bootstrap.of(category, k)` | `k` stratified bootstrap bags |
| `CrossValidation.of(n, k)` | Standard k-fold splits |
| `CrossValidation.stratify(labels, k)` | Stratified k-fold splits |
| `CrossValidation.nonoverlap(group, k)` | Group k-fold splits |
| `LOOCV.of(n)` | Leave-one-out training index arrays |

### Running Validation

| Method | Returns |
|---|---|
| `ClassificationValidation.of(formula, train, test, trainer)` | `ClassificationValidation<M>` |
| `ClassificationValidation.of(bags, x, y, trainer)` | `ClassificationValidations<M>` |
| `CrossValidation.classification(k, formula, data, trainer)` | `ClassificationValidations<M>` |
| `CrossValidation.classification(round, k, formula, data, trainer)` | `ClassificationValidations<M>` (repeated) |
| `Bootstrap.classification(k, formula, data, trainer)` | `ClassificationValidations<M>` |
| `LOOCV.classification(x, y, trainer)` | `ClassificationMetrics` |
| `RegressionValidation.of(formula, train, test, trainer)` | `RegressionValidation<M>` |
| `RegressionValidation.of(bags, x, y, trainer)` | `RegressionValidations<M>` |
| `CrossValidation.regression(k, formula, data, trainer)` | `RegressionValidations<M>` |
| `Bootstrap.regression(k, formula, data, trainer)` | `RegressionValidations<M>` |
| `LOOCV.regression(x, y, trainer)` | `RegressionMetrics` |

### Model Selection

| Method | Formula |
|---|---|
| `ModelSelection.AIC(logL, k)` | `2k − 2 logL` |
| `ModelSelection.BIC(logL, k, n)` | `k log n − 2 logL` |

---

## Common Pitfalls

### 1. Comparing cross-validated metrics across different sample sizes

`ClassificationMetrics.size` records the test-set size for each round. When comparing
models trained on different datasets, normalize by sample count rather than comparing
raw error counts.

### 2. Using `std` from a single fold

`ClassificationValidations.of` and `RegressionValidations.of` require a list of at
least one `ClassificationValidation` / `RegressionValidation`. With exactly one round,
`std` is `0.0` for every field — meaningful aggregation requires two or more rounds.

### 3. Ignoring `Double.NaN` in hard-classifier metrics

Probability-based metrics (`auc`, `logloss`, `crossEntropy`) are `Double.NaN` for
hard classifiers. Passing them to arithmetic expressions silently propagates `NaN`:

```java
// Unsafe: if auc is NaN this prints NaN
System.out.printf("AUC: %.4f%n", metrics.auc());

// Safe
if (!Double.isNaN(metrics.auc())) {
    System.out.printf("AUC: %.4f%n", metrics.auc());
}
```

### 4. Data leakage with group k-fold

Use `CrossValidation.nonoverlap` whenever samples within a group share information
(repeated measurements, sliding windows, augmented copies). Using standard k-fold
in these cases inflates accuracy estimates because the same underlying signal appears
in both train and test.

### 5. Repeated CV vs. more folds

Increasing `k` beyond 10 rarely improves variance; instead, use repeated CV
(`CrossValidation.classification(round, k, ...)`) to get a more stable estimate at
the same cost as `round × k` training runs.

### 6. LOOCV on large datasets

LOOCV fits the model `n` times. For `n = 10 000` with a non-trivial model this is
prohibitively slow. Prefer stratified 10-fold CV or bootstrap for datasets larger
than ~200–500 samples.

### 7. BIC requires `n > 0`

`ModelSelection.BIC` calls `Math.log(n)`. Passing `n ≤ 0` produces `NaN` or
`-Infinity` silently. Always ensure `n` is a positive integer matching the training
sample count.

---

*SMILE — Copyright © 2010-2026 Haifeng Li. GNU GPL licensed.*

