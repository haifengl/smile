# SMILE — Manifold Learning

## Table of Contents

- [Overview](#overview)
- [When to Use Manifold Learning](#when-to-use-manifold-learning)
- [Algorithm Comparison](#algorithm-comparison)
- [Classical MDS](#classical-mds)
  - [MDS Options](#mds-options)
  - [MDS Example](#mds-example)
- [Non-metric MDS (Isotonic MDS)](#non-metric-mds-isotonic-mds)
  - [IsotonicMDS Options](#isotonicmds-options)
  - [IsotonicMDS Example](#isotonicmds-example)
- [Sammon Mapping](#sammon-mapping)
  - [SammonMapping Options](#sammonmapping-options)
  - [SammonMapping Example](#sammonmapping-example)
- [Isomap](#isomap)
  - [IsoMap Options](#isomap-options)
  - [IsoMap Example](#isomap-example)
- [Locally Linear Embedding (LLE)](#locally-linear-embedding-lle)
  - [LLE Options](#lle-options)
  - [LLE Example](#lle-example)
- [Laplacian Eigenmaps](#laplacian-eigenmaps)
  - [LaplacianEigenmap Options](#laplacianeigenmap-options)
  - [LaplacianEigenmap Example](#laplacianeigenmap-example)
- [Kernel PCA (KPCA)](#kernel-pca-kpca)
  - [KPCA Options](#kpca-options)
  - [KPCA Example](#kpca-example)
- [t-SNE](#t-sne)
  - [TSNE Options](#tsne-options)
  - [TSNE Example](#tsne-example)
- [UMAP](#umap)
  - [UMAP Options](#umap-options)
  - [UMAP Example](#umap-example)
- [Choosing an Algorithm](#choosing-an-algorithm)
- [Tips and Best Practices](#tips-and-best-practices)
- [API Reference](#api-reference)

---

## Overview

The `smile.manifold` package contains SMILE's dimensionality reduction and manifold learning
algorithms. Given high-dimensional data, these algorithms compute a low-dimensional embedding
— typically 2-D or 3-D — that preserves the intrinsic geometry of the data as faithfully
as possible.

All algorithms follow a consistent pattern:

1. Create an `Options` record that holds hyperparameters.
2. Call the static `fit()` method with your data and options.
3. Read `.coordinates()` from the returned result object.

Options objects are serializable to and from `java.util.Properties`, enabling integration with
SMILE's hyperparameter search (`smile.hpo.Hyperparameters`).

---

## When to Use Manifold Learning

| Task | Recommended approach |
|---|---|
| Visualize high-dimensional data | t-SNE, UMAP, or IsoMap |
| Preserve global pairwise distances | MDS, Sammon Mapping |
| Preserve rank order of distances only | Isotonic MDS |
| Exploit known nonlinear manifold structure | IsoMap, LLE, Laplacian Eigenmaps |
| Apply a Mercer kernel | KPCA |
| Out-of-sample projection needed | KPCA (supports `apply()` for new points) |
| Large datasets (tens of thousands of points) | UMAP (scales well via approximate NNG) |

---

## Algorithm Comparison

| Algorithm | Type | Preserves | Out-of-sample | Scalability |
|---|---|---|---|---|
| MDS | Linear (metric) | Global distances | ✗ | O(n²) |
| Isotonic MDS | Non-metric | Rank order of distances | ✗ | O(n²) |
| Sammon Mapping | Iterative (metric) | Small pairwise distances | ✗ | O(n²) |
| IsoMap | Graph-based | Geodesic distances | ✗ | O(n²) |
| LLE | Spectral | Local linear structure | ✗ | O(n²) |
| Laplacian Eigenmaps | Spectral | Local neighborhood | ✗ | O(n²) |
| KPCA | Kernel/spectral | Kernel similarity | ✓ | O(n²) |
| t-SNE | Probabilistic | Local neighborhoods | ✗ | O(n²) |
| UMAP | Topological | Local + global structure | ✗ | O(n log n) approx. |

---

## Classical MDS

Classical multidimensional scaling (also known as **principal coordinates analysis**) takes a
symmetric matrix of pairwise dissimilarities and finds a low-dimensional point configuration
whose Euclidean distances best approximate those dissimilarities. When the input dissimilarities
*are* Euclidean distances, MDS produces the same result as PCA.

The algorithm:
1. Double-centers the squared dissimilarity matrix to obtain the Gram matrix **B**.
2. Computes the top-*d* eigenvalue decomposition of **B**.
3. Scales eigenvectors by the square root of their eigenvalues.

**Positive semi-definite correction**: If `positive = true`, MDS estimates and adds an additive
constant *c* to all off-diagonal entries so that the resulting Gram matrix is positive
semi-definite. This is useful when the dissimilarities are measured on an interval scale.

### MDS Options

| Parameter | Property key | Default | Description |
|---|---|---|---|
| `d` | `smile.mds.d` | `2` | Embedding dimension (≥ 2) |
| `positive` | `smile.mds.positive` | `false` | Estimate additive constant to make Gram matrix PSD |

### MDS Example

```java
import smile.manifold.MDS;
import smile.datasets.Eurodist;

// Load a distance matrix (European city distances)
var euro = new Eurodist();
double[][] proximity = euro.x();

// Default: 2-D embedding, no PSD correction
MDS mds = MDS.fit(proximity);
System.out.println("Eigenvalues:  " + Arrays.toString(mds.scores()));
System.out.println("Proportions:  " + Arrays.toString(mds.proportion()));
double[][] coords = mds.coordinates(); // [n][2]

// 3-D embedding with PSD correction
MDS mds3d = MDS.fit(proximity, new MDS.Options(3, true));
```

The `proportion` array gives the fraction of total variance explained by each dimension —
useful for deciding how many dimensions to retain.

---

## Non-metric MDS (Isotonic MDS)

Kruskal's non-metric MDS only requires that the **rank order** of dissimilarities be
preserved in the embedding — not their exact magnitudes. This makes it more robust to
non-Euclidean or ordinal dissimilarities.

The algorithm minimizes the *stress* function:

```
stress = sqrt( Σ(d_ij - d̂_ij)² / Σ d_ij² )
```

where *d̂_ij* are isotonic regression fitted values (non-decreasing monotone fit of the
configuration distances to the observed ranks). Minimization uses L-BFGS (with BFGS
as fallback), initialized from a classical MDS solution.

Kruskal's rules of thumb for stress:
- ≤ 0.025 → excellent fit
- ≤ 0.05  → good fit
- ≤ 0.10  → fair fit
- > 0.10  → poor fit

### IsotonicMDS Options

| Parameter | Property key | Default | Description |
|---|---|---|---|
| `d` | `smile.isotonic_mds.d` | `2` | Embedding dimension (≥ 2) |
| `tol` | `smile.isotonic_mds.tolerance` | `1E-4` | Convergence tolerance |
| `maxIter` | `smile.isotonic_mds.iterations` | `200` | Maximum L-BFGS iterations |

### IsotonicMDS Example

```java
import smile.manifold.IsotonicMDS;

double[][] proximity = ...; // symmetric dissimilarity matrix

// Default options
IsotonicMDS result = IsotonicMDS.fit(proximity);
System.out.printf("Stress: %.4f%n", result.stress());
double[][] coords = result.coordinates();

// Custom options: 3-D, tighter tolerance, more iterations
IsotonicMDS result3d = IsotonicMDS.fit(proximity,
        new IsotonicMDS.Options(3, 1E-5, 500));

// Warm-start from custom initial coordinates
double[][] init = MDS.fit(proximity).coordinates();
IsotonicMDS warm = IsotonicMDS.fit(proximity, init, new IsotonicMDS.Options());
```

---

## Sammon Mapping

Sammon's mapping is an iterative stress-minimization technique that places greater emphasis
on *small* pairwise distances. The objective is:

```
stress = (1 / Σ d*_ij) · Σ (d*_ij − d_ij)² / d*_ij
```

where *d*_ij* are input dissimilarities and *d_ij* are current embedding distances.
Dividing each squared error by *d*_ij* makes errors in small distances proportionally
more important than errors in large distances, helping to preserve local topology.

The optimizer is a diagonal Newton (steepest-descent with step-size adaptation):
the step grows by 1.5× when progress is made and shrinks by 5× when the stress
increases.

### SammonMapping Options

| Parameter | Property key | Default | Description |
|---|---|---|---|
| `d` | `smile.sammon.d` | `2` | Embedding dimension (≥ 2) |
| `step` | `smile.sammon.step` | `0.2` | Initial step size (≥ 0) |
| `maxIter` | `smile.sammon.iterations` | `100` | Maximum iterations |
| `tol` | `smile.sammon.tolerance` | `1E-4` | Convergence tolerance (stress change per 10 iters) |
| `stepTol` | `smile.sammon.step_tolerance` | `1E-3` | Early-stop if step shrinks below this |

### SammonMapping Example

```java
import smile.manifold.SammonMapping;

double[][] proximity = ...; // symmetric dissimilarity matrix

// Default: 2-D, step=0.2, maxIter=100
SammonMapping sm = SammonMapping.fit(proximity);
System.out.printf("Stress: %.4f%n", sm.stress());
double[][] coords = sm.coordinates();

// Custom options
SammonMapping sm3d = SammonMapping.fit(proximity,
        new SammonMapping.Options(3, 0.3, 200));

// Warm-start from an MDS solution (the coordinates array is mutated in-place)
double[][] init = MDS.fit(proximity).coordinates().clone();
SammonMapping warm = SammonMapping.fit(proximity, init,
        new SammonMapping.Options(2, 0.2, 100));
```
> **Note:** The 3-argument overload `fit(proximity, coordinates, options)` mutates the
> `coordinates` array in-place during optimization. Pass a defensive copy if you need
> to preserve the initial layout.

---

## Isomap

Isomap extends classical MDS by replacing straight-line Euclidean distances with **geodesic
distances** — shortest-path distances along the nearest-neighbor graph. This lets Isomap
unfold curved manifolds (like the Swiss roll) that MDS would distort.

Steps:
1. Build a k-nearest-neighbor graph.
2. Compute all-pairs shortest paths (Dijkstra).
3. Run classical MDS on the geodesic distance matrix.

**C-Isomap** (`conformal = true`, the default): edge weights in the NNG are divided by
the geometric mean of the average neighborhood distances of each endpoint, magnifying
regions of high density and shrinking regions of low density. This can produce more
uniform embeddings on non-uniformly sampled manifolds.

**Disconnected graphs**: Only the largest connected component of the NNG is embedded.
If your data splits into multiple components, increase *k* or pre-process the data.

### IsoMap Options

| Parameter | Property key | Default | Description |
|---|---|---|---|
| `k` | `smile.isomap.k` | `7` | Number of nearest neighbors (≥ 2) |
| `d` | `smile.isomap.d` | `2` | Embedding dimension (≥ 2) |
| `conformal` | `smile.isomap.conformal` | `true` | Use C-Isomap (conformal) variant |

### IsoMap Example

```java
import smile.manifold.IsoMap;
import smile.datasets.SwissRoll;
import java.util.Arrays;

var roll = new SwissRoll();
double[][] data = Arrays.copyOf(roll.data(), 1000);

// Standard Isomap (conformal = false)
double[][] coords = IsoMap.fit(data, new IsoMap.Options(7, 2, false));

// C-Isomap (conformal variant, default)
double[][] ccoords = IsoMap.fit(data, new IsoMap.Options(7));

// With a custom distance function
import smile.math.distance.ManhattanDistance;
double[][] embed = IsoMap.fit(data, new ManhattanDistance(), new IsoMap.Options(7));

// From a pre-built nearest-neighbor graph (reuse across algorithms)
import smile.graph.NearestNeighborGraph;
NearestNeighborGraph nng = NearestNeighborGraph.of(data, 7);
double[][] fromNng = IsoMap.fit(nng, new IsoMap.Options(7));
```

---

## Locally Linear Embedding (LLE)

LLE assumes that each point and its neighbors lie on (or near) a locally linear patch of
the manifold. The algorithm has three stages:

1. **Find neighbors**: Build k-NN graph.
2. **Compute reconstruction weights**: For each point *i*, find weights *w_ij* such that
   *x_i ≈ Σ_j w_ij x_j* (linear combination of neighbors), by solving a small least-squares system.
3. **Find embedding**: Find coordinates *y_i* in low-dimensional space that minimize
   *Σ_i || y_i − Σ_j w_ij y_j ||²*, i.e., the same linear relationships hold in the embedding.
   This is the bottom eigenvectors of the sparse matrix *(I − W)ᵀ(I − W)*.

**Regularization**: When k > D (ambient dimension of data), the local Gram matrix is
rank-deficient. LLE automatically adds a trace-proportional regularization term
(factor = 1e-3) in this case.

**Limitation**: LLE handles non-uniform density poorly. Consider Laplacian Eigenmaps or
UMAP if your data has varying density.

### LLE Options

| Parameter | Property key | Default | Description |
|---|---|---|---|
| `k` | `smile.lle.k` | `7` | Number of nearest neighbors (≥ 2) |
| `d` | `smile.lle.d` | `2` | Embedding dimension (≥ 2) |

### LLE Example

```java
import smile.manifold.LLE;
import smile.datasets.SwissRoll;
import java.util.Arrays;

var roll = new SwissRoll();
double[][] data = Arrays.copyOf(roll.data(), 1000);

// Default: k=7, d=2
double[][] coords = LLE.fit(data, new LLE.Options(7));

// 3-D embedding with k=12
double[][] coords3d = LLE.fit(data, new LLE.Options(12, 3));

// From a pre-built NNG (useful when reusing the graph for multiple algorithms)
import smile.graph.NearestNeighborGraph;
NearestNeighborGraph nng = NearestNeighborGraph.of(data, 7);
double[][] fromNng = LLE.fit(data, nng, 2);
```

---

## Laplacian Eigenmaps

Laplacian Eigenmaps constructs a low-dimensional embedding that optimally preserves local
neighborhood information in a graph-Laplacian sense. Points that are connected in the
neighbor graph are penalized for being far apart in the embedding.

Two weighting schemes are supported:

- **Discrete weights** (`t ≤ 0`): All edges in the k-NN graph have weight 1.
- **Heat kernel weights** (`t > 0`): Edge weight = exp(−||x_i − x_j||² / t). Larger *t*
  gives more uniform weights; smaller *t* emphasizes only very close neighbors.

The algorithm solves a generalized eigenproblem for the normalized graph Laplacian
**L = I − D^(−1/2) W D^(−1/2)**, computing the *d+1* smallest eigenvectors and
discarding the trivial constant eigenvector.

Laplacian Eigenmaps is closely related to spectral clustering and diffusion maps.
It is relatively insensitive to outliers because only local distances matter.

### LaplacianEigenmap Options

| Parameter | Property key | Default | Description |
|---|---|---|---|
| `k` | `smile.laplacian_eigenmap.k` | `7` | Number of nearest neighbors (≥ 2) |
| `d` | `smile.laplacian_eigenmap.d` | `2` | Embedding dimension (≥ 2) |
| `t` | `smile.laplacian_eigenmap.t` | `-1` | Heat kernel width (≤ 0 → discrete weights) |

### LaplacianEigenmap Example

```java
import smile.manifold.LaplacianEigenmap;
import smile.datasets.SwissRoll;
import java.util.Arrays;

var roll = new SwissRoll();
double[][] data = Arrays.copyOf(roll.data(), 1000);

// Discrete weights (t <= 0, default)
double[][] discrete = LaplacianEigenmap.fit(data, new LaplacianEigenmap.Options(7));

// Heat kernel with bandwidth t = 1.0
double[][] gauss = LaplacianEigenmap.fit(data, new LaplacianEigenmap.Options(7, 2, 1.0));

// Custom distance metric
import smile.math.distance.ChebyshevDistance;
double[][] custom = LaplacianEigenmap.fit(data, new ChebyshevDistance(),
        new LaplacianEigenmap.Options(5, 2, -1));

// From a pre-built NNG
import smile.graph.NearestNeighborGraph;
NearestNeighborGraph nng = NearestNeighborGraph.of(data, 7);
double[][] fromNng = LaplacianEigenmap.fit(nng, new LaplacianEigenmap.Options(7));
```

---

## Kernel PCA (KPCA)

Kernel PCA extends classical PCA by first implicitly mapping data into a
(potentially infinite-dimensional) reproducing kernel Hilbert space using a Mercer kernel,
and then performing PCA in that feature space. This allows KPCA to discover nonlinear
structure that linear PCA cannot.

The algorithm:
1. Computes the kernel (Gram) matrix **K**, where *K_ij = k(x_i, x_j)*.
2. Double-centers **K** to remove the mean in feature space.
3. Computes the top-*d* eigenvalue decomposition of the centered **K**.
4. Projects data onto the principal components.

**Out-of-sample extension**: Unlike most other manifold methods, `KPCA` implements
`Function<T, double[]>` — you can call `kpca.apply(newPoint)` to project new data points
that were not in the training set, as long as the same kernel is used.

**Eigenvalue threshold**: Principal components whose eigenvalues are below `threshold`
are discarded automatically, even if *d* components were requested.

### KPCA Options

| Parameter | Property key | Default | Description |
|---|---|---|---|
| `d` | `smile.kpca.d` | `2` | Number of principal components to keep (≥ 2) |
| `threshold` | `smile.kpca.threshold` | `1E-4` | Eigenvalue threshold; components below this are discarded |

### KPCA Example

```java
import smile.manifold.KPCA;
import smile.math.kernel.GaussianKernel;
import smile.datasets.SwissRoll;
import java.util.Arrays;

var roll = new SwissRoll();
double[][] data = Arrays.copyOf(roll.data(), 1000);

// Gaussian (RBF) kernel with sigma=1.0, 2 components
var kernel = new GaussianKernel(1.0);
KPCA<double[]> kpca = KPCA.fit(data, kernel, new KPCA.Options(2, 1E-4));

// Embedded training data
double[][] coords = kpca.coordinates();
System.out.println("Latent values: " + Arrays.toString(kpca.latent()));

// Project a new point (out-of-sample extension)
double[] newPoint = {1.2, 3.4, 5.6};
double[] embedding = kpca.apply(newPoint);

// Other built-in kernels
import smile.math.kernel.PolynomialKernel;
import smile.math.kernel.LaplacianKernel;

KPCA<double[]> poly = KPCA.fit(data, new PolynomialKernel(3), new KPCA.Options(3));
KPCA<double[]> lap  = KPCA.fit(data, new LaplacianKernel(1.0), new KPCA.Options(2));
```

---

## t-SNE

The t-distributed Stochastic Neighbor Embedding (t-SNE) is particularly well-suited to
**visualization** of high-dimensional data in 2-D or 3-D. It models the high-dimensional
data as a Gaussian probability distribution over pairs of points, and the low-dimensional
embedding as a Student-t distribution (heavy-tailed, to avoid the "crowding problem").
The embedding minimizes the KL divergence between the two distributions via
momentum-based gradient descent with per-dimension adaptive gains.

**Input flexibility**: If the input matrix `X` is square (n×n), it is treated as a
pre-computed squared dissimilarity matrix; otherwise it is treated as raw feature vectors
and pairwise squared distances are computed internally.

**Key hyperparameters**:
- **Perplexity** (default 20): Controls the effective number of neighbors. Typical range
  5–50. Must be strictly less than *n*. Larger values create more globally coherent but
  less locally detailed embeddings.
- **Learning rate η** (default 200): Typical range 10–1000. Too high → "ball" shape;
  too low → dense cloud with few outliers.
- **Early exaggeration** (default 12): Forces clusters to form compact, well-separated
  groups in the early optimization phase. Rarely needs tuning.

**Convergence**: The momentum switches from `momentum` (0.5) to `finalMomentum` (0.8)
at `momentumSwitchIter`. After the switch, optimization stops early if either the gradient
norm falls below `tol` or no improvement has been seen for `maxIterWithoutProgress`
iterations.

### TSNE Options

| Parameter | Property key | Default | Description |
|---|---|---|---|
| `d` | `smile.t_sne.d` | `2` | Embedding dimension (≥ 2) |
| `perplexity` | `smile.t_sne.perplexity` | `20` | Effective neighborhood size (≥ 2) |
| `eta` | `smile.t_sne.eta` | `200` | Learning rate (> 0) |
| `earlyExaggeration` | `smile.t_sne.early_exaggeration` | `12` | Exaggeration in early phase (> 0) |
| `maxIter` | `smile.t_sne.iterations` | `1000` | Maximum iterations (≥ 250) |
| `maxIterWithoutProgress` | `smile.t_sne.max_iterations_without_progress` | `50` | Early-stop patience (50 ≤ value ≤ maxIter) |
| `tol` | `smile.t_sne.tolerance` | `1E-7` | Gradient norm convergence threshold (> 0) |
| `momentum` | `smile.t_sne.momentum` | `0.5` | Initial momentum (> 0) |
| `finalMomentum` | `smile.t_sne.final_momentum` | `0.8` | Momentum after switch (> 0) |
| `momentumSwitchIter` | `smile.t_sne.momentum_switch` | `min(250, maxIter-1)` | Iteration to switch momentum (< maxIter) |
| `minGain` | `smile.t_sne.min_gain` | `0.01` | Floor for per-dimension adaptive gain (> 0) |

### TSNE Example

```java
import smile.manifold.TSNE;
import smile.math.MathEx;

double[][] x = ...; // high-dimensional feature matrix [n][p]

// Fix seed for reproducibility
MathEx.setSeed(19650218);

// Quick 2-D visualization with default options
TSNE result = TSNE.fit(x);
System.out.printf("Final KL divergence: %.4f%n", result.cost());
double[][] coords = result.coordinates(); // [n][2]

// 5-arg convenience constructor: d=2, perplexity=30, eta=200, exaggeration=12, maxIter=1000
TSNE.Options opts = new TSNE.Options(2, 30, 200, 12, 1000);
TSNE result2 = TSNE.fit(x, opts);

// From a pre-computed squared distance matrix (X is square n×n)
double[][] D = new double[n][n];
MathEx.pdist(x, D, MathEx::squaredDistance);
TSNE fromDist = TSNE.fit(D, opts);

// Full control with an iterative controller for monitoring / early stop
import smile.util.IterativeAlgorithmController;
import smile.util.AlgoStatus;

var controller = new IterativeAlgorithmController<AlgoStatus>();
var fullOpts = new TSNE.Options(2, 20, 200, 12, 1000, 50, 1E-7,
        0.5, 0.8, 250, 0.01, controller);
TSNE monitored = TSNE.fit(x, fullOpts);
```
> **Note:** t-SNE is **non-deterministic** (uses Gaussian random initialization)
> and **non-parametric** (no out-of-sample extension). Re-running with the same
> data may produce a different layout. Fix `MathEx.setSeed()` before calling `fit()`
> for reproducibility.
>
> t-SNE coordinates should only be interpreted **qualitatively** — distances and
> cluster sizes in the 2-D plot do **not** correspond to true distances in the
> original space.

---

## UMAP

Uniform Manifold Approximation and Projection (UMAP) is a modern dimensionality reduction
algorithm that is fast, scalable, and preserves both **local** and **global** structure
better than t-SNE. It is based on Riemannian geometry and algebraic topology.

UMAP constructs a weighted k-NN graph that approximates the data's topological structure,
then optimizes a low-dimensional layout to minimize cross-entropy between the high- and
low-dimensional fuzzy topological representations. This is done via stochastic gradient
descent with negative sampling.

**SMILE UMAP features**:
- Euclidean distance (default) or any custom `Metric<T>`.
- Automatic epoch count based on data size (if `epochs = 0`): 500 for small datasets
  (< 10 000 points), 200 for large ones.
- Approximate NNG via `NearestNeighborGraph.descent()` for datasets > 10 000 points,
  avoiding the O(n²) exact NNG construction.

### UMAP Options

| Parameter | Property key | Default | Description |
|---|---|---|---|
| `k` | `smile.umap.k` | `15` | Nearest neighbors (≥ 2) |
| `d` | `smile.umap.d` | `2` | Embedding dimension (≥ 2) |
| `epochs` | `smile.umap.epochs` | `0` | Optimization epochs; 0 = auto (200 or 500) |
| `learningRate` | `smile.umap.learning_rate` | `1.0` | Initial learning rate (> 0) |
| `minDist` | `smile.umap.min_dist` | `0.1` | Min distance between embedded points (> 0, ≤ spread) |
| `spread` | `smile.umap.spread` | `1.0` | Scale of the embedded space |
| `negativeSamples` | `smile.umap.negative_samples` | `5` | Negative samples per positive sample (> 0) |
| `repulsionStrength` | `smile.umap.repulsion_strength` | `1.0` | Weight of repulsion forces |
| `localConnectivity` | `smile.umap.local_connectivity` | `1.0` | Required local connectivity (≥ 1) |

**Effect of key parameters:**

| Parameter | Small value | Large value |
|---|---|---|
| `k` | Fine local structure, noise-sensitive | Smooth global structure |
| `minDist` | Tight, well-separated clusters | More continuous, dispersed layout |
| `spread` | Compact embedding | Spread-out embedding |
| `negativeSamples` | Weaker repulsion, faster | Stronger repulsion, more accurate |

### UMAP Example

```java
import smile.manifold.UMAP;
import smile.math.MathEx;

double[][] x = ...; // high-dimensional feature matrix [n][p]

// Fix seed for reproducibility
MathEx.setSeed(19650218);

// Default: k=15, d=2, auto epochs
double[][] coords = UMAP.fit(x, new UMAP.Options(15));

// Tight cluster visualization
double[][] tight = UMAP.fit(x, new UMAP.Options(15, 2, 0, 1.0, 0.0, 1.0, 5, 1.0, 1.0));

// 3-D embedding
double[][] coords3d = UMAP.fit(x, new UMAP.Options(15, 3, 0, 1.0, 0.1, 1.0, 5, 1.0, 1.0));

// Custom metric (e.g. cosine distance for text/document embeddings)
import smile.math.distance.CosineDistance;
double[][] cosine = UMAP.fit(x, new CosineDistance(), new UMAP.Options(15));

// From a pre-built NNG (reuse across multiple embedding runs)
import smile.graph.NearestNeighborGraph;
NearestNeighborGraph nng = NearestNeighborGraph.of(x, 15);
double[][] fromNng = UMAP.fit(x, nng, new UMAP.Options(15));
```
> **Note:** UMAP is also **non-deterministic** due to random negative sampling.
> Fix `MathEx.setSeed()` for reproducibility.

---

## Choosing an Algorithm

```
High-dimensional data
         │
         ├── Need out-of-sample projection? ──────────► KPCA
         │
         ├── Only a dissimilarity matrix available?
         │        ├── Metric distances? ────────────────► MDS or Sammon Mapping
         │        └── Ordinal (rank order only)? ────────► Isotonic MDS
         │
         └── Raw feature vectors:
                  │
                  ├── Primary goal: visualization (2-D / 3-D)?
                  │        ├── Large data or speed needed? ──► UMAP
                  │        └── Highest perceptual quality? ───► t-SNE
                  │
                  └── Primary goal: topology / downstream ML?
                           ├── Preserve global geodesic paths? ──► IsoMap
                           ├── Preserve local linear structure? ──► LLE
                           ├── Graph-Laplacian smoothness? ────────► Laplacian Eigenmaps
                           └── Kernel-defined similarity? ─────────► KPCA
```

---

## Tips and Best Practices

### Pre-processing
- **Normalize features** before applying any manifold algorithm. Standardize to zero mean
  and unit variance (or use `WinsorScaler`) so that no single feature dominates distances.
- For images or other high-dimensional data, a PCA pre-reduction to 50–100 dimensions
  before t-SNE or UMAP speeds up computation significantly without meaningful loss.

### Choosing k (neighborhood size)
- Smaller k → finer local structure, but more sensitive to noise and outliers.
- Larger k → smoother, more globally coherent, but can bridge distinct clusters.
- Suggested starting ranges:
  - LLE, Laplacian Eigenmaps: 5–15
  - IsoMap: 7–15
  - UMAP: 10–30

### t-SNE perplexity
- Rule of thumb: perplexity ≈ sqrt(n) as a starting point.
- Always verify `perplexity < n`.
- Try several values; different perplexities reveal structure at different scales.

### UMAP minDist
- For cluster visualization: `minDist = 0.0` or `0.1` (tightly packed points).
- For continuous structure or trajectory analysis: `minDist = 0.3` or higher.

### Disconnected graphs (IsoMap, LLE, Laplacian Eigenmaps)
- If k is too small, the nearest-neighbor graph may become disconnected.
  SMILE automatically uses only the largest connected component.
- Increase k or inspect isolated points / sub-clusters.

### Reproducibility
```java
smile.math.MathEx.setSeed(42L);   // fix RNG before calling fit()
```
t-SNE and UMAP use random initialization; fixing the seed gives reproducible layouts.

### Hyperparameter search
Use `smile.hpo.Hyperparameters` to sweep over options efficiently:

```java
import smile.hpo.Hyperparameters;

var hp = new Hyperparameters()
        .add("smile.umap.k",        new int[]{10, 15, 20, 30})
        .add("smile.umap.min_dist", new double[]{0.01, 0.1, 0.5});

hp.grid().forEach(props -> {
    UMAP.Options opts = UMAP.Options.of(props);
    double[][] coords = UMAP.fit(data, opts);
    // evaluate embedding quality ...
    System.out.println(props);
});
```

---

## API Reference

### `MDS` record
| Method | Description |
|---|---|
| `static MDS fit(double[][] proximity)` | 2-D MDS with default options |
| `static MDS fit(double[][] proximity, Options options)` | Full control |
| `double[] scores()` | Eigenvalues of the top-*d* components |
| `double[] proportion()` | Fraction of total variance in each component |
| `double[][] coordinates()` | Embedded coordinates `[n][d]` |

### `IsotonicMDS` record
| Method | Description |
|---|---|
| `static IsotonicMDS fit(double[][] proximity)` | 2-D non-metric MDS with defaults |
| `static IsotonicMDS fit(double[][] proximity, Options options)` | Full control |
| `static IsotonicMDS fit(double[][] proximity, double[][] init, Options options)` | Warm start |
| `double stress()` | Final normalized Kruskal stress |
| `double[][] coordinates()` | Embedded coordinates `[n][d]` |

### `SammonMapping` record
| Method | Description |
|---|---|
| `static SammonMapping fit(double[][] proximity)` | 2-D Sammon mapping with defaults |
| `static SammonMapping fit(double[][] proximity, Options options)` | Full control |
| `static SammonMapping fit(double[][] proximity, double[][] coordinates, Options options)` | Warm start (mutates `coordinates` in-place) |
| `double stress()` | Final Sammon stress (normalized) |
| `double[][] coordinates()` | Embedded coordinates `[n][d]` |

### `IsoMap` class (static factory)
| Method | Description |
|---|---|
| `static double[][] fit(double[][] data, Options options)` | Euclidean Isomap |
| `static <T> double[][] fit(T[] data, Distance<T> distance, Options options)` | Custom distance |
| `static double[][] fit(NearestNeighborGraph nng, Options options)` | From pre-built graph |

### `LLE` class (static factory)
| Method | Description |
|---|---|
| `static double[][] fit(double[][] data, Options options)` | Standard LLE |
| `static double[][] fit(double[][] data, NearestNeighborGraph nng, int d)` | From pre-built graph |

### `LaplacianEigenmap` class (static factory)
| Method | Description |
|---|---|
| `static double[][] fit(double[][] data, Options options)` | Euclidean Laplacian Eigenmaps |
| `static <T> double[][] fit(T[] data, Distance<T> distance, Options options)` | Custom distance |
| `static double[][] fit(NearestNeighborGraph nng, Options options)` | From pre-built graph |

### `KPCA<T>` class
| Method | Description |
|---|---|
| `static <T> KPCA<T> fit(T[] data, MercerKernel<T> kernel, Options options)` | Fit KPCA |
| `double[] apply(T x)` | Project a new point (out-of-sample extension) |
| `double[][] coordinates()` | Embedded training coordinates `[n][d]` |
| `double[] latent()` | Eigenvalues of kernel principal components |

### `TSNE` record
| Method | Description |
|---|---|
| `static TSNE fit(double[][] X)` | 2-D t-SNE with default options |
| `static TSNE fit(double[][] X, Options options)` | Full control |
| `double cost()` | Final KL divergence |
| `double[][] coordinates()` | Embedded coordinates `[n][d]` |

### `UMAP` class (static factory)
| Method | Description |
|---|---|
| `static double[][] fit(double[][] data, Options options)` | Euclidean UMAP |
| `static <T> double[][] fit(T[] data, Metric<T> distance, Options options)` | Custom metric |
| `static double[][] fit(double[][] data, NearestNeighborGraph nng, Options options)` | From pre-built graph |

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
