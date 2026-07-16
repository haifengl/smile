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
package smile.data.type;

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.time.*;
import java.util.HashSet;
import java.util.Set;
import smile.data.measure.NominalScale;
import smile.data.measure.RatioScale;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for smile.data.type package.
 *
 * @author Haifeng Li
 */
public class DataTypeFullTest {

    @BeforeAll
    public static void setUpClass() {}
    @AfterAll
    public static void tearDownClass() {}
    @BeforeEach
    public void setUp() {}
    @AfterEach
    public void tearDown() {}

    // =========================================================================
    // PrimitiveType – all eight primitive types, both nullable and non-nullable
    // =========================================================================

    @Test
    public void testBooleanType() {
        System.out.println("BooleanType");
        var t = DataTypes.BooleanType;
        assertEquals(DataType.ID.Boolean, t.id());
        assertEquals("boolean", t.name());
        assertTrue(t.isBoolean());
        assertTrue(t.isPrimitive());
        assertFalse(t.isNullable());
        assertEquals(Boolean.TRUE,  t.valueOf("true"));
        assertEquals(Boolean.FALSE, t.valueOf("false"));
        assertEquals(t, DataTypes.BooleanType);
        assertNotEquals(t, DataTypes.NullableBooleanType);
    }

    @Test
    public void testNullableBooleanType() {
        System.out.println("NullableBooleanType");
        var t = DataTypes.NullableBooleanType;
        assertTrue(t.isNullable());
        assertEquals("Boolean", t.name());
        assertEquals(t, DataTypes.NullableBooleanType);
        assertNotEquals(t, DataTypes.BooleanType);
    }

    @Test
    public void testByteType() {
        System.out.println("ByteType");
        var t = DataTypes.ByteType;
        assertEquals(DataType.ID.Byte, t.id());
        assertTrue(t.isByte());
        assertTrue(t.isIntegral());
        assertTrue(t.isNumeric());
        assertFalse(t.isNullable());
        assertEquals((byte) 42, t.valueOf("42"));
    }

    @Test
    public void testCharType() {
        System.out.println("CharType");
        var t = DataTypes.CharType;
        assertEquals(DataType.ID.Char, t.id());
        assertTrue(t.isChar());
        assertFalse(t.isNullable());
        assertEquals('A', t.valueOf("A"));
    }

    @Test
    public void testShortType() {
        System.out.println("ShortType");
        var t = DataTypes.ShortType;
        assertEquals(DataType.ID.Short, t.id());
        assertTrue(t.isShort());
        assertTrue(t.isIntegral());
        assertFalse(t.isNullable());
        assertEquals((short) 1000, t.valueOf("1000"));
    }

    @Test
    public void testIntType() {
        System.out.println("IntType");
        var t = DataTypes.IntType;
        assertEquals(DataType.ID.Int, t.id());
        assertTrue(t.isInt());
        assertTrue(t.isIntegral());
        assertTrue(t.isNumeric());
        assertFalse(t.isNullable());
        assertEquals(42, t.valueOf("42"));
    }

    @Test
    public void testNullableIntType() {
        System.out.println("NullableIntType");
        var t = DataTypes.NullableIntType;
        assertTrue(t.isNullable());
        assertTrue(t.isInt());
        assertEquals("Int", t.name());
    }

    @Test
    public void testLongType() {
        System.out.println("LongType");
        var t = DataTypes.LongType;
        assertEquals(DataType.ID.Long, t.id());
        assertTrue(t.isLong());
        assertTrue(t.isIntegral());
        assertFalse(t.isNullable());
        assertEquals(123456789012345L, t.valueOf("123456789012345"));
    }

    @Test
    public void testFloatType() {
        System.out.println("FloatType");
        var t = DataTypes.FloatType;
        assertEquals(DataType.ID.Float, t.id());
        assertTrue(t.isFloat());
        assertTrue(t.isFloating());
        assertTrue(t.isNumeric());
        assertFalse(t.isNullable());
        assertEquals(3.14f, (Float) t.valueOf("3.14"), 1e-5f);
    }

    @Test
    public void testDoubleType() {
        System.out.println("DoubleType");
        var t = DataTypes.DoubleType;
        assertEquals(DataType.ID.Double, t.id());
        assertTrue(t.isDouble());
        assertTrue(t.isFloating());
        assertTrue(t.isNumeric());
        assertFalse(t.isNullable());
        assertEquals(3.141592, (Double) t.valueOf("3.141592"), 1e-10);
    }

    @Test
    public void testDoubleTypeToString() {
        System.out.println("DoubleType toString formatting");
        var t = DataTypes.DoubleType;
        String s = t.toString(3.141592653589793);
        // Should round to 6 decimal places
        assertTrue(s.contains("3.14159"), "Expected formatted string, got: " + s);
    }

    @Test
    public void testNullableDoubleType() {
        System.out.println("NullableDoubleType");
        var t = DataTypes.NullableDoubleType;
        assertTrue(t.isNullable());
        assertTrue(t.isDouble());
        assertEquals("Double", t.name());
    }

