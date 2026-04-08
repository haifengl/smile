# Compressed Sensing User Guide

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Core Concepts](#core-concepts)
   - [Sparsity](#sparsity)
   - [The Measurement Model](#the-measurement-model)
   - [Restricted Isometry Property (RIP)](#restricted-isometry-property-rip)
   - [Sample Complexity](#sample-complexity)
4. [Measurement Matrix](#measurement-matrix)
   - [Gaussian](#gaussian)
   - [Bernoulli](#bernoulli)
   - [Partial Identity](#partial-identity)
   - [Wavelet Sparsifying Basis](#wavelet-sparsifying-basis)
   - [Adjoint Operations](#adjoint-operations)
   - [Implicit Matrix Interface](#implicit-matrix-interface)
5. [Recovery Algorithms](#recovery-algorithms)
   - [Orthogonal Matching Pursuit (OMP)](#orthogonal-matching-pursuit-omp)
   - [CoSaMP](#cosamp)
   - [Basis Pursuit / BPDN](#basis-pursuit--bpdn)
   - [Algorithm Comparison](#algorithm-comparison)
6. [Hyperparameter Tuning](#hyperparameter-tuning)
   - [OMP Options](#omp-options)
   - [CoSaMP Options](#cosamp-options)
   - [BasisPursuit Options](#basispursuit-options)
   - [Persisting Options](#persisting-options)
7. [Choosing a Sensing Matrix](#choosing-a-sensing-matrix)
8. [Noisy Measurements](#noisy-measurements)
9. [Wavelet-Domain Sparsity](#wavelet-domain-sparsity)
10. [Complete Examples](#complete-examples)
11. [Mathematical Background](#mathematical-background)
12. [References](#references)

---

## Overview

The `smile.cs` package implements **Compressed Sensing** (CS), a signal-acquisition
paradigm that exploits signal sparsity to recover a high-dimensional signal from
far fewer measurements than the classical Nyquist–Shannon theorem requires.

Given a signal `x ∈ ℝⁿ` that is `k`-sparse (has at most `k` non-zero entries),
and a measurement matrix `A ∈ ℝ^{m×n}` with `m ≪ n`, CS recovers `x` from
`y = Ax` exactly (or approximately in the noisy case) when `m = O(k log(n/k))`.

| Class | Algorithm | Type | Best for |
|---|---|---|---|
| `MeasurementMatrix` | — | Sensing operator | Building `y = Φ x` |
| `OMP` | Orthogonal Matching Pursuit | Greedy | Fast, known sparsity `k` |
| `CoSaMP` | Compressive Sampling Matching Pursuit | Greedy | Provable guarantees, near-optimal |
| `BasisPursuit` | L1-minimization via interior point | Convex | Best recovery quality, noisy data |

---

## Quick Start

```java
import smile.cs.*;
import smile.math.MathEx;

MathEx.setSeed(42);

// 1. Build a k-sparse ground-truth signal
int n = 128, m = 50, k = 5;
double[] xTrue = new double[n];
int[] locs = MathEx.permutate(n);
for (int i = 0; i < k; i++) xTrue[locs[i]] = 1.0 + MathEx.random();

// 2. Create a Gaussian sensing matrix and take measurements
MeasurementMatrix mm = MeasurementMatrix.gaussian(m, n);
double[] y = mm.measure(xTrue);

// 3. Recover with OMP (fastest, requires known k)
OMP omp = OMP.fit(mm.phi(), y, k);
System.out.printf("OMP support: %s%n", java.util.Arrays.toString(omp.support()));

// 4. Recover with CoSaMP (provable guarantees)
CoSaMP cosamp = CoSaMP.fit(mm.phi(), y, k);

// 5. Recover with Basis Pursuit (best quality, convex)
BasisPursuit bp = BasisPursuit.fit(mm.phi(), y);

// 6. Check relative L2 error
double err = relError(omp.x(), xTrue);
System.out.printf("OMP relative error: %.2e%n", err);
```

```java
static double relError(double[] xRec, double[] xTrue) {
    double num = 0, den = 0;
    for (int i = 0; i < xTrue.length; i++) {
        double d = xRec[i] - xTrue[i];
        num += d * d;
        den += xTrue[i] * xTrue[i];
    }
    return Math.sqrt(num / den);
}
```

---

## Core Concepts

### Sparsity

A signal `x ∈ ℝⁿ` is **`k`-sparse** if it has at most `k` non-zero entries:

```
‖x‖₀  =  |{ i : x[i] ≠ 0 }|  ≤  k
```

The signal need not be sparse in the original domain — it suffices that it is
sparse in some known **basis** Ψ. For example, natural images are sparse in
a wavelet basis; audio signals are sparse in the Fourier basis.

### The Measurement Model

The forward model is:

```
y  =  A x  =  Φ Ψ s
```

where:

| Symbol | Meaning |
|---|---|
| `x ∈ ℝⁿ` | Original signal |
| `y ∈ ℝᵐ` | Measurement vector (`m ≪ n`) |
| `Φ ∈ ℝ^{m×n}` | Sensing (measurement) matrix |
| `Ψ ∈ ℝ^{n×n}` | Sparsifying basis (e.g. wavelet transform) |
| `s ∈ ℝⁿ` | Sparse coefficients in Ψ-domain |
| `A = Φ Ψ` | Compound measurement matrix |

When the signal is already sparse in the canonical basis, `Ψ = I` and `A = Φ`.

### Restricted Isometry Property (RIP)

The RIP is the key theoretical condition guaranteeing recovery. A matrix `A`
satisfies the RIP of order `k` with constant `δ_k` if, for all `k`-sparse
vectors `x`:

```
(1 − δ_k) ‖x‖₂²  ≤  ‖Ax‖₂²  ≤  (1 + δ_k) ‖x‖₂²
```

Gaussian and Bernoulli matrices satisfy the RIP with high probability when
`m ≥ C · k · log(n/k)` for a universal constant `C ≈ 1–2`. This means the
sensing matrix approximately preserves the geometry of sparse signals.

### Sample Complexity

The minimum number of measurements for reliable recovery is:

| Regime | Required `m` |
|---|---|
| Exact recovery (noiseless) | `m ≥ C k log(n/k)` |
| Stable recovery (noisy) | `m ≥ C k log(n/k)` (same order) |
| Practical rule of thumb | `m ≥ 4k` to `m ≥ 5k` |

For example, recovering a `k=5`-sparse signal in `n=128` dimensions requires
approximately `m ≥ 20–25` measurements in practice.

---

## Measurement Matrix

`MeasurementMatrix` is an immutable `record` that encapsulates:
- The sensing matrix `Φ` (a `DenseMatrix`), and
- An optional wavelet sparsifying basis Ψ.

All factory methods produce sensing matrices whose entries are scaled so that
`E[‖Φ x‖₂²] ≈ ‖x‖₂²` (isometry in expectation).

### Gaussian

```java
MeasurementMatrix mm = MeasurementMatrix.gaussian(m, n);
```

Entries are i.i.d. `N(0, 1/m)`. Gaussian matrices satisfy the RIP with optimal
sample complexity and are incoherent with every fixed basis. They are the
standard choice for general-purpose compressed sensing.

**When to use:** default choice; strongest theoretical guarantees.

### Bernoulli

```java
MeasurementMatrix mm = MeasurementMatrix.bernoulli(m, n);
```

Entries are i.i.d. `±1/√m` with equal probability. Equivalent RIP guarantees
to Gaussian, but entries are binary which can be advantageous for hardware
implementations.

**When to use:** hardware-friendly sensing, or when Gaussian random numbers
are expensive to generate.

### Partial Identity

A random row-subsampling of the `n × n` identity — equivalently, measuring
`m` randomly selected components of `x`:

```java
// Automatic random selection of m rows
MeasurementMatrix mm = MeasurementMatrix.partial(m, n);

// Explicit row indices (must be in [0, n))
int[] rows = {0, 7, 15, 22, 40};
MeasurementMatrix mm2 = MeasurementMatrix.partial(rows, n);
```

**When to use:** when only a random subset of signal samples can be acquired
(e.g. missing data, random sub-Nyquist sampling).  
**Requirement:** the signal must be sparse in a basis *incoherent* with the
identity (e.g. the DFT basis); it will not work if the signal is sparse in
the canonical (time/space) domain itself.

### Wavelet Sparsifying Basis

When the signal is sparse in a wavelet domain, chain the sensing matrix with
a wavelet basis using `withWavelet(Wavelet)`:

```java
import smile.wavelet.*;

// Compound operator A = Φ · IDWT  (m × n, implicit)
MeasurementMatrix wm = MeasurementMatrix.gaussian(m, n)
                                        .withWavelet(new DaubechiesWavelet(8));

// Take measurements: y = Φ · IDWT(s)  where s is the sparse wavelet vector
double[] ySparse = wm.measureSparse(s);

// Back-project to wavelet domain: v = DWT(Φ^T y)
double[] v = wm.backProjectSparse(y);

// Get the implicit compound matrix for use with solvers
Matrix A = wm.toMatrix();
```

The wavelet operator is applied **implicitly** (never forming the full `n × n`
matrix), making this memory-efficient for large `n`.

**Supported wavelets:** any subclass of `smile.wavelet.Wavelet` — `HaarWavelet`,
`D4Wavelet`, `DaubechiesWavelet`, `CoifletWavelet`, `SymletWavelet`,
`BestLocalizedWavelet`.

### Adjoint Operations

| Method | Computes | Notes |
|---|---|---|
| `mm.measure(x)` | `y = Φ x` | Forward measurement |
| `mm.measureSparse(s)` | `y = Φ · IDWT(s)` | Forward in wavelet domain |
| `mm.backProject(y)` | `v = Φᵀ y` | Back-projection (adjoint) |
| `mm.backProjectSparse(y)` | `v = DWT(Φᵀ y)` | Adjoint in wavelet domain |

The adjoint identity holds exactly (to floating-point precision):

```
⟨Φ x, y⟩  =  ⟨x, Φᵀ y⟩
```

### Implicit Matrix Interface

`mm.toMatrix()` returns a `smile.tensor.Matrix` that supports both `mv`
(forward) and `tv` (adjoint) products without materialising the full
compound matrix. Pass this directly to the solvers:

```java
Matrix A = mm.toMatrix();
OMP result = OMP.fit(A, y, k);
```

---

## Recovery Algorithms

All three algorithms share the same call convention:

```java
SolverResult result = Solver.fit(A, y, /* options */);
double[] xRecovered = result.x();       // recovered signal, length n
int[]    support    = result.support(); // non-zero index set (OMP, CoSaMP)
int      iters      = result.iter();    // iterations performed
```

### Orthogonal Matching Pursuit (OMP)

OMP is a **greedy** algorithm that builds the support set one column at a time.
At each iteration it selects the column of `A` most correlated with the current
residual, augments the support, and solves a small least-squares problem on the
active support.

**Convergence guarantee:** for a Gaussian matrix with `m ≥ C k log n`,
OMP recovers any `k`-sparse `x` exactly in exactly `k` iterations when
`δ_{2k} < √2 − 1 ≈ 0.41`.

```java
// With known sparsity k
OMP result = OMP.fit(A, y, k);

// With explicit options (custom tolerance)
var opts = new OMP.Options(k, 1e-8);
OMP result = OMP.fit(A, y, opts);

// Inspect results
System.out.println("Support: " + Arrays.toString(result.support()));
System.out.println("Iterations: " + result.iter());
```

**Algorithm sketch:**
1. Initialise residual `r = y`, support `S = ∅`.
2. Find `j* = argmax_j |⟨A_j, r⟩| / ‖A_j‖₂`.
3. Add `j*` to `S`; solve `min ‖y − A_S c‖₂` via QR (Gram–Schmidt).
4. Update residual `r = y − A_S c_S`.
5. Repeat until `|S| = k` or `‖r‖₂ / ‖y‖₂ < tol`.

**Complexity per iteration:** O(mn) correlation + O(mk²) least-squares.  
**Total:** O(k(mn + mk²)).

**When to use:**
- Sparsity `k` is known or can be estimated.
- Speed is the primary concern.
- Signal-to-noise ratio is high.

---

### CoSaMP

CoSaMP (Compressive Sampling Matching Pursuit) is a greedy algorithm with
**provably near-optimal** recovery guarantees — it achieves essentially the
same error as the best `k`-term approximation.

Unlike OMP, CoSaMP maintains a candidate support of size `2k`, prunes it back
to `k` at each step, and re-solves least-squares on the pruned support.

```java
// With known sparsity k
CoSaMP result = CoSaMP.fit(A, y, k);

// Over-estimated sparsity (2k) still recovers correctly
CoSaMP result = CoSaMP.fit(A, y, 2 * k);

// With explicit options
var opts = new CoSaMP.Options(k, 100, 1e-8);
CoSaMP result = CoSaMP.fit(A, y, opts);
```

**Algorithm sketch (one iteration):**
1. **Proxy:** `e = Aᵀ r` (gradient of data-fit term).
2. **Identify:** find the `2k` largest entries of `|e|`; form union Ω with current support.
3. **Least-squares:** `b = argmin ‖y − A_Ω b‖₂` on the union support Ω.
4. **Prune:** retain only the `k` largest entries of `b`.
5. **Update:** `x_new` = least-squares on the pruned support; `r = y − A x_new`.

**Convergence:** O(log(‖x‖₂ / ε)) iterations to reach error ε.

**When to use:**
- Provable guarantees are needed.
- Sparsity may be slightly over-estimated.
- Moderate noise tolerance is required.

---

### Basis Pursuit / BPDN

Basis Pursuit solves the convex L1-minimization problem:

```
Basis Pursuit (noiseless):
  minimise  ‖x‖₁
  subject to  Ax = y

Basis Pursuit DeNoising — BPDN (noisy):
  minimise  ‖x‖₁
  subject to  ‖Ax − y‖₂ ≤ ε
```

The implementation uses a **log-barrier interior-point method** (primal–dual
Newton's method). Each Newton step requires solving a linear system
`(A D A^T) ν = b` where `D` is a positive-definite diagonal matrix; this is
solved with a preconditioned conjugate-gradient (CG) method.

```java
// Exact Basis Pursuit (epsilon = 0)
BasisPursuit bp = BasisPursuit.fit(A, y);

// BPDN with explicit noise bound epsilon
double epsilon = 0.01 * MathEx.norm(y);
var opts = new BasisPursuit.Options(epsilon, 10.0, 1e-8, 200, 50, 1e-3);
BasisPursuit bp = BasisPursuit.fit(A, y, opts);

// Check feasibility
double[] Ax = BasisPursuit.matvec(A, bp.x(), m, n, false);
```

**Convergence:** O(log(1/ε)) outer Newton iterations; each inner CG solve
takes O(mn) per iteration.

**When to use:**
- Best recovery quality is needed, especially in moderate noise.
- `k` is unknown (L1-minimization automatically promotes sparsity).
- The problem is small-to-medium sized (inner CG per Newton step is expensive).

---

### Algorithm Comparison

| Property | OMP | CoSaMP | Basis Pursuit |
|---|---|---|---|
| Type | Greedy | Greedy | Convex |
| Recovery guarantee | Exact (RIP δ_{2k} < √2−1) | Near-optimal (RIP δ_{4k} < 0.1) | Near-optimal (δ_{2k} < √2−1) |
| Requires known `k` | Yes | Yes (robust to over-estimate) | No |
| Noisy robustness | Moderate | Good | Best |
| Speed | Fastest | Fast | Slowest |
| Memory | O(mk) | O(mn) | O(mn) |
| Iterations | Exactly `k` | O(log(1/ε)) | O(log(1/ε)) outer + CG inner |
| Output | x, support, iter | x, support, iter | x, iter |

---

## Hyperparameter Tuning

### OMP Options

```java
OMP.Options opts = new OMP.Options(
    k,     // sparsity:  target number of non-zero components (> 0)
    1e-6   // tol:       stop early when ‖r‖₂/‖y‖₂ < tol (≥ 0)
);
```

| Parameter | Default | Effect |
|---|---|---|
| `sparsity` | required | Maximum support size; algorithm terminates after exactly `k` iterations unless `tol` is reached first |
| `tol` | `1e-6` | Early stopping on relative residual; set to `0` to always run exactly `k` iterations |

**Guidelines:**
- Set `sparsity` to your best estimate of `k`. An over-estimate increases
  the number of iterations but usually does not hurt accuracy (the extra
  selected atoms will have near-zero coefficients).
- Tighten `tol` (e.g. `1e-10`) for high-precision noiseless recovery.
- Loosen `tol` (e.g. `1e-4`) for noisy data to avoid over-fitting the noise.

---

### CoSaMP Options

```java
CoSaMP.Options opts = new CoSaMP.Options(
    k,     // sparsity:  target sparsity level (> 0)
    50,    // maxIter:   maximum number of iterations (> 0)
    1e-6   // tol:       relative residual tolerance (≥ 0)
);
```

| Parameter | Default | Effect |
|---|---|---|
| `sparsity` | required | Target support size `k`; CoSaMP is robust to moderate over-estimation |
| `maxIter` | `50` | Iteration budget; CoSaMP typically converges in 10–30 iterations |
| `tol` | `1e-6` | Stop when `‖r‖₂/‖y‖₂ < tol` |

**Guidelines:**
- CoSaMP requires `2k < m` for the internal `2k`-proxy step to be
  well-defined. If `2k ≥ m`, a warning is logged and the result may be
  unreliable.
- For noisy data, set `tol` to a value slightly larger than the expected
  noise-to-signal ratio (e.g. `1e-3` for 0.1% noise).

---

### BasisPursuit Options

```java
BasisPursuit.Options opts = new BasisPursuit.Options(
    epsilon,   // L2 constraint bound (0.0 for exact BP)
    10.0,      // mu: barrier update factor (> 1)
    1e-8,      // cgtol: inner CG convergence tolerance (> 0)
    200,       // cgMaxIter: maximum CG iterations per Newton step (> 0)
    50,        // maxIter: maximum outer Newton iterations (> 0)
    1e-3       // tol: duality-gap stopping criterion (> 0)
);
```

| Parameter | Default | Effect |
|---|---|---|
| `epsilon` | `0.0` | Noise bound `ε`; set to `0` for exact BP (noiseless), or to an estimate of `‖noise‖₂` for BPDN |
| `mu` | `10.0` | Barrier parameter growth rate per outer iteration; larger → fewer outer iters but less stable |
| `cgtol` | `1e-8` | Inner CG solver tolerance; tighten for higher precision |
| `cgMaxIter` | `200` | CG budget per Newton step; increase for ill-conditioned problems |
| `maxIter` | `50` | Outer Newton iteration budget; typically 10–30 suffice |
| `tol` | `1e-3` | Duality-gap stopping criterion; tighten for higher precision |

**Guidelines:**
- For **exact BP** (`epsilon = 0`), use tight `cgtol = 1e-10` and a generous
  `cgMaxIter = 300` to ensure the Newton steps are accurate enough.
- For **BPDN** (`epsilon > 0`), a natural choice for `epsilon` is
  `epsilon = σ √m` where `σ` is the standard deviation of the measurement
  noise. Alternatively use `epsilon = 0.05 * ‖y‖₂` as a conservative estimate.
- Increase `mu` to `20` or `50` if the algorithm is converging too slowly;
  decrease to `5` if it is oscillating.

---

### Persisting Options

All `Options` records support serialization to/from `java.util.Properties`:

```java
// Serialize
OMP.Options opts = new OMP.Options(5, 1e-6);
Properties props = opts.toProperties();
props.store(new FileWriter("omp.properties"), "OMP options");

// Deserialize
Properties loaded = new Properties();
loaded.load(new FileReader("omp.properties"));
OMP.Options opts2 = OMP.Options.of(loaded);
```

The same pattern applies to `CoSaMP.Options` and `BasisPursuit.Options`.

---

## Choosing a Sensing Matrix

| Signal type | Sensing matrix | Notes |
|---|---|---|
| General `k`-sparse in canonical basis | `gaussian(m, n)` | Best theoretical guarantees |
| Binary/integer signal values | `bernoulli(m, n)` | Hardware-friendly |
| Signal sparse in frequency (DFT) | `partial(m, n)` | Random sub-sampling in time |
| Signal sparse in wavelet domain | `gaussian(m, n).withWavelet(w)` | Memory-efficient implicit operator |
| Very large `n` (implicit operator) | Any `mm.toMatrix()` | DWT applied without forming Ψ |

**Rule of thumb for `m`:** aim for `m ≥ 4k` at minimum. For reliable recovery
with high probability use `m ≥ 4k log(n/k)`. Check the recovery error and
increase `m` if it is unsatisfactory.

---

## Noisy Measurements

In practice, measurements always contain noise: `y = Ax + ε`. All three
algorithms handle noise, but with different mechanisms:

### OMP (noisy)
Use the `tol` option to stop early before the algorithm fits the noise:

```java
// Stop when relative residual < noise level
double noiseLevel = sigma * Math.sqrt(m) / MathEx.norm(y);
OMP result = OMP.fit(A, y, new OMP.Options(k, noiseLevel));
```

### CoSaMP (noisy)
CoSaMP has built-in noise stability. The recovery error satisfies:

```
‖x_rec − x‖₂  ≤  C · ‖ε‖₂ / √m
```

Use the default options and set `tol` to match the noise level:

```java
double sigma = 0.01;  // per-measurement noise std
double[] yNoisy = y.clone();
for (int i = 0; i < m; i++) yNoisy[i] += sigma * rng.nextGaussian();

CoSaMP result = CoSaMP.fit(A, yNoisy, new CoSaMP.Options(k, 100, sigma));
```

### Basis Pursuit Denoising (BPDN)
Set `epsilon` to bound the expected noise norm `‖ε‖₂ ≈ σ √m`:

```java
double sigma = 0.01;
double epsilon = sigma * Math.sqrt(m);  // expected ‖ε‖₂

var opts = new BasisPursuit.Options(epsilon, 10.0, 1e-8, 200, 50, 1e-3);
BasisPursuit result = BasisPursuit.fit(A, yNoisy, opts);
```

---

## Wavelet-Domain Sparsity

Natural signals such as audio and images are often **not** sparse in the
canonical basis, but become sparse after a wavelet transform. The
`MeasurementMatrix.withWavelet()` API handles this transparently.

### Setup

```java
import smile.wavelet.*;

int n = 256;  // signal length (must be power of 2)
int m = 60;   // number of measurements
int k = 8;    // sparsity in wavelet domain

// True sparse wavelet coefficients
double[] sSparse = new double[n];
sSparse[3] = 2.5; sSparse[17] = -1.8; // ... k non-zeros

// Synthesise the signal from its wavelet coefficients
double[] xSignal = sSparse.clone();
new DaubechiesWavelet(8).inverse(xSignal);   // x = IDWT(s)

// Build compound measurement matrix A = Φ · IDWT
MeasurementMatrix wm = MeasurementMatrix.gaussian(m, n)
                                        .withWavelet(new DaubechiesWavelet(8));

// Measurements: y = Φ · IDWT(s) = Φ x
double[] y = wm.measureSparse(sSparse);
```

### Recovery

The recovery algorithms operate on the **wavelet coefficient domain** —
they recover the sparse `s`, not `x` directly:

```java
// Get the implicit compound matrix A = Φ Ψ
Matrix A = wm.toMatrix();   // m × n, implicit

// Recover sparse coefficients s via OMP
OMP result = OMP.fit(A, y, k);
double[] sRec = result.x();

// Synthesise the recovered signal: x_rec = IDWT(s_rec)
double[] xRec = sRec.clone();
new DaubechiesWavelet(8).inverse(xRec);
```

### Choosing a Wavelet for CS

| Wavelet | Notes |
|---|---|
| `HaarWavelet` | Fastest; good for piecewise-constant signals |
| `DaubechiesWavelet(8)` | Good general choice for smooth signals |
| `DaubechiesWavelet(20)` | Best sparsity for very smooth signals |
| `CoifletWavelet(12)` | Good when scaling coefficients must match samples |
| `SymletWavelet(8)` | Near-symmetric; reduced boundary effects |

---

## Complete Examples

### Example 1 — Basic Sparse Recovery with All Three Algorithms

```java
import smile.cs.*;
import smile.math.MathEx;
import java.util.Arrays;

public class BasicRecoveryExample {
    public static void main(String[] args) {
        MathEx.setSeed(2024);
        int n = 256, m = 60, k = 8;

        // Build k-sparse ground truth
        double[] xTrue = new double[n];
        int[] locs = MathEx.permutate(n);
        for (int i = 0; i < k; i++) {
            xTrue[locs[i]] = (i % 2 == 0 ? 1 : -1) * (1.0 + MathEx.random());
        }

        // Gaussian sensing matrix
        MeasurementMatrix mm = MeasurementMatrix.gaussian(m, n);
        double[] y = mm.measure(xTrue);

        // OMP
        OMP omp = OMP.fit(mm.phi(), y, k);
        System.out.printf("OMP:   rel error = %.2e, support = %s%n",
                relError(omp.x(), xTrue), Arrays.toString(omp.support()));

        // CoSaMP
        CoSaMP cosamp = CoSaMP.fit(mm.phi(), y, k);
        System.out.printf("CoSaMP: rel error = %.2e, iters = %d%n",
                relError(cosamp.x(), xTrue), cosamp.iter());

        // Basis Pursuit (exact)
        var bpOpts = new BasisPursuit.Options(0.0, 10.0, 1e-10, 300, 50, 1e-4);
        BasisPursuit bp = BasisPursuit.fit(mm.phi(), y, bpOpts);
        System.out.printf("BP:    rel error = %.2e, iters = %d%n",
                relError(bp.x(), xTrue), bp.iter());
    }

    static double relError(double[] xRec, double[] xTrue) {
        double num = 0, den = 0;
        for (int i = 0; i < xTrue.length; i++) {
            double d = xRec[i] - xTrue[i];
            num += d * d; den += xTrue[i] * xTrue[i];
        }
        return Math.sqrt(num / den);
    }
}
```

### Example 2 — Noisy Recovery with BPDN

```java
import smile.cs.*;
import smile.math.MathEx;
import java.util.Random;

public class NoisyRecoveryExample {
    public static void main(String[] args) {
        MathEx.setSeed(42);
        Random rng = new Random(42);
        int n = 128, m = 50, k = 5;
        double sigma = 0.05;   // per-measurement noise std

        // Ground truth
        double[] xTrue = new double[n];
        int[] locs = MathEx.permutate(n);
        for (int i = 0; i < k; i++) xTrue[locs[i]] = 1.0 + MathEx.random();

        // Noisy measurements
        MeasurementMatrix mm = MeasurementMatrix.gaussian(m, n);
        double[] yClean = mm.measure(xTrue);
        double[] yNoisy = yClean.clone();
        for (int i = 0; i < m; i++) yNoisy[i] += sigma * rng.nextGaussian();

        System.out.printf("Noise level: ‖ε‖₂ = %.4f%n",
                MathEx.norm(subtract(yNoisy, yClean)));

        // BPDN: set epsilon = sigma * sqrt(m)
        double epsilon = sigma * Math.sqrt(m);
        var opts = new BasisPursuit.Options(epsilon, 10.0, 1e-8, 200, 50, 1e-3);
        BasisPursuit bp = BasisPursuit.fit(mm.phi(), yNoisy, opts);

        // Verify residual constraint
        double[] Ax = BasisPursuit.matvec(mm.phi(), bp.x(), m, n, false);
        double resNorm = MathEx.norm(subtract(Ax, yNoisy));
        System.out.printf("BPDN: ‖Ax−y‖₂ = %.4f, epsilon = %.4f%n", resNorm, epsilon);
        System.out.printf("BPDN: rel error = %.4f%n",
                MathEx.norm(subtract(bp.x(), xTrue)) / MathEx.norm(xTrue));
    }

    static double[] subtract(double[] a, double[] b) {
        double[] r = a.clone();
        for (int i = 0; i < r.length; i++) r[i] -= b[i];
        return r;
    }
}
```

### Example 3 — Wavelet-Domain Sparse Recovery

```java
import smile.cs.*;
import smile.math.MathEx;
import smile.wavelet.*;

public class WaveletRecoveryExample {
    public static void main(String[] args) {
        MathEx.setSeed(100);
        int n = 128, m = 35, k = 6;
        Wavelet psi = new DaubechiesWavelet(8);

        // k-sparse signal in wavelet domain
        double[] sSparse = new double[n];
        int[] locs = MathEx.permutate(n);
        for (int i = 0; i < k; i++) sSparse[locs[i]] = 1.5 + MathEx.random();

        // Synthesise signal x = IDWT(s)
        double[] xSignal = sSparse.clone();
        psi.inverse(xSignal);

        // Compound measurement matrix A = Φ IDWT
        MeasurementMatrix wm = MeasurementMatrix.gaussian(m, n).withWavelet(psi);
        double[] y = wm.measureSparse(sSparse);

        // Recover sparse coefficients via OMP on A
        OMP result = OMP.fit(wm.toMatrix(), y, k);
        double[] sRec = result.x();

        // Reconstruct signal
        double[] xRec = sRec.clone();
        psi.inverse(xRec);

        // Report
        double coeffErr = MathEx.norm(subtract(sRec, sSparse)) / MathEx.norm(sSparse);
        double signalErr = MathEx.norm(subtract(xRec, xSignal)) / MathEx.norm(xSignal);
        System.out.printf("Wavelet coeff error: %.2e%n", coeffErr);
        System.out.printf("Signal error:         %.2e%n", signalErr);
        System.out.printf("Support: %s%n", java.util.Arrays.toString(result.support()));
    }

    static double[] subtract(double[] a, double[] b) {
        double[] r = a.clone(); for (int i = 0; i < r.length; i++) r[i] -= b[i]; return r;
    }
}
```

### Example 4 — Persisting and Loading Options

```java
import smile.cs.*;
import java.util.Properties;
import java.io.*;

public class OptionsSerializationExample {
    public static void main(String[] args) throws IOException {
        // Create and save OMP options
        OMP.Options ompOpts = new OMP.Options(8, 1e-8);
        Properties p = ompOpts.toProperties();
        try (var w = new FileWriter("omp.properties")) {
            p.store(w, "OMP recovery options");
        }

        // Load back
        Properties loaded = new Properties();
        try (var r = new FileReader("omp.properties")) {
            loaded.load(r);
        }
        OMP.Options restored = OMP.Options.of(loaded);
        System.out.printf("Restored: sparsity=%d, tol=%.2e%n",
                restored.sparsity(), restored.tol());

        // Same pattern for BasisPursuit
        BasisPursuit.Options bpOpts = new BasisPursuit.Options(0.02, 10.0, 1e-8, 200, 50, 1e-3);
        Properties bp = bpOpts.toProperties();
        BasisPursuit.Options bpRestored = BasisPursuit.Options.of(bp);
        System.out.printf("BP epsilon=%.4f, mu=%.1f%n",
                bpRestored.epsilon(), bpRestored.mu());
    }
}
```

### Example 5 — Adjoint / Incoherence Verification

```java
import smile.cs.*;
import smile.math.MathEx;

public class AdjointCheckExample {
    public static void main(String[] args) {
        MathEx.setSeed(0);
        int m = 30, n = 100;
        MeasurementMatrix mm = MeasurementMatrix.gaussian(m, n);

        double[] x = new double[n];
        double[] yv = new double[m];
        for (int i = 0; i < n; i++) x[i] = MathEx.random() - 0.5;
        for (int i = 0; i < m; i++) yv[i] = MathEx.random() - 0.5;

        // Verify ⟨Φx, y⟩ = ⟨x, Φᵀy⟩
        double[] Ax  = mm.measure(x);
        double[] Aty = mm.backProject(yv);
        double lhs = MathEx.dot(Ax, yv);
        double rhs = MathEx.dot(x, Aty);
        System.out.printf("⟨Φx,y⟩  = %.10f%n", lhs);
        System.out.printf("⟨x,Φᵀy⟩ = %.10f%n", rhs);
        System.out.printf("Error:   = %.2e%n", Math.abs(lhs - rhs));
    }
}
```

---

## Mathematical Background

### Sparse Recovery as L0 Minimization

The ideal (but NP-hard) formulation is:

```
minimise  ‖x‖₀   subject to  Ax = y
```

where `‖x‖₀` counts the number of non-zeros. CS theory shows that under
the RIP condition, the convex relaxation

```
minimise  ‖x‖₁   subject to  Ax = y
```

achieves the same solution. This is the Basis Pursuit problem.

### Why L1 Promotes Sparsity

The unit ball of `‖·‖₁` in `ℝⁿ` is a cross-polytope (diamond in 2D). When a
hyperplane `{x : Ax = y}` is tangent to this ball, it touches a vertex or
edge — corresponding to a sparse vector. This geometric intuition explains why
L1 minimization recovers sparse solutions.

### RIP and Incoherence

Two equivalent conditions guarantee recovery:

1. **RIP:** `A` has `δ_{2k} < √2 − 1`. Gaussian and Bernoulli matrices satisfy
   this with `m = O(k log(n/k))` rows.

2. **Mutual incoherence:** The coherence `μ(A) = max_{i≠j} |⟨A_i, A_j⟩|`
   between columns of the normalised `A` should be small. OMP and BP provably
   recover any `k`-sparse signal when `k < (1 + 1/μ) / 2`.

### Universal Threshold for `m`

For Gaussian `Φ` and a fixed sparsifying basis Ψ, exact recovery holds with
probability ≥ `1 − δ` when:

```
m  ≥  C · k · log(n/k) · log(1/δ)
```

For `k = 5`, `n = 128`, `δ = 0.01`:
```
m  ≥  2 · 5 · log(25.6) · log(100)  ≈  75
```

In practice the constant `C ≈ 1–2` and `m ≥ 4k` often suffices empirically.

### Gram–Schmidt Least-Squares in OMP

OMP solves the active-support least-squares problem via incremental
QR (Gram–Schmidt orthogonalisation). At step `t`, the QR factors of
`A_{S_t}` are updated in O(mt) time by orthogonalising the new column
against the existing Q-vectors. The coefficient estimate is then
`c = Qᵀ y` (projection) followed by back-substitution through R.

### Interior-Point Method for Basis Pursuit

Basis Pursuit is solved by lifting to:

```
minimise  1ᵀu   subject to  Ax = y,  −u ≤ x ≤ u
```

and applying a **log-barrier** (primal–dual interior-point) method:

```
minimise  t · 1ᵀu − Σ log(u_i + x_i) − Σ log(u_i − x_i)
subject to  Ax = y
```

The barrier parameter `t` is multiplied by `μ > 1` at each outer iteration.
Each Newton step requires solving a system of the form `(A D A^T) ν = b`
where `D = diag(1/w)` is computed from the barrier Hessian; this is solved
with CG.

---

## References

1. E. J. Candès and M. B. Wakin,
   "An introduction to compressive sampling,"
   *IEEE Signal Process. Mag.*, 25(2):21–30, 2008.

2. E. J. Candès and T. Tao,
   "Near-optimal signal recovery from random projections: Universal encoding strategies?"
   *IEEE Trans. Inf. Theory*, 52(12):5406–5425, 2006.

3. D. L. Donoho,
   "Compressed sensing,"
   *IEEE Trans. Inf. Theory*, 52(4):1289–1306, 2006.

4. J. A. Tropp and A. C. Gilbert,
   "Signal recovery from random measurements via orthogonal matching pursuit,"
   *IEEE Trans. Inf. Theory*, 53(12):4655–4666, 2007.

5. D. Needell and J. A. Tropp,
   "CoSaMP: Iterative signal recovery from incomplete and inaccurate samples,"
   *Appl. Comput. Harmon. Anal.*, 26(3):301–321, 2009.

6. S. S. Chen, D. L. Donoho and M. A. Saunders,
   "Atomic decomposition by basis pursuit,"
   *SIAM Rev.*, 43(1):129–159, 2001.

7. E. J. Candès, J. Romberg and T. Tao,
   "Robust uncertainty principles: Exact signal reconstruction from highly
   incomplete frequency information,"
   *IEEE Trans. Inf. Theory*, 52(2):489–509, 2006.

---

*Package:* `smile.cs` · *Module:* `smile-base` · *Since:* SMILE 6.0

