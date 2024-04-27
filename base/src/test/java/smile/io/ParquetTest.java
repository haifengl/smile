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

package smile.io;

import java.time.LocalDateTime;
import smile.data.DataFrame;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.math.matrix.Matrix;
import smile.util.Paths;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class ParquetTest {

    DataFrame df;

    public ParquetTest() {
        try {
            df = Parquet.read(Paths.getTestData("kylo/userdata1.parquet"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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


    /**
     * Test of nrow method, of class DataFrame.
     */
    @Test
    public void testNrows() {
        System.out.println("nrow");
        assertEquals(1000, df.nrow());
    }

    /**
     * Test of ncol method, of class DataFrame.
     */
    @Test
    public void testNcols() {
        System.out.println("ncol");
        assertEquals(13, df.ncol());
    }

    /**
     * Test of schema method, of class DataFrame.
     */
    @Test
    public void testSchema() {
        System.out.println("schema");
        System.out.println(df.schema());
        System.out.println(df.structure());
        System.out.println(df);
        smile.data.type.StructType schema = DataTypes.struct(
                new StructField("registration_dttm", DataTypes.DateTimeType),
                new StructField("id", DataTypes.IntegerObjectType),
                new StructField("first_name", DataTypes.StringType),
                new StructField("last_name", DataTypes.StringType),
                new StructField("email", DataTypes.StringType),
                new StructField("gender", DataTypes.StringType),
                new StructField("ip_address", DataTypes.StringType),
                new StructField("cc", DataTypes.StringType),
                new StructField("country", DataTypes.StringType),
                new StructField("birthdate", DataTypes.StringType),
                new StructField("salary", DataTypes.DoubleObjectType),
                new StructField("title", DataTypes.StringType),
                new StructField("comments", DataTypes.StringType)
        );
        assertEquals(schema, df.schema());
    }

    /**
     * Test of get method, of class DataFrame.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        System.out.println(df.get(0));
        System.out.println(df.get(1));
        assertEquals(LocalDateTime.parse("2016-02-03T07:55:29"), df.getDateTime(0, 0));
        assertEquals(1, df.getInt(0, 1));
        assertEquals("Amanda", df.getString(0, 2));
        assertEquals("Jordan", df.getString(0, 3));
        assertEquals("ajordan0@com.com", df.getString(0, 4));
        assertEquals("Female", df.getString(0, 5));
        assertEquals("1.197.201.2", df.getString(0, 6));
        assertEquals("6759521864920116", df.getString(0, 7));
        assertEquals("Indonesia", df.getString(0, 8));
        assertEquals("3/8/1971", df.getString(0, 9));
        assertEquals(49756.53, df.getDouble(0, 10), 1E-10);
        assertEquals("Internal Auditor", df.getString(0, 11));
        assertEquals("1E+02", df.getString(0, 12));
    }

    /**
     * Test of summary method, of class DataFrame.
     */
    @Test
    public void testDataFrameSummary() {
        System.out.println("summary");
        DataFrame output = df.summary();
        System.out.println(output);
        System.out.println(output.schema());
        assertEquals(2, output.nrow());
        assertEquals(5, output.ncol());
        assertEquals("id", output.get(0,0));
        assertEquals(1000L, output.get(0,1));
        assertEquals(1.0, output.get(0,2));
        assertEquals(500.5, output.get(0,3));
        assertEquals(1000.0, output.get(0,4));

        assertEquals("salary", output.get(1,0));
        assertEquals(932L, output.get(1,1));
        assertEquals(12380.49, output.get(1,2));
        assertEquals(149005.35665236053, output.get(1,3));
        assertEquals(286592.99, output.get(1,4));
    }

    /**
     * Test of toMatrix method, of class DataFrame.
     */
    @Test
    public void testDataFrameToMatrix() {
        System.out.println("toMatrix");
        Matrix output = df.select("id", "salary").toMatrix();
        System.out.println(output);
        assertEquals(1000, output.nrow());
        assertEquals(2, output.ncol());
        assertEquals(1, output.get(0, 0), 1E-10);
        assertEquals(2, output.get(1, 0), 1E-10);
        assertEquals(3, output.get(2, 0), 1E-10);
        assertEquals(4, output.get(3, 0), 1E-10);
        assertEquals(49756.53, output.get(0, 1), 1E-10);
        assertEquals(150280.17, output.get(1, 1), 1E-10);
        assertEquals(144972.51, output.get(2, 1), 1E-10);
        assertEquals(90263.05, output.get(3, 1), 1E-10);
        assertTrue(Double.isNaN(output.get(4, 1)));
    }
}