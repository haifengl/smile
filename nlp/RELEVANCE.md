# SMILE NLP — Relevance Ranking

Information retrieval is the task of finding documents in a corpus that
are most relevant to a user's query. The `smile.nlp.relevance` package
provides the scoring functions that power ranked search in `SimpleCorpus`.

| Class / Interface | Role                                                              |
|-------------------|-------------------------------------------------------------------|
| `RelevanceRanker` | Strategy interface — one `rank()` call per query term or term set |
| `Relevance` | Result record pairing a `Text` with its relevance `score`         |
| `TFIDF` | Classic TF-IDF with max-tf normalization                         |
| `BM25` | Okapi BM25 / BM25+ family (state of the art)                      |

---

## Core concepts

### Term Frequency (TF)

How often a query term appears in a document. A higher raw count means
the document is likely more *about* that term, but raw counts need
normalization to prevent long documents from dominating rankings.

### Inverse Document Frequency (IDF)

The log-ratio of the total number of documents to the number that contain
the term. Rare terms (high IDF) are more discriminative; stop words like
"the" appear everywhere (low IDF) and contribute almost nothing to rank.

### The `RelevanceRanker` interface

All scoring strategies implement a single interface:

```java
public interface RelevanceRanker {
    // Single-term scoring
    double rank(Corpus corpus, Document doc, String term, int tf, int n);

    // Multi-term scoring (sum of per-term scores by default)
    double rank(Corpus corpus, Document doc, String[] terms, int[] tf, int n);
}
```

`SimpleCorpus.search(RelevanceRanker, String)` calls `rank()` for every
matching document and returns results sorted highest-first.

### The `Relevance` record

`Relevance` pairs a document with its score:

```java
public record Relevance(Text text, double score) implements Comparable<Relevance> { … }
```

`compareTo` sorts in **ascending** order (lower score first). Use
`Comparator.reverseOrder()` — which `SimpleCorpus.search()` already
does — to get the most-relevant results first:

```java
Iterator<Relevance> results = corpus.search(new BM25(), "machine");
while (results.hasNext()) {
    Relevance r = results.next();
    System.out.printf("%.4f  %s%n", r.score(), r.text().title());
}
```

---

## TF-IDF — `TFIDF`

### Formula

$$\text{score}(t, d) = \left(a + (1-a)\cdot\frac{\text{tf}_{t,d}}{\text{maxtf}_d}\right) \cdot \ln\!\left(\frac{N}{n}\right)$$

| Symbol | Meaning |
|--------|---------|
| `a` | Smoothing parameter ∈ [0, 1] (default **0.4**) |
| tf<sub>t,d</sub> | Raw frequency of term *t* in document *d* |
| maxtf<sub>d</sub> | Maximum frequency of any term in *d* |
| *N* | Total number of documents in the corpus |
| *n* | Number of documents containing term *t* |

**Max-tf normalization** divides each tf by the maximum tf in the
document, then blends it with the smoothing constant `a`. This prevents
a single very frequent term from dominating all other terms in the same
document.

### Effect of the smoothing parameter

| `a` value | Behaviour |
|-----------|-----------|
| `0.0` | Pure relative TF: score ∝ tf/maxtf |
| `0.4` *(default)* | Moderate smoothing — recommended for most corpora |
| `0.5` | Common alternative in older literature |
| `1.0` | TF becomes flat (1.0 for all terms) — pure IDF ranking |

### Usage

```java
import smile.nlp.*;
import smile.nlp.relevance.*;
import java.util.Iterator;

SimpleCorpus corpus = new SimpleCorpus();
corpus.add(corpus.doc("The quick brown fox jumps over the lazy dog."));
corpus.add(corpus.doc("Machine learning is a branch of artificial intelligence."));
corpus.add(corpus.doc("Deep learning uses neural networks with many layers."));
corpus.add(corpus.doc("Natural language processing enables machines to understand text."));

// Default smoothing (a = 0.4)
RelevanceRanker tfidf = new TFIDF();

Iterator<Relevance> results = corpus.search(tfidf, "learning");
while (results.hasNext()) {
    Relevance r = results.next();
    System.out.printf("%.4f  %s%n", r.score(), r.text().content());
}
```

