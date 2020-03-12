Smile
=====

[![Join the chat at https://gitter.im/haifengl/smile](https://badges.gitter.im/haifengl/smile.svg)](https://gitter.im/haifengl/smile?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.haifengl/smile-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.haifengl/smile-core)

Smile (Statistical Machine Intelligence and Learning Engine) is
a fast and comprehensive machine learning, NLP, linear algebra,
graph, interpolation, and visualization system in Java and Scala.
With advanced data structures and algorithms,
Smile delivers state-of-art performance.

Smile covers every aspect of machine learning, including classification,
regression, clustering, association rule mining, feature selection,
manifold learning, multidimensional scaling, genetic algorithms,
missing value imputation, efficient nearest neighbor search, etc.

Smile is well documented and please check out the
[project website](http://haifengl.github.io/)
for programming guides and more information.

You can use the libraries through Maven central repository by adding the following to your project pom.xml file.
```
    <dependency>
      <groupId>com.github.haifengl</groupId>
      <artifactId>smile-core</artifactId>
      <version>2.2.2</version>
    </dependency>
```

For NLP, use the artifactId smile-nlp.

For Scala API, please use
```
    libraryDependencies += "com.github.haifengl" %% "smile-scala" % "2.2.2"
```

To enable machine optimized matrix computation, the users should add
the dependency of smile-netlib:
```
    <dependency>
      <groupId>com.github.haifengl</groupId>
      <artifactId>smile-netlib</artifactId>
      <version>2.2.2</version>
    </dependency>
```
and also make their machine-optimized libblas3 (CBLAS) and liblapack3 (Fortran)
available as shared libraries at runtime. This module employs the highly efficient
[netlib-java](https://github.com/fommil/netlib-java#netlib-java) library.

OS X
----
Apple OS X requires no further setup as it ships with the veclib framework.

Linux
-----
Generically-tuned ATLAS and OpenBLAS are available with most distributions
and must be enabled explicitly using the package-manager. For example,

 - sudo apt-get install libatlas3-base libopenblas-base
 - sudo update-alternatives --config libblas.so
 - sudo update-alternatives --config libblas.so.3
 - sudo update-alternatives --config liblapack.so
 - sudo update-alternatives --config liblapack.so.3

However, these are only generic pre-tuned builds. If you have Intel MKL installed,
you could also create symbolic links from libblas.so.3 and liblapack.so.3 to libmkl_rt.so
or use Debian's alternatives system.

Windows
-------
The native_system builds expect to find libblas3.dll and liblapack3.dll
on the %PATH% (or current working directory). Smile ships a prebuilt
[OpenBLAS](http://www.openblas.net/).
The users can also install vendor-supplied implementations, which may
offer better performance.

Shell
=====
Smile comes with an interactive shell. Download pre-packaged Smile from the [releases page](https://github.com/haifengl/smile/releases).
In the home directory of Smile, type
```
    ./bin/smile
```
to enter the shell, which is based on Ammonite-REPL. You can run any valid Scala expressions in the shell.
In the simplest case, you can use it as a calculator. Besides, all high-level Smile operators are predefined
in the shell. By default, the shell uses up to 4GB memory. If you need more memory to handle large data,
use the option `-J-Xmx`. For example,

```
    ./bin/smile -J-Xmx8192M
```
You can also modify the configuration file `./conf/smile.ini` for the memory and other JVM settings.
For detailed help, checkout the [project website](http://haifengl.github.io/smile/).

Smile implements the following major machine learning algorithms:

* **Classification**
Support Vector Machines, Decision Trees, AdaBoost, Gradient Boosting, Random Forest, Logistic Regression, Neural Networks, RBF Networks, Maximum Entropy Classifier, KNN, Naïve Bayesian, Fisher/Linear/Quadratic/Regularized Discriminant Analysis.

* **Regression**
Support Vector Regression, Gaussian Process, Regression Trees, Gradient Boosting, Random Forest, RBF Networks, OLS, LASSO, ElasticNet, Ridge Regression.

* **Feature Selection**
Genetic Algorithm based Feature Selection, Ensemble Learning based Feature Selection, Signal Noise ratio, Sum Squares ratio.

* **Clustering**
BIRCH, CLARANS, DBSCAN, DENCLUE, Deterministic Annealing, K-Means, X-Means, G-Means, Neural Gas, Growing Neural Gas, Hierarchical Clustering, Sequential Information Bottleneck, Self-Organizing Maps, Spectral Clustering, Minimum Entropy Clustering.

* **Association Rule & Frequent Itemset Mining**
FP-growth mining algorithm.

* **Manifold learning**
IsoMap, LLE, Laplacian Eigenmap, t-SNE, PCA, Kernel PCA, Probabilistic PCA, GHA, Random Projection, ICA.

* **Multi-Dimensional Scaling**
Classical MDS, Isotonic MDS, Sammon Mapping.

* **Nearest Neighbor Search**
BK-Tree, Cover Tree, KD-Tree, LSH.

* **Sequence Learning**
Hidden Markov Model, Conditional Random Field.

* **Natural Language Processing**
Sentence Splitter and Tokenizer, Bigram Statistical Test, Phrase Extractor, Keyword Extractor, Stemmer, POS Tagging, Relevance Ranking

Model Serialization
===================
Most models support the Java `Serializable` interface (all classifiers do support `Serializable` interface) so that
you can use them in Spark. For reading/writing the models in non-Java code, we suggest [XStream](https://github.com/x-stream/xstream) to serialize the trained models.
XStream is a simple library to serialize objects to XML and back again. XStream is easy to use and doesn't require mappings
(actually requires no modifications to objects). [Protostuff](http://code.google.com/p/protostuff/) is a
nice alternative that supports forward-backward compatibility (schema evolution) and validation.
Beyond XML, Protostuff supports many other formats such as JSON, YAML, protobuf, etc. For some predictive models,
we look forward to supporting PMML (Predictive Model Markup Language), an XML-based file format developed by the Data Mining Group.

Smile Scala API provides `read()`, `read.xstream()`, `write()`, and `write.xstream()` functions in package smile.io.

SmilePlot
=========

Smile also has a Swing-based data visualization library SmilePlot, which provides scatter plot, line plot, staircase plot, bar plot, box plot, histogram, 3D histogram, dendrogram, heatmap, hexmap, QQ plot, contour plot, surface, and wireframe. The class PlotCanvas provides builtin functions such as zoom in/out, export, print, customization, etc.

SmilePlot requires SwingX library for JXTable. But if your environment cannot use SwingX, it is easy to remove this dependency by using JTable.

To use SmilePlot, add the following to dependencies
```
    <dependency>
      <groupId>com.github.haifengl</groupId>
      <artifactId>smile-plot</artifactId>
      <version>2.2.2</version>
    </dependency>
```

Demo Gallery
============
<table class="center" width="100%">
    <tr>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/gallery/smile-demo-kpca.png"><img src="http://haifengl.github.io/gallery/smile-demo-kpca-small.png" alt="Kernel PCA"></a>
                <figcaption><h2>Kernel PCA</h2></figcaption>
            </figure>
        </td>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/gallery/smile-demo-isomap.png"><img src="http://haifengl.github.io/gallery/smile-demo-isomap-small.png" alt="IsoMap"></a>
                <figcaption><h2>IsoMap</h2></figcaption>
            </figure>
        </td>
    </tr>
    <tr>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/gallery/smile-demo-mds.png"><img src="http://haifengl.github.io/gallery/smile-demo-mds-small.png" alt="MDS"></a>
                <figcaption><h2>Multi-Dimensional Scaling</h2></figcaption>
            </figure>
        </td>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/gallery/smile-demo-som.png"><img src="http://haifengl.github.io/gallery/smile-demo-som-small.png" alt="SOM"></a>
                <figcaption><h2>SOM</h2></figcaption>
            </figure>
        </td>
    </tr>
    <tr>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/gallery/smile-demo-ann.png"><img src="http://haifengl.github.io/gallery/smile-demo-ann-small.png" alt="Neural Network"></a>
                <figcaption><h2>Neural Network</h2></figcaption>
            </figure>
        </td>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/gallery/smile-demo-svm.png"><img src="http://haifengl.github.io/gallery/smile-demo-svm-small.png" alt="SVM"></a>
                <figcaption><h2>SVM</h2></figcaption>
            </figure>
        </td>
    </tr>
    <tr>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/gallery/smile-demo-agglomerative-clustering.png"><img src="http://haifengl.github.io/gallery/smile-demo-agglomerative-clustering-small.png" alt="Agglomerative Clustering"></a>
                <figcaption><h2>Agglomerative Clustering</h2></figcaption>
            </figure>
        </td>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/gallery/smile-demo-xmeans.png"><img src="http://haifengl.github.io/gallery/smile-demo-xmeans-small.png" alt="X-Means"></a>
                <figcaption><h2>X-Means</h2></figcaption>
            </figure>
        </td>
    </tr>
    <tr>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/gallery/smile-demo-dbscan.png"><img src="http://haifengl.github.io/gallery/smile-demo-dbscan-small.png" alt="DBSCAN"></a>
                <figcaption><h2>DBSCAN</h2></figcaption>
            </figure>
        </td>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/gallery/smile-demo-neural-gas.png"><img src="http://haifengl.github.io/gallery/smile-demo-neural-gas-small.png" alt="Neural Gas"></a>
                <figcaption><h2>Neural Gas</h2></figcaption>
            </figure>
        </td>
    </tr>
    <tr>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/gallery/smile-demo-wavelet.png"><img src="http://haifengl.github.io/gallery/smile-demo-wavelet-small.png" alt="Wavelet"></a>
                <figcaption><h2>Wavelet</h2></figcaption>
            </figure>
        </td>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/gallery/smile-demo-mixture.png"><img src="http://haifengl.github.io/gallery/smile-demo-mixture-small.png" alt="Mixture"></a>
                <figcaption><h2>Exponential Family Mixture</h2></figcaption>
            </figure>
        </td>
    </tr>
</table>

