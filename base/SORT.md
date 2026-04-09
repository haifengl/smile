# SMILE — Sorting and Selection Algorithms User Guide

## Overview

The `smile.sort` package provides high-performance, allocation-free sorting and selection
primitives that are used throughout the SMILE library. It covers four concerns:

| Class / Interface | Purpose |
|---|---|
| `ShellSort` | In-place O(n log² n) sort for small arrays |
| `QuickSort` | Iterative Quicksort with optional co-sorting of a companion array |
| `QuickSelect` | O(n) k-th-smallest selection, median, quartiles |
| `HeapSelect<T>` | Single-pass top-k selection from a data stream (generic) |
| `IntHeapSelect` | Single-pass top-k selection from a data stream (`int`) |
| `FloatHeapSelect` | Single-pass top-k selection from a data stream (`float`) |
| `DoubleHeapSelect` | Single-pass top-k selection from a data stream (`double`) |
| `IQAgent` | Incremental quantile estimation from an unbounded data stream |

---

## ShellSort

Shell sort is a gap-based generalization of insertion sort using Knuth's gap sequence
(`1, 4, 13, 40, 121, …`). It is in-place and requires no auxiliary memory.

**Complexity:** O(n log² n) average/worst.  
**Best suited for:** arrays of up to a few thousand elements.

```java
int[]     ai = {5, 3, 1, 4, 2};
float[]   af = {5.0f, 3.0f, 1.0f};
double[]  ad = {5.0, 3.0, 1.0, 4.0, 2.0};
String[]  as = {"banana", "apple", "cherry"};

ShellSort.sort(ai);   // {1, 2, 3, 4, 5}
ShellSort.sort(af);   // {1.0, 3.0, 5.0}
ShellSort.sort(ad);   // {1.0, 2.0, 3.0, 4.0, 5.0}
ShellSort.sort(as);   // {"apple", "banana", "cherry"}
```

All four overloads sort the array **in ascending order in-place**.  
The generic overload accepts any `T extends Comparable<? super T>`.

---

## QuickSort

An iterative (non-recursive) Quicksort using a fixed-size stack and median-of-three
pivot selection. Sub-arrays of ≤ 7 elements are finished with insertion sort.

**Complexity:** O(n log n) average, O(n²) worst-case (rare with median-of-three).  
**Auxiliary space:** O(log n) (stack of size 64).

### Sorting a single array and obtaining the original indices

```java
double[] x = {3.0, 1.0, 4.0, 1.0, 5.0, 9.0};
int[] order = QuickSort.sort(x);
// x     → {1.0, 1.0, 3.0, 4.0, 5.0, 9.0}
// order → original index of each element before sorting
```

Available for `int[]`, `float[]`, `double[]`, and `T[]`.

### Co-sorting a companion array

The most common use case: sort one array while keeping a second array synchronised
(e.g. sort feature values while dragging along sample labels).

```java
int[]    x = {5, 2, 8, 1};
double[] y = {50.0, 20.0, 80.0, 10.0};
QuickSort.sort(x, y);
// x → {1, 2, 5, 8}
// y → {10.0, 20.0, 50.0, 80.0}
```

Co-sort overloads available:

| Key array | Companion array |
|---|---|
| `int[]`    | `int[]`, `double[]`, `Object[]` |
| `float[]`  | `int[]`, `float[]`, `Object[]`  |
| `double[]` | `int[]`, `double[]`, `Object[]` |
| `T[]`      | `int[]`                         |

Every overload also accepts an explicit `n` parameter to sort only the **first n
elements**:

```java
int[] x = {5, 3, 1, 4, 2, 9, 8};
int[] y = {50, 30, 10, 40, 20, 90, 80};
QuickSort.sort(x, y, 5);   // only first 5 elements sorted
```

---

## QuickSelect

QuickSelect finds the k-th smallest element in O(n) average time by partitioning the
array — the same pivot mechanism used in Quicksort, but only recursing into one half.

> **Note:** `QuickSelect` **modifies the input array** in-place (partial partition).
> Clone the array first if the original order must be preserved.

### select — arbitrary k

```java
int[]    xi = {7, 3, 5, 1, 9, 2};
double[] xd = {7.0, 3.0, 5.0, 1.0, 9.0, 2.0};

int    kMin = QuickSelect.select(xi, 0);   // 1  (minimum)
double kMax = QuickSelect.select(xd, 5);   // 9.0 (maximum)
```

Available for `int[]`, `float[]`, `double[]`, and `T[]`.

### median

Returns the element at index `n/2` after partial partition — the lower median for
even-length arrays.

```java
int[]    data = {5, 2, 3, 4, 1, 6, 7, 8, 9};
int      med  = QuickSelect.median(data);   // 5
```

