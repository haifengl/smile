# SMILE — Association Rule Mining

The package `smile.association` provides efficient mining of:

- **Frequent item sets** via the FP-Growth algorithm.
- **Association rules** with support, confidence, lift, and leverage metrics.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Core Concepts](#2-core-concepts)
3. [Data Format](#3-data-format)
4. [Build an FP-Tree](#4-build-an-fp-tree)
   - [4.1 In-Memory Construction](#41-in-memory-construction)
   - [4.2 Streaming Construction](#42-streaming-construction)
   - [4.3 Input Validation](#43-input-validation)
5. [Mine Frequent Item Sets (FP-Growth)](#5-mine-frequent-item-sets-fp-growth)
6. [Mine Association Rules (ARM)](#6-mine-association-rules-arm)
   - [6.1 Confidence Threshold](#61-confidence-threshold)
   - [6.2 Input Validation](#62-input-validation)
7. [Interpret Rule Metrics](#7-interpret-rule-metrics)
8. [End-to-End Examples](#8-end-to-end-examples)
   - [8.1 In-Memory Transactions](#81-in-memory-transactions)
   - [8.2 Supplier-Based Large File Mining](#82-supplier-based-large-file-mining)
   - [8.3 Filter by Lift](#83-filter-by-lift)
9. [Validation and Edge Cases](#9-validation-and-edge-cases)
10. [Performance Tips](#10-performance-tips)
11. [API Quick Reference](#11-api-quick-reference)

---

## 1) Overview

Package: `smile.association`

| Class | Role |
|---|---|
| `FPTree` | Compact transaction index (prefix tree + header table) |
| `FPGrowth` | Frequent item set mining via FP-Growth |
| `ItemSet` | Frequent item set with raw support count |
| `ARM` | Association rule mining from the T-tree |
| `AssociationRule` | Rule with antecedent, consequent, and metrics |

Workflow:

```
transactions  →  FPTree.of(minSupport, ...)
              →  FPGrowth.apply(tree)        // Stream<ItemSet>
              →  ARM.apply(confidence, tree) // Stream<AssociationRule>
```

---

## 2) Core Concepts

- **Transaction**: a set of item IDs (one basket / purchase / document).
- **Item set**: a subset of items appearing together in transactions.
- **Support** `supp(X)`: proportion of transactions containing `X`. Raw count in `ItemSet`; fraction in `AssociationRule`.
- **Rule** `X ⇒ Y`: `X` (antecedent) and `Y` (consequent) are disjoint item sets.
- **Confidence** `conf(X ⇒ Y) = supp(X ∪ Y) / supp(X)`: estimated `P(Y | X)`.
- **Lift** `lift(X ⇒ Y) = supp(X ∪ Y) / (supp(X) · supp(Y))`: ratio vs. statistical independence.
- **Leverage** `lev(X ⇒ Y) = supp(X ∪ Y) − supp(X) · supp(Y)`: absolute deviation from independence.

---

## 3) Data Format

Transactions are `int[]` arrays of non-negative item IDs.

```java
int[][] transactions = {
    {1, 3},
    {2},
    {4},
    {2, 3, 4},
    {2, 3},
    {2, 3},
    {1, 2, 3, 4},
    {1, 3},
    {1, 2, 3},
    {1, 2, 3}
};
```

**Rules:**
- Item IDs must be non-negative integers.
- Each row may have a different length.
- Duplicate items within one transaction are tolerated and collapsed: support is counted by *presence per transaction*, not multiplicity.

---

## 4) Build an FP-Tree

Class: `smile.association.FPTree`

### 4.1 In-Memory Construction

```java
// Absolute minimum support (frequency count)
FPTree tree = FPTree.of(3, transactions);        // minSupport = 3

// Relative minimum support (fraction of transactions)
FPTree tree = FPTree.of(0.3, transactions);      // 30% of 10 = 3
```

The two forms produce identical trees when the percentage rounds to the same integer count.

```java
int  n = tree.size();         // number of transactions
int  s = tree.minSupport();   // effective integer support threshold
```

### 4.2 Streaming Construction

For large datasets that cannot fit in memory, use a `Supplier<Stream<int[]>>`.
The supplier will be called **twice** — once for frequency counting and once for
tree construction.

```java
import java.util.stream.Stream;
import java.util.function.Supplier;

// Example: read transactions from a file
Supplier<Stream<int[]>> supplier = () -> {
    return java.nio.file.Files.lines(java.nio.file.Path.of("transactions.txt"))
            .map(line -> Arrays.stream(line.split(","))
                               .mapToInt(Integer::parseInt).toArray());
};

FPTree tree = FPTree.of(1500, supplier);         // absolute support
FPTree tree = FPTree.of(0.003, supplier);        // relative (0.3%)
```

### 4.3 Input Validation

`FPTree.of` enforces these preconditions, throwing `IllegalArgumentException` on violation:

| Variant | Condition checked |
|---|---|
| `of(int, ...)` | `minSupport >= 1` |
| `of(double, ...)` | `minSupport` in `(0, 1]` |
| Both | stream must not be empty |

```java
FPTree.of(0, transactions);    // throws — minSupport must be >= 1
FPTree.of(0.0, transactions);  // throws — percentage must be > 0
FPTree.of(1.1, transactions);  // throws — percentage must be <= 1
```

> **Empty-tree handling:** When `minSupport` is larger than the total transaction count,
> no items are frequent. `FPGrowth.apply` and `ARM.apply` will both return empty streams
> without error — this is the correct and tested behavior.

---

## 5) Mine Frequent Item Sets (FP-Growth)

Class: `smile.association.FPGrowth`

```java
FPTree tree = FPTree.of(3, transactions);

// Returns Stream<ItemSet> — lazy, sequential
Stream<ItemSet> stream = FPGrowth.apply(tree);

// Materialize and inspect
stream.forEach(set ->
    System.out.printf("%s  (support=%d)%n",
            Arrays.toString(set.items()), set.support())
);
```

`ItemSet` is an immutable `record`:

```java
record ItemSet(int[] items, int support) { ... }
```

| Field | Type | Meaning |
|---|---|---|
| `items()` | `int[]` | Item IDs in frequency-descending order |
| `support()` | `int` | Raw transaction count |

`ItemSet.equals` / `hashCode` include both `items` and `support`. `toString` produces e.g.:

```
ItemSet([3, 2], support=6)
```

---

## 6) Mine Association Rules (ARM)

Class: `smile.association.ARM`

### 6.1 Confidence Threshold

```java
FPTree tree = FPTree.of(3, transactions);

// All rules with confidence >= 0.5
Stream<AssociationRule> rules = ARM.apply(0.5, tree);

rules.forEach(System.out::println);
// AssociationRule([3] => [2], support=60.0%, confidence=75.0%, lift=1.07, leverage=0.040)
```

`AssociationRule` is an immutable `record`:

```java
record AssociationRule(int[] antecedent, int[] consequent,
                       double support, double confidence,
                       double lift, double leverage) { ... }
```

Common post-mining filters:

```java
// Confidence = 1.0 (deterministic rules)
ARM.apply(1.0, tree)

// All generated rules (confidence >= 0.0)
ARM.apply(0.0, tree)

// Filter for positive and meaningful correlation
ARM.apply(0.5, tree)
   .filter(r -> r.lift() > 1.1 && r.leverage() > 0.01)
   .forEach(System.out::println);
```

### 6.2 Input Validation

`ARM.apply` validates that `confidence` is in `[0, 1]`:

```java
ARM.apply(-0.1, tree);  // throws IllegalArgumentException
ARM.apply(1.1,  tree);  // throws IllegalArgumentException
```

---

## 7) Interpret Rule Metrics

Given rule `X ⇒ Y` over a database of `N` transactions:

| Metric | Formula | Interpretation |
|---|---|---|
| `support` | `count(X ∪ Y) / N` | Fraction of transactions containing both X and Y |
| `confidence` | `count(X ∪ Y) / count(X)` | Estimated `P(Y | X)` |
| `lift` | `support / (supp(X) · supp(Y))` | `> 1` positive correlation; `= 1` independent; `< 1` negative |
| `leverage` | `support − supp(X) · supp(Y)` | Absolute gain over independence |

**Example** — rule `{3} ⇒ {2}` from the 10-transaction dataset:

| | Value |
|---|---|
| `count({3})` | 8 → `supp({3}) = 0.8` |
| `count({2})` | 7 → `supp({2}) = 0.7` |
| `count({3,2})` | 6 → `support = 0.6` |
| `confidence` | `6/8 = 0.75` |
| `lift` | `0.6 / (0.8 × 0.7) ≈ 1.071` |
| `leverage` | `0.6 − 0.8 × 0.7 = 0.04` |

**Note on `equals`/`hashCode`:** two `AssociationRule` objects are equal if they have
the same antecedent, consequent, support, and confidence. `lift` and `leverage` are
derived metrics and are **intentionally excluded** from equality comparisons.

---

## 8) End-to-End Examples

### 8.1 In-Memory Transactions

```java
import smile.association.*;
import java.util.Arrays;

public class AssociationExample {
    public static void main(String[] args) {
        int[][] tx = {
            {1, 3}, {2}, {4}, {2, 3, 4}, {2, 3},
            {2, 3}, {1, 2, 3, 4}, {1, 3}, {1, 2, 3}, {1, 2, 3}
        };

        // Build FP-tree with minSupport = 30%
        FPTree tree = FPTree.of(0.3, tx);
        System.out.println("Transactions: " + tree.size());
        System.out.println("Min support:  " + tree.minSupport());

        // Frequent item sets
        System.out.println("\nFrequent item sets:");
        FPGrowth.apply(tree).forEach(set ->
            System.out.printf("  %s  support=%d%n", Arrays.toString(set.items()), set.support())
        );

        // Association rules
        System.out.println("\nAssociation rules (conf >= 0.5):");
        ARM.apply(0.5, tree).forEach(rule ->
            System.out.printf("  %s => %s  supp=%.2f conf=%.2f lift=%.3f lev=%.3f%n",
                Arrays.toString(rule.antecedent()),
                Arrays.toString(rule.consequent()),
                rule.support(), rule.confidence(),
                rule.lift(), rule.leverage())
        );
    }
}
```

**Expected output (partial):**

```
Frequent item sets:
  [4]     support=3
  [1]     support=5
  [1, 3]  support=5
  ...
  [3, 2]  support=6
  [3]     support=8

Association rules (conf >= 0.5):
  [3] => [2]     supp=0.60 conf=0.75 lift=1.071 lev=0.040
  [3] => [1]     supp=0.50 conf=0.63 lift=1.250 lev=0.100
  ...
```

### 8.2 Supplier-Based Large File Mining

Use the `Supplier<Stream<int[]>>` API when the dataset does not fit in memory.

```java
import smile.association.*;
import java.nio.file.Files;
import java.nio.file.Path;

Supplier<Stream<int[]>> data = () ->
    Files.lines(Path.of("transactions.dat"))
         .map(line -> Arrays.stream(line.split("\\s+"))
                            .mapToInt(Integer::parseInt)
                            .toArray());

// The supplier is called twice internally
FPTree tree = FPTree.of(0.003, data);   // 0.3% of transactions

long nSets  = FPGrowth.apply(tree).count();
long nRules = ARM.apply(0.5, tree).count();
System.out.printf("Frequent sets: %d, Rules: %d%n", nSets, nRules);
```

### 8.3 Filter by Lift

Mining can produce thousands of rules; narrow them down with stream operations:

```java
FPTree tree = FPTree.of(3, transactions);

ARM.apply(0.5, tree)
   .filter(r -> r.lift() > 1.1)         // meaningful correlation only
   .filter(r -> r.leverage() > 0.01)    // non-trivial gain
   .sorted(Comparator.comparingDouble(AssociationRule::lift).reversed())
   .limit(20)
   .forEach(System.out::println);
```

---

## 9) Validation and Edge Cases

### Empty tree (minSupport too high)

If `minSupport` exceeds the total number of transactions, no item is frequent.
Both `FPGrowth.apply` and `ARM.apply` return empty streams gracefully:

```java
FPTree tree = FPTree.of(11, tx);           // only 10 transactions
assertEquals(0, FPGrowth.apply(tree).count());  // OK
assertEquals(0, ARM.apply(0.5, tree).count());  // OK
```

### Duplicate items in one transaction

Items appearing more than once in a transaction are collapsed before indexing:

```java
int[][] dup = {{1, 1, 2}, {1, 2}, {1, 1, 1, 2}};
FPTree tree = FPTree.of(2, dup);
// support of {1} = 3, {2} = 3, {1, 2} = 3  (not inflated)
```

### Iterator contract

All three iterators (`FPGrowth`, `TotalSupportTree`, `ARM`) throw
`NoSuchElementException` when `next()` is called after exhaustion, consistent with
the Java `Iterator` contract.

### `AssociationRule` equality

`equals` and `hashCode` use antecedent, consequent, support, and confidence.
`lift` and `leverage` are derived values and are excluded, so two rules representing
the same statistical relationship compare equal regardless of floating-point
rounding in the derived metrics.

---

## 10) Performance Tips

| Tip | Rationale |
|---|---|
| Use compact integer IDs in `[0, N)` | Avoids sparse array overhead in the header table |
| Use the supplier API for large files | The two-pass design avoids loading all data into memory |
| Tune `minSupport` first | Support pruning dominates runtime; lower = exponentially more work |
| Stream and filter immediately | Avoid materializing millions of rules into a `List` |
| Percentage support for portability | `0.01` works for any database size; absolute counts are dataset-specific |

---

## 11) API Quick Reference

```java
// ── FP-Tree construction ──────────────────────────────────────────────────────

FPTree.of(int minSupport,    int[][] itemsets)
FPTree.of(double minSupport, int[][] itemsets)   // minSupport in (0, 1]
FPTree.of(int minSupport,    Supplier<Stream<int[]>> supplier)
FPTree.of(double minSupport, Supplier<Stream<int[]>> supplier)

int tree.size()          // number of transactions
int tree.minSupport()    // effective integer support threshold

// ── Frequent item sets ────────────────────────────────────────────────────────

Stream<ItemSet> FPGrowth.apply(FPTree tree)

// ItemSet (record)
int[]  items()     // item IDs
int    support()   // raw count

// ── Association rules ─────────────────────────────────────────────────────────

Stream<AssociationRule> ARM.apply(double confidence, FPTree tree)
// confidence must be in [0, 1]

// AssociationRule (record)
int[]  antecedent()   // LHS item IDs
int[]  consequent()   // RHS item IDs
double support()      // P(X ∪ Y)  — fraction
double confidence()   // P(Y | X)
double lift()         // correlation vs. independence (>1 = positive)
double leverage()     // absolute gain over independence
```

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
