/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    @Test
    public void testInt() {
        System.out.println("int");
        try {
            assertEquals(DataTypes.IntegerType, DataType.of("int"));
        } catch (Exception ex) {
            assertTrue(String.format("Unexpected exception: %s", ex), false);
        }
    }

    /**
     * Test of of method, of class DataType.
     */
    @Test
    public void testLong() {
        System.out.println("long");
        try {
            assertEquals(DataTypes.LongType, DataType.of("long"));
        } catch (Exception ex) {
            assertTrue(String.format("Unexpected exception: %s", ex), false);
        }
    }

    /**
     * Test of of method, of class DataType.
     */
    @Test
    public void testDouble() {
        System.out.println("double");
        try {
            assertEquals(DataTypes.DoubleType, DataType.of("double"));
        } catch (Exception ex) {
            assertTrue(String.format("Unexpected exception: %s", ex), false);
        }
    }

    /**
     * Test of of method, of class DataType.
     */
    @Test
    public void testArray() {
        System.out.println("array");
        try {
            assertEquals(DataTypes.array(DataTypes.IntegerType), DataType.of("Array[int]"));
        } catch (Exception ex) {
            assertTrue(String.format("Unexpected exception: %s", ex), false);
        }
    }

    /**
     * Test of of method, of class DataType.
     */
    @Test
    public void testObject() {
        System.out.println("object");
        try {
            assertEquals(DataTypes.object(Integer.class), DataType.of("Object[java.lang.Integer]"));
        } catch (Exception ex) {
            assertTrue(String.format("Unexpected exception: %s", ex), false);
        }
    }

    /**
     * Test of of method, of class DataType.
     */
    @Test
    public void testStruct() {
        System.out.println("struct");
        try {
            assertEquals(DataTypes.struct(
                    new StructField("age", DataTypes.IntegerType),
                    new StructField("birthday", DataTypes.DateType),
                    new StructField("gender", DataTypes.CharType),
                    new StructField("name", DataTypes.StringType),
                    new StructField("salary", DataTypes.object(Integer.class))
                    ),
                    DataType.of("Struct[age: int, birthday: Date[uuuu-MM-dd], gender: char, name: String, salary: Object[java.lang.Integer]]"));
        } catch (Exception ex) {
            assertTrue(String.format("Unexpected exception: %s", ex), false);
        }
    }
}