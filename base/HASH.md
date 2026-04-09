# SMILE — Hash Functions User Guide

The `smile.hash` package provides a collection of high-performance, non-cryptographic
hash functions and data structures built on top of them. It currently contains five
classes:

| Class | Purpose |
|---|---|
| `MurmurHash2` | Fast 32-bit and 64-bit non-cryptographic hash for raw byte data |
| `MurmurHash3` | Improved 32-bit and 128-bit non-cryptographic hash for raw byte data |
| `PerfectHash` | Compile-time-style perfect hash mapping a fixed string set to `[0, n)` |
| `PerfectMap` | Immutable `String → T` map backed by `PerfectHash` |
| `SimHash` | Locality-sensitive fingerprinting for near-duplicate detection |

---

## 1. MurmurHash2

MurmurHash2 is an extremely fast, non-cryptographic hash designed by Austin Appleby.
The name comes from the two basic operations in its inner loop: **multiply** (MU) and
**rotate** (R).  It is well-suited to hash-table construction, checksumming, and any
task that needs a fast, well-distributed digest but does **not** require cryptographic
strength.

SMILE's implementation is adapted from Apache Cassandra and is provided as a static
interface (`MurmurHash2`) with two overloads:

```
MurmurHash2.hash32(ByteBuffer data, int offset, int length, int seed)  → int
MurmurHash2.hash64(ByteBuffer data, int offset, int length, long seed) → long
```

### 1.1 32-bit hash

```java
import smile.hash.MurmurHash2;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
ByteBuffer buf = ByteBuffer.wrap(data);

int h32 = MurmurHash2.hash32(buf, 0, data.length, 42 /*seed*/);
System.out.printf("MurmurHash2-32 = 0x%08X%n", h32);
```

### 1.2 64-bit hash

```java
long h64 = MurmurHash2.hash64(buf, 0, data.length, 0L /*seed*/);
System.out.printf("MurmurHash2-64 = 0x%016X%n", h64);
```

### 1.3 Properties

| Property | Value |
|---|---|
| Output width | 32 or 64 bits |
| Endianness | Little-endian |
| Seed support | Yes — different seeds produce independent hash families |
| Cryptographic | No |
| Suitable for | Hash tables, bloom filters, checksums, sharding |

---

## 2. MurmurHash3

MurmurHash3 supersedes MurmurHash2 with better avalanche behavior and wider output
options.  SMILE provides two variants, both adapted from Apache Cassandra:

```
MurmurHash3.hash32(String text, int seed)                                → int
MurmurHash3.hash32(byte[] data, int offset, int length, int seed)        → int
MurmurHash3.hash128(ByteBuffer data, int offset, int length,
                    long seed, long[] result)                             → void
```

The 128-bit variant implements the **x64 flavour**: two independent 64-bit lanes
that are mixed together in the finalization step.

> **Note:** The x86 and x64 128-bit variants produce *different* outputs for the same
> input.  SMILE implements only the x64 variant.

### 2.1 32-bit hash from a String

```java
import smile.hash.MurmurHash3;

int h = MurmurHash3.hash32("the quick brown fox", 0);
```

### 2.2 32-bit hash from a byte array

```java
byte[] data = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
int h = MurmurHash3.hash32(data, 0, data.length, 0);
```

### 2.3 128-bit hash (x64)

The result is written into a caller-supplied `long[2]` array: `result[0]` = lower
64 bits, `result[1]` = upper 64 bits.

```java
byte[] data = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
ByteBuffer buf = ByteBuffer.wrap(data);
long[] result = new long[2];
MurmurHash3.hash128(buf, 0, data.length, 0L, result);
System.out.printf("h1=0x%016X  h2=0x%016X%n", result[0], result[1]);
```

### 2.4 Choosing between MurmurHash2 and MurmurHash3

| Criterion | MurmurHash2 | MurmurHash3 |
|---|---|---|
| Output widths | 32, 64 | 32, 128 |
| Speed (64-bit JVM) | Slightly faster 64-bit path | Slightly faster 32-bit path |
| Avalanche quality | Good | Excellent |
| Recommendation | Legacy code / Cassandra compatibility | New code |

---

## 3. PerfectHash

`PerfectHash` builds a **minimal perfect hash** over a fixed set of strings known
at construction time.  Every key maps to a unique integer in `[0, n)` in O(length)
time — there are no collisions and no chaining.

### 3.1 How it works

The hash function is:

```
h(w) = (len(w) + Σ kvals[w[i] − min]) mod tsize
```

where `kvals` is an integer array indexed by `(character − min_char)` and `tsize`
is the table size (≥ n).  Construction fills `kvals` with random integers and
checks whether all n keys map to distinct slots.  Failed attempts draw a new
random `kvals`; with a load factor of ≈ 2/3 this succeeds in O(1) expected trials.
The final lookup table maps each hash slot to the index of the keyword stored there,
or −1 for empty slots; correctness is verified by comparing the stored keyword
against the query string.

### 3.2 Basic usage

```java
import smile.hash.PerfectHash;

PerfectHash hash = new PerfectHash("abstract", "boolean", "break",
                                   "class", "continue", "default");

int idx = hash.get("break");     // returns 2 (index in the array above)
int miss = hash.get("integer");  // returns -1 — not in the set
```

### 3.3 Selecting character positions

For performance with long strings you can tell the hash to use only specific
character positions.  This is analogous to the `%` keyword option in `gperf`.

```java
// Only look at characters at positions 0 and 1.
PerfectHash hash = new PerfectHash(new int[]{0, 1}, keywords);
```

