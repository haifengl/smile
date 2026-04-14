# SMILE — Data Visualization User Guide

`smile.plot.swing` is SMILE's Swing-based 2D/3D plotting library. It provides
an interactive figure canvas with a rich set of built-in plot types, a flexible
color palette system, and a clean API that follows a consistent
**create → figure → display** pattern.

---

## Table of Contents

1. [Core Concepts](#1-core-concepts)
2. [Displaying a Figure](#2-displaying-a-figure)
3. [The Figure Object](#3-the-figure-object)
4. [Point Marks & Line Styles](#4-point-marks--line-styles)
5. [Color & Palettes](#5-color--palettes)
6. [Plot Types](#6-plot-types)
   - 6.1 [Scatter Plot](#61-scatter-plot)
   - 6.2 [Line Plot](#62-line-plot)
   - 6.3 [Bar Plot](#63-bar-plot)
   - 6.4 [Box Plot](#64-box-plot)
   - 6.5 [Histogram](#65-histogram)
   - 6.6 [Q-Q Plot](#66-q-q-plot)
   - 6.7 [Heatmap](#67-heatmap)
   - 6.8 [Contour Plot](#68-contour-plot)
   - 6.9 [3-D Surface Plot](#69-3-d-surface-plot)
   - 6.10 [Scree Plot](#610-scree-plot)
   - 6.11 [Sparse Matrix Plot](#611-sparse-matrix-plot)
   - 6.12 [Dendrogram](#612-dendrogram)
   - 6.13 [Text (Label) Plot](#613-text-label-plot)
   - 6.14 [Staircase Plot](#614-staircase-plot)
7. [Multi-Figure Layout (SPLOM)](#7-multi-figure-layout-splom)
8. [Axes, Labels, Ticks & Grid](#8-axes-labels-ticks--grid)
9. [Legends](#9-legends)
10. [Headless / Programmatic Export](#10-headless--programmatic-export)
11. [Architecture Overview](#11-architecture-overview)
12. [Quick-Reference Table](#12-quick-reference-table)

---

## 1. Core Concepts

Every visualization in `smile.plot.swing` is built from three layers:

| Layer | Class | Role |
|-------|-------|------|
| **Data model** | `Plot` subclass (`ScatterPlot`, `LinePlot`, …) | Holds the data; knows its own bounding box |
| **Figure** | `Figure` | Coordinate system, axes, title, legend overlay |
| **Display** | `FigurePane` / `Canvas` | Swing component that renders a `Figure` interactively |

The recommended workflow is:

```java
// 1. Build a Plot
ScatterPlot plot = ScatterPlot.of(points, 'o');

// 2. Wrap it in a Figure (bounds are inferred automatically)
Figure figure = plot.figure();

// 3. Optionally configure the Figure
figure.setTitle("My Scatter Plot")
      .setAxisLabels("X", "Y");

// 4. Display in a window
figure.show();                      // convenience wrapper
// --- or equivalently ---
new FigurePane(figure).window();
```

Every `Plot` subclass exposes a `figure()` factory method that creates a
pre-configured `Figure` with sensible defaults for that plot type (e.g.
`BoxPlot.figure()` hides the x-axis grid and sets category tick labels
automatically).

---

## 2. Displaying a Figure

### Interactive Swing window

```java
figure.show();                      // SwingUtilities.invokeLater internally
```

The window that opens has a built-in toolbar with:

- **Save** — exports to PNG/JPEG/BMP/SVG (via file-chooser dialog)
- **Print** — sends the figure to the system printer
- **Zoom** — rubber-band zoom with left-drag; right-click resets
- **Rotate** (3-D only) — drag to rotate, scroll to zoom

### Embedding in your own Swing layout

```java
FigurePane pane = new FigurePane(figure);
myPanel.add(pane, BorderLayout.CENTER);
```

### Headless / off-screen rendering

`Figure.toBufferedImage(width, height)` renders to a `BufferedImage` with
no display required. This is useful for automated report generation or tests:

```java
BufferedImage img = figure.toBufferedImage(1280, 960);
ImageIO.write(img, "png", new File("plot.png"));
```

---

## 3. The Figure Object

`Figure` is the central configuration object. All setters return `this` for
fluent chaining and fire `PropertyChangeEvent`s so that an attached `Canvas`
redraws itself automatically.

```java
Figure fig = new Figure(lowerBound, upperBound);   // explicit bounds
Figure fig = new Figure(lowerBound, upperBound, false); // no bound-padding
```

### Common configuration

```java
fig.setTitle("Iris Dataset")
   .setTitleFont(new Font("SansSerif", Font.BOLD, 18))
   .setTitleColor(Color.DARK_GRAY)
   .setMargin(0.12)               // fraction of canvas size; must be in (0, 0.3)
   .setLegendVisible(true);
```

### Adding and removing shapes at runtime

```java
fig.add(aPlot);           // Plot subclass — bounds are extended automatically
fig.add(aShape);          // any Shape (Label, GridLabel, …) — bounds unchanged
fig.remove(aPlot);
fig.clear();              // remove all shapes
```

### Listening for changes (e.g. to trigger repaint in custom code)

```java
fig.addPropertyChangeListener(e -> System.out.println(e.getPropertyName()));
```

Named events: `"title"`, `"margin"`, `"titleFont"`, `"titleColor"`,
`"axisLabels"`, `"axisLabel"`, `"addShape"`, `"removeShape"`,
`"addPlot"`, `"removePlot"`, `"clear"`, `"extendBound"`,
`"extendLowerBound"`, `"extendUpperBound"`, `"setBound"`.

---

## 4. Point Marks & Line Styles

### Point marks (`char`)

| Character | Shape |
|-----------|-------|
| `'.'` | small dot |
| `'+'` | plus sign |
| `'-'` | horizontal dash |
| `'|'` | vertical bar |
| `'*'` | star |
| `'x'` | cross |
| `'o'` | circle (open) |
| `'O'` | large circle (open) |
| `'@'` | filled circle |
| `'#'` | large filled circle |
| `'s'` | small square (open) |
| `'S'` | large square (open) |
| `'q'` | small filled square |
| `'Q'` | large filled square |
| `' '` | invisible (line plots: no point marker) |

### Line styles (`Line.Style` enum)

| Constant | Appearance |
|----------|-----------|
| `SOLID` | ─────── |
| `DOT` | ·········· |
| `DASH` | – – – – |
| `DOT_DASH` | –·–·–·– |
| `LONG_DASH` | ——— |

---

## 5. Color & Palettes

`Palette` provides CSS/HTML color parsing, named constants, and
color-ramp generators for continuous data.

### Parsing CSS/HTML colors

```java
Color red   = Palette.web("red");
Color coral = Palette.web("#ff6347");
Color semi  = Palette.web("rgba(255,99,71,0.5)");   // 50% opacity
Color hsl   = Palette.web("hsl(9, 100%, 64%)");
Color hex0x = Palette.web("0xff6347");
```

The second argument of `web(String, float)` is an additional opacity multiplier
in `[0, 1]`.

### Named color constants

```java
Palette.RED   Palette.GREEN   Palette.BLUE     Palette.CYAN
Palette.MAGENTA  Palette.YELLOW  Palette.BLACK  Palette.WHITE
Palette.DARK_GRAY  Palette.LIGHT_GRAY
```

### Colour-ramp arrays (for heatmaps / surfaces)

```java
Color[] jet     = Palette.jet(256);           // cold-to-hot rainbow
Color[] rainbow = Palette.rainbow(256);       // full-spectrum
Color[] heat    = Palette.heat(256);          // black-red-yellow-white
Color[] terrain = Palette.terrain(256);       // green-yellow-brown-white
Color[] topo    = Palette.topo(256);          // blue-cyan-green-yellow-red
Color[] gray    = Palette.grayscale(256);     // monochrome
```

All ramp methods accept an optional `float alpha` second argument for
transparency: `Palette.jet(256, 0.8f)`.

### Categorical palette

`Palette.get(int index)` returns a distinct color for the given group index
(cycles through a built-in list of 10 visually distinct colous). It is used
automatically by `ScatterPlot.of(x, labels, mark)` and similar factory
methods.

---

## 6. Plot Types

### 6.1 Scatter Plot

```java
double[][] points = {{1, 2}, {3, 4}, {5, 6}};

// Simplest form — black circles
ScatterPlot.of(points).figure().show();

// Custom mark and color
ScatterPlot.of(points, '@', Color.RED).figure().show();

// Multiple groups with automatic color assignment
double[][] x = ...;  // n-by-2
String[] labels = {"setosa", "versicolor", "virginica"};
ScatterPlot.of(x, labels, '*').figure().show();

// Integer class labels
int[] y = ...;
ScatterPlot.of(x, y, 'o').figure().show();

// 3-D scatter (n-by-3 points)
double[][] xyz = ...;
ScatterPlot.of(xyz, '@', Color.BLUE).figure().show();
```

#### From a DataFrame

```java
DataFrame iris = Read.arff(Paths.getTestData("weka/iris.arff"));

// 2-D: two numeric columns
ScatterPlot.of(iris, "sepallength", "sepalwidth", '*', Color.BLUE)
           .figure().setAxisLabels("Sepal Length", "Sepal Width")
           .show();

// 2-D with category coloring
ScatterPlot.of(iris, "sepallength", "sepalwidth", "class", '*')
           .figure().show();

// 3-D
ScatterPlot.of(iris, "sepallength", "sepalwidth", "petallength", "class", '*')
           .figure().show();
```

---

### 6.2 Line Plot

```java
double[] y = {0, 1, 4, 9, 16, 25};      // x = 0, 1, 2, 3, 4, 5

// y values only — x-coordinates default to [0, n)
LinePlot.of(y, Color.BLUE).figure().show();

// Explicit (x, y) pairs
double[][] xy = {{0,0},{1,1},{2,4},{3,9}};
LinePlot.of(xy, Line.Style.DASH, Color.RED).figure().show();

// With legend
LinePlot.of(y, Line.Style.SOLID, Color.GREEN, "y = x²")
        .figure().show();

// Parametric curve (heart)
double[][] heart = new double[200][2];
for (int i = 0; i < 200; i++) {
    double t = Math.PI * (i - 100) / 100.0;
    heart[i][0] = 16 * Math.pow(Math.sin(t), 3);
    heart[i][1] = 13 * Math.cos(t) - 5 * Math.cos(2*t)
                - 2 * Math.cos(3*t) - Math.cos(4*t);
}
LinePlot.of(heart, Color.RED).figure().show();
```

#### Multiple lines on one figure

```java
Figure fig = LinePlot.of(sin, Line.Style.SOLID, Color.BLUE, "sin").figure();
fig.add(LinePlot.of(cos, Line.Style.DASH,  Color.RED,  "cos"));
fig.show();
```

---

### 6.3 Bar Plot

```java
// Simple bar chart (values at positions 0, 1, 2, …)
double[] heights = {3.2, 5.1, 2.8, 4.4};
BarPlot.of(heights).figure().show();

// Integer data
int[] counts = {10, 25, 18, 30};
BarPlot.of(counts).figure().show();

// Multiple groups (grouped bar chart)
double[][] grouped = {
    {1.0, 2.0, 3.0},   // group A
    {1.5, 2.5, 1.8}    // group B
};
BarPlot.of(grouped, new String[]{"Group A", "Group B"}).figure().show();
```

---

### 6.4 Box Plot

```java
double[] setosa     = ...;   // petal lengths for each species
double[] versicolor = ...;
double[] virginica  = ...;

BoxPlot bp = new BoxPlot(
    new double[][]{setosa, versicolor, virginica},
    new String[]{"setosa", "versicolor", "virginica"}
);
bp.figure().setAxisLabels("", "Petal Length").show();

// Without labels
BoxPlot.of(setosa, versicolor, virginica).figure().show();
```

The box extends from Q1 to Q3 with the median line at Q2. Whiskers reach the
most extreme non-outlier observations (1.5 × IQR rule); outliers are shown
as individual `'o'` points. Hovering over a box shows a tooltip with Q1,
median, and Q3.

---

### 6.5 Histogram

```java
double[] data = ...;   // raw sample

// Default bins (square-root rule), probability density
Histogram.of(data).figure().show();

// Explicit bin count, frequency scale
Histogram.of(data, 30, false).figure().show();

// Custom color
Histogram.of(data, 20, true, Color.ORANGE).figure().show();

// Custom breakpoints (minimum 2 bins required)
double[] breaks = {0, 10, 20, 30, 40, 50};
Histogram.of(data, breaks, true).figure().show();
```

All overloads are available for both `double[]` and `int[]` data.

In **probability mode** (`prob = true`) the bar heights sum to 1.
In **frequency mode** (`prob = false`) the heights are raw counts.

---

### 6.6 Q-Q Plot

```java
double[] sample = ...;

// One-sample: compare to standard normal
QQPlot.of(sample).figure().show();

// One-sample: compare to any Distribution
QQPlot.of(sample, new ExponentialDistribution(1.0)).figure().show();

// Two-sample
QQPlot.of(sampleA, sampleB).figure().show();

// Integer data
int[] counts = ...;
QQPlot.of(counts, new PoissonDistribution(3.0)).figure().show();
```

The diagonal reference line (y = x) is drawn automatically. Points close to
the line indicate a good distributional fit.

---

### 6.7 Heatmap

```java
double[][] z = ...;    // m × n matrix of values

// Simplest — 16-color jet palette
Heatmap.of(z).figure().show();

// Custom palette size
Heatmap.of(z, 256).figure().show();

// Custom palette
Heatmap.of(z, Palette.heat(256)).figure().show();

// With axis coordinates
double[] x = IntStream.range(0, n).asDoubleStream().toArray();
double[] y = IntStream.range(0, m).asDoubleStream().toArray();
Heatmap.of(x, y, z, 64).figure().show();

// With string row/column labels (e.g. correlation matrix)
String[] genes = {"BRCA1", "TP53", "EGFR"};
Heatmap.of(genes, genes, correlation, 16).figure().show();
```

The color bar on the right side maps colors to the 1st–99th percentile
range of the data (outlier-robust). `NaN` values are rendered white.
Hovering shows `"rowLabel, columnLabel"` when labels are provided.

---

### 6.8 Contour Plot

```java
double[][] z = ...;

// Standalone contour (10 levels, linear)
Contour.of(z).figure().show();

// Overlay on a heatmap
Figure fig = Heatmap.of(z, 256).figure();
fig.add(Contour.of(z));
fig.show();

// Custom number of levels
Contour.of(z, 20, false).figure().show();      // 20 linear levels
Contour.of(z, 10, true).figure().show();       // 10 log-scale levels

// With explicit x/y coordinates
Contour.of(x, y, z).figure().show();

// Explicit contour levels (values at which to draw isolines)
double[] levels = {0.1, 0.5, 1.0, 2.0};
new Contour(z, levels).figure().show();
```

---

### 6.9 3-D Surface Plot

```java
double[][] z = ...;   // m × n grid

// Regular grid (x, y auto-assigned to 0.5, 1.5, 2.5, …)
Surface.of(z).figure().show();

// With color palette
Surface.of(z, Palette.jet(256, 1.0f)).figure().show();

// Explicit x/y coordinates
double[] x = ...;
double[] y = ...;
Surface.of(x, y, z, Palette.heat(128)).figure().show();

// Irregular mesh (m × n × 3 data)
double[][][] mesh = ...;   // mesh[i][j] = {xi, yj, zij}
new Surface(mesh, Palette.jet(256)).figure().show();
```

The 3-D window supports mouse-drag rotation and scroll-wheel zoom. The
painter's algorithm is used for triangle ordering, giving correct occlusion
without a depth buffer.

---

### 6.10 Scree Plot

Used after PCA to decide how many components to retain.

```java
// varianceProportion[i] = fraction of total variance in PC i+1
double[] varianceProportion = pca.varianceProportion();
new ScreePlot(varianceProportion).figure().show();
```

The plot draws two lines: **individual variance** (red) and
**cumulative variance** (blue). The x-axis is labelled `PC1`, `PC2`, …

---

### 6.11 Sparse Matrix Plot

Visualizes the sparsity pattern (and optionally, the entry values) of a
`SparseMatrix`.

```java
SparseMatrix A = SparseMatrix.text(path);

// Structure only (black dots)
SparseMatrixPlot.of(A).figure().setTitle("Sparsity pattern").show();

// Values as colors
SparseMatrixPlot.of(A, Palette.jet(256)).figure().show();
```

---

### 6.12 Dendrogram

Visualizes hierarchical clustering results.

```java
// merge  : (n-1) × 2 merge matrix from HierarchicalClustering
// height : (n-1) non-decreasing merge heights
int[][] merge  = hclust.tree();
double[] height = hclust.height();

new Dendrogram(merge, height).figure().show();

// Custom color
new Dendrogram(merge, height, Color.DARK_GRAY).figure().show();
```

---

### 6.13 Text (Label) Plot

Annotates a scatter of 2-D or 3-D points with text labels.

```java
// points[i] is the coordinate; texts[i] is the label
double[][] coords = ...;
String[] texts    = ...;
TextPlot.of(coords, texts).figure().show();

// Custom color
TextPlot.of(coords, texts, Color.BLUE).figure().show();

// Overlay text annotations on an existing scatter plot
Figure fig = ScatterPlot.of(coords, '.').figure();
fig.add(TextPlot.of(coords, texts));
fig.show();
```

---

### 6.14 Staircase Plot

A step function (staircase) plot — useful for empirical CDFs, step-function
classifiers, etc.

```java
double[][] steps = {{0,0},{1,1},{2,1},{3,2},{4,2},{5,3}};
StaircasePlot.of(steps).figure().show();

// Custom color
StaircasePlot.of(steps, Color.GREEN).figure().show();
```

---

## 7. Multi-Figure Layout (SPLOM)

`MultiFigurePane` arranges multiple `Canvas` panels in a grid.

### Manual grid

```java
MultiFigurePane panel = new MultiFigurePane(2, 3);   // 2 rows, 3 columns
panel.add(new Canvas(fig1));
panel.add(new Canvas(fig2));
// ...
panel.window().setVisible(true);
```

### Scatter Plot Matrix (SPLOM)

```java
// Uniform color
MultiFigurePane.splom(iris, '.', Color.BLUE).window();

// Category-colored
MultiFigurePane.splom(iris, '*', "class").window();
```

The SPLOM plots every numeric column pair. When a category column is provided
it is excluded from the axes but used to color each point group.

---

## 8. Axes, Labels, Ticks & Grid

All axis-level configuration is accessed via `figure.getAxis(i)`, where
`i = 0` is the x-axis, `i = 1` is the y-axis, and `i = 2` is the z-axis (for
3-D figures).

```java
Figure fig = plot.figure();

// Axis labels (shorthand for setting all at once)
fig.setAxisLabels("Sepal Length (cm)", "Sepal Width (cm)");

// Set a single axis label
fig.setAxisLabel(0, "Component 1");

// Custom tick marks
double[] positions = {0.5, 1.5, 2.5, 3.5};
String[] tickLabels = {"A", "B", "C", "D"};
fig.getAxis(0).setTicks(tickLabels, positions);

// Rotate axis labels (in radians; useful when labels are long)
fig.getAxis(0).setRotation(-Math.PI / 2);   // -90°

// Toggle grid lines
fig.getAxis(0).setGridVisible(false);
fig.getAxis(1).setGridVisible(true);

// Toggle tick marks and frame border
fig.getAxis(0).setTickVisible(false);
fig.getAxis(0).setFrameVisible(false);
```

---

## 9. Legends

Legends are displayed automatically when a `Plot` has legends and
`figure.isLegendVisible()` is `true` (the default).

```java
// Show / hide all legends
fig.setLegendVisible(false);

// Per-plot: supply Legend[] when constructing a plot
Legend[] legends = {
    new Legend("Class A", Color.RED),
    new Legend("Class B", Color.BLUE)
};
new ScatterPlot(points, legends).figure().show();
```

Legends are rendered as a color-swatch + text in the upper-right margin.

---

## 10. Headless / Programmatic Export

`Figure.toBufferedImage(width, height)` works without a display (pass
`-Djava.awt.headless=true`):

```java
Figure fig = Heatmap.of(z, Palette.jet(256)).figure();
fig.setTitle("Z-scores");

// Export to PNG
BufferedImage img = fig.toBufferedImage(1920, 1080);
ImageIO.write(img, "png", Path.of("heatmap.png").toFile());

// Convert to byte array (for REST endpoints, etc.)
ByteArrayOutputStream baos = new ByteArrayOutputStream();
ImageIO.write(img, "png", baos);
byte[] pngBytes = baos.toByteArray();
```

The `Headless` utility class also enables Swing painting in a headless
environment by providing a surrogate peer:

```java
Canvas canvas = new Canvas(figure);
Headless h = new Headless(canvas, 800, 600);
h.show();
// canvas is now laid-out and can be painted off-screen
```

---

## 11. Architecture Overview

```
┌────────────────────────────────────────────────────────────────┐
│  User code                                                     │
│  ScatterPlot.of(…) ──► Figure ──► FigurePane ──► JFrame       │
└───────────────────┬───────────────────────────────────────────┘
                    │
        ┌───────────▼────────────┐
        │  Figure                │
        │  ├─ Base (coord space) │
        │  ├─ Axis[]             │
        │  └─ List<Shape>        │
        │       ├─ Plot          │◄── ScatterPlot, LinePlot, …
        │       │    └─ Point[]  │
        │       ├─ Label         │
        │       └─ GridLabel     │
        └───────────┬────────────┘
                    │ paint(Renderer)
        ┌───────────▼────────────┐
        │  Canvas (JComponent)   │
        │  └─ Renderer           │
        │       └─ Projection2D  │
        │           or           │
        │          Projection3D  │
        └────────────────────────┘
```

| Class | Responsibility |
|-------|---------------|
| `Base` | Logical coordinate space; extends/rounds bounds to clean grid values |
| `Axis` | Tick generation, label rendering, grid lines, frame |
| `Shape` | Abstract base — carries color; knows how to `paint(Renderer)` |
| `Plot` | `Shape` subclass that also knows its data bounds (`getLowerBound`/`getUpperBound`) and optional legends/tooltips |
| `Renderer` | Adapts `Graphics2D` calls to logical coordinates via a `Projection` |
| `Projection2D` / `Projection3D` | Maps logical (data) coordinates to screen (pixel) coordinates |
| `FigurePane` | Swing `JPanel` wrapping a `Canvas` with a toolbar |
| `MultiFigurePane` | Swing `JPanel` holding a grid of `Canvas` components |
| `Palette` | Static utility — CSS color parser + color-ramp generators |

---

## 12. Quick-Reference Table

| Plot type | Factory method | Main input |
|-----------|---------------|------------|
| Scatter 2-D | `ScatterPlot.of(double[][] points, char mark)` | n×2 matrix |
| Scatter 3-D | `ScatterPlot.of(double[][] points, char mark)` | n×3 matrix |
| Scatter (groups) | `ScatterPlot.of(double[][] x, String[] labels, char mark)` | n×2 + class strings |
| Scatter (DataFrame) | `ScatterPlot.of(DataFrame, "x", "y", "class", mark)` | DataFrame columns |
| Line | `LinePlot.of(double[] y, Color)` | y-values (x auto) |
| Line | `LinePlot.of(double[][] xy, Line.Style, Color)` | n×2 matrix |
| Bar | `BarPlot.of(double[] heights)` | value array |
| Bar (grouped) | `BarPlot.of(double[][] data, String[] labels)` | k groups × n values |
| Box | `new BoxPlot(double[][] data, String[] labels)` | one array per group |
| Histogram | `Histogram.of(double[] data, int k, boolean prob)` | raw sample |
| Histogram | `Histogram.of(double[] data, double[] breaks, boolean prob)` | raw sample + breaks |
| Q-Q (1-sample) | `QQPlot.of(double[] data)` | vs. standard normal |
| Q-Q (1-sample) | `QQPlot.of(double[] data, Distribution d)` | vs. any distribution |
| Q-Q (2-sample) | `QQPlot.of(double[] x, double[] y)` | two samples |
| Heatmap | `Heatmap.of(double[][] z, Color[] palette)` | m×n matrix |
| Heatmap (labels) | `Heatmap.of(String[] rows, String[] cols, double[][] z)` | m×n matrix + labels |
| Contour | `Contour.of(double[][] z)` | m×n matrix |
| Surface (regular) | `Surface.of(double[][] z, Color[] palette)` | m×n height grid |
| Surface (mesh) | `Surface.of(double[] x, double[] y, double[][] z, palette)` | explicit coordinates |
| Scree | `new ScreePlot(double[] varianceProportion)` | PCA variance fractions |
| Sparse matrix | `SparseMatrixPlot.of(SparseMatrix)` | `SparseMatrix` |
| Dendrogram | `new Dendrogram(int[][] merge, double[] height)` | HClust result |
| Text labels | `TextPlot.of(double[][] coords, String[] texts)` | points + strings |
| Staircase | `StaircasePlot.of(double[][] steps)` | step-function points |
| SPLOM | `MultiFigurePane.splom(DataFrame, mark, "class")` | DataFrame |


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

