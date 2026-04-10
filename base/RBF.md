# SMILE — Radial Basis Functions

The `smile.math.rbf` package provides radial basis function (RBF) kernels used
primarily in RBF networks, scattered-data interpolation, and meshless
numerical methods.

## What is a Radial Basis Function?

A **radial basis function** is a real-valued function whose value depends only
on the distance from a fixed center point `c`:

```
φ(x, c) = φ( ‖x − c‖ )
```

Sums of RBFs approximate arbitrary functions:

```
y(x) = Σᵢ wᵢ φ( ‖x − cᵢ‖ )
```

where the centers `cᵢ` are typically chosen from the training data and the
weights `wᵢ` are solved via linear least squares. Because the approximation is
**linear in the weights**, fitting is fast and well-understood.

All classes in this package implement the `RadialBasisFunction` interface,
which extends `smile.util.function.Function` (i.e., `Serializable` + `f(double)`).

---

## The Interface

```java
public interface RadialBasisFunction extends Function {
    // Inherits: double f(double r)
    // Inherits: default double apply(double r)
}
```

Every RBF is callable as a plain univariate function of the distance `r`:

```java
RadialBasisFunction rbf = new GaussianRadialBasis(1.0);
double v = rbf.f(2.5);     // evaluate at distance r = 2.5
double v2 = rbf.apply(2.5); // same result — Scala / lambda friendly
```

---

## Available Functions

### Gaussian

```
φ(r) = exp( −r² / (2 r₀²) )
```

| Property | Value |
|----------|-------|
| `φ(0)` | 1 |
| Range | (0, 1] |
| Monotone | Decreasing |
| Tail | Decays rapidly to 0 |

The Gaussian is the most popular RBF. It can achieve very high interpolation
accuracy for smooth functions when `r₀` is well-chosen, but is sensitive to
the scale parameter.

```java
GaussianRadialBasis rbf = new GaussianRadialBasis(1.0); // r₀ = 1.0

double v = rbf.f(0.0); // 1.0
double v2 = rbf.f(2.0); // exp(-2) ≈ 0.135

// Query the scale
double r0 = rbf.scale(); // 1.0
```

**Scale effect**: Smaller `r₀` → sharper, more local influence. Larger `r₀` →
smoother, more global influence.

```java
GaussianRadialBasis sharp  = new GaussianRadialBasis(0.5);
GaussianRadialBasis smooth = new GaussianRadialBasis(2.0);
// sharp.f(1.0) < smooth.f(1.0)
```

---

### Multiquadric

```
φ(r) = sqrt( r² + r₀² )
```

| Property | Value |
|----------|-------|
| `φ(0)` | `r₀` |
| Range | `[r₀, ∞)` |
| Monotone | Increasing |
| Tail | Grows without bound |

Multiquadrics are often less sensitive to the choice of `r₀` than other RBFs.
Unlike the decaying RBFs, the multiquadric grows with distance — interpolation
requires a polynomial correction term to ensure the system is well-conditioned.

```java
MultiquadricRadialBasis rbf = new MultiquadricRadialBasis(1.0);
double v = rbf.f(3.0); // sqrt(9 + 1) = sqrt(10) ≈ 3.162
double r0 = rbf.scale(); // 1.0
```

---

### Inverse Multiquadric

```
φ(r) = 1 / sqrt( r² + r₀² )
```

| Property | Value |
|----------|-------|
| `φ(0)` | `1/r₀` |
| Range | (0, 1/r₀] |
| Monotone | Decreasing |
| Tail | Decays to 0 |

The inverse multiquadric behaves similarly to the multiquadric in terms of
conditioning but is bounded above and decays to zero, giving it properties
closer to the Gaussian.

```java
InverseMultiquadricRadialBasis rbf = new InverseMultiquadricRadialBasis(1.0);
double v = rbf.f(0.0); // 1/sqrt(0+1) = 1.0
double v2 = rbf.f(3.0); // 1/sqrt(10) ≈ 0.316

// Exact inverse relationship with multiquadric:
// imq.f(r) == 1.0 / mq.f(r)  (same r₀)
```

