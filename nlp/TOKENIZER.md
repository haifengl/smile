# SMILE NLP — Tokenizers and Text Splitters

Tokenization is the foundational step in almost every NLP pipeline: raw text
must be broken into discrete units (tokens, sentences, paragraphs) before any
further processing can take place. The `smile.nlp.tokenizer` package provides
a clean three-level hierarchy of splitters together with multiple
implementations suited for different use cases.

---

## Package Overview

| Level | Interface | Implementations |
|-------|-----------|-----------------|
| Paragraph | `ParagraphSplitter` | `SimpleParagraphSplitter` |
| Sentence  | `SentenceSplitter`  | `SimpleSentenceSplitter`, `BreakIteratorSentenceSplitter` |
| Word      | `Tokenizer`         | `SimpleTokenizer`, `PennTreebankTokenizer`, `BreakIteratorTokenizer` |

All three interfaces extend `java.util.function.Function<String, String[]>`,
so any splitter can be used directly in a Java stream pipeline.

A supporting dictionary class, `EnglishAbbreviations`, is used internally by
the English-specific implementations to avoid mis-splitting abbreviation
periods.

---

## Interfaces

### `Tokenizer`

```java
public interface Tokenizer extends Function<String, String[]> {
    String[] split(String text);   // tokenize text into words/tokens
}
```

### `SentenceSplitter`

```java
public interface SentenceSplitter extends Function<String, String[]> {
    String[] split(String text);   // segment text into sentences
}
```

### `ParagraphSplitter`

```java
public interface ParagraphSplitter extends Function<String, String[]> {
    String[] split(String text);   // segment text into paragraphs
}
```

Because all three interfaces implement `Function<String, String[]>`, you can
compose them with standard Java functional utilities:

```java
SentenceSplitter splitter = SimpleSentenceSplitter.getInstance();
Tokenizer tokenizer       = new SimpleTokenizer();

// Use as Function in a stream
String[] sentences = splitter.apply(text);
String[][] tokens  = Arrays.stream(sentences)
        .map(tokenizer)
        .toArray(String[][]::new);
```

---

## Word Tokenizers

### `SimpleTokenizer`

`SimpleTokenizer` is the recommended general-purpose English word tokenizer.
It handles contractions, possessives, punctuation, and abbreviation-final
periods sensibly.

**Key behaviours:**

* Splits most punctuation from adjoining words.
* Expands contractions to their full forms:

  | Input | Output tokens |
  |-------|---------------|
  | `won't` | `will not` |
  | `can't` | `can not` |
  | `shan't` | `shall not` |
  | `cannot` | `can not` |
  | `weren't` | `were not` |
  | `'tisn't` | `it is not` |
  | `I'm` | `I 'm` |
  | `he'll` | `he 'll` |
  | `gonna` | `gon na` |

* Keeps abbreviation-terminal periods attached (e.g., `etc.` stays `etc.`
  at the end of a sentence, but emits an additional `.` sentence-terminal
  token).
* Commas inside numbers (`2,500`) are **not** split.
* **Thread safety:** instances are independent and thread-safe (no shared
  mutable state; each call is stateless beyond the compiled `Pattern`
  constants).

#### Basic usage

```java
import smile.nlp.tokenizer.SimpleTokenizer;

SimpleTokenizer tokenizer = new SimpleTokenizer();

String[] tokens = tokenizer.split(
    "Dr. Smith won't attend the conference, but she'll send her notes.");

System.out.println(java.util.Arrays.toString(tokens));
// [Dr., Smith, will, not, attend, the, conference, ,, but, she, 'll, send, her, notes, .]
```

#### Numeric and punctuation edge cases

```java
SimpleTokenizer tokenizer = new SimpleTokenizer();

// Commas inside numbers are not split
System.out.println(Arrays.toString(tokenizer.split("Population is 2,500,000.")));
// [Population, is, 2,500,000, .]

// Ellipsis is separated
System.out.println(Arrays.toString(tokenizer.split("Wait... then go.")));
// [Wait, ..., then, go, .]
```

