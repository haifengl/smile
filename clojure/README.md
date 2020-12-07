# README

[Smile (Statistical Machine Intelligence and Learning Engine)](https://haifengl.github.io/)
is a fast and comprehensive machine learning, NLP, linear algebra,
graph, interpolation, and visualization system in Java and Scala.
With advanced data structures and algorithms, Smile delivers
state-of-art performance. Smile is well documented and please
check out the project [website](https://haifengl.github.io/)
for programming guides and more information.

For Clojure API, add the following dependency to your project or build file:
```
    [org.clojars.haifengl/smile "2.6.0"]
```

Some algorithms rely on BLAS and LAPACK (e.g. manifold learning,
some clustering algorithms, Gaussian Process regression, MLP, etc).
To use these algorithms, you should include OpenBLAS for optimized matrix
computation:
```
    [org.bytedeco/javacpp-platform   "1.5.4"]
    [org.bytedeco/openblas-platform  "0.3.10-1.5.4"]
    [org.bytedeco/arpack-ng-platform "3.7.0-1.5.4"]
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
    [org.clojars.haifengl/smile-mkl "2.6.0"]
```

Smile covers every aspect of machine learning, including classification,
regression, clustering, association rule mining, feature selection,
manifold learning, multidimensional scaling, genetic algorithms,
missing value imputation, efficient nearest neighbor search, etc.
