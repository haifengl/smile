/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.plot

import java.awt.Color
import smile.data.DataFrame
import smile.clustering.HierarchicalClustering
import smile.math.matrix.SparseMatrix
import smile.stat.distribution.{DiscreteDistribution, Distribution}
import smile.projection.PCA

/** Swing based data visualization.
  *
  * @author Haifeng Li
  */
package object swing {
  /** Scatter plot.
    *
    * @param x a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param color the color used to draw points.
    * @param mark the mark used to draw points.
    *               - . : dot
    *               - + : +
    *               - - : -
    *               - | : |
    *               - * : star
    *               - x : x
    *               - o : circle
    *               - O : large circle
    *               - @ : solid circle
    *               - # : large solid circle
    *               - s : square
    *               - S : large square
    *               - q : solid square
    *               - Q : large solid square
    *               - others : dot
    *
    * @return the plot canvas which can be added other shapes.
    */
  def plot(x: Array[Array[Double]], mark: Char = '*', color: Color = Color.BLACK): Canvas = {
    ScatterPlot.of(x, mark, color).canvas
  }

  /** Scatter plot.
    *
    * @param x a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param y labels of points.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def plot(x: Array[Array[Double]], y: Array[String], mark: Char): Canvas = {
    ScatterPlot.of(x, y, mark).canvas
  }

  /** Scatter plot.
    *
    * @param x a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param y class label.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def plot(x: Array[Array[Double]], y: Array[Int], mark: Char): Canvas = {
    ScatterPlot.of(x, y, mark).canvas
  }

  /** Scatter plot.
    *
    * @param data the data frame.
    * @param x    the column as x-axis.
    * @param y    the column as y-axis.
    * @return the plot canvas which can be added other shapes.
    */
  def plot(data: DataFrame, x: String, y: String, mark: Char, color: Color): Canvas = {
    val canvas = ScatterPlot.of(data, x, y, mark, color).canvas
    canvas.setAxisLabels(x, y)
    canvas
  }

  /** Scatter plot.
    *
    * @param data the data frame.
    * @param x    the column as x-axis.
    * @param y    the column as y-axis.
    * @param category the category column for coloring.
    * @return the plot canvas which can be added other shapes.
    */
  def plot(data: DataFrame, x: String, y: String, category: String, mark: Char): Canvas = {
    val canvas = ScatterPlot.of(data, x, y, category, mark).canvas
    canvas.setAxisLabels(x, y)
    canvas
  }

  /** Scatter plot.
    *
    * @param data the data frame.
    * @param x    the column as x-axis.
    * @param y    the column as y-axis.
    * @param z    the column as z-axis.
    * @return the plot canvas which can be added other shapes.
    */
  def plot(data: DataFrame, x: String, y: String, z: String, mark: Char, color: Color): Canvas = {
    val canvas = ScatterPlot.of(data, x, y, z, mark, color).canvas
    canvas.setAxisLabels(x, y, z)
    canvas
  }

  /** Scatter plot.
    *
    * @param data the data frame.
    * @param x    the column as x-axis.
    * @param y    the column as y-axis.
    * @param z    the column as z-axis.
    * @param category the category column for coloring.
    * @return the plot canvas which can be added other shapes.
    */
  def plot(data: DataFrame, x: String, y: String, z: String, category: String, mark: Char): Canvas = {
    val canvas = ScatterPlot.of(data, x, y, z, category, mark).canvas
    canvas.setAxisLabels(x, y, z)
    canvas
  }

  /** Scatterplot Matrix (SPLOM).
    *
    * @param data a data frame.
    * @param mark the legend for all classes.
    * @return the plot panel.
    */
  def splom(data: DataFrame, mark: Char, color: Color): PlotGrid = {
    PlotGrid.splom(data, mark, color)
  }

  /** Scatterplot Matrix (SPLOM).
    *
    * @param data an attribute frame.
    * @param mark the legend for all classes.
    * @param category the category column for coloring.
    * @return the plot panel.
    */
  def splom(data: DataFrame, mark: Char, category: String): PlotGrid = {
    PlotGrid.splom(data, mark, category)
  }

  /**
    * Text plot.
    *
    * @param texts       the texts.
    * @param coordinates a n-by-2 or n-by-3 matrix that are the coordinates of texts.
    */
  def text(texts: Array[String], coordinates: Array[Array[Double]]): Canvas = TextPlot.of(texts, coordinates).canvas

  /** Line plot.
    *
    * @param data a n-by-2 or n-by-3 matrix that describes coordinates of points.
    * @param style the stroke style of line.
    * @param mark the mark used to draw data points. The default value ' ' makes the point indistinguishable
    *               from the line on purpose.
    * @param color the color of line.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def line(data: Array[Array[Double]], style: Line.Style = Line.Style.SOLID, color: Color = Color.BLACK, mark: Char = ' ', label: String = null): Canvas = {
    if (label == null) {
      new LinePlot(new Line(data, style, mark, color)).canvas
    } else {
      val lines = Array(new Line(data, style, mark, color))
      val legends = Array(new Legend(label, color))
      new LinePlot(lines, legends).canvas
    }
  }

  /** Create a plot canvas with the staircase line plot.
    * @param data a n x 2 or n x 3 matrix that describes coordinates of points.
    */
  def staircase(data: Array[Array[Double]], color: Color = Color.BLACK, label: String = null): Canvas = {
    StaircasePlot.of(data, color, label).canvas
  }

  /** A box plot is a convenient way of graphically depicting groups of numerical
    * data through their five-number summaries (the smallest observation
    * (sample minimum), lower quartile (Q1), median (Q2), upper quartile (Q3),
    * and largest observation (sample maximum). A box plot may also indicate
    * which observations, if any, might be considered outliers.
    *
    * Box plots can be useful to display differences between populations without
    * making any assumptions of the underlying statistical distribution: they are
    * non-parametric. The spacings between the different parts of the box help
    * indicate the degree of dispersion (spread) and skewness in the data, and
    * identify outliers.
    *
    * For a data set, we construct a boxplot in the following manner:
    *
    *  - Calculate the first q<sub>1</sub>, the median q<sub>2</sub> and third
    * quartile q<sub>3</sub>.
    * - Calculate the interquartile range (IQR) by subtracting the first
    * quartile from the third quartile. (q<sub>3</sub> ? q<sub>1</sub>)
    *  - Construct a box above the number line bounded on the bottom by the first
    * quartile (q<sub>1</sub>) and on the top by the third quartile (q<sub>3</sub>).
    *  - Indicate where the median lies inside of the box with the presence of
    * a line dividing the box at the median value.
    *  - Any data observation which lies more than 1.5*IQR lower than the first
    * quartile or 1.5IQR higher than the third quartile is considered an outlier.
    * Indicate where the smallest value that is not an outlier is by connecting it
    * to the box with a horizontal line or "whisker". Optionally, also mark the
    * position of this value more clearly using a small vertical line. Likewise,
    * connect the largest value that is not an outlier to the box by a "whisker"
    * (and optionally mark it with another small vertical line).
    *  - Indicate outliers by dots.
    *
    * @param data a data matrix of which each row will create a box plot.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def boxplot(data: Array[Double]*): Canvas = {
    BoxPlot.of(data: _*).canvas
  }

  /** Box plot.
    *
    * @param data a data matrix of which each row will create a box plot.
    * @param labels the labels for each box plot.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def boxplot(data: Array[Array[Double]], labels: Array[String]): Canvas = {
    new BoxPlot(data, labels).canvas()
  }

  /** Contour plot. A contour plot is a graphical technique for representing a 3-dimensional
    * surface by plotting constant z slices, called contours, on a 2-dimensional
    * format. That is, given a value for z, lines are drawn for connecting the
    * (x, y) coordinates where that z value occurs. The contour plot is an
    * alternative to a 3-D surface plot.
    *
    * @param z the data matrix to create contour plot.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def contour(z: Array[Array[Double]]): Canvas = {
    Contour.of(z).canvas
  }

  /** Contour plot. A contour plot is a graphical technique for representing a 3-dimensional
    * surface by plotting constant z slices, called contours, on a 2-dimensional
    * format. That is, given a value for z, lines are drawn for connecting the
    * (x, y) coordinates where that z value occurs. The contour plot is an
    * alternative to a 3-D surface plot.
    *
    * @param z the data matrix to create contour plot.
    * @param levels the level values of contours.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def contour(z: Array[Array[Double]], levels: Array[Double]): Canvas = {
    new Contour(z, levels).canvas
  }

  /** Contour plot. A contour plot is a graphical technique for representing a 3-dimensional
    * surface by plotting constant z slices, called contours, on a 2-dimensional
    * format. That is, given a value for z, lines are drawn for connecting the
    * (x, y) coordinates where that z value occurs. The contour plot is an
    * alternative to a 3-D surface plot.
    *
    * @param x the x coordinates of the data grid of z. Must be in ascending order.
    * @param y the y coordinates of the data grid of z. Must be in ascending order.
    * @param z the data matrix to create contour plot.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def contour(x: Array[Double], y: Array[Double], z: Array[Array[Double]]): Canvas = {
    Contour.of(x, y, z).canvas
  }

  /** 3D surface plot.
    *
    * @param z the z-axis values of surface.
    * @param palette the color palette.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def surface(z: Array[Array[Double]], palette: Array[Color] = Palette.jet(16)): Canvas = {
    Surface.of(z, palette).canvas
  }

  /** 3D surface plot.
    *
    * @param x the x-axis values of surface.
    * @param y the y-axis values of surface.
    * @param z the z-axis values of surface.
    * @param palette the color palette.
    *
    * @return the plot canvas which can be added other shapes.
    */
  def surface(x: Array[Double], y: Array[Double], z: Array[Array[Double]], palette: Array[Color]): Canvas = {
    Surface.of(x, y, z, palette).canvas
  }

  /** Wire frame plot.
    * A wire frame model specifies each edge of the physical object where two
    * mathematically continuous smooth surfaces meet, or by connecting an
    * object's constituent vertices using straight lines or curves.
    *
    * @param vertices a n-by-2 or n-by-3 array which are coordinates of n vertices.
    * @param edges an m-by-2 array of which each row is the vertex indices of two
    *              end points of each edge.
    */
  def wireframe(vertices: Array[Array[Double]], edges: Array[Array[Int]]): Canvas = {
    Wireframe.of(vertices, edges).canvas
  }

  /** 2D grid plot.
    *
    * @param data an m x n x 2 array which are coordinates of m x n grid.
    */
  def grid(data: Array[Array[Array[Double]]]): Canvas = {
    Grid.of(data).canvas
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def heatmap(z: Array[Array[Double]], palette: Array[Color] = Palette.jet(16)): Canvas = {
    Heatmap.of(z, palette).canvas
  }

  /** Pseudo heat map plot.
    * @param x x coordinate of data matrix cells. Must be in ascending order.
    * @param y y coordinate of data matrix cells. Must be in ascending order.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def heatmap(x: Array[Double], y: Array[Double], z: Array[Array[Double]], palette: Array[Color]): Canvas = {
    new Heatmap(x, y, z, palette).canvas
  }

  /** Pseudo heat map plot.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param rowLabels the labels for rows of data matrix.
    * @param columnLabels the labels for columns of data matrix.
    * @param palette the color palette.
    */
  def heatmap(rowLabels: Array[String], columnLabels: Array[String], z: Array[Array[Double]], palette: Array[Color]): Canvas = {
    new Heatmap(rowLabels, columnLabels, z, palette).canvas
  }

  /** Visualize sparsity pattern.
    * @param matrix a sparse matrix.
    */
  def spy(matrix: SparseMatrix, k: Int = 1): Canvas = {
    if (k <= 1)
      SparseMatrixPlot.of(matrix).canvas
    else
      SparseMatrixPlot.of(matrix, k).canvas
  }

  /** Heat map with hex shape.
    * @param z a data matrix to be shown in pseudo heat map.
    * @param palette the color palette.
    */
  def hexmap(z: Array[Array[Double]], palette: Array[Color] = Palette.jet(16)): Canvas = {
    Hexmap.of(z, palette).canvas
  }

  /** Histogram plot.
    * @param data a sample set.
    * @param k the number of bins.
    */
  def hist(data: Array[Double], k: Int = 10, prob: Boolean = false, color: Color = Color.BLUE): Canvas = {
    Histogram.of(data, k, prob, color).canvas
  }

  /** Histogram plot.
    * @param data a sample set.
    * @param breaks an array of size k+1 giving the breakpoints between
    *               histogram cells. Must be in ascending order.
    */
  def hist(data: Array[Double], breaks: Array[Double], prob: Boolean, color: Color): Canvas = {
    Histogram.of(data, breaks, prob, color).canvas
  }

  /** 3D histogram plot.
    * @param data a sample set.
    * @param xbins the number of bins on x-axis.
    * @param ybins the number of bins on y-axis.
    */
  def hist3(data: Array[Array[Double]], xbins: Int = 10, ybins: Int = 10, prob: Boolean = false, palette: Array[Color] = Palette.jet(16)): Canvas = {
    new Histogram3D(data, xbins, ybins, prob, palette).canvas
  }

  /** QQ plot of samples to standard normal distribution.
    * The x-axis is the quantiles of x and the y-axis is the
    * quantiles of normal distribution.
    * @param x a sample set.
    */
  def qqplot(x: Array[Double]): Canvas = {
    QQPlot.of(x).canvas
  }

  /** QQ plot of samples to given distribution.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of
    * given distribution.
    * @param x a sample set.
    * @param d a distribution.
    */
  def qqplot(x: Array[Double], d: Distribution): Canvas = {
    QQPlot.of(x, d).canvas
  }

  /** QQ plot of two sample sets.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of y.
    * @param x a sample set.
    * @param y a sample set.
    */
  def qqplot(x: Array[Double], y: Array[Double]): Canvas = {
    QQPlot.of(x, y).canvas
  }

  /** QQ plot of samples to given distribution.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of
    * given distribution.
    * @param x a sample set.
    * @param d a distribution.
    */
  def qqplot(x: Array[Int], d: DiscreteDistribution): Canvas = {
    QQPlot.of(x, d).canvas
  }

  /** QQ plot of two sample sets.
    * The x-axis is the quantiles of x and the y-axis is the quantiles of y.
    * @param x a sample set.
    * @param y a sample set.
    */
  def qqplot(x: Array[Int], y: Array[Int]): Canvas = {
    QQPlot.of(x, y).canvas
  }

  /** The scree plot is a useful visual aid for determining an appropriate number of principal components.
    * The scree plot graphs the eigenvalue against the component number. To determine the appropriate
    * number of components, we look for an "elbow" in the scree plot. The component number is taken to
    * be the point at which the remaining eigenvalues are relatively small and all about the same size.
    *
    * @param pca principal component analysis object.
    */
  def screeplot(pca: PCA): Canvas = {
    new ScreePlot(pca).canvas
  }

  /** A dendrogram is a tree diagram to illustrate the arrangement
    * of the clusters produced by hierarchical clustering.
    *
    * @param hc hierarchical clustering object.
    */
  def dendrogram(hc: HierarchicalClustering): Canvas = {
    new Dendrogram(hc.tree, hc.height).canvas
  }

  /** A dendrogram is a tree diagram to illustrate the arrangement
    * of the clusters produced by hierarchical clustering.
    *
    * @param merge an n-1 by 2 matrix of which row i describes the merging of clusters at
    *              step i of the clustering. If an element j in the row is less than n, then
    *              observation j was merged at this stage. If j &ge; n then the merge
    *              was with the cluster formed at the (earlier) stage j-n of the algorithm.
    * @param height a set of n-1 non-decreasing real values, which are the clustering height,
    *               i.e., the value of the criterion associated with the clustering method
    *               for the particular agglomeration.
    */
  def dendrogram(merge: Array[Array[Int]], height: Array[Double]): Canvas = {
    new Dendrogram(merge, height).canvas
  }
}
