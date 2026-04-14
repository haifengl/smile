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
import java.awt.Font;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Figure}.
 */
public class FigureTest {

    // ── construction ────────────────────────────────────────────────────────

    @Test
    public void testDefaultMargin() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        assertEquals(0.15, f.getMargin(), 1e-9);
    }

    @Test
    public void testSetMarginValidValue() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        f.setMargin(0.2);
        assertEquals(0.2, f.getMargin(), 1e-9);
    }

    @Test
    public void testSetMarginZeroRejected() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        assertThrows(IllegalArgumentException.class, () -> f.setMargin(0.0));
    }

    @Test
    public void testSetMarginNegativeRejected() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        assertThrows(IllegalArgumentException.class, () -> f.setMargin(-0.01));
    }

    @Test
    public void testSetMarginAtUpperBoundRejected() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        assertThrows(IllegalArgumentException.class, () -> f.setMargin(0.3));
    }

    @Test
    public void testSetMarginJustBelowUpperBound() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        assertDoesNotThrow(() -> f.setMargin(0.29));
    }

    // ── title ───────────────────────────────────────────────────────────────

    @Test
    public void testDefaultTitleIsNull() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        assertNull(f.getTitle());
    }

    @Test
    public void testSetTitle() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        f.setTitle("My Plot");
        assertEquals("My Plot", f.getTitle());
    }

    @Test
    public void testSetTitleFont() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        Font font = new Font("Serif", Font.ITALIC, 20);
        f.setTitleFont(font);
        assertEquals(font, f.getTitleFont());
    }

    @Test
    public void testSetTitleColor() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        f.setTitleColor(Color.RED);
        assertEquals(Color.RED, f.getTitleColor());
    }

    // ── legend visibility ────────────────────────────────────────────────────

    @Test
    public void testLegendVisibleByDefault() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        assertTrue(f.isLegendVisible());
    }

    @Test
    public void testSetLegendVisible() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        f.setLegendVisible(false);
        assertFalse(f.isLegendVisible());
        f.setLegendVisible(true);
        assertTrue(f.isLegendVisible());
    }

    // ── axis labels ──────────────────────────────────────────────────────────

    @Test
    public void testSetAxisLabels() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        f.setAxisLabels("X Axis", "Y Axis");
        assertArrayEquals(new String[]{"X Axis", "Y Axis"}, f.getAxisLabels());
    }

    @Test
    public void testSetAxisLabel() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        f.setAxisLabel(0, "Horizontal");
        assertEquals("Horizontal", f.getAxisLabel(0));
    }

    // ── shapes ───────────────────────────────────────────────────────────────

    @Test
    public void testAddAndRemoveShape() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        var pts = new Point(new double[][]{{0.5, 0.5}}, 'o', Color.RED);
        f.add(pts);
        assertEquals(1, f.getShapes().size());
        f.remove(pts);
        assertEquals(0, f.getShapes().size());
    }

    @Test
    public void testClearShapes() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        f.add(new Point(new double[][]{{0.1, 0.1}}, '.', Color.BLUE));
        f.add(new Point(new double[][]{{0.9, 0.9}}, '.', Color.GREEN));
        f.clear();
        assertTrue(f.getShapes().isEmpty());
    }

    // ── toBufferedImage (headless rendering) ─────────────────────────────────

    @Test
    public void testToBufferedImageProducesCorrectDimensions() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        BufferedImage img = f.toBufferedImage(400, 300);
        assertEquals(400, img.getWidth());
        assertEquals(300, img.getHeight());
    }

    @Test
    public void testToBufferedImageIsNotAllWhite() {
        // A figure with axis lines should not be an entirely white image.
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        f.setTitle("Test");
        BufferedImage img = f.toBufferedImage(400, 300);
        boolean hasNonWhitePixel = false;
        outer:
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                if (img.getRGB(x, y) != 0xFFFFFFFF) {
                    hasNonWhitePixel = true;
                    break outer;
                }
            }
        }
        assertTrue(hasNonWhitePixel, "Rendered figure should contain non-white pixels");
    }

    @Test
    public void testToBufferedImageWithScatterPlot() {
        double[][] pts = {{0.2, 0.3}, {0.5, 0.7}, {0.8, 0.4}};
        ScatterPlot sp = ScatterPlot.of(pts, 'o');
        Figure f = sp.figure();
        assertDoesNotThrow(() -> f.toBufferedImage(600, 400));
    }

    // ── bounds ───────────────────────────────────────────────────────────────

    @Test
    public void testLowerAndUpperBoundsAreAccessible() {
        Figure f = new Figure(new double[]{1.0, 2.0}, new double[]{5.0, 6.0});
        double[] lo = f.getLowerBounds();
        double[] hi = f.getUpperBounds();
        assertNotNull(lo);
        assertNotNull(hi);
        assertTrue(hi[0] >= 5.0);
        assertTrue(hi[1] >= 6.0);
    }

    // ── property change ──────────────────────────────────────────────────────

    @Test
    public void testPropertyChangeListenerFiredOnTitleChange() throws Exception {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        var events = new java.util.ArrayList<String>();
        f.addPropertyChangeListener(e -> {
            synchronized (events) { events.add(e.getPropertyName()); }
        });
        f.setTitle("New Title");
        // SwingPropertyChangeSupport may fire on EDT; flush it.
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        synchronized (events) {
            assertTrue(events.contains("title"), "Expected 'title' event, got: " + events);
        }
    }

    @Test
    public void testPropertyChangeListenerFiredOnMarginChange() throws Exception {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        var events = new java.util.ArrayList<String>();
        f.addPropertyChangeListener(e -> {
            synchronized (events) { events.add(e.getPropertyName()); }
        });
        f.setMargin(0.2);
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
        synchronized (events) {
            assertTrue(events.contains("margin"), "Expected 'margin' event, got: " + events);
        }
    }

    @Test
    public void testRemovePropertyChangeListener() {
        Figure f = new Figure(new double[]{0, 0}, new double[]{1, 1});
        var events = new java.util.ArrayList<String>();
        java.beans.PropertyChangeListener listener =
                e -> events.add(e.getPropertyName());
        f.addPropertyChangeListener(listener);
        f.removePropertyChangeListener(listener);
        f.setTitle("After Remove");
        assertTrue(events.isEmpty());
    }
}

