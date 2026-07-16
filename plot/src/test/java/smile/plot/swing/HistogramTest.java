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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Histogram}.
 */
public class HistogramTest {

    // ── int[] overloads ──────────────────────────────────────────────────────

    @Test
    public void testIntDefaultBinsReturnsBarsPlot() {
        int[] data = {1, 2, 2, 3, 3, 3, 4, 4, 5};
        BarPlot plot = Histogram.of(data);
        assertNotNull(plot);
    }

    @Test
    public void testIntExplicitBinCount() {
        int[] data = new int[100];
        for (int i = 0; i < 100; i++) data[i] = i;
        BarPlot plot = Histogram.of(data, 10, true);
        assertNotNull(plot);
    }

    @Test
    public void testIntProbabilityScaleSumsToOne() {
        int[] data = new int[1000];
        for (int i = 0; i < 1000; i++) data[i] = i % 10;
        BarPlot plot = Histogram.of(data, 10, true);
        // In probability mode each bar height = count/n. Sum of heights = 1.
        double sum = 0.0;
        for (double[] bin : plot.bars[0].data) {
            sum += bin[1];
        }
        assertEquals(1.0, sum, 0.01);
    }

    @Test
    public void testIntFrequencyScaleCountsTotal() {
        int[] data = {1, 1, 2, 2, 2, 3};
        BarPlot plot = Histogram.of(data, 3, false);
        // All bins live inside one Bar's data array
        double total = 0.0;
        for (double[] bin : plot.bars[0].data) {
            total += bin[1];
        }
        assertEquals(data.length, (int) total);
    }

    @Test
    public void testIntBreaksOverload() {
        int[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        double[] breaks = {0.5, 3.5, 6.5, 10.5};
        BarPlot plot = Histogram.of(data, breaks, false);
        // One Bar object with 3 rows (one per bin)
        assertEquals(3, plot.bars[0].data.length);
    }

    @Test
    public void testIntBreaksTooFewThrows() {
        int[] data = {1, 2, 3};
        // breaks.length - 1 = 0 < 1 → should throw
        assertThrows(IllegalArgumentException.class,
                () -> Histogram.of(data, new double[]{1.0}, false));
    }

    // ── double[] overloads ────────────────────────────────────────────────────

    @Test
    public void testDoubleDefaultBins() {
        double[] data = {1.0, 2.0, 2.5, 3.0, 3.5, 4.0};
        BarPlot plot = Histogram.of(data);
        assertNotNull(plot);
    }

    @Test
    public void testDoubleExplicitBinCount() {
        double[] data = new double[200];
        for (int i = 0; i < 200; i++) data[i] = i * 0.1;
        BarPlot plot = Histogram.of(data, 20, true);
        assertNotNull(plot);
    }

    @Test
    public void testDoubleProbabilityScaleSumsToOne() {
        double[] data = new double[1000];
        for (int i = 0; i < 1000; i++) data[i] = i * 0.001;
        BarPlot plot = Histogram.of(data, 10, true);
        // Sum of bin heights = 1.0 in probability mode
        double sum = 0.0;
        for (double[] bin : plot.bars[0].data) {
            sum += bin[1];
        }
        assertEquals(1.0, sum, 0.01);
    }

    @Test
    public void testDoubleBreaksOverload() {
        double[] data = {0.1, 0.5, 1.2, 2.3, 3.7, 4.9};
        double[] breaks = {0.0, 2.0, 4.0, 6.0};
        BarPlot plot = Histogram.of(data, breaks, false);
        // One Bar with 3 rows (one per bin)
        assertEquals(3, plot.bars[0].data.length);
    }

    @Test
    public void testDoubleBreaksTooFewThrows() {
        double[] data = {1.0, 2.0};
        assertThrows(IllegalArgumentException.class,
                () -> Histogram.of(data, new double[]{1.0}, false));
    }

    // ── figure / rendering ────────────────────────────────────────────────────

    @Test
    public void testFigureRendersWithoutError() {
        double[] data = new double[500];
        for (int i = 0; i < 500; i++) data[i] = Math.sin(i * 0.1);
        BarPlot plot = Histogram.of(data, 20, true);
        Figure figure = plot.figure();
        assertDoesNotThrow(() -> figure.toBufferedImage(400, 300));
    }

    @Test
    public void testCustomColorApplied() {
        int[] data = {1, 2, 3};
        BarPlot plot = Histogram.of(data, 3, false, Color.RED);
        assertEquals(Color.RED, plot.bars[0].color);
    }
}

