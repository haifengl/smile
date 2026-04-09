# SMILE — Interpolation User Guide

The `smile.interpolation` package provides a comprehensive suite of interpolation
methods for one-dimensional, two-dimensional regular-grid, and scattered-data
problems.

---

## Contents

1. [Concepts](#1-concepts)
2. [1-D methods on a regular grid](#2-1-d-methods-on-a-regular-grid)
   - [LinearInterpolation](#21-linearinterpolation)
   - [CubicSplineInterpolation1D](#22-cubicsplineinterpolation1d)
3. [2-D methods on a regular grid](#3-2-d-methods-on-a-regular-grid)
   - [BilinearInterpolation](#31-bilinearinterpolation)
   - [BicubicInterpolation](#32-bicubicinterpolation)
   - [CubicSplineInterpolation2D](#33-cubicsplineinterpolation2d)
4. [Scattered-data methods](#4-scattered-data-methods)
   - [ShepardInterpolation](#41-shepardinterpolation)
   - [RBFInterpolation](#42-rbfinterpolation)
   - [KrigingInterpolation](#43-kriginginterpolation)
5. [Grid inpainting](#5-grid-inpainting)
   - [LaplaceInterpolation](#51-laplaceinterpolation)
6. [Variograms](#6-variograms)
7. [Choosing a method](#7-choosing-a-method)
8. [Quick-reference table](#8-quick-reference-table)

---

## 1. Concepts

**Interpolation** constructs a function that passes *exactly* through a set of
known data points `(x_i, y_i)`.  This is different from *regression*, which only
requires the function to be close to the data.

### Interfaces

| Interface | Purpose |
|---|---|
| `Interpolation` | 1-D interpolant — `double interpolate(double x)` |
| `Interpolation2D` | 2-D interpolant — `double interpolate(double x1, double x2)` |

Both interfaces extend `Serializable`, so interpolants can be persisted and
reloaded.

### Correlated lookup acceleration

All `AbstractInterpolation` subclasses automatically detect whether consecutive
calls are *correlated* (moving across the table sequentially).  When they are, a
fast **hunt** algorithm is used; otherwise a full **bisection** search is
performed.  This is invisible to the caller but makes dense sequential evaluation
significantly faster.

---

## 2. 1-D Methods on a Regular Grid

These methods require the x-values to be **monotone** (either increasing or
decreasing).

### 2.1 LinearInterpolation

Piecewise linear interpolation — each pair of adjacent knots is joined by a
straight line segment.

| Property | Value |
|---|---|
| Continuity | C⁰ (function, but not derivative, is continuous at knots) |
| Complexity | O(log n) per query (bisection) |
| Extrapolation | Extends the slope of the nearest end segment |
| Best for | Simple, fast approximations; step-function-like data |

```java
import smile.interpolation.LinearInterpolation;

double[] x = {0, 1, 2, 3, 4, 5, 6};
double[] y = {0, 0.8415, 0.9093, 0.1411, -0.7568, -0.9589, -0.2794};

LinearInterpolation interp = new LinearInterpolation(x, y);

System.out.println(interp.interpolate(2.5)); // ≈ 0.5252
System.out.println(interp.interpolate(0.0)); // 0.0  (exact at knot)
```

### 2.2 CubicSplineInterpolation1D

**Natural cubic spline** — piecewise cubic polynomials with continuous first and
second derivatives.  The "natural" boundary condition sets the second derivative
to zero at both endpoints.

| Property | Value |
|---|---|
| Continuity | C² (function, first and second derivatives are continuous) |
| Complexity | O(n) construction; O(log n) per query |
| Extrapolation | Extends the cubic polynomial at the nearest end segment |
| Best for | Smooth functions; much better approximation than linear for C² data |

```java
import smile.interpolation.CubicSplineInterpolation1D;

double[] x = {0, 1, 2, 3, 4, 5, 6};
double[] y = {0, 0.8415, 0.9093, 0.1411, -0.7568, -0.9589, -0.2794};

CubicSplineInterpolation1D spline = new CubicSplineInterpolation1D(x, y);

System.out.println(spline.interpolate(2.5)); // ≈ 0.5962 (closer to sin(2.5)≈0.5985)
```

#### Cubic spline vs. linear accuracy

```java
double trueValue = Math.sin(2.5);       // 0.5985
double errLinear = Math.abs(linear.interpolate(2.5) - trueValue); // ≈ 0.073
double errCubic  = Math.abs(spline.interpolate(2.5) - trueValue); // ≈ 0.002
```

The cubic spline is about 35× more accurate here.

#### Natural-spline boundary note

The natural (zero-second-derivative) boundary conditions mean the spline does
not reproduce polynomials of degree > 1 exactly near the endpoints.  For
functions with known non-zero second derivatives at the boundary, consider
clamped (specified-derivative) boundary conditions if your application requires
that precision.

---

## 3. 2-D Methods on a Regular Grid

All three 2-D methods require the data to lie on a **rectangular grid** defined
by two monotone coordinate arrays `x1[]` and `x2[]` and a value matrix
`y[x1.length][x2.length]`.

### 3.1 BilinearInterpolation

Extends linear interpolation to two dimensions by performing one linear
interpolation per dimension in sequence.

| Property | Value |
|---|---|
| Continuity | C⁰ inside cells; discontinuous first derivatives across cell boundaries |
| Best for | Fast lookup on dense grids; image scaling |

```java
import smile.interpolation.BilinearInterpolation;

double[] x1 = {1950, 1960, 1970, 1980, 1990};
double[] x2 = {10, 20, 30};
double[][] y = {
    {150.697, 199.592, 187.625},
    {179.323, 195.072, 250.287},
    {203.212, 179.092, 322.767},
    {226.505, 153.706, 426.730},
    {249.633, 120.281, 598.243}
};

BilinearInterpolation interp = new BilinearInterpolation(x1, x2, y);
System.out.println(interp.interpolate(1975, 15)); // ≈ 190.6
```

At the midpoint of a cell, bilinear interpolation returns the exact average of
the four corner values.

### 3.2 BicubicInterpolation

Uses a 4×4 neighbourhood of grid values at each query, computing 16 coefficients
that guarantee continuity of the value, both first partial derivatives, and the
cross-derivative `∂²f/∂x₁∂x₂`.

| Property | Value |
|---|---|
| Continuity | C¹ plus cross-derivative; second partial derivatives may be discontinuous |
| Best for | Image resampling, smooth surface rendering |
| Requirement | At least 4 × 4 grid points |

```java
import smile.interpolation.BicubicInterpolation;

BicubicInterpolation interp = new BicubicInterpolation(x1, x2, y);

// At a knot — exact reproduction
System.out.println(interp.interpolate(1970, 10)); // 203.212

// Interior point — smoother than bilinear
System.out.println(interp.interpolate(1975, 15)); // ≈ 175.7
```

### 3.3 CubicSplineInterpolation2D

Applies 1-D natural cubic splines **first along x2 at each x1 row**, then
**along x1** through the intermediate results.  This guarantees continuity of
both first and second partial derivatives, unlike bicubic interpolation.

| Property | Value |
|---|---|
| Continuity | C² in each dimension separately |
| Best for | Scientific data that is known to be twice-differentiable |
| Requirement | At least 2 points in each dimension |

```java
import smile.interpolation.CubicSplineInterpolation2D;

CubicSplineInterpolation2D interp = new CubicSplineInterpolation2D(x1, x2, y);

System.out.println(interp.interpolate(1975, 15)); // ≈ 168.0
System.out.println(interp.interpolate(1975, 25)); // ≈ 244.3
```

#### Comparing 2-D methods

| Method | Smoothness | Speed | Artifacts |
|---|---|---|---|
| Bilinear | C⁰ | Fastest | Visible edges at cell boundaries |
| Bicubic | C¹ + cross-deriv | Fast | Subtle ringing near sharp changes |
| Cubic Spline 2D | C² per-axis | Moderate | Fewest near smooth data |

---

## 4. Scattered-Data Methods

These methods work for data points that are **irregularly distributed** in space
— no grid is required.  They all implement the `interpolate(double... x)` pattern
(variadic `double[]` input) rather than a fixed-arity 2-D call.

### 4.1 ShepardInterpolation

Shepard (inverse-distance weighting) interpolation is the simplest scattered-data
method.  The interpolated value at a query point `x` is a weighted average of all
training values:

```
f(x) = Σ w_i(x) y_i / Σ w_i(x),   w_i(x) = ||x - x_i||^{-p}
```

Because `w_i → ∞` as `x → x_i`, the interpolant passes exactly through every
training point.

| Property | Value |
|---|---|
| Complexity | O(n) per query |
| Default p | 2 |
| Recommended p | 1 < p ≤ 3 |
| Best for | Quick, allocation-free scattered-data approximation for large n |

**Multi-dimensional:**

```java
import smile.interpolation.ShepardInterpolation;

double[][] x = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
double[]   y = {0.0,    1.0,    1.0,    2.0};

ShepardInterpolation interp = new ShepardInterpolation(x, y);
System.out.println(interp.interpolate(0.5, 0.5)); // ≈ 1.0
System.out.println(interp.interpolate(0.0, 0.0)); // 0.0  (exact at knot)
```

**1-D convenience class:**

```java
import smile.interpolation.ShepardInterpolation1D;

double[] x = {0, 1, 2, 3};
double[] y = {0, 1, 0, -1};

ShepardInterpolation1D s  = new ShepardInterpolation1D(x, y);       // p = 2
ShepardInterpolation1D s3 = new ShepardInterpolation1D(x, y, 3.0);  // p = 3
```

**2-D convenience class** (`ShepardInterpolation2D`) accepts two separate
`double[]` coordinate arrays for the x₁ and x₂ dimensions.

#### Effect of the power parameter p

| p | Behaviour |
|---|---|
| p = 1 | Smoother, more global influence |
| p = 2 | Good general default |
| p = 3 | More local; sharp transitions near knots |

Higher p makes the interpolant look more like nearest-neighbour as `p → ∞`.

### 4.2 RBFInterpolation

Radial basis function (RBF) interpolation expresses the interpolant as a linear
combination of radial basis functions centred at the training points:

```
f(x) = Σ w_i φ(||x - x_i||)
```

The weights `w_i` are found by solving the linear system `G w = y`, where
`G[i][j] = φ(||x_i - x_j||)` is the Gram matrix.

A **normalised** variant (NRBF) divides by the sum of basis function values,
which gives a partition-of-unity property and a Bayesian interpretation:

```
f(x) = Σ w_i φ(||x - x_i||) / Σ φ(||x - x_i||)
```

| Property | Value |
|---|---|
| Complexity | O(n²) construction; O(n) per query |
| Exact at knots | Yes |
| Works in any dimension | Yes |

**Available radial basis functions** (all in `smile.math.rbf`):

| Class | Formula φ(r) | Properties |
|---|---|---|
| `GaussianRadialBasis` | exp(−r²/2r₀²) | SPD Gram → Cholesky; sensitive to scale r₀ |
| `MultiquadricRadialBasis` | √(r² + r₀²) | Less sensitive to r₀ |
| `InverseMultiquadricRadialBasis` | 1/√(r² + r₀²) | SPD; decays to zero |
| `ThinPlateRadialBasis` | r² log(r/r₀) | Scale-invariant conditioning |

> **Scale parameter r₀:** Choose `r₀` to be similar to the typical separation
> between data points.  A value that is too small makes the Gram matrix nearly
> diagonal (poor approximation at non-knot points); too large makes it nearly
> rank-deficient.

**Multi-dimensional:**

```java
import smile.interpolation.RBFInterpolation;
import smile.math.rbf.GaussianRadialBasis;

double[][] x = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
double[]   y = {0.0,    1.0,    1.0,    2.0};

RBFInterpolation interp = new RBFInterpolation(x, y, new GaussianRadialBasis());

System.out.println(interp.interpolate(0.0, 0.0)); // 0.0  (exact at knot)
System.out.println(interp.interpolate(0.5, 0.5)); // ≈ 0.57
```

**Normalised multi-dimensional:**

```java
RBFInterpolation norm = new RBFInterpolation(x, y, new GaussianRadialBasis(), true);
```

**1-D convenience class** (with correct scale for stability):

```java
import smile.interpolation.RBFInterpolation1D;

double[] x = {0, 3, 6, 9, 12};
double[] y = {0.0, 0.14, -0.28, 0.41, -0.54};

// r0 = point spacing = 3, giving a well-conditioned Gram matrix
RBFInterpolation1D interp = new RBFInterpolation1D(x, y, new GaussianRadialBasis(3.0));
System.out.println(interp.interpolate(4.5)); // smooth value between knots
```

**2-D convenience class** (`RBFInterpolation2D`) accepts two separate `double[]`
coordinate arrays.

### 4.3 KrigingInterpolation

Kriging (also called Gaussian process regression) is a statistical interpolation
method that models the unknown function as a random field and finds the *best
linear unbiased estimator* (BLUE).  The prediction at a new point minimises the
expected squared error subject to an unbiasedness constraint.

The key ingredient is a **variogram** `γ(r)` that describes how variance grows
with separation distance `r`.  SMILE implements ordinary kriging with a
configurable variogram.

| Property | Value |
|---|---|
| Complexity | O(n³) construction (SVD); O(n) per query |
| Exact at knots | Yes (interpolation mode, no noise) |
| Supports measurement error | Yes — pass error variances as `double[] error` |
| Best for | Spatial statistics; geo-statistics; any domain where variance structure is known |

**Multi-dimensional (default power variogram):**

```java
import smile.interpolation.KrigingInterpolation;

double[][] x = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {0.5, 0.5}};
double[]   y = {0, 1, 1, 2, 1};

KrigingInterpolation kriging = new KrigingInterpolation(x, y);
System.out.println(kriging.interpolate(0.5, 0.5)); // ≈ 1.0
```

**With a custom variogram:**

```java
import smile.interpolation.variogram.GaussianVariogram;

var variogram = new GaussianVariogram(/*range=*/1.0, /*sill=*/2.0);
KrigingInterpolation kriging = new KrigingInterpolation(x, y, variogram, null);
```

**With measurement errors (smoothing kriging):**

```java
double[] errors = {0.05, 0.05, 0.05, 0.05, 0.05}; // sqrt of noise variance
KrigingInterpolation kriging = new KrigingInterpolation(x, y, variogram, errors);
// No longer passes exactly through knots; smoothed by the error model
```

**1-D convenience class:**

```java
import smile.interpolation.KrigingInterpolation1D;

double[] x = {0, 1, 2, 3, 4};
double[] y = {0, 1, 4, 9, 16};

// Default β = 1.5; valid range: 1 ≤ β < 2
KrigingInterpolation1D k = new KrigingInterpolation1D(x, y);

// With a custom β (larger β → stronger linear trend assumption)
KrigingInterpolation1D k2 = new KrigingInterpolation1D(x, y, 1.9);
```

**2-D convenience class** (`KrigingInterpolation2D`) accepts two separate
`double[]` coordinate arrays and an optional beta parameter.

---

## 5. Grid Inpainting

### 5.1 LaplaceInterpolation

`LaplaceInterpolation` fills **missing values** (represented as `Double.NaN`) in
a 2-D regular grid by solving the discrete Laplace equation:

```
∇²f = 0  (at every missing cell)
```

The Laplace solution produces the *smoothest* possible interpolant — it
minimises the integral of the squared Laplacian, which is the same objective
as a thin plate spline.  The system is solved iteratively using the
**biconjugate gradient** (BiCG) method, which is efficient for the very sparse
stencil.

| Property | Value |
|---|---|
| Input | A 2-D `double[][]` array; `NaN` marks missing entries |
| Output | Modified in-place; also returns estimated error |
| Known values | Preserved exactly |
| Best for | Image inpainting; restoring missing sensor readings; gap filling |

```java
import smile.interpolation.LaplaceInterpolation;

double[][] grid = {
    {1.0, 2.0,        Double.NaN, 4.0},
    {2.0, Double.NaN, Double.NaN, 5.0},
    {3.0, 4.0,        5.0,        6.0}
};

double error = LaplaceInterpolation.interpolate(grid);
System.out.printf("Converged with error = %.2e%n", error);
// grid now has NaN cells filled with smooth values

// With custom tolerance and iteration limit:
LaplaceInterpolation.interpolate(grid, 1e-8, 500);
```

#### Linear functions are reproduced exactly

The Laplace operator applied to any linear function `f(i,j) = ai + bj + c`
gives zero.  Therefore, if the boundary is linear, all interior values will be
recovered exactly.

---

## 6. Variograms

Variograms are used by `KrigingInterpolation` to model spatial dependence.
All variograms implement `Variogram` (which extends `smile.util.function.Function`)
via `double f(double r)`.

### Built-in variograms

#### PowerVariogram

```
γ(r) = nugget + α r^β,   1 ≤ β < 2
```

`α` is estimated from the data by unweighted least squares.  The default β = 1.5
is a good general choice; use β closer to 2 for strongly linear spatial trends.

```java
import smile.interpolation.variogram.PowerVariogram;

// α estimated automatically; β = 1.5; no nugget
PowerVariogram v = new PowerVariogram(x, y);

// Custom β with nugget
PowerVariogram v2 = new PowerVariogram(x, y, 1.8, 0.1);
```

#### GaussianVariogram

```
γ(r) = c + b (1 − exp(−3r²/a²))
```

Reaches plateau (sill `b + c`) at approximately `r = a` (range).

```java
import smile.interpolation.variogram.GaussianVariogram;

// range=2, sill=5, no nugget
GaussianVariogram v = new GaussianVariogram(2.0, 5.0);

// With nugget effect c=0.3
GaussianVariogram v2 = new GaussianVariogram(2.0, 5.0, 0.3);
```

#### ExponentialVariogram

```
γ(r) = c + b (1 − exp(−3r/a))
```

Rises faster near the origin than Gaussian; approaches sill asymptotically.

```java
import smile.interpolation.variogram.ExponentialVariogram;

ExponentialVariogram v = new ExponentialVariogram(/*range=*/3.0, /*sill=*/4.0);
```

#### SphericalVariogram

```
γ(r) = c + b (1.5 r/a − 0.5 (r/a)³)  for r ≤ a
γ(r) = c + b                            for r > a
```

Unlike Gaussian and Exponential, the spherical model reaches its sill **exactly**
at the range parameter `a`.

```java
import smile.interpolation.variogram.SphericalVariogram;

SphericalVariogram v = new SphericalVariogram(/*range=*/5.0, /*sill=*/3.0);
System.out.println(v.f(5.0)); // exactly 3.0 (sill reached)
System.out.println(v.f(10.0)); // also 3.0 (flat beyond range)
```

### Variogram parameter glossary

| Parameter | Meaning |
|---|---|
| **range** (a) | Distance at which spatial correlation becomes negligible |
| **sill** (b) | Maximum variogram value (variance of the process) |
| **nugget** (c) | Discontinuity at origin; represents micro-scale variability or measurement error |

---

## 7. Choosing a Method

### By data structure

```
Data lies on a regular 1-D grid?
  ├─ Need smoothness / accuracy → CubicSplineInterpolation1D
  └─ Need speed / simplicity   → LinearInterpolation

Data lies on a regular 2-D grid?
  ├─ Need C² smoothness        → CubicSplineInterpolation2D
  ├─ Need C¹ + cross-deriv     → BicubicInterpolation
  └─ Need speed / simplicity   → BilinearInterpolation

Data is scattered (irregular)?
  ├─ Large n, quick results    → ShepardInterpolation
  ├─ Need statistical model    → KrigingInterpolation
  └─ Need smooth approximation → RBFInterpolation

Regular grid with missing values?
  └─ LaplaceInterpolation
```

### By accuracy vs. speed trade-off

| Method | Accuracy | Construction | Query |
|---|---|---|---|
| `LinearInterpolation` | Low | O(1) | O(log n) |
| `CubicSplineInterpolation1D` | High | O(n) | O(log n) |
| `BilinearInterpolation` | Low | O(1) | O(log n + log m) |
| `BicubicInterpolation` | Medium | O(nm) | O(log n + log m) |
| `CubicSplineInterpolation2D` | High | O(nm) | O(log nm) |
| `ShepardInterpolation` | Medium | O(1) | O(n) |
| `RBFInterpolation` | High | O(n³) | O(n) |
| `KrigingInterpolation` | High | O(n³) | O(n) |

### By dimensionality

| Dimension | Recommended class |
|---|---|
| 1-D, grid | `LinearInterpolation`, `CubicSplineInterpolation1D` |
| 1-D, scattered | `ShepardInterpolation1D`, `RBFInterpolation1D`, `KrigingInterpolation1D` |
| 2-D, grid | `BilinearInterpolation`, `BicubicInterpolation`, `CubicSplineInterpolation2D` |
| 2-D, scattered | `ShepardInterpolation2D`, `RBFInterpolation2D`, `KrigingInterpolation2D` |
| n-D, scattered | `ShepardInterpolation`, `RBFInterpolation`, `KrigingInterpolation` |

---

## 8. Quick-Reference Table

| Class | Data layout | Dim | Exact at knots | Smoothness | Notes |
|---|---|---|---|---|---|
| `LinearInterpolation` | Grid | 1-D | Yes | C⁰ | Fastest 1-D method |
| `CubicSplineInterpolation1D` | Grid | 1-D | Yes | C² | Natural (zero-2nd-deriv) BCs |
| `BilinearInterpolation` | Grid | 2-D | Yes | C⁰ | Bilinear per cell |
| `BicubicInterpolation` | Grid | 2-D | Yes | C¹ + ∂²/∂x∂y | Needs ≥ 4×4 grid |
| `CubicSplineInterpolation2D` | Grid | 2-D | Yes | C² per axis | Tensor-product spline |
| `ShepardInterpolation` | Scattered | n-D | Yes | C∞ off-knot | O(n) query; no construction |
| `ShepardInterpolation1D` | Scattered | 1-D | Yes | C∞ off-knot | 1-D convenience wrapper |
| `ShepardInterpolation2D` | Scattered | 2-D | Yes | C∞ off-knot | 2-D convenience wrapper |
| `RBFInterpolation` | Scattered | n-D | Yes | C∞ off-knot | Solves n×n linear system |
| `RBFInterpolation1D` | Scattered | 1-D | Yes | C∞ off-knot | 1-D convenience wrapper |
| `RBFInterpolation2D` | Scattered | 2-D | Yes | C∞ off-knot | 2-D convenience wrapper |
| `KrigingInterpolation` | Scattered | n-D | Yes (no noise) | C∞ off-knot | Supports measurement errors |
| `KrigingInterpolation1D` | Scattered | 1-D | Yes | C∞ off-knot | Power variogram; β ∈ [1,2) |
| `KrigingInterpolation2D` | Scattered | 2-D | Yes | C∞ off-knot | 2-D convenience wrapper |
| `LaplaceInterpolation` | Grid+NaN | 2-D | Yes (known) | C∞ (harmonic) | Inpainting via BiCG solver |


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

