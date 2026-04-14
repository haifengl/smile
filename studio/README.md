# SMILE CLI User Guide

SMILE ships with a command-line launcher (`smile` / `smile.bat`) that exposes
five entry points. Depending on the first argument you pass (or the absence of one),
the launcher routes to one of:

| Invocation | Description                                           |
|---|-------------------------------------------------------|
| `smile` *(no args)* | Open the [SMILE Studio](STUDIO.md) GUI                |
| `smile shell` | Start the **Java (JShell)** interactive REPL          |
| `smile scala` | Start the **Scala 3** interactive REPL                |
| `smile train …` | **Train** a supervised learning model from a file     |
| `smile predict …` | **Predict** on a file using a saved model             |
| `smile serve …` | **Serve** a saved model as an HTTP prediction service |

---

## Table of Contents

1. [Installation & Setup](#1-installation--setup)
2. [Entry Point](#2-entry-point)
3. [Java Shell (`smile shell`)](#3-java-shell-smile-shell)
4. [Scala REPL (`smile scala`)](#4-scala-repl-smile-scala)
5. [Training Models (`smile train`)](#5-training-models-smile-train)
   - 5.1 [Global Options](#51-global-options)
   - 5.2 [Formula Auto-detection](#52-formula-auto-detection)
   - 5.3 [Classification Algorithms](#53-classification-algorithms)
   - 5.4 [Regression Algorithms](#54-regression-algorithms)
   - 5.5 [Cross-validation & Ensembles](#55-cross-validation--ensembles)
   - 5.6 [Feature Transformation](#56-feature-transformation)
   - 5.7 [Model Metadata](#57-model-metadata)
6. [Batch Prediction (`smile predict`)](#6-batch-prediction-smile-predict)
7. [Online Serving (`smile serve`)](#7-online-serving-smile-serve)
8. [Supported File Formats](#8-supported-file-formats)
9. [JVM Tuning (`conf/smile.ini`)](#9-jvm-tuning-confsmileini)
10. [Tutorials](#10-tutorials)
    - 10.1 [End-to-end: Iris Classification](#101-end-to-end-iris-classification)
    - 10.2 [End-to-end: Housing Price Regression](#102-end-to-end-housing-price-regression)
    - 10.3 [Cross-validation & Ensemble Workflow](#103-cross-validation--ensemble-workflow)
    - 10.4 [Online Prediction Service](#104-online-prediction-service)
    - 10.5 [Interactive Java Shell Session](#105-interactive-java-shell-session)
    - 10.6 [Interactive Scala REPL Session](#106-interactive-scala-repl-session)

---

## 1. Installation & Setup

### Prerequisites

- **Java 25** or later (the bundled JBR in the distribution is sufficient)
- **Python 3** (optional — required only for the Studio notebook's Python kernel
  and the `ty` language server)
- **ARPACK / OpenBLAS** (optional — for faster numerical operations)

### First-time Setup

Run the provided setup script once after unzipping the distribution:

```bash
# macOS / Linux
bin/setup

# Windows
bin\setup.bat
```

The script installs native libraries (`libarpack`, `libopenblas`) via the
system package manager and creates a Python virtual environment with the
packages listed in `conf/requirements.txt`.

### Running the Launcher

```bash
# macOS / Linux
bin/smile [command] [options]

# Windows
bin\smile.bat [command] [options]
```

The launcher reads JVM options from `conf/smile.ini` before forwarding the
remaining arguments to `smile.Main`.

---

## 2. Entry Point

`smile.Main.main(String[] args)` is the single entry point for all CLI and GUI
functionality.  The routing logic is:

```
args[0]   →  destination
─────────────────────────────────────────
"train"   →  smile.shell.Train   (picocli)
"predict" →  smile.shell.Predict (picocli)
"serve"   →  smile.shell.Serve   (picocli)
"scala"   →  smile.shell.ScalaREPL.start()
"shell"   →  smile.shell.JShell.start()
(other)   →  smile.studio.SmileStudio.start()
```

The system property `smile.home` points to the distribution root and
is used by all launchers to locate resources such as `bin/predef.jsh`,
`bin/predef.sc`, and `serve/quarkus-run.jar`.

For the User Guide for SMILE Studio GUI, see [STUDIO.md](STUDIO.md).
The rest of this document focuses on the CLI entry points (`shell`, `scala`,
`train`, `predict`, `serve`).

---

## 3. Java Shell (`smile shell`)

```bash
smile shell [jshell-options…]
```

Starts an interactive [JShell](https://docs.oracle.com/en/java/docs/jshell/)
session pre-configured for SMILE development.

### What Happens at Startup

1. The full SMILE class-path is added with `--class-path`.
2. JVM flags are injected into the remote JVM started by JShell:
   - `-XX:MaxMetaspaceSize=1024M`, `-Xss4M`, `-XX:MaxRAMPercentage=75`
   - `-XX:+UseZGC` for low-latency garbage collection
   - `--add-opens java.base/java.nio=ALL-UNNAMED` and `--enable-native-access`
3. `DEFAULT` and `PRINTING` JShell startup scripts are loaded (making
   `println()` available without a class qualifier).
4. **`bin/predef.jsh`** is loaded, which:
   - Defines the custom `smile` JShell feedback mode (compact, color output).
   - Imports every major SMILE package so you can start coding immediately
     without writing import statements.
   - Prints the SMILE ASCII logo and version banner.
5. Command history is persisted across sessions via `java.util.prefs.Preferences`.

### Pre-imported Packages

The following are imported automatically by `predef.jsh`:

```
smile.util.*          smile.graph.*          smile.math.*
smile.stat.*          smile.data.*           smile.data.formula.*
smile.data.measure.*  smile.data.type.*      smile.data.vector.*
smile.io.*            smile.plot.swing.*     smile.interpolation.*
smile.validation.*    smile.classification.* smile.regression.*
smile.feature.*       smile.clustering.*     smile.hpo.*
smile.vq.*            smile.manifold.*       smile.sequence.*
smile.nlp.*           smile.wavelet.*        smile.tensor.*
smile.anomaly.*       smile.association.*
```

All `java.lang.Math` and `smile.math.MathEx` static methods are also imported
with `import static`.

### Passing Extra JShell Arguments

Any arguments after `shell` are forwarded directly to JShell.  For example,
to execute a script file non-interactively:

```bash
smile shell examples/toy.jsh
```

### Saving and Loading Sessions

JShell's `/save` and `/open` commands work as normal:

```
smile> /save session.jsh
smile> /open session.jsh
```

---

## 4. Scala REPL (`smile scala`)

```bash
smile scala [scalac-options…]
```

Starts a [Scala 3 / Dotty REPL](https://docs.scala-lang.org/scala3/guides/repl/)
pre-configured for SMILE.

### What Happens at Startup

1. `-usejavacp` ensures the SMILE class-path is inherited from the JVM.
2. **`bin/predef.sc`** is loaded via `-repl-init-script`, which:
   - Imports all major Scala SMILE packages including the convenience `smile._`
     wildcard.
   - Imports implicit conversion helpers and DSL shortcuts (e.g.
     `"class" ~ "."` formula syntax, `read.arff(…)`, `randomForest(…)`).
   - Prints the SMILE ASCII logo and version banner.

### Pre-imported Packages

```scala
import smile._              // top-level Smile DSL
import smile.io._           // Read/Write helpers
import smile.data.formula._ // formula DSL
import smile.classification._
import smile.regression.{lm, ridge, lasso, gpr}
import smile.feature.*
import smile.clustering.*
// … and many more (see predef.sc)
```

---

## 5. Training Models (`smile train`)

```
smile train -d <file> -m <model> [global-options] <algorithm> [algo-options]
```

`smile train` trains a supervised learning model from a data file and
serializes it to disk.  It is built with [picocli](https://picocli.info/) and
uses a **two-level command structure**: global options come first, then the
algorithm sub-command with its own options.

### 5.1 Global Options

| Option | Short | Required | Default | Description |
|---|---|---|---|---|
| `--data <file>` | `-d` | ✔ | — | Training data file path |
| `--model <file>` | `-m` | ✔ | — | Output model file path (`.sml`) |
| `--test <file>` | | | — | Optional hold-out test file |
| `--format <fmt>` | | | auto-detect | Data format (see §8) |
| `--formula <expr>` | | | auto-detect | Model formula, e.g. `class ~ .` |
| `--model-id <id>` | | | — | Metadata tag: model identifier |
| `--model-version <ver>` | | | — | Metadata tag: model version string |
| `--kfold <k>` | `-k` | | `1` | Enable *k*-fold cross-validation |
| `--round <n>` | `-r` | | `1` | Repeated cross-validation rounds |
| `--ensemble` | `-e` | | `false` | Build ensemble from CV models |
| `--seed <n>` | `-s` | | `0` (off) | RNG seed for reproducibility |
| `--help` | `-h` | | | Print help and exit |
| `--version` | `-V` | | | Print SMILE version and exit |

### 5.2 Formula Auto-detection

If `--formula` is not specified, the response variable is chosen automatically
by inspecting the column names in the following priority order:

1. A column named **`class`**
2. A column named **`target`**
3. A column named **`y`**
4. The **first** column in the file

For the most predictable behaviour, always supply `--formula` explicitly, e.g.:

```bash
smile train -d data.csv --formula "price ~ ." -m model.sml ols
```

### 5.3 Classification Algorithms

All classification sub-commands default to classification mode.  Algorithms
that also support regression expose `--regression` to switch modes.

#### `random-forest` — Random Forest

```
smile train -d <file> -m <model> random-forest [options]
```

| Option | Description |
|---|---|
| `--regression` | Train regression instead of classification |
| `--trees <n>` | Number of trees (default: 500) |
| `--mtry <n>` | Features considered per split |
| `--split <rule>` | Split rule: `GINI`, `ENTROPY`, `CLASSIFICATION_ERROR` |
| `--max-depth <n>` | Maximum tree depth |
| `--max-nodes <n>` | Maximum leaf nodes per tree |
| `--node-size <n>` | Minimum samples per leaf |
| `--sampling <rate>` | Subsample rate, e.g. `0.8` |
| `--class-weight <w>` | Comma-separated class weights, e.g. `1,2` |

#### `gradient-boost` — Gradient Boosted Trees

```
smile train -d <file> -m <model> gradient-boost [options]
```

| Option | Description |
|---|---|
| `--regression` | Train regression instead of classification |
| `--trees <n>` | Number of boosting iterations |
| `--shrinkage <rate>` | Learning rate in `(0, 1]`, e.g. `0.1` |
| `--max-depth <n>` | Maximum tree depth |
| `--max-nodes <n>` | Maximum leaf nodes |
| `--node-size <n>` | Minimum samples per leaf |
| `--sampling <rate>` | Subsample rate |

#### `ada-boost` — Adaptive Boosting *(classification only)*

```
smile train -d <file> -m <model> ada-boost [options]
```

| Option | Description |
|---|---|
| `--trees <n>` | Number of weak classifiers |
| `--max-depth <n>` | Maximum tree depth |
| `--max-nodes <n>` | Maximum leaf nodes |
| `--node-size <n>` | Minimum samples per leaf |

#### `cart` — Classification and Regression Tree

```
smile train -d <file> -m <model> cart [options]
```

| Option | Description |
|---|---|
| `--regression` | Train regression instead of classification |
| `--split <rule>` | Split rule: `GINI`, `ENTROPY`, `CLASSIFICATION_ERROR` |
| `--max-depth <n>` | Maximum tree depth |
| `--max-nodes <n>` | Maximum leaf nodes |
| `--node-size <n>` | Minimum samples per leaf |

#### `logistic` — Logistic Regression *(classification only)*

```
smile train -d <file> -m <model> logistic [options]
```

| Option | Description |
|---|---|
| `--transform <rule>` | Feature transformation (see §5.6) |
| `--lambda <λ>` | L2 regularisation strength |
| `--iterations <n>` | Maximum number of LBFGS iterations |
| `--tolerance <ε>` | Convergence tolerance |

#### `fisher` — Fisher's Linear Discriminant *(classification only)*

```
smile train -d <file> -m <model> fisher [options]
```

| Option | Description |
|---|---|
| `--transform <rule>` | Feature transformation (see §5.6) |
| `--dimension <d>` | Dimensionality of the projected space |
| `--tolerance <ε>` | Singular covariance tolerance |

#### `lda` — Linear Discriminant Analysis *(classification only)*

```
smile train -d <file> -m <model> lda [options]
```

| Option | Description |
|---|---|
| `--transform <rule>` | Feature transformation (see §5.6) |
| `--priori <p0,p1,…>` | Comma-separated prior class probabilities |
| `--tolerance <ε>` | Singular covariance tolerance |

#### `qda` — Quadratic Discriminant Analysis *(classification only)*

```
smile train -d <file> -m <model> qda [options]
```

Same options as `lda`.

#### `rda` — Regularized Discriminant Analysis *(classification only)*

```
smile train -d <file> -m <model> rda --alpha <α> [options]
```

| Option | Required | Description |
|---|---|---|
| `--alpha <α>` | ✔ | Regularisation factor in `[0, 1]`; `0` = QDA, `1` = LDA |
| `--transform <rule>` | | Feature transformation (see §5.6) |
| `--priori <p0,p1,…>` | | Prior class probabilities |
| `--tolerance <ε>` | | Singular covariance tolerance |

#### `mlp` — Multilayer Perceptron

```
smile train -d <file> -m <model> mlp --layers <spec> [options]
```

| Option | Required | Description |
|---|---|---|
| `--layers <spec>` | ✔ | Network architecture, e.g. `ReLU(100)\|Sigmoid(30)` |
| `--regression` | | Train regression instead of classification |
| `--transform <rule>` | | Feature transformation (see §5.6) |
| `--epochs <n>` | | Training epochs |
| `--mini-batch <n>` | | Mini-batch size |
| `--learning-rate <sched>` | | Learning rate schedule (see below) |
| `--momentum <sched>` | | Momentum schedule |
| `--weight-decay <λ>` | | L2 weight decay |
| `--clip_norm <n>` | | Gradient clipping norm |
| `--rho <ρ>` | | RMSProp rho |
| `--epsilon <ε>` | | RMSProp epsilon |

**Layer specification** — pipe-separated list of `<activation>(<units>)`:

```
ReLU(256)|ReLU(128)|Sigmoid(64)
```

Supported activations: `ReLU`, `Sigmoid`, `Tanh`, `SoftMax`, `Linear`.

**Learning rate schedules** (also applies to `--momentum`):

| Format | Description |
|---|---|
| `0.01` | Constant rate |
| `linear(init, steps, final)` | Linear decay |
| `inverse(init, decay)` | Inverse time decay |
| `exp(init, decay)` | Exponential decay |
| `polynomial(init, steps, power)` | Polynomial decay |
| `piecewise(…)` | Piecewise constant |

#### `svm` — Support Vector Machine

```
smile train -d <file> -m <model> svm --kernel <fn> [options]
```

| Option | Required | Description |
|---|---|---|
| `--kernel <fn>` | ✔ | Kernel function (see below) |
| `--regression` | | Train SVR instead of SVC |
| `--transform <rule>` | | Feature transformation (see §5.6) |
| `-C <value>` | | Soft margin penalty |
| `--epsilon <ε>` | | ε-insensitive hinge loss (SVR only) |
| `--ovr` | | One-vs-Rest multi-class strategy |
| `--ovo` | | One-vs-One multi-class strategy |
| `--tolerance <ε>` | | SMO convergence tolerance |

**Kernel functions**: `Gaussian(σ)`, `Linear`, `Polynomial(degree, scale, offset)`,
`Laplacian(σ)`, `PearsonVII(ω, ν)`, `Hellinger`, `Tanh(scale, offset)`.

#### `rbf` — Radial Basis Function Network

```
smile train -d <file> -m <model> rbf --neurons <n> [options]
```

| Option | Required | Description                       |
|---|---|-----------------------------------|
| `--neurons <n>` | ✔ | Number of RBF neurons (centres)   |
| `--regression` | | Train regression RBF              |
| `--transform <rule>` | | Feature transformation (see §5.6) |
| `--normalize` | | Use normalized RBF network       |

### 5.4 Regression Algorithms

The following algorithms are **regression-only** and do not accept `--regression`.

#### `ols` — Ordinary Least Squares

```
smile train -d <file> -m <model> --formula "y ~ ." ols [options]
```

| Option | Description |
|---|---|
| `--method <qr\|svd>` | Fitting method: `qr` (default) or `svd` |
| `--stderr` | Compute standard errors of parameter estimates |
| `--recursive` | Use recursive least squares |

#### `lasso` — LASSO Regression

```
smile train -d <file> -m <model> --formula "y ~ ." lasso --lambda <λ> [options]
```

| Option | Required | Description |
|---|---|---|
| `--lambda <λ>` | ✔ | L1 regularisation strength |
| `--iterations <n>` | | Maximum coordinate-descent iterations |
| `--tolerance <ε>` | | Relative target duality-gap stopping criterion |

#### `ridge` — Ridge Regression

```
smile train -d <file> -m <model> --formula "y ~ ." ridge --lambda <λ>
```

| Option | Required | Description |
|---|---|---|
| `--lambda <λ>` | ✔ | L2 regularisation strength |

#### `elastic-net` — Elastic Net

```
smile train -d <file> -m <model> --formula "y ~ ." elastic-net --lambda1 <λ1> --lambda2 <λ2> [options]
```

| Option | Required | Description |
|---|---|---|
| `--lambda1 <λ1>` | ✔ | L1 penalty |
| `--lambda2 <λ2>` | ✔ | L2 penalty |
| `--iterations <n>` | | Maximum iterations |
| `--tolerance <ε>` | | Stopping tolerance |

#### `gaussian-process` — Gaussian Process Regression

```
smile train -d <file> -m <model> --formula "y ~ ." gaussian-process --kernel <fn> --noise <σ²> [options]
```

| Option | Required | Description                          |
|---|---|--------------------------------------|
| `--kernel <fn>` | ✔ | Kernel function (same syntax as SVM) |
| `--noise <σ²>` | ✔ | Noise variance                       |
| `--normalize` | | Normalize the response variable     |
| `--transform <rule>` | | Feature transformation (see §5.6)    |
| `--iterations <n>` | | Maximum HPO iterations               |
| `--tolerance <ε>` | | HPO stopping tolerance               |

### 5.5 Cross-validation & Ensembles

```bash
# 5-fold cross-validation, 3 repetitions
smile train -d data.arff -m model.sml -k 5 -r 3 random-forest --trees 100

# 5-fold CV, build ensemble of the fold models
smile train -d data.arff -m model.sml -k 5 --ensemble random-forest --trees 100
```

When `-k` > 1, the trainer prints three metric blocks:

```
Training metrics:   …
Validation metrics: …   ← stratified CV average
Test metrics:       …   ← only when --test is supplied
```

The saved model is the **full model** retrained on the entire training set
(unless `--ensemble` is used, in which case it is the ensemble of fold models).

### 5.6 Feature Transformation

Many algorithms accept a `--transform <rule>` option that applies a
`smile.feature.transform` pipeline before fitting.  Supported values:

| Value | Class | Description                                        |
|---|---|----------------------------------------------------|
| `standardizer` | `Standardizer` | Zero mean, unit variance                           |
| `winsor(lo,hi)` | `WinsorScaler` | Winsorise at percentiles, e.g. `winsor(0.01,0.99)` |
| `minmax` | `MinMaxScaler` | Scale to `[0, 1]`                                  |
| `MaxAbs` | `MaxAbsScaler` | Scale by maximum absolute value                    |
| `L1` | `Normalizer` | L1 normalize each sample                           |
| `L2` | `Normalizer` | L2 normalize each sample                           |
| `Linf` | `Normalizer` | L∞ normalize each sample                          |

### 5.7 Model Metadata

SMILE model files are standard Java serialized objects that also carry a
`Properties` tag map.  Two well-known keys are `id` and `version`:

```bash
smile train -d data.arff -m model.sml \
  --model-id    "iris-classifier-v1" \
  --model-version "2.0.0"            \
  random-forest --trees 200
```

You can store and retrieve arbitrary tags programmatically:

```java
var model = (ClassificationModel) Read.object(Path.of("model.sml"));
String id  = model.getTag(Model.ID);       // "iris-classifier-v1"
String ver = model.getTag(Model.VERSION);  // "2.0.0"
```

---

## 6. Batch Prediction (`smile predict`)

```
smile predict <data-file> --model <model-file> [options]
```

Loads a saved model, runs it over every row in `<data-file>`, and writes
one prediction per line to `stdout`.

### Options

| Option | Short | Required | Description |
|---|---|---|---|
| `<data-file>` | | ✔ | Input data file (positional argument) |
| `--model <file>` | `-m` | ✔ | Saved model file (`.sml`) |
| `--format <fmt>` | | | Data file format (see §8) |
| `--probability` | `-p` | | Append posterior probabilities for soft classifiers |

### Output Format

**Classification without `--probability`** — one predicted class label per line:

```
Iris-setosa
Iris-versicolor
Iris-setosa
…
```

**Classification with `--probability`** — label followed by per-class
probabilities (space-separated, 4 decimal places):

```
Iris-setosa     0.9821 0.0179 0.0000
Iris-versicolor 0.0200 0.8512 0.1288
…
```

> **Note:** `--probability` only applies to *soft* classifiers (those that
> implement posterior probability estimation, such as Random Forest, Logistic
> Regression, MLP, and SVM).  For hard classifiers the flag is silently
> ignored and only the class label is printed.

**Regression** — one numeric value per line (formatted by `Strings.format`):

```
60323.00
61122.00
…
```

### Redirecting Output

```bash
# Save predictions to a file
smile predict test.arff --model model.sml > predictions.txt

# Pass probabilities through a downstream tool
smile predict test.csv --model model.sml --probability | cut -d' ' -f2-
```

---

## 7. Online Serving (`smile serve`)

```
smile serve --model <path> [options]
```

Launches a Quarkus-based HTTP prediction server.  The server reads the model
from `<path>` at startup and exposes a REST endpoint for real-time inference.

### Options

| Option | Required | Default | Description |
|---|---|---|---|
| `--model <path>` | ✔ | — | Model file or folder |
| `--host <addr>` | | `0.0.0.0` | Network interface to bind |
| `--port <n>` | | `8080` | HTTP port |

### How It Works

`Serve` spawns a new JVM process running
`serve/quarkus-run.jar` (found under `$smile.home/serve/`) and passes the
model path and network settings as system properties:

```
-Dsmile.serve.model=<path>
-Dquarkus.http.host=<host>
-Dquarkus.http.port=<port>
```

The spawned process inherits stdin/stdout/stderr (`inheritIO()`), so logs
appear on the terminal.  The launcher waits for the child process to exit.

### Example

```bash
# Train a model
smile train -d iris.arff -m iris.sml random-forest --trees 200

# Serve it
smile serve --model iris.sml --port 9090
```

Once started, send a prediction request:

```bash
curl -X POST http://localhost:9090/predict \
     -H "Content-Type: application/json" \
     -d '{"sepallength":5.1,"sepalwidth":3.5,"petallength":1.4,"petalwidth":0.2}'
```

---

## 8. Supported File Formats

`smile train` and `smile predict` use `smile.io.Read.data()` to load data.
The format is auto-detected from the file extension; you can override it with
`--format`.

| Extension / Format | Description |
|---|---|
| `.arff` | Weka ARFF (with schema, nominal attributes) |
| `.csv` | Comma-separated values (header row expected) |
| `.tsv` / `.txt` | Tab-separated values |
| `.json` | JSON array of objects |
| `.parquet` | Apache Parquet (column-store) |
| `.avro` | Apache Avro |
| `.sas7bdat` | SAS data file |
| SQLite URL | `jdbc:sqlite:<path>` — full SQL support via `smile shell` |

**ARFF is recommended** for training data because it carries full schema
information (column types, nominal levels) which eliminates the need to
specify `--formula` manually.

---

## 9. JVM Tuning (`conf/smile.ini`)

The file `conf/smile.ini` contains JVM flags that are passed to every `smile`
invocation.  The defaults are tuned for a modern multi-core machine:

```ini
# Heap size
-J-Xmx4G -J-Xms2G

# ZGC for low-latency GC pauses
-J-XX:+UseZGC

# Compact object headers (experimental, Java 24+)
-J-XX:+UnlockExperimentalVMOptions -J-XX:+UseCompactObjectHeaders

# NUMA-aware allocation for multi-socket machines
-J-XX:+UseNUMA

# String deduplication (useful when parsing large CSV files)
-J-XX:+UseStringDeduplication
```

Key settings to adjust:

| Goal | Change |
|---|---|
| More heap for large datasets | `-J-Xmx8G` or `-J-XX:MaxRAMPercentage=75` |
| Reproducible GC pauses | Keep `-J-XX:+UseZGC` |
| Enable large TLB pages | Uncomment `-J-XX:+UseLargePages` |
| Reduce GC pressure | Increase `-J-Xms` closer to `-J-Xmx` |

---

## 10. Tutorials

### 10.1 End-to-end: Iris Classification

```bash
# 1. Train a Random Forest on iris
smile train \
  --data   examples/iris.arff \
  --model  iris_rf.sml         \
  --model-id "iris-rf"         \
  --model-version "1.0"        \
  random-forest --trees 200 --max-depth 10

# Output:
# Training metrics: {accuracy=1.000, …}

# 2. Evaluate on a test split
smile train \
  --data   train.arff \
  --test   test.arff  \
  --model  iris_rf.sml \
  random-forest --trees 200

# Output:
# Training metrics:  {accuracy=1.000, …}
# Test metrics:      {accuracy=0.973, …}

# 3. Predict on new data
smile predict new_flowers.arff --model iris_rf.sml

# 4. Predict with class probabilities
smile predict new_flowers.arff --model iris_rf.sml --probability
```

### 10.2 End-to-end: Housing Price Regression

```bash
# Train OLS on Boston Housing (response column: "price")
smile train \
  --data    housing.arff          \
  --formula "price ~ ."           \
  --model   housing_ols.sml       \
  ols --stderr

# Training metrics: {RMSE=4.679, MAE=3.389, R2=0.741}

# Ridge regression with stronger regularisation
smile train \
  --data    housing.arff          \
  --formula "price ~ ."           \
  --model   housing_ridge.sml     \
  ridge --lambda 1.0

# LASSO for sparse solutions
smile train \
  --data    housing.arff          \
  --formula "price ~ ."           \
  --model   housing_lasso.sml     \
  lasso --lambda 5.0

# Elastic Net
smile train \
  --data    housing.arff          \
  --formula "price ~ ."           \
  --model   housing_en.sml        \
  elastic-net --lambda1 1.0 --lambda2 0.5

# Predict
smile predict housing_test.arff --model housing_ridge.sml
```

### 10.3 Cross-validation & Ensemble Workflow

```bash
# 10-fold stratified CV, averaged metrics
smile train \
  --data  iris.arff              \
  --model iris_cv.sml            \
  --kfold 10                     \
  random-forest --trees 100

# Training metrics:    {accuracy=1.000, …}
# Validation metrics:  {accuracy=0.960, …}   ← 10-fold CV average

# 5-fold CV with 3 repetitions for more stable estimate
smile train \
  --data  iris.arff              \
  --model iris_cv3.sml           \
  --kfold 5 --round 3            \
  random-forest --trees 100

# 5-fold CV, save the ENSEMBLE of fold models (not the final retrained model)
smile train \
  --data     iris.arff           \
  --model    iris_ensemble.sml   \
  --kfold    5                   \
  --ensemble                     \
  random-forest --trees 100

# Reproducible run
smile train \
  --data  iris.arff --model iris_seed.sml --seed 42 \
  random-forest --trees 100
```

### 10.4 Online Prediction Service

```bash
# Train
smile train -d iris.arff -m iris.sml random-forest --trees 200

# Serve on port 8080 (all interfaces)
smile serve --model iris.sml

# Serve on a specific interface and port
smile serve --model iris.sml --host 127.0.0.1 --port 9090

# Query (after server is up)
curl http://localhost:9090/predict \
  -H "Content-Type: application/json" \
  -d '{"sepallength":6.3,"sepalwidth":2.5,"petallength":5.0,"petalwidth":1.9}'
# → "Iris-virginica"
```

### 10.5 Interactive Java Shell Session

Launch and explore the iris dataset:

```bash
smile shell
```

```java
smile> var iris = Read.arff(Paths.getTestData("weka/iris.arff"))
iris ==>
sepallength  sepalwidth  petallength  petalwidth  class
───────────────────────────────────────────────────────
5.1          3.5         1.4          0.2         Iris-setosa
…

smile> var formula = Formula.lhs("class")
formula ==> class ~ .

smile> var rf = RandomForest.fit(formula, iris)
rf ==> Random Forest classifier with 500 trees

smile> rf.metrics()
$3 ==> Metrics{accuracy=1.000, …}

smile> var probs = new double[3][]
smile> rf.predict(iris.get(0), probs[0] = new double[3])
$5 ==> 0   // class index 0 = Iris-setosa

// Load, split, and cross-validate
smile> var cv = CrossValidation.stratify(10, formula, iris,
   ...>     (f, d) -> RandomForest.fit(f, d))
smile> cv.avg()
$7 ==> {accuracy=0.960, …}
```

Run a script file non-interactively:

```bash
smile shell examples/regression.jsh
```

### 10.6 Interactive Scala REPL Session

```bash
smile scala
```

```scala
scala> val iris = read.arff(Paths.getTestData("weka/iris.arff"))
val iris: DataFrame = …

scala> val rf = randomForest("class" ~ ".", iris)
val rf: RandomForest = …

scala> rf.metrics()
val res0: Metrics = {accuracy=1.000, …}

// OLS on longley data
scala> val longley = read.arff(Paths.getTestData("weka/regression/longley.arff"))
scala> val model = lm("employed" ~ ".", longley)
scala> println(model)

// Gaussian process with RBF kernel
scala> val gp = gpr("employed" ~ ".", longley, new GaussianKernel(1.0), 0.1)
```

---

## Quick Reference Card

```
ROUTING
  smile                           → SMILE Studio (GUI)
  smile shell  [args]             → JShell REPL
  smile scala  [args]             → Scala 3 REPL
  smile train  -d FILE -m MODEL <algo> [algo-opts]
  smile predict FILE -m MODEL [-p]
  smile serve  --model MODEL [--host H] [--port P]

CLASSIFICATION ALGORITHMS
  random-forest  gradient-boost  ada-boost  cart
  logistic  fisher  lda  qda  rda  mlp  svm  rbf

REGRESSION ALGORITHMS
  random-forest  gradient-boost  cart  mlp  svm  rbf
  ols  lasso  ridge  elastic-net  gaussian-process

CROSS-VALIDATION FLAGS (train)
  -k <fold>    k-fold CV
  -r <rounds>  repeated CV
  -e           save ensemble of fold models
  -s <seed>    fix RNG seed

FEATURE TRANSFORMS (--transform)
  standardizer  winsor(lo,hi)  minmax  MaxAbs  L1  L2  Linf
```


---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

