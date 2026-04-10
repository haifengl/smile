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
package smile.data.vector;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.BitSet;
import smile.data.measure.NominalScale;
import smile.data.measure.OrdinalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.util.Index;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the smile.data.vector package.
 *
 * @author Haifeng Li
 */
public class VectorTest {

    @BeforeAll  public static void setUpClass() {}
    @AfterAll   public static void tearDownClass() {}
    @BeforeEach public void setUp() {}
    @AfterEach  public void tearDown() {}

    @Test
    public void testFactory() {
        System.out.println("of");
        var doubles = ValueVector.of("A", 1.0, 2.0, 3.0);
        assertEquals(DataTypes.DoubleType, doubles.dtype());

        var instants = ValueVector.of("B", Instant.now());
        assertEquals(DataTypes.DateTimeType, instants.dtype());

        var datetimes = ValueVector.of("B", LocalDateTime.now());
        assertEquals(DataTypes.DateTimeType, datetimes.dtype());

        var dates = ValueVector.of("B", LocalDate.now());
        assertEquals(DataTypes.DateType, dates.dtype());

        var times = ValueVector.of("B", LocalTime.now());
        assertEquals(DataTypes.TimeType, times.dtype());

        var cat = ValueVector.nominal("C", "test", "train", "test", "train");
        assertTrue(cat instanceof ByteVector);
        assertEquals(DataTypes.ByteType, cat.dtype());
        assertTrue(cat.measure() instanceof NominalScale);

        var strings = ValueVector.of("D",
                "this is a string vector",
                "Nominal/ordinal vectors store data as integers internally");
        assertEquals(DataTypes.StringType, strings.dtype());

        var arrayVector = ObjectVector.of("E", Index.range(0, 4).toArray(), new int[]{3, 3, 3, 3});
        assertEquals(DataTypes.IntArrayType, arrayVector.dtype());
    }

    // =========================================================================
    // BooleanVector
    // =========================================================================

    @Test
    public void testBooleanVectorBasics() {
        System.out.println("BooleanVector basics");
        var v = new BooleanVector("flag", new boolean[]{true, false, true, false});
        assertEquals(4, v.size());
        assertEquals("flag", v.name());
        assertEquals(DataTypes.BooleanType, v.dtype());
        assertFalse(v.isNullable());
        assertEquals(0, v.getNullCount());
        assertFalse(v.isNullAt(0));
        assertTrue(v.getBoolean(0));
        assertFalse(v.getBoolean(1));
    }

    @Test
    public void testBooleanVectorGetNumeric() {
        System.out.println("BooleanVector numeric conversions");
        var v = new BooleanVector("f", new boolean[]{true, false});
        assertEquals(1, v.getInt(0));
        assertEquals(0, v.getInt(1));
        assertEquals(1L, v.getLong(0));
        assertEquals(1.0, v.getDouble(0), 1e-10);
        assertEquals(0.0, v.getDouble(1), 1e-10);
        assertEquals((byte) 1, v.getByte(0));
        assertEquals((short) 0, v.getShort(1));
        assertEquals(1.0f, v.getFloat(0), 1e-6f);
        assertEquals('T', v.getChar(0));
        assertEquals('F', v.getChar(1));
    }

    @Test
    public void testBooleanVectorSet() {
        System.out.println("BooleanVector set");
        var v = new BooleanVector("f", new boolean[]{true, false});
        v.set(1, Boolean.TRUE);
        assertTrue(v.getBoolean(1));
    }

    @Test
    public void testBooleanVectorSetInvalidThrows() {
        System.out.println("BooleanVector set invalid throws");
        var v = new BooleanVector("f", new boolean[]{true});
        assertThrows(IllegalArgumentException.class, () -> v.set(0, "true"));
    }

    @Test
    public void testBooleanVectorWithName() {
        System.out.println("BooleanVector withName");
        var v = new BooleanVector("old", new boolean[]{true});
        var v2 = v.withName("new");
        assertEquals("new", v2.name());
    }

    @Test
    public void testBooleanVectorSlice() {
        System.out.println("BooleanVector slice");
        var v = new BooleanVector("f", new boolean[]{true, false, true, false});
        var s = v.slice(1, 3);
        assertEquals(2, s.size());
        assertFalse(s.getBoolean(0));
        assertTrue(s.getBoolean(1));
    }

    @Test
    public void testBooleanVectorIntStream() {
        System.out.println("BooleanVector intStream");
        var v = new BooleanVector("f", new boolean[]{true, false, true});
        int[] arr = v.intStream().toArray();
        assertArrayEquals(new int[]{1, 0, 1}, arr);
    }

    @Test
    public void testBooleanVectorToString() {
        System.out.println("BooleanVector toString");
        var v = new BooleanVector("flag", new boolean[]{true, false});
        String s = v.toString();
        assertTrue(s.contains("flag"));
    }

    // =========================================================================
    // ByteVector / ShortVector / CharVector (representative integral types)
    // =========================================================================

    @Test
    public void testByteVectorBasics() {
        System.out.println("ByteVector basics");
        var v = new ByteVector("b", new byte[]{1, 2, 3, -1});
        assertEquals(4, v.size());
        assertEquals((byte) 1, v.getByte(0));
        assertEquals((byte) -1, v.getByte(3));
        assertFalse(v.isNullable());
        assertEquals(0, v.getNullCount());
        assertFalse(v.isNullAt(2));
    }

    @Test
    public void testByteVectorNumericConversions() {
        System.out.println("ByteVector numeric conversions");
        var v = new ByteVector("b", new byte[]{10});
        assertEquals(10, v.getInt(0));
        assertEquals(10L, v.getLong(0));
        assertEquals(10.0, v.getDouble(0), 1e-10);
        assertEquals(10.0f, v.getFloat(0), 1e-5f);
        assertEquals((short) 10, v.getShort(0));
    }

