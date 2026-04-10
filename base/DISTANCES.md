# SMILE — Distance and Metric Functions

The `smile.math.distance` package provides a rich collection of distance and
metric functions used throughout SMILE for nearest-neighbor search, clustering,
classification, and other machine-learning algorithms.

## Core Abstractions

### `Distance<T>`

The root interface for all distance functions. A distance maps every pair of
objects to a non-negative real number and satisfies:

| Property | Requirement |
|----------|-------------|
| Non-negativity | `d(x, y) ≥ 0` |
| Isolation | `d(x, y) = 0` iff `x = y` |
| Symmetry | `d(x, y) = d(y, x)` |

`Distance<T>` extends `ToDoubleBiFunction<T,T>` and `Serializable`, so any
instance can be used directly with Java Streams.

```java
Distance<double[]> dist = new EuclideanDistance();
double d = dist.d(x, y);
// Also works as a BiFunction:
double d2 = dist.applyAsDouble(x, y);
```

The interface also provides a `pdist(T[] x)` default method that computes the
full lower-triangular pairwise distance matrix in parallel:

```java
double[][] data = { {0, 0}, {3, 0}, {0, 4} };
EuclideanDistance dist = new EuclideanDistance();
Matrix D = dist.pdist(data);
// D.get(1, 0) == 3.0, D.get(2, 0) == 4.0, D.get(2, 1) == 5.0
```

### `Metric<T>`

A `Metric` is a `Distance` that additionally satisfies the **triangle
inequality**: `d(x, z) ≤ d(x, y) + d(y, z)`. All standard Lp-norms are
metrics.

---

## Dense Vector Distances (`double[]`)

### Euclidean Distance

The straight-line distance, also known as the L₂ norm.

```
d(x, y) = sqrt( Σᵢ (xᵢ - yᵢ)² )
```

| Feature | Detail |
|---------|--------|
| Class | `EuclideanDistance` |
| Implements | `Metric<double[]>` |
| Weighted | Yes — pass a `double[] weight` to the constructor |
| NaN handling | NaN values are excluded; result is scaled by `n/m` |
| Sparse | `SparseEuclideanDistance` for `SparseArray` inputs |

```java
// Unweighted
EuclideanDistance dist = new EuclideanDistance();
double d = dist.d(new double[]{1, 2, 3}, new double[]{4, 3, 2});
// d ≈ 3.742

// Weighted
double[] w = {2.0, 1.0, 0.5};
EuclideanDistance wDist = new EuclideanDistance(w);

// Sparse
SparseEuclideanDistance sparseDist = new SparseEuclideanDistance();
double sd = sparseDist.d(sparseX, sparseY);
```

### Manhattan Distance

The sum of absolute differences, also known as the L₁ norm or city-block
distance.

```
d(x, y) = Σᵢ |xᵢ - yᵢ|
```

| Feature | Detail |
|---------|--------|
| Class | `ManhattanDistance` |
| Implements | `Metric<double[]>` |
| Weighted | Yes |
| NaN handling | NaN values excluded; result scaled by `n/m` |
| Sparse | `SparseManhattanDistance` |

```java
ManhattanDistance dist = new ManhattanDistance();
double d = dist.d(new double[]{1, 2, 3}, new double[]{4, 3, 2});
// d = |1-4| + |2-3| + |3-2| = 5

// Integer array shortcut (no NaN handling):
int[] x = {1, 2, 3, 4};
int[] y = {4, 3, 2, 1};
double di = new ManhattanDistance().d(x, y); // 8
```

### Chebyshev Distance

The maximum absolute difference over all dimensions, also known as the
L∞ norm or chessboard distance.

```
d(x, y) = maxᵢ |xᵢ - yᵢ|
```

| Feature | Detail |
|---------|--------|
| Class | `ChebyshevDistance` |
| Implements | `Metric<double[]>` |
| NaN handling | NaN values excluded |
| Sparse | `SparseChebyshevDistance` |

```java
ChebyshevDistance dist = new ChebyshevDistance();
double d = dist.d(new double[]{1, 5, 3}, new double[]{4, 2, 1});
// d = max(|1-4|, |5-2|, |3-1|) = 3
```

### Minkowski Distance

The general Lp norm, parameterized by order `p`.

```
d(x, y) = ( Σᵢ |xᵢ - yᵢ|ᵖ )^(1/p)
```

Special cases: `p=1` → Manhattan, `p=2` → Euclidean, `p→∞` → Chebyshev.

| Feature | Detail |
|---------|--------|
| Class | `MinkowskiDistance` |
| Implements | `Metric<double[]>` |
| Weighted | Yes |
| NaN handling | NaN values excluded; result scaled appropriately |
| Sparse | `SparseMinkowskiDistance` |

