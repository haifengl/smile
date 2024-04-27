/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.data.type;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class DataTypeTest {

    public DataTypeTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testInt() throws ClassNotFoundException {
        System.out.println("int");
        assertEquals(DataTypes.IntegerType, DataType.of("int"));
    }

    @Test
    public void testLong() throws ClassNotFoundException {
        System.out.println("long");
        assertEquals(DataTypes.LongType, DataType.of("long"));
    }

    @Test
    public void testDouble() throws ClassNotFoundException {
        System.out.println("double");
        assertEquals(DataTypes.DoubleType, DataType.of("double"));
    }

    @Test
    public void testArray() throws ClassNotFoundException {
        System.out.println("array");
        assertEquals(DataTypes.array(DataTypes.IntegerType), DataType.of("Array[int]"));
    }

    @Test
    public void testObject() throws ClassNotFoundException {
        System.out.println("object");
        assertEquals(DataTypes.object(Integer.class), DataType.of("Object[java.lang.Integer]"));
    }

    @Test
    public void testStruct() throws ClassNotFoundException {
        System.out.println("struct");
        StructType type = DataTypes.struct(
                new StructField("age", DataTypes.IntegerType),
                new StructField("birthday", DataTypes.DateType),
                new StructField("gender", DataTypes.CharType),
                new StructField("name", DataTypes.StringType),
                new StructField("salary", DataTypes.object(Integer.class))
        );
        System.out.println(type.name());
        System.out.println(type);
        assertEquals(type,
                DataType.of("Struct[age: int, birthday: Date[uuuu-MM-dd], gender: char, name: String, salary: Object[java.lang.Integer]]"));
    }
}