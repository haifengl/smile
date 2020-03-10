// Toy example

import smile.classification.RandomForest;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.util.Paths;

var data = Read.arff(Paths.getTestData("weka/iris.arff"));
System.out.println(data);

var formula = Formula.lhs("class");
var rf = RandomForest.fit(formula, data);
System.out.format("OOB error = %.2f%%%n", 100 * rf.error());

// exit JShell
/exit