---

### Inverse Quadratic

```
φ(r) = 1 / ( 1 + (r/r₀)² )
```

| Property | Value |
|----------|-------|
| `φ(0)` | 1 |
| Range | (0, 1] |
| Monotone | Decreasing |
| Tail | Algebraic (heavier than Gaussian) |

A completely monotone function, guaranteeing positive definiteness. Compared to
the Gaussian, it has heavier tails — useful when long-range correlations matter.
The value at `r = r₀` is exactly 0.5 (`φ(r₀) = 1/(1+1) = 0.5`).

```java
InverseQuadraticRadialBasis rbf = new InverseQuadraticRadialBasis(2.0);
double v  = rbf.f(0.0); // 1.0
double v2 = rbf.f(2.0); // 0.5  (at r = r₀)
double v3 = rbf.f(6.0); // 1/(1+9) = 0.1
```

---

### Thin Plate Spline

```
φ(r) = r² log(r / r₀),   φ(0) = 0
```

| Property | Value |
|----------|-------|
| `φ(0)` | 0 (limit) |
| Sign for `r < r₀` | Negative |
| Sign for `r > r₀` | Positive |
| `φ(r₀)` | 0 |
| Tail | Grows as `r² log r` |

The thin plate spline arises from minimizing the bending energy of an elastic
plate — it produces the smoothest interpolant in a weighted Sobolev sense.
When `r₀ = 1` it is equivalent to the `PolyharmonicSpline` of order 2.

```java
ThinPlateRadialBasis rbf = new ThinPlateRadialBasis(1.0);
double v1 = rbf.f(0.0);  // 0.0 (not NaN)
double v2 = rbf.f(0.5);  // negative (r < r₀)
double v3 = rbf.f(1.0);  // 0.0 (at r = r₀)
double v4 = rbf.f(Math.E); // e² ≈ 7.389 (φ(e) = e²·log(e/1) = e²·1)
```

---

### Polyharmonic Spline

A generalization of the thin plate spline parameterized by an integer order `k`:

```
k odd:  φ(r) = rᵏ
k even: φ(r) = rᵏ log(r),   φ(0) = 0
```

| Order | Formula | Common name |
|-------|---------|-------------|
| 1 | `r` | Linear spline |
| 2 | `r² log r` | Thin plate spline (with r₀ = 1) |
| 3 | `r³` | Cubic spline |
| 4 | `r⁴ log r` | Biharmonic spline |
| 5 | `r⁵` | — |

Higher-order splines produce smoother interpolants but require more
polynomial terms in the basis to remain well-conditioned.

```java
PolyharmonicSpline linear = new PolyharmonicSpline(1); // φ(r) = r
PolyharmonicSpline tps    = new PolyharmonicSpline(2); // φ(r) = r² log r  (default)
PolyharmonicSpline cubic  = new PolyharmonicSpline(3); // φ(r) = r³
PolyharmonicSpline biharm = new PolyharmonicSpline(4); // φ(r) = r⁴ log r

int k = tps.order(); // 2

// k=2 matches ThinPlateRadialBasis(1.0) exactly:
double r = 2.5;
double v1 = new PolyharmonicSpline(2).f(r);
double v2 = new ThinPlateRadialBasis(1.0).f(r);
// v1 == v2

// k=3 is the standard cubic RBF:
double v3 = new PolyharmonicSpline(3).f(3.0); // 27.0
```

---

## Summary Table

