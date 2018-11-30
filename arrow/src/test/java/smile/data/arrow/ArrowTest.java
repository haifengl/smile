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
package smile.data.frame;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.matrix.SparseMatrix;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class ArrowDataFrameTest {

    ArrowDataFrame df = new ArrowDataFrame(smile.util.Paths.getTestData("arrow/iris.feather"));

    public ArrowDataFrameTest() {
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
        assertEquals(150, df.nrows());
    }

    /**
     * Test of ncols method, of class DataFrame.
     */
    @Test
    public void testNcols() {
        System.out.println("ncols");
        assertEquals(4, df.ncols());
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
                new StructField("age", DataTypes.IntegerType),
                new StructField("birthday", DataTypes.DateType),
                new StructField("gender", DataTypes.CharType),
                new StructField("name", DataTypes.StringType),
                new StructField("salary", DataTypes.object(Double.class))
        );
        assertEquals(schema, df.schema());
    }

    /**
     * Test of names method, of class DataFrame.
     */
    @Test
    public void testNames() {
        System.out.println("names");
        String[] names = {"age", "birthday", "gender", "name", "salary"};
        assertTrue(Arrays.equals(names, df.names()));
    }

    /**
     * Test of types method, of class DataFrame.
     */
    @Test
    public void testTypes() {
        System.out.println("names");
        DataType[] types = {DataTypes.IntegerType, DataTypes.DateType, DataTypes.CharType, DataTypes.StringType, DataTypes.object(Double.class)};
        assertTrue(Arrays.equals(types, df.types()));
    }

    /**
     * Test of get method, of class DataFrame.
     */
    @Test
    public void testGet() {
        System.out.println("get");
        assertEquals(38, df.get(0).getInt(0));
        assertEquals("Alex", df.get(0).getString(3));
        assertEquals(10000., df.get(0).get(4));
        assertEquals(13, df.get(3).getInt(0));
        assertEquals("Amy", df.get(3).getString(3));
        assertEquals(null, df.get(3).get(4));

        assertEquals(38, df.get(0,0));
        assertEquals("Alex", df.get(0,3));
        assertEquals(10000., df.get(0,4));
        assertEquals(13, df.get(3,0));
        assertEquals("Amy", df.get(3,3));
        assertEquals(null, df.get(3,4));
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
        assertEquals("age", output.get(0,0));
        assertEquals(4L, output.get(0,1));
        assertEquals(13., output.get(0,2));
        assertEquals(30.5, output.get(0,3));
        assertEquals(48.0, output.get(0,4));
        assertEquals("salary", output.get(1,0));
        assertEquals(2L, output.get(1,1));
        assertEquals(10000., output.get(1,2));
        assertEquals(120000., output.get(1,3));
        assertEquals(230000., output.get(1,4));
    }

    /**
     * Test of toMatrix method, of class DataFrame.
     */
    @Test
    public void testDataFrameToMatrix() {
        System.out.println("toMatrix");
        DenseMatrix output = df.select("age", "salary").toMatrix();
        System.out.println(output);
        assertEquals(4, output.nrows());
        assertEquals(2, output.ncols());
        assertEquals(38., output.get(0, 0), 1E-10);
        assertEquals(23., output.get(1, 0), 1E-10);
        assertEquals(48., output.get(2, 0), 1E-10);
        assertEquals(13., output.get(3, 0), 1E-10);
        assertEquals(10000., output.get(0, 1), 1E-10);
        assertEquals(Double.NaN, output.get(1, 1), 1E-10);
        assertEquals(230000., output.get(2, 1), 1E-10);
        assertEquals(Double.NaN, output.get(3, 1), 1E-10);
    }
}