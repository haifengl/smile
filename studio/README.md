# Smile Studio

SMILE (Statistical Machine Intelligence & Learning Engine) is a fast and
comprehensive machine learning framework in Java. With advanced data
structures and  algorithms, SMILE delivers state-of-art performance.
SMILE covers every aspect of machine learning, including deep learning,
large language models, classification, regression, clustering, association
rule mining, feature selection and extraction, manifold learning,
multidimensional scaling, genetic algorithms, missing value imputation,
efficient nearest neighbor search, etc. Furthermore, SMILE also provides
advanced algorithms for graph, linear algebra, numerical analysis,
interpolation, computer algebra system for symbolic manipulations,
and data visualization.

Smile Studio is an interactive desktop application to help you be more
productive in building and serving models with SMILE. Similar to Jupyter
Notebooks, Smile Studio is a REPL (Read-Evaluate-Print-Loop) containing
an ordered list of input/output cells. Cd into the `bin` directory of SMILE
in a terminal, type
```shell script
    ./smile
```
to enter Smile Studio. If you work in a headless environment without
graphical interface, you may run `./smile shell` to enter Smile Shell
for Java, which pre-imports all major SMILE packages. If you prefer
Scala, type `./smile scala` to enter Smile Shell for Scala.

By default, Smile Studio/Shell uses up to 4GB memory. If you need more memory
to handle large data, use the option `-J-Xmx` or `-XX:MaxRAMPercentage`.
For example,
```shell script
    ./smile -J-Xmx30G
```
You can also modify the configuration file `conf/smile.ini` for the
memory and other JVM settings.

## System Requirements ##
Some algorithms rely on BLAS and LAPACK (e.g. manifold learning,
some clustering algorithms, Gaussian Process regression, MLP, etc.).
To use these algorithms, you should install OpenBLAS and ARPACK
for optimized matrix computation. For Windows, you can find the
pre-built DLL files from the `bin` directory of release packages.

To install on Linux (e.g., Ubuntu), run
```shell script
sudo apt update
sudo apt install libopenblas-dev libarpack2
```

On Mac, we use the BLAS library from the Accelerate framework
provided by macOS. The pre-built ARPACK for Apple silicon is
included in the `bin` directory of release packages.
However, macOS System Integrity Protection (SIP) significantly
impacts how JVM handles dynamic library loading by purging dynamic
linker (DYLD) environment variables like DYLD_LIBRARY_PATH when
launching protected processes. To work around, make sure to use
`bin` as working directory so that JVM can successfully load it.

For macOS on Intel chips, install ARPACK by running
```shell script
brew install arpack
```
SMILE will pick up the library seamlessly on these old systems.

## License ##
SMILE employs a dual license model designed to meet the development
and distribution needs of both commercial distributors (such as OEMs,
ISVs and VARs) and open source projects. For details, please see
[LICENSE](https://github.com/haifengl/smile/blob/master/LICENSE).
To acquire a commercial license, please contact smile.sales@outlook.com.

## Issues/Discussions ##

* **Discussion/Questions**:
If you wish to ask questions about SMILE, we're active on
[GitHub Discussions](https://github.com/haifengl/smile/discussions) and
[Stack Overflow](http://stackoverflow.com/questions/tagged/smile).

* **Docs**:
SMILE is well documented and [our docs are available online](https://haifengl.github.io/), where you can find tutorial,
programming guides, and more information. [Java Docs](https://haifengl.github.io/api/java/index.html),
[Scala Docs](https://haifengl.github.io/api/scala/index.html), [Kotlin Docs](https://haifengl.github.io/api/kotlin/index.html),
and [Clojure Docs](https://haifengl.github.io/api/clojure/index.html) are also available.

* **Issues/Feature Requests**:
  Finally, any bugs or features, please report to our [issue tracker](https://github.com/haifengl/smile/issues/new).
