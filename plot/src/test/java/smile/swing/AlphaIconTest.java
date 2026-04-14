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
package smile.swing;

import org.junit.jupiter.api.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AlphaIcon}.
 */
public class AlphaIconTest {

    private Icon sampleIcon;

    @BeforeEach
    public void setUp() {
        // Create a simple 16×16 red icon for testing.
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        sampleIcon = new ImageIcon(img);
    }

    @Test
    public void testDimensions() {
        AlphaIcon ai = new AlphaIcon(sampleIcon, 0.5f);
        assertEquals(sampleIcon.getIconWidth(),  ai.getIconWidth(),  "Width must match wrapped icon");
        assertEquals(sampleIcon.getIconHeight(), ai.getIconHeight(), "Height must match wrapped icon");
    }

    @Test
    public void testFullyOpaque() {
        AlphaIcon ai = new AlphaIcon(sampleIcon, 1.0f);
        assertEquals(1.0f, ai.alpha(), 1e-6f);
    }

    @Test
    public void testFullyTransparent() {
        AlphaIcon ai = new AlphaIcon(sampleIcon, 0.0f);
        assertEquals(0.0f, ai.alpha(), 1e-6f);
    }

    @Test
    public void testMidAlpha() {
        AlphaIcon ai = new AlphaIcon(sampleIcon, 0.5f);
        assertEquals(0.5f, ai.alpha(), 1e-6f);
    }

    @Test
    public void testNullIconThrows() {
        assertThrows(NullPointerException.class, () -> new AlphaIcon(null, 0.5f));
    }

    @Test
    public void testNegativeAlphaThrows() {
        assertThrows(IllegalArgumentException.class, () -> new AlphaIcon(sampleIcon, -0.1f));
    }

    @Test
    public void testAlphaGreaterThanOneThrows() {
        assertThrows(IllegalArgumentException.class, () -> new AlphaIcon(sampleIcon, 1.1f));
    }

    @Test
    public void testPaintIconDoesNotThrow() {
        AlphaIcon ai = new AlphaIcon(sampleIcon, 0.5f);
        BufferedImage canvas = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        assertDoesNotThrow(() -> ai.paintIcon(null, g, 0, 0));
        g.dispose();
    }

    @Test
    public void testRecordEquality() {
        AlphaIcon a = new AlphaIcon(sampleIcon, 0.7f);
        AlphaIcon b = new AlphaIcon(sampleIcon, 0.7f);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}

