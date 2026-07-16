# SMILE — Time Series Analysis

The `smile.timeseries` package provides tools for modeling and forecasting
univariate time series. It covers four areas:

| Class / Interface | Purpose |
|---|---|
| `TimeSeries` | Static utilities: differencing, autocovariance, ACF, PACF |
| `BoxTest` | Portmanteau autocorrelation tests (Box–Pierce, Ljung–Box) |
| `AR` | Autoregressive model AR(*p*) — Yule–Walker and OLS fitting |
| `ARMA` | Autoregressive moving-average model ARMA(*p*, *q*) |

---

## 1. Foundations

### 1.1 Stationarity

All models in this package assume **weak stationarity**: the mean and the
autocovariance between any two lags are time-invariant. For many real-world
series (trending prices, seasonal data) stationarity must be achieved by
pre-processing before fitting.

Two common transforms are:

* **Differencing** — removes a polynomial trend.
* **Log transform** — stabilizes variance (e.g., asset prices).

A log-return series `log(p_t) − log(p_{t-1})` typically satisfies stationarity
and is a natural starting point for financial data.

### 1.2 Model taxonomy

```
AR(p)   : x_t = b + φ₁x_{t-1} + … + φₚx_{t-p} + ε_t
MA(q)   : x_t = b + ε_t + θ₁ε_{t-1} + … + θ_qε_{t-q}
ARMA(p,q): combination of AR and MA
```

`ε_t` is white noise with variance σ². The intercept `b` captures a non-zero
series mean.

---

## 2. `TimeSeries` — Utility Functions

`TimeSeries` is a Java interface with only `static` methods; you never
instantiate it.

### 2.1 Differencing

```java
// First difference (lag=1)
double[] ret = TimeSeries.diff(logPrice, 1);

// Seasonal difference (lag=12 for monthly data)
double[] season = TimeSeries.diff(monthly, 12);

// Multi-order differencing — returns every intermediate series
double[][] d2 = TimeSeries.diff(x, 1, 2);
// d2[0] = first-difference pass
// d2[1] = second-difference pass (difference of the difference)
```

**Signature overview**

```java
static double[]   diff(double[] x, int lag)
static double[][] diff(double[] x, int lag, int differences)
```

* `lag` must be ≥ 1.
* `differences` must be ≥ 1.
* `lag * differences` must be strictly less than `x.length`.

Each differencing pass shrinks the series by `lag` elements.

**Example — second difference of a geometric series `{1, 2, 4, 8, 16}`**

```java
// lag=1, differences=2
double[][] d = TimeSeries.diff(new double[]{1,2,4,8,16}, 1, 2);
// d[0] = {1, 2, 4, 8}   (x[i+1]-x[i])
// d[1] = {1, 2, 4}       (d[0][i+1]-d[0][i])
```

### 2.2 Autocovariance

```java
static double cov(double[] x, int lag)
```

Returns the **unnormalized** sample autocovariance at the given lag:

```
cov(lag) = Σ_{i=lag}^{T-1} (x[i] - μ)(x[i-lag] - μ)
```

where `μ = mean(x)`. This is not divided by `T`, so it represents the raw sum
of cross-products. Useful when you need to build your own ACF normalization.

Negative lags are silently converted to their absolute value.

### 2.3 Autocorrelation Function (ACF)

```java
static double acf(double[] x, int lag)
```

Returns the sample autocorrelation at the given lag:

```
acf(0) = 1
acf(k) = cov(k) / cov(0)
```

```java
// ACF of the log-return series at lag 1
double r1 = TimeSeries.acf(logPriceDiff, 1);

// Lag 0 is always 1.0
assertEquals(1.0, TimeSeries.acf(x, 0), 1e-10);
```

### 2.4 Partial Autocorrelation Function (PACF)

```java
static double pacf(double[] x, int lag)
```

The PACF at lag *k* measures the direct correlation between `x_t` and
`x_{t-k}` after removing the contributions of all intermediate lags. It is
computed via the Yule–Walker equations on the ACF vector.

```java
double phi = TimeSeries.pacf(logPriceDiff, 3);
```

`pacf(0) = 1` and `pacf(1) = acf(1)`.

**Rule of thumb for order selection**

| Pattern | Suggested model |
|---|---|
| ACF cuts off at lag *q*; PACF tails off | MA(*q*) |
| PACF cuts off at lag *p*; ACF tails off | AR(*p*) |
| Both tail off | ARMA(*p*, *q*) |

---

## 3. `BoxTest` — Portmanteau Tests

After fitting a model, you want to check whether the residuals look like white
noise. The Box–Pierce and Ljung–Box tests formally test the null hypothesis
that the first `lag` autocorrelations are jointly zero.

### 3.1 Box–Pierce