    @Test
    public void testShortVectorBasics() {
        System.out.println("ShortVector basics");
        var v = new ShortVector("s", new short[]{100, 200, -50});
        assertEquals(3, v.size());
        assertEquals((short) 100, v.getShort(0));
        assertEquals(-50, v.getInt(2));
    }

    @Test
    public void testCharVectorBasics() {
        System.out.println("CharVector basics");
        var v = new CharVector("c", new char[]{'A', 'B', 'C'});
        assertEquals(3, v.size());
        assertEquals('A', v.getChar(0));
        assertEquals((int) 'B', v.getInt(1));
    }

    // =========================================================================
    // IntVector
    // =========================================================================

    @Test
    public void testIntVectorBasics() {
        System.out.println("IntVector basics");
        var v = new IntVector("age", new int[]{10, 20, 30, 40, 50});
        assertEquals(5, v.size());
        assertEquals("age", v.name());
        assertEquals(DataTypes.IntType, v.dtype());
        assertFalse(v.isNullable());
        assertEquals(0, v.getNullCount());
        assertFalse(v.anyNull());
        assertEquals(10, v.getInt(0));
        assertEquals(50, v.getInt(4));
    }

    @Test
    public void testIntVectorNumericConversions() {
        System.out.println("IntVector numeric conversions");
        var v = new IntVector("v", new int[]{7});
        assertEquals(7L,   v.getLong(0));
        assertEquals(7.0,  v.getDouble(0), 1e-10);
        assertEquals(7.0f, v.getFloat(0), 1e-5f);
        assertEquals((byte)  7, v.getByte(0));
        assertEquals((short) 7, v.getShort(0));
        assertTrue(v.getBoolean(0));  // nonzero → true
        assertFalse(new IntVector("v", new int[]{0}).getBoolean(0));
    }

    @Test
    public void testIntVectorSet() {
        System.out.println("IntVector set");
        var v = new IntVector("v", new int[]{1, 2, 3});
        v.set(1, 99);
        assertEquals(99, v.getInt(1));
    }

    @Test
    public void testIntVectorSetInvalidThrows() {
        System.out.println("IntVector set invalid throws");
        var v = new IntVector("v", new int[]{1});
        assertThrows(IllegalArgumentException.class, () -> v.set(0, "not-a-number"));
    }

    @Test
    public void testIntVectorGet() {
        System.out.println("IntVector get(Index)");
        var v = new IntVector("v", new int[]{10, 20, 30, 40, 50});
        var s = v.get(Index.of(1, 3));
        assertEquals(2, s.size());
        assertEquals(20, s.getInt(0));
        assertEquals(40, s.getInt(1));
    }

    @Test
    public void testIntVectorSlice() {
        System.out.println("IntVector slice");
        var v = new IntVector("v", new int[]{0, 1, 2, 3, 4});
        var s = v.slice(1, 4);
        assertEquals(3, s.size());
        assertEquals(1, s.getInt(0));
        assertEquals(3, s.getInt(2));
    }

    @Test
    public void testIntVectorWithName() {
        System.out.println("IntVector withName");
        var v = new IntVector("old", new int[]{1, 2});
        var v2 = v.withName("new");
        assertEquals("new", v2.name());
        assertEquals(v.getInt(0), v2.getInt(0));
    }

    @Test
    public void testIntVectorIntStream() {
        System.out.println("IntVector intStream");
        var v = new IntVector("v", new int[]{3, 1, 4, 1, 5});
        int sum = v.intStream().sum();
        assertEquals(14, sum);
    }

    @Test
    public void testIntVectorStatistics() {
        System.out.println("IntVector statistics");
        var v = new IntVector("v", new int[]{1, 2, 3, 4, 5});
        assertEquals(3.0,  v.mean(),   1e-10);
        assertEquals(15.0, v.sum(),    1e-10);
        assertEquals(1.0,  v.min(),    1e-10);
        assertEquals(5.0,  v.max(),    1e-10);
        assertEquals(3.0,  v.median(), 1e-10);
        assertTrue(v.var() > 0);
        assertTrue(v.stdev() > 0);
    }

    @Test
    public void testIntVectorInvalidTypeBuildThrows() {
        System.out.println("IntVector invalid dtype throws");
        var field = new StructField("v", DataTypes.DoubleType);
        assertThrows(IllegalArgumentException.class, () -> new IntVector(field, new int[]{1}));
    }

    @Test
    public void testIntVectorIsin() {
        System.out.println("IntVector isin(int...)");
        var v = new IntVector("v", new int[]{1, 2, 3, 4, 5});
        boolean[] mask = v.isin(2, 4);
        assertArrayEquals(new boolean[]{false, true, false, true, false}, mask);
    }

    @Test
    public void testIntVectorIsinNegative() {
        System.out.println("IntVector isin with negative values");
        var v = new IntVector("v", new int[]{-1, 0, 1});
        boolean[] mask = v.isin(-1, 1);
        assertArrayEquals(new boolean[]{true, false, true}, mask);
    }

    // =========================================================================
    // LongVector
    // =========================================================================

    @Test
    public void testLongVectorBasics() {
        System.out.println("LongVector basics");
        var v = new LongVector("ts", new long[]{100L, 200L, Long.MAX_VALUE});
        assertEquals(3, v.size());
        assertEquals(100L, v.getLong(0));
        assertEquals(Long.MAX_VALUE, v.getLong(2));
        assertFalse(v.isNullable());
        assertEquals(0, v.getNullCount());
    }

    @Test
    public void testLongVectorIntStream() {
        System.out.println("LongVector intStream (was missing override)");
        var v = new LongVector("v", new long[]{10L, 20L, 30L});
        // Should NOT throw UnsupportedOperationException
        int[] arr = v.intStream().toArray();
        assertArrayEquals(new int[]{10, 20, 30}, arr);
    }

    @Test
    public void testLongVectorLongStream() {
        System.out.println("LongVector longStream");
        var v = new LongVector("v", new long[]{1L, 2L, 3L});
        long sum = v.longStream().sum();
        assertEquals(6L, sum);
    }

