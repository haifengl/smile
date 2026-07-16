# SMILE — Validation Metrics

The `smile.validation.metric` package provides scalar evaluation metrics for
**classification**, **probabilistic classification**, **regression**, and
**clustering** tasks. Every metric is a stateless, serializable object that
implements one of four functional interfaces. The `static of(...)` factory
methods let you compute a score in one line without instantiating a class.

---

## 1. Package Overview

### Functional interfaces

| Interface | Method signature | Who implements it |
|---|---|---|
| `ClassificationMetric` | `double score(int[] truth, int[] prediction)` | `Accuracy`, `Error`, `Precision`, `Recall`, `FScore`, `FDR`, `Fallout`, `Specificity`, `Sensitivity`, `MatthewsCorrelation` |
| `ProbabilisticClassificationMetric` | `double score(int[] truth, double[] probability)` | `AUC`, `LogLoss` |
| `RegressionMetric` | `double score(double[] truth, double[] prediction)` | `MSE`, `RMSE`, `RSS`, `MAD`, `R2` |
| `ClusteringMetric` | `double score(int[] truth, int[] cluster)` | `RandIndex`, `AdjustedRandIndex`, `MutualInformation`, `NormalizedMutualInformation`, `AdjustedMutualInformation` |

All four interfaces extend `java.util.function.ToDoubleBiFunction` so they can
be used directly as lambdas or method references wherever that type is expected.

### Common conventions

- **Label arrays are zero-indexed integers.** Binary metrics expect labels in
  `{0, 1}` (0 = negative, 1 = positive). Multi-class metrics accept any
  non-negative integer labels; `max(label) + 1` is used as the number of
  classes.
- **Arrays must have equal length.** All `of(truth, prediction)` methods throw
  `IllegalArgumentException` if sizes differ.
- **Higher is better** for most metrics; exceptions are `Error`, `MSE`, `RMSE`,
  `RSS`, `MAD`, `LogLoss`, and `CrossEntropy` where lower is better.
- **Singleton instances** (`Accuracy.instance`, `AUC.instance`, etc.) are
  provided for convenience when you need a reusable object reference.

---

## 2. Classification Metrics

Classification metrics compare an integer label array `truth` against a
predicted integer label array `prediction`.

### 2.1 Accuracy

```java
double acc = Accuracy.of(truth, prediction);
// or via instance
Accuracy accuracy = new Accuracy();
double acc = accuracy.score(truth, prediction);
```

**Formula:** `acc = (number of correct predictions) / n`

Accuracy is symmetric and works for any number of classes. It is the complement
of the error rate: `accuracy + errorRate == 1.0`.

```java
int[] truth      = {1, 0, 1, 0, 1, 0};
int[] prediction = {1, 0, 0, 0, 1, 1};
double acc = Accuracy.of(truth, prediction);   // (4 correct) / 6 ≈ 0.667
```

**Caveat:** Accuracy is misleading on imbalanced datasets. A classifier that
always predicts the majority class can achieve 99 % accuracy on a 99:1 dataset
while being completely useless for the minority class.

---

### 2.2 Error

```java
int errors = Error.of(truth, prediction);
```

Returns the **raw count** of mismatches (not a rate). Cast to `double` when
used via `ClassificationMetric.score()`.

```java
int n      = truth.length;
int errors = Error.of(truth, prediction);
double errorRate = (double) errors / n;
double accuracy  = Accuracy.of(truth, prediction);
// errorRate + accuracy == 1.0
```

---

### 2.3 Precision, Recall, F-score

These three metrics work in both **binary** and **multi-class** modes.

#### Binary mode

```java
// Both arrays must contain only 0 and 1.
double p = Precision.of(truth, prediction);
double r = Recall.of(truth, prediction);
double f1 = FScore.of(truth, prediction, 1.0, null);  // F₁ = harmonic mean of P and R
```

| Metric | Formula | Numerator | Denominator |
|---|---|---|---|
| Precision | TP / (TP + FP) | True positives | All predicted positives |
| Recall | TP / (TP + FN) | True positives | All actual positives |
| F₁ | 2PR / (P + R) | — | — |

