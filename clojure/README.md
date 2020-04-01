# Smile

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=XKU5BZX7XHPQ6)
[![Join the chat at https://gitter.im/haifengl/smile](https://badges.gitter.im/haifengl/smile.svg)](https://gitter.im/haifengl/smile?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Smile (Statistical Machine Intelligence and Learning Engine) is
a fast and comprehensive machine learning, NLP, linear algebra,
graph, interpolation, and visualization system in Java and Scala.
With advanced data structures and algorithms,
Smile delivers state-of-art performance.
Smile is well documented and please check out the project
[website](http://haifengl.github.io/)
for programming guides and more information.

Smile covers every aspect of machine learning, including classification,
regression, clustering, association rule mining, feature selection,
manifold learning, multidimensional scaling, genetic algorithms,
missing value imputation, efficient nearest neighbor search, etc.

Smile implements the following major machine learning algorithms:

- **Classification:**
Support Vector Machines, Decision Trees, AdaBoost, Gradient Boosting, Random Forest, Logistic Regression, Neural Networks, RBF Networks, Maximum Entropy Classifier, KNN, Naïve Bayesian, Fisher/Linear/Quadratic/Regularized Discriminant Analysis.

- **Regression:**
Support Vector Regression, Gaussian Process, Regression Trees, Gradient Boosting, Random Forest, RBF Networks, OLS, LASSO, ElasticNet, Ridge Regression.

- **Feature Selection:**
Genetic Algorithm based Feature Selection, Ensemble Learning based Feature Selection, Signal Noise ratio, Sum Squares ratio.

- **Clustering:**
BIRCH, CLARANS, DBSCAN, DENCLUE, Deterministic Annealing, K-Means, X-Means, G-Means, Neural Gas, Growing Neural Gas, Hierarchical Clustering, Sequential Information Bottleneck, Self-Organizing Maps, Spectral Clustering, Minimum Entropy Clustering.

- **Association Rule & Frequent Itemset Mining:**
FP-growth mining algorithm.

- **Manifold Learning:**
IsoMap, LLE, Laplacian Eigenmap, t-SNE, PCA, Kernel PCA, Probabilistic PCA, GHA, Random Projection, ICA.

- **Multi-Dimensional Scaling:**
Classical MDS, Isotonic MDS, Sammon Mapping.

- **Nearest Neighbor Search:**
BK-Tree, Cover Tree, KD-Tree, LSH.

- **Sequence Learning:**
Hidden Markov Model, Conditional Random Field.

- **Natural Language Processing:**
Sentence Splitter and Tokenizer, Bigram Statistical Test, Phrase Extractor, Keyword Extractor, Stemmer, POS Tagging, Relevance Ranking


To enable machine optimized matrix computation, the users should
make their machine-optimized libblas3 (CBLAS) and liblapack3 (Fortran)
available as shared libraries at runtime. This module employs the highly efficient
[netlib-java](https://github.com/fommil/netlib-java#netlib-java) library.

#### OS X
Apple OS X requires no further setup as it ships with the veclib framework.

#### Linux
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

#### Windows
The native_system builds expect to find libblas3.dll and liblapack3.dll
on the %PATH% (or current working directory). Smile ships a prebuilt
[OpenBLAS](http://www.openblas.net/).
The users can also install vendor-supplied implementations, which may
offer better performance.

