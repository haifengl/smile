# SMILE — Mercer Kernels

The `smile.math.kernel` package implements Mercer kernel functions used in
support vector machines, Gaussian processes, kernel PCA, and other kernel
methods.

## Background

A **Mercer kernel** is a function `k(x, y)` that is:
- **Symmetric**: `k(x, y) = k(y, x)`
- **Positive semi-definite**: for any finite set of points, the kernel (Gram)
  matrix `K[i,j] = k(xᵢ, xⱼ)` has non-negative eigenvalues

PSD kernels enable the **kernel trick** — implicitly mapping data into a
(possibly infinite-dimensional) feature space `H` where a linear algorithm
operates exclusively on inner products:

```
k(x, y) = ⟨φ(x), φ(y)⟩_H
```

### Kernel taxonomy

| Category | Property | Examples |
|----------|----------|---------|
| **Isotropic** | depends only on `‖x−y‖` | Gaussian, Laplacian, Matérn, TPS |
| **Dot-product** | depends only on `xᵀy` | Linear, Polynomial, Tanh |
| **Other** | custom | Hellinger, Pearson |

---

## Core Interfaces

### `MercerKernel<T>`

All kernels implement `MercerKernel<T>`, which provides:

| Method | Description |
|--------|-------------|
| `double k(T x, T y)` | Kernel value |
| `double[] kg(T x, T y)` | Kernel value + gradient w.r.t. hyperparameters |
| `DenseMatrix K(T[] x)` | Gram matrix (lower triangular, computed in parallel) |
| `DenseMatrix K(T[] x, T[] y)` | Rectangular kernel matrix |
| `DenseMatrix[] KG(T[] x)` | Gram + gradient matrices for hyperparameter tuning |
| `MercerKernel<T> of(double[] params)` | Create new kernel with updated hyperparameters |
| `double[] hyperparameters()` | Current hyperparameter values |
| `double[] lo()` / `double[] hi()` | Bounds for hyperparameter search |

```java
GaussianKernel k = new GaussianKernel(1.0);

// Single evaluation
double v = k.k(x, y);

// Kernel matrix for a dataset
double[][] data = { x1, x2, x3 };
DenseMatrix K = k.K(data);
```

### `IsotropicKernel`

An isotropic kernel depends only on the Euclidean distance `r = ‖x−y‖`.
It exposes `k(double dist)` and a vectorized `K(DenseMatrix pdist)` that
accepts a pre-computed pairwise distance matrix.

### `DotProductKernel`

A dot-product kernel depends only on the inner product `xᵀy`.
It exposes `k(double dot)` and `K(Matrix pdot)` that accepts a
pre-computed Gram matrix of inner products.

---

## Isotropic Kernels (`double[]`)

### Gaussian (RBF / Squared Exponential)

```
k(x, y) = exp( −‖x−y‖² / (2σ²) )
```

The most widely used kernel. Infinitely differentiable, universal approximator.
Smaller `σ` creates sharper, more local features.

| Hyperparameter | Meaning | Default bounds |
|----------------|---------|----------------|
| `σ` (sigma) | Length scale | [1e-5, 1e5] |

```java
GaussianKernel k = new GaussianKernel(1.0);      // σ = 1
GaussianKernel k2 = new GaussianKernel(0.5, 1e-3, 10.0); // with tuning bounds

double v = k.k(x, y);      // kernel value
double[] kg = k.kg(x, y);  // [value, d/dσ]
```

### Laplacian (Exponential)

```
k(x, y) = exp( −‖x−y‖ / σ )
```

Less smooth than Gaussian (C⁰ continuous). Better for data with sharp
transitions. Equivalent to the Matérn kernel with `ν = 0.5`.

```java
LaplacianKernel k = new LaplacianKernel(1.0);
```

### Matérn

A flexible family parameterized by **smoothness** `ν`:

| `ν` | Smoothness | Equivalent |
|-----|-----------|------------|
| 0.5 | C⁰ (continuous) | Laplacian |
| 1.5 | C¹ (once differentiable) | — |
| 2.5 | C² (twice differentiable) | — |
| ∞   | C∞ (infinitely differentiable) | Gaussian |

```
ν = 0.5:  k(r) = exp(−r/σ)
ν = 1.5:  k(r) = (1 + √3·r/σ) exp(−√3·r/σ)
ν = 2.5:  k(r) = (1 + √5·r/σ + 5r²/(3σ²)) exp(−√5·r/σ)
ν = ∞:    k(r) = exp(−r²/(2σ²))
```

