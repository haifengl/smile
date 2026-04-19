# SMILE — Sequence Labeling

The package `smile.sequence` provides learning and decoding tools for sequence labeling tasks.

Main APIs:

- `HMM`: first-order Hidden Markov Model (generative sequence model)
- `HMMLabeler<T>`: typed wrapper around `HMM` with symbol-to-index mapping
- `CRF`: first-order linear-chain Conditional Random Field trained by gradient tree boosting
- `CRFLabeler<T>`: typed wrapper around `CRF` with feature extraction
- `SequenceLabeler<T>`: common prediction interface (`predict`)

## Table of Contents

- [1) When to use HMM vs CRF](#1-when-to-use-hmm-vs-crf)
- [2) HMM quick start](#2-hmm-quick-start)
- [3) HMMLabeler quick start](#3-hmmlabeler-quick-start)
- [4) CRF quick start](#4-crf-quick-start)
- [5) CRFLabeler quick start](#5-crflabeler-quick-start)
- [6) Input validation behavior](#6-input-validation-behavior)
- [7) Practical workflow](#7-practical-workflow)
- [8) Notes on performance and testing](#8-notes-on-performance-and-testing)

## 1) When to use HMM vs CRF

- Use `HMM` when you have discrete observations and want a simple probabilistic model with efficient training.
- Use `CRF` when feature engineering matters (contextual, rich, non-linear features) and you need stronger discriminative performance.
- `HMM` models `P(x, y)` (joint distribution), while `CRF` models `P(y | x)` (conditional distribution).

## 2) HMM quick start

Construct from known probabilities:

```java
import smile.sequence.HMM;
import smile.tensor.DenseMatrix;

double[] pi = {0.5, 0.5};
double[][] a = {{0.8, 0.2}, {0.2, 0.8}};
double[][] b = {{0.6, 0.4}, {0.4, 0.6}};

HMM hmm = new HMM(pi, DenseMatrix.of(a), DenseMatrix.of(b));

int[] o = {0, 0, 1, 1, 0};
int[] state = hmm.predict(o);            // Viterbi decode
double logp = hmm.logp(o);               // log P(o)
```

Fit from labeled sequences:

```java
int[][] observations = {
    {0, 0, 1, 1, 0},
    {1, 1, 0, 1, 1}
};
int[][] labels = {
    {0, 0, 1, 1, 0},
    {1, 1, 0, 1, 1}
};

HMM model = HMM.fit(observations, labels);
```

Unsupervised refinement (Baum-Welch):

```java
model.update(observations, 10);
```

## 3) HMMLabeler quick start

`HMMLabeler<T>` is convenient when your observations are not already integer symbols.

```java
import smile.sequence.HMMLabeler;

String[][] x = {
    {"N", "V", "N"},
    {"V", "V", "N"}
};
int[][] y = {
    {0, 1, 0},
    {1, 1, 0}
};

HMMLabeler<String> labeler = HMMLabeler.fit(x, y, symbol -> switch (symbol) {
    case "N" -> 0;
    case "V" -> 1;
    default -> throw new IllegalArgumentException("Unknown symbol: " + symbol);
});

int[] prediction = labeler.predict(new String[] {"N", "V", "N"});
```

## 4) CRF quick start

`CRF` works on `Tuple[]` sequences. Each tuple represents features for one time step.

```java
import smile.data.Tuple;
import smile.sequence.CRF;

Tuple[][] sequences = ...;    // your feature sequences
int[][] labels = ...;         // integer labels per token

CRF.Options options = new CRF.Options(
    100,   // ntrees
    20,    // maxDepth
    100,   // maxNodes
    5,     // nodeSize
    0.3    // shrinkage
);

CRF crf = CRF.fit(sequences, labels, options);

int[] y1 = crf.predict(sequences[0]);    // forward-backward per-position labels
int[] y2 = crf.viterbi(sequences[0]);    // globally coherent sequence
```

`CRF.Options` can be serialized to properties:

```java
java.util.Properties props = options.toProperties();
CRF.Options restored = CRF.Options.of(props);
```

## 5) CRFLabeler quick start

`CRFLabeler<T>` lets you train directly on typed inputs with a feature mapping function.

```java
import smile.data.Tuple;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.sequence.CRF;
import smile.sequence.CRFLabeler;

StructType schema = new StructType(
    new StructField("x", DataTypes.IntType)
);

java.util.function.Function<Integer, Tuple> features = v -> Tuple.of(schema, new int[] {v});

Integer[][] sequences = {
    {0, 1, 0},
    {1, 0, 1}
};
int[][] labels = {
    {0, 0, 0},
    {0, 0, 0}
};

CRFLabeler<Integer> labeler = CRFLabeler.fit(
    sequences,
    labels,
    features,
    new CRF.Options(10, 4, 8, 2, 0.3)
);

int[] p1 = labeler.predict(new Integer[] {0, 1, 0});
int[] p2 = labeler.viterbi(new Integer[] {0, 1, 0});
```

## 6) Input validation behavior

Recent API behavior in this package is strict by design.

### HMM/HMMLabeler

- Empty observation sequence in `predict/logp` -> `IllegalArgumentException`
- Out-of-range or negative observation symbol -> `IllegalArgumentException`
- Out-of-range or negative state index in joint scoring -> `IllegalArgumentException`
- `fit` with empty training arrays, empty sequence, negative symbols/states, or mismatched sequence lengths -> `IllegalArgumentException`
- `update` with negative iteration count, empty training set, empty sequence, or out-of-range symbols -> `IllegalArgumentException`

### CRF/CRFLabeler

- Empty sequence passed to `predict`/`viterbi` -> `IllegalArgumentException`
- `fit` with empty training arrays, mismatched sequence/label counts, mismatched per-sequence lengths, or empty sequence -> `IllegalArgumentException`
- Invalid `CRF.Options` values (e.g. `ntrees < 1`, `shrinkage <= 0 || > 1`) -> `IllegalArgumentException`

## 7) Practical workflow

1. Decide label encoding (`int` class ids from `0..k-1`).
2. Prepare train/test sequence splits.
3. Start with `HMM` baseline if observations are naturally discrete.
4. Move to `CRF`/`CRFLabeler` when you need richer feature interactions.
5. Compare `predict` vs `viterbi` decoding depending on task coherence requirements.
6. Track token-level error and sequence-level quality on held-out data.

## 8) Notes on performance and testing

- `CRF` training can be significantly heavier than `HMM`, especially on large datasets.
- In this codebase, dataset-heavy CRF tests are tagged `@Tag("integration")` to separate fast and slow lanes.
- For CI, run fast unit tests by excluding integration tags, and run integration-tagged tests in a dedicated lane.

---

*SMILE — Copyright © 2010-2026 Haifeng Li. GNU GPL licensed.*

