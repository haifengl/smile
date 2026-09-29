/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.swing

import java.awt.Color
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import smile.clustering.HierarchicalClustering
import smile.data.DataFrame
import smile.stat.distribution.DiscreteDistribution
import smile.stat.distribution.Distribution
import smile.tensor.SparseMatrix

/**
 * Scatter plot.
 *
 * @param x an n-by-2 or n-by-3 matrix that describes coordinates of points.
 * @param mark the mark used to draw points.
 * @param color the color used to draw points.
 * @return the plot canvas.
 */
fun plot(x: Array<DoubleArray>, mark: Char = '*', color: Color = Color.BLACK): Canvas {
    return Canvas(ScatterPlot.of(x, mark, color).figure())
}

/**
 * Scatter plot with string group labels.
 *
 * @param x an n-by-2 or n-by-3 matrix that describes coordinates of points.
 * @param y labels of points.
 * @param mark the mark used to draw points.
 * @return the plot canvas.
 */
fun plot(x: Array<DoubleArray>, y: Array<String>, mark: Char = '*'): Canvas {
    return Canvas(ScatterPlot.of(x, y, mark).figure())
}

/**
 * Scatter plot with integer class labels.
 *
 * @param x an n-by-2 or n-by-3 matrix that describes coordinates of points.
 * @param y class labels.
 * @param mark the mark used to draw points.
 * @return the plot canvas.
 */
fun plot(x: Array<DoubleArray>, y: IntArray, mark: Char = '*'): Canvas {
    return Canvas(ScatterPlot.of(x, y, mark).figure())
}

/**
 * Scatter plot from a data frame.
 *
 * @param data the data frame.
 * @param x the column as x-axis.
 * @param y the column as y-axis.
 * @param mark the mark used to draw points.
 * @param color the color used to draw points.
 * @return the plot canvas.
 */
fun plot(data: DataFrame, x: String, y: String, mark: Char = '*', color: Color = Color.BLACK): Canvas {
    val figure = ScatterPlot.of(data, x, y, mark, color).figure()
    figure.setAxisLabels(x, y)
    return Canvas(figure)
}

/**
 * Scatter plot from a data frame with color by category.
 *
 * @param data the data frame.
 * @param x the column as x-axis.
 * @param y the column as y-axis.
 * @param category the category column for coloring.
 * @param mark the mark used to draw points.
 * @return the plot canvas.
 */
fun plot(data: DataFrame, x: String, y: String, category: String, mark: Char = '*'): Canvas {
    val figure = ScatterPlot.of(data, x, y, category, mark).figure()
    figure.setAxisLabels(x, y)
    return Canvas(figure)
}

/**
 * 3D Scatter plot from a data frame.
 *
 * @param data the data frame.
 * @param x the column as x-axis.
 * @param y the column as y-axis.
 * @param z the column as z-axis.
 * @param mark the mark used to draw points.
 * @param color the color used to draw points.
 * @return the plot canvas.
 */
fun plot(data: DataFrame, x: String, y: String, z: String, mark: Char = '*', color: Color = Color.BLACK): Canvas {
    val figure = ScatterPlot.of(data, x, y, z, mark, color).figure()
    figure.setAxisLabels(x, y, z)
    return Canvas(figure)
}

/**
 * 3D Scatter plot from a data frame with color by category.
 *
 * @param data the data frame.
 * @param x the column as x-axis.
 * @param y the column as y-axis.
 * @param z the column as z-axis.
 * @param category the category column for coloring.
 * @param mark the mark used to draw points.
 * @return the plot canvas.
 */
fun plot(data: DataFrame, x: String, y: String, z: String, category: String, mark: Char = '*'): Canvas {
    val figure = ScatterPlot.of(data, x, y, z, category, mark).figure()
    figure.setAxisLabels(x, y, z)
    return Canvas(figure)
}

