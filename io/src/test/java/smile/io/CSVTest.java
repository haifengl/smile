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
public class CSVTest {
    
    public CSVTest() {
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
     * Test of read method, of class CSV.
     */
    @Test(expected = Test.None.class)
    public void testUsps() throws Exception {
        System.out.println("usps");
        CSVFormat format = CSVFormat.newFormat(' ');
        CSV csv = new CSV(format);
        DataFrame usps = csv.read(Paths.getTestData("usps/zip.train"));

        System.out.println(usps);
        System.out.println(usps.schema());

        assertEquals(7291, usps.nrows());
        assertEquals(257, usps.ncols());

        StructField[] fields = usps.schema().fields();
        assertEquals(DataTypes.IntegerType, fields[0].type);
        for (int i = 1; i < fields.length; i++) {
            assertEquals(DataTypes.DoubleType, fields[i].type);
        }

        assertEquals(6, usps.getInt(0, 0));
        assertEquals(5, usps.getInt(1, 0));
        assertEquals(4, usps.getInt(2, 0));
        assertEquals(-1.0000, usps.getDouble(0, 7), 1E-7);
        assertEquals(-0.6310, usps.getDouble(0, 8), 1E-7);
        assertEquals(0.8620, usps.getDouble(0, 9), 1E-7);

        assertEquals(1, usps.getInt(7290, 0));
        assertEquals(-1.0000, usps.getDouble(7290, 5), 1E-7);
        assertEquals(-0.1080, usps.getDouble(7290, 6), 1E-7);
        assertEquals(1.0000, usps.getDouble(7290, 7), 1E-7);
    }

    /**
     * Test of read method, of class CSV.
     */
    @Test(expected = Test.None.class)
    public void testGdp() throws Exception {
        System.out.println("gdp");

        CSVFormat format = CSVFormat.DEFAULT.withFirstRecordAsHeader().withCommentMarker('%');
        CSV csv = new CSV(format);
        DataFrame gdp = csv.read(Paths.getTestData("regression/gdp.csv"));

        System.out.println(gdp);
        System.out.println(gdp.schema());

        assertEquals(68, gdp.nrows());
        assertEquals(4, gdp.ncols());

        StructType schema = DataTypes.struct(
                new StructField("Country", DataTypes.StringType),
                new StructField("GDP Growth", DataTypes.DoubleType),
                new StructField("Debt", DataTypes.DoubleType),
                new StructField("Interest", DataTypes.DoubleType)
        );
        assertEquals(schema, gdp.schema());

        assertEquals("Australia", gdp.get(0, 0));
        assertEquals(2.4, gdp.getDouble(0, 1), 1E-7);
        assertEquals(30.6, gdp.getDouble(0, 2), 1E-7);
        assertEquals(5.3, gdp.getDouble(0, 3), 1E-7);

        assertEquals("Uruguay", gdp.get(67, 0));
        assertEquals(6.5, gdp.getDouble(67, 1), 1E-7);
        assertEquals(46.8, gdp.getDouble(67, 2), 1E-7);
        assertEquals(8.1, gdp.getDouble(67, 3), 1E-7);
    }

    /**
     * Test of read method, of class CSV.
     */
    @Test(expected = Test.None.class)
    public void testDiabetes() throws Exception {
        System.out.println("diabetes");

        CSVFormat format = CSVFormat.DEFAULT.withFirstRecordAsHeader();
        CSV csv = new CSV(format);
        DataFrame diabetes = csv.read(Paths.getTestData("regression/diabetes.csv"));

        System.out.println(diabetes);
        System.out.println(diabetes.schema());

        assertEquals(442, diabetes.nrows());
        assertEquals(65, diabetes.ncols());

        StructField[] fields = diabetes.schema().fields();
        assertEquals(DataTypes.IntegerType, fields[0].type);
        for (int i = 1; i < fields.length; i++) {
            assertEquals(DataTypes.DoubleType, fields[i].type);
        }

        assertEquals(151, diabetes.getInt(0, 0));
        assertEquals(0.038075906433423, diabetes.getDouble(0, 1), 1E-10);
        assertEquals(0.0506801187398183, diabetes.getDouble(0, 2), 1E-10);
        assertEquals(0.0616962065186835, diabetes.getDouble(0, 3), 1E-10);

        assertEquals(57, diabetes.get(441, 0));
        assertEquals(-0.0454724779400237, diabetes.getDouble(441, 1), 1E-10);
        assertEquals(-0.0446416365069889, diabetes.getDouble(441, 2), 1E-10);
        assertEquals(-0.0730303027164164, diabetes.getDouble(441, 3), 1E-10);
    }

    /**
     * Test of read method, of class CSV.
     */
    @Test(expected = Test.None.class)
    public void testProstate() throws Exception {
        System.out.println("prostate");

        CSVFormat format = CSVFormat.newFormat('\t').withFirstRecordAsHeader();
        CSV csv = new CSV(format);
        DataFrame prostate = csv.read(Paths.getTestData("regression/prostate-train.csv"));

        System.out.println(prostate);
        System.out.println(prostate.schema());

        assertEquals(67, prostate.nrows());
        assertEquals(9, prostate.ncols());

        StructType schema = DataTypes.struct(
                new StructField("lcavol", DataTypes.DoubleType),
                new StructField("lweight", DataTypes.DoubleType),
                new StructField("age", DataTypes.IntegerType),
                new StructField("lbph", DataTypes.DoubleType),
                new StructField("svi", DataTypes.IntegerType),
                new StructField("lcp", DataTypes.DoubleType),
                new StructField("gleason", DataTypes.IntegerType),
                new StructField("pgg45", DataTypes.IntegerType),
                new StructField("lpsa", DataTypes.DoubleType)
        );
        assertEquals(schema, prostate.schema());

        assertEquals(-0.579818495, prostate.getDouble(0, 0), 1E-7);
        assertEquals(2.769459, prostate.getDouble(0, 1), 1E-7);
        assertEquals(50, prostate.getInt(0, 2));
        assertEquals(-1.38629436, prostate.getDouble(0, 3), 1E-7);

        assertEquals(2.882563575, prostate.getDouble(66, 0), 1E-7);
        assertEquals(3.773910, prostate.getDouble(66, 1), 1E-7);
        assertEquals(68, prostate.getInt(66, 2));
        assertEquals(1.55814462, prostate.getDouble(66, 3), 1E-7);
    }

    /**
     * Test of read method, of class CSV.
     */
    @Test(expected = Test.None.class)
    public void testAbalone() throws Exception {
        System.out.println("abalone");

        CSV csv = new CSV();
        DataFrame abalone = csv.read(Paths.getTestData("regression/abalone-train.data"));

        System.out.println(abalone);
        System.out.println(abalone.schema());

        assertEquals(3133, abalone.nrows());
        assertEquals(9, abalone.ncols());

        StructField[] fields = abalone.schema().fields();
        assertEquals(DataTypes.StringType, fields[0].type);
        assertEquals(DataTypes.IntegerType, fields[fields.length - 1].type);
        for (int i = 1; i < fields.length - 1; i++) {
            assertEquals(DataTypes.DoubleType, fields[i].type);
        }

        assertEquals("M", abalone.get(0, 0));
        assertEquals(0.455, abalone.getDouble(0, 1), 1E-7);
        assertEquals(0.365, abalone.getDouble(0, 2), 1E-7);
        assertEquals(15, abalone.getInt(0, 8));

        assertEquals("F", abalone.get(3132, 0));
        assertEquals(0.685, abalone.getDouble(3132, 1), 1E-7);
        assertEquals(0.53, abalone.getDouble(3132, 2), 1E-7);
        assertEquals(10, abalone.getInt(3132, 8));
    }
}