Available for `int[]`, `float[]`, `double[]`, and `T[]`.

### Quartiles: q1 and q3

```java
double[] data = {1, 2, 3, 4, 5, 6, 7, 8};

double q1 = QuickSelect.q1(data);   // element at index n/4  → 3.0
double q3 = QuickSelect.q3(data);   // element at index 3n/4 → 7.0
```

Available for `int[]`, `float[]`, `double[]`, and `T[]`.

### Invariant

For any array `x`:

```java
double[] copy1 = x.clone(), copy2 = x.clone(), copy3 = x.clone();
assert QuickSelect.q1(copy1) <= QuickSelect.median(copy2);
assert QuickSelect.median(copy2) <= QuickSelect.q3(copy3);
```

---

## HeapSelect — Top-k from a Stream

The four `HeapSelect` variants maintain a **max-heap of the k smallest elements** seen
so far in a data stream. Each new value is processed in O(log k) time. When the stream
ends, `get(i)` returns the i-th smallest value in O(k log k) time.

| Class | Element type |
|---|---|
| `HeapSelect<T>` | Generic `T extends Comparable` |
| `IntHeapSelect` | `int` |
| `FloatHeapSelect` | `float` |
| `DoubleHeapSelect` | `double` |

### How it works

The heap always stores the **k smallest values encountered**. The root (`peek()`) is
the **largest** of those k values — the current k-th smallest boundary. When a new
value arrives that is smaller than the root, it replaces the root and the heap is
restored with `siftDown`.

```
Stream: 9, 2, 7, 1, 5, 8, 3    (k = 3)

After processing all values, heap holds {1, 2, 3}.
peek() → 3  (the 3rd smallest = k-th boundary)
get(0) → 1  (smallest)
get(1) → 2
get(2) → 3  (== peek())
```

### Construction and use

```java
// Generic
HeapSelect<Integer> hs = new HeapSelect<>(Integer.class, 5);

// Primitives (faster, no boxing)
IntHeapSelect    ihs = new IntHeapSelect(5);
FloatHeapSelect  fhs = new FloatHeapSelect(5);
DoubleHeapSelect dhs = new DoubleHeapSelect(5);
```

```java
DoubleHeapSelect top5 = new DoubleHeapSelect(5);

// Feed values one at a time
for (double v : stream) {
    top5.add(v);
}

// Query after the stream ends
int count = top5.size();           // total values seen (not bounded by k)
double boundary = top5.peek();     // largest of the 5 smallest (k-th smallest)
double smallest = top5.get(0);     // overall minimum seen
double second   = top5.get(1);     // 2nd smallest
double[] arr    = top5.toArray();  // copy of the k values in heap order

// Force a sort of the internal array
top5.sort();
// Now get(0) ≤ get(1) ≤ … ≤ get(k-1)
```

### `peek()` vs `get(i)`

| Method | Cost | Returns |
|---|---|---|
| `peek()` | O(1) | k-th smallest (root of max-heap) |
| `get(k-1)` | O(1) | same as `peek()` — fast path |
| `get(i)` for `i < k-1` | O(k log k) first call, O(1) subsequent | i-th smallest |

### Partial stream (fewer than k values)

When fewer than k values have been added, `get(i)` still works correctly — it treats
the current number of values as the effective k:

```java
DoubleHeapSelect hs = new DoubleHeapSelect(10);
hs.add(7.0);
hs.add(3.0);
hs.add(5.0);
// Only 3 values: get(0)=3.0, get(1)=5.0, get(2)=7.0
```

### Typical use: nearest-neighbour search

```java
// Find the 10 nearest neighbours to a query point
DoubleHeapSelect topK = new DoubleHeapSelect(10);
for (int i = 0; i < dataset.length; i++) {
    double dist = euclidean(query, dataset[i]);
    topK.add(dist);
}
topK.sort();
for (int i = 0; i < 10; i++) {
    System.out.printf("rank %d: dist=%.4f%n", i + 1, topK.get(i));
}
```

---

## IQAgent — Incremental Quantile Estimation

`IQAgent` estimates arbitrary quantiles from an unbounded data stream using a single
pass with constant memory, based on the algorithm of Chambers et al. (2006).

It maintains 251 quantile markers and processes incoming values in batches (`nbuf`,
default 1000). Each batch flush is O(nbuf log nbuf). Memory use is O(nq + nbuf) ≈
O(1000) regardless of the stream length.

### Construction

```java
IQAgent agent = new IQAgent();         // default batch size 1000
IQAgent agent = new IQAgent(10000);    // larger batch — better accuracy for very long streams
```

