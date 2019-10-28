// Toy example

val data = read.arff(Paths.getTestData("weka/iris.arff"))
println(data)

val formula = "class" ~
val rf = smile.classification.randomForest(formula, data)
println(s"OOB error = %.2f%%" format 100 * rf.error)

