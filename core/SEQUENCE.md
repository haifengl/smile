# # SMILE — Sequence Labeling

The `smile.sequence` package provides the sequence labeling algorithms.
This guide explains the core abstractions, each algorithm with its API,
key parameters, and usage patterns, and ends with a practical comparison
and tips.

---

## Table of Contents

1. [Package Overview](#1-package-overview)
2. [Core Abstraction: `SequenceLabeler`](#2-core-abstraction-sequencelabeler)
3. [Hidden Markov Model (HMM)](#3-hidden-markov-model-hmm)
   - 3.1 [Core API](#31-core-api)
   - 3.2 [Training: MLE via `fit`](#32-training-mle-via-fit)
   - 3.3 [Training: Baum–Welch via `update`](#33-training-baumwelch-via-update)
   - 3.4 [Scoring and Inference](#34-scoring-and-inference)
4. [HMMLabeler — Generic-type wrapper for HMM](#4-hmmlabeler--generic-type-wrapper-for-hmm)
5. [Conditional Random Field (CRF)](#5-conditional-random-field-crf)
   - 5.1 [Core API](#51-core-api)
   - 5.2 [Training: `CRF.fit`](#52-training-crffit)
   - 5.3 [Inference: forward-backward vs Viterbi](#53-inference-forward-backward-vs-viterbi)
   - 5.4 [CRF Hyperparameters (`CRF.Options`)](#54-crf-hyperparameters-crfoptions)
6. [CRFLabeler — Generic-type wrapper for CRF](#6-crflabeler--generic-type-wrapper-for-crf)
7. [HMM vs CRF: choosing the right model](#7-hmm-vs-crf-choosing-the-right-model)
8. [Common Patterns and Tips](#8-common-patterns-and-tips)

---

## 1. Package Overview

The `smile.sequence` package provides supervised algorithms for labeling sequential
data: given an observed sequence of symbols or feature vectors, predict the most likely
label at each position.

| Class | Type | Training | Inference |
|-------|------|----------|-----------|
| `HMM` | Generative (integer symbols) | MLE (`fit`) or Baum–Welch (`update`) | Viterbi |
| `HMMLabeler<T>` | Generic wrapper for `HMM` | delegates to `HMM.fit` / `update` | Viterbi via `predict` |
| `CRF` | Discriminative (feature Tuples) | Gradient tree boosting | Forward-backward (`predict`) or Viterbi (`viterbi`) |
| `CRFLabeler<T>` | Generic wrapper for `CRF` | delegates to `CRF.fit` | forward-backward or Viterbi |

Both `HMMLabeler<T>` and `CRFLabeler<T>` implement the `SequenceLabeler<T>` interface,
making it possible to swap algorithms with no change to calling code.

**Dependencies.** The CRF internally builds gradient-boosted regression trees from
`smile.regression.RegressionTree` and stores features as `smile.data.Tuple` objects
with `smile.data.type.StructType` schemas.

---

## 2. Core Abstraction: `SequenceLabeler`

```java
public interface SequenceLabeler<T> extends Serializable {
    int[] predict(T[] x);
}
```

`predict(T[] x)` takes an observation sequence and returns an integer label for each
position. Label indices are dense non-negative integers starting at 0.

All concrete labelers implement this interface and are serializable, enabling
persistence via `smile.io.Write` / `smile.io.Read`.

---

## 3. Hidden Markov Model (HMM)

A first-order HMM models a sequence of hidden states `s_0, s_1, …, s_{T-1}` that
generate observable symbols `o_0, o_1, …, o_{T-1}`. The model has three parameter
matrices:

| Symbol | Meaning | Dimensions |
|--------|---------|------------|
| `π` | Initial state probabilities | `[N]` |
| `A` | State transition probabilities; `A[i][j] = P(s_j \| s_i)` | `[N × N]` |
| `B` | Symbol emission probabilities; `B[i][j] = P(o_j \| s_i)` | `[N × M]` |

where `N` is the number of hidden states and `M` is the vocabulary size.

### 3.1 Core API

**Constructor:**

```java
HMM hmm = new HMM(double[] pi, DenseMatrix a, DenseMatrix b);
```

| Requirement | Details |
|-------------|---------|
| `pi.length > 0` | At least one state |
| `pi.length == a.nrow()` | Consistent state count |
| `a.nrow() == b.nrow()` | Consistent state count in both matrices |

**Accessors:**

```java
double[]   pi = hmm.getInitialStateProbabilities();   // live array — do not modify
DenseMatrix A = hmm.getStateTransitionProbabilities();
DenseMatrix B = hmm.getSymbolEmissionProbabilities();
```

**String representation:**

```java
System.out.println(hmm);  // prints pi, A, B matrices
```

### 3.2 Training: MLE via `fit`

Fits an HMM by maximum likelihood estimation from labeled observation sequences.
State counts and co-occurrence counts are accumulated, then normalized per row.

**Integer symbol sequences:**

```java
int[][] observations = { {0, 1, 0, 1}, {1, 0, 0, 1} };
int[][] labels       = { {0, 1, 0, 1}, {0, 1, 1, 0} };

HMM model = HMM.fit(observations, labels);
```

**Generic symbol sequences** (via an ordinal mapping function):

```java
String[][] observations = { {"H","T","H"}, {"T","H","T"} };
int[][]    labels       = { {0, 1, 0},     {1, 0, 1}     };

HMM model = HMM.fit(observations, labels, s -> s.equals("H") ? 0 : 1);
```

Both overloads throw `IllegalArgumentException` if the observation and label arrays
have different lengths or the training set is empty.

### 3.3 Training: Baum–Welch via `update`

Iteratively re-estimates model parameters from **unlabeled** observation sequences
using the expectation-maximization Baum–Welch algorithm. The model must be
**pre-initialized** (e.g. from `fit` or a reasonable prior).

```java
HMM model = new HMM(pi, DenseMatrix.of(a), DenseMatrix.of(b));

// Integer symbols
model.update(observations, 100);   // 100 EM iterations

// Generic symbols
model.update(stringObservations, 50, s -> s.equals("H") ? 0 : 1);
```

**Requirements:**
- Each training sequence must have length **> 2** (required by the forward-backward
  recursion).
- `iterations > 0`.
- All symbol values must be valid indices in `[0, M)`.

**Convergence.** Each call to `update` runs the full EM loop. The model is modified
in-place. Call again to continue training on new data (online Baum–Welch).

### 3.4 Scoring and Inference

**Joint probability** — P(o, s | H):

```java
int[] o = {0, 1, 0, 1};
int[] s = {0, 1, 1, 0};

double prob    = hmm.p(o, s);      // probability
double logProb = hmm.logp(o, s);   // log probability (avoids underflow for long sequences)
```

`logp(o, s)` computes `log(π[s_0]) + log(B[s_0][o_0]) + Σ(log(A[s_{t-1}][s_t]) + log(B[s_t][o_t]))`.

**Marginal probability** — P(o | H):

Computed via the scaled forward algorithm to avoid underflow on long sequences.

```java
double prob    = hmm.p(o);     // marginal probability (may underflow for very long sequences)
double logProb = hmm.logp(o);  // log marginal probability (numerically stable)
```

**Decoding** — most likely state sequence (Viterbi):

```java
int[] states = hmm.predict(o);
```

Runs the Viterbi algorithm in the log domain. When multiple paths have equal
log-probability, the smallest-index state is chosen (deterministic tie-breaking).
Returns an array of the same length as `o`.

**Validation.** Methods that take an observation sequence validate that:
- The sequence is non-empty.
- Every symbol value is in `[0, M)`.
- If a state sequence is also given, it must have the same length and states in `[0, N)`.

---

## 4. `HMMLabeler` — Generic-type wrapper for HMM

`HMMLabeler<T>` wraps an `HMM` together with a `ToIntFunction<T>` ordinal mapping,
implementing `SequenceLabeler<T>`. This allows working directly with any type `T` (e.g.
`String`, `Character`, a custom enum) without manually converting to integer symbols.

### Fitting

```java
// Via static fit (MLE from labeled sequences)
HMMLabeler<String> labeler = HMMLabeler.fit(observations, labels,
        s -> s.equals("H") ? 0 : 1);

// Via constructor (wrap a pre-built HMM)
HMM model = new HMM(pi, DenseMatrix.of(a), DenseMatrix.of(b));
HMMLabeler<String> labeler = new HMMLabeler<>(model, s -> s.equals("H") ? 0 : 1);
```

### Online update (Baum–Welch)

```java
labeler.update(newStringObservations, 50);
```

### Prediction and scoring

```java
String[] sequence = {"H", "T", "H", "H", "T"};

// Viterbi decoding
int[] states = labeler.predict(sequence);

// Joint probability P(o, s | H)
double p = labeler.p(sequence, knownStates);

// Log joint probability
double lp = labeler.logp(sequence, knownStates);

// Marginal probability P(o | H)
double mp = labeler.p(sequence);

// Log marginal probability (numerically stable)
double lmp = labeler.logp(sequence);

System.out.println(labeler);  // delegates to HMM.toString()
```

All methods translate `T[]` → `int[]` via the ordinal function and then delegate to
the underlying `HMM`.

---

## 5. Conditional Random Field (CRF)

A first-order linear-chain CRF is a discriminative model that defines a joint
probability over label sequences `y` given an input sequence `x`:

```
P(y | x) ∝ exp(Σ_t F_t(x, y_t, y_{t-1}))
```

The potential functions `F_t` are represented as weighted sums of regression trees,
trained by gradient tree boosting (Dietterich et al., 2008). This means:

- **No explicit feature weights** — the model implicitly captures complex feature
  interactions via the tree structure.
- **Scales linearly** with the Markov order and feature dimensionality (unlike
  iterative scaling).
- **Discriminative** — models P(y | x) directly, so it can incorporate arbitrary
  overlapping features without normalization concerns during training.

### 5.1 Core API

```java
public class CRF implements Serializable {
    public int[] predict(Tuple[] x);   // forward-backward per-position argmax
    public int[] viterbi(Tuple[] x);   // global Viterbi decoding
    public static CRF fit(Tuple[][] sequences, int[][] labels);
    public static CRF fit(Tuple[][] sequences, int[][] labels, Options options);
}
```

Input sequences are arrays of `Tuple` objects. Each `Tuple` provides a feature vector
for one position in the sequence. The Tuple schema must be consistent across all
positions and sequences.

### 5.2 Training: `CRF.fit`

```java
CRF model = CRF.fit(sequences, labels);
CRF model = CRF.fit(sequences, labels, new CRF.Options(100, 20, 100, 5, 0.3));
```

**What happens internally:**

1. A design matrix is built from all training positions. Each non-first position is
   replicated `k` times (once per possible previous label), appending the previous-label
   index as a synthetic `s(t-1)` feature. This encodes first-order Markov dependencies.
2. `k` independent gradient tree boosting loops run — one potential function tree
   ensemble per label class.
3. At each boosting round, forward-backward is run on all training sequences to compute
   gradient residuals. A regression tree fits those residuals, and its prediction is
   added to the ensemble with a step-size controlled by `shrinkage`.

**Input requirements:**
- `sequences.length == labels.length` (same number of sequences)
- Every sequence must be non-empty
- `sequences[i].length == labels[i].length` for all `i`
- All label values must be non-negative integers; the number of classes `k` is inferred
  as `max(all labels) + 1`
- All Tuples must share the same `StructType` schema

### 5.3 Inference: forward-backward vs Viterbi

The CRF provides two decoding strategies:

**`predict(Tuple[] x)` — per-position marginal argmax:**

```java
int[] labels = model.predict(featureSequence);
```

Runs the forward-backward algorithm on the trellis. At each position `t`, selects the
label `j` that maximises `α_t[j] × β_t[j]` (the product of the scaled forward and
backward variables). This minimises per-position label error (token accuracy).

The resulting label sequence may not correspond to a single most-probable global path,
but typically achieves **lower token-level error** than Viterbi.

**`viterbi(Tuple[] x)` — globally most-likely sequence:**

```java
int[] labels = model.viterbi(featureSequence);
```

Runs the Viterbi algorithm, finding the single label sequence `y*` that maximises the
joint potential:

```
y* = argmax_y Σ_t F_t(x, y_t, y_{t-1})
```

This guarantees a globally coherent label sequence, which is desirable in applications
like part-of-speech tagging or named-entity recognition where the label sequence must
satisfy structural constraints (e.g. "I-NP" can only follow "B-NP").

**When to choose each decoder:**

| Decoder | Best for | Notes |
|---------|---------|-------|
| `predict` (forward-backward) | Maximising per-token accuracy | Output may be globally inconsistent |
| `viterbi` | Applications requiring structural coherence | Slightly higher error rate per token |

### 5.4 CRF Hyperparameters (`CRF.Options`)

```java
public record Options(int ntrees, int maxDepth, int maxNodes, int nodeSize, double shrinkage)
```

| Parameter | Default | Description                                                                                      |
|-----------|---------|--------------------------------------------------------------------------------------------------|
| `ntrees` | `100` | Number of boosting rounds (trees per class). More trees = lower training error, risk of overfit. |
| `maxDepth` | `20` | Maximum depth of each regression tree. Deeper trees capture more complex feature interactions.   |
| `maxNodes` | `100` | Maximum number of leaf nodes per tree. Controls tree complexity independently of depth.          |
| `nodeSize` | `5` | Minimum samples per leaf. Higher values regularise the tree.                                     |
| `shrinkage` | `1.0` | Learning rate in (0, 1]. Smaller values give better generalization but require more trees.      |

**Validation:** `ntrees ≥ 1`, `maxDepth ≥ 2`, `maxNodes ≥ 2`, `nodeSize ≥ 1`,
`shrinkage ∈ (0, 1]`.

**Default constructor:**

```java
CRF.Options opts = new CRF.Options();  // ntrees=100, maxDepth=20, maxNodes=100, nodeSize=5, shrinkage=1.0
```

**Properties round-trip:**

```java
Properties props = opts.toProperties();
CRF.Options restored = CRF.Options.of(props);
```

| Property key | Default |
|---|---|
| `smile.crf.trees` | `100` |
| `smile.crf.max_depth` | `20` |
| `smile.crf.max_nodes` | `100` |
| `smile.crf.node_size` | `5` |
| `smile.crf.shrinkage` | `1.0` |

**Tuning guidance:**

```
High accuracy on training set, poor validation → reduce ntrees or maxDepth/maxNodes,
                                                 or decrease shrinkage (+ increase ntrees)
Underfitting (high training error)             → increase ntrees, maxDepth
Slow training                                  → decrease ntrees or increase nodeSize
```

---

## 6. `CRFLabeler` — Generic-type wrapper for CRF

`CRFLabeler<T>` wraps a `CRF` together with a `Function<T, Tuple>` feature extraction
function, implementing `SequenceLabeler<T>`. This allows working with any input type
`T` by mapping each observation to a `Tuple` feature vector.

### Defining a feature function

The feature function must return a `Tuple` with a consistent `StructType` schema for
every input. The simplest pattern uses a pre-built schema:

```java
StructType schema = new StructType(
        new StructField("word_length",  DataTypes.IntType),
        new StructField("is_capitalised", DataTypes.IntType)
);

Function<String, Tuple> features = word -> Tuple.of(schema, new int[]{
        word.length(),
        Character.isUpperCase(word.charAt(0)) ? 1 : 0
});
```

For real NLP tasks the feature function typically extracts many more features per token
(character n-grams, prefix/suffix flags, surrounding context, etc.).

### Fitting

```java
// Default options
CRFLabeler<String> labeler = CRFLabeler.fit(sequences, labels, features);

// Explicit options
CRFLabeler<String> labeler = CRFLabeler.fit(sequences, labels, features,
        new CRF.Options(100, 20, 100, 5, 0.3));
```

`sequences` is a `T[][]` (array of sequences, each sequence being an array of `T`).
`labels` is an `int[][]` of the same shape. The feature function is applied to every
element to produce the `Tuple[][]` used for training.

### Inference

```java
// Forward-backward per-position argmax (lower per-token error)
int[] predicted = labeler.predict(sentence);

// Viterbi globally consistent sequence
int[] viterbi = labeler.viterbi(sentence);
```

Both methods apply the feature function to each element in the input sequence and then
delegate to the underlying `CRF` for decoding.

### Full example — POS-style tagging

```java
// Training data: sentences of words, labels 0=noun, 1=verb, 2=other
String[][] trainSentences = {
    {"The", "cat", "sat"},
    {"Dogs", "run", "fast"}
};
int[][] trainLabels = {
    {2, 0, 1},
    {0, 1, 2}
};

StructType schema = new StructType(
        new StructField("length",   DataTypes.IntType),
        new StructField("capitals", DataTypes.IntType),
        new StructField("has_ed",   DataTypes.IntType)
);
Function<String, Tuple> features = w -> Tuple.of(schema, new int[]{
        w.length(),
        (int) w.chars().filter(Character::isUpperCase).count(),
        w.endsWith("ed") ? 1 : 0
});

CRFLabeler<String> tagger = CRFLabeler.fit(trainSentences, trainLabels, features,
        new CRF.Options(100, 5, 32, 2, 0.1));

String[] sentence = {"The", "dogs", "barked"};
int[] tags = tagger.predict(sentence);  // per-token argmax
int[] viterbiTags = tagger.viterbi(sentence);  // coherent sequence
```

---

## 7. HMM vs CRF: Choosing the Right Model

| Criterion | HMM | CRF |
|-----------|-----|-----|
| **Input type** | Integer symbol indices | Arbitrary feature vectors (`Tuple`) |
| **Model family** | Generative: models P(o, s) | Discriminative: models P(y \| x) |
| **Feature engineering** | None (vocabulary-level only) | Rich, arbitrary overlapping features |
| **Training data** | Labeled (`fit`) or unlabeled (Baum–Welch `update`) | Labeled only |
| **Inference algorithms** | Viterbi | Forward-backward (per-token) or Viterbi |
| **Typical accuracy** | Good baseline | Higher accuracy with good features |
| **Training speed** | Very fast (closed-form MLE) or iterative (Baum–Welch) | Slower (gradient boosting) |
| **Interpretability** | High (probabilistic parameters) | Medium (tree ensembles) |
| **Online learning** | Yes (Baum–Welch `update`) | No |
| **Serialization** | Yes | Yes |

**Choose HMM when:**
- Input consists of discrete symbols from a fixed vocabulary.
- You need online/incremental learning (Baum–Welch `update`).
- Training data is partially unlabeled.
- Speed and simplicity are priorities.
- The probabilistic model structure (Markov property + emissions) fits the domain.

**Choose CRF when:**
- Each position can be described by multiple overlapping features.
- High accuracy is the primary goal.
- You have fully labeled training data.
- You need per-token accuracy (`predict`) or globally coherent sequences (`viterbi`).
- The task benefits from non-local features or arbitrary input representations.

---

## 8. Common Patterns and Tips

### Serialization

Both `HMM` and `CRF` (and their labeler wrappers) are `Serializable`. Use
`smile.io.Write` / `smile.io.Read`:

```java
import smile.io.Write;
import smile.io.Read;
import java.nio.file.Path;

// Save
Write.object(labeler, Path.of("tagger.ser"));

// Load
CRFLabeler<?> loaded = (CRFLabeler<?>) Read.object(Path.of("tagger.ser"));
```

### Properties-driven configuration (CRF)

```java
// Save configuration to a properties file
Properties props = new CRF.Options(200, 10, 64, 3, 0.05).toProperties();
try (FileWriter w = new FileWriter("crf.properties")) {
    props.store(w, "CRF hyperparameters");
}

// Train with loaded properties
Properties loaded = new Properties();
try (FileReader r = new FileReader("crf.properties")) { loaded.load(r); }
CRFLabeler<String> labeler = CRFLabeler.fit(sequences, labels, features,
        CRF.Options.of(loaded));
```

### Evaluating accuracy

```java
int correct = 0, total = 0;
for (int i = 0; i < testSeqs.length; i++) {
    int[] predicted = labeler.predict(testSeqs[i]);
    for (int j = 0; j < predicted.length; j++) {
        if (predicted[j] == testLabels[i][j]) correct++;
        total++;
    }
}
System.out.printf("Token accuracy: %.2f%%%n", 100.0 * correct / total);
```

### Choosing between `predict` and `viterbi` at inference

For most sequence labeling tasks, run **both** on a held-out set and choose based on
your metric:

```java
int forwardBackwardErrors = 0;
int viterbiErrors = 0;
int n = 0;

for (int i = 0; i < testSeqs.length; i++) {
    int[] fb  = model.predict(testSeqs[i]);
    int[] vit = model.viterbi(testSeqs[i]);
    for (int j = 0; j < testSeqs[i].length; j++) {
        if (fb[j]  != testLabels[i][j]) forwardBackwardErrors++;
        if (vit[j] != testLabels[i][j]) viterbiErrors++;
        n++;
    }
}
System.out.printf("Forward-backward error: %.2f%%%n", 100.0 * forwardBackwardErrors / n);
System.out.printf("Viterbi error:          %.2f%%%n", 100.0 * viterbiErrors / n);
```

### Feature design for CRF

Good features for text sequence labeling typically include:

| Feature type | Example |
|---|---|
| Current token | `word.toLowerCase()` encoded as integer hash or index |
| Token shape | all-caps, capitalised, all-digits, mixed |
| Character n-grams | prefix/suffix of length 1–4 |
| Token length | number of characters |
| Context window | word at `t-1`, `t+1` encoded as separate fields |
| Gazetteer membership | is the word in a list of known names / places |
| Morphological flags | ends in "-ing", "-ed", "-ly", starts with upper case |

Each of these becomes a `StructField` in the schema. Keep features as integers (index
into a vocabulary) for compact representation; the CRF's internal regression trees
handle integer features natively.

### HMM: working with probabilities

- **Avoid `p(o)`** for long sequences — even with scaled forward, `Math.exp(logp(o))`
  underflows to 0 for sequences longer than a few hundred tokens. Always prefer
  `logp(o)` directly.
- **Verifying model parameters:** `P(o | H) ≤ P(o, s | H)` is never true — the
  joint is always ≤ the marginal: `p(o) ≥ p(o, s)` for any specific state sequence
  `s`.
- **After Baum–Welch,** inspect the parameter matrices for degenerate rows (all-zero
  emission probabilities) which indicate states that were never visited during EM.

### Memory and scale

| Concern | Guidance |
|---------|---------|
| Very long sequences (T > 10,000) | Both HMM and CRF work in linear time per sequence; memory is O(T × k). |
| Large vocabulary (M > 100,000) | HMM emission matrix `B` has N × M entries. For large M use a sparse representation or switch to CRF. |
| Many labels (k > 50) | CRF scales quadratically in k per position (k² states in the trellis). Evaluate `maxNodes` carefully to keep trees compact. |
| Large training set (n > 100,000 tokens) | CRF training runs in parallel per boosting round via `IntStream.parallel`. Use fewer trees with smaller `shrinkage` as a regulariser. |

---

*SMILE — Copyright © 2010-2026 Haifeng Li. GNU GPL licensed.*
