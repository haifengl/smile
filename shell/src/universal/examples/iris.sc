val data = read.arff("data/weka/iris.arff")
val formula = Formula.lhs("class")
println(data)

val rf = smile.classification.randomForest(formula, data)
println(s"OOB error = %.2f%%" format 100 * rf.error)

