// Toy example

import smile.classification.RandomForest;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.io.Paths;

var data = Read.arff(Paths.getTestData("weka/iris.arff"));
IO.println(data);

var formula = Formula.lhs("class");
var rf = RandomForest.fit(formula, data);
IO.println(rf.metrics());