---

### `PennTreebankTokenizer`

`PennTreebankTokenizer` follows the tokenization conventions of the Penn
Treebank corpus. It is a singleton (use `PennTreebankTokenizer.getInstance()`)
and is the standard choice when your downstream models (e.g., `HMMPOSTagger`)
were trained on Penn Treebank data.

**Key differences from `SimpleTokenizer`:**

| Input | `SimpleTokenizer` | `PennTreebankTokenizer` |
|-------|-------------------|------------------------|
| `won't` | `will not` | `wo n't` |
| `can't` | `can not` | `ca n't` |
| `'tisn't` | `it is not` | `'t is n't` |

The Penn Treebank convention keeps the contracted negative `n't` as a separate
morpheme; `SimpleTokenizer` expands to natural English forms instead.

#### Basic usage

```java
import smile.nlp.tokenizer.PennTreebankTokenizer;

PennTreebankTokenizer tokenizer = PennTreebankTokenizer.getInstance();

String[] tokens = tokenizer.split("They couldn't have known.");
System.out.println(java.util.Arrays.toString(tokens));
// [They, could, n't, have, known, .]
```

#### When to use

* Use `PennTreebankTokenizer` when feeding tokens to models trained on
  Penn Treebank data (including `HMMPOSTagger`).
* Use `SimpleTokenizer` for all other English NLP tasks where natural-English
  token forms are preferred.

---

### `BreakIteratorTokenizer`

`BreakIteratorTokenizer` wraps Java's `java.text.BreakIterator` for word
segmentation. It supports **any locale** supported by the JVM, making it the
right choice for non-English text.

> ⚠️ **Not thread-safe.** `BreakIterator` maintains internal state; each
> thread must create its own instance.

#### Basic usage

```java
import smile.nlp.tokenizer.BreakIteratorTokenizer;
import java.util.Locale;

// Default locale
BreakIteratorTokenizer tokenizer = new BreakIteratorTokenizer();
System.out.println(java.util.Arrays.toString(tokenizer.split("Hello, world!")));

// Explicit locale
BreakIteratorTokenizer frTokenizer = new BreakIteratorTokenizer(Locale.FRENCH);
System.out.println(java.util.Arrays.toString(frTokenizer.split("Bonjour, le monde!")));
```

#### Multi-threaded use

```java
ThreadLocal<BreakIteratorTokenizer> tlTokenizer =
        ThreadLocal.withInitial(BreakIteratorTokenizer::new);

// In each thread:
BreakIteratorTokenizer tokenizer = tlTokenizer.get();
String[] tokens = tokenizer.split(text);
```

---

## Sentence Splitters

### `SimpleSentenceSplitter`

`SimpleSentenceSplitter` is the recommended English sentence splitter. It is a
singleton that uses a set of regular-expression heuristics to handle the
hardest cases:

* A `.` after a known abbreviation (`Mr.`, `Dr.`, `etc.`, `vs.`, …) is
  **not** treated as a sentence boundary.
* `.` followed by a lowercase letter is not a boundary.
* `.` at the end of the string or before a newline is always a boundary.
* `?` and `!` are always boundaries.
* Treats carriage returns as whitespace (expects paragraph-segmented input).

> Assumes input has already been split into paragraphs. Feed each paragraph
> individually for best results.

#### Basic usage

```java
import smile.nlp.tokenizer.SimpleSentenceSplitter;

SimpleSentenceSplitter splitter = SimpleSentenceSplitter.getInstance();

String paragraph =
    "Dr. Smith attended the conf. in Jan. He presented his findings. "
  + "Was the result surprising? Absolutely!";

for (String sentence : splitter.split(paragraph)) {
    System.out.println(sentence);
}
// Dr. Smith attended the conf. in Jan.
// He presented his findings.
// Was the result surprising?
// Absolutely!
```

#### Thread safety

`SimpleSentenceSplitter` is a stateless singleton and is **thread-safe**.

---

### `BreakIteratorSentenceSplitter`

