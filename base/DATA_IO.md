# SMILE — Data I/O User Guide & Tutorial

This document covers the `smile.io` package — every class and interface used
to read data into and write data out of SMILE's in-memory representations
(`DataFrame`, `SparseDataset`, and serializable objects).

---

## Table of Contents

1. [Architecture overview](#1-architecture-overview)
2. [Input — resolving file paths and URIs](#2-input--resolving-file-paths-and-uris)
3. [Read — the one-stop reading interface](#3-read--the-one-stop-reading-interface)
   - [Auto-dispatch by extension](#31-auto-dispatch-by-extension)
   - [CSV](#32-csv)
   - [JSON](#33-json)
   - [ARFF](#34-arff)
   - [Apache Arrow / Feather](#35-apache-arrow--feather)
   - [Apache Avro](#36-apache-avro)
   - [Apache Parquet](#37-apache-parquet)
   - [SAS7BDAT](#38-sas7bdat)
   - [libsvm sparse format](#39-libsvm-sparse-format)
   - [Java object serialization](#310-java-object-serialization)
4. [Write — the one-stop writing interface](#4-write--the-one-stop-writing-interface)
   - [CSV](#41-csv)
   - [Apache Arrow](#42-apache-arrow)
   - [ARFF](#43-arff)
   - [Java object serialization](#44-java-object-serialization)
5. [CSV in depth](#5-csv-in-depth)
   - [Schema inference](#51-schema-inference)
   - [Explicit schema](#52-explicit-schema)
   - [Format string reference](#53-format-string-reference)
   - [CSVFormat object API](#54-csvformat-object-api)
   - [Charset](#55-charset)
   - [Reading a limited number of rows](#56-reading-a-limited-number-of-rows)
   - [Writing](#57-writing)
6. [JSON in depth](#6-json-in-depth)
   - [Single-line mode](#61-single-line-mode)
   - [Multi-line mode](#62-multi-line-mode)
   - [Schema override](#63-schema-override)
7. [ARFF in depth](#7-arff-in-depth)
   - [ARFF format primer](#71-arff-format-primer)
   - [Reading](#72-reading)
   - [Writing](#73-writing)
8. [Apache Arrow in depth](#8-apache-arrow-in-depth)
9. [Apache Avro in depth](#9-apache-avro-in-depth)
10. [Apache Parquet in depth](#10-apache-parquet-in-depth)
11. [SAS7BDAT in depth](#11-sas7bdat-in-depth)
12. [libsvm sparse format in depth](#12-libsvm-sparse-format-in-depth)
13. [CacheFiles — downloading remote datasets](#13-cachefiles--downloading-remote-datasets)
14. [Paths — test data helper](#14-paths--test-data-helper)
15. [End-to-end tutorials](#15-end-to-end-tutorials)
    - [Load, clean, and save a CSV pipeline](#151-load-clean-and-save-a-csv-pipeline)
    - [Cross-format conversion](#152-cross-format-conversion)
    - [Training a model from libsvm data](#153-training-a-model-from-libsvm-data)
    - [Downloading and caching a remote dataset](#154-downloading-and-caching-a-remote-dataset)
16. [API quick reference](#16-api-quick-reference)

---

## 1. Architecture overview

```
smile.io
│
├── Read          (interface)   Static factory methods for all read operations
├── Write         (interface)   Static factory methods for all write operations
│
├── CSV           (class)       Comma-/delimiter-separated values reader & writer
├── JSON          (class)       JSON reader (single-line and multi-line)
├── Arff          (class)       Weka ARFF reader & writer  (AutoCloseable)
├── Arrow         (class)       Apache Arrow IPC stream reader & writer
├── Avro          (class)       Apache Avro reader
├── Parquet       (class)       Apache Parquet reader (via Arrow Dataset API)
├── SAS           (interface)   SAS7BDAT reader (via Parso)
│
├── Input         (interface)   Resolve a String path/URI to InputStream/Reader
├── CacheFiles    (interface)   Download remote files to a local cache directory
└── Paths         (interface)   Locate test-data resources on the classpath
```

`Read` and `Write` are the recommended entry points for most use cases.
The concrete classes (`CSV`, `JSON`, `Arff`, …) are used directly only when
you need fine-grained control — custom charset, explicit schema, or row limit.

---

## 2. Input — resolving file paths and URIs

`Input` is a low-level helper used internally by every reader. You can also
use it directly to get a `BufferedReader` or `InputStream` for any location:

```java
import smile.io.Input;

// Local file path (absolute or relative)
InputStream s1 = Input.stream("/data/iris.csv");
InputStream s2 = Input.stream("data/iris.csv");

// Windows drive-letter path — treated as a local file
InputStream s3 = Input.stream("C:/data/iris.csv");

// file:// URI
InputStream s4 = Input.stream("file:///data/iris.csv");

// HTTP / FTP — streams the remote content directly
InputStream s5 = Input.stream("https://example.com/iris.csv");

// Buffered reader with explicit charset
BufferedReader r = Input.reader("data/iris.csv", StandardCharsets.ISO_8859_1);
```

**Resolution rules:**

| Input string | Resolved as |
|---|---|
| Starts with `file://` | Local path extracted from the URI |
| Scheme is one character (e.g. `C:`) | Windows drive letter — treated as local path |
| No scheme | Local path via `Path.of(path)` |
| `http://`, `https://`, `ftp://` | Remote URL — opened with `URI.toURL().openStream()` |

---

## 3. Read — the one-stop reading interface

`Read` is a static-method interface; you never instantiate it.

```java
import smile.io.Read;
```

### 3.1 Auto-dispatch by extension

`Read.data(path)` examines the **last path segment's** file extension and
delegates to the appropriate reader automatically. A query string or
fragment in the path is stripped before the extension is extracted, so
URIs like `s3://bucket/iris.csv?version=3` are handled correctly.

```java
DataFrame df = Read.data("iris.csv");                       // CSV
DataFrame df = Read.data("weather.arff");                   // ARFF
DataFrame df = Read.data("users.json");                     // JSON (single-line)
DataFrame df = Read.data("airline.sas7bdat");               // SAS
DataFrame df = Read.data("userdata.avro", "schema.avsc");   // Avro + schema path
DataFrame df = Read.data("file:///data/users.parquet");     // Parquet
DataFrame df = Read.data("events.feather");                 // Arrow/Feather
```

**Extension → reader mapping:**

| Extension(s) | Reader |
|---|---|
| `csv`, `txt`, `dat` | `Read.csv` |
| `arff` | `Read.arff` |
| `json` | `Read.json` |
| `sas7bdat` | `Read.sas` |
| `avro` | `Read.avro` (format = schema file path) |
| `parquet` | `Read.parquet` |
| `feather` | `Read.arrow` |

The optional `format` parameter is passed through to the underlying reader:

```java
// CSV: comma-separated key=value format options
DataFrame df = Read.data("data.csv", "header=true,delimiter=\\t,comment=#");

// CSV: explicit "csv" keyword overrides unrecognised extensions
DataFrame df = Read.data("data.dat",  "csv");
DataFrame df = Read.data("data.txt",  "csv,header=true");

// JSON: mode string
DataFrame df = Read.data("records.json", "MULTI_LINE");

// Avro: path to the .avsc schema file
DataFrame df = Read.data("records.avro", "schema/user.avsc");
```

### 3.2 CSV

```java
// Simplest – comma-delimited, no header, schema inferred from first 1000 rows
DataFrame df = Read.csv("iris.csv");

// With format string
DataFrame df = Read.csv("prostate.csv", "header=true,delimiter=\\t");

// With explicit CSVFormat object
CSVFormat fmt = CSVFormat.Builder.create()
        .setDelimiter('\t')
        .setHeader()
        .setSkipHeaderRecord(true)
        .get();
DataFrame df = Read.csv("prostate.csv", fmt);

// With explicit CSVFormat + schema
StructType schema = new StructType(
        new StructField("lcavol",  DataTypes.DoubleType),
        new StructField("age",     DataTypes.IntType));
DataFrame df = Read.csv("prostate.csv", fmt, schema);

// From a java.nio.file.Path (no URISyntaxException)
DataFrame df = Read.csv(Path.of("/data/iris.csv"));
DataFrame df = Read.csv(Path.of("/data/iris.csv"), fmt);
DataFrame df = Read.csv(Path.of("/data/iris.csv"), fmt, schema);
```

### 3.3 JSON

```java
// Single-line mode: one JSON object per line (default)
DataFrame df = Read.json("books.json");

// Multi-line mode: entire file is a JSON array
DataFrame df = Read.json("books.json", JSON.Mode.MULTI_LINE, null);

// From Path
DataFrame df = Read.json(Path.of("books.json"));
DataFrame df = Read.json(Path.of("books.json"), JSON.Mode.MULTI_LINE, null);
```

### 3.4 ARFF

```java
// String path or URI
DataFrame df = Read.arff("weather.arff");

// java.nio.file.Path
DataFrame df = Read.arff(Path.of("weather.arff"));
```

### 3.5 Apache Arrow / Feather

```java
// String path or URI
DataFrame df = Read.arrow("events.feather");

// java.nio.file.Path
DataFrame df = Read.arrow(Path.of("events.feather"));
```

### 3.6 Apache Avro

Avro requires a separate schema (`.avsc`) file or `InputStream`:

```java
// Schema as a file path string
DataFrame df = Read.avro("users.avro", "schema/user.avsc");

// Schema as an InputStream
InputStream schemaStream = getClass().getResourceAsStream("/user.avsc");
DataFrame df = Read.avro("users.avro", schemaStream);

// From java.nio.file.Path
DataFrame df = Read.avro(Path.of("users.avro"), Path.of("schema/user.avsc"));
DataFrame df = Read.avro(Path.of("users.avro"), schemaStream);
```

### 3.7 Apache Parquet

Parquet is read via the **Apache Arrow Dataset API** and requires a
`file://` URI on Windows (SMILE adds the leading `/` automatically):

```java
// From java.nio.file.Path (recommended — SMILE handles URI conversion)
DataFrame df = Read.parquet(Path.of("/data/users.parquet"));

// From a URI string  (add leading slash on Windows if needed)
DataFrame df = Read.parquet("file:///data/users.parquet");
```

### 3.8 SAS7BDAT

```java
// String path or URI
DataFrame df = Read.sas("airline.sas7bdat");

// java.nio.file.Path
DataFrame df = Read.sas(Path.of("airline.sas7bdat"));
```

### 3.9 libsvm sparse format

`Read.libsvm` returns a `SparseDataset<Integer>` (not a `DataFrame`):

```java
import smile.data.SparseDataset;

// String path or URI
SparseDataset<Integer> train = Read.libsvm("news20.dat");

// java.nio.file.Path
SparseDataset<Integer> test  = Read.libsvm(Path.of("news20.t.dat"));

// From a BufferedReader
SparseDataset<Integer> ds    = Read.libsvm(Files.newBufferedReader(path));

// Access samples
int label = train.get(0).y();               // integer class label
double v  = train.get(0).x().get(196);      // feature 196 value (0-based index)
int ncol  = train.ncol();                   // number of features
int nnz   = train.nz();                     // total non-zero entries
```

**libsvm format:**
```
<label> <index1>:<value1> <index2>:<value2> ...
```
- Indices are **1-based** in the file; SMILE converts them to 0-based internally.
- Indices must be ≥ 1 (a `NumberFormatException` is thrown for index 0).
- Indices within each row should be in ascending order.
- Empty lines are tolerated; an empty file produces an empty dataset.

### 3.10 Java object serialization

```java
// Read a serialized Java object
Object obj = Read.object(Path.of("model.ser"));
MyModel model = (MyModel) obj;
```

---

## 4. Write — the one-stop writing interface

`Write` is a static-method interface; you never instantiate it.

```java
import smile.io.Write;
```

### 4.1 CSV

```java
// Default comma-separated format (always writes a header row first)
Write.csv(df, Path.of("output.csv"));

// Custom format
CSVFormat fmt = CSVFormat.Builder.create()
        .setDelimiter('\t')
        .get();
Write.csv(df, Path.of("output.tsv"), fmt);
```

> **Note:** `Write.csv` always writes a header row (column names) as the first
> line, followed by the data rows. Every cell is serialized via
> `Tuple.getString(j)` so values are always human-readable strings.

### 4.2 Apache Arrow

```java
Write.arrow(df, Path.of("output.feather"));
```

Arrow preserves the full SMILE type system including nullable variants,
temporal types (`LocalDate`, `LocalTime`, `LocalDateTime`), and `String`
(UTF-8 VarChar).

### 4.3 ARFF

```java
// Third argument is the ARFF @relation name
Write.arff(df, Path.of("output.arff"), "my_dataset");
```

Numeric columns become `@attribute … NUMERIC`, string columns become
`@attribute … STRING`, and columns with a `NominalScale` measure become
`@attribute … {val1, val2, …}`.

### 4.4 Java object serialization

```java
// Write to a specific path
Write.object(model, Path.of("model.ser"));

// Write to a temp file (auto-deleted on JVM exit); useful in tests
Path tmp = Write.object(model);
```

---

## 5. CSV in depth

### 5.1 Schema inference

When no schema is provided, `CSV` reads the first `min(1000, limit)` rows
and infers a `StructType` using these rules:

1. Each column cell is parsed with `DataType.infer(value)`:
   - Pure integers → `IntType`
   - Integers that would overflow `int` → `LongType`
   - Decimal numbers → `DoubleType`
   - `true`/`false` (case-insensitive) → `BooleanType`
   - Everything else → `StringType`
2. Column types are **widened** across all sampled rows with
   `DataType.coerce(current, candidate)`:
   - `Int` + `Double` → `Double`
   - `Int` + `String` → `String`
3. If any value in a column is empty/missing after the schema pass, the
   inferred primitive type is promoted to its nullable variant
   (`NullableIntType`, `NullableDoubleType`, etc.).

Column names are taken from the CSV header row (if the format has header
enabled); otherwise synthetic names `V1`, `V2`, … are generated.

### 5.2 Explicit schema

Supplying an explicit schema bypasses inference entirely, which is faster
and prevents type misdetection on edge-case data:

```java
StructType schema = new StructType(
        new StructField("country",  DataTypes.StringType),
        new StructField("gdp_pct",  DataTypes.DoubleType),
        new StructField("debt_pct", DataTypes.DoubleType),
        new StructField("interest", DataTypes.DoubleType)
);

CSV csv = new CSV(CSVFormat.Builder.create()
        .setHeader().setSkipHeaderRecord(true).get());
csv.schema(schema);
DataFrame df = csv.read("gdp.csv");
```

### 5.3 Format string reference

`Read.csv(path, formatString)` and `Read.data(path, formatString)` accept a
comma-separated list of `key=value` pairs.  **The comma is the token
separator** — do not use a comma as the delimiter value; use `\\,` or switch
to `Read.csv(path, CSVFormat)` instead.

| Key | Value | Effect |
|---|---|---|
| `delimiter` | Single character (escape sequences: `\\t`, `\\n`, `\|`, …) | Sets the field delimiter |
| `header` | `true` | First row treated as column names, skipped from data |
| `header` | `col1\|col2\|…` | Explicit column names; no row is skipped |
| `comment` | Single character (e.g. `#`, `%`) | Lines starting with this character are ignored |
| `quote` | Single character (e.g. `"`, `'`) | Quoting character for fields containing the delimiter |
| `escape` | Single character (e.g. `\\`) | Escape character inside quoted fields |

```java
// Tab-delimited with header
DataFrame df = Read.csv("data.tsv", "delimiter=\\t,header=true");

// Pipe-delimited, percent comment, named columns
DataFrame df = Read.csv("data.txt", "delimiter=|,comment=%,header=a|b|c");

// Semicolon-delimited, single-quoted strings
DataFrame df = Read.csv("data.csv", "delimiter=;,quote='");
```

### 5.4 CSVFormat object API

For full control use Apache Commons CSV's `CSVFormat` builder directly:

```java
import org.apache.commons.csv.CSVFormat;

CSVFormat fmt = CSVFormat.Builder.create()
        .setDelimiter('\t')
        .setHeader()
        .setSkipHeaderRecord(true)
        .setCommentMarker('%')
        .setQuote('"')
        .setNullString("NA")
        .get();

DataFrame df = Read.csv(Path.of("data.tsv"), fmt);
```

### 5.5 Charset

The default charset is **UTF-8**. Override it with the `CSV` class directly:

```java
CSV csv = new CSV();
csv.charset(StandardCharsets.ISO_8859_1);
DataFrame df = csv.read(Path.of("latin1.csv"));
```

### 5.6 Reading a limited number of rows

Useful for previewing large files without loading everything into memory:

```java
CSV csv = new CSV();
DataFrame preview = csv.read(Path.of("bigfile.csv"), 100);  // first 100 rows
```

The schema is inferred from `min(1000, limit)` rows even when a limit is set.

### 5.7 Writing

`Write.csv` always writes a header line followed by data rows, all via
`Tuple.getString(j)` so values are text representations:

```java
// Default: comma-delimited, UTF-8
Write.csv(df, Path.of("output.csv"));

// Tab-delimited
Write.csv(df, Path.of("output.tsv"),
        CSVFormat.Builder.create().setDelimiter('\t').get());
```

To read the file back correctly, use `setHeader().setSkipHeaderRecord(true)`:

```java
CSVFormat readFmt = CSVFormat.Builder.create()
        .setHeader().setSkipHeaderRecord(true).get();
DataFrame restored = Read.csv(Path.of("output.csv"), readFmt);
```

---

## 6. JSON in depth

SMILE reads flat (non-nested) JSON; nested objects are not supported.

### 6.1 Single-line mode

One complete JSON object per line (newline-delimited JSON / NDJSON):

```json
{"id":1,"name":"Alice","score":9.5}
{"id":2,"name":"Bob","score":8.1}
```

```java
JSON json = new JSON();                        // default: SINGLE_LINE
DataFrame df = json.read(Path.of("data.json"));

// Or via Read:
DataFrame df = Read.json("data.json");
DataFrame df = Read.json(Path.of("data.json"));
```

### 6.2 Multi-line mode

The file is a single JSON array of objects:

```json
[
  {"id": 1, "name": "Alice", "score": 9.5},
  {"id": 2, "name": "Bob",   "score": 8.1}
]
```

```java
JSON json = new JSON().mode(JSON.Mode.MULTI_LINE);
DataFrame df = json.read(Path.of("data.json"));

// Or via Read:
DataFrame df = Read.json("data.json", JSON.Mode.MULTI_LINE, null);
DataFrame df = Read.data("data.json", "MULTI_LINE");
```

### 6.3 Schema override

```java
StructType schema = new StructType(
        new StructField("id",    DataTypes.IntType),
        new StructField("name",  DataTypes.StringType),
        new StructField("score", DataTypes.DoubleType)
);
JSON json = new JSON().mode(JSON.Mode.SINGLE_LINE).schema(schema);
DataFrame df = json.read(Path.of("data.json"));
```

---

## 7. ARFF in depth

### 7.1 ARFF format primer

```arff
% Comment lines start with %
@relation iris

@attribute sepallength  NUMERIC
@attribute sepalwidth   NUMERIC
@attribute class        {Iris-setosa, Iris-versicolor, Iris-virginica}

@data
5.1,3.5,Iris-setosa
4.9,3.0,Iris-setosa
```

SMILE supports:

| ARFF type | Java type in DataFrame |
|---|---|
| `NUMERIC` / `REAL` / `INTEGER` | `DoubleType` or `IntType` |
| `STRING` | `StringType` |
| `{val1, val2, …}` (nominal) | `ByteType` with `NominalScale` |
| `DATE [format]` | `DateTimeType` |
| `RELATIONAL` (sub-relation) | Flattened into columns |

Missing values (`?`) are loaded as `null`.

### 7.2 Reading

`Arff` implements `AutoCloseable` — always use it in a `try-with-resources`:

```java
try (Arff arff = new Arff(Path.of("weather.arff"))) {
    String name   = arff.name();    // @relation name
    StructType schema = arff.schema();
    DataFrame df  = arff.read();
    System.out.println(df);
}

// Or use the Read facade (handles close automatically):
DataFrame df = Read.arff("weather.arff");
DataFrame df = Read.arff(Path.of("weather.arff"));
```

Nominal columns are accessed via the `NominalScale` measure:

```java
// Raw byte code (0-based level index)
byte code = df.getByte(0, "class");

// Human-readable label
String label = df.column("class").getScale(0);   // e.g. "Iris-setosa"
```

### 7.3 Writing

```java
// Via Write facade
Write.arff(df, Path.of("output.arff"), "my_relation");

// Via Arff directly
Arff.write(df, Path.of("output.arff"), "my_relation");
```

---

## 8. Apache Arrow in depth

Apache Arrow uses an **IPC Stream** format (also called Feather v2). The file
extension is typically `.feather` or `.arrow`.

```java
// Read
Arrow arrow = new Arrow();
DataFrame df = arrow.read(Path.of("data.feather"));

// Read with a row limit
DataFrame df = arrow.read(Path.of("data.feather"), 10_000);

// Read from URI string
DataFrame df = arrow.read("file:///data/events.feather");

// Write  (default batch = 1 000 000 rows)
Arrow arrow = new Arrow();
arrow.write(df, Path.of("output.feather"));

// Write with custom batch size
Arrow arrow = new Arrow(500_000);
arrow.write(df, Path.of("output.feather"));

// Via Write facade
Write.arrow(df, Path.of("output.feather"));
```

**Type mapping (SMILE → Arrow):**

| SMILE type | Arrow type |
|---|---|
| `IntType` | `Int(32, signed)` |
| `LongType` | `Int(64, signed)` |
| `FloatType` | `FloatingPoint(SINGLE)` |
| `DoubleType` | `FloatingPoint(DOUBLE)` |
| `BooleanType` | `Bool` |
| `ByteType` | `Int(8, signed)` |
| `ShortType` | `Int(16, signed)` |
| `CharType` | `Int(16, unsigned)` |
| `StringType` | `Utf8` |
| `DecimalType` | `Decimal` |
| `DateType` | `Date(DAY)` |
| `TimeType` | `Time(MICROSECOND, 64)` |
| `DateTimeType` | `Timestamp(MICROSECOND)` |
| Nullable variants | Arrow validity bitmap |

---

## 9. Apache Avro in depth

Avro requires an explicit **Avro schema** (`.avsc`) file because the binary
Avro container format stores its own schema, but SMILE needs it upfront to
map Avro field types to SMILE types.

```java
// Constructor options
Avro avro1 = new Avro(schemaInputStream);
Avro avro2 = new Avro(Path.of("user.avsc"));   // reads schema from file

// Read all rows
DataFrame df = avro1.read(Path.of("users.avro"));

// Read with limit
DataFrame df = avro1.read(Path.of("users.avro"), 500);

// Via Read facade
DataFrame df = Read.avro("users.avro", "user.avsc");
DataFrame df = Read.avro(Path.of("users.avro"), Path.of("user.avsc"));
DataFrame df = Read.avro(Path.of("users.avro"), schemaStream);

// auto-dispatch via Read.data
DataFrame df = Read.data("users.avro", "user.avsc");
```

**Supported Avro types:**

| Avro type | SMILE type |
|---|---|
| `int` | `IntType` |
| `long` | `LongType` |
| `float` | `FloatType` |
| `double` | `DoubleType` |
| `boolean` | `BooleanType` |
| `string` / `bytes` | `StringType` |
| `enum` | `ByteType` with `NominalScale` |
| `null` union | Nullable variant of the paired type |

---

## 10. Apache Parquet in depth

Parquet is read via the **Apache Arrow Dataset API**. The path must be a
`file://` URI or a `java.nio.file.Path` (SMILE adds the URI prefix
automatically on all platforms including Windows).

```java
// Recommended: Path overload (SMILE handles URI conversion)
DataFrame df = Read.parquet(Path.of("/data/users.parquet"));

// URI string (must start with "file://")
DataFrame df = Read.parquet("file:///data/users.parquet");

// With row limit
DataFrame df = Parquet.read(Path.of("/data/users.parquet"), 1000);

// via Read.data
String path = Path.of("/data/users.parquet").toAbsolutePath().toString();
if (!path.startsWith("/")) path = "/" + path;   // Windows
DataFrame df = Read.data("file://" + path);
```

> **Note:** Parquet **read** is supported; Parquet **write** is not currently
> implemented in `Write`. Use Apache Arrow Feather as an alternative
> high-performance binary format for round-trips.

---

## 11. SAS7BDAT in depth

SAS files are read via the [Parso](https://github.com/epam/parso) library —
no SAS licence or native binary is required.

```java
// From a Path
DataFrame df = Read.sas(Path.of("airline.sas7bdat"));

// From a URI string
DataFrame df = Read.sas("file:///data/airline.sas7bdat");

// Direct API with limit
InputStream in = Files.newInputStream(Path.of("airline.sas7bdat"));
DataFrame df = SAS.read(in, 100);
```

SAS column types map to `DoubleType` (numeric) and `StringType` (character).
The SAS file's column labels are used as DataFrame column names.

---

## 12. libsvm sparse format in depth

The libsvm format is widely used by the SVM and gradient-boosting communities:

```
<label> <index1>:<value1> <index2>:<value2> ...
1 3:0.5 7:1.2 42:0.3
-1 1:2.0 3:1.0
```

Rules:
- **Label** is an integer class identifier (or any integer for regression).
- **Indices** are **1-based** integers in ascending order.
  SMILE converts them to 0-based internally.
- **Values** are floating-point numbers.
- An index of `0` in the file is illegal and throws `NumberFormatException`.
- A token without exactly one `:` is also illegal and throws
  `NumberFormatException`.

```java
// Read
SparseDataset<Integer> train = Read.libsvm(Path.of("train.dat"));
SparseDataset<Integer> test  = Read.libsvm(Path.of("test.dat"));

// Inspect
System.out.printf("rows=%d  cols=%d  nnz=%d%n",
        train.size(), train.ncol(), train.nz());

// Access a sample
SampleInstance<SparseArray, Integer> s = train.get(0);
int    label   = s.y();
double feature = s.x().get(6);   // 0-based column index

// Iterate non-zero entries
for (SparseArray.Entry e : s.x()) {
    System.out.printf("  col=%d  val=%.4f%n", e.index(), e.value());
}

// Convert to Harwell-Boeing CCS matrix for linear algebra
SparseMatrix X = train.toMatrix();
```

> **There is no `Write.libsvm`** in SMILE — if you need to write libsvm files,
> iterate over the `SparseDataset` and format the lines yourself.

---

## 13. CacheFiles — downloading remote datasets

`CacheFiles` downloads remote files to a platform-specific local cache
directory and avoids repeated downloads:

| Platform | Cache directory |
|---|---|
| Windows | `%LocalAppData%\smile\` |
| macOS | `~/Library/Caches/smile/` |
| Linux/other | `~/.cache/smile/` |

Override with the environment variable **`SMILE_CACHE`**.

```java
import smile.io.CacheFiles;

// Download once, cache forever
Path local = CacheFiles.download(
        "https://archive.ics.uci.edu/ml/machine-learning-databases/iris/iris.data");

// Force re-download even if the file already exists locally
Path local = CacheFiles.download(url, true);

// Find out where the cache lives
String cacheDir = CacheFiles.dir();

// Delete all cached files
CacheFiles.clean();
```

After downloading, read the file with any `Read.*` method:

```java
Path iris = CacheFiles.download("https://example.com/iris.csv");
DataFrame df = Read.csv(iris);
```

---

## 14. Paths — test data helper

`Paths` is a utility used primarily in tests to locate bundled resource files
without hard-coding absolute paths:

```java
import smile.io.Paths;

// Resolve a test-data file relative to smile.home
// Default: base/src/test/resources/data/
Path p = Paths.getTestData("regression/iris.csv");

// Get a BufferedReader directly
BufferedReader r = Paths.getTestDataReader("weka/weather.arff");

// Stream all lines
Stream<String> lines = Paths.getTestDataLines("libsvm/glass.txt");

// Extract the file name without extension
String stem = Paths.getFileName(p);            // "iris"

// Extract the file extension (lower-case)
String ext  = Paths.getFileExtension(p);       // "csv"

// Heuristically detect binary content
boolean binary = Paths.isBinary(p);
```

Override the test-data root with the system property:
```
-Dsmile.home=/my/data/root/
```

---

## 15. End-to-end tutorials

### 15.1 Load, clean, and save a CSV pipeline

```java
import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.data.type.*;
import smile.io.*;

// --- 1. Load with explicit schema and tab delimiter ---
StructType schema = new StructType(
        new StructField("lcavol",  DataTypes.DoubleType),
        new StructField("lweight", DataTypes.DoubleType),
        new StructField("age",     DataTypes.IntType),
        new StructField("lbph",    DataTypes.DoubleType),
        new StructField("svi",     DataTypes.IntType),
        new StructField("lcp",     DataTypes.DoubleType),
        new StructField("gleason", DataTypes.IntType),
        new StructField("pgg45",   DataTypes.IntType),
        new StructField("lpsa",    DataTypes.DoubleType)
);

CSVFormat fmt = CSVFormat.Builder.create()
        .setDelimiter('\t')
        .setHeader()
        .setSkipHeaderRecord(true)
        .get();

DataFrame train = Read.csv(Path.of("prostate-train.csv"), fmt, schema);
DataFrame test  = Read.csv(Path.of("prostate-test.csv"),  fmt, schema);

System.out.printf("train: %d × %d%n", train.nrow(), train.ncol());

// --- 2. Drop rows with any missing values ---
train = train.dropna();

// --- 3. Add a derived column ---
train = train.add("age2", DataTypes.IntType,
        row -> row.getInt("age") * row.getInt("age"));

// --- 4. Save as CSV (with header) ---
Write.csv(train, Path.of("prostate-clean.csv"));

// --- 5. Round-trip: read the saved file back ---
DataFrame restored = Read.csv(
        Path.of("prostate-clean.csv"),
        CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(true).get());
System.out.printf("restored: %d × %d%n", restored.nrow(), restored.ncol());
```

### 15.2 Cross-format conversion

Convert a Parquet file to Arrow Feather for use by a downstream SMILE model:

```java
import smile.data.DataFrame;
import smile.io.*;

// Read Parquet
DataFrame df = Read.parquet(Path.of("/data/userdata1.parquet"));
System.out.printf("parquet: %d rows, %d cols%n", df.nrow(), df.ncol());
System.out.println(df.schema());

// Write Arrow Feather
Write.arrow(df, Path.of("/data/userdata1.feather"));

// Round-trip verification
DataFrame df2 = Read.arrow(Path.of("/data/userdata1.feather"));
assert df.nrow() == df2.nrow();
assert df.ncol() == df2.ncol();
```

### 15.3 Training a model from libsvm data

```java
import smile.data.SparseDataset;
import smile.io.*;
import smile.tensor.SparseMatrix;

// Load training and test sets
SparseDataset<Integer> train = Read.libsvm(Path.of("news20.dat"));
SparseDataset<Integer> test  = Read.libsvm(Path.of("news20.t.dat"));

System.out.printf("train: %d samples, %d features, %d nnz%n",
        train.size(), train.ncol(), train.nz());

// L2 normalize each row for cosine similarity classifiers
train.unitize();
test.unitize();

// Convert to Harwell-Boeing CCS for linear algebra / SVM solvers
SparseMatrix X_train = train.toMatrix();

// Extract labels
int[] y_train = train.stream().mapToInt(s -> s.y()).toArray();
int[] y_test  = test.stream().mapToInt(s -> s.y()).toArray();

// … feed X_train and y_train into a SMILE classifier …
```

### 15.4 Downloading and caching a remote dataset

```java
import smile.data.DataFrame;
import smile.io.*;
import java.nio.file.Path;

// Download the UCI Iris dataset once; use local cache on subsequent runs
Path iris = CacheFiles.download(
        "https://archive.ics.uci.edu/ml/machine-learning-databases/iris/iris.data");

// Define schema (file has no header)
StructType schema = new StructType(
        new StructField("sepal_length", DataTypes.DoubleType),
        new StructField("sepal_width",  DataTypes.DoubleType),
        new StructField("petal_length", DataTypes.DoubleType),
        new StructField("petal_width",  DataTypes.DoubleType),
        new StructField("class",        DataTypes.StringType)
);

CSV csv = new CSV();
csv.schema(schema);
DataFrame df = csv.read(iris);

System.out.printf("Loaded: %d rows × %d cols%n", df.nrow(), df.ncol());
System.out.println(df.head(5));
```

---

## 16. API quick reference

### Read (static methods)

| Method | Returns | Description                        |
|---|---|------------------------------------|
| `Read.data(String)` | `DataFrame` | Auto-dispatch by file extension    |
| `Read.data(String, String)` | `DataFrame` | Auto-dispatch with format hint     |
| `Read.csv(String)` | `DataFrame` | CSV with default format            |
| `Read.csv(String, String)` | `DataFrame` | CSV with key=value format string   |
| `Read.csv(String, CSVFormat)` | `DataFrame` | CSV with explicit format           |
| `Read.csv(String, CSVFormat, StructType)` | `DataFrame` | CSV with format + schema           |
| `Read.csv(Path)` | `DataFrame` | CSV from Path                      |
| `Read.csv(Path, CSVFormat)` | `DataFrame` | CSV from Path + format             |
| `Read.csv(Path, CSVFormat, StructType)` | `DataFrame` | CSV from Path + format + schema    |
| `Read.json(String)` | `DataFrame` | JSON single-line                   |
| `Read.json(String, Mode, StructType)` | `DataFrame` | JSON with mode + schema            |
| `Read.json(Path)` | `DataFrame` | JSON from Path                     |
| `Read.json(Path, Mode, StructType)` | `DataFrame` | JSON from Path + mode + schema     |
| `Read.arff(String)` | `DataFrame` | ARFF from string path/URI          |
| `Read.arff(Path)` | `DataFrame` | ARFF from Path                     |
| `Read.sas(String)` | `DataFrame` | SAS7BDAT from string path/URI      |
| `Read.sas(Path)` | `DataFrame` | SAS7BDAT from Path                 |
| `Read.arrow(String)` | `DataFrame` | Arrow/Feather from string path/URI |
| `Read.arrow(Path)` | `DataFrame` | Arrow/Feather from Path            |
| `Read.avro(String, String)` | `DataFrame` | Avro + schema path                 |
| `Read.avro(String, InputStream)` | `DataFrame` | Avro + schema stream               |
| `Read.avro(Path, Path)` | `DataFrame` | Avro from Path + schema Path       |
| `Read.avro(Path, InputStream)` | `DataFrame` | Avro from Path + schema stream     |
| `Read.parquet(String)` | `DataFrame` | Parquet from `file://` URI         |
| `Read.parquet(Path)` | `DataFrame` | Parquet from Path                  |
| `Read.libsvm(String)` | `SparseDataset<Integer>` | libsvm from string path/URI        |
| `Read.libsvm(Path)` | `SparseDataset<Integer>` | libsvm from Path                   |
| `Read.libsvm(BufferedReader)` | `SparseDataset<Integer>` | libsvm from reader                 |
| `Read.object(Path)` | `Object` | Deserialize a Java object         |

### Write (static methods)

| Method | Description                             |
|---|-----------------------------------------|
| `Write.csv(DataFrame, Path)` | CSV with default format                 |
| `Write.csv(DataFrame, Path, CSVFormat)` | CSV with explicit format                |
| `Write.arrow(DataFrame, Path)` | Arrow/Feather                           |
| `Write.arff(DataFrame, Path, String)` | ARFF with relation name                 |
| `Write.object(Serializable)` | Serialize to a temp file (auto-deleted) |
| `Write.object(Serializable, Path)` | Serialize to a specific file           |

### CSV (instance methods)

| Method | Description |
|---|---|
| `new CSV()` | Default comma-separated format |
| `new CSV(CSVFormat)` | Custom format |
| `csv.schema(StructType)` | Override schema (fluent) |
| `csv.charset(Charset)` | Set charset (fluent) |
| `csv.read(String)` | Read all rows from string path |
| `csv.read(String, int)` | Read at most N rows |
| `csv.read(Path)` | Read all rows from Path |
| `csv.read(Path, int)` | Read at most N rows |
| `csv.inferSchema(Reader, int)` | Infer schema from first N rows |
| `csv.write(DataFrame, Path)` | Write to Path |

### JSON (instance methods)

| Method | Description |
|---|---|
| `new JSON()` | Default UTF-8 single-line |
| `json.schema(StructType)` | Override schema (fluent) |
| `json.charset(Charset)` | Set charset (fluent) |
| `json.mode(Mode)` | `SINGLE_LINE` or `MULTI_LINE` (fluent) |
| `json.read(Path)` | Read all objects |
| `json.read(Path, int)` | Read at most N objects |
| `json.read(String)` | Read from string path/URI |
| `json.read(String, int)` | Read at most N objects |

### Arff (instance methods)

| Method | Description |
|---|---|
| `new Arff(String)` | Open from string path/URI |
| `new Arff(Path)` | Open from Path |
| `new Arff(Reader)` | Open from Reader |
| `arff.name()` | @relation name |
| `arff.schema()` | Parsed `StructType` |
| `arff.read()` | Read all data rows |
| `arff.close()` | Close underlying reader |
| `Arff.write(df, Path, String)` | Static write method |

### Input (static methods)

| Method | Description |
|---|---|
| `Input.stream(String)` | `InputStream` for path or URI |
| `Input.reader(String)` | `BufferedReader` (UTF-8) |
| `Input.reader(String, Charset)` | `BufferedReader` with charset |

### CacheFiles (static methods)

| Method | Description |
|---|---|
| `CacheFiles.dir()` | Return cache directory path |
| `CacheFiles.download(String)` | Download URL to cache (skip if exists) |
| `CacheFiles.download(String, boolean)` | Download URL; force=true re-downloads |
| `CacheFiles.clean()` | Delete all cached files |


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

