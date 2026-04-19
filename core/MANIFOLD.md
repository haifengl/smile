# SMILE — Manifold Learning

This guide introduces manifold learning algorithms in `smile.manifold` for nonlinear dimensionality reduction and visualization.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Algorithm Map](#2-algorithm-map)
3. [Input Types and Common Patterns](#3-input-types-and-common-patterns)
4. [Neighbor-Graph Methods](#4-neighbor-graph-methods)
5. [Distance/Proximity Methods](#5-distanceproximity-methods)
6. [Probabilistic Embedding Methods](#6-probabilistic-embedding-methods)
7. [Kernel Method](#7-kernel-method)
8. [Choosing an Algorithm](#8-choosing-an-algorithm)
9. [Practical Tuning Tips](#9-practical-tuning-tips)
10. [Validation and Error Handling](#10-validation-and-error-handling)
11. [API Quick Reference](#11-api-quick-reference)

---

## 1) Overview

Package: `smile.manifold`

Main algorithms:

- `IsoMap`
- `LLE` (Locally Linear Embedding)
- `LaplacianEigenmap`
- `MDS` (Classical/Metric multidimensional scaling)
- `IsotonicMDS` (Non-metric MDS)
- `SammonMapping`
- `TSNE`
- `UMAP`
- `KPCA`

These methods transform high-dimensional data into lower-dimensional coordinates (usually 2D/3D for visualization).

---

## 2) Algorithm Map

- **Neighborhood graph + spectral**
  - `IsoMap`, `LLE`, `LaplacianEigenmap`, `UMAP`
- **Distance/proximity-preserving**
  - `MDS`, `IsotonicMDS`, `SammonMapping`
- **Distribution/topology-preserving visualization**
  - `TSNE`, `UMAP`
- **Kernel eigenspace projection**
  - `KPCA`

---

## 3) Input Types and Common Patterns

Different APIs accept different inputs:

- `double[][] data`: raw feature matrix.
- `T[] data + Distance/Metric`: custom object type with distance function.
- `NearestNeighborGraph`: precomputed k-NN graph (reused across methods).
- `double[][] proximity`: pairwise distance/dissimilarity matrix for MDS-family methods.

Common workflow:

1. Start with `double[][]`.
2. Standardize/normalize features if scales differ.
3. Pick algorithm + options.
4. Call `fit(...)`.
5. Plot returned coordinates.

---

## 4) Neighbor-Graph Methods

## IsoMap

```java
import smile.manifold.IsoMap;

double[][] y = IsoMap.fit(x, new IsoMap.Options(12, 2, false));
```

- Preserves approximate geodesic distances via shortest paths on a neighbor graph.
- Good when manifold is globally curved (e.g., Swiss roll).

## LLE

```java
import smile.manifold.LLE;

double[][] y = LLE.fit(x, new LLE.Options(12, 2));
```

- Preserves local linear reconstruction weights.
- Sensitive to `k` and local sampling density.

## Laplacian Eigenmap

```java
import smile.manifold.LaplacianEigenmap;

double[][] y = LaplacianEigenmap.fit(x, new LaplacianEigenmap.Options(12, 2, 1.0));
```

- Uses graph Laplacian eigenvectors.
- Emphasizes local neighborhood structure.

## UMAP

```java
import smile.manifold.UMAP;

double[][] y = UMAP.fit(x, new UMAP.Options(15));
```

- Fast and scalable for visualization and general manifold reduction.
- Usually strong default choice for exploratory 2D embedding.

---

## 5) Distance/Proximity Methods

These methods operate on a proximity/distance matrix (`double[][] proximity`).

## MDS

```java
import smile.manifold.MDS;

MDS mds = MDS.fit(proximity);
double[][] y = mds.coordinates();
```

## IsotonicMDS (Non-metric MDS)

```java
import smile.manifold.IsotonicMDS;

IsotonicMDS mds = IsotonicMDS.fit(proximity, new IsotonicMDS.Options(2, 100, 1E-4));
double[][] y = mds.coordinates();
```

## SammonMapping

```java
import smile.manifold.SammonMapping;

SammonMapping sammon = SammonMapping.fit(proximity, new SammonMapping.Options(2, 100, 0.2, 1E-4));
double[][] y = sammon.coordinates();
```

---

## 6) Probabilistic Embedding Methods

## t-SNE

```java
import smile.manifold.TSNE;

TSNE tsne = TSNE.fit(x, new TSNE.Options(2, 20, 200, 12, 1000));
double[][] y = tsne.coordinates();
```

Notes:

- Excellent for local cluster visualization.
- Not ideal for preserving global geometry.
- Perplexity and learning rate strongly affect output.

## UMAP

UMAP is also probabilistic/topological and often preferred for speed + scalability.

```java
import smile.manifold.UMAP;

double[][] y = UMAP.fit(x, new UMAP.Options(15, 2, 200, 1.0, 0.1, 1.0, 5, 1.0, 1.0));
```

---

## 7) Kernel Method

## KPCA

```java
import smile.manifold.KPCA;
import smile.math.kernel.GaussianKernel;

KPCA<double[]> kpca = KPCA.fit(x, new GaussianKernel(1.0), new KPCA.Options(2));
double[][] y = kpca.coordinates();
```

Use KPCA when nonlinear structure is well captured by a kernel but you still want eigenspace-style projection.

---

## 8) Choosing an Algorithm

- **First try for visualization:** `UMAP`
- **Cluster-separation visualization:** `TSNE`
- **Preserve geodesic manifold structure:** `IsoMap`
- **Strong local linear manifold assumption:** `LLE`
- **Given pairwise dissimilarities only:** `MDS` / `IsotonicMDS` / `SammonMapping`
- **Kernel-based nonlinear projection:** `KPCA`

---

## 9) Practical Tuning Tips

- Start with `d=2`; move to `d=3` for interactive 3D exploration.
- For neighbor-graph methods, tune `k`:
  - smaller `k` -> more local structure,
  - larger `k` -> more global smoothing.
- For `TSNE`:
  - perplexity often in `[5, 50]`,
  - default early exaggeration is usually fine,
  - PCA-to-50D preprocessing is often helpful for high-dimensional raw features.
- For `UMAP`:
  - `k` (`n_neighbors`) controls local/global tradeoff,
  - `minDist` controls cluster compactness,
  - keep `minDist <= spread`.
- Always compare multiple random seeds when interpreting visual clusters.

---

## 10) Validation and Error Handling

Current APIs validate key parameters (examples):

- `TSNE.Options`: minimum iterations, positive rates/tolerances, momentum constraints.
- `UMAP.Options`: `k >= 2`, `d >= 2`, `minDist > 0`, `minDist <= spread`, etc.

Recent reliability improvements include:

- corrected `TSNE.Options.of(...)` property parsing defaults/keys,
- fixed `UMAP.smoothKnnDist(...)` neighbor-loop indexing,
- guarded degenerate scaling/normalization paths in UMAP initialization.

---

## 11) API Quick Reference

```java
// IsoMap
double[][] IsoMap.fit(double[][] data, IsoMap.Options options)
<T> double[][] IsoMap.fit(T[] data, Distance<T> distance, IsoMap.Options options)
double[][] IsoMap.fit(NearestNeighborGraph nng, IsoMap.Options options)

// LLE
double[][] LLE.fit(double[][] data, LLE.Options options)
double[][] LLE.fit(double[][] data, NearestNeighborGraph nng, int d)

// Laplacian Eigenmap
double[][] LaplacianEigenmap.fit(double[][] data, LaplacianEigenmap.Options options)
<T> double[][] LaplacianEigenmap.fit(T[] data, Distance<T> distance, LaplacianEigenmap.Options options)
double[][] LaplacianEigenmap.fit(NearestNeighborGraph nng, LaplacianEigenmap.Options options)

// MDS / IsotonicMDS / SammonMapping
MDS MDS.fit(double[][] proximity)
MDS MDS.fit(double[][] proximity, MDS.Options options)

IsotonicMDS IsotonicMDS.fit(double[][] proximity)
IsotonicMDS IsotonicMDS.fit(double[][] proximity, IsotonicMDS.Options options)
IsotonicMDS IsotonicMDS.fit(double[][] proximity, double[][] init, IsotonicMDS.Options options)

SammonMapping SammonMapping.fit(double[][] proximity)
SammonMapping SammonMapping.fit(double[][] proximity, SammonMapping.Options options)
SammonMapping SammonMapping.fit(double[][] proximity, double[][] init, SammonMapping.Options options)

// t-SNE
TSNE TSNE.fit(double[][] X)
TSNE TSNE.fit(double[][] X, TSNE.Options options)

// UMAP
double[][] UMAP.fit(double[][] data, UMAP.Options options)
<T> double[][] UMAP.fit(T[] data, Metric<T> distance, UMAP.Options options)
<T> double[][] UMAP.fit(T[] data, NearestNeighborGraph nng, UMAP.Options options)

// KPCA
<T> KPCA<T> KPCA.fit(T[] data, MercerKernel<T> kernel, KPCA.Options options)
```

---

*SMILE — Copyright (c) 2010-2026 Haifeng Li. GNU GPL licensed.*