Only `ν ∈ {0.5, 1.5, 2.5, ∞}` are supported (these yield closed-form
expressions). The smoothness parameter is **fixed** during hyperparameter tuning.

```java
MaternKernel m15  = new MaternKernel(1.0, 1.5);
MaternKernel m25  = new MaternKernel(1.0, 2.5);
MaternKernel mInf = new MaternKernel(1.0, Double.POSITIVE_INFINITY);
```

### Thin Plate Spline

```
k(x, y) = (‖x−y‖/σ)² log(‖x−y‖/σ),   k(x, x) = 0
```

Arises naturally from the problem of minimizing bending energy of a thin
elastic plate. The value at `r = 0` is defined as 0 (the mathematical limit).

```java
ThinPlateSplineKernel k = new ThinPlateSplineKernel(1.0);
double v = k.k(x, y);
```

---

## Dot-Product Kernels (`double[]`)

### Linear

```
k(x, y) = xᵀy
```

The simplest kernel — feature space equals input space. Equivalent to a
standard linear model. No hyperparameters.

```java
LinearKernel k = new LinearKernel();
double v = k.k(x, y); // dot product
```

### Polynomial

```
k(x, y) = (γ xᵀy + λ)^d
```

Maps data into polynomial feature space of degree `d`. The offset `λ` must be
non-negative to ensure positive definiteness.

| Hyperparameter | Meaning | Tuning |
|----------------|---------|--------|
| `d` (degree) | Polynomial order | Fixed |
| `γ` (scale) | Inner-product scale | Tuned |
| `λ` (offset) | Bias term ≥ 0 | Tuned |

```java
PolynomialKernel poly2 = new PolynomialKernel(2);           // d=2, γ=1, λ=0
PolynomialKernel poly3 = new PolynomialKernel(3, 0.1, 1.0); // d=3, γ=0.1, λ=1

// Custom tuning bounds
PolynomialKernel pk = new PolynomialKernel(2, 1.0, 0.0,
    new double[]{1e-2, 1e-5}, new double[]{1e2, 1e5});
```

### Hyperbolic Tangent (Tanh / Sigmoid)

```
k(x, y) = tanh(γ xᵀy + λ)
```

Inspired by neural network activation functions. **Not guaranteed to be
positive semi-definite** for all parameter choices — use with care.

```java
HyperbolicTangentKernel tanh = new HyperbolicTangentKernel(0.1, 0.0);
```

---

## Other Kernels (`double[]`)

### Pearson VII

```
k(x, y) = (1 + C‖x−y‖²)^(−ω),   C = 4(2^(1/ω)−1) / σ²
```

A universal kernel based on the Pearson VII probability density function.
Useful for regression tasks — `ω` controls the tailing factor (sharpness).
Both `σ` and `ω` must be positive.

```java
PearsonKernel pk = new PearsonKernel(1.0, 2.0);  // sigma=1, omega=2
```

### Hellinger

```
k(x, y) = Σᵢ √(xᵢ yᵢ)
```

The inner product of the element-wise square roots — equivalent to the
cosine similarity in the square-root feature space. Useful for histogram
and probability vector inputs. No hyperparameters.

```java
HellingerKernel k = new HellingerKernel();
double[] hist1 = {0.25, 0.50, 0.25};
double[] hist2 = {0.10, 0.80, 0.10};
double v = k.k(hist1, hist2);
```

---

## Composite Kernels

### Sum Kernel

The sum of two kernels is always a valid Mercer kernel:

```
k(x, y) = k₁(x, y) + k₂(x, y)
```

Hyperparameters are the union of both constituent kernels' hyperparameters.

```java
GaussianKernel  gk = new GaussianKernel(1.0);
LaplacianKernel lk = new LaplacianKernel(2.0);
SumKernel<double[]> sk = new SumKernel<>(gk, lk);

double v = sk.k(x, y);
double[] hp = sk.hyperparameters(); // [σ_gaussian, σ_laplacian]
```

### Product Kernel

The product of two kernels is also a valid Mercer kernel:

```
k(x, y) = k₁(x, y) × k₂(x, y)
```

```java
ProductKernel<double[]> pk = new ProductKernel<>(gk, lk);
double v = pk.k(x, y);
```

---

## Sparse and Binary Sparse Kernels

For high-dimensional sparse data, all standard kernels have counterparts that
work on `SparseArray` or binary sparse `int[]` (indices of non-zero elements):

