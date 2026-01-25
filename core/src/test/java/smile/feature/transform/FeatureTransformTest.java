/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.feature.transform;

import smile.data.DataFrame;
import smile.datasets.ColonCancer;
import smile.datasets.ImageSegmentation;
import smile.data.transform.InvertibleColumnTransform;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class FeatureTransformTest {
    ImageSegmentation segment;
    public FeatureTransformTest() throws Exception {
        segment = new ImageSegmentation();
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
    public void testScaler() {
        System.out.println("Scaler");

        InvertibleColumnTransform transform = Scaler.fit(segment.train());
        DataFrame df = transform.apply(segment.test());
        System.out.println(transform);

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
    }

    @Test
    public void testWinsorScaler() {
        System.out.println("Winsor");

        InvertibleColumnTransform transform = WinsorScaler.fit(segment.train());
        DataFrame df = transform.apply(segment.test());
        System.out.println(transform);

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
    }

    @Test
    public void testMaxAbsScaler() {
        System.out.println("MaxAbs");

        InvertibleColumnTransform transform = MaxAbsScaler.fit(segment.train());
        DataFrame df = transform.apply(segment.test());
        System.out.println(transform);

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
    }

    @Test
    public void testStandardizer() {
        System.out.println("Standardizer");

        InvertibleColumnTransform transform = Standardizer.fit(segment.train());
        DataFrame df = transform.apply(segment.test());
        System.out.println(transform);

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
    }

    @Test
    public void testRobustStandardizer() {
        System.out.println("RobustStandardizer");

        InvertibleColumnTransform transform = RobustStandardizer.fit(segment.train());
        DataFrame df = transform.apply(segment.test());
        System.out.println(transform);

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
    }

    @Test
    public void testNormalizer() throws Exception {
        System.out.println("Normalizer");
        var data = ColonCancer.load();
        DataFrame colon = DataFrame.of(data.x());
        Normalizer transform = new Normalizer(Normalizer.Norm.L1, colon.names());
        DataFrame df = transform.apply(colon);
        System.out.println(transform);

        assertEquals(0.013340, df.getDouble(0, 0), 1E-4);
        assertEquals(0.008492, df.getDouble(0, 1), 1E-4);
        assertEquals(0.006621, df.getDouble(0, 2), 1E-4);
        assertEquals(0.006313, df.getDouble(0, 3), 1E-4);
        assertEquals(0.008204, df.getDouble(0, 5), 1E-4);
        assertEquals(0.003370, df.getDouble(0, 6), 1E-4);

        assertEquals(0.010351, df.getDouble(1, 0), 1E-4);
        assertEquals(0.007589, df.getDouble(1, 1), 1E-4);
        assertEquals(0.005516, df.getDouble(1, 2), 1E-4);
        assertEquals(0.004199, df.getDouble(1, 3), 1E-4);
        assertEquals(0.006291, df.getDouble(1, 5), 1E-4);
        assertEquals(0.004347, df.getDouble(1, 6), 1E-4);

        transform = new Normalizer(Normalizer.Norm.L2, colon.names());
        df = transform.apply(colon);
        System.out.println(transform);

        assertEquals(0.303366, df.getDouble(0, 0), 1E-4);
        assertEquals(0.193131, df.getDouble(0, 1), 1E-4);
        assertEquals(0.150577, df.getDouble(0, 2), 1E-4);
        assertEquals(0.143568, df.getDouble(0, 3), 1E-4);
        assertEquals(0.186565, df.getDouble(0, 5), 1E-4);
        assertEquals(0.076632, df.getDouble(0, 6), 1E-4);

        assertEquals(0.256772, df.getDouble(1, 0), 1E-4);
        assertEquals(0.188274, df.getDouble(1, 1), 1E-4);
        assertEquals(0.136829, df.getDouble(1, 2), 1E-4);
        assertEquals(0.104179, df.getDouble(1, 3), 1E-4);
        assertEquals(0.156063, df.getDouble(1, 5), 1E-4);
        assertEquals(0.107846, df.getDouble(1, 6), 1E-4);

        transform = new Normalizer(Normalizer.Norm.L_INF, colon.names());
        df = transform.apply(colon);
        System.out.println(transform);

        assertEquals(1.000000, df.getDouble(0, 0), 1E-4);
        assertEquals(0.636625, df.getDouble(0, 1), 1E-4);
        assertEquals(0.496356, df.getDouble(0, 2), 1E-4);
        assertEquals(0.473249, df.getDouble(0, 3), 1E-4);
        assertEquals(0.614981, df.getDouble(0, 5), 1E-4);
        assertEquals(0.252604, df.getDouble(0, 6), 1E-4);

        assertEquals(1.000000, df.getDouble(1, 0), 1E-4);
        assertEquals(0.733233, df.getDouble(1, 1), 1E-4);
        assertEquals(0.532880, df.getDouble(1, 2), 1E-4);
        assertEquals(0.405724, df.getDouble(1, 3), 1E-4);
        assertEquals(0.607786, df.getDouble(1, 5), 1E-4);
        assertEquals(0.420008, df.getDouble(1, 6), 1E-4);
    }
}
