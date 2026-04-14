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

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for array cell renderers:
 * {@link ByteArrayCellRenderer}, {@link DoubleArrayCellRenderer},
 * {@link FloatArrayCellRenderer}, {@link IntegerArrayCellRenderer},
 * {@link LongArrayCellRenderer}, {@link ShortArrayCellRenderer}.
 */
public class ArrayCellRendererTest {

    // ── ByteArrayCellRenderer ────────────────────────────────────────────────

    @Test
    public void testByteArrayNonNull() {
        ByteArrayCellRenderer r = new ByteArrayCellRenderer();
        byte[] value = {1, 2, 3};
        r.setValue(value);
        assertEquals(Arrays.toString(value), r.getText());
    }

    @Test
    public void testByteArrayNull() {
        ByteArrayCellRenderer r = new ByteArrayCellRenderer();
        r.setValue(null);
        assertEquals("[]", r.getText());
    }

    // ── DoubleArrayCellRenderer ──────────────────────────────────────────────

    @Test
    public void testDoubleArrayNonNull() {
        DoubleArrayCellRenderer r = new DoubleArrayCellRenderer();
        double[] value = {1.1, 2.2, Double.NaN};
        r.setValue(value);
        assertEquals(Arrays.toString(value), r.getText());
    }

    @Test
    public void testDoubleArrayNull() {
        DoubleArrayCellRenderer r = new DoubleArrayCellRenderer();
        r.setValue(null);
        assertEquals("[]", r.getText());
    }

    // ── FloatArrayCellRenderer ───────────────────────────────────────────────

    @Test
    public void testFloatArrayNonNull() {
        FloatArrayCellRenderer r = new FloatArrayCellRenderer();
        float[] value = {1.1f, 2.2f};
        r.setValue(value);
        assertEquals(Arrays.toString(value), r.getText());
    }

    @Test
    public void testFloatArrayNull() {
        FloatArrayCellRenderer r = new FloatArrayCellRenderer();
        r.setValue(null);
        assertEquals("[]", r.getText());
    }

    // ── IntegerArrayCellRenderer ─────────────────────────────────────────────

    @Test
    public void testIntegerArrayNonNull() {
        IntegerArrayCellRenderer r = new IntegerArrayCellRenderer();
        int[] value = {10, 20, 30};
        r.setValue(value);
        assertEquals(Arrays.toString(value), r.getText());
    }

    @Test
    public void testIntegerArrayNull() {
        IntegerArrayCellRenderer r = new IntegerArrayCellRenderer();
        r.setValue(null);
        assertEquals("[]", r.getText());
    }

    // ── LongArrayCellRenderer ────────────────────────────────────────────────

    @Test
    public void testLongArrayNonNull() {
        LongArrayCellRenderer r = new LongArrayCellRenderer();
        long[] value = {100L, 200L};
        r.setValue(value);
        assertEquals(Arrays.toString(value), r.getText());
    }

    @Test
    public void testLongArrayNull() {
        LongArrayCellRenderer r = new LongArrayCellRenderer();
        r.setValue(null);
        assertEquals("[]", r.getText());
    }

    // ── ShortArrayCellRenderer ───────────────────────────────────────────────

    @Test
    public void testShortArrayNonNull() {
        ShortArrayCellRenderer r = new ShortArrayCellRenderer();
        short[] value = {5, 10, 15};
        r.setValue(value);
        assertEquals(Arrays.toString(value), r.getText());
    }

    @Test
    public void testShortArrayNull() {
        ShortArrayCellRenderer r = new ShortArrayCellRenderer();
        r.setValue(null);
        assertEquals("[]", r.getText());
    }
}

