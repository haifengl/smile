// t-SNE on MNIST
import smile.io.*
import smile.feature.extraction.*
import smile.manifold.*
import smile.plot.swing.*

var mnist = smile.read.csv(Paths.getTestData("mnist/mnist2500_X.txt").toString, delimiter=" ", header=false).toArray()
val labels = smile.read.csv(Paths.getTestData("mnist/mnist2500_labels.txt").toString, header=false).column(0).toIntArray()

val pca = PCA.fit(mnist).getProjection(50)
val X = pca(mnist)

val model = tsne(X, 2, 20, 200, 12, 550)
val canvas = plot(model.coordinates, labels, '@')
canvas.window()