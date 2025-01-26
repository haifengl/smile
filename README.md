# Smile &mdash; Statistical Machine Intelligence and Learning Engine

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.haifengl/smile-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.haifengl/smile-core)

## Goal ##
<img align="left" width="48" src="/web/src/images/smile.jpg">
Smile is a fast and comprehensive machine learning framework in Java.
Smile also provides APIs in Scala, Kotlin, and Clojure with
corresponding language paradigms. With advanced data structures and
algorithms, Smile delivers state-of-art performance.
Smile covers every aspect of machine learning, including deep learning,
large language models, classification, regression, clustering, association
rule mining, feature selection and extraction, manifold learning,
multidimensional scaling, genetic algorithms, missing value imputation,
efficient nearest neighbor search, etc. Furthermore, Smile also provides
advanced algorithms for graph, linear algebra, numerical analysis,
interpolation, computer algebra system for symbolic manipulations,
and data visualization.

## Features ##
Smile implements the following major machine learning algorithms:

- **GenAI:**
Native Java implementation of Llama 3.1, tiktoken tokenizer, high performance
LLM inference server with OpenAI-compatible APIs and SSE-based chat streaming,
fully functional frontend. [A free service](https://smile-ai.org) is available
for personal or test usage. No registration is required.

- **Deep Learning:**
Deep learning with CPU and GPU. EfficientNet model for image classification.

- **Classification:**
Support Vector Machines, Decision Trees, AdaBoost, Gradient Boosting,
Random Forest, Logistic Regression, Neural Networks, RBF Networks,
Maximum Entropy Classifier, KNN, Naïve Bayesian,
Fisher/Linear/Quadratic/Regularized Discriminant Analysis.

- **Regression:**
Support Vector Regression, Gaussian Process, Regression Trees,
Gradient Boosting, Random Forest, RBF Networks, OLS, LASSO, ElasticNet,
Ridge Regression.

- **Feature Selection:**
Genetic Algorithm based Feature Selection, Ensemble Learning based Feature
Selection, TreeSHAP, Signal Noise ratio, Sum Squares ratio.

- **Clustering:**
BIRCH, CLARANS, DBSCAN, DENCLUE, Deterministic Annealing, K-Means,
X-Means, G-Means, Neural Gas, Growing Neural Gas, Hierarchical
Clustering, Sequential Information Bottleneck, Self-Organizing Maps,
Spectral Clustering, Minimum Entropy Clustering.

- **Association Rule & Frequent Itemset Mining:**
FP-growth mining algorithm.

- **Manifold Learning:**
IsoMap, LLE, Laplacian Eigenmap, t-SNE, UMAP, PCA, Kernel PCA,
Probabilistic PCA, GHA, Random Projection, ICA.

- **Multi-Dimensional Scaling:**
Classical MDS, Isotonic MDS, Sammon Mapping.

- **Nearest Neighbor Search:**
BK-Tree, Cover Tree, KD-Tree, SimHash, LSH.

- **Sequence Learning:**
Hidden Markov Model, Conditional Random Field.

- **Natural Language Processing:**
Sentence Splitter and Tokenizer, Bigram Statistical Test, Phrase Extractor,
Keyword Extractor, Stemmer, POS Tagging, Relevance Ranking

## License ##
SMILE employs a dual license model designed to meet the development
and distribution needs of both commercial distributors (such as OEMs,
ISVs and VARs) and open source projects. For details, please see
[LICENSE](https://github.com/haifengl/smile/blob/master/LICENSE).
To acquire a commercial license, please contact smile.sales@outlook.com.

## Issues/Discussions ##

* **Discussion/Questions**:
  If you wish to ask questions about Smile, we're active on [GitHub Discussions](https://github.com/haifengl/smile/discussions) and [Stack Overflow](http://stackoverflow.com/questions/tagged/smile).

* **Docs**:
Smile is well documented and [our docs are available online](https://haifengl.github.io/), where you can find tutorial,
programming guides, and more information. If you'd like to help improve the docs, they're part of this repository
in the `web/src` directory. [Java Docs](https://haifengl.github.io/api/java/index.html),
[Scala Docs](https://haifengl.github.io/api/scala/index.html), [Kotlin Docs](https://haifengl.github.io/api/kotlin/index.html),
and [Clojure Docs](https://haifengl.github.io/api/clojure/index.html) are also available.

* **Issues/Feature Requests**:
  Finally, any bugs or features, please report to our [issue tracker](https://github.com/haifengl/smile/issues/new).

## Installation ##
You can use the libraries through Maven central repository by adding the
following to your project pom.xml file.
```
    <dependency>
      <groupId>com.github.haifengl</groupId>
      <artifactId>smile-core</artifactId>
      <version>4.2.0</version>
    </dependency>
```

For deep learning and NLP, use the artifactId smile-deep and smile-nlp, respectively.

For Scala API, please add the below into your sbt script.
```
    libraryDependencies += "com.github.haifengl" %% "smile-scala" % "4.2.0"
```

For Kotlin API, add the below into the `dependencies` section
of Gradle build script.
```
    implementation("com.github.haifengl:smile-kotlin:4.2.0")
```

For Clojure API, add the following dependency to your project file:
```
    [org.clojars.haifengl/smile "4.2.0"]
```

Some algorithms rely on BLAS and LAPACK (e.g. manifold learning,
some clustering algorithms, Gaussian Process regression, MLP, etc.).
To use these algorithms, you should include OpenBLAS for optimized matrix
computation:
```
    libraryDependencies ++= Seq(
      "org.bytedeco" % "javacpp"   % "1.5.11"        classifier "macosx-arm64" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
      "org.bytedeco" % "openblas"  % "0.3.28-1.5.11" classifier "macosx-arm64" classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64",
      "org.bytedeco" % "arpack-ng" % "3.9.1-1.5.11"  classifier "macosx-x86_64" classifier "windows-x86_64" classifier "linux-x86_64"
    )
```
In this example, we include all supported 64-bit platforms and filter out
32-bit platforms. The user should include only the needed platforms to save
spaces.

If you prefer other BLAS implementations, you can use any library found on
the "java.library.path" or on the class path, by specifying it with the
"org.bytedeco.openblas.load" system property. For example, to use the BLAS
library from the Accelerate framework on Mac OS X, we can pass options such
as `-Dorg.bytedeco.openblas.load=blas`.

If you have a default installation of MKL or simply include the following
modules that include the full version of MKL binaries, Smile will automatically
switch to MKL.
```
libraryDependencies ++= {
  val version = "2025.0-1.5.11"
  Seq(
    "org.bytedeco" % "mkl-platform"        % version,
    "org.bytedeco" % "mkl-platform-redist" % version
  )
}
```

## Shell ##
Smile comes with interactive shells for Java, Scala and Kotlin.
Download pre-packaged Smile from the
[releases page](https://github.com/haifengl/smile/releases).
After unziping the package and cd into the home directory of Smile
in a terminal, type
```
    ./bin/jshell.sh
```
to enter Smile shell in Java, which pre-imports all major Smile packages.
You can run any valid Java expressions in the shell. In the simplest case,
you can use it as a calculator.

To enter the shell in Scala, type
```
    ./bin/smile
```
Similar to the shell in Java, all major Smile packages are pre-imported.
Besides, all high-level Smile operators are predefined in the shell.

By default, the shell uses up to 75% memory. If you need more memory
to handle large data, use the option `-J-Xmx` or `-XX:MaxRAMPercentage`.
For example,
```
    ./bin/smile -J-Xmx30G
```
You can also modify the configuration file `./conf/smile.ini` for the
memory and other JVM settings.

To use Smile shell in Kotlin, type
```
    ./bin/kotlin.sh
```
Unfortunately, Kotlin shell doesn't support pre-import packages.

## Model Serialization ##
Most models support the Java `Serializable` interface (all classifiers
do support `Serializable` interface) so that you can serialze a model
and ship it to a production environment for inference. You may also
use serialized models in other systems such as Spark.

## Visualization ##
A picture is worth a thousand words. In machine learning, we usually handle
high-dimensional data, which is impossible to draw on display directly.
But a variety of statistical plots are tremendously valuable for us to grasp
the characteristics of many data points. Smile provides data visualization tools
such as plots and maps for researchers to understand information more easily and quickly.
To use smile-plot, add the following to dependencies
```
    <dependency>
      <groupId>com.github.haifengl</groupId>
      <artifactId>smile-plot</artifactId>
      <version>4.2.0</version>
    </dependency>
```

On Swing-based systems, the user may leverage `smile.plot.swing` package to
create a variety of plots such as scatter plot, line plot, staircase plot,
bar plot, box plot, histogram, 3D histogram, dendrogram, heatmap, hexmap,
QQ plot, contour plot, surface, and wireframe.

This library also support data visualization in declarative approach.
With `smile.plot.vega` package, we can create a specification
that describes visualizations as mappings from data to properties
of graphical marks (e.g., points or bars). The specification is
based on [Vega-Lite](https://vega.github.io/vega-lite/). In a web browser,
the Vega-Lite compiler automatically produces visualization components
including axes, legends, and scales. It then determines properties
of these components based on a set of carefully designed rules.

## Contributing ##
Please read the [contributing.md](CONTRIBUTING.md) on how to build and test Smile.

## Maintainers ##
- Haifeng Li (@haifengl)
- Karl Li (@kklioss)

## Gallery
<table class="center" style="width:100%;">
  <tr>
    <td colspan="3">
      <figure>
        <a href="/web/src/images/splom.png"><img src="/web/src/images/splom.png" alt="SPLOM"></a>
        <figcaption style="text-align: center;"><h3>Scatterplot Matrix</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/web/src/images/pca.png"><img src="/web/src/images/pca.png" alt="Scatter"></a>
        <figcaption style="text-align: center;"><h3>Scatter Plot</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/images/heart.png"><img src="/web/src/images/heart.png" alt="Heart"></a>
        <figcaption style="text-align: center;"><h3>Line Plot</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/images/surface.png"><img src="/web/src/images/surface.png" alt="Surface"></a>
        <figcaption style="text-align: center;"><h3>Surface Plot</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/web/src/images/bar.png"><img src="/web/src/images/bar.png" alt="Scatter"></a>
        <figcaption style="text-align: center;"><h3>Bar Plot</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/images/box.png"><img src="/web/src/images/box.png" alt="Box Plot"></a>
        <figcaption style="text-align: center;"><h3>Box Plot</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/images/histogram2d.png"><img src="/web/src/images/histogram2d.png" alt="Histogram"></a>
        <figcaption style="text-align: center;"><h3>Histogram Heatmap</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/web/src/images/rolling.png"><img src="/web/src/images/rolling.png" alt="Rolling"></a>
        <figcaption style="text-align: center;"><h3>Rolling Average</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/images/map.png"><img src="/web/src/images/map.png" alt="Map"></a>
        <figcaption style="text-align: center;"><h3>Geo Map</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/images/umap.png"><img src="/web/src/images/umap.png" alt="UMAP"></a>
        <figcaption style="text-align: center;"><h3>UMAP</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/web/src/images/text.png"><img src="/web/src/images/text.png" alt="Text"></a>
        <figcaption style="text-align: center;"><h3>Text Plot</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/images/contour.png"><img src="/web/src/images/contour.png" alt="Contour"></a>
        <figcaption style="text-align: center;"><h3>Heatmap with Contour</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/images/hexmap.png"><img src="/web/src/images/hexmap.png" alt="Hexmap"></a>
        <figcaption style="text-align: center;"><h3>Hexmap</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/web/src/images/isomap.png"><img src="/web/src/images/isomap.png" alt="IsoMap"></a>
        <figcaption style="text-align: center;"><h3>IsoMap</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/images/umap.png"><img src="/web/src/images/lle.png" alt="LLE"></a>
        <figcaption style="text-align: center;"><h3>LLE</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/gallery/smile-demo-kpca.png"><img src="/web/src/gallery/smile-demo-kpca-small.png" alt="Kernel PCA"></a>
        <figcaption style="text-align: center;"><h3>Kernel PCA</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/web/src/gallery/smile-demo-ann.png"><img src="/web/src/gallery/smile-demo-ann-small.png" alt="Neural Network"></a>
        <figcaption style="text-align: center;"><h3>Neural Network</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/gallery/smile-demo-svm.png"><img src="/web/src/gallery/smile-demo-svm-small.png" alt="SVM"></a>
        <figcaption style="text-align: center;"><h3>SVM</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/gallery/smile-demo-agglomerative-clustering.png"><img src="/web/src/gallery/smile-demo-agglomerative-clustering-small.png" alt="Hierarchical Clustering"></a>
        <figcaption style="text-align: center;"><h3>Hierarchical Clustering</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/web/src/gallery/smile-demo-som.png"><img src="/web/src/gallery/smile-demo-som-small.png" alt="SOM"></a>
        <figcaption style="text-align: center;"><h3>SOM</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/gallery/smile-demo-dbscan.png"><img src="/web/src/gallery/smile-demo-dbscan-small.png" alt="DBSCAN"></a>
        <figcaption style="text-align: center;"><h3>DBSCAN</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/gallery/smile-demo-neural-gas.png"><img src="/web/src/gallery/smile-demo-neural-gas-small.png" alt="Neural Gas"></a>
        <figcaption style="text-align: center;"><h3>Neural Gas</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td>
      <figure>
        <a href="/web/src/gallery/smile-demo-wavelet.png"><img src="/web/src/gallery/smile-demo-wavelet-small.png" alt="Wavelet"></a>
        <figcaption style="text-align: center;"><h3>Wavelet</h3></figcaption>
      </figure>
    </td>
    <td>
      <figure>
        <a href="/web/src/gallery/smile-demo-mixture.png"><img src="/web/src/gallery/smile-demo-mixture-small.png" alt="Mixture"></a>
        <figcaption style="text-align: center;"><h3>Exponential Family Mixture</h3></figcaption>
    </figure>
    </td>
      <td>
      <figure>
        <a href="/web/src/images/teapot.png"><img src="/web/src/images/teapot.png" alt="Teapot"></a>
        <figcaption style="text-align: center;"><h3>Teapot Wireframe</h3></figcaption>
      </figure>
    </td>
  </tr>
  <tr>
    <td colspan="3">
      <figure>
        <a href="/web/src/images/grid-interpolation2d.png"><img src="/web/src/images/grid-interpolation2d.png" alt="Interpolation"></a>
        <figcaption style="text-align: center;"><h3>Grid Interpolation</h3></figcaption>
      </figure>
    </td>
  </tr>
</table>
