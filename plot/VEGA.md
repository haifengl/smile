# SMILE — Declarative Data Visualization User Guide

`smile.plot.vega` is SMILE's declarative plotting library built on top of
**[Vega-Lite](https://vega.github.io/vega-lite/)**.  Instead of drawing
pixels directly, every method call builds a JSON specification that is
rendered by the Vega-Lite JavaScript library inside a browser or a notebook.
The result is publication-quality, interactive SVG/Canvas output with
built-in pan, zoom, and tooltip support — all with zero rendering code on the
Java side.

---

## Table of Contents

1. [Core Concepts](#1-core-concepts)
2. [Displaying a Chart](#2-displaying-a-chart)
3. [View — the Single-Chart Container](#3-view--the-single-chart-container)
4. [Data Sources](#4-data-sources)
5. [Marks](#5-marks)
6. [Encoding Channels](#6-encoding-channels)
7. [Transforms](#7-transforms)
8. [Predicates](#8-predicates)
9. [Axes and Legends](#9-axes-and-legends)
10. [View Composition](#10-view-composition)
    - 10.1 [Layer](#101-layer)
    - 10.2 [Concatenation](#102-concatenation)
    - 10.3 [Facet](#103-facet)
    - 10.4 [Repeat (SPLOM)](#104-repeat-splom)
11. [Configuration & Theming](#11-configuration--theming)
12. [Embedding in Notebooks / iframes](#12-embedding-in-notebooks--iframes)
13. [Architecture Overview](#13-architecture-overview)
14. [Quick-Reference Table](#14-quick-reference-table)

---

## 1. Core Concepts

Every chart is a **Vega-Lite JSON specification** — a plain `ObjectNode`
built up through a fluent Java API.  There are three fundamental objects:

| Object | Class | Role |
|--------|-------|------|
| **View** | `View` | Top-level container: holds data, mark, and encoding |
| **Mark** | `Mark` | The geometric shape used to represent data (bar, line, point …) |
| **Field** | `Field` | Binds a data field to a visual channel (x, y, color, size …) |

The typical workflow is:

```java
// 1. Create a view
View view = new View("Simple Bar Chart");

// 2. Point it at data
view.data().url("https://example.com/data.json");

// 3. Choose a mark type
view.mark("bar");

// 4. Encode fields to visual channels
view.encode("x", "category").type("ordinal");
view.encode("y", "value").type("quantitative");

// 5. Display
view.show();
```

All setters return `this` (or a related sub-object) for fluent chaining.

---

## 2. Displaying a Chart

### Open in a browser window

```java
view.show();               // writes a temp HTML file and opens it
view.show(true);           // same but silently swallows any exception
```

The generated HTML page loads Vega, Vega-Lite, and Vega-Embed from the
jsDelivr CDN and renders the chart.  The page includes an **Export**,
**View Source**, and **Open in Vega Editor** toolbar.

### Get the raw HTML string

```java
String html = view.html();        // English lang attribute
String html = view.html("fr");    // Custom lang attribute
```

The generated `<html lang="…">` attribute is always properly quoted.

### Get the JSON specification

```java
String compact = view.toString();       // compact JSON
String pretty  = view.toPrettyString(); // indented JSON

// Access the underlying Jackson ObjectNode directly
ObjectNode spec = view.spec();
```

---

## 3. View — the Single-Chart Container

`View` is the primary class for building a single-view chart.  It extends
`VegaLite`, which provides the top-level properties shared by all
specification types.

### Top-level properties

```java
View v = new View()
    .title("My Chart")              // chart title
    .name("chart1")                 // reference name
    .description("For ARIA")        // ARIA/accessibility description
    .background("white");           // CSS background color
```

### Size

```java
v.width(600).height(400);           // fixed pixel dimensions
v.width("container");               // responsive: fill the parent
v.widthStep(30);                    // per-band width (discrete x-axis)
v.heightStep(20);                   // per-band height (discrete y-axis)
```

### Padding

```java
v.padding(10);                      // same on all four sides
v.padding(5, 10, 5, 10);           // left, top, right, bottom
```

### Autosize

```java
v.autosize();                                    // default: "pad", no resize, "content"
v.autosize("fit", true, "padding");              // fit to container, re-calculate on update
```

---

## 4. Data Sources

`view.data()` returns a `Data` object that supports four source types.

### Inline JSON

```java
// JSON array of records (recommended — parsed to proper JSON, not a string)
view.data().values("""
    [
      {"a": "A", "b": 28},
      {"a": "B", "b": 55}
    ]""");

// Java array / List<T>
record Point(double x, double y) {}
view.data().values(new Point[]{new Point(1, 2), new Point(3, 4)});
view.data().values(List.of(Map.of("x", 1, "y", 2)));
```

> **Note:** `values(String json)` parses the string as JSON using
> `ObjectMapper.readTree()`.  The resulting spec contains a proper JSON
> array/object, not a quoted string.

### URL

```java
view.data().url("https://vega.github.io/vega-lite/examples/data/cars.json");
view.data().url("data/iris.csv").format("csv");
view.data().url("data/stocks.tsv").format("tsv");
```

### Named placeholder (bind at runtime)

```java
view.data().name("myDataset");   // populated later via Vega signals
```

### Format options

```java
// CSV with explicit column types
view.data().csv("data.csv", Map.of("year", "number", "active", "boolean"));

// TSV
view.data().tsv("data.tsv", null);   // null = auto-infer types

// Custom delimiter
view.data().dsv("data.pipe", "|");
view.data().dsv("data.pipe", "|", Map.of("value", "number"));

// JSON with a property path
view.data().json("world.json", "features");      // equivalent to json.features

// TopoJSON
view.data().topojson("us-10m.json", "feature", "counties");
view.data().topojson("us-10m.json", "mesh", "states");
```

---

## 5. Marks

`view.mark(type)` selects the geometric primitive and returns a `Mark`
object for further configuration.

### Primitive mark types

| String | Shape |
|--------|-------|
| `"bar"` | Rectangle bar |
| `"line"` | Connected line |
| `"area"` | Filled area |
| `"point"` | Symbol (scatter) |
| `"circle"` | Filled circle |
| `"square"` | Filled square |
| `"tick"` | Short line/tick |
| `"rect"` | Generic rectangle |
| `"rule"` | Horizontal/vertical rule |
| `"text"` | Text label |
| `"arc"` | Arc / pie / donut |
| `"geoshape"` | Geographic shape |
| `"trail"` | Variable-width line |
| `"image"` | Raster image |

### Composite mark types

| String | Shape |
|--------|-------|
| `"boxplot"` | Box-and-whisker |
| `"errorband"` | Shaded error band |
| `"errorbar"` | Error bars |

### Common mark properties

```java
view.mark("point")
    .color("steelblue")         // fill/stroke color (CSS color)
    .opacity(0.8)               // overall opacity
    .size(100)                  // point size in pixels²
    .shape("cross")             // point shape
    .filled(true)               // fill the mark
    .strokeWidth(2)             // stroke width
    .strokeDash(4, 2)           // [dash length, gap length]
    .clip(true)                 // clip to enclosing group bounds
    .tooltip(true)              // show all encoding fields in tooltip
    .tooltip("encoding")        // tooltip from encoding fields only
    .tooltip("data")            // tooltip from all data fields
    .aria(true)                 // include ARIA attributes in SVG
    .invalid("filter");         // skip null/NaN values
```

### Line & area extras

```java
view.mark("line").point(true);             // overlay point markers
view.mark("area").line(true).point(true);  // with line and points
view.mark("line").interpolate("monotone"); // curve interpolation
```

### Bar extras

```java
view.mark("bar")
    .cornerRadiusTopLeft(4)
    .cornerRadiusTopRight(4);
```

### Arc (pie / donut)

```java
view.mark("arc").innerRadius(50).stroke("#fff");
```

### Composite mark configuration

```java
view.mark("boxplot").extent("min-max");   // whiskers to data range
view.mark("errorband").extent("ci");      // 95% confidence interval
view.mark("errorbar").extent("stderr");   // standard error
```

---

## 6. Encoding Channels

`view.encode(channel, field)` binds a data field to a visual channel and
returns a `Field` object for further refinement.

### Position channels

```java
view.encode("x",  "Horsepower").type("quantitative");
view.encode("y",  "Miles_per_Gallon").type("quantitative");
view.encode("x2", "end").type("quantitative");   // range: x to x2
view.encode("y2", "end").type("quantitative");
```

### Non-positional channels

```java
view.encode("color",        "Origin").type("nominal");
view.encode("size",         "Acceleration").type("quantitative");
view.encode("shape",        "Origin").type("nominal");
view.encode("opacity",      "count").type("quantitative");
view.encode("strokeWidth",  "value").type("quantitative");
view.encode("text",         "label").type("nominal");
view.encode("tooltip",      "value").type("quantitative");
view.encode("href",         "url").type("nominal");
view.encode("detail",       "group").type("nominal");   // grouping without color
```

### Polar / arc channels

```java
view.encode("theta",  "value").type("quantitative").stack("zero");
view.encode("radius", "value").type("quantitative").scale("sqrt").zero(true);
```

### Facet channels (shorthand)

```java
view.encode("row",    "species").type("nominal");
view.encode("column", "year").type("ordinal");
view.encode("facet",  "category").type("nominal");
```

### Field type

```java
.type("quantitative")   // continuous numeric
.type("ordinal")        // ordered discrete
.type("nominal")        // unordered discrete (categorical)
.type("temporal")       // date / time
.type("geojson")        // geographic shape
```

### Aggregate functions

```java
.aggregate("sum")
.aggregate("mean")
.aggregate("median")
.aggregate("min")
.aggregate("max")
.aggregate("count")
.aggregate("distinct")
.aggregate("stdev")
.aggregate("variance")
```

### Time units

```java
.timeUnit("year")
.timeUnit("yearmonth")
.timeUnit("month")
.timeUnit("date")       // day of month
.timeUnit("day")        // day of week
.timeUnit("hours")
.timeUnit("minutes")
.timeUnit("seconds")
.timeUnit("quarter")
```

### Binning

```java
.bin(true)                              // auto bins
.bin("binned")                          // data is already binned
.bin(new BinParams().maxBins(20))       // custom parameters
.bin(new BinParams().step(5).nice(true))
```

`BinParams` options: `anchor`, `base`, `divide`, `extent`, `maxBins`,
`minStep`, `nice`, `step`, `steps`.

### Scale

```java
.type("log")            // log scale
.zero(false)            // do not force zero
.range(0, 5000)         // pixel range [min, max]
.range("#lightblue", "#darkblue")   // color range
.domain(0, 100)         // explicit domain
.domainMin(0)
.domainMax(100)
.scale(null)            // remove the scale (identity mapping)
.scale("sqrt")          // scale type shorthand
```

### Sorting

```java
.sort("ascending")
.sort("descending")
.sort("-y")             // sort by y-channel, descending
```

### Stacking

```java
.stack("zero")          // stacked bar / area
.stack("normalize")     // percentage (100%) stacked
.stack("center")        // streamgraph
.stack(null)            // layered (no stacking)
```

### Constant values and datums

```java
// Constant visual value — not mapped through a scale
view.encodeValue("color", "red");
view.encodeValue("strokeWidth", 2);
view.encodeValue("opacity", 0.5);

// Constant data value — mapped through the scale
view.encodeDatum("x", 0);          // reference line at x = 0
view.encodeDatum("y", "2020-01-01");
```

### Color domain / range shortcuts

```java
// Explicit ordinal domain and color range
view.encode("color", "weather").type("nominal")
    .domain("sun", "fog", "drizzle", "rain", "snow")
    .range("#e7ba52", "#c7c7c7", "#aec7e8", "#1f77b4", "#9467bd");

// Remove the legend for this channel
view.encode("color", "entity").type("nominal").removeLegend();
```

---

## 7. Transforms

`view.transform()` returns a `Transform` object that appends entries to the
view-level `transform` array.  Transforms are executed in order, before
any inline (encoding-level) transforms.

Multiple transforms can be chained:

```java
view.transform()
    .filter("datum.year == 2000")
    .calculate("datum.sex == 2 ? 'Female' : 'Male'", "gender");
```

Calling `view.transform()` multiple times appends to the **same** array.

### Filter

```java
// Expression string
view.transform().filter("datum.b2 > 60");

// Predicate object (see §8)
view.transform().filter(Predicate.valid("IMDB Rating"));
view.transform().filter(Predicate.and(
        Predicate.valid("IMDB Rating"),
        Predicate.valid("Rotten Tomatoes Rating")));
```

### Calculate (formula)

```java
// datum refers to the current row
view.transform().calculate("split(datum.Name, ' ')[0]", "Brand");
view.transform().calculate("datum.count / datum.total", "percent");
```

### Bin

```java
view.transform().bin("Horsepower", "bin_hp");
// produces fields: bin_hp (start) and bin_hp_end (end)
```

### Aggregate

```java
// Sum of "people", grouped by "age"
view.transform().aggregate("sum", "people", "totalPeople", "age");

// Count of rows (no field required)
view.transform().aggregate("count", "*", "count", "IMDB Rating");
```

### Join Aggregate (preserve original rows)

```java
// Appends a new "TotalCount" column to every row
view.transform().joinAggregate("sum", "Count", "TotalCount");
view.transform().joinAggregate("sum", "Count", "TotalCount", "category");
```

### Density

```java
view.transform()
    .density("IMDB Rating")
    .bandwidth(0.3)
    .groupby("genre");
```

### Loess smoothing

```java
view.transform().loess("Miles_per_Gallon", "Horsepower").bandwidth(0.3);
```

### Regression

```java
view.transform().regression("Miles_per_Gallon", "Horsepower")
    .method("linear")
    .extent(50.0, 250.0);
```

### Quantile

```java
view.transform().quantile("price").step(0.01).as("p", "v");
```

### Pivot

```java
view.transform().pivot("year", "gdp").groupby("country");
```

### Impute (fill missing values)

```java
view.transform().impute("value", "year")
    .method("value")
    .value(0)
    .groupby("symbol");
```

### Stack

```java
view.transform()
    .stack("count", "stackedCount", "category")
    .offset("normalize")
    .sort(new SortField("category", "ascending"));
```

### Fold (wide → long)

```java
// Turns columns "2010", "2020" into rows with key and value fields
view.transform().fold(new String[]{"2010", "2020"}, new String[]{"year", "gdp"});
```

### Flatten (arrays → rows)

```java
view.transform().flatten(new String[]{"tags"}, new String[]{"tag"});
```

### Sample

```java
view.transform().sample(500);   // reservoir sample of 500 rows
```

### Time Unit

```java
view.transform().timeUnit("year", "date", "year");
```

### Lookup (join with secondary dataset)

```java
// Join to a selection parameter
view.transform().lookup("id", "brushParam");

// Join to a secondary data source
var lookupData = view.transform().lookupData("id").fields("rate");
lookupData.data().url("unemployment.tsv");
view.transform().lookup("id", lookupData);
```

### Window

```java
// Cumulative sum
view.transform()
    .window(new WindowTransformField("sum", "count", 0, "CumulativeCount"))
    .sort("date")
    .frame(null, 0);  // unbounded preceding → current row

// Rolling average over ±15 rows
view.transform()
    .window(new WindowTransformField("mean", "temp_max", 0, "rolling_mean"))
    .frame(-15, 15);

// Rank (no field needed — pass null)
view.transform()
    .window(new WindowTransformField("rank", null, 0, "Rank"))
    .sort("score");
```

`WindowTransformField` is a Java `record`:
`(String op, String field, double param, String as)`.
When `field` is `null` or `param` is `0`, those properties are omitted from
the JSON (as required by the Vega-Lite schema for ops like `rank`, `count`).

### Extent

```java
view.transform().extent("price", "priceExtent");
```

---

## 8. Predicates

`Predicate` objects are used in `transform().filter(predicate)` and in
conditional encodings.

### Field predicates

```java
// Validity (neither null nor NaN)
Predicate.valid("score")

// Comparison: equal, lt, lte, gt, gte
Predicate.of("year",  "equal", 2000.0)
Predicate.of("score", "gt",    60.0)
Predicate.of("flag",  "equal", true)
Predicate.of("name",  "equal", "Alice")

// Range (inclusive)
Predicate.range("score", 0, 100)

// One-of set membership
Predicate.oneOf("country", "USA", "UK", "CA")
Predicate.oneOf("rank",    1.0, 2.0, 3.0)
```

### Parameter predicate

```java
// Include only rows inside a named Vega selection
new Predicate("brush", true)    // empty selection = no rows
new Predicate("brush", false)   // empty selection = all rows (default)
```

### Logical composition

```java
// AND
Predicate.and(Predicate.valid("x"), Predicate.valid("y"))

// OR
Predicate.or(Predicate.range("year", 1990, 2000),
             Predicate.range("year", 2010, 2020))

// NOT
Predicate.not(Predicate.of("entity", "equal", "All natural disasters"))
```

### Time-unit predicate

```java
Predicate.of("date", "equal", "2001")
         .timeUnit("year")
```

---

## 9. Axes and Legends

### Axis

Obtained from `field.axis()` or `config.axis()`.

```java
// Per-field axis customization
view.encode("x", "a").type("ordinal")
    .axis()
        .title("Category")
        .labelAngle(-45)
        .labelOverlap("greedy")
        .format("%b %Y")         // D3 number / time format
        .domain(false)           // hide the axis baseline
        .grid(false)             // hide grid lines
        .ticks(false)            // hide tick marks
        .tickCount(5);

// Remove the axis completely
view.encode("x", "a").type("ordinal").axis(null);
```

### Legend

Obtained from `field.legend()`.

```java
view.encode("color", "weather").type("nominal")
    .legend()
        .title("Weather Type")
        .orient("bottom")
        .columns(3)
        .clipHeight(30);

// Remove the legend
view.encode("color", "entity").type("nominal").removeLegend();
```

---

## 10. View Composition

### 10.1 Layer

`Layer` superimposes multiple views on a shared canvas.  Data and encoding
can be set at the layer level and inherited by child views.

```java
var line = new View();
line.mark("line").color("red").size(3);
line.encode("x", "date").type("temporal");
line.encode("y", "rolling_mean").type("quantitative");

var points = new View();
points.mark("point").opacity(0.3);
points.encode("x", "date").type("temporal").title("Date");
points.encode("y", "temp_max").type("quantitative").title("Max Temperature");

var layer = new Layer(line, points)
        .title("Rolling Averages over Raw Values")
        .width(600).height(300);

// Shared data at the layer level
layer.data().url("seattle-weather.csv").format("csv");

// Shared transform
layer.transform()
     .window(new WindowTransformField("mean", "temp_max", 0, "rolling_mean"))
     .frame(-15, 15);

layer.show();
```

### 10.2 Concatenation

`Concat` places views side-by-side or in a grid.

```java
// Horizontal
Concat.horizontal(view1, view2).show();

// Vertical
Concat.vertical(view1, view2).title("Vertical Layout").show();

// Flow grid (N views, M columns)
new Concat(3, v1, v2, v3, v4, v5, v6).show();
```

Resolution of shared scales/axes/legends:

```java
Concat c = Concat.horizontal(v1, v2);
c.resolveScale("color", "independent");   // separate color scales
c.resolveAxis("y",      "independent");   // separate y axes
c.resolveLegend("size", "shared");        // single shared size legend
```

Layout properties (available on all `ViewLayoutComposition` types):

```java
c.align("all");          // grid alignment: "all", "each", "none"
c.bounds("flush");       // size calculation: "full" or "flush"
c.center(true);          // center views within grid cells
c.spacing(10);           // gap in pixels between sub-views
```

### 10.3 Facet

`Facet` creates trellis/small-multiple plots.

```java
// Wrap layout (single facet field)
var facet = new Facet(view)
        .columns(3)
        .title("Population by Age and Gender");
facet.facet("gender").type("nominal").title("Gender");
facet.data().url("population.json");
facet.show();

// Row × Column grid
var facet = new Facet(view);
facet.row("site").type("ordinal");
facet.column("year").type("ordinal");
facet.data().url("barley.json");
facet.show();
```

Alternatively, use the encoding shorthand directly on a `View`:

```java
view.encode("facet",  "gender").type("nominal").columns(3);
view.encode("row",    "site").type("ordinal");
view.encode("column", "year").type("ordinal");
```

### 10.4 Repeat (SPLOM)

`Repeat` creates a matrix of views by repeating a spec over a list of fields.

```java
// 1-D repeat (layer)
var repeat = new Repeat(view, "temp_max", "temp_min", "precipitation");
repeat.columns(2);
repeat.data().url("seattle-weather.csv").format("csv");
repeat.show();

// 2-D repeat — Scatter Plot Matrix (SPLOM)
var cell = new View().width(150).height(150);
cell.mark("point");
cell.encode("x", "repeat:column").type("quantitative").zero(false);
cell.encode("y", "repeat:row").type("quantitative").zero(false);
cell.encode("color", "species").type("nominal");

String[] rows    = {"petalWidth", "petalLength", "sepalWidth", "sepalLength"};
String[] columns = {"sepalLength", "sepalWidth", "petalLength", "petalWidth"};
var splom = new Repeat(cell, rows, columns).title("Iris SPLOM");
splom.data().url("iris.json");
splom.show();
```

---

## 11. Configuration & Theming

`view.config()` returns a `Config` object; `view.viewConfig()` returns a
`ViewConfig` that controls the default single-view dimensions and borders.

### Global defaults

```java
view.config()
    .background("#f5f5f5")
    .numberFormat(".2f")
    .timeFormat("%Y")
    .axis()          // returns FormatConfig for axes
        .domain(false)
        .grid(false);
```

### Single-view defaults

```java
view.viewConfig()
    .stroke("transparent")   // remove border around the plot area
    .strokeWidth(0)
    .step(13);               // default band/point step size
```

### Inline style per axis

```java
view.viewConfig().stroke("transparent").axis().domainWidth(1);
```

### Per-mark style defaults

```java
view.config().mark().opacity(0.7).interpolate("monotone");
```

---

## 12. Embedding in Notebooks / iframes

In Jupyter-like environments (e.g. [BeakerX](http://beakerx.com/),
[Kotlin Notebooks](https://plugins.jetbrains.com/plugin/16340-kotlin-notebook),
[SMILE-based notebooks](https://github.com/haifengl/smile)) the chart can be
embedded as a self-contained `<iframe>`:

```java
// Auto-generated UUID id
String fragment = view.iframe();

// Explicit id for referencing from JavaScript
String fragment = view.iframe("chart-1");
```

The `iframe` output:
- wraps the full HTML in a `srcdoc` attribute (HTML-escaped)
- contains a small script that auto-sizes the iframe height to the content

The iframe is safe to concatenate directly into a larger HTML template or a
notebook cell output.

> **Implementation note:** `String.format` is intentionally **not** used
> inside `iframe()`, because the HTML-escaped spec JSON may contain `%`
> characters that `String.format` would misinterpret as format specifiers.

---

## 13. Architecture Overview

```
smile.plot.vega
├── VegaLite          base class — JSON spec, title, padding, autosize, html(), show()
│   └── View          single-view spec — mark, encoding, width/height
│       └── Layer     superimposed views — inherits View for shared channels
│
├── Mark              mark definition — type, color, opacity, tooltip, clip …
├── Field             encoding channel definition — type, aggregate, bin, scale, axis, legend …
├── Data              data source — url, values, csv, tsv, json, topojson …
├── Transform         view-level transforms — filter, calculate, bin, aggregate …
│   ├── DensityTransform
│   ├── LoessTransform
│   ├── RegressionTransform
│   ├── QuantileTransform
│   ├── ImputeTransform
│   ├── PivotTransform
│   ├── StackTransform
│   └── WindowTransform
│
├── Predicate         filter predicates — valid, of, range, oneOf, and, or, not
├── Axis              axis configuration
├── Legend            legend configuration
├── Config            global configuration / theme
├── ViewConfig        single-view canvas defaults
├── FormatConfig      number and time format defaults
├── BinParams         custom binning parameters
│
├── Concat            horizontal / vertical / flow grid composition
├── Facet             trellis (small multiples) composition
├── Repeat            field-repeat composition (incl. SPLOM)
│
├── ViewComposition   interface — resolveScale, resolveAxis, resolveLegend
└── ViewLayoutComposition interface — align, bounds, center, spacing
```

Every class wraps a Jackson `ObjectNode` (or `ArrayNode`). Setters mutate the
node in-place and return `this`, making them fluent and side-effect-free from
the caller's perspective.  `VegaLite.mapper` is the shared, stateless
`ObjectMapper` instance used throughout the package.

---

## 14. Quick-Reference Table

### Mark types

| Mark | Typical use |
|------|-------------|
| `"bar"` | Bar chart, histogram, Gantt chart |
| `"line"` | Line chart, time series |
| `"area"` | Area chart, streamgraph |
| `"point"` | Scatter plot, bubble chart |
| `"circle"` | Scatter with filled circles |
| `"rect"` | Heatmap, 2D histogram |
| `"arc"` | Pie chart, donut, radial chart |
| `"text"` | Text/label scatter |
| `"rule"` | Reference lines |
| `"tick"` | Strip / rug plot |
| `"geoshape"` | Choropleth map |
| `"boxplot"` | Box-and-whisker |
| `"errorband"` | Confidence / prediction band |
| `"errorbar"` | Error bars |

### Encoding channels

| Channel | Category | Description |
|---------|----------|-------------|
| `x`, `y` | Position | Horizontal / vertical position |
| `x2`, `y2` | Position | Second endpoint for ranges |
| `xOffset`, `yOffset` | Offset | Jitter / dodge offset |
| `theta`, `radius` | Polar | Angle and radius for arcs |
| `longitude`, `latitude` | Geographic | Geographic coordinates |
| `color` | Mark property | Fill or stroke color |
| `fill`, `stroke` | Mark property | Separate fill / stroke color |
| `opacity` | Mark property | Overall opacity |
| `size` | Mark property | Point size or bar width |
| `shape` | Mark property | Point shape |
| `strokeWidth` | Mark property | Stroke width |
| `strokeDash` | Mark property | Stroke dash pattern |
| `text` | Text | Text content |
| `tooltip` | Tooltip | Tooltip content |
| `href` | Hyperlink | Click-through URL |
| `detail` | Level of detail | Grouping without color |
| `facet`, `row`, `column` | Facet | Trellis faceting |

### View composition classes

| Class | Vega-Lite operator | Use case |
|-------|--------------------|----------|
| `Layer` | `layer` | Overlay multiple marks on a shared axis |
| `Concat.horizontal()` | `hconcat` | Side-by-side views |
| `Concat.vertical()` | `vconcat` | Stacked views |
| `new Concat(n, …)` | `concat` | Grid with `n` columns |
| `Facet` | `facet` | Small multiples (one per data subset) |
| `Repeat` | `repeat` | Small multiples (one per field) |

### Transform summary

| Method | Vega-Lite key | Purpose |
|--------|---------------|---------|
| `filter(expr)` | `filter` | Row filtering |
| `calculate(expr, as)` | `calculate` | Derived column |
| `bin(field, as)` | `bin` | Numeric binning |
| `aggregate(op, field, as, …)` | `aggregate` | Group-by aggregation |
| `joinAggregate(…)` | `joinaggregate` | Aggregate preserving rows |
| `density(field)` | `density` | KDE density estimate |
| `loess(field, on)` | `loess` | LOESS smoothing |
| `regression(field, on)` | `regression` | Regression line |
| `quantile(field)` | `quantile` | Quantile estimation |
| `pivot(field, value)` | `pivot` | Long → wide |
| `fold(fields, as)` | `fold` | Wide → long |
| `flatten(fields, as)` | `flatten` | Array column → rows |
| `impute(field, key)` | `impute` | Fill missing values |
| `stack(field, as, …)` | `stack` | Cumulative stacking |
| `sample(n)` | `sample` | Reservoir sampling |
| `timeUnit(unit, field, as)` | `timeUnit` | Temporal extraction |
| `lookup(key, from)` | `lookup` | Join with secondary dataset |
| `window(fields)` | `window` | Window / rolling functions |
| `extent(field, param)` | `extent` | Compute field extent |



---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

