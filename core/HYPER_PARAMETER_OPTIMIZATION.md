# SMILE — Hyperparameter Optimization

## Table of Contents

- [Overview](#overview)
- [What is a Hyperparameter?](#what-is-a-hyperparameter)
- [Getting Started](#getting-started)
- [Registering Parameters](#registering-parameters)
  - [Fixed Values](#fixed-values)
  - [Discrete Choices](#discrete-choices)
  - [Ranged Values](#ranged-values)
    - [Integer Ranges](#integer-ranges)
    - [Double Ranges](#double-ranges)
- [Search Strategies](#search-strategies)
  - [Grid Search](#grid-search)
  - [Random Search](#random-search)
  - [Choosing a Strategy](#choosing-a-strategy)
- [Managing Parameters](#managing-parameters)
- [Integration with SMILE Models](#integration-with-smile-models)
  - [Random Forest Example](#random-forest-example)
  - [Gradient Boosted Trees Example](#gradient-boosted-trees-example)
  - [Neural Network Example](#neural-network-example)
- [Cross-Validation Integration](#cross-validation-integration)
- [Best Practices](#best-practices)
- [API Reference](#api-reference)

---

## Overview

The `smile.hpo` package provides a lightweight, fluent API for **hyperparameter optimization (HPO)**.  
It lets you declare the search space for any SMILE model's configuration properties and then
enumerate that space via **grid search** (exhaustive) or **random search** (stochastic), yielding
standard `java.util.Properties` objects that can be passed directly to any SMILE `fit()` method.

Key features:

| Feature | Detail |
|---|---|
| Fluent builder | Chain `add()` calls to build the search space |
| Three value types | Discrete arrays, integer ranges, double ranges |
| Grid search | Exhaustive Cartesian product, finite `Stream<Properties>` |
| Random search | Infinite uniform-sample stream; use `.limit(n)` or `random(n)` |
| Insertion-order determinism | Parameters enumerated in registration order |
| Input validation | Null/blank names, empty arrays, and invalid ranges are caught eagerly |

---

## What is a Hyperparameter?

Machine learning models have two classes of parameters:

- **Learned parameters** — weights, split thresholds, centroids — derived automatically from data
  during `fit()`.
- **Hyperparameters** — values that *govern* the learning process and must be set *before* training
  begins.

Hyperparameters fall into two sub-categories:

| Sub-category | Examples |
|---|---|
| Model hyperparameters | Tree depth, number of trees, network topology |
| Algorithm hyperparameters | Learning rate, mini-batch size, regularization strength |

Choosing good hyperparameter values is often what separates a mediocre model from a great one.
The `smile.hpo` package automates the search.

---

## Getting Started

```java
import smile.hpo.Hyperparameters;
import smile.classification.RandomForest;
import smile.data.formula.Formula;
import smile.validation.metric.Accuracy;

// 1. Define the search space
var hp = new Hyperparameters()
        .add("smile.random.forest.trees",     100)              // fixed
        .add("smile.random.forest.mtry",      new int[]{2,3,4}) // discrete
        .add("smile.random.forest.max.nodes", 100, 500, 50);    // range

// 2. Load data
var train = Read.arff("data/weka/segment-challenge.arff");
var test  = Read.arff("data/weka/segment-test.arff");
var formula = Formula.lhs("class");
int[] testy = formula.y(test).toIntArray();

// 3. Run grid search
hp.grid().forEach(props -> {
    var model = RandomForest.fit(formula, train, props);
    int[] pred = model.predict(test);
    System.out.printf("Props: %s%n", props);
    System.out.printf("Accuracy: %.2f%%%n%n", 100.0 * Accuracy.of(testy, pred));
});
```

---

## Registering Parameters

All registration is done through overloaded `add(name, ...)` methods that return `this`,
enabling a fluent builder chain.

### Fixed Values

Use a fixed value when you want a parameter to be constant across all configurations.
Internally it is stored as a single-element array; the parameter always appears in the
generated `Properties`.

```java
var hp = new Hyperparameters()
        .add("smile.random.forest.trees", 200)      // int
        .add("smile.svm.C",               1.0)      // double
        .add("smile.svm.kernel",          "linear"); // String
```

### Discrete Choices

Provide an explicit array of candidate values when the search space is enumerable.

```java
var hp = new Hyperparameters()
        .add("smile.random.forest.mtry",     new int[]   {2, 3, 4, 5})
        .add("smile.svm.C",                  new double[]{0.1, 1.0, 10.0, 100.0})
        .add("smile.decision.tree.split",    new String[]{"GINI", "ENTROPY"});
```

Empty arrays are rejected immediately with `IllegalArgumentException`.

### Ranged Values

#### Integer Ranges

```java
// Auto-step: step = max(1, (end-start)/10)
hp.add("smile.random.forest.max.nodes", 50, 500);

// Explicit step: values 100, 150, 200, 250, 300
hp.add("smile.random.forest.max.nodes", 100, 300, 50);
```

`toArray()` generates `start, start+step, start+2*step, …` using integer multiplication
(no accumulation error). The last value is the largest multiple of `step` from `start`
that does **not exceed** `end`.

| Call | Generated values |
|---|---|
| `add("k", 0, 10, 3)` | `0, 3, 6, 9` |
| `add("k", 0, 12, 5)` | `0, 5, 10` |
| `add("k", 10, 50)`   | `10, 14, 18, …, 46` (auto step = 4) |

#### Double Ranges

```java
// Auto-step: step = (end-start)/10
hp.add("smile.svm.C", 0.01, 100.0);

// Explicit step: values 0.1, 0.2, 0.3, 0.4, 0.5
hp.add("smile.logistic.lambda", 0.1, 0.5, 0.1);
```

Values are computed as `start + i * step` (multiplication avoids floating-point
accumulation drift) and clamped to `end` so no value ever exceeds the stated bound.

| Call | Generated values |
|---|---|
| `add("lr", 0.0, 1.0, 0.25)` | `0.0, 0.25, 0.50, 0.75, 1.0` |
| `add("lr", 0.0, 1.0, 0.3)`  | `0.0, 0.3, 0.6, 0.9` |

> **Note — range semantics vs. random search**: The `step` field affects only
> `grid()`. In `random()`, ranged parameters are sampled uniformly from
> `[start, end)` regardless of step.

---

## Search Strategies

### Grid Search

```java
Stream<Properties> stream = hp.grid();
```

`grid()` returns a **finite** `Stream<Properties>` whose size is the **Cartesian product**
of all registered parameter value lists.

```
3 values for mtry  ×  5 values for max.nodes  =  15 configurations
```

All configurations are materialised up-front. Be mindful of combinatorial explosion — adding
even a few parameters with many values can produce millions of configurations.

```java
// Count configurations before running
long total = hp.grid().count(); // terminal operation — re-call hp.grid() to iterate
System.out.println("Configurations to evaluate: " + total);

hp.grid().forEach(props -> {
    var model = RandomForest.fit(formula, train, props);
    // evaluate ...
});
```

Parameters appear in **insertion order** within each `Properties` object, and the stream
iterates with the last-registered parameter varying fastest (rightmost index increments first).

### Random Search

```java
// Infinite stream — always use limit() or random(n)
hp.random().limit(50).forEach(props -> { ... });

// Convenience form
hp.random(50).forEach(props -> { ... });
```

`random()` returns an **infinite** `Stream.generate(...)`.  
Each element is an independently sampled `Properties` where:

- **Discrete arrays** — one element chosen uniformly at random.
- **Integer ranges** — a random integer drawn uniformly from `[start, end)`.
- **Double ranges** — a random double drawn uniformly from `[start, end)`.

> ⚠️ **Never call `.collect()` or `.count()` on the unbounded stream directly —
> it will loop forever. Always chain `.limit(n)` or use `random(int n)`.**

### Choosing a Strategy

| Criterion | Grid Search | Random Search |
|---|---|---|
| Search space size | Small (< ~1 000) | Large or infinite |
| Guarantees | Exhaustive coverage | Statistical coverage |
| Redundant parameters | Wastes evaluations | Naturally avoids |
| Parallelism | `.parallel()` safe | `.parallel()` safe |
| Recommended | Feasibility checks, fine-tuning | Initial exploration |

Empirical research (Bergstra & Bengio, 2012) shows that random search is often more
efficient than grid search when many hyperparameters are unimportant, because it avoids
repeated evaluation of redundant combinations.

---

## Managing Parameters

### Overwriting

Calling `add()` with the same name twice silently replaces the first registration.
This makes it easy to derive variant search spaces:

```java
var base = new Hyperparameters()
        .add("smile.random.forest.trees",     100)
        .add("smile.random.forest.max.nodes", 50, 300, 50);

// Variant: try more trees
base.add("smile.random.forest.trees", new int[]{100, 200, 300});
```

### Removing a Parameter

```java
hp.remove("smile.random.forest.trees"); // no-op if not present
```

### Clearing All Parameters

```java
hp.clear();
```

Both `remove()` and `clear()` return `this` for chaining.

### Querying

```java
int count = hp.size(); // number of registered parameters
```

### Calling `grid()` or `random()` on an Empty Instance

Both methods throw `IllegalStateException` if no parameters have been registered,
giving a clear error rather than a cryptic `NoSuchElementException` or silent hang.

```java
var hp = new Hyperparameters();
hp.grid();   // throws IllegalStateException: No hyperparameters have been registered
hp.random(); // same
```

---

## Integration with SMILE Models

Every SMILE model that accepts a `Properties` argument reads its tuning knobs from
well-known property keys (documented in each model's Javadoc). The `Hyperparameters`
builder generates exactly those `Properties` objects.

### Random Forest Example

```java
import smile.hpo.Hyperparameters;
import smile.classification.RandomForest;
import smile.data.formula.Formula;
import smile.validation.metric.Accuracy;
import smile.io.Read;

var formula = Formula.lhs("class");
var train   = Read.arff("data/weka/iris.arff");
var test    = Read.arff("data/weka/iris.arff");
int[] testy = formula.y(test).toIntArray();

var hp = new Hyperparameters()
        .add("smile.random.forest.trees",     new int[]{100, 200, 500})
        .add("smile.random.forest.mtry",      new int[]{1, 2, 3})
        .add("smile.random.forest.max.nodes", 20, 200, 20);

// Grid search: 3 × 3 × 10 = 90 configurations
var best = new double[]{0};
var bestProps = new java.util.Properties[1];

hp.grid().forEach(props -> {
    var model    = RandomForest.fit(formula, train, props);
    double acc   = Accuracy.of(testy, model.predict(test));
    if (acc > best[0]) {
        best[0]      = acc;
        bestProps[0] = props;
    }
});

System.out.printf("Best accuracy: %.4f%n", best[0]);
System.out.println("Best config:  " + bestProps[0]);
```

### Gradient Boosted Trees Example

```java
var hp = new Hyperparameters()
        .add("smile.gbt.trees",        new int[]{100, 200})
        .add("smile.gbt.max.depth",    new int[]{4, 6, 8})
        .add("smile.gbt.shrinkage",    new double[]{0.05, 0.1, 0.2})
        .add("smile.gbt.sampling.rate",new double[]{0.7, 0.8, 1.0});

// 2 × 3 × 3 × 3 = 54 configurations
hp.grid().forEach(props -> {
    var model = GradientTreeBoost.fit(formula, train, props);
    // evaluate ...
});
```

### Neural Network Example

```java
var hp = new Hyperparameters()
        .add("smile.mlp.epochs",        new int[]{10, 20, 50})
        .add("smile.mlp.mini.batch",    new int[]{32, 64, 128})
        .add("smile.mlp.learning.rate", new double[]{0.001, 0.01, 0.1})
        .add("smile.mlp.momentum",      new double[]{0.0, 0.9});

// Random search — 3×3×3×2 = 54 grid would be fine, but random is quicker to prototype
hp.random(30).forEach(props -> {
    var model = MLP.fit(x, y, props);
    // evaluate ...
});
```

---

## Cross-Validation Integration

For reliable evaluation, pair hyperparameter search with k-fold cross-validation:

```java
import smile.validation.CrossValidation;
import smile.validation.metric.Accuracy;

var hp = new Hyperparameters()
        .add("smile.random.forest.trees",     new int[]{100, 200})
        .add("smile.random.forest.max.nodes", 50, 300, 50);

double bestAcc = 0;
Properties bestProps = null;

for (var props : (Iterable<Properties>) hp.grid()::iterator) {
    var result = CrossValidation.classification(5, x, y,
            (xi, yi) -> RandomForest.fit(formula, DataFrame.of(xi, schema), props));
    double acc = result.avg().accuracy();
    System.out.printf("%.4f  %s%n", acc, props);
    if (acc > bestAcc) {
        bestAcc   = acc;
        bestProps = props;
    }
}

System.out.println("Best CV accuracy: " + bestAcc);
System.out.println("Best config:      " + bestProps);
```

---

## Best Practices

1. **Start with random search** for initial exploration, then switch to a narrower grid
   search around the promising region.

2. **Mind combinatorial explosion.** Print `hp.grid().count()` before committing to a
   full grid run. Prefer random search when `count > 1_000`.

3. **Fix cheap parameters first.** Lock parameters you are confident about with
   `add("key", fixedValue)` so they do not contribute to the Cartesian product.

4. **Use log-scale ranges for learning rates and regularization.**  
   Provide an explicit array instead of a linear range:
   ```java
   hp.add("smile.svm.C", new double[]{0.001, 0.01, 0.1, 1.0, 10.0, 100.0});
   ```

5. **Prefer cross-validation** over a single train/test split when the dataset is small.

6. **Parallelize safely.** Both `grid()` and `random()` produce independent `Properties`
   objects; calling `.parallel()` on the stream is safe as long as the model `fit()`
   method itself is thread-safe (most SMILE classifiers are stateless factories).
   ```java
   hp.grid().parallel().forEach(props -> { ... });
   ```

7. **Limit random search** — always use `.limit(n)` or `random(int n)`. Forgetting to
   limit the stream causes an infinite loop.

8. **Persist the best configuration** by writing it to a `.properties` file:
   ```java
   try (var out = new FileOutputStream("best.properties")) {
       bestProps.store(out, "Best hyperparameters");
   }
   ```

---

## API Reference

### `Hyperparameters` class

#### Constructors

| Constructor | Description |
|---|---|
| `Hyperparameters()` | Creates an empty hyperparameter search space. |

#### `add()` — Parameter Registration

| Method | Description |
|---|---|
| `add(String name, int value)` | Registers a fixed integer value. |
| `add(String name, double value)` | Registers a fixed double value. |
| `add(String name, String value)` | Registers a fixed string value. |
| `add(String name, int[] values)` | Registers a discrete set of integer choices. |
| `add(String name, double[] values)` | Registers a discrete set of double choices. |
| `add(String name, String[] values)` | Registers a discrete set of string choices. |
| `add(String name, int start, int end)` | Integer range; step = `max(1, (end-start)/10)`. |
| `add(String name, int start, int end, int step)` | Integer range with explicit step. |
| `add(String name, double start, double end)` | Double range; step = `(end-start)/10`. |
| `add(String name, double start, double end, double step)` | Double range with explicit step. |

All `add()` overloads:
- Throw `IllegalArgumentException` if `name` is null or blank.
- Throw `IllegalArgumentException` if an array is empty.
- Throw `IllegalArgumentException` if `start >= end` or `step <= 0`.
- **Silently overwrite** if the same name is registered twice.
- Return `this` for chaining.

#### Management

| Method | Description |
|---|---|
| `remove(String name)` | Removes a parameter; no-op if absent. Returns `this`. |
| `clear()` | Removes all parameters. Returns `this`. |
| `size()` | Returns the number of registered parameters. |

#### Search

| Method | Description |
|---|---|
| `grid()` | Finite `Stream<Properties>` — exhaustive Cartesian product. Throws `IllegalStateException` if empty. |
| `random()` | **Infinite** `Stream<Properties>` — uniform random sampling. Throws `IllegalStateException` if empty. Must be followed by `.limit(n)`. |
| `random(int n)` | Convenience: equivalent to `random().limit(n)`. Throws `IllegalArgumentException` if `n <= 0`. |


---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
