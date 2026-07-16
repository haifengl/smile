# SMILE NLP — Stemmers

Stemming is a term-normalization technique that reduces inflected or derived
words to their root (stem) form. For example, *running*, *runner*, and *runs*
all reduce to *run*. This helps information retrieval and text mining systems
treat morphological variants of the same word as a single token.

SMILE provides two production-quality English stemmers in the
`smile.nlp.stemmer` package:

| Class | Algorithm | Aggressiveness |
|-------|-----------|----------------|
| `PorterStemmer` | Porter (1980) | Moderate |
| `LancasterStemmer` | Paice/Husk Lancaster (1990) | High |

Both implement the `Stemmer` interface, which also extends
`java.util.function.Function<String, String>`, making stemmers first-class
citizens in Java stream pipelines.

---

## `Stemmer` Interface

```java
package smile.nlp.stemmer;

public interface Stemmer extends java.util.function.Function<String, String> {
    /** Reduce a word to its stem. */
    String stem(String word);

    /** Delegates to {@code stem} so the stemmer can be used as a {@code Function}. */
    default String apply(String word) { return stem(word); }
}
```

Because `Stemmer` extends `Function<String, String>` you can pass any stemmer
directly to `Stream.map`, `String[]` transformations, or any other
higher-order function:

```java
Stemmer stemmer = new PorterStemmer();

List<String> stems = List.of("running", "jumps", "easily", "fairly")
        .stream()
        .map(stemmer)          // Stemmer IS a Function<String,String>
        .toList();

System.out.println(stems); // [run, jump, easili, fairli]
```

---

## Porter Stemmer — `PorterStemmer`

`PorterStemmer` implements Martin Porter's classic 1980 algorithm. It
applies five sequential rule steps, each stripping or transforming a specific
class of English suffixes. The algorithm is deterministic and widely used as
a baseline in information retrieval benchmarks.

### Characteristics

* **Aggressiveness:** moderate — results are recognizable English roots.
* **Idempotent:** stemming a stem returns the same stem.
* **Case:** input should be lower-case; the class does not lower-case
  automatically.
* **Thread safety:** **NOT** thread-safe. Each thread must use its own
  instance.

### Basic usage

```java
import smile.nlp.stemmer.PorterStemmer;

PorterStemmer porter = new PorterStemmer();

System.out.println(porter.stem("caresses"));  // caress
System.out.println(porter.stem("ponies"));    // poni
System.out.println(porter.stem("troubles"));  // troubl
System.out.println(porter.stem("hopping"));   // hop
System.out.println(porter.stem("tanned"));    // tan
System.out.println(porter.stem("falling"));   // fall
System.out.println(porter.stem("generalization")); // gener
System.out.println(porter.stem("revival"));   // reviv
```

### Using Porter stemmer in a text-processing pipeline

Because `PorterStemmer` is **not** thread-safe, create one instance per
thread. `ThreadLocal` is the idiomatic way to do this in multi-threaded code:

```java
import smile.nlp.stemmer.PorterStemmer;
import smile.nlp.tokenizer.SimpleTokenizer;

ThreadLocal<PorterStemmer> tlStemmer =
        ThreadLocal.withInitial(PorterStemmer::new);

SimpleTokenizer tokenizer = new SimpleTokenizer();

String[] tokens = tokenizer.split("The cats are sitting on the mats");
String[] stems  = new String[tokens.length];

PorterStemmer stemmer = tlStemmer.get();
for (int i = 0; i < tokens.length; i++) {
    stems[i] = stemmer.stem(tokens[i].toLowerCase());
}

System.out.println(java.util.Arrays.toString(stems));
// [the, cat, are, sit, on, the, mat]
```

### Porter algorithm overview

The algorithm proceeds through five passes:

