# SMILE Base Module

`smile-base` is the foundation module of the [SMILE](https://haifengl.github.io)
(Statistical Machine Intelligence and Learning Engine) library.
It provides all the data structures, mathematical primitives,
statistical utilities, and I/O facilities that the rest of SMILE
is built upon.

---

## Package Map

| Package | Description                                                                      |
|---------|----------------------------------------------------------------------------------|
| `smile.data` | [DataFrame, Tuple, type system, measures, vectors](#data)                        |
| `smile.data.formula` | [Formula language for model matrices](#formula)                                  |
| `smile.data.transform` | [Feature-transformation pipelines](#data-transformation)                         |
| `smile.datasets` | [Built-in benchmark datasets](#datasets)                                         |
| `smile.io` | [Data I/O — CSV, JSON, Parquet, ARFF, …](#data-io)                               |
| `smile.tensor` | [Tensors and dense/sparse linear algebra](#tensor--linear-algebra)               |
| `smile.math` | [Core math utilities, MathEx, special functions](#math)                          |
| `smile.math.distance` | [Distance and metric functions](#distances)                                      |
| `smile.math.kernel` | [Mercer kernel functions (SVM, GP, …)](#kernels)                                 |
| `smile.math.rbf` | [Radial basis functions](#radial-basis-functions)                                |
| `smile.math.random` | [Pseudorandom number generators](#random-number-generators)                      |
| `smile.math.BFGS` | [Quasi-Newton BFGS / L-BFGS optimization](#bfgs-optimisation)                   |
| `smile.cs` | [Compressed sensing](#compressed-sensing)                                        |
| `smile.stat.distribution` | [Probability distributions](#probability-distributions)                          |
| `smile.stat.hypothesis` | [Hypothesis testing](#hypothesis-testing)                                        |
| `smile.interpolation` | [1-D and 2-D interpolation](#interpolation)                                      |
| `smile.neighbor` | [Nearest-neighbor search (KD-tree, ball tree, LSH, …)](#nearest-neighbor-search) |
| `smile.graph` | [Graph data structures and algorithms](#graph)                                   |
| `smile.gap` | [Genetic algorithm (GAP framework)](#genetic-algorithm)                          |
| `smile.ica` | [Independent component analysis](#independent-component-analysis)                |
| `smile.hash` | [Non-cryptographic hash functions](#hash-functions)                              |
| `smile.sort` | [Sorting and selection algorithms](#sorting--selection)                          |
| `smile.wavelet` | [Wavelet transforms](#wavelets)                                                  |

---

## Data

**Package:** `smile.data`, `smile.data.type`, `smile.data.measure`, `smile.data.vector`  
**Guide:** [DATA_FRAME.md](DATA_FRAME.md)

`DataFrame` is SMILE's primary in-memory tabular data structure — a typed,
column-oriented table with named fields, rich type metadata, and a streaming
API.  `Tuple` is a single row of a `DataFrame`.  The `smile.data.type` package
defines a complete type system (`DataType`, `StructType`, …), and
`smile.data.measure` provides nominal, ordinal, interval, and ratio measurement
scales.  Strongly-typed column vectors (`IntVector`, `DoubleVector`,
`StringVector`, …) live in `smile.data.vector`.

```java
DataFrame iris = Read.arff(Paths.getTestData("weka/iris.arff"));
DataFrame subset = iris.select("sepallength", "sepalwidth", "class");
double[] lengths = iris.column("sepallength").toDoubleArray();
```

---

## Formula

**Package:** `smile.data.formula`  
**Guide:** [FORMULA.md](FORMULA.md)

A compact, symbolic language for specifying model matrices (design matrices)
from a `DataFrame`.  Inspired by R's formula notation.

```java
// All predictors except class
Formula f = Formula.lhs("class");

// Explicit terms with an interaction
Formula f = Formula.of("y", "x1", "x2", "x1:x2");

double[][] X = f.x(dataFrame).toArray();
double[]   y = f.y(dataFrame).toDoubleArray();
```

---

## Data Transformation

**Package:** `smile.data.transform`  
**Guide:** [DATA_TRANSFORMATION.md](DATA_TRANSFORMATION.md)

Composable, serializable feature-transformation pipelines.  Each transformer
is fitted on training data and then applied consistently to new data.
Includes scaling (`Standardizer`, `MaxAbsScaler`, `MinMaxScaler`),
encoding (`OneHotEncoder`, `KBinsDiscretizer`), imputation, and more.

```java
var scaler = Standardizer.fit(trainDF);
DataFrame normalized = scaler.apply(trainDF);
DataFrame normalizedTest = scaler.apply(testDF);
```

---

## Datasets

**Package:** `smile.datasets`  
**Guide:** [DATASET.md](DATASET.md)

Ready-to-use loaders for over 30 standard machine-learning benchmark datasets
(Iris, MNIST, USPS, Breast Cancer, CPU, Abalone, …).  Each loader returns
structured data suitable for immediate use with SMILE classifiers, regressors,
or clustering algorithms.

```java
var iris      = Iris.load();
var mnist     = MNIST.load();
var breastCancer = BreastCancer.load();
```

---

## Data I/O

**Package:** `smile.io`  
**Guide:** [DATA_IO.md](DATA_IO.md)

Unified read/write API for many tabular and hierarchical formats:

| Format | Read | Write |
|--------|:----:|:-----:|
| CSV / TSV / DSV | ✓ | ✓ |
| JSON (row-oriented) | ✓ | ✓ |
| Apache Parquet | ✓ | ✓ |
| Apache Avro | ✓ | — |
| Apache Arrow | ✓ | ✓ |
| Weka ARFF | ✓ | — |
| LibSVM sparse | ✓ | — |
| Matrix Market | ✓ | — |

```java
DataFrame df = Read.csv("data.csv");
DataFrame df = Read.parquet("data.parquet");
Write.csv(df, Paths.get("out.csv"));
```

---

## Tensor & Linear Algebra

**Package:** `smile.tensor`, `smile.linalg`  
**Guide:** [TENSOR.md](TENSOR.md)

Core numerical data structures used throughout SMILE:

- **`DenseMatrix`** — double-precision dense matrix with BLAS/LAPACK backend
- **`SparseMatrix`** — CSR compressed sparse matrix
- **`BandMatrix`**, **`SymmMatrix`**, **`Cholesky`**, **`LU`**, **`QR`**, **`SVD`**, **`EVD`** — decompositions
- **`IMatrix`** / **`FloatMatrix`** — integer and single-precision variants
- **N-D `Tensor`** — general multi-dimensional array (float32 / float64)

```java
DenseMatrix A = DenseMatrix.of(new double[][]{{1,2},{3,4}});
DenseMatrix B = A.mm(A);          // matrix multiply
var svd = A.svd();                // full SVD
double[] x = A.solve(b);         // least-squares solve
```

---

## Math

**Package:** `smile.math`  

`MathEx` is a large static utility class with fast implementations of
common mathematical operations: log-sum-exp, softmax, entropy, distance
helpers, combinatorics, special functions (gamma, beta, erf, …), and
more. `smile.math.special` provides the underlying special-function
implementations.

```java
double lse = MathEx.logSumExp(logProbs);
MathEx.softmax(scores);
double h = MathEx.entropy(probs);
```

---

## Distances

**Package:** `smile.math.distance`  
**Guide:** [DISTANCES.md](DISTANCES.md)

A rich collection of distance and similarity metrics implementing the
`Distance<T>` interface:

| Metric | Class |
|--------|-------|
| Euclidean | `EuclideanDistance` |
| Manhattan (L1) | `ManhattanDistance` |
| Chebyshev (L∞) | `ChebyshevDistance` |
| Minkowski (Lp) | `MinkowskiDistance` |
| Cosine | `CosineDistance` |
| Mahalanobis | `MahalanobisDistance` |
| Hamming | `HammingDistance` |
| Levenshtein (edit) | `EditDistance` |
| Jaccard | `JaccardDistance` |
| Dynamic Time Warping | `DynamicTimeWarping` |
| Sparse vectors | `SparseEuclideanDistance`, `SparseManhattanDistance`, … |

---

## Kernels

**Package:** `smile.math.kernel`  
**Guide:** [KERNELS.md](KERNELS.md)

Mercer kernel functions used in support vector machines, Gaussian processes,
and kernel methods.  All implement `MercerKernel<T>`.

| Kernel | Class |
|--------|-------|
| Linear | `LinearKernel` |
| Polynomial | `PolynomialKernel` |
| Gaussian (RBF) | `GaussianKernel` |
| Laplacian | `LaplacianKernel` |
| Matérn 3/2, 5/2 | `Matern32Kernel`, `Matern52Kernel` |
| Hyperbolic Tangent | `HyperbolicTangentKernel` |
| Sparse variants | `SparseGaussianKernel`, `SparseLaplacianKernel`, … |

---

## Radial Basis Functions

**Package:** `smile.math.rbf`  
**Guide:** [RBF.md](RBF.md)

Radial basis functions used for interpolation, RBF networks, and other
kernel methods.

| RBF | Class |
|-----|-------|
| Gaussian | `GaussianRadialBasis` |
| Multiquadric | `MultiquadricRadialBasis` |
| Inverse Multiquadric | `InverseMultiquadricRadialBasis` |
| Thin-plate spline | `ThinPlateSplineRadialBasis` |

---

## Random Number Generators

**Package:** `smile.math.random`  
**Guide:** [RNG.md](RNG.md)

High-quality pseudorandom number generators and distributions:

- **`MersenneTwister`** — MT19937, the default general-purpose PRNG
- **`XoShiRo256StarStar`**, **`XoRoShiRo128StarStar`** — fast modern RNGs
- **`Halton`**, **`SobolSequence`** — low-discrepancy quasi-random sequences

```java
var rng = new MersenneTwister(42);
double u = rng.nextDouble();
int[] perm = rng.permutation(100);
```

---

## BFGS Optimization

**Package:** `smile.math`  
**Guide:** [BFGS.md](BFGS.md)

Quasi-Newton BFGS and L-BFGS unconstrained optimisers.  The `BFGS` class
minimises a smooth, differentiable objective function given its gradient.
L-BFGS uses a limited-memory approximation suitable for high-dimensional
problems.

```java
double[] x0 = {0.0, 0.0};
double[] xMin = BFGS.minimize(f, g, x0, 1e-6, 200);
```

---

## Compressed Sensing

**Package:** `smile.cs`  
**Guide:** [COMPRESSED_SENSING.md](COMPRESSED_SENSING.md)

Sparse signal recovery from underdetermined linear measurements using the
Basis Pursuit (BP) and LASSO formulations.  Useful for compressive imaging,
sparse regression, and dictionary learning.

---

## Probability Distributions

**Package:** `smile.stat.distribution`  
**Guide:** [DISTRIBUTIONS.md](DISTRIBUTIONS.md)

A comprehensive library of univariate and multivariate distributions, each
implementing `Distribution` (or `DiscreteDistribution`).  Every distribution
supports PDF/PMF, CDF, quantile (inverse CDF), mean, variance, entropy, and
random sampling.

**Continuous:** `GaussianDistribution`, `ExponentialDistribution`,
`GammaDistribution`, `BetaDistribution`, `WeibullDistribution`,
`LogNormalDistribution`, `TDistribution`, `FDistribution`,
`ChiSquaredDistribution`, `CauchyDistribution`, `LogisticDistribution`,
`UniformDistribution`, `EmpiricalDistribution`, `KernelDensityEstimation`, …

**Discrete:** `BinomialDistribution`, `PoissonDistribution`,
`NegativeBinomialDistribution`, `GeometricDistribution`,
`HypergeometricDistribution`, `DiscreteUniformDistribution`, …

**Multivariate:** `MultivariateGaussianDistribution`,
`DirichletDistribution`, `MultivariateExponentialFamilyMixture`, …

```java
var gauss = new GaussianDistribution(0, 1);
double p = gauss.cdf(1.96);       // ≈ 0.975
double x = gauss.quantile(0.975); // ≈ 1.96
double[] samples = gauss.rand(1000);
```

---

## Hypothesis Testing

**Package:** `smile.stat.hypothesis`  
**Guide:** [HYPOTHESIS_TESTING.md](HYPOTHESIS_TESTING.md)

Parametric and non-parametric tests for comparing means, variances,
distributions, and correlations.

| Test | Class / Method |
|------|---------------|
| One-sample t-test | `TTest.test(double[] x, double μ)` |
| Two-sample t-test | `TTest.test(double[] x, double[] y)` |
| Paired t-test | `TTest.pairedTest(double[] x, double[] y)` |
| F-test (variance) | `FTest.test(double[] x, double[] y)` |
| Chi-squared goodness-of-fit | `ChiSqTest.test(int[] counts, double[] prob)` |
| Chi-squared independence | `ChiSqTest.test(int[][] table)` |
| Kolmogorov-Smirnov | `KSTest.test(double[] x, Distribution d)` |
| Two-sample KS | `KSTest.test(double[] x, double[] y)` |
| Spearman correlation | `SpearmanTest` |
| Kendall's τ | `KendallTest` |

All test objects expose a `p-value` field for decision making.

---

## Interpolation

**Package:** `smile.interpolation`  
**Guide:** [INTERPOLATION.md](INTERPOLATION.md)

Smooth function reconstruction from a discrete set of sample points.

**1-D:** `LinearInterpolation`, `PolynomialInterpolation`,
`SplineInterpolation` (natural cubic spline), `CubicSplineInterpolation`,
`RBFInterpolation1D`, `KrigingInterpolation1D`

**2-D:** `BilinearInterpolation`, `BicubicInterpolation`,
`CubicSplineInterpolation2D`, `RBFInterpolation2D`,
`KrigingInterpolation2D`, `ShepardInterpolation2D`

```java
double[] x = {0, 1, 2, 3};
double[] y = {0, 1, 4, 9};
var spline = new CubicSplineInterpolation(x, y);
double v = spline.interpolate(1.5);   // ≈ 2.25
```

---

## Nearest-Neighbor Search

**Package:** `smile.neighbor`  
**Guide:** [NEAREST_NEIGHBOR.md](NEAREST_NEIGHBOR.md)

Exact and approximate nearest-neighbor data structures.

| Structure | Class | Best for |
|-----------|-------|----------|
| KD-tree | `KDTree` | Low-dimensional Euclidean data |
| Ball tree | `BallTree` | Arbitrary metric spaces |
| Cover tree | `CoverTree` | Expandable, metric spaces |
| LSH | `LSH` | High-dimensional approximate NN |
| MPLSH | `MPLSH` | Multi-probe LSH |

All implement `NearestNeighborSearch<K,V>` and support k-NN and
range queries.

```java
var kdtree = KDTree.of(data);
Neighbor<double[], double[]>[] nn = kdtree.knn(query, 5);
```

---

## Graph

**Package:** `smile.graph`  
**Guide:** [GRAPH.md](GRAPH.md)

Directed and undirected weighted graphs with adjacency-list and
adjacency-matrix representations, plus classic graph algorithms.

- **`AdjacencyList`** — sparse graph (recommended for most uses)
- **`AdjacencyMatrix`** — dense graph
- **Algorithms:** BFS, DFS, topological sort, shortest path (Dijkstra,
  Bellman-Ford), minimum spanning tree (Prim, Kruskal), strongly connected
  components (Tarjan)

```java
var g = new AdjacencyList(6, false);  // 6 vertices, undirected
g.addEdge(0, 1, 1.0);
int[] order = g.bfs(0);
double[] dist = g.dijkstra(0);
```

---

## Genetic Algorithm

**Package:** `smile.gap`  
**Guide:** [GAP.md](GAP.md)

A flexible genetic algorithm framework (GAP — Genetic Algorithm Platform)
for combinatorial and continuous optimization.  Users implement a
`Chromosome` with `fitness()`, `crossover()`, and `mutate()` to define
the problem; the `GeneticAlgorithm` driver handles selection, recombination,
and termination.

```java
var ga = new GeneticAlgorithm<>(population, 0.5, 0.01);
Chromosome best = ga.evolve(1000, 1e-4);
```

---

## Independent Component Analysis

**Package:** `smile.ica`  
**Guide:** [ICA.md](ICA.md)

FastICA for blind source separation.  Recovers statistically independent
components from a linear mixture of signals — widely used in signal
processing, fMRI analysis, and feature extraction.

```java
var ica = ICA.fit(X, 3);   // extract 3 independent components
double[][] S = ica.transform(X);
```

---

## Hash Functions

**Package:** `smile.hash`  
**Guide:** [HASH.md](HASH.md)

High-performance, non-cryptographic hash functions and locality-sensitive
hashing (LSH) families.

| Hash | Class | Notes |
|------|-------|-------|
| MurmurHash3 | `MurmurHash3` | 32-bit / 128-bit; fast, high quality |
| SHA-1 | `SHA` | Cryptographic (SHA-1 used for LSH) |
| MinHash | `MinHash` | Jaccard similarity LSH |
| SimHash | `SimHash` | Cosine similarity LSH |
| Random projection LSH | `RandomProjectionHash` | Euclidean LSH |
| Cross-polytope LSH | `CrossPolytope` | Efficient Euclidean LSH |

---

## Sorting & Selection

**Package:** `smile.sort`  
**Guide:** [SORT.md](SORT.md)

Allocation-free, cache-friendly sorting and selection algorithms used
internally throughout SMILE.

| Algorithm | Class / Method |
|-----------|---------------|
| Introsort (O(n log n)) | `IntroSort` |
| Heap sort | `HeapSort` |
| Shell sort | `ShellSort` |
| Quickselect (k-th element) | `QuickSelect` |
| Indexed sort | `QuickSort` (with index array) |
| Partition | `QuickSelect.select(a, k)` |

---

## Wavelets

**Package:** `smile.wavelet`  
**Guide:** [WAVELET.md](WAVELET.md)

Discrete Wavelet Transform (DWT) and Wavelet Packet Transform (WPT) for
signal processing, compression, and multi-resolution analysis.

**Families:** Haar, Daubechies (D4–D20), Symlet (S8–S20), Coiflet (C6–C30),
BestLocalized (BL14–BL20), Vaidyanathan.

```java
double[] signal = ...;
var wavelet = new DaubechiesWavelet(4);   // D4
wavelet.transform(signal);               // in-place DWT
wavelet.inverse(signal);                 // reconstruction
```

---

## Building

```bash
# Build the module
./gradlew :base:build

# Run tests
./gradlew :base:test

# Generate Javadoc
./gradlew :base:javadoc
```

Dependencies: Java 25+, BLAS/LAPACK native libraries (optional — pure-Java
fallback included).


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

