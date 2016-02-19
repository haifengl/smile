/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile

/** Common shell commands.
  *
  * @author Haifeng Li
  */
package object shell {

  /** Built in benchmarks */
  def benchmark(tests: String*) = {
    smile.benchmark.Benchmark.main(tests.toArray)
  }

  /** Show demo window */
  def demo = {
    javax.swing.SwingUtilities.invokeLater(new Runnable {
      override def run(): Unit = {
        smile.demo.SmileDemo.createAndShowGUI(false)
      }
    })
  }
  /** Print help summary */
  def help: Unit = {
    help("")
  }

  /** Print the information of a command. */
  def help(command: String) = command match {
    case "help" => println("print the command summary")
    case "read" => println(
      """
        |  def read(file: String): AnyRef
        |
        |  Reads an object/model back from a file created by write command.
      """.stripMargin)
    case "write" => println(
      """
        |  def write[T <: Object](x: T, file: String): Unit
        |
        |  Writes an object/model to a file.
      """.stripMargin)
    case "readArff" => println(
      """
        |  def readArff(file: String): AttributeDataset
        |
        |  Reads an ARFF file.
      """.stripMargin)
    case "readLibsvm" => println(
      """
        |  def readLibsvm(file: String): SparseDataset
        |
        |  Reads a LivSVM file.
      """.stripMargin)
    case "readSparseMatrix" => println(
      """
        |  def readSparseMatrix(file: String): SparseMatrix
        |
        |  Reads Harwell-Boeing column-compressed sparse matrix.
      """.stripMargin)
    case "readSparseData" => println(
      """
        |  def readSparseData(file: String, arrayIndexStartBase: Int = 0): SparseDataset
        |
        |  Reads spare dataset in coordinate triple tuple list format.
        |  Coordinate file stores a list of (row, column, value) tuples:
        |
        |    instanceID attributeID value
        |    instanceID attributeID value
        |    instanceID attributeID value
        |    instanceID attributeID value
        |    ...
        |    instanceID attributeID value
        |    instanceID attributeID value
        |    instanceID attributeID value
        |
        |  Ideally, the entries are sorted (by row index, then column index) to
        |  improve random access times. This format is good for incremental matrix
        |  construction.
        |
        |  Optionally, there may be 2 header lines
        |
        |    D    // The number of instances
        |    W    // The number of attributes
        |
        |  or 3 header lines
        |
        |    D    // The number of instances
        |    W    // The number of attributes
        |    N    // The total number of nonzero items in the dataset.
        |
        |  These header lines will be ignored.
        |
        |  @param arrayIndexStartBase the starting index of array. By default, it is
        |    0 as in C/C++ and Java. But it could be 1 to parse data produced
        |    by other programming language such as Fortran.
      """.stripMargin)
    case "readBinarySparseData" => println(
      """
        |  def readBinarySparseData(file: String): BinarySparseDataset
        |
        |  Reads binary sparse dataset. Each item is stored as an integer array, which
        |  are the indices of nonzero elements in ascending order
      """.stripMargin)
    case "readTable" | "readCsv" => println(
      """
        |  def readTable(file: String, response: Option[(Attribute, Int)], delimiter: String = "\\s+", comment: String = "%", missing: String = "?", header: Boolean = false, rowNames: Boolean = false): AttributeDataset
        |  def readCsv(file: String, response: Option[(Attribute, Int)], comment: String = "%", missing: String = "?", header: Boolean = false, rowNames: Boolean = false): AttributeDataset
        |
        |  Reads a delimited text file with response variable. By default, the parser expects a
        |  white-space-separated-values file. Each line in the file corresponds
        |  to a row in the table. Within a line, fields are separated by white spaces,
        |  each field belonging to one table column. This class can also be
        |  used to read other text tabular files by setting delimiter character
        |  such ash ','. The file may contain comment lines (starting with '%')
        |  and missing values (indicated by placeholder '?').
        |
        |  @param file the file path
        |  @param response the attribute type of response variable
        |  @param responseIndex the column index of response variable. The column index starts at 0.
        |  @param delimiter delimiter string
        |  @param comment the start of comment lines
        |  @param missing the missing value placeholder
        |  @param header true if the first row is header/column names
        |  @param rowNames true if the first column is row id/names
      """.stripMargin)
    case "readGct" | "readPcl" | "readRes" | "readRes" => println(
      """
        |  def readGct(file: String): AttributeDataset
        |  def readPcl(file: String): AttributeDataset
        |  def readRes(file: String): AttributeDataset
        |  def readRes(file: String): AttributeDataset
        |
        |  Reads microarray gene expression file.
        |
        |   The TXT format is a tab delimited file
        |   format that describes an expression dataset. It is organized as follows:
        |
        |   The first line contains the labels Name and Description followed by the
        |   identifiers for each sample in the dataset. The Description is optional.
        |
        |     Line format: Name(tab)Description(tab)(sample 1 name)(tab)(sample 2 name) (tab) ... (sample N name)
        |
        |     Example: Name Description DLBC1_1 DLBC2_1 ... DLBC58_0
        |
        |   The remainder of the file contains data for each of the genes. There is one
        |   line for each gene. Each line contains the gene name, gene description, and
        |   a value for each sample in the dataset. If the first line contains the
        |   Description label, include a description for each gene. If the first line
        |   does not contain the Description label, do not include descriptions for
        |   any gene. Gene names and descriptions can contain spaces since fields are
        |   separated by tabs.
        |
        |     Line format: (gene name) (tab) (gene description) (tab) (col 1 data) (tab) (col 2 data) (tab) ... (col N data)
        |
        |     Example: AFFX-BioB-5_at AFFX-BioB-5_at (endogenous control) -104 -152 -158 ... -44
      """.stripMargin)
    case "window" => println(
      """
        |  def window(title: String = ""): JFrame
        |
        |  Create a plot window.
      """.stripMargin)
    case "plot" => println(
      """
        |  def plot(data: Array[Array[Double]], legend: Char = '*', color: Color = Color.BLACK): (JFrame, PlotCanvas)
        |  def plot(data: Array[Array[Double]], labels: Array[String]): (JFrame, PlotCanvas)
        |  def plot(data: Array[Array[Double]], label: Array[Int], legend: Array[Char], palette: Array[Color]): (JFrame, PlotCanvas)
        |
        |  Scatter plot.
        |
        |  @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
        |  @param color the color used to draw points.
        |  @param legend the legend used to draw points. Available legends are
        |    . : dot
        |    + : +
        |    - : -
        |    | : |
        |    * : star
        |    x : x
        |    o : circle
        |    O : large circle
        |    @ : solid circle
        |    # : large solid circle
        |    s : square
        |    S : large square
        |    q : solid square
        |    Q : large solid square
        |    others : dot
        |  @param labels labels of points.
        |  @param label the class labels of data.
        |  @param palette the colors for each class.
      """.stripMargin)
    case "line" => println(
      """
        |  def line(data: Array[Array[Double]], style: Line.Style = Line.Style.SOLID, color: Color = Color.BLACK, legend: Char = ' '): (JFrame, PlotCanvas)
        |
        |  Scatter plot which connects points by straight lines.
        |
        |  @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
        |  @param style the stroke style of line.
        |  @param legend the legend used to draw data points. The default value ' ' makes the point indistinguishable
        |                from the line on purpose.
        |  @param color the color of line.
      """.stripMargin)
    case "staircase" => println(
      """
        |  def staircase(data: Array[Double]*): (JFrame, PlotCanvas)
        |
        |  Create a plot canvas with the staircase line plot of given data.
        |
        |  @param data a n x 2 or n x 3 matrix that describes coordinates of points.
      """.stripMargin)
    case "boxplot" => println(
      """
        |  def boxplot(data: Array[Double]*): (JFrame, PlotCanvas)
        |  def boxplot(data: Array[Array[Double]], labels: Array[String]): (JFrame, PlotCanvas)
        |
        |  A boxplot is a convenient way of graphically depicting groups of numerical
        |  data through their five-number summaries (the smallest observation
        |  (sample minimum), lower quartile (Q1), median (Q2), upper quartile (Q3),
        |  and largest observation (sample maximum). A boxplot may also indicate
        |  which observations, if any, might be considered outliers.
        |
        |  Boxplots can be useful to display differences between populations without
        |  making any assumptions of the underlying statistical distribution: they are
        |  non-parametric. The spacings between the different parts of the box help
        |  indicate the degree of dispersion (spread) and skewness in the data, and
        |  identify outliers.
        |
        |  @param data a data matrix of which each row will create a box plot.
        |  @param labels the labels for each box plot.
      """.stripMargin)
    case "heatmap" => println(
      """
        |  def heatmap(z: Array[Array[Double]]): (JFrame, PlotCanvas)
        |  def heatmap(z: Array[Array[Double]], palette: Array[Color]): (JFrame, PlotCanvas)
        |  def heatmap(x: Array[Double], y: Array[Double], z: Array[Array[Double]]): (JFrame, PlotCanvas)
        |  def heatmap(x: Array[Double], y: Array[Double], z: Array[Array[Double]], palette: Array[Color]): (JFrame, PlotCanvas)
        |  def heatmap(rowLabels: Array[String], columnLabels: Array[String], z: Array[Array[Double]]): (JFrame, PlotCanvas)
        |  def heatmap(rowLabels: Array[String], columnLabels: Array[String], z: Array[Array[Double]], palette: Array[Color]): (JFrame, PlotCanvas)
        |  def heatmap(matrix: SparseMatrix): (JFrame, PlotCanvas)
        |
        |  A heat map is a graphical representation of data where the values taken by
        |  a variable in a two-dimensional map are represented as colors.
        |
        |  @param x x coordinate of data matrix cells. Must be in ascending order.
        |  @param y y coordinate of data matrix cells. Must be in ascending order.
        |  @param z a data matrix to be shown in pseudo heat map.
        |  @param rowLabels the labels for rows of data matrix.
        |  @param columnLabels the labels for columns of data matrix.
        |  @param palette the color palette.
        |  @param matrix a sparse matrix.
      """.stripMargin)
    case "hexmap" => println(
      """
        |  def hexmap(z: Array[Array[Double]]): (JFrame, PlotCanvas)
        |  def hexmap(z: Array[Array[Double]], palette: Array[Color]): (JFrame, PlotCanvas)
        |  def hexmap(labels: Array[Array[String]], z: Array[Array[Double]]): (JFrame, PlotCanvas)
        |  def hexmap(labels: Array[Array[String]], z: Array[Array[Double]], palette: Array[Color]): (JFrame, PlotCanvas)
        |
        |  Hexmap is a variant of heat map by replacing rectangle cells with hexagon cells.
        |
        |  @param labels the descriptions of each cell in the data matrix.
        |  @param z a data matrix to be shown in pseudo heat map.
        |  @param palette the color palette.
      """.stripMargin)
    case "histogram" => println(
      """
        |  def histogram(data: Array[Double]): (JFrame, PlotCanvas)
        |  def histogram(data: Array[Double], k: Int): (JFrame, PlotCanvas)
        |  def histogram(data: Array[Double], breaks: Array[Double]): (JFrame, PlotCanvas)
        |  def histogram(data: Array[Array[Double]]): (JFrame, PlotCanvas)
        |  def histogram(data: Array[Array[Double]], k: Int): (JFrame, PlotCanvas)
        |  def histogram(data: Array[Array[Double]], xbins: Int, ybins: Int): (JFrame, PlotCanvas)
        |
        |  A histogram is a graphical display of tabulated frequencies, shown as bars.
        |  It shows what proportion of cases fall into each of several categories:
        |  it is a form of data binning. The categories are usually specified as
        |  non-overlapping intervals of some variable. The categories (bars) must
        |  be adjacent. The intervals (or bands, or bins) are generally of the same
        |  size, and are most easily interpreted if they are.
        |
        |  @param data a sample set.
        |  @param breaks an array of size k+1 giving the breakpoints between
        |                histogram cells. Must be in ascending order.
        |  @param k the number of bins.
        |  @param xbins the number of bins on x-axis.
        |  @param ybins the number of bins on y-axis.
      """.stripMargin)
    case "contour" => println(
      """
        |  def contour(z: Array[Array[Double]]): (JFrame, PlotCanvas)
        |  def contour(z: Array[Array[Double]], levels: Array[Double], palette: Array[Color]): (JFrame, PlotCanvas)
        |  def contour(x: Array[Double], y: Array[Double], z: Array[Array[Double]]): (JFrame, PlotCanvas)
        |  def contour(x: Array[Double], y: Array[Double], z: Array[Array[Double]], levels: Array[Double], palette: Array[Color]): (JFrame, PlotCanvas)
        |
        |  A contour plot is a graphical technique for representing a 3-dimensional
        |  surface by plotting constant z slices, called contours, on a 2-dimensional
        |  format. That is, given a value for z, lines are drawn for connecting the
        |  (x, y) coordinates where that z value occurs. The contour plot is an
        |  alternative to a 3-D surface plot.
        |
        |  @param z the data matrix to create contour plot.
        |  @param x the x coordinates of the data grid of z. Must be in ascending order.
        |  @param y the y coordinates of the data grid of z. Must be in ascending order.
        |  @param levels the level values of contours.
        |  @param palette the color for each contour level.
      """.stripMargin)
    case "surface" => println(
      """
        |  def surface(z: Array[Array[Double]], palette: Array[Color] = null): (JFrame, PlotCanvas)
        |  def surface(x: Array[Double], y: Array[Double], z: Array[Array[Double]], palette: Array[Color] = null): (JFrame, PlotCanvas)
        |
        |  Create a plot canvas with the 3D surface plot of given data.
        |
        |  @param x the x-axis values of surface.
        |  @param y the y-axis values of surface.
        |  @param z the z-axis values of surface.
        |  @param palette the color palette.
      """.stripMargin)
    case "wireframe" => println(
      """
        |  def wireframe(vertices: Array[Array[Double]], edges: Array[Array[Int]]): (JFrame, PlotCanvas)
        |
        |  Create a wire frame plot canvas.
        |
        |  @param vertices an m x n x 2 or m x n x 3 array which are coordinates of m x n grid.
        |  @param edges an m-by-2 array of which each row is the vertex indices of two
        |               end points of each edge.
      """.stripMargin)
    case "grid" => println(
      """
        |  def grid(data: Array[Array[Array[Double]]]): (JFrame, PlotCanvas)
        |
        |  2D grid plot.
        |
        |  @param data an m x n x 2 array which are coordinates of m x n grid.
      """.stripMargin)
    case "qqplot" => println(
      """
        |  def qqplot(x: Array[Double]): (JFrame, PlotCanvas)
        |  def qqplot(x: Array[Double], d: Distribution): (JFrame, PlotCanvas)
        |  def qqplot(x: Array[Double], y: Array[Double]): (JFrame, PlotCanvas)
        |  def qqplot(x: Array[Int], d: DiscreteDistribution): (JFrame, PlotCanvas)
        |  def qqplot(x: Array[Int], y: Array[Int]): (JFrame, PlotCanvas)
        |
        |  A Q-Q plot ("Q" stands for quantile) is a probability plot, a kind of
        |  graphical method for comparing two probability distributions, by
        |  plotting their quantiles against each other. In addition, Q-Q plots
        |  can be used as a graphical means of estimating parameters in a
        |  location-scale family of distributions.
        |
        |  @param x a sample set.
        |  @param y a sample set.
        |  @param d a distribution.
      """.stripMargin)
    case "dendrogram" => println(
      """
        |  def dendrogram(merge: Array[Array[Int]], height: Array[Double]): (JFrame, PlotCanvas)
        |
        |  A dendrogram is a tree diagram frequently used to illustrate the arrangement
        |  of the clusters produced by hierarchical clustering.
        |
        |  @param merge an n-1 by 2 matrix of which row i describes the merging of clusters at
        |               step i of the clustering. If an element j in the row is less than n, then
        |               observation j was merged at this stage. If j &ge; n then the merge
        |               was with the cluster formed at the (earlier) stage j-n of the algorithm.
        |  @param height a set of n-1 non-decreasing real values, which are the clustering height,
        |                i.e., the value of the criterion associated with the clustering method
        |                for the particular agglomeration.
      """.stripMargin)
    case "knn" => println(
      """
        |  def knn[T <: AnyRef](x: KNNSearch[T, T], y: Array[Int], k: Int): KNN[T]
        |  def knn[T <: AnyRef](x: Array[T], y: Array[Int], distance: Distance[T], k: Int): KNN[T]
        |  def knn(x: Array[Array[Double]], y: Array[Int], k: Int): KNN[Array[Double]]
        |
        |  K-nearest neighbor classifier. The k-nearest neighbor algorithm (k-NN) is
        |  a method for classifying objects by a majority vote of its neighbors,
        |  with the object being assigned to the class most common amongst its k
        |  nearest neighbors (k is a positive integer, typically small).
        |  k-NN is a type of instance-based learning, or lazy learning where the
        |  function is only approximated locally and all computation
        |  is deferred until classification.
        |
        |  The best choice of k depends upon the data; generally, larger values of
        |  k reduce the effect of noise on the classification, but make boundaries
        |  between classes less distinct. A good k can be selected by various
        |  heuristic techniques, e.g. cross-validation. In binary problems, it is
        |  helpful to choose k to be an odd number as this avoids tied votes.
        |
        |  A drawback to the basic majority voting classification is that the classes
        |  with the more frequent instances tend to dominate the prediction of the
        |  new object, as they tend to come up in the k nearest neighbors when
        |  the neighbors are computed due to their large number. One way to overcome
        |  this problem is to weight the classification taking into account the
        |  distance from the test point to each of its k nearest neighbors.
        |
        |  Often, the classification accuracy of k-NN can be improved significantly
        |  if the distance metric is learned with specialized algorithms such as
        |  Large Margin Nearest Neighbor or Neighborhood Components Analysis.
        |
        |  Nearest neighbor rules in effect compute the decision boundary in an
        |  implicit manner. It is also possible to compute the decision boundary
        |  itself explicitly, and to do so in an efficient manner so that the
        |  computational complexity is a function of the boundary complexity.
        |
        |  The nearest neighbor algorithm has some strong consistency results. As
        |  the amount of data approaches infinity, the algorithm is guaranteed to
        |  yield an error rate no worse than twice the Bayes error rate (the minimum
        |  achievable error rate given the distribution of the data). k-NN is
        |  guaranteed to approach the Bayes error rate, for some value of k (where k
        |  increases as a function of the number of data points).
        |
        |  @param x k-nearest neighbor search data structure of training instances.
        |  @param y training labels in [0, c), where c is the number of classes.
        |  @param k the number of neighbors for classification.
      """.stripMargin)
    case "logit" => println(
      """
        |  def logit(x: Array[Array[Double]], y: Array[Int], lambda: Double = 0.0, tol: Double = 1E-5, maxIter: Int = 500): LogisticRegression
        |
        |  Logistic regression. Logistic regression (logit model) is a generalized
        |  linear model used for binomial regression. Logistic regression applies
        |  maximum likelihood estimation after transforming the dependent into
        |  a logit variable. A logit is the natural log of the odds of the dependent
        |  equaling a certain value or not (usually 1 in binary logistic models,
        |  the highest value in multinomial models). In this way, logistic regression
        |  estimates the odds of a certain event (value) occurring.
        |
        |  Goodness-of-fit tests such as the likelihood ratio test are available
        |  as indicators of model appropriateness, as is the Wald statistic to test
        |  the significance of individual independent variables.
        |
        |  Logistic regression has many analogies to ordinary least squares (OLS)
        |  regression. Unlike OLS regression, however, logistic regression does not
        |  assume linearity of relationship between the raw values of the independent
        |  variables and the dependent, does not require normally distributed variables,
        |  does not assume homoscedasticity, and in general has less stringent
        |  requirements.
        |
        |  Compared with linear discriminant analysis, logistic regression has several
        |  advantages:
        |
        |   * It is more robust: the independent variables don't have to be normally
        |     distributed, or have equal variance in each group
        |   * It does not assume a linear relationship between the independent
        |     variables and dependent variable.
        |   * It may handle nonlinear effects since one can add explicit interaction
        |     and power terms.
        |
        |  However, it requires much more data to achieve stable, meaningful results.
        |
        |  Logistic regression also has strong connections with neural network and
        |  maximum entropy modeling. For example, binary logistic regression is
        |  equivalent to a one-layer, single-output neural network with a logistic
        |  activation function trained under log loss. Similarly, multinomial logistic
        |  regression is equivalent to a one-layer, softmax-output neural network.
        |
        |  Logistic regression estimation also obeys the maximum entropy principle, and
        |  thus logistic regression is sometimes called "maximum entropy modeling",
        |  and the resulting classifier the "maximum entropy classifier".
        |
        |  @param x training samples.
        |  @param y training labels in [0, k), where k is the number of classes.
        |  @param lambda lambda > 0 gives a "regularized" estimate of linear
        |                weights which often has superior generalization performance, especially
        |                when the dimensionality is high.
        |  @param tol the tolerance for stopping iterations.
        |  @param maxIter the maximum number of iterations.
        |
        |  @return Logistic regression model.
      """.stripMargin)
    case "maxent" => println(
      """
        |  def maxent(x: Array[Array[Int]], y: Array[Int], p: Int, lambda: Double = 0.1, tol: Double = 1E-5, maxIter: Int = 500): Maxent
        |
        |  Maximum entropy classifier. Maximum entropy is a technique for learning
        |  probability distributions from data. In maximum entropy models, the
        |  observed data itself is assumed to be the testable information. Maximum
        |  entropy models don't assume anything about the probability distribution
        |  other than what have been observed and always choose the most uniform
        |  distribution subject to the observed constraints.
        |
        |  Basically, maximum entropy classifier is another name of multinomial logistic
        |  regression applied to categorical independent variables, which are
        |  converted to binary dummy variables. Maximum entropy models are widely
        |  used in natural language processing.  Here, we provide an implementation
        |  which assumes that binary features are stored in a sparse array, of which
        |  entries are the indices of nonzero features.
        |
        |  A. L. Berger, S. D. Pietra, and V. J. D. Pietra. A maximum entropy approach to natural language processing. Computational Linguistics 22(1):39-71, 1996.</li>
        |
        |  @param x training samples. Each sample is represented by a set of sparse
        |           binary features. The features are stored in an integer array, of which
        |           are the indices of nonzero features.
        |  @param y training labels in [0, k), where k is the number of classes.
        |  @param p the dimension of feature space.
        |  @param lambda lambda > 0 gives a "regularized" estimate of linear
        |                weights which often has superior generalization performance, especially
        |                when the dimensionality is high.
        |  @param tol tolerance for stopping iterations.
        |  @param maxIter maximum number of iterations.
        |
        |  @return Maximum entropy model.
      """.stripMargin)
    case "svm" => println(
      """
        |  def svm[T <: AnyRef](x: Array[T], y: Array[Int], kernel: MercerKernel[T], C: Double, strategy: SVM.Multiclass = SVM.Multiclass.ONE_VS_ONE, epoch: Int = 1): SVM[T]
        |
        |  Support vector machines for classification. The basic support vector machine
        |  is a binary linear classifier which chooses the hyperplane that represents
        |  the largest separation, or margin, between the two classes. If such a
        |  hyperplane exists, it is known as the maximum-margin hyperplane and the
        |  linear classifier it defines is known as a maximum margin classifier.
        |
        |  If there exists no hyperplane that can perfectly split the positive and
        |  negative instances, the soft margin method will choose a hyperplane
        |  that splits the instances as cleanly as possible, while still maximizing
        |  the distance to the nearest cleanly split instances.
        |
        |  The nonlinear SVMs are created by applying the kernel trick to
        |  maximum-margin hyperplanes. The resulting algorithm is formally similar,
        |  except that every dot product is replaced by a nonlinear kernel function.
        |  This allows the algorithm to fit the maximum-margin hyperplane in a
        |  transformed feature space. The transformation may be nonlinear and
        |  the transformed space be high dimensional. For example, the feature space
        |  corresponding Gaussian kernel is a Hilbert space of infinite dimension.
        |  Thus though the classifier is a hyperplane in the high-dimensional feature
        |  space, it may be nonlinear in the original input space. Maximum margin
        |  classifiers are well regularized, so the infinite dimension does not spoil
        |  the results.
        |
        |  The effectiveness of SVM depends on the selection of kernel, the kernel's
        |  parameters, and soft margin parameter C. Given a kernel, best combination
        |  of C and kernel's parameters is often selected by a grid-search with
        |  cross validation.
        |
        |  The dominant approach for creating multi-class SVMs is to reduce the
        |  single multi-class problem into multiple binary classification problems.
        |  Common methods for such reduction is to build binary classifiers which
        |  distinguish between (i) one of the labels to the rest (one-versus-all)
        |  or (ii) between every pair of classes (one-versus-one). Classification
        |  of new instances for one-versus-all case is done by a winner-takes-all
        |  strategy, in which the classifier with the highest output function assigns
        |  the class. For the one-versus-one approach, classification
        |  is done by a max-wins voting strategy, in which every classifier assigns
        |  the instance to one of the two classes, then the vote for the assigned
        |  class is increased by one vote, and finally the class with most votes
        |  determines the instance classification.
        |
        |  @param x training data
        |  @param y training labels
        |  @param kernel Mercer kernel
        |  @param C Regularization parameter
        |  @param strategy multi-class classification strategy, one vs all or one vs one.
        |  @param epoch the number of training epochs
        |
        |  @return SVM model.
      """.stripMargin)
    case "cart" => println(
      """
        |  def cart(x: Array[Array[Double]], y: Array[Int], J: Int, attributes: Array[Attribute] = null, splitRule: DecisionTree.SplitRule = DecisionTree.SplitRule.GINI): DecisionTree
        |  def cart(x: Array[Array[Double]], y: Array[Double], J: Int, attributes: Array[Attribute] = null): RegressionTree
        |
        |  Decision tree for classification. A decision tree can be learned by
        |  splitting the training set into subsets based on an attribute value
        |  test. This process is repeated on each derived subset in a recursive
        |  manner called recursive partitioning. The recursion is completed when
        |  the subset at a node all has the same value of the target variable,
        |  or when splitting no longer adds value to the predictions.
        |
        |  The algorithms that are used for constructing decision trees usually
        |  work top-down by choosing a variable at each step that is the next best
        |  variable to use in splitting the set of items. "Best" is defined by how
        |  well the variable splits the set into homogeneous subsets that have
        |  the same value of the target variable. Different algorithms use different
        |  formulae for measuring "best". Used by the CART algorithm, Gini impurity
        |  is a measure of how often a randomly chosen element from the set would
        |  be incorrectly labeled if it were randomly labeled according to the
        |  distribution of labels in the subset. Gini impurity can be computed by
        |  summing the probability of each item being chosen times the probability
        |  of a mistake in categorizing that item. It reaches its minimum (zero) when
        |  all cases in the node fall into a single target category. Information gain
        |  is another popular measure, used by the ID3, C4.5 and C5.0 algorithms.
        |  Information gain is based on the concept of entropy used in information
        |  theory. For categorical variables with different number of levels, however,
        |  information gain are biased in favor of those attributes with more levels.
        |  Instead, one may employ the information gain ratio, which solves the drawback
        |  of information gain.
        |
        |  Classification and Regression Tree techniques have a number of advantages
        |  over many of those alternative techniques.
        |
        |   * Simple to understand and interpret.
        |     In most cases, the interpretation of results summarized in a tree is
        |     very simple. This simplicity is useful not only for purposes of rapid
        |     classification of new observations, but can also often yield a much simpler
        |     "model" for explaining why observations are classified or predicted in a
        |     particular manner.
        |
        |   * Able to handle both numerical and categorical data.
        |     Other techniques are usually specialized in analyzing datasets that
        |     have only one type of variable.
        |
        |   * Tree methods are nonparametric and nonlinear.
        |     The final results of using tree methods for classification or regression
        |     can be summarized in a series of (usually few) logical if-then conditions
        |     (tree nodes). Therefore, there is no implicit assumption that the underlying
        |     relationships between the predictor variables and the dependent variable
        |     are linear, follow some specific non-linear link function, or that they
        |     are even monotonic in nature. Thus, tree methods are particularly well
        |     suited for data mining tasks, where there is often little a priori
        |     knowledge nor any coherent set of theories or predictions regarding which
        |     variables are related and how. In those types of data analytics, tree
        |     methods can often reveal simple relationships between just a few variables
        |     that could have easily gone unnoticed using other analytic techniques.
        |
        |  One major problem with classification and regression trees is their high
        |  variance. Often a small change in the data can result in a very different
        |  series of splits, making interpretation somewhat precarious. Besides,
        |  decision-tree learners can create over-complex trees that cause over-fitting.
        |  Mechanisms such as pruning are necessary to avoid this problem.
        |  Another limitation of trees is the lack of smoothness of the prediction
        |  surface.
        |
        |  Some techniques such as bagging, boosting, and random forest use more than
        |  one decision tree for their analysis.
        |
        |  @param x the training instances.
        |  @param y the response variable.
        |  @param J the maximum number of leaf nodes in the tree.
        |  @param attributes the attribute properties.
        |  @param splitRule the splitting rule.
        |
        |  @return Decision tree model.
      """.stripMargin)
    case "randomForest" => println(
      """
        |  def randomForest(x: Array[Array[Double]], y: Array[Int], attributes: Array[Attribute] = null, T: Int = 500, mtry: Int = -1, J: Int = -1, splitRule: DecisionTree.SplitRule = DecisionTree.SplitRule.GINI, classWeight: Array[Int] = null): RandomForest = {
        |  def randomForest(x: Array[Array[Double]], y: Array[Double], attributes: Array[Attribute] = null, T: Int = 500, mtry: Int = -1, S: Int = 5): RandomForest
        |
        |  Random forest for classification. Random forest is an ensemble classifier
        |  that consists of many decision trees and outputs the majority vote of
        |  individual trees. The method combines bagging idea and the random
        |  selection of features.
        |
        |  Each tree is constructed using the following algorithm:
        |
        |   * If the number of cases in the training set is N, randomly sample N cases
        |     with replacement from the original data. This sample will
        |     be the training set for growing the tree.
        |   * If there are M input variables, a number m &lt;&lt; M is specified such
        |     that at each node, m variables are selected at random out of the M and
        |     the best split on these m is used to split the node. The value of m is
        |     held constant during the forest growing.
        |   * Each tree is grown to the largest extent possible. There is no pruning.
        |
        |  The advantages of random forest are:
        |
        |   * For many data sets, it produces a highly accurate classifier.
        |   * It runs efficiently on large data sets.
        |   * It can handle thousands of input variables without variable deletion.
        |   * It gives estimates of what variables are important in the classification.
        |   * It generates an internal unbiased estimate of the generalization error
        |     as the forest building progresses.
        |   * It has an effective method for estimating missing data and maintains
        |     accuracy when a large proportion of the data are missing.
        |
        |  The disadvantages are
        |
        |   * Random forests are prone to over-fitting for some datasets. This is
        |     even more pronounced on noisy data.
        |   * For data including categorical variables with different number of
        |     levels, random forests are biased in favor of those attributes with more
        |     levels. Therefore, the variable importance scores from random forest are
        |     not reliable for this type of data.
        |
        |
        |  @param x the training instances.
        |  @param y the response variable.
        |  @param attributes the attribute properties. If not provided, all attributes
        |                    are treated as numeric values.
        |  @param T the number of trees.
        |  @param mtry the number of random selected features to be used to determine
        |              the decision at a node of the tree. floor(sqrt(dim)) seems to give
        |              generally good performance, where dim is the number of variables.
        |  @param J maximum number of leaf nodes.
        |  @param splitRule Decision tree node split rule.
        |  @param classWeight Priors of the classes.
        |
        |  @return Random forest classification model.
      """.stripMargin)
    case "gbm" => println(
      """
        |  def gbm(x: Array[Array[Double]], y: Array[Int], attributes: Array[Attribute] = null, T: Int = 500, J: Int = 6, eta: Double = 0.05, f: Double = 0.7): GradientTreeBoost
        |  def gbm(x: Array[Array[Double]], y: Array[Double], attributes: Array[Attribute] = null, loss: GradientTreeBoost.Loss = GradientTreeBoost.Loss.LeastAbsoluteDeviation, T: Int = 500, J: Int = 6, eta: Double = 0.05, f: Double = 0.7): GradientTreeBoost
        |
        |  Gradient boosting for classification. Gradient boosting is typically used
        |  with decision trees (especially CART regression trees) of a fixed size as
        |  base learners. For this special case Friedman proposes a modification to
        |  gradient boosting method which improves the quality of fit of each base
        |  learner.
        |
        |  Generic gradient boosting at the t-th step would fit a regression tree to
        |  pseudo-residuals. Let J be the number of its leaves. The tree partitions
        |  the input space into J disjoint regions and predicts a constant value in
        |  each region. The parameter J controls the maximum allowed
        |  level of interaction between variables in the model. With J = 2 (decision
        |  stumps), no interaction between variables is allowed. With J = 3 the model
        |  may include effects of the interaction between up to two variables, and
        |  so on. Hastie et al. comment that typically 4 &le; J &le; 8 work well
        |  for boosting and results are fairly insensitive to the choice of in
        |  this range, J = 2 is insufficient for many applications, and J &gt; 10 is
        |  unlikely to be required.
        |
        |  Fitting the training set too closely can lead to degradation of the model's
        |  generalization ability. Several so-called regularization techniques reduce
        |  this over-fitting effect by constraining the fitting procedure.
        |  One natural regularization parameter is the number of gradient boosting
        |  iterations T (i.e. the number of trees in the model when the base learner
        |  is a decision tree). Increasing T reduces the error on training set,
        |  but setting it too high may lead to over-fitting. An optimal value of T
        |  is often selected by monitoring prediction error on a separate validation
        |  data set.
        |
        |  Another regularization approach is the shrinkage which times a parameter
        |  eta (called the "learning rate") to update term.
        |  Empirically it has been found that using small learning rates (such as
        |  eta < 0.1) yields dramatic improvements in model's generalization ability
        |  over gradient boosting without shrinking (eta = 1). However, it comes at
        |  the price of increasing computational time both during training and
        |  prediction: lower learning rate requires more iterations.
        |
        |  Soon after the introduction of gradient boosting Friedman proposed a
        |  minor modification to the algorithm, motivated by Breiman's bagging method.
        |  Specifically, he proposed that at each iteration of the algorithm, a base
        |  learner should be fit on a subsample of the training set drawn at random
        |  without replacement. Friedman observed a substantial improvement in
        |  gradient boosting's accuracy with this modification.
        |
        |  Subsample size is some constant fraction f of the size of the training set.
        |  When f = 1, the algorithm is deterministic and identical to the one
        |  described above. Smaller values of f introduce randomness into the
        |  algorithm and help prevent over-fitting, acting as a kind of regularization.
        |  The algorithm also becomes faster, because regression trees have to be fit
        |  to smaller datasets at each iteration. Typically, f is set to 0.5, meaning
        |  that one half of the training set is used to build each base learner.
        |
        |  Also, like in bagging, sub-sampling allows one to define an out-of-bag
        |  estimate of the prediction performance improvement by evaluating predictions
        |  on those observations which were not used in the building of the next
        |  base learner. Out-of-bag estimates help avoid the need for an independent
        |  validation dataset, but often underestimate actual performance improvement
        |  and the optimal number of iterations.
        |
        |  Gradient tree boosting implementations often also use regularization by
        |  limiting the minimum number of observations in trees' terminal nodes.
        |  It's used in the tree building process by ignoring any splits that lead
        |  to nodes containing fewer than this number of training set instances.
        |  Imposing this limit helps to reduce variance in predictions at leaves.
        |
        |  J. H. Friedman. Greedy Function Approximation: A Gradient Boosting Machine, 1999.</li>
        |  J. H. Friedman. Stochastic Gradient Boosting, 1999.</li>
        |
        |  @param x the training instances.
        |  @param y the class labels.
        |  @param attributes the attribute properties. If not provided, all attributes
        |                    are treated as numeric values.
        |  @param T the number of iterations (trees).
        |  @param J the number of leaves in each tree.
        |  @param eta the shrinkage parameter in (0, 1] controls the learning rate of procedure.
        |  @param f the sampling fraction for stochastic tree boosting.
        |
        |  @return Gradient boosted trees.
      """.stripMargin)
    case "adaboost" => println(
      """
        |  def adaboost(x: Array[Array[Double]], y: Array[Int], attributes: Array[Attribute] = null, T: Int = 500, J: Int = 2): AdaBoost
        |
        |  AdaBoost (Adaptive Boosting) classifier with decision trees. In principle,
        |  AdaBoost is a meta-algorithm, and can be used in conjunction with many other
        |  learning algorithms to improve their performance. In practice, AdaBoost with
        |  decision trees is probably the most popular combination. AdaBoost is adaptive
        |  in the sense that subsequent classifiers built are tweaked in favor of those
        |  instances misclassified by previous classifiers. AdaBoost is sensitive to
        |  noisy data and outliers. However in some problems it can be less susceptible
        |  to the over-fitting problem than most learning algorithms.
        |
        |  AdaBoost calls a weak classifier repeatedly in a series of rounds from
        |  total T classifiers. For each call a distribution of weights is updated
        |  that indicates the importance of examples in the data set for the
        |  classification. On each round, the weights of each incorrectly classified
        |  example are increased (or alternatively, the weights of each correctly
        |  classified example are decreased), so that the new classifier focuses more
        |  on those examples.
        |
        |  The basic AdaBoost algorithm is only for binary classification problem.
        |  For multi-class classification, a common approach is reducing the
        |  multi-class classification problem to multiple two-class problems.
        |  This implementation is a multi-class AdaBoost without such reductions.
        |
        |  Yoav Freund, Robert E. Schapire. A Decision-Theoretic Generalization of on-Line Learning and an Application to Boosting, 1995.</li>
        |  Ji Zhu, Hui Zhou, Saharon Rosset and Trevor Hastie. Multi-class Adaboost, 2009.</li>
        |
        |  @param x the training instances.
        |  @param y the response variable.
        |  @param attributes the attribute properties. If not provided, all attributes
        |                    are treated as numeric values.
        |  @param T the number of trees.
        |  @param J the maximum number of leaf nodes in the trees.
        |
        |  @return AdaBoost model.
      """.stripMargin)
    case "fisher" => println(
      """
        |  def fisher(x: Array[Array[Double]], y: Array[Int], L: Int = -1, tol: Double = 1E-4): FLD
        |
        |  Fisher's linear discriminant. Fisher defined the separation between two
        |  distributions to be the ratio of the variance between the classes to
        |  the variance within the classes, which is, in some sense, a measure
        |  of the signal-to-noise ratio for the class labeling. FLD finds a linear
        |  combination of features which maximizes the separation after the projection.
        |  The resulting combination may be used for dimensionality reduction
        |  before later classification.
        |
        |  The terms Fisher's linear discriminant and LDA are often used
        |  interchangeably, although FLD actually describes a slightly different
        |  discriminant, which does not make some of the assumptions of LDA such
        |  as normally distributed classes or equal class covariances.
        |  When the assumptions of LDA are satisfied, FLD is equivalent to LDA.
        |
        |  FLD is also closely related to principal component analysis (PCA), which also
        |  looks for linear combinations of variables which best explain the data.
        |  As a supervised method, FLD explicitly attempts to model the
        |  difference between the classes of data. On the other hand, PCA is a
        |  unsupervised method and does not take into account any difference in class.
        |
        |  One complication in applying FLD (and LDA) to real data
        |  occurs when the number of variables/features does not exceed
        |  the number of samples. In this case, the covariance estimates do not have
        |  full rank, and so cannot be inverted. This is known as small sample size
        |  problem.
        |
        |  @param x training instances.
        |  @param y training labels in [0, k), where k is the number of classes.
        |  @param L the dimensionality of mapped space. The default value is the number of classes - 1.
        |  @param tol a tolerance to decide if a covariance matrix is singular; it
        |             will reject variables whose variance is less than tol<sup>2</sup>.
        |
        |  @return fisher discriminant analysis model.
      """.stripMargin)
    case "lda" => println(
      """
        |  def lda(x: Array[Array[Double]], y: Array[Int], priori: Array[Double] = null, tol: Double = 1E-4): LDA
        |
        |  Linear discriminant analysis. LDA is based on the Bayes decision theory
        |  and assumes that the conditional probability density functions are normally
        |  distributed. LDA also makes the simplifying homoscedastic assumption (i.e.
        |  that the class covariances are identical) and that the covariances have full
        |  rank. With these assumptions, the discriminant function of an input being
        |  in a class is purely a function of this linear combination of independent
        |  variables.
        |
        |  LDA is closely related to ANOVA (analysis of variance) and linear regression
        |  analysis, which also attempt to express one dependent variable as a
        |  linear combination of other features or measurements. In the other two
        |  methods, however, the dependent variable is a numerical quantity, while
        |  for LDA it is a categorical variable (i.e. the class label). Logistic
        |  regression and probit regression are more similar to LDA, as they also
        |  explain a categorical variable. These other methods are preferable in
        |  applications where it is not reasonable to assume that the independent
        |  variables are normally distributed, which is a fundamental assumption
        |  of the LDA method.
        |
        |  One complication in applying LDA (and Fisher's discriminant) to real data
        |  occurs when the number of variables/features does not exceed
        |  the number of samples. In this case, the covariance estimates do not have
        |  full rank, and so cannot be inverted. This is known as small sample size
        |  problem.
        |
        |  @param x training samples.
        |  @param y training labels in [0, k), where k is the number of classes.
        |  @param priori the priori probability of each class. If null, it will be
        |                estimated from the training data.
        |  @param tol a tolerance to decide if a covariance matrix is singular; it
        |             will reject variables whose variance is less than tol<sup>2</sup>.
        |
        |  @return linear discriminant analysis model.
      """.stripMargin)
    case "qda" => println(
      """
        |  def qda(x: Array[Array[Double]], y: Array[Int], priori: Array[Double] = null, tol: Double = 1E-4): QDA
        |
        |  Quadratic discriminant analysis. QDA is closely related to linear discriminant
        |  analysis (LDA). Like LDA, QDA models the conditional probability density
        |  functions as a Gaussian distribution, then uses the posterior distributions
        |  to estimate the class for a given test data. Unlike LDA, however,
        |  in QDA there is no assumption that the covariance of each of the classes
        |  is identical. Therefore, the resulting separating surface between
        |  the classes is quadratic.
        |
        |  The Gaussian parameters for each class can be estimated from training data
        |  with maximum likelihood (ML) estimation. However, when the number of
        |  training instances is small compared to the dimension of input space,
        |  the ML covariance estimation can be ill-posed. One approach to resolve
        |  the ill-posed estimation is to regularize the covariance estimation.
        |  One of these regularization methods is {@link rda regularized discriminant analysis}.
        |
        |  @param x training samples.
        |  @param y training labels in [0, k), where k is the number of classes.
        |  @param priori the priori probability of each class. If null, it will be
        |                estimated from the training data.
        |  @param tol a tolerance to decide if a covariance matrix is singular; it
        |             will reject variables whose variance is less than tol^2.
        |
        |  @return Quadratic discriminant analysis model.
      """.stripMargin)
    case "rda" => println(
      """
        |  def rda(x: Array[Array[Double]], y: Array[Int], alpha: Double, priori: Array[Double] = null, tol: Double = 1E-4): RDA
        |
        |  Regularized discriminant analysis. RDA is a compromise between LDA and QDA,
        |  which allows one to shrink the separate covariances of QDA toward a common
        |  variance as in LDA. This method is very similar in flavor to ridge regression.
        |  The regularized covariance matrices of each class is
        |
        |    Sigma_k(alpha) = alpha * Sigma_k + (1 - alpha) * Sigma
        |
        |  The quadratic discriminant function is defined using the shrunken covariance
        |  matrices Sigma_k(alpha). The parameter alpha in [0, 1]
        |  controls the complexity of the model. When &alpha; is one, RDA becomes QDA.
        |  While alpha is zero, RDA is equivalent to LDA. Therefore, the
        |  regularization factor alpha allows a continuum of models between LDA and QDA.
        |
        |  @param x training samples.
        |  @param y training labels in [0, k), where k is the number of classes.
        |  @param alpha regularization factor in [0, 1] allows a continuum of models
        |               between LDA and QDA.
        |  @param priori the priori probability of each class.
        |  @param tol tolerance to decide if a covariance matrix is singular; it
        |             will reject variables whose variance is less than tol^2.
        |
        |  @return Regularized discriminant analysis model.
      """.stripMargin)
    case "ols" => println(
      """
        |  def ols(x: Array[Array[Double]], y: Array[Double]): OLS
        |
        |  Ordinary least squares. In linear regression,
        |  the model specification is that the dependent variable is a linear
        |  combination of the parameters (but need not be linear in the independent
        |  variables). The residual is the difference between the value of the
        |  dependent variable predicted by the model, and the true value of the
        |  dependent variable. Ordinary least squares obtains parameter estimates
        |  that minimize the sum of squared residuals, SSE (also denoted RSS).
        |
        |  The OLS estimator is consistent when the independent variables are
        |  exogenous and there is no multicollinearity, and optimal in the class
        |  of linear unbiased estimators when the errors are homoscedastic and
        |  serially uncorrelated. Under these conditions, the method of OLS provides
        |  minimum-variance mean-unbiased estimation when the errors have finite
        |  variances.
        |
        |  There are several different frameworks in which the linear regression
        |  model can be cast in order to make the OLS technique applicable. Each
        |  of these settings produces the same formulas and same results, the only
        |  difference is the interpretation and the assumptions which have to be
        |  imposed in order for the method to give meaningful results. The choice
        |  of the applicable framework depends mostly on the nature of data at hand,
        |  and on the inference task which has to be performed.
        |
        |  Least squares corresponds to the maximum likelihood criterion if the
        |  experimental errors have a normal distribution and can also be derived
        |  as a method of moments estimator.
        |
        |  Once a regression model has been constructed, it may be important to
        |  confirm the goodness of fit of the model and the statistical significance
        |  of the estimated parameters. Commonly used checks of goodness of fit
        |  include the R-squared, analysis of the pattern of residuals and hypothesis
        |  testing. Statistical significance can be checked by an F-test of the overall
        |  fit, followed by t-tests of individual parameters.
        |
        |  Interpretations of these diagnostic tests rest heavily on the model
        |  assumptions. Although examination of the residuals can be used to
        |  invalidate a model, the results of a t-test or F-test are sometimes more
        |  difficult to interpret if the model's assumptions are violated.
        |  For example, if the error term does not have a normal distribution,
        |  in small samples the estimated parameters will not follow normal
        |  distributions and complicate inference. With relatively large samples,
        |  however, a central limit theorem can be invoked such that hypothesis
        |  testing may proceed using asymptotic approximations.
        |
        |  @param x a matrix containing the explanatory variables.
        |  @param y the response values.
      """.stripMargin)
    case "ridge" => println(
      """
        |  def ridge(x: Array[Array[Double]], y: Array[Double], lambda: Double): RidgeRegression
        |
        |  Ridge Regression. When the predictor variables are highly correlated amongst
        |  themselves, the coefficients of the resulting least squares fit may be very
        |  imprecise. By allowing a small amount of bias in the estimates, more
        |  reasonable coefficients may often be obtained. Ridge regression is one
        |  method to address these issues. Often, small amounts of bias lead to
        |  dramatic reductions in the variance of the estimated model coefficients.
        |  Ridge regression is such a technique which shrinks the regression
        |  coefficients by imposing a penalty on their size. Ridge regression was
        |  originally developed to overcome the singularity of the X'X matrix.
        |  This matrix is perturbed so as to make its determinant appreciably
        |  different from 0.
        |
        |  Ridge regression is a kind of Tikhonov regularization, which is the most
        |  commonly used method of regularization of ill-posed problems. Another
        |  interpretation of ridge regression is available through Bayesian estimation.
        |  In this setting the belief that weight should be small is coded into a prior
        |  distribution.
        |
        |  @param x a matrix containing the explanatory variables.
        |  @param y the response values.
        |  @param lambda the shrinkage/regularization parameter.
      """.stripMargin)
    case "lasso" => println(
      """
        |  def lasso(x: Array[Array[Double]], y: Array[Double], lambda: Double, tol: Double = 1E-3, maxIter: Int = 5000): LASSO
        |
        |  Least absolute shrinkage and selection operator.
        |  The Lasso is a shrinkage and selection method for linear regression.
        |  It minimizes the usual sum of squared errors, with a bound on the sum
        |  of the absolute values of the coefficients (i.e. L<sub>1</sub>-regularized).
        |  It has connections to soft-thresholding of wavelet coefficients, forward
        |  stage-wise regression, and boosting methods.
        |
        |  The Lasso typically yields a sparse solution, of which the parameter
        |  vector &beta; has relatively few nonzero coefficients. In contrast, the
        |  solution of L2-regularized least squares (i.e. ridge regression)
        |  typically has all coefficients nonzero. Because it effectively
        |  reduces the number of variables, the Lasso is useful in some contexts.
        |
        |  For over-determined systems (more instances than variables, commonly in
        |  machine learning), we normalize variables with mean 0 and standard deviation
        |  1. For under-determined systems (less instances than variables, e.g.
        |  compressed sensing), we assume white noise (i.e. no intercept in the linear
        |  model) and do not perform normalization. Note that the solution
        |  is not unique in this case.
        |
        |  There is no analytic formula or expression for the optimal solution to the
        |  L1-regularized least squares problems. Therefore, its solution
        |  must be computed numerically. The objective function in the
        |  L1-regularized least squares is convex but not differentiable,
        |  so solving it is more of a computational challenge than solving the
        |  L2-regularized least squares. The Lasso may be solved using
        |  quadratic programming or more general convex optimization methods, as well
        |  as by specific algorithms such as the least angle regression algorithm.
        |
        |  @param x a matrix containing the explanatory variables.
        |  @param y the response values.
        |  @param lambda the shrinkage/regularization parameter.
        |  @param tol the tolerance for stopping iterations (relative target duality gap).
        |  @param maxIter the maximum number of iterations.
      """.stripMargin)
    case "mlp" => println(
      """
        |  def mlp(x: Array[Array[Double]], y: Array[Int], numUnits: Array[Int], error: NeuralNetwork.ErrorFunction, epochs: Int = 25, activation: NeuralNetwork.ActivationFunction, eta: Double = 0.1, alpha: Double = 0.0, lambda: Double = 0.0): NeuralNetwork
        |
        |  Multilayer perceptron neural network.
        |  An MLP consists of several layers of nodes, interconnected through weighted
        |  acyclic arcs from each preceding layer to the following, without lateral or
        |  feedback connections. Each node calculates a transformed weighted linear
        |  combination of its inputs (output activations from the preceding layer), with
        |  one of the weights acting as a trainable bias connected to a constant input.
        |  The transformation, called activation function, is a bounded non-decreasing
        |  (non-linear) function, such as the sigmoid functions (ranges from 0 to 1).
        |  Another popular activation function is hyperbolic tangent which is actually
        |  equivalent to the sigmoid function in shape but ranges from -1 to 1.
        |  More specialized activation functions include radial basis functions which
        |  are used in RBF networks.
        |
        |  The representational capabilities of a MLP are determined by the range of
        |  mappings it may implement through weight variation. Single layer perceptrons
        |  are capable of solving only linearly separable problems. With the sigmoid
        |  function as activation function, the single-layer network is identical
        |  to the logistic regression model.
        |
        |  The universal approximation theorem for neural networks states that every
        |  continuous function that maps intervals of real numbers to some output
        |  interval of real numbers can be approximated arbitrarily closely by a
        |  multi-layer perceptron with just one hidden layer. This result holds only
        |  for restricted classes of activation functions, which are extremely complex
        |  and NOT smooth for subtle mathematical reasons. On the other hand, smoothness
        |  is important for gradient descent learning. Besides, the proof is not
        |  constructive regarding the number of neurons required or the settings of
        |  the weights. Therefore, complex systems will have more layers of neurons
        |  with some having increased layers of input neurons and output neurons
        |  in practice.
        |
        |  The most popular algorithm to train MLPs is back-propagation, which is a
        |  gradient descent method. Based on chain rule, the algorithm propagates the
        |  error back through the network and adjusts the weights of each connection in
        |  order to reduce the value of the error function by some small amount.
        |  For this reason, back-propagation can only be applied on networks with
        |  differentiable activation functions.
        |
        |  During error back propagation, we usually times the gradient with a small
        |  number &eta;, called learning rate, which is carefully selected to ensure
        |  that the network converges to a local minimum of the error function
        |  fast enough, without producing oscillations. One way to avoid oscillation
        |  at large &eta;, is to make the change in weight dependent on the past weight
        |  change by adding a momentum term.
        |
        |  Although the back-propagation algorithm may performs gradient
        |  descent on the total error of all instances in a batch way,
        |  the learning rule is often applied to each instance separately in an online
        |  way or stochastic way. There exists empirical indication that the stochastic
        |  way results in faster convergence.
        |
        |  In practice, the problem of over-fitting has emerged. This arises in
        |  convoluted or over-specified systems when the capacity of the network
        |  significantly exceeds the needed free parameters. There are two general
        |  approaches for avoiding this problem: The first is to use cross-validation
        |  and similar techniques to check for the presence of over-fitting and
        |  optimally select hyper-parameters such as to minimize the generalization
        |  error. The second is to use some form of regularization, which emerges
        |  naturally in a Bayesian framework, where the regularization can be
        |  performed by selecting a larger prior probability over simpler models;
        |  but also in statistical learning theory, where the goal is to minimize over
        |  the "empirical risk" and the "structural risk".
        |
        |  For neural networks, the input patterns usually should be scaled/standardized.
        |  Commonly, each input variable is scaled into interval [0, 1] or to have
        |  mean 0 and standard deviation 1.
        |
        |  For penalty functions and output units, the following natural pairings are
        |  recommended:
        |
        |   * linear output units and a least squares penalty function.
        |   * a two-class cross-entropy penalty function and a logistic
        |     activation function.
        |   * a multi-class cross-entropy penalty function and a softmax
        |     activation function.
        |
        |  By assigning a softmax activation function on the output layer of
        |  the neural network for categorical target variables, the outputs
        |  can be interpreted as posterior probabilities, which are very useful.
        |
        |  @param x training samples.
        |  @param y training labels in [0, k), where k is the number of classes.
        |  @param numUnits the number of units in each layer.
        |  @param error the error function.
        |  @param activation the activation function of output layer.
        |  @param epochs the number of epochs of stochastic learning.
        |  @param eta the learning rate.
        |  @param alpha the momentum factor.
        |  @param lambda the weight decay for regularization.
      """.stripMargin)
    case "rbfnet" | "nrbfnet" => println(
      """
        |  def rbfnet[T <: AnyRef](x: Array[T], y: Array[Int], distance: Metric[T], rbf: RadialBasisFunction, centers: Array[T]): RBFNetwork[T]
        |  def rbfnet[T <: AnyRef](x: Array[T], y: Array[Int], distance: Metric[T], rbf: Array[RadialBasisFunction], centers: Array[T]): RBFNetwork[T]
        |  def rbfnet[T <: AnyRef](x: Array[T], y: Array[Double], distance: Metric[T], rbf: RadialBasisFunction, centers: Array[T]): RBFNetwork[T]
        |  def rbfnet[T <: AnyRef](x: Array[T], y: Array[Double], distance: Metric[T], rbf: Array[RadialBasisFunction], centers: Array[T]): RBFNetwork[T]
        |
        |  def nrbfnet[T <: AnyRef](x: Array[T], y: Array[Int], distance: Metric[T], rbf: RadialBasisFunction, centers: Array[T]): RBFNetwork[T]
        |  def nrbfnet[T <: AnyRef](x: Array[T], y: Array[Int], distance: Metric[T], rbf: Array[RadialBasisFunction], centers: Array[T]): RBFNetwork[T]
        |  def nrbfnet[T <: AnyRef](x: Array[T], y: Array[Double], distance: Metric[T], rbf: RadialBasisFunction, centers: Array[T]): RBFNetwork[T]
        |  def nrbfnet[T <: AnyRef](x: Array[T], y: Array[Double], distance: Metric[T], rbf: Array[RadialBasisFunction], centers: Array[T]): RBFNetwork[T]
        |
        |  Radial basis function networks. A radial basis function network is an
        |  artificial neural network that uses radial basis functions as activation
        |  functions. It is a linear combination of radial basis functions. They are
        |  used in function approximation, time series prediction, and control.
        |
        |  In its basic form, radial basis function network is in the form
        |
        |    y(x) = Sigma * w_i * phi(||x-c_i||)
        |
        |  where the approximating function y(x) is represented as a sum of N radial
        |  basis functions phi, each associated with a different center c_i,
        |  and weighted by an appropriate coefficient w<sub>i</sub>. For distance,
        |  one usually chooses Euclidean distance. The weights w<sub>i</sub> can
        |  be estimated using the matrix methods of linear least squares, because
        |  the approximating function is linear in the weights.
        |
        |  The centers c_i can be randomly selected from training data,
        |  or learned by some clustering method (e.g. k-means), or learned together
        |  with weight parameters undergo a supervised learning processing
        |  (e.g. error-correction learning).
        |
        |  The popular choices for phi comprise the Gaussian function and the so
        |  called thin plate splines. The advantage of the thin plate splines is that
        |  their conditioning is invariant under scalings. Gaussian, multi-quadric
        |  and inverse multi-quadric are infinitely smooth and and involve a scale
        |  or shape parameter, r_0 > 0. Decreasing
        |  r_0 tends to flatten the basis function. For a
        |  given function, the quality of approximation may strongly depend on this
        |  parameter. In particular, increasing r_0 has the
        |  effect of better conditioning (the separation distance of the scaled points
        |  increases).
        |
        |  A variant on RBF networks is normalized radial basis function (NRBF)
        |  networks, in which we require the sum of the basis functions to be unity.
        |  NRBF arises more naturally from a Bayesian statistical perspective. However,
        |  there is no evidence that either the NRBF method is consistently superior
        |  to the RBF method, or vice versa.
        |
        |  SVMs with Gaussian kernel have similar structure as RBF networks with
        |  Gaussian radial basis functions. However, the SVM approach "automatically"
        |  solves the network complexity problem since the size of the hidden layer
        |  is obtained as the result of the QP procedure. Hidden neurons and
        |  support vectors correspond to each other, so the center problems of
        |  the RBF network is also solved, as the support vectors serve as the
        |  basis function centers. It was reported that with similar number of support
        |  vectors/centers, SVM shows better generalization performance than RBF
        |  network when the training data size is relatively small. On the other hand,
        |  RBF network gives better generalization performance than SVM on large
        |  training data.
        |
        |  @param x training samples.
        |  @param y training labels in [0, k), where k is the number of classes.
        |  @param distance the distance metric functor.
        |  @param rbf the radial basis functions.
        |  @param centers the centers of RBF functions.
        |  @param normalized true for the normalized RBF network.
      """.stripMargin)
    case "naiveBayes" => println(
      """
        |  def naiveBayes(x: Array[Array[Double]], y: Array[Int], model: NaiveBayes.Model, priori: Array[Double] = null, sigma: Double = 1.0): NaiveBayes
        |  def naiveBayes(priori: Array[Double], condprob: Array[Array[Distribution]]): NaiveBayes
        |
        |  Naive Bayes classifier. A naive Bayes classifier is a simple probabilistic
        |  classifier based on applying Bayes' theorem with strong (naive) independence
        |  assumptions. Depending on the precise nature of the probability model, naive
        |  Bayes classifiers can be trained very efficiently in a supervised learning
        |  setting.
        |
        |  In spite of their naive design and apparently over-simplified assumptions,
        |  naive Bayes classifiers have worked quite well in many complex real-world
        |  situations and are very popular in Natural Language Processing (NLP).
        |
        |  For a general purpose naive Bayes classifier without any assumptions
        |  about the underlying distribution of each variable, we don't provide
        |  a learning method to infer the variable distributions from the training data.
        |  Instead, the users can fit any appropriate distributions on the data by
        |  themselves with various {@link Distribution} classes. Although the predict
        |  method takes an array of double values as a general form of independent variables,
        |  the users are free to use any discrete distributions to model categorical or
        |  ordinal random variables.
        |
        |  For document classification in NLP, there are two different ways we can set
        |  up an naive Bayes classifier: multinomial model and Bernoulli model. The
        |  multinomial model generates one term from the vocabulary in each position
        |  of the document. The multivariate Bernoulli model or Bernoulli model
        |  generates an indicator for each term of the vocabulary, either indicating
        |  presence of the term in the document or indicating absence.
        |  Of the two models, the Bernoulli model is particularly sensitive to noise
        |  features. A Bernoulli naive Bayes classifier requires some form of feature selection or else its accuracy will be low.
        |
        |  The different generation models imply different estimation strategies and
        |  different classification rules. The Bernoulli model estimates as the
        |  fraction of documents of class that contain term. In contrast, the
        |  multinomial model estimates as the fraction of tokens or fraction of
        |  positions in documents of class that contain term. When classifying a
        |  test document, the Bernoulli model uses binary occurrence information,
        |  ignoring the number of occurrences, whereas the multinomial model keeps
        |  track of multiple occurrences. As a result, the Bernoulli model typically
        |  makes many mistakes when classifying long documents. However, it was reported
        |  that the Bernoulli model works better in sentiment analysis.
        |
        |  The models also differ in how non-occurring terms are used in classification.
        |  They do not affect the classification decision in the multinomial model;
        |  but in the Bernoulli model the probability of nonoccurrence is factored
        |  in when computing. This is because only the Bernoulli model models
        |  absence of terms explicitly.
        |
        |  @param x training samples.
        |  @param y training labels in [0, k), where k is the number of classes.
        |  @param model the generation model of naive Bayes classifier.
        |  @param priori the priori probability of each class.
        |  @param sigma the prior count of add-k smoothing of evidence.
        |  @param condprob the conditional distribution of each variable in
        |                  each class. In particular, condprob[i][j] is the conditional
        |                  distribution P(x_j | class i).
      """.stripMargin)
    case "svr" => println(
      """
        |  def svr[T <: AnyRef](x: Array[T], y: Array[Double], kernel: MercerKernel[T], eps: Double, C: Double, tol: Double = 1E-3): SVR[T]
        |  def svr[T <: AnyRef](x: Array[T], y: Array[Double], weight: Array[Double], kernel: MercerKernel[T], eps: Double, C: Double, tol: Double): SVR[T]
        |
        |  Support vector regression. Like SVMs for classification, the model produced
        |  by SVR depends only on a subset of the training data, because the cost
        |  function ignores any training data close to the model prediction (within
        |  a threshold).
        |
        |  @param x training data
        |  @param y training labels
        |  @param kernel the kernel function.
        |  @param eps the loss function error threshold.
        |  @param C the soft margin penalty parameter.
        |  @param tol the tolerance of convergence test.
      """.stripMargin)
    case "gpr" => println(
      """
        |  def gpr[T <: AnyRef](x: Array[T], y: Array[Double], kernel: MercerKernel[T], lambda: Double): GaussianProcessRegression[T]
        |  def gpr[T <: AnyRef](x: Array[T], y: Array[Double], t: Array[T], kernel: MercerKernel[T], lambda: Double, nystrom: Boolean = false): GaussianProcessRegression[T]
        |
        |  Gaussian Process for Regression. A Gaussian process is a stochastic process
        |  whose realizations consist of random values associated with every point in
        |  a range of times (or of space) such that each such random variable has
        |  a normal distribution. Moreover, every finite collection of those random
        |  variables has a multivariate normal distribution.
        |
        |  A Gaussian process can be used as a prior probability distribution over
        |  functions in Bayesian inference. Given any set of N points in the desired
        |  domain of your functions, take a multivariate Gaussian whose covariance
        |  matrix parameter is the Gram matrix of N points with some desired kernel,
        |  and sample from that Gaussian. Inference of continuous values with a
        |  Gaussian process prior is known as Gaussian process regression.
        |
        |  The fitting is performed in the reproducing kernel Hilbert space with
        |  the "kernel trick". The loss function is squared-error. This also arises
        |  as the kriging estimate of a Gaussian random field in spatial statistics.
        |
        |  A significant problem with Gaussian process prediction is that it typically
        |  scales as O(n^3). For large problems (e.g. n > 10,000) both
        |  storing the Gram matrix and solving the associated linear systems are
        |  prohibitive on modern workstations. An extensive range of proposals have
        |  been suggested to deal with this problem. A popular approach is the
        |  reduced-rank Approximations of the Gram Matrix, known as Nystrom approximation.
        |  Greedy approximation is another popular approach that uses an active set of
        |  training points of size m selected from the training set of size n > m.
        |  We assume that it is impossible to search for the optimal subset of size m
        |  due to combinatorics. The points in the active set could be selected
        |  randomly, but in general we might expect better performance if the points
        |  are selected greedily w.r.t. some criterion. Recently, researchers had
        |  proposed relaxing the constraint that the inducing variables must be a
        |  subset of training/test cases, turning the discrete selection problem
        |  into one of continuous optimization.
        |
        |  @param x the training dataset.
        |  @param y the response variable.
        |  @param t the inducing input. In case of regressor approximation, they are
        |           pre-selected or inducing samples acting as active set of regressors.
        |           In simple case, these can be chosen randomly from the training set or
        |           as the centers of k-means clustering.
        |           In case of Nystrom approximation, these can be chosen as the centers of
        |            k-means clustering.
        |  @param kernel the Mercer kernel.
        |  @param lambda the shrinkage/regularization parameter.
      """.stripMargin)
    case "" => println(
      """
        | General:
        |   help  -- print this summary
        |   :help -- print Scala shell command summary
        |   :quit -- exit the shell
        |   demo  -- show demo window
        |   benchmark -- benchmark tests
        |
        | I/O:
        |   read  -- Reads an object/model back from a file created by write command.
        |   write -- Writes an object/model to a file.
        |   readArff -- Reads an ARFF file.
        |   readLibsvm -- Reads a LivSVM file.
        |   readSparseMatrix -- Reads Harwell - Boeing column - compressed sparse matrix.
        |   readSparseData -- Reads spare dataset in coordinate triple tuple list format.
        |   readBinarySparseData -- Reads binary sparse dataset.
        |   readTable -- Reads a delimited text file.
        |   readCsv -- Reads a CSV file.
        |   readGct -- Reads GCT microarray gene expression file.
        |   readPcl -- Reads PCL microarray gene expression file.
        |   readRes -- Reads RES microarray gene expression file.
        |   readTxt -- Reads TXT microarray gene expression file.
        |
        | Classification:
        |   knn -- K-nearest neighbor classifier.
        |   logit -- Logistic regression.
        |   maxent -- Maximum entropy classifier.
        |   mlp -- Multilayer perceptron neural network.
        |   rbfnet -- Radial basis function network.
        |   svm -- Support vector machine for classification.
        |   cart -- Decision tree for classification.
        |   randomForest -- Random forest for classification.
        |   gbm -- Gradient boosting for classification.
        |   adaboost -- AdaBoost (Adaptive Boosting) classifier with decision trees.
        |   fisher -- Fisher's linear discriminant.
        |   lda -- Linear discriminant analysis.
        |   qda -- Quadratic discriminant analysis.
        |   rda -- Regularized discriminant analysis.
        |   naiveBayes -- Naive Bayes classifier.
        |
        | Regression:
        |   ols -- Ordinary least square.
        |   ridge -- Ridge regression.
        |   lasso -- Least absolute shrinkage and selection operator.
        |   rbfnet -- Radial basis function network
        |   svr -- Support vector regression.
        |   cart -- Regression tree.
        |   randomForest -- Random forest for regression.
        |   gbm -- Gradient boosting for regression.
        |   gpr -- Gaussian process for regression.
        |
        | Graphics:
        |   plot -- Scatter plot.
        |   line -- Scatter plot which connects points by straight lines.
        |   boxplot -- Boxplots can be useful to display differences between populations.
        |   heatmap -- Two-dimensional map represented as colors.
        |   hexmap -- A variant of heat map by replacing rectangle cells with hexagon cells.
        |   histogram -- Histogram of tabulated frequencies.
        |   contour -- Contour plot of a 3-dimensional surface.
        |   surface -- 3D surface plot.
        |   wireframe -- 3D presentation of physical object.
        |   grid -- 2D grid plot.
        |   qqplot -- Q-Q plot ("Q" stands for quantile) for comparing two probability distributions.
        |   dendrogram -- Tree diagram to illustrate the arrangement of the clusters produced by hierarchical clustering.
      """.stripMargin)
    case unknown => println(s"""Unknown command: $unknown, type "help()" to see available commands.""")
  }
}
