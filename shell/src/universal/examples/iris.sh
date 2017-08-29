#!/bin/bash
exec smile "$0" "$@"
!#

val data = read.arff("data/weka/iris.arff", 4)
val (x, y) = data.unzipInt

val rf = smile.classification.randomForest(x, y)
println(s"OOB error = ${rf.error}")

