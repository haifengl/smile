// Benchmark on USPS zip code hand writing data

import smile.data.*;
import smile.data.formula.*;
import smile.data.measure.*;
import smile.data.type.*;
import smile.io.*;
import smile.base.rbf.RBF;
import smile.base.mlp.Layer;
import smile.base.mlp.OutputFunction;
import smile.classification.*;
import smile.clustering.KMeans;
import smile.math.*;
import smile.math.distance.*;
import smile.math.kernel.*;
import smile.math.rbf.*;
import smile.util.function.TimeFunction;
import smile.validation.*;
import smile.validation.metric.*;
import org.apache.commons.csv.CSVFormat;

var format = CSVFormat.DEFAULT.withDelimiter(' ');
var zipTrain = Read.csv(Paths.getTestData("usps/zip.train"), format);
var zipTest = Read.csv(Paths.getTestData("usps/zip.test"), format);

var formula = Formula.lhs("V1");
var x = formula.x(zipTrain).toArray();
var y = formula.y(zipTrain).toIntArray();
var testx = formula.x(zipTest).toArray();
var testy = formula.y(zipTest).toIntArray();

var n = x.length;
var k = 10;
//--- CELL ---
// Random Forest
System.out.println("Training Random Forest of 200 trees...");
var forest = RandomForest.fit(formula, zipTrain, new RandomForest.Options(200));
System.out.println(forest.metrics());

var rfpred = forest.predict(zipTest);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, rfpred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, rfpred));
//--- CELL ---
// Gradient Tree Boost
System.out.println("Training Gradient Tree Boost of 200 trees...");
var gbm = GradientTreeBoost.fit(formula, zipTrain, new GradientTreeBoost.Options(200));
var gbmpred = gbm.predict(zipTest);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, gbmpred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, gbmpred));
//--- CELL ---
// SVM
System.out.println("Training SVM, one epoch...");
var kernel = new GaussianKernel(8.0);
var svm = OneVersusRest.fit(x, y, (x, y) -> SVM.fit(x, y, kernel, new SVM.Options(5)));
var svmpred = svm.predict(testx);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, svmpred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, svmpred));
//--- CELL ---
// RBF Network
System.out.println("Training RBF Network...");
var kmeans = KMeans.fit(x, 200, 10);
var distance = new EuclideanDistance();
var neurons = RBF.of(kmeans.centers(), new GaussianRadialBasis(8.0), distance);
var rbfnet = RBFNetwork.fit(x, y, neurons);
var rbfpred = rbfnet.predict(testx);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, rbfpred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, rbfpred));
//--- CELL ---
// Logistic Regression
System.out.println("Training Logistic regression...");
var logit = LogisticRegression.fit(x, y, new LogisticRegression.Options(0.3, 1E-3, 1000));
var logitpred = logit.predict(testx);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, logitpred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, logitpred));
//--- CELL ---
// Neural Network
System.out.println("Training Neural Network, 10 epoch...");

var net = new MLP(Layer.input(256),
                  Layer.leaky(768, 0.2, 0.02),
                  Layer.rectifier(192),
                  Layer.rectifier(30),
                  Layer.mle(k, OutputFunction.SOFTMAX)
);

net.setLearningRate(TimeFunction.linear(0.01, 20000, 0.001));

for (int epoch = 1; epoch <= 10; epoch++) {
  System.out.format("----- epoch %d -----%n", epoch);
  for (int i : MathEx.permutate(x.length)) {
    net.update(x[i], y[i]);
  }
  var prediction = net.predict(testx);
  System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, prediction)));
}
