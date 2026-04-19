# SMILE — Association Rule Mining

The module `smile.association` provides efficient mining of:

- **Frequent item sets** (via FP-Growth)
- **Association rules** (via support/confidence + interestingness measures)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Core Concepts](#2-core-concepts)
3. [Data Format](#3-data-format)
4. [Build an FP-Tree](#4-build-an-fp-tree)
5. [Mine Frequent Item Sets (FP-Growth)](#5-mine-frequent-item-sets-fp-growth)
6. [Mine Association Rules (ARM)](#6-mine-association-rules-arm)
7. [Interpret Rule Metrics](#7-interpret-rule-metrics)
8. [End-to-End Examples](#8-end-to-end-examples)
9. [Validation and Edge Cases](#9-validation-and-edge-cases)
10. [Performance Tips](#10-performance-tips)
11. [API Quick Reference](#11-api-quick-reference)

---

## 1) Overview

Package: `smile.association`

Main classes:

- `FPTree` - compact transaction index.
- `FPGrowth` - frequent item set mining.
- `ItemSet` - frequent item set and support.
- `ARM` - association rule mining from frequent item sets.
- `AssociationRule` - antecedent, consequent, and metrics.

Workflow:

1. Build an `FPTree` from transactions.
2. Mine frequent item sets (`FPGrowth.apply(tree)`).
3. Mine association rules (`ARM.apply(confidence, tree)`).

---

## 2) Core Concepts

- **Transaction**: a set of item IDs.
- **Item set**: a subset of items appearing together.
- **Support**: frequency/probability an item set appears.
- **Rule**: `X => Y`, where `X ∩ Y = ∅`.
- **Confidence**: `P(Y | X)`.
- **Lift**: correlation strength relative to independence.
- **Leverage**: absolute difference from expected co-occurrence under independence.

---

## 3) Data Format

`smile.association` expects transactions as `int[]` item IDs.

### Requirements

- Item IDs should be non-negative integers.
- Each row can have different length.
- Duplicate items inside one transaction are tolerated.
  - Supports are counted by **presence per transaction**, not multiplicity.

Example dataset:

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

---

## 4) Build an FP-Tree

Class: `smile.association.FPTree`

Construction APIs:

```java
// Minimum support as frequency
FPTree tree = FPTree.of(3, transactions);

// Minimum support as percentage
FPTree tree = FPTree.of(0.3, transactions);

// For large datasets: supplier-based streaming (called twice internally)
FPTree tree = FPTree.of(1500, () -> transactionStream());
```

Quick inspection:

```java
int nTransactions = tree.size();
int minSupport = tree.minSupport();
```

---

## 5) Mine Frequent Item Sets (FP-Growth)

Class: `smile.association.FPGrowth`

Run FP-Growth:

```java
import smile.association.*;

FPTree tree = FPTree.of(3, transactions);

// Stream<ItemSet>
var stream = FPGrowth.apply(tree);

// Materialize
var frequent = stream.toList();

for (ItemSet set : frequent) {
    System.out.printf("%s  support=%d%n",
            java.util.Arrays.toString(set.items()),
            set.support());
}
```

`ItemSet` fields:

- `items()` - item IDs in mined order
- `support()` - support as count (frequency)

---

## 6) Mine Association Rules (ARM)

Class: `smile.association.ARM`

Generate rules from the same FP-tree with a confidence threshold:

```java
import smile.association.*;

FPTree tree = FPTree.of(3, transactions);

double minConfidence = 0.5;
var rules = ARM.apply(minConfidence, tree).toList();

for (AssociationRule rule : rules) {
    System.out.println(rule);
}
```

Each `AssociationRule` contains:

- `antecedent()`
- `consequent()`
- `support()` (fraction, not count)
- `confidence()`
- `lift()`
- `leverage()`

---

## 7) Interpret Rule Metrics

Given rule `X => Y`:

- **Support** (`support`) = `P(X ∪ Y)`
  - how frequent the full rule is.
- **Confidence** (`confidence`) = `P(Y | X)`
  - how often `Y` appears when `X` appears.
- **Lift** (`lift`) = `P(X ∪ Y) / (P(X)P(Y))`
  - `> 1`: positive association
  - `= 1`: independence
  - `< 1`: negative association
- **Leverage** (`leverage`) = `P(X ∪ Y) - P(X)P(Y)`
  - absolute deviation from independence.

Practical rule quality filtering often uses:

1. minimum support,
2. minimum confidence,
3. optional minimum lift (e.g. `lift >= 1.1`).

---

## 8) End-to-End Examples

### 8.1 Frequent item sets + rules from in-memory transactions

```java
import smile.association.*;
import java.util.Arrays;

public class AssociationExample {
    public static void main(String[] args) {
        int[][] tx = {
            {1, 3}, {2}, {4}, {2, 3, 4}, {2, 3},
            {2, 3}, {1, 2, 3, 4}, {1, 3}, {1, 2, 3}, {1, 2, 3}
        };

        // 1) Build FP-tree
        FPTree tree = FPTree.of(3, tx);

        // 2) Frequent item sets
        System.out.println("Frequent item sets:");
        FPGrowth.apply(tree).forEach(set ->
            System.out.printf("  %s support=%d%n", Arrays.toString(set.items()), set.support())
        );

        // 3) Association rules
        System.out.println("\nAssociation rules:");
        ARM.apply(0.5, tree).forEach(rule ->
            System.out.printf("  %s => %s  supp=%.3f conf=%.3f lift=%.3f lev=%.3f%n",
                Arrays.toString(rule.antecedent()),
                Arrays.toString(rule.consequent()),
                rule.support(),
                rule.confidence(),
                rule.lift(),
                rule.leverage())
        );
    }
}
```

### 8.2 Supplier-based large file mining

Use `Supplier<Stream<int[]>>` when reading from disk to avoid loading all
transactions into memory.

```java
import smile.association.*;
import java.util.stream.Stream;

// transactionStream() should open and parse file into Stream<int[]>
FPTree tree = FPTree.of(0.003, () -> transactionStream());
long nItemSets = FPGrowth.apply(tree).count();
long nRules = ARM.apply(0.5, tree).count();
```

> Note: supplier is called twice during one-step tree construction.

---

## 9) Validation and Edge Cases

### Iterator behavior

Iterators now follow Java contract strictly:

- calling `next()` after exhaustion throws `NoSuchElementException`.

This applies to internal iterators used by:

- `FPGrowth`
- `TotalSupportTree`
- `ARM`

### Empty transaction stream

`FPTree` creation from stream throws `IllegalArgumentException` for empty input.

### Duplicate items in one transaction

Duplicates are compacted and counted once per transaction for support,
avoiding inflated frequencies.

### Equality semantics of `AssociationRule`

`AssociationRule.equals`/`hashCode` are based on:

- antecedent,
- consequent,
- support,
- confidence.

(They intentionally do not include lift/leverage in equality.)

---

## 10) Performance Tips

- Prefer integer item encoding with compact ID range.
- Use supplier-based API for large files.
- Tune minimum support to control output volume:
  - lower support -> exponentially more item sets/rules.
- Start with stricter confidence and relax gradually.
- If rule count is huge, stream and filter early:

```java
ARM.apply(0.6, tree)
   .filter(r -> r.lift() >= 1.1)
   .limit(10_000)
   .forEach(...);
```

---

## 11) API Quick Reference

```java
// FP-tree construction
FPTree.of(int minSupport, int[][] itemsets)
FPTree.of(double minSupport, int[][] itemsets)
FPTree.of(int minSupport, Supplier<Stream<int[]>> supplier)
FPTree.of(double minSupport, Supplier<Stream<int[]>> supplier)

int size()
int minSupport()

// Frequent item set mining
Stream<ItemSet> FPGrowth.apply(FPTree tree)

// Association rule mining
Stream<AssociationRule> ARM.apply(double confidence, FPTree tree)

// ItemSet
int[] items()
int support()

// AssociationRule
int[] antecedent()
int[] consequent()
double support()
double confidence()
double lift()
double leverage()
```

---

*SMILE — Copyright (c) 2010-2026 Haifeng Li. GNU GPL licensed.*

