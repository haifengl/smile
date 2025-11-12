// t-SNE on MNIST
import smile.io._
import smile.feature.extraction._
import smile.manifold._
import smile.plot.swing._

var mnist = smile.read.csv(Paths.getTestData("mnist/mnist2500_X.txt").toString, delimiter=" ", header=false).toArray()
val labels = smile.read.csv(Paths.getTestData("mnist/mnist2500_labels.txt").toString, header=false).column(0).toIntArray()

val pca = PCA.fit(mnist).getProjection(50)
val X = pca(mnist)

val perplexity = 20
val tsne = new TSNE(X, 2, perplexity, 200, 1000)

val canvas = plot(tsne.coordinates, labels, '@')
canvas.window()
