# SMILE — Regression User Guide

The `smile.regression` package provides a comprehensive suite of regression algorithms.
This guide describes the core abstractions, explains each algorithm with its API,
key parameters, and usage patterns, and ends with a comparative summary and practical tips.

---

## Table of Contents

1. [Package Overview](#1-package-overview)
2. [Core Abstractions](#2-core-abstractions)
   - 2.1 [Regression Interface](#21-regression-interface)
   - 2.2 [DataFrameRegression](#22-dataframeregression)
   - 2.3 [LinearModel](#23-linearmodel)
3. [Linear Models](#3-linear-models)
   - 3.1 [OLS — Ordinary Least Squares](#31-ols--ordinary-least-squares)
   - 3.2 [RidgeRegression](#32-ridgeregression)
   - 3.3 [LASSO](#33-lasso)
   - 3.4 [ElasticNet](#34-elasticnet)
4. [Generalized Linear Models](#4-generalized-linear-models)
   - 4.1 [GLM — Generalized Linear Model](#41-glm--generalized-linear-model)
   - 4.2 [GLM Families (`smile.regression.glm`)](#42-glm-families-smileregressionglm)
   - 4.3 [GAM — Generalized Additive Model](#43-gam--generalized-additive-model)
5. [Tree and Ensemble Regressors](#5-tree-and-ensemble-regressors)
   - 5.1 [RegressionTree](#51-regressiontree)
   - 5.2 [RandomForest](#52-randomforest)
   - 5.3 [GradientTreeBoost](#53-gradienttreeboost)
6. [Support Vector Regression](#6-support-vector-regression)
   - 6.1 [SVM — Epsilon-SVR](#61-svm--epsilon-svr)
   - 6.2 [LinearSVM, SparseLinearSVM, BinarySparseLinearSVM](#62-linearsvm-sparselinearsvm-binarysparselinearsvm)
   - 6.3 [KernelMachine](#63-kernelmachine)
7. [Gaussian Process Regression](#7-gaussian-process-regression)
8. [Neural Network and RBF](#8-neural-network-and-rbf)
   - 8.1 [MLP — Multilayer Perceptron](#81-mlp--multilayer-perceptron)
   - 8.2 [RBFNetwork — Radial Basis Function Network](#82-rbfnetwork--radial-basis-function-network)
9. [Algorithm Comparison](#9-algorithm-comparison)
10. [Common Patterns and Tips](#10-common-patterns-and-tips)

---

## 1. Package Overview

The `smile.regression` package provides a complete suite of supervised regression
algorithms for predicting a continuous response variable. The package follows a
consistent design:

- **Static `fit(…)` factory methods** — every algorithm exposes one or more `fit`
  methods; there is no mutable builder to misconfigure.
- **Uniform `Regression<T>` interface** — a single `predict(T x)` for scalar output,
  plus batch, list, and dataset overloads; optional online `update`.
- **DataFrame integration** — algorithms that work with named columns implement
  `DataFrameRegression` and accept `Formula` + `DataFrame`.
- **Serializable** — every model implements `java.io.Serializable` for persistence.
- **Properties round-trip** — hyperparameters can be read from and written to
  `java.util.Properties` via nested `Options` records, enabling configuration-file
  workflows.

The package also contains two sub-packages:
- `smile.regression.glm` — GLM distribution families (link functions, variance,
  deviance, log-likelihood).
- `smile.regression.gam` — B-spline and smoothing spline primitives for GAM.

---

## 2. Core Abstractions

### 2.1 `Regression` Interface

```java
public interface Regression<T> extends ToDoubleFunction<T>, Serializable
```

The central contract that every regression model implements.

| Method | Description |
|--------|-------------|
| `double predict(T x)` | Predicts the response value for a single instance. |
| `double[] predict(T[] x)` | Batch prediction over an array. |
| `double[] predict(List<T> x)` | Batch prediction over a list. |
| `double[] predict(Dataset<T,?> x)` | Batch prediction over a `Dataset`. |
| `boolean online()` | Returns `true` if the model supports incremental updates. |
| `void update(T x, double y)` | Online update with one sample (default: throws). |
| `void update(T[] x, double[] y)` | Batch online update. |
| `void update(Dataset<T,Double> batch)` | Batch update from a `Dataset`. |
| `double applyAsDouble(T x)` | Alias for `predict`; satisfies `ToDoubleFunction<T>`. |

The interface also declares a nested `Trainer<T, M>`:

```java
interface Trainer<T, M extends Regression<T>> {
    M fit(T[] x, double[] y);
    M fit(T[] x, double[] y, Properties params);
}
```

Use `Trainer` when writing generic code that works with any regression algorithm or
when hyperparameters come from a configuration file.

**Ensemble average.** The static factory method `Regression.ensemble(models...)` returns
a model whose prediction is the arithmetic mean of all constituent models. The ensemble
is an online learner only if every base model is.

```java
Regression<double[]> avg = Regression.ensemble(model1, model2, model3);
double y = avg.predict(sample);  // mean of three predictions
```

**Online learning check.**

```java
if (model.online()) {
    model.update(newX, newY);
}
```

### 2.2 `DataFrameRegression`

```java
public interface DataFrameRegression extends Regression<Tuple>
```

An extension for models trained on `DataFrame` objects via a `Formula`. It adds:

| Method | Description |
|--------|-------------|
| `Formula formula()` | The formula used at training time. |
| `StructType schema()` | The feature schema of the design matrix. |
| `double predict(Tuple x)` | Predicts from a named-column `Tuple`. |
| `double[] predict(DataFrame df)` | Predicts all rows of a `DataFrame`. |

All tree-based models (`RegressionTree`, `RandomForest`, `GradientTreeBoost`) and
`GLM`, `GAM`, `LinearModel` implement `DataFrameRegression`.

The static helper `DataFrameRegression.of(formula, model)` adapts any
`Regression<double[]>` into a `DataFrameRegression`, applying formula extraction
automatically:

```java
Regression<double[]> base = RidgeRegression.fit(x, y, lambda);
DataFrameRegression dfModel = DataFrameRegression.of(formula, data, base::fit);
```

### 2.3 `LinearModel`

The shared result type produced by `OLS`, `RidgeRegression`, `LASSO`, and `ElasticNet`.
Implements `DataFrameRegression` and provides comprehensive statistical diagnostics.

**Key accessors:**

| Method | Description |
|--------|-------------|
| `double[] coefficients()` | Fitted weights (last element is the intercept when included in the design). |
| `double intercept()` | The intercept term. |
| `double RSquared()` | Coefficient of determination R². |
| `double adjustedRSquared()` | R² adjusted for the number of predictors. |
| `double RSS()` | Residual sum of squares. |
| `double TSS()` | Total sum of squares. |
| `double error()` | Root mean square error of the residuals (σ̂). |
| `double[][] ttest()` | t-tests of coefficients: estimate, std error, t-statistic, p-value. |
| `double F()` | F-statistic for overall significance. |
| `double pvalue()` | p-value of the F-test. |
| `double[] fittedValues()` | Training fitted values. |
| `double[] residuals()` | Training residuals. |
| `double predict(Tuple x)` | Predict from a `Tuple`. |
| `double predict(double[] x)` | Predict from a raw feature array. |
| `double[] predict(DataFrame df)` | Batch prediction. |
| `void update(double[] x, double y)` | Recursive Least Squares (RLS) update when enabled. |
| `String toString()` | Human-readable summary resembling R's `lm` output. |

**Recursive Least Squares** (online updates) is available when `OLS.Options.recursive =
true` (the default). Each call to `update(x, y)` adjusts the model using the Sherman–
Morrison–Woodbury formula; no matrix factorization is required per update.

---

## 3. Linear Models

### 3.1 OLS — Ordinary Least Squares

Ordinary least squares minimises the sum of squared residuals `‖y - Xβ‖²`. The design
matrix is built automatically from the `Formula` — you do not add an intercept column
manually.

```java
LinearModel model = OLS.fit(formula, data);
LinearModel model = OLS.fit(formula, data, options);
```

**Options**

| Option | Default | Description                                                                                          |
|--------|---------|------------------------------------------------------------------------------------------------------|
| `method` | `QR` | Factorization method: `QR` (default, fast, numerically stable) or `SVD` (handles rank-deficient X). |
| `stderr` | `true` | Whether to compute standard errors, t-statistics, and p-values for all coefficients.                 |
| `recursive` | `true` | Whether to enable recursive least squares via `model.update(x, y)`.                                  |

```java
// Properties API
Properties props = new Properties();
props.setProperty("smile.ols.method", "QR");
props.setProperty("smile.ols.standard_error", "true");
props.setProperty("smile.ols.recursive", "false");
LinearModel model = OLS.fit(formula, data, OLS.Options.of(props));
```

**Full diagnostics example:**

```java
LinearModel model = OLS.fit(Formula.lhs("y"), data);
System.out.println(model);  // prints coefficients, std errors, t-tests, R², F-stat

double r2   = model.RSquared();
double rmse = model.error();

double[][] ttest = model.ttest();
// ttest[i][0] = coefficient estimate
// ttest[i][1] = standard error
// ttest[i][2] = t-statistic
// ttest[i][3] = two-sided p-value
```

**RLS update:**

```java
LinearModel model = OLS.fit(formula, data);  // recursive=true by default
model.update(newFeatures, newTarget);         // Sherman-Morrison-Woodbury
```

**Limitations.** Fails when `n ≤ p` (more predictors than observations) — use
`RidgeRegression` or LASSO in that regime. With multicollinearity the QR solver
automatically falls back to SVD.

### 3.2 `RidgeRegression`

Ridge regression adds an L2 penalty `λ‖β‖²` to the least-squares objective, shrinking
all coefficients toward zero. Features are standardised internally so the penalty is
applied fairly across different scales.

```java
LinearModel model = RidgeRegression.fit(formula, data, lambda);
LinearModel model = RidgeRegression.fit(x, y, lambda);

// Via Options record
LinearModel model = RidgeRegression.fit(formula, data, options);
```

**Parameters.**

| Parameter | Description                                                                                                            |
|-----------|------------------------------------------------------------------------------------------------------------------------|
| `lambda` | Regularization strength (`λ > 0`). Larger = more shrinkage. Typical range: `0.001–1000`. Select via cross-validation. |

```java
// Convenience: fit with explicit lambda
LinearModel model = RidgeRegression.fit(formula, boston, 0.1);

double[] coef = model.coefficients();
double   rmse = model.error();
```

**When to use.** When you have many moderately-relevant predictors and need stable
estimation under multicollinearity. Ridge does not zero any coefficient exactly, so it
does not perform variable selection.

### 3.3 `LASSO`

LASSO (Least Absolute Shrinkage and Selection Operator) adds an L1 penalty `λ‖β‖₁`,
producing sparse solutions where some coefficients are exactly zero. It is solved via
an interior-point PCG algorithm on the standardised design matrix.

```java
LinearModel model = LASSO.fit(formula, data, lambda);
LinearModel model = LASSO.fit(x, y, lambda);
LinearModel model = LASSO.fit(x, y, lambda, tol, maxIter);
```

**Parameters.**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `lambda` | — | L1 penalty. Larger = sparser solution. |
| `tol` | `1E-4` | Convergence tolerance for the duality gap. |
| `maxIter` | `500` | Maximum interior-point iterations. |

**When to use.** When you believe only a subset of predictors is truly relevant (sparse
signal), or when you need an interpretable model with automatic variable selection.

### 3.4 `ElasticNet`

Elastic net combines L1 and L2 penalties, expressed as `λ₁‖β‖₁ + λ₂‖β‖²`. It is
implemented by augmenting the design matrix and delegating to the LASSO solver.

```java
LinearModel model = ElasticNet.fit(formula, data, lambda1, lambda2);
LinearModel model = ElasticNet.fit(x, y, lambda1, lambda2);
LinearModel model = ElasticNet.fit(x, y, lambda1, lambda2, tol, maxIter);
```

**Parameters.**

| Parameter | Description |
|-----------|-------------|
| `lambda1` | L1 penalty coefficient (`> 0`). Controls sparsity. |
| `lambda2` | L2 penalty coefficient (`> 0`). Controls grouping effect. |

**When to use.** When predictors are correlated in groups. LASSO tends to pick one from
each group arbitrarily; elastic net tends to include or exclude the group as a whole.
Also useful when `p > n`.

**Choosing λ₁ and λ₂.** A common approach is to fix the mixing ratio `α = λ₁/(λ₁+λ₂)` ∈ [0,1] (0 = pure ridge, 1 = pure LASSO) and select total penalty by cross-validation.

---

## 4. Generalized Linear Models

### 4.1 `GLM` — Generalized Linear Model

GLM extends linear regression to non-normal response distributions through a *link
function* and a *variance function*, fitted by Iteratively Weighted Least Squares (IWLS).

```java
GLM model = GLM.fit(formula, data, model);
GLM model = GLM.fit(formula, data, model, options);
```

Where `model` is a `smile.regression.glm.Model` instance (a distribution family with
link function). See [Section 4.2](#42-glm-families-smileregressionglm) for available
families.

**Options**

| Option | Default | Description |
|--------|---------|-------------|
| `tol` | `1E-5` | Convergence tolerance on change in deviance between IWLS iterations. |
| `maxIter` | `50` | Maximum IWLS iterations. |

```java
// Properties API
Properties props = new Properties();
props.setProperty("smile.glm.tolerance",  "1E-6");
props.setProperty("smile.glm.iterations", "100");
GLM model = GLM.fit(formula, data, Bernoulli.logit(), GLM.Options.of(props));
```

**API.**

| Method | Description |
|--------|-------------|
| `double[] coefficients()` | Fitted linear predictor coefficients (including intercept). |
| `double[][] ztest()` | Coefficient z-tests: estimate, std error, z-score, p-value. |
| `double[] fittedValues()` | Fitted mean values on the response scale (after `invlink`). |
| `double[] devianceResiduals()` | Deviance residuals for each observation. |
| `double deviance()` | Residual deviance of the fitted model. |
| `double logLikelihood()` | Log-likelihood of the fitted model. |
| `double AIC()` | Akaike information criterion `2k − 2ℓ`. |
| `double BIC()` | Bayesian information criterion. |
| `double predict(Tuple x)` | Predicts on the response scale via `invlink(Xβ)`. |
| `double[] predict(DataFrame df)` | Batch prediction. |
| `String toString()` | R-style summary with deviance table, coefficients, z-tests. |

**Interpreting output:**

```java
GLM model = GLM.fit(Formula.lhs("y"), data, Bernoulli.logit());
System.out.println(model);        // prints summary

double[][] z = model.ztest();
// z[i][0] = coefficient  (log-odds scale for logit link)
// z[i][1] = std error
// z[i][2] = z-score
// z[i][3] = p-value (two-sided Wald test)

// Prediction returns probability (0–1) for Bernoulli/Binomial
double prob = model.predict(testTuple);
```

**Requirements.** `n > p` (more observations than predictors). Multinomial response is
not supported; use `smile.classification.LogisticRegression` instead.

### 4.2 GLM Families (`smile.regression.glm`)

Each family is a Java interface (`Bernoulli`, `Binomial`, `Poisson`, `Gaussian`)
exposing a static factory that returns a `Model`.

| Family | Factory | Link | Response type |
|--------|---------|------|---------------|
| Bernoulli | `Bernoulli.logit()` | logit | Binary (0/1) outcomes |
| Binomial | `Binomial.logit(int[] n)` | logit | Sample proportions (counts / n), each with trial count n[i] |
| Poisson | `Poisson.log()` | log | Non-negative count data |
| Gaussian | `Gaussian.identity()` | identity | Continuous with additive Gaussian noise |
| Gaussian | `Gaussian.log()` | log | Positive continuous; multiplicative errors |
| Gaussian | `Gaussian.inverse()` | inverse | Positive continuous; inverse relationship |

**Bernoulli (binary logistic regression):**

```java
// y must be 0 or 1
GLM model = GLM.fit(Formula.lhs("default"), data, Bernoulli.logit());
double prob = model.predict(tuple);  // P(y = 1 | x)
```

**Binomial (grouped binary outcomes):**

```java
// y[i] = k[i]/n[i] (proportion of successes), n[i] = number of trials
int[] n = {10, 20, 15, ...};
GLM model = GLM.fit(Formula.lhs("proportion"), data, Binomial.logit(n));
```

**Poisson (count regression):**

```java
// y must be non-negative integers
GLM model = GLM.fit(Formula.lhs("count"), data, Poisson.log());
double rate = model.predict(tuple);  // estimated rate λ
```

**Gaussian with non-default link (e.g. log):**

```java
// Use when the multiplicative model is more natural than additive
GLM model = GLM.fit(Formula.lhs("price"), data, Gaussian.log());
```

### 4.3 GAM — Generalized Additive Model

GAM models the response as the sum of smooth non-parametric functions of each predictor:
`g(μ) = β₀ + f₁(x₁) + f₂(x₂) + …`. Each `fᵢ` is a P-spline fitted by penalized
iteratively reweighted least squares (PIRLS) with backfitting.

```java
GAM model = GAM.fit(formula, data);                    // Gaussian identity by default
GAM model = GAM.fit(formula, data, glmModel);           // custom family
GAM model = GAM.fit(formula, data, glmModel, options);  // with hyperparameters
```

**Options**

| Option | Default | Description |
|--------|---------|-------------|
| `k` | `5` | Number of B-spline knots per predictor. More knots = more flexible. |
| `lambdas` | null | Smoothing penalties per predictor. Estimated from data if null. |
| `tol` | `1E-4` | Backfitting convergence tolerance. |
| `maxIter` | `50` | Maximum backfitting iterations. |

**API.**

```java
GAM model = GAM.fit(Formula.lhs("price"), data);

double[] edf   = model.effectiveDF();   // effective degrees of freedom per predictor
double   aic   = model.AIC();
double   bic   = model.BIC();
double   dev   = model.deviance();
double   logL  = model.logLikelihood();

double yhat = model.predict(tuple);
double[] yhats = model.predict(df);
```

**Restrictions.** Only numeric predictors are supported; categorical variables must be
encoded before use. The formula must produce a purely numeric design matrix.

---

## 5. Tree and Ensemble Regressors

All three models implement `DataFrameRegression` and accept `Formula` + `DataFrame`.

### 5.1 `RegressionTree`

A single CART regression tree that minimises squared-error loss (or a custom `Loss`
function when used as a boosting base learner).

```java
RegressionTree model = RegressionTree.fit(formula, data);
RegressionTree model = RegressionTree.fit(formula, data, props);
```

**Options** (via `Properties`)

| Property | Default | Description |
|----------|---------|-------------|
| `smile.cart.node.size` | `5` | Minimum leaf node size. |
| `smile.cart.max.depth` | `20` | Maximum tree depth. |
| `smile.cart.max.nodes` | `0` (unlimited) | Maximum leaf nodes. |
| `smile.regression_tree.bins` | `-1` (auto) | Number of histogram bins for continuous features. |

**API.**

```java
double   yhat   = model.predict(tuple);
double[] yhats  = model.predict(df);
double[] imp    = model.importance();   // impurity-based feature importance
String   text   = model.toString();     // printable tree
Formula  f      = model.formula();
```

**Use cases.** A quick non-parametric baseline. Prone to overfitting — prefer
`RandomForest` or `GradientTreeBoost` in production.

### 5.2 `RandomForest`

An ensemble of regression trees, each trained on a bootstrap sample with random feature
subsets at each split. Aggregates via the arithmetic mean.

```java
RandomForest model = RandomForest.fit(formula, data);
RandomForest model = RandomForest.fit(formula, data, props);
```

**Options** (via `Properties`)

| Property | Default | Description |
|----------|---------|-------------|
| `smile.random.forest.trees` | `500` | Number of trees. |
| `smile.random.forest.mtry` | `max(p/3, 1)` | Feature count sampled per split. |
| `smile.random.forest.max.depth` | `20` | Maximum tree depth. |
| `smile.random.forest.node.size` | `5` | Minimum node size. |
| `smile.random.forest.sampling.rate` | `1.0` | Bootstrap fraction (< 1 for subsampling). |

**API.**

```java
double   yhat    = model.predict(tuple);
double[] yhats   = model.predict(df);

RegressionMetrics metrics = model.metrics();  // OOB RMSE, MAD, etc.
double   oobRMSE = model.metrics().rmse();

double[] imp     = model.importance();         // feature importance
double[][] shap  = model.shap(df);             // SHAP values
Formula  formula = model.formula();

// Merge two forests (e.g. parallel training)
RandomForest merged = model1.merge(model2);

// Trim the least accurate trees
model.trim(200);  // keep best 200 trees
```

**OOB estimation.** Each tree is evaluated on the samples not used in its bootstrap,
providing an almost-free unbiased generalization error estimate:

```java
System.out.println("OOB RMSE: " + model.metrics().rmse());
```

### 5.3 `GradientTreeBoost`

Gradient boosting with regression trees: each tree fits the pseudo-residuals (negative
gradient of the loss) of the current ensemble. Supports multiple loss functions.

```java
GradientTreeBoost model = GradientTreeBoost.fit(formula, data);
GradientTreeBoost model = GradientTreeBoost.fit(formula, data, props);
```

**Options** (via `Properties`)

| Property | Default | Description                                                            |
|----------|---------|------------------------------------------------------------------------|
| `smile.gbt.trees` | `500` | Number of boosting rounds.                                             |
| `smile.gbt.loss` | `LeastSquares` | Loss function: `LeastSquares`, `LeastAbsoluteDeviation`, `Huber`.      |
| `smile.gbt.shrinkage` | `0.05` | Learning rate. Smaller = more trees needed but better generalization. |
| `smile.gbt.max.depth` | `5` | Maximum tree depth.                                                    |
| `smile.gbt.node.size` | `5` | Minimum node size.                                                     |
| `smile.gbt.sampling.rate` | `0.7` | Subsample fraction per tree (stochastic GBT).                          |

**API.**

```java
double   yhat   = model.predict(tuple);
double[] yhats  = model.predict(df);

double[] imp    = model.importance();   // split-gain feature importance
double[][] shap = model.shap(df);       // SHAP values

// Inspect cumulative test error as trees are added
int[][] test = model.test(testDf);      // [n_trees][n_samples]
```

**Loss functions.**

| Loss | Best for |
|------|---------|
| `LeastSquares` | Standard regression; assumes Gaussian noise |
| `LeastAbsoluteDeviation` | Robust to outliers; fits the conditional median |
| `Huber` | Combines advantages of both; needs a `delta` quantile threshold |

**Tuning.** Shrinkage and tree count are a trade-off: smaller shrinkage (`0.01–0.05`)
usually gives the best results but requires more trees. Subsampling
(`sampling.rate < 1.0`) adds stochasticity that reduces overfitting.

---

## 6. Support Vector Regression

### 6.1 `SVM` — Epsilon-SVR

The `SVM` class is a static factory for epsilon-insensitive support vector regression.
It wraps `smile.model.svm.SVR` and returns a `Regression` model. No penalty is imposed
on training samples whose prediction error is within `±ε` of the true value.

```java
// Dense Euclidean / linear kernel
Regression<double[]>  model = SVM.fit(x, y, options);

// Dense arbitrary kernel
KernelMachine<T>      model = SVM.fit(x, y, kernel, options);

// Dense sparse (SparseArray)
SparseLinearSVM       model = SVM.fit(x, y, p, options);   // x: SparseArray[]

// Binary sparse (0/1 feature arrays)
BinarySparseLinearSVM model = SVM.fit(x, y, p, options);   // x: int[][]

// From Properties (auto-selects kernel from smile.svm.kernel)
Regression<double[]>  model = SVM.fit(x, y, props);
```

**`SVM.Options` record.**

| Parameter | Default | Description                                                               |
|-----------|---------|---------------------------------------------------------------------------|
| `eps` | `1.0` | Epsilon tube half-width. Smaller = tighter fit.                           |
| `C` | `1.0` | Soft margin penalty. Larger = lower training error, less regularization. |
| `tol` | `1E-3` | SMO convergence tolerance.                                                |

```java
SVM.Options opts = new SVM.Options(0.5, 10.0);         // eps=0.5, C=10
SVM.Options opts = new SVM.Options(0.5, 10.0, 1E-4);  // explicit tolerance

// Round-trip via Properties
Properties props = opts.toProperties();
SVM.Options restored = SVM.Options.of(props);
```

**Kernel selection.**

| Kernel | Class | Typical use |
|--------|-------|------------|
| Linear | `LinearKernel` | High-dimensional sparse / text data |
| Gaussian (RBF) | `GaussianKernel(sigma)` | General nonlinear; most popular |
| Polynomial | `PolynomialKernel(d)` | Polynomial-shaped responses |
| Laplacian | `LaplacianKernel(sigma)` | Robust to outliers |

```java
// Nonlinear SVR with a Gaussian kernel
KernelMachine<double[]> model = SVM.fit(x, y,
        new GaussianKernel(5.0),
        new SVM.Options(1.5, 100.0));

double yhat = model.predict(sample);
```

**Properties-driven workflow:**

```java
Properties props = new Properties();
props.setProperty("smile.svm.kernel",    "rbf");
props.setProperty("smile.svm.epsilon",   "0.5");
props.setProperty("smile.svm.C",         "100");
props.setProperty("smile.svm.tolerance", "1E-3");
Regression<double[]> model = SVM.fit(x, y, props);
```

### 6.2 `LinearSVM`, `SparseLinearSVM`, `BinarySparseLinearSVM`

Thin wrappers around a trained `LinearKernelMachine` that expose the weight vector and
intercept. All three return the **continuous** regression score — not a classification
label.

| Class | Input type | Use case |
|-------|-----------|---------|
| `LinearSVM` | `double[]` | Dense linear SVR |
| `SparseLinearSVM` | `SparseArray` | Sparse feature vectors (NLP) |
| `BinarySparseLinearSVM` | `int[]` | Binary (0/1) sparse features |

```java
LinearSVM model = (LinearSVM) SVM.fit(x, y, new SVM.Options(1.0, 10.0));

double[] w = model.weights();    // linear coefficient vector
double   b = model.intercept();  // bias term
double   y = model.predict(x);  // w·x + b (continuous regression output)
```

### 6.3 `KernelMachine`

A wrapper implementing `Regression<T>` around `smile.model.svm.KernelMachine<T>`, used
as the return type for nonlinear SVR. `predict(x)` computes the kernel expansion:

```
ŷ = Σᵢ αᵢ · K(xᵢ, x) + b
```

---

## 7. Gaussian Process Regression

Gaussian Process Regression (GPR) provides Bayesian inference over functions: given
training data it produces both a predictive mean and a predictive variance (uncertainty
estimate). Predictions are exact (cubic time and quadratic space in `n`), or approximate
via Subset of Regressors (SR) / Nyström for large datasets.

```java
// Exact GP (n < ~5000)
GaussianProcessRegression<double[]> model =
        GaussianProcessRegression.fit(x, y, kernel, lambda);

// Exact with kernel hyperparameter optimization (BFGS)
GaussianProcessRegression<double[]> model =
        GaussianProcessRegression.fit(x, y, kernel, lambda, true);

// Subset of Regressors (SR) approximation
double[][] inducing = ...;  // m << n inducing points
GaussianProcessRegression<double[]> model =
        GaussianProcessRegression.fit(x, y, inducing, kernel, lambda);

// Nyström approximation
GaussianProcessRegression<double[]> model =
        GaussianProcessRegression.nystrom(x, y, inducing, kernel, lambda);
```

**Parameters.**

| Parameter | Description                                                                    |
|-----------|--------------------------------------------------------------------------------|
| `kernel` | A `MercerKernel<T>` (e.g., `GaussianKernel`, `MaternKernel`).                  |
| `lambda` | Noise variance σ² (regularization). Must be > 0.                              |
| `optimize` | If `true`, optimise kernel hyperparameters via marginal log-likelihood (BFGS). |

**Prediction with uncertainty:**

```java
// Predictive mean only
double yhat = model.predict(query);

// Predictive mean + variance/std
double[] estimation = new double[2];  // [0] = mean, [1] = std dev
model.predict(query, estimation);
double mean    = estimation[0];
double stddev  = estimation[1];

// 95% prediction interval (assuming Gaussian posterior)
double lower = mean - 1.96 * stddev;
double upper = mean + 1.96 * stddev;
```

**Joint predictive distribution** over multiple test points:

```java
MultivariateGaussianDistribution joint = model.predict(queries);
double[] means = joint.mean();
DenseMatrix cov = joint.cov();
```

**Scaling.** Exact GP is `O(n³)` to train and `O(n)` to predict. For `n > 10,000`, use
SR or Nyström approximations. SR predictions are still exact functions of the inducing
set; Nyström may produce negative variance — SR is generally preferred.

**Kernel hyperparameter tuning.** Pass `optimize = true` or explicitly call
`model.fit(kernel)` with updated parameters:

```java
GaussianProcessRegression<double[]> model =
        GaussianProcessRegression.fit(x, y, new GaussianKernel(1.0), 0.01, true);
// The kernel parameters are automatically optimised during fit.
```

---

## 8. Neural Network and RBF

### 8.1 `MLP` — Multilayer Perceptron

A fully connected feed-forward network trained by mini-batch stochastic back-propagation.
Regression output is a single linear neuron (no activation on the output layer).

```java
MLP model = MLP.fit(x, y,
        new Layer.ReLU(128),
        new Layer.ReLU(64)
);

// With optional output scaler (rescales targets for better training)
MLP model = MLP.fit(x, y, scaler,
        new Layer.ReLU(128),
        new Layer.ReLU(64)
);

// From Properties
MLP model = MLP.fit(x, y, props);
```

**Layer builders** (from `smile.model.mlp`):

| Type | Description                                                 |
|------|-------------------------------------------------------------|
| `Layer.ReLU(units)` | Rectified linear — most common for regression hidden layers |
| `Layer.Sigmoid(units)` | Logistic sigmoid                                            |
| `Layer.Tanh(units)` | Hyperbolic tangent                                          |
| `Layer.Mish(units)` | Smooth ReLU variant                                         |
| `Layer.SELU(units)` | Self-normalizing; works well without batch norm            |

**Options** (via `Properties`)

| Property | Default | Description                |
|----------|---------|----------------------------|
| `smile.mlp.learning.rate` | `0.01` | SGD learning rate.         |
| `smile.mlp.momentum` | `0.0` | Momentum coefficient.      |
| `smile.mlp.weight.decay` | `0.0` | L2 weight regularization. |
| `smile.mlp.epochs` | `10` | Number of training epochs. |
| `smile.mlp.mini.batch` | `32` | Mini-batch size.           |

**Online learning:**

```java
model.update(sample, target);           // single-sample SGD step
model.update(batchX, batchY);           // mini-batch step
```

### 8.2 `RBFNetwork` — Radial Basis Function Network

A two-layer network: the first layer computes RBF activations from a set of centres
(typically found by k-means), the second layer is a linear least-squares fit.

```java
// Automatic centres from k-means
RBFNetwork<double[]> model = RBFNetwork.fit(x, y, rbf);

// Pre-computed centres
RBFNetwork<double[]> model = RBFNetwork.fit(x, y, centers, rbf, normalized);
```

- `rbf`: array of `RadialBasisFunction` objects (one per centre). Common choice:
  `GaussianRadialBasis(sigma)`.
- `normalized`: if `true`, activations are normalized to sum to 1 (partition-of-unity
  property; often improves extrapolation).

```java
double yhat = model.predict(sample);
```

`RBFNetwork` does not support online updates.

---

## 9. Algorithm Comparison

| Algorithm | Input | Online | DataFrame | Notes |
|-----------|-------|--------|-----------|-------|
| OLS | `double[]` via Formula | ✓ (RLS) | ✓ | Full statistical diagnostics; exact solution |
| RidgeRegression | `double[]` / Formula | ✓ (RLS) | ✓ | L2 penalty; handles multicollinearity |
| LASSO | `double[]` / Formula | ✗ | ✓ | L1 penalty; sparse coefficients |
| ElasticNet | `double[]` / Formula | ✗ | ✓ | L1+L2 penalty; grouped sparsity |
| GLM | `Tuple` via Formula | ✗ | ✓ | Non-Gaussian families; IWLS |
| GAM | `Tuple` via Formula | ✗ | ✓ | Non-parametric smooth terms |
| RegressionTree | `Tuple` via Formula | ✗ | ✓ | Interpretable; baseline only |
| RandomForest | `Tuple` via Formula | ✗ | ✓ | Best general-purpose; OOB error |
| GradientTreeBoost | `Tuple` via Formula | ✗ | ✓ | Highest accuracy; robust losses |
| SVM (linear) | `double[]` | ✗ | ✗ | Margin-based; ε-insensitive |
| SVM (kernel) | `T` (generic) | ✗ | ✗ | Nonlinear kernel; support vectors |
| SparseLinearSVM | `SparseArray` | ✗ | ✗ | Sparse high-dim data |
| BinarySparseLinearSVM | `int[]` | ✗ | ✗ | Binary feature vectors |
| GaussianProcessRegression | `T` (generic) | ✗ | ✗ | Uncertainty estimates; O(n³) |
| MLP | `double[]` | ✓ | ✗ | Deep learning; highly expressive |
| RBFNetwork | `T` (generic) | ✗ | ✗ | Two-layer; k-means centres |

---

## 10. Common Patterns and Tips

### Serialization

All models implement `java.io.Serializable`. Use `smile.io.Read` and `smile.io.Write`:

```java
import smile.io.Write;
import smile.io.Read;

Write.object(model, Path.of("model.ser"));
Regression<?> loaded = (Regression<?>) Read.object(Path.of("model.ser"));
```

### Configuration via Properties

Every algorithm with tunable hyperparameters exposes a `fit(…, Properties)` overload
and a nested `Options` record with `toProperties()` / `of(Properties)` for serialization:

```java
// Save configuration
Properties props = new SVM.Options(0.5, 10.0).toProperties();
try (FileWriter fw = new FileWriter("svm.properties")) { props.store(fw, null); }

// Restore and train
Properties loaded = new Properties();
try (FileReader fr = new FileReader("svm.properties")) { loaded.load(fr); }
Regression<double[]> model = SVM.fit(x, y, loaded);
```

### Reproducibility

Random sampling (random forests, GBT subsampling, k-means RBF centres) depends on the
global RNG. Seed it before training:

```java
smile.math.MathEx.setSeed(12345);
RandomForest model = RandomForest.fit(formula, data);
```

### Online (Incremental) Learning

Only `OLS` (via RLS), `RidgeRegression` (via RLS when `V` is set), and `MLP` support
online updates. Always check before calling:

```java
if (model.online()) {
    model.update(newX, newY);
}
```

### Feature Scaling

Scale-invariant models (trees, random forests, GBT) work well without normalization.
Scale-sensitive models require it:

| Scale-insensitive | Scale-sensitive (normalize first)                      |
|-------------------|---------------------------------------------------------|
| `RegressionTree`, `RandomForest`, `GradientTreeBoost` | `OLS`, `RidgeRegression`, `LASSO`, `ElasticNet`         |
| — | `SVM`, `GaussianProcessRegression`, `RBFNetwork`, `MLP` |

```java
// Standardize columns in-place before fitting SVM
MathEx.standardize(x);
Regression<double[]> model = SVM.fit(x, y, new SVM.Options(1.0, 10.0));
```

### Choosing between linear regularisation methods

| Situation | Recommendation |
|-----------|----------------|
| All predictors relevant, multicollinearity present | `RidgeRegression` |
| Sparse signal — most predictors irrelevant | `LASSO` |
| Correlated predictor groups, sparse overall | `ElasticNet` |
| n < p (under-determined system) | `LASSO` or `ElasticNet` |

### Choosing between ensemble methods

| Situation | Recommendation |
|-----------|----------------|
| Fast baseline, interpretable rules | `RegressionTree` |
| Balanced accuracy + speed + OOB estimate | `RandomForest` |
| Highest accuracy, can tune | `GradientTreeBoost` |
| Robustness to outliers | `GradientTreeBoost` with `Huber` or `LeastAbsoluteDeviation` loss |

### GLM family selection

| Response type | Family | Link |
|---------------|--------|------|
| Continuous, additive noise | Gaussian | identity |
| Positive continuous, multiplicative noise | Gaussian | log |
| Proportions or rates | Gaussian | inverse |
| Binary 0/1 | Bernoulli | logit |
| Grouped binary (k successes out of n) | Binomial | logit |
| Non-negative integer counts | Poisson | log |

### Gaussian Process: when to use exact vs approximate

| n | Recommendation                                           |
|---|----------------------------------------------------------|
| n ≤ 1,000 | Exact GP; optimise kernel hyperparameters                |
| 1,000 < n ≤ 10,000 | Exact GP without hyperparameter optimization            |
| n > 10,000 | Subset of Regressors (SR) approximation; choose `m ≈ √n` |
| n > 100,000 | Consider switching to GBT or MLP                         |

### Inspecting GLM and OLS models programmatically

```java
// Coefficient p-values (identify significant predictors)
LinearModel ols = OLS.fit(formula, data);
double[][] ttest = ols.ttest();
for (int i = 0; i < ttest.length; i++) {
    if (ttest[i][3] < 0.05) {
        System.out.printf("%-20s coef=%+.4f  p=%.4f%n",
                ols.schema().field(i).name(), ttest[i][0], ttest[i][3]);
    }
}

// GLM coefficient interpretation (logit scale for Bernoulli/Binomial)
GLM glm = GLM.fit(formula, data, Bernoulli.logit());
double[][] ztest = glm.ztest();
for (int i = 0; i < ztest.length; i++) {
    double logOddsRatio = ztest[i][0];
    double oddsRatio    = Math.exp(logOddsRatio);
    System.out.printf("OR=%.3f  p=%.4f%n", oddsRatio, ztest[i][3]);
}
```

### Memory and scale considerations

| Concern | Guidance |
|---------|---------|
| `n > 1M` rows | `RandomForest`, `GradientTreeBoost`; avoid exact GP. |
| `p > n` (wide data) | `LASSO`, `ElasticNet`, `SparseLinearSVM`. |
| Streaming / incremental | `MLP` (online), `OLS` with RLS option. |
| Sparse high-dimensional text | `SparseLinearSVM`, `BinarySparseLinearSVM`. |
| Uncertainty quantification | `GaussianProcessRegression` (exact or SR). |
| Interpretable model | `OLS`, `RidgeRegression`, `LASSO`, `RegressionTree`. |

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
