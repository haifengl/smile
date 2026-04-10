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
package smile.data;

import smile.data.vector.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for new DataFrame methods: rename, sort, sample, RowIndex improvements.
 *
 * @author Haifeng Li
 */
public class DataFrameNewMethodsTest {

    DataFrame df;

    public DataFrameNewMethodsTest() {
        df = new DataFrame(
                new StringVector("name",   new String[]{"Charlie", "Alice", "Bob", "Dave"}),
                new IntVector   ("age",    new int[]   {30,         25,      35,    28}),
                new DoubleVector("salary", new double[]{80000.0,    60000.0, 90000.0, 70000.0})
        );
    }

    @BeforeAll
    public static void setUpClass() {}

    @AfterAll
    public static void tearDownClass() {}

    @BeforeEach
    public void setUp() {}

    @AfterEach
    public void tearDown() {}

    // -----------------------------------------------------------------------
    // rename
    // -----------------------------------------------------------------------

    @Test
    public void testRename() {
        System.out.println("rename");
        DataFrame out = df.rename("age", "years");
        assertEquals("years", out.schema().field(1).name());
        assertEquals("years", out.column(1).name());
        // original column data is preserved
        assertEquals(30, out.getInt(0, 1));
        assertEquals(25, out.getInt(1, 1));
    }

    @Test
    public void testRenameDoesNotAffectOtherColumns() {
        System.out.println("rename other columns unchanged");
        df.rename("salary", "pay");
        assertEquals("name",   df.names()[0]);
        assertEquals("age",    df.names()[1]);
        assertEquals("pay",    df.names()[2]);
    }

    // -----------------------------------------------------------------------
    // sort
    // -----------------------------------------------------------------------

    @Test
    public void testSortAscending() {
        System.out.println("sort ascending");
        DataFrame sorted = df.sort("age");
        assertEquals(4, sorted.nrow());
        // ages: 25 < 28 < 30 < 35
        assertEquals(25, sorted.getInt(0, 1));
        assertEquals(28, sorted.getInt(1, 1));
        assertEquals(30, sorted.getInt(2, 1));
        assertEquals(35, sorted.getInt(3, 1));
        // names should follow
        assertEquals("Alice",   sorted.getString(0, 0));
        assertEquals("Dave",    sorted.getString(1, 0));
        assertEquals("Charlie", sorted.getString(2, 0));
        assertEquals("Bob",     sorted.getString(3, 0));
    }

    @Test
    public void testSortDescending() {
        System.out.println("sort descending");
        DataFrame sorted = df.sort("salary", false);
        assertEquals(4, sorted.nrow());
        // salaries: 90000 > 80000 > 70000 > 60000
        assertEquals(90000.0, sorted.getDouble(0, 2), 1e-10);
        assertEquals(80000.0, sorted.getDouble(1, 2), 1e-10);
        assertEquals(70000.0, sorted.getDouble(2, 2), 1e-10);
        assertEquals(60000.0, sorted.getDouble(3, 2), 1e-10);
    }

    @Test
    public void testSortString() {
        System.out.println("sort string column");
        DataFrame sorted = df.sort("name");
        assertEquals("Alice",   sorted.getString(0, 0));
        assertEquals("Bob",     sorted.getString(1, 0));
        assertEquals("Charlie", sorted.getString(2, 0));
        assertEquals("Dave",    sorted.getString(3, 0));
    }

    // -----------------------------------------------------------------------
    // sample
    // -----------------------------------------------------------------------

    @Test
    public void testSampleSize() {
        System.out.println("sample size");
        DataFrame s = df.sample(2);
        assertEquals(2, s.nrow());
        assertEquals(df.ncol(), s.ncol());
    }

    @Test
    public void testSampleLargerThanDataFrame() {
        System.out.println("sample size >= nrow");
        DataFrame s = df.sample(100); // larger than 4 rows
        // should return all rows (capped at size)
        assertEquals(df.nrow(), s.nrow());
    }

    @Test
    public void testSamplePreservesSchema() {
        System.out.println("sample preserves schema");
        DataFrame s = df.sample(3);
        assertEquals(df.schema(), s.schema());
    }

    // -----------------------------------------------------------------------
    // RowIndex
    // -----------------------------------------------------------------------

    @Test
    public void testRowIndex() {
        System.out.println("RowIndex apply");
        Object[] labels = {"a", "b", "c", "d"};
        RowIndex index = new RowIndex(labels);
        assertEquals(0, index.apply("a"));
        assertEquals(3, index.apply("d"));
    }

