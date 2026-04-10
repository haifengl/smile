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

import java.util.BitSet;
import java.util.List;
import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests covering bug-fixes and coverage gaps in smile.data.DataFrame.
 * Bug fixes verified:
 *  1. DataFrame.add(ValueVector...) - two vectors with the same NEW name
 *     in one batch call bypassed the duplicate-name check.
 *  2. DataFrame.of(StructType, List) - threw IllegalArgumentException for
 *     empty list; now returns a zero-row DataFrame with the correct schema.
 * New feature:
 *  3. DataFrame.slice(int, int) - contiguous row range selection.
 *  4. rename, sort, sample, RowIndex improvements.
 */
public class DataFrameRegressionTest {

    DataFrame df;        // 4-row mixed-type frame
    DataFrame nullable;  // frame with nullable columns

    public DataFrameRegressionTest() {
        df = new DataFrame(
                new StringVector("name",   new String[]{"Charlie", "Alice", "Bob", "Dave"}),
                new IntVector   ("age",    new int[]   {30, 25, 35, 28}),
                new DoubleVector("salary", new double[]{80000.0, 60000.0, 90000.0, 70000.0})
        );

        // nullable: age has null at index 1, salary has null at index 3
        double[] salaries = {80000.0, 60000.0, 90000.0, Double.NaN};
        BitSet salaryNull = new BitSet(4);
        salaryNull.set(3);
        int[] ages = {30, Integer.MIN_VALUE, 35, 28};
        BitSet ageNull = new BitSet(4);
        ageNull.set(1);
        nullable = new DataFrame(
                new StringVector("name", new String[]{"A", "B", "C", "D"}),
                new NullableIntVector(new StructField("age", DataTypes.NullableIntType), ages, ageNull),
                new NullableDoubleVector(new StructField("salary", DataTypes.NullableDoubleType), salaries, salaryNull)
        );
    }


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

    // ==============================================================
    // BUG FIX 1 – add() with duplicate names in the SAME batch call
    // ==============================================================

    @Test
    public void testAddDuplicateNameInBatchThrows() {
        System.out.println("BUG-FIX: add() with two columns of the same new name throws");
        IntVector v1 = new IntVector("score", new int[]{1, 2, 3, 4});
        IntVector v2 = new IntVector("score", new int[]{5, 6, 7, 8});
        assertThrows(IllegalArgumentException.class, () -> df.add(v1, v2));
    }

    @Test
    public void testAddClashingExistingNameThrows() {
        System.out.println("add() with a name already in schema throws");
        IntVector clash = new IntVector("age", new int[]{1, 2, 3, 4});
        assertThrows(IllegalArgumentException.class, () -> df.add(clash));
    }

