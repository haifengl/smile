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
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import smile.data.vector.StringVector;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PageTableModel} via its concrete subclass
 * {@link DataFrameTableModel}.
 */
public class PageTableModelTest {

    /** A small DataFrame with 25 rows used across all tests. */
    private DataFrame df25;
    /** A DataFrame with exactly 1 row. */
    private DataFrame df1;

    @BeforeEach
    public void setUp() {
        df25 = buildDataFrame(25);
        df1  = buildDataFrame(1);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static DataFrame buildDataFrame(int rows) {
        int[]    ids  = new int[rows];
        double[] vals = new double[rows];
        String[] lbls = new String[rows];
        for (int i = 0; i < rows; i++) {
            ids[i]  = i + 1;
            vals[i] = (i + 1) * 1.5;
            lbls[i] = "row" + i;
        }
        return new DataFrame(
                new IntVector("id",    ids),
                new DoubleVector("value", vals),
                new StringVector("label", lbls));
    }

    // ── PageTableModel – row-count / page-count ─────────────────────────────

    @Test
    public void testPageCountSinglePage() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(100);   // one page covers all 25 rows
        assertEquals(1, m.getPageCount());
    }

    @Test
    public void testPageCountExactMultiple() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(5);
        assertEquals(5, m.getPageCount());
    }

    @Test
    public void testPageCountNonMultiple() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(10);     // 25 rows → 3 pages (10, 10, 5)
        assertEquals(3, m.getPageCount());
    }

    @Test
    public void testRowCountFirstPage() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(10);
        m.setPage(0);
        assertEquals(10, m.getRowCount());
    }

    @Test
    public void testRowCountLastPage() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(10);
        m.setPage(2);           // last page has 5 rows
        assertEquals(5, m.getRowCount());
    }

    @Test
    public void testRowCountSingleRowDataFrame() {
        DataFrameTableModel m = new DataFrameTableModel(df1);
        assertEquals(1, m.getRowCount());
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    @Test
    public void testPageDownMovesForward() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(10);
        assertTrue(m.pageDown());
        assertEquals(1, m.getPage());
    }

    @Test
    public void testPageDownAtLastPageReturnsFalse() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(10);
        m.setPage(2); // last page
        assertFalse(m.pageDown());
        assertEquals(2, m.getPage());
    }

    @Test
    public void testPageUpMovesBack() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(10);
        m.setPage(2);
        assertTrue(m.pageUp());
        assertEquals(1, m.getPage());
    }

    @Test
    public void testPageUpAtFirstPageReturnsFalse() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        assertFalse(m.pageUp());
        assertEquals(0, m.getPage());
    }

    @Test
    public void testSetPageBounds() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(10);
        assertFalse(m.setPage(-1),             "Negative page must fail");
        assertFalse(m.setPage(m.getPageCount()), "Page >= pageCount must fail");
        assertTrue(m.setPage(2));
    }

    // ── Page-size changes ────────────────────────────────────────────────────

    @Test
    public void testSetPageSizeRejectsZero() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        assertThrows(IllegalArgumentException.class, () -> m.setPageSize(0));
    }

    @Test
    public void testSetPageSizeRejectsNegative() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        assertThrows(IllegalArgumentException.class, () -> m.setPageSize(-5));
    }

    @Test
    public void testSetPageSizeKeepsPageInRange() {
        // Start on page 4 of a 5-page setup, then enlarge pages so there are fewer.
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(5);
        m.setPage(4);          // valid: page 4 of 5 pages
        m.setPageSize(25);     // now only 1 page exists
        assertTrue(m.getPage() < m.getPageCount(),
                "Page must remain within [0, pageCount) after resize");
    }

    @Test
    public void testSetPageSizeNoOpWhenSame() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(10);
        m.setPage(1);
        m.setPageSize(10);     // no-op
        assertEquals(1, m.getPage(), "Page must not change on no-op setPageSize");
    }

    // ── getRealRow ───────────────────────────────────────────────────────────

    @Test
    public void testGetRealRowFirstPage() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(10);
        assertEquals(0, m.getRealRow(0));
        assertEquals(9, m.getRealRow(9));
    }

    @Test
    public void testGetRealRowSecondPage() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(10);
        m.setPage(1);
        assertEquals(10, m.getRealRow(0));
        assertEquals(19, m.getRealRow(9));
    }

    // ── getValueAt / getValueAtRealRow ───────────────────────────────────────

    @Test
    public void testGetValueAtMatchesDataFrame() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        m.setPageSize(10);
        // row 0, col 0 on page 0 → real row 0 → id = 1
        assertEquals(1, m.getValueAt(0, 0));
        // row 0, col 0 on page 1 → real row 10 → id = 11
        m.setPage(1);
        assertEquals(11, m.getValueAt(0, 0));
    }

    // ── Column metadata ──────────────────────────────────────────────────────

    @Test
    public void testColumnCount() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        assertEquals(3, m.getColumnCount());
    }

    @Test
    public void testColumnNames() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        assertEquals("id",    m.getColumnName(0));
        assertEquals("value", m.getColumnName(1));
        assertEquals("label", m.getColumnName(2));
    }

    @Test
    public void testGetColumnClassInt() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        assertEquals(Integer.class, m.getColumnClass(0));
    }

    @Test
    public void testGetColumnClassDouble() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        assertEquals(Double.class, m.getColumnClass(1));
    }

    @Test
    public void testGetColumnClassString() {
        DataFrameTableModel m = new DataFrameTableModel(df25);
        assertEquals(String.class, m.getColumnClass(2));
    }
}

