// Benchmark on USPS zip code hand writing data

import smile.data.*;
import smile.data.formula.*;
import smile.data.measure.*;
import smile.data.type.*;
import smile.io.*;
import smile.base.cart.SplitRule;
import smile.base.rbf.RBF;
import smile.base.mlp.Layer;
import smile.classification.*;
import smile.clustering.KMeans;
import smile.math.*;
import smile.math.distance.*;
import smile.math.kernel.*;
import smile.math.rbf.*;
import smile.validation.*;
import smile.validation.metric.*;
import org.apache.commons.csv.CSVFormat;

var format = CSVFormat.DEFAULT.withDelimiter(' ');
var zipTrain = Read.csv(smile.util.Paths.getTestData("usps/zip.train"), format);
var zipTest = Read.csv(smile.util.Paths.getTestData("usps/zip.test"), format);

var formula = Formula.lhs("V1");
var x = formula.x(zipTrain).toArray();
var y = formula.y(zipTrain).toIntArray();
var testx = formula.x(zipTest).toArray();
var testy = formula.y(zipTest).toIntArray();

var n = x.length;
var k = 10;

// Random Forest
System.out.println("Training Random Forest of 200 trees...");
var prop = new java.util.Properties();
prop.setProperty("smile.random.forest.trees", "200");
var forest = RandomForest.fit(formula, zipTrain, prop);
System.out.println(forest.metrics());

var pred = forest.predict(zipTest);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, pred));

// Gradient Tree Boost
System.out.println("Training Gradient Tree Boost of 200 trees...")
prop.setProperty("smile.gbt.trees", "200");
var gbm = GradientTreeBoost.fit(formula, zipTrain, prop)
pred = gbm.predict(zipTest);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, pred));

// SVM
System.out.println("Training SVM, one epoch...")
var kernel = new GaussianKernel(8.0);
var svm = OneVersusOne.fit(x, y, (x, y) -> SVM.fit(x, y, kernel, 5, 1E-3));
pred = svm.predict(testx);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, pred));

// RBF Network
System.out.println("Training RBF Network...");
var kmeans = KMeans.fit(x, 200);
var distance = new EuclideanDistance();
var neurons = RBF.of(kmeans.centroids, new GaussianRadialBasis(8.0), distance);
var rbfnet = RBFNetwork.fit(x, y, neurons);
pred = rbfnet.predict(testx);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, pred));

// Logistic Regression
System.out.println("Training Logistic regression...")
var logit = LogisticRegression.fit(x, y, 0.3, 1E-3, 1000);
pred = logit.predict(testx);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, pred));

// Neural Network
System.out.println("Training Neural Network, 10 epoch...");
var net = new MLP(Layer.input(256),
  Layer.sigmoid(768),
  Layer.sigmoid(192),
  Layer.sigmoid(30),
  Layer.mle(10, OutputFunction.SIGMOID)
);

net.setLearningRate(TimeFunction.linear(0.01, 20000, 0.001));

for (int epoch = 0; epoch < 10; epoch++) {
  System.out.format("----- epoch %d -----%n", epoch);
  for (int i : MathEx.permutate(x.length)) {
    net.update(x[i], y[i]);
  }
  var prediction = net.predict(testx);
  System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, prediction)));
}

/exit