| Dense kernel | Sparse (`SparseArray`) | Binary sparse (`int[]`) |
|-------------|------------------------|------------------------|
| `LinearKernel` | `SparseLinearKernel` | `BinarySparseLinearKernel` |
| `PolynomialKernel` | `SparsePolynomialKernel` | `BinarySparsePolynomialKernel` |
| `GaussianKernel` | `SparseGaussianKernel` | `BinarySparseGaussianKernel` |
| `LaplacianKernel` | `SparseLaplacianKernel` | `BinarySparseLaplacianKernel` |
| `MaternKernel` | `SparseMaternKernel` | `BinarySparseMaternKernel` |
| `ThinPlateSplineKernel` | `SparseThinPlateSplineKernel` | `BinarySparseThinPlateSplineKernel` |
| `HyperbolicTangentKernel` | `SparseHyperbolicTangentKernel` | `BinarySparseHyperbolicTangentKernel` |

```java
// Sparse
SparseGaussianKernel sgk = new SparseGaussianKernel(1.0);
SparseArray sx = new SparseArray();
sx.append(0, 1.0); sx.append(5, 3.0);
double v = sgk.k(sx, sy);

// Binary sparse (non-zero index sets)
BinarySparseLinearKernel blk = new BinarySparseLinearKernel();
int[] b1 = {0, 2, 5};   // non-zero indices
int[] b2 = {2, 5, 8};
double dot = blk.k(b1, b2); // 2 (intersection size)
```

---

## Parsing from Strings

`MercerKernel.of(String)` parses a kernel description, enabling configuration
via properties files or command-line arguments:

```java
// Dense kernels
MercerKernel<double[]> k1 = MercerKernel.of("linear()");
MercerKernel<double[]> k2 = MercerKernel.of("gaussian(1.0)");
MercerKernel<double[]> k3 = MercerKernel.of("polynomial(2, 1.0, 0.0)");
MercerKernel<double[]> k4 = MercerKernel.of("matern(1.0, 1.5)");
MercerKernel<double[]> k5 = MercerKernel.of("laplacian(0.5)");
MercerKernel<double[]> k6 = MercerKernel.of("tanh(0.1, 0.0)");
MercerKernel<double[]> k7 = MercerKernel.of("tps(1.0)");
MercerKernel<double[]> k8 = MercerKernel.of("pearson(1.0, 1.0)");
MercerKernel<double[]> k9 = MercerKernel.of("hellinger");

// Sparse
MercerKernel<SparseArray> sk = MercerKernel.sparse("gaussian(1.0)");

// Binary sparse
MercerKernel<int[]> bk = MercerKernel.binary("polynomial(2, 1.0, 0.0)");
```

The `toString()` of every kernel produces a string accepted by `of()`,
enabling a round-trip:

```java
GaussianKernel gk = new GaussianKernel(2.5);
MercerKernel<double[]> gk2 = MercerKernel.of(gk.toString()); // GaussianKernel(2.5000)
```

---

## Hyperparameter Tuning

Every kernel exposes `hyperparameters()`, `lo()`, `hi()`, and `of(double[])`
for integration with automatic hyperparameter optimization:

```java
GaussianKernel k = new GaussianKernel(1.0);
double[] hp  = k.hyperparameters(); // [1.0]
double[] lo  = k.lo();              // [1e-5]
double[] hi  = k.hi();              // [1e5]

// Produce a new kernel with updated parameters
GaussianKernel k2 = k.of(new double[]{2.0});
```

Composite kernels (`SumKernel`, `ProductKernel`) concatenate the
hyperparameter vectors and bounds of their constituents.

---

## Choosing the Right Kernel

| Data type | Recommended kernels |
|-----------|----------------------|
| Smooth continuous data | `GaussianKernel`, `MaternKernel(ν=2.5)` |
| Non-smooth / piecewise | `LaplacianKernel`, `MaternKernel(ν=0.5 or 1.5)` |
| Polynomial relationships | `PolynomialKernel` |
| Text / histograms | `LinearKernel`, `PolynomialKernel`, `HellingerKernel` |
| Sparse high-dimensional | `SparseLinearKernel`, `SparseGaussianKernel` |
| Binary feature sets | `BinarySparseLinearKernel` |
| Probabilistic vectors | `HellingerKernel` |
| X-ray / spectral peaks | `PearsonKernel` |
| Multiple characteristics | `SumKernel` or `ProductKernel` |


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

