// Toy example
import smile.*
import smile.io.*
import smile.data.formula.*
import smile.classification.*

val data = read.arff(Paths.getTestData("weka/iris.arff"))
println(data)

val formula = "class" ~ "."
val rf = randomForest(formula, data)
println(rf.metrics())