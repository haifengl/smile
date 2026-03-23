# README

The Clojure API is deprecated due to limited usage.
Please use SMILE's Java API directly.

[SMILE (Statistical Machine Intelligence and Learning Engine)](https://haifengl.github.io/)
is a fast and comprehensive machine learning, NLP, linear algebra,
graph, interpolation, and visualization system in Java and Scala.
With advanced data structures and algorithms, SMILE delivers
state-of-art performance. SMILE is well documented and please
check out the project [website](https://haifengl.github.io/)
for programming guides and more information.

For Clojure API, add the following dependency to your project or build file:
```
    [org.clojars.haifengl/smile "4.2.0"]
```

Some algorithms rely on BLAS and LAPACK (e.g. manifold learning,
some clustering algorithms, Gaussian Process regression, MLP, etc.).
To use these algorithms, you should include OpenBLAS for optimized matrix
computation:
```
    [org.bytedeco/javacpp-platform   "1.5.11"]
    [org.bytedeco/openblas-platform  "0.3.28-1.5.11"]
    [org.bytedeco/arpack-ng-platform "3.9.1-1.5.11"]
```

If you prefer other BLAS implementations, you can use any library found on
the "java.library.path" or on the class path, by specifying it with the
"org.bytedeco.openblas.load" system property. For example, to use the BLAS
library from the Accelerate framework on Mac OS X, we can pass options such
as `-Djava.library.path=/usr/lib/ -Dorg.bytedeco.openblas.load=blas`.

SMILE covers every aspect of machine learning, including classification,
regression, clustering, association rule mining, feature selection,
manifold learning, multidimensional scaling, genetic algorithms,
missing value imputation, efficient nearest neighbor search, etc.
