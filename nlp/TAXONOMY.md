# SMILE NLP — Taxonomy User Guide

## Overview

The `smile.nlp.taxonomy` package provides a lightweight, in-memory **rooted tree** data structure
for representing taxonomies (also called *concept hierarchies* or *IS-A hierarchies*).  The
package contains three public classes:

| Class | Role                                                           |
|---|----------------------------------------------------------------|
| `Taxonomy` | The tree itself — construction, navigation, and serialization |
| `Concept` | A single node, carrying a *synonym set* (synset) of keywords   |
| `TaxonomicDistance` | Edge-counting distance and three semantic-similarity measures  |

Typical use cases include:

* **NLP / information retrieval** — computing semantic distance between words via WordNet-style
  hierarchies.
* **Ontology management** — building and querying lightweight OWL/SKOS-like concept trees.
* **Knowledge-based systems** — organising domain knowledge for rule engines or classifiers.

---

## Core Concepts

### Concept nodes and synonym sets

Every node in the tree is a `Concept`.  A concept can be *named* (one or more keywords are
registered to it) or *anonymous* (no keywords, used as a structural grouping node).  Multiple
keywords that refer to the same concept form a **synset** (synonym set) — exactly like WordNet.

```
animal                   ← named concept, keyword "animal"
    mammal, warm-blooded ← named concept with two synonyms
        dog, canine      ← named concept with two synonyms
        cat, feline
    reptile
        snake
```

All keywords inside a taxonomy must be unique: attempting to register a keyword that already
exists throws `IllegalArgumentException`.

### Tree structure

A `Taxonomy` always has exactly one **root** node.  The root may be anonymous or named.
Every other `Concept` has exactly one parent and zero or more children.  The depth of the
root is **0**; the height of a leaf is **0**.

---

## Building a Taxonomy

### Programmatic construction

```java
// 1. Create the taxonomy with an optional root keyword
Taxonomy taxonomy = new Taxonomy("entity");
Concept root = taxonomy.getRoot();

// 2. Add children with addChild(String...)
Concept physical = root.addChild("physical");
Concept living   = physical.addChild("living");
Concept animal   = living.addChild("animal");
Concept plant    = living.addChild("plant");

// 3. Create a Concept with synonyms using the Concept constructor
Concept mammal = new Concept(animal, "mammal", "warm-blooded");
Concept dog    = new Concept(mammal, "dog", "canine");
Concept cat    = new Concept(mammal, "cat", "feline");
```

### Parsing from indented text

`Taxonomy.of(String)` parses a simple indented-text format, which is convenient for
configuration files or tests:

```
# lines starting with '#' are comments
animal
    mammal, warm-blooded
        dog, canine
        cat, feline
    reptile
        snake
```

Rules:
* **Indentation** is 4 spaces (or 1 tab = 4 spaces) per depth level.
* **Synonyms** on the same line are comma-separated.
* The first non-blank, non-comment line becomes the root.

```java
String text = """
    animal
        mammal, warm-blooded
            dog, canine
            cat, feline
        reptile
            snake
    """;

Taxonomy t = Taxonomy.of(text);
Concept mammal = t.getConcept("mammal");
Concept canine = t.getConcept("canine");
// dog and canine are the same concept node
assert t.getConcept("dog") == t.getConcept("canine");
```

---

## Querying the Taxonomy

### Looking up concepts

```java
Concept c = taxonomy.getConcept("dog");   // null if not found
boolean exists = taxonomy.contains("cat");
List<String> all = taxonomy.getConcepts(); // all registered keywords
```

### Structural properties

```java
int depth  = taxonomy.depth("dog");   // 0 = root; -1 if not found
int height = taxonomy.height();       // max depth of any node
int size   = taxonomy.size();         // number of keywords (named concepts)
int nodes  = taxonomy.nodeCount();    // total nodes, including anonymous ones
```

### Concept-level properties

```java
Concept dog = taxonomy.getConcept("dog");

boolean leaf        = dog.isLeaf();
int     depth       = dog.depth();        // depth from root
int     height      = dog.height();       // height of sub-tree
int     subtreeSize = dog.subtreeSize();  // keyword count in sub-tree

Set<String>    keywords  = dog.keywords();
List<Concept>  children  = dog.children();
List<Concept>  siblings  = dog.siblings();
List<Concept>  fromRoot  = dog.getPathFromRoot();
List<Concept>  toRoot    = dog.getPathToRoot();
```

### Ancestor / descendant tests

```java
taxonomy.isAncestor("mammal", "dog");    // true
taxonomy.isDescendant("dog", "mammal");  // true — symmetric helper

dog.isAncestorOf(mammal);   // false
dog.isDescendantOf(mammal); // true
```

### Lowest Common Ancestor (LCA)

```java
Concept lca = taxonomy.lowestCommonAncestor("dog", "snake");
// → "animal" (the deepest node that is an ancestor of both)
```

The LCA of a node with itself is the node itself.

### Shortest path

```java
List<Concept> path = taxonomy.shortestPath("dog", "snake");
// dog → mammal → animal → reptile → snake
// path.size() == distance + 1
```

