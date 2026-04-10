# SMILE — BFGS Optimization

The `smile.math.BFGS` class implements three closely related quasi-Newton
minimization algorithms, all accessible through overloaded `minimize()` static
methods:

| Variant | Method signature | Memory | Constraints |
|---------|-----------------|--------|-------------|
| **BFGS** | `minimize(func, x, gtol, maxIter)` | O(n²) | none |
| **L-BFGS** | `minimize(func, m, x, gtol, maxIter)` | O(m·n) | none |
| **L-BFGS-B** | `minimize(func, m, x, l, u, gtol, maxIter)` | O(m·n) | box bounds |

---

## Background

### BFGS

The *Broyden–Fletcher–Goldfarb–Shanno* (BFGS) algorithm is the gold standard
for moderate-sized unconstrained smooth minimization. It belongs to the class
of **quasi-Newton** methods: instead of computing the exact Hessian ∇²f(x)
(expensive) it maintains a running approximation **H** of the *inverse* Hessian,
updated cheaply at each iteration using only gradient differences.

Each BFGS iteration performs three steps:

1. **Descent direction**: compute **d** = −**H** ∇f(x).
2. **Line search**: find a step length α satisfying the Wolfe conditions
   (sufficient decrease + curvature); update x ← x + α **d**.
3. **Hessian update**: update **H** using the rank-2 formula:

$$
H_{k+1} = H_k
  + \frac{s_k s_k^\top}{s_k^\top y_k}
  - \frac{H_k y_k y_k^\top H_k}{y_k^\top H_k y_k}
$$

where **s**ₖ = xₖ₊₁ − xₖ and **y**ₖ = ∇f(xₖ₊₁) − ∇f(xₖ).

Because **H** is stored as a dense n × n matrix, the memory cost is **O(n²)**,
which makes BFGS impractical for problems with more than a few thousand
variables.

### L-BFGS

*Limited-memory BFGS* (L-BFGS) avoids storing the full inverse Hessian
approximation. Instead it keeps a rolling window of the last **m** pairs of
(position difference **s**, gradient difference **y**) vectors and uses a
two-loop recursion to compute the matrix–vector product **H**ₖ **g** implicitly.

Memory is **O(m·n)** — independent of the problem dimension. A history size of
`m = 5` (the default suggested range is 3–7) is sufficient for most problems.

### L-BFGS-B

*Limited-memory BFGS with Bounds* (L-BFGS-B) extends L-BFGS to handle
**box constraints**:

```
lᵢ ≤ xᵢ ≤ uᵢ    for each variable xᵢ
```

At each iteration the algorithm:

1. Computes the **Generalized Cauchy Point** (GCP) — the first local minimizer
   of a piecewise-linear model along the projected steepest-descent path.  This
   identifies which variables are at their bounds ("fixed") and which are free.
2. Solves a **reduced subspace problem** over the free variables using the
   L-BFGS inverse Hessian approximation.
3. Performs a **projected line search** that keeps the iterate inside the box.

---

## API

All variants share the same pattern: the function value at the minimum is
returned, and the solution vector `x` is updated **in-place**.

### Implementing the Objective Function

All three variants require a
`smile.util.function.DifferentiableMultivariateFunction`, which has two methods:

```java
/** Returns f(x). */
double f(double[] x);

/** Computes the gradient g = ∇f(x) and returns f(x). */
double g(double[] x, double[] g);
```

The `g()` method must fill the supplied array `g` with the partial derivatives
and also return the function value. This dual-purpose design avoids redundant
evaluations when the value and gradient are computed together.

### BFGS — Unconstrained, Dense Hessian

```java
double minimum = BFGS.minimize(func, x, gtol, maxIter);
```

| Parameter | Meaning |
|-----------|---------|
| `func` | `DifferentiableMultivariateFunction` to minimize |
| `x` | initial guess; **overwritten** with the solution on return |
| `gtol` | gradient convergence tolerance (e.g. `1e-5`); must be `> 0` |
| `maxIter` | maximum number of outer iterations |

**Returns** the function value at the best point found.

