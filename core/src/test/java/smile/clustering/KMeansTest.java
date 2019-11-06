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

package smile.clustering;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.GaussianMixture;
import smile.data.USPS;
import smile.math.MathEx;
import smile.validation.AdjustedRandIndex;
import smile.validation.RandIndex;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class KMeansTest {
    double[][] x = GaussianMixture.x;
    int[] y = GaussianMixture.y;

    public KMeansTest() {

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
    public void testBBD4() {
        System.out.println("BBD 4");
        MathEx.setSeed(19650218); // to get repeatable results.
        KMeans kmeans = KMeans.fit(x, 4);
        double r = RandIndex.of(y, kmeans.y);
        double r2 = AdjustedRandIndex.of(y, kmeans.y);
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.6111, r, 1E-4);
        assertEquals(0.2475, r2, 1E-4);
    }

    @Test
    public void testLloyd4() {
        System.out.println("Lloyd 4");
        MathEx.setSeed(19650218); // to get repeatable results.
        KMeans kmeans = KMeans.lloyd(x, 4);
        double r = RandIndex.of(y, kmeans.y);
        double r2 = AdjustedRandIndex.of(y, kmeans.y);
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.6111, r, 1E-4);
        assertEquals(0.2475, r2, 1E-4);
    }

    @Test
    public void testBBD64() {
        System.out.println("BBD 64");
        MathEx.setSeed(19650218); // to get repeatable results.
        KMeans kmeans = KMeans.fit(x, 64);
        double r = RandIndex.of(y, kmeans.y);
        double r2 = AdjustedRandIndex.of(y, kmeans.y);
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.4714, r, 1E-4);
        assertEquals(0.0185, r2, 1E-4);
    }

    @Test
    public void testLloyd64() {
        System.out.println("Lloyd 64");
        MathEx.setSeed(19650218); // to get repeatable results.
        KMeans kmeans = KMeans.lloyd(x, 64);
        double r = RandIndex.of(y, kmeans.y);
        double r2 = AdjustedRandIndex.of(y, kmeans.y);
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.4714, r, 1E-4);
        assertEquals(0.0185, r2, 1E-4);
    }

    @Test(expected = Test.None.class)
    public void testUSPS() throws Exception {
        System.out.println("USPS");
        MathEx.setSeed(19650218); // to get repeatable results.

        double[][] x = USPS.x;
        int[] y = USPS.y;
        double[][] testx = USPS.testx;
        int[] testy = USPS.testy;

        KMeans kmeans = KMeans.fit(x, 10, 100, 4);

        double r = RandIndex.of(y, kmeans.y);
        double r2 = AdjustedRandIndex.of(y, kmeans.y);
        System.out.format("Training rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.9063, r, 1E-4);
        assertEquals(0.5148, r2, 1E-4);
            
        int[] p = new int[testx.length];
        for (int i = 0; i < testx.length; i++) {
            p[i] = kmeans.predict(testx[i]);
        }
            
        r = RandIndex.of(testy, p);
        r2 = AdjustedRandIndex.of(testy, p);
        System.out.format("Testing rand index = %.2f%%, adjusted rand index = %.2f%%%n", 100.0 * r, 100.0 * r2);
        assertEquals(0.8942, r, 1E-4);
        assertEquals(0.4540, r2, 1E-4);
    }
}