`BreakIteratorSentenceSplitter` wraps `java.text.BreakIterator` for sentence
segmentation. Like `BreakIteratorTokenizer`, it supports **any locale**.

> ⚠️ **Not thread-safe.** Create one instance per thread.

#### Basic usage

```java
import smile.nlp.tokenizer.BreakIteratorSentenceSplitter;
import java.util.Locale;

// Default locale
BreakIteratorSentenceSplitter splitter = new BreakIteratorSentenceSplitter();

// Specific locale
BreakIteratorSentenceSplitter deSplitter =
        new BreakIteratorSentenceSplitter(Locale.GERMAN);

for (String sentence : deSplitter.split("Das ist ein Test. Und noch ein Satz.")) {
    System.out.println(sentence);
}
// Das ist ein Test.
// Und noch ein Satz.
```

---

## Paragraph Splitter

### `SimpleParagraphSplitter`

`SimpleParagraphSplitter` is a singleton that segments text into paragraphs by
splitting on **one or more blank lines**. A blank line is any line containing
only whitespace characters.

It also handles the Unicode paragraph separator character (U+2029).

#### Basic usage

```java
import smile.nlp.tokenizer.SimpleParagraphSplitter;

SimpleParagraphSplitter splitter = SimpleParagraphSplitter.getInstance();

String document =
    "First paragraph with multiple sentences. It continues here.\n\n"
  + "Second paragraph begins after the blank line.\n\n"
  + "Third paragraph.";

for (String para : splitter.split(document)) {
    System.out.println("PARAGRAPH: " + para);
}
// PARAGRAPH: First paragraph with multiple sentences. It continues here.
// PARAGRAPH: Second paragraph begins after the blank line.
// PARAGRAPH: Third paragraph.
```

`SimpleParagraphSplitter` is stateless and **thread-safe**.

---

## English Abbreviations — `EnglishAbbreviations`

`EnglishAbbreviations` is a package-private interface that exposes a static
dictionary of common English abbreviations loaded from the classpath resource
`abbreviations_en.txt`. It is used internally by `SimpleSentenceSplitter` and
`PennTreebankTokenizer` to avoid splitting on abbreviation periods.

The dictionary includes titles (`Mr`, `Mrs`, `Dr`, `Prof`), calendar items
(`Jan`, `Feb`, `Mon`, `Tue`), geographic terms (`Ave`, `Blvd`, `St`),
Latin abbreviations (`etc`, `vs`, `cf`, `al`), and more. It is not directly
accessible from outside the package.

---

## Complete Pipeline Example

A typical NLP preprocessing pipeline works in three stages: paragraph →
sentence → token.

```java
import smile.nlp.tokenizer.*;
import smile.nlp.stemmer.PorterStemmer;
import smile.nlp.pos.*;

// ── Splitters & tokenizer ────────────────────────────────────────────
ParagraphSplitter paragraphSplitter = SimpleParagraphSplitter.getInstance();
SentenceSplitter  sentenceSplitter  = SimpleSentenceSplitter.getInstance();
Tokenizer         tokenizer         = new SimpleTokenizer();
HMMPOSTagger      tagger            = HMMPOSTagger.getDefault();

ThreadLocal<PorterStemmer> tlStemmer = ThreadLocal.withInitial(PorterStemmer::new);

// ── Input document ───────────────────────────────────────────────────
String document =
    "Alan Turing was a British mathematician. "
  + "He proposed the Turing test in 1950.\n\n"
  + "His work laid the foundation for computer science.";

// ── Pipeline ─────────────────────────────────────────────────────────
PorterStemmer stemmer = tlStemmer.get();

for (String paragraph : paragraphSplitter.split(document)) {
    for (String sentence : sentenceSplitter.split(paragraph)) {
        String[] tokens = tokenizer.split(sentence);
        PennTreebankPOS[] tags = tagger.tag(tokens);

        for (int i = 0; i < tokens.length; i++) {
            if (tags[i].open) { // content word
                String stem = stemmer.stem(tokens[i].toLowerCase());
                System.out.printf("%-20s %-6s %s%n", tokens[i], tags[i], stem);
            }
        }
        System.out.println();
    }
}
```

