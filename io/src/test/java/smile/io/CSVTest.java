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

    /**
     * Test of read method, of class CSV.
     */
    @Test(expected = Test.None.class)
    public void testUserdata() throws Exception {
        System.out.println("userdata");

        CSVFormat format = CSVFormat.DEFAULT.withFirstRecordAsHeader();
        CSV csv = new CSV(format);
        DataFrame df = csv.read(Paths.getTestData("csv/userdata1.csv"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(1000, df.nrows());
        assertEquals(13, df.ncols());

        smile.data.type.StructType schema = DataTypes.struct(
                new StructField("registration_dttm", DataTypes.StringType),
                new StructField("id", DataTypes.IntegerType),
                new StructField("first_name", DataTypes.StringType),
                new StructField("last_name", DataTypes.StringType),
                new StructField("email", DataTypes.StringType),
                new StructField("gender", DataTypes.StringType),
                new StructField("ip_address", DataTypes.StringType),
                new StructField("cc", DataTypes.LongObjectType),
                new StructField("country", DataTypes.StringType),
                new StructField("birthdate", DataTypes.StringType),
                new StructField("salary", DataTypes.DoubleObjectType),
                new StructField("title", DataTypes.StringType),
                new StructField("comments", DataTypes.StringType)
        );
        assertEquals(schema, df.schema());

        assertEquals("2016-02-03T07:55:29Z", df.get(0,0));
        assertEquals(1, df.getLong(0, 1));
        assertEquals("Amanda", df.getString(0, 2));
        assertEquals("Jordan", df.getString(0, 3));
        assertEquals("ajordan0@com.com", df.getString(0, 4));
        assertEquals("Female", df.getString(0, 5));
        assertEquals("1.197.201.2", df.getString(0, 6));
        assertEquals(6759521864920116L, df.getLong(0, 7));
        assertEquals("Indonesia", df.getString(0, 8));
        assertEquals("3/8/1971", df.getString(0, 9));
        assertEquals(49756.53, df.getDouble(0, 10), 1E-10);
        assertEquals("Internal Auditor", df.getString(0, 11));
        assertEquals("1E+02", df.getString(0, 12));
    }
}
