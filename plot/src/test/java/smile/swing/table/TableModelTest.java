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
import smile.data.DataFrame;
import smile.data.vector.*;
import smile.tensor.DenseMatrix;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DataFrameTableModel} and {@link MatrixTableModel}.
 */
public class TableModelTest {

    // ── DataFrameTableModel ─────────────────────────────────────────────────

    private static DataFrame buildMixedDataFrame() {
        return new DataFrame(
                new ByteVector("b",   new byte[]   {1, 2, 3}),
                new ShortVector("s",  new short[]  {10, 20, 30}),
                new IntVector("i",    new int[]    {100, 200, 300}),
                new LongVector("l",   new long[]   {1000L, 2000L, 3000L}),
                new FloatVector("f",  new float[]  {1.1f, 2.2f, 3.3f}),
                new DoubleVector("d", new double[] {1.11, 2.22, 3.33}),
                new BooleanVector("bo", new boolean[]{true, false, true}),
                new StringVector("st", new String[] {"a", "b", "c"}));
    }

    @Test
    public void testDataFrameGetColumnClassByte() {
        DataFrameTableModel m = new DataFrameTableModel(buildMixedDataFrame());
        assertEquals(Byte.class, m.getColumnClass(0));
    }

    @Test
    public void testDataFrameGetColumnClassShort() {
        DataFrameTableModel m = new DataFrameTableModel(buildMixedDataFrame());
        assertEquals(Short.class, m.getColumnClass(1));
    }

    @Test
    public void testDataFrameGetColumnClassInt() {
        DataFrameTableModel m = new DataFrameTableModel(buildMixedDataFrame());
        assertEquals(Integer.class, m.getColumnClass(2));
    }

    @Test
    public void testDataFrameGetColumnClassLong() {
        DataFrameTableModel m = new DataFrameTableModel(buildMixedDataFrame());
        assertEquals(Long.class, m.getColumnClass(3));
    }

    @Test
    public void testDataFrameGetColumnClassFloat() {
        DataFrameTableModel m = new DataFrameTableModel(buildMixedDataFrame());
        assertEquals(Float.class, m.getColumnClass(4));
    }

    @Test
    public void testDataFrameGetColumnClassDouble() {
        DataFrameTableModel m = new DataFrameTableModel(buildMixedDataFrame());
        assertEquals(Double.class, m.getColumnClass(5));
    }

    @Test
    public void testDataFrameGetColumnClassBoolean() {
        DataFrameTableModel m = new DataFrameTableModel(buildMixedDataFrame());
        assertEquals(Boolean.class, m.getColumnClass(6));
    }

    @Test
    public void testDataFrameGetColumnClassString() {
        DataFrameTableModel m = new DataFrameTableModel(buildMixedDataFrame());
        assertEquals(String.class, m.getColumnClass(7));
    }

    @Test
    public void testDataFrameRowAndColumnCount() {
        DataFrame df = buildMixedDataFrame();
        DataFrameTableModel m = new DataFrameTableModel(df);
        assertEquals(df.nrow(), m.getRealRowCount());
        assertEquals(df.ncol(), m.getColumnCount());
    }

    @Test
    public void testDataFrameGetValueAtRealRow() {
        DataFrame df = buildMixedDataFrame();
        DataFrameTableModel m = new DataFrameTableModel(df);
        // The 'i' column (index 2) for row 1 should be 200.
        assertEquals(200, m.getValueAtRealRow(1, 2));
    }

    @Test
    public void testDataFrameColumnNames() {
        DataFrameTableModel m = new DataFrameTableModel(buildMixedDataFrame());
        assertEquals("b",  m.getColumnName(0));
        assertEquals("st", m.getColumnName(7));
    }

    // ── MatrixTableModel ────────────────────────────────────────────────────

    @Test
    public void testMatrixRowAndColumnCount() {
        DenseMatrix matrix = DenseMatrix.of(new double[][]{{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}});
        MatrixTableModel m = new MatrixTableModel(matrix);
        assertEquals(2, m.getRealRowCount());
        assertEquals(3, m.getColumnCount());
    }

    @Test
    public void testMatrixGetColumnClassIsDouble() {
        DenseMatrix matrix = DenseMatrix.of(new double[][]{{1.0}});
        MatrixTableModel m = new MatrixTableModel(matrix);
        assertEquals(Double.class, m.getColumnClass(0));
    }

    @Test
    public void testMatrixDefaultColumnName() {
        // Without named columns the name should be "V1", "V2", ...
        DenseMatrix matrix = DenseMatrix.of(new double[][]{{1.0, 2.0, 3.0}});
        MatrixTableModel m = new MatrixTableModel(matrix);
        assertEquals("V1", m.getColumnName(0));
        assertEquals("V2", m.getColumnName(1));
        assertEquals("V3", m.getColumnName(2));
    }

    @Test
    public void testMatrixNamedColumns() {
        DenseMatrix dm = DenseMatrix.of(new double[][]{{1.0, 2.0}});
        dm.withColNames(new String[]{"Alpha", "Beta"});
        MatrixTableModel m = new MatrixTableModel(dm);
        assertEquals("Alpha", m.getColumnName(0));
        assertEquals("Beta",  m.getColumnName(1));
    }

    @Test
    public void testMatrixGetValueAtRealRow() {
        DenseMatrix matrix = DenseMatrix.of(new double[][]{{10.0, 20.0}, {30.0, 40.0}});
        MatrixTableModel m = new MatrixTableModel(matrix);
        assertEquals(30.0, (double) m.getValueAtRealRow(1, 0), 1e-9);
        assertEquals(40.0, (double) m.getValueAtRealRow(1, 1), 1e-9);
    }
}