```java
BoxTest result = BoxTest.pierce(residuals, lag);
```

Statistic: `Q = n * Σ_{l=1}^{lag} r_l²`

### 3.2 Ljung–Box (preferred for small samples)

```java
BoxTest result = BoxTest.ljung(residuals, lag);
```

Statistic: `Q = n(n+2) * Σ_{l=1}^{lag} r_l² / (n-l)`

The Ljung–Box correction reduces the downward bias of the Box–Pierce statistic
in small samples and is the default choice in most software.

### 3.3 Reading results

```java
BoxTest box = BoxTest.ljung(residuals, 20);

box.type      // BoxTest.Type.Ljung_Box
box.df        // degrees of freedom = lag
box.q         // test statistic
box.pvalue    // chi-square p-value

System.out.println(box);
// Ljung-Box test
// Q* = 9.0415, df = 5, p-value = 0.1074
// ...
```

A **large p-value** (e.g., > 0.05) means you fail to reject the white-noise
null — the residuals are consistent with white noise, indicating a good model
fit.

### 3.4 Validation

```java
// lag must be in [1, x.length-1]
assertThrows(IllegalArgumentException.class, () -> BoxTest.ljung(x, 0));
assertThrows(IllegalArgumentException.class, () -> BoxTest.ljung(x, x.length));
```

---

## 4. `AR` — Autoregressive Model

The AR(*p*) model is:

```
x_t = b + φ₁x_{t-1} + φ₂x_{t-2} + … + φₚx_{t-p} + ε_t
```

### 4.1 Fitting methods

Two estimators are available, chosen by the factory method you call:

| Method | Factory | When to use |
|---|---|---|
| Yule–Walker | `AR.fit(x, p)` | Guaranteed stationary fit; fast; uses only ACF |
| OLS / Least Squares | `AR.ols(x, p)` | More accurate coefficients; may be non-stationary |

```java
AR ywModel  = AR.fit(logPriceDiff, 6);   // Yule-Walker
AR olsModel = AR.ols(logPriceDiff, 6);   // OLS with standard errors
AR olsFast  = AR.ols(logPriceDiff, 6, false); // OLS, skip SE computation
```

The `stderr` flag controls whether standard errors and t-tests are computed.
Passing `false` is faster when you only need point estimates.

### 4.2 Parameter access

```java
int    p    = model.p();            // AR order
double b    = model.intercept();    // intercept
double[] ar = model.ar();           // φ₁, φ₂, …, φₚ

double[] fit  = model.fittedValues();  // length = x.length - p
double[] res  = model.residuals();     // length = x.length - p
double rss    = model.RSS();           // residual sum of squares
double var    = model.variance();      // RSS / df
int df        = model.df();            // x.length - p
double r2     = model.R2();
double adjR2  = model.adjustedR2();
```

### 4.3 Significance tests (OLS only)

When `stderr = true` (the default for `AR.ols`), the model carries a `p × 4`
matrix returned by `ttest()`:

| Column | Content |
|---|---|
| 0 | Coefficient estimate |
| 1 | Standard error |
| 2 | t-statistic |
| 3 | p-value |

```java
double[][] tt = model.ttest();
for (int i = 0; i < model.p(); i++) {
    System.out.printf("φ_%d  estimate=%.4f  SE=%.4f  t=%.3f  p=%.4f%n",
        i+1, tt[i][0], tt[i][1], tt[i][2], tt[i][3]);
}
```

`ttest()` returns `null` when the model was fitted with `stderr = false` or
via the Yule–Walker method.

### 4.4 Forecasting

```java
// One-step-ahead forecast
double next = model.forecast();

// l-step-ahead forecast (iterated)
double[] horizon = model.forecast(3);
// horizon[0] = one step ahead
// horizon[1] = two steps ahead
// horizon[2] = three steps ahead
```

Multi-step forecasts are produced by iterating the AR recursion: each
predicted value is fed back as a lagged input for subsequent steps. As the
horizon grows, the forecast converges toward the long-run mean.

```java
// Forecast horizon must be positive
assertThrows(IllegalArgumentException.class, () -> model.forecast(0));
```

### 4.5 Model summary

```java
System.out.println(model);
```

Prints residual quantiles, a coefficient table (with t-stats if available),
the residual variance, R², and adjusted R².

### 4.6 Full example — AR(6) on Bitcoin log returns

```java
var bitcoin = new BitcoinPrice();
double[] logPrice    = bitcoin.logPrice();
double[] logReturn   = TimeSeries.diff(logPrice, 1);  // make stationary

// Examine PACF to choose order
for (int k = 1; k <= 10; k++) {
    System.out.printf("PACF(%2d) = %+.4f%n", k, TimeSeries.pacf(logReturn, k));
}

// Fit AR(6) with OLS
AR model = AR.ols(logReturn, 6);
System.out.println(model);

// Forecast next 5 trading days
double[] forecast = model.forecast(5);
System.out.println(Arrays.toString(forecast));

// Diagnostic: test residuals for autocorrelation
BoxTest lbTest = BoxTest.ljung(model.residuals(), 20);
System.out.println(lbTest);
```

