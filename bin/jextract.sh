#!/bin/bash

../jextract-22/bin/jextract --include-dir /usr/include/x86_64-linux-gnu --output base/src/main/java --target-package smile.linalg.blas --library openblas /usr/include/x86_64-linux-gnu/cblas-openblas.h

../jextract-22/bin/jextract --include-dir /usr/include --output base/src/main/java --target-package smile.linalg.lapack --library lapacke /usr/include/lapacke.h