| Step | Examples of rules applied |
|------|--------------------------|
| 1a | `sses → ss`, `ies → i`, `ss → ss`, `s →` |
| 1b | `eed → ee`, `ed →`, `ing →` (with post-processing) |
| 1c | `y → i` (if stem contains a vowel) |
| 2  | `ational → ate`, `tional → tion`, `enci → ence`, etc. |
| 3  | `icate → ic`, `ative →`, `alize → al`, etc. |
| 4  | `al →`, `ance →`, `ence →`, `er →`, `ic →`, etc. |
| 5a | `e →` (conditional on consonant measure) |
| 5b | `ll → l` (conditional on consonant measure) |

---

## Lancaster Stemmer — `LancasterStemmer`

`LancasterStemmer` implements the Paice/Husk Lancaster algorithm (1990). It
is an **iterative** conflation stemmer that applies a single flat table of
rules repeatedly until no more rules fire. It is significantly more aggressive
than Porter and may produce stems that are not recognizable English words.

### Characteristics

* **Aggressiveness:** high — produces shorter stems, tighter conflation.
* **Iterative:** rules are applied in a loop until the word stabilises.
* **Prefix stripping (optional):** can strip scientific prefixes such as
  `kilo`, `micro`, `milli`, `intra`, `ultra`, `mega`, `nano`, `pico`,
  `pseudo`.
* **Case:** input is **automatically** lower-cased; non-letter characters are
  removed.
* **Custom rules:** you can supply your own rule table via `InputStream`.
* **Thread safety:** safe as long as you do not share the same instance
  across threads while modifying it.

### Constructors

| Constructor | Description |
|-------------|-------------|
| `LancasterStemmer()` | Default rules, prefix stripping **disabled** |
| `LancasterStemmer(boolean stripPrefix)` | Default rules, control prefix stripping |
| `LancasterStemmer(InputStream rules)` | Custom rule file, no prefix stripping |
| `LancasterStemmer(InputStream rules, boolean stripPrefix)` | Custom rules + prefix control |

### Basic usage

```java
import smile.nlp.stemmer.LancasterStemmer;

LancasterStemmer lancaster = new LancasterStemmer();

System.out.println(lancaster.stem("maximum"));   // maxim
System.out.println(lancaster.stem("presumably")); // presum
System.out.println(lancaster.stem("multiply"));  // multiply
System.out.println(lancaster.stem("provision")); // provid
System.out.println(lancaster.stem("owed"));      // ow
System.out.println(lancaster.stem("ear"));       // ear
System.out.println(lancaster.stem("saying"));    // say
System.out.println(lancaster.stem("crying"));    // cry
```

### Enabling prefix stripping

For scientific or technical text containing metric prefixes, enable prefix
stripping:

```java
LancasterStemmer lancaster = new LancasterStemmer(true);

System.out.println(lancaster.stem("microprocessor")); // processor → process
System.out.println(lancaster.stem("kilowatt"));       // watt
System.out.println(lancaster.stem("pseudoscience"));  // scienc
```

Prefixes handled: `kilo`, `micro`, `milli`, `intra`, `ultra`, `mega`,
`nano`, `pico`, `pseudo`.

### Custom rule file

Provide your own `Lancaster_rules.txt`-format rules via an `InputStream`:

```java
import java.io.FileInputStream;
import smile.nlp.stemmer.LancasterStemmer;

try (var is = new FileInputStream("my_rules.txt")) {
    LancasterStemmer custom = new LancasterStemmer(is);
    System.out.println(custom.stem("running"));
}
```

Each rule in the file is a single token on its own line following the format:
`<suffix><flag><remove><append>` (Lancaster rule encoding). Lines may contain
trailing comments separated by whitespace; everything after the first
whitespace is ignored.

---

## Choosing Between Porter and Lancaster

| Criterion | Porter | Lancaster |
|-----------|--------|-----------|
| Stem readability | Good — stems are often real words | Poor — stems can be fragments |
| Conflation strength | Moderate | High |
| Speed | Fast (5 linear steps) | Slightly slower (iterative) |
| Typical use case | IR, search engines, topic models | Aggressive deduplication, small vocabularies |
| Thread safety | **Per-thread instance required** | Safe with one instance per thread |

**Rule of thumb:** start with `PorterStemmer`. Switch to `LancasterStemmer`
if you need tighter vocabulary conflation (e.g., for clustering or when
vocabulary size is the bottleneck).

