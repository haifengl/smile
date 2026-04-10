# SMILE — DataFrame User Guide & Tutorial

This document covers `smile.data.DataFrame`, `smile.data.Tuple`, and the
supporting packages `smile.data.type`, `smile.data.measure`, and
`smile.data.vector`.

---

## Table of Contents

1. [Architecture overview](#1-architecture-overview)
2. [Data types — `smile.data.type`](#2-data-types--smileddatatype)
   - [Primitive and nullable types](#21-primitive-and-nullable-types)
   - [Temporal types](#22-temporal-types)
   - [Other types](#23-other-types)
   - [StructField](#24-structfield)
   - [StructType](#25-structtype)
3. [Levels of measurement — `smile.data.measure`](#3-levels-of-measurement--smiledatameasure)
   - [NominalScale](#31-nominalscale)
   - [OrdinalScale](#32-ordinalscale)
   - [IntervalScale](#33-intervalscale)
   - [RatioScale](#34-ratioscale)
4. [Column vectors — `smile.data.vector`](#4-column-vectors--smiledatavector)
   - [Primitive vectors](#41-primitive-vectors)
   - [Nullable primitive vectors](#42-nullable-primitive-vectors)
   - [Object vectors](#43-object-vectors)
   - [Common ValueVector operations](#44-common-valuevector-operations)
5. [Tuple — a single row](#5-tuple--a-single-row)
6. [DataFrame](#6-dataframe)
   - [Creating a DataFrame](#61-creating-a-dataframe)
   - [Inspecting a DataFrame](#62-inspecting-a-dataframe)
   - [Accessing cells, rows, and columns](#63-accessing-cells-rows-and-columns)
   - [Row indexing with RowIndex](#64-row-indexing-with-rowindex)
   - [Selecting and dropping columns](#65-selecting-and-dropping-columns)
   - [Adding and replacing columns](#66-adding-and-replacing-columns)
   - [Renaming columns](#67-renaming-columns)
   - [Filtering rows](#68-filtering-rows)
   - [Sorting](#69-sorting)
   - [Slicing and sampling](#610-slicing-and-sampling)
   - [Combining DataFrames](#611-combining-dataframes)
   - [Missing values](#612-missing-values)
   - [Categorical encoding with factorize](#613-categorical-encoding-with-factorize)
   - [Exporting to numeric arrays and matrices](#614-exporting-to-numeric-arrays-and-matrices)
   - [Statistics with describe](#615-statistics-with-describe)
   - [Printing and display](#616-printing-and-display)
   - [Loading from JDBC](#617-loading-from-jdbc)
7. [CategoricalEncoder](#7-categoricalencoder)
8. [End-to-end tutorial](#8-end-to-end-tutorial)
9. [API quick reference](#9-api-quick-reference)

---

## 1. Architecture overview

```
smile.data.type
│
├── DataType (interface)          – describes storage format
│     ├── Primitive types         – BooleanType, ByteType, ShortType,
│     │                             IntType, LongType, FloatType, DoubleType,
│     │                             CharType  (each has nullable=false/true)
│     ├── TemporalType            – DateType, TimeType, DateTimeType
│     ├── DecimalType             – BigDecimal
│     ├── StringType              – String
│     ├── ObjectType              – arbitrary Object
│     ├── ArrayType               – primitive array columns
│     └── StructType              – schema (ordered list of StructFields)
│
├── StructField(name, dtype, measure?)  – one column descriptor
└── DataTypes                     – constants and factory methods

smile.data.measure
│
├── Measure (interface)           – adds semantic meaning to a numeric column
│     ├── CategoricalMeasure      – discrete labeled integer codes
│     │     ├── NominalScale      – unordered categories (gender, country…)
│     │     └── OrdinalScale      – ordered categories (rating, grade…)
│     └── NumericalMeasure        – continuous numeric annotations
│           ├── IntervalScale     – no true zero (temperature °C, year)
│           └── RatioScale        – true zero (price, weight, count)
└── Measure.Currency / Percent    – built-in ratio-scale singletons

smile.data.vector
│
├── ValueVector (interface)       – one typed column of a DataFrame
│     ├── PrimitiveVector         – non-nullable: IntVector, DoubleVector…
│     ├── NullablePrimitiveVector – nullable: NullableIntVector…
│     ├── StringVector            – String column (always nullable-compatible)
│     ├── NumberVector<N>         – boxed Number column (BigDecimal…)
│     └── ObjectVector<T>         – arbitrary Object column

smile.data
│
├── Tuple (interface)             – one row; immutable ordered field list
│     └── Row (record)            – Tuple backed by a DataFrame + row index
│
├── RowIndex                      – optional label → ordinal index for rows
│
└── DataFrame (record)            – 2-D heterogeneous tabular data
      schema    : StructType
      columns   : List<ValueVector>
      index     : RowIndex?        (optional row labels)
```

---

## 2. Data types — `smile.data.type`

Every column in a `DataFrame` has a `DataType` that describes how values
are stored.  Access the singletons via `DataTypes.*`:

```java
import smile.data.type.DataTypes;
import smile.data.type.DataType;
```

### 2.1 Primitive and nullable types

| Singleton | Java type | Nullable variant |
|-----------|-----------|-----------------|
| `DataTypes.BooleanType` | `boolean` | `DataTypes.NullableBooleanType` |
| `DataTypes.ByteType` | `byte` | `DataTypes.NullableByteType` |
| `DataTypes.ShortType` | `short` | `DataTypes.NullableShortType` |
| `DataTypes.IntType` | `int` | `DataTypes.NullableIntType` |
| `DataTypes.LongType` | `long` | `DataTypes.NullableLongType` |
| `DataTypes.FloatType` | `float` | `DataTypes.NullableFloatType` |
| `DataTypes.DoubleType` | `double` | `DataTypes.NullableDoubleType` |
| `DataTypes.CharType` | `char` | `DataTypes.NullableCharType` |

**Non-nullable** primitive types store values directly in a primitive array —
no boxing, no null overhead.  **Nullable** variants add a `BitSet` null-mask
alongside the primitive array; reading a null cell via the primitive accessor
(`getDouble`, `getInt`, …) returns `Double.NaN` / `Integer.MIN_VALUE` as the
sentinel; always use `isNullAt(i)` first when the column is nullable.

```java
boolean isNull = df.isNullAt(row, col);
if (!isNull) {
    double v = df.getDouble(row, col);
}
```

### 2.2 Temporal types

| Singleton | Java type |
|-----------|-----------|
| `DataTypes.DateType` | `java.time.LocalDate` |
| `DataTypes.TimeType` | `java.time.LocalTime` |
| `DataTypes.DateTimeType` | `java.time.LocalDateTime` |

Temporal columns are always represented as object arrays (they are not
primitives), but SMILE provides ISO-8601 parsing and formatting out of the
box via `dtype.valueOf(String)` and `dtype.toString(Object)`.

### 2.3 Other types

| Singleton / factory | Java type | Notes |
|--------------------|-----------|-------|
| `DataTypes.StringType` | `String` | Always nullable |
| `DataTypes.DecimalType` | `BigDecimal` | Always nullable |
| `DataTypes.ObjectType` | `Object` | Catch-all |
| `DataTypes.object(Class<?>)` | specific class | Resolves to known type if possible |
| `DataTypes.IntArrayType` etc. | `int[]` etc. | Primitive array columns |

### 2.4 StructField

A `StructField` is an immutable triple `(name, dtype, measure?)` that
describes a single column.

```java
import smile.data.type.StructField;

// Plain double column
StructField age     = new StructField("age",    DataTypes.IntType);

// Nullable salary
StructField salary  = new StructField("salary", DataTypes.NullableDoubleType);

// Categorical column with a NominalScale
StructField gender  = new StructField("gender", DataTypes.ByteType,
                                      new NominalScale("Male", "Female"));

// Useful predicates
boolean numeric = age.isNumeric();      // true for non-nominal numeric
boolean nullable = salary.dtype().isNullable();
```

`StructField` is a Java `record`; two fields are equal when name, dtype, and
measure all match.

### 2.5 StructType

`StructType` is the schema of a `DataFrame` or `Tuple`.  It is an ordered
list of `StructField`s with a fast name-to-index lookup map.

```java
import smile.data.type.StructType;

StructType schema = new StructType(
    new StructField("name",   DataTypes.StringType),
    new StructField("age",    DataTypes.IntType),
    new StructField("salary", DataTypes.NullableDoubleType)
);

// Field access
StructField f = schema.field("age");    // by name
StructField f = schema.field(1);        // by ordinal

int j         = schema.indexOf("age");  // ordinal of "age"
String[]  ns  = schema.names();
DataType[] dt = schema.dtypes();
int       len = schema.length();
```

`StructType` is **mutable** for internal use by `DataFrame` (columns can be
added/renamed in-place).  Do not cache a `StructType` reference and assume
it is immutable.

---

## 3. Levels of measurement — `smile.data.measure`

A `Measure` is an optional annotation on a `StructField` that adds semantic
meaning to a numeric column.  It controls how values are rendered and how
SMILE's algorithms treat the column (e.g., dummy encoding for categorical
variables).

```java
import smile.data.measure.*;
```

### 3.1 NominalScale

Unordered categories.  Each integer code maps to a string label.

```java
// From string labels (codes are 0, 1, 2, …)
NominalScale gender = new NominalScale("Male", "Female");

// From an enum
NominalScale color = new NominalScale(Color.class); // enum Color { Red, Green, Blue }

// From explicit code→label pairs
NominalScale custom = new NominalScale(
    new int[]    {1, 3, 7},
    new String[] {"Low", "Mid", "High"}
);

int    code  = gender.valueOf("Female").intValue(); // 1
String label = gender.level(0);                    // "Male"
int    size  = gender.size();                      // 2
```

When a column has a `NominalScale`, `getString(i)` returns the label, not
the raw integer, and `df.factorize()` will produce columns backed by a
`NominalScale`.

### 3.2 OrdinalScale

Ordered categories.  Levels carry an implied rank; values are kept sorted.

```java
OrdinalScale rating = new OrdinalScale("Poor", "Fair", "Good", "Excellent");
// ordinals: 0=Poor, 1=Fair, 2=Good, 3=Excellent
```

The ordinal position is meaningful for comparison and sorting but
arithmetic (mean, variance) is not valid on pure ordinal data.

### 3.3 IntervalScale

Numeric, no true zero.  Arithmetic differences are meaningful, ratios are
not.  Primarily used as an annotation for documentation/display.

```java
IntervalScale celsius = new IntervalScale(NumberFormat.getInstance());
```

### 3.4 RatioScale

Numeric with a true zero.  All arithmetic is valid.

```java
// Built-in singletons
Measure price  = Measure.Currency;   // formats as currency
Measure pct    = Measure.Percent;    // formats as percentage

// Custom
RatioScale weight = new RatioScale(NumberFormat.getInstance());
```

**Compatibility rules** (enforced by `StructField`'s compact constructor):
- `NumericalMeasure` is invalid for `Boolean`, `Char`, `String` columns.
- `CategoricalMeasure` is only valid for integral (`int`, `long`, `byte`,
  `short`) columns.

---

## 4. Column vectors — `smile.data.vector`

A `ValueVector` is a typed, indexed, one-dimensional array that forms a
single column of a `DataFrame`.

```java
import smile.data.vector.*;
```

### 4.1 Primitive vectors

Each primitive type has a corresponding non-nullable vector class:

| Class | Backing store | Example |
|-------|---------------|---------|
| `IntVector` | `int[]` | `new IntVector("age", new int[]{25, 30, 35})` |
| `LongVector` | `long[]` | `new LongVector("ts", new long[]{...})` |
| `FloatVector` | `float[]` | `new FloatVector("score", new float[]{...})` |
| `DoubleVector` | `double[]` | `new DoubleVector("salary", new double[]{...})` |
| `BooleanVector` | `boolean[]` | `new BooleanVector("flag", new boolean[]{...})` |
| `ByteVector` | `byte[]` | `new ByteVector("cat", new byte[]{...})` |
| `ShortVector` | `short[]` | `new ShortVector("rank", new short[]{...})` |
| `CharVector` | `char[]` | `new CharVector("grade", new char[]{...})` |

All take an optional `StructField` as the first argument when a measure is
needed:

```java
StructField field = new StructField("gender", DataTypes.ByteType,
                                    new NominalScale("Male", "Female"));
ByteVector gender = new ByteVector(field, new byte[]{0, 1, 0, 1});
```

### 4.2 Nullable primitive vectors

Nullable variants store an additional `BitSet` null-mask:

```java
import java.util.BitSet;

double[] values   = {80000.0, Double.NaN, 90000.0};
BitSet   nullMask = new BitSet(3);
nullMask.set(1);  // index 1 is null

NullableDoubleVector salary = new NullableDoubleVector(
    new StructField("salary", DataTypes.NullableDoubleType),
    values, nullMask
);

salary.isNullable();     // true
salary.isNullAt(1);      // true
salary.getDouble(0);     // 80000.0
salary.getNullCount();   // 1
```

Corresponding nullable classes: `NullableIntVector`, `NullableLongVector`,
`NullableFloatVector`, `NullableDoubleVector`, `NullableBooleanVector`,
`NullableByteVector`, `NullableShortVector`, `NullableCharVector`.

### 4.3 Object vectors

| Class | Content |
|-------|---------|
| `StringVector` | `String[]` — always nullable |
| `NumberVector<N>` | `Number[]` — `BigDecimal`, boxed primitives |
| `ObjectVector<T>` | `Object[]` — `LocalDate`, `LocalDateTime`, any type |

```java
StringVector  names = new StringVector("name",  new String[]{"Alice","Bob"});
ObjectVector<LocalDate> dates = new ObjectVector<>(
    new StructField("birthday", DataTypes.DateType),
    new LocalDate[]{ LocalDate.of(1990,1,1), LocalDate.of(1985,6,15) }
);
```

### 4.4 Common ValueVector operations

```java
ValueVector v = df.column("age");

// Size and nullability
int n  = v.size();
boolean nullable = v.isNullable();
boolean hasNull  = v.anyNull();
int nullCount    = v.getNullCount();
boolean rowNull  = v.isNullAt(2);

// Typed reads
int    i = v.getInt(0);
double d = v.getDouble(0);
String s = v.getString(0);       // uses measure for categorical columns
Object o = v.get(0);             // boxed / object value, may be null

// Bulk export
int[]    ia = v.toIntArray();
double[] da = v.toDoubleArray();
String[] sa = v.toStringArray();

// Streaming
v.intStream().sum();
v.doubleStream().average();
v.stream().filter(Objects::nonNull).count();

// Boolean filter masks
boolean[] mask = v.eq(30);       // element-wise ==
boolean[] mask = v.gt(25);       // element-wise >
boolean[] mask = v.isin(25, 30); // element-wise membership

// Sub-selection
ValueVector sub = v.get(Index.of(new int[]{0, 2, 3}));

// Rename (returns new vector)
ValueVector renamed = v.withName("years");

// In-place mutation
v.set(0, 99);
```

---

## 5. Tuple — a single row

`Tuple` is an interface representing one row of a `DataFrame`.  It is
**immutable** from the user's perspective — mutating a `Row` (the concrete
implementation) directly modifies the backing `DataFrame`.

### Creating a Tuple

```java
import smile.data.Tuple;
import smile.data.type.StructType;

// Construct standalone from schema + values
StructType schema = new StructType(
    new StructField("x", DataTypes.IntType),
    new StructField("y", DataTypes.DoubleType)
);
Tuple t = Tuple.of(schema, new Object[]{42, 3.14});
Tuple t = Tuple.of(schema, new int[]{42}, new double[]{3.14});
```

### Reading fields

All accessors have both ordinal (`int i`) and name (`String field`) forms:

```java
Tuple row = df.get(0);

// By ordinal
int    age    = row.getInt(0);
double salary = row.getDouble(2);
String name   = row.getString(1);

// By name
int    age    = row.getInt("age");
double salary = row.getDouble("salary");

// Generic (boxed, may return null)
Object val = row.get(0);
Object val = row.get("name");

// Null check — always check before primitive accessors on nullable columns
boolean isNull = row.isNullAt(2);
boolean isNull = row.isNullAt("salary");
boolean anyNull = row.anyNull();
```

Available typed accessors: `getBoolean`, `getChar`, `getByte`, `getShort`,
`getInt`, `getLong`, `getFloat`, `getDouble`, `getString`.

### Exporting to a double array

```java
// All fields as doubles (NaN for nulls, level encoding for categoricals)
double[] arr = row.toArray();

// Selective fields
double[] arr = row.toArray("age", "salary");

// With intercept (bias=1) and dummy encoding
double[] arr = row.toArray(true, CategoricalEncoder.DUMMY, "age", "gender", "salary");
```

### Schema access

```java
StructType schema  = row.schema();
int        length  = row.length();
int        j       = row.indexOf("salary");
```

---

## 6. DataFrame

`DataFrame` is a Java `record` — immutable value by reference — backed by:
- `StructType schema` — column descriptors
- `List<ValueVector> columns` — the actual data, one vector per column
- `RowIndex index` — optional row labels (may be `null`)

The `List<ValueVector>` and `StructType` are **mutable** for the in-place
operations `add()`, `set()`, `rename()`, and `fillna()`.  All other
operations that structurally reshape the data (e.g., `select`, `drop`,
`sort`, `concat`) return a **new** `DataFrame`.

```java
import smile.data.DataFrame;
```

### 6.1 Creating a DataFrame

#### From column vectors (most direct)

```java
DataFrame df = new DataFrame(
    new StringVector ("name",   new String[] {"Alice", "Bob", "Charlie"}),
    new IntVector    ("age",    new int[]    {25, 30, 35}),
    new DoubleVector ("salary", new double[] {60000., 80000., 90000.})
);
```

#### From a 2-D double / float / int array

```java
double[][] data = {{1.0, 2.0}, {3.0, 4.0}, {5.0, 6.0}};

// Auto-named columns: V1, V2
DataFrame df = DataFrame.of(data);

// Custom names
DataFrame df = DataFrame.of(data, "x", "y");
DataFrame df = DataFrame.of(new int[][]{{1,2},{3,4}}, "a", "b");
DataFrame df = DataFrame.of(new float[][]{{1f},{2f}}, "v");
```

#### From a Java Bean / POJO via reflection

SMILE uses Java Beans introspection (`getXxx()` methods) to discover
columns automatically. Field order in the schema follows the alphabetical
order of getter names.

```java
public class Person {
    public String getName()     { return name; }
    public int    getAge()      { return age;  }
    public Double getSalary()   { return salary; }   // nullable wrapper → NullableDoubleType
    public Gender getGender()   { return gender; }   // enum → ByteVector with NominalScale
    // …
}

List<Person> persons = List.of(
    new Person("Alice", 25, 60000., Gender.Female),
    new Person("Bob",   30, null,   Gender.Male)
);
DataFrame df = DataFrame.of(Person.class, persons);
```

**Rules for automatic type inference:**
- `int` / `long` / `float` / `double` / `boolean` / `char` / `byte` / `short`
  → non-nullable primitive vector
- Boxed `Integer`, `Double`, etc. and `Number` subclasses → nullable vector
  or `NumberVector`
- `String` → `StringVector`
- Enum → `ByteVector` (≤127 levels), `ShortVector` (≤32767), or `IntVector`;
  always annotated with a `NominalScale`
- `LocalDate`, `LocalDateTime`, `LocalTime` → `ObjectVector` with temporal type
- Anything else → `ObjectVector`

#### From a schema + list/stream of Tuples

```java
StructType schema = new StructType(
    new StructField("x", DataTypes.IntType),
    new StructField("y", DataTypes.DoubleType)
);

// Non-empty list
List<Tuple> rows = List.of(Tuple.of(schema, 1, 2.0), Tuple.of(schema, 3, 4.0));
DataFrame df = DataFrame.of(schema, rows);

// Stream variant
DataFrame df = DataFrame.of(schema, rows.stream());

// Empty list — returns a zero-row DataFrame with the correct schema
DataFrame empty = DataFrame.of(schema, List.of());
```

#### From a JDBC ResultSet

```java
try (Connection conn = DriverManager.getConnection(url);
     Statement  stmt = conn.createStatement();
     ResultSet  rs   = stmt.executeQuery("SELECT * FROM employees")) {
    DataFrame df = DataFrame.of(rs);
}
```

JDBC type mapping is handled automatically via `StructType.of(ResultSet)`.

### 6.2 Inspecting a DataFrame

```java
// Dimensions
int nrow = df.nrow();          // number of rows
int ncol = df.ncol();          // number of columns
int size = df.size();          // alias for nrow()
int[] shape = {df.shape(0), df.shape(1)};  // {nrow, ncol}
boolean empty = df.isEmpty();

// Schema
StructType schema   = df.schema();
String[]   names    = df.names();
DataType[] dtypes   = df.dtypes();
Measure[]  measures = df.measures();

// Print
System.out.println(df);          // head(10)
System.out.println(df.head(5));  // first 5 rows
System.out.println(df.tail(5));  // last 5 rows
System.out.println(df.toString(2, 7, true));  // rows [2,7)

// Summary statistics
System.out.println(df.describe());
```

`describe()` returns a new `DataFrame` with one row per column and columns:
`column`, `type`, `measure`, `count` (non-null), `mode`, `mean`, `std`,
`min`, `25%`, `50%`, `75%`, `max`.

### 6.3 Accessing cells, rows, and columns

#### Cells

```java
Object  val = df.get(row, col);           // boxed value, may be null
int     i   = df.getInt(row, col);
long    l   = df.getLong(row, col);
float   f   = df.getFloat(row, col);
double  d   = df.getDouble(row, col);
String  s   = df.getString(row, col);     // uses measure for categoricals
String  sc  = df.getScale(row, col);      // level name for NominalScale / OrdinalScale
boolean nil = df.isNullAt(row, col);
```

#### Rows (Tuples)

```java
Tuple row = df.get(0);                    // first row
Tuple row = df.apply(0);                  // Scala alias

// Iterate all rows
for (Row row : df) { /* … */ }
df.stream().forEach(row -> /* … */);
List<Row> list = df.toList();
```

#### Columns

```java
ValueVector col = df.column(0);           // by ordinal
ValueVector col = df.column("age");       // by name
ValueVector col = df.apply("age");        // Scala alias
```

#### Mutating a cell

```java
df.set(row, col, value);
df.update(row, col, value);               // Scala alias
```

### 6.4 Row indexing with RowIndex

A `RowIndex` maps arbitrary label objects to row ordinals, allowing
label-based row selection similar to Pandas `.loc`.

```java
// Attach an index from an existing column (column is removed from data)
DataFrame indexed = df.setIndex("name");

// Attach an index from an external array
DataFrame indexed = df.setIndex(new Object[]{"r0","r1","r2","r3"});

// Look up by label
Tuple row  = indexed.loc("Alice");          // single row
DataFrame sub = indexed.loc("Alice","Bob"); // multiple rows
```

`RowIndex` is also used internally by `join()` to perform an inner join on
shared keys.

```java
// RowIndex directly
RowIndex index = new RowIndex(new Object[]{"a","b","c"});

int  i   = index.apply("b");             // 1
int  i   = index.getOrDefault("x");     // -1 (not found)
boolean has = index.containsKey("a");   // true
int  n   = index.size();                // 3
```

**Constraints:** no null values, no duplicate values — both throw
`IllegalArgumentException` at construction time.

### 6.5 Selecting and dropping columns

All selection/drop operations return a **new** `DataFrame`.

```java
// Select by ordinal indices
DataFrame sub = df.select(0, 2);

// Select by name
DataFrame sub = df.select("name", "salary");
DataFrame sub = df.apply("name", "salary");   // Scala alias

// Drop by ordinal index
DataFrame sub = df.drop(1);
DataFrame sub = df.drop(0, 2);

// Drop by name
DataFrame sub = df.drop("age");
DataFrame sub = df.drop("age", "birthday");
```

### 6.6 Adding and replacing columns

`add()` and `set()` **mutate `this` in-place** (they modify the internal
`List<ValueVector>` and `StructType`).

```java
// Add new columns — all must have the same size as the DataFrame
// and names must not clash with existing columns or each other
IntVector bonus = new IntVector("bonus", new int[]{5000, 8000, 12000});
df.add(bonus);

// Two columns at once — both names must be distinct from each other
// and from existing columns
df.add(c1, c2);

// Replace an existing column (or add if not present)
df.set("salary", updatedSalaryVector);
df.update("salary", updatedSalaryVector);  // Scala alias
```

### 6.7 Renaming columns

`rename()` mutates both the `StructType` and the backing `ValueVector`
in-place:

```java
df.rename("age", "years");
// df.names() is now ["name", "years", "salary"]
```

### 6.8 Filtering rows

All row-selection operations return a new `DataFrame`.

#### Boolean mask

```java
// Build a boolean mask manually or from a column comparison
boolean[] mask = df.column("age").gt(28);
DataFrame sub  = df.get(mask);
```

#### Index object

```java
import smile.util.Index;

// From explicit row indices
DataFrame sub = df.get(Index.of(new int[]{0, 2, 3}));

// From a boolean mask
DataFrame sub = df.get(Index.of(new boolean[]{true, false, true, true}));
```

#### dropna

```java
// Remove any row that has at least one null/NaN value
DataFrame clean = df.dropna();
```

### 6.9 Sorting

`sort()` returns a new `DataFrame` with all rows reordered.  Null values
always sort to the end regardless of direction.

```java
// Ascending (default)
DataFrame sorted = df.sort("age");

// Descending
DataFrame sorted = df.sort("salary", false);
```

The sort is stable and works on any column type: integral, floating-point,
`String`, and any `Comparable`.

### 6.10 Slicing and sampling

```java
// Contiguous row range [from, to)
DataFrame slice  = df.slice(1, 4);   // rows 1, 2, 3
DataFrame first  = df.slice(0, 1);   // first row only
DataFrame empty  = df.slice(2, 2);   // zero rows (valid)

// Random sample without replacement
DataFrame sample = df.sample(50);    // up to 50 rows (capped at nrow())
```

`slice()` validates that `0 ≤ from ≤ to ≤ nrow()`.

### 6.11 Combining DataFrames

#### merge — horizontal (column union)

Combines two or more DataFrames side-by-side.  All must have the same row
count.  Clashing column names get a `_2`, `_3`, … suffix.

```java
DataFrame wide = left.merge(right);
DataFrame wide = a.merge(b, c, d);
```

#### concat — vertical (row union)

Stacks DataFrames on top of each other.  All must have the **exact same
schema**.

```java
DataFrame tall = train.concat(test);
DataFrame tall = a.concat(b, c);
```

If all frames have a `RowIndex`, the indices are concatenated too.

#### join — inner join on RowIndex

Performs an inner join using matching row-label keys.  If either frame has
no `RowIndex`, falls back to `merge()`.

```java
DataFrame merged = left.join(right);
// Rows present in both left.index and right.index are kept;
// unmatched rows are dropped.
```

### 6.12 Missing values

```java
// Drop rows with any null/NaN
DataFrame clean = df.dropna();

// Fill NaN/Inf in numeric columns in-place
df.fillna(0.0);    // replace with zero
df.fillna(-1.0);   // replace with sentinel
```

`fillna` operates on `DoubleVector`, `FloatVector`, `NullablePrimitiveVector`,
and `NumberVector` columns; non-numeric columns are unaffected.

### 6.13 Categorical encoding with factorize

`factorize()` converts `String` columns into `IntVector` columns annotated
with a `NominalScale`.  The integer codes are assigned in alphabetical order
of the distinct string values.

```java
// Convert all String columns
DataFrame f = df.factorize();

// Convert specific columns
DataFrame f = df.factorize("color", "country");

// Inspect the resulting scale
NominalScale scale = (NominalScale) f.schema().field("color").measure();
String label = scale.level(0);    // first level alphabetically
int    code  = scale.valueOf("Red").intValue();
```

This is the standard step to prepare string data for machine-learning
algorithms that require integer inputs.

### 6.14 Exporting to numeric arrays and matrices

Both `toArray()` and `toMatrix()` convert the DataFrame to a dense numeric
representation with optional bias (intercept) column and categorical
encoding.

```java
// Default: no bias, level encoding, all columns
double[][] X = df.toArray();

// Selective columns
double[][] X = df.toArray("age", "salary", "gender");

// With bias + dummy encoding for categoricals
double[][] X = df.toArray(true, CategoricalEncoder.DUMMY, "age", "gender", "salary");

// DenseMatrix form (suitable for linear algebra)
DenseMatrix M = df.toMatrix();
DenseMatrix M = df.toMatrix(true, CategoricalEncoder.DUMMY, "rowNameColumn");
```

`NaN` is used for null/missing values in the output array.

See [§7](#7-categoricalencoder) for the `CategoricalEncoder` options.

### 6.15 Statistics with describe

```java
DataFrame stats = df.describe();
System.out.println(stats);
```

Output columns: `column`, `type`, `measure`, `count`, `mode`, `mean`, `std`,
`min`, `25%`, `50%`, `75%`, `max`.

- Categorical columns report `mode`, `min`, `median`, `max` over the integer
  codes.
- Floating-point columns report `mean`, `std`, `min`, quartiles, `max`.
- Integral columns report all statistics.
- String / object columns report only `count` (non-null) and `mode`.

### 6.16 Printing and display

```java
System.out.println(df);            // head(10)
System.out.println(df.head(5));
System.out.println(df.tail(5));
System.out.println(df.toString(from, to, truncate));
```

`toString(from, to, truncate)`:
- `from` must be in `[0, nrow]`; `from > nrow` throws.
- `to ≤ from` (or after clamping to `nrow`) returns `"Empty DataFrame\n"`.
- Columns wider than `maxColWidth` are truncated with `"..."` when
  `truncate=true`.

### 6.17 Loading from JDBC

```java
import java.sql.*;

try (Connection conn = DriverManager.getConnection(jdbcUrl, user, pass);
     Statement  stmt = conn.createStatement();
     ResultSet  rs   = stmt.executeQuery("SELECT * FROM sales")) {
    DataFrame df = DataFrame.of(rs);
    System.out.println(df.describe());
}
```

JDBC types are mapped to SMILE types via `StructType.of(ResultSetMetaData)`.

---

## 7. CategoricalEncoder

`CategoricalEncoder` controls how categorical (`NominalScale` / `OrdinalScale`)
columns are converted when calling `toArray()`, `toMatrix()`, or
`Tuple.toArray()`.

| Enum value | Meaning | Output columns per category |
|-----------|---------|----------------------------|
| `LEVEL` | Integer level code (default) | 1 — raw code value |
| `DUMMY` | Dummy / treatment encoding | k−1 binary columns (reference = first level) |
| `ONE_HOT` | Full one-hot encoding | k binary columns |

```java
import smile.data.CategoricalEncoder;

// Level encoding (default) — "gender" becomes a single int column
double[][] X = df.toArray("age", "gender");

// Dummy encoding — k levels → k-1 binary columns
// e.g. gender {Male=0, Female=1} → one binary column "gender_Female"
double[][] X = df.toArray(false, CategoricalEncoder.DUMMY, "age", "gender");

// One-hot encoding — k levels → k binary columns
double[][] X = df.toArray(false, CategoricalEncoder.ONE_HOT, "age", "gender");
```

---

## 8. End-to-end tutorial

This tutorial processes an employee dataset from a raw POJO list through to
a numeric design matrix ready for a machine-learning algorithm.

### Step 1 — Define the domain object and load data

```java
import java.time.LocalDate;
import smile.data.DataFrame;
import smile.data.measure.*;
import smile.data.type.*;
import smile.data.vector.*;

public enum Department { Engineering, Marketing, HR }

public class Employee {
    public String     getName()       { return name; }
    public int        getAge()        { return age; }
    public Department getDepartment() { return dept; }
    public LocalDate  getHireDate()   { return hireDate; }
    public Double     getSalary()     { return salary; }   // nullable
    // …constructor, fields…
}

List<Employee> employees = loadEmployees();  // from DB / file / …
DataFrame df = DataFrame.of(Employee.class, employees);

System.out.println(df.schema());
// age: int
// department: byte  nominal[Engineering, HR, Marketing]
// hireDate: Date
// name: String
// salary: double?
```

### Step 2 — Inspect and describe

```java
System.out.println(df);
System.out.println(df.describe());

// How many rows have a null salary?
long nullSalaries = df.column("salary").getNullCount();
System.out.println("Missing salaries: " + nullSalaries);
```

### Step 3 — Add a derived column

```java
import smile.data.vector.IntVector;

// Tenure in years = current year - hire year
int[] tenure = new int[df.nrow()];
for (int i = 0; i < df.nrow(); i++) {
    LocalDate d = (LocalDate) df.column("hireDate").get(i);
    tenure[i] = LocalDate.now().getYear() - d.getYear();
}
df.add(new IntVector("tenure", tenure));
```

### Step 4 — Handle missing values

```java
// Option A: drop rows with any null
DataFrame clean = df.dropna();

// Option B: fill salary nulls with median
double medianSalary = df.column("salary").doubleStream()
        .filter(Double::isFinite).sorted()
        .skip(df.nrow() / 2).findFirst().orElse(0.0);
df.fillna(medianSalary);
```

### Step 5 — Sort and slice

```java
// Sort by salary descending
DataFrame sorted = df.sort("salary", false);

// Top 10 earners
DataFrame top10 = sorted.slice(0, Math.min(10, sorted.nrow()));
System.out.println(top10.head(10));
```

### Step 6 — Select features and encode categoricals

```java
// Select the columns we want for the model
DataFrame features = df.select("age", "tenure", "salary", "department");

// For algorithms that need integer encoding:
// "department" already has NominalScale (auto-detected from enum)

// Export design matrix with dummy encoding
double[][] X = features.drop("salary")
        .toArray(false, CategoricalEncoder.DUMMY,
                 "age", "tenure", "department");

// Response vector
double[] y = features.column("salary").toDoubleArray();
```

### Step 7 — Set a row index for traceability

```java
DataFrame indexed = df.setIndex("name");

// Later: look up a specific employee by name
Tuple alice = indexed.loc("Alice");
System.out.println("Alice's salary: " + alice.getDouble("salary"));

// Join two DataFrames on employee name
DataFrame reviews = loadReviews().setIndex("employee");
DataFrame combined = indexed.join(reviews);
```

### Step 8 — Describe the final feature set

```java
DataFrame finalFeatures = df.select("age", "tenure", "department", "salary");
System.out.println(finalFeatures.describe());

// Verify no nulls remain
boolean anyNull = finalFeatures.stream().anyMatch(Tuple::anyNull);
System.out.println("Any nulls: " + anyNull);
```

---

## 9. API quick reference

### DataFrame static factories

| Method | Description |
|--------|-------------|
| `new DataFrame(ValueVector...)` | Construct from column vectors |
| `new DataFrame(RowIndex, ValueVector...)` | With row index |
| `DataFrame.of(double[][], String...)` | From 2-D double array |
| `DataFrame.of(float[][], String...)` | From 2-D float array |
| `DataFrame.of(int[][], String...)` | From 2-D int array |
| `DataFrame.of(Class<T>, List<T>)` | From POJOs via reflection |
| `DataFrame.of(StructType, List<Tuple>)` | From tuple list (empty → zero-row frame) |
| `DataFrame.of(StructType, Stream<Tuple>)` | From tuple stream |
| `DataFrame.of(ResultSet)` | From JDBC ResultSet |

### DataFrame instance methods

| Method | Returns | Mutates `this`? | Description |
|--------|---------|-----------------|-------------|
| `nrow()` / `size()` | `int` | no | Number of rows |
| `ncol()` | `int` | no | Number of columns |
| `shape(dim)` | `int` | no | Size of dimension 0 (rows) or 1 (cols) |
| `isEmpty()` | `boolean` | no | True if zero rows |
| `schema()` | `StructType` | no | Column schema |
| `names()` | `String[]` | no | Column names |
| `dtypes()` | `DataType[]` | no | Column types |
| `measures()` | `Measure[]` | no | Column measures |
| `column(int)` / `column(String)` | `ValueVector` | no | Column vector |
| `get(int, int)` | `Object` | no | Cell (boxed) |
| `getInt/Double/…(int,int)` | primitive | no | Cell (typed) |
| `getString(int,int)` | `String` | no | Cell as string (uses measure) |
| `isNullAt(int,int)` | `boolean` | no | Null check |
| `set(int,int,Object)` | `void` | **yes** | Set cell value |
| `get(int)` | `Tuple` | no | Row as Tuple |
| `get(Index)` | `DataFrame` | no | Rows by Index |
| `get(boolean[])` | `DataFrame` | no | Rows by boolean mask |
| `slice(int,int)` | `DataFrame` | no | Rows `[from, to)` |
| `sample(int)` | `DataFrame` | no | Random sample without replacement |
| `sort(String)` | `DataFrame` | no | Ascending sort |
| `sort(String,boolean)` | `DataFrame` | no | Sort with direction |
| `select(int...)` | `DataFrame` | no | Columns by index |
| `select(String...)` | `DataFrame` | no | Columns by name |
| `drop(int...)` | `DataFrame` | no | Remove columns by index |
| `drop(String...)` | `DataFrame` | no | Remove columns by name |
| `add(ValueVector...)` | `DataFrame` | **yes** | Add new columns |
| `set(String,ValueVector)` | `DataFrame` | **yes** | Replace or add column |
| `rename(String,String)` | `DataFrame` | **yes** | Rename column in-place |
| `merge(DataFrame...)` | `DataFrame` | no | Horizontal column union |
| `concat(DataFrame...)` | `DataFrame` | no | Vertical row union |
| `join(DataFrame)` | `DataFrame` | no | Inner join on RowIndex |
| `setIndex(String)` | `DataFrame` | no | Column → RowIndex (removes column) |
| `setIndex(Object[])` | `DataFrame` | no | Attach RowIndex array |
| `loc(Object)` | `Tuple` | no | Row by label |
| `loc(Object...)` | `DataFrame` | no | Rows by labels |
| `dropna()` | `DataFrame` | no | Remove rows with any null |
| `fillna(double)` | `DataFrame` | **yes** | Fill NaN/null in numeric columns |
| `factorize(String...)` | `DataFrame` | no | Encode string columns as NominalScale |
| `toArray(String...)` | `double[][]` | no | Numeric array (LEVEL encoding) |
| `toArray(boolean,CategoricalEncoder,String...)` | `double[][]` | no | Numeric array with options |
| `toMatrix()` | `DenseMatrix` | no | Matrix (LEVEL, no bias) |
| `toMatrix(boolean,CategoricalEncoder,String)` | `DenseMatrix` | no | Matrix with options |
| `describe()` | `DataFrame` | no | Summary statistics |
| `head(int)` | `String` | no | Top-N rows formatted |
| `tail(int)` | `String` | no | Bottom-N rows formatted |
| `toString(int,int,boolean)` | `String` | no | Row range formatted |
| `stream()` | `Stream<Row>` | no | Row stream |
| `iterator()` | `Iterator<Row>` | no | Row iterator |
| `toList()` | `List<Row>` | no | All rows as list |

### StructType

| Method | Description |
|--------|-------------|
| `new StructType(StructField...)` | Construct from fields |
| `field(int)` / `field(String)` | Get field by ordinal or name |
| `indexOf(String)` | Ordinal of named field |
| `length()` | Number of fields |
| `names()` / `dtypes()` / `measures()` | Field property arrays |
| `add(StructField)` | Append a field (mutable) |
| `rename(String, String)` | Rename a field (mutable) |

### StructField

| Constructor / method | Description |
|---------------------|-------------|
| `new StructField(name, dtype)` | Without measure |
| `new StructField(name, dtype, measure)` | With measure |
| `withName(String)` | Return renamed copy |
| `isNumeric()` | True for non-nominal numeric fields |
| `toString(Object)` | Format a value using measure or dtype |

### ValueVector (selected)

| Method | Description |
|--------|-------------|
| `size()` | Element count |
| `isNullable()` | True if vector can contain nulls |
| `isNullAt(int)` | Null check at position |
| `getNullCount()` | Count of null positions |
| `anyNull()` | True if any null exists |
| `get(int)` | Boxed value (may be null) |
| `getInt/Double/…(int)` | Typed value |
| `getString(int)` | String form (uses measure) |
| `set(int, Object)` | Mutation |
| `get(Index)` | Sub-selection |
| `withName(String)` | Return renamed copy |
| `toIntArray()` / `toDoubleArray()` / `toStringArray()` | Bulk export |
| `intStream()` / `longStream()` / `doubleStream()` / `stream()` | Streaming |
| `eq(Object)` / `ne` / `lt` / `le` / `gt` / `ge` | Element-wise comparison masks |
| `isin(String...)` / `isin(int...)` | Membership mask |
| `isNull()` | Per-element null mask |


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