When there are **no predicted positives** (Precision) or **no actual positives**
(Recall), the result is `NaN`. Handle this defensively:

```java
double p = Precision.of(truth, prediction);
if (Double.isNaN(p)) {
    // model made no positive predictions
}
```

#### Multi-class mode — `Averaging` strategy

Pass one of three `Averaging` enum values as the third argument:

| Strategy | Description |
|---|---|
| `Averaging.Macro` | Compute per-class metric, take unweighted mean. Treats all classes equally. |
| `Averaging.Micro` | Pool all TP/FP/FN globally. Equivalent to accuracy for Micro-Precision/Recall. |
| `Averaging.Weighted` | Compute per-class metric, weight by class support in `truth`. |

```java
import smile.validation.metric.Averaging;

double macroPrecision   = Precision.of(truth, pred, Averaging.Macro);
double microPrecision   = Precision.of(truth, pred, Averaging.Micro);
double weightedPrecision = Precision.of(truth, pred, Averaging.Weighted);

double macroRecall   = Recall.of(truth, pred, Averaging.Macro);
double macroF1       = FScore.of(truth, pred, 1.0, Averaging.Macro);
```

#### Generalized Fβ score

The `beta` parameter controls the trade-off between precision and recall:

```
Fβ = (1 + β²) · (P · R) / (β²·P + R)
```

- **β < 1**: weights precision more heavily (e.g., spam detection where false
  positives matter).
- **β = 1**: F₁, the harmonic mean of P and R (most common choice).
- **β > 1**: weights recall more heavily (e.g., disease screening where missing
  a case is costly).

```java
double f2 = FScore.of(truth, prediction, 2.0, null);  // binary, recall-weighted
FScore f05Instance = new FScore(0.5, Averaging.Macro); // reusable instance
double score = f05Instance.score(truth, prediction);
```

`beta` must be strictly positive; passing `0` or a negative value throws
`IllegalArgumentException("Non-positive beta: ...")`.

---

### 2.4 False Discovery Rate (FDR)

```java
double fdr = FDR.of(truth, prediction);
```

**Formula:** `FDR = FP / (TP + FP) = 1 − Precision`

Only applicable to binary labels `{0, 1}`. Returns `NaN` if no positive
predictions are made.

---

### 2.5 Fallout (False Positive Rate)

```java
double fpr = Fallout.of(truth, prediction);
```

**Formula:** `FPR = FP / (FP + TN) = FP / (number of true negatives)`

The *negatives* in this metric are samples where `truth[i] != 1` (i.e., any
non-positive label counts as negative, not just `truth == 0`).

Returns `NaN` if there are no negative samples in `truth`.

---

### 2.6 Specificity (True Negative Rate)

```java
double tnr = Specificity.of(truth, prediction);
```

**Formula:** `TNR = TN / (TN + FP) = TN / (number of samples where truth == 0)`

Specificity counts only samples where `truth[i] == 0` as negatives (stricter
than `Fallout`). Returns `NaN` if no negative samples exist.

`Specificity = 1 − Fallout` only when all non-positive labels are exactly `0`.

---

### 2.7 Sensitivity (True Positive Rate / Recall)

```java
double tpr = Sensitivity.of(truth, prediction);
```

**Formula:** `TPR = TP / (TP + FN)`

Binary only; identical to binary `Recall.of(truth, prediction)`. Returns `NaN`
if there are no positive samples.

---

### 2.8 Matthews Correlation Coefficient (MCC)

```java
double mcc = MatthewsCorrelation.of(truth, prediction);
```

**Formula:**

```
MCC = (TP·TN − FP·FN) / sqrt((TP+FP)(TP+FN)(TN+FP)(TN+FN))
```

MCC is widely considered the most informative single metric for binary
classification because it accounts for all four cells of the confusion matrix
and is robust to class imbalance.

- **MCC = +1**: perfect prediction.
- **MCC = 0**: no better than random.
- **MCC = −1**: total disagreement (inverted classifier).

