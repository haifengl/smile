# SMILE — Dataset User Guide & Tutorial

This document covers the `smile.data.Dataset` interface and its concrete
implementations: `SimpleDataset`, `SparseDataset`, `BinarySparseDataset`,
and `BinarySparseSequenceDataset`.  For tabular two-dimensional data, see
[DATA_FRAME.md](DATA_FRAME.md) instead.

---

## Table of Contents

1. [Concepts](#1-concepts)
2. [SampleInstance — the atomic unit](#2-sampleinstance--the-atomic-unit)
3. [Dataset — the core interface](#3-dataset--the-core-interface)
   - [Accessing elements](#31-accessing-elements)
   - [Streaming](#32-streaming)
   - [Mini-batch iteration](#33-mini-batch-iteration)
   - [Display](#34-display)
   - [Static factory methods](#35-static-factory-methods)
4. [SimpleDataset](#4-simpledataset)
5. [SparseDataset](#5-sparsedataset)
   - [SparseArray](#51-sparsearray)
   - [Constructing a SparseDataset](#52-constructing-a-sparsedataset)
   - [Querying a SparseDataset](#53-querying-a-sparsedataset)
   - [Row normalization](#54-row-normalization)
   - [Converting to SparseMatrix](#55-converting-to-sparsematrix)
   - [Loading from file](#56-loading-from-file)
6. [BinarySparseDataset](#6-binarysparsedataset)
   - [Constructing a BinarySparseDataset](#61-constructing-a-binarysparsedataset)
   - [Querying a BinarySparseDataset](#62-querying-a-binarysparsedataset)
   - [Converting to SparseMatrix](#63-converting-to-sparsematrix)
   - [Loading from file](#64-loading-from-file)
7. [BinarySparseSequenceDataset](#7-binarysparsesequencedataset)
   - [Data model](#71-data-model)
   - [Constructing](#72-constructing)
   - [Accessing sequences](#73-accessing-sequences)
   - [Flattened access](#74-flattened-access)
   - [Loading from file](#75-loading-from-file)
8. [Choosing the right class](#8-choosing-the-right-class)
9. [End-to-end tutorials](#9-end-to-end-tutorials)
   - [Text classification with SparseDataset](#91-text-classification-with-sparsedataset)
   - [Market-basket analysis with BinarySparseDataset](#92-market-basket-analysis-with-binarysparsedataset)
   - [Sequence labelling with BinarySparseSequenceDataset](#93-sequence-labelling-with-binarysparsesequencedataset)
10. [API quick reference](#10-api-quick-reference)

---

## 1. Concepts

```
Dataset<D, T>                      interface
│
├── SimpleDataset<D, T>            ArrayList-backed in-memory store
│     ├── SparseDataset<T>         D = SparseArray   (real-valued sparse rows)
│     └── BinarySparseDataset<T>   D = int[]         (binary sparse rows)
│           └── BinarySparseSequenceDataset
│                                  D = int[][], T = int[]
│                                  (sequences of binary sparse frames)
│
SampleInstance<D, T>               record  { D x;  T y; }
```

The generic parameters mean:

| Parameter | Role | Examples |
|-----------|------|---------|
| `D` | the **data** (feature) type | `double[]`, `SparseArray`, `int[]`, `int[][]`, any POJO |
| `T` | the **target** (label) type | `Integer`, `Double`, `String`, `int[]`, `null`/`Void` for unlabelled data |

Every item in a `Dataset` is a `SampleInstance<D, T>` — an immutable pair of
a data object `x` and an optional target label `y`.

---

## 2. SampleInstance — the atomic unit

```java
import smile.data.SampleInstance;

// Labelled: feature vector + class label
SampleInstance<double[], Integer> s = new SampleInstance<>(features, 1);

// Unlabelled: no target
SampleInstance<double[], Void> u = new SampleInstance<>(features);
// equivalent to new SampleInstance<>(features, null)

// Access
double[] x = s.x();   // feature data
Integer  y = s.y();   // target label (may be null)
```

`SampleInstance` is a Java **record**, so it is immutable and provides
a readable `toString`.

---

## 3. Dataset — the core interface

```java
import smile.data.Dataset;
import smile.data.SampleInstance;
```

`Dataset<D, T>` extends `Iterable<SampleInstance<D, T>>` and adds random
access, streaming, mini-batch iteration, and a handful of static factory
methods.

### 3.1 Accessing elements

```java
Dataset<double[], Integer> ds = /* … */;

int n = ds.size();
boolean empty = ds.isEmpty();

// Random access
SampleInstance<double[], Integer> item  = ds.get(0);
SampleInstance<double[], Integer> item  = ds.apply(0);   // Scala alias

double[] features = item.x();
int      label    = item.y();
```

### 3.2 Streaming

```java
// Sequential stream
ds.stream().forEach(s -> System.out.println(s.y()));

// Count per class
ds.stream()
  .collect(java.util.stream.Collectors.groupingBy(
      SampleInstance::y,
      java.util.stream.Collectors.counting()))
  .forEach((label, count) ->
      System.out.printf("class %d: %d samples%n", label, count));
```

### 3.3 Mini-batch iteration

`batch(int size)` returns a shuffled iterator of fixed-size sublists.  Every
call to the iterator reshuffles the dataset, making it suitable for
stochastic gradient descent.

```java
Iterator<List<SampleInstance<double[], Integer>>> it = ds.batch(32);
while (it.hasNext()) {
    List<SampleInstance<double[], Integer>> batch = it.next();
    // process batch …
}
```

The last batch may be smaller than `size` if `ds.size()` is not a multiple
of `size`.

### 3.4 Display

```java
// Show first 10 items
System.out.println(ds.toString(10));
// Shows the first 10 SampleInstance.toString() values, plus "N more rows..."
```

### 3.5 Static factory methods

`Dataset` provides several convenience factories that all return a
`SimpleDataset`:

```java
import java.util.List;

// From a collection of SampleInstances
Dataset<double[], Integer> ds = Dataset.of(instances);

// From parallel data and target lists
Dataset<double[], Integer> ds = Dataset.of(dataList, targetList);

// From parallel data and target arrays
Dataset<double[], Integer> ds = Dataset.of(dataArray, targetArray);

// Primitive target variants (auto-boxed to Integer / Float / Double)
Dataset<double[], Integer> ds = Dataset.of(dataArray, intTargets);
Dataset<double[], Float>   ds = Dataset.of(dataArray, floatTargets);
Dataset<double[], Double>  ds = Dataset.of(dataArray, doubleTargets);
```

---

## 4. SimpleDataset

`SimpleDataset<D, T>` is the default in-memory implementation of `Dataset`.
It stores all samples in an `ArrayList` on the heap and is the backing class
for all four concrete types.

```java
import smile.data.SimpleDataset;
import java.util.List;

// Build from a pre-formed list of SampleInstances
List<SampleInstance<double[], String>> instances = List.of(
    new SampleInstance<>(new double[]{1.0, 2.0}, "cat"),
    new SampleInstance<>(new double[]{3.0, 4.0}, "dog"),
    new SampleInstance<>(new double[]{5.0, 6.0}, "cat")
);
SimpleDataset<double[], String> ds = new SimpleDataset<>(instances);

System.out.println(ds.size());         // 3
System.out.println(ds.get(1).y());     // "dog"
ds.stream().map(SampleInstance::y).forEach(System.out::println);
```

Use `SimpleDataset` directly when your data is already dense (e.g.
`double[]`, `float[]`, image tensors, embedding vectors) and you simply need
a labelled container that supports random access and mini-batch iteration.

---

## 5. SparseDataset

`SparseDataset<T>` stores rows as `SparseArray` objects — each row is a
sorted list of `(column-index, double-value)` pairs.  Only non-zero entries
are stored, making it efficient for high-dimensional, sparse data such as
**TF-IDF document vectors**, **user–item interaction matrices**, or any
feature matrix where most entries are zero.

The class uses the **LIL (List of Lists)** sparse format internally, which
supports efficient incremental row construction.  When matrix operations are
needed, convert to **CCS (Compressed Column Storage)** via `toMatrix()`.

### 5.1 SparseArray

`smile.util.SparseArray` is the per-row building block.

```java
import smile.util.SparseArray;

SparseArray row = new SparseArray();

// Append in any order (will be sorted when needed)
row.append(5,  1.5);
row.append(0,  0.9);
row.append(12, 3.0);

// Or use set() for random updates
row.set(0, 2.0);       // overwrite column 0

// Read a value (returns 0.0 for absent columns)
double v = row.get(5);   // 1.5

// Iterate over non-zero entries
for (SparseArray.Entry e : row) {
    System.out.printf("col %d = %.3f%n", e.index(), e.value());
}

// Stream API
double sumSquares = row.valueStream().map(x -> x * x).sum();
int    nnz        = row.size();
```

### 5.2 Constructing a SparseDataset

#### From an array of SparseArrays (no labels)

```java
import smile.data.SparseDataset;

SparseArray[] rows = new SparseArray[3];
rows[0] = new SparseArray(); rows[0].append(0, 0.9); rows[0].append(1, 0.4);
rows[1] = new SparseArray(); rows[1].append(0, 0.4); rows[1].append(1, 0.5); rows[1].append(2, 0.3);
rows[2] = new SparseArray(); rows[2].append(1, 0.3); rows[2].append(2, 0.8);

SparseDataset<Void> ds = SparseDataset.of(rows);
```

#### From SampleInstances with labels

```java
import java.util.List;

List<SampleInstance<SparseArray, Integer>> labelled = List.of(
    new SampleInstance<>(rows[0], 0),
    new SampleInstance<>(rows[1], 1),
    new SampleInstance<>(rows[2], 1)
);
SparseDataset<Integer> ds = new SparseDataset<>(labelled);
```

#### Specifying the number of columns explicitly

When you know the column count upfront (e.g., loading a pre-split test set
whose vocabulary is already fixed from training data), pass it as the second
argument to avoid automatic discovery:

```java
SparseDataset<Void> ds = new SparseDataset<>(instances, 6906);
```

### 5.3 Querying a SparseDataset

```java
int nrow  = ds.nrow();     // number of rows (== ds.size())
int ncol  = ds.ncol();     // number of columns (max column index + 1)
int nnz   = ds.nz();       // total number of non-zero entries
int col5  = ds.nz(5);      // non-zero count in column 5

// Cell access
double v = ds.get(0, 1);   // value at row 0, column 1 (0.0 if absent)

// Row access (as SparseArray)
SparseArray row0 = ds.get(0).x();

// Label access
Integer label = ds.get(0).y();   // null for Void-typed datasets
```

### 5.4 Row normalization

```java
// L2 normalize each row (unit Euclidean norm)
ds.unitize();

// L1 normalize each row (unit Manhattan norm)
ds.unitize1();
```

Both methods modify the `SparseArray` values **in-place**.  Apply them
after constructing the dataset and before converting to a matrix or feeding
into an algorithm.

### 5.5 Converting to SparseMatrix

`toMatrix()` converts the LIL representation to the **Harwell-Boeing
Compressed Column Storage** (`SparseMatrix`) format, which is required by
SMILE's linear algebra routines.

```java
import smile.tensor.SparseMatrix;

SparseMatrix sm = ds.toMatrix();

int nrow = sm.nrow();
int ncol = sm.ncol();
int nnz  = sm.length();
double v = sm.get(0, 1);
```

> **Note:** `toMatrix()` always allocates a new `SparseMatrix`.  Call it
> once, cache the result, and reuse it — do not call it in a loop.

### 5.6 Loading from file

`SparseDataset.from(Path)` reads the **coordinate triple format**:

```
D W N          ← optional header: rows, columns, non-zero entries
instanceID attributeID value
instanceID attributeID value
...
```

All IDs in the header and data lines use **1-based** indices by default
(common in Matlab / R output and LIBSVM-style files).

```java
import java.nio.file.Paths;

// 1-based indices (default)
SparseDataset<Void> ds = SparseDataset.from(Paths.get("kos.txt"), 1);

// 0-based indices (C/Java style)
SparseDataset<Void> ds = SparseDataset.from(Paths.get("data.txt"), 0);

// Convenience overload (uses 0-based)
SparseDataset<Void> ds = SparseDataset.from(Paths.get("data.txt"));

System.out.printf("rows=%d  cols=%d  nnz=%d%n",
    ds.nrow(), ds.ncol(), ds.nz());
```

---

## 6. BinarySparseDataset

`BinarySparseDataset<T>` is a specialization where every feature is either
**0 or 1**.  Each row is stored as a **sorted `int[]`** of the column
indices where the feature is 1.  This is far more memory-efficient than
`SparseDataset` for data such as:

- **Market-basket / transaction data** (items bought = 1, items not bought = 0)
- **Document presence/absence** features (word present = 1, absent = 0)
- **Set membership** problems

### 6.1 Constructing a BinarySparseDataset

#### From a 2-D int array (no labels)

Each row of the array lists the **indices of active (1) features** in any
order; they are sorted automatically.

```java
import smile.data.BinarySparseDataset;

int[][] transactions = {
    {1, 2, 3},          // items 1, 2, 3 were purchased
    {2, 4},             // items 2, 4 were purchased
    {1, 3, 4, 5}        // items 1, 3, 4, 5 were purchased
};
BinarySparseDataset<Void> ds = BinarySparseDataset.of(transactions);
```

#### From SampleInstances with labels

```java
List<SampleInstance<int[], Integer>> labelled = List.of(
    new SampleInstance<>(new int[]{1, 2, 3}, 0),
    new SampleInstance<>(new int[]{2, 4},    1),
    new SampleInstance<>(new int[]{1, 3, 4}, 0)
);
BinarySparseDataset<Integer> ds = new BinarySparseDataset<>(labelled);
```

### 6.2 Querying a BinarySparseDataset

```java
int ncol  = ds.ncol();     // max column index + 1
int nnz   = ds.length();   // total number of 1 entries

// Binary cell access (binary search — O(log k) where k = row nnz)
int v = ds.get(0, 1);      // 1 if feature 1 is active in row 0, else 0

// Row access (as int[])
int[] row0 = ds.get(0).x();

// Label access
Integer label = ds.get(0).y();
```

### 6.3 Converting to SparseMatrix

```java
SparseMatrix sm = ds.toMatrix();
// All non-zero entries have value 1.0
double v = sm.get(0, 1);   // 1.0 or 0.0
```

### 6.4 Loading from file

`BinarySparseDataset.from(Path)` reads a text file where each line is a
whitespace-separated list of **active feature indices** (0-based):

```
1 2 3
2 4
1 3 4 5
```

```java
import java.nio.file.Paths;

BinarySparseDataset<Void> ds = BinarySparseDataset.from(
    Paths.get("kosarak.dat"));

System.out.printf("transactions=%d  items=%d  total_purchases=%d%n",
    ds.size(), ds.ncol(), ds.length());
```

---

## 7. BinarySparseSequenceDataset

`BinarySparseSequenceDataset` handles **sequences** where each step in the
sequence is itself a binary sparse feature vector.  This is the data model
for structured-output tasks such as:

- **Named Entity Recognition (NER)** — each token has binary indicator
  features (capitalized? ends-in-s? preceded by "Mr."?) and a label
  (PERSON, ORG, …)
- **Part-of-Speech tagging** — each word position has features and a POS tag
- **Any CRF / HMM sequence labelling** task

### 7.1 Data model

```
Dataset item i:
  x  =  int[][]  — shape [sequence_length, num_features]
                    each x[j] is the binary sparse feature vector
                    for position j in the sequence
  y  =  int[]    — shape [sequence_length]
                    class label for each position

Internally also provides:
  seq[i][j]  =  Tuple  — x[j] as a Tuple with NominalScale fields
  tag[i][j]  =  int    — y[j]
```

### 7.2 Constructing

Build `SampleInstance<int[][], int[]>` objects manually and pass to the
constructor along with the feature dimensionality `p` and the class count `k`:

```java
import smile.data.BinarySparseSequenceDataset;

// A toy sequence of length 3, each with 4 binary features, 3 classes
int[][] x1 = {
    {0, 2},     // position 0: features 0 and 2 are active
    {1, 3},     // position 1: features 1 and 3 are active
    {0, 1, 2}   // position 2: features 0, 1 and 2 are active
};
int[] y1 = {0, 1, 2};  // label per position

List<SampleInstance<int[][], int[]>> data = List.of(
    new SampleInstance<>(x1, y1)
);

int p = 4;  // number of distinct feature indices
int k = 3;  // number of class labels
BinarySparseSequenceDataset ds = new BinarySparseSequenceDataset(p, k, data);
```

The constructor:
1. Discovers distinct values per feature position across all sequences.
2. Builds a `StructType` schema with one `StructField` per feature column,
   each annotated with a `NominalScale`.
3. Wraps each `int[]` frame as a `Tuple` using that schema.

### 7.3 Accessing sequences

```java
int p = ds.p();   // number of features
int k = ds.k();   // number of classes

// Sequence access
Tuple[][] seqs = ds.seq();     // all sequences as Tuple arrays
int[][]   tags  = ds.tag();    // all label sequences

// Single sequence
Tuple[] seq0 = seqs[0];         // sequence 0
int[]   tag0  = tags[0];        // labels for sequence 0

// Single position
Tuple pos0_0 = seq0[0];         // position 0 in sequence 0
int   label  = tag0[0];         // label for position 0

// Feature value via Tuple accessor
int feature2 = pos0_0.getInt(2);
```

### 7.4 Flattened access

For algorithms that process all positions independently (e.g., maximum-
entropy classifiers used as the emission model in a CRF):

```java
// Flatten all sequences into one int[][] array
int[][] allX = ds.x();

// Flatten all labels
int[] allY = ds.y();

System.out.printf("total positions: %d%n", allX.length);
```

### 7.5 Loading from file

`BinarySparseSequenceDataset.load(Path)` reads a structured text format:

**Header line:**
```
N K P
```
where `N` = number of sequences, `K` = number of classes, `P` = number of
features.

**Data lines** (one per sequence position):
```
seqID  pos  len  feat1  feat2  …  featLen  label
```

| Field | Meaning |
|-------|---------|
| `seqID` | Sequence identifier (0-based, must increase monotonically) |
| `pos` | Position within the sequence |
| `len` | Number of active features for this position |
| `feat1…featLen` | Active feature indices |
| `label` | Class label for this position |

Example file (2 sequences, 3 classes, 5 features):

```
2 3 5
0 0 2 1 3 0
0 1 1 2 1
1 0 3 0 2 4 2
1 1 2 1 3 0
```

```java
import java.nio.file.Paths;

BinarySparseSequenceDataset ds =
    BinarySparseSequenceDataset.load(Paths.get("sequences.txt"));

System.out.printf("sequences=%d  classes=%d  features=%d%n",
    ds.size(), ds.k(), ds.p());
```

---

## 8. Choosing the right class

| Data characteristics | Recommended class |
|---|---|
| Dense features (`double[]`, image, embedding) | `SimpleDataset` |
| Sparse real-valued features (TF-IDF, ratings) | `SparseDataset` |
| Binary/presence-absence features (transactions, text BoW) | `BinarySparseDataset` |
| Sequences of binary sparse frames (NER, POS tagging) | `BinarySparseSequenceDataset` |

**Memory comparison** for a dataset with 10 000 rows, 50 000 columns, and
on average 100 non-zero entries per row:

| Class | Memory per row |
|-------|----------------|
| Dense `double[]` | 50 000 × 8 B = **400 KB** |
| `SparseDataset` (`SparseArray`) | 100 × (4 + 8) B = **1.2 KB** |
| `BinarySparseDataset` (`int[]`) | 100 × 4 B = **400 B** |

---

## 9. End-to-end tutorials

### 9.1 Text classification with SparseDataset

This tutorial builds a bag-of-words TF-IDF classifier from a corpus of
labelled documents.

```java
import smile.data.*;
import smile.util.SparseArray;
import smile.tensor.SparseMatrix;
import java.util.*;

// -----------------------------------------------------------------
// Step 1 — Tokenise and build vocabulary
// -----------------------------------------------------------------
List<String[]> tokenised = tokenise(documents);  // your tokeniser
Map<String, Integer> vocab = buildVocab(tokenised);

// -----------------------------------------------------------------
// Step 2 — Build TF-IDF SparseArrays
// -----------------------------------------------------------------
List<SampleInstance<SparseArray, Integer>> instances = new ArrayList<>();
for (int i = 0; i < tokenised.length; i++) {
    SparseArray row = computeTFIDF(tokenised[i], vocab, idfWeights);
    instances.add(new SampleInstance<>(row, labels[i]));
}

SparseDataset<Integer> train = new SparseDataset<>(
    instances.subList(0, splitPoint));
SparseDataset<Integer> test  = new SparseDataset<>(
    instances.subList(splitPoint, instances.size()),
    train.ncol());    // fix column count to training vocabulary size

// -----------------------------------------------------------------
// Step 3 — L2 normalize
// -----------------------------------------------------------------
train.unitize();
test.unitize();

// -----------------------------------------------------------------
// Step 4 — Convert to matrix and train
// -----------------------------------------------------------------
SparseMatrix X_train = train.toMatrix();

int[]    y_train = train.stream()
        .mapToInt(s -> s.y()).toArray();
int[]    y_test  = test.stream()
        .mapToInt(s -> s.y()).toArray();

System.out.printf("train: %d rows, %d cols, %d nnz%n",
    train.nrow(), train.ncol(), train.nz());
```

### 9.2 Market-basket analysis with BinarySparseDataset

```java
import smile.data.*;
import smile.tensor.SparseMatrix;
import java.nio.file.Paths;

// -----------------------------------------------------------------
// Load transaction data: each line = space-separated item IDs
// -----------------------------------------------------------------
BinarySparseDataset<Void> transactions =
    BinarySparseDataset.from(Paths.get("kosarak.dat"));

System.out.printf("Transactions : %d%n", transactions.size());
System.out.printf("Unique items : %d%n", transactions.ncol());
System.out.printf("Total events : %d%n", transactions.length());

// -----------------------------------------------------------------
// Compute item co-occurrence via SparseMatrix  (X^T X)
// -----------------------------------------------------------------
SparseMatrix X = transactions.toMatrix();
// X is (transactions × items); X^T * X is (items × items) co-occurrence

// -----------------------------------------------------------------
// Inspect a specific transaction
// -----------------------------------------------------------------
int[] basket = transactions.get(0).x();
System.out.println("First basket items: " + Arrays.toString(basket));

// -----------------------------------------------------------------
// Check whether item 1056 was in transaction 990001
// -----------------------------------------------------------------
int bought = transactions.get(990001, 1056);
System.out.printf("Item 1056 in tx 990001: %s%n", bought == 1 ? "yes" : "no");

// -----------------------------------------------------------------
// Mini-batch iteration for online learning
// -----------------------------------------------------------------
Iterator<List<SampleInstance<int[], Void>>> it = transactions.batch(1000);
while (it.hasNext()) {
    List<SampleInstance<int[], Void>> batch = it.next();
    processBatch(batch);  // your incremental update
}
```

### 9.3 Sequence labelling with BinarySparseSequenceDataset

```java
import smile.data.*;
import java.nio.file.Paths;

// -----------------------------------------------------------------
// Load a CoNLL-style binary sequence dataset
// -----------------------------------------------------------------
BinarySparseSequenceDataset ds =
    BinarySparseSequenceDataset.load(Paths.get("ner_train.txt"));

System.out.printf("Sequences : %d%n", ds.size());
System.out.printf("Classes   : %d%n", ds.k());
System.out.printf("Features  : %d%n", ds.p());

// -----------------------------------------------------------------
// Extract sequences and tags for a CRF / Viterbi decoder
// -----------------------------------------------------------------
Tuple[][] sequences = ds.seq();
int[][]   labels    = ds.tag();

for (int i = 0; i < sequences.length; i++) {
    Tuple[] seq = sequences[i];
    int[]   tag = labels[i];
    System.out.printf("Sequence %d: length=%d%n", i, seq.length);
    for (int j = 0; j < seq.length; j++) {
        System.out.printf("  pos %d  label=%d  features=%s%n",
            j, tag[j], seq[j]);
    }
}

// -----------------------------------------------------------------
// Flatten for an emission-only classifier
// -----------------------------------------------------------------
int[][] X = ds.x();
int[]   y = ds.y();
System.out.printf("Total positions: %d%n", X.length);

// -----------------------------------------------------------------
// Mini-batch for stochastic training
// -----------------------------------------------------------------
var it = ds.batch(16);
while (it.hasNext()) {
    var batch = it.next();
    // each SampleInstance<int[][], int[]> is one full sequence
    for (var instance : batch) {
        int[][] xi = instance.x();  // sequence frames
        int[]   yi = instance.y();  // per-frame labels
        trainStep(xi, yi);
    }
}
```

---

## 10. API quick reference

### SampleInstance\<D, T\>

| Method | Description |
|--------|-------------|
| `new SampleInstance<>(D x, T y)` | Labelled instance |
| `new SampleInstance<>(D x)` | Unlabelled (y = null) |
| `x()` | Feature data |
| `y()` | Target label (may be null) |

### Dataset\<D, T\> (interface)

| Method | Returns | Description |
|--------|---------|-------------|
| `size()` | `int` | Number of instances |
| `isEmpty()` | `boolean` | True if no instances |
| `get(int i)` | `SampleInstance<D,T>` | Instance at index i |
| `apply(int i)` | `SampleInstance<D,T>` | Scala alias for get |
| `stream()` | `Stream<SampleInstance<D,T>>` | Sequential stream |
| `iterator()` | `Iterator<SampleInstance<D,T>>` | Java for-each iterator |
| `batch(int size)` | `Iterator<List<SampleInstance<D,T>>>` | Shuffled mini-batch iterator |
| `toList()` | `List<SampleInstance<D,T>>` | All instances as list |
| `toString(int numRows)` | `String` | First N instances printed |
| `Dataset.of(Collection)` | `Dataset<D,T>` | Factory from collection |
| `Dataset.of(List, List)` | `Dataset<D,T>` | Factory from parallel lists |
| `Dataset.of(D[], T[])` | `Dataset<D,T>` | Factory from parallel arrays |
| `Dataset.of(D[], int[])` | `Dataset<D,Integer>` | Factory with int targets |
| `Dataset.of(D[], float[])` | `Dataset<D,Float>` | Factory with float targets |
| `Dataset.of(D[], double[])` | `Dataset<D,Double>` | Factory with double targets |

### SimpleDataset\<D, T\>

| Constructor | Description |
|-------------|-------------|
| `new SimpleDataset<>(Collection<SampleInstance<D,T>>)` | From any collection |

Inherits all `Dataset` methods.

### SparseDataset\<T\>

| Method / Constructor | Description                           |
|---------------------|---------------------------------------|
| `new SparseDataset<>(Collection<SampleInstance<SparseArray,T>>)` | Auto-detect ncol                      |
| `new SparseDataset<>(Collection<SampleInstance<SparseArray,T>>, int ncol)` | Explicit ncol                         |
| `SparseDataset.of(SparseArray[])` | Unlabelled factory                    |
| `SparseDataset.of(SparseArray[], int ncol)` | Unlabelled factory with explicit ncol |
| `SparseDataset.from(Path)` | Load coordinate-triple file (0-based) |
| `SparseDataset.from(Path, int origin)` | Load with explicit index origin       |
| `nrow()` | Number of rows (= size())             |
| `ncol()` | Number of columns                     |
| `nz()` | Total non-zero entries                |
| `nz(int j)` | Non-zero entries in column j          |
| `get(int i, int j)` | Value at (i, j)                       |
| `unitize()` | L2-normalize each row in-place        |
| `unitize1()` | L1-normalize each row in-place       |
| `toMatrix()` | Convert to CCS `SparseMatrix`         |

### BinarySparseDataset\<T\>

| Method / Constructor | Description |
|---------------------|-------------|
| `new BinarySparseDataset<>(Collection<SampleInstance<int[],T>>)` | From instances |
| `BinarySparseDataset.of(int[][])` | Unlabelled factory |
| `BinarySparseDataset.from(Path)` | Load one-row-per-line index file |
| `ncol()` | Number of columns (max index + 1) |
| `length()` | Total number of 1 entries |
| `get(int i, int j)` | Binary value at (i, j) via binary search |
| `toMatrix()` | Convert to CCS `SparseMatrix` (values are 1.0) |

### BinarySparseSequenceDataset

| Method / Constructor | Description |
|---------------------|-------------|
| `new BinarySparseSequenceDataset(int p, int k, List<SampleInstance<int[][], int[]>>)` | Construct with feature count and class count |
| `BinarySparseSequenceDataset.load(Path)` | Load from structured text format |
| `p()` | Number of features per sequence element |
| `k()` | Number of class labels |
| `seq()` | All sequences as `Tuple[][]` |
| `tag()` | All label sequences as `int[][]` |
| `x()` | Flattened feature frames `int[][]` |
| `y()` | Flattened label array `int[]` |

---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

