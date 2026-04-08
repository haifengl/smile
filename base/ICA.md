# Independent Component Analysis (ICA) User Guide

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Mathematical Background](#mathematical-background)
   - [The ICA Model](#the-ica-model)
   - [Assumptions](#assumptions)
   - [Non-Gaussianity as Independence Proxy](#non-gaussianity-as-independence-proxy)
   - [The FastICA Algorithm](#the-fastica-algorithm)
   - [Data Whitening](#data-whitening)
4. [Contrast Functions](#contrast-functions)
   - [LogCosh (default)](#logcosh-default)
   - [Gaussian (Exp)](#gaussian-exp)
   - [Kurtosis](#kurtosis)
   - [Choosing a Contrast Function](#choosing-a-contrast-function)
   - [Custom Contrast Functions](#custom-contrast-functions)
5. [Hyperparameters](#hyperparameters)
   - [Options Record](#options-record)
   - [Persisting Options with Properties](#persisting-options-with-properties)
6. [Input Data Layout](#input-data-layout)
7. [Working with the Result](#working-with-the-result)
8. [Practical Guidance](#practical-guidance)
   - [Number of Components](#number-of-components)
   - [Convergence and Iteration Limit](#convergence-and-iteration-limit)
   - [Reproducibility and Seeding](#reproducibility-and-seeding)
   - [Sign and Ordering Ambiguity](#sign-and-ordering-ambiguity)
   - [Limitations](#limitations)
9. [Complete Examples](#complete-examples)
   - [Cocktail Party Problem](#cocktail-party-problem)
   - [Feature Extraction](#feature-extraction)
   - [Configuring via Properties](#configuring-via-properties)
10. [ICA vs PCA](#ica-vs-pca)
11. [References](#references)

---

## Overview

The `smile.ica` package provides **Independent Component Analysis (ICA)** via the
**FastICA** algorithm invented by Aapo Hyvärinen. ICA is a blind source-separation
technique that decomposes a set of mixed observed signals into a set of maximally
statistically independent components.

| Class / Record | Role |
|---|---|
| `ICA` | The fitted model; holds the unmixing matrix |
| `ICA.Options` | Hyperparameters: contrast function, iteration limit, tolerance |
| `LogCosh` | Default contrast function — general purpose |
| `Exp` | Gaussian contrast function — super-Gaussian / robust |
| `Kurtosis` | Kurtosis contrast function — simple, sensitive to outliers |

All contrast-function classes implement `smile.util.function.DifferentiableFunction`
and `java.io.Serializable`, so custom implementations can be serialized alongside
the model.

---

## Quick Start

```java
import smile.ica.*;
import smile.math.MathEx;

// data[i] is sample i, data[i][j] is the j-th mixed signal value.
// Rearrange to variables × samples before calling fit().
double[][] data = /* load your mixed-signal matrix (samples × variables) */;
double[][] X    = MathEx.transpose(data); // variables × samples

// Fit ICA — extract 2 independent components using default settings.
MathEx.setSeed(19650218);       // for reproducibility
ICA ica = ICA.fit(X, 2);

// Each row of components() is one unit-norm independent component vector.
double[][] components = ica.components();
System.out.printf("Component 0, sample 0: %.5f%n", components[0][0]);
System.out.printf("Component 1, sample 0: %.5f%n", components[1][0]);
```

---

## Mathematical Background

### The ICA Model

ICA assumes the observed signal vector **x** is a linear instantaneous mixture
of statistically independent source signals **s**:

```
x = A · s
```

where:
- **x** ∈ ℝᵐ is the vector of observed (mixed) signals at one time instant
- **s** ∈ ℝᵖ is the vector of unknown independent source signals
- **A** ∈ ℝ^{m×p} is the unknown mixing matrix

The goal is to estimate the **unmixing matrix W** such that:

```
ŝ = W · x
```

recovers estimates of the original sources **s** up to permutation and scaling.

### Assumptions

ICA requires the following assumptions to hold:

1. **Statistical independence** — the source signals `s₁, s₂, …, sₚ` are
   mutually statistically independent.
2. **Non-Gaussianity** — at most one source may be Gaussian (by the Central
   Limit Theorem, a mixture of independent sources becomes more Gaussian; ICA
   reverses this by maximizing non-Gaussianity).
3. **Linearity** — the mixing is instantaneous and linear (no time delays or
   convolution).
4. **Sufficient observations** — at least as many observed signals as source
   signals (`m ≥ p`).

### Non-Gaussianity as Independence Proxy

FastICA measures non-Gaussianity using **negentropy** approximations based on a
non-quadratic, non-linear contrast function G(u). Negentropy is always
non-negative and equals zero if and only if the variable is Gaussian.

The negentropy approximation is:

```
J(y) ≈ [E{G(y)} - E{G(ν)}]²
```

where ν is a standard Gaussian variable and the expectation is over samples.
Maximizing this over unit-norm directions w gives the most non-Gaussian
(most independent) projection.

### The FastICA Algorithm

FastICA uses a **fixed-point iteration** to find each unmixing vector **w**:

```
w ← (1/n) · X · g(Xᵀw) − mean(g′(Xᵀw)) · w
w ← w / ‖w‖
```

where:
- **X** ∈ ℝ^{n×m} is the whitened data matrix (n samples, m variables)
- `g = G′` is the first derivative of the contrast function G
- `g′ = G″` is the second derivative

After convergence of each component, **deflation orthogonalization** removes
its contribution so that subsequent components are orthogonal:

```
w ← w − Σₖ (wₖᵀ w) wₖ    for all previously found components wₖ
w ← w / ‖w‖
```

Convergence is declared when:

```
min(‖w − w_old‖, ‖w + w_old‖) < tol
```

The two-case test handles the sign ambiguity: a direction and its negative
represent the same component.

### Data Whitening

Before applying FastICA the data is **pre-whitened** (sphered):

1. **Center** — subtract the mean of each observed variable.
2. **Eigendecompose** — compute the eigendecomposition of the sample covariance
   matrix `Cₓ = XᵀX / n = E D Eᵀ`.
3. **Scale** — form the whitened data `Z = X E D^{-1/2}`.

After whitening, `E{ZᵀZ} = I`, so the covariance is the identity matrix.
Whitening reduces the ICA problem to finding an orthogonal rotation, which
simplifies the optimization considerably.

> **Numerical note:** If any eigenvalue of the covariance matrix is smaller
> than `1e-8`, an `IllegalArgumentException` is thrown — the data is nearly
> linearly dependent and ICA is not applicable without dimensionality reduction.

---

## Contrast Functions

A contrast function G must be:
- non-quadratic
- non-linear
- twice differentiable

The three built-in contrast functions cover the most common use cases.

### LogCosh (default)

```java
new LogCosh()   // or  new ICA.Options("LogCosh", 100)
```

| Property | Value |
|---|---|
| G(u) | `log(cosh(u))`  ≈ `\|u\| − log 2` for large \|u\| |
| G′(u) = g(u) | `tanh(u)` |
| G″(u) = g′(u) | `1 − tanh²(u)` |
| Implementation | Numerically stable: `\|u\| + log1p(exp(−2\|u\|)) − log 2` |
| Suitable for | **General purpose** — balances robustness and accuracy |
| Signal types | Sub-Gaussian and super-Gaussian sources |

`LogCosh` is the recommended default. It is smooth, bounded in derivative, and
avoids the numerical overflow that `log(cosh(x))` would produce for `|x| > 710`.

### Gaussian (Exp)

```java
new Exp()   // or  new ICA.Options("Gaussian", 100)
```

| Property | Value |
|---|---|
| G(u) | `−exp(−u²/2)` |
| G′(u) = g(u) | `u · exp(−u²/2)` |
| G″(u) = g′(u) | `(1 − u²) · exp(−u²/2)` |
| Suitable for | **Super-Gaussian sources**, robustness to outliers |
| Signal types | Sparse signals, impulsive noise |

The Gaussian contrast function down-weights extreme values, making it more
robust when the data contain outliers.

### Kurtosis

```java
new Kurtosis()   // or  new ICA.Options("Kurtosis", 100)
```

| Property | Value |
|---|---|
| G(u) | `u⁴ / 4` |
| G′(u) = g(u) | `u³` |
| G″(u) = g′(u) | `3u²` |
| Suitable for | Simple / educational use, clean data |
| Signal types | Any, but **sensitive to outliers** |

Kurtosis is a classical measure of non-Gaussianity. However, because G grows as
`u⁴`, it is highly sensitive to large values (outliers). Prefer `LogCosh` or
`Exp` for real data.

### Choosing a Contrast Function

| Scenario | Recommended |
|---|---|
| No prior knowledge | `LogCosh` |
| Super-Gaussian signals (e.g., speech, sparse) | `Exp` |
| Sub-Gaussian signals (e.g., uniform) | `LogCosh` |
| Clean data, no outliers, quick test | `Kurtosis` |
| Outlier-prone data | `Exp` |

### Custom Contrast Functions

Implement `DifferentiableFunction` and optionally `Serializable`:

```java
import smile.util.function.DifferentiableFunction;
import java.io.Serializable;

public class MyContrast implements DifferentiableFunction, Serializable {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    @Override
    public double f(double x) {
        // G(u) — the contrast function itself (not required by FastICA
        // but required by the DifferentiableFunction interface)
        return Math.log1p(x * x);
    }

    @Override
    public double g(double x) {
        // G′(u) — first derivative
        return 2.0 * x / (1.0 + x * x);
    }

    @Override
    public double g2(double x) {
        // G″(u) — second derivative
        double d = 1.0 + x * x;
        return 2.0 * (1.0 - x * x) / (d * d);
    }
}

// Use it
ICA.Options opts = new ICA.Options(new MyContrast(), 200, 1E-5);
ICA ica = ICA.fit(X, p, opts);
```

---

## Hyperparameters

### Options Record

```java
ICA.Options(DifferentiableFunction contrast, int maxIter, double tol)
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `contrast` | `DifferentiableFunction` | `new LogCosh()` | Contrast function G |
| `maxIter` | `int` | `100` | Maximum fixed-point iterations per component |
| `tol` | `double` | `1e-4` | Convergence tolerance on ‖w − w_old‖ |

**Convenience constructors:**

```java
// Default tolerance 1e-4
new ICA.Options(new LogCosh(), 100)

// Custom tolerance
new ICA.Options(new LogCosh(), 200, 1E-6)

// By name
new ICA.Options("LogCosh",  100)
new ICA.Options("Gaussian", 100)
new ICA.Options("Kurtosis", 100)
```

### Persisting Options with Properties

`Options` can be serialized to/from `java.util.Properties`, which is convenient
for configuration files or command-line parameter passing.

```java
// Save
ICA.Options opts = new ICA.Options(new Exp(), 150, 1E-5);
Properties props = opts.toProperties();
// props contains:
//   smile.ica.contrast   = Gaussian
//   smile.ica.iterations = 150
//   smile.ica.tolerance  = 1.0E-5

// Restore
ICA.Options restored = ICA.Options.of(props);
```

Property keys:

| Key | Value |
|---|---|
| `smile.ica.contrast` | `"LogCosh"`, `"Gaussian"`, `"Kurtosis"`, or fully-qualified class name |
| `smile.ica.iterations` | integer string |
| `smile.ica.tolerance` | double string |

When a fully-qualified class name is stored, `Options.of()` instantiates it via
reflection using its no-argument constructor — so custom contrast classes must
have a public no-arg constructor.

---

## Input Data Layout

`ICA.fit()` expects data in **variables × samples** layout:

```
data[i][j]  →  value of the i-th observed signal (variable) at time j (sample)
```

- Rows: observed signals / channels (dimension = m)
- Columns: time steps / observations (dimension = n)

If your data is in the conventional **samples × variables** layout (each row is
one observation), transpose it first:

```java
double[][] samplesXvars = /* ... */;              // shape: n × m
double[][] X = MathEx.transpose(samplesXvars);    // shape: m × n
ICA ica = ICA.fit(X, p);
```

The number of components `p` must satisfy `1 ≤ p ≤ m` (number of signals).

---

## Working with the Result

`ICA` is a Java `record` with a single field:

```java
double[][] components = ica.components();
```

- `components.length` == `p` (number of independent components extracted)
- `components[i].length` == `n` (number of samples)
- Each row `components[i]` is a **unit-norm** vector in the whitened sample
  space representing the i-th independent component.
- Rows are **mutually orthogonal**: `components[i] · components[j] ≈ 0` for
  `i ≠ j`.

`ICA` implements `java.io.Serializable` (serialVersionUID = 2), so models can
be saved and loaded with standard Java object serialization or any compatible
framework.

---

## Practical Guidance

### Number of Components

Set `p` to the number of source signals you believe are present. In the absence
of domain knowledge:

- Start with `p = m` (full decomposition) to see all components, then keep
  only the most interpretable ones.
- Use domain knowledge or cross-validation to select a smaller `p`.
- `p` must not exceed `m` (the number of observed signals).

### Convergence and Iteration Limit

If a component does not converge within `maxIter` iterations a `WARN`-level
SLF4J message is emitted:

```
Component 2 did not converge in 100 iterations.
```

Suggested remedies:

1. **Increase `maxIter`** — try 200 or 500.
2. **Loosen `tol`** — e.g., `1e-3` if sub-sample precision is acceptable.
3. **Change contrast function** — `Exp` sometimes converges faster for
   super-Gaussian sources.
4. **Check data quality** — near-collinear signals or strong outliers can
   prevent convergence.

### Reproducibility and Seeding

FastICA initializes **w** with random Gaussian vectors. Results are
non-deterministic across runs. For reproducible output call:

```java
MathEx.setSeed(19650218);
ICA ica = ICA.fit(X, p);
```

### Sign and Ordering Ambiguity

ICA has two fundamental ambiguities that cannot be resolved algorithmically:

1. **Sign** — `w` and `−w` define the same independent subspace. The sign of
   each extracted component is arbitrary.
2. **Order** — there is no canonical ordering of the components. The order
   may vary between runs even with the same seed.

If consistent ordering matters, sort the components by a domain-specific
criterion (e.g., variance of the recovered source, frequency content, etc.)
after fitting.

### Limitations

| Limitation | Details |
|---|---|
| Linear mixing only | ICA does not handle nonlinear or convolutive mixtures |
| No temporal structure | FastICA ignores time-ordering; for time-series use SOBI or TDSEP |
| Gaussian sources | Cannot separate more than one Gaussian source |
| Square or over-determined systems | Requires `m ≥ p` (at least as many sensors as sources) |
| Outlier sensitivity (`Kurtosis`) | Use `LogCosh` or `Exp` for noisy real data |

---

## Complete Examples

### Cocktail Party Problem

The classic motivating example: separate two mixed speech-like signals.

```java
import smile.ica.*;
import smile.math.MathEx;

MathEx.setSeed(12345);

int T = 2000;

// Two non-Gaussian source signals
double[] s1 = new double[T];   // sawtooth
double[] s2 = new double[T];   // square wave
for (int t = 0; t < T; t++) {
    s1[t] = 2.0 * ((t % 50) / 50.0) - 1.0;
    s2[t] = Math.signum(Math.sin(2 * Math.PI * t / 30.0));
}

// Linear mixing: two microphones
double[][] mixed = new double[T][2];
for (int t = 0; t < T; t++) {
    mixed[t][0] = 0.7 * s1[t] + 0.3 * s2[t];   // microphone 1
    mixed[t][1] = 0.4 * s1[t] + 0.6 * s2[t];   // microphone 2
}

// Fit ICA (must transpose to variables × samples layout)
ICA ica = ICA.fit(MathEx.transpose(mixed), 2);

double[][] w = ica.components();
System.out.printf("‖w₀‖ = %.6f (should be 1.0)%n", MathEx.norm(w[0]));
System.out.printf("‖w₁‖ = %.6f (should be 1.0)%n", MathEx.norm(w[1]));
System.out.printf("w₀ · w₁ = %.6f (should be ≈ 0)%n", MathEx.dot(w[0], w[1]));
```

### Feature Extraction

ICA can be used for feature extraction as an alternative to PCA. Unlike PCA,
ICA components are statistically independent (not just uncorrelated), which
makes them more meaningful for non-Gaussian data such as natural images or
EEG signals.

```java
import smile.ica.*;
import smile.math.MathEx;

// Suppose eegData is channels × timepoints
double[][] eegData = loadEEG(); // shape: 64 × 10000

MathEx.setSeed(42);

// Extract 20 independent components from 64-channel EEG
ICA.Options opts = new ICA.Options(new LogCosh(), 300, 1E-5);
ICA ica = ICA.fit(eegData, 20, opts);

// The components matrix: 20 × 10000
double[][] icComponents = ica.components();

// Inspect convergence via SLF4J logging output.
// Each row icComponents[i] is a unit-norm vector in the whitened sample space.
```

### Configuring via Properties

Useful for externalizing configuration in applications or ML pipelines:

```java
import smile.ica.*;
import java.util.Properties;

// ---- at configuration time ----
Properties props = new Properties();
props.setProperty("smile.ica.contrast",   "Gaussian");
props.setProperty("smile.ica.iterations", "200");
props.setProperty("smile.ica.tolerance",  "1E-5");

// ---- at fit time ----
ICA.Options opts = ICA.Options.of(props);  // throws ReflectiveOperationException
ICA ica = ICA.fit(X, p, opts);

// ---- serialize the options for later ----
Properties savedProps = opts.toProperties();
// savedProps.getProperty("smile.ica.contrast") == "Gaussian"
```

---

## ICA vs PCA

Both ICA and PCA decompose a multivariate signal, but with different goals:

| Aspect | PCA | ICA |
|---|---|---|
| Criterion | Maximum variance | Maximum statistical independence |
| Output | Uncorrelated components | Independent components |
| Gaussian data | Optimal | Undefined (see Limitations) |
| Non-Gaussian data | Sub-optimal | Optimal |
| Ordering | Decreasing variance | Arbitrary |
| Sign | Consistent (largest projection positive) | Arbitrary |
| Preprocessing | No | Requires whitening (done automatically) |
| Typical use | Dimensionality reduction, compression | Blind source separation, artifact removal |

In practice, **PCA whitening is applied as a pre-processing step inside
FastICA** — the two methods are complementary rather than competing.

---

## References

1. Aapo Hyvärinen. *Fast and robust fixed-point algorithms for independent
   component analysis.* IEEE Transactions on Neural Networks, 10(3):626–634,
   1999.

2. Aapo Hyvärinen and Erkki Oja. *Independent component analysis: Algorithms
   and applications.* Neural Networks, 13(4–5):411–430, 2000.

3. Aapo Hyvärinen, Juha Karhunen, and Erkki Oja. *Independent Component
   Analysis.* Wiley, 2001.

4. Pierre Comon. *Independent component analysis, a new concept?* Signal
   Processing, 36(3):287–314, 1994.


---

*Package:* `smile.ica` · *Module:* `smile-base` · *Since:* SMILE 2.0

