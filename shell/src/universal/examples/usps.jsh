// Benchmark on USPS zip code hand writing data

import smile.data.*;
import smile.data.formula.*;
import smile.data.measure.*;
import smile.data.type.*;
import smile.io.*;
import smile.base.cart.SplitRule;
import smile.base.rbf.RBF;
import smile.base.mlp.*;
import smile.classification.*;
import smile.clustering.KMeans;
import smile.feature.*;
import smile.math.*;
import smile.math.distance.*;
import smile.math.kernel.*;
import smile.math.rbf.*;
import smile.validation.*;
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
System.out.format("OOB error rate = %.2f%%%n", (100.0 * forest.error()));

var pred = Validation.test(forest, zipTest);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, pred));

// Gradient Tree Boost
System.out.println("Training Gradient Tree Boost of 200 trees...")
prop.setProperty("smile.gbt.trees", "200");
var gbm =GradientTreeBoost.fit(formula, zipTrain, prop)
pred = Validation.test(gbm, zipTest);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, pred));

// SVM
System.out.println("Training SVM, one epoch...")
var kernel = new GaussianKernel(8.0);
var svm = OneVersusOne.fit(x, y, (x, y) -> SVM.fit(x, y, kernel, 5, 1E-3));
pred = Validation.test(svm, testx);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, pred));

// RBF Network
System.out.println("Training RBF Network...");
var kmeans = KMeans.fit(x, 200);
var distance = new EuclideanDistance();
var neurons = RBF.of(kmeans.centroids, new GaussianRadialBasis(8.0), distance);
var rbfnet = RBFNetwork.fit(x, y, neurons);
pred = Validation.test(rbfnet, testx);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, pred));

// Logistic Regression
System.out.println("Training Logistic regression...")
var logit = LogisticRegression.fit(x, y, 0.3, 1E-3, 1000);
pred = Validation.test(logit, testx);
System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, pred)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, pred));

// Neural Network
var scaler = Standardizer.fit(x);
var scaledX = scaler.transform(x);
var scaledTestX = scaler.transform(testx);

System.out.println("Training Neural Network, 10 epoch...");
var net = new MLP(256,
  Layer.sigmoid(768),
  Layer.sigmoid(192),
  Layer.sigmoid(30),
  Layer.mle(k, OutputFunction.SIGMOID)
)
net.setLearningRate(0.1);
net.setMomentum(0.0);

for (int epoch = 0; epoch < 10; epoch++) {
  System.out.format("----- epoch %d -----%n", epoch);
  for (int i : MathEx.permutate(n)) {
    net.update(scaledX[i], y[i]);
  }
  var prediction = Validation.test(net, scaledTestX);
  System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, prediction)));
}

/exit