Use a larger batch size when you expect more than ~1,000,000 values and need higher
accuracy.

### Feeding data

```java
for (double value : dataStream) {
    agent.add(value);
}
```

### Querying quantiles

```java
double p50 = agent.quantile(0.50);   // median
double p25 = agent.quantile(0.25);   // first quartile
double p75 = agent.quantile(0.75);   // third quartile
double p99 = agent.quantile(0.99);   // 99th percentile
double min = agent.quantile(0.0);    // ≈ minimum
double max = agent.quantile(1.0);    // ≈ maximum
```

`quantile(p)` can be called **at any time** during or after the stream — there is no
need to wait until all data has been fed. The result is automatically flushed if there
is a pending partial batch.

### Accuracy

On a uniform distribution of 100,000 values, each percentile is accurate to within
±1% of the true value. Accuracy improves with larger batch sizes and more data.

```java
IQAgent agent = new IQAgent();
for (int i = 1; i <= 100_000; i++) agent.add(i);

// p=0.25 → ≈ 25,000  (±1%)
// p=0.50 → ≈ 50,000  (±1%)
// p=0.75 → ≈ 75,000  (±1%)
```

### Monotonicity guarantee

Quantile estimates are **non-decreasing** in p: `quantile(p1) ≤ quantile(p2)` for
`p1 ≤ p2`. This makes the estimates safe to use as interval bounds.

---

## Choosing the Right Tool

| Need | Use |
|---|---|
| Sort a small array (< 1,000) in-place | `ShellSort` |
| Sort a large array in-place | `QuickSort` |
| Sort array A while keeping array B aligned | `QuickSort.sort(A, B)` |
| Find the k-th smallest in an array | `QuickSelect.select(x, k)` |
| Find median / quartiles in an array | `QuickSelect.median(x)` / `q1` / `q3` |
| Top-k from a stream (known k, fits in memory) | `DoubleHeapSelect` / `IntHeapSelect` |
| Any quantile from an unbounded stream | `IQAgent` |

---

## Performance Notes

- All classes are allocation-free after construction — no objects are created per
  element. This makes them safe for tight numerical loops.
- `IntHeapSelect`, `FloatHeapSelect`, `DoubleHeapSelect` avoid boxing overhead versus
  `HeapSelect<T>` and should be preferred for primitive data.
- `QuickSort` uses a fixed stack of 64 entries, sufficient for arrays of up to
  2^32 elements without stack overflow.
- `IQAgent` has a constant memory footprint: `O(nbuf + 251)` doubles regardless of
  stream length.

---

## Complete Example

```java
import smile.sort.*;

public class SortDemo {
    public static void main(String[] args) {
        // 1. ShellSort — sort in-place
        double[] data = {3.1, 1.4, 1.5, 9.2, 6.5, 3.5, 8.9};
        ShellSort.sort(data);
        System.out.println("Sorted: " + java.util.Arrays.toString(data));

        // 2. QuickSort — sort + track original indices
        double[] values = {3.0, 1.0, 4.0, 1.0, 5.0, 9.0};
        int[] order = QuickSort.sort(values);
        System.out.println("Sorted values: " + java.util.Arrays.toString(values));
        System.out.println("Original indices: " + java.util.Arrays.toString(order));

        // 3. QuickSort — co-sort feature vector with label vector
        double[] features = {0.9, 0.2, 0.7, 0.4};
        int[]    labels   = {3,   0,   2,   1};
        QuickSort.sort(features, labels);
        System.out.println("Co-sorted features: " + java.util.Arrays.toString(features));
        System.out.println("Co-sorted labels  : " + java.util.Arrays.toString(labels));

        // 4. QuickSelect — median and quartiles
        double[] sample = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5};
        System.out.printf("q1=%.1f  median=%.1f  q3=%.1f%n",
                QuickSelect.q1(sample.clone()),
                QuickSelect.median(sample.clone()),
                QuickSelect.q3(sample.clone()));

        // 5. DoubleHeapSelect — top-5 smallest in a stream
        DoubleHeapSelect top5 = new DoubleHeapSelect(5);
        for (int i = 100; i >= 1; i--) top5.add(i);   // stream: 100, 99, ..., 1
        top5.sort();
        System.out.print("Top-5 smallest: ");
        for (int i = 0; i < 5; i++) System.out.print(top5.get(i) + " ");
        System.out.println();   // → 1.0 2.0 3.0 4.0 5.0

        // 6. IQAgent — quantiles from a long stream
        IQAgent agent = new IQAgent();
        for (int i = 1; i <= 100_000; i++) agent.add(i);
        System.out.printf("Stream quantiles — p25: %.0f  p50: %.0f  p75: %.0f%n",
                agent.quantile(0.25),
                agent.quantile(0.50),
                agent.quantile(0.75));
    }
}
```

