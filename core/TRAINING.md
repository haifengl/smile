# SMILE — Training Models with `smile.model.Model`

`smile.model.Model` is a unified training interface that lets you fit, evaluate,
and manage any SMILE classification or regression algorithm through a single,
consistent API.  You supply an algorithm name, a formula that identifies the
response column, a training `DataFrame`, and a `Properties` bag of
hyperparameters.  The result is a `ClassificationModel` or `RegressionModel`
record that bundles the trained predictor together with training metrics,
optional cross-validation metrics, optional held-out test metrics, and a
metadata tag store.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Core Concepts](#core-concepts)
3. [Training a Classification Model](#training-a-classification-model)
4. [Training a Regression Model](#training-a-regression-model)
5. [Cross-Validation and Ensembles](#cross-validation-and-ensembles)
6. [Evaluating Models](#evaluating-models)
7. [Making Predictions](#making-predictions)
8. [Model Metadata Tags](#model-metadata-tags)
9. [Classification Algorithm Reference](#classification-algorithm-reference)
10. [Regression Algorithm Reference](#regression-algorithm-reference)
11. [Kernel Specification Strings](#kernel-specification-strings)
12. [Serialization](#serialization)
13. [Complete Examples](#complete-examples)

---

## Quick Start

```java
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.model.ClassificationModel;
import smile.model.Model;
import smile.model.RegressionModel;
import java.util.Properties;

// --- Classification ---
var formula  = Formula.lhs("label");        // "label" is the response column
var params   = new Properties();
params.setProperty("smile.random_forest.trees", "200");

ClassificationModel clf = Model.classification(
        "random-forest", formula, trainData, testData, params);

System.out.println(clf.train());            // training metrics
System.out.println(clf.test());             // test-set metrics
int label = clf.predict(row);               // single-row inference

// --- Regression ---
RegressionModel reg = Model.regression(
        "ols", Formula.lhs("price"), trainData, testData, new Properties());

System.out.println(reg.train());
double prediction = reg.predict(row);
```

---

## Core Concepts

### The `Model` interface

`Model` is a thin interface satisfied by both `ClassificationModel` and
`RegressionModel`.  It exposes four read-only accessor methods:

| Method | Returns |
|--------|---------|
| `algorithm()` | The algorithm name string (e.g. `"random-forest"`) |
| `schema()` | The input feature schema — **without** the response column |
| `formula()` | The `Formula` that was used to train the model |
| `tags()` | The `Properties` metadata bag (mutable via `setTag`) |

### `Formula`

A `Formula` identifies which column is the response variable and which columns
are predictors.  The simplest form is:

```java
Formula.lhs("y")        // predict column "y", use all remaining columns
```

Use `smile.data.formula.Formula` for more complex specifications such as
interactions or column exclusions.

### `Properties` hyperparameters

All algorithm hyperparameters are passed as a `java.util.Properties` object.
Each algorithm reads its own namespaced keys; unknown keys are silently ignored.
The `Properties` object is **cloned** when the model is created, so mutating
the original object after training does not affect the stored model.

---

## Training a Classification Model

### Without a held-out test set

```java
ClassificationModel model = Model.classification(
        algorithm, formula, trainData, null, params);
```

`model.test()` will be `null`.

### With a held-out test set

```java
ClassificationModel model = Model.classification(
        algorithm, formula, trainData, testData, params);
```

`model.test()` will contain accuracy, error count, F1, AUC, etc.

### Full overload (with cross-validation)

```java
ClassificationModel model = Model.classification(
        algorithm, formula, trainData, testData, params,
        kfold,    // number of CV folds; set < 2 to skip CV
        round,    // number of repeated CV rounds
        ensemble  // true = ensemble fold models; false = retrain on full data
);
```

See [Cross-Validation and Ensembles](#cross-validation-and-ensembles) for details.

---

## Training a Regression Model

The regression API mirrors the classification API exactly:

```java
// No test set
RegressionModel model = Model.regression(algorithm, formula, trainData, null, params);

// With test set
RegressionModel model = Model.regression(algorithm, formula, trainData, testData, params);

// With cross-validation
RegressionModel model = Model.regression(
        algorithm, formula, trainData, testData, params, kfold, round, ensemble);
```

---

## Cross-Validation and Ensembles

Pass `kfold >= 2` to enable cross-validation.  The training loop runs
`round × kfold` folds; `model.validation()` returns the **averaged** CV
metrics across all rounds.

```java
Properties params = new Properties();
params.setProperty("smile.svm.kernel", "Gaussian(6.4)");
params.setProperty("smile.svm.C", "100");

ClassificationModel model = Model.classification(
        "svm", formula, trainData, testData, params,
        5,      // 5-fold CV
        3,      // repeated 3 times → 15 folds total
        true    // combine fold models into an ensemble
);

System.out.println("CV metrics:   " + model.validation());
System.out.println("Test metrics: " + model.test());
```

### `ensemble` flag

| Value | Final model is… | `validation()` |
|-------|----------------|----------------|
| `false` | Retrained fresh on the **full** training set | Non-null |
| `true` | A **soft-vote ensemble** of the `round × kfold` fold models | Non-null |

When `kfold < 2`, the `ensemble` flag has no effect and `validation()` returns
`null`.

### Choosing `kfold` and `round`

| Situation | Recommendation |
|-----------|---------------|
| Quick estimate on small data | `kfold=5`, `round=1` |
| Stable estimate, moderate data | `kfold=5`, `round=3` |
| Publication-quality evaluation | `kfold=10`, `round=5` |
| Very small dataset | `kfold=n` (leave-one-out) |

---

## Evaluating Models

Every `ClassificationModel` and `RegressionModel` exposes three metric slots:

```java
model.train()       // always non-null; measured on the training set
model.validation()  // non-null only when kfold >= 2 was requested
model.test()        // non-null only when a test DataFrame was supplied
```

### Classification metrics

`ClassificationMetrics` prints a human-readable summary that includes:

- **Accuracy** and **error count**
- **Precision / Recall / F1** (macro and per-class)
- **AUC** (for binary problems)
- **Fit time** (milliseconds, only on `train()`)

```java
ClassificationMetrics m = model.test();
System.out.printf("Accuracy: %.1f%%%n", 100.0 * m.accuracy());
System.out.printf("Errors:   %d%n",     m.error());
```

### Regression metrics

`RegressionMetrics` includes:

- **RMSE** (root mean squared error)
- **MAE** (mean absolute error)
- **R²** (coefficient of determination)
- **Fit time** (milliseconds, only on `train()`)

```java
RegressionMetrics m = model.test();
System.out.printf("R²:   %.3f%n", m.r2());
System.out.printf("RMSE: %.3f%n", m.rmse());
```

---

## Making Predictions

### Single-row inference

Both model types expose a `predict(Tuple x)` method that accepts a single data
row:

```java
// Classification — returns the predicted class index
int label = clf.predict(row);

// Classification with posterior probabilities
double[] posterior = new double[clf.numClasses()];
int label = clf.predict(row, posterior);
// posterior[i] = P(class i | row)

// Regression — returns the predicted value
double value = reg.predict(row);
```

### Batch inference

Access the underlying predictor for batch prediction:

```java
// Classification
DataFrameClassifier classifier = clf.classifier();
int[] labels = classifier.predict(batchDataFrame);

// Regression
DataFrameRegression regressor = reg.regression();
double[] values = regressor.predict(batchDataFrame);
```

### Getting a row from a DataFrame

```java
Tuple row = dataFrame.get(0);   // first row
```

---

## Model Metadata Tags

Every model carries a `Properties`-backed tag store that survives serialization.
Use it to record provenance, version, deployment environment, or any other
string metadata.

```java
model.setTag(Model.ID,      "iris-classifier-v2");
model.setTag(Model.VERSION, "2.1.0");
model.setTag("trained_by",  "pipeline-ci-42");
model.setTag("dataset",     "iris-1.2");
```

Reading tags:

```java
String id      = model.getTag(Model.ID);
String version = model.getTag(Model.VERSION);
String env     = model.getTag("env", "production");  // second arg is default
```

Built-in tag key constants:

| Constant | String value | Intended use |
|----------|-------------|--------------|
| `Model.ID` | `"id"` | Unique model identifier |
| `Model.VERSION` | `"version"` | Version string |

Tags are stored on a **cloned** copy of the training `Properties`, so the
hyperparameter keys used during training are also available under `model.tags()`
after training.

---

## Classification Algorithm Reference

Pass the algorithm name as the first argument to `Model.classification(...)`.

### `"random-forest"`

Random Forest classifier using bootstrap aggregation over decision trees.

| Property key | Default | Description |
|---|---|---|
| `smile.random_forest.trees` | `500` | Number of trees |
| `smile.random_forest.mtry` | `0` | Features per split (`0` = √p) |
| `smile.random_forest.split_rule` | `GINI` | Split criterion: `GINI`, `ENTROPY`, `CLASSIFICATION_ERROR` |
| `smile.random_forest.max_depth` | `20` | Maximum tree depth |
| `smile.random_forest.max_nodes` | `0` | Maximum leaf nodes (`0` = unlimited) |
| `smile.random_forest.node_size` | `5` | Minimum samples per leaf |
| `smile.random_forest.sampling_rate` | `1.0` | Fraction of rows sampled per tree |
| `smile.random_forest.class_weight` | *(none)* | Per-class integer weights, e.g. `"1,2"` |

```java
params.setProperty("smile.random_forest.trees",     "200");
params.setProperty("smile.random_forest.max_nodes", "100");
params.setProperty("smile.random_forest.split_rule","ENTROPY");
```

### `"gradient-boost"`

Gradient Boosted Trees (multi-class via one-vs-rest).

| Property key | Default | Description |
|---|---|---|
| `smile.gradient_boost.trees` | `500` | Number of boosting rounds |
| `smile.gradient_boost.max_depth` | `20` | Max depth of base trees |
| `smile.gradient_boost.max_nodes` | `6` | Max nodes per base tree |
| `smile.gradient_boost.node_size` | `5` | Min samples per leaf |
| `smile.gradient_boost.shrinkage` | `0.05` | Learning rate (shrinkage) |
| `smile.gradient_boost.sampling_rate` | `0.7` | Row subsample rate per round |

```java
params.setProperty("smile.gradient_boost.trees",     "300");
params.setProperty("smile.gradient_boost.shrinkage", "0.1");
```

### `"ada-boost"`

AdaBoost with shallow decision stumps.

| Property key | Default | Description |
|---|---|---|
| `smile.adaboost.trees` | `500` | Number of weak learners |
| `smile.adaboost.max_depth` | `20` | Max depth of each weak learner |
| `smile.adaboost.max_nodes` | `6` | Max nodes per weak learner |
| `smile.adaboost.node_size` | `5` | Min samples per leaf |

### `"cart"`

Single unpruned decision tree (CART).

| Property key | Default | Description |
|---|---|---|
| `smile.cart.split_rule` | `GINI` | Split criterion: `GINI`, `ENTROPY`, `CLASSIFICATION_ERROR` |
| `smile.cart.max_depth` | `20` | Maximum depth |
| `smile.cart.max_nodes` | `0` | Maximum leaves (`0` = unlimited) |
| `smile.cart.node_size` | `5` | Minimum samples per leaf |

### `"logistic"`

Multinomial logistic regression with L2 regularization, solved via BFGS.

| Property key | Default | Description |
|---|---|---|
| `smile.logistic.lambda` | `0.1` | L2 regularization strength |
| `smile.logistic.tolerance` | `1E-5` | Convergence tolerance |
| `smile.logistic.iterations` | `500` | Maximum iterations |

### `"fisher"`

Fisher's Linear Discriminant Analysis. No tunable hyperparameter keys; pass an
empty `Properties`.

### `"lda"`

Linear Discriminant Analysis with equal covariance assumption. No tunable keys.

### `"qda"`

Quadratic Discriminant Analysis with per-class covariances. No tunable keys.

### `"rda"`

Regularized Discriminant Analysis — interpolates between LDA and QDA.

| Property key | Default | Description |
|---|---|---|
| `smile.rda.alpha` | `0.9` | Mixing coefficient (0 = LDA, 1 = QDA) |
| `smile.rda.priori` | *(estimated)* | Class prior probabilities, e.g. `"0.3,0.7"` |
| `smile.rda.tolerance` | `1E-4` | Minimum eigenvalue threshold |

### `"mlp"`

Multilayer Perceptron classifier.

| Property key | Default | Description |
|---|---|---|
| `smile.mlp.layers` | `ReLU(100)` | Hidden layer spec, e.g. `"Sigmoid(50)"`, `"ReLU(128)\|Sigmoid(64)"` |
| `smile.mlp.epochs` | `100` | Training epochs |
| `smile.mlp.mini_batch` | `32` | Mini-batch size |
| `smile.mlp.learning_rate` | *(unchanged)* | Learning rate, e.g. `"0.01"` |
| `smile.mlp.weight_decay` | *(unchanged)* | L2 weight decay |
| `smile.mlp.momentum` | *(unchanged)* | Momentum schedule |
| `smile.mlp.clip_value` | *(unchanged)* | Gradient clipping by value |
| `smile.mlp.clip_norm` | *(unchanged)* | Gradient clipping by norm |
| `smile.mlp.RMSProp.rho` | *(disabled)* | RMSProp decay rate; setting this enables RMSProp |
| `smile.mlp.RMSProp.epsilon` | `1E-7` | RMSProp stability constant (used when `rho` is set) |

```java
params.setProperty("smile.mlp.layers",         "ReLU(256)|Sigmoid(128)");
params.setProperty("smile.mlp.epochs",         "50");
params.setProperty("smile.mlp.mini_batch",     "64");
params.setProperty("smile.mlp.learning_rate",  "0.001");
params.setProperty("smile.mlp.RMSProp.rho",    "0.9");
```

### `"svm"`

Support Vector Machine classifier using LASVM.  For two-class problems the
default strategy is binary SVM; for multi-class it defaults to one-vs-rest.

| Property key | Default | Description |
|---|---|---|
| `smile.svm.kernel` | `linear` | Kernel string (see [Kernel Specification Strings](#kernel-specification-strings)) |
| `smile.svm.C` | `1.0` | Soft-margin penalty |
| `smile.svm.type` | `binary` / `ovr` | Multiclass strategy: `binary`, `ovr`, `ovo` |
| `smile.svm.tolerance` | `1E-3` | Solver convergence tolerance |
| `smile.svm.epochs` | `1` | Training passes over the data |

```java
params.setProperty("smile.svm.kernel", "Gaussian(6.4)");
params.setProperty("smile.svm.C",      "100");
params.setProperty("smile.svm.type",   "ovo");
```

### `"rbf"`

Radial Basis Function Network classifier.

No standard property keys are documented.  Pass an empty `Properties` to use
the default network configuration, or refer to `RBFNetwork.Options` for any
available keys.

---

## Regression Algorithm Reference

Pass the algorithm name as the first argument to `Model.regression(...)`.

### `"random-forest"`

Random Forest regressor.

| Property key | Default | Description |
|---|---|---|
| `smile.random_forest.trees` | `500` | Number of trees |
| `smile.random_forest.mtry` | `0` | Features per split (`0` = p/3) |
| `smile.random_forest.max_depth` | `20` | Maximum depth |
| `smile.random_forest.max_nodes` | `0` | Maximum leaves |
| `smile.random_forest.node_size` | `5` | Minimum samples per leaf |
| `smile.random_forest.sampling_rate` | `1.0` | Row subsample rate |

### `"gradient-boost"`

Gradient Boosted Trees regressor.

| Property key | Default | Description |
|---|---|---|
| `smile.gradient_boost.loss` | `LeastAbsoluteDeviation` | Loss function: `LeastSquares`, `LeastAbsoluteDeviation`, `Huber` |
| `smile.gradient_boost.trees` | `500` | Number of trees |
| `smile.gradient_boost.max_depth` | `20` | Max depth |
| `smile.gradient_boost.max_nodes` | `6` | Max nodes |
| `smile.gradient_boost.node_size` | `5` | Min leaf size |
| `smile.gradient_boost.shrinkage` | `0.05` | Learning rate |
| `smile.gradient_boost.sampling_rate` | `0.7` | Subsample rate |

```java
params.setProperty("smile.gradient_boost.loss",     "Huber");
params.setProperty("smile.gradient_boost.trees",    "300");
params.setProperty("smile.gradient_boost.shrinkage","0.1");
```

### `"cart"`

Single regression tree (CART).

| Property key | Default | Description |
|---|---|---|
| `smile.cart.max_depth` | `20` | Maximum depth |
| `smile.cart.max_nodes` | `0` | Maximum leaves |
| `smile.cart.node_size` | `5` | Minimum samples per leaf |

### `"ols"`

Ordinary Least Squares linear regression.

| Property key | Default | Description |
|---|---|---|
| `smile.ols.method` | `QR` | Solver method: `QR`, `SVD`, `Cholesky` |
| `smile.ols.standard_error` | `true` | Compute standard errors and p-values |
| `smile.ols.recursive` | `true` | Use recursive residuals |

### `"lasso"`

LASSO regression (L1 penalized).

| Property key | Default | Description |
|---|---|---|
| `smile.lasso.lambda` | `1` | L1 regularization strength |
| `smile.lasso.tolerance` | `1E-4` | Convergence tolerance |
| `smile.lasso.iterations` | `1000` | Maximum iterations |

### `"elastic-net"`

Elastic Net regression (L1 + L2 penalized).

**Both `lambda1` and `lambda2` must be supplied — they have no defaults.**

| Property key | Default | Description |
|---|---|---|
| `smile.elastic_net.lambda1` | **required** | L1 (LASSO) penalty |
| `smile.elastic_net.lambda2` | **required** | L2 (ridge) penalty |
| `smile.elastic_net.tolerance` | `1E-4` | Convergence tolerance |
| `smile.elastic_net.iterations` | `1000` | Maximum iterations |

```java
params.setProperty("smile.elastic_net.lambda1", "0.1");
params.setProperty("smile.elastic_net.lambda2", "0.5");
```

### `"ridge"`

Ridge regression (L2 penalized).

| Property key | Default | Description |
|---|---|---|
| `smile.ridge.lambda` | `1` | Ridge penalty strength |
| `smile.ridge.beta0` | `0` | Intercept offset |

### `"gaussian-process"`

Gaussian Process regression.

| Property key | Default | Description |
|---|---|---|
| `smile.gaussian_process.kernel` | `linear` | Kernel string (see [Kernel Specification Strings](#kernel-specification-strings)) |
| `smile.gaussian_process.noise` | `1E-10` | Observation noise / numerical jitter |
| `smile.gaussian_process.normalize` | `true` | Normalize inputs and targets |
| `smile.gaussian_process.tolerance` | `1E-5` | Numerical tolerance |
| `smile.gaussian_process.iterations` | `0` | Max hyperparameter tuning iterations |

### `"mlp"`

Multilayer Perceptron regressor.

Uses the same `smile.mlp.*` property keys as the classification MLP, plus:

| Property key | Default | Description |
|---|---|---|
| `smile.mlp.scaler` | *(none)* | Output scaling for the target variable |
| `smile.mlp.layers` | `ReLU(100)` | Hidden layer architecture |
| `smile.mlp.epochs` | `100` | Training epochs |
| `smile.mlp.mini_batch` | `32` | Mini-batch size |
| `smile.mlp.learning_rate` | *(unchanged)* | Learning rate |
| `smile.mlp.RMSProp.rho` | *(disabled)* | Enables RMSProp when set |

```java
params.setProperty("smile.mlp.activation",     "ReLU(50)|Sigmoid(30)");
params.setProperty("smile.mlp.epochs",         "30");
params.setProperty("smile.mlp.learning_rate",  "0.2");
```

### `"svm"`

Support Vector Regression (ε-SVR).

| Property key | Default | Description |
|---|---|---|
| `smile.svm.kernel` | `linear` | Kernel string |
| `smile.svm.epsilon` | `1.0` | Width of the ε-insensitive tube |
| `smile.svm.C` | `1.0` | Soft-margin penalty |
| `smile.svm.tolerance` | `1E-3` | Solver tolerance |

```java
params.setProperty("smile.svm.kernel",   "Gaussian(6.0)");
params.setProperty("smile.svm.C",        "5");
params.setProperty("smile.svm.epsilon",  "0.5");
```

### `"rbf"`

Radial Basis Function Network regressor. Pass an empty `Properties` for
default behaviour.

---

## Kernel Specification Strings

The `"svm"` and `"gaussian-process"` algorithms both accept a
`smile.svm.kernel` / `smile.gaussian_process.kernel` property whose value is a
short string parsed by `MercerKernel.of(String)`.

| String | Kernel |
|--------|--------|
| `linear` | Linear kernel \( k(x,y) = x \cdot y \) |
| `Gaussian(sigma)` | Gaussian RBF \( \exp(-\|x-y\|^2 / (2\sigma^2)) \) |
| `Laplacian(sigma)` | Laplacian \( \exp(-\|x-y\| / \sigma) \) |
| `Polynomial(degree,scale,offset)` | Polynomial \( (\text{scale}\, x \cdot y + \text{offset})^d \) |
| `ThinPlateSpline(sigma)` | Thin-plate spline |
| `Pearson(omega,nu)` | Pearson VII |
| `Hyperbolic(scale,offset)` | Hyperbolic tangent |
| `Hellinger` | Hellinger kernel for histograms |
| `SparsLinear` | Sparse linear kernel |
| `BinarySparseLinear` | Binary sparse linear kernel |

Examples:

```java
params.setProperty("smile.svm.kernel", "Gaussian(1.0)");
params.setProperty("smile.svm.kernel", "Polynomial(3,1.0,0.0)");
params.setProperty("smile.gaussian_process.kernel", "Gaussian(2.5)");
```

---

## Serialization

Both `ClassificationModel` and `RegressionModel` implement `java.io.Serializable`.
You can persist and restore a trained model using standard Java object streams:

```java
import java.io.*;

// Save
try (var out = new ObjectOutputStream(new FileOutputStream("model.bin"))) {
    out.writeObject(model);
}

// Load
ClassificationModel loaded;
try (var in = new ObjectInputStream(new FileInputStream("model.bin"))) {
    loaded = (ClassificationModel) in.readObject();
}

// Inference on the loaded model works identically
int label = loaded.predict(row);
```

The serialized payload includes the trained predictor, all metrics, the formula,
the feature schema, and the metadata tags.

---

## Complete Examples

### Example 1 — Random Forest Classifier with a held-out test set

```java
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.datasets.ImageSegmentation;
import smile.model.ClassificationModel;
import smile.model.Model;
import java.util.Properties;

var segment = new ImageSegmentation();
var formula = segment.formula();

var params = new Properties();
params.setProperty("smile.random_forest.trees",     "200");
params.setProperty("smile.random_forest.max_nodes", "100");

ClassificationModel model = Model.classification(
        "random-forest",
        formula,
        segment.train(),
        segment.test(),
        params);

model.setTag(Model.ID, "segmentation-rf");
model.setTag(Model.VERSION, "1.0.0");

System.out.println("Train: " + model.train());
System.out.println("Test:  " + model.test());
System.out.printf("Test error: %d%n", model.test().error());
```

### Example 2 — SVM Classifier with repeated cross-validation + ensemble

```java
import smile.feature.transform.Standardizer;
import smile.model.ClassificationModel;
import smile.model.Model;
import java.util.Properties;

var segment  = new ImageSegmentation();
var scaler   = Standardizer.fit(segment.train());
var train    = scaler.apply(segment.train());
var test     = scaler.apply(segment.test());

var params   = new Properties();
params.setProperty("smile.svm.kernel", "Gaussian(6.4)");
params.setProperty("smile.svm.C",      "100");
params.setProperty("smile.svm.type",   "ovo");

ClassificationModel ensemble = Model.classification(
        "svm",
        segment.formula(),
        train,
        test,
        params,
        5,     // 5-fold
        3,     // repeated 3 times
        true   // build ensemble
);

System.out.println("CV (avg):  " + ensemble.validation());
System.out.println("Test:      " + ensemble.test());
```

### Example 3 — OLS Regression

```java
import smile.datasets.ProstateCancer;
import smile.model.Model;
import smile.model.RegressionModel;
import java.util.Properties;

var prostate = new ProstateCancer();

RegressionModel model = Model.regression(
        "ols",
        prostate.formula(),
        prostate.train(),
        prostate.test(),
        new Properties());

System.out.printf("R²:   %.3f%n", model.test().r2());
System.out.printf("RMSE: %.3f%n", model.test().rmse());

double prediction = model.predict(prostate.test().get(0));
System.out.printf("Prediction: %.3f%n", prediction);
```

### Example 4 — MLP Regressor

```java
import smile.feature.transform.WinsorScaler;
import smile.model.Model;
import smile.model.RegressionModel;
import java.util.Properties;

var prostate = new ProstateCancer();
var scaler   = WinsorScaler.fit(prostate.train(), 0.01, 0.99);
var train    = scaler.apply(prostate.train());
var test     = scaler.apply(prostate.test());

var params   = new Properties();
params.setProperty("smile.mlp.layers",        "ReLU(64)|Sigmoid(32)");
params.setProperty("smile.mlp.epochs",        "50");
params.setProperty("smile.mlp.learning_rate", "0.01");
params.setProperty("smile.mlp.RMSProp.rho",   "0.9");

RegressionModel model = Model.regression(
        "mlp", prostate.formula(), train, test, params);

System.out.println(model.test());
```

### Example 5 — Soft Posterior Probabilities

```java
ClassificationModel logistic = Model.classification(
        "logistic", formula, trainData, null, new Properties());

double[] posterior = new double[logistic.numClasses()];
int label = logistic.predict(row, posterior);

System.out.printf("Predicted class: %d%n", label);
for (int i = 0; i < posterior.length; i++) {
    System.out.printf("  P(class %d) = %.3f%n", i, posterior[i]);
}
```

### Example 6 — Tags for Deployment Tracking

```java
ClassificationModel model = Model.classification(
        "random-forest", formula, trainData, testData, params);

model.setTag(Model.ID,      "model-" + UUID.randomUUID());
model.setTag(Model.VERSION, "3.2.1");
model.setTag("dataset",     "iris-2026-04");
model.setTag("author",      "ml-team");
model.setTag("environment", "staging");

// Retrieve later
System.out.println(model.getTag(Model.ID));
System.out.println(model.getTag("environment", "production")); // default value
```

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
