# SMILE — Vector Quantization

The module `smile.vq` provides online/incremental vector quantizers
and topology-learning algorithms for clustering, compression, and
prototype-based modeling.

---

## Table of Contents

1. Overview
2. Common API (`VectorQuantizer`)
3. Algorithms
   - BIRCH
   - SOM
   - NeuralGas
   - GrowingNeuralGas
   - NeuralMap
4. Neighborhood Functions (`Neighborhood`)
5. Choosing an Algorithm
6. End-to-End Examples
7. Parameter Tuning Tips
8. Validation and Error Handling
9. Thread-Safety Notes
10. API Quick Reference

---

## 1) Overview

Package: `smile.vq`

Main classes:

- `VectorQuantizer` - shared online quantizer interface.
- `BIRCH` - CF-tree based incremental quantization.
- `SOM` - Self-Organizing Map with 2D lattice and U-matrix support.
- `NeuralGas` - rank-based soft competitive learning.
- `GrowingNeuralGas` - adaptive graph that inserts/removes neurons.
- `NeuralMap` - adaptive graph inspired by GNG and BIRCH.
- `Neighborhood` - SOM neighborhood kernels (`bubble`, `Gaussian`).

Supporting graph classes used by GNG/NeuralMap:

- `smile.vq.hebb.Neuron`
- `smile.vq.hebb.Edge`

---

## 2) Common API (`VectorQuantizer`)

All quantizers implement:

```java
public interface VectorQuantizer {
    void update(double[] x);   // online learning with one sample
    double[] quantize(double[] x); // nearest representative/prototype
}
```

Typical online workflow:

1. Construct model with hyperparameters.
2. Stream samples through `update(x)`.
3. Use `quantize(x)` for compression/prototype lookup.
4. Optionally inspect model internals (`neurons()`, `centroids()`, `umatrix()`).

---

## 3) Algorithms

## BIRCH

Class: `smile.vq.BIRCH`

BIRCH maintains a CF tree and quantizes by nearest leaf-cluster centroid.
It is memory-friendly and incremental, often a good baseline for large streams.

Constructor:

```java
BIRCH(int d, int B, int L, double T)
```

- `d`: input dimension.
- `B`: internal-node branching factor (>= 2).
- `L`: leaf capacity (>= 2).
- `T`: max subcluster radius (> 0).

Useful method:

- `double[][] centroids()` - collect leaf-level centroids.

Example:

```java
import smile.vq.BIRCH;

BIRCH model = new BIRCH(2, 20, 20, 0.8);
for (double[] x : data) {
    model.update(x);
}

double[] q = model.quantize(new double[] {1.2, -0.3});
double[][] centers = model.centroids();
```

---

## SOM

Class: `smile.vq.SOM`

SOM maps high-dimensional data to a 2D lattice while preserving topology.
Useful for visualization and structure discovery.

Construction options:

1. Build lattice directly:

```java
SOM(double[][][] neurons, TimeFunction alpha, Neighborhood theta)
```

2. Build lattice from data seeds:

```java
double[][][] lattice = SOM.lattice(nrow, ncol, samples);
```

Useful methods:

- `double[][][] neurons()` - current lattice weights.
- `double[][] umatrix()` - U-matrix (neighbor distance heatmap).

Example:

```java
import smile.vq.SOM;
import smile.vq.Neighborhood;
import smile.util.function.TimeFunction;

double[][][] lattice = SOM.lattice(20, 20, data);
SOM som = new SOM(
    lattice,
    TimeFunction.constant(0.1),
    Neighborhood.Gaussian(1.0, data.length * 5.0)
);

for (int epoch = 0; epoch < 5; epoch++) {
    for (double[] x : data) {
        som.update(x);
    }
}

double[] q = som.quantize(data[0]);
double[][] u = som.umatrix();
```

---

## NeuralGas

Class: `smile.vq.NeuralGas`

Neural Gas uses rank-based adaptation of all prototypes, often converging
more robustly than hard winner-only updates.

Constructor:

```java
NeuralGas(double[][] neurons,
          TimeFunction alpha,
          TimeFunction theta,
          TimeFunction lifetime)
```

- `neurons`: initial prototypes (at least 2).
- `alpha`: learning-rate schedule.
- `theta`: neighborhood/rank schedule.
- `lifetime`: edge lifetime schedule for the induced network.

Useful methods:

- `double[][] neurons()` - current prototypes.
- `Graph network()` - current connectivity graph.

Example:

```java
import smile.vq.NeuralGas;
import smile.util.function.TimeFunction;
import smile.clustering.CentroidClustering;

int k = 100;
double[][] init = CentroidClustering.seeds(data, k);
int T = data.length * 10;

NeuralGas gas = new NeuralGas(
    init,
    TimeFunction.exp(0.3, T / 2.0),
    TimeFunction.exp(30.0, T / 8.0),
    TimeFunction.constant(data.length * 2.0)
);

for (int epoch = 0; epoch < 10; epoch++) {
    for (double[] x : data) {
        gas.update(x);
    }
}

double[] q = gas.quantize(data[0]);
```

---

## GrowingNeuralGas

Class: `smile.vq.GrowingNeuralGas`

GNG dynamically adds/removes nodes and edges while learning topology.
Good when cluster count is unknown and topology adapts over time.

Constructors:

```java
GrowingNeuralGas(int d)

GrowingNeuralGas(int d,
                 double epsBest,
                 double epsNeighbor,
                 int edgeLifetime,
                 int lambda,
                 double alpha,
                 double beta)
```

Useful method:

- `Neuron[] neurons()` - current adaptive graph vertices.

Example:

