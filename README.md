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
[project website](http://haifengl.github.io/smile/)
for programming guides and more information.

You can use the libraries through Maven central repository by adding the following to your project pom.xml file.
```
    <dependency>
      <groupId>com.github.haifengl</groupId>
      <artifactId>smile-core</artifactId>
      <version>1.5.0</version>
    </dependency>
```

For NLP, use the artifactId smile-nlp.

For Scala API, please use
```
    libraryDependencies += "com.github.haifengl" %% "smile-scala" % "1.5.0"
```

To enable machine optimized matrix computation, the users should add
the dependency of smile-netlib:
```
    <dependency>
      <groupId>com.github.haifengl</groupId>
      <artifactId>smile-netlib</artifactId>
      <version>1.5.0</version>
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

However, these are only generic pre-tuned builds. If you have an Intel MKL licence,
you could also create symbolic links from libblas.so.3 and liblapack.so.3 to libmkl_rt.so
or use Debian's alternatives system.

Windows
-------
The native_system builds expect to find libblas3.dll and liblapack3.dll
on the %PATH% (or current working directory). Smile ships a prebuilt
[OpenBLAS](http://www.openblas.net/).
The users can also install vendor-supplied implementations, which may
offer better performance.

Smile comes with an interactive shell. Download pre-packaged Smile from the [releases page](https://github.com/haifengl/smile/releases).
In the home directory of Smile, type
```
    ./bin/smile
```
to enter the shell, which is based on Scala interpreter. So you can run any valid Scala expressions in the shell.
In the simplest case, you can use it as a calculator. Besides, all high-level Smile operators are predefined
in the shell. By default, the shell uses up to 4GB memory. If you need more memory to handle large data,
use the option `-J-Xmx`. For example,

```
    ./bin/smile -J-Xmx8192M
```
You can also modify the configuration file `./conf/application.ini` for the memory and other JVM settings.
For detailed help, checkout the [project website](http://haifengl.github.io/smile/).

Smile implements the following major machine learning algorithms:

* **Classification**
Support Vector Machines, Decision Trees, AdaBoost, Gradient Boosting, Random Forest, Logistic Regression, Neural Networks, RBF Networks, Maximum Entropy Classifier, KNN, Naïve Bayesian, Fisher/Linear/Quadratic/Regularized Discriminant Analysis.

* **Regression**
Support Vector Regression, Gaussian Process, Regression Trees, Gradient Boosting, Random Forest, RBF Networks, OLS, LASSO, Ridge Regression.

* **Feature Selection**
Genetic Algorithm based Feature Selection, Ensemble Learning based Feature Selection, Signal Noise ratio, Sum Squares ratio.

* **Clustering**
BIRCH, CLARANS, DBScan, DENCLUE, Deterministic Annealing, K-Means, X-Means, G-Means, Neural Gas, Growing Neural Gas, Hierarchical Clustering, Sequential Information Bottleneck, Self-Organizing Maps, Spectral Clustering, Minimum Entropy Clustering.

* **Association Rule & Frequent Itemset Mining**
FP-growth mining algorithm

* **Manifold learning**
IsoMap, LLE, Laplacian Eigenmap, t-SNE, PCA, Kernel PCA, Probabilistic PCA, GHA, Random Projection

* **Multi-Dimensional Scaling**
Classical MDS, Isotonic MDS, Sammon Mapping

* **Nearest Neighbor Search**
BK-Tree, Cover Tree, KD-Tree, LSH

* **Sequence Learning**
Hidden Markov Model, Conditional Random Field.

* **Natural Language Processing**
Sentence Splitter and Tokenizer, Bigram Statistical Test, Phrase Extractor, Keyword Extractor, Stemmer, POS Tagging, Relevance Ranking

Model Serialization
===================
Most models support the Java `Serializable` interface (all classifiers do support `Serializable` interface) so that
you can use them in Spark. For reading/writing the models in non-Java code, we suggest [XStream](http://xstream.codehaus.org) to serialize the trained models.
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
      <version>1.5.0</version>
    </dependency>
```

Demo Gallery
============
<table class="center" width="100%">
    <tr>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/smile/gallery/smile-demo-kpca.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-kpca-small.png" alt="Kernel PCA"></a>
                <figcaption><h2>Kernel PCA</h2></figcaption>
            </figure>
        </td>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/smile/gallery/smile-demo-isomap.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-isomap-small.png" alt="IsoMap"></a>
                <figcaption><h2>IsoMap</h2></figcaption>
            </figure>
        </td>
    </tr>
    <tr>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/smile/gallery/smile-demo-mds.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-mds-small.png" alt="MDS"></a>
                <figcaption><h2>Multi-Dimensional Scaling</h2></figcaption>
            </figure>
        </td>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/smile/gallery/smile-demo-som.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-som-small.png" alt="SOM"></a>
                <figcaption><h2>SOM</h2></figcaption>
            </figure>
        </td>
    </tr>
    <tr>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/smile/gallery/smile-demo-ann.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-ann-small.png" alt="Neural Network"></a>
                <figcaption><h2>Neural Network</h2></figcaption>
            </figure>
        </td>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/smile/gallery/smile-demo-svm.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-svm-small.png" alt="SVM"></a>
                <figcaption><h2>SVM</h2></figcaption>
            </figure>
        </td>
    </tr>
    <tr>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/smile/gallery/smile-demo-agglomerative-clustering.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-agglomerative-clustering-small.png" alt="Agglomerative Clustering"></a>
                <figcaption><h2>Agglomerative Clustering</h2></figcaption>
            </figure>
        </td>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/smile/gallery/smile-demo-xmeans.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-xmeans-small.png" alt="X-Means"></a>
                <figcaption><h2>X-Means</h2></figcaption>
            </figure>
        </td>
    </tr>
    <tr>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/smile/gallery/smile-demo-dbscan.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-dbscan-small.png" alt="DBScan"></a>
                <figcaption><h2>DBScan</h2></figcaption>
            </figure>
        </td>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/smile/gallery/smile-demo-neural-gas.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-neural-gas-small.png" alt="Neural Gas"></a>
                <figcaption><h2>Neural Gas</h2></figcaption>
            </figure>
        </td>
    </tr>
    <tr>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/smile/gallery/smile-demo-wavelet.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-wavelet-small.png" alt="Wavelet"></a>
                <figcaption><h2>Wavelet</h2></figcaption>
            </figure>
        </td>
        <td width="50%">
            <figure>
                <a href="http://haifengl.github.io/smile/gallery/smile-demo-mixture.png"><img src="http://haifengl.github.io/smile/gallery/smile-demo-mixture-small.png" alt="Mixture"></a>
                <figcaption><h2>Exponential Family Mixture</h2></figcaption>
            </figure>
        </td>
    </tr>
</table>

Tutorial
========
This tutorial shows how to use the Smile Java API for predictive modeling
(classification and regression). It includes loading data, training
and testing the model, and applying the model. If you use Scala, we
strongly recommend the new high level Scala API, which is similar to
R and Matlab. The programming guide with Scala API is available at
[project website](http://haifengl.github.io/smile/).

## Load Data
Most Smile algorithms take simple double[] as input so you can use your favorite methods or library to import the data as long as the samples are in double arrays. To make life easier, Smile does provide a couple of parsers for popular data formats, such as Weka's ARFF files, LibSVM's file format, delimited text files, and binary sparse data. These classes are in the package smile.data.parser. The package smile.data.parser.microarray also provides several parsers for microarray gene expression datasets, including GCT, PCL, RES, and TXT files. In the following example, we use the ARFF parser to load the weather dataset:
```java
ArffParser arffParser = new ArffParser();
arffParser.setResponseIndex(4);
AttributeDataset weather = arffParser.parse(new FileInputStream("data/weka/weather.nominal.arff"));
double[][] x = weather.toArray(new double[weather.size()][]);
int[] y = weather.toArray(new int[weather.size()]);
```
Note that the data file weather.nominal.arff is in Smile distribution package.
After unpacking the package, there is a lot of testing data in the directory of
`$smile/data`, where `$smile` is the the root of Smile package.

In the second line, we use `setResponseIndex` to set the column index (starting at 0) of the dependent/response variable. In supervised learning, we need a response variable for each sample to train the model. Basically, it is the _y_ in the mathematical model. For classification, it is the class label. For regression, it is of real value. Without setting it, the data assumes no response variable. In that case, the data can be used for testing or unsupervised learning.

The parse method can take a `URI`, `File`, path string, or `InputStream` as an input argument. And it returns an `AttributeDataset` object, which is a dataset of a number of attributes. All attribute values are stored as double even if the attribute may be nominal, ordinal, string, or date. The first call of `toArray` taking a `double[][]` argument fills the array with all the parsed data and returns it, of which each row is a sample/object. The second call of `toArray` taking an int array fills it with the class labels of the samples and then returns it.

The `AttributeDataset.attributes` method returns the list of `Attribute` objects in the dataset. The `Attribute` object contains the type information (and optional weight), which is needed in some algorithms (e.g. decision trees). The `Attribute` object also contains variable name and description, which are useful in the output or UI.

Similar to `ArffParser`, we can also use the `DelimitedTextParser` class to parse plain delimited text files. By default, the parser expects a white-space-separated-values file. Each line in the file corresponds to a row in the table. Within a line, fields are separated by white spaces, each field belonging to one table column. This class can also be used to read other text tabular files by setting the delimiter character such as ','. The file may contain comment lines (starting with '%') and missing values (indicated by placeholder '?'), which can both be parameterized.
```java
DelimitedTextParser parser = new DelimitedTextParser();
parser.setResponseIndex(new NominalAttribute("class"), 0);
AttributeDataset usps = parser.parse("USPS Train", new FileInputStream("data/usps/zip.train"));
```
where the `setResponseIndex` also takes an extra parameter about the attribute of the response variable. Because this is a classification problem, we set it to a `NominalAttribute` with name "class". In case of regression, we should use `NumericAttribute` instead.

If your input data contains different types of attributes (e.g. `NumericAttribute`, `NominalAttribute`, `StringAttribute`, `DateAttribute`, etc), you should pass an array of `Attribute[]` to the constructor of `DelimitedTextParser` to indicate the data types of each column. By default, `DelimitedTextParser` assumes all columns as `NumericAttribute`.

## Train The Model
Smile implements a variety of classification and regression algorithms. In what follows, we train a support vector machine (SVM) on the USPS zip code handwriting dataset. The SVM employs a Gaussian kernel and one-to-one strategy as this is a multi-class problem. Different from LibSVM or other popular SVM library, Smile implements an online learning algorithm for training SVM. The method `learn` trains the SVM with the given dataset for one epoch. The caller may call this method multiple times to obtain better accuracy although one epoch is usually sufficient. Note that after calling `learn`, we need to call the `finish` method, which processes support vectors until they converge. As it is an online algorithm, the user may update the model anytime by calling `learn` even after calling the `finish` method. In the example, we show another way of learning by working on single sample. As shown in the example, we simply call the `predict` method on a testing sample. Both `learn` and `predict` methods are generic for all classification and regression algorithms.
```java
DelimitedTextParser parser = new DelimitedTextParser();
parser.setResponseIndex(new NominalAttribute("class"), 0);
try {
    AttributeDataset train = parser.parse("USPS Train", new FileInputStream("/data/usps/zip.train"));
    AttributeDataset test = parser.parse("USPS Test", new FileInputStream("/data/usps/zip.test"));

    double[][] x = train.toArray(new double[train.size()][]);
    int[] y = train.toArray(new int[train.size()]);
    double[][] testx = test.toArray(new double[test.size()][]);
    int[] testy = test.toArray(new int[test.size()]);
            
    SVM<double[]> svm = new SVM<double[]>(new GaussianKernel(8.0), 5.0, Math.max(y)+1, SVM.Multiclass.ONE_VS_ONE);
    svm.learn(x, y);
    svm.finish();
            
    int error = 0;
    for (int i = 0; i < testx.length; i++) {
        if (svm.predict(testx[i]) != testy[i]) {
            error++;
        }
    }

    System.out.format("USPS error rate = %.2f%%\n", 100.0 * error / testx.length);
            
    System.out.println("USPS one more epoch...");
    for (int i = 0; i < x.length; i++) {
        int j = Math.randomInt(x.length);
        svm.learn(x[j], y[j]);
    }
            
    svm.finish();

    error = 0;
    for (int i = 0; i < testx.length; i++) {
        if (svm.predict(testx[i]) != testy[i]) {
            error++;
        }
    }
    System.out.format("USPS error rate = %.2f%%\n", 100.0 * error / testx.length);
} catch (Exception ex) {
    System.err.println(ex);
}
```
As aforementioned, tree based methods need the type information of attributes. In the next example, we train an `AdaBoost` model on the weather dataset.
```java
ArffParser arffParser = new ArffParser();
arffParser.setResponseIndex(4);
AttributeDataset weather = arffParser.parse(new FileInputStream("/data/weka/weather.nominal.arff"));
double[][] x = weather.toArray(new double[weather.size()][]);
int[] y = weather.toArray(new int[weather.size()]);

AdaBoost forest = new AdaBoost(weather.attributes(), x, y, 200, 4);
```
In the example, we set the number of trees to 200 and the maximum number of leaf nodes in the trees to 4, which works as a regularization control.

## Model Validation
In the example of USPS, we have both training and test datasets. However, we frequently have only a single dataset for building models. For model validation, Smile provide LOOCV (leave-one-out cross validation), cross validation, and bootstrap in the package smile.validation. Additionally, the package also has various measures to evaluate classification, regression, and clustering. For example, we have accuracy, fallout, FDR, F-measure (F1 score or F-score), precision, recall, sensitivity, specificity for classification; absolute deviation, MSE, RMSE, RSS for regression; rand index, adjust rand index for clustering. The following is an example how to use LOOCV.
```java
double[][] x = weather.toArray(new double[weather.size()][]);
int[] y = weather.toArray(new int[weather.size()]);

int n = x.length;
LOOCV loocv = new LOOCV(n);
int error = 0;
for (int i = 0; i < n; i++) {
    double[][] trainx = Math.slice(x, loocv.train[i]);
    int[] trainy = Math.slice(y, loocv.train[i]);
                
    AdaBoost forest = new AdaBoost(weather.attributes(), trainx, trainy, 200, 4);
    if (y[loocv.test[i]] != forest.predict(x[loocv.test[i]]))
        error++;
}
            
System.out.println("Decision Tree error = " + error);
```

## Use The Trained Model
All classifiers in Smile implement the following interface.
```java
public interface Classifier<T> {
    public int predict(T x);
    public int predict(T x, double[] posteriori);
}
```
To use the trained model, we can apply the method `predict` on a new sample. Besides just returning the class label, many methods (e.g. neural networks) can also output the posteriori probabilities of each class. 
