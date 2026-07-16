# SMILE — Wavelet User Guide

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [The Wavelet Family Hierarchy](#the-wavelet-family-hierarchy)
   - [Haar Wavelet](#haar-wavelet)
   - [D4 Wavelet](#d4-wavelet)
   - [Daubechies Wavelets](#daubechies-wavelets)
   - [Coiflet Wavelets](#coiflet-wavelets)
   - [Symlet Wavelets](#symlet-wavelets)
   - [Best Localized Wavelets](#best-localized-wavelets)
   - [Custom Wavelets](#custom-wavelets)
4. [Discrete Wavelet Transform](#discrete-wavelet-transform)
   - [Forward Transform](#forward-transform)
   - [Inverse Transform](#inverse-transform)
   - [Input Requirements](#input-requirements)
   - [Coefficient Layout](#coefficient-layout)
5. [Wavelet Shrinkage — Signal Denoising](#wavelet-shrinkage--signal-denoising)
   - [Universal Threshold](#universal-threshold)
   - [Hard vs Soft Thresholding](#hard-vs-soft-thresholding)
   - [1-D Denoising](#1-d-denoising)
   - [2-D Denoising](#2-d-denoising)
6. [Choosing a Wavelet](#choosing-a-wavelet)
7. [Working with Non-Power-of-2 Lengths](#working-with-non-power-of-2-lengths)
8. [Mathematical Background](#mathematical-background)
9. [Complete Examples](#complete-examples)

---

## Overview

The `smile.wavelet` package provides a fast, in-place **Discrete Wavelet Transform (DWT)** and its inverse for 1-D and 2-D signals, together with **wavelet shrinkage** for automatic signal denoising.

Key properties of the implementation:

| Property | Value |
|---|---|
| Transform type | Orthogonal (energy-preserving) |
| Algorithm | Mallat pyramid algorithm |
| In-place | Yes — the signal array is modified in-place |
| Complexity | O(N) per level, O(N) total |
| Signal length | Power of 2 |

---

## Quick Start

```java
import smile.wavelet.*;

// 1. Pick a wavelet
Wavelet wavelet = new DaubechiesWavelet(8);

// 2. Forward transform (in-place)
double[] signal = { 1.0, 2.0, 3.0, 4.0, 3.0, 2.0, 1.0, 0.0 };
wavelet.transform(signal);
// signal[] now holds the wavelet coefficients

// 3. Inverse transform (in-place) — recover the original signal
wavelet.inverse(signal);

// 4. Denoise a noisy signal in one line
double[] noisy = { /* your data */ };
WaveletShrinkage.denoise(noisy, new HaarWavelet());
```

---

## The Wavelet Family Hierarchy

All wavelets extend the base `Wavelet` class:

```
Wavelet
├── HaarWavelet
├── D4Wavelet
├── DaubechiesWavelet  (n = 4, 6, 8, 10, 12, 14, 16, 18, 20)
├── CoifletWavelet     (n = 6, 12, 18, 24, 30)
├── SymletWavelet      (n = 8, 10, 12, 14, 16, 18, 20)
└── BestLocalizedWavelet (n = 14, 18, 20)
```

### Haar Wavelet

The simplest possible wavelet — a step function. Equivalent to D2 (Daubechies with 2 coefficients). Computes pair-wise averages and differences, producing a fast transform with the lowest computational cost.

```java
Wavelet w = new HaarWavelet();
```

**Best for:** piecewise-constant signals, quick prototyping, signal with abrupt discontinuities.

---

### D4 Wavelet

Daubechies D4 (4-coefficient) wavelet using a different centering convention from `DaubechiesWavelet(4)`. The forward and inverse steps are computed explicitly (no generic filter loop) for maximum speed.

```java
Wavelet w = new D4Wavelet();
```

**Best for:** signals with linear trends. One extra vanishing moment compared to Haar.

---

### Daubechies Wavelets

The standard Daubechies family with *maximal vanishing moments* for a given support width `n`. The number of vanishing moments equals `n/2`.

```java
Wavelet w4  = new DaubechiesWavelet(4);   // 2 vanishing moments
Wavelet w6  = new DaubechiesWavelet(6);   // 3 vanishing moments
Wavelet w8  = new DaubechiesWavelet(8);   // 4 vanishing moments
// ... up to n=20 (10 vanishing moments)
```

**Supported orders:** 4, 6, 8, 10, 12, 14, 16, 18, 20.

**Best for:** general-purpose use; more vanishing moments → better polynomial approximation at the cost of a wider support.

---

### Coiflet Wavelets

Coiflets are designed so that *both* the wavelet function and the scaling function have vanishing moments. This makes the scaling coefficients better approximations of signal samples, which is useful in numerical analysis.

```java
Wavelet c6  = new CoifletWavelet(6);    // 2 vanishing moments each
Wavelet c12 = new CoifletWavelet(12);   // 4 vanishing moments each
// ... up to n=30
```

**Supported orders:** 6, 12, 18, 24, 30 (multiples of 6).

**Best for:** signals where the coarsest approximation coefficients should closely match the original sample values (e.g. numerical integration, quadrature).

---

### Symlet Wavelets

Symlets (S8–S20) are near-symmetric modifications of the Daubechies family. They have the same number of vanishing moments for a given support, but the filter energy is more symmetrically distributed, reducing phase distortion.

```java
Wavelet s8  = new SymletWavelet(8);
Wavelet s12 = new SymletWavelet(12);
// ... up to n=20
```

**Supported orders:** 8, 10, 12, 14, 16, 18, 20 (even numbers only).

**Best for:** audio processing and signals where linear phase response matters.

---

### Best Localized Wavelets

Best Localized (BL) wavelets minimize time-frequency uncertainty while maintaining compact support. They are better localized in time than standard Daubechies wavelets of the same order.

```java
Wavelet bl14 = new BestLocalizedWavelet(14);
Wavelet bl18 = new BestLocalizedWavelet(18);
Wavelet bl20 = new BestLocalizedWavelet(20);
```

**Supported orders:** 14, 18, 20.

**Best for:** transient detection in signals where both time and frequency localization matter.

---

### Custom Wavelets

Any orthogonal filter bank coefficients can be wrapped in the base `Wavelet` class directly:

```java
// Example: Daubechies D4 coefficients
double[] h = {
    0.4829629131445341,  0.8365163037378079,
    0.2241438680420134, -0.1294095225512604
};
Wavelet myWavelet = new Wavelet(h);
```

The constructor automatically derives the high-pass (wavelet) filter `g` from the low-pass (scaling) filter `h` via the quadrature mirror filter (QMF) relationship:
```
g[n] = (-1)^n * h[N-1-n]
```

---

## Discrete Wavelet Transform

### Forward Transform

`wavelet.transform(double[] a)` applies the full multi-level DWT in-place. Starting from the full signal length `N`, it repeatedly applies the one-level forward filter, halving the active length at each step, until the active length drops below `ncof` (the number of filter coefficients).

```java
double[] a = { 1, 2, 3, 4, 5, 6, 7, 8 };  // length must be a power of 2
new DaubechiesWavelet(4).transform(a);
// a[] now contains the multi-resolution wavelet coefficients
```

The resulting array is ordered coarsest-to-finest:

```
[ s1 | d1 | d2 .... | d3 ............ ]
   ^    ^    ^--- level 2 detail        ^--- level 3 detail (finest)
   |    |
   |    +--- level 1 detail (coarsest)
   +--- level 0 scaling (global mean)
```

### Inverse Transform

`wavelet.inverse(double[] a)` reconstructs the original signal from its wavelet coefficients in-place. It is the exact numerical inverse of `transform()`:

```java
double[] original = { 1, 2, 3, 4, 5, 6, 7, 8 };
double[] a = original.clone();

Wavelet w = new DaubechiesWavelet(8);
w.transform(a);
w.inverse(a);

// a[] ≈ original[] (to within floating-point rounding, ≈ 1e-14)
```

### Input Requirements

| Requirement | Detail |
|---|---|
| **Length** | Must be a power of 2 (2, 4, 8, 16, …) |
| **Minimum length** | Must be ≥ the number of filter coefficients `ncof` |
| **In-place** | The input array is modified; clone first if the original is needed |

Both `transform()` and `inverse()` throw `IllegalArgumentException` if the input length is not a power of 2, or is shorter than `ncof`.

```java
// Zero-pad to the next power of 2 for non-power-of-2 signals:
int n = signal.length;
int padded = Integer.highestOneBit(n - 1) << 1;  // next power of 2
double[] a = Arrays.copyOf(signal, padded);        // right-pad with zeros
wavelet.transform(a);
```

### Coefficient Layout

After `transform()`, index 0 holds the **global scaling coefficient** (proportional to the signal mean). This coefficient is never thresholded by `WaveletShrinkage`.

---

## Wavelet Shrinkage — Signal Denoising

`WaveletShrinkage` is a utility interface with only static methods. No instantiation is needed.

### Universal Threshold

The threshold is computed automatically using Donoho & Johnstone's **universal threshold**:

```
λ = σ · √(2 · log(N))
```

where:
- `N` is the signal length,
- `σ` is estimated from the **finest-scale detail coefficients** (the upper half of the transformed array) using the scaled Median Absolute Deviation (MAD):

```
σ̂ = MAD(finest detail coefficients) / 0.6745
```

This estimator is robust to signal content because the finest-scale detail coefficients are dominated by noise (not signal) for smooth signals.

### Hard vs Soft Thresholding

| Mode | Formula | Effect |
|---|---|---|
| **Hard** (default) | `c → c if |c| ≥ λ, else 0` | Preserves large coefficients exactly; produces sharper edges |
| **Soft** | `c → sign(c)·max(|c|−λ, 0)` | Shrinks all surviving coefficients toward zero; smoother reconstruction |

Hard thresholding tends to produce better MSE for sparse signals; soft thresholding gives smoother reconstructions and is preferred when the signal is expected to be continuous.

### 1-D Denoising

```java
import smile.wavelet.*;

double[] signal = /* ... your noisy 1-D signal, length must be a power of 2 ... */;

// Hard thresholding (default)
WaveletShrinkage.denoise(signal, new DaubechiesWavelet(8));

// Explicit hard thresholding
WaveletShrinkage.denoise(signal, new DaubechiesWavelet(8), false);

// Soft thresholding
WaveletShrinkage.denoise(signal, new DaubechiesWavelet(8), true);
```

**Note:** `denoise()` modifies the array in-place. Clone it first if the original is needed.

**The global scaling coefficient `signal[0]` is always preserved** — it encodes the mean of the signal and is never zeroed by thresholding.

### 2-D Denoising

The 2-D extension applies the 1-D denoising independently to each row, then to each column (separable 2-D wavelet transform):

```java
double[][] image = /* rows × cols, both dimensions must be powers of 2 */;

// Hard thresholding (default)
WaveletShrinkage.denoise2D(image, new HaarWavelet());

// Soft thresholding
WaveletShrinkage.denoise2D(image, new HaarWavelet(), true);
```

---

## Choosing a Wavelet

| Use case | Recommended wavelet |
|---|---|
| General denoising / prototyping | `DaubechiesWavelet(8)` |
| Piecewise-constant or discontinuous signals | `HaarWavelet` |
| Signals with smooth, polynomial-like trends | `DaubechiesWavelet(12)` or `DaubechiesWavelet(20)` |
| Audio / minimal phase distortion | `SymletWavelet(8)` – `SymletWavelet(20)` |
| Numerical integration / quadrature | `CoifletWavelet(12)` |
| Transient / event detection | `BestLocalizedWavelet(14)` |
| Speed-critical applications | `HaarWavelet` or `D4Wavelet` |

As a rule of thumb: more filter coefficients → more vanishing moments → better suppression of smooth signal content in detail coefficients → more accurate denoising for smooth signals, at the cost of a wider support and slightly higher boundary effects.

---

## Working with Non-Power-of-2 Lengths

The DWT requires that the signal length be a **power of 2**. For signals of arbitrary length, zero-pad on the right:

```java
import java.util.Arrays;

double[] rawSignal = /* length N, not necessarily a power of 2 */;

// Find the next power of 2
int n = rawSignal.length;
int paddedLength = n == 1 ? 1 : Integer.highestOneBit(n - 1) << 1;

double[] padded = Arrays.copyOf(rawSignal, paddedLength);  // pads with 0.0

Wavelet w = new DaubechiesWavelet(8);
WaveletShrinkage.denoise(padded, w);

// Trim back to original length
double[] denoised = Arrays.copyOf(padded, n);
```

**Tip:** Symmetric (periodic) padding often gives better results than zero padding at signal boundaries, especially when the signal does not start and end near zero. For simple use cases, zero padding is sufficient.

---

## Mathematical Background

### Mallat Pyramid Algorithm

The DWT is computed via the **Mallat pyramid** (also called sub-band coding). At each level, the signal of length `n` is decomposed by:

1. Convolving with the **low-pass scaling filter** `h` (downsampled by 2) → approximation coefficients of length `n/2`.
2. Convolving with the **high-pass wavelet filter** `g` (downsampled by 2) → detail coefficients of length `n/2`.

Both convolutions use **circular (periodic) boundary conditions**, so the output has exactly the same length as the input.

The high-pass filter is derived from the low-pass filter via the quadrature mirror relationship:

```
g[k] = (-1)^k · h[N−1−k]
```

This guarantees that the transform is **orthogonal** (energy-preserving, Parseval's theorem holds).

### Orthogonality and Energy Preservation

For any orthogonal wavelet, the DWT preserves signal energy (Parseval's theorem):

```
Σ a[i]²  =  Σ w[i]²
```

where `a` is the original signal and `w` are the wavelet coefficients.

### Universal Threshold Derivation

For a length-`N` signal with i.i.d. Gaussian noise of standard deviation `σ`, the universal threshold

```
λ = σ · √(2 log N)
```

guarantees (with high probability as N → ∞) that all pure-noise coefficients are zeroed while at least one signal coefficient survives, minimising the minimax risk over a broad class of signals (Donoho & Johnstone, 1994).

---

## Complete Examples

### Example 1 — Round-Trip Transform

```java
import smile.wavelet.*;

public class RoundTripExample {
    public static void main(String[] args) {
        double[] signal = {1, 2, 3, 4, 5, 6, 7, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        double[] original = signal.clone();

        Wavelet w = new DaubechiesWavelet(8);
        w.transform(signal);

        System.out.println("Wavelet coefficients:");
        for (int i = 0; i < signal.length; i++) {
            System.out.printf("  w[%2d] = %10.6f%n", i, signal[i]);
        }

        w.inverse(signal);

        System.out.println("\nReconstruction error:");
        double maxErr = 0;
        for (int i = 0; i < signal.length; i++) {
            maxErr = Math.max(maxErr, Math.abs(signal[i] - original[i]));
        }
        System.out.printf("  max |error| = %.2e%n", maxErr);  // expect ~1e-15
    }
}
```

### Example 2 — 1-D Signal Denoising

```java
import smile.wavelet.*;
import java.util.Arrays;

public class DenoiseExample {
    public static void main(String[] args) {
        int n = 128;
        double[] clean  = new double[n];
        double[] noisy  = new double[n];

        // Build a clean signal + broadband noise
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < n; i++) {
            clean[i] = Math.sin(2 * Math.PI * i / 32.0);
            noisy[i] = clean[i] + 0.4 * rng.nextGaussian();
        }

        // Denoise with Daubechies D8, hard thresholding
        double[] denoised = noisy.clone();
        WaveletShrinkage.denoise(denoised, new DaubechiesWavelet(8), false);

        // Report MSE improvement
        System.out.printf("Noisy  MSE = %.6f%n", mse(clean, noisy));
        System.out.printf("Denoised MSE = %.6f%n", mse(clean, denoised));
    }

    static double mse(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) { double d = a[i]-b[i]; sum += d*d; }
        return sum / a.length;
    }
}
```

### Example 3 — Soft Thresholding Comparison

```java
import smile.wavelet.*;

public class SoftVsHardExample {
    public static void main(String[] args) {
        double[] signal = buildNoisySignal(256);

        double[] hard = signal.clone();
        WaveletShrinkage.denoise(hard, new SymletWavelet(8), false);

        double[] soft = signal.clone();
        WaveletShrinkage.denoise(soft, new SymletWavelet(8), true);

        System.out.println("Hard-thresholded signal (first 8): "
                + java.util.Arrays.toString(java.util.Arrays.copyOf(hard, 8)));
        System.out.println("Soft-thresholded signal (first 8): "
                + java.util.Arrays.toString(java.util.Arrays.copyOf(soft, 8)));
    }

    static double[] buildNoisySignal(int n) {
        double[] t = new double[n];
        java.util.Random rng = new java.util.Random(0);
        for (int i = 0; i < n; i++)
            t[i] = Math.cos(2 * Math.PI * i / 64.0) + 0.3 * rng.nextGaussian();
        return t;
    }
}
```

### Example 4 — 2-D Image Denoising

```java
import smile.wavelet.*;

public class Denoise2DExample {
    public static void main(String[] args) {
        int n = 64;  // must be a power of 2
        double[][] image = new double[n][n];

        // Synthetic image: smooth 2-D cosine + noise
        java.util.Random rng = new java.util.Random(1);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                image[i][j] = Math.cos(2 * Math.PI * i / 16.0)
                             * Math.cos(2 * Math.PI * j / 16.0)
                             + 0.3 * rng.nextGaussian();

        WaveletShrinkage.denoise2D(image, new DaubechiesWavelet(8), false);

        System.out.println("Centre pixel after denoising: " + image[n/2][n/2]);
    }
}
```

### Example 5 — Custom Wavelet

```java
import smile.wavelet.*;

public class CustomWaveletExample {
    // Daubechies D6 coefficients
    private static final double[] D6 = {
        0.3326705529500825,  0.8068915093110924, 0.4598775021184914,
       -0.1350110200102546, -0.0854412738820267, 0.0352262918857095
    };

    public static void main(String[] args) {
        Wavelet w = new Wavelet(D6);

        double[] a = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                      15,14,13,12,11,10, 9, 8, 7,  6,  5,  4,  3,  2,  1,  0};
        double[] original = a.clone();

        w.transform(a);
        w.inverse(a);

        double maxErr = 0;
        for (int i = 0; i < a.length; i++)
            maxErr = Math.max(maxErr, Math.abs(a[i] - original[i]));
        System.out.printf("Custom D6 roundtrip error: %.2e%n", maxErr);
    }
}
```

---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