/**
 * Scatterplot Matrix (SPLOM).
 *
 * @param data a data frame.
 * @param mark the point mark for data points.
 * @param color the color for all points.
 * @return the multi-figure plot panel.
 */
fun splom(data: DataFrame, mark: Char = '*', color: Color = Color.BLACK): MultiFigurePane {
    return MultiFigurePane.splom(data, mark, color)
}

/**
 * Scatterplot Matrix (SPLOM) with color by category.
 *
 * @param data a data frame.
 * @param mark the point mark for data points.
 * @param category the category column for coloring.
 * @return the multi-figure plot panel.
 */
fun splom(data: DataFrame, mark: Char, category: String): MultiFigurePane {
    return MultiFigurePane.splom(data, mark, category)
}

/**
 * Scatterplot Matrix (SPLOM) with color by category.
 *
 * @param data a data frame.
 * @param category the category column for coloring.
 * @param mark the point mark for data points.
 * @return the multi-figure plot panel.
 */
fun splom(data: DataFrame, category: String, mark: Char = '*'): MultiFigurePane {
    return MultiFigurePane.splom(data, mark, category)
}

/**
 * Text plot.
 *
 * @param texts the texts to render.
 * @param coordinates an n-by-2 or n-by-3 matrix of coordinates for texts.
 * @return the plot canvas.
 */
fun text(texts: Array<String>, coordinates: Array<DoubleArray>): Canvas {
    return Canvas(TextPlot.of(texts, coordinates).figure())
}

/**
 * Line plot.
 *
 * @param data an n-by-2 or n-by-3 matrix that describes coordinates of points.
 * @param style the stroke style of line.
 * @param color the color of line.
 * @param mark the mark used to draw data points.
 * @param label the legend label.
 * @return the plot canvas.
 */
fun line(
    data: Array<DoubleArray>,
    style: Line.Style = Line.Style.SOLID,
    color: Color = Color.BLACK,
    mark: Char = ' ',
    label: String? = null
): Canvas {
    val figure = if (label == null) {
        LinePlot(Line(data, style, mark, color)).figure()
    } else {
        val lines = arrayOf(Line(data, style, mark, color))
        val legends = arrayOf(Legend(label, color))
        LinePlot(lines, legends).figure()
    }
    return Canvas(figure)
}

/**
 * Staircase line plot.
 *
 * @param data an n x 2 or n x 3 matrix that describes coordinates of points.
 * @param color the line color.
 * @param label the legend label.
 * @return the plot canvas.
 */
fun staircase(data: Array<DoubleArray>, color: Color = Color.BLACK, label: String? = null): Canvas {
    return Canvas(StaircasePlot.of(data, color, label).figure())
}

/**
 * Bar plot.
 *
 * @param data the bar heights.
 * @return the plot canvas.
 */
fun barplot(data: DoubleArray): Canvas {
    return Canvas(BarPlot.of(data).figure())
}

/**
 * Bar plot.
 *
 * @param data the bar heights.
 * @return the plot canvas.
 */
fun barplot(data: IntArray): Canvas {
    return Canvas(BarPlot.of(data).figure())
}

/**
 * Grouped bar plot.
 *
 * @param data each row is a data set of bars (bar height).
 * @param labels the group labels.
 * @return the plot canvas.
 */
fun barplot(data: Array<DoubleArray>, labels: Array<String>): Canvas {
    return Canvas(BarPlot.of(data, labels).figure())
}

/**
 * Box plot from variable arrays.
 *
 * @param data data arrays of which each row will create a box plot.
 * @return the plot canvas.
 */
fun boxplot(vararg data: DoubleArray): Canvas {
    val array = Array(data.size) { i -> data[i] }
    return Canvas(BoxPlot.of(*array).figure())
}

/**
 * Box plot with labels.
 *
 * @param data a data matrix of which each row will create a box plot.
 * @param labels the labels for each box plot.
 * @return the plot canvas.
 */
