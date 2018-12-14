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
package smile.io;

import org.apache.commons.csv.CSVFormat;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.DataFrame;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.util.Paths;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class JSONTest {

    public JSONTest() {
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
     * Test of read method, of class JSON.
     */
    @Test(expected = Test.None.class)
    public void testBooks() throws Exception {
        System.out.println("books");
        JSON json = new JSON();
        DataFrame df = json.read(Paths.getTestData("json/books1.json"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(7, df.nrows());
        assertEquals(10, df.ncols());

        StructType schema = DataTypes.struct(
                new StructField("series_t", DataTypes.StringType),
                new StructField("pages_i", DataTypes.IntegerType),
                new StructField("author", DataTypes.StringType),
                new StructField("price", DataTypes.DoubleType),
                new StructField("cat", DataTypes.StringType),
                new StructField("name", DataTypes.StringType),
                new StructField("genre_s", DataTypes.StringType),
                new StructField("sequence_i", DataTypes.IntegerType),
                new StructField("inStock", DataTypes.BooleanType),
                new StructField("id", DataTypes.StringType)
        );
        assertEquals(schema, df.schema());

        assertEquals("Percy Jackson and the Olympians", df.get(0, 0));
        assertEquals(384, df.get(0, 1));
        assertEquals("Rick Riordan", df.get(0, 2));
        assertEquals(12.5, df.getDouble(0, 3), 1E-7);

        assertEquals(null, df.get(6, 0));
        assertEquals(475, df.get(6, 1));
        assertEquals("Michael McCandless", df.get(6, 2));
        assertEquals(30.5, df.getDouble(6, 3), 1E-7);
    }

    /**
     * Test of read method, of class JSON.
     */
    @Test(expected = Test.None.class)
    public void testColors() throws Exception {
        System.out.println("colors");

        JSON json = new JSON();
        DataFrame df = json.read(Paths.getTestData("json/colors.json"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(7291, df.nrows());
        assertEquals(257, df.ncols());

        StructField[] fields = df.schema().fields();
        assertEquals(DataTypes.IntegerType, fields[0].type);
        for (int i = 1; i < fields.length; i++) {
            assertEquals(DataTypes.DoubleType, fields[i].type);
        }

        assertEquals(6, df.getInt(0, 0));
        assertEquals(5, df.getInt(1, 0));
        assertEquals(4, df.getInt(2, 0));
        assertEquals(-1.0000, df.getDouble(0, 7), 1E-7);
        assertEquals(-0.6310, df.getDouble(0, 8), 1E-7);
        assertEquals(0.8620, df.getDouble(0, 9), 1E-7);

        assertEquals(1, df.getInt(7290, 0));
        assertEquals(-1.0000, df.getDouble(7290, 5), 1E-7);
        assertEquals(-0.1080, df.getDouble(7290, 6), 1E-7);
        assertEquals(1.0000, df.getDouble(7290, 7), 1E-7);
    }

    /**
     * Test of read method, of class JSON.
     */
    @Test(expected = Test.None.class)
    public void testBooksMultiLine() throws Exception {
        System.out.println("books multi-line");
        JSON json = new JSON().mode(JSON.Mode.MULTI_LINE);
        DataFrame df = json.read(Paths.getTestData("json/books2.json"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(7, df.nrows());
        assertEquals(10, df.ncols());

        StructType schema = DataTypes.struct(
                new StructField("series_t", DataTypes.StringType),
                new StructField("pages_i", DataTypes.IntegerType),
                new StructField("author", DataTypes.StringType),
                new StructField("price", DataTypes.DoubleType),
                new StructField("cat", DataTypes.StringType),
                new StructField("name", DataTypes.StringType),
                new StructField("genre_s", DataTypes.StringType),
                new StructField("sequence_i", DataTypes.IntegerType),
                new StructField("inStock", DataTypes.BooleanType),
                new StructField("id", DataTypes.StringType)
        );
        assertEquals(schema, df.schema());

        assertEquals("Percy Jackson and the Olympians", df.get(0, 0));
        assertEquals(384, df.get(0, 1));
        assertEquals("Rick Riordan", df.get(0, 2));
        assertEquals(12.5, df.getDouble(0, 3), 1E-7);

        assertEquals(null, df.get(6, 0));
        assertEquals(475, df.get(6, 1));
        assertEquals("Michael McCandless", df.get(6, 2));
        assertEquals(30.5, df.getDouble(6, 3), 1E-7);
    }
}
