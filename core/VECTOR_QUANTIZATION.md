# SMILE — Vector Quantization

## Table of Contents

1. [Overview](#1-overview)
2. [The `VectorQuantizer` Interface](#2-the-vectorquantizer-interface)
3. [BIRCH — CF-Tree Quantizer](#3-birch--cf-tree-quantizer)
4. [Neural Gas](#4-neural-gas)
5. [Growing Neural Gas](#5-growing-neural-gas)
6. [NeuralMap](#6-neuralmap)
7. [Self-Organizing Map (SOM)](#7-self-organizing-map-som)
8. [The `smile.vq.hebb` Package](#8-the-smilevqhebb-package)
9. [Neighborhood Functions (SOM)](#9-neighborhood-functions-som)
10. [Algorithm Comparison and Selection Guide](#10-algorithm-comparison-and-selection-guide)
11. [Common Patterns and Tips](#11-common-patterns-and-tips)

---

## 1. Overview

**Vector quantization (VQ)** is a lossy compression technique that maps an input vector to the nearest element of a finite set of *code vectors* (also called *prototypes* or *centroids*). Each algorithm in `smile.vq` learns these prototypes from data through online, competitive learning: prototypes compete for each incoming signal, and the winner (or winners) are updated to better represent that signal.

All algorithms in this package implement the `VectorQuantizer` interface and support incremental (online) learning via `update(double[] x)`. This makes them suitable for streaming data, large datasets that do not fit in memory, or scenarios where the data distribution may shift over time.

The package contains five quantizer implementations:

| Class | Topology | Fixed size? | Key strength |
|---|---|---|---|
| `BIRCH` | CF-tree (hierarchical) | No | Fast, memory-bounded, handles very large datasets |
| `NeuralGas` | None (soft competition) | Yes | Robust convergence, good coverage |
| `GrowingNeuralGas` | Graph (dynamic) | No | Adapts topology and size to data |
| `NeuralMap` | Graph (dynamic, radius-gated) | No | Memory-efficient, noise-resistant |
| `SOM` | 2-D rectangular lattice | Yes | Topology-preserving, visual interpretation |

---

## 2. The `VectorQuantizer` Interface

```java
public interface VectorQuantizer extends Serializable {
    int OUTLIER = Integer.MAX_VALUE;

    void update(double[] x);

    double[] quantize(double[] x);
}
```

### `update(double[] x)`

Presents a new data point to the model. The model updates its internal prototype(s) using competitive learning. Calling `update` repeatedly over the training set (typically for multiple epochs in random order) trains the model.

### `quantize(double[] x)`

Returns the **weight vector of the nearest prototype** for a given input. For all implementations this is an internal reference — do not modify the returned array, or you will corrupt the model state. If you need the value for further computation, copy it first:

```java
double[] proto = quantizer.quantize(x);
double[] copy = proto.clone();
```

### `OUTLIER`

The constant `Integer.MAX_VALUE` is reserved as a label for outliers or noise in higher-level clustering algorithms that build on top of VQ models. The VQ algorithms themselves do not use it internally.

### Serialization

All implementations are `Serializable`, so a trained model can be saved and restored with standard Java serialization.

---

## 3. BIRCH — CF-Tree Quantizer

### Algorithm

BIRCH (Balanced Iterative Reducing and Clustering using Hierarchies) builds a **CF-tree** — a height-balanced tree of *Clustering Features (CF)*. Each CF is a compact statistical summary `(N, LS, SS)`:

- **N**: number of data points in the sub-cluster
- **LS**: linear sum of the points (sum of each coordinate)
- **SS**: square sum of the points (sum of squared coordinates)

From these three statistics the centroid and radius of a sub-cluster can be computed exactly and cheaply. When a new data point arrives, it is absorbed into the closest leaf CF if the resulting radius stays within the threshold `T`; otherwise a new CF is created. Leaves that exceed capacity `L` are split; internal nodes that exceed branching factor `B` are likewise split, growing the tree.

`quantize` descends the tree to the nearest leaf CF and returns its centroid.

### Construction

```java
BIRCH(int d, int B, int L, double T)
```

| Parameter | Meaning | Typical range |
|---|---|---|
| `d` | Dimensionality of data | — |
| `B` | Maximum children per internal node | 5–50 |
| `L` | Maximum CFs per leaf node | 5–50 |
| `T` | Radius threshold for absorbing a point into an existing CF | data-dependent |

Constraints: `B ≥ 2`, `L ≥ 2`, `T > 0`.

The radius threshold `T` is the most important parameter. A smaller `T` creates more, finer sub-clusters; a larger `T` creates fewer, coarser ones. Inspect your data's pairwise distances to calibrate: `T` should be on the order of the expected intra-cluster spread.

### Usage

```java
// 256-dimensional data (e.g. image patches), branching=5, leaf capacity=5, radius=6.0
BIRCH birch = new BIRCH(256, 5, 5, 6.0);

// Online ingestion — single pass over training data
for (double[] xi : trainingData) {
    birch.update(xi);
}

// Quantize new points
double[] prototype = birch.quantize(queryPoint);

// Retrieve all leaf-level sub-cluster centroids for downstream clustering
double[][] centroids = birch.centroids();
```

### `centroids()`

Returns the centroid of every CF at the leaf level. These centroids can be fed into a hierarchical clustering algorithm (e.g., `smile.clustering.HierarchicalClustering`) to produce the final cluster assignments in a second pass — this is phase 2 of the original BIRCH paper.

### Key properties

- **Single-pass**: one scan through the data is enough to build the tree.
- **Memory-bounded**: tree depth is controlled by `T`, `B`, and `L`; BIRCH will never create more leaf CFs than the data variance requires given the radius constraint.
- **Insertions are O(log n)** in the height of the tree.
- `quantize` returns a **new array** (the centroid is computed on the fly from the CF statistics), so it is safe to modify.

---

## 4. Neural Gas

### Algorithm

Neural Gas (Martinetz & Schulten, 1991–1994) uses **soft competition**: for every input signal `x`, *all* neurons are ranked by distance and updated, with adaptation strength decaying exponentially with rank. The neuron closest to `x` (rank 0) moves the most; the neuron furthest away (rank N−1) moves the least. Over time, the prototypes distribute themselves to match the data density.

Additionally, a **competitive Hebbian edge** is drawn between the best-matching unit (BMU) and the second-best-matching unit at each step. These edges record which neurons are topologically adjacent in data space. Old edges can be pruned via `network()`.

The adaptation rule for rank `k` at iteration `t` is:

```
Δw_k = α(t) · exp(−k / θ(t)) · (x − w_k)
```

where `α(t)` is the learning rate and `θ(t)` is the neighbourhood width, both typically decaying over time.

### Construction

```java
NeuralGas(double[][] neurons, TimeFunction alpha, TimeFunction theta, TimeFunction lifetime)
```

| Parameter | Meaning |
|---|---|
| `neurons` | Initial weight vectors (at least 2); commonly drawn with `CentroidClustering.seeds` |
| `alpha` | Learning-rate schedule `α(t)` |
| `theta` | Neighbourhood-width schedule `θ(t)` |
| `lifetime` | Edge lifetime: edges older than `lifetime(t)` steps are zeroed by `network()` |

`TimeFunction` is a `@FunctionalInterface` in `smile.util.function`. Common schedules:

```java
TimeFunction.constant(value)              // fixed value
TimeFunction.exp(initialValue, decayTime) // exponential decay: v0 * exp(-t / T)
```

### Usage

```java
double[][] x = trainingData;
int epochs = 20;
int T = x.length * epochs;

// 400 prototypes seeded by k-means++
double[][] seeds = CentroidClustering.seeds(x, 400);
NeuralGas gas = new NeuralGas(
        seeds,
        TimeFunction.exp(0.3, T / 2.0),   // alpha: 0.3 → ~0 over half the run
        TimeFunction.exp(30,  T / 8.0),   // theta: 30 → ~0 over one eighth of run
        TimeFunction.constant(x.length * 2) // edges alive for ~2 epochs
);

for (int epoch = 0; epoch < epochs; epoch++) {
    for (int j : MathEx.permutate(x.length)) {
        gas.update(x[j]);
    }
}

// Access trained prototypes in original index order
double[][] prototypes = gas.neurons();

// Retrieve topology graph (prunes expired edges)
Graph graph = gas.network();

// Quantize
double[] nearest = gas.quantize(queryPoint);
```

### `neurons()`

Returns the weight vectors sorted by original index (the order in which they were passed to the constructor). This ordering is stable across calls.

### `network()`

Returns the `AdjacencyMatrix` of competitive Hebbian edges. Edges whose timestamp is more than `lifetime(t)` steps old have their weight zeroed. The returned graph can be used for topology analysis or to identify disconnected clusters.

### Key properties

- Fixed number of prototypes: set at construction time.
- Robust, smooth convergence due to soft competition (no sharp winner-takes-all).
- Requires schedule tuning: `alpha` and `theta` must decay to near-zero over the training run for good results.
- Parallelism: distance computation uses `IntStream.parallel()` internally.

---

## 5. Growing Neural Gas

### Algorithm

Growing Neural Gas (GNG, Fritzke 1995) extends Neural Gas with the ability to **dynamically add and remove neurons** during training. It uses hard competition (only BMU and second-best are updated) combined with competitive Hebbian edges and age-based edge pruning.

Key additions over Neural Gas:

1. **Local error accumulation**: each neuron tracks a running sum of its squared distances to signals it wins.
2. **Edge aging**: every edge emanating from the BMU is aged after each step; the edge to the second-best unit is refreshed (age=0).
3. **Edge pruning**: edges older than `edgeLifetime` are removed. Neurons that become isolated (no edges) are also removed.
4. **Neuron insertion**: every `lambda` signals, a new neuron is inserted halfway between the neuron with the highest accumulated error (`q`) and its neighbor with the highest error (`f`). The edge `(q, f)` is replaced by edges `(q, r)` and `(r, f)`.
5. **Global error decay**: all error variables are multiplied by `beta` after every signal.

### Construction

Two constructors are provided:

```java
// Default hyperparameters
GrowingNeuralGas(int d)

// Full control
GrowingNeuralGas(int d, double epsBest, double epsNeighbor,
                 int edgeLifetime, int lambda, double alpha, double beta)
```

| Parameter | Default | Meaning |
|---|---|---|
| `d` | — | Dimensionality |
| `epsBest` | 0.2 | Learning rate for BMU |
| `epsNeighbor` | 0.006 | Learning rate for BMU's neighbors |
| `edgeLifetime` | 50 | Maximum edge age before pruning |
| `lambda` | 100 | Neuron insertion interval (signals between insertions) |
| `alpha` | 0.5 | Error reduction factor for `q` and `f` after insertion |
| `beta` | 0.995 | Global error decay factor (applied after every signal) |

### Bootstrap behavior

The first two `update` calls do not trigger learning — they simply add the first two neurons. Learning begins from the third call onward. A neuron insertion at a lambda boundary is skipped if the highest-error neuron has no topological neighbors (a rare but safe condition).

### Usage

```java
GrowingNeuralGas model = new GrowingNeuralGas(x[0].length);

for (int epoch = 0; epoch < 10; epoch++) {
    for (int j : MathEx.permutate(x.length)) {
        model.update(x[j]);
    }
    System.out.printf("%d neurons after epoch %d%n", model.neurons().length, epoch + 1);
}

// Inspect the learned prototypes and topology
Neuron[] neurons = model.neurons();
for (Neuron n : neurons) {
    double[] w = n.w;        // prototype weight vector
    double error = n.counter; // accumulated local error
    List<Edge> nbrs = n.edges; // topological neighbors
}

double[] nearest = model.quantize(queryPoint);
```

### Hyperparameter guidance

| Goal | Adjustment |
|---|---|
| Fewer, coarser prototypes | Increase `lambda`, decrease `edgeLifetime` |
| More, finer prototypes | Decrease `lambda`, increase `edgeLifetime` |
| Faster convergence | Increase `epsBest` and `epsNeighbor` |
| Smoother topology | Decrease `epsBest`, increase `edgeLifetime` |
| Adapt to drifting distributions | Smaller `beta` (faster error decay), smaller `edgeLifetime` |

### Key properties

- No need to specify the number of prototypes in advance.
- Topology is encoded in the edge graph: disconnected components correspond to separate clusters.
- Uses `HeapSelect` for efficient O(n) BMU+second-best search.

---

## 6. NeuralMap

### Algorithm

NeuralMap is a hybrid algorithm combining ideas from GNG and BIRCH. Like GNG it uses competitive Hebbian learning and edge aging. Unlike GNG, neuron creation is controlled by a **distance threshold `r`** rather than a fixed insertion interval:

- If the nearest existing neuron is farther than `r`, a new neuron is created at `x`.
- If only the nearest is within `r` but not the second-nearest, a new neuron is created at `x` and connected to the nearest.
- If both the nearest and second-nearest are within `r`, the normal Hebbian update runs: the nearest neuron is moved toward `x`, its neighbors are lightly updated, the edge to the second-nearest is refreshed, and stale edges are pruned.

A **freshness counter** (updated by `counter += 1` for the winning neuron, decayed by `counter *= beta` for all neurons after each step) tracks how recently each neuron has been active. The `clear(double eps)` method removes neurons whose freshness falls below `eps` and also prunes any remaining stale edges.

### Construction

```java
NeuralMap(int d, double r, double epsBest, double epsNeighbor, int edgeLifetime, double beta)
```

| Parameter | Meaning |
|---|---|
| `d` | Dimensionality |
| `r` | Distance threshold for neuron activation |
| `epsBest` | Learning rate for the nearest neuron |
| `epsNeighbor` | Learning rate for topological neighbors |
| `edgeLifetime` | Maximum edge age before pruning |
| `beta` | Global freshness decay per step (0 < beta ≤ 1) |

### Usage

```java
// 256-dimensional signals; neurons must be within radius 8 to be activated
NeuralMap model = new NeuralMap(256, 8.0, 0.01, 0.002, 50, 0.995);

for (int epoch = 0; epoch < 5; epoch++) {
    for (int j : MathEx.permutate(x.length)) {
        model.update(x[j]);
    }
    // Prune stale neurons (freshness < 1e-7) and old edges
    model.clear(1E-7);
    System.out.printf("%d neurons after epoch %d%n", model.neurons().length, epoch + 1);
}

double[] nearest = model.quantize(queryPoint);
```

### `clear(double eps)`

Removes all neurons whose `counter < eps` AND whose edge list is empty (either because they were never connected, or all their edges were pruned). Neurons with `counter ≥ eps` also have their edges pruned of any that exceed `edgeLifetime`. Call `clear` between epochs to remove noise and keep the model compact.

**Important**: `eps` must be non-negative. Passing a negative value throws `IllegalArgumentException`.

### Key properties

- The radius `r` provides natural noise resistance: isolated spurious signals far from any existing neuron create their own neuron, but that neuron quickly becomes stale and is removed by `clear`.
- Unlike GNG, the network size is bounded by how many distinct `r`-balls cover the data — not by a fixed insertion rate.
- Suitable for streaming/online settings where the data distribution may have outliers.

---

## 7. Self-Organizing Map (SOM)

### Algorithm

The Self-Organizing Map (Kohonen, 2000) organizes a fixed set of neurons on a **2-D rectangular lattice**. Each neuron has a grid position `(i, j)` and a weight vector `w` in input space. Training proceeds as follows:

1. Find the **best matching unit (BMU)**: the neuron whose weight is closest to the input `x`.
2. Update all neurons toward `x`, with the update magnitude controlled by both the learning rate `α(t)` and a **neighborhood function** `θ(di, dj, t)` where `(di, dj)` is the lattice displacement from the BMU:

```
Δw = α(t) · θ(i − i_bmu, j − j_bmu, t) · (x − w)
```

As training progresses, `α` and the neighborhood width shrink, causing the map to first organize globally and then fine-tune locally.

The result is a map where nearby neurons respond to similar inputs — a topology-preserving projection of the input space onto the 2-D lattice.

### Construction

```java
SOM(double[][][] neurons, TimeFunction alpha, Neighborhood theta)
```

| Parameter | Meaning |
|---|---|
| `neurons` | Initial `[nrow][ncol][d]` weight array |
| `alpha` | Learning-rate schedule `α(t)` |
| `theta` | Neighborhood function |

All rows must have the same number of columns; all weight vectors must have the same length `d`. The lattice must be non-empty and have non-zero dimensionality.

### Lattice initialization with `SOM.lattice`

A good initialization seeds the lattice by projecting k-means++ seeds onto a 2-D MDS embedding, which preserves the global data structure from the start:

```java
double[][][] lattice = SOM.lattice(nrow, ncol, trainingData);
```

This uses `CentroidClustering.seeds` for the initial seeds, then a pairwise distance matrix and `MDS.fit` to compute 2-D coordinates. The MDS coordinates are sorted so that the row and column grid positions respect the embedded distances. This is the recommended way to initialize large SOMs.

### Usage

```java
int epochs = 20;
int nrow = 20, ncol = 20; // 400-neuron map

double[][][] lattice = SOM.lattice(nrow, ncol, trainingData);

SOM som = new SOM(
        lattice,
        TimeFunction.constant(0.1),
        Neighborhood.Gaussian(1.0, trainingData.length * epochs / 4.0)
);

for (int epoch = 0; epoch < epochs; epoch++) {
    for (int j : MathEx.permutate(trainingData.length)) {
        som.update(trainingData[j]);
    }
}

// Access the learned lattice (internal references, do not modify)
double[][][] learned = som.neurons(); // [nrow][ncol][d]

// Compute the U-matrix for visualization
double[][] umatrix = som.umatrix(); // [nrow][ncol]

// Quantize
double[] nearest = som.quantize(queryPoint);
```

### U-matrix

`umatrix()` computes the **Unified Distance Matrix**: for each neuron, its value is the maximum Euclidean distance to its orthogonal (4-connected) grid neighbors. Large U-matrix values indicate cluster boundaries; low values indicate dense regions. The U-matrix is the standard way to visualize SOM cluster structure as a heat map.

```
umatrix[i][j] = max distance from neuron (i,j) to its grid neighbors
```

Corner and edge cells only consider their existing neighbors (the grid is not toroidal).

### Neighborhood functions

Two built-in neighborhood functions are provided (see [Section 9](#9-neighborhood-functions-som)):

| Function | Behavior |
|---|---|
| `Neighborhood.bubble(radius)` | Flat 1.0 within Chebyshev distance < `radius`, 0 outside |
| `Neighborhood.Gaussian(sigma, T)` | Gaussian, width decays from `sigma` to ~0 over `T` iterations |

The Gaussian neighborhood is recommended for good maps; the bubble function is faster and a reasonable approximation.

### Key properties

- Fixed-size 2-D topology: the map structure is determined at construction time.
- Topology-preserving: similar inputs activate neighboring neurons.
- The `neurons()` method returns internal weight references — do not modify the returned arrays.
- Neurons are only skipped in `update` if `α(t) * θ < 1E-5` (the internal `tol` threshold), making updates efficient for large lattices.
- Update is parallelized over the full neuron array via `Arrays.stream(neurons).parallel()`.

---

## 8. The `smile.vq.hebb` Package

The sub-package `smile.vq.hebb` provides the graph primitives used by GNG and NeuralMap.

### `Neuron`

```java
public class Neuron implements Comparable<Neuron>, Serializable {
    public final double[] w;        // weight / prototype vector
    public final List<Edge> edges;  // topological neighbors (undirected edges stored bidirectionally)
    public transient double distance; // last computed distance to an input signal
    public double counter;          // local counter (error accumulation or freshness)
}
```

Key methods:

| Method | Description |
|---|---|
| `update(double[] x, double eps)` | `w += eps * (x − w)` — moves the prototype toward `x` |
| `distance(double[] x)` | Sets `this.distance = MathEx.distance(w, x)` |
| `addEdge(Neuron neighbor)` | Adds an undirected edge with age 0 |
| `addEdge(Neuron neighbor, int age)` | Adds an edge with a specified initial age |
| `removeEdge(Neuron neighbor)` | Removes the edge to `neighbor` |
| `setEdgeAge(Neuron neighbor, int age)` | Updates the age of the edge to `neighbor` |
| `age()` | Increments the age of all emanating edges by 1 |
| `compareTo(Neuron o)` | Orders by `distance` (ascending) — used with `HeapSelect` |

`distance` is declared `transient` so it is not serialized (it is a working variable, not persistent state).

### `Edge`

```java
public class Edge implements Serializable {
    public final Neuron neighbor; // the connected neuron
    public int age;               // number of steps since this edge was last refreshed
}
```

Edges are stored **bidirectionally**: if neuron A has an edge to B, B also has an edge to A. The algorithms maintain this invariant themselves (`addEdge`/`removeEdge` must be called on both endpoints when creating or removing a connection).

---

## 9. Neighborhood Functions (SOM)

`Neighborhood` is a `@FunctionalInterface`:

```java
double of(int i, int j, int t)
```

`i` and `j` are the row/column displacements from the BMU (signed); `t` is the current iteration count.

### Bubble neighborhood

```java
Neighborhood.bubble(int radius)
// Returns 1.0 if |i| < radius AND |j| < radius, else 0.0
```

All neurons within the Chebyshev square of side `2*radius - 1` centered on the BMU are updated equally. The boundary itself (distance exactly `radius`) is **not** included (strict inequality).

```java
Neighborhood bubble = Neighborhood.bubble(2);
bubble.of(0, 0, t)  // → 1.0 (winner)
bubble.of(1, 1, t)  // → 1.0 (inside)
bubble.of(2, 0, t)  // → 0.0 (on boundary, excluded)
```

### Gaussian neighborhood

```java
Neighborhood.Gaussian(double sigma, double T)
// Returns exp(−0.5 * (i² + j²) / s²) where s = sigma * exp(−t / T)
```

The neighbourhood width shrinks exponentially from `sigma` to near zero over `T` iterations. The winner always evaluates to exactly 1.0 (displacement 0,0 gives exponent 0).

```java
// Gaussian that shrinks over 8000 iterations
Neighborhood gauss = Neighborhood.Gaussian(2.0, 8000.0);
gauss.of(0, 0, 0)    // → 1.0
gauss.of(1, 0, 0)    // → exp(-0.5 / 4) ≈ 0.882
gauss.of(1, 0, 4000) // → narrower, smaller value
```

### Custom neighborhood functions

Because `Neighborhood` is a functional interface, you can provide any lambda:

```java
// Linearly decaying step function
int initialRadius = 5;
Neighborhood custom = (i, j, t) -> {
    int radius = Math.max(1, initialRadius - t / 1000);
    return Math.abs(i) < radius && Math.abs(j) < radius ? 1.0 : 0.0;
};
```

---

## 10. Algorithm Comparison and Selection Guide

### Quantization error on USPS handwritten digits (256-dimensional, ~7,000 training, ~2,000 test)

These figures are from the integration test suite with fixed seed `19650218`:

| Algorithm | Config | Train error | Test error |
|---|---|---|---|
| K-Means (baseline) | k=400 | 5.829 | 6.631 |
| Neural Gas | 400 neurons, 20 epochs | 5.699 | 6.531 |
| Growing Neural Gas | default params, 10 epochs | 5.593 | 6.432 |
| NeuralMap | r=8, 5 epochs + clear | 6.023 | 6.939 |
| SOM | 20×20 grid, 20 epochs | — | 6.582 |
| BIRCH | B=5, L=5, T=6 | 5.902 | 7.350 |

*Lower is better. Errors are mean Euclidean distances.*

### Decision guide

**Use BIRCH when:**
- You have very large datasets and need a single fast pass.
- Memory is constrained (the CF-tree is compact).
- You plan to run hierarchical or k-means clustering on the resulting centroids (two-phase BIRCH).
- You don't need to control the exact number of output centroids ahead of time.

**Use Neural Gas when:**
- You know the number of prototypes you need.
- You want reliable, smooth convergence without topology constraints.
- You need the competition Hebbian topology graph for downstream analysis.
- You have sufficient time for multi-epoch training.

**Use Growing Neural Gas when:**
- You don't know the right number of prototypes.
- The data has unknown or complex cluster structure.
- You want the topology graph to emerge naturally.
- Prototype count flexibility is more important than training speed.

**Use NeuralMap when:**
- Data arrives as a stream with possible outliers or noise.
- You want radius-based noise rejection (sporadic far-outlier signals don't corrupt existing prototypes).
- You need a compact, periodically pruned model (call `clear` between epochs).
- Memory footprint matters more than training throughput.

**Use SOM when:**
- Topology preservation is a goal — you want similar inputs to map to adjacent neurons.
- You want to visualize the data structure via the U-matrix.
- You have a fixed budget for the number of neurons arranged in a 2-D grid.
- The downstream task benefits from the ordered, regular layout (e.g., navigable maps, colorable grids).

---

## 11. Common Patterns and Tips

### Multi-epoch training with random shuffling

All online VQ algorithms benefit from processing data in a different random order each epoch, which avoids artifacts from fixed ordering:

```java
for (int epoch = 0; epoch < numEpochs; epoch++) {
    for (int j : MathEx.permutate(x.length)) {
        model.update(x[j]);
    }
}
```

`MathEx.permutate(n)` returns a random permutation of `0..n-1`.

### Computing mean quantization error

```java
double error = 0.0;
for (double[] xi : testData) {
    double[] proto = model.quantize(xi);
    error += MathEx.distance(xi, proto);
}
error /= testData.length;
System.out.printf("Mean quantization error = %.4f%n", error);
```

### Do not modify the returned prototype

`quantize` and `neurons()` return **internal references** for GNG, NeuralMap, NeuralGas, and SOM. Modifying them corrupts the model:

```java
// Dangerous — modifies the internal weight vector:
double[] proto = gas.quantize(x);
proto[0] = 0.0; // BUG

// Safe:
double[] proto = gas.quantize(x).clone();
```

BIRCH's `quantize` is safe: it returns a freshly computed centroid array.

### Seeding prototypes intelligently

For Neural Gas and SOM, random uniform initialization wastes early training iterations on global reorganization. Using k-means++ seeds (`CentroidClustering.seeds`) or the MDS-based `SOM.lattice` helper gives a much better starting point:

```java
// Neural Gas: seed with 200 representative points
double[][] seeds = CentroidClustering.seeds(trainingData, 200);
NeuralGas gas = new NeuralGas(seeds, alpha, theta, lifetime);

// SOM: MDS-ordered lattice
double[][][] lattice = SOM.lattice(10, 20, trainingData);
SOM som = new SOM(lattice, alpha, Neighborhood.Gaussian(1.0, T));
```

### Tuning the learning-rate schedule for Neural Gas and SOM

A common rule of thumb for exponential schedules over `N` total update steps:

```java
int N = trainingData.length * numEpochs;
TimeFunction alpha = TimeFunction.exp(0.3,  N / 2.0); // learning rate
TimeFunction theta = TimeFunction.exp(30.0, N / 8.0); // neighbourhood width
```

Both should reach near-zero by the end of training. Too large a final value leaves the map restless; too small wastes early training.

### GNG / NeuralMap: monitor neuron count between epochs

```java
for (int epoch = 0; epoch < 10; epoch++) {
    for (int j : MathEx.permutate(x.length)) { model.update(x[j]); }
    // NeuralMap only:
    ((NeuralMap) model).clear(1E-7);
    System.out.printf("Epoch %d: %d neurons%n", epoch, model.neurons().length);
}
```

If neuron count grows without bound, decrease `edgeLifetime` (GNG/NeuralMap) or decrease `lambda` (GNG) to trigger more pruning.

### SOM U-matrix interpretation

```java
double[][] u = som.umatrix();
// u[i][j] is large at cluster boundaries, small within dense regions.
// Use this as a heat map: high values → inter-cluster gaps.
```

Plot `u` as an image (nrow × ncol) using your preferred visualization library to identify cluster structure without labelling.

### BIRCH radius threshold calibration

If you have no prior knowledge of the data scale, estimate a reasonable `T` from a sample:

```java
double sampleDist = 0.0;
int pairs = 0;
for (int i = 0; i < Math.min(200, x.length); i++) {
    for (int j = i + 1; j < Math.min(200, x.length); j++) {
        sampleDist += MathEx.distance(x[i], x[j]);
        pairs++;
    }
}
double meanPairwise = sampleDist / pairs;
// Set T to roughly 10–20% of the mean pairwise distance
double T = meanPairwise * 0.15;
BIRCH birch = new BIRCH(d, 5, 5, T);
```

### Serialization

All implementations are serializable. Save and restore a trained model with standard Java object streams:

```java
// Save
try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("model.ser"))) {
    oos.writeObject(trainedModel);
}

// Restore
try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("model.ser"))) {
    VectorQuantizer restored = (VectorQuantizer) ois.readObject();
}
```

Note: `Neuron.distance` is `transient` and will be reset to `Double.MAX_VALUE` after deserialization. This is intentional — it is a working variable that is overwritten before use.

---

*See also: `smile.clustering.KMeans`, `smile.clustering.HierarchicalClustering`, `smile.manifold.MDS`, `smile.util.function.TimeFunction`*

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
