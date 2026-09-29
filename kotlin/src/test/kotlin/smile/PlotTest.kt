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
import java.awt.GraphicsEnvironment
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import smile.clustering.hclust
import smile.data.DataFrame
import smile.io.Paths
import smile.read
import smile.stat.distribution.GaussianDistribution
import smile.tensor.SparseMatrix

class PlotTest {

    private val iris: DataFrame by lazy {
        read.arff(Paths.getTestData("weka/iris.arff"))
    }

    private val pts = arrayOf(
        doubleArrayOf(0.0, 1.0),
        doubleArrayOf(1.0, 2.0),
        doubleArrayOf(2.0, 0.5),
        doubleArrayOf(3.0, 3.0)
    )

    private val pts3d = arrayOf(
        doubleArrayOf(0.0, 1.0, 0.5),
        doubleArrayOf(1.0, 2.0, 1.5),
        doubleArrayOf(2.0, 0.5, 2.5),
        doubleArrayOf(3.0, 3.0, 3.5)
    )

    private val gridZ = arrayOf(
        doubleArrayOf(1.0, 2.0, 3.0),
        doubleArrayOf(2.0, 3.0, 4.0),
        doubleArrayOf(3.0, 4.0, 5.0)
    )

    @Test
    fun testPlotScatter() {
        val c1 = plot(pts)
        assertNotNull(c1)
        assertNotNull(c1.figure())

        val c2 = plot(pts, arrayOf("A", "B", "A", "B"), 'o')
        assertNotNull(c2)

        val c3 = plot(pts, intArrayOf(0, 1, 0, 1), '+')
        assertNotNull(c3)

        val c4 = plot(iris, "sepallength", "sepalwidth", '*', Color.RED)
        assertNotNull(c4)
        assertEquals("sepallength", c4.figure().getAxis(0).label)
        assertEquals("sepalwidth", c4.figure().getAxis(1).label)

        val c5 = plot(iris, "sepallength", "sepalwidth", "class", 'x')
        assertNotNull(c5)

        val c6 = plot(iris, "sepallength", "sepalwidth", "petallength", '*', Color.BLUE)
        assertNotNull(c6)
        assertEquals("sepallength", c6.figure().getAxis(0).label)
        assertEquals("sepalwidth", c6.figure().getAxis(1).label)
        assertEquals("petallength", c6.figure().getAxis(2).label)

        val c7 = plot(iris, "sepallength", "sepalwidth", "petallength", "class", 'o')
        assertNotNull(c7)
    }

    @Test
    fun testSplom() {
        val s1 = splom(iris, '*', Color.BLACK)
        assertNotNull(s1)

        val s2 = splom(iris, '*', "class")
        assertNotNull(s2)

        val s3 = splom(iris, "class", 'o')
        assertNotNull(s3)
    }

    @Test
    fun testTextAndLine() {
        val t = text(arrayOf("P1", "P2", "P3", "P4"), pts)
        assertNotNull(t)

        val l1 = line(pts)
        assertNotNull(l1)

        val l2 = line(pts, Line.Style.DASH, Color.BLUE, 'o', "MyLine")
        assertNotNull(l2)

        val s = staircase(pts, Color.RED, "Steps")
        assertNotNull(s)
    }

