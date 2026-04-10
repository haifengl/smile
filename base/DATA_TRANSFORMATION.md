# SMILE — Data Transformation User Guide & Tutorial

The `smile.data.transform` package provides a composable, serializable pipeline
for preprocessing tabular data before model training.  Every transform maps a
`Tuple` (one row) or an entire `DataFrame` to a transformed counterpart, and the
higher-level implementations in `smile.feature.transform` build the common
statistical scalers on top of that foundation.

---

## Table of Contents

1. [Architecture overview](#1-architecture-overview)
2. [The Transform interface](#2-the-transform-interface)
3. [The InvertibleTransform interface](#3-the-invertibletransform-interface)
4. [ColumnTransform](#4-columntransform)
5. [InvertibleColumnTransform](#5-invertiblecolumntransform)
6. [Built-in scalers and standardisers](#6-built-in-scalers-and-standardisers)
   - [Scaler — min-max scaling](#61-scaler--min-max-scaling)
   - [Standardizer — z-score standardisation](#62-standardizer--z-score-standardisation)
   - [RobustStandardizer — median / IQR](#63-robustandardizer--median--iqr)
   - [WinsorScaler — percentile clamping](#64-winsorscaler--percentile-clamping)
   - [MaxAbsScaler — maximum absolute value](#65-maxabsscaler--maximum-absolute-value)
   - [Normalizer — per-row unit norm](#66-normalizer--per-row-unit-norm)
7. [Composing transforms](#7-composing-transforms)
   - [andThen and compose](#71-andthen-and-compose)
   - [pipeline](#72-pipeline)
   - [fit — data-dependent pipelines](#73-fit--data-dependent-pipelines)
8. [Inverting transforms](#8-inverting-transforms)
9. [Nullable column handling](#9-nullable-column-handling)
10. [Serialization](#10-serialization)
11. [Writing a custom transform](#11-writing-a-custom-transform)
12. [End-to-end tutorial](#12-end-to-end-tutorial)
13. [Choosing the right transform](#13-choosing-the-right-transform)
14. [API quick reference](#14-api-quick-reference)

---

## 1. Architecture overview

```
Transform  ──────────────────────────────────────────────────────────────────
│  apply(Tuple)   → Tuple          (row-by-row)
│  apply(DataFrame) → DataFrame    (batch, column-parallel)
│  andThen(Transform) → Transform  (compose forward)
│  compose(Transform) → Transform  (compose backward)
│
├── ColumnTransform                (map-of-lambdas, column-wise)
│     └── InvertibleColumnTransform (adds invert() for both Tuple and DataFrame)
│
InvertibleTransform extends Transform
│  invert(Tuple)     → Tuple
│  invert(DataFrame) → DataFrame
```

**All built-in scalers** (`Scaler`, `Standardizer`, `RobustStandardizer`,
`WinsorScaler`, `MaxAbsScaler`) return an `InvertibleColumnTransform`.
`Normalizer` returns a plain `ColumnTransform`-compatible `Transform`.

---

## 2. The Transform interface

`Transform` lives in `smile.data.transform` and extends
`java.util.function.Function<Tuple, Tuple>`.  Every preprocessing step
implements this single interface.

### Row-level application

```java
Transform t = /* any transform */;
Tuple outRow = t.apply(inRow);
```

### Batch application

The default `apply(DataFrame)` implementation streams all rows through the
row-level `apply(Tuple)`.  `ColumnTransform` overrides this with a
column-parallel fast path that avoids per-row object allocation.

```java
DataFrame out = t.apply(df);
```

### Static factories

```java
// Build a single-step pipeline from already-fitted transforms
Transform chain = Transform.pipeline(step1, step2, step3);

// Fit several data-dependent transforms in sequence.
// Each subsequent trainer receives the data as transformed by all previous steps.
Transform pipeline = Transform.fit(trainDf,
        Standardizer::fit,
        data -> WinsorScaler.fit(data, 0.01, 0.99));
```

---

## 3. The InvertibleTransform interface

Extends `Transform` with two inverse operations:

```java
public interface InvertibleTransform extends Transform {
    Tuple     invert(Tuple x);
    DataFrame invert(DataFrame data);
}
```

Inversion is useful whenever the original feature space must be recovered —
for example, to interpret a model's output in the original units, or to
reconstruct an input from a latent representation.

```java
InvertibleColumnTransform scaler = Standardizer.fit(train);
DataFrame scaled    = scaler.apply(test);
DataFrame recovered = scaler.invert(scaled);  // back to original units
```

---

## 4. ColumnTransform

`ColumnTransform` is the concrete class that powers all column-wise transforms.
It holds a `Map<String, Function>` — a mapping from column name to the
`smile.util.function.Function` lambda to apply to that column's values.

### Constructing manually

```java
import smile.data.transform.ColumnTransform;
import smile.util.function.Function;
import java.util.Map;

// Log-transform "income", leave everything else unchanged
Map<String, Function> transforms = Map.of("income", Math::log);
ColumnTransform ct = new ColumnTransform("log-income", transforms);

DataFrame out = ct.apply(df);
```

### Behaviour

- Columns **not** present in the map are passed through unchanged (same
  `ValueVector` object — zero copy).
- Transformed columns always become `DoubleType` (or `NullableDoubleType`
  for nullable inputs).
- The `apply(DataFrame)` implementation processes all columns **in parallel**
  using `IntStream.range(...).parallel()`.

### toString

```java
System.out.println(ct);
// log-income(
//   log(income)
// )
```

The output format is `<name>(\n  <function_toString()>, ...\n)`.
Each `Function` implementation's `toString()` should describe the
transformation (the built-in scalers do this; lambdas print as the
default JVM reference unless overridden).

---

## 5. InvertibleColumnTransform

Extends `ColumnTransform` and additionally stores a second map of inverse
lambdas, one per transformed column.

```java
import smile.data.transform.InvertibleColumnTransform;

Map<String, Function> transforms = Map.of("price", v -> Math.log1p(v));
Map<String, Function> inverses   = Map.of("price", v -> Math.expm1(v));

InvertibleColumnTransform ict =
    new InvertibleColumnTransform("log1p-price", transforms, inverses);

DataFrame logPrices = ict.apply(df);
DataFrame original  = ict.invert(logPrices);  // ≈ df
```

### Inversion behaviour

- Columns in the **inverses map** are inverted using the supplied lambda.
- Columns **not** in the inverses map are passed through unchanged.
- Nullable columns retain their null mask on both the forward and inverse
  paths — nulls are never fabricated or lost.

---

## 6. Built-in scalers and standardisers

All built-in transforms follow the same pattern:

1. Call `Xxx.fit(trainData)` to learn parameters from training data.
2. Call `.apply(anyData)` to transform training **and** test data alike.
3. Optionally call `.invert(transformedData)` to recover the original scale.

> **Train/test discipline:** Always fit on training data only.
> Apply the fitted transform to both train and test sets.

### 6.1 Scaler — min-max scaling

Maps each numeric column to the range `[0, 1]`:

```
x_scaled = (x - min) / (max - min)
```

Values outside the training range are **clamped** to `[0, 1]`.

```java
import smile.feature.transform.Scaler;

// Fit on train, transform all numeric columns
InvertibleColumnTransform scaler = Scaler.fit(train);
DataFrame trainScaled = scaler.apply(train);
DataFrame testScaled  = scaler.apply(test);

// Fit on selected columns only
InvertibleColumnTransform partial = Scaler.fit(train, "age", "income");
```

**When to use:**  Algorithms that are sensitive to feature magnitude and when
the data has no severe outliers (e.g. KNN, SVM with RBF kernel, neural
networks).

**When to avoid:**  Data with large outliers — a single extreme value will
compress all normal values into a tiny sub-range.  Use `WinsorScaler` instead.

### 6.2 Standardizer — z-score standardisation

Scales each numeric column to zero mean and unit variance:

```
x_std = (x - μ) / σ
```

If the standard deviation is zero (constant column), the scale factor is `1.0`
and the column is only centred.

```java
import smile.feature.transform.Standardizer;

InvertibleColumnTransform std = Standardizer.fit(train);
DataFrame trainStd = std.apply(train);
DataFrame testStd  = std.apply(test);
```

**When to use:**  When the algorithm assumes Gaussian-distributed features or
uses distance / dot-product computations (linear regression, logistic
regression, linear SVM, PCA).

**When to avoid:**  Heavy-tailed distributions or data with many outliers —
a single large outlier inflates σ and under-compresses the bulk of the data.
Use `RobustStandardizer` instead.

### 6.3 RobustStandardizer — median / IQR

Scales by subtracting the median and dividing by the interquartile range (IQR):

```
x_robust = (x - median) / IQR
```

The IQR is the difference between the 75th and 25th percentiles.  If the IQR
is zero, the scale factor is `1.0`.

```java
import smile.feature.transform.RobustStandardizer;

InvertibleColumnTransform robust = RobustStandardizer.fit(train);
DataFrame robustTrain = robust.apply(train);
```

**When to use:**  Data with outliers or heavy-tailed distributions.  Median
and IQR are much less sensitive to extreme values than mean and standard
deviation.

### 6.4 WinsorScaler — percentile clamping

A two-step approach:
1. Clamp values outside the `[lower, upper]` percentile range of the training
   distribution.
2. Scale the result to `[0, 1]`.

```java
import smile.feature.transform.WinsorScaler;

// Default: 5th percentile lower, 95th percentile upper
InvertibleColumnTransform ws = WinsorScaler.fit(train);

// Custom percentiles — clamp at 1st and 99th percentile
InvertibleColumnTransform ws2 = WinsorScaler.fit(train, 0.01, 0.99);

// Transform specific columns only
InvertibleColumnTransform ws3 = WinsorScaler.fit(train, 0.05, 0.95, "price", "quantity");
```

**When to use:**  When you know the training set has outliers that are
meaningful in the training data but that you do not want to distort scaling
for the bulk of the data.  Common in financial and sensor data.

### 6.5 MaxAbsScaler — maximum absolute value

Divides each feature by its maximum absolute value so that all values lie in
`[-1, 1]`.  This scaler does **not** centre the data (mean is not subtracted),
making it suitable for sparse data where centering would destroy sparsity.

```java
import smile.feature.transform.MaxAbsScaler;

InvertibleColumnTransform mas = MaxAbsScaler.fit(train);
DataFrame scaled = mas.apply(test);
```

**When to use:**  Sparse data (e.g. TF-IDF vectors) or when the sign of
values is meaningful and you want to preserve zero values exactly.

### 6.6 Normalizer — per-row unit norm

`Normalizer` rescales each **row** (sample) so that its vector norm equals 1.
Unlike the column-wise scalers above, `Normalizer` operates across columns
within a single row.  It supports three norms:

| Norm | Formula |
|------|---------|
| `L1` | `Σ|xᵢ|` = 1 |
| `L2` | `√(Σxᵢ²)` = 1 |
| `L_INF` | `max(|xᵢ|)` = 1 |

```java
import smile.feature.transform.Normalizer;
import smile.feature.transform.Normalizer.Norm;

// Normalize specific columns
String[] features = {"f1", "f2", "f3"};

Normalizer l2 = new Normalizer(Norm.L2, features);
DataFrame normed = l2.apply(df);

// Apply row-by-row to a single Tuple
Tuple row = df.get(0);
Tuple normRow = l2.apply(row);
```

`Normalizer` is **not** invertible (it is not an `InvertibleTransform`).

**When to use:**  Text classification (TF-IDF), cosine-similarity-based
algorithms, or whenever the magnitude of a row is irrelevant but the direction
(ratio of features) is meaningful.

---

## 7. Composing transforms

### 7.1 andThen and compose

`andThen` and `compose` mirror the semantics of
`java.util.function.Function`:

```java
// a.andThen(b) ≡ b(a(x)) — apply a first, then b
Transform aFirst = a.andThen(b);

// b.compose(a) ≡ b(a(x)) — same result, different spelling
Transform alsoAFirst = b.compose(a);
```

Both return a new `Transform` (a lambda, not a named class).

```java
InvertibleColumnTransform scaler = Scaler.fit(train);
InvertibleColumnTransform std    = Standardizer.fit(train);

// Scale first, then standardise
Transform both = scaler.andThen(std);
DataFrame out  = both.apply(test);
```

> **Note:** The composed result is a plain `Transform`, not an
> `InvertibleTransform`, even if both inputs are invertible.  If you need
> inversion of the composed result, use `Transform.fit` (see §7.3) or
> invert the steps individually in reverse order.

### 7.2 pipeline

`Transform.pipeline(t1, t2, ..., tN)` is equivalent to chaining `andThen`
calls and is more readable for three or more steps:

```java
Transform pipeline = Transform.pipeline(
        imputer,     // fill missing values
        scaler,      // [0, 1] scale
        std          // z-score
);

DataFrame train_prepped = pipeline.apply(train);
DataFrame test_prepped  = pipeline.apply(test);
```

Throws `IllegalArgumentException` if called with no transforms.

### 7.3 fit — data-dependent pipelines

`Transform.fit(data, trainer1, trainer2, ...)` builds a pipeline where each
trainer is a `Function<DataFrame, Transform>` — a function that observes
the data **as transformed so far** and returns the next step:

```java
Transform pipeline = Transform.fit(train,
        // Step 1: fit imputer on raw training data
        data -> SimpleImputer.fit(data),

        // Step 2: fit scaler on imputed training data
        data -> Scaler.fit(data),

        // Step 3: fit standardiser on scaled+imputed training data
        data -> Standardizer.fit(data)
);

// Apply the whole fitted pipeline to test data
DataFrame testOut = pipeline.apply(test);
```

This is the canonical way to build multi-step preprocessing pipelines where
each step's parameters depend on the output of the previous steps.

Throws `IllegalArgumentException` if called with no trainers.

---

## 8. Inverting transforms

`InvertibleTransform.invert()` maps from the transformed space back to the
original space.  This is used for:

- **Interpreting predictions:** A model trained on standardised targets
  must have its output de-standardised before presenting to users.
- **Reconstruction errors:** Auto-encoders or PCA-based anomaly detectors
  can measure reconstruction fidelity in the original units.
- **Debugging:** Verify that `invert(apply(x)) ≈ x`.

```java
InvertibleColumnTransform std = Standardizer.fit(train);

// Forward pass
DataFrame trainStd = std.apply(train);
double[] yStd = model.predict(trainStd);

// Recover predictions in original units
// Build a single-column DataFrame to pass through invert()
DataFrame predDf = DataFrame.of(new DoubleVector("y", yStd));
DataFrame predOriginal = std.invert(predDf);
```

### Row-level inversion

```java
Tuple row          = df.get(0);
Tuple scaled       = std.apply(row);
Tuple backToNormal = std.invert(scaled);
```

### Composing inverses

When multiple invertible transforms are stacked, inverting the composed
pipeline requires **reversing the order**:

```java
InvertibleColumnTransform s1 = Scaler.fit(train);
InvertibleColumnTransform s2 = Standardizer.fit(s1.apply(train));

// Forward: s1 then s2
DataFrame fwd = s2.apply(s1.apply(test));

// Inverse: s2⁻¹ then s1⁻¹  (reversed order)
DataFrame inv = s1.invert(s2.invert(fwd));
```

---

## 9. Nullable column handling

SMILE supports nullable columns backed by `NullableDoubleVector` (and
equivalent types for other primitives).  All transforms in this package
preserve nullability:

- A nullable input column always produces a nullable output column —
  the null bit-mask is copied intact.
- The transform lambda is still called on the raw `double` value for a null
  cell (which is `Double.NaN` by convention), but the result is ignored
  and replaced by `NaN` / null in the output vector.
- **Inversion** also preserves the null mask.

```java
// Suppose "income" has some null entries
InvertibleColumnTransform scaler = Scaler.fit(train);

DataFrame scaled = scaler.apply(train);
// scaled.column("income").isNullable()  → true  (same as input)
// scaled.column("income").isNullAt(k)   → same as train

DataFrame restored = scaler.invert(scaled);
// restored.column("income").isNullable() → true (null mask preserved)
```

---

## 10. Serialization

`Transform` extends `java.io.Serializable`.  All built-in implementations
and the closure-captured lambdas inside `InvertibleColumnTransform` are
serializable, so a fitted transform can be saved and reloaded:

```java
// Save
try (var out = new ObjectOutputStream(new FileOutputStream("scaler.ser"))) {
    out.writeObject(scaler);
}

// Load
InvertibleColumnTransform loaded;
try (var in = new ObjectInputStream(new FileInputStream("scaler.ser"))) {
    loaded = (InvertibleColumnTransform) in.readObject();
}

// Use the loaded transform on new data
DataFrame newData = loaded.apply(incoming);
```

> **Caution with lambdas:** Only use method references or anonymous classes
> when the enclosing class is itself `Serializable`.  A non-serializable
> lambda passed to `ColumnTransform` or `InvertibleColumnTransform` will
> cause `NotSerializableException` at save time.

---

## 11. Writing a custom transform

### Option A — implement Transform directly

Useful when the transformation is not column-wise (e.g. row normalization,
dimension reduction):

```java
import smile.data.transform.Transform;
import smile.data.Tuple;

public class ClipTransform implements Transform {
    private final double min, max;
    private final Set<String> columns;

    public ClipTransform(double min, double max, String... columns) {
        this.min = min;
        this.max = max;
        this.columns = new HashSet<>(Arrays.asList(columns));
    }

    @Override
    public Tuple apply(Tuple x) {
        StructType schema = x.schema();
        return new smile.data.AbstractTuple(schema) {
            @Override
            public Object get(int i) {
                String name = schema.field(i).name();
                if (columns.contains(name)) {
                    double v = x.getDouble(i);
                    return Math.max(min, Math.min(max, v));
                }
                return x.get(i);
            }
        };
    }
}
```

### Option B — use ColumnTransform with lambdas

The simplest approach for column-wise numeric transformations:

```java
// Clip all values to [-3, 3]
Map<String, Function> clips = new HashMap<>();
for (String col : numericColumns) {
    clips.put(col, x -> Math.max(-3.0, Math.min(3.0, x)));
}
ColumnTransform clipper = new ColumnTransform("clip±3", clips);
```

### Option C — use InvertibleColumnTransform with lambdas

When you need both forward and inverse:

```java
// Box-Cox power transform with λ = 0.5  (square-root transform)
double lambda = 0.5;
Map<String, Function> fwd = Map.of(
    "revenue", x -> (Math.pow(x, lambda) - 1.0) / lambda
);
Map<String, Function> inv = Map.of(
    "revenue", y -> Math.pow(y * lambda + 1.0, 1.0 / lambda)
);
InvertibleColumnTransform boxCox =
    new InvertibleColumnTransform("BoxCox(0.5)", fwd, inv);
```

### Option D — data-dependent custom transform via fit

```java
// Normalize each column by its own training median
static InvertibleColumnTransform fitMedianScaler(DataFrame data, String... cols) {
    Map<String, Function> transforms = new HashMap<>();
    Map<String, Function> inverses   = new HashMap<>();
    for (String col : cols) {
        double[] vals = data.column(col).toDoubleArray();
        double median = MathEx.median(vals);
        double scale  = MathEx.isZero(median) ? 1.0 : Math.abs(median);
        transforms.put(col, x -> x / scale);
        inverses.put(col,   y -> y * scale);
    }
    return new InvertibleColumnTransform("MedianScaler", transforms, inverses);
}
```

---

## 12. End-to-end tutorial

This tutorial preprocesses a lending dataset that has `loan_amount`
(double), `annual_income` (double, nullable), `grade` (categorical),
`term` (int) and `default` (int, the label).

### Step 1 — Split data

```java
DataFrame data = Read.csv("loans.csv");

// 80/20 train/test split (indices)
int n = data.size();
int trainSize = (int)(n * 0.8);
DataFrame train = data.slice(0, trainSize);
DataFrame test  = data.slice(trainSize, n);
```

### Step 2 — Impute missing values

```java
import smile.feature.imputation.SimpleImputer;

// Fit: learn per-column fill values (mean for numeric, mode for categorical)
Transform imputer = SimpleImputer.fit(train);
DataFrame trainImputed = imputer.apply(train);
DataFrame testImputed  = imputer.apply(test);
```

### Step 3 — Scale numeric features

```java
import smile.feature.transform.WinsorScaler;

// Use Winsor to be robust to the income outliers
InvertibleColumnTransform scaler =
    WinsorScaler.fit(trainImputed, 0.01, 0.99, "loan_amount", "annual_income");

DataFrame trainScaled = scaler.apply(trainImputed);
DataFrame testScaled  = scaler.apply(testImputed);
```

### Step 4 — Compose into a single reusable pipeline

```java
Transform fullPipeline = Transform.fit(train,
    SimpleImputer::fit,
    data -> WinsorScaler.fit(data, 0.01, 0.99, "loan_amount", "annual_income")
);

// Apply in one call — same result as steps 2-3 above
DataFrame trainPrepped = fullPipeline.apply(train);
DataFrame testPrepped  = fullPipeline.apply(test);
```

### Step 5 — Train a model using a Formula

```java
import smile.data.formula.Formula;

Formula formula = Formula.of("default",
        Terms.$("loan_amount"),
        Terms.$("annual_income"),
        Terms.$("term"),
        Terms.$("grade"));

// Extract design matrix and response
var X = formula.matrix(trainPrepped);
var y = formula.y(trainPrepped).toIntArray();

// … fit your model with X and y …
```

### Step 6 — Invert predictions to interpret results

```java
// Suppose we predicted a continuous score and want to understand it
// in the original income units:
double[] scores = model.predict(formula.matrix(testPrepped));

DataFrame scoreDf = DataFrame.of(new DoubleVector("annual_income", scores));
DataFrame inOriginalScale = scaler.invert(scoreDf);
System.out.println(inOriginalScale);
```

### Step 7 — Inspect and save the pipeline

```java
// Inspect what each step learned
System.out.println(fullPipeline);

// Serialize for reuse in production
try (var out = new ObjectOutputStream(new FileOutputStream("pipeline.ser"))) {
    out.writeObject(fullPipeline);
}
```

---

## 13. Choosing the right transform

| Situation | Recommended transform |
|---|---|
| No strong outliers, range matters | `Scaler` |
| Gaussian assumption, distance-based algo | `Standardizer` |
| Outliers present, want robustness | `RobustStandardizer` |
| Outliers present, want bounded output | `WinsorScaler` |
| Sparse data, zero must stay zero | `MaxAbsScaler` |
| Direction matters, not magnitude | `Normalizer` (L2) |
| Text / count vectors | `Normalizer` (L1 or L2) |
| Custom log / power transformation | `InvertibleColumnTransform` + lambdas |
| Missing values before scaling | `SimpleImputer` first, then scaler |
| Multiple sequential steps | `Transform.fit(...)` pipeline |

### Quick comparison of column-wise scalers

Given a column with values `{1, 2, 3, 100}` (the `100` is an outlier):

| Transform | 1 | 2 | 3 | 100 |
|---|---|---|---|---|
| `Scaler` | 0.000 | 0.010 | 0.020 | 1.000 |
| `Standardizer` (μ≈26.5, σ≈48.0) | −0.53 | −0.51 | −0.49 | 1.53 |
| `RobustStandardizer` (med=2.5, IQR≈1.5) | −1.00 | −0.33 | 0.33 | 65.0 |
| `WinsorScaler` (5/95 pct) | 0.000 | 0.500 | 1.000 | 1.000 |
| `MaxAbsScaler` (max=100) | 0.010 | 0.020 | 0.030 | 1.000 |

`Scaler` compresses `{1,2,3}` to near zero because of the outlier.
`WinsorScaler` clamps the outlier so `{1,2,3}` spread cleanly across `[0,1]`.
`RobustStandardizer` is robust to the outlier but its output is unbounded.

---

## 14. API quick reference

### `Transform` (interface — `smile.data.transform`)

| Member | Description |
|--------|-------------|
| `apply(Tuple)` | Transform one row (abstract). |
| `apply(DataFrame)` | Transform all rows (default: row-stream; overridden by `ColumnTransform` for column-parallel batch). |
| `andThen(Transform)` | Compose: `this`, then `after`. |
| `compose(Transform)` | Compose: `before`, then `this`. |
| `Transform.pipeline(Transform...)` | Chain multiple transforms left-to-right. Throws on empty input. |
| `Transform.fit(DataFrame, Function<DataFrame,Transform>...)` | Fit a data-dependent pipeline. Each trainer sees data as already transformed. Throws on empty input. |

### `InvertibleTransform` (interface — `smile.data.transform`)

| Member | Description |
|--------|-------------|
| `invert(Tuple)` | Inverse-transform one row. |
| `invert(DataFrame)` | Inverse-transform all rows. Preserves nullable columns. |

### `ColumnTransform` (class — `smile.data.transform`)

| Member | Description |
|--------|-------------|
| `ColumnTransform(String name, Map<String,Function> transforms)` | Constructor. |
| `apply(Tuple)` | Applies lambdas to matching column positions; others pass through. |
| `apply(DataFrame)` | Column-parallel batch transform; preserves null masks. |
| `toString()` | `"<name>(\n  <fn>, ...\n)"` |

### `InvertibleColumnTransform` (class — `smile.data.transform`)

| Member | Description |
|--------|-------------|
| `InvertibleColumnTransform(String, Map<String,Function>, Map<String,Function>)` | Constructor (name, forward lambdas, inverse lambdas). |
| `invert(Tuple)` | Applies inverse lambdas; unmatched columns pass through. |
| `invert(DataFrame)` | Column-parallel inverse; preserves null masks. |

### Built-in scalers (all in `smile.feature.transform`)

| Class | Factory method | Formula | Invertible |
|-------|----------------|---------|------------|
| `Scaler` | `Scaler.fit(data, cols...)` | `(x−min)/(max−min)` clamped to `[0,1]` | Yes |
| `Standardizer` | `Standardizer.fit(data, cols...)` | `(x−μ)/σ` | Yes |
| `RobustStandardizer` | `RobustStandardizer.fit(data, cols...)` | `(x−median)/IQR` | Yes |
| `WinsorScaler` | `WinsorScaler.fit(data)` or `fit(data, lo, hi, cols...)` | clamp then `(x−pLo)/(pHi−pLo)` | Yes |
| `MaxAbsScaler` | `MaxAbsScaler.fit(data, cols...)` | `x / max(|x|)` | Yes |
| `Normalizer` | `new Normalizer(Norm, cols...)` | row-wise unit norm (L1/L2/L∞) | No |

All column-wise `fit()` methods accept an optional varargs `String... columns`
argument.  When omitted, **all numeric columns** are transformed automatically.

---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