Convergence is declared when either:
- the scaled gradient norm falls below `gtol`, or
- the scaled step length falls below `4 × ε` (machine epsilon × 10⁻⁸).

If `maxIter` is reached without convergence a warning is logged and the best
value found so far is returned.

### L-BFGS — Unconstrained, Limited Memory

```java
double minimum = BFGS.minimize(func, m, x, gtol, maxIter);
```

| Parameter | Meaning |
|-----------|---------|
| `m` | number of correction pairs to retain (`3 ≤ m ≤ 7` recommended; `m = 5` is a common choice) |
| (others) | same as BFGS |

Use L-BFGS instead of BFGS when **n > ~1000** or when memory is limited.  For
small n the dense BFGS method typically converges in fewer iterations.

### L-BFGS-B — Bound-Constrained, Limited Memory

```java
double minimum = BFGS.minimize(func, m, x, l, u, gtol, maxIter);
```

| Parameter | Meaning |
|-----------|---------|
| `l` | lower bounds array, length `n` |
| `u` | upper bounds array, length `n` |
| (others) | same as L-BFGS |

Both `l` and `u` must have the same length as `x`.  The initial point `x` need
not satisfy the bounds; `x` is clamped to `[l, u]` before the first iteration.
Use `Double.NEGATIVE_INFINITY` / `Double.POSITIVE_INFINITY` to leave individual
components unbounded.

---

## Configuration

The internal convergence constants can be tuned via JVM system properties:

| Property | Default | Meaning |
|----------|---------|---------|
| `smile.bfgs.epsilon` | `1e-8` | Base epsilon used to derive `TOLX` and `TOLF` |

Set on the command line with `-Dsmile.bfgs.epsilon=1e-10`.

---

## Examples

### Example 1 — BFGS on the Rosenbrock Function

The *Rosenbrock banana function* `f(x, y) = (1-x)² + 100(y-x²)²` has a
narrow curved valley; its global minimum is 0 at (1, 1).

```java
import smile.math.BFGS;
import smile.util.function.DifferentiableMultivariateFunction;

DifferentiableMultivariateFunction rosenbrock = new DifferentiableMultivariateFunction() {
    @Override
    public double f(double[] x) {
        double t1 = 1.0 - x[0];
        double t2 = 10.0 * (x[1] - x[0] * x[0]);
        return t1 * t1 + t2 * t2;
    }

    @Override
    public double g(double[] x, double[] g) {
        double t1 = 1.0 - x[0];
        double t2 = 10.0 * (x[1] - x[0] * x[0]);
        g[1] = 20.0 * t2;
        g[0] = -2.0 * (x[0] * g[1] + t1);
        return t1 * t1 + t2 * t2;
    }
};

double[] x = {-1.2, 1.0};             // starting point
double fmin = BFGS.minimize(rosenbrock, x, 1e-5, 500);

System.out.printf("Minimum: f = %.2e at (%.6f, %.6f)%n", fmin, x[0], x[1]);
// Minimum: f = 3.45e-10 at (1.000000, 1.000000)
```

### Example 2 — L-BFGS for Large-Scale Problems

```java
int n = 10_000;
// Quadratic bowl: f(x) = Σ i·xᵢ² — minimum is 0 at the origin
DifferentiableMultivariateFunction bowl = new DifferentiableMultivariateFunction() {
    @Override
    public double f(double[] x) {
        double sum = 0;
        for (int i = 0; i < x.length; i++) sum += (i + 1.0) * x[i] * x[i];
        return sum;
    }

    @Override
    public double g(double[] x, double[] g) {
        double sum = 0;
        for (int i = 0; i < x.length; i++) {
            g[i] = 2.0 * (i + 1.0) * x[i];
            sum += (i + 1.0) * x[i] * x[i];
        }
        return sum;
    }
};

double[] x = new double[n];
java.util.Arrays.fill(x, 1.0);        // start at all-ones

// m = 5 history pairs — dense BFGS would need a 10000×10000 matrix
double fmin = BFGS.minimize(bowl, 5, x, 1e-8, 1000);
System.out.printf("Minimum: %.6e%n", fmin);
```