    // =========================================================================
    // PrimitiveType hashCode contract
    // =========================================================================

    @Test
    public void testPrimitiveTypeHashCodeConsistency() {
        System.out.println("PrimitiveType hashCode consistency");
        // Equal objects must have same hashCode
        assertEquals(DataTypes.IntType.hashCode(), DataTypes.IntType.hashCode());
        assertEquals(DataTypes.DoubleType.hashCode(), DataTypes.DoubleType.hashCode());
        // Non-nullable and nullable versions must differ in hashCode
        assertNotEquals(DataTypes.IntType.hashCode(), DataTypes.NullableIntType.hashCode());
        assertNotEquals(DataTypes.DoubleType.hashCode(), DataTypes.NullableDoubleType.hashCode());
    }

    @Test
    public void testPrimitiveTypeInHashSet() {
        System.out.println("PrimitiveType in HashSet");
        Set<DataType> set = new HashSet<>();
        set.add(DataTypes.IntType);
        set.add(DataTypes.DoubleType);
        set.add(DataTypes.IntType); // duplicate
        assertEquals(2, set.size());
        assertTrue(set.contains(DataTypes.IntType));
        assertTrue(set.contains(DataTypes.DoubleType));
    }

    // =========================================================================
    // StringType
    // =========================================================================

    @Test
    public void testStringType() {
        System.out.println("StringType");
        var t = DataTypes.StringType;
        assertEquals(DataType.ID.String, t.id());
        assertEquals("String", t.name());
        assertTrue(t.isString());
        assertTrue(t.isObject());
        assertFalse(t.isPrimitive());
        assertTrue(t.isNullable());
        assertEquals("hello", t.valueOf("hello"));
        assertEquals(t, DataTypes.StringType);
        assertEquals(t.hashCode(), DataTypes.StringType.hashCode());
    }

    // =========================================================================
    // DecimalType
    // =========================================================================

    @Test
    public void testDecimalType() {
        System.out.println("DecimalType");
        var t = DataTypes.DecimalType;
        assertEquals(DataType.ID.Decimal, t.id());
        assertTrue(t.isDecimal());
        assertTrue(t.isNumeric());
        assertEquals("Decimal", t.name());
        assertEquals(new BigDecimal("3.14159"), t.valueOf("3.14159"));
        assertEquals(t, DataTypes.DecimalType);
        assertEquals(t.hashCode(), DataTypes.DecimalType.hashCode());
    }

    // =========================================================================
    // DateType / TimeType / DateTimeType
    // =========================================================================

    @Test
    public void testDateType() {
        System.out.println("DateType");
        var t = DataTypes.DateType;
        assertEquals(DataType.ID.Date, t.id());
        assertEquals("Date", t.name());
        LocalDate d = LocalDate.of(2024, 3, 15);
        assertEquals(d, t.valueOf("2024-03-15"));
        assertEquals("2024-03-15", t.toString(d));
        assertEquals(t, DataTypes.DateType);
        assertEquals(t.hashCode(), DataTypes.DateType.hashCode());
    }

    @Test
    public void testTimeType() {
        System.out.println("TimeType");
        var t = DataTypes.TimeType;
        assertEquals(DataType.ID.Time, t.id());
        assertEquals("Time", t.name());
        LocalTime time = LocalTime.of(14, 30, 45);
        assertEquals(time, t.valueOf("14:30:45"));
        String s = t.toString(time);
        assertTrue(s.startsWith("14:30:45"), "Expected time string, got: " + s);
        assertEquals(t, DataTypes.TimeType);
        assertEquals(t.hashCode(), DataTypes.TimeType.hashCode());
    }

    @Test
    public void testTimeTypeOffsetTime() {
        System.out.println("TimeType OffsetTime toString");
        var t = DataTypes.TimeType;
        OffsetTime ot = OffsetTime.of(10, 0, 0, 0, ZoneOffset.ofHours(5));
        String s = t.toString(ot);
        assertNotNull(s);
        assertTrue(s.contains("10:00:00"), "Expected offset time string, got: " + s);
    }

    @Test
    public void testDateTimeType() {
        System.out.println("DateTimeType");
        var t = DataTypes.DateTimeType;
        assertEquals(DataType.ID.DateTime, t.id());
        assertEquals("DateTime", t.name());
        LocalDateTime dt = LocalDateTime.of(2024, 3, 15, 14, 30, 45);
        // Fix: valueOf now uses ISO_LOCAL_DATE_TIME
        assertEquals(dt, t.valueOf("2024-03-15T14:30:45"));
        String s = t.toString(dt);
        assertTrue(s.contains("2024-03-15"), "Expected datetime string, got: " + s);
        assertEquals(t, DataTypes.DateTimeType);
        assertEquals(t.hashCode(), DataTypes.DateTimeType.hashCode());
    }