```java
MinkowskiDistance m3 = new MinkowskiDistance(3);  // L3 norm
double d = m3.d(x, y);

// Weighted L4
MinkowskiDistance wm4 = new MinkowskiDistance(4, weights);
```

### Mahalanobis Distance

A scale-invariant distance that accounts for correlations among variables
using a covariance matrix Σ.

```
d(x, y) = sqrt( (x-y)ᵀ Σ⁻¹ (x-y) )
```

When Σ = I (identity), Mahalanobis reduces to Euclidean distance.

```java
double[][] cov = {
    {0.9, 0.4, 0.7},
    {0.4, 0.5, 0.3},
    {0.7, 0.3, 0.8}
};
MahalanobisDistance dist = new MahalanobisDistance(cov);
double d = dist.d(x, y);
```

---

## Statistical / Probabilistic Distances

### Correlation Distance

Defined as `1 − r(x, y)` where `r` is the correlation coefficient.
Range is `[0, 2]` — value 0 means perfect positive correlation, 2 means
perfect negative correlation.

Supports three correlation methods:

| Method | Class constructor argument |
|--------|---------------------------|
| Pearson | `"pearson"` (default) |
| Spearman (rank) | `"spearman"` |
| Kendall (tau) | `"kendall"` |

```java
CorrelationDistance pearson   = new CorrelationDistance();           // Pearson
CorrelationDistance spearman  = new CorrelationDistance("spearman");
CorrelationDistance kendall   = new CorrelationDistance("kendall");

double[] x = {1.0, 2.0, 3.0, 4.0};
double[] y = {4.0, 3.0, 2.0, 1.0};
double d = pearson.d(x, y); // 2.0 (perfectly anti-correlated)
```

### Jensen-Shannon Distance

The square root of the Jensen-Shannon divergence between two probability
distributions P and Q:

```
JSD(P‖Q) = ( KL(P‖M) + KL(Q‖M) ) / 2,   M = (P + Q) / 2
d(P, Q)  = sqrt( JSD(P‖Q) )
```

This is a proper metric bounded in `[0, 1]` (with natural logarithm).

```java
double[] p = {0.3, 0.4, 0.3};
double[] q = {0.5, 0.2, 0.3};
JensenShannonDistance dist = new JensenShannonDistance();
double d = dist.d(p, q);
```

---

## Set / Categorical Distances

### Jaccard Distance

Measures dissimilarity between two sets as the complement of the Jaccard
similarity coefficient:

```
d(A, B) = 1 − |A ∩ B| / |A ∪ B|
```

Works on both `Set<T>` and `T[]` inputs:

```java
// On arrays (generic type)
JaccardDistance<String> dist = new JaccardDistance<>();
String[] a = {"cat", "dog", "bird"};
String[] b = {"dog", "bird", "fish"};
double d = dist.d(a, b); // 1 - 2/4 = 0.5

// Static helper for java.util.Set
Set<Integer> s1 = Set.of(1, 2, 3, 4);
Set<Integer> s2 = Set.of(3, 4, 5, 6);
double d2 = JaccardDistance.d(s1, s2); // 1 - 2/6 ≈ 0.667
```

### Hamming Distance

Counts the number of positions where two strings or arrays differ.
For integers and longs, uses the hardware `POPCNT` instruction via
`Integer.bitCount` / `Long.bitCount`.

```java
// Integer arrays
HammingDistance dist = new HammingDistance();
int[] x = {1, 0, 1, 1, 0};
int[] y = {1, 0, 0, 1, 1};
double d = dist.d(x, y); // 2

// Bit-level (single integer)
int d2 = HammingDistance.d(0x5D, 0x49); // 2

// Long integer
int d3 = HammingDistance.d(0xFFL, 0x00L); // 8

// Byte arrays
byte[] bx = {1, 0, 1, 1, 1, 0, 1};
byte[] by = {1, 0, 0, 1, 0, 0, 1};
int d4 = HammingDistance.d(bx, by); // 2

// BitSet
HammingDistance hd = new HammingDistance();
double d5 = hd.d(bitSetX, bitSetY);
```

### Lee Distance

A generalization of Hamming distance for q-ary alphabets `{0, 1, …, q−1}`.
Each symbol comparison wraps around modulo `q`:

```
d(x, y) = Σᵢ min( |xᵢ − yᵢ|,  q − |xᵢ − yᵢ| )
```

When `q = 2` or `q = 3`, Lee distance equals Hamming distance.

```java
// Alphabet size q=6
LeeDistance dist = new LeeDistance(6);
int[] x = {3, 3, 4, 0};
int[] y = {2, 5, 4, 3};
double d = dist.d(x, y); // 6

// Wrap-around: |0 - 5| = 5, but min(5, 6-5) = 1
int[] x2 = {0};
int[] y2 = {5};
double d2 = new LeeDistance(6).d(x2, y2); // 1
```

