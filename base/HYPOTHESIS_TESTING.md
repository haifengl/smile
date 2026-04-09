# SMILE — Hypothesis Testing

The `smile.stat.hypothesis` package provides parametric and non-parametric
statistical hypothesis tests. The `smile.stat.Hypothesis` interface acts as
a concise facade that groups all tests under a single import.

---

## Table of Contents

1. [Overview](#1-overview)
2. [The `Hypothesis` Facade](#2-the-hypothesis-facade)
3. [t-Tests](#3-t-tests)
4. [F-Tests and ANOVA](#4-f-tests-and-anova)
5. [Kolmogorov-Smirnov Test](#5-kolmogorov-smirnov-test)
6. [Correlation Tests](#6-correlation-tests)
7. [Chi-Square Tests](#7-chi-square-tests)
8. [Interpreting Results](#8-interpreting-results)
9. [Quick Reference](#9-quick-reference)

---

## 1. Overview

Every test returns a **result record** that bundles the test statistic,
degrees of freedom, and two-sided p-value:

| Test | Result type | Key fields |
|------|------------|------------|
| t-test | `TTest` | `t()`, `df()`, `pvalue()` |
| F-test / ANOVA | `FTest` | `f()`, `df1()`, `df2()`, `pvalue()` |
| KS test | `KSTest` | `d()`, `pvalue()` |
| Correlation | `CorTest` | `cor()`, `t()`, `df()`, `pvalue()` |
| Chi-square | `ChiSqTest` | `chisq()`, `df()`, `pvalue()`, `CramerV()` |

All records implement `toString()` that prints the full test summary.

### Significance Codes

The helper `Hypothesis.significance(double pvalue)` returns the conventional
significance annotation:

| Range | Code |
|-------|------|
| p < 0.001 | `***` |
| p < 0.01  | `**` |
| p < 0.05  | `*` |
| p < 0.1   | `.` |
| p ≥ 0.1   | (blank) |

```java
double p = 0.003;
System.out.println(Hypothesis.significance(p)); // "**"
```

---

## 2. The `Hypothesis` Facade

`smile.stat.Hypothesis` is a marker interface with nested static-method
interfaces. Import it once and access all tests through a uniform API.

```java
import smile.stat.Hypothesis;
import smile.stat.hypothesis.*;

// t-test
TTest t = Hypothesis.t.test(x, 0.0);          // one-sample
TTest t = Hypothesis.t.test(x, y);            // two-sample (Welch)
TTest t = Hypothesis.t.test(x, y, "equal.var");
TTest t = Hypothesis.t.test(x, y, "paired");

// F-test / ANOVA
FTest f = Hypothesis.F.test(x, y);            // variance test
FTest f = Hypothesis.F.test(labels, values);   // one-way ANOVA

// KS test
KSTest ks = Hypothesis.KS.test(x, dist);      // one-sample
KSTest ks = Hypothesis.KS.test(x, y);         // two-sample

// Correlation tests
CorTest c = Hypothesis.cor.test(x, y);                // Pearson
CorTest c = Hypothesis.cor.test(x, y, "spearman");
CorTest c = Hypothesis.cor.test(x, y, "kendall");

// Chi-square tests
ChiSqTest cs = Hypothesis.chisq.test(bins, prob);        // one-sample
ChiSqTest cs = Hypothesis.chisq.test(bins1, bins2);      // two-sample
ChiSqTest cs = Hypothesis.chisq.test(contingencyTable);  // independence
```

---

## 3. t-Tests

Student's t-tests are used to compare means when the population is assumed
normally distributed. All variants are in `TTest`.

### One-Sample t-Test

**Null hypothesis H₀**: the population mean equals a specified value `μ₀`.

```java
double[] x = {2.1, 2.4, 1.9, 2.3, 2.0, 2.2, 1.8, 2.1, 2.0, 2.3};

TTest result = TTest.test(x, 2.0); // H0: mean = 2.0
// or via facade:
TTest result = Hypothesis.t.test(x, 2.0);

System.out.println(result);
// → One Sample t-test(t = 1.1547, df = 9.000, p-value = 0.278 )

System.out.printf("t=%.4f, df=%.0f, p=%.4f%n",
    result.t(), result.df(), result.pvalue());

if (result.pvalue() < 0.05) {
    System.out.println("Reject H0: mean significantly differs from 2.0");
} else {
    System.out.println("Fail to reject H0");
}
```

### Two-Sample t-Test (Welch's)

**Null hypothesis H₀**: the two populations have equal means.
Uses Welch's formula (unequal variances) by default.

```java
double[] x = {2.5, 2.7, 2.3, 2.6, 2.4};
double[] y = {3.0, 3.2, 3.1, 2.9, 3.0};

TTest result = TTest.test(x, y, false); // false = unequal variances (Welch)
// or via facade:
TTest result = Hypothesis.t.test(x, y);

System.out.println(result);
```

### Two-Sample t-Test (Equal Variance)

```java
TTest result = TTest.test(x, y, true); // pooled variance
// or via facade:
TTest result = Hypothesis.t.test(x, y, "equal.var");
```

Use `"equal.var"` when you have reason to believe the two populations have
the same variance (e.g. confirmed by an F-test).

### Paired t-Test

**Null hypothesis H₀**: the mean *difference* between paired observations is zero.
Suitable for before/after designs.

```java
double[] before = {5.0, 5.5, 4.8, 5.2, 5.1};
double[] after  = {5.8, 6.0, 5.5, 5.7, 5.6};

TTest result = TTest.testPaired(before, after);
// or via facade:
TTest result = Hypothesis.t.test(before, after, "paired");

System.out.println(result);
```

### Correlation t-Test

Tests whether the Pearson correlation coefficient differs significantly from 0.

```java
double r = MathEx.cor(x, y);
int df = x.length - 2;

TTest result = TTest.test(r, df);
// or via facade:
TTest result = Hypothesis.t.test(r, df);
```

---

## 4. F-Tests and ANOVA

### Two-Sample Variance Test

**Null hypothesis H₀**: the two populations have equal variances.

> ⚠️ The F-test is extremely sensitive to departures from normality. Use it
> only when the data is confirmed to be normally distributed.

```java
double[] x = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
double[] y = {1.1, 1.9, 2.8, 4.2, 5.1, 6.2};

FTest result = FTest.test(x, y);
// or via facade:
FTest result = Hypothesis.F.test(x, y);

System.out.println(result);
// → F-test(f = 1.1143, df1 = 5, df2 = 5, p-value = 0.9012)

System.out.printf("F=%.4f, df1=%d, df2=%d, p=%.4f%n",
    result.f(), result.df1(), result.df2(), result.pvalue());
```

### One-Way ANOVA

**Null hypothesis H₀**: all group means are equal.
Assumes equal variances across groups.

```java
int[]    groups = {1,1,1,1,1,1, 2,2,2,2,2,2, 3,3,3,3,3,3};
double[] values = {6,8,4,5,3,4, 8,12,9,11,6,8, 13,9,11,8,7,12};

FTest result = FTest.test(groups, values);
// or via facade:
FTest result = Hypothesis.F.test(groups, values);

System.out.println(result);
// → F-test(f = 9.2647, df1 = 2, df2 = 15, p-value = 0.002399)

if (result.pvalue() < 0.05) {
    System.out.println("At least one group mean differs significantly.");
}
```

---

## 5. Kolmogorov-Smirnov Test

The KS test compares distributions via their empirical CDFs. It is
non-parametric and sensitive to differences in both location and shape.

> **Note**: Both `test()` overloads **sort the input arrays in place**.
> Pass copies if the original order must be preserved.

### One-Sample KS Test

**Null hypothesis H₀**: the data was drawn from the reference distribution.

```java
import smile.stat.distribution.GaussianDistribution;

double[] x = /* observed data */;

KSTest result = KSTest.test(x, GaussianDistribution.getInstance());
// or via facade:
KSTest result = Hypothesis.KS.test(x, GaussianDistribution.getInstance());

System.out.println(result);
// → One Sample Kolmogorov-Smirnov Test(d = 0.0930, p-value = 0.7598)

System.out.printf("D=%.4f, p=%.4f%n", result.d(), result.pvalue());
// Large p-value → fail to reject H0 (data is consistent with N(0,1))
```

### Two-Sample KS Test

**Null hypothesis H₀**: both samples are drawn from the same distribution.

```java
double[] x = /* sample 1 */;
double[] y = /* sample 2 */;

KSTest result = KSTest.test(x, y);
// or via facade:
KSTest result = Hypothesis.KS.test(x, y);

System.out.println(result);
// Small p-value → distributions differ significantly
```

---

## 6. Correlation Tests

`CorTest` provides three correlation measures, each with an associated
significance test.

### Pearson Correlation

Measures **linear** association. Assumes bivariate normality.

```java
double[] x = {44.4, 45.9, 41.9, 53.3, 44.7, 44.1, 50.7, 45.2, 60.1};
double[] y = { 2.6,  3.1,  2.5,  5.0,  3.6,  4.0,  5.2,  2.8,  3.8};

CorTest result = CorTest.pearson(x, y);
// or via facade:
CorTest result = Hypothesis.cor.test(x, y);           // default = Pearson
CorTest result = Hypothesis.cor.test(x, y, "pearson");

System.out.println(result);
// → Pearson Correlation Test(cor = 0.57, t = 1.8411, df = 7, p-value = 0.1082)

System.out.printf("r=%.4f, t=%.4f, df=%.0f, p=%.4f%n",
    result.cor(), result.t(), result.df(), result.pvalue());
```

### Spearman Rank Correlation

Measures **monotone** association. Distribution-free (non-parametric).

```java
CorTest result = CorTest.spearman(x, y);
// or:
CorTest result = Hypothesis.cor.test(x, y, "spearman");

System.out.printf("ρ=%.4f, p=%.4f%n", result.cor(), result.pvalue());
```

### Kendall Rank Correlation

Measures **concordance** between rankings. More robust than Spearman for
small samples with many ties.

```java
CorTest result = CorTest.kendall(x, y);
// or:
CorTest result = Hypothesis.cor.test(x, y, "kendall");

System.out.printf("τ=%.4f, p=%.4f%n", result.cor(), result.pvalue());
```

> **Note**: The Kendall test is non-parametric. Its `df()` field is set to 0.
> The p-value uses a normal approximation valid for n ≥ 10.

### Choosing a Correlation Method

| Scenario | Recommended method |
|----------|--------------------|
| Continuous, normally distributed data | Pearson |
| Ordinal data, or non-normal continuous | Spearman |
| Small samples with ties | Kendall |

---

## 7. Chi-Square Tests

Pearson's chi-square tests compare observed counts to expected counts.

> ⚠️ The chi-square approximation is only valid when expected counts are
> reasonably large (typically ≥ 5 per cell). With small counts, consider
> Fisher's exact test.

### One-Sample Goodness-of-Fit

**Null hypothesis H₀**: the observed frequencies follow the expected distribution.

```java
// Observed die rolls
int[]    bins = {20, 22, 13, 22, 10, 13};
double[] prob = {1.0/6, 1.0/6, 1.0/6, 1.0/6, 1.0/6, 1.0/6}; // fair die

ChiSqTest result = ChiSqTest.test(bins, prob);
// or via facade:
ChiSqTest result = Hypothesis.chisq.test(bins, prob);

System.out.println(result);
// → One Sample Chi-squared Test(t = 8.36, df = 5, p-value = 0.1370)

System.out.printf("χ²=%.4f, df=%.0f, p=%.4f%n",
    result.chisq(), result.df(), result.pvalue());
```

Specify a custom number of constraints (default = 1) when parameters were
estimated from the data:

```java
// Constraints = 1 (default): one constraint from the total count
ChiSqTest result = ChiSqTest.test(bins, prob, 1);
// or:
ChiSqTest result = Hypothesis.chisq.test(bins, prob, 1);
```

### Two-Sample Chi-Square Test

**Null hypothesis H₀**: both samples have the same underlying distribution.

```java
int[] bins1 = {8, 13, 16, 10, 3};
int[] bins2 = {4,  9, 14, 16, 7};

ChiSqTest result = ChiSqTest.test(bins1, bins2);
// or via facade:
ChiSqTest result = Hypothesis.chisq.test(bins1, bins2);

System.out.printf("χ²=%.4f, df=%.0f, p=%.4f%n",
    result.chisq(), result.df(), result.pvalue());
```

### Chi-Square Test of Independence (Contingency Table)

**Null hypothesis H₀**: the two categorical variables are independent.

Also computes **Cramér's V** — a measure of association strength between
0 (no association) and 1 (perfect association). For 2×2 tables, equals
the Phi coefficient; continuity correction is applied automatically.

```java
// 2×2 contingency table
int[][] table = {
    {12,  7},
    { 5,  7}
};

ChiSqTest result = ChiSqTest.test(table);
// or via facade:
ChiSqTest result = Hypothesis.chisq.test(table);

System.out.println(result);
// → Contingency Table Chi-squared Test(t = 0.9..., df = 1, p-value = 0.4233, Cramer's V = 0.22)

System.out.printf("χ²=%.4f, df=%.0f, p=%.4f, V=%.4f%n",
    result.chisq(), result.df(), result.pvalue(), result.CramerV());
```

Larger tables (r × c) work the same way:

```java
int[][] table3x3 = {
    {50, 30, 20},
    {15, 45, 40},
    {35, 25, 40}
};
ChiSqTest result = ChiSqTest.test(table3x3);
```

---

## 8. Interpreting Results

### The p-value

The **p-value** is the probability of observing data at least as extreme as
the sample, *assuming the null hypothesis is true*. A small p-value provides
evidence against H₀.

| p-value | Interpretation |
|---------|----------------|
| < 0.001 | Very strong evidence against H₀ |
| 0.001–0.01 | Strong evidence against H₀ |
| 0.01–0.05 | Moderate evidence against H₀ |
| 0.05–0.1 | Weak / suggestive evidence |
| ≥ 0.1 | Insufficient evidence to reject H₀ |

> **Common misconception**: a large p-value does not "prove" H₀. It merely
> means the data are consistent with H₀.

### Two-Sided vs. One-Sided Tests

All tests in this package report **two-sided p-values**. To obtain a
one-sided p-value for a t-test, divide by 2 (for symmetric distributions):

```java
TTest t = TTest.test(x, 0.0);
double oneSidedP = t.pvalue() / 2.0; // if you predicted the direction
```

### Multiple Comparisons

If you run many tests simultaneously, consider correcting for multiple
comparisons (Bonferroni, Benjamini-Hochberg, etc.) to control the
family-wise error rate or false discovery rate.

### Effect Size

The p-value tells you *whether* an effect exists, not *how large* it is.
Always report an effect size alongside the p-value:

| Test | Suggested effect size |
|------|-----------------------|
| t-test | Cohen's d |
| F-test / ANOVA | η² (eta-squared) |
| Correlation | r (Pearson), ρ (Spearman), τ (Kendall) |
| Chi-square | Cramér's V (provided in `CramerV()`) |

---

## 9. Quick Reference

```java
import smile.stat.Hypothesis;
import smile.stat.hypothesis.*;

// ── Significance code ─────────────────────────────────────────────
Hypothesis.significance(0.003); // → "**"

// ── t-Tests ───────────────────────────────────────────────────────
TTest r = Hypothesis.t.test(x, mu0);          // one-sample
TTest r = Hypothesis.t.test(x, y);            // Welch two-sample
TTest r = Hypothesis.t.test(x, y, "equal.var"); // pooled two-sample
TTest r = Hypothesis.t.test(x, y, "paired");  // paired
TTest r = Hypothesis.t.test(corr, df);        // correlation significance

r.t();       // t-statistic
r.df();      // degrees of freedom
r.pvalue();  // two-sided p-value

// ── F-Test / ANOVA ────────────────────────────────────────────────
FTest r = Hypothesis.F.test(x, y);            // variance equality
FTest r = Hypothesis.F.test(labels, values);  // one-way ANOVA

r.f();       // F-statistic
r.df1();     // numerator df
r.df2();     // denominator df
r.pvalue();

// ── KS Test ───────────────────────────────────────────────────────
KSTest r = Hypothesis.KS.test(x, distribution); // one-sample
KSTest r = Hypothesis.KS.test(x, y);            // two-sample

r.d();       // KS statistic (max |F_n - F|)
r.pvalue();

// ── Correlation Tests ─────────────────────────────────────────────
CorTest r = Hypothesis.cor.test(x, y);              // Pearson
CorTest r = Hypothesis.cor.test(x, y, "spearman");  // Spearman
CorTest r = Hypothesis.cor.test(x, y, "kendall");   // Kendall

r.cor();     // correlation coefficient
r.t();       // test statistic
r.df();      // degrees of freedom (0 for Kendall)
r.pvalue();

// ── Chi-Square Tests ──────────────────────────────────────────────
ChiSqTest r = Hypothesis.chisq.test(bins, prob);         // goodness-of-fit
ChiSqTest r = Hypothesis.chisq.test(bins, prob, k);      // with k constraints
ChiSqTest r = Hypothesis.chisq.test(bins1, bins2);       // two-sample
ChiSqTest r = Hypothesis.chisq.test(bins1, bins2, k);    // with k constraints
ChiSqTest r = Hypothesis.chisq.test(contingencyTable);   // independence

r.chisq();   // chi-square statistic
r.df();      // degrees of freedom
r.pvalue();
r.CramerV(); // effect size (contingency table only)
```

### Direct API (without facade)

```java
TTest.test(x, mean);
TTest.test(x, y, equalVariance);
TTest.testPaired(x, y);
TTest.test(r, df);

FTest.test(x, y);
FTest.test(labels, values);          // ANOVA

KSTest.test(x, distribution);
KSTest.test(x, y);

CorTest.pearson(x, y);
CorTest.spearman(x, y);
CorTest.kendall(x, y);

ChiSqTest.test(bins, prob);
ChiSqTest.test(bins, prob, constraints);
ChiSqTest.test(bins1, bins2);
ChiSqTest.test(bins1, bins2, constraints);
ChiSqTest.test(table);
```


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