Expected output:
```
Sorted: [1.4, 1.5, 3.1, 3.5, 6.5, 8.9, 9.2]
Sorted values: [1.0, 1.0, 3.0, 4.0, 5.0, 9.0]
Original indices: [1, 3, 0, 2, 4, 5]
Co-sorted features: [0.2, 0.4, 0.7, 0.9]
Co-sorted labels  : [0, 1, 2, 3]
q1=2.0  median=4.0  q3=5.0
Top-5 smallest: 1.0 2.0 3.0 4.0 5.0
Stream quantiles — p25: 25001  p50: 50001  p75: 75001
```

---

## API Quick Reference

### `ShellSort` (static)

| Method | Description |
|---|---|
| `sort(int[] x)` | Sort in ascending order |
| `sort(float[] x)` | Sort in ascending order |
| `sort(double[] x)` | Sort in ascending order |
| `sort(T[] x)` | Sort in ascending order |

### `QuickSort` (static)

| Method | Description |
|---|---|
| `sort(int[] x)` | Sort; return original-index permutation |
| `sort(float[] x)` | Sort; return original-index permutation |
| `sort(double[] x)` | Sort; return original-index permutation |
| `sort(T[] x)` | Sort; return original-index permutation |
| `sort(int[] x, int[] y)` | Co-sort |
| `sort(int[] x, double[] y)` | Co-sort |
| `sort(int[] x, Object[] y)` | Co-sort |
| `sort(float[] x, int[] y)` | Co-sort |
| `sort(float[] x, float[] y)` | Co-sort |
| `sort(float[] x, Object[] y)` | Co-sort |
| `sort(double[] x, int[] y)` | Co-sort |
| `sort(double[] x, double[] y)` | Co-sort |
| `sort(double[] x, Object[] y)` | Co-sort |
| `sort(T[] x, int[] y)` | Co-sort with Comparator |
| `sort(…, int n)` | All variants accept `n` to sort only first n elements |

### `QuickSelect` (static)

| Method | Description |
|---|---|
| `select(int[] x, k)` | k-th smallest (0-based) |
| `select(float[] x, k)` | k-th smallest |
| `select(double[] x, k)` | k-th smallest |
| `select(T[] x, k)` | k-th smallest |
| `median(int[] x)` | Element at index n/2 |
| `median(float[] x)` | Element at index n/2 |
| `median(double[] x)` | Element at index n/2 |
| `median(T[] x)` | Element at index n/2 |
| `q1(int[] x)` | Element at index n/4 |
| `q1(float[] x)` | Element at index n/4 |
| `q1(double[] x)` | Element at index n/4 |
| `q1(T[] x)` | Element at index n/4 |
| `q3(int[] x)` | Element at index 3n/4 |
| `q3(float[] x)` | Element at index 3n/4 |
| `q3(double[] x)` | Element at index 3n/4 |
| `q3(T[] x)` | Element at index 3n/4 |

### `DoubleHeapSelect` / `IntHeapSelect` / `FloatHeapSelect` / `HeapSelect<T>`

| Method | Description |
|---|---|
| `HeapSelect(Class, k)` / `XxxHeapSelect(k)` | Construct with heap capacity k |
| `add(value)` | Feed one value from the stream |
| `peek()` | k-th smallest seen so far (heap root, O(1)) |
| `get(i)` | i-th smallest seen so far (0-based) |
| `sort()` | Sort the internal heap array |
| `size()` | Total number of values fed (may exceed k) |
| `toArray()` | Copy of the k tracked values in heap order |
| `toArray(T[] a)` | Copy into a supplied array *(HeapSelect only)* |
| `siftDown()` | Re-heapify after direct mutation of peek element *(HeapSelect only)* |

### `IQAgent`

| Method | Description |
|---|---|
| `IQAgent()` | Default batch size 1000 |
| `IQAgent(int nbuf)` | Custom batch size |
| `add(double datum)` | Feed one value |
| `quantile(double p)` | Estimated p-quantile (0 ≤ p ≤ 1) |

---

## References

1. R. Sedgewick. *Algorithms in C*, 3rd ed. Addison-Wesley, 1998.
   (Shell sort, Quicksort, Quickselect, heap operations)
2. W. D. Clinger. "How to read floating point numbers accurately." *PLDI*, 1990.
3. J. M. Chambers, D. A. James, D. Lambert, and S. Vander Wiel.
   "Monitoring Networked Applications with Incremental Quantile Estimation."
   *Statistical Science* 21(4):463–475, 2006.
   ([IQAgent algorithm](https://doi.org/10.1214/088342306000000583))


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

