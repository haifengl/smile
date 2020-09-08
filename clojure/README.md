# Smile

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=XKU5BZX7XHPQ6)
[![Join the chat at https://gitter.im/haifengl/smile](https://badges.gitter.im/haifengl/smile.svg)](https://gitter.im/haifengl/smile?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[Smile (Statistical Machine Intelligence and Learning Engine)](https://haifengl.github.io/)
is a fast and comprehensive machine learning, NLP, linear algebra,
graph, interpolation, and visualization system in Java and Scala.
With advanced data structures and algorithms, Smile delivers
state-of-art performance. Smile is well documented and please
check out the project [website](https://haifengl.github.io/)
for programming guides and more information.

For Clojure API, add the following dependency to your project or build file:
```
    [org.clojars.haifengl/smile "2.5.1"]
```

Some algorithms rely on BLAS and LAPACK (e.g. manifold learning,
some clustering algorithms, Gaussian Process regression, MLP, etc).
To use these algorithms, you should include OpenBLAS for optimized matrix
computation:
```
    [org.bytedeco/arpack-ng "3.7.0-1.5.3"]
```

If you prefer other BLAS implementations, you can use any library found on
the "java.library.path" or on the class path, by specifying it with the
"org.bytedeco.openblas.load" system property. For example, to use the BLAS
library from the Accelerate framework on Mac OS X, we can pass options such
as `-Djava.library.path=/usr/lib/ -Dorg.bytedeco.openblas.load=blas`.

For a default installation of MKL that would be `-Dorg.bytedeco.openblas.load=mkl_rt`.
Or you may simply include `smile-mkl` module in your project, which includes
MKL binaries. With `smile-mkl` module in the class path, Smile will
automatically switch to MKL.
```
    [org.clojars.haifengl/smile-mkl "2.5.1"]
```

Smile covers every aspect of machine learning, including classification,
regression, clustering, association rule mining, feature selection,
manifold learning, multidimensional scaling, genetic algorithms,
missing value imputation, efficient nearest neighbor search, etc.

Smile implements the following major machine learning algorithms:

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