```java
import smile.vq.GrowingNeuralGas;

GrowingNeuralGas gng = new GrowingNeuralGas(2);
for (int epoch = 0; epoch < 10; epoch++) {
    for (double[] x : data) {
        gng.update(x);
    }
}

double[] q = gng.quantize(new double[] {0.5, 1.1});
```

---

## NeuralMap

Class: `smile.vq.NeuralMap`

NeuralMap is a compact adaptive graph learner inspired by GNG/BIRCH.
It uses a radius-based activation policy and supports stale-node cleanup.

Constructor (explicit dimension):

```java
NeuralMap(int d,
          double r,
          double epsBest,
          double epsNeighbor,
          int edgeLifetime,
          double beta)
```

- `d`: input dimension.
- `r`: activation radius (> 0).
- `epsBest`: winner learning rate.
- `epsNeighbor`: neighbor learning rate.
- `edgeLifetime`: max edge age.
- `beta`: freshness decay.

Useful methods:

- `Neuron[] neurons()` - current nodes.
- `void clear(double eps)` - remove stale/noise nodes and old edges.

Example:

```java
import smile.vq.NeuralMap;

NeuralMap map = new NeuralMap(2, 1.5, 0.01, 0.002, 50, 0.995);
for (int epoch = 0; epoch < 5; epoch++) {
    for (double[] x : data) {
        map.update(x);
    }
    map.clear(1E-7);
}

double[] q = map.quantize(new double[] {0.2, -0.8});
```

---

## 4) Neighborhood Functions (`Neighborhood`)

Used mainly by SOM.

```java
Neighborhood bubble = Neighborhood.bubble(2);
Neighborhood gaussian = Neighborhood.Gaussian(1.0, 10_000);
```

- `bubble(radius)`: hard neighborhood (0/1 updates).
- `Gaussian(sigma, T)`: smooth neighborhood that shrinks with iteration `t`.

---

## 5) Choosing an Algorithm

- Use `BIRCH` when:
  - data is large/streaming,
  - you want simple incremental centroid quantization,
  - memory efficiency matters.

- Use `SOM` when:
  - topology-preserving 2D map is needed,
  - you want U-matrix style visualization.

- Use `NeuralGas` when:
  - fixed number of prototypes is acceptable,
  - you want stable convergence with rank-based updates.

- Use `GrowingNeuralGas` when:
  - prototype count should adapt automatically,
  - graph structure is part of the output.

- Use `NeuralMap` when:
  - you want adaptive topology with practical stale-node cleanup,
  - radius-based activation is a good fit.

---

## 6) End-to-End Example (Reusable Utility)

```java
import smile.vq.VectorQuantizer;
import smile.vq.BIRCH;

public class QuantizationDemo {
    static double avgQuantizationError(VectorQuantizer q, double[][] x) {
        double err = 0.0;
        for (double[] xi : x) {
            err += smile.math.MathEx.distance(xi, q.quantize(xi));
        }
        return err / x.length;
    }

    public static void main(String[] args) {
        double[][] train = {
            {0.0, 0.0}, {0.1, 0.0}, {5.0, 5.1}, {4.9, 5.0}
        };

        VectorQuantizer q = new BIRCH(2, 4, 4, 0.5);
        for (double[] x : train) q.update(x);

        System.out.println("Avg Q error: " + avgQuantizationError(q, train));
    }
}
```

---

## 7) Parameter Tuning Tips

- Start with data normalization/standardization before training.
- For SOM:
  - larger lattice -> better topology detail, slower training.
  - `Gaussian` is smoother; `bubble` is cheaper.
- For NeuralGas:
  - initialize with sensible seeds (e.g., k-means seeds).
  - decay `alpha` and `theta` over training horizon.
- For GNG/NeuralMap:
  - `edgeLifetime` too small -> graph fragments quickly.
  - `beta` close to 1.0 keeps history longer.
  - `epsBest` should be significantly larger than `epsNeighbor`.
- Evaluate with average quantization error and stability across runs.

---

## 8) Validation and Error Handling

Dimension and parameter validation is strict.

Common exceptions:

- `IllegalArgumentException`:
  - invalid constructor hyperparameters,
  - null input vectors,
  - dimension mismatch.

- `IllegalStateException`:
  - `quantize(...)` called before model has any learned prototypes/nodes.

Dimension mismatch message format is standardized:

```text
Invalid input dimension: expected <d>, actual <n>
```

---

## 9) Thread-Safety Notes

- Do not assume instances are thread-safe for concurrent `update(...)`.
- Treat each quantizer instance as single-writer.
- `quantize(...)` may mutate temporary internal buffers in some algorithms;
  avoid concurrent reads/writes on the same instance unless you add external
  synchronization.

---

## 10) API Quick Reference

```java
// Core interface
void update(double[] x)
double[] quantize(double[] x)

// BIRCH
new BIRCH(int d, int B, int L, double T)
double[][] centroids()

// SOM
new SOM(double[][][] neurons, TimeFunction alpha, Neighborhood theta)
static double[][][] SOM.lattice(int nrow, int ncol, double[][] samples)
double[][][] neurons()
double[][] umatrix()

// NeuralGas
new NeuralGas(double[][] neurons, TimeFunction alpha, TimeFunction theta, TimeFunction lifetime)
double[][] neurons()
Graph network()

// GrowingNeuralGas
new GrowingNeuralGas(int d)
new GrowingNeuralGas(int d, double epsBest, double epsNeighbor, int edgeLifetime, int lambda, double alpha, double beta)
smile.vq.hebb.Neuron[] neurons()

// NeuralMap
new NeuralMap(int d, double r, double epsBest, double epsNeighbor, int edgeLifetime, double beta)
smile.vq.hebb.Neuron[] neurons()
void clear(double eps)

// Neighborhood
Neighborhood.bubble(int radius)
Neighborhood.Gaussian(double sigma, double T)
```

---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