The path goes *up* from the first concept to the LCA and then *down* to the second concept.

### Level / BFS / DFS traversal

```java
// All nodes at a given depth
List<Concept> level2 = taxonomy.level(2);

// Breadth-first order (root first)
List<Concept> bfsOrder = taxonomy.bfs();

// Depth-first pre-order traversal with a consumer
taxonomy.forEach(node -> System.out.println(node));

// All leaf nodes
List<Concept> leaves = taxonomy.leaves();

// All keywords under a given concept
List<String> subWords = taxonomy.subtree("mammal");
```

---

## Modifying a Taxonomy

### Adding and removing keywords

```java
Concept dog = taxonomy.getConcept("dog");
dog.addKeywords("hound", "pooch");   // add synonyms
dog.removeKeyword("pooch");          // remove one synonym
```

### Adding and removing children

```java
Concept newNode = parent.addChild("newConcept"); // returns the new Concept

// Or create and attach later:
Concept extra = new Concept(parent, "extra", "additional");

// Remove a child (and its entire sub-tree)
parent.removeChild(child); // also removes all descendant keywords from index
```

---

## Visualizing the Tree

`Taxonomy.toString()` renders the tree using Unicode box-drawing characters:

```java
Taxonomy t = Taxonomy.of("""
    animal
        mammal
            dog
            cat
        reptile
            snake
    """);
System.out.println(t);
```

Output:

```
[animal]
├── [mammal]
│   ├── [dog]
│   └── [cat]
└── [reptile]
    └── [snake]
```

Anonymous nodes are shown as `(anon)`.

---

## Semantic Distance and Similarity

`TaxonomicDistance` implements `smile.math.distance.Distance<Concept>` and wraps a `Taxonomy`
to provide four measures.

```java
TaxonomicDistance td = new TaxonomicDistance(taxonomy);
```

### Edge-counting distance

The number of edges on the shortest path between two concepts through their LCA:

```
d(a, b) = depth(a) + depth(b) − 2 × depth(LCA(a, b))
```

```java
double dist = td.d("dog", "snake");   // by keyword
double dist = td.d(dogConcept, snakeConcept); // by Concept object
```

Properties: symmetric, 0 iff identical, integer-valued.

### Normalized distance

The raw distance divided by the diameter of the taxonomy (`2 × height`), yielding a value in
**[0, 1]**:

```java
double nd = td.normalizedDistance("dog", "snake"); // 0.0 → 1.0
```

### Wu-Palmer similarity

Proposed by Wu & Palmer (1994), based on the depth of the LCA relative to the depths of the
two concepts:

```
sim(a, b) = 2 × depth(LCA) / (depth(a) + depth(b))
```

Returns **1** when both concepts are identical, and approaches **0** when the LCA is near the
root.

```java
double sim = td.wuPalmer("dog", "cat");   // 0.0 → 1.0
```

### Leacock-Chodorow similarity

Proposed by Leacock & Chodorow (1998), combining edge-counting distance with the overall
taxonomy height `H`:

```
raw  = −log(d(a, b) / (2 × H))
norm = raw / log(2 × H)          ∈ [0, 1]
```

Higher similarity means fewer edges between the two concepts. Returns **1** for identical
concepts.

```java
double sim = td.leacockChodorow("dog", "cat"); // 0.0 → 1.0
```

The raw score is normalized by dividing by `log(2H)` so that it always falls in `[0, 1]`.

### Lin similarity

Proposed by Lin (1998), using **information content (IC)** as a proxy for specificity.  When
no external corpus is available, depth in the taxonomy serves as the IC proxy:

```
IC(c) = −log((depth(c) + 1) / (H + 1))

sim(a, b) = 2 × IC(LCA) / (IC(a) + IC(b))
```

Deeper (more specific) concepts have higher IC. Returns **1** for identical concepts, **0**
when both concepts are at the root and the taxonomy has a single level.

```java
double sim = td.lin("dog", "cat"); // 0.0 → 1.0
```

### Summary of similarity measures

| Measure | Formula                       | Corpus needed? | Notes |
|---|-------------------------------|---|---|
| Wu-Palmer | `2·d(LCA) / (d(a)+d(b))`      | No | Simple, fast, widely used |
| Leacock-Chodorow | `−log(dist/(2H))` normalized | No | Sensitive to path length |
| Lin | `2·IC(LCA) / (IC(a)+IC(b))`   | No (depth proxy) | Information-theoretic foundation |

---

## Complete Example