### Example 3 — L-BFGS-B with Box Constraints

Find the minimum of `f(x₀, x₁) = (x₀ − 3)² + (x₁ − 4)² + 1` subject to
`3.5 ≤ x₀ ≤ 5` and `3.5 ≤ x₁ ≤ 5`.  The unconstrained minimum (3, 4) is
outside the box, so the constrained minimum is at the boundary (3.5, 4).

```java
DifferentiableMultivariateFunction func = new DifferentiableMultivariateFunction() {
    @Override
    public double f(double[] x) {
        double d0 = x[0] - 3, d1 = x[1] - 4;
        return d0 * d0 + d1 * d1 + 1;
    }

    @Override
    public double g(double[] x, double[] g) {
        double d0 = x[0] - 3, d1 = x[1] - 4;
        g[0] = 2 * d0;
        g[1] = 2 * d1;
        return d0 * d0 + d1 * d1 + 1;
    }
};

double[] x = {0.0, 0.0};              // starting point (outside bounds is fine)
double[] l = {3.5, 3.5};
double[] u = {5.0, 5.0};

double fmin = BFGS.minimize(func, 5, x, l, u, 1e-8, 500);
System.out.printf("x = (%.4f, %.4f), f = %.4f%n", x[0], x[1], fmin);
// x = (3.5000, 4.0000), f = 1.2500
```

---

## Choosing the Right Variant

```
Problem size         Constraints      Recommended variant
──────────────────────────────────────────────────────────
n ≤ 1 000            none             BFGS
n > 1 000            none             L-BFGS (m = 5)
any n                box bounds       L-BFGS-B (m = 5)
```

**General guidelines:**

- For **smooth, well-conditioned** problems BFGS usually converges in O(n)
  iterations; L-BFGS may need slightly more but uses far less memory.
- Prefer **L-BFGS** whenever `n > 1000` to avoid the O(n²) Hessian storage.
- Use **L-BFGS-B** any time variables have natural bounds (e.g. probabilities
  in [0, 1], non-negative weights, angles in [0, 2π]).
- All three variants require the **gradient** to be analytically supplied.
  If only a function value is available, consider finite-difference approximation
  or automatic differentiation to construct the `DifferentiableMultivariateFunction`.

---

## Convergence Criteria

All variants use the same two stopping tests:

1. **Step-length test** — converged when the largest relative step component
   falls below `TOLX = 4ε`:

   ```
   max_i |Δxᵢ| / max(|xᵢ|, 1) < TOLX
   ```

2. **Gradient test** — converged when the scaled infinity-norm of the gradient
   falls below `gtol`:

   ```
   max_i |∂f/∂xᵢ| · max(|xᵢ|, 1) / max(f, 1) < gtol
   ```

   L-BFGS-B uses the **projected gradient** norm instead, which respects the
   box constraints:

   ```
   max_i |proj_gradient_i| < gtol
   ```

   where `proj_gradient_i = clip(xᵢ − gᵢ, lᵢ, uᵢ) − xᵢ`.

---

## Line Search

All three variants share the same Wolfe-condition backtracking line search.
Starting from a full step α = 1, the step is reduced using:

- **Quadratic interpolation** on the first backtrack, then
- **Cubic interpolation** on subsequent backtracks,

with the minimum step bounded below at `0.1 × α` to prevent steps from
shrinking too fast, and bounded above at `stpmax = 100 × max(‖x‖, n)` to
prevent excessively large jumps into undefined regions.

The sufficient decrease condition (Armijo) is checked with `ftol = 1e-4`.

---

## References

1. R. Fletcher, *Practical Methods of Optimization*, 2nd ed., Wiley, 1987.
2. D. C. Liu and J. Nocedal, "On the limited memory BFGS method for large
   scale optimization," *Mathematical Programming B*, 45 (1989), 503–528.
3. R. H. Byrd, P. Lu, J. Nocedal, and C. Zhu, "A limited memory algorithm for
   bound constrained optimization," *SIAM Journal on Scientific Computing*,
   16(5) (1995), 1190–1208.


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