Choosing positions that maximise distinctiveness (e.g., the first and last
characters of a keyword) typically reduces construction time.

### 3.4 Constraints

* The keyword array must be non-empty.
* Duplicate keywords throw `IllegalArgumentException`.
* The set is **frozen at construction time** — there is no way to add or remove
  keywords after construction.
* `PerfectHash` is `Serializable` and thread-safe for concurrent reads.

---

## 4. PerfectMap

`PerfectMap<T>` is an **immutable, perfect-hash-backed `String → T` map**.  Lookup
is O(key length) with zero allocation on the hot path.  It is ideal for small,
fixed dictionaries that are read very frequently (e.g., HTTP header tables,
language keyword→token mappings, configuration key→handler tables).

### 4.1 Building with the fluent Builder

```java
import smile.hash.PerfectMap;

PerfectMap<Integer> tokenTypes = new PerfectMap.Builder<Integer>()
    .add("abstract",   1)
    .add("boolean",    2)
    .add("break",      3)
    .add("class",      4)
    .add("continue",   5)
    .add("default",    6)
    .build();

int type = tokenTypes.get("break");   // 3
Object miss = tokenTypes.get("goto"); // null
```

### 4.2 Building from an existing Map

```java
Map<String, String> source = Map.of(
    "Content-Type",     "application/json",
    "Accept",           "application/json",
    "Authorization",    "Bearer …"
);

PerfectMap<String> headers = new PerfectMap.Builder<>(source).build();
String ct = headers.get("Content-Type"); // "application/json"
```

### 4.3 Null semantics

`get(key)` returns `null` for any key that was not in the original key set.
It never throws for well-formed (non-null) query strings.

---

## 5. SimHash

`SimHash` is a **locality-sensitive fingerprinting** algorithm.  Unlike ordinary
hash functions, two similar inputs produce fingerprints with a small Hamming
distance, while dissimilar inputs produce fingerprints with a large Hamming
distance.  This makes SimHash useful for near-duplicate detection in large
document collections (it is used by the Google crawler for exactly this purpose).

Each fingerprint is a 64-bit `long`.  The Hamming distance between two fingerprints
is `Long.bitCount(h1 ^ h2)`.  Documents are typically considered near-duplicates
when their Hamming distance is ≤ 3.

### 5.1 Text similarity (tokenised documents)

Use `SimHash.text()` to get a `SimHash<String[]>` that treats each element of the
array as a token.  Internally, every token is hashed with MurmurHash2-64 and its
hash bits vote (+1 or −1) on 64 counters; the sign of each counter becomes one bit
of the fingerprint.

```java
import smile.hash.SimHash;

SimHash<String[]> sh = SimHash.text();

String[] doc1 = {"the", "quick", "brown", "fox", "jumps"};
String[] doc2 = {"the", "quick", "brown", "fox", "leaps"}; // one word different

long h1 = sh.hash(doc1);
long h2 = sh.hash(doc2);

int hamming = Long.bitCount(h1 ^ h2);
System.out.println("Hamming distance = " + hamming); // small (≤ 5 typically)
```

### 5.2 Weighted feature vectors

Use `SimHash.of(byte[][] features)` when each document is represented as a sparse
vector of pre-defined feature weights.  Each feature is identified by its byte
representation; the weight array passed to `hash(int[] weights)` must be the same
length as the `features` array.

```java
byte[][] features = {
    "title".getBytes(),
    "body".getBytes(),
    "tags".getBytes()
};
SimHash<int[]> sh = SimHash.of(features);

// A document with title weight 5, body weight 3, tags weight 1
long fp = sh.hash(new int[]{5, 3, 1});
```

### 5.3 Near-duplicate detection pipeline

A typical pipeline computes fingerprints for all documents and then uses a
**hash table** keyed on the fingerprint to find exact duplicates, or a
**Hamming-distance index** (e.g., sorted permutations) to find near-duplicates:

```java
SimHash<String[]> sh = SimHash.text();
Map<Long, String> seen = new HashMap<>();

for (var entry : corpus.entrySet()) {
    String id  = entry.getKey();
    String[] tokens = entry.getValue();
    long fp = sh.hash(tokens);

    // Exact fingerprint match → likely duplicate
    String prev = seen.putIfAbsent(fp, id);
    if (prev != null) {
        System.out.println(id + " is a near-duplicate of " + prev);
    }
}
```

For large corpora, partition the 64 bits into k bands of b bits each
(standard LSH technique) and bucket documents by each band independently to
find pairs within Hamming distance ≤ b·k efficiently.

### 5.4 Properties

| Property | Value |
|---|---|
| Output width | 64 bits |
| Similarity metric | Hamming distance |
| Internal hash | MurmurHash2-64 |
| Near-duplicate threshold | Hamming ≤ 3 (configurable by application) |
| Thread safety | Stateless — the `SimHash` instance is thread-safe |

---

## 6. Quick-reference table

| Use case | Recommended class / method |
|---|---|
| Hash a byte buffer into a 32-bit int | `MurmurHash2.hash32` |
| Hash a byte buffer into a 64-bit long | `MurmurHash2.hash64` |
| Hash a string into a 32-bit int | `MurmurHash3.hash32(String, seed)` |
| Hash a byte array into a 128-bit value | `MurmurHash3.hash128` |
| O(1) lookup in a fixed string set | `PerfectHash` |
| Immutable `String → T` dictionary | `PerfectMap` |
| Near-duplicate document detection | `SimHash.text()` |
| Weighted feature-vector fingerprinting | `SimHash.of(byte[][])` |


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