---

## Choosing the Right Implementation

### Word tokenizer

| Scenario | Recommended |
|----------|-------------|
| General English text | `SimpleTokenizer` |
| Penn Treebank / pre-trained NLP models | `PennTreebankTokenizer` |
| Non-English or multilingual | `BreakIteratorTokenizer` |

### Sentence splitter

| Scenario | Recommended |
|----------|-------------|
| English text (production use) | `SimpleSentenceSplitter` |
| Multilingual / locale-sensitive | `BreakIteratorSentenceSplitter` |

### Paragraph splitter

| Scenario | Recommended |
|----------|-------------|
| Any text with blank-line paragraph boundaries | `SimpleParagraphSplitter` |

---

## Thread-Safety Summary

| Class | Thread-safe? | Notes |
|-------|-------------|-------|
| `SimpleTokenizer` | ✅ Yes | Stateless after construction |
| `PennTreebankTokenizer` | ✅ Yes | Stateless singleton |
| `BreakIteratorTokenizer` | ❌ No | `BreakIterator` is not thread-safe; use `ThreadLocal` |
| `SimpleSentenceSplitter` | ✅ Yes | Stateless singleton |
| `BreakIteratorSentenceSplitter` | ❌ No | `BreakIterator` is not thread-safe; use `ThreadLocal` |
| `SimpleParagraphSplitter` | ✅ Yes | Stateless singleton |

---

## API Quick-Reference

```java
// ── Word tokenizers ──────────────────────────────────────────────────
Tokenizer simple    = new SimpleTokenizer();                    // thread-safe
Tokenizer ptb       = PennTreebankTokenizer.getInstance();      // singleton, thread-safe
Tokenizer biTok     = new BreakIteratorTokenizer();             // per-thread
Tokenizer biTokFr   = new BreakIteratorTokenizer(Locale.FRENCH);// locale-aware

String[] tokens = simple.split("He won't go.");
// [He, will, not, go, .]

// ── Sentence splitters ───────────────────────────────────────────────
SentenceSplitter ss  = SimpleSentenceSplitter.getInstance();    // singleton
SentenceSplitter bis = new BreakIteratorSentenceSplitter();     // per-thread
SentenceSplitter bde = new BreakIteratorSentenceSplitter(Locale.GERMAN);

String[] sentences = ss.split("Hello world. How are you?");
// [Hello world., How are you?]

// ── Paragraph splitter ───────────────────────────────────────────────
ParagraphSplitter ps = SimpleParagraphSplitter.getInstance();   // singleton
String[] paragraphs  = ps.split("Para one.\n\nPara two.");
// [Para one., Para two.]

// ── As Function in streams ───────────────────────────────────────────
String[][] allTokens = Arrays.stream(sentences)
        .map(simple)                    // Tokenizer IS a Function<String,String[]>
        .toArray(String[][]::new);
```

---

## Notes and Caveats

* **Input assumptions** — `SimpleSentenceSplitter` and both word tokenizers
  assume the input is a single paragraph (no embedded newlines from paragraph
  breaks). Pass paragraph-split text through `SimpleParagraphSplitter` first.
* **Sentence-final abbreviations** — `SimpleSentenceSplitter` consults
  `EnglishAbbreviations` to avoid splitting on abbreviation periods, but the
  dictionary is not exhaustive. Domain-specific abbreviations may require a
  custom splitter.
* **Penn Treebank conventions** — if you use `PennTreebankTokenizer`, make
  sure your downstream models (taggers, parsers) are trained on Penn Treebank
  tokenized data. Mixing conventions causes accuracy drops.
* **Locale** — `BreakIterator`-based classes are locale-aware but rely on the
  ICU data bundled with the JVM. Results may vary across JVM vendors.
* **Empty tokens** — all implementations filter out blank tokens, so
  `String[] tokens` will never contain an empty string.

---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

