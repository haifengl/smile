#!/bin/bash
exec smile -nc "$0" "$@"
!#
import smile._
import smile.data._
import smile.shell._
import smile.classification._

val data = read.arff("data/weka/iris.arff", 4)
val (x, y) = data.unzipInt

val rf = randomForest(x, y)
println(s"OOB error = ${rf.error}")

