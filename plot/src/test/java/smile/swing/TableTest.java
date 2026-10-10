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

import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Table}.
 */
public class TableTest {

    @Test
    public void testCreateAlternateRowColorDarkTheme() {
        // Given a dark theme background color (e.g. FlatLaf Dark #2B2D30)
        Color darkBg = new Color(43, 45, 48);

        // When computing the alternate row color
        Color altColor = Table.createAlternateRowColor(darkBg);

        // Then it should remain dark (luminance < 0.5) and subtly lighter than base
        double luminance = (0.2126 * altColor.getRed() + 0.7152 * altColor.getGreen() + 0.0722 * altColor.getBlue()) / 255.0;
        assertTrue(luminance < 0.5, "Alternate row color in dark theme must remain dark");
        assertTrue(altColor.getRed() >= darkBg.getRed(), "Red channel should be lightened or equal");
        assertTrue(altColor.getGreen() >= darkBg.getGreen(), "Green channel should be lightened or equal");
        assertTrue(altColor.getBlue() >= darkBg.getBlue(), "Blue channel should be lightened or equal");
        assertNotEquals(Color.WHITE, altColor, "Alternate row color must not be white in dark theme");
    }

    @Test
    public void testCreateAlternateRowColorLightTheme() {
        // Given a light theme background color (pure white)
        Color lightBg = Color.WHITE;

        // When computing the alternate row color
        Color altColor = Table.createAlternateRowColor(lightBg);

        // Then it should remain light (luminance >= 0.5) and subtly darker than base
        double luminance = (0.2126 * altColor.getRed() + 0.7152 * altColor.getGreen() + 0.0722 * altColor.getBlue()) / 255.0;
        assertTrue(luminance >= 0.5, "Alternate row color in light theme must remain light");
        assertTrue(altColor.getRed() < 255, "Red channel should be subtly darkened");
        assertTrue(altColor.getGreen() < 255, "Green channel should be subtly darkened");
        assertTrue(altColor.getBlue() < 255, "Blue channel should be subtly darkened");
    }

    @Test
    public void testDarkThemeRowColorsDoNotUseWhite() {
        // Given a table on a dark background
        Object[][] data = {{"Row 0"}, {"Row 1"}, {"Row 2"}, {"Row 3"}};
        String[] columns = {"Col"};
        Table table = new Table(new DefaultTableModel(data, columns));
        Color darkBg = new Color(43, 45, 48);
        Color lightFg = new Color(220, 227, 234);
        table.setBackground(darkBg);
        table.setForeground(lightFg);

        // When rendering even and odd rows
        Component evenRowComp = table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
        Color evenBg = evenRowComp.getBackground();

        Component oddRowComp = table.prepareRenderer(table.getCellRenderer(1, 0), 1, 0);
        Color oddBg = oddRowComp.getBackground();

        // Then neither even nor odd row should have white background
        assertNotEquals(Color.WHITE, evenBg, "Even row background must not be white in dark theme");
        assertNotEquals(Color.WHITE, oddBg, "Odd row background must not be white in dark theme");

        // And both row backgrounds should have dark luminance (< 0.5)
        double evenLum = (0.2126 * evenBg.getRed() + 0.7152 * evenBg.getGreen() + 0.0722 * evenBg.getBlue()) / 255.0;
        double oddLum = (0.2126 * oddBg.getRed() + 0.7152 * oddBg.getGreen() + 0.0722 * oddBg.getBlue()) / 255.0;
        assertTrue(evenLum < 0.5, "Even row background should be dark");
        assertTrue(oddLum < 0.5, "Odd row background should be dark");
        assertNotEquals(evenBg, oddBg, "Even and odd rows should have subtle contrast");
    }

    @Test
    public void testCustomUIManagerAlternateRowColorRespected() {
        // Given a custom alternate row color registered in UIManager
        Color customAlt = new Color(50, 60, 70);
        UIManager.put("Table.alternateRowColor", customAlt);

        try {
            Table table = new Table(new DefaultTableModel(new Object[][]{{"A"}, {"B"}}, new String[]{"Col"}));
            Component oddRowComp = table.prepareRenderer(table.getCellRenderer(1, 0), 1, 0);
            assertEquals(customAlt, oddRowComp.getBackground(), "Should respect Table.alternateRowColor from UIManager");
        } finally {
            UIManager.put("Table.alternateRowColor", null);
        }
    }
}
