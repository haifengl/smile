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

package smile.io;

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