---

## Sequence Distances

### Edit Distance (Levenshtein / Damerau)

Edit distance counts the minimum number of single-character edit operations
needed to transform one string into another.

| Variant | Allowed operations |
|---------|-------------------|
| Levenshtein | insert, delete, substitute |
| Damerau-Levenshtein | insert, delete, substitute, **transpose adjacent** |

Two implementations are provided:

**Static methods** — simple O(mn) dynamic programming, multi-thread safe:

```java
// Levenshtein
int d1 = EditDistance.levenshtein("kitten", "sitting"); // 3

// Damerau (cheaper for transpositions)
int d2 = EditDistance.damerau("act", "cat"); // 1 (one transposition)
```

**Instance (BR algorithm)** — faster O(kn) algorithm when the distance is
small, pre-allocates working arrays:

```java
// Unit cost, Levenshtein
EditDistance lev = new EditDistance(maxLen, false);
double d1 = lev.d("Levenshtein", "Laeveshxtin"); // 4

// Unit cost, Damerau
EditDistance dam = new EditDistance(maxLen, true);
double d2 = dam.d("act", "cat"); // 1
```

**Weighted edit distance** — custom substitution, insertion and deletion
costs via a `CharSubstitutionCost` table:

```java
EditDistance wEdit = new EditDistance(substitutionCostTable, maxLen);
```

### Dynamic Time Warping (DTW)

DTW aligns two time series non-linearly to measure their similarity
independent of temporal distortions. An optional Sakoe-Chiba band constrains
the warp window to improve both accuracy and speed.

```java
double[] x = { /* time series 1 */ };
double[] y = { /* time series 2 */ };

// Unconstrained DTW
double d1 = DynamicTimeWarping.d(x, y);

// With Sakoe-Chiba band: radius = 20 steps
double d2 = DynamicTimeWarping.d(x, y, 20);

// float[] and int[] overloads are also available
float[] xf = { /* ... */ };
double d3 = DynamicTimeWarping.d(xf, yf);
```

For generic types, use the `DynamicTimeWarping<T>` class with any
`Distance<T>`:

```java
// DTW on boxed Double sequences
Distance<Double> absDiff = (a, b) -> Math.abs(a - b);
DynamicTimeWarping<Double> dtw = new DynamicTimeWarping<>(absDiff);

Double[] xa = {1.0, 2.0, 3.0};
Double[] ya = {1.0, 2.0, 4.0};
double d = dtw.d(xa, ya); // 1.0

// With radius (percentage of sequence length)
DynamicTimeWarping<Double> dtwBand = new DynamicTimeWarping<>(absDiff, 0.1);
```

---

## Sparse Array Distances

All standard Lp distances have counterparts that work on `SparseArray`
(indices stored as sorted int arrays):

| Dense class | Sparse class |
|-------------|--------------|
| `EuclideanDistance` | `SparseEuclideanDistance` |
| `ManhattanDistance` | `SparseManhattanDistance` |
| `ChebyshevDistance` | `SparseChebyshevDistance` |
| `MinkowskiDistance` | `SparseMinkowskiDistance` |

```java
SparseArray s1 = new SparseArray();
s1.append(0, 3.0);
s1.append(2, 4.0);

SparseArray s2 = new SparseArray();
s2.append(1, 5.0);
s2.append(2, 1.0);

double d = new SparseEuclideanDistance().d(s1, s2);
```

---

## NaN Handling

`EuclideanDistance`, `ManhattanDistance`, and `MinkowskiDistance` (and their
sparse variants) treat `NaN` values as **missing**. The distance is computed
on the `m` non-missing pairs, then scaled up by `n/m` to restore the same
expected magnitude as if all `n` dimensions were observed.

If **all** values are NaN, `Double.NaN` is returned.

```java
double[] x = {Double.NaN, 3.0, 4.0};
double[] y = {1.0, Double.NaN, 1.0};
// Only index 2 contributes: |4-1| = 3; n=3, m=1
// Result = sqrt(3 * 9 / 1) = sqrt(27) ≈ 5.196
double d = new EuclideanDistance().d(x, y);
```

---

## Choosing the Right Distance

| Use case | Recommended distance |
|----------|----------------------|
| General continuous data | `EuclideanDistance` |
| Outlier-robust / sparse differences | `ManhattanDistance` |
| Data with correlated features | `MahalanobisDistance` |
| Variable-length sequences | `DynamicTimeWarping` |
| String / character sequences | `EditDistance` |
| Sets / binary features | `JaccardDistance`, `HammingDistance` |
| Probability distributions | `JensenShannonDistance` |
| Cyclic integer symbols | `LeeDistance` |
| Smooth distributions (kernel trick) | prefer a kernel; see `smile.math.kernel` |


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

