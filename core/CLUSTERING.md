# SMILE — Clustering User Guide

The `smile.clustering` package provides a comprehensive suite of clustering algorithms for
partitioning data into meaningful groups. Every algorithm is implemented as a static factory
class (no state in the class itself), returning a rich result object that carries cluster labels,
centroids, distortions, and prediction capabilities.

---

## Table of Contents

1. [Core Abstractions](#1-core-abstractions)
   - [Clustering interface and `Options`](#11-clustering-interface-and-options)
   - [Partitioning](#12-partitioning)
   - [CentroidClustering](#13-centroidclustering)
2. [K-Means](#2-k-means)
3. [K-Medoids (CLARANS)](#3-k-medoids-clarans)
4. [K-Modes](#4-k-modes)
5. [X-Means](#5-x-means)
6. [G-Means](#6-g-means)
7. [Deterministic Annealing](#7-deterministic-annealing)
8. [DBSCAN](#8-dbscan)
9. [HDBSCAN](#9-hdbscan)
10. [DENCLUE](#10-denclue)
11. [MEC](#11-mec)
12. [SIB](#12-sib)
13. [Spectral Clustering](#13-spectral-clustering)
14. [Hierarchical Clustering](#14-hierarchical-clustering)
    - [Linkage Methods](#141-linkage-methods)
15. [Running Multiple Trials](#15-running-multiple-trials)
16. [Algorithm Comparison](#16-algorithm-comparison)
17. [Common Patterns and Tips](#17-common-patterns-and-tips)

---

## 1. Core Abstractions

### 1.1 Clustering Interface and Options

`Clustering` is a utility interface that defines the global `OUTLIER` constant and the shared
`Options` record used by most partitioning algorithms.

```java
// The label assigned to noise points / outliers
int OUTLIER = Integer.MAX_VALUE;
```

`Clustering.Options` bundles the most common hyperparameters:

| Parameter | Type | Description |
|---|---|---|
| `k` | `int` | Number of clusters (≥ 2) |
| `maxIter` | `int` | Maximum iterations (> 0) |
| `tol` | `double` | Convergence tolerance (≥ 0) |
| `controller` | `IterativeAlgorithmController<AlgoStatus>` | Optional training controller; pass `null` to disable |

```java
// Convenience constructor (tol = 1E-4, no controller)
var opts = new Clustering.Options(4, 100);

// Full constructor
var opts = new Clustering.Options(4, 100, 1E-5, null);

// Serialize to / restore from java.util.Properties (useful for configuration files)
Properties props = opts.toProperties();
Clustering.Options restored = Clustering.Options.of(props);
```

Property keys used in serialization:

| Key | Default |
|---|---|
| `smile.clustering.k` | `2` |
| `smile.clustering.iterations` | `100` |
| `smile.clustering.tolerance` | `1E-4` |

---

### 1.2 Partitioning

`Partitioning` is the base class for density-based algorithms (DBSCAN, HDBSCAN, MEC, DENCLUE)
that do not produce centroids. It stores:

- `k` — number of clusters found (not counting the outlier pseudo-cluster).
- `group[]` — per-point cluster label; noise/outlier points carry `Clustering.OUTLIER`.

```java
DBSCAN<double[]> model = DBSCAN.fit(data, 5, 0.5);

int k            = model.k;           // number of real clusters
int[] labels     = model.group();     // per-point labels
int labelOf0     = model.group(0);    // label of first point
```

---

### 1.3 CentroidClustering

`CentroidClustering<T, U>` is a record returned by all centroid-based algorithms. `T` is the
centroid type and `U` is the data point type (usually both `double[]`; SIB uses different types).

**Fields and accessors:**

| Method | Returns | Description |
|---|---|---|
| `k()` | `int` | Number of clusters |
| `centers()` | `T[]` | Cluster centroids / medoids |
| `center(i)` | `T` | Centroid of cluster `i` |
| `group()` | `int[]` | Per-point cluster labels |
| `group(i)` | `int` | Label of data point `i` |
| `proximity(i)` | `double` | Squared distance of point `i` to its centroid |
| `size(i)` | `int` | Number of points in cluster `i` |
| `distortions()` | `double[]` | Average squared distance per cluster + overall |
| `distortion()` | `double` | Overall average squared distance (WCSS / n) |
| `radius(i)` | `double` | RMS radius of cluster `i`: `sqrt(distortions[i])` |
| `predict(x)` | `int` | Assign a new point to the nearest centroid |
| `name()` | `String` | Algorithm name |
| `toString()` | `String` | Formatted cluster summary table |

`CentroidClustering` implements `Comparable<CentroidClustering<T,U>>` by total distortion,
which makes it easy to select the best run across multiple random restarts.

**Example — inspecting the result:**

```java
CentroidClustering<double[], double[]> model = KMeans.fit(data, 4, 100);

System.out.println(model);              // formatted table
System.out.printf("Distortion: %.4f%n", model.distortion());
System.out.printf("Cluster 0 RMS radius: %.4f%n", model.radius(0));

// Predict for a new observation
int label = model.predict(newPoint);
```

**K-Means++ seeding** is used internally by every centroid algorithm to initialize centroids.
The static helpers are also available for custom use:

```java
// K-Means++ initial clustering for arbitrary distance function
CentroidClustering<double[], double[]> init =
        CentroidClustering.init("MyAlgo", data, k, MathEx::distance);

// Convenient shortcut: returns k cloned seed vectors
double[][] seeds = CentroidClustering.seeds(data, k);
```

---

## 2. K-Means

**Algorithm:** Lloyd's algorithm with K-Means++ initialization and BBD-tree acceleration
(Kanungo et al.). For each iteration, the BBD-tree speeds up the assignment step from O(nkd)
toward O(n log k · d). Missing-value data is supported via the `lloyd` variant.

**When to use:** The default choice for numerical vector data when `k` is known in advance.
Fast and memory-efficient even for millions of points.

### API

```java
// Quick form
CentroidClustering<double[], double[]> model = KMeans.fit(data, k, maxIter);

// Full options
CentroidClustering<double[], double[]> model =
        KMeans.fit(data, new Clustering.Options(k, maxIter, tol, controller));

// Pre-built BBD-tree (reuse across runs)
BBDTree bbd = new BBDTree(data);
CentroidClustering<double[], double[]> model = KMeans.fit(bbd, data, opts);

// Data with NaN (missing values)
CentroidClustering<double[], double[]> model = KMeans.lloyd(data, k, maxIter);
```

**Parameters:**

| Parameter | Meaning |
|---|---|
| `k` | Number of clusters. Must be ≥ 2 and ≤ n. |
| `maxIter` | Lloyd iterations per run (default 100 is usually sufficient). |
| `tol` | Stop when improvement < tol (default `1E-4`). |

**Validation:** Throws `IllegalArgumentException` for empty data, ragged rows, non-finite
values (NaN/Inf), or `k > n`.

**Example:**

```java
import smile.clustering.*;
import smile.math.MathEx;

MathEx.setSeed(42);
double[][] data = /* your n×d matrix */;

// Single run
CentroidClustering<double[], double[]> model = KMeans.fit(data, 5, 100);
System.out.println(model);

// Best of 10 runs
CentroidClustering<double[], double[]> best =
        Clustering.run(10, () -> KMeans.fit(data, 5, 100));
```

---

## 3. K-Medoids (CLARANS)

**Algorithm:** CLARANS (Clustering Large Applications based on RANdomized Search). Unlike
K-Means, medoids are always actual data points. CLARANS treats the search as a graph walk:
nodes are sets of `k` medoids; edges connect nodes that differ by exactly one medoid. It
samples random neighbor nodes and moves to the best one found.

**When to use:**
- When cluster centers must be real observations (interpretable prototypes).
- When a custom `Distance<T>` measure is needed on arbitrary objects (strings, graphs, etc.).
- When data contains outliers that would otherwise distort centroids.

### API

```java
// Quick form — uses Distance<T> for generic objects
CentroidClustering<T, T> model = KMedoids.fit(data, distance, k);

// Full options (tol = maxNeighbor ratio; maxIter = numLocal, capped at 3)
CentroidClustering<T, T> model =
        KMedoids.fit(data, distance, new Clustering.Options(k, 2, 0.0125, null));
```

**Parameters (via `Clustering.Options`):**

| Options field | CLARANS meaning |
|---|---|
| `k` | Number of clusters |
| `maxIter` | `numLocal` — number of local-minima searches (capped at 3; 2 is usually best) |
| `tol` | Ratio for `maxNeighbor = tol * k * (n - k)` (default `0.0125`) |

**Example:**

```java
import smile.clustering.*;
import smile.math.distance.EuclideanDistance;

Double[][] boxed = /* boxed double[][] */;
var model = KMedoids.fit(boxed, new EuclideanDistance(), 4);

// Medoids are actual data points
for (Double[] center : model.centers()) {
    System.out.println(Arrays.toString(center));
}
```

---

## 4. K-Modes

**Algorithm:** Categorical analogue of K-Means. Centroids are per-column modes (most frequent
value), and distances are Hamming distances on integer-coded categorical features.

**When to use:** Purely categorical or mixed-integer data where Euclidean distance is
meaningless.

### API

```java
// data is int[][], where each int is a category code
CentroidClustering<int[], int[]> model = KModes.fit(data, k, maxIter);

// Full options
CentroidClustering<int[], int[]> model =
        KModes.fit(data, new Clustering.Options(k, maxIter));
```

**Example:**

```java
// Gender: 0=male, 1=female
// Color: 0=red, 1=green, 2=blue
// Size: 0=S, 1=M, 2=L
int[][] data = {
    {0, 0, 0}, {0, 0, 1}, {1, 2, 2}, {1, 2, 1}
};

CentroidClustering<int[], int[]> model = KModes.fit(data, 2, 50);
int[] mode0 = model.center(0); // modal values of cluster 0
int label = model.predict(new int[]{0, 0, 0});
```

---

## 5. X-Means

**Algorithm:** Extends K-Means by automatically determining the number of clusters. Starting
from a single cluster, it iteratively tries to split each centroid in two. A split is accepted
only if the BIC (Bayesian Information Criterion) of the two-child model is better than the
parent's BIC under a Gaussian spherical model. The outer loop refines all centroids with
BBD-tree-accelerated Lloyd updates after each splitting round.

**When to use:** When `k` is unknown and the data are reasonably Gaussian-like. The BIC
criterion favors spherical, balanced clusters.

### API

```java
// kmax is the upper bound on cluster count
CentroidClustering<double[], double[]> model = XMeans.fit(data, kmax, maxIter);

// Full options (k field is kmax here)
CentroidClustering<double[], double[]> model =
        XMeans.fit(data, new Clustering.Options(kmax, maxIter));
```

**Parameters:**

| Parameter | Meaning |
|---|---|
| `kmax` | Maximum number of clusters to discover |
| `maxIter` | K-Means iterations per inner call |

**Example:**

```java
CentroidClustering<double[], double[]> model = XMeans.fit(data, 20, 100);
System.out.printf("Discovered %d clusters%n", model.k());
```

---

## 6. G-Means

**Algorithm:** Like X-Means but uses the Anderson-Darling statistical test rather than BIC to
decide whether to split a cluster. It projects cluster members onto the candidate split
direction and tests whether the 1-D projection is Gaussian. If the null hypothesis (Gaussian)
is rejected, the split is accepted.

**When to use:** When `k` is unknown and you suspect non-Gaussian within-cluster distributions.
G-Means is more sensitive to multi-modality than X-Means.

### API

```java
CentroidClustering<double[], double[]> model = GMeans.fit(data, kmax, maxIter);
```

**Example:**

```java
CentroidClustering<double[], double[]> model = GMeans.fit(data, 10, 100);
System.out.printf("G-Means found %d clusters%n", model.k());
```

---

## 7. Deterministic Annealing

**Algorithm:** A soft-EM approach that avoids local optima by treating temperature as an
annealing parameter. At high temperature all centroids converge to the global mean. As
temperature decreases, pairs of centroids split when the distance between them exceeds a
threshold, growing the model from 2 to `kmax` effective clusters. A final hard-assignment
pass produces the `CentroidClustering` result.

**When to use:** When you want to avoid local optima for a given `kmax` and can afford a
longer training time. Particularly effective on well-separated Gaussian clusters.

### API

```java
CentroidClustering<double[], double[]> model =
        DeterministicAnnealing.fit(data, kmax, alpha, maxIter);

// Full options
var opts = new DeterministicAnnealing.Options(kmax, alpha, maxIter, tol, splitTol, null);
CentroidClustering<double[], double[]> model = DeterministicAnnealing.fit(data, opts);
```

**Parameters (`DeterministicAnnealing.Options`):**

| Parameter | Default | Description |
|---|---|---|
| `kmax` | — | Maximum number of clusters (≥ 2) |
| `alpha` | — | Temperature cooling rate; must be in (0, 1). Typical values: 0.8–0.95 |
| `maxIter` | — | Maximum Lloyd iterations at each temperature level |
| `tol` | `1E-4` | Convergence tolerance for soft distortion |
| `splitTol` | `1E-2` | Minimum centroid separation before a split is accepted |

Property keys:

| Key | Default |
|---|---|
| `smile.deterministic_annealing.k` | `2` |
| `smile.deterministic_annealing.alpha` | `0.9` |
| `smile.deterministic_annealing.iterations` | `100` |
| `smile.deterministic_annealing.tolerance` | `1E-4` |
| `smile.deterministic_annealing.split_tolerance` | `1E-2` |

**Example:**

```java
MathEx.setSeed(42);
var model = DeterministicAnnealing.fit(data, 10, 0.9, 100);
System.out.printf("Converged to %d clusters, distortion = %.4f%n",
        model.k(), model.distortion());
```

---

## 8. DBSCAN

**Algorithm:** Density-Based Spatial Clustering of Applications with Noise. DBSCAN forms
clusters from regions of high density separated by regions of low density. A point is a
*core point* if at least `minPts` neighbors lie within radius `ε`. Dense regions reachable
from a core point form a cluster. Points unreachable from any core point are labeled
`Clustering.OUTLIER`.

**Key properties:**
- Discovers clusters of arbitrary shape.
- Does not require specifying `k` in advance.
- Inherently labels noise as outliers.
- Works poorly in high-dimensional space (curse of dimensionality).
- Uses a KD-tree for `double[]` data (O(n log n)); a `LinearSearch` for arbitrary types.

### API

```java
// Euclidean data — KD-tree accelerated
DBSCAN<double[]> model = DBSCAN.fit(data, minPts, radius);

// Arbitrary objects with a custom Distance
DBSCAN<T> model = DBSCAN.fit(data, distance, minPts, radius);

// Pre-built RNNSearch (e.g. BallTree, KDTree)
DBSCAN<T> model = DBSCAN.fit(data, nns, minPts, radius);
```

**Parameters:**

| Parameter | Meaning |
|---|---|
| `minPts` | Minimum neighbors within `radius` for a core point (≥ 1). Typical: 4–10. |
| `radius` | Neighborhood radius `ε` (> 0). Choose at the "knee" of a k-NN distance plot. |

**Prediction:**

```java
// Assigns x to the cluster of the closest core point, or OUTLIER
int label = model.predict(x);
```

**Accessors:**

```java
int k         = model.k;              // number of clusters (excluding outliers)
int[] labels  = model.group();        // per-training-point labels
int minPts    = model.minPoints();
double r      = model.radius();
```

**Example:**

```java
DBSCAN<double[]> model = DBSCAN.fit(data, 5, 0.5);
System.out.printf("Found %d clusters%n", model.k);

long noiseCount = Arrays.stream(model.group())
        .filter(l -> l == Clustering.OUTLIER).count();
System.out.printf("Noise points: %d%n", noiseCount);

int label = model.predict(newPoint);
if (label == Clustering.OUTLIER) System.out.println("New point is noise");
```

---

## 9. HDBSCAN

**Algorithm:** Hierarchical DBSCAN. Builds a full density hierarchy (mutual-reachability
MST → condensed tree) and selects stable flat clusters via a stability criterion. Unlike
DBSCAN, HDBSCAN handles clusters of varying density without needing a global `ε`.

**Pipeline:**
1. Compute per-point core distances using the `minPoints` nearest neighbours.
2. Build the mutual-reachability graph: edge weight = max(coreDistance(a), coreDistance(b), dist(a,b)).
3. Compute the minimum spanning tree of this graph.
4. Convert the MST to a hierarchy and prune using `minClusterSize`.

**When to use:** When clusters have varying densities or DBSCAN's single `ε` is hard to tune.

> **Note:** The current implementation computes full pairwise distances (O(n²) memory and
> time). For very large datasets consider sub-sampling or a nearest-neighbour accelerated
> variant.

### API

```java
// Euclidean data
HDBSCAN<double[]> model = HDBSCAN.fit(data, minPoints, minClusterSize);

// Arbitrary distance
HDBSCAN<T> model = HDBSCAN.fit(data, distance, minPoints, minClusterSize);

// Options record
var opts = new HDBSCAN.Options(minPoints, minClusterSize);
HDBSCAN<double[]> model = HDBSCAN.fit(data, opts);
```

**Parameters (`HDBSCAN.Options`):**

| Parameter | Description |
|---|---|
| `minPoints` | Neighborhood size for core-distance estimation. Larger = smoother density. |
| `minClusterSize` | Minimum cluster size in the condensed hierarchy. Controls granularity. |

**Accessors:**

```java
int k                  = model.k;
int[] labels           = model.group();     // OUTLIER for noise
int mp                 = model.minPoints();
int mcs                = model.minClusterSize();
double[] coreDistances = model.coreDistance();
double[] stability     = model.stability(); // per-cluster stability scores
```

**Example:**

```java
HDBSCAN<double[]> model = HDBSCAN.fit(data, 5, 10);
System.out.printf("Found %d clusters%n", model.k);
double[] stability = model.stability();
for (int i = 0; i < model.k; i++) {
    System.out.printf("Cluster %d stability: %.4f%n", i, stability[i]);
}
```

---

## 10. DENCLUE

**Algorithm:** DENsity CLUstering. Selects `m` representative sample points via K-Means
(the "particles"), then hill-climbs the Gaussian kernel density estimate from each training
point to its local density maximum (the "attractor"). DBSCAN is run on the resulting
attractors to produce the final cluster assignment.

**When to use:** Smooth, continuous data where clusters are density peaks. Not suitable for
high-dimensional data (curse of dimensionality), sparse data, or uniform distributions.

### API

```java
// Quick form
DENCLUE model = DENCLUE.fit(data, sigma, m);

// Full options
var opts = new DENCLUE.Options(sigma, m, minPts, tol);
DENCLUE model = DENCLUE.fit(data, opts);
```

**Parameters (`DENCLUE.Options`):**

| Parameter | Default | Description |
|---|---|---|
| `sigma` | — | Gaussian kernel bandwidth. Choose so that the number of density attractors is stable across a range of sigma. |
| `m` | — | Number of sample points for KDE (≪ n; trades speed for accuracy). |
| `minPts` | `10` | Minimum attractor neighborhood for DBSCAN post-processing. |
| `tol` | `1E-2` | Hill-climbing convergence tolerance. |

Property keys:

| Key | Default |
|---|---|
| `smile.denclue.sigma` | `1.0` |
| `smile.denclue.m` | `100` |
| `smile.denclue.min_points` | `10` |
| `smile.denclue.tolerance` | `1E-2` |

**Prediction:**

```java
// Hill-climb from x and match to nearest training attractor
int label = model.predict(x);  // may be Clustering.OUTLIER
```

**Accessors:**

```java
int k              = model.k;
int[] labels       = model.group();
double sigma       = model.sigma();
double tol         = model.tolerance();
double[] radius    = model.radius();          // per-point attractor radius
double[][] attract = model.attractors();      // per-point density attractors
```

**Example:**

```java
MathEx.setSeed(42);
DENCLUE model = DENCLUE.fit(data, 0.5, 200);
System.out.printf("DENCLUE found %d clusters%n", model.k);

// Predict a new point
int label = model.predict(newPoint);
```

---

## 11. MEC

**Algorithm:** Non-parametric Minimum Conditional Entropy Clustering. Minimizes the
conditional entropy H(C | x) over cluster labels C by iteratively relabeling each point
to reduce entropy in its neighborhood (Parzen density estimate). Unlike DBSCAN/DENCLUE,
MEC needs a good initialization (e.g. K-Means output) — a random start is not appropriate.
MEC can naturally handle outliers and reveal structure without assuming a fixed k.

**When to use:** When you have a rough initial partition and want to refine it according to a
density criterion. Effective when the true cluster count is uncertain.

### API

```java
// Initialize from K-Means (double[][] data)
MEC<double[]> model = MEC.fit(data, radius, k, maxIter);

// Initialize from K-Medoids (arbitrary Distance<T>)
MEC<T> model = MEC.fit(data, distance, radius, k, maxIter);

// Full options
var opts = new MEC.Options(k, maxIter, tol, controller);
MEC<double[]> model = MEC.fit(data, radius, opts);
```

**Parameters:**

| Parameter | Meaning |
|---|---|
| `radius` | Parzen window radius for density estimation |
| `k` | Initial number of clusters (MEC may merge some) |
| `maxIter` | Maximum relabeling iterations |

**Prediction:**

```java
// Assigns x based on nearest core neighborhood; may return OUTLIER
int label = model.predict(x);
```

**Accessors:**

```java
double entropy = model.entropy();  // objective — smaller is better
double radius  = model.radius();
int[] labels   = model.group();
```

`MEC` implements `Comparable<MEC<T>>` by entropy (lower = better), enabling use with
`Clustering.run`.

**Example:**

```java
MEC<double[]> model = MEC.fit(data, 1.5, 4, 100);
System.out.printf("MEC entropy: %.4f, clusters: %d%n", model.entropy(), model.k);

// Best of 5 restarts
MEC<double[]> best = Clustering.run(5, () -> MEC.fit(data, 1.5, 4, 100));
```

---

## 12. SIB

**Algorithm:** Sequential Information Bottleneck. Clusters document vectors (sparse term
histograms) by minimizing the Jensen-Shannon divergence between cluster centroids and member
distributions. Each iteration performs a sequential sweep: every document is reassigned to the
cluster that maximally reduces JS divergence.

**When to use:** Text clustering (bag-of-words or TF-IDF vectors). SIB treats documents as
probability distributions over vocabulary and exploits sparsity efficiently.

### API

```java
// data is SparseArray[] (sparse document–term histograms, normalized to sum to 1)
CentroidClustering<SparseArray, SparseArray> model = SIB.fit(data, k, maxIter);

// Full options
var opts = new Clustering.Options(k, maxIter);
CentroidClustering<SparseArray, SparseArray> model = SIB.fit(data, opts);
```

**Example:**

```java
import smile.util.SparseArray;

// Build normalized term-frequency vectors
SparseArray[] docs = /* ... */;

var model = SIB.fit(docs, 5, 100);
System.out.printf("SIB JS distortion: %.4f%n", model.distortion());

// Predict a new document
int label = model.predict(newDoc);
```

---

## 13. Spectral Clustering

**Algorithm:** Constructs an RBF (Gaussian) similarity matrix from the data, computes the
normalized graph Laplacian, embeds the data in the top-`k` eigenvectors, then runs K-Means
in that embedding. Captures non-convex clusters and complex manifold structure.

**Nyström approximation:** For large `n`, use `l > 0` random samples to approximate the
eigenvectors without forming the full n×n similarity matrix, reducing cost from O(n²) to O(nl).

**Sparse text path:** `SpectralClustering.fit(SparseIntArray[], p, d)` builds a TF-IDF-weighted
Laplacian for document term-count arrays.

**When to use:** Non-convex or ring-shaped clusters; data where Euclidean distance is poor
but a similarity kernel is available. Avoid on very large datasets without Nyström.

### API

```java
// Euclidean data, exact
CentroidClustering<double[], double[]> model =
        SpectralClustering.fit(data, k, sigma, maxIter);

// Full options
var opts = new SpectralClustering.Options(k, l, sigma, maxIter, tol, controller);
CentroidClustering<double[], double[]> model = SpectralClustering.fit(data, opts);

// Pre-built similarity matrix
CentroidClustering<double[], double[]> model =
        SpectralClustering.fit(similarityMatrix, opts);

// Sparse text data (term count arrays)
CentroidClustering<double[], double[]> model =
        SpectralClustering.fit(sparseDocs, k, numNeighbors);
```

**Parameters (`SpectralClustering.Options`):**

| Parameter | Default | Description |
|---|---|---|
| `k` | — | Number of clusters (≥ 2) |
| `l` | `0` | Nyström sample count. Use 0 for exact. Must be ≥ k if > 0. |
| `sigma` | — | RBF kernel width. Sensitive parameter; tune on your data. |
| `maxIter` | — | K-Means iterations in embedding space |
| `tol` | `1E-4` | K-Means convergence tolerance |

Property keys:

| Key | Default |
|---|---|
| `smile.spectral_clustering.k` | `2` |
| `smile.spectral_clustering.l` | `0` |
| `smile.spectral_clustering.sigma` | `1.0` |
| `smile.spectral_clustering.iterations` | `100` |
| `smile.spectral_clustering.tolerance` | `1E-4` |

**Example:**

```java
// Concentric ring dataset — K-Means fails; spectral clustering succeeds
CentroidClustering<double[], double[]> model =
        SpectralClustering.fit(ringData, 2, 0.5, 100);
System.out.printf("Spectral clustering distortion: %.4f%n", model.distortion());
```

---

## 14. Hierarchical Clustering

**Algorithm:** Agglomerative bottom-up clustering. Each observation starts in its own cluster;
pairs of clusters are greedily merged by the chosen *linkage* criterion until a single cluster
remains. The full merge history (dendrogram) is stored and can be cut at any height or count.

**When to use:**
- When you want to explore the full cluster hierarchy without committing to a fixed `k`.
- When data is small to medium (the algorithm is O(n²) or O(n² log n) depending on linkage).
- When the "shape" of the dendrogram provides insight into the data structure.

### API

```java
// Fit using any Linkage object
HierarchicalClustering model = HierarchicalClustering.fit(linkage);

// Cut by number of clusters
int[] labels = model.partition(k);

// Cut by merge height
int[] labels = model.partition(h);
```

**`partition(double h)` notes:** Validates that the height array is monotonically
non-decreasing (a property of all valid linkages). Throws `IllegalArgumentException` if `h`
is larger than all merge heights (no cut possible) or `IllegalStateException` if the linkage
produces a non-monotonic tree.

**Accessors:**

```java
int[][] tree   = model.tree();    // (n-1)×2 merge table
double[] height = model.height(); // merge heights (non-decreasing)
```

### 14.1 Linkage Methods

All linkage classes live in `smile.clustering.linkage` and implement `Linkage`. They are built
from a data matrix or pre-computed distance matrix.

| Class | Update formula | Notes |
|---|---|---|
| `SingleLinkage` | min(d(a,x), d(b,x)) | Chaining effect; sensitive to outliers |
| `CompleteLinkage` | max(d(a,x), d(b,x)) | Compact, balanced clusters |
| `UPGMALinkage` | size-weighted average | UPGMA; good general-purpose choice |
| `WPGMALinkage` | unweighted average | WPGMA; ignores cluster sizes |
| `UPGMCLinkage` | centroid of centroids (squared) | Stored as squared distance; height sqrt'd at output |
| `WPGMCLinkage` | midpoint (squared) | Stored as squared distance; height sqrt'd at output |
| `WardLinkage` | minimizes within-cluster variance (squared) | Best cluster quality; similar to K-Means result |

**Constructors:**

```java
// From data matrix (computes pairwise Euclidean distances)
WardLinkage linkage = WardLinkage.of(data);

// From a pre-computed symmetric distance matrix
float[][] dist = Linkage.proximity(data, new EuclideanDistance());
WardLinkage linkage = new WardLinkage(dist);
```

> **Note:** `Linkage` stores distances as `float` (not `double`) and supports at most
> 65 535 observations.

**Example:**

```java
import smile.clustering.*;
import smile.clustering.linkage.*;

double[][] data = /* ... */;

// Full dendrogram with Ward linkage
HierarchicalClustering model = HierarchicalClustering.fit(WardLinkage.of(data));

// Cut into 4 clusters
int[] labels4 = model.partition(4);

// Cut at height 5.0 (inspect model.height() to choose a meaningful cut point)
int[] labelsH = model.partition(5.0);

// Visualise merge heights
double[] heights = model.height();
for (int i = heights.length - 1; i >= Math.max(0, heights.length - 10); i--) {
    System.out.printf("Merge %d: height = %.4f%n", i, heights[i]);
}
```

---

## 15. Running Multiple Trials

All algorithms based on random initialization (`KMeans`, `KMedoids`, `KModes`, `MEC`, etc.)
may converge to different local optima on different runs. `Clustering.run` repeats the fit
`n` times and returns the best result (minimum `compareTo`).

```java
import smile.clustering.Clustering;
import smile.math.MathEx;

MathEx.setSeed(19650218);

// Best of 10 K-Means runs
CentroidClustering<double[], double[]> best =
        Clustering.run(10, () -> KMeans.fit(data, 5, 100));

System.out.printf("Best distortion: %.4f%n", best.distortion());
```

`Clustering.run` works for any `T extends Comparable<? super T>`. `CentroidClustering`
compares by total distortion; `MEC` compares by conditional entropy.

Throws `IllegalArgumentException` if `runs ≤ 0`.

---

## 16. Algorithm Comparison

| Algorithm | k required | Handles outliers | Arbitrary shape | Distance | Data type | Scalability |
|---|---|---|---|---|---|---|
| K-Means | Yes | No | No (convex) | Euclidean | `double[]` | O(nkd·iter) fast |
| K-Medoids | Yes | Partial | No (convex) | Any | Any | O(n²k) |
| K-Modes | Yes | No | No (convex) | Hamming | `int[]` (categorical) | O(nkd·iter) |
| X-Means | Upper bound | No | No (spherical) | Euclidean | `double[]` | O(nkd·iter) |
| G-Means | Upper bound | No | No | Euclidean | `double[]` | O(nkd·iter) |
| Det. Annealing | Upper bound | No | No | Euclidean | `double[]` | Slow (multi-temperature) |
| DBSCAN | No | Yes | Yes | Any | Any (KD-tree for `double[]`) | O(n log n) w/ index |
| HDBSCAN | No | Yes | Yes | Any | Any | O(n²) current impl. |
| DENCLUE | No | Yes | Yes (density) | Euclidean | `double[]` | O(nm·iter) |
| MEC | Initial k | Yes | Yes (density) | Any | Any | O(n·radius·iter) |
| SIB | Yes | No | No | Jensen-Shannon | `SparseArray` (text) | O(nkd·iter) |
| Spectral | Yes | No | Yes (manifold) | RBF kernel | `double[]` | O(n²) or O(nl) w/ Nyström |
| Hierarchical | Post-hoc | Partial | Yes | Any | Any (float prox.) | O(n² log n), ≤ 65 535 pts |

---

## 17. Common Patterns and Tips

### Choosing k

- **Unknown k:** Use X-Means or G-Means (BIC/Anderson-Darling) for Gaussian-like data; DBSCAN
  or HDBSCAN for density-based or irregular clusters; MEC when you have an initial partition.
- **Elbow method:** Run K-Means for k = 2…K, plot `model.distortion()` vs. k, and pick
  the "elbow."
- **Silhouette:** Compute silhouette scores with `smile.validation.metric.Silhouette` to
  compare different k values.

### Handling Outliers

- DBSCAN, HDBSCAN, DENCLUE, and MEC all label noise points with `Clustering.OUTLIER`
  (`Integer.MAX_VALUE`).
- K-Means and centroid-based methods are sensitive to outliers; consider preprocessing with
  DBSCAN to remove noise before running K-Means.

### Reproducibility

All randomized algorithms (`KMeans`, `KMedoids`, `KModes`, `DeterministicAnnealing`) respect
SMILE's global RNG:

```java
smile.math.MathEx.setSeed(42);
```

Set this before every `fit` call to get reproducible results.

### Serialization

Most models implement `java.io.Serializable`. Use `smile.io.Write.object` and
`smile.io.Read.object` for persistence:

```java
import smile.io.*;
import java.nio.file.Path;

Path file = Write.object(model);
CentroidClustering<double[], double[]> loaded = (CentroidClustering<double[], double[]>) Read.object(file);
```

### Properties Round-Trip

Every algorithm with a dedicated `Options` record supports serialization to / from
`java.util.Properties`:

```java
Properties props = opts.toProperties();
// persist props to a file, environment, config system…
var restored = DENCLUE.Options.of(props);
```

### Training Progress Monitoring

Pass an `IterativeAlgorithmController<AlgoStatus>` to receive per-iteration callbacks:

```java
import smile.util.*;

var controller = new IterativeAlgorithmController<AlgoStatus>(status -> {
    System.out.printf("Iter %d: distortion = %.4f%n",
            status.iterations(), status.loss());
});

CentroidClustering<double[], double[]> model =
        KMeans.fit(data, new Clustering.Options(5, 100, 1E-4, controller));
```

Set `controller.interrupt()` from any thread to stop training after the current iteration.

### Linkage Selection for Hierarchical Clustering

| Goal | Recommended linkage |
|---|---|
| Compact spherical clusters | `WardLinkage` |
| Long chains or arbitrary shape | `SingleLinkage` |
| Balanced clusters, moderate sensitivity | `CompleteLinkage` |
| Robust average (most used in bioinformatics) | `UPGMALinkage` |

### Sigma Tuning for DENCLUE and Spectral Clustering

A useful heuristic for `sigma` in both algorithms is to set it equal to the median
nearest-neighbor distance in the data:

```java
double sigma = MathEx.median(
        Arrays.stream(data)
              .mapToDouble(x -> /* distance to nearest neighbor of x */ 0.0)
              .toArray());
```

For DENCLUE, the paper recommends choosing `sigma` such that the number of density attractors
stays constant over a range of `sigma` values. Scan `sigma` logarithmically and pick the
plateau.

### Memory and Scale Limits

| Algorithm | Dominant memory cost | Practical limit |
|---|---|---|
| K-Means (BBD-tree) | O(n·d) | Millions of points |
| DBSCAN (KD-tree) | O(n·d) | Hundreds of thousands |
| HDBSCAN | O(n²) pairwise distances | ~10 000 points |
| Hierarchical clustering | O(n²) proximity matrix (float) | ≤ 65 535 points |
| Spectral (exact) | O(n²) similarity matrix | ~10 000 points |
| Spectral (Nyström, `l` samples) | O(n·l) | Hundreds of thousands |

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
