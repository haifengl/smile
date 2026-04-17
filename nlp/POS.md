# SMILE NLP — Part-of-Speech Tagging

Part-of-speech (POS) tagging is the process of assigning a grammatical
label—noun, verb, adjective, etc.—to every token in a sentence. SMILE
provides a complete POS-tagging pipeline through the `smile.nlp.pos` package:

| Class / Interface | Role |
|-------------------|------|
| `POSTagger` | Interface implemented by every tagger |
| `PennTreebankPOS` | Enum of all 45 Penn Treebank tags |
| `HMMPOSTagger` | Production-quality HMM-based tagger |
| `RegexPOSTagger` | Fast rule-based pre-classifier for numbers, URLs, e-mails |
| `EnglishPOSLexicon` | Static English lexicon (~200 k entries) |

---

## Tag Set — `PennTreebankPOS`

All taggers return arrays of `PennTreebankPOS` constants, which cover the
complete [Penn Treebank II](https://www.cis.upenn.edu/~treebank/) tag set.

### Open-class (content) tags

| Constant | Description | `open` |
|----------|-------------|--------|
| `NN` | Noun, singular or mass | ✓ |
| `NNS` | Noun, plural | ✓ |
| `NNP` | Proper noun, singular | ✓ |
| `NNPS` | Proper noun, plural | ✓ |
| `VB` | Verb, base form | ✓ |
| `VBD` | Verb, past tense | ✓ |
| `VBG` | Verb, gerund or present participle | ✓ |
| `VBN` | Verb, past participle | ✓ |
| `VBP` | Verb, non-3rd person singular present | ✓ |
| `VBZ` | Verb, 3rd person singular present | ✓ |
| `JJ` | Adjective | ✓ |
| `JJR` | Adjective, comparative | ✓ |
| `JJS` | Adjective, superlative | ✓ |
| `RB` | Adverb | ✓ |
| `RBR` | Adverb, comparative | ✓ |
| `RBS` | Adverb, superlative | ✓ |
| `CD` | Cardinal number | ✓ |
| `FW` | Foreign word | ✓ |
| `SYM` | Symbol | ✓ |
| `UH` | Interjection | ✓ |

### Closed-class (function) tags

| Constant | Description |
|----------|-------------|
| `CC` | Coordinating conjunction |
| `DT` | Determiner |
| `EX` | Existential *there* |
| `IN` | Preposition or subordinating conjunction |
| `MD` | Modal verb |
| `PDT` | Predeterminer |
| `POS` | Possessive ending |
| `PRP` | Personal pronoun |
| `PRP$` | Possessive pronoun |
| `RP` | Particle |
| `TO` | *to* |
| `WDT` | Wh-determiner |
| `WP` | Wh-pronoun |
| `WP$` | Possessive wh-pronoun |
| `WRB` | Wh-adverb |

### Punctuation constants

These constants have overridden `toString()` methods that return the
actual symbol, making them safe to print directly:

| Constant | `toString()` | Matches |
|----------|-------------|---------|
| `SENT` | `.` | `.` `?` `!` |
| `COMMA` | `,` | `,` |
| `COLON` | `:` | `:` `;` `...` |
| `DASH` | `-` | `-` |
| `POUND` | `#` | `#` |
| `OPENING_PARENTHESIS` | `(` | `(` `[` `{` |
| `CLOSING_PARENTHESIS` | `)` | `)` `]` `}` |
| `OPENING_QUOTATION` | ` `` ` | `` ` `` ` `` `` `` |
| `CLOSING_QUOTATION` | `''` | `'` `''` |
| `$` | `$` | `$` |

### `open` field

Every `PennTreebankPOS` constant exposes a boolean `open` field that is
`true` for open-class (content) words and `false` for closed-class
(function) words. This is useful for filtering during feature extraction:

```java
PennTreebankPOS[] tags = tagger.tag(tokens);
for (int i = 0; i < tokens.length; i++) {
    if (tags[i].open) {
        // content word — include in bag-of-words, keyword extraction, etc.
    }
}
```

### `getValue(String)` — parsing tag strings from corpora

`PennTreebankPOS.getValue(String)` is a robust alternative to
`PennTreebankPOS.valueOf(String)`. It automatically maps raw punctuation
characters to their named enum constants before calling `valueOf`:

```java
PennTreebankPOS tag = PennTreebankPOS.getValue("NN");   // → NN
PennTreebankPOS dot = PennTreebankPOS.getValue(".");    // → SENT
PennTreebankPOS com = PennTreebankPOS.getValue(",");    // → COMMA
PennTreebankPOS lp  = PennTreebankPOS.getValue("(");   // → OPENING_PARENTHESIS
```

Throws `IllegalArgumentException` for unknown strings.

---

## HMM POS Tagger — `HMMPOSTagger`

`HMMPOSTagger` is the primary tagger. It is a first-order Hidden Markov
Model trained on the Penn Treebank WSJ and Brown corpora. For words not
seen during training it falls back to `RegexPOSTagger` (numbers, URLs,
e-mails), and then to the most probable tag according to the Viterbi path.

### Using the bundled default model

A pre-trained model is bundled as a resource inside the `nlp` JAR. Load
it once (the singleton is cached and the call is thread-safe):

```java
HMMPOSTagger tagger = HMMPOSTagger.getDefault();

String[] sentence = {"The", "cat", "sat", "on", "the", "mat", "."};
PennTreebankPOS[] tags = tagger.tag(sentence);

for (int i = 0; i < sentence.length; i++) {
    System.out.printf("%-12s %s%n", sentence[i], tags[i]);
}
```

Expected output:

```
The          DT
cat          NN
sat          VBD
on           IN
the          DT
mat          NN
.            SENT
```

### Complete tagging pipeline

In practice you will tokenize text before tagging. Use
`SimpleSentenceSplitter` and `SimpleTokenizer` from `smile.nlp.tokenizer`:

```java
import smile.nlp.pos.*;
import smile.nlp.tokenizer.*;

HMMPOSTagger tagger = HMMPOSTagger.getDefault();
SimpleSentenceSplitter sentSplitter = SimpleSentenceSplitter.getInstance();
SimpleTokenizer tokenizer = new SimpleTokenizer();

String text = "Alan Turing proposed the Turing test in 1950. "
            + "It remains a benchmark for artificial intelligence.";

for (String sentence : sentSplitter.split(text)) {
    String[] tokens = tokenizer.split(sentence);
    PennTreebankPOS[] tags = tagger.tag(tokens);

    for (int i = 0; i < tokens.length; i++) {
        System.out.printf("%-20s %s%n", tokens[i], tags[i]);
    }
    System.out.println();
}
```

### Training a custom model

If you have annotated data in Penn Treebank format (one `word/TAG` pair
per token, sentences separated by blank lines) you can train your own
model with `HMMPOSTagger.fit`:

```java
// Load annotated sentences
List<String[]> sentences = new ArrayList<>();
List<PennTreebankPOS[]> labels   = new ArrayList<>();
HMMPOSTagger.read("/path/to/corpus", sentences, labels);

String[][] x          = sentences.toArray(new String[0][]);
PennTreebankPOS[][] y = labels.toArray(new PennTreebankPOS[0][]);

HMMPOSTagger custom = HMMPOSTagger.fit(x, y);

// Persist the model for later use
try (ObjectOutputStream oos = new ObjectOutputStream(
        new FileOutputStream("my-pos-tagger.model"))) {
    oos.writeObject(custom);
}
```

The training corpus directory is walked recursively; every file whose
name ends in `.POS` is read. Each line must follow the format:
`word/TAG word/TAG ...` (Penn Treebank II convention).

#### Cross-validated accuracy

On the bundled Penn Treebank corpora, 10-fold cross-validation yields:

| Corpus | Error tokens | Total tokens | Error rate |
|--------|-------------|--------------|------------|
| WSJ    | ≈ 51 325    | ≈ 1 017 k    | ≈ 5.0 %    |
| Brown  | ≈ 55 589    | ≈ 1 175 k    | ≈ 4.7 %    |

---

## Regex POS Tagger — `RegexPOSTagger`

`RegexPOSTagger` is a lightweight pre-classifier used internally by
`HMMPOSTagger` for tokens not found in the training vocabulary. It covers:

| Pattern | Tag |
|---------|-----|
| Integer or decimal number (`123`, `3.14`) | `CD` |
| Comma-formatted number (`1,234`, `1,234.56`) | `CD` |
| Phone number (`914-544-3333`, `544-3333`) | `NN` |
| Phone extension (`x123`) | `NN` |
| URL (`http://…`, `ftp://…`) | `NN` |
| E-mail address (`user@domain.tld`) | `NN` |

It can be used directly when you only need surface-form rules:

```java
import smile.nlp.pos.*;
import java.util.Optional;

Optional<PennTreebankPOS> tag = RegexPOSTagger.tag("1,234.56");
tag.ifPresent(t -> System.out.println(t)); // CD

Optional<PennTreebankPOS> url = RegexPOSTagger.tag("https://example.com");
url.ifPresent(t -> System.out.println(t)); // NN

Optional<PennTreebankPOS> none = RegexPOSTagger.tag("computer");
System.out.println(none.isEmpty()); // true — no regex match
```

`RegexPOSTagger.tag()` returns an empty `Optional` when no pattern
matches, so callers never need to null-check.

---

## English POS Lexicon — `EnglishPOSLexicon`

`EnglishPOSLexicon` provides a static dictionary of approximately 200 000
English words with their possible POS tags. It is a combination of the
[Moby Part-of-Speech II](http://aspell.sourceforge.net/wl/) database and
WordNet. Many words are ambiguous and are listed with multiple tags, in
priority order (most common usage first).

```java
import smile.nlp.pos.*;
import java.util.Optional;

// Single-sense lookup
Optional<PennTreebankPOS[]> tags = EnglishPOSLexicon.get("run");
tags.ifPresent(ts -> {
    System.out.println("Primary POS: " + ts[0]);   // VB
    System.out.println("Total senses: " + ts.length);
});

// Unknown word
Optional<PennTreebankPOS[]> unknown = EnglishPOSLexicon.get("xyzzy");
System.out.println(unknown.isEmpty()); // true
```

The lexicon is loaded once from a classpath resource at class
initialization time and is thread-safe for concurrent reads.

### Moby character → Penn Treebank mapping

| Moby char | Meaning | Penn tag |
|-----------|---------|----------|
| `N`, `h`, `o` | Noun / noun phrase / nominative | `NN` |
| `p` | Plural noun | `NNS` |
| `V`, `t`, `i` | Verb (any form) | `VB` |
| `A` | Adjective | `JJ` |
| `v` | Adverb | `RB` |
| `C` | Conjunction | `CC` |
| `P` | Preposition | `IN` |
| `!` | Interjection | `UH` |
| `r` | Pronoun | `PRP` |
| `D`, `I` | Definite / indefinite article | `DT` |

---

## API quick-reference

```java
// ── PennTreebankPOS ─────────────────────────────────────────────────
PennTreebankPOS tag = PennTreebankPOS.getValue("VBZ"); // parse from string
boolean isContent   = tag.open;                        // true for open class
String symbol       = PennTreebankPOS.SENT.toString(); // "."

// ── HMMPOSTagger ────────────────────────────────────────────────────
HMMPOSTagger tagger          = HMMPOSTagger.getDefault();          // singleton
PennTreebankPOS[] tags        = tagger.tag(new String[]{"Hello","world"});
HMMPOSTagger custom           = HMMPOSTagger.fit(trainX, trainY);  // train

// ── RegexPOSTagger ──────────────────────────────────────────────────
Optional<PennTreebankPOS> num  = RegexPOSTagger.tag("3.14");       // CD
Optional<PennTreebankPOS> none = RegexPOSTagger.tag("hello");      // empty

// ── EnglishPOSLexicon ───────────────────────────────────────────────
Optional<PennTreebankPOS[]> ts = EnglishPOSLexicon.get("run");     // [VB, NN, …]
Optional<PennTreebankPOS[]> no = EnglishPOSLexicon.get("xyzzy");   // empty
```

---

## Notes and caveats

* **Thread safety** — `HMMPOSTagger.getDefault()` uses double-checked
  locking; once loaded the singleton is safe to share across threads.
  `EnglishPOSLexicon` is read-only after static initialization.
* **Token boundaries** — all taggers expect input that has already been
  tokenized. Use `SimpleTokenizer` or a custom `Tokenizer` first.
* **Case sensitivity** — `HMMPOSTagger` is case-sensitive in its emission
  model. Pass tokens in their original capitalization.
* **Serialization** — `HMMPOSTagger` implements `Serializable`
  (`serialVersionUID = 2L`). The bundled model can be loaded with
  standard Java `ObjectInputStream`.


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

