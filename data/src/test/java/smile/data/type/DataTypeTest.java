/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.data.type;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import smile.data.type.DataType;
import smile.data.type.DataTypes;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class DataTypeTest {

    public DataTypeTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of of method, of class DataType.
     */
    @Test(expected = Test.None.class)
    public void testInt() throws ClassNotFoundException {
        System.out.println("int");
        assertEquals(DataTypes.IntegerType, DataType.of("int"));
    }

    /**
     * Test of of method, of class DataType.
     */
    @Test(expected = Test.None.class)
    public void testLong() throws ClassNotFoundException {
        System.out.println("long");
        assertEquals(DataTypes.LongType, DataType.of("long"));
    }

    /**
     * Test of of method, of class DataType.
     */
    @Test(expected = Test.None.class)
    public void testDouble() throws ClassNotFoundException {
        System.out.println("double");
        assertEquals(DataTypes.DoubleType, DataType.of("double"));
    }

    /**
     * Test of of method, of class DataType.
     */
    @Test(expected = Test.None.class)
    public void testArray() throws ClassNotFoundException {
        System.out.println("array");
        assertEquals(DataTypes.array(DataTypes.IntegerType), DataType.of("Array[int]"));
    }

    /**
     * Test of of method, of class DataType.
     */
    @Test(expected = Test.None.class)
    public void testObject() throws ClassNotFoundException {
        System.out.println("object");
        assertEquals(DataTypes.object(Integer.class), DataType.of("Object[java.lang.Integer]"));
    }

    /**
     * Test of of method, of class DataType.
     */
    @Test(expected = Test.None.class)
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
        System.out.println(type.toString());
        assertEquals(type,
                DataType.of("Struct[age: int, birthday: Date[uuuu-MM-dd], gender: char, name: String, salary: Object[java.lang.Integer]]"));
    }
}