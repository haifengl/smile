# SMILE NLP — Collocation Finding

A **collocation** is a sequence of words that co-occur with statistical
significance — more often than would be expected by chance. Classic examples
are *New York*, *machine learning*, *carbon dioxide*, and *take care*.

| Class / Method | Role |
|----------------|------|
| `smile.nlp.Bigram` | Bigram (word pair) record with optional frequency and score |
| `smile.nlp.NGram` | N-gram (word sequence) record with optional frequency |
| `smile.nlp.Text` | Lightweight document holder |
| `smile.nlp.Corpus` | Corpus statistics interface |
| `smile.nlp.SimpleCorpus` | In-memory corpus + bigram collocation finder |
| `NGram.apriori(…)` | Apriori-based n-gram collocation extractor |

---

## Table of Contents

1. [Core Data Models](#1-core-data-models)
   - [Text](#11-text)
   - [Bigram](#12-bigram)
   - [NGram](#13-ngram)
2. [Building a Corpus](#2-building-a-corpus)
   - [Corpus Interface](#21-corpus-interface)
   - [SimpleCorpus](#22-simplecorpus)
3. [Bigram Collocations](#3-bigram-collocations)
   - [What is a Bigram Collocation?](#31-what-is-a-bigram-collocation)
   - [Top-k by Likelihood Ratio](#32-top-k-by-likelihood-ratio)
   - [All Significant Bigrams by p-value](#33-all-significant-bigrams-by-p-value)
4. [N-Gram Collocations](#4-n-gram-collocations)
   - [Algorithm Overview](#41-algorithm-overview)
   - [Extracting N-Grams with NGram.apriori](#42-extracting-n-grams-with-ngramapriori)
5. [Complete End-to-End Example](#5-complete-end-to-end-example)
6. [Parameter Tuning Guide](#6-parameter-tuning-guide)
7. [Design Notes](#7-design-notes)
7. [Design Notes](#7-design-notes)

---

## 1. Core Data Models

### 1.1 `Text`

`smile.nlp.Text` is a plain document holder. It can be created via factory
methods:

```java
import smile.nlp.Text;

// body-only (title is "", id is a random UUID)
Text doc = Text.of("The quick brown fox jumps over the lazy dog.");

// with title
Text doc = Text.of("Fable", "The quick brown fox jumps over the lazy dog.");
```

`Text` exposes `title()` and `content()` accessors.

### 1.2 `Bigram`

`smile.nlp.Bigram` is a **record** that models an ordered word pair, with
optional frequency and statistical score fields:

```java
public record Bigram(String w1, String w2, int count, double score)
        implements Comparable<Bigram> { … }
```

| Component | Description |
|-----------|-------------|
| `w1()` | First word |
| `w2()` | Second word |
| `count()` | Raw frequency in the corpus (`-1` when unknown) |
| `score()` | Likelihood-ratio statistic (`NaN` when unknown) |

```java
import smile.nlp.Bigram;

// Lookup key (count/score unknown)
Bigram key = new Bigram("machine", "learning");
System.out.println(key);          // (machine learning)

// With statistics (returned by SimpleCorpus.bigrams)
Bigram b = new Bigram("machine", "learning", 42, 312.7);
System.out.println(b);            // (machine learning, 42, 312.70)
System.out.println(b.w1());       // machine
System.out.println(b.score());    // 312.7
```

`equals` / `hashCode` compare the **word pair only**, making `Bigram` safe
as a `HashMap` key. `compareTo` orders by `score` (ascending), which is
intentionally inconsistent with `equals`; use `Collections.reverseOrder()`
to sort strongest collocations first.

### 1.3 `NGram`

`smile.nlp.NGram` is a **record** that models a contiguous word sequence:

```java
public record NGram(String[] words, int count) implements Comparable<NGram> { … }
```

| n | Name |
|---|------|
| 1 | unigram |
| 2 | bigram |
| 3 | trigram |
| 4 | 4-gram |

```java
import smile.nlp.NGram;

// Lookup key (count unknown)
NGram key = new NGram(new String[]{"New", "York", "City"});
System.out.println(key);           // [New, York, City]

// With frequency (returned by NGram.apriori)
NGram ng  = new NGram(new String[]{"machine", "learning"}, 17);
System.out.println(ng);            // ([machine, learning], 17)
System.out.println(ng.words()[0]); // machine
```

`equals` / `hashCode` use `Arrays.equals` / `Arrays.hashCode` on the word
array. `compareTo` orders by `count` ascending.

---

## 2. Building a Corpus

### 2.1 `Corpus` Interface

`smile.nlp.Corpus` provides the statistics needed by the bigram collocation
algorithms:

| Method | Description |
|--------|-------------|
| `size()` | Total number of tokens |
| `docCount()` | Number of documents |
| `termCount()` | Number of unique terms |
| `bigramCount()` | Number of unique bigrams |
| `avgDocSize()` | Average tokens per document |
| `count(String term)` | Frequency of a single term |
| `count(Bigram bigram)` | Frequency of a word pair |
| `terms()` | `Iterator` over all unique terms |
| `bigrams()` | `Iterator` over all unique bigrams |
| `search(String)` | Documents containing a term |
| `search(RelevanceRanker, String)` | Ranked search (single term) |
| `search(RelevanceRanker, String[])` | Ranked search (multiple terms) |

### 2.2 `SimpleCorpus`

`smile.nlp.SimpleCorpus` is the built-in in-memory implementation. Its
default constructor uses:

- `SimpleSentenceSplitter` for sentence segmentation
- `SimpleTokenizer` for word tokenization
- `EnglishStopWords.DEFAULT` for stop-word filtering
- `EnglishPunctuations` for punctuation filtering

```java
import smile.nlp.*;
import smile.nlp.tokenizer.*;
import smile.nlp.dictionary.*;

// Default corpus (English, stop words and punctuation filtered)
SimpleCorpus corpus = new SimpleCorpus();

// Custom components
SimpleCorpus corpus = new SimpleCorpus(
    SimpleSentenceSplitter.getInstance(),
    new SimpleTokenizer(),
    EnglishStopWords.COMPREHENSIVE,
    EnglishPunctuations.getInstance()
);
```

Create and add documents:

```java
// doc() applies the corpus's tokenizer, stop-word filter, etc.
corpus.add(corpus.doc("SMILE is a fast, general machine learning library."));
corpus.add(corpus.doc("Machine learning powers modern NLP applications."));
corpus.add(corpus.doc("Deep learning is a subfield of machine learning."));
```

Or load from a file:

```java
import smile.nlp.normalizer.SimpleNormalizer;

SimpleNormalizer normalizer = SimpleNormalizer.getInstance();
Files.lines(Path.of("data/corpus.txt"))
     .map(normalizer::normalize)
     .filter(line -> !line.isBlank())
     .forEach(line -> corpus.add(corpus.doc(line)));
```

After loading, inspect corpus statistics:

```java
System.out.println("Documents : " + corpus.docCount());
System.out.println("Tokens    : " + corpus.size());
System.out.println("Unique    : " + corpus.termCount());
System.out.println("Bigrams   : " + corpus.bigramCount());
System.out.println("Avg size  : " + corpus.avgDocSize());
```

---

## 3. Bigram Collocations

### 3.1 What is a Bigram Collocation?

A **bigram collocation** is a pair of adjacent words _w₁ w₂_ that appear
together significantly more often than expected under the independence
assumption.

SMILE uses the **log-likelihood ratio test statistic** (−2 log λ), which
follows a chi-square distribution with 1 degree of freedom under the null
hypothesis. Higher values indicate stronger evidence of collocation.

- **H₀:** _P(w₂ | w₁) = P(w₂)_ — the words are independent
- **H₁:** _P(w₂ | w₁) ≠ P(w₂)_ — the words co-occur non-randomly

Both collocation methods live directly on `SimpleCorpus`.

### 3.2 Top-k by Likelihood Ratio

```java
List<Bigram> bigrams(int k, int minFrequency)
```

Returns the **top `k`** bigrams by likelihood-ratio score, considering only
bigrams whose raw count exceeds `minFrequency`. Results are sorted descending
by score.

```java
SimpleCorpus corpus = new SimpleCorpus();
// ... load documents ...

// Top 10 bigrams appearing more than 5 times
List<Bigram> top10 = corpus.bigrams(10, 5);

for (Bigram b : top10) {
    System.out.printf("%-20s  count=%-5d  score=%.2f%n",
        b.w1() + " " + b.w2(), b.count(), b.score());
}
```

Example output:
```
new york              count=46     score=545.16
carbon dioxide        count=38     score=412.83
european union        count=31     score=298.74
```

**Parameters:**
- `k` — must be positive; throws `IllegalArgumentException` otherwise.
- `minFrequency` — exclusive lower bound on count (`count > minFrequency`).

### 3.3 All Significant Bigrams by p-value

```java
List<Bigram> bigrams(double p, int minFrequency)
```

Returns **all** bigrams whose p-value is below the threshold `p`, filtered by
`minFrequency`. Results are sorted descending by score.

```java
// All bigrams significant at the 0.01% level
List<Bigram> significant = corpus.bigrams(0.0001, 5);
System.out.println("Significant bigrams: " + significant.size());
```

**Parameters:**
- `p` — must satisfy `0 < p < 1`; throws `IllegalArgumentException` otherwise.
- Smaller `p` = stricter test = fewer results.

**Choosing `p`:**

| p-value | Interpretation |
|---------|---------------|
| `0.05`  | Significant at 5 % level (lenient) |
| `0.01`  | Significant at 1 % level |
| `0.001` | Significant at 0.1 % level |
| `0.0001`| Very highly significant (strict) |

---

## 4. N-Gram Collocations

### 4.1 Algorithm Overview

`NGram.apriori` implements an **Apriori-like** pruning algorithm (Furnkranz
1998) that efficiently extracts all frequent n-gram collocations from a
collection of tokenized sentences:

1. **Unigrams (n = 1):** Count every single token. Keep those with
   frequency ≥ `minFrequency`.
2. **Bigrams (n = 2):** A candidate bigram _(w, w+1)_ is counted only if
   both component unigrams survived step 1.
3. **Trigrams (n = 3):** A candidate trigram is counted only if both its
   leading bigram _(w, w+1)_ and trailing bigram _(w+1, w+2)_ survived step 2.
4. **General:** Extend one word at a time using the set of surviving
   (n−1)-grams as a pruning filter.
5. **Stop-word filter:** Discard n-grams whose **first or last** word is in
   `EnglishStopWords.DEFAULT`. Interior stop words are permitted (e.g.,
   *"commander in chief"* is kept).

The Apriori pruning avoids counting n-grams that can never be frequent because
a shorter sub-sequence is already rare, making the algorithm efficient even for
large documents.

### 4.2 Extracting N-Grams with `NGram.apriori`

```java
public static NGram[][] apriori(Collection<String[]> sentences,
                                int maxNGramSize,
                                int minFrequency)
```

**Input:** A collection of tokenized sentences (each as a `String[]`).  
**Output:** An array of length `maxNGramSize + 1`. Index `i` holds the
`NGram[]` for i-grams, sorted **descending by count**. Index `0` is always
empty.

```java
import smile.nlp.NGram;
import smile.nlp.stemmer.PorterStemmer;
import smile.nlp.tokenizer.*;

PorterStemmer stemmer = new PorterStemmer();
SimpleTokenizer tokenizer = new SimpleTokenizer();
List<String[]> sentences = new ArrayList<>();

String text = Files.readString(Path.of("data/article.txt"));
for (String para : SimpleParagraphSplitter.getInstance().split(text)) {
    for (String sent : SimpleSentenceSplitter.getInstance().split(para)) {
        String[] tokens = tokenizer.split(sent);
        // lowercase + stem for better conflation
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = stemmer.stem(tokens[i].toLowerCase());
        }
        sentences.add(tokens);
    }
}

// Extract up to 4-grams appearing at least 4 times
NGram[][] result = NGram.apriori(sentences, 4, 4);

// result[0] → empty  (no such thing as 0-grams)
// result[1] → significant unigrams
// result[2] → significant bigrams
// result[3] → significant trigrams
// result[4] → significant 4-grams

for (int n = 1; n < result.length; n++) {
    System.out.println("=== " + n + "-grams ===");
    for (NGram ng : result[n]) {
        System.out.println(ng);   // ([word1, word2], count)
    }
}
```

Example output:
```
=== 1-grams ===
([machin], 42)
([learn], 39)
([algorithm], 28)
…
=== 2-grams ===
([machin, learn], 17)
([neural, network], 12)
…
=== 3-grams ===
([deep, neural, network], 7)
…
```

Each `NGram[]` is sorted in **descending order by count**, so the most
frequent n-grams come first.

---

## 5. Complete End-to-End Example

```java
import smile.nlp.*;
import smile.nlp.normalizer.SimpleNormalizer;
import smile.nlp.stemmer.PorterStemmer;
import smile.nlp.tokenizer.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CollocationExample {
    public static void main(String[] args) throws Exception {
        SimpleNormalizer normalizer = SimpleNormalizer.getInstance();

        // ── Bigram collocations ──────────────────────────────────────────
        SimpleCorpus corpus = new SimpleCorpus();
        Files.lines(Path.of("data/news.txt"))
             .map(normalizer::normalize)
             .filter(line -> !line.isBlank())
             .forEach(line -> corpus.add(corpus.doc(line)));

        System.out.printf("Corpus: %d documents, %d tokens%n",
                corpus.docCount(), corpus.size());

        // Top 20 bigrams appearing more than 3 times
        System.out.println("\n=== Top 20 Bigram Collocations ===");
        for (Bigram b : corpus.bigrams(20, 3)) {
            System.out.printf("  %-25s  count=%3d  score=%7.2f%n",
                b.w1() + " " + b.w2(), b.count(), b.score());
        }

        // All bigrams significant at p < 0.001
        List<Bigram> sig = corpus.bigrams(0.001, 3);
        System.out.printf("%nBigrams significant at p<0.001: %d%n", sig.size());

        // ── N-gram collocations ──────────────────────────────────────────
        PorterStemmer stemmer = new PorterStemmer();
        SimpleTokenizer tokenizer = new SimpleTokenizer();
        List<String[]> sentences  = new ArrayList<>();

        String text = Files.readString(Path.of("data/article.txt"));
        text = normalizer.normalize(text);

        for (String para : SimpleParagraphSplitter.getInstance().split(text)) {
            for (String sent : SimpleSentenceSplitter.getInstance().split(para)) {
                String[] tokens = tokenizer.split(sent);
                for (int i = 0; i < tokens.length; i++) {
                    tokens[i] = stemmer.stem(tokens[i].toLowerCase());
                }
                sentences.add(tokens);
            }
        }

        System.out.println("\n=== N-Gram Collocations (max 4-gram, min freq 3) ===");
        NGram[][] ngrams = NGram.apriori(sentences, 4, 3);
        for (int n = 1; n < ngrams.length; n++) {
            System.out.printf("%n--- %d-grams: %d ---%n", n, ngrams[n].length);
            int limit = Math.min(5, ngrams[n].length);
            for (int i = 0; i < limit; i++) {
                System.out.println("  " + ngrams[n][i]);
            }
        }
    }
}
```

---

## 6. Parameter Tuning Guide

### `minFrequency`

Both `SimpleCorpus.bigrams` and `NGram.apriori` use `minFrequency` as an
**exclusive** lower bound (`count > minFrequency`). A bigram or n-gram with
exactly `count == minFrequency` is **not** included.

| Corpus size | Recommended `minFrequency` |
|-------------|---------------------------|
| < 1 000 tokens | 1–2 |
| 1 000 – 10 000 tokens | 2–5 |
| 10 000 – 100 000 tokens | 5–10 |
| > 100 000 tokens | 10–50 |

### `k` (top-k bigrams)

Use `corpus.bigrams(k, minFrequency)` when you need a fixed-size list (e.g.,
for a feature vector or display). Start with `k = 20` and adjust.

### `p` (significance threshold)

Use `corpus.bigrams(p, minFrequency)` when you need all statistically
significant bigrams. A stricter `p` (e.g., `0.0001`) avoids spurious
collocations; a looser `p` (e.g., `0.05`) maximises recall.

### `maxNGramSize`

For most NLP tasks `maxNGramSize = 3` or `4` is sufficient. Large values
increase memory and computation while the count of qualifying n-grams drops
rapidly.

### Stemming before `NGram.apriori`

Stemming before calling `NGram.apriori` merges inflected forms (*run / runs /
running*) into a single token, increasing the effective frequency of each
n-gram and improving recall:

```java
tokens[i] = stemmer.stem(tokens[i].toLowerCase());
```

---

## 7. Design Notes

### Migration from `smile.nlp.collocation`

The old `smile.nlp.collocation.Bigram` and `smile.nlp.collocation.NGram`
classes have been removed. The new location and equivalent API are:

| Old | New |
|-----|-----|
| `smile.nlp.collocation.Bigram` | `smile.nlp.Bigram` (record) |
| `smile.nlp.collocation.NGram` | `smile.nlp.NGram` (record) |
| `smile.nlp.collocation.Bigram.of(corpus, k, minFreq)` | `corpus.bigrams(k, minFreq)` |
| `smile.nlp.collocation.Bigram.of(corpus, p, minFreq)` | `corpus.bigrams(p, minFreq)` |
| `smile.nlp.collocation.NGram.of(sentences, max, minFreq)` | `NGram.apriori(sentences, max, minFreq)` |

Field access has also changed from public instance variables to **record
accessor methods**: `b.w1` → `b.w1()`, `ng.words` → `ng.words()`, etc.

### Likelihood Ratio vs. Mutual Information

SMILE uses the **log-likelihood ratio** (G²) statistic rather than pointwise
mutual information (PMI). The likelihood ratio is preferred because:

- It is a proper significance test with a known chi-square null distribution.
- It is less sensitive to rare events (PMI is inflated for rare pairs).
- It scales better to large corpora.

### Stop-word Filtering in `NGram.apriori`

An n-gram is discarded if its **first or last** word is in
`EnglishStopWords.DEFAULT`. This removes n-grams like *"the president"* or
*"of the"* which are grammatically common but not meaningful collocations.
Content words in the interior of an n-gram (e.g., *"commander in chief"*) are
permitted. To use a different stop-word list, pre-filter your tokens before
calling `NGram.apriori`.

### Thread Safety

| Component | Thread-safe? | Notes |
|-----------|-------------|-------|
| `NGram.apriori` | ✅ Yes | Stateless static method |
| `SimpleCorpus.bigrams` | ✅ Yes (read-only) | Safe once corpus is built |
| `SimpleCorpus.add` / `doc` | ❌ No | Build corpus in a single thread |

---

## API Quick-Reference

```java
// ── Bigram ───────────────────────────────────────────────────────────
Bigram key  = new Bigram("machine", "learning");          // lookup key
Bigram full = new Bigram("machine", "learning", 42, 312.7); // with stats
String w1   = full.w1();      // "machine"
int count   = full.count();   // 42
double score = full.score();  // 312.7

// ── NGram ────────────────────────────────────────────────────────────
NGram ng    = new NGram(new String[]{"machine","learning"}, 17);
String[] ws = ng.words();     // ["machine", "learning"]
int freq    = ng.count();     // 17

// ── SimpleCorpus bigram collocations ─────────────────────────────────
SimpleCorpus corpus = new SimpleCorpus();
corpus.add(corpus.doc("Some text goes here."));

List<Bigram> top20 = corpus.bigrams(20, 3);          // top-k
List<Bigram> sig   = corpus.bigrams(0.001, 3);       // by p-value

// ── NGram.apriori ────────────────────────────────────────────────────
NGram[][] result = NGram.apriori(sentences, 4, 4);   // up to 4-grams
NGram[] bigrams  = result[2];                         // bigrams
NGram[] trigrams = result[3];                         // trigrams
```

---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