### Low-level `rank()` method

You can also call the scoring formula directly without a `Corpus`:

```java
TFIDF tfidf = new TFIDF(0.4);           // smoothing = 0.4
double score = tfidf.rank(
        3,          // tf   — term appears 3 times in the document
        10,         // maxtf — most frequent term in document appears 10 times
        10_000_000, // N    — 10 M documents in corpus
        1_000       // n    — term appears in 1 000 documents
);
// score ≈ (0.4 + 0.6*0.3) * ln(10000) ≈ 0.58 * 9.21 ≈ 5.34
```

---

## BM25 / BM25+ — `BM25`

BM25 (Okapi BM25) is the de facto standard for term-based document
retrieval and consistently outperforms vanilla TF-IDF in benchmarks.
SMILE implements **BM25+**, which adds a lower-bound `delta` to prevent
long matching documents from being unfairly penalized.

### Formula

#### Standard body scoring with document-length normalization

$$\text{tf\_norm} = \frac{\text{tf} \cdot (k_1+1)}{\text{tf} + k_1\!\left(1 - b + b\cdot\dfrac{|d|}{\text{avgdl}}\right)}$$

$$\text{IDF} = \ln\!\left(\frac{N - n + 0.5}{n + 0.5} + 1\right)$$

$$\text{score}(t, d) = (\text{tf\_norm} + \delta) \cdot \text{IDF}$$

| Parameter | Default | Role                                                                                                                         |
|-----------|---------|------------------------------------------------------------------------------------------------------------------------------|
| `k1` | **1.2** | TF saturation speed. `k1 = 0` → binary model; large → raw TF.                                                                |
| `b` | **0.75** | Length normalization strength. `b = 0` → BM15 (no normalization); `b = 1` → BM11 (full normalization).                      |
| `delta` | **1.0** | BM25+ lower bound. Prevents matching long documents from scoring below non-matching short ones. Set to `0` for classic BM25. |

### Parameter guidelines

| Goal                                | Recommendation |
|-------------------------------------|---------------|
| Short queries, news-style documents | `k1 ∈ [1.2, 2.0]`, `b = 0.75` |
| Verbose technical documents         | Increase `b` toward `1.0` |
| Disable length normalization        | `b = 0` (BM15) |
| Classic BM25 (no BM25+ extension)   | `delta = 0` |

### Usage

```java
// Default BM25+ (k1=1.2, b=0.75, delta=1.0)
RelevanceRanker bm25 = new BM25();

Iterator<Relevance> results = corpus.search(bm25, "neural");
while (results.hasNext()) {
    Relevance r = results.next();
    System.out.printf("%.4f  %s%n", r.score(), r.text().content());
}
```

Custom parameters:

```java
// More aggressive TF saturation, no BM25+ delta
BM25 custom = new BM25(2.0, 0.75, 0.0);
```

### Multi-field scoring (`score()` overload)

BM25 also exposes a multi-field `score()` method that combines body,
title and anchor-text signals with fixed BM25F weights — useful when
documents have a structured title and hyperlinks:

```java
BM25 bm25f = new BM25();
double score = bm25f.score(
        termFreq,       // body tf
        docSize,        // body length
        avgDocSize,     // average body length
        titleTermFreq,  // title tf
        titleSize,      // title length
        avgTitleSize,   // average title length
        anchorTermFreq, // anchor tf
        anchorSize,     // anchor length
        avgAnchorSize,  // average anchor length
        N,              // total documents
        n               // documents containing the term
);
```

### Low-level `score()` methods

```java
BM25 bm25 = new BM25(2.0, 0.75, 0.0);

// With document-length normalization
double s1 = bm25.score(
        3.0,        // term frequency in document
        100,        // document length (tokens)
        150.0,      // average document length
        10_000_000, // N
        1_000       // n
);

// Without length normalization (pre-normalized freq)
double s2 = bm25.score(
        3.0,        // normalized frequency
        10_000_000, // N
        1_000       // n
);
```

---