    @Test
    public void testAddWrongSizeThrows() {
        System.out.println("add() with wrong size throws");
        IntVector short_ = new IntVector("x", new int[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> df.add(short_));
    }

    @Test
    public void testAddTwoDistinctColumnsSucceeds() {
        System.out.println("add() with two new distinct-name columns succeeds");
        // Fresh copy so we don't pollute other tests (DataFrame is mutable for add)
        DataFrame fresh = new DataFrame(
                new StringVector("name",   new String[]{"X", "Y", "Z", "W"}),
                new IntVector   ("age",    new int[]   {1, 2, 3, 4})
        );
        IntVector c1 = new IntVector("v1", new int[]{10, 20, 30, 40});
        IntVector c2 = new IntVector("v2", new int[]{50, 60, 70, 80});
        fresh.add(c1, c2);
        assertEquals(4, fresh.ncol());
        assertEquals(10, fresh.getInt(0, 2));
        assertEquals(50, fresh.getInt(0, 3));
    }

    // ==============================================================
    // BUG FIX 2 – of(StructType, List) with empty list
    // ==============================================================

    @Test
    public void testOfEmptyListReturnsZeroRowFrame() {
        System.out.println("BUG-FIX: DataFrame.of(schema, emptyList) returns 0-row DataFrame");
        StructType schema = new StructType(
                new StructField("x", DataTypes.IntType),
                new StructField("y", DataTypes.DoubleType)
        );
        DataFrame empty = DataFrame.of(schema, List.of());
        assertEquals(0, empty.nrow());
        assertEquals(2, empty.ncol());
        assertEquals(schema, empty.schema());
    }

    @Test
    public void testOfEmptyStreamReturnsZeroRowFrame() {
        System.out.println("BUG-FIX: DataFrame.of(schema, emptyStream) returns 0-row DataFrame");
        StructType schema = new StructType(
                new StructField("a", DataTypes.StringType),
                new StructField("b", DataTypes.DoubleType)
        );
        DataFrame empty = DataFrame.of(schema, java.util.stream.Stream.empty());
        assertEquals(0, empty.nrow());
        assertEquals(schema, empty.schema());
    }

    @Test
    public void testOfEmptyListAllPrimitiveTypes() {
        System.out.println("BUG-FIX: of(schema, emptyList) handles every primitive column type");
        StructType schema = new StructType(
                new StructField("i",  DataTypes.IntType),
                new StructField("l",  DataTypes.LongType),
                new StructField("d",  DataTypes.DoubleType),
                new StructField("f",  DataTypes.FloatType),
                new StructField("b",  DataTypes.BooleanType),
                new StructField("by", DataTypes.ByteType),
                new StructField("s",  DataTypes.ShortType),
                new StructField("c",  DataTypes.CharType),
                new StructField("st", DataTypes.StringType)
        );
        DataFrame empty = DataFrame.of(schema, List.of());
        assertEquals(0, empty.nrow());
        assertEquals(9, empty.ncol());
    }

    // ==============================================================
    // NEW FEATURE 3 – slice(int, int)
    // ==============================================================

    @Test
    public void testSliceMiddle() {
        System.out.println("slice middle rows");
        DataFrame s = df.slice(1, 3);  // rows 1 and 2
        assertEquals(2, s.nrow());
        assertEquals(df.ncol(), s.ncol());
        assertEquals("Alice", s.getString(0, 0));
        assertEquals("Bob",   s.getString(1, 0));
    }

    @Test
    public void testSliceAll() {
        System.out.println("slice all rows");
        DataFrame s = df.slice(0, df.nrow());
        assertEquals(df.nrow(), s.nrow());
        for (int i = 0; i < df.nrow(); i++) {
            assertEquals(df.getString(i, 0), s.getString(i, 0));
        }
    }

    @Test
    public void testSliceEmpty() {
        System.out.println("slice empty range");
        DataFrame s = df.slice(2, 2);
        assertEquals(0, s.nrow());
        assertEquals(df.ncol(), s.ncol());
    }

    @Test
    public void testSliceFirstRow() {
        System.out.println("slice single first row");
        DataFrame s = df.slice(0, 1);
        assertEquals(1, s.nrow());
        assertEquals("Charlie", s.getString(0, 0));
    }

    @Test
    public void testSliceLastRow() {
        System.out.println("slice single last row");
        DataFrame s = df.slice(df.nrow() - 1, df.nrow());
        assertEquals(1, s.nrow());
        assertEquals("Dave", s.getString(0, 0));
    }

    @Test
    public void testSliceNegativeFromThrows() {
        System.out.println("slice negative from throws");
        assertThrows(IllegalArgumentException.class, () -> df.slice(-1, 2));
    }

    @Test
    public void testSliceToGreaterThanSizeThrows() {
        System.out.println("slice to > size throws");
        assertThrows(IllegalArgumentException.class, () -> df.slice(0, df.nrow() + 1));
    }

    @Test
    public void testSliceToLessThanFromThrows() {
        System.out.println("slice to < from throws");
        assertThrows(IllegalArgumentException.class, () -> df.slice(3, 1));
    }

    @Test
    public void testSlicePreservesSchema() {
        System.out.println("slice preserves schema");
        DataFrame s = df.slice(0, 2);
        assertEquals(df.schema(), s.schema());
    }

    // ==============================================================
    // rename – in-place mutation, both schema and column vector
    // ==============================================================

    @Test
    public void testRenameUpdatesSchemaAndVector() {
        System.out.println("rename: schema and column vector both updated");
        DataFrame fresh = new DataFrame(
                new IntVector("a", new int[]{1, 2, 3}),
                new IntVector("b", new int[]{4, 5, 6})
        );
        fresh.rename("a", "alpha");
        assertEquals("alpha", fresh.schema().field(0).name());
        assertEquals("alpha", fresh.column(0).name());
        // Data intact
        assertEquals(1, fresh.getInt(0, 0));
        assertEquals(2, fresh.getInt(1, 0));
    }

    @Test
    public void testRenameNonExistentColumnThrows() {
        System.out.println("rename non-existent column throws");
        assertThrows(Exception.class, () -> df.rename("nonexistent", "x"));
    }

    // ==============================================================
    // sort – ascending, descending, nulls last, stable
    // ==============================================================

    // -----------------------------------------------------------------------
    // sort
    // -----------------------------------------------------------------------

    @Test
    public void testSortAscendingInt() {
        System.out.println("sort ascending int");
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
    public void testSortDescendingDouble() {
        System.out.println("sort descending double");
        DataFrame s = df.sort("salary", false);
        assertEquals(90000.0, s.getDouble(0, 2), 1e-10);
        assertEquals(80000.0, s.getDouble(1, 2), 1e-10);
        assertEquals(70000.0, s.getDouble(2, 2), 1e-10);
        assertEquals(60000.0, s.getDouble(3, 2), 1e-10);
    }

    @Test
    public void testSortString() {
        System.out.println("sort string column");
        DataFrame s = df.sort("name");
        assertEquals("Alice",   s.getString(0, 0));
        assertEquals("Bob",     s.getString(1, 0));
        assertEquals("Charlie", s.getString(2, 0));
        assertEquals("Dave",    s.getString(3, 0));
    }

    @Test
    public void testSortNullsLast() {
        System.out.println("sort: nulls sort to the end");
        // nullable age: {30, null, 35, 28} ascending → {28, 30, 35, null}
        DataFrame s = nullable.sort("age");
        assertEquals(28, s.getInt(0, 1));
        assertEquals(30, s.getInt(1, 1));
        assertEquals(35, s.getInt(2, 1));
        assertTrue(s.isNullAt(3, 1), "null must sort to the end");
    }

    @Test
    public void testSortPreservesNcol() {
        System.out.println("sort preserves column count");
        DataFrame s = df.sort("age");
        assertEquals(df.ncol(), s.ncol());
    }

    @Test
    public void testSortRowsConsistent() {
        System.out.println("sort: all columns stay consistent after sort");
        DataFrame s = df.sort("age");
        // Alice has age=25, salary=60000
        assertEquals("Alice", s.getString(0, 0));
        assertEquals(25,      s.getInt(0, 1));
        assertEquals(60000.0, s.getDouble(0, 2), 1e-10);
    }

    // ==============================================================
    // sample
    // ==============================================================

    @Test
    public void testSampleReturnsCorrectSize() {
        System.out.println("sample returns requested size");
        DataFrame s = df.sample(2);
        assertEquals(2, s.nrow());
    }

    @Test
    public void testSampleCappedAtSize() {
        System.out.println("sample capped at frame size");
        DataFrame s = df.sample(1000);
        assertEquals(df.nrow(), s.nrow());
    }

    @Test
    public void testSamplePreservesColumns() {
        System.out.println("sample preserves column count and schema");
        DataFrame s = df.sample(3);
        assertEquals(df.ncol(), s.ncol());
        assertEquals(df.schema(), s.schema());
    }

    @Test
    public void testSampleZeroRows() {
        System.out.println("sample 0 rows returns empty frame");
        DataFrame s = df.sample(0);
        assertEquals(0, s.nrow());
        assertEquals(df.ncol(), s.ncol());
    }

    // ==============================================================
    // dropna / fillna
    // ==============================================================

    @Test
    public void testDropnaRemovesNullRows() {
        System.out.println("dropna removes rows with any null");
        // rows 1 (age null) and 3 (salary null) should be dropped
        DataFrame clean = nullable.dropna();
        assertEquals(2, clean.nrow()); // rows 0 and 2 survive
        assertEquals("A", clean.getString(0, 0));
        assertEquals("C", clean.getString(1, 0));
    }

    @Test
    public void testDropnaAllCleanFrameUnchanged() {
        System.out.println("dropna on frame with no nulls returns all rows");
        DataFrame clean = df.dropna();
        assertEquals(df.nrow(), clean.nrow());
    }

    @Test
    public void testFillnaReplacesMissingDouble() {
        System.out.println("fillna replaces NaN in double column");
        DataFrame fresh = new DataFrame(
                new DoubleVector("x", new double[]{1.0, Double.NaN, 3.0})
        );
        fresh.fillna(-1.0);
        assertEquals(-1.0, fresh.getDouble(1, 0), 1e-10);
        assertEquals(1.0,  fresh.getDouble(0, 0), 1e-10);
        assertEquals(3.0,  fresh.getDouble(2, 0), 1e-10);
    }

    @Test
    public void testFillnaLeavesNonNullUntouched() {
        System.out.println("fillna does not replace finite values");
        DataFrame fresh = new DataFrame(
                new DoubleVector("x", new double[]{1.0, 2.0, 3.0})
        );
        fresh.fillna(99.0);
        assertEquals(1.0, fresh.getDouble(0, 0), 1e-10);
        assertEquals(2.0, fresh.getDouble(1, 0), 1e-10);
        assertEquals(3.0, fresh.getDouble(2, 0), 1e-10);
    }

    // ==============================================================
    // select / drop
    // ==============================================================

    @Test
    public void testSelectByIndex() {
        System.out.println("select by column indices");
        DataFrame s = df.select(0, 2); // name and salary
        assertEquals(2, s.ncol());
        assertEquals("name",   s.names()[0]);
        assertEquals("salary", s.names()[1]);
    }

    @Test
    public void testSelectByName() {
        System.out.println("select by column names");
        DataFrame s = df.select("salary", "name");
        assertEquals(2, s.ncol());
        assertEquals("salary", s.names()[0]);
        assertEquals("name",   s.names()[1]);
    }

    @Test
    public void testDropByIndex() {
        System.out.println("drop by column index");
        DataFrame d = df.drop(1); // drop age
        assertEquals(2, d.ncol());
        assertEquals("name",   d.names()[0]);
        assertEquals("salary", d.names()[1]);
    }

    @Test
    public void testDropByName() {
        System.out.println("drop by column name");
        DataFrame d = df.drop("age");
        assertEquals(2, d.ncol());
        assertFalse(List.of(d.names()).contains("age"));
    }

    @Test
    public void testDropMultipleNames() {
        System.out.println("drop multiple column names");
        DataFrame d = df.drop("age", "salary");
        assertEquals(1, d.ncol());
        assertEquals("name", d.names()[0]);
    }

    // ==============================================================
    // merge / concat
    // ==============================================================

    @Test
    public void testMergeHorizontal() {
        System.out.println("merge: horizontal combination");
        DataFrame a = new DataFrame(new IntVector("x", new int[]{1, 2, 3}));
        DataFrame b = new DataFrame(new IntVector("y", new int[]{4, 5, 6}));
        DataFrame m = a.merge(b);
        assertEquals(3, m.nrow());
        assertEquals(2, m.ncol());
        assertEquals(1, m.getInt(0, 0));
        assertEquals(4, m.getInt(0, 1));
    }

    @Test
    public void testMergeSuffixOnClashingName() {
        System.out.println("merge: clashing column gets _2 suffix");
        DataFrame a = new DataFrame(new IntVector("x", new int[]{1, 2}));
        DataFrame b = new DataFrame(new IntVector("x", new int[]{3, 4}));
        DataFrame m = a.merge(b);
        assertEquals(2, m.ncol());
        assertEquals("x",   m.names()[0]);
        assertEquals("x_2", m.names()[1]);
    }

    @Test
    public void testMergeDifferentSizeThrows() {
        System.out.println("merge: size mismatch throws");
        DataFrame a = new DataFrame(new IntVector("x", new int[]{1, 2}));
        DataFrame b = new DataFrame(new IntVector("y", new int[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> a.merge(b));
    }

    @Test
    public void testConcatVertical() {
        System.out.println("concat: vertical combination");
        DataFrame a = new DataFrame(new IntVector("x", new int[]{1, 2}));
        DataFrame b = new DataFrame(new IntVector("x", new int[]{3, 4}));
        DataFrame c = a.concat(b);
        assertEquals(4, c.nrow());
        assertEquals(1, c.ncol());
        assertEquals(1, c.getInt(0, 0));
        assertEquals(4, c.getInt(3, 0));
    }

    @Test
    public void testConcatSchemaMismatchThrows() {
        System.out.println("concat: schema mismatch throws");
        DataFrame a = new DataFrame(new IntVector("x", new int[]{1}));
        DataFrame b = new DataFrame(new IntVector("y", new int[]{2}));
        assertThrows(IllegalArgumentException.class, () -> a.concat(b));
    }

    // ==============================================================
    // setIndex / loc
    // ==============================================================

    @Test
    public void testSetIndexFromArray() {
        System.out.println("setIndex from Object array");
        Object[] labels = {"r0", "r1", "r2", "r3"};
        DataFrame indexed = df.setIndex(labels);
        assertNotNull(indexed.index());
        assertEquals("r0", indexed.index().values()[0]);
        assertEquals("r3", indexed.index().values()[3]);
    }

    @Test
    public void testSetIndexWrongSizeThrows() {
        System.out.println("setIndex wrong size throws");
        assertThrows(IllegalArgumentException.class,
                () -> df.setIndex(new Object[]{"a", "b"}));
    }

    @Test
    public void testSetIndexFromColumn() {
        System.out.println("setIndex from column name removes that column");
        DataFrame indexed = df.setIndex("name");
        assertNotNull(indexed.index());
        // "name" column should be removed
        assertFalse(List.of(indexed.names()).contains("name"),
                "column used as index should be removed");
        assertEquals(df.ncol() - 1, indexed.ncol());
        // index values should be the original name values
        assertEquals("Charlie", indexed.index().values()[0]);
    }

    @Test
    public void testLocByLabel() {
        System.out.println("loc by single label");
        DataFrame indexed = df.setIndex(new Object[]{"r0", "r1", "r2", "r3"});
        Tuple row = indexed.loc("r2");
        assertEquals("Bob", row.getString(0)); // name col after setIndex is first
    }

    @Test
    public void testLocMultipleLabels() {
        System.out.println("loc by multiple labels");
        DataFrame indexed = df.setIndex(new Object[]{"r0", "r1", "r2", "r3"});
        DataFrame sub = indexed.loc("r0", "r3");
        assertEquals(2, sub.nrow());
    }

    // ==============================================================
    // join
    // ==============================================================

    @Test
    public void testJoinOnIndex() {
        System.out.println("join on matching index");
        DataFrame a = new DataFrame(new IntVector("v1", new int[]{10, 20, 30}))
                .setIndex(new Object[]{"x", "y", "z"});
        DataFrame b = new DataFrame(new IntVector("v2", new int[]{40, 50, 60}))
                .setIndex(new Object[]{"y", "z", "w"});
        DataFrame j = a.join(b);
        // inner join: only y and z match
        assertEquals(2, j.nrow());
        assertEquals(2, j.ncol());
    }

    @Test
    public void testJoinFallsBackToMergeWhenNoIndex() {
        System.out.println("join without index falls back to merge");
        DataFrame a = new DataFrame(new IntVector("x", new int[]{1, 2}));
        DataFrame b = new DataFrame(new IntVector("y", new int[]{3, 4}));
        DataFrame j = a.join(b);
        assertEquals(2, j.nrow());
        assertEquals(2, j.ncol());
    }

    // ==============================================================
    // factorize
    // ==============================================================

    @Test
    public void testFactorizeStringColumn() {
        System.out.println("factorize: string column gets NominalScale");
        DataFrame f = df.factorize("name");
        assertInstanceOf(NominalScale.class, f.schema().field("name").measure(),
                "factorized column must have NominalScale");
        assertTrue(f.schema().field("name").dtype().isIntegral(),
                "factorized column must be integral");
    }

    @Test
    public void testFactorizeAllStringColumns() {
        System.out.println("factorize with no args converts all string columns");
        DataFrame withStr = new DataFrame(
                new StringVector("a", new String[]{"x", "y", "x"}),
                new StringVector("b", new String[]{"p", "q", "p"}),
                new IntVector   ("c", new int[]   {1, 2, 3})
        );
        DataFrame f = withStr.factorize();
        assertInstanceOf(NominalScale.class, f.schema().field("a").measure());
        assertInstanceOf(NominalScale.class, f.schema().field("b").measure());
        // non-string columns unaffected
        assertNull(f.schema().field("c").measure());
    }

    @Test
    public void testFactorizeAlphabeticLevels() {
        System.out.println("factorize: levels are sorted alphabetically");
        DataFrame withStr = new DataFrame(
                new StringVector("cat", new String[]{"c", "a", "b", "a"})
        );
        DataFrame f = withStr.factorize("cat");
        NominalScale scale = (NominalScale) f.schema().field("cat").measure();
        // Levels should be ["a","b","c"] sorted
        assertEquals("a", scale.level(0));
        assertEquals("b", scale.level(1));
        assertEquals("c", scale.level(2));
    }

    // ==============================================================
    // toString / head / tail
    // ==============================================================

    @Test
    public void testHeadLessThanSize() {
        System.out.println("head(2) returns 2-row string");
        String s = df.head(2);
        assertNotNull(s);
        assertTrue(s.contains("Charlie"));
        assertFalse(s.contains("Dave"));
    }

    @Test
    public void testHeadMoreThanSize() {
        System.out.println("head(100) returns all rows");
        String s = df.head(100);
        assertTrue(s.contains("Charlie"));
        assertTrue(s.contains("Dave"));
    }

    @Test
    public void testTailLessThanSize() {
        System.out.println("tail(2) returns last 2 rows");
        String s = df.tail(2);
        assertFalse(s.contains("Charlie"));
        assertTrue(s.contains("Dave"));
    }

    // -----------------------------------------------------------------------
    // toString edge cases
    // -----------------------------------------------------------------------

    @Test
    public void testToStringEmptyRange() {
        System.out.println("toString with empty range returns 'Empty DataFrame'");
        String s = df.toString(2, 2, true);
        assertNotNull(s);
        assertEquals("Empty DataFrame\n", s);
    }

    @Test
    public void testToStringFromEqualsSize() {
        System.out.println("toString with from==size returns 'Empty DataFrame'");
        String s = df.toString(4, 5, true);
        assertNotNull(s);
        assertEquals("Empty DataFrame\n", s);
    }

    @Test
    public void testToStringNegativeFromThrows() {
        System.out.println("toString with negative from throws");
        assertThrows(IllegalArgumentException.class, () -> df.toString(-1, 2, true));
    }

    @Test
    public void testToStringFromBeyondSizeThrows() {
        System.out.println("toString with from > size throws");
        assertThrows(IllegalArgumentException.class, () -> df.toString(5, 6, true));
    }

    // ==============================================================
    // describe
    // ==============================================================

    @Test
    public void testDescribeRowCountEqualsColumnCount() {
        System.out.println("describe: one row per column");
        DataFrame desc = df.describe();
        assertEquals(df.ncol(), desc.nrow());
    }

    @Test
    public void testDescribeColumnNames() {
        System.out.println("describe: column names are reported correctly");
        DataFrame desc = df.describe();
        // First column of describe is "column"
        assertEquals("name",   desc.getString(0, 0));
        assertEquals("age",    desc.getString(1, 0));
        assertEquals("salary", desc.getString(2, 0));
    }

    @Test
    public void testDescribeCountNonNull() {
        System.out.println("describe: count excludes nulls");
        DataFrame desc = nullable.describe();
        // age column: 3 non-null (index 1 is null)
        int ageRow = -1, salaryRow = -1;
        for (int i = 0; i < desc.nrow(); i++) {
            if ("age".equals(desc.getString(i, 0)))    ageRow    = i;
            if ("salary".equals(desc.getString(i, 0))) salaryRow = i;
        }
        assertTrue(ageRow >= 0 && salaryRow >= 0, "Should have age and salary rows");
        assertEquals(3, desc.getInt(ageRow, 3));    // 3 non-null ages
        assertEquals(3, desc.getInt(salaryRow, 3)); // 3 non-null salaries
    }

    // ==============================================================
    // isNullAt / get cell types
    // ==============================================================

    @Test
    public void testIsNullAt() {
        System.out.println("isNullAt");
        assertFalse(nullable.isNullAt(0, 1)); // age row 0 not null
        assertTrue(nullable.isNullAt(1, 1));  // age row 1 is null
        assertFalse(nullable.isNullAt(2, 2)); // salary row 2 not null
        assertTrue(nullable.isNullAt(3, 2));  // salary row 3 is null
    }

    @Test
    public void testGetCellTypedAccessors() {
        System.out.println("getInt / getDouble cell accessors");
        assertEquals(30,      df.getInt(0, 1));
        assertEquals(25,      df.getInt(1, 1));
        assertEquals(80000.0, df.getDouble(0, 2), 1e-10);
        assertEquals(60000.0, df.getDouble(1, 2), 1e-10);
    }

    @Test
    public void testGetStringAccessor() {
        System.out.println("getString cell accessor");
        assertEquals("Charlie", df.getString(0, 0));
        assertEquals("Dave",    df.getString(3, 0));
    }

    // ==============================================================
    // of(double[][], String...) / of(int[][], String...) factory
    // ==============================================================

    @Test
    public void testOfDoubleArrayAutoNames() {
        System.out.println("of(double[][]) auto-generates column names V1, V2...");
        double[][] data = {{1.0, 2.0}, {3.0, 4.0}};
        DataFrame f = DataFrame.of(data);
        assertEquals("V1", f.names()[0]);
        assertEquals("V2", f.names()[1]);
        assertEquals(1.0, f.getDouble(0, 0), 1e-10);
        assertEquals(4.0, f.getDouble(1, 1), 1e-10);
    }

    @Test
    public void testOfDoubleArrayCustomNames() {
        System.out.println("of(double[][], names)");
        double[][] data = {{1.0, 2.0}, {3.0, 4.0}};
        DataFrame f = DataFrame.of(data, "x", "y");
        assertEquals("x", f.names()[0]);
        assertEquals("y", f.names()[1]);
    }

    @Test
    public void testOfIntArray() {
        System.out.println("of(int[][])");
        int[][] data = {{1, 2}, {3, 4}, {5, 6}};
        DataFrame f = DataFrame.of(data, "a", "b");
        assertEquals(3, f.nrow());
        assertEquals(2, f.ncol());
        assertEquals(5, f.getInt(2, 0));
    }

    // ==============================================================
    // DataFrame record constructor guards
    // ==============================================================

    @Test
    public void testConstructorEmptyColumnsThrows() {
        System.out.println("DataFrame constructor: empty columns throws");
        assertThrows(IllegalArgumentException.class,
                () -> new DataFrame(new StructType(), List.of(), null));
    }

    @Test
    public void testConstructorColumnSizeMismatchThrows() {
        System.out.println("DataFrame constructor: column size mismatch throws");
        assertThrows(IllegalArgumentException.class, () -> new DataFrame(
                new IntVector("a", new int[]{1, 2, 3}),
                new IntVector("b", new int[]{4, 5})       // size 2 vs 3
        ));
    }

    // ==============================================================
    // stream / iterator / toList
    // ==============================================================

    @Test
    public void testStreamRowCount() {
        System.out.println("stream row count");
        long count = df.stream().count();
        assertEquals(df.nrow(), count);
    }

    @Test
    public void testIteratorRowCount() {
        System.out.println("iterator row count");
        int count = 0;
        for (@SuppressWarnings("unused") Row row : df) count++;
        assertEquals(df.nrow(), count);
    }

    @Test
    public void testToListSize() {
        System.out.println("toList size");
        List<Row> list = df.toList();
        assertEquals(df.nrow(), list.size());
    }

    // ==============================================================
    // shape / size / nrow / ncol / isEmpty
    // ==============================================================

    @Test
    public void testShape() {
        System.out.println("shape()");
        assertEquals(df.nrow(), df.shape(0));
        assertEquals(df.ncol(), df.shape(1));
    }

    @Test
    public void testShapeInvalidDimThrows() {
        System.out.println("shape: invalid dim throws");
        assertThrows(IllegalArgumentException.class, () -> df.shape(2));
    }

    @Test
    public void testIsEmpty() {
        System.out.println("isEmpty");
        assertFalse(df.isEmpty());
        DataFrame empty = DataFrame.of(df.schema(), List.of());
        assertTrue(empty.isEmpty());
    }

    @Test
    public void testNrowNcolConsistency() {
        System.out.println("nrow/ncol/size consistency");
        assertEquals(df.size(), df.nrow());
        assertEquals(df.columns().size(), df.ncol());
    }
}