    @Test
    fun testBarPlot() {
        val b1 = barplot(doubleArrayOf(1.0, 2.0, 3.0, 4.0))
        assertNotNull(b1)

        val b2 = barplot(intArrayOf(2, 4, 6, 8))
        assertNotNull(b2)

        val b3 = barplot(arrayOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(3.0, 4.0)), arrayOf("G1", "G2"))
        assertNotNull(b3)
    }

    @Test
    fun testBoxPlot() {
        val b1 = boxplot(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0), doubleArrayOf(2.0, 3.0, 4.0, 5.0, 6.0))
        assertNotNull(b1)

        val b2 = boxplot(
            arrayOf(doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(4.0, 5.0, 6.0)),
            arrayOf("A", "B")
        )
        assertNotNull(b2)

        val b3 = boxplot(iris, "sepallength", "sepalwidth")
        assertNotNull(b3)
    }

    @Test
    fun testContourAndSurface() {
        val c1 = contour(gridZ)
        assertNotNull(c1)

        val c2 = contour(gridZ, doubleArrayOf(1.5, 2.5, 3.5, 4.5))
        assertNotNull(c2)

        val c3 = contour(doubleArrayOf(0.0, 1.0, 2.0), doubleArrayOf(0.0, 1.0, 2.0), gridZ)
        assertNotNull(c3)

        val s1 = surface(gridZ)
        assertNotNull(s1)

        val s2 = surface(doubleArrayOf(0.0, 1.0, 2.0), doubleArrayOf(0.0, 1.0, 2.0), gridZ)
        assertNotNull(s2)
    }

    @Test
    fun testWireframeAndGrid() {
        val vertices = arrayOf(
            doubleArrayOf(0.0, 0.0, 0.0),
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(1.0, 1.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0)
        )
        val edges = arrayOf(
            intArrayOf(0, 1),
            intArrayOf(1, 2),
            intArrayOf(2, 3),
            intArrayOf(3, 0)
        )
        val w = wireframe(vertices, edges)
        assertNotNull(w)

        val gridData = Array(2) { i ->
            Array(2) { j ->
                doubleArrayOf(i.toDouble(), j.toDouble())
            }
        }
        val g = grid(gridData)
        assertNotNull(g)
    }

    @Test
    fun testHeatmapAndHexmap() {
        val h1 = heatmap(gridZ)
        assertNotNull(h1)

        val h2 = heatmap(doubleArrayOf(0.0, 1.0, 2.0), doubleArrayOf(0.0, 1.0, 2.0), gridZ)
        assertNotNull(h2)

        val h3 = heatmap(arrayOf("R1", "R2", "R3"), arrayOf("C1", "C2", "C3"), gridZ)
        assertNotNull(h3)

        val hex = hexmap(gridZ)
        assertNotNull(hex)
    }

    @Test
    fun testSpyAndHistogram() {
        val sparse = SparseMatrix.text(Paths.getTestData("matrix/mesh2em5.txt"))

        val spy1 = spy(sparse)
        assertNotNull(spy1)

        val spy2 = spy(sparse, 16)
        assertNotNull(spy2)

        val data = doubleArrayOf(1.0, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0)
        val h1 = hist(data, 5)
        assertNotNull(h1)

        val h2 = hist(data, doubleArrayOf(0.0, 2.0, 4.0, 6.0))
        assertNotNull(h2)

        val h3 = hist3(pts, 5, 5)
        assertNotNull(h3)
    }

    @Test
    fun testQQPlotAndScreePlot() {
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val qq1 = qqplot(data)
        assertNotNull(qq1)

        val qq2 = qqplot(data, GaussianDistribution(0.0, 1.0))
        assertNotNull(qq2)

        val qq3 = qqplot(data, doubleArrayOf(1.5, 2.5, 3.5, 4.5, 5.5))
        assertNotNull(qq3)

        val intData = intArrayOf(1, 2, 3, 4, 5)
        val qq4 = qqplot(intData, intArrayOf(2, 3, 4, 5, 6))
        assertNotNull(qq4)

        val scree = screeplot(doubleArrayOf(0.6, 0.25, 0.1, 0.05))
        assertNotNull(scree)
    }

    @Test
    fun testDendrogram() {
        val data = arrayOf(
            doubleArrayOf(1.0, 2.0),
            doubleArrayOf(1.5, 1.8),
            doubleArrayOf(5.0, 8.0),
            doubleArrayOf(8.0, 8.0),
            doubleArrayOf(1.0, 0.6),
            doubleArrayOf(9.0, 11.0)
        )
        val hc = hclust(data, "complete")
        val d1 = dendrogram(hc)
        assertNotNull(d1)

        val d2 = dendrogram(hc.tree(), hc.height())
        assertNotNull(d2)
    }

    @Test
    fun testHtml() {
        val canvas = plot(pts)
        val imgTag = Html.figure(canvas.figure(), 200, 200)
        assertTrue(imgTag.startsWith("<img src=\"data:image/png;base64,"))
        assertTrue(imgTag.endsWith("\">"))
    }

    @Test
    fun testWindowAndShow() {
        if (!GraphicsEnvironment.isHeadless()) {
            val canvas = plot(pts)
            val window = JWindow.of(canvas)
            assertNotNull(window.frame)
            window.close()

            val frame = show(canvas)
            assertNotNull(frame)
            frame.dispose()
        }
    }
}
