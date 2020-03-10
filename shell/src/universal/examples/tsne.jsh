// t-SNE on MNIST

import smile.io.Read;
import smile.util.Paths;
import smile.manifold.TSNE;
import smile.projection.PCA;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotPanel;
import smile.plot.swing.ScatterPlot;
import org.apache.commons.csv.CSVFormat;

var format = CSVFormat.DEFAULT.withDelimiter(' ');
var mnist = Read.csv(Paths.getTestData("mnist/mnist2500_X.txt"), format).toArray();
var labels = Read.csv(Paths.getTestData("mnist/mnist2500_labels.txt"), format).column(0).toIntArray();

var pca = PCA.fit(mnist);
pca.setProjection(50);
var X = pca.project(mnist);

var perplexity = 20;
var tsne = new TSNE(X, 2, perplexity, 200, 1000);

var plot = ScatterPlot.plot(tsne.coordinates, labels, '@', Palette.COLORS);
plot.setTitle("t-SNE of MNIST");
plot.window();
