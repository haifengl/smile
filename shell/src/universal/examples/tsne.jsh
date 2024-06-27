// t-SNE on MNIST

import smile.io.Read;
import smile.util.Paths;
import smile.manifold.TSNE;
import smile.feature.extraction.PCA;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotPanel;
import smile.plot.swing.ScatterPlot;
import org.apache.commons.csv.CSVFormat;

var format = CSVFormat.DEFAULT.withDelimiter(' ');
var mnist = Read.csv(Paths.getTestData("mnist/mnist2500_X.txt"), format).toArray();
var labels = Read.csv(Paths.getTestData("mnist/mnist2500_labels.txt"), format).column(0).toIntArray();

var pca = PCA.fit(mnist).getProjection(50);
var X = pca.apply(mnist);

var perplexity = 20;
var tsne = new TSNE(X, 2, perplexity, 200, 1000);

var plot = ScatterPlot.of(tsne.coordinates, labels, '@');
plot.setTitle("t-SNE of MNIST");
plot.canvas().window();

/exit