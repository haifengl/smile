# SMILE — Formula User Guide & Tutorial

The `smile.data.formula` package provides a compact, symbolic language for specifying
statistical models. A **formula** describes which column is the *response* (dependent
variable) and which columns are the *predictors* (independent variables), including
optional transformations and interactions. It is the primary bridge between a raw
`DataFrame` and the design matrices consumed by SMILE's machine-learning algorithms.

---

## Table of Contents

1. [Concepts](#1-concepts)
2. [Quick Start](#2-quick-start)
3. [Building Formulas](#3-building-formulas)
   - [From a string](#31-from-a-string)
   - [Factory methods](#32-factory-methods)
4. [Terms Reference](#4-terms-reference)
   - [Dot (`.`) — all remaining columns](#41-dot----all-remaining-columns)
   - [Variable](#42-variable)
   - [Intercept (`0` / `1`)](#43-intercept-0--1)
   - [Delete (`-`)](#44-delete--)
   - [Arithmetic operators](#45-arithmetic-operators)
   - [Math functions](#46-math-functions)
   - [Factor interaction (`::`)](#47-factor-interaction-)
   - [Factor crossing (`&&` / `^`)](#48-factor-crossing----)
   - [Date / time features](#49-date--time-features)
   - [Constant values](#410-constant-values)
   - [Custom lambdas](#411-custom-lambdas)
5. [Applying a Formula](#5-applying-a-formula)
   - [Binding to a schema](#51-binding-to-a-schema)
   - [Producing a DataFrame](#52-producing-a-dataframe)
   - [Extracting predictors only](#53-extracting-predictors-only)
   - [Extracting the response](#54-extracting-the-response)
   - [Producing a design matrix](#55-producing-a-design-matrix)
   - [Applying row-by-row to Tuples](#56-applying-row-by-row-to-tuples)
6. [Expanding a Formula](#6-expanding-a-formula)
7. [Intercept / Bias Control](#7-intercept--bias-control)
8. [Nullable Columns](#8-nullable-columns)
9. [Working with Categorical Variables](#9-working-with-categorical-variables)
10. [Date / Time Tutorial](#10-date--time-tutorial)
11. [Custom Transformations Tutorial](#11-custom-transformations-tutorial)
12. [Thread Safety & Lifecycle](#12-thread-safety--lifecycle)
13. [API Cheat Sheet](#13-api-cheat-sheet)

---

## 1. Concepts

### Formula

A `Formula` has two sides separated by `~`:

```
response ~ predictor1 + predictor2 + ...
```

| Side | Name | Meaning |
|------|------|---------|
| Left of `~` | **Response** | The column to predict (dependent variable). Optional — omit for unsupervised tasks. |
| Right of `~` | **Predictors** | One or more terms that describe the inputs (independent variables). |

### Term

A **`Term`** is a node in the formula expression tree. Terms are composable:
- `Variable("age")` — a raw column reference
- `Add(Variable("x"), val(10))` — an arithmetic expression
- `FactorCrossing("a","b","c")` — a crossing of categorical factors
- `Date("timestamp", YEAR, MONTH)` — date feature extraction

### Feature

When a `Term` is **bound** to a concrete `StructType` (schema), it produces one or more
**`Feature`** objects. A `Feature` knows its output `StructField` (name + type + measure)
and can extract a value from any `Tuple` or an entire `ValueVector` from a `DataFrame`.

---

## 2. Quick Start

```java
import smile.data.DataFrame;
import smile.data.formula.Formula;
import static smile.data.formula.Terms.*;

// Load data
DataFrame df = /* ... your DataFrame ... */;

// ① Predict "salary" from all other columns
Formula f1 = Formula.lhs("salary");

// ② Predict "class" from age and log(income)
Formula f2 = Formula.of("class", $("age"), log("income"));

// ③ Get the design matrix (bias column included by default)
var X = f2.matrix(df);

// ④ Get the response vector
var y = f2.y(df);

// ⑤ Get just the predictor DataFrame
var xdf = f2.x(df);
```

---

## 3. Building Formulas

### 3.1 From a String

`Formula.of(String)` parses an R-style formula string. This is the most convenient
approach for interactive use.

```java
Formula f = Formula.of("salary ~ age + log(income) + gender");
```

**Parsing rules:**

| Token | Meaning |
|-------|---------|
| `y ~ x` | Response `y`, predictor `x` |
| ` ~ x` | No response (RHS only) |
| `y ~ .` | Response `y`, all remaining columns as predictors |
| `+ term` | Add term to predictors |
| `- term` | Remove term from predictors |
| `- 1` or `+ 0` | Remove intercept |
| `+ 1` | Explicitly include intercept |
| `a:b:c` | Interaction of factors `a`, `b`, `c` |
| `(a x b x c)` | Full factor crossing of `a`, `b`, `c` |
| `(a x b x c)^2` | Factor crossing up to degree 2 |
| `log(x)` | Apply `log` to column `x` |
| `abs(x)` | Apply `abs` to column `x` |
| *(any supported function)* | See [§4.6](#46-math-functions) |

**Examples:**

```java
Formula.of("y ~ .")                       // y ~ all other columns
Formula.of("y ~ x1 + x2 - 1")            // no intercept
Formula.of("y ~ log(x) + sqrt(z)")        // transformations
Formula.of("y ~ (a x b x c)^2")           // interactions up to degree 2
Formula.of("y ~ a:b + c")                 // explicit interaction + main effect
Formula.of(" ~ .")                        // no response, all columns
```

> **Round-trip guarantee:** `Formula.of(formula.toString())` always equals the original
> formula.

### 3.2 Factory Methods

Use the programmatic API for type-safety and IDE completion.

```java
// lhs("col")  — response only; predictors = all remaining columns (.)
Formula f = Formula.lhs("salary");
// equivalent to: Formula.of("salary ~ .")

// of(response, predictors...)  — explicit response + predictor terms
Formula f = Formula.of("salary", $("age"), log("income"), cross("a","b"));

// of(response, String... predictors) — shorthand with column names
Formula f = Formula.of("salary", "age", "gender");

// rhs(Term...)  — no response variable (unsupervised / feature extraction)
Formula f = Formula.rhs($("age"), log("income"));

// rhs(String...)  — no response, column names only
Formula f = Formula.rhs("age", "gender");
```

---

## 4. Terms Reference

All term-builder methods live in the `Terms` interface. The recommended usage is:

```java
import static smile.data.formula.Terms.*;
```

### 4.1 Dot (`.`) — all remaining columns

The special term `"."` means *every column not otherwise mentioned in the formula*.

```java
Formula.of("salary", dot())               // salary ~ .
Formula.of("salary", dot(), delete("id")) // salary ~ . - id
```

In a string formula use the literal `.`:

```java
Formula.of("salary ~ . - id")
```

> **Note:** `.` is only valid on the **right-hand side**. Using it as the response throws
> `IllegalArgumentException`.

---

### 4.2 Variable

References a column by name.

```java
Term t = $("age");          // shorthand factory in Terms
Term t = new Variable("age");
```

String columns passed to `Formula.of(String, String...)` are automatically wrapped:

```java
Formula.of("y", "x1", "x2")  // x1 and x2 become Variable terms
```

---

### 4.3 Intercept (`0` / `1`)

Controls whether a bias/intercept column is added when producing a design matrix via
`formula.matrix(data)`.

```java
// Explicitly include intercept (default behaviour)
Formula.of("y ~ x + 1")
Formula.of("y", $("x"), new Intercept(true))

// Remove intercept — fit a line through the origin
Formula.of("y ~ x + 0")
Formula.of("y ~ x - 1")
Formula.of("y", $("x"), new Intercept(false))
```

If neither `0` nor `1` appears, the bias column **is** included by default.

---

### 4.4 Delete (`-`)

Removes a previously specified or Dot-implied term.

```java
// Remove a single column
Formula.of("y ~ . - id")
Formula.of("y", dot(), delete("id"))

// Remove an interaction
Formula.of("y ~ (a x b x c) - a:b")
Formula.of("y", cross("a","b","c"), delete(interact("a","b")))
```

You can also call `Terms.delete(String)` or `Terms.delete(Term)`:

```java
Term t = delete("age");
Term t = delete(log("income"));
```

---

### 4.5 Arithmetic Operators

Binary arithmetic terms operate on two numeric columns (or any combination of columns
and constant values). The result type follows Java's numeric promotion rules
(`int → long → float → double`).

| Method | Expression | Notes |
|--------|-----------|-------|
| `add(a, b)` | `a + b` | |
| `sub(a, b)` | `a - b` | |
| `mul(a, b)` | `a * b` | |
| `div(a, b)` | `a / b` | Integer division for `int`/`long` operands |

Each method has four overloads: `(Term, Term)`, `(String, String)`, `(Term, String)`,
`(String, Term)`.

```java
add("x", "y")             // x + y
sub("revenue", "cost")    // revenue - cost
mul("price", val(1.1))    // price * 1.1  (constant scale-up by 10%)
div("total", val(100))    // total / 100
```

**Using inside a formula:**

```java
Formula.of("profit", dot(), sub("revenue", "cost"), div("profit", "revenue"))
// profit ~ . + (revenue - cost) + (profit / revenue)
```

> **Type safety:** Both operands must be numeric (`int`, `long`, `float`, or `double`).
> Passing a `String` or other non-numeric column throws `IllegalStateException` at
> bind-time.

---

### 4.6 Math Functions

All functions operate on numeric columns and produce a `double` result (except `abs`,
`round`, and `sign` which preserve input precision).

#### Rounding

| Call | Result type | Description |
|------|-------------|-------------|
| `abs(x)` | same as input | Absolute value; supports `int`, `long`, `float`, `double` |
| `ceil(x)` | `double` | Ceiling (smallest integer ≥ x) |
| `floor(x)` | `double` | Floor (largest integer ≤ x) |
| `round(x)` | same as input | Nearest integer; `Math.round` semantics |
| `rint(x)` | `double` | Nearest integer (IEEE 754 "round to even") |

#### Logarithms & Exponentials

| Call | Description |
|------|-------------|
| `log(x)` | Natural logarithm ln(x) |
| `log2(x)` | Base-2 logarithm |
| `log10(x)` | Base-10 logarithm |
| `log1p(x)` | ln(1 + x) — numerically stable for small x |
| `exp(x)` | e^x |
| `expm1(x)` | e^x − 1 — numerically stable for small x |

#### Powers & Roots

| Call | Description |
|------|-------------|
| `sqrt(x)` | Square root √x |
| `cbrt(x)` | Cube root ∛x |

#### Trigonometry

| Call | Description |
|------|-------------|
| `sin(x)` | Sine (radians) |
| `cos(x)` | Cosine (radians) |
| `tan(x)` | Tangent (radians) |
| `asin(x)` | Arc-sine (radians) |
| `acos(x)` | Arc-cosine (radians) |
| `atan(x)` | Arc-tangent (radians) |
| `sinh(x)` | Hyperbolic sine |
| `cosh(x)` | Hyperbolic cosine |
| `tanh(x)` | Hyperbolic tangent |

#### Sign

| Call | Result type | Description |
|------|-------------|-------------|
| `signum(x)` | `double` | Floating-point sign: -1.0, 0.0, or 1.0 |
| `sign(x)` | `int` | Integer sign: -1, 0, or 1 |
| `ulp(x)` | `double` | Unit of least precision |

Every function has both a `String` overload (column name) and a `Term` overload
(for nesting):

```java
log("income")                  // log of the "income" column
log(add("base", "bonus"))      // log(base + bonus)  — nested terms
sqrt(div("variance", val(n)))  // sqrt(variance / n)
```

---

### 4.7 Factor Interaction (`::`)

`FactorInteraction` combines two or more **categorical** columns into a single
composite categorical feature. All participating columns must carry a `CategoricalMeasure`
(e.g. `NominalScale`).

```java
// Programmatic
Term t = interact("outlook", "temperature");  // outlook:temperature
Term t = interact("a", "b", "c");             // a:b:c

// String formula
Formula.of("play ~ a:b + c")
```

The resulting feature has a `NominalScale` whose levels are the Cartesian product of the
input levels, joined with `":"`:

```
dry:low, dry:high, wet:low, wet:high
```

---

### 4.8 Factor Crossing (`&&` / `^`)

`FactorCrossing` is syntactic sugar that generates all main effects **and** all
pairwise (or higher-order) interactions among a set of factors:

```
(a x b x c)     ≡  a + b + c + a:b + a:c + b:c + a:b:c
(a x b x c)^2   ≡  a + b + c + a:b + a:c + b:c        (interactions up to degree 2)
```

```java
// Full crossing of three factors
Term t = cross("a", "b", "c");               // (a x b x c)

// Crossing up to degree 2 only
Term t = cross(2, "a", "b", "c");            // (a x b x c)^2

// String formula
Formula.of("y ~ (a x b x c)^2")
Formula.of("y ~ (a x b x c)")
```

Combine with `delete` to remove specific interactions:

```java
Formula.of("y", cross("a","b","c"), delete(interact("a","b")))
// Adds a, b, c, a:c, b:c, a:b:c  (a:b removed)
```

---

### 4.9 Date / Time Features

The `Date` term extracts numeric sub-fields from `LocalDate`, `LocalDateTime`, or
`LocalTime` columns.

```java
date("timestamp", DateFeature.YEAR, DateFeature.MONTH, DateFeature.DAY_OF_MONTH)
date("birthday",  DateFeature.YEAR, DateFeature.DAY_OF_WEEK)
date("checkIn",   DateFeature.HOUR, DateFeature.MINUTE)
```

**Available `DateFeature` values:**

| Feature | Column types | Range | Measure |
|---------|-------------|-------|---------|
| `YEAR` | Date, DateTime | e.g. 2024 | — |
| `QUARTER` | Date, DateTime | 1–4 | — |
| `MONTH` | Date, DateTime | 1–12 | `NominalScale` (JANUARY…DECEMBER) |
| `WEEK_OF_YEAR` | Date, DateTime | 0–53 | — |
| `WEEK_OF_MONTH` | Date, DateTime | 0–5 | — |
| `DAY_OF_YEAR` | Date, DateTime | 1–366 | — |
| `DAY_OF_MONTH` | Date, DateTime | 1–31 | — |
| `DAY_OF_WEEK` | Date, DateTime | 1–7 | `NominalScale` (MONDAY…SUNDAY) |
| `HOUR` | Time, DateTime | 0–23 | — |
| `MINUTE` | Time, DateTime | 0–59 | — |
| `SECOND` | Time, DateTime | 0–59 | — |

> **Type safety:**  
> - Requesting `HOUR`/`MINUTE`/`SECOND` on a `Date` column throws `UnsupportedOperationException`.  
> - Requesting `YEAR`/`MONTH`/… on a `Time` column throws `UnsupportedOperationException`.  
> - `DateTime` columns support all features.

**String formula:**

```java
// Not currently parseable from a string; use the Java API:
Formula.rhs(date("timestamp", DateFeature.YEAR, DateFeature.MONTH))
```

---

### 4.10 Constant Values

`val(x)` creates a term that returns the same constant value for every row. Use it
together with arithmetic operators to encode fixed transformations.

```java
val(1)         // integer constant 1
val(0.5)       // double constant 0.5
val(100L)      // long constant 100
val(true)      // boolean constant true
val('A')       // char constant 'A'
val((byte)  1) // byte constant 1
val((short) 2) // short constant 2
val("label")   // Object constant — produces an object column
```

**Typical use-cases:**

```java
mul("price", val(1.08))       // apply 8% tax
add("age", val(-18))          // centre age at 18
div("bytes", val(1024 * 1024)) // convert bytes → MiB
```

---

### 4.11 Custom Lambdas

`Terms.of(...)` lets you attach any Java lambda as a formula term without writing a
new class. There are overloads for unary and binary functions returning `int`, `long`,
`double`, or an arbitrary object type.

#### Unary lambdas

```java
// ToIntFunction<T>
Term t = Terms.of("clip", "age", (Integer x) -> Math.max(0, Math.min(x, 100)));

// ToDoubleFunction<T>
Term t = Terms.of("normalize", "score",
                  (Double x) -> (x - mean) / stddev);

// Function<T, R> with explicit return class
Term t = Terms.of("bucket", "income", String.class,
                  (Double x) -> x < 50_000 ? "low" : x < 150_000 ? "mid" : "high");
```

#### Binary lambdas

```java
// ToDoubleBiFunction<T, U>
Term t = Terms.of("ratio", "numerator", "denominator",
                  (Double a, Double b) -> a / b);

// ToIntBiFunction<T, U>
Term t = Terms.of("diff_days", "start", "end",
                  (LocalDate a, LocalDate b) -> (int) ChronoUnit.DAYS.between(a, b));

// BiFunction<T, U, R>
Term t = Terms.of("concat", "first", "last", String.class,
                  (String a, String b) -> a + " " + b);
```

Use these terms just like any built-in term:

```java
Formula f = Formula.of("churn", dot(),
        Terms.of("tenure_months", "start_date", "end_date",
                 (LocalDate s, LocalDate e) ->
                     (int) ChronoUnit.MONTHS.between(s, e)));
```

---

## 5. Applying a Formula

### 5.1 Binding to a Schema

`formula.bind(StructType)` resolves column names to schema positions and compiles the
term tree into an efficient array of `Feature` objects. The result is the **predictor
schema** (`xschema`).

```java
StructType xschema = formula.bind(df.schema());
System.out.println(xschema);
```

Binding is **lazy and cached** — the first call per schema does the work; subsequent
calls with the same schema object return immediately. Binding is thread-local, so the
same `Formula` instance can be safely shared across threads.

### 5.2 Producing a DataFrame

`formula.frame(DataFrame)` returns a `DataFrame` containing the **response column
first**, followed by all predictor columns, exactly as specified by the formula.

```java
DataFrame out = formula.frame(df);
// Columns: [response, predictor1, predictor2, ...]
```

If the response column is absent from the data (e.g., when predicting on new data),
`frame()` still returns the predictor columns only.

### 5.3 Extracting Predictors Only

```java
DataFrame xdf = formula.x(df);
```

Returns only the predictor columns. Useful for scoring new observations.

### 5.4 Extracting the Response

```java
// As a ValueVector (for use with SMILE learners)
ValueVector y = formula.y(df);

// As a double value from a single Tuple
double yval = formula.y(tuple);

// As an int value from a single Tuple
int yint = formula.yint(tuple);
```

Throws `UnsupportedOperationException` if the formula has no response term.

### 5.5 Producing a Design Matrix

`formula.matrix(DataFrame)` converts the predictor `DataFrame` into a dense `DenseMatrix`
(suitable for linear algebra and gradient-based learners). Categorical columns are
**dummy encoded** automatically.

```java
DenseMatrix X = formula.matrix(df);          // with bias column (default)
DenseMatrix X = formula.matrix(df, true);    // with bias column
DenseMatrix X = formula.matrix(df, false);   // without bias column
```

The bias column (all-ones) is prepended when the formula has no explicit `Intercept(false)`
term, or when `bias=true` is passed explicitly.

### 5.6 Applying Row-by-Row to Tuples

```java
// Full (response + predictors) Tuple
Tuple yx = formula.apply(tuple);

// Predictors-only Tuple
Tuple x = formula.x(tuple);
```

These are useful in streaming/online scenarios where you process one row at a time.

---

## 6. Expanding a Formula

`formula.expand(StructType)` resolves the `.` (Dot) and `FactorCrossing` meta-terms
against an actual schema, returning a new `Formula` where every term is a concrete
`Variable`, `FactorInteraction`, or arithmetic expression.

```java
Formula f = Formula.of("salary ~ . - id + log(age)");
Formula expanded = f.expand(df.schema());
System.out.println(expanded);
// salary ~ gender + birthday + name + log(age)   (id was deleted)
```

This is useful for inspecting exactly which columns a formula will consume before
fitting a model.

---

## 7. Intercept / Bias Control

| Formula string | `hasBias()` | Effect on `matrix()` |
|---|---|---|
| `y ~ x` | `true` | Bias column prepended |
| `y ~ x + 1` | `true` | Bias column prepended |
| `y ~ x + 0` | `false` | No bias column |
| `y ~ x - 1` | `false` | No bias column |
| `matrix(df, true)` | (override) | Bias column forced on |
| `matrix(df, false)` | (override) | Bias column forced off |

```java
// Fit line through origin
Formula f = Formula.of("y ~ x + 0");
DenseMatrix X = f.matrix(df);   // single column, no bias

// Explicit bias override
DenseMatrix X = f.matrix(df, true);  // force bias regardless of formula
```

---

## 8. Nullable Columns

SMILE `DataFrame` supports nullable columns (backed by `NullableDoubleVector`, etc.).
Formula arithmetic propagates nulls correctly: if **any** operand of `+`, `-`, `*`, `/`
is `null` for a given row, the result for that row is `null`.

```java
// salary is nullable; age is not
Formula f = Formula.rhs(add("salary", "age"));
DataFrame out = f.frame(df);
// Rows where salary == null → result is null
```

When a nullable column is converted to a design matrix via `matrix()`, null values
become `Double.NaN`.

---

## 9. Working with Categorical Variables

Categorical columns carry a `CategoricalMeasure` (usually `NominalScale` or
`OrdinalScale`). The formula handles them in two ways:

### As plain predictors

Including a categorical variable directly passes through its integer encoding (the
underlying code value in the `NominalScale`).

```java
Formula.of("play", $("outlook"), $("temperature"))
```

### As dummy-encoded predictors (in `matrix()`)

When `formula.matrix(df)` is called, all categorical predictors are automatically
**dummy-encoded** (one binary column per level, with the first level dropped as the
reference). This is identical to R's default treatment of factors.

```java
// "outlook" has levels {sunny, overcast, rainy}
// matrix() produces two binary columns: outlook_overcast, outlook_rainy
DenseMatrix X = formula.matrix(df);
```

### As interaction terms

Use `interact` or `cross` (see §4.7 and §4.8) to build interaction features from
categorical columns. The resulting feature is itself categorical with a `NominalScale`
whose levels are the Cartesian product.

---

## 10. Date / Time Tutorial

This tutorial shows how to enrich a sales DataFrame with calendar features.

```java
import java.time.LocalDate;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.formula.DateFeature;
import static smile.data.formula.Terms.*;

// Suppose df has columns: order_date (LocalDate), amount (double), region (String)

Formula f = Formula.of("amount",
        dot(),                      // include region
        date("order_date",          // extract calendar features
                DateFeature.YEAR,
                DateFeature.QUARTER,
                DateFeature.MONTH,
                DateFeature.DAY_OF_WEEK));

DataFrame out = f.frame(df);
// Columns: amount, region, order_date_YEAR, order_date_QUARTER,
//          order_date_MONTH, order_date_DAY_OF_WEEK
//
// order_date_MONTH    has NominalScale (JANUARY … DECEMBER)
// order_date_DAY_OF_WEEK has NominalScale (MONDAY … SUNDAY)
```

Use `getString()` on the result to decode the nominal level names:

```java
System.out.println(out.getString(0, 3));  // e.g. "MARCH"
System.out.println(out.getString(0, 4));  // e.g. "TUESDAY"
```

For `LocalDateTime` columns all 11 features are available:

```java
date("created_at",
     DateFeature.YEAR, DateFeature.MONTH, DateFeature.DAY_OF_MONTH,
     DateFeature.HOUR, DateFeature.MINUTE)
```

For `LocalTime`-only columns use only time features:

```java
date("open_time", DateFeature.HOUR, DateFeature.MINUTE)
```

---

## 11. Custom Transformations Tutorial

This tutorial builds a feature-engineering pipeline for a lending dataset that has
`loan_amount`, `income`, `start_date`, and `end_date` columns.

```java
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import smile.data.formula.Formula;
import static smile.data.formula.Terms.*;

Formula f = Formula.of("default",

    // Debt-to-income ratio
    div("loan_amount", "income"),

    // Log-transform income to reduce skew
    log("income"),

    // Loan duration in months via a custom binary lambda
    Terms.of("duration_months", "start_date", "end_date",
             (LocalDate s, LocalDate e) ->
                 (int) ChronoUnit.MONTHS.between(s, e)),

    // Flag high-value loans (> 50 000) via a custom unary lambda
    Terms.of("high_value", "loan_amount", String.class,
             (Double x) -> x > 50_000 ? "yes" : "no"),

    // Square-root of loan amount to reduce skew
    sqrt("loan_amount")
);

// Bind to schema to inspect output columns
var schema = f.bind(df.schema());
System.out.println(schema);

// Produce design matrix with bias
var X = f.matrix(df);
var y = f.y(df);
```

---

## 12. Thread Safety & Lifecycle

`Formula` implements `AutoCloseable`. Internally it stores the compiled `Feature` array
in a `ThreadLocal`, so **multiple threads can share a single `Formula` instance** and
bind it to the same or different schemas concurrently.

```java
// Safe: one Formula object, many threads
try (Formula f = Formula.of("y ~ x")) {
    parallelStream.forEach(df -> {
        var X = f.matrix(df);  // thread-safe
    });
}  // close() removes thread-local binding and avoids memory leaks
```

**Best practice — always close in a try-with-resources** when the formula is used
inside a long-lived thread pool:

```java
try (Formula f = Formula.lhs("label")) {
    // ... use f ...
}
```

Calling `bind()` a second time with the **same `StructType` object** is a no-op
(cached). Passing a different schema re-binds and replaces the cached binding.

---

## 13. API Cheat Sheet

### `Formula` static factories

| Method | Description |
|--------|-------------|
| `Formula.of(String)` | Parse formula from R-style string |
| `Formula.of(String, String...)` | Response string + predictor column names |
| `Formula.of(String, Term...)` | Response string + predictor terms |
| `Formula.of(Term, Term...)` | Response term + predictor terms |
| `Formula.lhs(String)` | Response only; predictors = `.` |
| `Formula.lhs(Term)` | Response term only; predictors = `.` |
| `Formula.rhs(String...)` | No response; predictor column names |
| `Formula.rhs(Term...)` | No response; predictor terms |

### `Formula` instance methods

| Method | Returns | Description |
|--------|---------|-------------|
| `bind(StructType)` | `StructType` | Bind to schema; returns predictor schema |
| `expand(StructType)` | `Formula` | Expand `.` and crossings against schema |
| `frame(DataFrame)` | `DataFrame` | Response + predictor columns |
| `x(DataFrame)` | `DataFrame` | Predictor columns only |
| `y(DataFrame)` | `ValueVector` | Response column |
| `matrix(DataFrame)` | `DenseMatrix` | Dummy-encoded design matrix (with bias) |
| `matrix(DataFrame, boolean)` | `DenseMatrix` | Design matrix with explicit bias flag |
| `apply(Tuple)` | `Tuple` | Response + predictors for one row |
| `x(Tuple)` | `Tuple` | Predictors for one row |
| `y(Tuple)` | `double` | Response value (double) for one row |
| `yint(Tuple)` | `int` | Response value (int) for one row |
| `response()` | `Term` | The response term (may be `null`) |
| `predictors()` | `Term[]` | The predictor terms |
| `toString()` | `String` | R-style formula string |
| `close()` | `void` | Release thread-local binding |

### `Terms` static builders (import static)

| Method | Symbol | Description |
|--------|--------|-------------|
| `$(String)` | variable | Create variable (auto-detects function names) |
| `dot()` | `.` | All remaining columns |
| `delete(String/Term)` | `- x` | Delete term |
| `interact(String...)` | `a:b:c` | Factor interaction |
| `cross(String...)` | `(a x b)` | Full factor crossing |
| `cross(int, String...)` | `(a x b)^n` | Factor crossing to degree n |
| `date(String, DateFeature...)` | — | Date/time feature extraction |
| `val(x)` | constant | Constant term |
| `add(a, b)` | `a + b` | Addition |
| `sub(a, b)` | `a - b` | Subtraction |
| `mul(a, b)` | `a * b` | Multiplication |
| `div(a, b)` | `a / b` | Division |
| `abs(x)` | — | Absolute value |
| `ceil(x)` | — | Ceiling |
| `floor(x)` | — | Floor |
| `round(x)` | — | Round |
| `rint(x)` | — | Round to even |
| `exp(x)` | — | e^x |
| `expm1(x)` | — | e^x − 1 |
| `log(x)` | — | ln(x) |
| `log1p(x)` | — | ln(1+x) |
| `log2(x)` | — | log₂(x) |
| `log10(x)` | — | log₁₀(x) |
| `sqrt(x)` | — | √x |
| `cbrt(x)` | — | ∛x |
| `sin(x)` | — | sin(x) |
| `cos(x)` | — | cos(x) |
| `tan(x)` | — | tan(x) |
| `asin(x)` | — | arcsin(x) |
| `acos(x)` | — | arccos(x) |
| `atan(x)` | — | arctan(x) |
| `sinh(x)` | — | sinh(x) |
| `cosh(x)` | — | cosh(x) |
| `tanh(x)` | — | tanh(x) |
| `signum(x)` | — | −1.0, 0.0, or 1.0 |
| `sign(x)` | — | −1, 0, or 1 (integer) |
| `ulp(x)` | — | Unit of least precision |
| `of(name, x, ToIntFunction)` | — | Custom int transform |
| `of(name, x, ToLongFunction)` | — | Custom long transform |
| `of(name, x, ToDoubleFunction)` | — | Custom double transform |
| `of(name, x, Class, Function)` | — | Custom object transform |
| `of(name, x, y, ToIntBiFunction)` | — | Custom int bi-transform |
| `of(name, x, y, ToLongBiFunction)` | — | Custom long bi-transform |
| `of(name, x, y, ToDoubleBiFunction)` | — | Custom double bi-transform |
| `of(name, x, y, Class, BiFunction)` | — | Custom object bi-transform |


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

