import java.awt.Color;
import java.util.*;
import java.util.stream.*;
import smile.io.*;
import smile.stat.distribution.*;
import smile.tensor.*;
import smile.util.function.*;
import smile.interpolation.BicubicInterpolation;
import smile.plot.swing.*;
import static smile.swing.SmileUtilities.*;

double[][] heart = new double[200][2];
for (int i = 0; i < heart.length; i++) {
    double t = PI * (i - 100) / 100;
    heart[i][0] = 16 * pow(sin(t), 3);
    heart[i][1] = 13 * cos(t) - 5 * cos(2*t) - 2 * cos(3*t) - cos(4*t);
}
var figure = LinePlot.of(heart, Color.RED).figure();
figure.setTitle("Mathematical Beauty");
show(figure);
//--- CELL ---
var home = System.getProperty("smile.home");
var iris = Read.arff(home + "/data/weka/iris.arff");
var figure = ScatterPlot.of(iris, "sepallength", "sepalwidth", "class", '*').figure();
figure.setAxisLabels("sepallength", "sepalwidth");
figure.setTitle("Iris");
show(figure);
//--- CELL ---
var figure = ScatterPlot.of(iris, "sepallength", "sepalwidth", "petallength", "class", '*').figure();
figure.setAxisLabels("sepallength", "sepalwidth", "petallength");
figure.setTitle("Iris 3D");
show(figure);
//--- CELL ---
var splom = MultiFigurePane.splom(iris, '*', "class");
var frame = show(splom);
frame.setTitle("Scatterplot Matrix");
//--- CELL ---
String[] labels = ((smile.data.measure.NominalScale) iris.schema().field("class").measure()).levels();
double[][] data = new double[labels.length][];
for (int i = 0; i < data.length; i++) {
    var label = labels[i];
    data[i] = iris.stream().
          filter(row -> row.getString("class").equals(label)).
          mapToDouble(row -> row.getFloat("sepallength")).
          toArray();
}
var figure = new BoxPlot(data, labels).figure();
figure.setAxisLabels("", "sepallength");
figure.setTitle("Box Plot");
show(figure);
//--- CELL ---
var cow = Read.csv(home + "/data/stat/cow.txt").column("V1").toDoubleArray();
var figure = Histogram.of(cow, 50, true).figure();
figure.setAxisLabels("Weight", "Probability");
figure.setTitle("Cow Weight");
show(figure);

var figure = Histogram.of(Arrays.stream(cow).filter(w -> w <= 3500).toArray(), 50, true).figure();
figure.setAxisLabels("Weight", "Probability");
figure.setTitle("Cow Weight <= 3500");
show(figure);
//--- CELL ---
var gauss = new GaussianDistribution(0.0, 1.0);
var data = DoubleStream.generate(gauss::rand).limit(1000).toArray();
var figure = QQPlot.of(data).figure();
figure.setTitle("Q-Q Plot");
show(figure);
//--- CELL ---
double[][] z = {
      {1.0, 2.0, 4.0, 1.0},
      {6.0, 3.0, 5.0, 2.0},
      {4.0, 2.0, 1.0, 5.0},
      {5.0, 4.0, 2.0, 3.0}
};

// make the matrix larger with bicubic interpolation
double[] x = {0.0, 1.0, 2.0, 3.0};
double[] y = {0.0, 1.0, 2.0, 3.0};
var bicubic = new BicubicInterpolation(x, y, z);
var Z = new double[101][101];
for (int i = 0; i <= 100; i++) {
    for (int j = 0; j <= 100; j++)
        Z[i][j] = bicubic.interpolate(i * 0.03, j * 0.03);
}

var figure = Heatmap.of(Z, Palette.jet(256)).figure();
figure.add(Contour.of(Z));
figure.setTitle("Heatmap with Contour");
show(figure);
//--- CELL ---
var figure = Surface.of(Z, Palette.jet(256, 1.0f)).figure();
figure.setTitle("Surface Plot");
show(figure);