    @Test
    public void testRowIndexContainsKey() {
        System.out.println("RowIndex containsKey");
        RowIndex index = new RowIndex(new Object[]{"x", "y", "z"});
        assertTrue(index.containsKey("x"));
        assertFalse(index.containsKey("w"));
    }

    @Test
    public void testRowIndexGetOrDefault() {
        System.out.println("RowIndex getOrDefault");
        RowIndex index = new RowIndex(new Object[]{"x", "y"});
        assertEquals(0,  index.getOrDefault("x"));
        assertEquals(-1, index.getOrDefault("missing"));
    }

    @Test
    public void testRowIndexMissingKeyThrows() {
        System.out.println("RowIndex apply missing key throws");
        RowIndex index = new RowIndex(new Object[]{"a", "b"});
        assertThrows(IllegalArgumentException.class, () -> index.apply("c"));
    }

    @Test
    public void testRowIndexDuplicateThrows() {
        System.out.println("RowIndex duplicate throws");
        assertThrows(IllegalArgumentException.class,
                () -> new RowIndex(new Object[]{"a", "a"}));
    }

    @Test
    public void testRowIndexNullThrows() {
        System.out.println("RowIndex null value throws");
        assertThrows(IllegalArgumentException.class,
                () -> new RowIndex(new Object[]{"a", null}));
    }

    // -----------------------------------------------------------------------
    // toString edge cases
    // -----------------------------------------------------------------------

    @Test
    public void testToStringEmptyRange() {
        System.out.println("toString empty range");
        // from == to should not throw — returns empty message
        String s = df.toString(2, 2, true);
        assertNotNull(s);
        assertFalse(s.isEmpty());
    }

    @Test
    public void testToStringFromEqualsSize() {
        System.out.println("toString from == size()");
        String s = df.toString(4, 5, true);
        assertNotNull(s);
        assertFalse(s.isEmpty());
    }

    // -----------------------------------------------------------------------
    // PrimitiveVector sum/variance
    // -----------------------------------------------------------------------

    @Test
    public void testPrimitiveVectorSum() {
        System.out.println("DoubleVector sum");
        DoubleVector v = new DoubleVector("v", new double[]{1.0, 2.0, 3.0, 4.0, 5.0});
        assertEquals(15.0, v.sum(), 1e-10);
    }

    @Test
    public void testPrimitiveVectorVar() {
        System.out.println("DoubleVector variance");
        DoubleVector v = new DoubleVector("v", new double[]{2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0});
        // population variance = 4.0
        assertEquals(4.571428, v.var(), 1e-5);
    }

    @Test
    public void testIntVectorSum() {
        System.out.println("IntVector sum");
        IntVector v = new IntVector("v", new int[]{10, 20, 30});
        assertEquals(60.0, v.sum(), 1e-10);
    }

    // -----------------------------------------------------------------------
    // ValueVector isin(int...)
    // -----------------------------------------------------------------------

    @Test
    public void testIsinIntArray() {
        System.out.println("ValueVector isin(int...)");
        IntVector v = new IntVector("v", new int[]{1, 2, 3, 4, 5});
        boolean[] result = v.isin(2, 4);
        assertFalse(result[0]); // 1
        assertTrue(result[1]);  // 2
        assertFalse(result[2]); // 3
        assertTrue(result[3]);  // 4
        assertFalse(result[4]); // 5
    }

    @Test
    public void testNullableDoubleVectorSumVar() {
        System.out.println("NullableDoubleVector sum/var");
        java.util.BitSet mask = new java.util.BitSet(5);
        mask.set(2); // index 2 is null
        var v = new smile.data.vector.NullableDoubleVector("v",
                new double[]{1.0, 2.0, Double.NaN, 4.0, 5.0}, mask);
        // sum of 1+2+4+5 = 12
        assertEquals(12.0, v.sum(), 1e-10);
        // non-null count = 4
        assertEquals(4, v.size() - v.getNullCount());
    }

    @Test
    public void testSparseDatasetNcolGrows() {
        System.out.println("SparseDataset ncol grows with data");
        smile.util.SparseArray row = new smile.util.SparseArray();
        row.append(0, 1.0);
        row.append(9, 2.0); // column 9 exceeds ncol=2
        var dataset = smile.data.SparseDataset.of(
                new smile.util.SparseArray[]{row}, 2);
        assertTrue(dataset.ncol() >= 10);
        assertEquals(2.0, dataset.get(0, 9), 1e-10);
    }
}

