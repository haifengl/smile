// Toy example

import smile.*
import smile.classification.*
import smile.data.formula.Formula
import smile.util.Paths

val data = read.arff(Paths.getTestData("weka/iris.arff"))
println(data)

val formula = Formula.lhs("class")
val rf = randomForest(formula, data)
println("OOB error = ${rf.error()}")
