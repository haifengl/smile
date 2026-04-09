# SMILE — Probability Distributions

The `smile.stat.distribution` package provides a comprehensive library of
univariate and multivariate probability distributions, mixture models, and
non-parametric density estimators. All distributions share a common
interface, making it easy to swap them in algorithms.

---

## Table of Contents

1. [Core Interfaces](#1-core-interfaces)
2. [Continuous Univariate Distributions](#2-continuous-univariate-distributions)
3. [Discrete Univariate Distributions](#3-discrete-univariate-distributions)
4. [Multivariate Distributions](#4-multivariate-distributions)
5. [Mixture Models](#5-mixture-models)
6. [Non-Parametric Density Estimation](#6-non-parametric-density-estimation)
7. [Parameter Estimation (MLE / MOM)](#7-parameter-estimation-mle--mom)
8. [Random Variate Generation](#8-random-variate-generation)
9. [Numerical Notes](#9-numerical-notes)
10. [Quick Reference](#10-quick-reference)

---

## 1. Core Interfaces

### `Distribution`

Every continuous univariate distribution implements this interface:

| Method | Description |
|--------|-------------|
| `p(double x)` | PDF value at `x` |
| `logp(double x)` | Log-PDF (computed directly for stability) |
| `cdf(double x)` | Cumulative distribution function P(X ≤ x) |
| `quantile(double p)` | Inverse CDF (quantile function) |
| `mean()` | Distribution mean |
| `variance()` | Distribution variance |
| `sd()` | Standard deviation (default: `√variance()`) |
| `entropy()` | Shannon entropy |
| `rand()` | Draw one random sample |
| `rand(int n)` | Draw `n` random samples |
| `likelihood(double[] x)` | Likelihood of a data set |
| `logLikelihood(double[] x)` | Log-likelihood of a data set |

### `DiscreteDistribution`

Discrete distributions additionally provide:

| Method | Description |
|--------|-------------|
| `p(int k)` | PMF value at integer `k` |
| `logp(int k)` | Log-PMF |
| `cdf(double k)` | CDF at `k` |
| `randi()` | Draw one integer sample |
| `randi(int n)` | Draw `n` integer samples |

### `ExponentialFamily` / `DiscreteExponentialFamily`

Distributions in the exponential family implement an `M()` method for the
M-step of the EM algorithm, enabling them to be used inside mixture models.

---

## 2. Continuous Univariate Distributions

### Gaussian (Normal) Distribution

**`GaussianDistribution(double mu, double sigma)`**

The bell-shaped distribution parameterised by mean μ and standard deviation σ.

```java
// Create N(2.0, 1.5)
var g = new GaussianDistribution(2.0, 1.5);

System.out.println(g.p(2.0));      // ~0.2659 (mode value)
System.out.println(g.cdf(3.5));    // P(X ≤ 3.5)
System.out.println(g.quantile(0.975)); // 97.5th percentile

// Fit to data by MLE (estimates mu and sigma)
double[] data = g.rand(1000);
var est = GaussianDistribution.fit(data);
System.out.println(est); // GaussianDistribution(mu≈2.0, sigma≈1.5)
```

The shared standard-normal singleton is obtained via `GaussianDistribution.getInstance()`.
It uses the Box-Muller algorithm for random variate generation. Its cache is
automatically reset when `MathEx.setSeed()` is called, ensuring reproducible
sequences.

---

### Log-Normal Distribution

**`LogNormalDistribution(double mu, double sigma)`**

If ln(X) ~ N(μ, σ²) then X has a log-normal distribution.

```java
var d = new LogNormalDistribution(1.0, 0.5);
System.out.println(d.mean());     // exp(mu + sigma²/2)
System.out.println(d.variance()); // (exp(sigma²)-1)*exp(2*mu+sigma²)

double[] data = d.rand(500);
var est = LogNormalDistribution.fit(data);
```

> **Note**: `variance()` uses the correct formula `(e^σ²-1)·e^(2μ+σ²)`.
> Older releases incorrectly used μ² instead of σ² in the first exponent.

---

### Gamma Distribution

**`GammaDistribution(double k, double theta)`**

Shape parameter `k > 0`, scale parameter `θ > 0`.
PDF: f(x) = x^(k−1) · e^(−x/θ) / (θ^k · Γ(k))

```java
// Gamma(3, 2.1)  — shape=3, scale=2.1
var d = new GammaDistribution(3, 2.1);
System.out.println(d.mean());     // k * theta = 6.3
System.out.println(d.variance()); // k * theta² = 13.23

// Fit by MLE
double[] data = d.rand(1000);
var est = GammaDistribution.fit(data);
```

**Fractional shape parameters** are fully supported via the
**Marsaglia-Tsang (2000)** algorithm. For `0 < k < 1`, the implementation
uses the `Gamma(k+1)` trick (`X = Gamma(k+1) · U^(1/k)`).

Special cases:
- `k=1, θ=1/λ` → Exponential(λ)
- `k=ν/2, θ=2` → χ²(ν)

---

### Exponential Distribution

**`ExponentialDistribution(double lambda)`**

Rate parameter λ > 0. Mean = 1/λ. Memoryless property.

```java
var d = new ExponentialDistribution(2.0);  // rate=2, mean=0.5
System.out.println(d.mean());     // 0.5
System.out.println(d.cdf(1.0));   // 1 - e^{-2} ≈ 0.865

var est = ExponentialDistribution.fit(data);
```

---

### Beta Distribution

**`BetaDistribution(double alpha, double beta)`**

Defined on [0, 1]; parameterised by shape parameters α > 0, β > 0.

```java
var d = new BetaDistribution(2.0, 5.0);
System.out.println(d.mean());  // alpha/(alpha+beta) = 2/7
System.out.println(d.p(0.3));  // pdf at 0.3

var est = BetaDistribution.fit(data);
```

`logp()` is computed directly using `lgamma` for numerical precision, with
correct edge-case handling at `x=0` and `x=1`.

---

### Weibull Distribution

**`WeibullDistribution(double k, double lambda)`**

Shape `k > 0`, scale `λ > 0`. Widely used in reliability / survival analysis.

```java
var d = new WeibullDistribution(1.5, 2.0);
System.out.println(d.mean());
System.out.println(d.cdf(1.0));

// MLE fitting via Newton-Raphson
double[] data = d.rand(1000);
var est = WeibullDistribution.fit(data);
System.out.println("k=" + est.k + ", lambda=" + est.lambda);
```

Special case: `k=1` → Exponential distribution.

---

### Logistic Distribution

**`LogisticDistribution(double mu, double scale)`**

Location μ, positive scale `s`.

```java
var d = new LogisticDistribution(0.0, 1.0);
System.out.println(d.p(0.0));   // 0.25
System.out.println(d.cdf(0.0)); // 0.5
```

`logp()` is computed directly using `log1p(exp(-z))` for numerical stability
in the tails.

---

### t-Distribution (Student's t)

**`TDistribution(int nu)`**

Degrees of freedom `ν ≥ 1`. Used in hypothesis testing when population
standard deviation is unknown.

```java
var t20 = new TDistribution(20);
System.out.println(t20.cdf(2.086));  // one-sided p-value
System.out.println(t20.quantile(0.975)); // critical value

// Two-tailed tests
System.out.println(t20.cdf2tailed(2.086));
System.out.println(t20.quantile2tailed(0.05)); // two-tailed 5% critical value
```

Edge cases:
- `ν=1` (Cauchy): `mean()` and `variance()` throw `UnsupportedOperationException`
- `ν=2`: `variance()` returns `Double.POSITIVE_INFINITY`

Random samples are generated via `Z / √(χ²(ν)/ν)` (ratio method),
not the slow inverse-CDF bisection.

---

### F-Distribution

**`FDistribution(int nu1, int nu2)`**

```java
var f = new FDistribution(5, 20);
System.out.println(f.mean());      // nu2/(nu2-2)
System.out.println(f.cdf(3.0));
System.out.println(f.quantile(0.95)); // upper 5% critical value
```

`p(x)`, `logp(x)`, and `cdf(x)` return `0` / `NEGATIVE_INFINITY` for `x ≤ 0`
(no exception thrown). Random samples use the chi-square ratio method.

---

### Chi-Square Distribution

**`ChiSquareDistribution(int nu)`**

Equivalent to `Gamma(ν/2, 2)`. Used in goodness-of-fit and independence tests.

```java
var chi = new ChiSquareDistribution(10);
System.out.println(chi.mean());       // nu
System.out.println(chi.quantile(0.95)); // upper 5% critical value
```

Random samples use the fast Marsaglia-Tsang `GammaDistribution(ν/2, 2)` path.

---

## 3. Discrete Univariate Distributions

### Bernoulli Distribution

**`BernoulliDistribution(double p)`**

Single Bernoulli trial with success probability `p ∈ [0, 1]`.

```java
var d = new BernoulliDistribution(0.3);
System.out.println(d.p(0)); // q = 0.7
System.out.println(d.p(1)); // p = 0.3
System.out.println(d.mean());     // p
System.out.println(d.variance()); // p*(1-p)

// Fit from boolean array
boolean[] trials = {true, false, true, true, false};
var est = new BernoulliDistribution(trials);
```

`logp(k)` correctly handles the degenerate cases `p=0` and `p=1`,
returning `0.0` or `NEGATIVE_INFINITY` instead of `NaN`.

---

### Binomial Distribution

**`BinomialDistribution(int n, double p)`**

Number of successes in `n` independent Bernoulli trials.

```java
var d = new BinomialDistribution(100, 0.3);
System.out.println(d.p(30));       // P(X=30)
System.out.println(d.cdf(35));     // P(X≤35)
System.out.println(d.quantile(0.975));

// MLE
int[] data = d.randi(1000);
var est = BinomialDistribution.fit(data);
```

`p(k)` is computed as `exp(logp(k))` to avoid the fragile
`floor(0.5 + exp(lchoose))` rounding used in older versions.
`logp()` handles `p=0` and `p=1` without `NaN`.

---

### Poisson Distribution

**`PoissonDistribution(double lambda)`**

Rate parameter `λ ≥ 0`.

```java
var d = new PoissonDistribution(3.5);
System.out.println(d.p(3));    // P(X=3)
System.out.println(d.cdf(5));  // P(X≤5)

double[] data = /* counts */ null;
var est = PoissonDistribution.fit(/*int[] data*/);
```

**Degenerate case** `λ=0`: P(X=0)=1, all others 0. `logp(0)` returns `0.0`
(not `NaN` from `0·log(0)`).

---

### Geometric Distribution

**`GeometricDistribution(double p)`**

Number of failures before the first success (0-indexed, support {0, 1, 2, …}).

```java
var d = new GeometricDistribution(0.3);
System.out.println(d.p(0));   // 0.3  (immediate success)
System.out.println(d.mean()); // (1-p)/p

var est = GeometricDistribution.fit(data);
```

See also **`ShiftedGeometricDistribution`** (1-indexed: number of trials
until first success, support {1, 2, 3, …}).

---

### Negative Binomial Distribution

**`NegativeBinomialDistribution(double r, double p)`**

Number of successes before `r` failures; supports non-integer `r`.

```java
var d = new NegativeBinomialDistribution(3.0, 0.3);
System.out.println(d.mean());      // r*(1-p)/p
System.out.println(d.variance());  // r*(1-p)/p²

// Method-of-moments fitting
int[] data = d.randi(2000);
var est = NegativeBinomialDistribution.fit(data);
```

---

### Hypergeometric Distribution

**`HyperGeometricDistribution(int N, int m, int n)`**

Draws `n` items without replacement from a population of `N` containing `m` defects.

```java
// N=50 items, 10 defective, draw 15
var d = new HyperGeometricDistribution(50, 10, 15);
System.out.println(d.mean());     // n*m/N = 3.0
System.out.println(d.p(3));
System.out.println(d.cdf(5));
```

Uses a fast patchwork-rejection algorithm for large `n·m/(N)` and
an inversion method otherwise.

---

### Empirical Distribution

**`EmpiricalDistribution(double[] prob)`**

A discrete distribution defined by an explicit probability table.
Uses **Walker's alias method** for O(1) random sampling.

```java
// Uniform over {0, 1, 2}
var d = new EmpiricalDistribution(new double[]{1.0/3, 1.0/3, 1.0/3});
System.out.println(d.rand()); // 0, 1, or 2

// Fit from integer data
int[] data = {0, 1, 1, 2, 0, 1};
var est = EmpiricalDistribution.fit(data);

// Fit from data with a custom value set (e.g. non-contiguous {0, 2, 5})
IntSet xs = IntSet.of(data);
var est2 = EmpiricalDistribution.fit(data, xs);
```

---

## 4. Multivariate Distributions

### Multivariate Gaussian

**`MultivariateGaussianDistribution(double[] mean, DenseMatrix cov)`**  
**`MultivariateGaussianDistribution(double[] mean, double[] variance)`** (diagonal)

```java
double[] mu = {1.0, 0.0, -1.0};
double[][] sigma = {
    {0.9, 0.4, 0.7},
    {0.4, 0.5, 0.3},
    {0.7, 0.3, 0.8}
};

var d = new MultivariateGaussianDistribution(mu, DenseMatrix.of(sigma));
double[] x = {1.0, 0.0, -1.0};
System.out.println(d.p(x));    // pdf at x
System.out.println(d.cdf(x));  // cdf (Genz algorithm, error < 0.001)
double[] sample = d.rand();    // multivariate random sample

// MLE fitting
double[][] data = d.rand(500);
var est = MultivariateGaussianDistribution.fit(data);          // full cov
var estDiag = MultivariateGaussianDistribution.fit(data, true); // diagonal cov
```

CDF uses the **Genz (1992)** quasi-Monte Carlo algorithm with error < 0.001
in 99.9% of cases.

---

## 5. Mixture Models

Mixture models represent a distribution as a weighted sum of component
distributions: P(x) = Σ wᵢ · P(x | component_i).

### Finite Mixture (Continuous)

**`Mixture`** — base class for continuous mixtures.

```java
// Manual construction
var c1 = new Mixture.Component(0.5, new GaussianDistribution(0.0, 1.0));
var c2 = new Mixture.Component(0.5, new GaussianDistribution(4.0, 1.0));
var mix = new Mixture(c1, c2);

System.out.println(mix.mean());     // 2.0  (weighted mean)
System.out.println(mix.variance()); // 5.0  (law of total variance)
System.out.println(mix.p(2.0));     // pdf at 2.0
System.out.println(mix.logp(2.0));  // log-pdf (log-sum-exp, numerically stable)
```

> **Important**: `variance()` correctly applies the **law of total variance**:
> `Var(X) = E[Var(X|Z)] + Var(E[X|Z]) = Σwᵢσᵢ² + Σwᵢμᵢ² − μ²`

### Gaussian Mixture Model (EM)

**`GaussianMixture.fit(double[] data)`** — automatically determines the number
of components using BIC.

```java
MathEx.setSeed(19650218);
double[] data = /* observed data */;
GaussianMixture mixture = GaussianMixture.fit(data);
System.out.println(mixture);           // e.g. "0.50 x N(0.0, 1.0) + 0.50 x N(4.0, 1.0)"
System.out.println(mixture.size());    // number of components
System.out.println(mixture.bic);       // BIC score
```

### Exponential Family Mixture (EM)

Fit a mixture of **any** exponential-family distributions (Gaussian,
Exponential, Gamma, etc.) using `ExponentialFamilyMixture.fit()`:

```java
MathEx.setSeed(19650218);
// Initial component configuration
var components = new Mixture.Component[]{
    new Mixture.Component(0.25, new GaussianDistribution(0.0, 1.0)),
    new Mixture.Component(0.25, new ExponentialDistribution(1.0)),
    new Mixture.Component(0.50, new GammaDistribution(1.0, 2.0))
};

ExponentialFamilyMixture result = ExponentialFamilyMixture.fit(data, components);
System.out.printf("Log-likelihood: %.4f%n", result.L);
System.out.printf("BIC: %.4f%n", result.bic);
for (var c : result.components) {
    System.out.printf("  %.2f x %s%n", c.priori(), c.distribution());
}
```

The EM algorithm uses the **log-sum-exp trick** in both the E-step and the
log-likelihood evaluation to prevent numerical underflow.

### Regularized EM

Pass a regularization parameter `γ ∈ (0, 0.2]` to encourage larger
components to "absorb" probability mass from tiny components:

```java
ExponentialFamilyMixture result =
    ExponentialFamilyMixture.fit(data, components, 0.1, 500, 1E-4);
```

### Discrete Mixture (EM)

For integer-valued data, use `DiscreteExponentialFamilyMixture`:

```java
var d1 = new DiscreteExponentialFamilyMixture.Component(0.5, new PoissonDistribution(2.0));
var d2 = new DiscreteExponentialFamilyMixture.Component(0.5, new PoissonDistribution(8.0));
var result = DiscreteExponentialFamilyMixture.fit(intData, d1, d2);
```

### Multivariate Gaussian Mixture

```java
MathEx.setSeed(42);
var gm = MultivariateGaussianMixture.fit(3, data); // 3 components
System.out.println(gm.size());   // number of fitted components
double[] x = data[0];
System.out.println(gm.p(x));     // pdf at x
int comp = gm.map(x);            // most probable component index
```

### Posteriori Probability

All mixture classes expose `posteriori(x)` to compute the soft assignments:

```java
GaussianMixture mix = GaussianMixture.fit(data);
double[] prob = mix.posteriori(2.5); // P(component_i | x=2.5)
int label = mix.map(2.5);            // hard assignment (most probable)
```

---

## 6. Non-Parametric Density Estimation

### Kernel Density Estimation

**`KernelDensity(double[] data)`**  
**`KernelDensity(double[] data, double bandwidth)`**

Uses a **Gaussian kernel** with Scott's rule for bandwidth selection
(h = 1.06 · σ · n^(−1/5)).

```java
double[] data = /* observed values */;
var kde = new KernelDensity(data);

System.out.println("bandwidth = " + kde.bandwidth()); // Scott's rule
System.out.println("mean  = " + kde.mean());
System.out.println("var   = " + kde.variance());

// Density evaluation
System.out.println(kde.p(3.5));     // pdf estimate
System.out.println(kde.logp(3.5));  // log-pdf (log-sum-exp)
System.out.println(kde.cdf(3.5));   // cdf estimate

// Inverse CDF (bisection on cdf)
System.out.println(kde.quantile(0.5)); // median

// Smoothed bootstrap sampling
double sample = kde.rand(); // data point + Gaussian noise

// Log-likelihood
System.out.println(kde.logLikelihood(testData));
```

The `p()` method restricts the kernel sum to the active window
`[x − 5h, x + 5h]` using binary search for O(h/σ · n) evaluation.

---

## 7. Parameter Estimation (MLE / MOM)

All parametric distributions provide a static `fit()` method:

| Distribution | Method | Link |
|---|---|---|
| `GaussianDistribution` | MLE | `GaussianDistribution.fit(double[])` |
| `LogNormalDistribution` | MLE | `LogNormalDistribution.fit(double[])` |
| `GammaDistribution` | MLE (Newton-Raphson) | `GammaDistribution.fit(double[])` |
| `ExponentialDistribution` | MLE | `ExponentialDistribution.fit(double[])` |
| `BetaDistribution` | MLE (Newton-Raphson) | `BetaDistribution.fit(double[])` |
| `WeibullDistribution` | MLE (Newton-Raphson) | `WeibullDistribution.fit(double[])` |
| `BinomialDistribution` | MLE | `BinomialDistribution.fit(int[])` |
| `PoissonDistribution` | MLE | `PoissonDistribution.fit(int[])` |
| `GeometricDistribution` | MLE | `GeometricDistribution.fit(int[])` |
| `ShiftedGeometricDistribution` | MLE | `ShiftedGeometricDistribution.fit(int[])` |
| `NegativeBinomialDistribution` | MOM | `NegativeBinomialDistribution.fit(int[])` |
| `EmpiricalDistribution` | frequency count | `EmpiricalDistribution.fit(int[])` |
| `MultivariateGaussianDistribution` | MLE | `MultivariateGaussianDistribution.fit(double[][])` |
| `GaussianMixture` | EM (auto-select k) | `GaussianMixture.fit(double[])` |
| `ExponentialFamilyMixture` | EM | `ExponentialFamilyMixture.fit(double[], Component...)` |
| `MultivariateGaussianMixture` | EM | `MultivariateGaussianMixture.fit(int k, double[][])` |

---

## 8. Random Variate Generation

SMILE uses the following algorithms for each distribution:

| Distribution | Algorithm |
|---|---|
| `GaussianDistribution` | Box-Muller (polar form) |
| `GammaDistribution` | Marsaglia-Tsang (2000) for any shape k > 0 |
| `ChiSquareDistribution` | `GammaDistribution(ν/2, 2)` |
| `TDistribution` | Z / √(χ²(ν)/ν) |
| `FDistribution` | (χ²(d₁)/d₁) / (χ²(d₂)/d₂) |
| `BetaDistribution` | Inverse transform sampling |
| `ExponentialDistribution` | `-log(U)/λ` (inverse transform) |
| `LogNormalDistribution` | `exp(GaussianDistribution.rand())` |
| `WeibullDistribution` | Inverse transform: `λ·(−log U)^(1/k)` |
| `BinomialDistribution` | Chop-down / patchwork-rejection |
| `PoissonDistribution` | Inversion / patchwork-rejection |
| `HyperGeometricDistribution` | Patchwork-rejection (HPRS) or inversion |
| `GeometricDistribution` | Exponential flooring |
| `EmpiricalDistribution` | Walker's alias method O(1) |

All generators respect the seed set via `MathEx.setSeed(long seed)`. The
Gaussian singleton's Box-Muller cache is automatically invalidated on
re-seeding to ensure full reproducibility.

---

## 9. Numerical Notes

### Log-Sum-Exp

`logp(x)` throughout the package is computed via **direct formulas** rather
than `log(p(x))`. In mixture models, the log-density is evaluated via the
numerically stable log-sum-exp identity:

```
log Σᵢ wᵢ·pᵢ(x)  =  max_i(log wᵢ + log pᵢ(x))
                     + log Σᵢ exp(log wᵢ + log pᵢ(x) − max)
```

### Boundary Cases

| Distribution | x=0 behaviour |
|---|---|
| `GammaDistribution(k>1, θ)` | `p(0)=0`, `logp(0)=−∞` |
| `GammaDistribution(k=1, θ)` | `p(0)=1/θ`, `logp(0)=−log θ` |
| `GammaDistribution(k<1, θ)` | `p(0)=+∞`, `logp(0)=+∞` |
| `PoissonDistribution(λ=0)` | `p(0)=1`, `logp(0)=0`; no NaN |
| `BinomialDistribution(n,p=0)` | `p(0)=1`, `logp(0)=0`; no NaN |
| `FDistribution`, `BetaDistribution` | `p(0)=0`; no exception |

### Law of Total Variance

`Mixture.variance()` and `MultivariateMixture.cov()` implement the
**law of total variance** / **Bienaymé formula**:

```
Var(X) = E[Var(X|Z)] + Var(E[X|Z])
       = Σᵢ wᵢ σᵢ²  +  Σᵢ wᵢ μᵢ²  −  μ²
```

---

## 10. Quick Reference

```java
import smile.stat.distribution.*;

// --- Continuous ---
new GaussianDistribution(mu, sigma);
new LogNormalDistribution(mu, sigma);
new GammaDistribution(k, theta);
new ExponentialDistribution(lambda);
new BetaDistribution(alpha, beta);
new WeibullDistribution(k, lambda);
new LogisticDistribution(mu, scale);
new TDistribution(nu);
new FDistribution(nu1, nu2);
new ChiSquareDistribution(nu);

// --- Discrete ---
new BernoulliDistribution(p);
new BinomialDistribution(n, p);
new PoissonDistribution(lambda);
new GeometricDistribution(p);          // 0-indexed (failures before success)
new ShiftedGeometricDistribution(p);   // 1-indexed (trials until success)
new NegativeBinomialDistribution(r, p);
new HyperGeometricDistribution(N, m, n);
new EmpiricalDistribution(probArray);

// --- Multivariate ---
new MultivariateGaussianDistribution(mean, covMatrix);

// --- Mixture ---
GaussianMixture.fit(data);
ExponentialFamilyMixture.fit(data, components);
MultivariateGaussianMixture.fit(k, data);

// --- Non-parametric ---
new KernelDensity(data);
new KernelDensity(data, bandwidth);

// --- Common operations ---
dist.p(x);          // pdf / pmf
dist.logp(x);       // log pdf / pmf
dist.cdf(x);        // CDF
dist.quantile(p);   // inverse CDF
dist.rand();        // one sample
dist.rand(n);       // n samples
dist.mean();
dist.variance();
dist.entropy();
dist.logLikelihood(data);
```


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