---

## 5. `ARMA` — Autoregressive Moving-Average Model

The ARMA(*p*, *q*) model is:

```
x_t = b + φ₁x_{t-1} + … + φₚx_{t-p}
        + ε_t + θ₁ε_{t-1} + … + θ_qε_{t-q}
```

The MA terms allow the model to capture autocorrelation patterns that pure AR
models require a very high order to approximate.

### 5.1 Fitting — Hannan–Rissanen algorithm

```java
ARMA model = ARMA.fit(x, p, q);
```

The fitting procedure is a two-stage least-squares method (Hannan–Rissanen):

1. Fit a long AR(*m*) where `m = p + q + 20` to obtain proxy residuals.
2. Regress `x_t` on `p` AR lags and `q` MA lags (from the stage-1 residuals)
   plus an intercept using SVD-based least squares.

Standard errors for all `p + q` AR and MA coefficients are provided
automatically.

**Minimum series length:** the series must satisfy
`x.length > p + q + 20 + max(p, q)`, so at least ~50 observations are needed
for even modest orders.

### 5.2 Parameter access

```java
int p        = model.p();           // AR order
int q        = model.q();           // MA order
double b     = model.intercept();   // intercept
double[] ar  = model.ar();          // φ₁, …, φₚ
double[] ma  = model.ma();          // θ₁, …, θ_q

double[] fit  = model.fittedValues();
double[] res  = model.residuals();
double rss    = model.RSS();
double var    = model.variance();
int df        = model.df();         // n = x.length - (p+q+20) - max(p,q)
double r2     = model.R2();
double adjR2  = model.adjustedR2();
```

### 5.3 Significance tests

`ttest()` returns a `(p+q) × 4` matrix in the same layout as `AR`:
rows 0..p-1 are the AR coefficients; rows p..p+q-1 are the MA coefficients.

```java
double[][] tt = model.ttest();
for (int i = 0; i < model.p() + model.q(); i++) {
    String label = i < model.p()
        ? "AR[" + (i+1) + "]"
        : "MA[" + (i - model.p() + 1) + "]";
    System.out.printf("%-8s  est=%.4f  SE=%.4f  t=%.3f  p=%.4f%n",
        label, tt[i][0], tt[i][1], tt[i][2], tt[i][3]);
}
```

### 5.4 Forecasting

```java
// One-step-ahead (uses last p observed values + last q residuals)
double next = model.forecast();

// l-step-ahead
double[] h = model.forecast(5);
```

Future residuals beyond the training window are assumed to be zero (the
expected value of white noise), so the forecast converges toward the long-run
mean as the horizon increases.

```java
// First element of multi-step forecast equals the single-step forecast
assertEquals(model.forecast(), model.forecast(5)[0], 1e-12);

// Horizon must be positive
assertThrows(IllegalArgumentException.class, () -> model.forecast(0));
```

### 5.5 Full example — ARMA(6, 3) on Bitcoin log returns

```java
var bitcoin = new BitcoinPrice();
double[] logReturn = TimeSeries.diff(bitcoin.logPrice(), 1);

ARMA model = ARMA.fit(logReturn, 6, 3);
System.out.println(model);

// One-step forecast
double nextStep = model.forecast();

// Three-step forecast
double[] h3 = model.forecast(3);

// Diagnostic
BoxTest lb = BoxTest.ljung(model.residuals(), 20);
if (lb.pvalue < 0.05) {
    System.out.println("Residuals still autocorrelated — consider higher order");
}
```

---

## 6. Workflow: Fitting an ARMA Model

```
                       Raw series
                           │
                    Stationarity check
                    (visual ACF plot,
                     unit-root tests)
                           │
                  Non-stationary ──► Apply diff() / log()
                           │
                    Stationary series
                           │
               ┌──────────┴──────────┐
         ACF/PACF analysis         Information criteria
         (cut-off patterns)        (AIC, BIC — external)
               └──────────┬──────────┘
                           │
                   Choose p, q
                           │
                 ARMA.fit(x, p, q)  or  AR.ols(x, p)
                           │
                  Inspect coefficients
                  and t-statistics
                           │
                 BoxTest.ljung(residuals, 20)
                  p-value > 0.05 ? ──► ✓ Accept model
                      │
                  p-value ≤ 0.05 ──► Increase p or q
                           │
                    model.forecast(l)
```

### 6.1 Choosing between AR and ARMA

