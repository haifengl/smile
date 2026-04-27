# Statistical Machine Intelligence & Learning Engine <img align="left" width="40" src="/website/src/images/smile.jpg" alt="SMILE">
[![Maven Central](https://img.shields.io/maven-central/v/com.github.haifengl/smile-core)](https://central.sonatype.com/artifact/com.github.haifengl/smile-core)
[![CI](https://github.com/haifengl/smile/actions/workflows/ci.yml/badge.svg)](https://github.com/haifengl/smile/actions/workflows/ci.yml)

SMILE (Statistical Machine Intelligence & Learning Engine) is a comprehensive,
high-performance machine learning framework for the JVM. SMILE v5+ requires
**Java 25**; v4.x requires Java 21; all previous versions require Java 8.
SMILE also provides idiomatic APIs for **Scala** and **Kotlin**.
With advanced data structures and algorithms, SMILE delivers state-of-the-art
performance across every aspect of machine learning.

---

## Table of Contents

1. [Features](#features)
2. [Module Map](#module-map)
3. [Installation](#installation)
   - [Maven](#maven)
   - [SBT (Scala)](#sbt-scala)
   - [Gradle (Kotlin)](#gradle-kotlin)
   - [Native Libraries (BLAS / LAPACK)](#native-libraries-blas--lapack)
4. [Quick Start](#quick-start)
5. [SMILE Studio & Shell](#smile-studio--shell)
6. [Model Serialization](#model-serialization)
7. [Visualization](#visualization)
8. [License](#license)
9. [Issues & Discussions](#issues--discussions)
10. [Contributing](#contributing)
11. [Maintainers](#maintainers)
12. [Gallery](#gallery)

---

## Features

| Area | Highlights |
|---|---|
| **LLM** | LLaMA-3 inference, tiktoken BPE tokenizer, OpenAI-compatible REST server, SSE chat streaming |
| **Deep Learning** | LibTorch/GPU backend, EfficientNet-V2 image classification, custom layer API |
| **Classification** | SVM, Decision Trees, Random Forest, AdaBoost, Gradient Boosting, Logistic Regression, Neural Networks, RBF Networks, MaxEnt, KNN, Naïve Bayes, LDA/QDA/RDA |
| **Regression** | SVR, Gaussian Process, Regression Trees, GBDT, Random Forest, RBF, OLS, LASSO, ElasticNet, Ridge |
| **Clustering** | BIRCH, CLARANS, DBSCAN, DENCLUE, Deterministic Annealing, K-Means, X-Means, G-Means, Neural Gas, Growing Neural Gas, Hierarchical, SIB, SOM, Spectral, Min-Entropy |
| **Manifold Learning** | IsoMap, LLE, Laplacian Eigenmap, t-SNE, UMAP, PCA, Kernel PCA, Probabilistic PCA, GHA, Random Projection, ICA |
| **Feature Engineering** | Genetic Algorithm selection, Ensemble selection, TreeSHAP, SNR, Sum-Squares ratio, data transformations, formula API |
| **NLP** | Sentence / word tokenization, Bigram test, Phrase & Keyword extraction, Stemmer, POS tagging, Relevance ranking |
| **Association Rules** | FP-growth frequent itemset mining |
| **Sequence Learning** | Hidden Markov Model, Conditional Random Field |
| **Nearest Neighbor** | BK-Tree, Cover Tree, KD-Tree, SimHash, LSH |
| **Numerical Methods** | Linear algebra, numerical optimization (BFGS, L-BFGS), interpolation, wavelets, RBF, distributions, hypothesis tests |
| **Visualization** | Swing plots (scatter, line, bar, box, histogram, surface, heatmap, contour, …) and declarative Vega-Lite charts |

---

## Module Map

Each module has its own detailed user guide.  Click the **README** link for
the module overview, or drill into individual topic guides.

### `base/` — Foundation
> Data structures, math, linear algebra, statistical utilities, I/O

| Document | Topics |
|---|---|
| [README](base/README.md) | Module overview and dependency setup |
| [DATA_FRAME.md](base/DATA_FRAME.md) | DataFrame API — creation, selection, transformation |
| [DATA_IO.md](base/DATA_IO.md) | CSV, JSON, Parquet, Arrow, JDBC, Avro readers/writers |
| [DATA_TRANSFORMATION.md](base/DATA_TRANSFORMATION.md) | Scalers, encoders, imputers, feature transforms |
| [DATASET.md](base/DATASET.md) | Built-in benchmark and real-world datasets |
| [FORMULA.md](base/FORMULA.md) | R-style formula language for model matrices |
| [DISTRIBUTIONS.md](base/DISTRIBUTIONS.md) | Probability distributions (Normal, Poisson, Beta, …) |
| [HYPOTHESIS_TESTING.md](base/HYPOTHESIS_TESTING.md) | t-test, chi-squared, ANOVA, KS-test, … |
| [DISTANCES.md](base/DISTANCES.md) | Euclidean, Mahalanobis, Hamming, edit distance, … |
| [NEAREST_NEIGHBOR.md](base/NEAREST_NEIGHBOR.md) | KD-Tree, Cover Tree, BK-Tree, LSH |
| [KERNELS.md](base/KERNELS.md) | Gaussian, polynomial, Laplacian, and other kernel functions |
| [RBF.md](base/RBF.md) | Radial basis function networks |
| [INTERPOLATION.md](base/INTERPOLATION.md) | Linear, cubic spline, bilinear, bicubic |
| [GRAPH.md](base/GRAPH.md) | Adjacency list/matrix graph, BFS/DFS, spanning trees |
| [SORT.md](base/SORT.md) | Quick sort, heap sort, counting sort, index sort |
| [HASH.md](base/HASH.md) | Locality-sensitive hashing, SimHash |
| [RNG.md](base/RNG.md) | Random number generators, sampling, permutations |
| [BFGS.md](base/BFGS.md) | L-BFGS and BFGS numerical optimizers |
| [ICA.md](base/ICA.md) | Independent Component Analysis |
| [TENSOR.md](base/TENSOR.md) | N-dimensional array (CPU tensor without LibTorch) |
| [WAVELET.md](base/WAVELET.md) | DWT, CWT, and wavelet families |
| [GAP.md](base/GAP.md) | GAP statistic for optimal cluster count estimation |
| [COMPRESSED_SENSING.md](base/COMPRESSED_SENSING.md) | Compressed sensing and basis pursuit |

### `core/` — Machine Learning Algorithms
> Classification, regression, clustering, manifold learning, and more

| Document | Topics |
|---|---|
| [README](core/README.md) | Module overview |
| [CLASSIFICATION.md](core/CLASSIFICATION.md) | SVM, Random Forest, AdaBoost, GBDT, KNN, Naïve Bayes, LDA, … |
| [REGRESSION.md](core/REGRESSION.md) | SVR, Gaussian Process, LASSO, Ridge, ElasticNet, GBDT, … |
| [CLUSTERING.md](core/CLUSTERING.md) | K-Means, DBSCAN, BIRCH, SOM, Spectral Clustering, … |
| [FEATURE_ENGINEERING.md](core/FEATURE_ENGINEERING.md) | Feature selection, PCA, ICA, projection, encoding |
| [MANIFOLD.md](core/MANIFOLD.md) | t-SNE, UMAP, IsoMap, LLE, Laplacian Eigenmap |
| [ANOMALY_DETECTION.md](core/ANOMALY_DETECTION.md) | IsolationForest, one-class SVM, local outlier factor |
| [ASSOCIATION_RULE_MINING.md](core/ASSOCIATION_RULE_MINING.md) | FP-growth, association rules, frequent itemsets |
| [SEQUENCE.md](core/SEQUENCE.md) | HMM (Baum-Welch, Viterbi), CRF |
| [TIME_SERIES.md](core/TIME_SERIES.md) | ARIMA, box-plots, autocorrelation |
| [REGRESSION.md](core/REGRESSION.md) | Full regression API reference |
| [TRAINING.md](core/TRAINING.md) | Cross-validation, bootstrap, hyper-parameter search |
| [VALIDATION.md](core/VALIDATION.md) | Hold-out, k-fold, leave-one-out evaluation |
| [VALIDATION_METRICS.md](core/VALIDATION_METRICS.md) | Accuracy, AUC, F1, RMSE, MAE, confusion matrix |
| [HYPER_PARAMETER_OPTIMIZATION.md](core/HYPER_PARAMETER_OPTIMIZATION.md) | Grid search, random search, Bayesian optimization |
| [VECTOR_QUANTIZATION.md](core/VECTOR_QUANTIZATION.md) | LVQ, Neural Gas, SOM as vector quantizers |
| [ONNX.md](core/ONNX.md) | Exporting and importing models via ONNX |

### `deep/` — Deep Learning & LLMs
> LibTorch-backed GPU/CPU tensor operations, neural network layers, LLaMA-3 inference, EfficientNet

| Document | Topics |
|---|---|
| [README](deep/README.md) | Full deep-learning & LLM user guide (tensors, layers, loss, optimizer, EfficientNet, LLaMA) |

The `deep/README.md` covers:
- **`smile.deep.tensor`** — Tensor factory, indexing, arithmetic, AutoScope memory management, dtype/device
- **`smile.deep.layer`** — Linear, Conv2d, pooling, normalization (BN/GN/RMS), dropout, embedding, sequential blocks
- **`smile.deep.activation`** — ReLU, GELU, SiLU, Tanh, Sigmoid, Softmax, GLU, HardShrink, …
- **`smile.deep.Loss`** — MSE, cross-entropy, BCE, Huber, KL, hinge, and more
- **`smile.deep.Optimizer`** — SGD, Adam, AdamW, RMSprop
- **`smile.deep.Model`** — Abstract base class + training loop
- **`smile.deep.metric`** — Accuracy, Precision, Recall, F1Score with macro/micro/weighted averaging
- **`smile.llm`** — `Message`, `Role`, `FinishReason`, `ChatCompletion` records; sinusoidal & RoPE positional encodings
- **`smile.llm.tokenizer`** — `Tokenizer` interface, `Tiktoken` BPE implementation (LLaMA-3 compatible)
- **`smile.llm.llama`** — Full LLaMA-3 stack: `Llama.build()`, `generate()`, `chat()`, streaming via `SubmissionPublisher`
- **`smile.vision`** — `VisionModel`, `ImageDataset`, `EfficientNet.V2S/M/L()` pretrained models, ImageNet labels
- **`smile.vision.transform`** — `Transform` interface, `ImageClassification` pipeline, resize/crop/toTensor helpers

### `nlp/` — Natural Language Processing
> Text normalization, tokenization, POS tagging, stemming, relevance ranking

| Document | Topics |
|---|---|
| [README](nlp/README.md) | Module overview |
| [TOKENIZER.md](nlp/TOKENIZER.md) | Sentence splitter, word tokenizer, regex tokenizer |
| [POS.md](nlp/POS.md) | Part-of-speech tagging (Brill tagger, HMM tagger) |
| [STEM.md](nlp/STEM.md) | Porter, Lancaster, Lovins stemmers; lemmatization |
| [COLLOCATION.md](nlp/COLLOCATION.md) | Bigram/trigram statistical tests, phrase extraction |
| [RELEVANCE.md](nlp/RELEVANCE.md) | TF-IDF, BM25, keyword extraction |
| [TAXONOMY.md](nlp/TAXONOMY.md) | WordNet integration, synsets, hypernyms |

### `plot/` — Data Visualization
> Swing-based interactive plots and declarative Vega-Lite charts

| Document | Topics |
|---|---|
| [README](plot/README.md) | Swing plotting API — scatter, line, bar, box, histogram, heatmap, surface, contour, wireframe |
| [VEGA.md](plot/VEGA.md) | Declarative `smile.plot.vega` (Vega-Lite) — JSON spec generation, web/Jupyter rendering |

### `serve/` — Inference Server
> Quarkus-based REST inference service with OpenAI-compatible API and SSE streaming

| Document | Topics |
|---|---|
| [README](serve/README.md) | Building and running the server, `/chat/completions` endpoint, SSE streaming, configuration |

### `studio/` — Interactive Shell & Desktop IDE
> REPL / notebook environment for Java, Scala, and Kotlin

| Document                      | Topics |
|-------------------------------|---|
| [README.md](studio/README.md) | Desktop Studio notebook UI, cell types, output rendering |
| [CLI](studio/CLI.md)    | CLI entry points (`smile`, `smile shell`, `smile scala`, `smile kotlin`, `smile server`) |

### `scala/` — Scala API
> Idiomatic Scala shim — concise wrappers, symbolic operators, Scala collections integration

| Document | Topics |
|---|---|
| [README](scala/README.md) | API overview, `smile.classification`, `smile.regression`, `smile.clustering`, `smile.plot` in Scala |

### `kotlin/` — Kotlin API
> Idiomatic Kotlin shim — extension functions, named parameters, builder DSLs

| Document | Topics |
|---|---|
| [README](kotlin/README.md) | API overview, extension functions, Kotlin-style builders |
| [packages.md](kotlin/packages.md) | Full package-by-package listing of all Kotlin extension functions |

### `json/` — JSON Library (Scala)
> Lightweight zero-dependency JSON library for Scala with a clean DSL

| Document | Topics |
|---|---|
| [README](json/README.md) | Parsing, building, pattern matching, path navigation, serialization |

### `spark/` — Apache Spark Integration
> Use SMILE models inside Spark ML pipelines

| Document | Topics |
|---|---|
| [README](spark/README.md) | `SmileTransformer`, `SmileClassifier`, `SmileRegressor`; training and scoring in Spark DataFrames |

---

## Installation

### Maven

```xml
<!-- Core ML algorithms -->
<dependency>
  <groupId>com.github.haifengl</groupId>
  <artifactId>smile-core</artifactId>
  <version>6.0.1</version>
</dependency>

<!-- Deep learning + LLMs (requires LibTorch) -->
<dependency>
  <groupId>com.github.haifengl</groupId>
  <artifactId>smile-deep</artifactId>
  <version>6.0.1</version>
</dependency>

<!-- Natural language processing -->
<dependency>
  <groupId>com.github.haifengl</groupId>
  <artifactId>smile-nlp</artifactId>
  <version>6.0.1</version>
</dependency>

<!-- Data visualization -->
<dependency>
  <groupId>com.github.haifengl</groupId>
  <artifactId>smile-plot</artifactId>
  <version>6.0.1</version>
</dependency>
```

### SBT (Scala)

```scala
libraryDependencies += "com.github.haifengl" %% "smile-scala" % "6.0.1"
```

### Gradle (Kotlin)

```kotlin
dependencies {
    implementation("com.github.haifengl:smile-kotlin:6.0.1")
}
```

### Native Libraries (BLAS / LAPACK)

Several algorithms (manifold learning, Gaussian Process, MLP, some clustering)
require BLAS and LAPACK.

**Linux (Ubuntu / Debian)**
```shell
sudo apt update
sudo apt install libopenblas-dev libarpack2-dev
```

**macOS (Homebrew)**
```shell
brew install arpack
# If macOS SIP strips DYLD_LIBRARY_PATH, copy the dylib to your working dir:
cp /opt/homebrew/lib/libarpack.dylib .
```

**Windows** — pre-built DLLs are included in the `bin/` directory of the
[release package](https://github.com/haifengl/smile/releases).
Add that directory to `PATH`.

**GPU (CUDA)** — make sure the LibTorch CUDA native libraries are on
`java.library.path` and that your Bytedeco `pytorch` classifier matches
your CUDA version (e.g., `linux-x86_64-gpu-cuda12.4`).

---

## Quick Start

```java
import smile.classification.RandomForest;
import smile.data.formula.Formula;
import smile.io.Read;

// Load data
var data = Read.csv("src/test/resources/iris.csv");

// Train a random forest
var forest = RandomForest.fit(Formula.lhs("species"), data);

// Predict
int label = forest.predict(data.get(0));
System.out.println("Predicted class: " + label);
```

For deep learning and LLM examples, see [deep/README.md](deep/README.md).
For visualization examples, see [plot/README.md](plot/README.md).

---

## SMILE Studio & Shell

SMILE ships with an interactive desktop Studio (notebook-style) and a set of
CLI shells.  See [studio/README.md](studio/README.md) for full documentation.

Download a pre-packaged release from the
[releases page](https://github.com/haifengl/smile/releases), then:

```shell
cd bin
./setup      # install required native dependencies
./smile      # launch SMILE Studio (desktop GUI)
```

Other entry points:

| Command           | Description |
|-------------------|---|
| `./smile`         | Desktop notebook IDE |
| `./smile shell`   | Java REPL with all SMILE packages pre-imported |
| `./smile scala`   | Scala REPL |
| `./smile train`   | Train a supervised learning model     |
| `./smile predict` | Predict on a file using a saved model             |
| `./smile serve`   | Start the LLM inference server |

To increase the JVM heap:
```shell
./smile -J-Xmx30G
```

---

## Model Serialization

Most SMILE models implement `java.io.Serializable`.  You can serialize a
trained model to disk and load it in a production environment or inside a
Spark job:

```java
// Save
try (var out = new ObjectOutputStream(new FileOutputStream("model.ser"))) {
    out.writeObject(forest);
}

// Load
try (var in = new ObjectInputStream(new FileInputStream("model.ser"))) {
    var loaded = (RandomForest) in.readObject();
}
```

---

## Visualization

SMILE provides two visualization layers:

- **`smile.plot.swing`** — Swing-based interactive 2D/3D plots.  See [plot/README.md](plot/README.md).
- **`smile.plot.vega`** — Declarative Vega-Lite charts for browsers and Jupyter.  See [plot/VEGA.md](plot/VEGA.md).

```xml
<dependency>
  <groupId>com.github.haifengl</groupId>
  <artifactId>smile-plot</artifactId>
  <version>6.0.1</version>
</dependency>
```

---

## License

SMILE employs a dual license model designed to meet the development
and distribution needs of both commercial distributors (OEMs, ISVs, VARs)
and open source projects.  For details, see
[LICENSE](https://github.com/haifengl/smile/blob/master/LICENSE).
To acquire a commercial license, contact **smile.sales@outlook.com**.

---

## Issues & Discussions

| Channel | Purpose |
|---|---|
| [GitHub Discussions](https://github.com/haifengl/smile/discussions) | Questions, ideas, show-and-tell |
| [Stack Overflow `[smile]`](http://stackoverflow.com/questions/tagged/smile) | Technical Q&A |
| [Issue Tracker](https://github.com/haifengl/smile/issues/new) | Bug reports and feature requests |
| [Online Docs](https://haifengl.github.io/) | Tutorials and programming guides |
| [Java API](https://haifengl.github.io/api/java/index.html) · [Scala API](https://haifengl.github.io/api/scala/index.html) · [Kotlin API](https://haifengl.github.io/api/kotlin/index.html) · [Clojure API](https://haifengl.github.io/api/clojure/index.html) | API Javadoc |

---

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for build and test instructions.

---

## Maintainers

- Haifeng Li ([@haifengl](https://github.com/haifengl))
- Karl Li ([@kklioss](https://github.com/kklioss))

---

## Gallery
<table class="center" style="width:100%;">
  <tr>
    <td colspan="3">
      <figure>
        <a href="/website/src/images/splom.png"><img src="/website/src/images/splom.png" alt="SPLOM"></a>
        <figcaption style="text-align: center;"><h3>Scatterplot Matrix</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/website/src/images/pca.png"><img src="/website/src/images/pca.png" alt="Scatter"></a>
        <figcaption style="text-align: center;"><h3>Scatter Plot</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/images/heart.png"><img src="/website/src/images/heart.png" alt="Heart"></a>
        <figcaption style="text-align: center;"><h3>Line Plot</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/images/surface.png"><img src="/website/src/images/surface.png" alt="Surface"></a>
        <figcaption style="text-align: center;"><h3>Surface Plot</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/website/src/images/bar.png"><img src="/website/src/images/bar.png" alt="Scatter"></a>
        <figcaption style="text-align: center;"><h3>Bar Plot</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/images/box.png"><img src="/website/src/images/box.png" alt="Box Plot"></a>
        <figcaption style="text-align: center;"><h3>Box Plot</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/images/histogram2d.png"><img src="/website/src/images/histogram2d.png" alt="Histogram"></a>
        <figcaption style="text-align: center;"><h3>Histogram Heatmap</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/website/src/images/rolling.png"><img src="/website/src/images/rolling.png" alt="Rolling"></a>
        <figcaption style="text-align: center;"><h3>Rolling Average</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/images/map.png"><img src="/website/src/images/map.png" alt="Map"></a>
        <figcaption style="text-align: center;"><h3>Geo Map</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/images/umap.png"><img src="/website/src/images/umap.png" alt="UMAP"></a>
        <figcaption style="text-align: center;"><h3>UMAP</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/website/src/images/text.png"><img src="/website/src/images/text.png" alt="Text"></a>
        <figcaption style="text-align: center;"><h3>Text Plot</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/images/contour.png"><img src="/website/src/images/contour.png" alt="Contour"></a>
        <figcaption style="text-align: center;"><h3>Heatmap with Contour</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/images/hexmap.png"><img src="/website/src/images/hexmap.png" alt="Hexmap"></a>
        <figcaption style="text-align: center;"><h3>Hexmap</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/website/src/images/isomap.png"><img src="/website/src/images/isomap.png" alt="IsoMap"></a>
        <figcaption style="text-align: center;"><h3>IsoMap</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/images/umap.png"><img src="/website/src/images/lle.png" alt="LLE"></a>
        <figcaption style="text-align: center;"><h3>LLE</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/gallery/smile-demo-kpca.png"><img src="/website/src/gallery/smile-demo-kpca-small.png" alt="Kernel PCA"></a>
        <figcaption style="text-align: center;"><h3>Kernel PCA</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/website/src/gallery/smile-demo-ann.png"><img src="/website/src/gallery/smile-demo-ann-small.png" alt="Neural Network"></a>
        <figcaption style="text-align: center;"><h3>Neural Network</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/gallery/smile-demo-svm.png"><img src="/website/src/gallery/smile-demo-svm-small.png" alt="SVM"></a>
        <figcaption style="text-align: center;"><h3>SVM</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/gallery/smile-demo-agglomerative-clustering.png"><img src="/website/src/gallery/smile-demo-agglomerative-clustering-small.png" alt="Hierarchical Clustering"></a>
        <figcaption style="text-align: center;"><h3>Hierarchical Clustering</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/website/src/gallery/smile-demo-som.png"><img src="/website/src/gallery/smile-demo-som-small.png" alt="SOM"></a>
        <figcaption style="text-align: center;"><h3>SOM</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/gallery/smile-demo-dbscan.png"><img src="/website/src/gallery/smile-demo-dbscan-small.png" alt="DBSCAN"></a>
        <figcaption style="text-align: center;"><h3>DBSCAN</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/gallery/smile-demo-neural-gas.png"><img src="/website/src/gallery/smile-demo-neural-gas-small.png" alt="Neural Gas"></a>
        <figcaption style="text-align: center;"><h3>Neural Gas</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/website/src/gallery/smile-demo-wavelet.png"><img src="/website/src/gallery/smile-demo-wavelet-small.png" alt="Wavelet"></a>
        <figcaption style="text-align: center;"><h3>Wavelet</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/website/src/gallery/smile-demo-mixture.png"><img src="/website/src/gallery/smile-demo-mixture-small.png" alt="Mixture"></a>
        <figcaption style="text-align: center;"><h3>Exponential Family Mixture</h3></figcaption>
    </figure>
    </td>
      <td>
      <figure>
        <a href="/website/src/images/teapot.png"><img src="/website/src/images/teapot.png" alt="Teapot"></a>
        <figcaption style="text-align: center;"><h3>Teapot Wireframe</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td colspan="3">
      <figure>
        <a href="/website/src/images/grid-interpolation2d.png"><img src="/website/src/images/grid-interpolation2d.png" alt="Interpolation"></a>
        <figcaption style="text-align: center;"><h3>Grid Interpolation</h3></figcaption>
      </figure>
    </td>
  </tr>
</table>