| Class | Formula | `φ(0)` | Tail | Bounded |
|-------|---------|---------|------|---------|
| `GaussianRadialBasis` | `exp(−r²/2r₀²)` | 1 | Fast decay | ✓ (0,1] |
| `InverseMultiquadricRadialBasis` | `1/√(r²+r₀²)` | 1 | Slow decay | ✓ (0,1] |
| `InverseQuadraticRadialBasis` | `1/(1+(r/r₀)²)` | 1 | Algebraic decay | ✓ (0,1] |
| `MultiquadricRadialBasis` | `√(r²+r₀²)` | `r₀` | Grows | ✗ |
| `ThinPlateRadialBasis` | `r² log(r/r₀)` | 0 | Grows | ✗ |
| `PolyharmonicSpline` | `rᵏ` or `rᵏ log r` | 0 | Grows | ✗ |

---

## Scale Parameter `r₀`

All scale-based RBFs expose a `scale()` method:

```java
GaussianRadialBasis            rbf1 = new GaussianRadialBasis(2.0);
MultiquadricRadialBasis        rbf2 = new MultiquadricRadialBasis(2.0);
InverseMultiquadricRadialBasis rbf3 = new InverseMultiquadricRadialBasis(2.0);
InverseQuadraticRadialBasis    rbf4 = new InverseQuadraticRadialBasis(2.0);
ThinPlateRadialBasis           rbf5 = new ThinPlateRadialBasis(2.0);

rbf1.scale(); // 2.0
```

All constructors validate that `r₀ > 0` and throw `IllegalArgumentException`
if not. All classes also provide a default no-argument constructor with `r₀ = 1`.

```java
// These all use r₀ = 1.0
GaussianRadialBasis            g = new GaussianRadialBasis();
MultiquadricRadialBasis        m = new MultiquadricRadialBasis();
InverseMultiquadricRadialBasis i = new InverseMultiquadricRadialBasis();
InverseQuadraticRadialBasis    q = new InverseQuadraticRadialBasis();
ThinPlateRadialBasis           t = new ThinPlateRadialBasis();
PolyharmonicSpline             p = new PolyharmonicSpline(); // k=2
```

---

## Choosing the Scale `r₀`

The scale parameter dramatically affects interpolation quality. A general
guideline:

> `r₀` should be **larger** than the typical separation between data points,
> but **smaller** than the feature size (outer scale) of the function being
> approximated.

A practical approach is **leave-one-out cross-validation**:

```java
// Pseudo-code for LOO scale search
double bestR0 = 1.0;
double bestError = Double.MAX_VALUE;
for (double r0 : candidates) {
    GaussianRadialBasis rbf = new GaussianRadialBasis(r0);
    // build interpolant omitting each point in turn, measure error at omitted point
    double error = leaveOneOutError(data, rbf);
    if (error < bestError) { bestError = error; bestR0 = r0; }
}
```

---

## Usage with RBF Networks

RBF networks in SMILE use `RadialBasisFunction` instances to define the
activation function of each hidden neuron:

```java
import smile.classification.RBFNetwork;
import smile.model.rbf.RBF;

// Fit Gaussian RBF network with 30 neurons
RBFNetwork<double[]> model = RBFNetwork.fit(x, y, RBF.fit(x, 30));

// Custom RBF type
GaussianRadialBasis customRbf = new GaussianRadialBasis(0.5);
RBF<double[]>[] neurons = RBF.of(centers, customRbf, new EuclideanDistance());
RBFNetwork<double[]> model2 = RBFNetwork.fit(x, y, neurons);
```

---

## Choosing the Right RBF

| Situation | Recommendation |
|-----------|---------------|
| General interpolation | `GaussianRadialBasis` (tune `r₀`) |
| Less sensitivity to `r₀` | `MultiquadricRadialBasis` |
| Smooth, bounded interpolant | `InverseMultiquadricRadialBasis` |
| Heavy-tailed correlations | `InverseQuadraticRadialBasis` |
| Physically motivated smoothness | `ThinPlateRadialBasis` or `PolyharmonicSpline(2)` |
| C¹ smoothness, compact support | `PolyharmonicSpline(3)` (cubic) |
| Higher-order smoothness | `PolyharmonicSpline(k)` with larger `k` |


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

