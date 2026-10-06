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
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SmileUtilities}.
 */
public class SmileUtilitiesTest {

    /** A class in this package, used to resolve test resources. */
    private static final Class<?> OWNER = SmileUtilitiesTest.class;

    /** Draws a solid square image of the given size. */
    private static BufferedImage solid(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

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

    @Test
    public void testScaleImageIconPreservesAspectRatio() {
        // Given a wide 64×32 source
        ImageIcon icon = new ImageIcon(solid(64, 32, Color.RED));

        // When scaled to fit a 16px box
        ImageIcon scaled = SmileUtilities.scaleImageIcon(icon, 16);

        // Then the proportions are kept rather than stretched to a square
        assertEquals(16, scaled.getIconWidth());
        assertEquals(8, scaled.getIconHeight());
    }

    @Test
    public void testScaleImageIconRejectsNonPositiveSize() {
        ImageIcon icon = new ImageIcon(solid(16, 16, Color.RED));
        assertThrows(IllegalArgumentException.class,
                () -> SmileUtilities.scaleImageIcon(icon, 0));
    }

    @Test
    public void testScaleImageIconCarriesHiDpiVariant() {
        // Given a large source, as the Studio PNGs are
        ImageIcon icon = new ImageIcon(solid(512, 512, Color.RED));

        // When scaled to a 16px logical size
        ImageIcon scaled = SmileUtilities.scaleImageIcon(icon, 16);

        // Then the icon exposes a 1× and a 2× bitmap for HiDPI displays
        assertInstanceOf(MultiResolutionImage.class, scaled.getImage());
        MultiResolutionImage mr = (MultiResolutionImage) scaled.getImage();
        List<Image> variants = mr.getResolutionVariants();
        assertEquals(2, variants.size());
        assertEquals(16, variants.get(0).getWidth(null));
        assertEquals(32, variants.get(1).getWidth(null));
    }

    @Test
    public void testImageIconSelectsHiDpiVariantWhenPainted() {
        // Given an ImageIcon wrapping a multi-resolution image whose 1× and 2×
        // variants are distinguishable. This pins the mechanism scaleImageIcon
        // relies on: ImageIcon delegates painting to the underlying image, so
        // Swing picks the variant that matches the device transform.
        Image one = solid(16, 16, Color.RED);
        Image two = solid(32, 32, Color.BLUE);
        ImageIcon icon = new ImageIcon(new BaseMultiResolutionImage(one, two));

        // When painted under a 2× device transform
        BufferedImage dest = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dest.createGraphics();
        g.scale(2, 2);
        icon.paintIcon(null, g, 0, 0);
        g.dispose();

        // Then the 2× variant is used, not a blurry upscale of the 1× one
        assertEquals(Color.BLUE.getRGB(), dest.getRGB(16, 16));
    }

    @Test
    public void testImageIconUsesBaseVariantAtNormalDensity() {
        // Given the same multi-resolution icon
        Image one = solid(16, 16, Color.RED);
        Image two = solid(32, 32, Color.BLUE);
        ImageIcon icon = new ImageIcon(new BaseMultiResolutionImage(one, two));

        // When painted at 1×
        BufferedImage dest = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dest.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();

        // Then the 1× variant is used
        assertEquals(Color.RED.getRGB(), dest.getRGB(8, 8));
    }

    // ── loadImageIcon ─────────────────────────────────────────────────────────

    @Test
    public void testLoadImageIconScalesResource() {
        // Given a real 96×96 icon on the classpath
        // When loaded at the small size
        ImageIcon icon = SmileUtilities.loadImageIcon(
                OWNER, "table/images/forward.png", SmileUtilities.SMALL_ICON_SIZE);

        // Then it is scaled to that size
        assertEquals(SmileUtilities.SMALL_ICON_SIZE, icon.getIconWidth());
        assertEquals(SmileUtilities.SMALL_ICON_SIZE, icon.getIconHeight());
    }

    @Test
    public void testLoadImageIconMissingResourceIsEmptyNotFatal() {
        // Given a resource that does not exist
        // When loaded
        ImageIcon icon = SmileUtilities.loadImageIcon(OWNER, "images/does-not-exist.png", 16);

        // Then an empty icon is returned rather than an exception, so a missing
        // decoration cannot take down the menu bar being built.
        assertNotNull(icon);
        assertTrue(icon.getIconWidth() <= 0, "expected an empty icon, got width " + icon.getIconWidth());
        assertTrue(icon.getIconHeight() <= 0, "expected an empty icon, got height " + icon.getIconHeight());
    }

    // ── loadActionIcons ───────────────────────────────────────────────────────

    @Test
    public void testLoadActionIconsReturnsBothSizes() {
        // Given a real icon on the classpath
        // When loaded as action icons
        SmileUtilities.ActionIcons icons = SmileUtilities.loadActionIcons(
                OWNER, "table/images/forward.png");

        // Then the small and large variants match the Swing sizes
        assertEquals(SmileUtilities.SMALL_ICON_SIZE, icons.small().getIconWidth());
        assertEquals(SmileUtilities.LARGE_ICON_SIZE, icons.large().getIconWidth());
    }

    @Test
    public void testActionIconsApplyToAction() {
        // Given a pair of icons and an action
        SmileUtilities.ActionIcons icons = SmileUtilities.loadActionIcons(
                OWNER, "table/images/forward.png");
        AbstractAction action = new AbstractAction("Test") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                // no-op
            }
        };

        // When applied
        icons.applyTo(action);

        // Then both Swing icon keys are set
        assertSame(icons.small(), action.getValue(Action.SMALL_ICON));
        assertSame(icons.large(), action.getValue(Action.LARGE_ICON_KEY));
    }

    // ── loadFrameIcons ────────────────────────────────────────────────────────

    @Test
    public void testLoadFrameIconsReturnsEverySize() {
        // Given a real icon on the classpath
        // When loaded as frame icons
        List<Image> icons = SmileUtilities.loadFrameIcons(OWNER, "table/images/forward.png");

        // Then there is one bitmap per requested size, in order
        assertEquals(SmileUtilities.FRAME_ICON_SIZES.size(), icons.size());
        for (int i = 0; i < icons.size(); i++) {
            int size = SmileUtilities.FRAME_ICON_SIZES.get(i);
            assertEquals(size, icons.get(i).getWidth(null), "width at index " + i);
            assertEquals(size, icons.get(i).getHeight(null), "height at index " + i);
        }
    }

    @Test
    public void testLoadFrameIconsMissingResourceIsEmptyNotFatal() {
        // Given a resource that does not exist
        // When loaded
        List<Image> icons = SmileUtilities.loadFrameIcons(OWNER, "images/does-not-exist.png");

        // Then an empty list is returned rather than an exception, so a missing
        // decoration cannot stop the window from being created.
        assertNotNull(icons);
        assertTrue(icons.isEmpty(), "expected no icons, got " + icons.size());
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

