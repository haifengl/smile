// Regression

import smile.data.*;
import smile.data.formula.*;
import smile.io.*;
import smile.regression.*;
import smile.clustering.KMeans;
import smile.math.distance.*;
import smile.math.kernel.*;
import smile.math.rbf.*;
import smile.model.rbf.RBF;
import smile.validation.*;
import smile.validation.metric.*;
import org.apache.commons.csv.CSVFormat;

// Ordinary Least Squares
var planes = Read.arff(Paths.getTestData("weka/regression/2dplanes.arff"));
var model = OLS.fit(Formula.lhs("y"), planes);
IO.println(model);
model.predict(planes.get(0));

//--- CELL ---
// Ridge Regression
var longley = Read.arff(Paths.getTestData("weka/regression/longley.arff"));
var ridge = RidgeRegression.fit(Formula.lhs("employed"), longley, 0.0057);
IO.println(ridge);

//--- CELL ---
// Lasso Regression
var diabetes = Read.csv(Paths.getTestData("regression/diabetes.csv"), CSVFormat.DEFAULT.withFirstRecordAsHeader());
var formula = Formula.lhs("y");
LASSO.fit(formula, diabetes, new LASSO.Options(10));
LASSO.fit(formula, diabetes, new LASSO.Options(100));
LASSO.fit(formula, diabetes, new LASSO.Options(500));

//--- CELL ---
// Radial Basis Function Networks
var y = diabetes.column("y").toDoubleArray();
var x = diabetes.select(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).toArray(); // use only the primary attributes
var rbf = CrossValidation.regression(10, x, y,
        (x, y) -> RBFNetwork.fit(x, y, RBF.fit(x, 10), false));
IO.println(rbf);

//--- CELL ---
// Support Vector Regression
var svmOptions = new SVM.Options(20, 10);
var svm = CrossValidation.regression(10, x, y,
        (x, y) -> SVM.fit(x, y, new GaussianKernel(0.06), svmOptions));
IO.println(svm);

//--- CELL ---
// Regression Tree
var tree = CrossValidation.regression(10, Formula.lhs("y"), diabetes,
        (formula, data) -> RegressionTree.fit(formula, data));
IO.println(tree);

//--- CELL ---
// Random Forest
var forest = CrossValidation.regression(10, Formula.lhs("y"), diabetes,
        (formula, data) -> RandomForest.fit(formula, data));
IO.println(forest);

//--- CELL ---
// Gradient Boosting
var gbm = CrossValidation.regression(10, Formula.lhs("y"), diabetes,
        (formula, data) -> GradientTreeBoost.fit(formula, data));
IO.println(gbm);

//--- CELL ---
// Gaussian Process
var gpOptions = new GaussianProcessRegression.Options(0.01);
var gp = CrossValidation.regression(10, x, y, (x, y) -> {
        var t = KMeans.fit(x, 20, 5).centers();
        return GaussianProcessRegression.fit(x, y, t, new GaussianKernel(0.06), gpOptions);
    });
IO.println(gp);
