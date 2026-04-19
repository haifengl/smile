# SMILE — Time Series

The package `smile.timeseries` provides the following time-series utilities and models:

- `TimeSeries`: differencing and correlation helpers (`diff`, `cov`, `acf`, `pacf`)
- `AR`: autoregressive model fitting and forecasting (`AR(p)`)
- `ARMA`: autoregressive moving-average model fitting and forecasting (`ARMA(p, q)`)
- `BoxTest`: portmanteau tests (`Box-Pierce`, `Ljung-Box`)

## Table of Contents

- [1) Quick start](#1-quick-start)
- [2) `TimeSeries` utilities](#2-timeseries-utilities)
- [3) Autoregressive model (`AR`)](#3-autoregressive-model-ar)
- [4) ARMA model (`ARMA`)](#4-arma-model-arma)
- [5) Residual diagnostics with `BoxTest`](#5-residual-diagnostics-with-boxtest)
- [6) Recommended workflow](#6-recommended-workflow)
- [7) Practical tips](#7-practical-tips)

## 1) Quick start

```java
import smile.timeseries.AR;
import smile.timeseries.ARMA;
import smile.timeseries.BoxTest;
import smile.timeseries.TimeSeries;

// Raw time series
double[] x = ...;

// Make a first difference series
double[] dx = TimeSeries.diff(x, 1);

// Fit AR(6) by OLS
AR ar = AR.ols(dx, 6);

// Fit ARMA(6, 3)
ARMA arma = ARMA.fit(dx, 6, 3);

// Residual white-noise diagnostics
BoxTest lb = BoxTest.ljung(ar.residuals(), 10);
System.out.println(lb);

// Forecasts
double oneStep = ar.forecast();
double[] next5 = ar.forecast(5);
```

## 2) `TimeSeries` utilities

`TimeSeries` provides static methods for common preprocessing and correlation analysis.

### `diff`

- `TimeSeries.diff(x, lag)`: first differencing at lag `lag`
- `TimeSeries.diff(x, lag, differences)`: repeated differencing; returns each differenced stage

Examples:

```java
double[] d1 = TimeSeries.diff(x, 1);          // x[t] - x[t-1]
double[] seasonal = TimeSeries.diff(x, 12);   // seasonal differencing

double[][] steps = TimeSeries.diff(x, 1, 2);  // first and second differences
double[] second = steps[1];
```

Validation behavior:

- `lag` must be `> 0`
- `differences` must be `> 0`
- `lag * differences` must be `< x.length`
- otherwise an `IllegalArgumentException` is thrown

### `cov`, `acf`, `pacf`

- `cov(x, lag)`: auto-covariance at lag
- `acf(x, lag)`: auto-correlation at lag (`acf(x, 0) == 1`)
- `pacf(x, lag)`: partial auto-correlation at lag

Notes:

- Negative lag is accepted and converted to absolute value.
- Absolute lag must be `< x.length`, or `IllegalArgumentException` is thrown.

## 3) Autoregressive model (`AR`)

### Fit models

- `AR.fit(x, p)`: Yule-Walker fit
- `AR.ols(x, p)`: OLS fit with standard-error estimation
- `AR.ols(x, p, stderr)`: OLS with optional t-test statistics

```java
AR yw = AR.fit(dx, 6);
AR ols = AR.ols(dx, 6);
```

### Inspect fitted model

```java
int order = ols.p();
double[] coeff = ols.ar();
double intercept = ols.intercept();
double[] fitted = ols.fittedValues();
double[] residuals = ols.residuals();
double variance = ols.variance();
```

If you call `AR.ols(..., true)` (or `AR.ols(...)`), `ttest()` returns coefficient summary columns:

1. estimate
2. standard error
3. t statistic
4. p-value

### Forecasting

- `forecast()`: one-step-ahead forecast
- `forecast(l)`: `l`-step ahead forecast

```java
double f1 = ols.forecast();
double[] f10 = ols.forecast(10);
```

Validation behavior:

- `l` must be `> 0`, otherwise `IllegalArgumentException`

## 4) ARMA model (`ARMA`)

### Fit model

`ARMA.fit(x, p, q)` uses a Hannan-Rissanen style procedure.

```java
ARMA model = ARMA.fit(dx, 6, 3);
```

Validation behavior:

- `p > 0` and `p < x.length`
- `q > 0` and `q < x.length`
- input length must be large enough for the fitting procedure; otherwise `IllegalArgumentException`

### Inspect fitted model

```java
int p = model.p();
int q = model.q();
double[] ar = model.ar();
double[] ma = model.ma();
double b = model.intercept();
double[] residuals = model.residuals();
double[] fitted = model.fittedValues();
```

### Forecasting

- `forecast()`: one-step-ahead forecast
- `forecast(l)`: `l`-step ahead forecast

```java
double next = model.forecast();
double[] next3 = model.forecast(3);
```

Validation behavior:

- `l` must be `> 0`, otherwise `IllegalArgumentException`

## 5) Residual diagnostics with `BoxTest`

Use portmanteau tests to check whether residual autocorrelation remains.

- `BoxTest.pierce(x, lag)`
- `BoxTest.ljung(x, lag)`

```java
BoxTest bp = BoxTest.pierce(model.residuals(), 10);
BoxTest lb = BoxTest.ljung(model.residuals(), 10);

System.out.printf("%s p-value=%.6f%n", bp.type, bp.pvalue);
System.out.printf("%s p-value=%.6f%n", lb.type, lb.pvalue);
```

Interpretation (rule of thumb):

- a small p-value suggests remaining autocorrelation
- a larger p-value suggests residuals are closer to white noise

Validation behavior:

- `lag` must be `> 0` and `< x.length`, otherwise `IllegalArgumentException`

## 6) Recommended workflow

1. Start from a raw series `x`.
2. Apply log/seasonal transformation as needed (outside this package).
3. Difference with `TimeSeries.diff` to improve stationarity.
4. Use `acf`/`pacf` to guide order selection.
5. Fit candidate models (`AR`, `ARMA`).
6. Validate residuals using `BoxTest`.
7. Compare candidate models and forecast performance on holdout data.

## 7) Practical tips

- Keep `p` and `q` modest unless you have strong evidence for higher orders.
- Use holdout/backtesting for model selection, not in-sample fit alone.
- If forecasts or diagnostics look unstable, re-check stationarity and outliers first.
- Expect `R2`/adjusted `R2` in time-series models to be less informative than residual diagnostics plus out-of-sample error.


---

*SMILE — Copyright © 2010-2026 Haifeng Li. GNU GPL licensed.*
