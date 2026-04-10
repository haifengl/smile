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

import smile.data.measure.NominalScale;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.data.vector.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Tuple interface.
 *
 * @author Haifeng Li
 */
public class TupleTest {

    StructType schema;
    Tuple objectTuple;
    Tuple doubleTuple;
    Tuple intTuple;

    public TupleTest() {
        schema = new StructType(
                new StructField("name",   DataTypes.StringType),
                new StructField("age",    DataTypes.IntType),
                new StructField("salary", DataTypes.NullableDoubleType),
                new StructField("gender", DataTypes.ByteType,
                        new NominalScale("Male", "Female"))
        );

        objectTuple = Tuple.of(schema, new Object[]{"Alice", 30, 75000.0, (byte) 1});
        doubleTuple = Tuple.of(schema, new double[]{0.0, 30.0, 75000.0, 1.0});
        intTuple    = Tuple.of(schema, new int[]   {0,   30,   0,         1});
    }

    @BeforeAll
    public static void setUpClass() {}

    @AfterAll
    public static void tearDownClass() {}

    @BeforeEach
    public void setUp() {}

    @AfterEach
    public void tearDown() {}

    @Test
    public void testLength() {
        System.out.println("length");
        assertEquals(4, objectTuple.length());
        assertEquals(4, doubleTuple.length());
        assertEquals(4, intTuple.length());
    }

    @Test
    public void testGetByIndex() {
        System.out.println("get by index");
        assertEquals("Alice", objectTuple.get(0));
        assertEquals(30,      objectTuple.get(1));
        assertEquals(75000.0, objectTuple.get(2));
    }

    @Test
    public void testGetByName() {
        System.out.println("get by name");
        assertEquals("Alice", objectTuple.get("name"));
        assertEquals(30,      objectTuple.get("age"));
    }

    @Test
    public void testApplyAlias() {
        System.out.println("apply alias");
        assertEquals("Alice", objectTuple.apply(0));
        assertEquals("Alice", objectTuple.apply("name"));
    }

    @Test
    public void testIsNullAt() {
        System.out.println("isNullAt");
        assertFalse(objectTuple.isNullAt(0));
        assertFalse(objectTuple.isNullAt(2));

        Tuple withNull = Tuple.of(schema, new Object[]{"Bob", 25, null, (byte) 0});
        assertTrue(withNull.isNullAt(2));
        assertFalse(withNull.isNullAt(0));
    }

    @Test
    public void testAnyNull() {
        System.out.println("anyNull");
        assertFalse(objectTuple.anyNull());
        Tuple withNull = Tuple.of(schema, new Object[]{"Bob", 25, null, (byte) 0});
        assertTrue(withNull.anyNull());
    }

    @Test
    public void testGetInt() {
        System.out.println("getInt");
        assertEquals(30, objectTuple.getInt(1));
        assertEquals(30, objectTuple.getInt("age"));
    }

    @Test
    public void testGetDouble() {
        System.out.println("getDouble");
        assertEquals(75000.0, objectTuple.getDouble(2), 1e-10);
        assertEquals(75000.0, objectTuple.getDouble("salary"), 1e-10);
        assertEquals(30.0,    doubleTuple.getDouble(1), 1e-10);
    }

    @Test
    public void testGetLong() {
        System.out.println("getLong");
        assertEquals(30L, objectTuple.getLong(1));
    }

    @Test
    public void testGetFloat() {
        System.out.println("getFloat");
        assertEquals(30.0f, objectTuple.getFloat(1), 1e-5f);
    }

    @Test
    public void testGetByte() {
        System.out.println("getByte");
        assertEquals((byte) 1, objectTuple.getByte(3));
    }

    @Test
    public void testGetString() {
        System.out.println("getString");
        assertEquals("Alice",  objectTuple.getString(0));
        // Nominal field returns level name
        assertEquals("Female", objectTuple.getString(3));
    }

    @Test
    public void testGetScale() {
        System.out.println("getScale");
        assertEquals("Female", objectTuple.getScale(3));
        assertEquals("Female", objectTuple.getScale("gender"));
    }

    @Test
    public void testIndexOf() {
        System.out.println("indexOf");
        assertEquals(0, objectTuple.indexOf("name"));
        assertEquals(1, objectTuple.indexOf("age"));
        assertEquals(3, objectTuple.indexOf("gender"));
    }

    @Test
    public void testToArray() {
        System.out.println("toArray");
        // Only select numeric fields - String fields cannot be converted to double
        double[] arr = objectTuple.toArray("age", "salary", "gender");
        assertEquals(3, arr.length);
        assertEquals(30.0,    arr[0], 1e-10);
        assertEquals(75000.0, arr[1], 1e-10);
        assertEquals(1.0,     arr[2], 1e-10);
    }

    @Test
    public void testToArrayWithBias() {
        System.out.println("toArray with bias");
        double[] arr = objectTuple.toArray(true, CategoricalEncoder.LEVEL, "age", "salary");
        assertEquals(3, arr.length);
        assertEquals(1.0,     arr[0], 1e-10); // bias
        assertEquals(30.0,    arr[1], 1e-10);
        assertEquals(75000.0, arr[2], 1e-10);
    }

    @Test
    public void testToArrayDummyEncoding() {
        System.out.println("toArray dummy encoding");
        // gender has 2 levels → DUMMY creates 1 indicator column
        double[] arr = objectTuple.toArray(false, CategoricalEncoder.DUMMY, "gender");
        assertEquals(1, arr.length);
        // Female (index 1) → indicator for level 1 = 1
        assertEquals(1.0, arr[0], 1e-10);
    }

    @Test
    public void testToArrayOneHotEncoding() {
        System.out.println("toArray one-hot encoding");
        // gender has 2 levels → ONE_HOT creates 2 indicator columns
        double[] arr = objectTuple.toArray(false, CategoricalEncoder.ONE_HOT, "gender");
        assertEquals(2, arr.length);
        assertEquals(0.0, arr[0], 1e-10); // Male → 0
        assertEquals(1.0, arr[1], 1e-10); // Female → 1
    }

    @Test
    public void testDoubleArrayTuple() {
        System.out.println("double array tuple");
        assertEquals(30.0, doubleTuple.getDouble(1), 1e-10);
        assertEquals(30, doubleTuple.getInt(1));
    }

    @Test
    public void testIntArrayTuple() {
        System.out.println("int array tuple");
        assertEquals(30, intTuple.getInt(1));
    }

    @Test
    public void testSchema() {
        System.out.println("schema");
        assertEquals(schema, objectTuple.schema());
    }
}

