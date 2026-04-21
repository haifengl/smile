# SMILE JSON

`smile-json` is a lightweight, clean, and efficient JSON library for Scala.
It is built entirely within the SMILE project and has **no external runtime
dependencies**.

Key capabilities:

- A **mutable** model of JSON objects and arrays, making it practical for use
  in database-oriented code where documents are updated in place.
- An **efficient recursive-descent parser** (adapted from spray-json) that
  reads from `String`, `Array[Char]`, or `Array[Byte]` (UTF-8) inputs.
- **Compact** and **pretty-print** serialization.
- **String interpolators** (`json""" """` and `jsan""" """`) with embedded
  variable support.
- **Beyond standard JSON**: native types for `Int`, `Long`, `Counter`,
  `BigDecimal`, `Instant`/`LocalDate`/`LocalTime`/`LocalDateTime`,
  `Timestamp`, `UUID`, `ObjectId` (BSON), and `Binary`.
- **BSON-compatible binary serialization** via `JsonSerializer`, suitable for
  efficient on-wire or on-disk storage.
- **Implicit conversions** in both directions so that Scala values slot into
  JSON structures without boilerplate.

---

## Table of Contents

1. [Installation](#installation)
2. [The Type Hierarchy](#the-type-hierarchy)
3. [Parsing JSON](#parsing-json)
4. [Navigating a Document](#navigating-a-document)
5. [Mutating a Document](#mutating-a-document)
6. [Building Documents Programmatically](#building-documents-programmatically)
7. [Serializing to Text](#serializing-to-text)
8. [Implicit Conversions](#implicit-conversions)
9. [Extended Type System](#extended-type-system)
10. [JsArray Operations](#jsarray-operations)
11. [Binary Serialization (BSON)](#binary-serialization-bson)
12. [ObjectId](#objectid)
13. [Complete Examples](#complete-examples)

---

## Installation

Add the dependency in `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":json"))
}
```

Then in every Scala file that uses the library:

```scala
import smile.json.*
```

This single import brings in the string interpolators, all `JsValue` types, and
all implicit conversions.

---

## The Type Hierarchy

All JSON values extend the sealed trait `JsValue`.

```
JsValue
├── JsNull          — JSON null
├── JsUndefined     — sentinel for missing/absent values
├── JsBoolean       — true / false  (aliases: JsTrue, JsFalse)
├── JsInt           — 32-bit integer
├── JsLong          — 64-bit integer
├── JsCounter       — 64-bit integer (56 effective bits; database counter)
├── JsDouble        — IEEE 754 double
├── JsDecimal       — arbitrary-precision decimal (java.math.BigDecimal)
├── JsString        — Unicode text
├── JsDate          — instant (java.time.Instant, milliseconds since epoch)
├── JsLocalDate     — date without time (java.time.LocalDate)
├── JsLocalTime     — time without date (java.time.LocalTime)
├── JsLocalDateTime — date + time without zone (java.time.LocalDateTime)
├── JsTimestamp     — SQL TIMESTAMP with nanosecond precision
├── JsUUID          — java.util.UUID
├── JsObjectId      — BSON-style 12-byte ObjectId
├── JsBinary        — raw byte array
├── JsObject        — mutable ordered map of String → JsValue
└── JsArray         — mutable indexed sequence of JsValue
```

`JsValue` extends `scala.Dynamic`, so dot-notation field access is supported on
`JsObject` without any code generation.

---

## Parsing JSON

### String interpolators (literal strings)

The `json""" """` interpolator parses a string literal into a `JsObject`.
The `jsan""" """` interpolator parses a string literal into a `JsArray`.

```scala
import smile.json.*

val doc = json"""
  {
    "store": {
      "book": [
        {
          "category": "reference",
          "author":   "Nigel Rees",
          "title":    "Sayings of the Century",
          "price":    8.95
        },
        {
          "category": "fiction",
          "author":   "Evelyn Waugh",
          "title":    "Sword of Honour",
          "price":    12.99
        },
        {
          "category": "fiction",
          "author":   "Herman Melville",
          "title":    "Moby Dick",
          "isbn":     "0-553-21311-3",
          "price":    8.99
        },
        {
          "category": "fiction",
          "author":   "J. R. R. Tolkien",
          "title":    "The Lord of the Rings",
          "isbn":     "0-395-19395-8",
          "price":    22.99
        }
      ],
      "bicycle": {
        "color": "red",
        "price": 19.95
      }
    }
  }
  """

val arr = jsan"""[1, 2, 3, "hello"]"""
```

Both interpolators support embedded variable references:

```scala
val title  = "Effective Scala"
val price  = 29.99
val author = "Li Haoyi"

val book = json"""
  {
    "title":  $title,
    "price":  $price,
    "author": $author
  }
  """
```

### Parsing a string variable

When the JSON text is in a runtime variable rather than a literal, use the
`parseJson` or `parseJsObject` extension methods on `String` (brought in by
`import smile.json.*`):

```scala
val raw = """{"x": 1, "y": 2}"""

val value:  JsValue  = raw.parseJson       // any JsValue
val obj:    JsObject = raw.parseJsObject   // JsObject (throws if not an object)
```

`parseJson` handles any valid JSON expression:

```scala
"42".parseJson          // JsInt(42)
"3.14".parseJson        // JsDouble(3.14)
"true".parseJson        // JsBoolean(true)
"null".parseJson        // JsNull
"[1,2,3]".parseJson     // JsArray
```

### Parser input variants

`JsonParser` also accepts `Array[Char]` and `Array[Byte]` (UTF-8) directly:

```scala
JsonParser(charArray)   // Array[Char]
JsonParser(byteArray)   // Array[Byte] (UTF-8)
JsonParser(string)      // String
```

### Number literal suffixes

The parser recognises two non-standard numeric suffixes for unambiguous type
selection in JSON text:

| Suffix | Type produced | Example |
|--------|--------------|---------|
| `l` or `L` | `JsLong`    | `42L`   |
| `c` or `C` | `JsCounter` | `0C`    |

Without a suffix, integers that fit in 32 bits become `JsInt`; larger integers
automatically become `JsLong`.

### Automatic recognition of UUIDs and ObjectIds

During parsing, any JSON string whose value exactly matches the UUID pattern
(`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`) is transparently lifted to `JsUUID`,
and any string matching `ObjectId(…)` (24 hex digits) is lifted to
`JsObjectId`.

---

## Navigating a Document

### Bracket notation

```scala
doc("store")("bicycle")("color")       // JsString("red")
doc("store")("book")(0)("author")      // JsString("Nigel Rees")
doc("store")("book")(2)("isbn")        // JsString("0-553-21311-3")
```

Negative indices count from the end:

```scala
doc("store")("book")(-1)("title")      // last book: "The Lord of the Rings"
```

Range slices on arrays:

```scala
val books = doc("store")("book").asInstanceOf[JsArray]
books(0, 2)        // first two books (exclusive end)
books(0, 4, 2)     // every other book: indices 0 and 2
books(1 until 3)   // books at indices 1 and 2
```

### Dot notation (Dynamic)

Because `JsValue` extends `scala.Dynamic`, field access reads naturally as if
the object had named methods:

```scala
doc.store.bicycle.color            // JsString("red")
doc.store.book(0).author           // JsString("Nigel Rees")
doc.store.book(1).price.asDouble   // 12.99
```

**Note:** because Scala is statically typed, `doc.store.book` has the static
type `JsValue`. Use `asInstanceOf[JsArray]` (or a pattern match) before
calling array-specific methods like `foreach`.

### Missing fields — `JsUndefined`

Accessing a field that does not exist returns `JsUndefined` rather than
throwing:

```scala
doc.book              // JsUndefined  (correct path is doc.store.book)
doc.store.bike.color  // JsUndefined  (correct key is "bicycle")
```

Use `JsObject.contains` to test presence before reading:

```scala
val obj = doc("store").asInstanceOf[JsObject]
if (obj.contains("bicycle")) …
```

Or use the safe `get` method that returns `Option[JsValue]`:

```scala
doc("store").asInstanceOf[JsObject].get("bicycle")   // Some(JsObject(...))
doc("store").asInstanceOf[JsObject].get("missing")   // None
```

### Extracting Scala values

Each `JsValue` subtype exposes `asXxx` methods to extract the underlying value:

```scala
val price: Double = doc.store.bicycle.price.asDouble
val color: String = doc.store.bicycle.color.asInt      // UnsupportedOperationException: not an Int
```

Available extractors:

| Method | Return type |
|--------|------------|
| `asBoolean` | `Boolean` |
| `asInt` | `Int` |
| `asLong` | `Long` |
| `asDouble` | `Double` |
| `asDecimal` | `java.math.BigDecimal` |
| `asInstant` | `java.time.Instant` |
| `asLocalDate` | `java.time.LocalDate` |
| `asLocalTime` | `java.time.LocalTime` |
| `asLocalDateTime` | `java.time.LocalDateTime` |
| `asTimestamp` | `java.sql.Timestamp` |

For the numeric types (`JsInt`, `JsLong`, `JsDouble`, `JsCounter`,
`JsDecimal`) cross-type coercions work:

```scala
JsInt(42).asDouble       // 42.0
JsDouble(3.0).asInt      // 3
JsLong(1000L).asInt      // 1000
```

---

## Mutating a Document

`JsObject` and `JsArray` are **mutable**. This is intentional — it makes the
library practical for in-place document updates that are common in database
workflows.

### Setting / adding a field

Assign through dot notation:

```scala
doc.store.bicycle.color = "green"
```

Or bracket notation:

```scala
doc("store")("bicycle")("color") = JsString("green")
```

The right-hand side is implicitly converted from common Scala types:

```scala
doc.store.bicycle.price  = 24.99          // Double → JsDouble
doc.store.bicycle.inStock = true          // Boolean → JsBoolean
doc.store.bicycle.year   = 2024           // Int → JsInt
doc.store.bicycle.serial = "SN-12345"     // String → JsString
```

### Removing a field

```scala
// returns Option[JsValue] — the removed value, or None if absent
val removed: Option[JsValue] = doc.store.book(0).asInstanceOf[JsObject].remove("price")

// Setting to JsUndefined has the same effect but returns nothing
doc.store.book(0).price = JsUndefined
```

### Deep merge

`JsObject` supports deep merging with `++=`:

```scala
val patch = json"""{"bicycle": {"color": "blue", "gears": 21}}"""
doc.store.asInstanceOf[JsObject] ++= patch

// "color" is overwritten; "gears" is added; other fields are untouched
```

Nested `JsObject` values are merged recursively; all other types are replaced.

---

## Building Documents Programmatically

### JsObject

```scala
// Vararg key–value pairs (insertion order preserved)
val obj = JsObject(
  "name"  -> JsString("Alice"),
  "age"   -> JsInt(30),
  "score" -> JsDouble(9.5)
)

// From an immutable Map
val map: Map[String, JsValue] = Map("x" -> JsInt(1), "y" -> JsInt(2))
val obj = JsObject(map)

// From a mutable map — implicit conversion
import scala.collection.mutable
val mmap = mutable.Map("key" -> (JsString("val"): JsValue))
val obj: JsObject = mmap   // implicit conversion
```

Scala maps of primitive types are converted implicitly via `toJsObject`:

```scala
val m: Map[String, Int] = Map("a" -> 1, "b" -> 2)
val obj: JsObject = m.toJsObject   // Map[String, Int] → JsObject

// Available for: Boolean, Int, Long, Double, BigDecimal, String, Date
```

### JsArray

```scala
// Vararg elements
val arr = JsArray(JsInt(1), JsDouble(2.5), JsString("three"))

// Implicit conversion from Array[T <: JsValue] or Seq[T <: JsValue]
val arr: JsArray = Array(JsInt(1), JsInt(2), JsInt(3))
val arr: JsArray = List(JsString("a"), JsString("b"))
```

Sequences of primitive types are converted via `toJsArray`:

```scala
val ints: Seq[Int] = Seq(1, 2, 3)
val arr: JsArray = ints.toJsArray   // Seq[Int] → JsArray

// Available for: Boolean, Int, Long, Double, BigDecimal, String,
//               LocalDate, LocalTime, LocalDateTime, Date, Timestamp
```

---

## Serializing to Text

### Compact (default)

`toString` and `compactPrint` both produce compact output with no extra
whitespace:

```scala
val doc = json"""{"a": 1, "b": [2, 3]}"""

doc.toString       // {"a":1,"b":[2,3]}
doc.compactPrint   // identical
```

### Pretty-print

```scala
doc.prettyPrint
```

produces indented output:

```json
{
  "a": 1,
  "b": [
    2,
    3
  ]
}
```

### JSON Lines (newline-delimited JSON)

`JsArray` has a convenience method for JSONL output, one element per line:

```scala
val arr = jsan"""[{"id":1},{"id":2},{"id":3}]"""
println(arr.jsonl)
// {"id":1}
// {"id":2}
// {"id":3}
```

---

## Implicit Conversions

`import smile.json.*` activates two sets of implicit conversions so you almost
never need to construct `JsXxx` wrapper types explicitly.

### Scala → JsValue (lift)

| Scala type | JsValue type |
|-----------|-------------|
| `Boolean` | `JsBoolean` |
| `Int` | `JsInt` |
| `Long` | `JsLong` |
| `Double` | `JsDouble` |
| `java.math.BigDecimal` | `JsDecimal` |
| `String` | `JsString` |
| `java.time.Instant` | `JsDate` |
| `java.time.LocalDate` | `JsLocalDate` |
| `java.time.LocalTime` | `JsLocalTime` |
| `java.time.LocalDateTime` | `JsLocalDateTime` |
| `java.sql.Timestamp` | `JsTimestamp` |
| `java.util.Date` | `JsTimestamp` |
| `java.util.UUID` | `JsUUID` |
| `ObjectId` | `JsObjectId` |
| `Array[Byte]` | `JsBinary` |
| `Array[T <: JsValue]` | `JsArray` |
| `Seq[T <: JsValue]` | `JsArray` |
| `Seq[(String, T <: JsValue)]` | `JsObject` |
| `Map[String, T <: JsValue]` | `JsObject` |

### JsValue → Scala (lower)

| JsValue type | Scala type |
|-------------|-----------|
| `JsBoolean` | `Boolean` |
| `JsInt` | `Int` |
| `JsLong` | `Long` |
| `JsDouble` | `Double` |
| `JsDecimal` | `java.math.BigDecimal` |
| `JsString` | `String` |
| `JsDate` | `java.time.Instant` |
| `JsLocalDate` | `java.time.LocalDate` |
| `JsLocalTime` | `java.time.LocalTime` |
| `JsLocalDateTime` | `java.time.LocalDateTime` |
| `JsTimestamp` | `java.sql.Timestamp` (also `java.util.Date`) |
| `JsObjectId` | `ObjectId` |
| `JsUUID` | `java.util.UUID` |
| `JsBinary` | `Array[Byte]` |

These lowering conversions allow `JsXxx` values to be passed directly to any
Java/Scala API that expects the underlying type:

```scala
val price: Double = doc.store.bicycle.price   // implicit JsDouble → Double
val color: String = doc.store.bicycle.color   // implicit JsString → String
```

### Collection convenience conversions

Sequences and maps of primitive types gain `toJsArray` / `toJsObject` extension
methods:

```scala
Seq(true, false, true).toJsArray
Array(1, 2, 3).toJsArray
Seq(1.0, 2.0, 3.0).toJsArray
Seq("a", "b").toJsArray
Map("x" -> 1, "y" -> 2).toJsObject
Map("ts" -> java.time.LocalDate.now()).toJsObject
```

---

## Extended Type System

Beyond the six standard JSON types (`null`, `boolean`, `number`, `string`,
`array`, `object`) `smile-json` supports these extended value types:

### Numeric types

| Type | When used |
|------|-----------|
| `JsInt` | 32-bit integer (parsed automatically when value fits) |
| `JsLong` | 64-bit integer (auto-promoted when value overflows `Int`; literal suffix `L`) |
| `JsCounter` | 64-bit integer, semantically a counter; literal suffix `C` |
| `JsDouble` | IEEE 754 double (any number containing `.`, `e`, or `E`) |
| `JsDecimal` | Arbitrary-precision decimal |

```scala
JsInt(42)
JsLong(Long.MaxValue)
JsCounter(0L)
JsDouble(3.14)
JsDecimal("1234567890.123456789012345678901234567890")
JsDecimal(new java.math.BigDecimal("9.99"))
```

### Temporal types

All temporal types round-trip through text as ISO-8601 strings; in BSON they
are stored as compact integers.

```scala
import java.time.*

JsDate(Instant.now())                          // UTC instant (milliseconds)
JsDate(epochMillis: Long)
JsDate("2024-01-15T10:30:00Z")                 // ISO-8601 string

JsLocalDate(LocalDate.of(2024, 1, 15))
JsLocalDate(epochDay: Long)
JsLocalDate("2024-01-15")

JsLocalTime(LocalTime.of(10, 30, 0))
JsLocalTime(nanoOfDay: Long)
JsLocalTime("10:30:00")

JsLocalDateTime(LocalDateTime.of(2024, 1, 15, 10, 30))
JsLocalDateTime(date: LocalDate, time: LocalTime)
JsLocalDateTime("2024-01-15T10:30:00")

JsTimestamp(java.sql.Timestamp.from(Instant.now()))   // nanosecond precision
JsTimestamp(epochMillis: Long)
JsTimestamp("2024-01-15 10:30:00.123456789")          // JDBC escape format
```

**Precision note:** `JsLocalTime` and `JsLocalDateTime` truncate to-second
precision in BSON storage (nanoseconds are dropped). Use `JsTimestamp` when
sub-second precision matters.

### Identity types

```scala
// UUID — auto-detected when parsing strings of length 36
JsUUID()                                     // random UUID
JsUUID(UUID.randomUUID())
JsUUID(mostSigBits: Long, leastSigBits: Long)
JsUUID("550e8400-e29b-41d4-a716-446655440000")
JsUUID(bytes: Array[Byte])                   // name-based UUID from bytes

// ObjectId (BSON) — auto-detected when parsing "ObjectId(…)" strings
JsObjectId()                                 // generate new ObjectId
JsObjectId(ObjectId.generate)
JsObjectId("507f1f77bcf86cd799439011")       // 24-char hex string
JsObjectId(bytes: Array[Byte])

// Binary
JsBinary(Array[Byte](0x48, 0x65, 0x6c, 0x6c, 0x6f))
```

### Comparison and ordering

All concrete `JsValue` subtypes that represent scalar values implement
`Ordered[T]`, so they can be sorted and compared with `<`, `>`, `<=`, `>=`.
The `JsValueOrdering` can sort a heterogeneous collection by attempting numeric
comparison first, falling back to string comparison:

```scala
import scala.util.Sorting
val values: Array[JsValue] = Array(JsInt(3), JsDouble(1.5), JsInt(2))
Sorting.quickSort(values)(JsValueOrdering)
// → JsDouble(1.5), JsInt(2), JsInt(3)
```

---

## JsArray Operations

`JsArray` implements `Iterable[JsValue]`, giving access to all standard Scala
collection operations.

### Appending elements

```scala
val a = JsArray(JsInt(1), JsInt(2), JsInt(3))

a += JsInt(4)                        // append one element
a += JsInt(5)
a ++= JsArray(JsInt(6), JsInt(7))    // append another array
a ++= List(JsInt(8), JsInt(9))       // append any IterableOnce
```

### Prepending elements

```scala
JsInt(0) +=: a              // prepend one element
JsArray(JsInt(-1)) ++=: a   // prepend another array
```

### Inserting at an index

```scala
a.insertAll(2, Iterable(JsString("inserted"), JsString("here")))
```

### Removing elements

```scala
a.remove(0)          // remove and return element at index 0
a.remove(-1)         // remove last element
a.remove(1, 2)       // remove 2 elements starting at index 1

// Setting to JsUndefined does NOT shrink the array
a(0) = JsUndefined   // keeps size; element becomes undefined
```

### Updating in place

```scala
a(2) = JsString("replaced")
```

### Iteration and functional operations

```scala
val books = doc.store.book.asInstanceOf[JsArray]

books.foreach { b => println(b.title) }

val titles: Iterable[JsValue] = books.map(_.title)
val cheap  = books.filter(_.price.asDouble < 10.0)
val total  = books.map(_.price.asDouble).sum
val found  = books.find(_.author == "Herman Melville")

books.forall(_.price.asDouble > 0.0)
books.exists(_.category == "fiction")
books.size
books.isEmpty
```

---

## Binary Serialization (BSON)

`JsonSerializer` encodes any `JsValue` to a compact binary format based on the
[BSON specification](http://bsonspec.org/spec.html), with extensions for the
extra types not covered by BSON.

**Key characteristics:**

- Root can be any `JsValue` (BSON requires a document root; `smile-json`
  relaxes this).
- `JsLocalTime` and `JsLocalDateTime` lose sub-second precision (stored as
  packed integers for space efficiency).
- `JsCounter` is not supported in the binary format (throws
  `IllegalArgumentException`).
- Default buffer size is **10 MB**. Pass a pre-allocated `ByteBuffer` for
  different sizes.
- **Not thread-safe** — each thread needs its own instance.

```scala
val serializer = new JsonSerializer()           // 10 MB buffer (default)
val big        = new JsonSerializer(ByteBuffer.allocate(50 * 1024 * 1024))  // 50 MB

// Serialize any JsValue to Array[Byte]
val bytes: Array[Byte] = serializer.serialize(doc)

// Deserialize back
val restored: JsValue = serializer.deserialize(bytes)

// Or from a ByteBuffer directly
val restored: JsValue = serializer.deserialize(byteBuffer)

// Reuse the internal buffer (useful in tight loops)
serializer.clear()
val bytes = serializer.serialize(nextDoc)
```

### BSON type codes

The serializer uses standard BSON type bytes where possible and reserves custom
bytes for the extended types:

| Byte | Type |
|------|------|
| `0x01` | `JsDouble` |
| `0x02` | `JsString` |
| `0x03` | `JsObject` |
| `0x04` | `JsArray` |
| `0x05` | `JsBinary` / `JsUUID` |
| `0x07` | `JsObjectId` |
| `0x08` | `JsBoolean` |
| `0x09` | `JsDate` (UTC millis) |
| `0x0A` | `JsNull` |
| `0x10` | `JsInt` |
| `0x12` | `JsLong` |
| `0x20` | `JsLocalDate` |
| `0x21` | `JsLocalTime` |
| `0x22` | `JsLocalDateTime` |
| `0x23` | `JsTimestamp` |
| `0x30` | `JsDecimal` |
| `0x06` | `JsUndefined` |

---

## ObjectId

`ObjectId` is a 12-byte BSON identifier composed of:

```
┌───────────────┬────────────────┬────────────────┬──────────────┐
│  timestamp    │  machine id    │  thread id     │  increment   │
│   (4 bytes)   │   (3 bytes)    │   (2 bytes)    │  (3 bytes)   │
└───────────────┴────────────────┴────────────────┴──────────────┘
```

This structure means ObjectIds are naturally sortable by creation time.

```scala
// Generate a new ObjectId
val id = ObjectId.generate         // or: ObjectId()

// Construct from a 24-character hex string
val id = ObjectId("507f1f77bcf86cd799439011")

// Safe parse (returns Try)
val idOpt: scala.util.Try[ObjectId] = ObjectId.parse("507f1f77bcf86cd799439011")

// Generate with a specific timestamp (for range queries)
val anchor = ObjectId.fromTime(System.currentTimeMillis, fillOnlyTimestamp = true)

// Extract creation time
val createdAt: java.util.Date = id.timestamp

// String representation
id.toString   // ObjectId(507F1F77BCF86CD799439011)
```

`JsObjectId` wraps `ObjectId` and is recognised automatically during JSON
parsing. Any string of exactly the form `ObjectId(<24 hex chars>)` is parsed
to `JsObjectId` — no special annotation needed.

---

## Complete Examples

### Example 1 — Parse, navigate, and pretty-print

```scala
import smile.json.*

val doc = json"""
  {
    "store": {
      "book": [
        {"title": "Scala Programming",    "price": 34.99, "pages": 450},
        {"title": "Functional Design",    "price": 42.00, "pages": 380},
        {"title": "Type-Driven Dev",      "price": 39.50, "pages": 410}
      ],
      "bicycle": {"color": "red", "price": 19.95}
    }
  }
  """

// Navigate with dot notation
println(doc.store.bicycle.color)           // red
println(doc.store.book(0).title)           // Scala Programming

// Navigate with bracket notation
println(doc("store")("book")(1)("price"))  // 42.0

// Iterate the array
doc.store.book.asInstanceOf[JsArray].foreach { b =>
  println(s"${b.title} — $$${b.price.asDouble}")
}

// Pretty-print
println(doc.prettyPrint)
```

### Example 2 — Build a document programmatically

```scala
import smile.json.*
import java.util.UUID

// Construct via JsObject literal syntax
val user = JsObject(
  "_id"       -> JsObjectId(),
  "name"      -> JsString("Alice"),
  "age"       -> JsInt(30),
  "scores"    -> Seq(98, 87, 93).toJsArray,      // Seq[Int] → JsArray
  "metadata"  -> Map("role" -> "admin", "team" -> "ml").toJsObject,
  "sessionId" -> JsUUID(UUID.randomUUID())
)

println(user.prettyPrint)

// Or build via mutation
val record = JsObject()
record.name   = "Bob"              // String auto-lifted to JsString
record.active = true               // Boolean auto-lifted to JsBoolean
record.score  = 4.8                // Double auto-lifted to JsDouble
```

### Example 3 — Mutate a nested document

```scala
import smile.json.*

val inventory = json"""
  {
    "items": [
      {"sku": "A1", "qty": 10, "price": 5.00},
      {"sku": "B2", "qty": 3,  "price": 12.50}
    ]
  }
  """

val items = inventory.items.asInstanceOf[JsArray]

// Update a field in place
items(0).asInstanceOf[JsObject]("qty") = JsInt(15)

// Remove a field
items(1).asInstanceOf[JsObject].remove("price")

// Append a new item
items += json"""{"sku": "C3", "qty": 7, "price": 8.00}"""

println(inventory.prettyPrint)
```

### Example 4 — Embedded variable interpolation

```scala
import smile.json.*
import java.time.LocalDate

case class Product(sku: String, name: String, price: Double, launched: LocalDate)

def toJson(p: Product): JsObject = json"""
  {
    "sku":      ${p.sku},
    "name":     ${p.name},
    "price":    ${p.price},
    "launched": ${p.launched}
  }
  """

val product = Product("X9", "Widget Pro", 24.99, LocalDate.of(2024, 3, 1))
println(toJson(product).prettyPrint)
// "launched" is stored as JsLocalDate, serialized as "2024-03-01"
```

### Example 5 — Binary serialization round-trip

```scala
import smile.json.*

val doc = json"""{"user": "Alice", "score": 99, "active": true}"""

val serializer = new JsonSerializer()

// Encode to binary (BSON-compatible)
val bytes: Array[Byte] = serializer.serialize(doc)
println(s"Encoded: ${bytes.length} bytes")

// Decode back
val restored = serializer.deserialize(bytes).asInstanceOf[JsObject]
println(restored.prettyPrint)

assert(restored("user")   == JsString("Alice"))
assert(restored("score")  == JsInt(99))
assert(restored("active") == JsBoolean(true))
```

### Example 6 — Sorting and filtering an array

```scala
import smile.json.*

val catalog = jsan"""
  [
    {"title": "Moby Dick",    "price": 8.99},
    {"title": "Hamlet",       "price": 4.50},
    {"title": "War & Peace",  "price": 14.99}
  ]
  """

// Filter books under $10
val affordable = catalog.filter(_.price.asDouble < 10.0)
affordable.foreach(b => println(b.title))

// Sort by price
val sorted = catalog.toSeq.sortBy(_.price.asDouble)
sorted.foreach(b => println(s"${b.title}: $$${b.price.asDouble}"))

// Total price
val total = catalog.map(_.price.asDouble).sum
println(f"Total: $$${total}%.2f")
```

### Example 7 — Working with ObjectId and temporal types

```scala
import smile.json.*
import java.time.{Instant, LocalDate}

// Create a document with identity and temporal fields
val event = JsObject(
  "_id"       -> JsObjectId(),
  "type"      -> JsString("purchase"),
  "createdAt" -> JsDate(Instant.now()),
  "date"      -> JsLocalDate(LocalDate.now()),
  "amount"    -> JsDecimal("123.456789012345")   // arbitrary precision
)

// Extract the creation timestamp from the ObjectId
val oid = event("_id").asInstanceOf[JsObjectId].value
println(s"Created: ${oid.timestamp}")

// Round-trip through BSON
val serializer = new JsonSerializer()
val bytes = serializer.serialize(event)
val restored = serializer.deserialize(bytes)

println(restored.prettyPrint)
```

---

## Design Notes

### Mutability

Unlike most Scala JSON libraries (Circe, play-json, ujson in immutable mode),
`smile-json` deliberately makes `JsObject` and `JsArray` **mutable**. This
choice is driven by database use cases where partial document updates are common
and copying the entire document on each change would be costly.

If you need value semantics, perform deep copies manually or work with
`prettyPrint`/`compactPrint` and re-parse.

### Field ordering

`JsObject` uses `collection.mutable.SeqMap` internally, so **insertion order is
preserved** in both serialization and iteration — consistent with the modern
JSON specification and important for reproducible output.

### Number representation

The parser preserves the distinction between integers and doubles:

- Integers that fit in 32 bits → `JsInt`
- Integers that overflow 32 bits → `JsLong`
- Numbers with `.`, `e`, or `E` → `JsDouble`

This means round-tripping `42` through the parser always yields `JsInt(42)`,
not `JsDouble(42.0)`, which avoids unintended precision loss and matches what
most database drivers expect.

### `JsUndefined` vs `JsNull`

| Value | Meaning |
|-------|---------|
| `JsNull` | Explicit JSON `null` — a value is present and is null |
| `JsUndefined` | Field absent or sentinel for deletion |

Setting a field to `JsUndefined` removes it from the next serialization of a
`JsObject`. On `JsArray`, setting an element to `JsUndefined` does **not**
shrink the array; use `remove(index)` for that.

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