    @Test
    public void testDateTimeTypeInstant() {
        System.out.println("DateTimeType Instant toString");
        var t = DataTypes.DateTimeType;
        Instant inst = Instant.parse("2024-03-15T14:30:45Z");
        String s = t.toString(inst);
        assertTrue(s.contains("2024-03-15"), "Expected instant string, got: " + s);
    }

    @Test
    public void testDateTimeTypeZonedDateTime() {
        System.out.println("DateTimeType ZonedDateTime toString");
        var t = DataTypes.DateTimeType;
        ZonedDateTime zdt = ZonedDateTime.of(2024, 3, 15, 14, 30, 45, 0, ZoneOffset.UTC);
        String s = t.toString(zdt);
        assertNotNull(s);
        assertTrue(s.contains("2024-03-15"), "Expected zoned datetime string, got: " + s);
    }

    // =========================================================================
    // ArrayType
    // =========================================================================

    @Test
    public void testArrayTypeInt() {
        System.out.println("ArrayType int");
        var t = DataTypes.IntArrayType;
        assertEquals(DataType.ID.Array, t.id());
        assertEquals("Array[int]", t.name());
        assertEquals(DataTypes.IntType, t.getComponentType());
        assertEquals("[1, 2, 3]", t.toString(new int[]{1, 2, 3}));
    }

    @Test
    public void testArrayTypeDouble() {
        System.out.println("ArrayType double");
        var t = DataTypes.DoubleArrayType;
        assertEquals("Array[double]", t.name());
        assertEquals("[1.5, 2.5]", t.toString(new double[]{1.5, 2.5}));
    }

    @Test
    public void testArrayTypeBoolean() {
        System.out.println("ArrayType boolean");
        assertEquals("[true, false]", DataTypes.BooleanArrayType.toString(new boolean[]{true, false}));
    }

    @Test
    public void testArrayTypeByte() {
        System.out.println("ArrayType byte");
        assertEquals("[1, 2]", DataTypes.ByteArrayType.toString(new byte[]{1, 2}));
    }

    @Test
    public void testArrayTypeChar() {
        System.out.println("ArrayType char");
        assertEquals("[a, b]", DataTypes.CharArrayType.toString(new char[]{'a', 'b'}));
    }

    @Test
    public void testArrayTypeLong() {
        System.out.println("ArrayType long");
        assertEquals("[1, 2]", DataTypes.LongArrayType.toString(new long[]{1L, 2L}));
    }

    @Test
    public void testArrayTypeFloat() {
        System.out.println("ArrayType float");
        assertEquals("[1.0, 2.0]", DataTypes.FloatArrayType.toString(new float[]{1.0f, 2.0f}));
    }

    @Test
    public void testArrayTypeEquals() {
        System.out.println("ArrayType equals");
        assertEquals(DataTypes.IntArrayType, DataTypes.IntArrayType);
        assertNotEquals(DataTypes.IntArrayType, DataTypes.DoubleArrayType);
        // Nullable array type equality uses .equals(), not ==
        var nullable = DataTypes.array(DataTypes.NullableIntType);
        var nullable2 = DataTypes.array(DataTypes.NullableIntType);
        assertEquals(nullable, nullable2);
        assertNotEquals(nullable, DataTypes.IntArrayType);
    }