Prefer **AR** when:
* The PACF cuts off sharply at lag *p* and the ACF tails off.
* You need a fast, non-iterative fit (both methods are closed form).
* You want Yule–Walker to guarantee a stationary fit.

Prefer **ARMA** when:
* Both ACF and PACF tail off (MA component is present).
* A parsimonious model is needed: ARMA(1,1) often beats AR(5) in parsimony
  and out-of-sample accuracy.

### 6.2 Choosing between Yule–Walker and OLS for AR

| Criterion | Yule–Walker | OLS |
|---|---|---|
| Stationarity of fitted model | Guaranteed | Not guaranteed |
| Coefficient accuracy | Slightly biased | Unbiased (consistent) |
| Standard errors available | No | Yes |
| Speed | Comparable | Comparable |

In practice, **OLS is recommended** unless you explicitly need the stationarity
guarantee (e.g., when using the fitted model as a starting point for a
stationary process simulator).

---

## 7. Serialization

Both `AR` and `ARMA` implement `java.io.Serializable` (`serialVersionUID = 2L`).
You can persist and restore models with standard Java serialization or any
compatible framework.

```java
// Save
try (var out = new ObjectOutputStream(new FileOutputStream("ar6.ser"))) {
    out.writeObject(model);
}

// Load
AR loaded;
try (var in = new ObjectInputStream(new FileInputStream("ar6.ser"))) {
    loaded = (AR) in.readObject();
}
double nextForecast = loaded.forecast();
```

---

## 8. Quick-reference API

### `TimeSeries`

```java
static double[]   diff(double[] x, int lag)
static double[][] diff(double[] x, int lag, int differences)
static double     cov(double[] x, int lag)
static double     acf(double[] x, int lag)
static double     pacf(double[] x, int lag)
```

### `BoxTest`

```java
static BoxTest pierce(double[] x, int lag)  // Box-Pierce test
static BoxTest ljung(double[] x, int lag)   // Ljung-Box test

Type   type     // Box_Pierce or Ljung_Box
int    df       // degrees of freedom (= lag)
double q        // test statistic
double pvalue   // chi-square p-value
```

### `AR`

```java
static AR fit(double[] x, int p)                      // Yule-Walker
static AR ols(double[] x, int p)                      // OLS with SE
static AR ols(double[] x, int p, boolean stderr)      // OLS optional SE

int       p()
double    intercept()
double[]  ar()
double[]  fittedValues()
double[]  residuals()
double    RSS()
double    variance()
int       df()
double    R2()
double    adjustedR2()
double[][] ttest()      // null if stderr=false or Yule-Walker
double    forecast()
double[]  forecast(int l)
```

### `ARMA`

```java
static ARMA fit(double[] x, int p, int q)

int       p()
int       q()
double    intercept()
double[]  ar()
double[]  ma()
double[]  fittedValues()
double[]  residuals()
double    RSS()
double    variance()
int       df()
double    R2()
double    adjustedR2()
double[][] ttest()      // (p+q) × 4; rows 0..p-1 = AR, rows p..p+q-1 = MA
double    forecast()
double[]  forecast(int l)
```

---

## 9. Common Pitfalls

**Fitting on a non-stationary series**  
Applying `AR.ols` or `ARMA.fit` directly to a trending or seasonal series
produces meaningless coefficients. Always verify stationarity (e.g., with an
augmented Dickey–Fuller test) or apply `diff()` / log-transform first.

**Choosing `p` or `q` too large**  
Overfitting produces near-unit-root AR coefficients and inflated MA terms.
Start small (order 1–3), increase only if the Ljung–Box test rejects
white-noise residuals.

**Minimum series length for ARMA**  
`ARMA.fit(x, p, q)` requires `x.length > p + q + 20 + max(p, q)`. For
ARMA(5,5) this means at least 51 observations. An `IllegalArgumentException`
is thrown otherwise.

**`AR.ols` does not guarantee stationarity**  
If the fitted AR polynomial has roots near or outside the unit circle, forecasts
may diverge. Use `AR.fit` (Yule–Walker) or check the characteristic roots
externally when stationarity is essential.

**`forecast(int l)` and the MA memory horizon**  
For ARMA, residuals beyond the training window are treated as zero. This means
the MA contribution to multi-step forecasts decays to zero after *q* steps,
and the forecast converges toward the long-run mean driven entirely by the AR
part. This is expected behaviour, not a bug.

**`ttest()` covers AR coefficients only in `AR` (not the intercept)**  
The `ttest()` matrix for `AR` has `p` rows (one per AR lag). The intercept
`b` is available via `intercept()` but does not have an associated row in the
t-test table.

---

*SMILE — Copyright © 2010-2026 Haifeng Li. GNU GPL licensed.*

