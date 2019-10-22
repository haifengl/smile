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
import smile.util.Paths;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class SASTest {

    public SASTest() {
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
     * Test of read method, of class SAS.
     */
    @Test(expected = Test.None.class)
    public void testAirline() throws Exception {
        System.out.println("airline");

        DataFrame df = SAS.read(Paths.getTestData("sas/airline.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(32, df.nrows());
        assertEquals(6, df.ncols());

        StructField[] fields = df.schema().fields();
        for (int i = 0; i < fields.length; i++) {
            assertEquals(DataTypes.DoubleType, fields[i].type);
        }

        assertEquals(1948, df.getDouble(0, 0), 1E-6);
        assertEquals(1.214, df.getDouble(0, 1), 1E-6);
        assertEquals(0.243, df.getDouble(0, 2), 1E-6);
    }

    /**
     * Test of read method, of class SAS.
     */
    @Test(expected = Test.None.class)
    public void testFlorida() throws Exception {
        System.out.println("florida");

        DataFrame df = SAS.read(Paths.getTestData("sas/florida.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(67, df.nrows());
        assertEquals(6, df.ncols());

        StructField[] fields = df.schema().fields();
        for (int i = 0; i < fields.length; i++) {
            assertEquals(DataTypes.DoubleType, fields[i].type);
        }

        assertEquals(0, df.getDouble(0, 0), 1E-6);
        assertEquals(47300, df.getDouble(0, 1), 1E-6);
        assertEquals(34062, df.getDouble(0, 2), 1E-6);
    }

    /**
     * Test of read method, of class SAS.
     */
    @Test(expected = Test.None.class)
    public void testGlod() throws Exception {
        System.out.println("gold");

        DataFrame df = SAS.read(Paths.getTestData("sas/gold.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(200, df.nrows());
        assertEquals(1, df.ncols());

        StructField[] fields = df.schema().fields();
        for (int i = 0; i < fields.length; i++) {
            assertEquals(DataTypes.DoubleType, fields[i].type);
        }

        assertEquals(-0.731528, df.getDouble(0, 0), 1E-6);
        assertEquals(-0.444905, df.getDouble(1, 0), 1E-6);
        assertEquals(-0.462609, df.getDouble(2, 0), 1E-6);
    }

    /**
     * Test of read method, of class SAS.
     */
    @Test(expected = Test.None.class)
    public void testGolf() throws Exception {
        System.out.println("golf");

        DataFrame df = SAS.read(Paths.getTestData("sas/golf.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(150, df.nrows());
        assertEquals(2, df.ncols());

        StructField[] fields = df.schema().fields();
        for (int i = 0; i < fields.length; i++) {
            assertEquals(DataTypes.DoubleType, fields[i].type);
        }

        assertEquals(-5, df.getDouble(0, 0), 1E-6);
        assertEquals(2, df.getDouble(0, 1), 1E-6);
    }

    /**
     * Test of read method, of class SAS.
     */
    @Test(expected = Test.None.class)
    public void testJobs() throws Exception {
        System.out.println("jobs");

        DataFrame df = SAS.read(Paths.getTestData("sas/jobs.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(24, df.nrows());
        assertEquals(2, df.ncols());

        StructField[] fields = df.schema().fields();
        for (int i = 0; i < fields.length; i++) {
            assertEquals(DataTypes.DoubleType, fields[i].type);
        }

        assertEquals(5.63, df.getDouble(0, 0), 1E-6);
        assertEquals(104.629997, df.getDouble(0, 1), 1E-6);
    }

    /**
     * Test of read method, of class SAS.
     */
    @Test(expected = Test.None.class)
    public void testMeat() throws Exception {
        System.out.println("meta");

        DataFrame df = SAS.read(Paths.getTestData("sas/meat.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(17, df.nrows());
        assertEquals(5, df.ncols());

        StructField[] fields = df.schema().fields();
        for (int i = 0; i < fields.length; i++) {
            assertEquals(DataTypes.DoubleType, fields[i].type);
        }

        assertEquals(121.300003, df.getDouble(0, 0), 1E-6);
        assertEquals(355, df.getDouble(0, 1), 1E-6);
        assertEquals(25.68, df.getDouble(0, 2), 1E-6);
    }

    /**
     * Test of read method, of class SAS.
     */
    @Test(expected = Test.None.class)
    public void testTax() throws Exception {
        System.out.println("tax");

        DataFrame df = SAS.read(Paths.getTestData("sas/tax.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(30, df.nrows());
        assertEquals(4, df.ncols());

        StructField[] fields = df.schema().fields();
        for (int i = 0; i < fields.length; i++) {
            assertEquals(DataTypes.DoubleType, fields[i].type);
        }

        assertEquals(9.215, df.getDouble(0, 0), 1E-6);
        assertEquals(1.643, df.getDouble(0, 1), 1E-6);
        assertEquals(9.518, df.getDouble(0, 2), 1E-6);
    }
}