The input labels must reduce to a **2×2** confusion matrix (exactly two
distinct classes). Returns `NaN` when the denominator is zero (e.g., all
predictions or all truths are the same class).

```java
int[] truth      = {1, 0, 1, 0, 1, 0, 1, 0};
int[] prediction = {1, 0, 1, 0, 0, 1, 1, 0};
double mcc = MatthewsCorrelation.of(truth, prediction);  // ≈ 0.5
```

---

### 2.9 Confusion Matrix

A `ConfusionMatrix` is not a scalar metric; it is a 2-D summary from which any
per-class breakdown can be derived.

```java
ConfusionMatrix cm = ConfusionMatrix.of(truth, prediction);
int[][] matrix = cm.matrix();
// matrix[t][p] = count of samples with true label t, predicted as p
System.out.println(cm);  // formatted table
```

The matrix dimension is `(max_label + 1) × (max_label + 1)` based on the
union of values in `truth` and `prediction`.

---

## 3. Probabilistic Classification Metrics

Probabilistic metrics require a **continuous score** (probability) in addition
to the integer ground truth.

### 3.1 AUC (Area Under the ROC Curve)

```java
double auc = AUC.of(truth, probability);
```

- `truth`: binary labels `{0, 1}`.
- `probability`: positive-class probability score (higher means more likely
  to be positive).

**Interpretation:** AUC equals the probability that a randomly chosen positive
sample is ranked higher than a randomly chosen negative sample.

| AUC value | Meaning |
|---|---|
| 1.0 | Perfect ranking — all positives rank above all negatives |
| 0.5 | Random classifier |
| 0.0 | Worst-case — all positives rank below all negatives |

**Algorithm:** Mann–Whitney U rank statistic with tie-averaging:

```
AUC = (sum_ranks_of_positives − pos*(pos+1)/2) / (pos * neg)
```

Ties in `probability` receive the average of their ranks.

```java
int[]    truth = {0, 0, 1, 1};
double[] prob  = {0.1, 0.4, 0.35, 0.8};
double auc = AUC.of(truth, prob);  // = 0.75
```

Returns `NaN` when `truth` contains only one class (no positive or no negative
samples).

---

### 3.2 Log Loss (Binary Cross-Entropy)

```java
double loss = LogLoss.of(truth, probability);
```

- `truth`: binary labels `{0, 1}`.
- `probability`: predicted probability for the positive class, in `(0, 1)`.

**Formula:**

```
LogLoss = −(1/n) Σᵢ [ truth[i]·log(pᵢ) + (1−truth[i])·log(1−pᵢ) ]
```

Computed in nats (natural logarithm). For `truth[i] == 0`, uses
`Math.log1p(−pᵢ)` for numerical accuracy at values near 0.

```java
int[]    truth = {0, 0, 1, 1, 0};
double[] prob  = {0.1, 0.4, 0.35, 0.8, 0.1};
double loss = LogLoss.of(truth, prob);  // ≈ 0.3989
```

- **Lower is better.** Perfect confidence yields 0; confident wrong predictions
  yield `+∞`.
- Probabilities must be in `(0, 1)`; values of exactly 0 or 1 at the wrong
  class produce infinite loss.
- Only binary labels `{0, 1}` are accepted; other values throw
  `IllegalArgumentException`.

---

### 3.3 Cross-Entropy (Multiclass Log Loss)

```java
double ce = CrossEntropy.of(truth, probability);
```

- `truth`: integer class index for each sample.
- `probability`: `double[n][k]` — row `i` contains the probability distribution
  over `k` classes for sample `i`.

**Formula:**

```
CE = −(1/n) Σᵢ log(probability[i][truth[i]])
```

`CrossEntropy` is an interface (not a class); call the `static of(...)` method
directly. It generalizes `LogLoss` to any number of classes; for `k = 2` the
values are identical up to the column selection convention.

```java
int[] truth = {0, 1, 2, 0};
double[][] prob = {
    {0.9, 0.05, 0.05},
    {0.05, 0.9, 0.05},
    {0.05, 0.05, 0.9},
    {0.9, 0.05, 0.05}
};
double ce = CrossEntropy.of(truth, prob);  // = -log(0.9) ≈ 0.1054
```

