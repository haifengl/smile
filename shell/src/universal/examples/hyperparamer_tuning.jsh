import smile.classification.RandomForest;
import smile.data.formula.Formula;
import smile.io.*;
import smile.validation.*;
import smile.validation.metric.*;

var hp = new Hyperparameters()
    .add("smile.random.forest.trees", 100) // a fixed value
    .add("smile.random.forest.mtry", new int[] {2, 3, 4}) // an array of values to choose
    .add("smile.random.forest.max.nodes", 100, 500, 50); // range [100, 500] with step 50


var train = Read.arff("data/weka/segment-challenge.arff");
var test = Read.arff("data/weka/segment-test.arff");
var formula = Formula.lhs("class");
var testy = formula.y(test).toIntArray();

// grid search
hp.grid().forEach(prop -> {
    var model = RandomForest.fit(formula, train, prop);
    var pred = model.predict(test);
    System.out.println(prop);
    System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
    System.out.println(ConfusionMatrix.of(testy, pred));
});

// random search
hp.random().limit(20).forEach(prop -> {
    var model = RandomForest.fit(formula, train, prop);
    var pred = model.predict(test);
    System.out.println(prop);
    System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
    System.out.println(ConfusionMatrix.of(testy, pred));
});
