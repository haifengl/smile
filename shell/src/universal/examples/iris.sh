#!/bin/bash
exec smile -nc "$0" "$@"
!#
import smile.data._
import smile.shell._

val data = readArff("data/weka/iris.arff", 4)
val (x, y) = data.unzipInt

val rf = randomDecisionForest(x, y)
println(s"OOB error = ${rf.error}")