fun boxplot(data: Array<DoubleArray>, labels: Array<String>): Canvas {
    return Canvas(BoxPlot(data, labels).figure())
}

/**
 * Box plot from a data frame.
 *
 * @param data the data frame.
 * @param labels the column labels to plot.
 * @return the plot canvas.
 */
fun boxplot(data: DataFrame, vararg labels: String): Canvas {
    val cols = if (labels.isEmpty()) data.schema().names() else labels
    val array = Array(cols.size) { i -> data.column(cols[i]).toDoubleArray() }
    return Canvas(BoxPlot(array, arrayOf(*cols)).figure())
}

/**
 * Contour plot.
 *
 * @param z the data matrix to create contour plot.
 * @return the plot canvas.
 */
fun contour(z: Array<DoubleArray>): Canvas {
    return Canvas(Contour.of(z).figure())
}

/**
 * Contour plot with specified levels.
 *
 * @param z the data matrix to create contour plot.
 * @param levels the level values of contours.
 * @return the plot canvas.
 */
fun contour(z: Array<DoubleArray>, levels: DoubleArray): Canvas {
    return Canvas(Contour(z, levels).figure())
}

/**
 * Contour plot with grid coordinates.
 *
 * @param x the x coordinates of the data grid of z. Must be in ascending order.
 * @param y the y coordinates of the data grid of z. Must be in ascending order.
 * @param z the data matrix to create contour plot.
 * @return the plot canvas.
 */
fun contour(x: DoubleArray, y: DoubleArray, z: Array<DoubleArray>): Canvas {
    return Canvas(Contour.of(x, y, z).figure())
}

/**
 * 3D surface plot.
 *
 * @param z the z-axis values of surface.
 * @param palette the color palette.
 * @return the plot canvas.
 */
fun surface(z: Array<DoubleArray>, palette: Array<Color> = Palette.jet(16)): Canvas {
    return Canvas(Surface.of(z, palette).figure())
}

/**
 * 3D surface plot with coordinate grid.
 *
 * @param x the x-axis values of surface.
 * @param y the y-axis values of surface.
 * @param z the z-axis values of surface.
 * @param palette the color palette.
 * @return the plot canvas.
 */
fun surface(x: DoubleArray, y: DoubleArray, z: Array<DoubleArray>, palette: Array<Color> = Palette.jet(16)): Canvas {
    return Canvas(Surface.of(x, y, z, palette).figure())
}

/**
 * Wireframe plot.
 *
 * @param vertices an n-by-2 or n-by-3 array of vertex coordinates.
 * @param edges an m-by-2 array of which each row is vertex indices of an edge.
 * @return the plot canvas.
 */
fun wireframe(vertices: Array<DoubleArray>, edges: Array<IntArray>): Canvas {
    return Canvas(Wireframe.of(vertices, edges).figure())
}

/**
 * 2D grid plot.
 *
 * @param data an m x n x 2 array of grid coordinates.
 * @return the plot canvas.
 */
fun grid(data: Array<Array<DoubleArray>>): Canvas {
    return Canvas(Grid.of(data).figure())
}

/**
 * Pseudo heatmap plot.
 *
 * @param z a data matrix to be shown in heatmap.
 * @param palette the color palette.
 * @return the plot canvas.
 */
fun heatmap(z: Array<DoubleArray>, palette: Array<Color> = Palette.jet(16)): Canvas {
    return Canvas(Heatmap.of(z, palette).figure())
}

/**
 * Pseudo heatmap plot with coordinate axes.
 *
 * @param x x coordinate of data matrix cells. Must be in ascending order.
 * @param y y coordinate of data matrix cells. Must be in ascending order.
 * @param z a data matrix to be shown in heatmap.
 * @param palette the color palette.
 * @return the plot canvas.
 */
fun heatmap(x: DoubleArray, y: DoubleArray, z: Array<DoubleArray>, palette: Array<Color> = Palette.jet(16)): Canvas {
    return Canvas(Heatmap(x, y, z, palette).figure())
}

