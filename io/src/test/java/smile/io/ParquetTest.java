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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import java.time.LocalDateTime;
import smile.data.DataFrame;
import smile.data.type.DataTypes;
import smile.data.type.StructField;
import smile.math.matrix.DenseMatrix;
import smile.math.matrix.SparseMatrix;
import smile.util.Paths;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class ParquetTest {

    DataFrame df;

    public ParquetTest() {
        try {
            Parquet parquet = new Parquet();
            df = parquet.read(Paths.getTestData("parquet/userdata1.parquet"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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
     * Test of nrows method, of class DataFrame.
     */
    @Test
    public void testNrows() {
        System.out.println("nrows");
        assertEquals(1000, df.nrows());
    }

    /**
     * Test of ncols method, of class DataFrame.
     */
    @Test
    public void testNcols() {
        System.out.println("ncols");
        assertEquals(13, df.ncols());
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
        assertEquals(LocalDateTime.parse("2016-02-03T07:55:29"), df.get(0).getDateTime(0));
        assertEquals(1, df.get(0).getInt(1));
        assertEquals("Amanda", df.get(0).getString(2));
        assertEquals("Jordan", df.get(0).getString(3));
        assertEquals("ajordan0@com.com", df.get(0).getString(4));
        assertEquals("Female", df.get(0).getString(5));
        assertEquals("1.197.201.2", df.get(0).getString(6));
        assertEquals("6759521864920116", df.get(0).getString(7));
        assertEquals("Indonesia", df.get(0).getString(8));
        assertEquals("3/8/1971", df.get(0).getString(9));
        assertEquals(49756.53, df.get(0).getDouble(10), 1E-10);
        assertEquals("Internal Auditor", df.get(0).getString(11));
        assertEquals("1E+02", df.get(0).getString(12));
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
        assertEquals(2, output.nrows());
        assertEquals(5, output.ncols());
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
        DenseMatrix output = df.select("id", "salary").toMatrix();
        System.out.println(output);
        assertEquals(1000, output.nrows());
        assertEquals(2, output.ncols());
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