```java
import smile.nlp.taxonomy.*;

public class TaxonomyDemo {
    public static void main(String[] args) {
        // Build the taxonomy from indented text
        String text = """
            entity
                physical
                    living
                        animal
                            mammal, warm-blooded
                                dog, canine
                                cat, feline
                                whale
                            reptile
                                snake
                                lizard
                    non-living
                        rock
                        water
                abstract
                    number
                    concept
            """;

        Taxonomy tax = Taxonomy.of(text);

        // Visualize
        System.out.println(tax);

        // Navigate
        System.out.println("Height : " + tax.height());
        System.out.println("Size   : " + tax.size() + " keywords");
        System.out.println("Nodes  : " + tax.nodeCount());

        // LCA
        Concept lca = tax.lowestCommonAncestor("dog", "snake");
        System.out.println("LCA(dog, snake) = " + lca);   // [animal]

        // Path
        tax.shortestPath("dog", "snake")
           .forEach(c -> System.out.print(c + " → "));
        System.out.println();

        // Distances and similarities
        TaxonomicDistance td = new TaxonomicDistance(tax);
        System.out.printf("d(dog, cat)            = %.1f%n", td.d("dog", "cat"));
        System.out.printf("normalized(dog, cat)   = %.3f%n", td.normalizedDistance("dog", "cat"));
        System.out.printf("Wu-Palmer(dog, cat)    = %.3f%n", td.wuPalmer("dog", "cat"));
        System.out.printf("Leacock-Chodorow(dog,cat) = %.3f%n", td.leacockChodorow("dog", "cat"));
        System.out.printf("Lin(dog, cat)          = %.3f%n", td.lin("dog", "cat"));

        System.out.printf("%ndog vs snake:%n");
        System.out.printf("Wu-Palmer   = %.3f%n", td.wuPalmer("dog", "snake"));
        System.out.printf("Lin         = %.3f%n", td.lin("dog", "snake"));

        // Subtree
        System.out.println("Mammals: " + tax.subtree("mammal"));

        // Leaves
        System.out.println("Leaves: " + tax.leaves().stream()
            .map(c -> c.keywords().iterator().next())
            .toList());
    }
}
```

---

## API Quick Reference

### `Taxonomy`

| Method | Description |
|---|---|
| `Taxonomy(String... keywords)` | Create taxonomy with an optional named root |
| `getRoot()` | Returns the root `Concept` |
| `getConcept(String keyword)` | Look up a concept by keyword; `null` if not found |
| `contains(String keyword)` | `true` if the keyword is registered |
| `getConcepts()` | All registered keywords |
| `size()` | Number of keywords |
| `nodeCount()` | Total tree nodes (including anonymous) |
| `depth(String keyword)` | Depth of a keyword (`-1` if not found) |
| `height()` | Maximum depth of any node |
| `lowestCommonAncestor(String, String)` | LCA by keyword |
| `lowestCommonAncestor(Concept, Concept)` | LCA by node reference |
| `isAncestor(String ancestor, String descendant)` | Ancestor test by keyword |
| `isDescendant(String descendant, String ancestor)` | Descendant test by keyword |
| `shortestPath(String, String)` | Ordered node list from v to w |
| `shortestPath(Concept, Concept)` | Ordered node list from v to w |
| `subtree(String keyword)` | All keywords in sub-tree rooted at keyword |
| `bfs()` | All nodes in breadth-first order |
| `leaves()` | All leaf nodes |
| `level(int depth)` | All nodes at a given depth |
| `forEach(Consumer<Concept>)` | DFS pre-order traversal |
| `toString()` | Pretty-print the tree with box-drawing characters |
| `Taxonomy.of(String text)` | Parse from indented text *(static factory)* |

### `Concept`

| Method | Description |
|---|---|
| `keywords()` | The synonym set |
| `addKeywords(String... kws)` | Add synonyms |
| `removeKeyword(String kw)` | Remove a synonym |
| `children()` | Child concept list |
| `addChild(String kw)` | Create and attach a named child |
| `addChild(Concept c)` | Attach an existing concept as a child |
| `removeChild(Concept c)` | Detach a child (and prune its sub-tree from the index) |
| `siblings()` | All sibling concepts |
| `isLeaf()` | `true` if no children |
| `depth()` | Depth from root |
| `height()` | Height of sub-tree rooted here |
| `subtreeSize()` | Keyword count in sub-tree |
| `isAncestorOf(Concept c)` | `true` if `this` is a proper ancestor of `c` |
| `isDescendantOf(Concept c)` | `true` if `this` is a proper descendant of `c` |
| `getPathFromRoot()` | Ordered list from root to this node |
| `getPathToRoot()` | Ordered list from this node to root |

### `TaxonomicDistance`

| Method | Description |
|---|---|
| `d(String x, String y)` | Edge-counting distance |
| `d(Concept x, Concept y)` | Edge-counting distance (by node) |
| `normalizedDistance(String x, String y)` | Normalized distance in [0, 1] |
| `wuPalmer(String x, String y)` | Wu-Palmer similarity in (0, 1] |
| `leacockChodorow(String x, String y)` | Leacock-Chodorow similarity in [0, 1] |
| `lin(String x, String y)` | Lin similarity in [0, 1] |

---

## References

1. Z. Wu and M. Palmer. *Verb semantics and lexical selection.* ACL, 1994.
2. C. Leacock and M. Chodorow. *Combining local context and WordNet similarity for word sense
   identification.* WordNet: An Electronic Lexical Database, 1998.
3. D. Lin. *An information-theoretic definition of similarity.* ICML, 1998.
4. G. A. Miller. *WordNet: A lexical database for English.* Communications of the ACM, 1995.


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