/**
 * Pseudo heatmap plot with row and column labels.
 *
 * @param rowLabels the labels for rows of data matrix.
 * @param columnLabels the labels for columns of data matrix.
 * @param z a data matrix to be shown in heatmap.
 * @param palette the color palette.
 * @return the plot canvas.
 */
fun heatmap(rowLabels: Array<String>, columnLabels: Array<String>, z: Array<DoubleArray>, palette: Array<Color> = Palette.jet(16)): Canvas {
    return Canvas(Heatmap(rowLabels, columnLabels, z, palette).figure())
}

/**
 * Visualize sparsity pattern.
 *
 * @param matrix a sparse matrix.
 * @param k the number of colors in the palette.
 * @return the plot canvas.
 */
fun spy(matrix: SparseMatrix, k: Int = 1): Canvas {
    val figure = if (k <= 1)
        SparseMatrixPlot.of(matrix).figure()
    else
        SparseMatrixPlot.of(matrix, k).figure()
    return Canvas(figure)
}

/**
 * Heatmap with hex shape.
 *
 * @param z a data matrix to be shown in hexmap.
 * @param palette the color palette.
 * @return the plot canvas.
 */
fun hexmap(z: Array<DoubleArray>, palette: Array<Color> = Palette.jet(16)): Canvas {
    return Canvas(Hexmap.of(z, palette).figure())
}

/**
 * Histogram plot.
 *
 * @param data a sample set.
 * @param k the number of bins.
 * @param prob if true, probability scale; otherwise frequency scale.
 * @param color the color of bars.
 * @return the plot canvas.
 */
fun hist(data: DoubleArray, k: Int = 10, prob: Boolean = false, color: Color = Color.BLUE): Canvas {
    return Canvas(Histogram.of(data, k, prob, color).figure())
}

/**
 * Histogram plot with custom breaks.
 *
 * @param data a sample set.
 * @param breaks an array of size k+1 giving the breakpoints between cells.
 * @param prob if true, probability scale; otherwise frequency scale.
 * @param color the color of bars.
 * @return the plot canvas.
 */
fun hist(data: DoubleArray, breaks: DoubleArray, prob: Boolean = false, color: Color = Color.BLUE): Canvas {
    return Canvas(Histogram.of(data, breaks, prob, color).figure())
}

/**
 * 3D histogram plot.
 *
 * @param data a sample set.
 * @param xbins the number of bins on x-axis.
 * @param ybins the number of bins on y-axis.
 * @param prob if true, probability scale; otherwise frequency scale.
 * @param palette the color palette.
 * @return the plot canvas.
 */
fun hist3(
    data: Array<DoubleArray>,
    xbins: Int = 10,
    ybins: Int = 10,
    prob: Boolean = false,
    palette: Array<Color> = Palette.jet(16)
): Canvas {
    return Canvas(Histogram3D(data, xbins, ybins, prob, palette).figure())
}

/**
 * QQ plot of samples to standard normal distribution.
 *
 * @param x a sample set.
 * @return the plot canvas.
 */
fun qqplot(x: DoubleArray): Canvas {
    return Canvas(QQPlot.of(x).figure())
}

/**
 * QQ plot of samples to given distribution.
 *
 * @param x a sample set.
 * @param d a continuous distribution.
 * @return the plot canvas.
 */
fun qqplot(x: DoubleArray, d: Distribution): Canvas {
    return Canvas(QQPlot.of(x, d).figure())
}

/**
 * QQ plot of two continuous sample sets.
 *
 * @param x a sample set.
 * @param y another sample set.
 * @return the plot canvas.
 */
fun qqplot(x: DoubleArray, y: DoubleArray): Canvas {
    return Canvas(QQPlot.of(x, y).figure())
}

/**
 * QQ plot of samples to given discrete distribution.
 *
 * @param x a sample set.
 * @param d a discrete distribution.
 * @return the plot canvas.
 */
