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
package smile.plot.swing;

import org.junit.jupiter.api.*;
import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ScatterPlot}, {@link LinePlot}, {@link BoxPlot},
 * {@link QQPlot}, {@link BarPlot}, and {@link ScreePlot}.
 * All rendering assertions are done via {@link Figure#toBufferedImage} so
 * the tests run in a headless environment without a display.
 */
public class PlotRenderTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private static BufferedImage render(Figure f) {
        return f.toBufferedImage(400, 300);
    }

    private static boolean hasNonWhitePixel(BufferedImage img) {
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                if (img.getRGB(x, y) != 0xFFFFFFFF) return true;
            }
        }
        return false;
    }

    // ── ScatterPlot ───────────────────────────────────────────────────────────

    @Test
    public void testScatterPlotBounds() {
        double[][] pts = {{0.0, 0.0}, {1.0, 2.0}, {3.0, -1.0}};
        ScatterPlot sp = ScatterPlot.of(pts, 'o');
        double[] lo = sp.getLowerBound();
        double[] hi = sp.getUpperBound();
        assertEquals(0.0, lo[0], 1e-9);
        assertEquals(-1.0, lo[1], 1e-9);
        assertEquals(3.0, hi[0], 1e-9);
        assertEquals(2.0, hi[1], 1e-9);
    }

    @Test
    public void testScatterPlotRendersWithoutError() {
        double[][] pts = {{0.0, 0.0}, {1.0, 1.0}, {2.0, 0.5}};
        Figure f = ScatterPlot.of(pts, '.').figure();
        assertDoesNotThrow(() -> render(f));
        assertTrue(hasNonWhitePixel(render(f)));
    }

    @Test
    public void testScatterPlotWithColorRendersCorrectly() {
        double[][] pts = {{0.5, 0.5}, {1.5, 1.5}};
        ScatterPlot sp = ScatterPlot.of(pts, 'o', Color.RED);
        Figure f = sp.figure();
        assertDoesNotThrow(() -> render(f));
    }

    @Test
    public void testScatterPlot3DRendersWithoutError() {
        double[][] pts = {{0.0, 0.0, 0.0}, {1.0, 1.0, 1.0}, {2.0, 0.5, 1.5}};
        ScatterPlot sp = ScatterPlot.of(pts, '*');
        Figure f = sp.figure();
        assertDoesNotThrow(() -> render(f));
    }

    // ── LinePlot ──────────────────────────────────────────────────────────────

    @Test
    public void testLinePlotBounds() {
        double[][] pts = {{0.0, 5.0}, {1.0, 3.0}, {2.0, 8.0}};
        LinePlot lp = LinePlot.of(pts, Color.BLUE);
        double[] lo = lp.getLowerBound();
        double[] hi = lp.getUpperBound();
        assertEquals(0.0, lo[0], 1e-9);
        assertEquals(3.0, lo[1], 1e-9);
        assertEquals(2.0, hi[0], 1e-9);
        assertEquals(8.0, hi[1], 1e-9);
    }

    @Test
    public void testLinePlotRendersWithoutError() {
        double[][] pts = {{0.0, 0.0}, {1.0, 1.0}, {2.0, 4.0}, {3.0, 9.0}};
        Figure f = LinePlot.of(pts, Color.RED).figure();
        assertDoesNotThrow(() -> render(f));
        assertTrue(hasNonWhitePixel(render(f)));
    }

    @Test
    public void testLinePlotWithStyleRendersWithoutError() {
        double[][] pts = {{0.0, 0.0}, {1.0, 2.0}, {2.0, 1.0}};
        Line line = new Line(pts, Line.Style.DASH, ' ', Color.GREEN);
        Figure f = new LinePlot(line).figure();
        assertDoesNotThrow(() -> render(f));
    }

    // ── BoxPlot ───────────────────────────────────────────────────────────────

    @Test
    public void testBoxPlotConstructionWithMatchingLabels() {
        double[][] data = {{1, 2, 3, 4, 5}, {2, 3, 4, 5, 6}};
        String[] labels = {"A", "B"};
        assertDoesNotThrow(() -> new BoxPlot(data, labels));
    }

    @Test
    public void testBoxPlotConstructionMismatchedLabelsThrows() {
        double[][] data = {{1, 2, 3}};
        String[] labels = {"A", "B"};
        assertThrows(IllegalArgumentException.class, () -> new BoxPlot(data, labels));
    }

    @Test
    public void testBoxPlotRendersWithoutError() {
        double[] group1 = new double[50];
        double[] group2 = new double[50];
        for (int i = 0; i < 50; i++) {
            group1[i] = i * 0.5;
            group2[i] = i * 0.3 + 5;
        }
        BoxPlot bp = new BoxPlot(new double[][]{group1, group2}, new String[]{"G1", "G2"});
        Figure f = bp.figure();
        assertDoesNotThrow(() -> render(f));
        assertTrue(hasNonWhitePixel(render(f)));
    }

    @Test
    public void testBoxPlotBoundsMinMax() {
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0};
        BoxPlot bp = new BoxPlot(new double[][]{data}, null);
        double[] lo = bp.getLowerBound();
        double[] hi = bp.getUpperBound();
        assertEquals(1.0, lo[1], 1e-9);
        assertEquals(5.0, hi[1], 1e-9);
    }

    @Test
    public void testBoxPlotTooltipInsideBoxReturnsValue() {
        double[] data = new double[100];
        for (int i = 0; i < 100; i++) data[i] = i;
        BoxPlot bp = new BoxPlot(new double[][]{data}, null);
        // x≈0.5 is the centre of the first box; y inside the IQR
        var tooltip = bp.tooltip(new double[]{0.5, 50.0});
        assertTrue(tooltip.isPresent());
    }

    @Test
    public void testBoxPlotTooltipOutsideBoxEmpty() {
        double[] data = {1.0, 2.0, 3.0, 4.0, 5.0};
        BoxPlot bp = new BoxPlot(new double[][]{data}, null);
        // x=5 is way outside the first box
        var tooltip = bp.tooltip(new double[]{5.0, 3.0});
        assertFalse(tooltip.isPresent());
    }

    // ── QQPlot ────────────────────────────────────────────────────────────────

    @Test
    public void testQQPlotOfGaussian() {
        double[] data = new double[100];
        for (int i = 0; i < 100; i++) data[i] = i * 0.1 - 5.0;
        QQPlot qq = QQPlot.of(data);
        assertNotNull(qq);
        // The plot must have sensible bounds derived from the 100 data points.
        double[] lo = qq.getLowerBound();
        double[] hi = qq.getUpperBound();
        assertTrue(hi[0] > lo[0], "Upper X bound must exceed lower X bound");
    }

    @Test
    public void testQQPlotRendersWithoutError() {
        double[] data = new double[200];
        for (int i = 0; i < 200; i++) data[i] = Math.sin(i * 0.05);
        QQPlot qq = QQPlot.of(data);
        Figure f = qq.figure();
        assertDoesNotThrow(() -> render(f));
        assertTrue(hasNonWhitePixel(render(f)));
    }

    @Test
    public void testQQPlotBoundsAreSensible() {
        double[] data = new double[50];
        for (int i = 0; i < 50; i++) data[i] = i * 0.2;
        QQPlot qq = QQPlot.of(data);
        double[] lo = qq.getLowerBound();
        double[] hi = qq.getUpperBound();
        assertTrue(hi[0] > lo[0], "Upper X bound must exceed lower X bound");
        assertTrue(hi[1] > lo[1], "Upper Y bound must exceed lower Y bound");
    }

    // ── BarPlot ───────────────────────────────────────────────────────────────

    @Test
    public void testBarPlotRendersWithoutError() {
        double[][] data = {{1.0, 3.0}, {2.0, 5.0}, {3.0, 2.0}};
        Bar bar = new Bar(data, 0.8, Color.BLUE);
        BarPlot bp = new BarPlot(bar);
        Figure f = bp.figure();
        assertDoesNotThrow(() -> render(f));
    }

    @Test
    public void testBarPlotBoundsIncludeBarWidth() {
        double[][] data = {{2.0, 4.0}};
        Bar bar = new Bar(data, 1.0, Color.RED);
        BarPlot bp = new BarPlot(bar);
        double[] lo = bp.getLowerBound();
        // x lower bound = 2.0 - 1.0/2 = 1.5
        assertEquals(1.5, lo[0], 1e-9);
    }

    // ── ScreePlot ─────────────────────────────────────────────────────────────

    @Test
    public void testScreePlotRendersWithoutError() {
        double[] variance = {0.4, 0.25, 0.15, 0.10, 0.06, 0.04};
        ScreePlot sp = new ScreePlot(variance);
        Figure f = sp.figure();
        assertDoesNotThrow(() -> render(f));
        assertTrue(hasNonWhitePixel(render(f)));
    }

    @Test
    public void testScreePlotCumulativeVarianceEndsAtOne() {
        double[] variance = {0.5, 0.3, 0.2};
        ScreePlot sp = new ScreePlot(variance);
        // cumulative variance of the last point in cumVar line should be 1.0
        double[] cumVarPoints = sp.lines()[1].points[variance.length - 1];
        assertEquals(1.0, cumVarPoints[1], 1e-9);
    }

    @Test
    public void testScreePlotAxisLabelsAreSet() {
        double[] variance = {0.6, 0.4};
        ScreePlot sp = new ScreePlot(variance);
        Figure f = sp.figure();
        assertEquals("Principal Component", f.getAxisLabel(0));
        assertEquals("Proportion of Variance", f.getAxisLabel(1));
    }
}

