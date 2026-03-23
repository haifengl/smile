import smile.io.Read;
import smile.io.Paths;
import smile.manifold.TSNE;
import smile.manifold.UMAP;
import smile.feature.extraction.PCA;
import smile.plot.swing.ScatterPlot;
import static smile.swing.SmileUtilities.*;
import org.apache.commons.csv.CSVFormat;

var format = CSVFormat.DEFAULT.withDelimiter(' ');
var mnist = Read.csv(Paths.getTestData("mnist/mnist2500_X.txt"), format).toArray();
var labels = Read.csv(Paths.getTestData("mnist/mnist2500_labels.txt"), format).column(0).toIntArray();

var pca = PCA.fit(mnist).getProjection(50);
var X = pca.apply(mnist);

// t-SNE on MNIST
var tsne = TSNE.fit(X, new TSNE.Options(2, 20, 200, 12, 550));

var figure = ScatterPlot.of(tsne.coordinates(), labels, '@').figure();
figure.setTitle("MNIST - t-SNE");
show(figure);

// UMAP on MNIST
var umap = UMAP.fit(mnist, new UMAP.Options(15));
figure = ScatterPlot.of(umap, labels, '@').figure();
figure.setTitle("MNIST - UMAP");
show(figure);
