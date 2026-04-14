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
package smile.swing.table;

import org.junit.jupiter.api.*;

import java.text.NumberFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NumberCellRenderer}.
 */
public class NumberCellRendererTest {

    @Test
    public void testDefaultConstructorFormatsNumber() {
        NumberCellRenderer r = new NumberCellRenderer();
        r.setValue(1234.567);
        // Just ensure it doesn't throw and produces a non-empty string.
        assertNotNull(r.getText());
        assertFalse(r.getText().isEmpty());
    }

    @Test
    public void testPrecisionConstructor() {
        NumberCellRenderer r = new NumberCellRenderer(2);
        r.setValue(3.1);  // value with only 1 significant decimal
        String text = r.getText();
        // The constructor sets minimum fraction digits to 2, so we expect at least 2 decimal places.
        int dotIdx = text.indexOf('.');
        if (dotIdx < 0) dotIdx = text.indexOf(',');
        assertTrue(dotIdx >= 0, "Formatted number should have a decimal separator");
        String fraction = text.substring(dotIdx + 1).replaceAll("[^0-9]", "");
        assertTrue(fraction.length() >= 2, "Should have at least 2 decimal digits");
    }

    @Test
    public void testNullValueRendersEmptyString() {
        NumberCellRenderer r = new NumberCellRenderer();
        r.setValue(null);
        assertEquals("", r.getText());
    }

    @Test
    public void testIntegerPresetInstance() {
        NumberCellRenderer r = NumberCellRenderer.INTEGER;
        r.setValue(42);
        assertFalse(r.getText().isEmpty());
    }

    @Test
    public void testNumberFormatIsAccessible() {
        NumberFormat fmt = NumberFormat.getPercentInstance();
        NumberCellRenderer r = new NumberCellRenderer(fmt);
        assertSame(fmt, r.getNumberFormat());
    }

    @Test
    public void testHorizontalAlignmentIsRight() {
        NumberCellRenderer r = new NumberCellRenderer();
        assertEquals(javax.swing.SwingConstants.RIGHT, r.getHorizontalAlignment());
    }
}