    @Test
    public void testLongVectorGetIndex() {
        System.out.println("LongVector get(Index)");
        var v = new LongVector("v", new long[]{10L, 20L, 30L});
        var s = v.get(Index.of(0, 2));
        assertEquals(10L, s.getLong(0));
        assertEquals(30L, s.getLong(1));
    }

    @Test
    public void testLongVectorErrorMessageIncludesField() {
        System.out.println("LongVector error message includes field name");
        var field = new StructField("myLong", DataTypes.IntType);
        try {
            new LongVector(field, new long[]{1L});
            fail("Should have thrown");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("myLong"),
                "Error message should contain field name, got: " + e.getMessage());
        }
    }

    // =========================================================================
    // FloatVector
    // =========================================================================

    @Test
    public void testFloatVectorBasics() {
        System.out.println("FloatVector basics");
        var v = new FloatVector("f", new float[]{1.5f, 2.5f, Float.NaN});
        assertEquals(3, v.size());
        assertEquals(1.5f, v.getFloat(0), 1e-6f);
        assertTrue(v.isNullAt(2));  // NaN is null
        assertFalse(v.isNullAt(0));
    }

    @Test
    public void testFloatVectorGetBoolean() {
        System.out.println("FloatVector getBoolean (bug fix: nonzero=true)");
        var v = new FloatVector("f", new float[]{0.0f, 1.5f, -0.5f});
        assertFalse(v.getBoolean(0));  // 0 → false (was buggy: true)
        assertTrue(v.getBoolean(1));   // nonzero → true
        assertTrue(v.getBoolean(2));   // nonzero → true
    }

    @Test
    public void testFloatVectorFillna() {
        System.out.println("FloatVector fillna");
        var v = new FloatVector("f", new float[]{1.0f, Float.NaN, 3.0f});
        v.fillna(0.0f);
        assertFalse(v.isNullAt(1));
        assertEquals(0.0f, v.getFloat(1), 1e-6f);
    }

    // =========================================================================
    // DoubleVector
    // =========================================================================

    @Test
    public void testDoubleVectorBasics() {
        System.out.println("DoubleVector basics");
        var v = new DoubleVector("score", new double[]{1.1, 2.2, 3.3, Double.NaN});
        assertEquals(4, v.size());
        assertEquals("score", v.name());
        assertEquals(DataTypes.DoubleType, v.dtype());
        assertFalse(v.isNullable());
        assertEquals(1.1, v.getDouble(0), 1e-10);
        assertTrue(v.isNullAt(3));
        assertFalse(v.isNullAt(0));
    }

    @Test
    public void testDoubleVectorGetBoolean() {
        System.out.println("DoubleVector getBoolean (bug fix: nonzero=true)");
        var v = new DoubleVector("v", new double[]{0.0, 1.0, -2.5});
        assertFalse(v.getBoolean(0));  // 0 → false
        assertTrue(v.getBoolean(1));   // nonzero → true
        assertTrue(v.getBoolean(2));   // nonzero → true
    }

    @Test
    public void testDoubleVectorStatistics() {
        System.out.println("DoubleVector statistics");
        var v = new DoubleVector("v", new double[]{2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0});
        assertEquals(5.0,  v.mean(),   1e-10);
        assertEquals(40.0, v.sum(),    1e-10);
        assertEquals(2.0,  v.min(),    1e-10);
        assertEquals(9.0,  v.max(),    1e-10);
        assertEquals(5.0,  v.median(), 1e-10);
        assertTrue(v.var() > 0);
        assertTrue(v.stdev() > 0);
        assertTrue(v.q1() <= v.median());
        assertTrue(v.q3() >= v.median());
    }

    @Test
    public void testDoubleVectorFillna() {
        System.out.println("DoubleVector fillna");
        var v = new DoubleVector("v", new double[]{1.0, Double.NaN, 3.0, Double.POSITIVE_INFINITY});
        v.fillna(0.0);
        assertEquals(0.0, v.getDouble(1), 1e-10);
        assertEquals(0.0, v.getDouble(3), 1e-10);
    }

    @Test
    public void testDoubleVectorGetIndex() {
        System.out.println("DoubleVector get(Index)");
        var v = new DoubleVector("v", new double[]{1.0, 2.0, 3.0, 4.0, 5.0});
        var s = v.get(Index.of(0, 2, 4));
        assertEquals(3, s.size());
        assertEquals(1.0, s.getDouble(0), 1e-10);
        assertEquals(3.0, s.getDouble(1), 1e-10);
        assertEquals(5.0, s.getDouble(2), 1e-10);
    }

    @Test
    public void testDoubleVectorDoubleStream() {
        System.out.println("DoubleVector doubleStream");
        var v = new DoubleVector("v", new double[]{1.0, 2.0, 3.0});
        assertEquals(6.0, v.doubleStream().sum(), 1e-10);
    }

    // =========================================================================
    // NullableIntVector
    // =========================================================================

    @Test
    public void testNullableIntVectorBasics() {
        System.out.println("NullableIntVector basics");
        BitSet mask = new BitSet(4);
        mask.set(1);
        mask.set(3);
        var v = new NullableIntVector("v", new int[]{10, 0, 30, 0}, mask);
        assertEquals(4, v.size());
        assertTrue(v.isNullable());
        assertEquals(2, v.getNullCount());
        assertFalse(v.isNullAt(0));
        assertTrue(v.isNullAt(1));
        assertEquals(10, v.getInt(0));
        assertNull(v.get(1));
        assertEquals(Integer.valueOf(10), v.get(0));
    }

    @Test
    public void testNullableIntVectorGetDoubleNaN() {
        System.out.println("NullableIntVector getDouble returns NaN for null");
        BitSet mask = new BitSet(2);
        mask.set(0);
        var v = new NullableIntVector("v", new int[]{0, 42}, mask);
        assertTrue(Double.isNaN(v.getDouble(0)));
        assertEquals(42.0, v.getDouble(1), 1e-10);
    }

    @Test
    public void testNullableIntVectorSet() {
        System.out.println("NullableIntVector set null");
        BitSet mask = new BitSet(3);
        var v = new NullableIntVector("v", new int[]{1, 2, 3}, mask);
        v.set(1, null);
        assertTrue(v.isNullAt(1));
        assertNull(v.get(1));
    }

    @Test
    public void testNullableIntVectorGetIndex() {
        System.out.println("NullableIntVector get(Index)");
        BitSet mask = new BitSet(4);
        mask.set(1);
        var v = new NullableIntVector("v", new int[]{10, 0, 30, 40}, mask);
        var s = v.get(Index.of(0, 1, 3));
        assertEquals(3, s.size());
        assertEquals(10, s.getInt(0));
        assertTrue(s.isNullAt(1));
        assertEquals(40, s.getInt(2));
    }

    @Test
    public void testNullableIntVectorFromFactory() {
        System.out.println("NullableIntVector from ValueVector.ofNullable");
        var v = ValueVector.ofNullable("v", 1, null, 3, null, 5);
        assertEquals(5, v.size());
        assertEquals(2, v.getNullCount());
        assertTrue(v.isNullAt(1));
        assertTrue(v.isNullAt(3));
        assertFalse(v.isNullAt(0));
        assertEquals(1, v.getInt(0));
        assertEquals(3, v.getInt(2));
    }

    @Test
    public void testNullableIntVectorStatistics() {
        System.out.println("NullableIntVector statistics (ignores nulls)");
        var v = ValueVector.ofNullable("v", 1, null, 3, null, 5);
        // sum of 1+3+5 = 9; mean = 3
        assertEquals(9.0, v.sum(),  1e-10);
        assertEquals(3.0, v.mean(), 1e-10);
    }

    // =========================================================================
    // NullableDoubleVector
    // =========================================================================

    @Test
    public void testNullableDoubleVectorBasics() {
        System.out.println("NullableDoubleVector basics");
        var v = ValueVector.ofNullable("v", 1.0, null, 3.0);
        assertEquals(3, v.size());
        assertTrue(v.isNullable());
        assertEquals(1, v.getNullCount());
        assertTrue(v.isNullAt(1));
        assertFalse(v.isNullAt(0));
        assertEquals(1.0, v.getDouble(0), 1e-10);
        assertTrue(Double.isNaN(v.getDouble(1)));
    }

    @Test
    public void testNullableDoubleVectorGetBoolean() {
        System.out.println("NullableDoubleVector getBoolean (bug fix)");
        BitSet mask = new BitSet(3);
        var v = new NullableDoubleVector("v", new double[]{0.0, 1.5, -0.5}, mask);
        assertFalse(v.getBoolean(0));  // 0 → false
        assertTrue(v.getBoolean(1));
        assertTrue(v.getBoolean(2));
    }

    @Test
    public void testNullableDoubleVectorFillna() {
        System.out.println("NullableDoubleVector fillna");
        BitSet mask = new BitSet(3);
        mask.set(1);
        var v = new NullableDoubleVector("v", new double[]{1.0, Double.NaN, 3.0}, mask);
        v.fillna(-1.0);
        assertFalse(v.isNullAt(1));
        assertEquals(-1.0, v.getDouble(1), 1e-10);
    }

    // =========================================================================
    // NullableFloatVector
    // =========================================================================

    @Test
    public void testNullableFloatVectorGetBoolean() {
        System.out.println("NullableFloatVector getBoolean (bug fix)");
        BitSet mask = new BitSet(3);
        var v = new NullableFloatVector("v", new float[]{0.0f, 1.5f, -0.1f}, mask);
        assertFalse(v.getBoolean(0));
        assertTrue(v.getBoolean(1));
        assertTrue(v.getBoolean(2));
    }

    @Test
    public void testNullableFloatVectorFromFactory() {
        System.out.println("NullableFloatVector from ValueVector.ofNullable");
        var v = ValueVector.ofNullable("v", 1.5f, null, 3.5f);
        assertEquals(3, v.size());
        assertTrue(v.isNullAt(1));
        assertEquals(1.5f, v.getFloat(0), 1e-6f);
        assertTrue(Float.isNaN(v.getFloat(1)));
    }

    // =========================================================================
    // StringVector
    // =========================================================================

    @Test
    public void testStringVectorBasics() {
        System.out.println("StringVector basics");
        var v = new StringVector("name", new String[]{"Alice", "Bob", null, "Dave"});
        assertEquals(4, v.size());
        assertEquals("name", v.name());
        assertEquals(DataTypes.StringType, v.dtype());
        assertTrue(v.isNullable());
        assertTrue(v.isNullAt(2));
        assertFalse(v.isNullAt(0));
        assertEquals("Alice", v.get(0));
        assertNull(v.get(2));
    }

    @Test
    public void testStringVectorGetNullCount() {
        System.out.println("StringVector getNullCount");
        var v = new StringVector("v", new String[]{"a", null, "c", null, "e"});
        assertEquals(2, v.getNullCount());
    }

    @Test
    public void testStringVectorParseNumeric() {
        System.out.println("StringVector parse numeric");
        var v = new StringVector("v", new String[]{"1", "2", "3"});
        assertArrayEquals(new int[]{1, 2, 3}, v.intStream().toArray());
        assertEquals(6.0, v.doubleStream().sum(), 1e-10);
    }

    @Test
    public void testStringVectorGetChar() {
        System.out.println("StringVector getChar");
        var v = new StringVector("v", new String[]{"ABC", ""});
        assertEquals('A', v.getChar(0));
        assertEquals('\u0000', v.getChar(1));
    }

    @Test
    public void testStringVectorNominal() {
        System.out.println("StringVector nominal()");
        var v = new StringVector("color", new String[]{"red", "blue", "red", "green"});
        NominalScale scale = v.nominal();
        assertEquals(3, scale.size());
        assertTrue(scale.contains("red"));
        assertTrue(scale.contains("blue"));
        assertTrue(scale.contains("green"));
    }

    @Test
    public void testStringVectorFactorize() {
        System.out.println("StringVector factorize");
        var v = new StringVector("color", new String[]{"red", "blue", "red", null});
        NominalScale scale = v.nominal();
        var fv = v.factorize(scale);
        assertEquals(4, fv.size());
        // null → -1
        assertEquals(-1, fv.getByte(3));
        // red and blue get valid byte codes
        assertEquals(fv.getByte(0), fv.getByte(2)); // both "red"
    }

    @Test
    public void testStringVectorGetIndex() {
        System.out.println("StringVector get(Index)");
        var v = new StringVector("v", new String[]{"a", "b", "c", "d"});
        var s = v.get(Index.of(1, 3));
        assertEquals(2, s.size());
        assertEquals("b", s.get(0));
        assertEquals("d", s.get(1));
    }

    // =========================================================================
    // ObjectVector
    // =========================================================================

    @Test
    public void testObjectVectorGetNullCountBugFix() {
        System.out.println("ObjectVector getNullCount bug fix (was counting non-null)");
        var v = new ObjectVector<>("v", new String[]{"a", null, "c", null, "e"});
        // Bug: was counting non-null (3), should count null (2)
        assertEquals(2, v.getNullCount());
    }

    @Test
    public void testObjectVectorNullCountAllPresent() {
        System.out.println("ObjectVector getNullCount all present");
        var v = new ObjectVector<>("v", new Integer[]{1, 2, 3});
        assertEquals(0, v.getNullCount());
        assertFalse(v.anyNull());
    }

    @Test
    public void testObjectVectorNullCountAllNull() {
        System.out.println("ObjectVector getNullCount all null");
        var v = new ObjectVector<>("v", new String[]{null, null});
        assertEquals(2, v.getNullCount());
        assertTrue(v.anyNull());
    }

    @Test
    public void testObjectVectorIsNullAt() {
        System.out.println("ObjectVector isNullAt");
        var v = new ObjectVector<>("v", new String[]{"a", null, "c"});
        assertFalse(v.isNullAt(0));
        assertTrue(v.isNullAt(1));
        assertFalse(v.isNullAt(2));
    }

    @Test
    public void testObjectVectorDistinct() {
        System.out.println("ObjectVector distinct");
        var v = new ObjectVector<>("v", new String[]{"a", "b", "a", "c", "b"});
        var distinct = v.distinct();
        assertEquals(3, distinct.size());
    }

    // =========================================================================
    // NumberVector
    // =========================================================================

    @Test
    public void testNumberVectorBasics() {
        System.out.println("NumberVector basics");
        var v = new NumberVector<>(new StructField("v", DataTypes.DecimalType),
                new java.math.BigDecimal[]{java.math.BigDecimal.ONE, java.math.BigDecimal.TEN});
        assertEquals(2, v.size());
        assertEquals(5.5, v.mean(), 1e-10);
        assertEquals(10.0, v.max(), 1e-10);
    }

    // =========================================================================
    // ValueVector.eq / ne (null-safety bug fix)
    // =========================================================================

    @Test
    public void testEqNullSafe() {
        System.out.println("ValueVector.eq null-safe (bug fix)");
        var v = new StringVector("v", new String[]{"a", null, "a"});
        boolean[] result = v.eq("a");
        assertTrue(result[0]);
        assertFalse(result[1]);  // null != "a", no NPE
        assertTrue(result[2]);
    }

    @Test
    public void testNeNullSafe() {
        System.out.println("ValueVector.ne null-safe (bug fix)");
        var v = new StringVector("v", new String[]{"a", null, "b"});
        boolean[] result = v.ne("a");
        assertFalse(result[0]);
        assertTrue(result[1]);  // null != "a"
        assertTrue(result[2]);
    }

    @Test
    public void testEqVectorNullSafe() {
        System.out.println("ValueVector.eq vector null-safe");
        var v1 = new StringVector("v", new String[]{"a", null, "c"});
        var v2 = new StringVector("v", new String[]{"a", null, "x"});
        boolean[] result = v1.eq(v2);
        assertTrue(result[0]);
        assertTrue(result[1]);  // null == null
        assertFalse(result[2]);
    }

    @Test
    public void testEqVectorSizeMismatchThrows() {
        System.out.println("ValueVector.eq size mismatch throws");
        var v1 = new IntVector("v", new int[]{1, 2});
        var v2 = new IntVector("v", new int[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> v1.eq(v2));
    }

    // =========================================================================
    // Comparison operators (lt, le, gt, ge)
    // =========================================================================

    @Test
    public void testLtLeGtGe() {
        System.out.println("ValueVector comparison operators");
        var v = new DoubleVector("v", new double[]{1.0, 2.0, 3.0, 4.0, 5.0});
        boolean[] lt3 = v.lt(3.0);
        assertArrayEquals(new boolean[]{true, true, false, false, false}, lt3);

        boolean[] le3 = v.le(3.0);
        assertArrayEquals(new boolean[]{true, true, true, false, false}, le3);

        boolean[] gt3 = v.gt(3.0);
        assertArrayEquals(new boolean[]{false, false, false, true, true}, gt3);

        boolean[] ge3 = v.ge(3.0);
        assertArrayEquals(new boolean[]{false, false, true, true, true}, ge3);
    }

    // =========================================================================
    // isin
    // =========================================================================

    @Test
    public void testIsinString() {
        System.out.println("ValueVector.isin(String...)");
        var v = new StringVector("v", new String[]{"a", "b", "c", "d"});
        boolean[] result = v.isin("a", "c");
        assertArrayEquals(new boolean[]{true, false, true, false}, result);
    }

    // =========================================================================
    // isNull / anyNull
    // =========================================================================

    @Test
    public void testIsNull() {
        System.out.println("ValueVector.isNull");
        var v = ValueVector.ofNullable("v", 1, null, 3, null);
        boolean[] nulls = v.isNull();
        assertArrayEquals(new boolean[]{false, true, false, true}, nulls);
    }

    @Test
    public void testAnyNullPrimitive() {
        System.out.println("PrimitiveVector anyNull always false");
        var v = new IntVector("v", new int[]{1, 2, 3});
        assertFalse(v.anyNull());
    }

    // =========================================================================
    // toArray methods
    // =========================================================================

    @Test
    public void testToIntArray() {
        System.out.println("ValueVector.toIntArray");
        var v = new IntVector("v", new int[]{3, 1, 4, 1, 5});
        assertArrayEquals(new int[]{3, 1, 4, 1, 5}, v.toIntArray());
    }

    @Test
    public void testToDoubleArray() {
        System.out.println("ValueVector.toDoubleArray");
        var v = new DoubleVector("v", new double[]{1.0, 2.0, 3.0});
        assertArrayEquals(new double[]{1.0, 2.0, 3.0}, v.toDoubleArray(), 1e-10);
    }

    @Test
    public void testToStringArray() {
        System.out.println("ValueVector.toStringArray");
        var v = new IntVector("v", new int[]{1, 2, 3});
        String[] arr = v.toStringArray();
        assertArrayEquals(new String[]{"1", "2", "3"}, arr);
    }

    // =========================================================================
    // Nominal / Ordinal vectors
    // =========================================================================

    @Test
    public void testNominalStringVector() {
        System.out.println("ValueVector.nominal(String...)");
        var v = ValueVector.nominal("color", "red", "blue", "red", "green");
        assertNotNull(v.measure());
        assertTrue(v.measure() instanceof NominalScale);
        assertEquals(DataTypes.ByteType, v.dtype());
        // "red" appears twice with same code
        assertEquals(v.getByte(0), v.getByte(2));
    }

    @Test
    public void testOrdinalStringVector() {
        System.out.println("ValueVector.ordinal(String...)");
        var v = ValueVector.ordinal("rank", "low", "mid", "high", "low");
        assertTrue(v.measure() instanceof OrdinalScale);
    }

    enum Color { RED, GREEN, BLUE }

    @Test
    public void testNominalEnumVector() {
        System.out.println("ValueVector.nominal(Enum...)");
        var v = ValueVector.nominal("c", Color.RED, Color.GREEN, Color.BLUE, Color.RED);
        assertTrue(v.measure() instanceof NominalScale);
        assertEquals(DataTypes.ByteType, v.dtype());
        assertEquals(v.getByte(0), v.getByte(3)); // both RED
    }

    @Test
    public void testOrdinalEnumVector() {
        System.out.println("ValueVector.ordinal(Enum...)");
        var v = ValueVector.ordinal("c", Color.RED, Color.GREEN, Color.BLUE);
        assertTrue(v.measure() instanceof OrdinalScale);
        assertEquals(3, v.size());
    }

    // =========================================================================
    // AbstractVector toString
    // =========================================================================

    @Test
    public void testToStringShortVector() {
        System.out.println("AbstractVector toString short vector");
        var v = new IntVector("nums", new int[]{1, 2, 3});
        String s = v.toString();
        assertTrue(s.startsWith("nums["));
        assertTrue(s.contains("1"));
    }

    @Test
    public void testToStringLongVector() {
        System.out.println("AbstractVector toString long vector shows ellipsis");
        var v = new IntVector("nums", new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11});
        String s = v.toString();
        assertTrue(s.contains("more"), "Long vector should show '...N more', got: " + s);
    }

    // =========================================================================
    // getScale
    // =========================================================================

    @Test
    public void testGetScale() {
        System.out.println("ValueVector.getScale with NominalScale");
        NominalScale scale = new NominalScale("Male", "Female");
        var field = new StructField("gender", DataTypes.ByteType, scale);
        var v = new ByteVector(field, new byte[]{0, 1, 0});
        assertEquals("Male",   v.getScale(0));
        assertEquals("Female", v.getScale(1));
        assertEquals("Male",   v.getScale(2));
    }

    // =========================================================================
    // Nullable vector factory methods
    // =========================================================================

    @Test
    public void testNullableBooleanFromFactory() {
        System.out.println("ValueVector.ofNullable(Boolean...)");
        var v = ValueVector.ofNullable("v", true, null, false);
        assertEquals(3, v.size());
        assertEquals(1, v.getNullCount());
        assertTrue(v.isNullAt(1));
        assertTrue(v.getBoolean(0));
        assertFalse(v.getBoolean(2));
    }

    @Test
    public void testNullableCharFromFactory() {
        System.out.println("ValueVector.ofNullable(Character...)");
        var v = ValueVector.ofNullable("v", 'A', null, 'C');
        assertEquals(3, v.size());
        assertEquals(1, v.getNullCount());
        assertEquals('A', v.getChar(0));
        assertTrue(v.isNullAt(1));
    }

    @Test
    public void testNullableByteFromFactory() {
        System.out.println("ValueVector.ofNullable(Byte...)");
        var v = ValueVector.ofNullable("v", (byte) 1, null, (byte) 3);
        assertEquals(1, v.getNullCount());
        assertEquals((byte) 1, v.getByte(0));
    }

    @Test
    public void testNullableShortFromFactory() {
        System.out.println("ValueVector.ofNullable(Short...)");
        var v = ValueVector.ofNullable("v", (short) 100, null, (short) 300);
        assertEquals(1, v.getNullCount());
        assertEquals((short) 100, v.getShort(0));
    }

    @Test
    public void testNullableLongFromFactory() {
        System.out.println("ValueVector.ofNullable(Long...)");
        var v = ValueVector.ofNullable("v", 1L, null, 3L);
        assertEquals(1, v.getNullCount());
        assertEquals(1L, v.getLong(0));
        assertTrue(v.isNullAt(1));
    }

    // =========================================================================
    // Nullable vector statistics – nulls must be excluded
    // =========================================================================

    @Test
    public void testNullableByteStatistics() {
        System.out.println("NullableByteVector statistics (ignores nulls)");
        var v = ValueVector.ofNullable("v", (byte) 2, null, (byte) 8);
        // valid: 2, 8 → sum=10, mean=5
        assertEquals(10.0, v.sum(),  1e-10);
        assertEquals(5.0,  v.mean(), 1e-10);
    }

    @Test
    public void testNullableShortStatistics() {
        System.out.println("NullableShortVector statistics (ignores nulls)");
        var v = ValueVector.ofNullable("v", (short) 4, null, (short) 6);
        assertEquals(10.0, v.sum(),  1e-10);
        assertEquals(5.0,  v.mean(), 1e-10);
    }

    @Test
    public void testNullableLongStatistics() {
        System.out.println("NullableLongVector statistics (ignores nulls)");
        var v = ValueVector.ofNullable("v", 3L, null, 7L);
        assertEquals(10.0, v.sum(),  1e-10);
        assertEquals(5.0,  v.mean(), 1e-10);
    }

    @Test
    public void testNullableFloatStatistics() {
        System.out.println("NullableFloatVector statistics (ignores nulls)");
        var v = ValueVector.ofNullable("v", 2.0f, null, 8.0f);
        assertEquals(10.0, v.sum(),  1e-10);
        assertEquals(5.0,  v.mean(), 1e-10);
    }

    @Test
    public void testNullableDoubleStatistics() {
        System.out.println("NullableDoubleVector statistics (ignores nulls)");
        var v = ValueVector.ofNullable("v", 1.0, null, 3.0, null, 5.0);
        assertEquals(9.0, v.sum(),  1e-10);
        assertEquals(3.0, v.mean(), 1e-10);
        assertEquals(1.0, v.min(),  1e-10);
        assertEquals(5.0, v.max(),  1e-10);
    }

    // =========================================================================
    // Nullable vector set() – must clear null bit
    // =========================================================================

    @Test
    public void testNullableByteSetClearsNullBit() {
        System.out.println("NullableByteVector.set clears null bit");
        BitSet mask = new BitSet(3);
        mask.set(1);
        var v = new NullableByteVector("v", new byte[]{1, 0, 3}, mask);
        assertTrue(v.isNullAt(1));
        v.set(1, (byte) 42);
        assertFalse(v.isNullAt(1));
        assertEquals((byte) 42, v.getByte(1));
    }

    @Test
    public void testNullableShortSetClearsNullBit() {
        System.out.println("NullableShortVector.set clears null bit");
        BitSet mask = new BitSet(2);
        mask.set(0);
        var v = new NullableShortVector("v", new short[]{0, 200}, mask);
        assertTrue(v.isNullAt(0));
        v.set(0, (short) 100);
        assertFalse(v.isNullAt(0));
        assertEquals((short) 100, v.getShort(0));
    }

    @Test
    public void testNullableIntSetClearsNullBit() {
        System.out.println("NullableIntVector.set clears null bit");
        BitSet mask = new BitSet(2);
        mask.set(0);
        var v = new NullableIntVector("v", new int[]{0, 99}, mask);
        assertTrue(v.isNullAt(0));
        v.set(0, 55);
        assertFalse(v.isNullAt(0));
        assertEquals(55, v.getInt(0));
    }

    @Test
    public void testNullableLongSetClearsNullBit() {
        System.out.println("NullableLongVector.set clears null bit");
        BitSet mask = new BitSet(2);
        mask.set(1);
        var v = new NullableLongVector("v", new long[]{1L, 0L}, mask);
        assertTrue(v.isNullAt(1));
        v.set(1, 99L);
        assertFalse(v.isNullAt(1));
        assertEquals(99L, v.getLong(1));
    }

    @Test
    public void testNullableFloatSetClearsNullBit() {
        System.out.println("NullableFloatVector.set clears null bit");
        BitSet mask = new BitSet(2);
        mask.set(0);
        var v = new NullableFloatVector("v", new float[]{Float.NaN, 2.0f}, mask);
        assertTrue(v.isNullAt(0));
        v.set(0, 1.5f);
        assertFalse(v.isNullAt(0));
        assertEquals(1.5f, v.getFloat(0), 1e-6f);
    }

    @Test
    public void testNullableDoubleSetClearsNullBit() {
        System.out.println("NullableDoubleVector.set clears null bit");
        BitSet mask = new BitSet(2);
        mask.set(0);
        var v = new NullableDoubleVector("v", new double[]{Double.NaN, 2.0}, mask);
        assertTrue(v.isNullAt(0));
        v.set(0, 3.14);
        assertFalse(v.isNullAt(0));
        assertEquals(3.14, v.getDouble(0), 1e-10);
    }

    @Test
    public void testNullableBooleanSetClearsNullBit() {
        System.out.println("NullableBooleanVector.set clears null bit");
        BitSet mask = new BitSet(2);
        mask.set(0);
        var v = new NullableBooleanVector("v", new boolean[]{false, true}, mask);
        assertTrue(v.isNullAt(0));
        v.set(0, Boolean.TRUE);
        assertFalse(v.isNullAt(0));
        assertTrue(v.getBoolean(0));
    }

    // =========================================================================
    // fillna – null-bit must be cleared for nullable vectors
    // =========================================================================

    @Test
    public void testNullableIntFillna() {
        System.out.println("NullableIntVector fillna clears null bit");
        var v = ValueVector.ofNullable("v", 1, null, 3);
        assertTrue(v.isNullAt(1));
        v.fillna(0.0);
        assertFalse(v.isNullAt(1));
        assertEquals(0, v.getInt(1));
        assertEquals(0, v.getNullCount());
    }

    @Test
    public void testNullableShortFillna() {
        System.out.println("NullableShortVector fillna clears null bit");
        var v = ValueVector.ofNullable("v", (short) 10, null, (short) 30);
        v.fillna(0.0);
        assertFalse(v.isNullAt(1));
        assertEquals(0, v.getShort(1));
    }

    @Test
    public void testNullableByteFillna() {
        System.out.println("NullableByteVector fillna clears null bit");
        var v = ValueVector.ofNullable("v", (byte) 5, null, (byte) 15);
        v.fillna(0.0);
        assertFalse(v.isNullAt(1));
        assertEquals(0, v.getByte(1));
    }

    @Test
    public void testNullableLongFillna() {
        System.out.println("NullableLongVector fillna clears null bit");
        var v = ValueVector.ofNullable("v", 10L, null, 30L);
        v.fillna(0.0);
        assertFalse(v.isNullAt(1));
        assertEquals(0L, v.getLong(1));
    }

    @Test
    public void testNullableFloatFillna() {
        System.out.println("NullableFloatVector fillna clears null bit");
        BitSet mask = new BitSet(3);
        mask.set(1);
        var v = new NullableFloatVector("v", new float[]{1.0f, Float.NaN, 3.0f}, mask);
        v.fillna(0.0f);
        assertFalse(v.isNullAt(1));
        assertEquals(0.0f, v.getFloat(1), 1e-6f);
    }

    // =========================================================================
    // NullableBooleanVector statistics (nulls excluded via doubleStream NaN)
    // =========================================================================

    @Test
    public void testNullableBooleanStatistics() {
        System.out.println("NullableBooleanVector statistics (ignores nulls)");
        // true=1, null, false=0, true=1 → valid: {1, 0, 1}, mean=2/3
        BitSet mask = new BitSet(4);
        mask.set(1);
        var v = new NullableBooleanVector("v", new boolean[]{true, false, false, true}, mask);
        assertEquals(2.0, v.sum(),  1e-10);
        assertEquals(2.0/3, v.mean(), 1e-10);
    }

    // =========================================================================
    // Nullable double stream returns NaN for null (intStream sentinel MIN_VALUE
    // must NOT appear in doubleStream)
    // =========================================================================

    @Test
    public void testNullableIntDoubleStreamNaN() {
        System.out.println("NullableIntVector doubleStream returns NaN for null");
        BitSet mask = new BitSet(3);
        mask.set(1);
        var v = new NullableIntVector("v", new int[]{1, 0, 3}, mask);
        double[] arr = v.doubleStream().toArray();
        assertEquals(1.0, arr[0], 1e-10);
        assertTrue(Double.isNaN(arr[1]));
        assertEquals(3.0, arr[2], 1e-10);
    }

    @Test
    public void testNullableLongDoubleStreamNaN() {
        System.out.println("NullableLongVector doubleStream returns NaN for null");
        BitSet mask = new BitSet(2);
        mask.set(0);
        var v = new NullableLongVector("v", new long[]{0L, 42L}, mask);
        double[] arr = v.doubleStream().toArray();
        assertTrue(Double.isNaN(arr[0]));
        assertEquals(42.0, arr[1], 1e-10);
    }

    @Test
    public void testNullableByteDoubleStreamNaN() {
        System.out.println("NullableByteVector doubleStream returns NaN for null");
        BitSet mask = new BitSet(2);
        mask.set(1);
        var v = new NullableByteVector("v", new byte[]{5, 0}, mask);
        double[] arr = v.doubleStream().toArray();
        assertEquals(5.0, arr[0], 1e-10);
        assertTrue(Double.isNaN(arr[1]));
    }

    @Test
    public void testNullableShortDoubleStreamNaN() {
        System.out.println("NullableShortVector doubleStream returns NaN for null");
        BitSet mask = new BitSet(2);
        mask.set(0);
        var v = new NullableShortVector("v", new short[]{0, 7}, mask);
        double[] arr = v.doubleStream().toArray();
        assertTrue(Double.isNaN(arr[0]));
        assertEquals(7.0, arr[1], 1e-10);
    }

    // =========================================================================
    // IntVector get(Index) – must not autobox
    // =========================================================================

    @Test
    public void testIntVectorGetIndexCorrect() {
        System.out.println("IntVector get(Index) returns correct values");
        var v = new IntVector("v", new int[]{10, 20, 30, 40, 50});
        var s = v.get(Index.of(4, 2, 0));
        assertEquals(3, s.size());
        assertEquals(50, s.getInt(0));
        assertEquals(30, s.getInt(1));
        assertEquals(10, s.getInt(2));
    }

    // =========================================================================
    // LongVector statistics
    // =========================================================================

    @Test
    public void testLongVectorStatistics() {
        System.out.println("LongVector statistics");
        var v = new LongVector("v", new long[]{1L, 2L, 3L, 4L, 5L});
        assertEquals(3.0,  v.mean(),   1e-10);
        assertEquals(15.0, v.sum(),    1e-10);
        assertEquals(1.0,  v.min(),    1e-10);
        assertEquals(5.0,  v.max(),    1e-10);
        assertEquals(3.0,  v.median(), 1e-10);
    }

    // =========================================================================
    // FloatVector statistics
    // =========================================================================

    @Test
    public void testFloatVectorStatistics() {
        System.out.println("FloatVector statistics");
        var v = new FloatVector("v", new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f});
        assertEquals(3.0,  v.mean(),   1e-5);
        assertEquals(15.0, v.sum(),    1e-5);
        assertEquals(5.0,  v.max(),    1e-5);
    }

    // =========================================================================
    // ShortVector / ByteVector statistics
    // =========================================================================

    @Test
    public void testShortVectorStatistics() {
        System.out.println("ShortVector statistics");
        var v = new ShortVector("v", new short[]{2, 4, 6, 8, 10});
        assertEquals(6.0,  v.mean(), 1e-10);
        assertEquals(30.0, v.sum(),  1e-10);
    }

    @Test
    public void testByteVectorStatistics() {
        System.out.println("ByteVector statistics");
        var v = new ByteVector("v", new byte[]{10, 20, 30});
        assertEquals(20.0, v.mean(), 1e-10);
        assertEquals(60.0, v.sum(),  1e-10);
    }
}

