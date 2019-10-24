val data = read.arff(Paths.getTestData("weka/iris.arff").toString)
val formula = Formula.lhs("class")
println(data)

val rf = smile.classification.randomForest(formula, data)
println(s"OOB error = %.2f%%" format 100 * rf.error)

