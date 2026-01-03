// Toy example
import smile._
import smile.io._
import smile.data.formula._
import smile.classification._

val data = read.arff(Paths.getTestData("weka/iris.arff"))
println(data)

val formula = "class" ~ "."
val rf = randomForest(formula, data)
println(rf.metrics())