    @Test
    public void testArrayTypeHashCode() {
        System.out.println("ArrayType hashCode");
        assertEquals(DataTypes.IntArrayType.hashCode(), DataTypes.IntArrayType.hashCode());
        var t1 = DataTypes.array(DataTypes.NullableDoubleType);
        var t2 = DataTypes.array(DataTypes.NullableDoubleType);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    public void testArrayTypeInHashSet() {
        System.out.println("ArrayType in HashSet");
        Set<DataType> set = new HashSet<>();
        set.add(DataTypes.IntArrayType);
        set.add(DataTypes.DoubleArrayType);
        set.add(DataTypes.IntArrayType); // duplicate
        assertEquals(2, set.size());
    }

    // =========================================================================
    // ObjectType
    // =========================================================================

    @Test
    public void testObjectTypeBasic() {
        System.out.println("ObjectType basic");
        var t = DataTypes.object(Integer.class);
        assertEquals(DataType.ID.Object, t.id());
        assertTrue(t.isObject());
        assertTrue(t.isInt());
        assertEquals("Class<java.lang.Integer>", t.name());
    }

    @Test
    public void testObjectTypeLongFormat() {
        System.out.println("ObjectType Long format (bug fix: was using IntType::toString)");
        var t = DataTypes.object(Long.class);
        assertTrue(t.isLong());
        // Should format as Long, not truncate to int
        String s = t.toString(Long.MAX_VALUE);
        assertEquals(Long.toString(Long.MAX_VALUE), s);
    }

    @Test
    public void testObjectTypeDoubleFormat() {
        System.out.println("ObjectType Double format");
        var t = DataTypes.object(Double.class);
        assertTrue(t.isDouble());
        String s = t.toString(3.14);
        assertNotNull(s);
        assertTrue(s.contains("3.14"), "Expected double formatted, got: " + s);
    }

    @Test
    public void testObjectTypeFloatFormat() {
        System.out.println("ObjectType Float format");
        var t = DataTypes.object(Float.class);
        assertTrue(t.isFloat());
        String s = t.toString(2.5f);
        assertNotNull(s);
    }

    @Test
    public void testObjectTypeEquals() {
        System.out.println("ObjectType equals");
        assertEquals(DataTypes.object(Integer.class), DataTypes.object(Integer.class));
        assertNotEquals(DataTypes.object(Integer.class), DataTypes.object(Long.class));
    }

    @Test
    public void testObjectTypeHashCode() {
        System.out.println("ObjectType hashCode");
        assertEquals(
            DataTypes.object(Integer.class).hashCode(),
            DataTypes.object(Integer.class).hashCode()
        );
    }

    @Test
    public void testObjectTypeInHashSet() {
        System.out.println("ObjectType in HashSet");
        Set<DataType> set = new HashSet<>();
        set.add(DataTypes.object(Integer.class));
        set.add(DataTypes.object(Long.class));
        set.add(DataTypes.object(Integer.class)); // duplicate
        assertEquals(2, set.size());
    }

    // =========================================================================
    // DataType.of(String) – round-trip serialization
    // =========================================================================

    @Test
    public void testOfStringAllPrimitives() throws Exception {
        System.out.println("DataType.of(String) primitives");
        assertEquals(DataTypes.BooleanType,  DataType.of("boolean"));
        assertEquals(DataTypes.CharType,     DataType.of("char"));
        assertEquals(DataTypes.ByteType,     DataType.of("byte"));
        assertEquals(DataTypes.ShortType,    DataType.of("short"));
        assertEquals(DataTypes.IntType,      DataType.of("int"));
        assertEquals(DataTypes.LongType,     DataType.of("long"));
        assertEquals(DataTypes.FloatType,    DataType.of("float"));
        assertEquals(DataTypes.DoubleType,   DataType.of("double"));
    }

    @Test
    public void testOfStringNullables() throws Exception {
        System.out.println("DataType.of(String) nullable primitives");
        assertEquals(DataTypes.NullableBooleanType, DataType.of("Boolean"));
        assertEquals(DataTypes.NullableCharType,    DataType.of("Char"));
        assertEquals(DataTypes.NullableByteType,    DataType.of("Byte"));
        assertEquals(DataTypes.NullableShortType,   DataType.of("Short"));
        assertEquals(DataTypes.NullableIntType,     DataType.of("Int"));
        assertEquals(DataTypes.NullableLongType,    DataType.of("Long"));
        assertEquals(DataTypes.NullableFloatType,   DataType.of("Float"));
        assertEquals(DataTypes.NullableDoubleType,  DataType.of("Double"));
    }

    @Test
    public void testOfStringTemporalTypes() throws Exception {
        System.out.println("DataType.of(String) temporal types");
        assertEquals(DataTypes.DecimalType,  DataType.of("Decimal"));
        assertEquals(DataTypes.StringType,   DataType.of("String"));
        assertEquals(DataTypes.DateType,     DataType.of("Date"));
        assertEquals(DataTypes.DateTimeType, DataType.of("DateTime"));
        assertEquals(DataTypes.TimeType,     DataType.of("Time"));
    }

    @Test
    public void testOfStringArray() throws Exception {
        System.out.println("DataType.of(String) array");
        assertEquals(DataTypes.IntArrayType,    DataType.of("Array[int]"));
        assertEquals(DataTypes.DoubleArrayType, DataType.of("Array[double]"));
        assertEquals(DataTypes.array(DataTypes.NullableIntType), DataType.of("Array[Int]"));
    }

    @Test
    public void testOfStringObject() throws Exception {
        System.out.println("DataType.of(String) object");
        assertEquals(DataTypes.object(Integer.class), DataType.of("Class<java.lang.Integer>"));
        assertEquals(DataTypes.object(Double.class),  DataType.of("Class<java.lang.Double>"));
    }

    @Test
    public void testOfStringStruct() throws Exception {
        System.out.println("DataType.of(String) struct");
        DataType t = DataType.of("Struct(age: int, name: String)");
        assertTrue(t instanceof StructType st && st.length() == 2);
    }

    @Test
    public void testOfStringUnknownThrows() {
        System.out.println("DataType.of(String) unknown type throws");
        assertThrows(Exception.class, () -> DataType.of("UnknownType"));
    }

    @Test
    public void testRoundTripNameOf() throws Exception {
        System.out.println("DataType name() round-trip via of()");
        DataType[] types = {
            DataTypes.IntType, DataTypes.NullableDoubleType, DataTypes.LongType,
            DataTypes.StringType, DataTypes.DateType, DataTypes.DateTimeType,
            DataTypes.TimeType, DataTypes.DecimalType,
            DataTypes.IntArrayType, DataTypes.object(Integer.class)
        };
        for (DataType t : types) {
            assertEquals(t, DataType.of(t.name()),
                "Round-trip failed for: " + t.name());
        }
    }

    // =========================================================================
    // DataType.of(Class<?>)
    // =========================================================================

    @Test
    public void testOfClass() {
        System.out.println("DataType.of(Class)");
        assertEquals(DataTypes.IntType,      DataType.of(int.class));
        assertEquals(DataTypes.LongType,     DataType.of(long.class));
        assertEquals(DataTypes.FloatType,    DataType.of(float.class));
        assertEquals(DataTypes.DoubleType,   DataType.of(double.class));
        assertEquals(DataTypes.BooleanType,  DataType.of(boolean.class));
        assertEquals(DataTypes.ByteType,     DataType.of(byte.class));
        assertEquals(DataTypes.ShortType,    DataType.of(short.class));
        assertEquals(DataTypes.CharType,     DataType.of(char.class));

        assertEquals(DataTypes.NullableIntType,    DataType.of(Integer.class));
        assertEquals(DataTypes.NullableDoubleType, DataType.of(Double.class));
        assertEquals(DataTypes.NullableLongType,   DataType.of(Long.class));
        assertEquals(DataTypes.NullableFloatType,  DataType.of(Float.class));
        assertEquals(DataTypes.NullableBooleanType,DataType.of(Boolean.class));
        assertEquals(DataTypes.NullableByteType,   DataType.of(Byte.class));
        assertEquals(DataTypes.NullableShortType,  DataType.of(Short.class));
        assertEquals(DataTypes.NullableCharType,   DataType.of(Character.class));
    }

    @Test
    public void testOfClassArray() {
        System.out.println("DataType.of(Class) array");
        assertEquals(DataTypes.IntArrayType,    DataType.of(int[].class));
        assertEquals(DataTypes.DoubleArrayType, DataType.of(double[].class));
    }

    @Test
    public void testOfClassEnum() {
        System.out.println("DataType.of(Class) enum");
        enum Color { RED, GREEN, BLUE }
        DataType t = DataType.of(Color.class);
        // 3 levels → byte category
        assertEquals(DataTypes.ByteType, t);
    }

    // =========================================================================
    // DataType.infer(String)
    // =========================================================================

    @Test
    public void testInferNull() {
        System.out.println("DataType.infer null/empty");
        assertNull(DataType.infer(null));
        assertNull(DataType.infer(""));
    }

    @Test
    public void testInferInt() {
        System.out.println("DataType.infer integer");
        assertEquals(DataTypes.IntType,  DataType.infer("42"));
        assertEquals(DataTypes.IntType,  DataType.infer("-7"));
        assertEquals(DataTypes.IntType,  DataType.infer("0"));
    }

    @Test
    public void testInferLong() {
        System.out.println("DataType.infer long");
        // Values beyond Integer range
        assertEquals(DataTypes.LongType, DataType.infer("9999999999"));
    }

    @Test
    public void testInferDouble() {
        System.out.println("DataType.infer double");
        assertEquals(DataTypes.DoubleType, DataType.infer("3.14"));
        assertEquals(DataTypes.DoubleType, DataType.infer("-2.718"));
        assertEquals(DataTypes.DoubleType, DataType.infer("1e10"));
    }

    @Test
    public void testInferBoolean() {
        System.out.println("DataType.infer boolean");
        assertEquals(DataTypes.BooleanType, DataType.infer("true"));
        assertEquals(DataTypes.BooleanType, DataType.infer("false"));
    }

    @Test
    public void testInferDate() {
        System.out.println("DataType.infer date");
        assertEquals(DataTypes.DateType, DataType.infer("2024-03-15"));
    }

    @Test
    public void testInferDateTime() {
        System.out.println("DataType.infer datetime");
        assertEquals(DataTypes.DateTimeType, DataType.infer("2024-03-15T14:30:45"));
    }

    @Test
    public void testInferString() {
        System.out.println("DataType.infer string fallback");
        assertEquals(DataTypes.StringType, DataType.infer("hello world"));
        assertEquals(DataTypes.StringType, DataType.infer("not_a_number"));
    }

    // =========================================================================
    // DataType.coerce(DataType, DataType)
    // =========================================================================

    @Test
    public void testCoerceSameType() {
        System.out.println("DataType.coerce same type");
        assertEquals(DataTypes.IntType,    DataType.coerce(DataTypes.IntType, DataTypes.IntType));
        assertEquals(DataTypes.StringType, DataType.coerce(DataTypes.StringType, DataTypes.StringType));
    }

    @Test
    public void testCoerceNullLeft() {
        System.out.println("DataType.coerce null left");
        assertEquals(DataTypes.IntType, DataType.coerce(null, DataTypes.IntType));
    }

    @Test
    public void testCoerceNullRight() {
        System.out.println("DataType.coerce null right");
        assertEquals(DataTypes.IntType, DataType.coerce(DataTypes.IntType, null));
    }

    @Test
    public void testCoerceIntDouble() {
        System.out.println("DataType.coerce int + double → double");
        assertEquals(DataTypes.DoubleType,
            DataType.coerce(DataTypes.IntType, DataTypes.DoubleType));
        assertEquals(DataTypes.DoubleType,
            DataType.coerce(DataTypes.DoubleType, DataTypes.IntType));
    }

    @Test
    public void testCoerceDateDateTime() {
        System.out.println("DataType.coerce Date + DateTime → DateTime");
        assertEquals(DataTypes.DateTimeType,
            DataType.coerce(DataTypes.DateType, DataTypes.DateTimeType));
        assertEquals(DataTypes.DateTimeType,
            DataType.coerce(DataTypes.DateTimeType, DataTypes.DateType));
    }

    @Test
    public void testCoerceIncompatibleFallsToString() {
        System.out.println("DataType.coerce incompatible → String");
        assertEquals(DataTypes.StringType,
            DataType.coerce(DataTypes.IntType, DataTypes.StringType));
        assertEquals(DataTypes.StringType,
            DataType.coerce(DataTypes.DateType, DataTypes.IntType));
    }

    // =========================================================================
    // DataType.prompt(DataType, DataType) – type promotion
    // =========================================================================

    @Test
    public void testPromptIntInt() {
        System.out.println("DataType.prompt int + int → int");
        assertEquals(DataTypes.IntType,
            DataType.prompt(DataTypes.IntType, DataTypes.IntType));
    }

    @Test
    public void testPromptIntLong() {
        System.out.println("DataType.prompt int + long → long");
        assertEquals(DataTypes.LongType,
            DataType.prompt(DataTypes.IntType, DataTypes.LongType));
    }

    @Test
    public void testPromptIntFloat() {
        System.out.println("DataType.prompt int + float → float");
        assertEquals(DataTypes.FloatType,
            DataType.prompt(DataTypes.IntType, DataTypes.FloatType));
    }

    @Test
    public void testPromptIntDouble() {
        System.out.println("DataType.prompt int + double → double");
        assertEquals(DataTypes.DoubleType,
            DataType.prompt(DataTypes.IntType, DataTypes.DoubleType));
    }

    @Test
    public void testPromptFloatDouble() {
        System.out.println("DataType.prompt float + double → double");
        assertEquals(DataTypes.DoubleType,
            DataType.prompt(DataTypes.FloatType, DataTypes.DoubleType));
    }

    @Test
    public void testPromptNullablePreserved() {
        System.out.println("DataType.prompt nullable preserved");
        assertEquals(DataTypes.NullableIntType,
            DataType.prompt(DataTypes.IntType, DataTypes.NullableIntType));
        assertEquals(DataTypes.NullableDoubleType,
            DataType.prompt(DataTypes.NullableIntType, DataTypes.NullableDoubleType));
    }

    @Test
    public void testPromptInvalidTypeThrows() {
        System.out.println("DataType.prompt invalid type throws");
        assertThrows(IllegalArgumentException.class,
            () -> DataType.prompt(DataTypes.StringType, DataTypes.IntType));
        assertThrows(IllegalArgumentException.class,
            () -> DataType.prompt(DataTypes.IntType, DataTypes.StringType));
    }

    // =========================================================================
    // DataType.of(JDBCType, boolean, String)
    // =========================================================================

    @Test
    public void testOfJdbcType() {
        System.out.println("DataType.of(JDBCType)");
        assertEquals(DataTypes.IntType,      DataType.of(JDBCType.INTEGER,   false, "H2"));
        assertEquals(DataTypes.NullableIntType, DataType.of(JDBCType.INTEGER, true, "H2"));
        assertEquals(DataTypes.LongType,     DataType.of(JDBCType.BIGINT,    false, "H2"));
        assertEquals(DataTypes.FloatType,    DataType.of(JDBCType.FLOAT,     false, "H2"));
        assertEquals(DataTypes.DoubleType,   DataType.of(JDBCType.DOUBLE,    false, "H2"));
        assertEquals(DataTypes.StringType,   DataType.of(JDBCType.VARCHAR,   false, "H2"));
        assertEquals(DataTypes.DateType,     DataType.of(JDBCType.DATE,      false, "H2"));
        assertEquals(DataTypes.TimeType,     DataType.of(JDBCType.TIME,      false, "H2"));
        assertEquals(DataTypes.DateTimeType, DataType.of(JDBCType.TIMESTAMP, false, "H2"));
        assertEquals(DataTypes.DecimalType,  DataType.of(JDBCType.DECIMAL,   false, "H2"));
        assertEquals(DataTypes.ByteArrayType,DataType.of(JDBCType.BINARY,    false, "H2"));
    }

    @Test
    public void testOfJdbcTypeSQLiteNumeric() {
        System.out.println("DataType.of(JDBCType) SQLite NUMERIC");
        // SQLite NUMERIC is mapped to double, not decimal
        assertEquals(DataTypes.DoubleType,
            DataType.of(JDBCType.NUMERIC, false, "SQLite"));
        assertEquals(DataTypes.NullableDoubleType,
            DataType.of(JDBCType.NUMERIC, true, "SQLite"));
    }

    @Test
    public void testOfJdbcTypeUnsupportedThrows() {
        System.out.println("DataType.of(JDBCType) unsupported throws");
        assertThrows(UnsupportedOperationException.class,
            () -> DataType.of(JDBCType.OTHER, false, "H2"));
    }

    // =========================================================================
    // StructField
    // =========================================================================

    @Test
    public void testStructFieldBasic() {
        System.out.println("StructField basic");
        var f = new StructField("age", DataTypes.IntType);
        assertEquals("age",           f.name());
        assertEquals(DataTypes.IntType, f.dtype());
        assertNull(f.measure());
        assertEquals("age: int", f.toString());
    }

    @Test
    public void testStructFieldWithMeasure() {
        System.out.println("StructField with measure");
        var scale = new NominalScale("M", "F");
        var f = new StructField("gender", DataTypes.ByteType, scale);
        assertEquals(scale, f.measure());
        // toString(Object) should use measure
        assertEquals("M", f.toString(0));
        assertEquals("F", f.toString(1));
    }

    @Test
    public void testStructFieldIsNumeric() {
        System.out.println("StructField isNumeric");
        var numeric = new StructField("age", DataTypes.IntType);
        assertTrue(numeric.isNumeric());

        var nominal = new StructField("cat", DataTypes.ByteType, new NominalScale("A", "B"));
        assertFalse(nominal.isNumeric());

        var str = new StructField("name", DataTypes.StringType);
        assertFalse(str.isNumeric());
    }

    @Test
    public void testStructFieldWithName() {
        System.out.println("StructField withName");
        var f = new StructField("age", DataTypes.IntType);
        var f2 = f.withName("years");
        assertEquals("years", f2.name());
        assertEquals(f.dtype(), f2.dtype());
        assertNull(f2.measure());
    }

    @Test
    public void testStructFieldValueOf() {
        System.out.println("StructField valueOf");
        var f = new StructField("val", DataTypes.IntType);
        assertEquals(42, f.valueOf("42"));
    }

    @Test
    public void testStructFieldToStringNull() {
        System.out.println("StructField toString null");
        var f = new StructField("val", DataTypes.DoubleType);
        assertEquals("null", f.toString(null));
    }

    @Test
    public void testStructFieldEquals() {
        System.out.println("StructField equals");
        var f1 = new StructField("age", DataTypes.IntType);
        var f2 = new StructField("age", DataTypes.IntType);
        var f3 = new StructField("age", DataTypes.LongType);
        assertEquals(f1, f2);
        assertNotEquals(f1, f3);
    }

    @Test
    public void testStructFieldInvalidNumericalMeasure() {
        System.out.println("StructField rejects NumericalMeasure on non-numeric type");
        var scale = new RatioScale(java.text.NumberFormat.getInstance());
        assertThrows(IllegalArgumentException.class,
            () -> new StructField("name", DataTypes.StringType, scale));
    }

    @Test
    public void testStructFieldInvalidCategoricalMeasure() {
        System.out.println("StructField rejects CategoricalMeasure on non-integral type");
        var scale = new NominalScale("A", "B");
        assertThrows(IllegalArgumentException.class,
            () -> new StructField("val", DataTypes.DoubleType, scale));
    }

    // =========================================================================
    // StructType
    // =========================================================================

    @Test
    public void testStructTypeBasic() {
        System.out.println("StructType basic");
        var st = new StructType(
            new StructField("age",  DataTypes.IntType),
            new StructField("name", DataTypes.StringType)
        );
        assertEquals(2, st.length());
        assertEquals("age",  st.field(0).name());
        assertEquals("name", st.field(1).name());
        assertArrayEquals(new String[]{"age", "name"}, st.names());
    }

    @Test
    public void testStructTypeIndexOf() {
        System.out.println("StructType indexOf");
        var st = new StructType(
            new StructField("x", DataTypes.DoubleType),
            new StructField("y", DataTypes.DoubleType)
        );
        assertEquals(0, st.indexOf("x"));
        assertEquals(1, st.indexOf("y"));
    }

    @Test
    public void testStructTypeDuplicateFieldThrows() {
        System.out.println("StructType duplicate field name throws");
        assertThrows(IllegalArgumentException.class, () ->
            new StructType(
                new StructField("x", DataTypes.IntType),
                new StructField("x", DataTypes.DoubleType)
            ));
    }

    @Test
    public void testStructTypeAdd() {
        System.out.println("StructType add");
        var st = new StructType(new StructField("a", DataTypes.IntType));
        st.add(new StructField("b", DataTypes.DoubleType));
        assertEquals(2, st.length());
        assertEquals("b", st.field(1).name());
    }

    @Test
    public void testStructTypeAddDuplicateThrows() {
        System.out.println("StructType add duplicate throws");
        var st = new StructType(new StructField("a", DataTypes.IntType));
        assertThrows(IllegalArgumentException.class,
            () -> st.add(new StructField("a", DataTypes.DoubleType)));
    }

    @Test
    public void testStructTypeRename() {
        System.out.println("StructType rename");
        var st = new StructType(
            new StructField("old", DataTypes.IntType),
            new StructField("b",   DataTypes.StringType)
        );
        st.rename("old", "new");
        assertEquals("new", st.field(0).name());
        assertEquals(0, st.indexOf("new"));
        assertEquals(-1, st.indexOf("old"));
    }

    @Test
    public void testStructTypeSet() {
        System.out.println("StructType set");
        var st = new StructType(new StructField("x", DataTypes.IntType));
        st.set(0, new StructField("y", DataTypes.DoubleType));
        assertEquals("y", st.field(0).name());
        assertEquals(DataTypes.DoubleType, st.field(0).dtype());
    }

    @Test
    public void testStructTypeEquals() {
        System.out.println("StructType equals");
        var st1 = new StructType(new StructField("a", DataTypes.IntType));
        var st2 = new StructType(new StructField("a", DataTypes.IntType));
        var st3 = new StructType(new StructField("b", DataTypes.IntType));
        assertEquals(st1, st2);
        assertNotEquals(st1, st3);
    }

    @Test
    public void testStructTypeNameRoundTrip() throws Exception {
        System.out.println("StructType name round-trip");
        var st = new StructType(
            new StructField("age",  DataTypes.IntType),
            new StructField("name", DataTypes.StringType)
        );
        DataType parsed = DataType.of(st.name());
        assertEquals(st, parsed);
    }

    // =========================================================================
    // DataTypes factory methods
    // =========================================================================

    @Test
    public void testDataTypesCategorySmall() {
        System.out.println("DataTypes.category small");
        assertEquals(DataTypes.ByteType,  DataTypes.category(2));
        assertEquals(DataTypes.ByteType,  DataTypes.category(128));
    }

    @Test
    public void testDataTypesCategoryMedium() {
        System.out.println("DataTypes.category medium");
        assertEquals(DataTypes.ShortType, DataTypes.category(129));
        assertEquals(DataTypes.ShortType, DataTypes.category(32768));
    }

    @Test
    public void testDataTypesCategoryLarge() {
        System.out.println("DataTypes.category large");
        assertEquals(DataTypes.IntType, DataTypes.category(32769));
    }

    @Test
    public void testDataTypesObjectSpecialClasses() {
        System.out.println("DataTypes.object special classes");
        assertEquals(DataTypes.DecimalType,  DataTypes.object(java.math.BigDecimal.class));
        assertEquals(DataTypes.StringType,   DataTypes.object(String.class));
        assertEquals(DataTypes.DateType,     DataTypes.object(LocalDate.class));
        assertEquals(DataTypes.TimeType,     DataTypes.object(LocalTime.class));
        assertEquals(DataTypes.DateTimeType, DataTypes.object(LocalDateTime.class));
    }

    @Test
    public void testDataTypesArraySingleton() {
        System.out.println("DataTypes.array returns singleton for primitives");
        assertSame(DataTypes.IntArrayType,    DataTypes.array(DataTypes.IntType));
        assertSame(DataTypes.DoubleArrayType, DataTypes.array(DataTypes.DoubleType));
        assertSame(DataTypes.LongArrayType,   DataTypes.array(DataTypes.LongType));
        assertSame(DataTypes.FloatArrayType,  DataTypes.array(DataTypes.FloatType));
        assertSame(DataTypes.BooleanArrayType,DataTypes.array(DataTypes.BooleanType));
        assertSame(DataTypes.ByteArrayType,   DataTypes.array(DataTypes.ByteType));
        assertSame(DataTypes.ShortArrayType,  DataTypes.array(DataTypes.ShortType));
        assertSame(DataTypes.CharArrayType,   DataTypes.array(DataTypes.CharType));
    }

    @Test
    public void testDataTypesArrayNonSingleton() {
        System.out.println("DataTypes.array non-singleton for nullable/object");
        var t = DataTypes.array(DataTypes.NullableIntType);
        assertNotSame(DataTypes.IntArrayType, t);
        assertEquals(DataTypes.NullableIntType, t.getComponentType());
    }

    // =========================================================================
    // toString(Object) null-safety
    // =========================================================================

    @Test
    public void testToStringNullDefault() {
        System.out.println("DataType.toString(null) null-safety");
        assertEquals("null", DataTypes.StringType.toString(null));
        assertEquals("null", DataTypes.DateType.toString(null));
        assertEquals("null", DataTypes.DateTimeType.toString(null));
        assertEquals("null", DataTypes.TimeType.toString(null));
    }
}

