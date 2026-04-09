# SMILE — Nearest Neighbor Search

Nearest neighbor search (NNS) is a fundamental operation in machine learning,
data mining, and computational geometry: given a set **S** of points in a metric
space **M** and a query point **q ∈ M**, find the point(s) in **S** that are
closest to **q**. The `smile.neighbor` package provides a rich collection of
exact and approximate algorithms, all behind a clean, uniform interface.

---

## Table of Contents

1. [Core Interfaces](#1-core-interfaces)
2. [The `Neighbor` Record](#2-the-neighbor-record)
3. [Algorithm Overview](#3-algorithm-overview)
4. [LinearSearch — Brute Force](#4-linearsearch--brute-force)
5. [KDTree — Space Partitioning for Low Dimensions](#5-kdtree--space-partitioning-for-low-dimensions)
6. [CoverTree — General Metric Spaces](#6-covertree--general-metric-spaces)
7. [BKTree — Discrete Metric Spaces](#7-bktree--discrete-metric-spaces)
8. [LSH — Locality-Sensitive Hashing](#8-lsh--locality-sensitive-hashing)
9. [MPLSH — Multi-Probe LSH](#9-mplsh--multi-probe-lsh)
10. [SNLSH — LSH for Signatures / Text](#10-snlsh--lsh-for-signatures--text)
11. [RandomProjectionTree and Forest](#11-randomprojectiontree-and-forest)
12. [NearestNeighborGraph](#12-nearestneighborgraph)
13. [Choosing the Right Algorithm](#13-choosing-the-right-algorithm)
14. [Performance Notes](#14-performance-notes)
15. [Complete Examples](#15-complete-examples)

---

## 1. Core Interfaces

Every search structure implements one or both of these interfaces:

### `KNNSearch<K, V>`

```java
public interface KNNSearch<K, V> {
    // Returns the single nearest neighbor (excludes the query object itself
    // by reference equality — useful when querying training data).
    default Neighbor<K, V> nearest(K q);

    // Returns the k nearest neighbors, sorted by distance (closest last
    // due to max-heap ordering; call Arrays.sort() if ascending is needed).
    Neighbor<K, V>[] search(K q, int k);
}
```

### `RNNSearch<K, V>`

```java
public interface RNNSearch<K, V> {
    // Appends all neighbors within the given radius to the list.
    // The list is NOT cleared before appending; results may be in any order.
    void search(K q, double radius, List<Neighbor<K, V>> neighbors);
}
```

**Key design points**

* The query object is compared by **reference equality** (`q == key`) and
  excluded from results. This lets you call `search(data[i], k)` to find the
  k nearest *other* points without getting the point itself back.
* All structures are **serializable** — they can be saved to disk and reloaded.
* `K` is the **key type** (the coordinate/feature vector used for distance
  computation). `V` is the **value type** (the associated payload object,
  which can be the same as `K`).

---

## 2. The `Neighbor` Record

Every search result is wrapped in an immutable `Neighbor<K, V>` record:

```java
public record Neighbor<K, V>(K key, V value, int index, double distance)
        implements Comparable<Neighbor<K, V>> { ... }
```

| Field      | Description                                                        |
|------------|--------------------------------------------------------------------|
| `key`      | The feature vector / key of the neighbor                           |
| `value`    | The payload object associated with the neighbor                    |
| `index`    | Zero-based index of the neighbor in the original dataset           |
| `distance` | Euclidean (or metric) distance from the query to this neighbor     |

`Neighbor` implements `Comparable` by distance (ties broken by index), so
collections of neighbors can be sorted with `Collections.sort()` or
`Arrays.sort()`.

```java
// Factory method for the common case where key == value
Neighbor<double[], double[]> n = Neighbor.of(point, 42, 3.14);

// Inspect results
System.out.printf("Nearest: index=%d, dist=%.4f%n", n.index(), n.distance());
```

---

## 3. Algorithm Overview

| Class                   | Query type         | Distance metric    | Exact? | Best for                              |
|-------------------------|--------------------|--------------------|--------|---------------------------------------|
| `LinearSearch`          | any `K`            | any `Distance<K>`  | ✅ Yes | Ground truth / high dimensions        |
| `KDTree`                | `double[]`         | Euclidean          | ✅ Yes | Low dimensions (d ≤ ~20)             |
| `CoverTree`             | any `K`            | any `Metric<K>`    | ✅ Yes | General metrics, low intrinsic dim    |
| `BKTree`                | any `K`            | integer metric     | ✅ Yes | String/edit-distance search           |
| `LSH`                   | `double[]`         | Euclidean (approx) | ❌ No  | High-dimensional approx NN            |
| `MPLSH`                 | `double[]`         | Euclidean (approx) | ❌ No  | High-dimensional, fewer hash tables   |
| `SNLSH`                 | any `K`            | Hamming (SimHash)  | ❌ No  | Near-duplicate text / set similarity  |
| `RandomProjectionTree`  | `double[]`         | Euclidean/Angular  | ❌ No  | High-dim approx, candidate generation |
| `RandomProjectionForest`| `double[]`         | Euclidean/Angular  | ❌ No  | High-dim approx NN, kNN graphs        |

---

## 4. LinearSearch — Brute Force

`LinearSearch` computes the distance from the query to **every** point in the
dataset. It is `O(N·d)` per query but has zero index-building overhead and
**no memory overhead** beyond storing the data itself. Counterintuitively, it
beats tree-based approaches in high dimensions.

### Construction

```java
// 1. Key and value are the same (common case)
double[][] data = ...; // N × d array
LinearSearch<double[], double[]> search = LinearSearch.of(data, MathEx::distance);

// 2. Key and value are different types
double[][] keys = ...;
String[]   values = ...;
LinearSearch<double[], String> search =
    new LinearSearch<>(keys, values, MathEx::distance);

// 3. Value objects that expose their own key via a function
record Point(String label, double[] vec) {}
List<Point> points = ...;
LinearSearch<double[], Point> search =
    new LinearSearch<>(points, MathEx::distance, Point::vec);

// 4. Any metric — edit distance on strings
String[] words = ...;
LinearSearch<String, String> wordSearch =
    LinearSearch.of(words, new EditDistance(true));
```

### Queries

```java
// Nearest neighbor (excludes query object by reference)
Neighbor<double[], double[]> nn = search.nearest(q);

// k nearest neighbors
Neighbor<double[], double[]>[] knn = search.search(q, 10);

// Range search — all points within radius r
List<Neighbor<double[], double[]>> inRange = new ArrayList<>();
search.search(q, 2.0, inRange);
```

---

## 5. KDTree — Space Partitioning for Low Dimensions

A **KD-tree** (k-dimensional tree) recursively bisects the space along the
axis of maximum variance, building a binary tree. Queries prune entire
subtrees when the splitting hyperplane is farther than the current best
distance. Average complexity is `O(log N)` for randomly distributed points
in low dimensions.

> **Curse of dimensionality:** KD-tree performance degrades sharply above
> ~15–20 dimensions. A rule of thumb: the dataset size N should satisfy
> N >> 2<sup>d</sup>. Use LSH or random projection methods for high-d data.

### Construction

```java
// Keys == values (most common)
KDTree<double[]> tree = KDTree.of(data);

// Separate keys and values
KDTree<String> tree = new KDTree<>(keys, labels);
```

The tree is built once during construction in `O(N log N)` time.

### Queries

```java
// 1-NN
Neighbor<double[], double[]> nn = tree.nearest(q);

// k-NN — result array is sorted by distance (ascending)
Neighbor<double[], double[]>[] knn = tree.search(q, 5);

// Range search
List<Neighbor<double[], double[]>> results = new ArrayList<>();
tree.search(q, 1.5, results);
```

### Implementation notes (optimizations)

* All traversal uses **squared distances** to avoid `Math.sqrt` until the
  very last step.
* An **early-exit** inner loop abandons distance computation as soon as the
  partial sum exceeds the current best.
* Branch pruning: a subtree is skipped when `diff² ≥ bestDist²` where `diff`
  is the signed distance to the splitting hyperplane.
* The `search(q, k)` result is assembled with a plain loop, not a Stream,
  to avoid allocation overhead.

---

## 6. CoverTree — General Metric Spaces

The **Cover Tree** is a hierarchical data structure that works with *any*
metric satisfying the triangle inequality. It maintains a hierarchy of
"covering" balls and prunes branches whose covering radius cannot possibly
contain a closer neighbor.

Theoretical bound: `O(c¹² log N)` per query, where `c` is the **expansion
constant** of the dataset (a measure of its intrinsic dimensionality).

### Construction

```java
// With any Metric<K> — e.g. Euclidean on double[]
CoverTree<double[], double[]> tree = CoverTree.of(data, MathEx::distance);

// Custom base (default 1.3; larger base = shallower tree, wider fans)
CoverTree<double[], double[]> tree = CoverTree.of(data, MathEx::distance, 2.0);

// From a List
List<double[]> list = Arrays.asList(data);
CoverTree<double[], double[]> tree = CoverTree.of(list, MathEx::distance);

// With separate keys and values
CoverTree<double[], String> tree = CoverTree.of(keys, labels, MathEx::distance);
```

### Queries

```java
Neighbor<double[], double[]> nn   = tree.nearest(q);
Neighbor<double[], double[]>[] knn = tree.search(q, 10);

List<Neighbor<double[], double[]>> inRange = new ArrayList<>();
tree.search(q, 2.0, inRange);
```

### When to prefer CoverTree over KDTree

| Scenario                            | Prefer        |
|-------------------------------------|---------------|
| Euclidean distance, d ≤ 20          | `KDTree`      |
| Non-Euclidean metric (e.g. cosine)  | `CoverTree`   |
| Very clustered / low intrinsic dim  | `CoverTree`   |
| Uniformly distributed, d ≤ 15      | `KDTree`      |

---

## 7. BKTree — Discrete Metric Spaces

A **BK-tree** (Burkhard–Keller tree) is specialized for *integer-valued*
metrics such as edit (Levenshtein) distance. Each node stores all children
at distinct integer distances, enabling efficient range queries like
"all words within edit distance 2 of this word."

It implements only `RNNSearch` (range queries), not `KNNSearch`.

### Construction

```java
// Static factory from an array
EditDistance dist = new EditDistance(50, true);
BKTree<String, String> tree = BKTree.of(words, dist);

// Manual construction
BKTree<String, String> tree = new BKTree<>(dist);
for (String w : words) {
    tree.add(w, w);
}
```

Duplicate insertions (distance 0 to an existing node) are silently ignored.

### Queries

```java
// All words within edit distance 2
List<Neighbor<String, String>> results = new ArrayList<>();
tree.search("colour", 2, results);

// Integer overload (the natural form for edit distance)
tree.search("colour", 2, results);

// Double overload — radius is truncated to int
tree.search("colour", 2.0, results);
```

### Complexity

Building: `O(N log N)` average.  
Query: `O(N^(r/d))` where `r` is the radius and `d` is the maximum branching
distance — typically much faster than linear scan for small radii.

---

## 8. LSH — Locality-Sensitive Hashing

**Locality-Sensitive Hashing** trades exactness for speed in high-dimensional
Euclidean spaces. It projects data onto random vectors and bins points into
hash buckets — nearby points are likely to share a bucket, so only bucket
members need distance computation.

`LSH` uses the **p-stable distribution** family: the hash function is
`h(x) = ⌊(a·x + b) / w⌋` where **a** is a random Gaussian vector and
**b** is uniform in [0, w). Multiple hash tables (L) are built, each using
k independent hash functions combined into a composite key.

> **Approximate only:** LSH may miss true nearest neighbors (false negatives)
> and query `nearest()` may return `null` if no candidates are found in any
> bucket.

### Parameters

| Parameter | Meaning                                      | Guidance                        |
|-----------|----------------------------------------------|---------------------------------|
| `w`       | Bucket width                                 | Should be ≈ the typical NN distance |
| `L`       | Number of hash tables                        | More → higher recall, slower   |
| `k`       | Hash functions per table                     | More → fewer false positives    |
| `H`       | Hash table size (number of buckets)          | Should be > N                  |

### Construction

```java
// Automatic parameter selection (L ≈ N^0.25, k ≈ log10(N))
LSH<double[]> lsh = new LSH<>(data, data, 4.0);

// Explicit parameters: d=256-dim, L=50 tables, k=3 projections, w=4.0
LSH<double[]> lsh = new LSH<>(256, 50, 3, 4.0);
for (int i = 0; i < data.length; i++) {
    lsh.put(data[i], data[i]);
}
```

### Queries

```java
// Nearest (may return null if no candidates in any bucket)
Neighbor<double[], double[]> nn = lsh.nearest(q);
if (nn != null) {
    System.out.printf("Approx NN: index=%d, dist=%.4f%n", nn.index(), nn.distance());
}

// k-NN
Neighbor<double[], double[]>[] knn = lsh.search(q, 10);

// Range search
List<Neighbor<double[], double[]>> results = new ArrayList<>();
lsh.search(q, 8.0, results);
```

---

## 9. MPLSH — Multi-Probe LSH

**Multi-Probe LSH** extends basic LSH by intelligently probing *multiple*
buckets per hash table that are likely to contain the true nearest neighbors.
This achieves higher recall with far fewer hash tables than standard LSH.

`MPLSH` requires a training phase to learn the posteriori model:

```java
// Build the MPLSH structure
MPLSH<double[]> mplsh = new MPLSH<>(256, 100, 3, 4.0);
for (double[] x : data) {
    mplsh.put(x, x);
}

// Train the posteriori model on a sample of the data
double[][] trainSamples = ...; // 500–1000 random data points
LinearSearch<double[], double[]> linear = LinearSearch.of(data, MathEx::distance);
mplsh.fit(linear, trainSamples, 8.0);  // radius = search radius
```

### Queries

Multi-probe queries take two extra parameters controlling the trade-off
between speed and recall:

| Parameter | Meaning                                       |
|-----------|-----------------------------------------------|
| `recall`  | Target recall probability (e.g. 0.95 = 95%)  |
| `T`       | Maximum number of probes per hash table        |

```java
// k-NN with 95% target recall, up to 50 probes
Neighbor<double[], double[]>[] knn = mplsh.search(q, 10, 0.95, 50);

// Range search
List<Neighbor<double[], double[]>> results = new ArrayList<>();
mplsh.search(q, 8.0, results, 0.95, 50);

// Falls back to basic LSH if fit() has not been called
Neighbor<double[], double[]>[] fallback = mplsh.search(q, 10);
```

---

## 10. SNLSH — LSH for Signatures / Text

`SNLSH` (**Signature Nearest-neighbor LSH**) uses **SimHash** to convert
arbitrary objects (e.g. token sets, bag-of-words vectors) into 64-bit
signatures, then applies banded LSH on the Hamming distance between
signatures. It is ideal for near-duplicate detection in text corpora.

Distances are integers (Hamming distance in [0, 64]).

### Construction

```java
// 8 bands, text SimHash
SNLSH<String[], String> lsh = new SNLSH<>(8, SimHash.text());

for (String sentence : sentences) {
    String[] tokens = sentence.split("\\s+");
    lsh.put(tokens, sentence);
}
```

### Queries

```java
// Range search: all items within Hamming distance 10
List<Neighbor<String[], String>> results = new ArrayList<>();
String[] queryTokens = "the quick brown fox".split("\\s+");
lsh.search(queryTokens, 10, results);

for (var n : results) {
    System.out.printf("index=%d, hamming=%.0f, value=%s%n",
        n.index(), n.distance(), n.value());
}
```

---

## 11. RandomProjectionTree and Forest

**Random Projection Trees** partition space using random hyperplanes, adapting
to intrinsic data geometry. A `RandomProjectionForest` combines multiple trees
for higher recall — it is the basis of algorithms like UMAP.

### RandomProjectionTree

```java
// Euclidean: leafSize = 10 (max points per leaf)
RandomProjectionTree tree = RandomProjectionTree.of(data, 10, false);

// Angular (cosine): good for normalized vectors
RandomProjectionTree tree = RandomProjectionTree.of(data, 10, true);
```

> **Constraint:** `k ≤ leafSize` in `search(q, k)`. The tree only returns
> candidates within a single leaf; k cannot exceed how many points fit there.

```java
// k must be <= leafSize
Neighbor<double[], double[]>[] candidates = tree.search(q, 5);

// Introspect tree structure
System.out.println("Nodes:  " + tree.numNodes());
System.out.println("Leaves: " + tree.numLeaves());

// Access raw leaf samples (useful for kNN-graph construction)
List<int[]> leafSamples = tree.leafSamples();
```

### RandomProjectionForest

A forest of trees merges candidates across all trees, then selects the
k true nearest from the union set. Recall improves with more trees.

```java
// 5 trees, leafSize=10, Euclidean
RandomProjectionForest forest = RandomProjectionForest.of(data, 5, 10, false);

// Angular (for cosine similarity)
RandomProjectionForest forest = RandomProjectionForest.of(data, 10, 10, true);

// k-NN query
Neighbor<double[], double[]>[] knn = forest.search(q, 7);
```

### Building a kNN Graph

```java
// Returns a NearestNeighborGraph
RandomProjectionForest forest = RandomProjectionForest.of(data, 7, 10, false);
NearestNeighborGraph graph = forest.toGraph(7);

int[][] neighbors = graph.neighbors();   // neighbors[i] = indices of i's k-NNs
double[][] dists   = graph.distances();  // dists[i][j] = distance to neighbors[i][j]
```

> Note: `toGraph` gives **at most** k neighbors per node — sparse nodes that
> appear in few leaves may have fewer.

---

## 12. NearestNeighborGraph

`NearestNeighborGraph` (in `smile.graph`) is an immutable record representing
a precomputed kNN graph. It is produced by `RandomProjectionForest.toGraph()`
or can be built directly from dense data:

```java
// Build exact kNN graph from data using KDTree
NearestNeighborGraph graph = NearestNeighborGraph.of(data, 15, MathEx::distance);

// With a custom metric
NearestNeighborGraph graph = NearestNeighborGraph.of(data, 10, cosineDistance);

// Inspect
int k              = graph.k();
int[][] neighbors  = graph.neighbors();
double[][] dists   = graph.distances();

System.out.println("NN of vertex 0: " + Arrays.toString(neighbors[0]));
```

---

## 13. Choosing the Right Algorithm

```
Is the metric Euclidean on double[]?
│
├─ YES
│   Is d ≤ ~20 dimensions?
│   ├─ YES  →  KDTree  (exact, very fast)
│   └─ NO
│       Is N very large or memory tight?
│       ├─ YES, need approx result  →  LSH / MPLSH / RandomProjectionForest
│       └─ NO, need exact result    →  LinearSearch (may beat tree methods)
│
└─ NO
    Is the metric integer-valued (e.g. edit distance)?
    ├─ YES  →  BKTree
    └─ NO   →  CoverTree (any metric satisfying triangle inequality)

For near-duplicate text / set similarity:
    →  SNLSH (SimHash + banded Hamming)
```

### Quick reference

| N       | d    | Exact? | Recommended                       |
|---------|------|--------|-----------------------------------|
| ≤ 100k  | ≤ 10 | Yes    | `KDTree`                         |
| ≤ 100k  | ≤ 20 | Yes    | `KDTree` or `CoverTree`          |
| any     | > 20 | Yes    | `LinearSearch`                   |
| > 100k  | > 20 | No     | `LSH`, `MPLSH`, `RandomProjectionForest` |
| any     | any  | Yes    | `CoverTree` (general metric)     |
| any     | any  | Yes    | `BKTree` (integer metric only)   |
| text    | N/A  | No     | `SNLSH`                          |

---

## 14. Performance Notes

### KDTree
* Uses **squared distances** throughout traversal — `sqrt` is called only once
  per result at assembly time.
* **Early-exit** inner loop: dimension-by-dimension accumulation stops as soon
  as partial sum exceeds current best.
* Branch pruning compares `diff²` against `bestDist²` — no `Math.abs` needed.
* Dimensionality `d` is stored as a field to avoid repeated array-length
  lookups.

### LSH / MPLSH
* The `w` (bucket width) parameter has the single largest effect on
  performance. Set it to roughly the expected nearest-neighbor distance.
* For very high-recall requirements, prefer `MPLSH` with a trained posteriori
  model over many basic LSH tables.
* Call `getCandidates()` only once per query — the `BitSet`-based dedup is O(1)
  per candidate.

### CoverTree
* The `coverRadiusCache` (`IntDoubleHashMap`) caches `base^scale` values to
  avoid repeated `Math.pow` calls during both build and search.
* Performance degrades when data have high expansion constant (i.e. high
  intrinsic dimensionality). Profile with your own data before committing.

### General tips
* **Seed the RNG** with `MathEx.setSeed(seed)` before constructing any
  randomized structure (LSH, MPLSH, RandomProjectionTree/Forest) to get
  reproducible results.
* All `search(q, k)` results come out of a max-heap, so the array is in
  **descending** distance order (largest distance at index 0). Call
  `Arrays.sort(results)` for ascending order.
* All structures are **serializable** — save them to disk with Java
  serialization to avoid rebuilding on each application restart.

---

## 15. Complete Examples

### Example 1 — KDTree on USPS digit data

```java
import smile.datasets.USPS;
import smile.math.MathEx;
import smile.neighbor.*;

var usps = new USPS();
double[][] train = usps.x();
double[][] test  = usps.testx();

// Build index
KDTree<double[]> tree = KDTree.of(train);

// 1-NN classification
int correct = 0;
for (int i = 0; i < test.length; i++) {
    Neighbor<double[], double[]> nn = tree.nearest(test[i]);
    // ... compare label of nn.index() with true label of test[i]
}
```

### Example 2 — BKTree for spell-checking

```java
import smile.math.distance.EditDistance;
import smile.neighbor.*;
import java.util.ArrayList;

String[] dictionary = /* load dictionary words */;
BKTree<String, String> tree = BKTree.of(dictionary, new EditDistance(50, true));

// Find all words within edit distance 2 of "colour"
List<Neighbor<String, String>> suggestions = new ArrayList<>();
tree.search("colour", 2, suggestions);
suggestions.stream()
    .sorted()
    .forEach(n -> System.out.printf("  %s (distance %d)%n",
        n.value(), (int) n.distance()));
```

### Example 3 — LSH for high-dimensional approximate search

```java
import smile.math.MathEx;
import smile.neighbor.*;
import java.util.ArrayList;

MathEx.setSeed(19650218);
double[][] data = /* N × 256 feature vectors */;

// Build LSH index (w ≈ typical NN distance)
LSH<double[]> lsh = new LSH<>(data, data, 4.0);

// Approximate nearest neighbor
double[] q = data[0];
Neighbor<double[], double[]> nn = lsh.nearest(q);
if (nn != null) {
    System.out.printf("Approx NN: index=%d, dist=%.4f%n", nn.index(), nn.distance());
}

// Range search
List<Neighbor<double[], double[]>> neighbors = new ArrayList<>();
lsh.search(q, 8.0, neighbors);
System.out.printf("Found %d neighbors within radius 8.0%n", neighbors.size());
```

### Example 4 — MPLSH with trained posteriori model

```java
import smile.math.MathEx;
import smile.neighbor.*;

MathEx.setSeed(42);
double[][] data = /* N × 256 feature vectors */;

MPLSH<double[]> mplsh = new MPLSH<>(256, 100, 3, 4.0);
for (double[] x : data) mplsh.put(x, x);

// Train on a 500-point subsample
double[][] trainSamples = new double[500][];
int[] idx = MathEx.permutate(data.length);
for (int i = 0; i < 500; i++) trainSamples[i] = data[idx[i]];

LinearSearch<double[], double[]> linear = LinearSearch.of(data, MathEx::distance);
mplsh.fit(linear, trainSamples, 8.0);

// Query: 10-NN, target recall 95%, up to 50 probes per table
Neighbor<double[], double[]>[] results = mplsh.search(data[0], 10, 0.95, 50);
```

### Example 5 — SNLSH for near-duplicate text detection

```java
import smile.hash.SimHash;
import smile.neighbor.*;
import java.util.ArrayList;

String[] corpus = /* load documents */;

SNLSH<String[], String> lsh = new SNLSH<>(8, SimHash.text());
for (String doc : corpus) {
    String[] tokens = doc.toLowerCase().split("\\s+");
    lsh.put(tokens, doc);
}

// Find near-duplicates of a query document
String query = "The quick brown fox jumps over the lazy dog";
String[] queryTokens = query.toLowerCase().split("\\s+");
List<Neighbor<String[], String>> results = new ArrayList<>();
lsh.search(queryTokens, 10, results);  // Hamming distance ≤ 10

for (var n : results) {
    System.out.printf("Hamming=%.0f: %s%n", n.distance(), n.value());
}
```

### Example 6 — RandomProjectionForest kNN graph

```java
import smile.math.MathEx;
import smile.neighbor.*;
import smile.graph.NearestNeighborGraph;

MathEx.setSeed(19650218);
double[][] data = /* N × d data */;

// Build approximate kNN graph via random projection forest
RandomProjectionForest forest = RandomProjectionForest.of(data, 10, 15, false);
NearestNeighborGraph graph = forest.toGraph(15);

// Each row: indices of up to 15 nearest neighbors
int[][] neighbors = graph.neighbors();
double[][] dists  = graph.distances();
System.out.println("Neighbors of point 0: " + java.util.Arrays.toString(neighbors[0]));
```

---

## References

1. J. L. Bentley. *Multidimensional binary search trees used for associative searching.* CACM, 1975.
2. A. Beygelzimer, S. Kakade, and J. Langford. *Cover Trees for Nearest Neighbor.* ICML, 2006.
3. W. Burkhard and R. Keller. *Some approaches to best-match file searching.* CACM, 1973.
4. A. Andoni and P. Indyk. *Near-Optimal Hashing Algorithms for Near Neighbor Problem in High Dimensions.* FOCS, 2006.
5. Q. Lv, W. Josephson, Z. Wang, M. Charikar, and K. Li. *Multi-probe LSH: efficient indexing for high-dimensional similarity search.* VLDB, 2007.
6. M. S. Charikar. *Similarity Estimation Techniques from Rounding Algorithms.* STOC, 2002.
7. S. Dasgupta and Y. Freund. *Random projection trees and low dimensional manifolds.* STOC, 2008.


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