---

## Integration with Other SMILE NLP Components

### With tokenizer and POS tagger (stem only content words)

```java
import smile.nlp.stemmer.*;
import smile.nlp.tokenizer.*;
import smile.nlp.pos.*;

HMMPOSTagger tagger    = HMMPOSTagger.getDefault();
SimpleTokenizer tokenizer = new SimpleTokenizer();
Stemmer stemmer        = new PorterStemmer();

String text = "The dogs are barking loudly near the fences.";
String[] tokens = tokenizer.split(text);
PennTreebankPOS[] tags = tagger.tag(tokens);

for (int i = 0; i < tokens.length; i++) {
    if (tags[i].open) {  // content words only
        System.out.printf("%-15s → %s%n", tokens[i], stemmer.stem(tokens[i].toLowerCase()));
    }
}
// dogs           → dog
// barking        → bark
// loudly         → loudli
// fences         → fence
```

### Building a bag-of-stems index

```java
import smile.nlp.stemmer.PorterStemmer;
import smile.nlp.tokenizer.SimpleTokenizer;
import java.util.*;

SimpleTokenizer tokenizer = new SimpleTokenizer();
ThreadLocal<PorterStemmer> tlStemmer = ThreadLocal.withInitial(PorterStemmer::new);

List<String> documents = List.of(
    "The quick brown fox jumps over the lazy dog",
    "Jumping foxes and running dogs are common"
);

Map<String, List<Integer>> index = new HashMap<>();

for (int docId = 0; docId < documents.size(); docId++) {
    String[] tokens = tokenizer.split(documents.get(docId));
    PorterStemmer stemmer = tlStemmer.get();
    for (String token : tokens) {
        String stem = stemmer.stem(token.toLowerCase());
        index.computeIfAbsent(stem, k -> new ArrayList<>()).add(docId);
    }
}

System.out.println(index.get("jump")); // [0, 1]
System.out.println(index.get("dog"));  // [0, 1]
System.out.println(index.get("fox"));  // [0, 1]
```

---

## API Quick-Reference

```java
// ── Stemmer (interface) ─────────────────────────────────────────────
Stemmer s = new PorterStemmer();
String stem = s.stem("running");          // "run"
String same = s.apply("running");         // "run"  (Function<String,String>)
List<String> stems = words.stream().map(s).toList();  // stream pipeline

// ── PorterStemmer ────────────────────────────────────────────────────
PorterStemmer porter = new PorterStemmer();   // one per thread!
String root = porter.stem("generalization");  // "gener"

// ── LancasterStemmer ─────────────────────────────────────────────────
LancasterStemmer lancaster = new LancasterStemmer();             // default rules
LancasterStemmer withPrefix = new LancasterStemmer(true);        // strip prefixes
LancasterStemmer custom = new LancasterStemmer(inputStream);     // custom rules
String r = lancaster.stem("presumably");                         // "presum"
```

---

## Notes and Caveats

* **Input language** — both stemmers are designed for English only.
* **Input case** — `PorterStemmer` does **not** lower-case input; callers
  must do so. `LancasterStemmer` lower-cases and strips non-letter characters
  automatically.
* **Minimum word length** — `LancasterStemmer` skips words of three
  characters or fewer.
* **Stemming ≠ lemmatization** — stems may not be dictionary words. Use a
  POS-aware lemmatiser if you need real dictionary base forms.
* **Thread safety** — `PorterStemmer` uses an internal working buffer (`b`,
  `j`, `k` fields) and is **not** thread-safe. Create one instance per thread
  or protect shared instances with synchronization. `LancasterStemmer` has no
  mutable state after construction and can be shared across threads safely.

---

## References

1. M. F. Porter, *An algorithm for suffix stripping*, Program, **14**(3),
   130–137, 1980. <https://www.tartarus.org/~martin/PorterStemmer>
2. C. D. Paice, *Another stemmer*, SIGIR Forum, **24**(3), 56–61, 1990.

---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

