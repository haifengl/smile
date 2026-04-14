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
 * Tests for {@link SmileUtilities}.
 */
public class SmileUtilitiesTest {

    // ── scaleImageIcon ────────────────────────────────────────────────────────

    @Test
    public void testScaleImageIconProducesCorrectSize() {
        BufferedImage src = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        ImageIcon icon = new ImageIcon(src);
        ImageIcon scaled = SmileUtilities.scaleImageIcon(icon, 24);
        assertEquals(24, scaled.getIconWidth());
        assertEquals(24, scaled.getIconHeight());
    }

    @Test
    public void testScaleImageIconScaleUpProducesCorrectSize() {
        BufferedImage src = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        ImageIcon icon = new ImageIcon(src);
        ImageIcon scaled = SmileUtilities.scaleImageIcon(icon, 128);
        assertEquals(128, scaled.getIconWidth());
        assertEquals(128, scaled.getIconHeight());
    }

    @Test
    public void testScaleImageIconPreservesPixelType() {
        BufferedImage src = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 32, 32);
        g.dispose();

        ImageIcon icon = new ImageIcon(src);
        ImageIcon scaled = SmileUtilities.scaleImageIcon(icon, 16);
        // Result image should be ARGB
        Image img = scaled.getImage();
        assertNotNull(img);
    }

    // ── getLineOfOffset ────────────────────────────────────────────────────────

    @Test
    public void testGetLineOfOffsetFirstLine() {
        JTextArea area = new JTextArea("line1\nline2\nline3");
        assertEquals(0, SmileUtilities.getLineOfOffset(area, 0));
        assertEquals(0, SmileUtilities.getLineOfOffset(area, 4));
    }

    @Test
    public void testGetLineOfOffsetSecondLine() {
        JTextArea area = new JTextArea("line1\nline2\nline3");
        // "line1\n" is 6 chars; offset 6 is start of line2
        assertEquals(1, SmileUtilities.getLineOfOffset(area, 6));
    }

    // ── getOffsetOfLine ────────────────────────────────────────────────────────

    @Test
    public void testGetOffsetOfLineFirstLine() {
        JTextArea area = new JTextArea("hello\nworld");
        assertEquals(0, SmileUtilities.getOffsetOfLine(area, 0));
    }

    @Test
    public void testGetOffsetOfLineSecondLine() {
        JTextArea area = new JTextArea("hello\nworld");
        // "hello\n" = 6 chars
        assertEquals(6, SmileUtilities.getOffsetOfLine(area, 1));
    }

    // ── getWordAt ──────────────────────────────────────────────────────────────

    @Test
    public void testGetWordAtEndOfFirstWord() throws Exception {
        JTextArea area = new JTextArea("foo.bar");
        // offset 3 → after "foo"
        assertEquals("foo", SmileUtilities.getWordAt(area, 3));
    }

    @Test
    public void testGetWordAtAfterDot() throws Exception {
        JTextArea area = new JTextArea("foo.bar");
        // offset 7 → after "foo.bar", last token after splitting on '.' is "bar"
        assertEquals("bar", SmileUtilities.getWordAt(area, 7));
    }

    @Test
    public void testGetWordAtEmptyLine() throws Exception {
        JTextArea area = new JTextArea("   ");
        assertEquals("", SmileUtilities.getWordAt(area, 2));
    }
}