---

## 4. Regression Metrics

Regression metrics compare continuous truth and prediction arrays.

### 4.1 RSS — Residual Sum of Squares

```java
double rss = RSS.of(truth, prediction);
```

**Formula:** `RSS = Σ (yᵢ − ŷᵢ)²`

RSS is scale-dependent and grows with `n`. Use it when you need the raw
magnitude of the fit, not a normalized quantity.

---

### 4.2 MSE — Mean Squared Error

```java
double mse = MSE.of(truth, prediction);
```

**Formula:** `MSE = RSS / n = (1/n) Σ (yᵢ − ŷᵢ)²`

MSE penalizes large errors heavily (squaring effect) and is the optimization
objective for ordinary least squares. Scale is in squared units of `y`.

---

### 4.3 RMSE — Root Mean Squared Error

```java
double rmse = RMSE.of(truth, prediction);
```

**Formula:** `RMSE = √MSE`

Same units as `y`; directly interpretable as "typical error magnitude".
`RMSE ≥ MAD` always (by Jensen's inequality).

---

### 4.4 MAD — Mean Absolute Error

```java
double mae = MAD.of(truth, prediction);
```

**Formula:** `MAD = (1/n) Σ |yᵢ − ŷᵢ|`

Also called MAE in many frameworks. Less sensitive to outliers than MSE/RMSE
because it does not square the residuals. Both `MAD(truth, pred)` and
`MAD(pred, truth)` produce the same result.

---

### 4.5 R² — Coefficient of Determination

```java
double r2 = R2.of(truth, prediction);
```

**Formula:**

```
R² = 1 − RSS / TSS
TSS = Σ (yᵢ − ȳ)²   (total sum of squares)
```

| R² value | Interpretation |
|---|---|
| 1.0 | Perfect fit — predictions equal truth exactly |
| 0.0 | Model is no better than always predicting `mean(truth)` |
| < 0 | Model is worse than predicting the mean |

**Important:** When `truth` is constant (`TSS = 0`), R² is undefined and
returns `NaN` or infinity. Check before comparing:

```java
double r2 = R2.of(truth, prediction);
if (!Double.isFinite(r2)) {
    // constant truth — R² is not meaningful
}
```

---

### 4.6 Aggregated regression metrics

`smile.validation.RegressionMetrics` bundles all five into a single record:

```java
import smile.validation.RegressionMetrics;

RegressionMetrics m = RegressionMetrics.of(fitTime, scoreTime, truth, prediction);
System.out.println(m.RSS());
System.out.println(m.MSE());
System.out.println(m.RMSE());
System.out.println(m.MAD());
System.out.println(m.R2());
```

---

## 5. Clustering Metrics

Clustering metrics compare a **ground-truth labelling** against a proposed
**cluster assignment**. Labels in both arrays are **permutation-invariant**:
the metrics only care about which samples are grouped together, not which
integer label names each group.

All clustering metrics use a `ContingencyTable` internally, which remaps the
raw integer labels to contiguous indices automatically.

---

### 5.1 Rand Index

```java
double ri = RandIndex.of(truth, cluster);
```

The Rand index measures the fraction of pairs of samples that are either
**both in the same group** or **both in different groups** in both labellings.

**Formula:**

```
RI = (number of agreeing pairs) / C(n, 2)
   = (T − P/2 − Q/2 + C(n,2)) / C(n,2)
```

where:
- `T = Σ C(nᵢⱼ, 2)` — pairs that agree in both clusterings.
- `P = Σᵢ C(aᵢ, 2)` — pairs in the same ground-truth class.
- `Q = Σⱼ C(bⱼ, 2)` — pairs in the same predicted cluster.

Range: `[0, 1]`. A value of 1 means perfect agreement.

**Limitation:** The Rand index has a non-zero expected value for random
clusterings (especially when many clusters are used). Use **Adjusted Rand
Index** for chance-corrected evaluation.

---

### 5.2 Adjusted Rand Index (ARI)

```java
double ari = AdjustedRandIndex.of(truth, cluster);
```

Corrects the Rand index for the expected agreement under chance:

```
ARI = (RI − E[RI]) / (max(RI) − E[RI])
```

| ARI value | Interpretation |
|---|---|
| 1.0 | Perfect agreement |
| 0.0 | Agreement at the level of a random clustering |
| < 0 | Worse than random |

ARI is the standard clustering quality metric when ground-truth labels are
available.

```java
int[] clusters = {0, 0, 1, 1, 2, 2};
int[] alt      = {1, 1, 0, 0, 2, 2};  // same partition, different labels
double ari = AdjustedRandIndex.of(clusters, alt);  // = 1.0 (perfect)
```

---

### 5.3 Mutual Information (MI)

```java
double mi = MutualInformation.of(truth, cluster);
```

Measures the information shared between two labellings (in **nats**, natural
log):

```
I(X;Y) = Σᵢⱼ (nᵢⱼ/n) · log[ (nᵢⱼ/n) / ((aᵢ/n)(bⱼ/n)) ]
```

- **MI = H(truth)** when `truth == cluster` (perfect clustering).
- **MI = 0** when the two labellings are statistically independent.
- Non-negative by definition.

```java
int[] x = {0, 0, 0, 1, 1, 1};
MutualInformation.of(x, x);   // = ln(2) ≈ 0.6931 nats
MutualInformation.of(x, new int[]{0,1,0,1,0,1});  // = 0.0 (independent)
```

---

### 5.4 Normalized Mutual Information (NMI)

NMI scales MI to the interval `[0, 1]` by dividing by a normalization factor
derived from the marginal entropies. Five normalization methods are available:

| Constant | Formula | Notes                                                                        |
|---|---|------------------------------------------------------------------------------|
| `NormalizedMutualInformation.JOINT` | `I / H(X,Y)` | H(X,Y) = joint entropy                                                       |
| `NormalizedMutualInformation.MAX` | `I / max(H(X), H(Y))` | Bounded by the larger entropy                                                |
| `NormalizedMutualInformation.MIN` | `I / min(H(X), H(Y))` | Can reach 1 even for imperfect clustering if one labelling has lower entropy |
| `NormalizedMutualInformation.SUM` | `2I / (H(X) + H(Y))` | Symmetric F-measure-like                                                     |
| `NormalizedMutualInformation.SQRT` | `I / √(H(X)·H(Y))` | Geometric mean normalization                                                |

```java
double nmi = NormalizedMutualInformation.max(truth, cluster);
// or via instance:
double nmi = NormalizedMutualInformation.MAX.score(truth, cluster);
```

All variants equal **1.0** for a perfect clustering and **0.0** for
statistically independent labellings. The variants differ for intermediate cases.

**Note:** Due to floating-point arithmetic, values may be infinitesimally above
1.0 (e.g., 1.0000000000000002); treat values within `1 + 1e-10` as 1.0 in
downstream comparisons.

---

### 5.5 Adjusted Mutual Information (AMI)

AMI corrects MI for chance under a hypergeometric model (analogous to how ARI
corrects RI):

```
AMI = (I − E[MI]) / (norm − E[MI])
```

Four normalization methods are provided:

| Constant | Denominator |
|---|---|
| `AdjustedMutualInformation.MAX` | `max(H(X), H(Y)) − E[MI]` |
| `AdjustedMutualInformation.MIN` | `min(H(X), H(Y)) − E[MI]` |
| `AdjustedMutualInformation.SUM` | `0.5·(H(X) + H(Y)) − E[MI]` |
| `AdjustedMutualInformation.SQRT` | `√(H(X)·H(Y)) − E[MI]` |

```java
double ami = AdjustedMutualInformation.max(truth, cluster);
// or via instance:
double ami = AdjustedMutualInformation.MAX.score(truth, cluster);
```

| AMI value | Interpretation |
|---|---|
| 1.0 | Perfect agreement |
| 0.0 | No more information than chance |
| < 0 | Worse than chance |

**Warning:** The expected MI computation involves a double sum over the
hypergeometric support and can be slow for large numbers of clusters.

---

## 6. Choosing the Right Metric

### Classification

| Scenario | Recommended metric(s) |
|---|---|
| Balanced classes, overall correctness | `Accuracy` |
| Imbalanced classes | `F₁`, `MCC`, `AUC` |
| Precision–recall trade-off | `Precision`, `Recall`, `Fβ`, `FDR` |
| Confident probabilistic output | `LogLoss`, `AUC` |
| Multi-class, equal class importance | `Macro F₁` |
| Multi-class, class-proportional | `Weighted F₁` |
| Best single binary metric | `MCC` |

### Regression

| Scenario | Recommended metric(s) |
|---|---|
| General-purpose | `RMSE`, `R²` |
| Outlier-robust | `MAD` |
| Comparing across different scales | `R²` |
| Matching the loss function (OLS) | `RSS` or `MSE` |

### Clustering

| Scenario                                          | Recommended metric(s) |
|---------------------------------------------------|---|
| Ground truth available, absolute quality          | `ARI` |
| Information-theoretic comparison                  | `AMI (MAX)` |
| Pairwise agreement, no correction                 | `Rand Index` |
| Raw MI for downstream use                         | `MutualInformation` |
| Normalized to `[0, 1]` without chance correction | `NMI (MAX)` |

---

## 7. Usage Patterns

### 7.1 Quick one-liner evaluation

```java
import smile.validation.metric.*;

// Classification
double acc  = Accuracy.of(truth, pred);
double f1   = FScore.of(truth, pred, 1.0, null);
double mcc  = MatthewsCorrelation.of(truth, pred);
double auc  = AUC.of(truth, prob);
double loss = LogLoss.of(truth, prob);

// Regression
double rmse = RMSE.of(yTrue, yPred);
double r2   = R2.of(yTrue, yPred);
double mad  = MAD.of(yTrue, yPred);

// Clustering
double ari = AdjustedRandIndex.of(labels, clusters);
double ami = AdjustedMutualInformation.max(labels, clusters);
double nmi = NormalizedMutualInformation.max(labels, clusters);
```

### 7.2 Reusable metric instances

Pass metrics as `ClassificationMetric` / `RegressionMetric` parameters:

```java
ClassificationMetric metric = new FScore(2.0, Averaging.Macro);  // F₂, macro
double score = metric.score(truth, prediction);

// Use as lambda / method reference
ClassificationMetric simpleAcc = Accuracy::of;  // won't work; use instance
ClassificationMetric acc = Accuracy.instance;
```

Pre-built singleton instances for all metrics:

```java
Accuracy.instance
Error.instance
AUC.instance
LogLoss.instance
MSE.instance
RMSE.instance
RSS.instance
MAD.instance
R2.instance
MutualInformation.instance
RandIndex.instance
AdjustedRandIndex.instance
NormalizedMutualInformation.JOINT   // or MAX, MIN, SUM, SQRT
AdjustedMutualInformation.MAX       // or MIN, SUM, SQRT
```

### 7.3 Aggregated metrics via `ClassificationMetrics`

```java
import smile.validation.ClassificationMetrics;

ClassificationMetrics m = ClassificationMetrics.of(fitTime, scoreTime,
                                                    truth, prediction, prob);
System.out.println(m.accuracy());
System.out.println(m.f1());
System.out.println(m.mcc());
System.out.println(m.auc());
System.out.println(m.logloss());
```

### 7.4 Cross-validation evaluation

```java
import smile.validation.*;

var result = CrossValidation.classification(5, data, labels,
    (x, y) -> SVM.fit(x, y, kernel, C, tol),
    ClassificationMetrics::of);

System.out.println(result);
```

---

## 8. Numeric Examples

### Binary classification summary

```java
int[] truth = {1,1,1,1,1,0,0,0,0,0};
int[] pred  = {1,1,1,0,0,1,0,0,0,0};
// TP=3, FN=2, FP=1, TN=4

Accuracy.of(truth, pred)             // 7/10 = 0.7
Error.of(truth, pred)                // 3
Precision.of(truth, pred)            // 3/(3+1) = 0.75
Recall.of(truth, pred)               // 3/(3+2) = 0.60
FScore.of(truth, pred, 1.0, null)    // 2*0.75*0.60/(0.75+0.60) ≈ 0.667
FDR.of(truth, pred)                  // 1/4 = 0.25
Specificity.of(truth, pred)          // 4/(4+1) = 0.80
Sensitivity.of(truth, pred)          // same as Recall = 0.60
MatthewsCorrelation.of(truth, pred)  // ≈ 0.398
```

### AUC with tie-breaking

```java
int[]    truth = {0, 0, 1, 1};
double[] prob  = {0.1, 0.4, 0.35, 0.8};
// Sorted ascending by prob: labels=[0,1,0,1], ranks=[1,2,3,4]
// Sum of positive ranks = 2+4 = 6
// AUC = (6 - 2*3/2) / (2*2) = 3/4 = 0.75
AUC.of(truth, prob);  // 0.75
```

### R² interpretation

```java
double[] truth = {3.0, -0.5, 2.0, 7.0};
double[] pred  = {2.5,  0.0, 2.0, 8.0};
R2.of(truth, pred);   // ≈ 0.948 — excellent fit

double[] naive = {3.625, 3.625, 3.625, 3.625};  // always predict mean
R2.of(truth, naive);  // = 0.0 — no better than the mean
```

### Perfect clustering vs independent clustering

```java
int[] x = {0, 0, 0, 1, 1, 1};

// Perfect: truth == cluster
AdjustedRandIndex.of(x, x)                       // 1.0
NormalizedMutualInformation.max(x, x)             // 1.0
AdjustedMutualInformation.max(x, x)               // 1.0

// Independent: balanced 2×2 contingency → MI = 0
int[] y = {0, 1, 0, 1, 0, 1};
MutualInformation.of(x, y)                        // 0.0... actually non-zero here
// Use balanced n=4 for exact independence:
MutualInformation.of(new int[]{0,0,1,1}, new int[]{0,1,0,1})  // 0.0
```

---

## 9. Edge Cases and Pitfalls

**All predictions from one class (no positives predicted)**  
`Precision` and `FDR` return `NaN` when no sample is predicted positive.

**All ground truth from one class**  
`Recall`, `Sensitivity` return `NaN`; `AUC` returns `NaN`; `MCC` returns
`NaN`; `R2` returns `NaN`/infinity if all truth values are equal (TSS = 0).
Guard with `Double.isFinite(result)` before using.

**Only one class in truth for MCC**  
`MatthewsCorrelation` requires a 2×2 confusion matrix. If `truth` contains
only one distinct value, the resulting confusion matrix has only one non-zero
row/column and `MCC` returns `NaN`.

**AMI performance**  
`AdjustedMutualInformation` computes the expected MI via a double loop over
the hypergeometric support. For datasets with many small clusters (large `R`
and `C`), this is noticeably slow. Prefer `ARI` or `NMI` for exploratory work.

**NMI slightly above 1.0**  
Floating-point rounding can produce NMI values of `1.0 + ε`. When using NMI
in comparisons (e.g., storing the best score), clamp to `[0.0, 1.0]`:

```java
double nmi = Math.min(1.0, NormalizedMutualInformation.max(truth, cluster));
```

**Large label IDs in `ConfusionMatrix` / `Precision` / `Recall`**  
These metrics allocate arrays of size `max(label) + 1`. Labels like
`{0, 1000}` allocate a 1001-element array. Use remapped/contiguous labels
for efficiency.

**Probabilistic metrics require calibrated probabilities**  
`LogLoss`, `CrossEntropy`, and `AUC` use the raw score values directly.
`LogLoss` blows up (`+∞`) if a probability of exactly `0.0` or `1.0` is
submitted for the wrong class. Calibrate or clip probabilities before use:

```java
double p = Math.max(1e-15, Math.min(1 - 1e-15, rawProbability));
```

---

*SMILE — Copyright © 2010-2026 Haifeng Li. GNU GPL licensed.*

