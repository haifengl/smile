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

package smile.feature;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.DataFrame;
import smile.data.Colon;
import smile.data.Segment;

import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class FeatureTransformTest {

    public FeatureTransformTest() {
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
    public void testScaler() {
        System.out.println("Scaler");

        Scaler scaler = Scaler.fit(Segment.train);
        DataFrame df = scaler.transform(Segment.test);

        assertEquals(0.565217, df.getDouble(0, 0), 1E-4);
        assertEquals(0.100000, df.getDouble(0, 1), 1E-4);
        assertEquals(0.000000, df.getDouble(0, 2), 1E-4);
        assertEquals(0.000000, df.getDouble(0, 3), 1E-4);
        assertEquals(0.079848, df.getDouble(0, 5), 1E-4);
        assertEquals(0.002050, df.getDouble(0, 6), 1E-4);

        assertEquals(0.462451, df.getDouble(1, 0), 1E-4);
        assertEquals(0.704167, df.getDouble(1, 1), 1E-4);
        assertEquals(0.000000, df.getDouble(1, 2), 1E-4);
        assertEquals(0.000000, df.getDouble(1, 3), 1E-4);
        assertEquals(0.066540, df.getDouble(1, 5), 1E-4);
        assertEquals(0.001494, df.getDouble(1, 6), 1E-4);

        scaler = Scaler.fit(Segment.x);
        double[][] x = scaler.transform(Segment.testx);
        assertEquals(0.565217, x[0][0], 1E-4);
        assertEquals(0.100000, x[0][1], 1E-4);
        assertEquals(0.000000, x[0][2], 1E-4);
        assertEquals(0.000000, x[0][3], 1E-4);
        assertEquals(0.079848, x[0][5], 1E-4);
        assertEquals(0.002050, x[0][6], 1E-4);

        assertEquals(0.462451, x[1][0], 1E-4);
        assertEquals(0.704167, x[1][1], 1E-4);
        assertEquals(0.000000, x[1][2], 1E-4);
        assertEquals(0.000000, x[1][3], 1E-4);
        assertEquals(0.066540, x[1][5], 1E-4);
        assertEquals(0.001494, x[1][6], 1E-4);
    }

    @Test
    public void testWinsorScaler() {
        System.out.println("Winsor");

        WinsorScaler scaler = WinsorScaler.fit(Segment.train);
        DataFrame df = scaler.transform(Segment.test);

        assertEquals(0.573525, df.getDouble(0, 0), 1E-4);
        assertEquals(0.027485, df.getDouble(0, 1), 1E-4);
        assertEquals(0.000000, df.getDouble(0, 2), 1E-4);
        assertEquals(0.000000, df.getDouble(0, 3), 1E-4);
        assertEquals(0.422118, df.getDouble(0, 5), 1E-4);
        assertEquals(0.265186, df.getDouble(0, 6), 1E-4);

        assertEquals(0.457985, df.getDouble(1, 0), 1E-4);
        assertEquals(0.800570, df.getDouble(1, 1), 1E-4);
        assertEquals(0.000000, df.getDouble(1, 2), 1E-4);
        assertEquals(0.000000, df.getDouble(1, 3), 1E-4);
        assertEquals(0.344465, df.getDouble(1, 5), 1E-4);
        assertEquals(0.191074, df.getDouble(1, 6), 1E-4);

        assertEquals(0.0, df.getDouble(2, 0), 1E-4);
        assertEquals(1.0, df.getDouble(3, 1), 1E-4);

        scaler = WinsorScaler.fit(Segment.x);
        double[][] x = scaler.transform(Segment.testx);
        assertEquals(0.573525, x[0][0], 1E-4);
        assertEquals(0.027485, x[0][1], 1E-4);
        assertEquals(0.000000, x[0][2], 1E-4);
        assertEquals(0.000000, x[0][3], 1E-4);
        assertEquals(0.422118, x[0][5], 1E-4);
        assertEquals(0.265186, x[0][6], 1E-4);

        assertEquals(0.457985, x[1][0], 1E-4);
        assertEquals(0.800570, x[1][1], 1E-4);
        assertEquals(0.000000, x[1][2], 1E-4);
        assertEquals(0.000000, x[1][3], 1E-4);
        assertEquals(0.344465, x[1][5], 1E-4);
        assertEquals(0.191074, x[1][6], 1E-4);

        assertEquals(0.0, x[2][0], 1E-4);
        assertEquals(1.0, x[3][1], 1E-4);
    }

    @Test
    public void testMaxAbsScaler() {
        System.out.println("MaxAbs");

        MaxAbsScaler scaler = MaxAbsScaler.fit(Segment.train);
        DataFrame df = scaler.transform(Segment.test);

        assertEquals(0.566929, df.getDouble(0, 0), 1E-4);
        assertEquals(0.139442, df.getDouble(0, 1), 1E-4);
        assertEquals(1.000000, df.getDouble(0, 2), 1E-4);
        assertEquals(0.000000, df.getDouble(0, 3), 1E-4);
        assertEquals(0.079848, df.getDouble(0, 5), 1E-4);
        assertEquals(0.002050, df.getDouble(0, 6), 1E-4);

        assertEquals(0.464567, df.getDouble(1, 0), 1E-4);
        assertEquals(0.717131, df.getDouble(1, 1), 1E-4);
        assertEquals(1.000000, df.getDouble(1, 2), 1E-4);
        assertEquals(0.000000, df.getDouble(1, 3), 1E-4);
        assertEquals(0.066540, df.getDouble(1, 5), 1E-4);
        assertEquals(0.001494, df.getDouble(1, 6), 1E-4);

        scaler = MaxAbsScaler.fit(Segment.x);
        double[][] x = scaler.transform(Segment.testx);
        assertEquals(0.566929, x[0][0], 1E-4);
        assertEquals(0.139442, x[0][1], 1E-4);
        assertEquals(1.000000, x[0][2], 1E-4);
        assertEquals(0.000000, x[0][3], 1E-4);
        assertEquals(0.079848, x[0][5], 1E-4);
        assertEquals(0.002050, x[0][6], 1E-4);

        assertEquals(0.464567, x[1][0], 1E-4);
        assertEquals(0.717131, x[1][1], 1E-4);
        assertEquals(1.000000, x[1][2], 1E-4);
        assertEquals(0.000000, x[1][3], 1E-4);
        assertEquals(0.066540, x[1][5], 1E-4);
        assertEquals(0.001494, x[1][6], 1E-4);
    }

    @Test
    public void testStandardizer() {
        System.out.println("Standardizer");

        Standardizer scaler = Standardizer.fit(Segment.train);
        DataFrame df = scaler.transform(Segment.test);

        assertEquals( 0.256779, df.getDouble(0, 0), 1E-4);
        assertEquals(-1.537501, df.getDouble(0, 1), 1E-4);
        assertEquals( 0.000000, df.getDouble(0, 2), 1E-4);
        assertEquals(-0.364283, df.getDouble(0, 3), 1E-4);
        assertEquals( 0.125478, df.getDouble(0, 5), 1E-4);
        assertEquals(-0.088232, df.getDouble(0, 6), 1E-4);

        assertEquals(-0.098278, df.getDouble(1, 0), 1E-4);
        assertEquals( 0.974190, df.getDouble(1, 1), 1E-4);
        assertEquals( 0.000000, df.getDouble(1, 2), 1E-4);
        assertEquals(-0.364283, df.getDouble(1, 3), 1E-4);
        assertEquals(-0.007325, df.getDouble(1, 5), 1E-4);
        assertEquals(-0.098583, df.getDouble(1, 6), 1E-4);

        scaler = Standardizer.fit(Segment.x);
        double[][] x = scaler.transform(Segment.testx);
        assertEquals( 0.256779, x[0][0], 1E-4);
        assertEquals(-1.537501, x[0][1], 1E-4);
        assertEquals( 0.000000, x[0][2], 1E-4);
        assertEquals(-0.364283, x[0][3], 1E-4);
        assertEquals( 0.125478, x[0][5], 1E-4);
        assertEquals(-0.088232, x[0][6], 1E-4);

        assertEquals(-0.098278, x[1][0], 1E-4);
        assertEquals( 0.974190, x[1][1], 1E-4);
        assertEquals( 0.000000, x[1][2], 1E-4);
        assertEquals(-0.364283, x[1][3], 1E-4);
        assertEquals(-0.007325, x[1][5], 1E-4);
        assertEquals(-0.098583, x[1][6], 1E-4);
    }

    @Test
    public void testRobustStandardizer() {
        System.out.println("RobustStandardizer");

        RobustStandardizer scaler = RobustStandardizer.fit(Segment.train);
        DataFrame df = scaler.transform(Segment.test);

        assertEquals( 0.173228, df.getDouble(0, 0), 1E-4);
        assertEquals(-0.939850, df.getDouble(0, 1), 1E-4);
        assertEquals( 0.000000, df.getDouble(0, 2), 1E-4);
        assertEquals( 0.000000, df.getDouble(0, 3), 1E-4);
        assertEquals( 0.746149, df.getDouble(0, 5), 1E-4);
        assertEquals( 0.844629, df.getDouble(0, 6), 1E-4);

        assertEquals(-0.031496, df.getDouble(1, 0), 1E-4);
        assertEquals( 0.617615, df.getDouble(1, 1), 1E-4);
        assertEquals( 0.000000, df.getDouble(1, 2), 1E-4);
        assertEquals( 0.000000, df.getDouble(1, 3), 1E-4);
        assertEquals( 0.476919, df.getDouble(1, 5), 1E-4);
        assertEquals( 0.459047, df.getDouble(1, 6), 1E-4);

        scaler = RobustStandardizer.fit(Segment.x);
        double[][] x = scaler.transform(Segment.testx);
        assertEquals( 0.173228, x[0][0], 1E-4);
        assertEquals(-0.939850, x[0][1], 1E-4);
        assertEquals( 0.000000, x[0][2], 1E-4);
        assertEquals( 0.000000, x[0][3], 1E-4);
        assertEquals( 0.746149, x[0][5], 1E-4);
        assertEquals( 0.844629, x[0][6], 1E-4);

        assertEquals(-0.031496, x[1][0], 1E-4);
        assertEquals( 0.617615, x[1][1], 1E-4);
        assertEquals( 0.000000, x[1][2], 1E-4);
        assertEquals( 0.000000, x[1][3], 1E-4);
        assertEquals( 0.476919, x[1][5], 1E-4);
        assertEquals( 0.459047, x[1][6], 1E-4);
    }

    @Test
    public void testNormalizer() {
        System.out.println("Normalizer");

        double[][] x = Normalizer.L1.transform(Colon.x);
        assertEquals(0.013340, x[0][0], 1E-4);
        assertEquals(0.008492, x[0][1], 1E-4);
        assertEquals(0.006621, x[0][2], 1E-4);
        assertEquals(0.006313, x[0][3], 1E-4);
        assertEquals(0.008204, x[0][5], 1E-4);
        assertEquals(0.003370, x[0][6], 1E-4);

        assertEquals(0.010351, x[1][0], 1E-4);
        assertEquals(0.007589, x[1][1], 1E-4);
        assertEquals(0.005516, x[1][2], 1E-4);
        assertEquals(0.004199, x[1][3], 1E-4);
        assertEquals(0.006291, x[1][5], 1E-4);
        assertEquals(0.004347, x[1][6], 1E-4);

        x = Normalizer.L2.transform(Colon.x);
        assertEquals(0.303366, x[0][0], 1E-4);
        assertEquals(0.193131, x[0][1], 1E-4);
        assertEquals(0.150577, x[0][2], 1E-4);
        assertEquals(0.143568, x[0][3], 1E-4);
        assertEquals(0.186565, x[0][5], 1E-4);
        assertEquals(0.076632, x[0][6], 1E-4);

        assertEquals(0.256772, x[1][0], 1E-4);
        assertEquals(0.188274, x[1][1], 1E-4);
        assertEquals(0.136829, x[1][2], 1E-4);
        assertEquals(0.104179, x[1][3], 1E-4);
        assertEquals(0.156063, x[1][5], 1E-4);
        assertEquals(0.107846, x[1][6], 1E-4);

        x = Normalizer.L_INF.transform(Colon.x);
        assertEquals(1.000000, x[0][0], 1E-4);
        assertEquals(0.636625, x[0][1], 1E-4);
        assertEquals(0.496356, x[0][2], 1E-4);
        assertEquals(0.473249, x[0][3], 1E-4);
        assertEquals(0.614981, x[0][5], 1E-4);
        assertEquals(0.252604, x[0][6], 1E-4);

        assertEquals(1.000000, x[1][0], 1E-4);
        assertEquals(0.733233, x[1][1], 1E-4);
        assertEquals(0.532880, x[1][2], 1E-4);
        assertEquals(0.405724, x[1][3], 1E-4);
        assertEquals(0.607786, x[1][5], 1E-4);
        assertEquals(0.420008, x[1][6], 1E-4);
    }
}
