/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.io;

import smile.data.DataFrame;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.data.type.StructType;
import smile.io.Paths;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class JSONTest {

    public JSONTest() {
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
    public void testBooks() throws Exception {
        System.out.println("books");
        JSON json = new JSON();
        DataFrame df = json.read(Paths.getTestData("kylo/books.json"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(7, df.size());
        assertEquals(10, df.ncol());

        StructType schema = new StructType(
                new StructField("series_t", DataTypes.StringType),
                new StructField("pages_i", DataTypes.IntType),
                new StructField("author", DataTypes.StringType),
                new StructField("price", DataTypes.DoubleType),
                new StructField("cat", DataTypes.StringType),
                new StructField("name", DataTypes.StringType),
                new StructField("genre_s", DataTypes.StringType),
                new StructField("sequence_i", DataTypes.IntType),
                new StructField("inStock", DataTypes.BooleanType),
                new StructField("id", DataTypes.StringType)
        );
        assertEquals(schema, df.schema());

        assertEquals("Percy Jackson and the Olympians", df.get(0, 0));
        assertEquals(384, df.get(0, 1));
        assertEquals("Rick Riordan", df.get(0, 2));
        assertEquals(12.5, df.getDouble(0, 3), 1E-7);

        assertNull(df.get(6, 0));
        assertEquals(475, df.get(6, 1));
        assertEquals("Michael McCandless", df.get(6, 2));
        assertEquals(30.5, df.getDouble(6, 3), 1E-7);
    }

    @Test
    public void testBooksMultiLine() throws Exception {
        System.out.println("books multi-line");
        JSON json = new JSON().mode(JSON.Mode.MULTI_LINE);
        DataFrame df = json.read(Paths.getTestData("kylo/books_array.json"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(7, df.size());
        assertEquals(10, df.ncol());

        StructType schema = new StructType(
                new StructField("series_t", DataTypes.StringType),
                new StructField("pages_i", DataTypes.IntType),
                new StructField("author", DataTypes.StringType),
                new StructField("price", DataTypes.DoubleType),
                new StructField("cat", DataTypes.StringType),
                new StructField("name", DataTypes.StringType),
                new StructField("genre_s", DataTypes.StringType),
                new StructField("sequence_i", DataTypes.IntType),
                new StructField("inStock", DataTypes.BooleanType),
                new StructField("id", DataTypes.StringType)
        );
        assertEquals(schema, df.schema());

        assertEquals("Percy Jackson and the Olympians", df.get(0, 0));
        assertEquals(384, df.get(0, 1));
        assertEquals("Rick Riordan", df.get(0, 2));
        assertEquals(12.5, df.getDouble(0, 3), 1E-7);

        assertNull(df.get(6, 0));
        assertEquals(475, df.get(6, 1));
        assertEquals("Michael McCandless", df.get(6, 2));
        assertEquals(30.5, df.getDouble(6, 3), 1E-7);
    }
}