fun qqplot(x: IntArray, d: DiscreteDistribution): Canvas {
    return Canvas(QQPlot.of(x, d).figure())
}

/**
 * QQ plot of two discrete sample sets.
 *
 * @param x a sample set.
 * @param y another sample set.
 * @return the plot canvas.
 */
fun qqplot(x: IntArray, y: IntArray): Canvas {
    return Canvas(QQPlot.of(x, y).figure())
}

/**
 * Scree plot for principal component analysis.
 *
 * @param varianceProportion the proportion of variance contained in each principal component.
 * @return the plot canvas.
 */
fun screeplot(varianceProportion: DoubleArray): Canvas {
    return Canvas(ScreePlot(varianceProportion).figure())
}

/**
 * Dendrogram for hierarchical clustering.
 *
 * @param hc hierarchical clustering object.
 * @return the plot canvas.
 */
fun dendrogram(hc: HierarchicalClustering): Canvas {
    return Canvas(Dendrogram(hc.tree(), hc.height()).figure())
}

/**
 * Dendrogram for hierarchical clustering tree and heights.
 *
 * @param merge an (n-1)-by-2 matrix of cluster merges.
 * @param height non-decreasing clustering heights.
 * @return the plot canvas.
 */
fun dendrogram(merge: Array<IntArray>, height: DoubleArray): Canvas {
    return Canvas(Dendrogram(merge, height).figure())
}

/**
 * Shows a scene in a new window.
 *
 * @param scene the scene (Canvas, MultiFigurePane, etc.).
 * @return the window frame.
 */
fun show(scene: Scene): JFrame = scene.window()

/**
 * JFrame window wrapper.
 */
interface JWindow {
    val frame: JFrame

    /** Closes the window programmatically. */
    fun close() {
        frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
    }

    companion object {
        /** Opens a plot window. */
        fun of(canvas: Canvas): CanvasWindow = CanvasWindow(canvas.window(), canvas)

        /** Opens a multi-figure window. */
        fun of(canvas: MultiFigurePane): MultiFigureWindow = MultiFigureWindow(canvas.window(), canvas)
    }
}

/**
 * Plot canvas window.
 */
data class CanvasWindow(override val frame: JFrame, val canvas: Canvas) : JWindow

/**
 * Multi-figure plot window.
 */
data class MultiFigureWindow(override val frame: JFrame, val canvas: MultiFigurePane) : JWindow

/**
 * HTML `<img>` tag generator for Figure and JComponent.
 */
object Html {
    /**
     * Returns the HTML img tag of the figure encoded by BASE64.
     *
     * @param figure the figure.
     * @param width image width in pixels.
     * @param height image height in pixels.
     * @return the HTML img tag.
     */
    fun figure(figure: Figure, width: Int = 600, height: Int = 600): String {
        val bi = figure.toBufferedImage(width, height)
        val os = ByteArrayOutputStream()
        ImageIO.write(bi, "png", os)
        val base64 = Base64.getEncoder().encodeToString(os.toByteArray())
        return """<img src="data:image/png;base64,$base64">"""
    }

    /**
     * Returns the HTML img tag of the swing component encoded by BASE64.
     *
     * @param canvas the swing component.
     * @param width image width in pixels.
     * @param height image height in pixels.
     * @return the HTML img tag.
     */
    fun of(canvas: JComponent, width: Int = 600, height: Int = 600): String {
        val headless = Headless(canvas, width, height)
        headless.pack()
        headless.isVisible = true
        SwingUtilities.invokeAndWait {}

        val bi = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = bi.createGraphics()
        canvas.print(g2d)

        val os = ByteArrayOutputStream()
        ImageIO.write(bi, "png", os)
        val base64 = Base64.getEncoder().encodeToString(os.toByteArray())
        return """<img src="data:image/png;base64,$base64">"""
    }
}
