/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

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

    @Test
    public void testAirline() throws Exception {
        System.out.println("airline");

        DataFrame df = SAS.read(Paths.getTestData("sas/airline.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(32, df.nrow());
        assertEquals(6, df.ncol());

        StructField[] fields = df.schema().fields();
        for (StructField field : fields) {
            assertEquals(DataTypes.DoubleType, field.type);
        }

        assertEquals(1948, df.getDouble(0, 0), 1E-6);
        assertEquals(1.214, df.getDouble(0, 1), 1E-6);
        assertEquals(0.243, df.getDouble(0, 2), 1E-6);
    }

    @Test
    public void testFlorida() throws Exception {
        System.out.println("florida");

        DataFrame df = SAS.read(Paths.getTestData("sas/florida.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(67, df.nrow());
        assertEquals(6, df.ncol());

        StructField[] fields = df.schema().fields();
        for (StructField field : fields) {
            assertEquals(DataTypes.DoubleType, field.type);
        }

        assertEquals(0, df.getDouble(0, 0), 1E-6);
        assertEquals(47300, df.getDouble(0, 1), 1E-6);
        assertEquals(34062, df.getDouble(0, 2), 1E-6);
    }

    @Test
    public void testGlod() throws Exception {
        System.out.println("gold");

        DataFrame df = SAS.read(Paths.getTestData("sas/gold.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(200, df.nrow());
        assertEquals(1, df.ncol());

        StructField[] fields = df.schema().fields();
        for (StructField field : fields) {
            assertEquals(DataTypes.DoubleType, field.type);
        }

        assertEquals(-0.731528, df.getDouble(0, 0), 1E-6);
        assertEquals(-0.444905, df.getDouble(1, 0), 1E-6);
        assertEquals(-0.462609, df.getDouble(2, 0), 1E-6);
    }

    @Test
    public void testGolf() throws Exception {
        System.out.println("golf");

        DataFrame df = SAS.read(Paths.getTestData("sas/golf.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(150, df.nrow());
        assertEquals(2, df.ncol());

        StructField[] fields = df.schema().fields();
        for (StructField field : fields) {
            assertEquals(DataTypes.DoubleType, field.type);
        }

        assertEquals(-5, df.getDouble(0, 0), 1E-6);
        assertEquals(2, df.getDouble(0, 1), 1E-6);
    }

    @Test
    public void testJobs() throws Exception {
        System.out.println("jobs");

        DataFrame df = SAS.read(Paths.getTestData("sas/jobs.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(24, df.nrow());
        assertEquals(2, df.ncol());

        StructField[] fields = df.schema().fields();
        for (StructField field : fields) {
            assertEquals(DataTypes.DoubleType, field.type);
        }

        assertEquals(5.63, df.getDouble(0, 0), 1E-6);
        assertEquals(104.629997, df.getDouble(0, 1), 1E-6);
    }

    @Test
    public void testMeat() throws Exception {
        System.out.println("meta");

        DataFrame df = SAS.read(Paths.getTestData("sas/meat.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(17, df.nrow());
        assertEquals(5, df.ncol());

        StructField[] fields = df.schema().fields();
        for (StructField field : fields) {
            assertEquals(DataTypes.DoubleType, field.type);
        }

        assertEquals(121.300003, df.getDouble(0, 0), 1E-6);
        assertEquals(355, df.getDouble(0, 1), 1E-6);
        assertEquals(25.68, df.getDouble(0, 2), 1E-6);
    }

    @Test
    public void testTax() throws Exception {
        System.out.println("tax");

        DataFrame df = SAS.read(Paths.getTestData("sas/tax.sas7bdat"));

        System.out.println(df);
        System.out.println(df.schema());

        assertEquals(30, df.nrow());
        assertEquals(4, df.ncol());

        StructField[] fields = df.schema().fields();
        for (StructField field : fields) {
            assertEquals(DataTypes.DoubleType, field.type);
        }

        assertEquals(9.215, df.getDouble(0, 0), 1E-6);
        assertEquals(1.643, df.getDouble(0, 1), 1E-6);
        assertEquals(9.518, df.getDouble(0, 2), 1E-6);
    }
}
