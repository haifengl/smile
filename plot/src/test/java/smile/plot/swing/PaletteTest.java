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
 * Tests for {@link Palette}.
 */
public class PaletteTest {

    // ── Palette.web() ─────────────────────────────────────────────────────────

    @Test
    public void testWebNamedColorRed() {
        Color c = Palette.web("red");
        assertEquals(255, c.getRed());
        assertEquals(0,   c.getGreen());
        assertEquals(0,   c.getBlue());
    }

    @Test
    public void testWebNamedColorBlue() {
        Color c = Palette.web("blue");
        assertEquals(0,   c.getRed());
        assertEquals(0,   c.getGreen());
        assertEquals(255, c.getBlue());
    }

    @Test
    public void testWebHexSixDigit() {
        Color c = Palette.web("#ff8000");
        assertEquals(255, c.getRed());
        assertEquals(128, c.getGreen());
        assertEquals(0,   c.getBlue());
    }

    @Test
    public void testWebHexSixDigitCaseInsensitive() {
        Color upper = Palette.web("#FF8000");
        Color lower = Palette.web("#ff8000");
        assertEquals(upper, lower);
    }

    @Test
    public void testWebHexThreeDigit() {
        // #f80 → expand to #ff8800
        Color c = Palette.web("#f80");
        // Each nibble is doubled: f→15, 8→8, 0→0 (scaled to [0,1] per nibble/15)
        assertEquals(Math.round(15 / 15.0f * 255), c.getRed(),   2);
        assertEquals(Math.round(8 / 15.0f  * 255), c.getGreen(), 2);
        assertEquals(Math.round(0 / 15.0f  * 255), c.getBlue(),  2);
    }

    @Test
    public void testWebRgbFunction() {
        Color c = Palette.web("rgb(100, 150, 200)");
        assertEquals(100, c.getRed(),   1);
        assertEquals(150, c.getGreen(), 1);
        assertEquals(200, c.getBlue(),  1);
    }

    @Test
    public void testWebRgbPercent() {
        Color c = Palette.web("rgb(100%, 0%, 50%)");
        assertEquals(255, c.getRed(),   1);
        assertEquals(0,   c.getGreen(), 1);
        assertEquals(127, c.getBlue(),  2);
    }

    @Test
    public void testWebRgbaFunction() {
        Color c = Palette.web("rgba(255, 0, 0, 0.5)");
        assertEquals(255, c.getRed());
        assertEquals(0,   c.getGreen());
        assertEquals(0,   c.getBlue());
        // alpha ≈ 0.5 * 255 ≈ 127
        assertEquals(127, c.getAlpha(), 2);
    }

    @Test
    public void testWebHslFunction() {
        // Palette passes HSL values directly to hsb(h, s, l, a) where l is
        // treated as HSB brightness (not HSL lightness).
        // hsl(0°, 100%, 50%) → hsb(0, 1.0, 0.5, 1.0) → RGB(128, 0, 0).
        Color c = Palette.web("hsl(0, 100%, 50%)");
        assertEquals(128, c.getRed(),   2);
        assertEquals(0,   c.getGreen(), 1);
        assertEquals(0,   c.getBlue(),  1);
    }

    @Test
    public void testWebWithOpacity() {
        Color c = Palette.web("#ff0000", 0.5f);
        assertEquals(255, c.getRed());
        assertEquals(0,   c.getGreen());
        assertEquals(0,   c.getBlue());
        assertEquals(127, c.getAlpha(), 2);
    }

    @Test
    public void testWebEmptyStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> Palette.web(""));
    }

    @Test
    public void testWebInvalidNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> Palette.web("notacolor"));
    }

    @Test
    public void testWebNegativeOpacityThrows() {
        assertThrows(IllegalArgumentException.class, () -> Palette.web("#ff0000", -0.1f));
    }

    @Test
    public void testWebOpacityAboveOneThrows() {
        assertThrows(IllegalArgumentException.class, () -> Palette.web("#ff0000", 1.1f));
    }

    @Test
    public void testWeb0xPrefixHex() {
        Color c = Palette.web("0xff0000");
        assertEquals(255, c.getRed());
        assertEquals(0,   c.getGreen());
        assertEquals(0,   c.getBlue());
    }

    // ── Palette color arrays ─────────────────────────────────────────────────

    @Test
    public void testJetPaletteLength() {
        Color[] jet = Palette.jet(256);
        assertEquals(256, jet.length);
    }

    @Test
    public void testJetPaletteNoNullEntries() {
        Color[] jet = Palette.jet(64);
        for (Color c : jet) {
            assertNotNull(c);
        }
    }

    @Test
    public void testRainbowPaletteLength() {
        Color[] rainbow = Palette.rainbow(100);
        assertEquals(100, rainbow.length);
    }

    @Test
    public void testHeatPaletteLength() {
        Color[] heat = Palette.heat(50);
        assertEquals(50, heat.length);
    }

    @Test
    public void testTerrainPaletteLength() {
        Color[] terrain = Palette.terrain(128);
        assertEquals(128, terrain.length);
    }

    // ── named color constants ────────────────────────────────────────────────

    @Test
    public void testNamedColorConstantsNotNull() {
        assertNotNull(Palette.RED);
        assertNotNull(Palette.GREEN);
        assertNotNull(Palette.BLUE);
        assertNotNull(Palette.YELLOW);
        assertNotNull(Palette.CYAN);
        assertNotNull(Palette.MAGENTA);
        assertNotNull(Palette.WHITE);
        assertNotNull(Palette.BLACK);
        assertNotNull(Palette.DARK_GRAY);
        assertNotNull(Palette.LIGHT_GRAY);
    }
}

