# SMILE NLP Module

The `smile-nlp` module provides a comprehensive Natural Language Processing
(NLP) toolkit for the Java platform. It covers the full text-processing
pipeline — from raw string normalization, paragraph / sentence / word
splitting, through morphological analysis (stemming, POS tagging), to
higher-level tasks such as collocation extraction, relevance ranking, word
embeddings, and ontology management.

---

## Module Contents

| Sub-package / Class | Description                                                     | Guide                              |
|---------------------|-----------------------------------------------------------------|------------------------------------|
| `smile.nlp` (core) | `Text`, `Corpus`, `SimpleCorpus`, `Bigram`, `NGram`, `Word2Vec` | [COLLOCATION.md](COLLOCATION.md)   |
| `smile.nlp.normalizer` | Unicode text normalization                                     | [§ Normalizer](#normalizer)        |
| `smile.nlp.tokenizer` | Paragraph, sentence, and word splitting                         | [TOKENIZER.md](TOKENIZER.md)       |
| `smile.nlp.stemmer` | Porter & Lancaster word stemmers                                | [STEM.md](STEM.md)                 |
| `smile.nlp.pos` | Penn Treebank POS tagging (HMM)                                 | [POS.md](POS.md)                   |
| `smile.nlp.dictionary` | Stop-word lists, punctuation sets, English dictionary           | [§ Dictionaries](#dictionaries)    |
| `smile.nlp.relevance` | TF-IDF and BM25 relevance ranking                               | [RELEVANCE.md](RELEVANCE.md)       |
| `smile.nlp.taxonomy` | Concept taxonomy (tree) and taxonomic distance                  | [TAXONOMY.md](TAXONOMY.md) |

---

## Dependency

Add `smile-nlp` to your Gradle build:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.haifengl:smile-nlp:4.x.x")
}
```

---

## Quick Start

The following snippet demonstrates a complete preprocessing pipeline:

```java
import smile.nlp.*;
import smile.nlp.normalizer.SimpleNormalizer;
import smile.nlp.tokenizer.*;
import smile.nlp.stemmer.PorterStemmer;
import smile.nlp.pos.*;

// 1. Normalize raw text
SimpleNormalizer normalizer = SimpleNormalizer.getInstance();
String text = normalizer.normalize(rawText);

// 2. Split into paragraphs → sentences → tokens
ParagraphSplitter paragraphs = SimpleParagraphSplitter.getInstance();
SentenceSplitter  sentences  = SimpleSentenceSplitter.getInstance();
Tokenizer         tokenizer  = new SimpleTokenizer();

// 3. POS-tag every sentence (one Porter stemmer per thread)
HMMPOSTagger tagger = HMMPOSTagger.getDefault();
ThreadLocal<PorterStemmer> tlStemmer = ThreadLocal.withInitial(PorterStemmer::new);

for (String para : paragraphs.split(text)) {
    for (String sent : sentences.split(para)) {
        String[] tokens = tokenizer.split(sent);
        PennTreebankPOS[] tags = tagger.tag(tokens);
        PorterStemmer stemmer = tlStemmer.get();

        for (int i = 0; i < tokens.length; i++) {
            if (tags[i].open) {                   // content words only
                String stem = stemmer.stem(tokens[i].toLowerCase());
                System.out.printf("%-20s %-6s %s%n", tokens[i], tags[i], stem);
            }
        }
    }
}
```

---

## Core Data Types (`smile.nlp`)

### `Text`

`Text` is a lightweight, immutable record that holds a document's content
and optional title:

```java
Text doc = Text.of("Machine learning is a subfield of AI.");
Text titled = Text.of("Overview", "Machine learning is a subfield of AI.");
System.out.println(doc.content());   // "Machine learning is a subfield of AI."
System.out.println(titled.title());  // "Overview"
```

### `Bigram` and `NGram`

These are Java **records** for representing word pairs and n-word sequences,
with optional raw-frequency and statistical-score fields. They are used
primarily for collocation extraction — see [COLLOCATION.md](COLLOCATION.md).

```java
Bigram b = new Bigram("machine", "learning");           // lookup key
NGram  n = new NGram(new String[]{"deep","learning"});  // lookup key
```

### `Corpus` and `SimpleCorpus`

`Corpus` is an interface for an indexed document collection. `SimpleCorpus`
is the built-in in-memory implementation that tokenises documents on insertion
and maintains term/bigram frequency tables for downstream tasks:

```java
SimpleCorpus corpus = new SimpleCorpus();
corpus.add(corpus.doc("SMILE is a fast machine learning library."));
corpus.add(corpus.doc("Deep learning uses neural networks."));

System.out.println(corpus.docCount());   // 2
System.out.println(corpus.size());       // total tokens
System.out.println(corpus.count("learning")); // term frequency
```

`SimpleCorpus` also provides bigram collocation extraction (`corpus.bigrams(…)`)
and full-text relevance search. See [COLLOCATION.md](COLLOCATION.md) and
[RELEVANCE.md](RELEVANCE.md).

### `Word2Vec`

`Word2Vec` loads pre-trained word embeddings (Word2vec binary format or GloVe
text format) and provides cosine-similarity lookup:

```java
import smile.nlp.Word2Vec;
import java.nio.file.Path;

// Load Word2vec binary model
Word2Vec w2v = Word2Vec.of(Path.of("GoogleNews-vectors-negative300.bin"));

// Load GloVe text model
Word2Vec glove = Word2Vec.glove(Path.of("glove.6B.100d.txt"));

// Look up a vector
float[] vec = w2v.apply("king");    // throws if not found
w2v.lookup("king").ifPresent(v -> System.out.println(v.length)); // 300

// Cosine similarity
w2v.similarity("king", "queen").ifPresent(System.out::println);  // ≈ 0.65

System.out.println(w2v.size());       // vocabulary size
System.out.println(w2v.dimension());  // embedding dimensionality
```

---

## Normalizer

`smile.nlp.normalizer.SimpleNormalizer` is a stateless singleton that
applies the following Unicode-aware normalization steps:

| Step                | Detail                                                    |
|---------------------|-----------------------------------------------------------|
| NFKC normalization | Canonical decomposition followed by canonical composition |
| Whitespace          | Strip, trim, and compress all whitespace                  |
| Control chars       | Remove `Cc` and `Cf` Unicode categories                   |
| Dashes              | Normalize em-dash, en-dash, etc. → `-`                    |
| Double quotes       | Normalize typographic `"…"` → `"`                         |
| Single quotes       | Normalize typographic `'…'` → `'`                        |

```java
import smile.nlp.normalizer.SimpleNormalizer;

SimpleNormalizer norm = SimpleNormalizer.getInstance();   // thread-safe singleton
String clean = norm.normalize("\u201CHello,\u201D   world\u2019s\u2026");
// → "\"Hello,\"   world's..."
```

Always apply `SimpleNormalizer` before tokenising text from heterogeneous
sources (web pages, PDFs, user input) to avoid spurious tokens caused by
Unicode encoding variants.

---

## Tokenizers and Splitters

The `smile.nlp.tokenizer` package provides three levels of text splitting.
See **[TOKENIZER.md](TOKENIZER.md)** for full documentation.

| Class | Level | Notes |
|-------|-------|-------|
| `SimpleParagraphSplitter` | Paragraph | Splits on blank lines; singleton |
| `SimpleSentenceSplitter` | Sentence | English heuristic + abbreviation dict; singleton |
| `BreakIteratorSentenceSplitter` | Sentence | Locale-aware; not thread-safe |
| `SimpleTokenizer` | Word | Expands contractions; thread-safe |
| `PennTreebankTokenizer` | Word | Penn Treebank conventions; singleton |
| `BreakIteratorTokenizer` | Word | Locale-aware; not thread-safe |

---

## Stemmers

The `smile.nlp.stemmer` package provides two English word stemmers.
See **[STEM.md](STEM.md)** for full documentation.

| Class | Algorithm | Aggressiveness | Thread-safe? |
|-------|-----------|----------------|-------------|
| `PorterStemmer` | Porter 1980 | Moderate | ❌ (per-thread) |
| `LancasterStemmer` | Paice/Husk 1990 | High | ✅ |

```java
import smile.nlp.stemmer.*;

PorterStemmer   porter   = new PorterStemmer();          // one per thread
LancasterStemmer lancas  = new LancasterStemmer();        // shareable
LancasterStemmer withPfx = new LancasterStemmer(true);    // strip prefixes

System.out.println(porter.stem("generalization")); // "gener"
System.out.println(lancas.stem("presumably"));     // "presum"
```

Both implement `Stemmer extends Function<String,String>`, so they work
directly in Java stream pipelines.

---

## Part-of-Speech Tagging

The `smile.nlp.pos` package provides a complete POS-tagging pipeline.
See **[POS.md](POS.md)** for full documentation.

```java
import smile.nlp.pos.*;

HMMPOSTagger tagger = HMMPOSTagger.getDefault();   // pre-trained HMM model

String[] tokens = {"The", "cat", "sat", "on", "the", "mat", "."};
PennTreebankPOS[] tags = tagger.tag(tokens);

for (int i = 0; i < tokens.length; i++) {
    System.out.printf("%-12s %s%n", tokens[i], tags[i]);
}
// The          DT
// cat          NN
// sat          VBD
// ...
```

Key classes:

| Class | Role                                                                                  |
|-------|---------------------------------------------------------------------------------------|
| `PennTreebankPOS` | Enum of 45 Penn Treebank tags; `open` field distinguishes content from function words |
| `HMMPOSTagger` | Pre-trained first-order HMM tagger; serializable                                     |
| `RegexPOSTagger` | Rule-based pre-classifier for numbers, URLs, e-mails                                  |
| `EnglishPOSLexicon` | ~200 k word lexicon (Moby + WordNet)                                                  |

---

## Dictionaries

The `smile.nlp.dictionary` package provides ready-to-use word lists.

### Stop-word Lists — `EnglishStopWords`

Four levels of English stop-word coverage:

| Constant | Coverage |
|----------|----------|
| `EnglishStopWords.DEFAULT` | Standard list (~500 words) |
| `EnglishStopWords.COMPREHENSIVE` | Extended list (thousands of words) |
| `EnglishStopWords.GOOGLE` | Google's stop-word list |
| `EnglishStopWords.MYSQL` | MySQL FullText stop-word list |

```java
import smile.nlp.dictionary.EnglishStopWords;

EnglishStopWords stopWords = EnglishStopWords.DEFAULT;
System.out.println(stopWords.contains("the"));    // true
System.out.println(stopWords.contains("machine")); // false
```

### Punctuation — `EnglishPunctuations`

```java
import smile.nlp.dictionary.EnglishPunctuations;

EnglishPunctuations punct = EnglishPunctuations.getInstance();
System.out.println(punct.contains("."));   // true
System.out.println(punct.contains("NLP")); // false
```

### English Dictionary — `EnglishDictionary`

A broad-coverage English word dictionary backed by a Bloom filter for
memory-efficient membership testing:

```java
import smile.nlp.dictionary.EnglishDictionary;

System.out.println(EnglishDictionary.contains("serendipity")); // true
System.out.println(EnglishDictionary.contains("xyzzy"));       // false
```

---

## Collocation Extraction

Collocations are statistically significant word co-occurrences. The
`smile.nlp` package provides two complementary algorithms.
See **[COLLOCATION.md](COLLOCATION.md)** for full documentation.

### Bigram collocations (likelihood ratio)

```java
SimpleCorpus corpus = new SimpleCorpus();
// ... load documents via corpus.add(corpus.doc(text)) ...

// Top-20 bigrams by likelihood-ratio score (min raw count > 3)
List<Bigram> top20 = corpus.bigrams(20, 3);

// All bigrams significant at p < 0.001
List<Bigram> sig = corpus.bigrams(0.001, 3);
```

### N-gram collocations (Apriori)

```java
import smile.nlp.NGram;

// sentences = List<String[]>  (already tokenised)
NGram[][] result = NGram.apriori(sentences, 4, 4);  // up to 4-grams, min freq 4

NGram[] bigrams  = result[2];   // sorted desc by count
NGram[] trigrams = result[3];
```

---

## Relevance Ranking

The `smile.nlp.relevance` package provides plug-in relevance rankers for
corpus search. See **[RELEVANCE.md](RELEVANCE.md)** for full documentation.

```java
import smile.nlp.relevance.*;

// TF-IDF search
RelevanceRanker tfidf = new TFIDF();
corpus.search(tfidf, "machine learning").forEachRemaining(r ->
    System.out.printf("%.4f  %s%n", r.relevance(), r.text().title()));

// BM25 search (Okapi weighting, state-of-the-art)
RelevanceRanker bm25 = new BM25();
corpus.search(bm25, new String[]{"deep", "learning"}).forEachRemaining(r ->
    System.out.printf("%.4f  %s%n", r.relevance(), r.text().title()));
```

| Ranker | Class | Notes |
|--------|-------|-------|
| TF-IDF | `TFIDF` | Classic weighted bag-of-words |
| BM25 / Okapi | `BM25` | Probabilistic; state-of-the-art for ad-hoc retrieval |

---

## Taxonomy

The `smile.nlp.taxonomy` package provides a lightweight **concept taxonomy**
(an ordered tree of named concepts) and a **taxonomic distance** measure
between two concepts in the tree. See **[TAXONOMY.md](TAXONOMY.md)** for full documentation.

```java
import smile.nlp.taxonomy.*;

// Build a simple IS-A hierarchy
Taxonomy taxonomy = new Taxonomy("entity");
Concept entity  = taxonomy.getConcept("entity");
Concept animal  = entity.addChild("animal");
Concept plant   = entity.addChild("plant");
Concept dog     = animal.addChild("dog");
Concept cat     = animal.addChild("cat");
Concept rose    = plant.addChild("rose");

// Retrieve a concept
Concept c = taxonomy.getConcept("dog");
System.out.println(c.getKeywords());   // [dog]

// Taxonomic distance between two concepts
TaxonomicDistance dist = new TaxonomicDistance(taxonomy);
System.out.println(dist.d("dog", "cat"));   // sibling distance
System.out.println(dist.d("dog", "rose"));  // cross-branch distance
```

`TaxonomicDistance` implements the Wu-Palmer measure, which quantifies the
semantic similarity of two concepts based on their depth in the hierarchy and
the depth of their least-common subsumer.

---

## Package Map

```
smile.nlp
├── Text, Corpus, SimpleCorpus          – document model & in-memory corpus
├── Bigram, NGram                        – collocation data records
├── Word2Vec                             – word embeddings (Word2vec / GloVe)
├── AnchorText                           – web anchor-text record
│
├── normalizer/
│   ├── Normalizer                       – interface
│   └── SimpleNormalizer                 – Unicode NFKC + whitespace cleanup
│
├── tokenizer/
│   ├── ParagraphSplitter / SimpleParagraphSplitter
│   ├── SentenceSplitter  / SimpleSentenceSplitter / BreakIteratorSentenceSplitter
│   └── Tokenizer         / SimpleTokenizer / PennTreebankTokenizer / BreakIteratorTokenizer
│
├── stemmer/
│   ├── Stemmer                          – interface (extends Function<String,String>)
│   ├── PorterStemmer                    – Porter 1980
│   └── LancasterStemmer                 – Paice/Husk 1990
│
├── pos/
│   ├── PennTreebankPOS                  – 45-tag enum
│   ├── POSTagger                        – interface
│   ├── HMMPOSTagger                     – pre-trained HMM tagger
│   ├── RegexPOSTagger                   – rule-based pre-classifier
│   └── EnglishPOSLexicon                – ~200 k word POS dictionary
│
├── dictionary/
│   ├── StopWords / EnglishStopWords     – stop-word lists
│   ├── Punctuations / EnglishPunctuations
│   ├── Dictionary / EnglishDictionary   – broad-coverage word list
│   └── Abbreviations                    – common abbreviation list
│
├── relevance/
│   ├── RelevanceRanker                  – interface
│   ├── Relevance                        – (document, score) record
│   ├── TFIDF                            – TF-IDF ranker
│   └── BM25                             – Okapi BM25 ranker
│
└── taxonomy/
    ├── Concept                          – tree node with keywords
    ├── Taxonomy                         – concept tree
    └── TaxonomicDistance                – Wu-Palmer semantic distance
```

---

## User Guides

| Document | Contents |
|----------|----------|
| [TOKENIZER.md](TOKENIZER.md) | Paragraph, sentence, and word splitting; choosing implementations; thread-safety |
| [STEM.md](STEM.md) | Porter and Lancaster stemmers; stream pipeline usage |
| [POS.md](POS.md) | POS tagging with HMM; Penn Treebank tag set; custom model training |
| [COLLOCATION.md](COLLOCATION.md) | Bigram collocations (likelihood ratio); n-gram collocations (Apriori); `SimpleCorpus` |
| [RELEVANCE.md](RELEVANCE.md) | TF-IDF and BM25 relevance ranking; corpus search |

---

## References

1. M. F. Porter, *An algorithm for suffix stripping*, Program, **14**(3), 130–137, 1980.
2. C. D. Paice, *Another stemmer*, SIGIR Forum, **24**(3), 56–61, 1990.
3. J. Furnkranz, *A Study Using n-gram Features for Text Categorization*, OEFAI TR-98-30, 1998.
4. S. Robertson & H. Zaragoza, *The Probabilistic Relevance Framework: BM25 and Beyond*, 2009.
5. Z. Wu & M. Palmer, *Verb Semantics and Lexical Selection*, ACL 1994.
6. T. Mikolov et al., *Distributed Representations of Words and Phrases*, NeurIPS 2013.

---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

