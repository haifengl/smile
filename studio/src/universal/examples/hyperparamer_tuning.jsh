import smile.classification.RandomForest;
import smile.data.formula.Formula;
import smile.hpo.Hyperparameters;
import smile.io.*;
import smile.validation.*;
import smile.validation.metric.*;

var hp = new Hyperparameters();
hp.add("smile.random.forest.trees", 100); // a fixed value
hp.add("smile.random.forest.mtry", new int[] {2, 3, 4}); // an array of values to choose
hp.add("smile.random.forest.max.nodes", 100, 500, 50); // range [100, 500] with step 50

var train = Read.arff(Paths.getTestData("weka/segment-challenge.arff"));
var test = Read.arff(Paths.getTestData("weka/segment-test.arff"));
var formula = Formula.lhs("class");
var testy = formula.y(test).toIntArray();

//--- CELL ---
// grid search
hp.grid().forEach(prop -> {
    var model = RandomForest.fit(formula, train, RandomForest.Options.of(prop));
    var pred = model.predict(test);
    System.out.println(prop);
    System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
    System.out.println(ConfusionMatrix.of(testy, pred));
});

//--- CELL ---
// random search
hp.random().limit(20).forEach(prop -> {
    var model = RandomForest.fit(formula, train, RandomForest.Options.of(prop));
    var pred = model.predict(test);
    System.out.println(prop);
    System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
    System.out.println(ConfusionMatrix.of(testy, pred));
});