## End-to-end search example

```java
import smile.nlp.*;
import smile.nlp.relevance.*;
import java.util.Iterator;
import java.util.List;

// 1. Build a corpus
SimpleCorpus corpus = new SimpleCorpus();
List<String> texts = List.of(
    "Artificial intelligence and machine learning are transforming technology.",
    "Deep learning is a subfield of machine learning.",
    "Natural language processing uses statistical models and machine learning.",
    "Computer vision relies on deep learning and convolutional neural networks.",
    "The history of artificial intelligence dates back to the 1950s."
);
for (String text : texts) {
    corpus.add(corpus.doc(text));
}

// 2. Rank with BM25 (single term)
System.out.println("=== BM25: 'machine' ===");
RelevanceRanker bm25 = new BM25();
Iterator<Relevance> it = corpus.search(bm25, "machine");
while (it.hasNext()) {
    Relevance r = it.next();
    System.out.printf("%.4f  %s%n", r.score(), r.text().content());
}

// 3. Multi-term search (OR semantics — union of posting lists)
System.out.println("\n=== BM25: 'deep' + 'neural' ===");
it = corpus.search(bm25, new String[]{"deep", "neural"});
while (it.hasNext()) {
    Relevance r = it.next();
    System.out.printf("%.4f  %s%n", r.score(), r.text().content());
}

// 4. Switch to TF-IDF
System.out.println("\n=== TF-IDF: 'learning' ===");
RelevanceRanker tfidf = new TFIDF();
it = corpus.search(tfidf, "learning");
while (it.hasNext()) {
    Relevance r = it.next();
    System.out.printf("%.4f  %s%n", r.score(), r.text().content());
}
```

---

## Sorting `Relevance` results manually

`Relevance` implements `Comparable<Relevance>` with *ascending* natural
order. When you hold a `List<Relevance>` and want to sort it:

```java
List<Relevance> list = new ArrayList<>();
// … populate list …

// Most relevant first (descending score)
list.sort(Comparator.reverseOrder());

// Least relevant first (ascending score) — natural order
Collections.sort(list);
```

`SimpleCorpus.search(RelevanceRanker, …)` always returns results in
descending order (most relevant first).

---

## Choosing between TF-IDF and BM25

| Criterion | TF-IDF | BM25                                  |
|-----------|--------|---------------------------------------|
| Simplicity | ✓ Straightforward formula | Slightly more complex                 |
| Long documents | Bias toward long docs | Length-normalized by `b`              |
| TF saturation | None — linear in tf | Logarithmic saturation via `k1`       |
| Tunable | One parameter (`a`) | Three parameters (`k1`, `b`, `delta`) |
| Benchmark accuracy | Good baseline | State of the art                      |
| Recommended for | Simple prototypes, small corpora | Production search, large corpora      |

**Rule of thumb:** start with `new BM25()` (defaults work well for most
English document collections). Switch to `TFIDF` only when you need a
simpler, interpretable baseline.

---

## API quick-reference

```java
// ── TFIDF ───────────────────────────────────────────────────────────
TFIDF tfidf = new TFIDF();          // smoothing = 0.4
TFIDF tfidf = new TFIDF(0.5);       // custom smoothing ∈ [0, 1]
double score = tfidf.rank(tf, maxtf, N, n);   // direct formula

// ── BM25 ────────────────────────────────────────────────────────────
BM25 bm25 = new BM25();                       // k1=1.2, b=0.75, delta=1.0
BM25 bm25 = new BM25(k1, b, delta);           // custom params
double s   = bm25.score(freq, docSize, avgDocSize, N, n);  // with length
double s   = bm25.score(freq, N, n);                       // pre-normalized

// ── RelevanceRanker (via SimpleCorpus) ──────────────────────────────
Iterator<Relevance> it = corpus.search(ranker, "query");
Iterator<Relevance> it = corpus.search(ranker, new String[]{"q1","q2"});

// ── Relevance record ────────────────────────────────────────────────
Text   doc   = r.text();
double score = r.score();
list.sort(Comparator.reverseOrder());  // most-relevant first
```


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

